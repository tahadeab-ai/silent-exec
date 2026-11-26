package com.silentexec.android.core

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

sealed class SafetyDecision {
    object AllowSilent : SafetyDecision()
    data class RequireConfirm(val reason: String) : SafetyDecision()
    data class Block(val reason: String) : SafetyDecision()
}

object SafetyGate {
    const val CONFIDENCE_THRESHOLD: Double = 0.65

    private val SAFE_TIER1_NAVIGATION_READ_ACTIONS = setOf(
        ActionType.OPEN_URL,
        ActionType.OPEN_ANDROID_APP,
        ActionType.UI_CLICK,
        ActionType.UI_TYPE,
        ActionType.UI_SCROLL,
        ActionType.UI_BACK,
        ActionType.UI_HOME,
        ActionType.REPLY,
        ActionType.DONE,
        ActionType.READ_FILE,
        ActionType.WEB_FETCH,
        ActionType.MEMORY_SAVE,
        ActionType.COPY_TO_CLIPBOARD,
        ActionType.BATCH_ACTIONS
    )

    private val SHELL_METACHARACTERS = listOf("&&", "||", ";", "|", "$(", "`")

    private val PROTECTED_SYSTEM_PREFIXES = listOf(
        "c:/windows",
        "c:/program files",
        "c:/program files (x86)",
        "/etc",
        "/system",
        "/root",
        "/bin",
        "/sbin",
        "/usr/bin",
        "/usr/sbin"
    )

    fun evaluate(cmd: ActionCommand): SafetyDecision {
        // 1. Validate confidence bounds
        if (cmd.confidence !in 0.0..1.0) {
            return SafetyDecision.Block(
                "Confidence score ${cmd.confidence} is invalid (must be between 0.0 and 1.0)"
            )
        }

        // 2. Check target for blocked patterns
        cmd.target?.let { target ->
            detectBlockedPattern(target, "target")?.let { reason ->
                return SafetyDecision.Block(reason)
            }
        }

        // 3. Check folder for blocked patterns
        cmd.folder?.let { folder ->
            detectBlockedPattern(folder, "folder")?.let { reason ->
                return SafetyDecision.Block(reason)
            }
        }

        // 4. Check args for blocked patterns
        cmd.args?.let { args ->
            detectArgsBlockedPatterns(args)?.let { reason ->
                return SafetyDecision.Block(reason)
            }
        }

        // 4.5. Validate nested actions for batch_actions
        cmd.actions?.let { subActions ->
            if (subActions.size > 4) {
                return SafetyDecision.Block(
                    "batch_actions exceeds maximum of 4 sub-actions (found ${subActions.size})"
                )
            }
            for ((idx, sub) in subActions.withIndex()) {
                when (val subDecision = evaluate(sub)) {
                    is SafetyDecision.Block -> return SafetyDecision.Block(
                        "Batch sub-action ${idx + 1} blocked: ${subDecision.reason}"
                    )
                    is SafetyDecision.RequireConfirm -> return SafetyDecision.RequireConfirm(
                        "Batch sub-action ${idx + 1} requires confirmation: ${subDecision.reason}"
                    )
                    is SafetyDecision.AllowSilent -> {}
                }
            }
        }

        // 5. Evaluate Safety Tier and Confidence
        return when (cmd.action.tier) {
            SafetyTier.TIER_2_CONFIRM -> {
                SafetyDecision.RequireConfirm(
                    "Action '${cmd.action.wireName}' is Tier 2 and requires explicit user confirmation"
                )
            }
            SafetyTier.TIER_1_SILENT -> {
                if (cmd.action in SAFE_TIER1_NAVIGATION_READ_ACTIONS && cmd.confidence >= 0.50) {
                    SafetyDecision.AllowSilent
                } else if (cmd.confidence < CONFIDENCE_THRESHOLD) {
                    SafetyDecision.RequireConfirm(
                        "Confidence %.2f is below the %.2f threshold; downgraded to require confirmation"
                            .format(cmd.confidence, CONFIDENCE_THRESHOLD)
                    )
                } else {
                    SafetyDecision.AllowSilent
                }
            }
        }
    }

    fun evaluatePlan(plan: MultiStepPlan): SafetyDecision {
        var requireConfirmReason: String? = null
        for ((index, step) in plan.steps.withIndex()) {
            when (val decision = evaluate(step)) {
                is SafetyDecision.Block -> {
                    return SafetyDecision.Block(
                        "Step ${index + 1} (${step.action.wireName}) blocked: ${decision.reason}"
                    )
                }
                is SafetyDecision.RequireConfirm -> {
                    if (requireConfirmReason == null) {
                        requireConfirmReason =
                            "Step ${index + 1} (${step.action.wireName}) requires confirmation: ${decision.reason}"
                    }
                }
                is SafetyDecision.AllowSilent -> {}
            }
        }
        return if (requireConfirmReason != null) {
            SafetyDecision.RequireConfirm(requireConfirmReason)
        } else {
            SafetyDecision.AllowSilent
        }
    }

    private fun detectBlockedPattern(value: String, fieldName: String): String? {
        // Path traversal check
        if (value.contains("..")) {
            return "Path traversal attempt detected in $fieldName: '$value'"
        }

        // Shell metacharacter injection check
        for (meta in SHELL_METACHARACTERS) {
            if (value.contains(meta)) {
                return "Shell injection metacharacter '$meta' detected in $fieldName: '$value'"
            }
        }

        // Protected system path check
        val normalized = value.replace('\\', '/').lowercase().trim()
        for (prefix in PROTECTED_SYSTEM_PREFIXES) {
            if (normalized == prefix ||
                normalized.startsWith("$prefix/") ||
                normalized.startsWith("$prefix\\")
            ) {
                return "Access to protected system path '$prefix' blocked in $fieldName: '$value'"
            }
        }

        return null
    }

    private fun detectArgsBlockedPatterns(element: JsonElement): String? {
        when (element) {
            is JsonPrimitive -> {
                if (element.isString) {
                    detectBlockedPattern(element.content, "args")?.let { return it }
                }
            }
            is JsonObject -> {
                for ((key, value) in element) {
                    detectBlockedPattern(key, "args.key")?.let { return it }
                    detectArgsBlockedPatterns(value)?.let { return it }
                }
            }
            else -> {}
        }
        return null
    }
}
