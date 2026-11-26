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
        var lastScreenChanged: Boolean? = null

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
            val prompt = buildObservationPrompt(cleanGoal, beforeObs, stepHistory, lastScreenChanged)
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
                lastScreenChanged = false
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
            val changed = afterObs.signatureHash != beforeObs.signatureHash
            lastScreenChanged = changed

            stepHistory.add(
                "Step $step: ${cmd.action.wireName}(${cmd.target ?: ""}) -> ok (screenChanged=$changed)"
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
        val cmd = engine.inferActionCommand(userQuery = prompt, maxTokens = 192).getOrThrow()
        return ActionJson.toJson(cmd)
    }

    private fun buildObservationPrompt(
        goal: String,
        obs: ScreenObservation,
        history: List<String>,
        previousScreenChanged: Boolean?
    ): String {
        val pkgLine = if (obs.packageName.isNotBlank()) "Foreground app: ${obs.packageName}" else "Foreground app: unknown"
        val changeLine = when (previousScreenChanged) {
            true -> "Previous action changed the screen."
            false -> "WARNING: Previous action did NOT change the screen. Try another element, scroll, or finish."
            null -> ""
        }
        val historySection = if (history.isEmpty()) "Completed steps: none" else "Completed steps:\n${history.joinToString("\n")}"
        val screenSection = if (obs.elements.isEmpty()) {
            "Current screen elements: none visible"
        } else {
            "Current screen elements:\n${obs.elements.joinToString("\n")}"
        }
        return listOf(
            "Goal: $goal",
            pkgLine,
            changeLine,
            historySection,
            screenSection,
            "Return ONLY one valid ActionCommand JSON or call execute_device_action."
        ).filter { it.isNotBlank() }.joinToString("\n\n")
    }

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
