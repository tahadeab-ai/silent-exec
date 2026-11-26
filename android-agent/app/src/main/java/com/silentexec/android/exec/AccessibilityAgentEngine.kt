package com.silentexec.android.exec

import com.silentexec.android.core.ActionCommand
import com.silentexec.android.core.ActionJson
import com.silentexec.android.core.ActionType
import com.silentexec.android.core.AppSettings
import com.silentexec.android.core.ExecutionMode
import com.silentexec.android.core.FastRuleRouter
import com.silentexec.android.core.InferenceSource
import com.silentexec.android.core.Platform
import com.silentexec.android.core.SafetyDecision
import com.silentexec.android.core.SafetyGate
import com.silentexec.android.net.LanBridgeClient
import com.silentexec.android.nlp.CloudApiClient
import com.silentexec.android.nlp.OnDeviceLlamaEngine
import com.silentexec.android.nlp.PromptFormatter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class ScreenObservation(
    val packageName: String,
    val elements: List<String>,
    val signatureHash: String = "$packageName|${elements.joinToString("\n")}".hashCode().toString()
)

fun interface ScreenObserver {
    fun observe(): ScreenObservation
}

data class AgentActionResult(
    val success: Boolean,
    val message: String
)

fun interface ActionPerformer {
    fun perform(command: ActionCommand, mode: ExecutionMode): AgentActionResult
}

sealed interface AgentTraceEvent {
    data class Started(val goal: String) : AgentTraceEvent
    data class Observed(
        val step: Int,
        val packageName: String,
        val elementCount: Int,
        val previousScreenChanged: Boolean?
    ) : AgentTraceEvent

    data class Reasoned(
        val step: Int,
        val thought: String,
        val command: ActionCommand
    ) : AgentTraceEvent

    data class ConfirmRequired(
        val step: Int,
        val command: ActionCommand,
        val reason: String,
        val respond: (Boolean) -> Unit
    ) : AgentTraceEvent

    data class Acted(
        val step: Int,
        val command: ActionCommand,
        val result: AgentActionResult
    ) : AgentTraceEvent

    data class Finished(val summary: String, val stepsTaken: Int) : AgentTraceEvent
    data class Failed(val message: String) : AgentTraceEvent
    data object Cancelled : AgentTraceEvent
}

/**
 * OpenPhoneUse-inspired Accessibility Screen-Control Loop:
 * Observe (Accessibility Tree) -> Reason (Tool Calling / JSON) -> Safety Confirmation ->
 * Act -> Verify Screen Changed -> Repeat until Done.
 */
class AccessibilityAgentEngine(
    private val settings: AppSettings,
    private val observer: ScreenObserver = defaultObserver(),
    private val performer: ActionPerformer,
    private val llamaEngine: OnDeviceLlamaEngine? = null,
    private val bridgeClient: LanBridgeClient? = null,
    private val cloudApiClient: CloudApiClient? = null,
    private val customDecider: ((prompt: String) -> String)? = null,
    private val maxSteps: Int = 12,
    private val settleDelayMs: Long = 550L
) {
    @Volatile
    private var isCancelled: Boolean = false

    fun cancel() {
        isCancelled = true
    }

    fun run(
        goal: String,
        onEvent: (AgentTraceEvent) -> Unit
    ) {
        isCancelled = false
        val cleanGoal = goal.trim()
        if (cleanGoal.isEmpty()) return

        onEvent(AgentTraceEvent.Started(cleanGoal))

        // Fast deterministic 1-step router (back, home, volume, simple app launch)
        if (settings.fastRouterEnabled) {
            val fast = FastRuleRouter.route(cleanGoal)
            if (fast != null) {
                onEvent(AgentTraceEvent.Reasoned(1, "Fast deterministic route", fast))
                if (!confirmIfSensitive(1, fast, onEvent)) {
                    onEvent(AgentTraceEvent.Cancelled)
                    return
                }
                val res = performer.perform(fast, settings.executionMode)
                onEvent(AgentTraceEvent.Acted(1, fast, res))
                onEvent(AgentTraceEvent.Finished(res.message, 1))
                return
            }
        }

        val stepHistory = mutableListOf<String>()
        val actionHistory = mutableListOf<StepRecord>()
        val subGoals = parseSubGoals(cleanGoal)
        var completedSubGoals = 0
        var lastScreenChanged: Boolean? = null
        var recoveryDirective: String? = null
        var consecutiveRecoveryTurns = 0

        for (step in 1..maxSteps) {
            if (isCancelled) {
                onEvent(AgentTraceEvent.Cancelled)
                return
            }

            // 1. Observe
            val beforeObs = observer.observe()
            onEvent(
                AgentTraceEvent.Observed(
                    step = step,
                    packageName = beforeObs.packageName,
                    elementCount = beforeObs.elements.size,
                    previousScreenChanged = lastScreenChanged
                )
            )

            // 2. Reason
            val prompt = buildObservationPrompt(
                goal = cleanGoal,
                obs = beforeObs,
                history = stepHistory,
                previousScreenChanged = lastScreenChanged,
                subGoals = subGoals,
                completedSubGoals = completedSubGoals,
                recoveryDirective = recoveryDirective
            )
            val rawOutput = try {
                decideNextJson(prompt)
            } catch (t: Throwable) {
                onEvent(AgentTraceEvent.Failed(t.message ?: "Inference failed"))
                return
            }

            val cmd = try {
                val jsonStr = PromptFormatter.extractActionJson(rawOutput)
                PromptFormatter.normalizeCommand(ActionJson.fromLlmJson(jsonStr))
            } catch (t: Throwable) {
                onEvent(AgentTraceEvent.Failed("Invalid action JSON: ${t.message}"))
                return
            }

            val thought = cmd.thought?.takeIf { it.isNotBlank() } ?: "Step $step: ${cmd.action.wireName}"
            onEvent(AgentTraceEvent.Reasoned(step, thought, cmd))

            // Done or Reply terminates loop cleanly
            if (cmd.action == ActionType.DONE) {
                val summary = cmd.target?.takeIf { it.isNotBlank() } ?: thought
                onEvent(AgentTraceEvent.Finished(summary, step))
                return
            }
            if (cmd.action == ActionType.REPLY) {
                val res = performer.perform(cmd, settings.executionMode)
                onEvent(AgentTraceEvent.Acted(step, cmd, res))
                onEvent(AgentTraceEvent.Finished(cmd.target ?: res.message, step))
                return
            }

            // Hard Repeat-Action Ban
            val normTarget = normalizeTarget(cmd.target)
            val isNavAction = cmd.action in NAV_REPEAT_GUARD_ACTIONS
            val alreadyInsideTarget = isNavAction && packageMatchesTarget(beforeObs.packageName, cmd)
            val sameAsPrevUnchanged = lastScreenChanged == false &&
                actionHistory.lastOrNull()?.let { it.action == cmd.action && it.normalizedTarget == normTarget } == true
            val repeatedNavInSamePkg = isNavAction && actionHistory.any {
                it.action == cmd.action && it.normalizedTarget == normTarget && it.currentPackage == beforeObs.packageName
            }

            if (alreadyInsideTarget || sameAsPrevUnchanged || repeatedNavInSamePkg) {
                consecutiveRecoveryTurns++
                if (consecutiveRecoveryTurns >= 2) {
                    val pkgLabel = beforeObs.packageName.ifBlank { "current screen" }
                    onEvent(
                        AgentTraceEvent.Finished(
                            "Stopped cleanly: screen remained unchanged after repeated recovery attempts in $pkgLabel.",
                            step
                        )
                    )
                    return
                }
                val pkgDisplay = beforeObs.packageName.ifBlank { "current app" }
                recoveryDirective =
                    "BLOCKED REPEAT: You are ALREADY inside $pkgDisplay. Do NOT call open_url/open_android_app again. " +
                    "Pick a visible element #index from the screen list below to click/type, or call done/reply."
                stepHistory.add("Step $step: BLOCKED repeat ${cmd.action.wireName}(${cmd.target ?: ""}) inside $pkgDisplay")
                lastScreenChanged = false
                continue
            }

            // 3. Safety Confirmation (only before delete/send/pay or Tier-2 in interactive mode)
            if (!confirmIfSensitive(step, cmd, onEvent)) {
                onEvent(AgentTraceEvent.Cancelled)
                return
            }

            // 4. Act
            val result = performer.perform(cmd, settings.executionMode)
            onEvent(AgentTraceEvent.Acted(step, cmd, result))

            if (!result.success) {
                stepHistory.add("Step $step: ${cmd.action.wireName}(${cmd.target ?: ""}) -> FAILED: ${result.message}")
                actionHistory.add(StepRecord(step, cmd.action, normTarget, beforeObs.packageName, false))
                lastScreenChanged = false
                if (recoveryDirective != null) {
                    consecutiveRecoveryTurns++
                    if (consecutiveRecoveryTurns >= 2) {
                        onEvent(
                            AgentTraceEvent.Finished(
                                "Stopped cleanly: recovery action failed (${result.message}).",
                                step
                            )
                        )
                        return
                    }
                }
                continue
            }

            // 5. Verify Screen Changed
            if (settleDelayMs > 0 && cmd.action in UI_TRANSITION_ACTIONS) {
                try {
                    Thread.sleep(settleDelayMs)
                } catch (_: InterruptedException) {
                    onEvent(AgentTraceEvent.Cancelled)
                    return
                }
            }
            val afterObs = observer.observe()
            val changed = if (cmd.action in NON_UI_CONTENT_ACTIONS) {
                true
            } else {
                afterObs.signatureHash != beforeObs.signatureHash
            }
            lastScreenChanged = changed
            actionHistory.add(StepRecord(step, cmd.action, normTarget, beforeObs.packageName, changed))

            if (changed) {
                recoveryDirective = null
                consecutiveRecoveryTurns = 0
                if (completedSubGoals < subGoals.size) {
                    completedSubGoals++
                }
            } else if (recoveryDirective != null) {
                consecutiveRecoveryTurns++
                if (consecutiveRecoveryTurns >= 2) {
                    val pkgLabel = afterObs.packageName.ifBlank { "current screen" }
                    onEvent(
                        AgentTraceEvent.Finished(
                            "Stopped cleanly: screen remained unchanged for 2 consecutive recovery turns in $pkgLabel.",
                            step
                        )
                    )
                    return
                }
            }

            val detailSuffix = if (cmd.action in NON_UI_CONTENT_ACTIONS && result.message.isNotBlank()) {
                " | output: ${result.message.take(1500)}"
            } else {
                ""
            }
            stepHistory.add(
                "Step $step: ${cmd.action.wireName}(${cmd.target ?: ""}) -> ok (screenChanged=$changed)$detailSuffix"
            )
        }

        onEvent(AgentTraceEvent.Finished("Completed $maxSteps steps", maxSteps))
    }

    private fun confirmIfSensitive(
        step: Int,
        cmd: ActionCommand,
        onEvent: (AgentTraceEvent) -> Unit
    ): Boolean {
        val gateDecision = SafetyGate.evaluate(cmd)
        val sensitiveReason = when {
            gateDecision is SafetyDecision.Block -> {
                onEvent(AgentTraceEvent.Failed(gateDecision.reason))
                return false
            }
            gateDecision is SafetyDecision.RequireConfirm -> gateDecision.reason
            isSensitiveUiTarget(cmd) -> "Confirm sensitive action: ${cmd.action.wireName} (${cmd.target.orEmpty()})"
            else -> null
        } ?: return true

        if (settings.executionMode == ExecutionMode.DRY_RUN) {
            return true
        }

        val latch = CountDownLatch(1)
        val approved = AtomicBoolean(false)
        onEvent(
            AgentTraceEvent.ConfirmRequired(
                step = step,
                command = cmd,
                reason = sensitiveReason,
                respond = { decision ->
                    approved.set(decision)
                    latch.countDown()
                }
            )
        )
        latch.await(120, TimeUnit.SECONDS)
        return approved.get() && !isCancelled
    }

    private fun decideNextJson(prompt: String): String {
        customDecider?.let { return it(prompt) }

        if (settings.inferenceSource == InferenceSource.OPENAI_COMPAT) {
            val client = cloudApiClient ?: CloudApiClient(
                baseUrl = settings.openAiApiBase,
                model = settings.openAiApiModel,
                apiKey = settings.openAiApiKey
            )
            return client.generateCompletion(
                systemPrompt = PromptFormatter.DEFAULT_SYSTEM_PROMPT,
                userPrompt = prompt,
                useToolCalling = true
            ).getOrThrow()
        }

        if (settings.inferenceSource == InferenceSource.LAN_BRIDGE) {
            val client = bridgeClient ?: LanBridgeClient()
            val res = client.executeRemote(
                host = settings.bridgeHost,
                port = settings.bridgePort,
                token = settings.bridgeToken,
                prompt = prompt,
                dryRun = settings.executionMode == ExecutionMode.DRY_RUN
            )
            return ActionJson.toJson(
                ActionCommand(
                    action = ActionType.REPLY,
                    target = res.getOrThrow(),
                    platform = Platform.ANDROID,
                    confidence = 1.0
                )
            )
        }

        val engine = llamaEngine
            ?: throw IllegalStateException("Local GGUF engine not initialized")
        val cmd = engine.inferActionCommand(userQuery = prompt, maxTokens = 384).getOrThrow()
        return ActionJson.toJson(cmd)
    }

    private fun buildObservationPrompt(
        goal: String,
        obs: ScreenObservation,
        history: List<String>,
        previousScreenChanged: Boolean?,
        subGoals: List<String> = emptyList(),
        completedSubGoals: Int = 0,
        recoveryDirective: String? = null
    ): String {
        val pkgLine = if (obs.packageName.isNotBlank()) "Foreground app: ${obs.packageName}" else "Foreground app: unknown"
        val checklistSection = if (subGoals.size > 1) {
            val lines = subGoals.mapIndexed { idx, item ->
                val tag = when {
                    idx < completedSubGoals -> " [DONE]"
                    idx == completedSubGoals -> " [CURRENT]"
                    else -> ""
                }
                "${idx + 1}. $item$tag"
            }
            "Progress Checklist:\n${lines.joinToString("\n")}"
        } else {
            ""
        }
        val changeLine = when (previousScreenChanged) {
            true -> "Previous action changed the screen."
            false -> "WARNING: Previous action did NOT change the screen. Try another element, scroll, or finish."
            null -> ""
        }
        val directiveLine = recoveryDirective.orEmpty()
        val historySection = if (history.isEmpty()) "Completed steps: none" else "Completed steps:\n${history.joinToString("\n")}"
        val screenSection = if (obs.elements.isEmpty()) {
            "Current screen elements: none visible"
        } else {
            "Current screen elements:\n${obs.elements.joinToString("\n")}"
        }
        return listOf(
            "Goal: $goal",
            pkgLine,
            checklistSection,
            changeLine,
            directiveLine,
            historySection,
            screenSection,
            "Return ONLY one valid ActionCommand JSON or call execute_device_action."
        ).filter { it.isNotBlank() }.joinToString("\n\n")
    }

    private data class StepRecord(
        val step: Int,
        val action: ActionType,
        val normalizedTarget: String,
        val currentPackage: String,
        val screenChanged: Boolean?
    )

    companion object {
        private val UI_TRANSITION_ACTIONS = setOf(
            ActionType.UI_CLICK,
            ActionType.UI_TYPE,
            ActionType.UI_SCROLL,
            ActionType.OPEN_ANDROID_APP,
            ActionType.OPEN_URL,
            ActionType.UI_BACK,
            ActionType.UI_HOME
        )

        private val NAV_REPEAT_GUARD_ACTIONS = setOf(
            ActionType.OPEN_URL,
            ActionType.OPEN_ANDROID_APP,
            ActionType.LAUNCH_APP
        )

        private val NON_UI_CONTENT_ACTIONS = setOf(
            ActionType.READ_FILE,
            ActionType.WEB_FETCH,
            ActionType.MEMORY_SAVE,
            ActionType.COPY_TO_CLIPBOARD
        )

        private val APP_PACKAGE_HINTS = mapOf(
            "youtube" to "youtube",
            "يوتيوب" to "youtube",
            "whatsapp" to "whatsapp",
            "واتساب" to "whatsapp",
            "chrome" to "chrome",
            "كروم" to "chrome",
            "telegram" to "telegram",
            "تليجرام" to "telegram",
            "instagram" to "instagram",
            "انستقرام" to "instagram",
            "settings" to "settings",
            "الاعدادات" to "settings",
            "الإعدادات" to "settings"
        )

        fun normalizeTarget(target: String?): String =
            target?.trim()?.lowercase()?.replace(Regex("\\s+"), " ") ?: ""

        fun packageMatchesTarget(currentPackage: String, cmd: ActionCommand): Boolean {
            if (currentPackage.isBlank()) return false
            val pkgLower = currentPackage.lowercase()
            val target = normalizeTarget(cmd.target)
            if (target.isEmpty()) return false
            if (pkgLower == target) return true
            for ((keyword, pkgHint) in APP_PACKAGE_HINTS) {
                if (target.contains(keyword) && pkgLower.contains(pkgHint)) {
                    return true
                }
            }
            if (cmd.action == ActionType.OPEN_URL && (pkgLower.contains("chrome") || pkgLower.contains("browser"))) {
                return true
            }
            return target.length >= 4 && pkgLower.contains(target)
        }

        fun parseSubGoals(goal: String): List<String> {
            val parts = goal
                .split(Regex("(?:\\s+(?:ثم|بعدين|بعدها|and|then)\\s+|\\s+و\\s+|[,،])", RegexOption.IGNORE_CASE))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            return if (parts.isEmpty()) listOf(goal.trim()) else parts.take(6)
        }

        private val SENSITIVE_KEYWORDS = listOf(
            "delete", "remove", "erase", "send", "pay", "purchase", "checkout", "transfer",
            "حذف", "مسح", "إرسال", "ارسال", "دفع", "شراء", "تحويل"
        )

        fun isSensitiveUiTarget(cmd: ActionCommand): Boolean {
            if (cmd.action == ActionType.DELETE_FILE) return true
            if (cmd.action != ActionType.UI_CLICK) return false
            val target = cmd.target?.lowercase().orEmpty()
            return SENSITIVE_KEYWORDS.any { target.contains(it) }
        }

        fun defaultObserver(): ScreenObserver = ScreenObserver {
            val svc = SilentExecAccessibilityService.get()
            if (svc == null) {
                ScreenObservation(packageName = "", elements = emptyList())
            } else {
                ScreenObservation(
                    packageName = svc.activePackageName(),
                    elements = svc.extractPrunedUiStrings()
                )
            }
        }
    }
}
