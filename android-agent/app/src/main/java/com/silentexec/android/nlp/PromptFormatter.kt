package com.silentexec.android.nlp

import com.silentexec.android.core.ActionCommand
import com.silentexec.android.core.ActionJson
import com.silentexec.android.core.ActionType
import com.silentexec.android.core.MemoryItem
import com.silentexec.android.core.MemoryStore
import com.silentexec.android.core.MultiStepPlan
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.net.URLEncoder

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
        "You are SilentExec, a step-by-step ReAct OS execution agent. " +
        "Emit ONLY valid ActionCommand or MultiStepPlan JSON matching the strict schema (no conversational text). " +
        "Always reason briefly in \"thought\" (1 short sentence) about what is currently on screen and what SINGLE next step advances the user's goal. " +
        "Never cram follow-up actions (like open first video or click like) into a search query. " +
        "Use numbered screen elements (#0, #1, ...) from the live pruned UI tree for ui_click and ui_type. " +
        "Emit action \"done\" ONLY when the entire user goal is genuinely complete."

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
     * - Relevant user preferences from MemoryStore
     * - The live pruned screen elements
     * - Step history of completed actions
     */
    fun buildScreenAwarePrompt(
        userGoal: String,
        prunedUiTree: List<String>,
        stepHistory: List<String>,
        memories: List<MemoryItem> = MemoryStore.getRelevantMemories(userGoal, limit = 4)
    ): String {
        val memorySection = if (memories.isNotEmpty()) {
            "User Preferences & Context:\n" + memories.joinToString("\n") { "- ${it.key}: ${it.content}" }
        } else {
            ""
        }

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

        val sections = listOf(
            "Goal: ${userGoal.trim()}",
            memorySection,
            historySection,
            screenSection,
            "Think step-by-step in \"thought\", then emit the SINGLE next ActionCommand JSON using #index for screen elements, or {\"thought\":\"Goal complete.\",\"action\":\"done\",\"platform\":\"android\",\"confidence\":1.0} ONLY when the entire goal is genuinely complete."
        ).filter { it.isNotBlank() }

        val userQuery = sections.joinToString("\n\n")
        return buildChatMLPrompt(DEFAULT_SYSTEM_PROMPT, userQuery)
    }

    /**
     * Builds a 1-retry self-healing prompt feeding the screen UI tree and correction hint back.
     */
    fun buildSelfHealingPrompt(
        userGoal: String,
        prunedUiTree: List<String>,
        stepHistory: List<String>,
        malformedOutput: String,
        memories: List<MemoryItem> = MemoryStore.getRelevantMemories(userGoal, limit = 4)
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

Correction: Your previous response was not valid ActionCommand JSON:
"$malformedOutput"
Fix it and emit ONLY valid ActionCommand JSON adhering to schema, e.g.:
{"thought":"Tap the search button on screen","action":"ui_click","target":"#0","platform":"android","confidence":0.9}
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

        val candidate = sanitizeDuplicateJsonKeys(cleaned.substring(startIdx, endIdx + 1))

        // Validate JSON object structure
        val element = Json.parseToJsonElement(candidate)
        element.jsonObject // throws if not a JSON object

        return candidate
    }

    /**
     * Deduplicates repeated keys within any JSON object (e.g. small edge models emitting
     * `"platform"` twice in the same object), preserving the last occurrence.
     */
    fun sanitizeDuplicateJsonKeys(jsonStr: String): String {
        return runCatching { JsonKeyDeduplicator(jsonStr).sanitizeValue() }.getOrElse { jsonStr }
    }

    private class JsonKeyDeduplicator(private val jsonStr: String) {
        private val len = jsonStr.length
        private var pos = 0

        private fun skipWs() {
            while (pos < len && jsonStr[pos].isWhitespace()) pos++
        }

        private fun readString(): String {
            val start = pos
            pos++ // opening quote
            while (pos < len) {
                val c = jsonStr[pos]
                if (c == '\\') {
                    pos += 2
                } else if (c == '"') {
                    pos++
                    return jsonStr.substring(start, pos)
                } else {
                    pos++
                }
            }
            return jsonStr.substring(start)
        }

        private fun unescapeJsonKey(quoted: String): String {
            if (quoted.length < 2) return quoted
            return quoted.substring(1, quoted.length - 1)
        }

        fun sanitizeValue(): String {
            skipWs()
            if (pos >= len) return ""
            return when (jsonStr[pos]) {
                '{' -> sanitizeObject()
                '[' -> sanitizeArray()
                '"' -> readString()
                else -> {
                    val start = pos
                    while (pos < len && jsonStr[pos] !in charArrayOf(',', '}', ']', ' ', '\t', '\r', '\n')) {
                        pos++
                    }
                    jsonStr.substring(start, pos)
                }
            }
        }

        private fun sanitizeObject(): String {
            pos++ // '{'
            skipWs()
            if (pos < len && jsonStr[pos] == '}') {
                pos++
                return "{}"
            }
            val entries = LinkedHashMap<String, Pair<String, String>>()
            while (pos < len) {
                skipWs()
                if (pos >= len || jsonStr[pos] == '}') {
                    if (pos < len) pos++
                    break
                }
                if (jsonStr[pos] != '"') {
                    return jsonStr
                }
                val rawKey = readString()
                val keyName = unescapeJsonKey(rawKey)
                skipWs()
                if (pos < len && jsonStr[pos] == ':') pos++
                val value = sanitizeValue()
                entries[keyName] = rawKey to value
                skipWs()
                if (pos < len && jsonStr[pos] == ',') {
                    pos++
                } else if (pos < len && jsonStr[pos] == '}') {
                    pos++
                    break
                }
            }
            return entries.values.joinToString(prefix = "{", postfix = "}", separator = ",") { (k, v) ->
                "$k:$v"
            }
        }

        private fun sanitizeArray(): String {
            pos++ // '['
            skipWs()
            if (pos < len && jsonStr[pos] == ']') {
                pos++
                return "[]"
            }
            val items = mutableListOf<String>()
            while (pos < len) {
                skipWs()
                if (pos >= len || jsonStr[pos] == ']') {
                    if (pos < len) pos++
                    break
                }
                items.add(sanitizeValue())
                skipWs()
                if (pos < len && jsonStr[pos] == ',') {
                    pos++
                } else if (pos < len && jsonStr[pos] == ']') {
                    pos++
                    break
                }
            }
            return items.joinToString(prefix = "[", postfix = "]", separator = ",")
        }
    }

    /**
     * Normalizes `open_url` targets: if `target` is a plain search phrase (e.g. "ابحث عن nlp")
     * rather than a URL or domain, converts it to a YouTube or Google search URL.
     */
    fun normalizeOpenUrlTarget(target: String?, thought: String?): String? {
        val trimmed = target?.trim() ?: return null
        if (trimmed.isEmpty()) return trimmed
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return trimmed
        }
        val domainRegex = Regex("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$")
        if (!trimmed.contains(' ') && domainRegex.matches(trimmed)) {
            return "https://$trimmed"
        }
        val cleanedQuery = trimmed.replace(
            Regex("^(?:ابحث عن|بحث عن|ابحث|search for|search)\\s+", RegexOption.IGNORE_CASE),
            ""
        ).trim().ifEmpty { trimmed }
        val encoded = URLEncoder.encode(cleanedQuery, "UTF-8")
        val thoughtLower = thought?.lowercase().orEmpty()
        return if (thoughtLower.contains("youtube") || thoughtLower.contains("يوتيوب")) {
            "https://www.youtube.com/results?search_query=$encoded"
        } else {
            "https://www.google.com/search?q=$encoded"
        }
    }

    fun normalizeCommand(cmd: ActionCommand): ActionCommand {
        if (cmd.action != ActionType.OPEN_URL) return cmd
        val normalized = normalizeOpenUrlTarget(cmd.target, cmd.thought)
        return if (normalized != cmd.target) cmd.copy(target = normalized) else cmd
    }

    /**
     * Parses either a `{"steps": [...]}` MultiStepPlan or a single ActionCommand JSON
     * using the lenient RuntimeActionJson parser so extra LLM keys never crash execution.
     */
    fun parsePlanOrSingleAction(rawOutput: String): PlanOrAction {
        val jsonStr = extractActionJson(rawOutput)

        // 1. Try MultiStepPlan
        val plan = runCatching { ActionJson.planFromLlmJson(jsonStr) }.getOrNull()
        if (plan != null) {
            val normalizedPlan = MultiStepPlan(plan.steps.map { normalizeCommand(it) })
            return PlanOrAction.Plan(normalizedPlan)
        }

        // 2. Try single ActionCommand
        val single = runCatching { ActionJson.fromLlmJson(jsonStr) }.getOrNull()
        if (single != null) {
            return PlanOrAction.Action(normalizeCommand(single))
        }

        throw IllegalArgumentException("JSON is neither a valid MultiStepPlan nor ActionCommand: '$jsonStr'")
    }
}
