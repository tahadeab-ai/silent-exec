package com.silentexec.android.exec

import com.silentexec.android.core.ActionCommand
import com.silentexec.android.core.ActionType
import com.silentexec.android.core.MemoryStore

enum class ActionOutcome {
    SUCCESS,
    SILENT_FAILURE,
    ERROR
}

data class ReflectionResult(
    val outcome: ActionOutcome,
    val correctiveHint: String?,
    val consecutiveSilentFailures: Int,
    val shouldAbortOrEscalate: Boolean
)

/**
 * Deterministic StateHashReflector (Mobile-Agent MIT pattern).
 * Compares pre-action and post-action screen signatures + package names;
 * emits SILENT_FAILURE with a corrective hint when UI is unchanged and flags abort/escalate after 2 consecutive silent failures.
 */
class StateHashReflector {
    private var beforeSignature: String = ""
    private var beforePackage: String = ""
    var consecutiveSilentFailures: Int = 0
        private set

    fun recordBefore(signature: String, packageName: String) {
        beforeSignature = signature
        beforePackage = packageName
    }

    fun evaluateAfter(
        mutatesUi: Boolean,
        afterSignature: String,
        afterPackage: String
    ): ReflectionResult {
        if (!mutatesUi) {
            consecutiveSilentFailures = 0
            return ReflectionResult(ActionOutcome.SUCCESS, null, 0, false)
        }

        val unchanged = beforeSignature.isNotEmpty() &&
            beforeSignature == afterSignature &&
            beforePackage == afterPackage

        return if (unchanged) {
            consecutiveSilentFailures++
            val escalate = consecutiveSilentFailures >= 2
            ReflectionResult(
                outcome = ActionOutcome.SILENT_FAILURE,
                correctiveHint = "Previous tap had no UI change; try alternate index or scroll",
                consecutiveSilentFailures = consecutiveSilentFailures,
                shouldAbortOrEscalate = escalate
            )
        } else {
            consecutiveSilentFailures = 0
            ReflectionResult(ActionOutcome.SUCCESS, null, 0, false)
        }
    }

    companion object {
        fun isUiMutatingAction(action: ActionType): Boolean {
            return action in setOf(
                ActionType.UI_CLICK,
                ActionType.UI_TYPE,
                ActionType.UI_SCROLL,
                ActionType.UI_BACK,
                ActionType.UI_HOME,
                ActionType.BATCH_ACTIONS
            )
        }
    }
}

/**
 * Lightweight session Notetaker (Mobile-Agent MIT pattern).
 * Populated ONLY on verified SUCCESS steps to carry transient cross-app facts (prices, titles, OTPs, IDs),
 * plus persistence of learned app shortcuts via MemoryStore.
 */
class Notetaker {
    private val sessionFacts = LinkedHashMap<String, String>()

    @Synchronized
    fun recordOnSuccess(outcome: ActionOutcome, key: String, value: String) {
        if (outcome != ActionOutcome.SUCCESS) return
        val k = key.trim()
        val v = value.trim()
        if (k.isNotEmpty() && v.isNotEmpty()) {
            sessionFacts[k] = v
        }
    }

    @Synchronized
    fun getFact(key: String): String? = sessionFacts[key.trim()]

    @Synchronized
    fun allFacts(): Map<String, String> = sessionFacts.toMap()

    @Synchronized
    fun formatForPrompt(): String {
        if (sessionFacts.isEmpty()) return ""
        return "Session Notes:\n" + sessionFacts.entries.joinToString("\n") { (k, v) -> "- $k: $v" }
    }

    fun saveLearnedAppShortcut(label: String, packageName: String) {
        if (label.isBlank() || packageName.isBlank()) return
        MemoryStore.saveMemory(
            key = "app_shortcut:${label.trim().lowercase()}",
            content = packageName.trim(),
            category = "app_shortcut"
        )
    }
}

data class TakeOverRequest(
    val stepIndex: Int,
    val reason: String,
    val isSecureScreen: Boolean,
    val sensitiveAction: ActionCommand?
)

/**
 * Deterministic Take_over Gate (Open-AutoGLM Apache-2.0 pattern).
 * Detects sensitive actions (payments, sending messages/emails, app installs, permission dialogs)
 * AND FLAG_SECURE / empty protected screens, pausing the loop for explicit user decision.
 */
object TakeOverGate {
    private val SENSITIVE_KEYWORDS = listOf(
        "pay", "checkout", "purchase", "confirm order", "transfer", "send money",
        "دفع", "شراء", "تأكيد الطلب", "تحويل", "سداد",
        "install", "uninstall", "تثبيت", "إزالة التثبيت",
        "allow", "permission", "سماح", "أذونات"
    )

    fun evaluate(
        stepIndex: Int,
        cmd: ActionCommand?,
        phoneState: PhoneState?
    ): TakeOverRequest? {
        if (phoneState?.isSecureScreen == true) {
            return TakeOverRequest(
                stepIndex = stepIndex,
                reason = "Protected/FLAG_SECURE screen detected (${phoneState.packageName}); user take-over required",
                isSecureScreen = true,
                sensitiveAction = cmd
            )
        }

        if (cmd != null) {
            if (cmd.action == ActionType.SEND_MESSAGE || cmd.action == ActionType.SYSTEM_POWER) {
                return TakeOverRequest(
                    stepIndex = stepIndex,
                    reason = "Sensitive action '${cmd.action.wireName}' requires explicit user confirmation",
                    isSecureScreen = false,
                    sensitiveAction = cmd
                )
            }
            val combined = listOfNotNull(cmd.target, cmd.thought).joinToString(" ").lowercase()
            val matched = SENSITIVE_KEYWORDS.firstOrNull { combined.contains(it) }
            if (matched != null) {
                return TakeOverRequest(
                    stepIndex = stepIndex,
                    reason = "Sensitive action detected ('$matched'); user take-over required",
                    isSecureScreen = false,
                    sensitiveAction = cmd
                )
            }
        }

        return null
    }
}
