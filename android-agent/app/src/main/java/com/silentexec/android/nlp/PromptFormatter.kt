package com.silentexec.android.nlp

import com.silentexec.android.core.ActionCommand
import com.silentexec.android.core.ActionJson
import com.silentexec.android.core.MultiStepPlan
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * Result wrapper for parsed LLM outputs: either a MultiStepPlan or a single ActionCommand.
 */
sealed class PlanOrAction {
    data class Plan(val multiStepPlan: MultiStepPlan) : PlanOrAction()
    data class Action(val actionCommand: ActionCommand) : PlanOrAction()
}

/**
 * ChatML Prompt Formatter and Strict JSON Extractor for SilentExec Android.
 * Enforces zero-chatter token constraints in strict parity with `nlp-training/generate_dataset.py`
 * and Rust `core-rust/crates/silent-exec-pc/src/inference.rs`.
 */
object PromptFormatter {

    const val DEFAULT_SYSTEM_PROMPT: String =
        "You are SilentExec, a zero-chatter OS execution agent. " +
        "Emit only a valid ActionCommand or MultiStepPlan JSON matching the strict schema. " +
        "No conversational responses."

    private val CHATTER_INDICATORS = listOf(
        "sure", "here is", "here's", "certainly", "i can", "of course",
        "sorry", "as requested", "here you go", "note that", "please find",
        "حاضر", "بالتأكيد", "تفضل", "إليك", "سأقوم"
    )

    /**
     * Constructs a standard ChatML prompt for Qwen2.5 / ChatML-tuned models.
     */
    fun buildChatMLPrompt(systemPrompt: String = DEFAULT_SYSTEM_PROMPT, userQuery: String): String {
        return "<|im_start|>system\n${systemPrompt.trim()}<|im_end|>\n" +
               "<|im_start|>user\n${userQuery.trim()}<|im_end|>\n" +
               "<|im_start|>assistant\n"
    }

    /**
     * Formats user query using default system prompt.
     */
    fun formatPrompt(userQuery: String): String {
        return buildChatMLPrompt(DEFAULT_SYSTEM_PROMPT, userQuery)
    }

    /**
     * Builds a screen-aware ChatML prompt for iterative ReAct loop:
     * - The full user goal
     * - The live pruned screen elements
     * - Step history of completed actions
     */
    fun buildScreenAwarePrompt(
        userGoal: String,
        prunedUiTree: List<String>,
        stepHistory: List<String>
    ): String {
        val screenSection = if (prunedUiTree.isNotEmpty()) {
            "Current screen elements:\n" + prunedUiTree.joinToString("\n")
        } else {
            "Current screen elements: none visible"
        }

        val historySection = if (stepHistory.isNotEmpty()) {
            "Completed steps:\n" + stepHistory.joinToString("\n")
        } else {
            "Completed steps: none"
        }

        val userQuery = """
Goal: ${userGoal.trim()}
$historySection
$screenSection

Emit the next ActionCommand JSON or final {"action":"done","platform":"android","confidence":1.0} when the goal is achieved.
""".trimIndent()

        return buildChatMLPrompt(DEFAULT_SYSTEM_PROMPT, userQuery)
    }

    /**
     * Strictly extracts ActionCommand or MultiStepPlan JSON from raw model output.
     * Strips ChatML EOS tokens, markdown code fences, and unconditionally rejects conversational chatter.
     */
    fun extractActionJson(rawOutput: String): String {
        val trimmed = rawOutput.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("Model output is empty")
        }

        var cleaned = trimmed
        if (cleaned.endsWith("<|im_end|>")) {
            cleaned = cleaned.removeSuffix("<|im_end|>").trim()
        }
        if (cleaned.endsWith("<|endoftext|>")) {
            cleaned = cleaned.removeSuffix("<|endoftext|>").trim()
        }

        // Strip markdown code fences if present
        if (cleaned.startsWith("```")) {
            val lines = cleaned.lines()
            if (lines.size >= 2 && lines.last().trim().startsWith("```")) {
                cleaned = lines.subList(1, lines.size - 1).joinToString("\n").trim()
            }
        }

        val startIdx = cleaned.indexOf('{')
        if (startIdx == -1) {
            throw IllegalArgumentException("No JSON object found in output: '$rawOutput'")
        }

        val endIdx = cleaned.lastIndexOf('}')
        if (endIdx < startIdx) {
            throw IllegalArgumentException("Unterminated JSON object in output: '$rawOutput'")
        }

        val prefix = cleaned.substring(0, startIdx).trim()
        val suffix = cleaned.substring(endIdx + 1).trim()

        val prefixLower = prefix.lowercase()
        val suffixLower = suffix.lowercase()

        for (indicator in CHATTER_INDICATORS) {
            if (prefixLower.contains(indicator) || suffixLower.contains(indicator)) {
                throw IllegalArgumentException("Conversational chatter detected in model response: '$cleaned'")
            }
        }

        // Reject if prefix or suffix contains words
        if (prefix.any { it.isLetter() }) {
            throw IllegalArgumentException("Conversational prefix text rejected: '$prefix'")
        }
        if (suffix.any { it.isLetter() }) {
            throw IllegalArgumentException("Conversational suffix text rejected: '$suffix'")
        }

        val candidate = cleaned.substring(startIdx, endIdx + 1)

        // Validate JSON object structure
        val element = Json.parseToJsonElement(candidate)
        element.jsonObject // throws if not a JSON object

        return candidate
    }

    /**
     * Parses either a `{"steps": [...]}` MultiStepPlan or a single ActionCommand JSON.
     */
    fun parsePlanOrSingleAction(rawOutput: String): PlanOrAction {
        val jsonStr = extractActionJson(rawOutput)

        // 1. Try MultiStepPlan
        val plan = runCatching { ActionJson.planFromJson(jsonStr) }.getOrNull()
        if (plan != null) {
            return PlanOrAction.Plan(plan)
        }

        // 2. Try single ActionCommand
        val single = runCatching { ActionJson.fromJson(jsonStr) }.getOrNull()
        if (single != null) {
            return PlanOrAction.Action(single)
        }

        throw IllegalArgumentException("JSON is neither a valid MultiStepPlan nor ActionCommand: '$jsonStr'")
    }
}
