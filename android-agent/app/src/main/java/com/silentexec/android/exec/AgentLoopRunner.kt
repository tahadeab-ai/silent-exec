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
import com.silentexec.android.core.SafetyTier
import com.silentexec.android.net.LanBridgeClient
import com.silentexec.android.nlp.CloudApiClient
import com.silentexec.android.nlp.OnDeviceLlamaEngine
import com.silentexec.android.nlp.PlanOrAction
import com.silentexec.android.nlp.PromptFormatter
import java.util.Locale
import java.util.concurrent.CountDownLatch

sealed class AgentStepUpdate {
    data class Understanding(val goal: String) : AgentStepUpdate()
    data class FastRuleMatched(val command: ActionCommand) : AgentStepUpdate()
    data class PlanResolved(val totalSteps: Int, val steps: List<ActionCommand>) : AgentStepUpdate()
    data class StepStarted(val stepIndex: Int, val totalSteps: Int, val command: ActionCommand) : AgentStepUpdate()
    data class AwaitingConfirmation(
        val stepIndex: Int,
        val totalSteps: Int,
        val command: ActionCommand,
        val reason: String
    ) : AgentStepUpdate()
    data class StepCompleted(val stepIndex: Int, val totalSteps: Int, val details: String) : AgentStepUpdate()
    data class StepSkipped(val stepIndex: Int, val totalSteps: Int) : AgentStepUpdate()
    data class StepFailed(val stepIndex: Int, val totalSteps: Int, val error: String) : AgentStepUpdate()
    data class DryRunValidated(
        val stepIndex: Int,
        val totalSteps: Int,
        val command: ActionCommand,
        val note: String
    ) : AgentStepUpdate()
    data class AllCompleted(val totalSteps: Int) : AgentStepUpdate()
    data class Aborted(val reason: String) : AgentStepUpdate()
}

enum class StepDecision {
    RUN,
    SKIP,
    STOP
}

/**
 * Real Multi-Step Screen-Aware Agent Loop for SilentExec Android.
 * Implements Observe -> Think -> Act with:
 * - <15ms FastRuleRouter bypass for simple commands
 * - One-shot MultiStepPlan execution OR iterative ReAct loop (up to 6 steps)
 * - Full compliance with SILENT, INTERACTIVE, and DRY_RUN modes
 * - Real-time step updates emitted to UI and Floating Bubble
 * - Instant cancellation anytime via cancel()
 */
class AgentLoopRunner(
    private val executor: AndroidActionExecutor,
    private val settings: AppSettings,
    private val llamaEngine: OnDeviceLlamaEngine? = null,
    private val bridgeClient: LanBridgeClient? = null,
    private val cloudApiClient: CloudApiClient? = null,
    private val grammarText: String = "",
    private val stepDelayMs: Long = 600L,
    private val uiObserver: (() -> List<String>)? = null,
    private val decider: ((prompt: String) -> String)? = null
) {

    @Volatile
    private var isCancelled: Boolean = false

    fun cancel() {
        isCancelled = true
    }

    fun isExecutionCancelled(): Boolean = isCancelled

    fun run(
        userGoal: String,
        onUpdate: (AgentStepUpdate) -> Unit,
        onConfirmStep: ((step: ActionCommand, index: Int, total: Int, callback: (StepDecision) -> Unit) -> Unit)? = null
    ) {
        isCancelled = false
        val cleanGoal = userGoal.trim()
        if (cleanGoal.isEmpty()) return

        onUpdate(AgentStepUpdate.Understanding(cleanGoal))

        // 1. FastRuleRouter check (<15ms)
        if (settings.fastRouterEnabled) {
            val fastAction = FastRuleRouter.route(cleanGoal)
            if (fastAction != null) {
                onUpdate(AgentStepUpdate.FastRuleMatched(fastAction))
                executeSingleStep(fastAction, 1, 1, onUpdate, onConfirmStep)
                return
            }
        }

        // 2. Direct JSON check
        val directPlanOrAction = runCatching {
            PromptFormatter.parsePlanOrSingleAction(cleanGoal)
        }.getOrNull()

        if (directPlanOrAction != null) {
            when (directPlanOrAction) {
                is PlanOrAction.Plan -> {
                    executeMultiStepPlan(directPlanOrAction.multiStepPlan.steps, onUpdate, onConfirmStep)
                    return
                }
                is PlanOrAction.Action -> {
                    executeSingleStep(directPlanOrAction.actionCommand, 1, 1, onUpdate, onConfirmStep)
                    return
                }
            }
        }

        // 3. Multi-Step Screen-Aware ReAct Loop
        val stepHistory = mutableListOf<String>()
        val maxSteps = 6

        var currentStepIndex = 1
        var isLoopFinished = false

        while (currentStepIndex <= maxSteps && !isLoopFinished && !isCancelled) {
            val screenElements = uiObserver?.invoke()
                ?: (SilentExecAccessibilityService.get()?.extractPrunedUiStrings() ?: emptyList())

            val prompt = PromptFormatter.buildScreenAwarePrompt(cleanGoal, screenElements, stepHistory)

            val rawDecision = try {
                obtainDecision(prompt, cleanGoal)
            } catch (e: Exception) {
                onUpdate(AgentStepUpdate.StepFailed(currentStepIndex, maxSteps, "Decision failed: ${e.message}"))
                return
            }

            if (isCancelled) {
                onUpdate(AgentStepUpdate.Aborted("Cancelled by user"))
                return
            }

            val planOrAction = try {
                PromptFormatter.parsePlanOrSingleAction(rawDecision)
            } catch (e: Exception) {
                // If model output cannot be parsed, fallback to local heuristic
                val fallbackCmd = parseLocalIntentFallback(cleanGoal)
                PlanOrAction.Action(fallbackCmd)
            }

            when (planOrAction) {
                is PlanOrAction.Plan -> {
                    // Model emitted full multi-step plan
                    executeMultiStepPlan(planOrAction.multiStepPlan.steps, onUpdate, onConfirmStep)
                    return
                }
                is PlanOrAction.Action -> {
                    val cmd = planOrAction.actionCommand

                    if (cmd.action == ActionType.DONE) {
                        onUpdate(AgentStepUpdate.AllCompleted(currentStepIndex - 1))
                        isLoopFinished = true
                        break
                    }

                    val stepSuccess = executeStepInLoop(cmd, currentStepIndex, maxSteps, onUpdate, onConfirmStep)
                    if (!stepSuccess) {
                        return
                    }

                    stepHistory.add("Step $currentStepIndex: ${cmd.action.wireName}(${cmd.target ?: ""}) -> OK")
                    currentStepIndex++

                    if (stepDelayMs > 0 && currentStepIndex <= maxSteps && !isCancelled) {
                        try { Thread.sleep(stepDelayMs) } catch (_: InterruptedException) {}
                    }
                }
            }
        }

        if (isCancelled) {
            onUpdate(AgentStepUpdate.Aborted("Cancelled by user"))
        } else if (!isLoopFinished) {
            onUpdate(AgentStepUpdate.AllCompleted(currentStepIndex - 1))
        }
    }

    private fun executeMultiStepPlan(
        steps: List<ActionCommand>,
        onUpdate: (AgentStepUpdate) -> Unit,
        onConfirmStep: ((step: ActionCommand, index: Int, total: Int, callback: (StepDecision) -> Unit) -> Unit)?
    ) {
        val total = steps.size
        onUpdate(AgentStepUpdate.PlanResolved(total, steps))

        for ((index, cmd) in steps.withIndex()) {
            if (isCancelled) {
                onUpdate(AgentStepUpdate.Aborted("Cancelled by user"))
                return
            }

            val stepNum = index + 1

            if (cmd.action == ActionType.DONE) {
                onUpdate(AgentStepUpdate.StepCompleted(stepNum, total, "Task completed"))
                continue
            }

            val success = executeStepInLoop(cmd, stepNum, total, onUpdate, onConfirmStep)
            if (!success) {
                return
            }

            if (stepDelayMs > 0 && stepNum < total && !isCancelled) {
                try { Thread.sleep(stepDelayMs) } catch (_: InterruptedException) {}
            }
        }

        if (!isCancelled) {
            onUpdate(AgentStepUpdate.AllCompleted(total))
        }
    }

    private fun executeSingleStep(
        cmd: ActionCommand,
        stepIndex: Int,
        totalSteps: Int,
        onUpdate: (AgentStepUpdate) -> Unit,
        onConfirmStep: ((step: ActionCommand, index: Int, total: Int, callback: (StepDecision) -> Unit) -> Unit)?
    ) {
        val success = executeStepInLoop(cmd, stepIndex, totalSteps, onUpdate, onConfirmStep)
        if (success && !isCancelled) {
            onUpdate(AgentStepUpdate.AllCompleted(totalSteps))
        }
    }

    private fun executeStepInLoop(
        cmd: ActionCommand,
        stepIndex: Int,
        totalSteps: Int,
        onUpdate: (AgentStepUpdate) -> Unit,
        onConfirmStep: ((step: ActionCommand, index: Int, total: Int, callback: (StepDecision) -> Unit) -> Unit)?
    ): Boolean {
        // 1. Safety Gate Evaluation
        when (val decision = SafetyGate.evaluate(cmd)) {
            is SafetyDecision.Block -> {
                onUpdate(AgentStepUpdate.StepFailed(stepIndex, totalSteps, "Blocked: ${decision.reason}"))
                return false
            }
            is SafetyDecision.RequireConfirm -> {
                // Handled according to mode below
            }
            is SafetyDecision.AllowSilent -> {
                // Allowed
            }
        }

        // 2. Mode: DRY_RUN
        if (settings.executionMode == ExecutionMode.DRY_RUN) {
            val note = if (cmd.action.tier == SafetyTier.TIER_2_CONFIRM) {
                "Valid (Tier 2 Requires Confirmation)"
            } else {
                "Valid (Tier 1 Silent)"
            }
            onUpdate(AgentStepUpdate.DryRunValidated(stepIndex, totalSteps, cmd, note))
            return true
        }

        // 3. Mode: INTERACTIVE
        if (settings.executionMode == ExecutionMode.INTERACTIVE) {
            onUpdate(
                AgentStepUpdate.AwaitingConfirmation(
                    stepIndex,
                    totalSteps,
                    cmd,
                    "Step requires approval in Interactive Mode"
                )
            )

            var decision = StepDecision.RUN
            if (onConfirmStep != null) {
                val latch = CountDownLatch(1)
                onConfirmStep(cmd, stepIndex, totalSteps) { userChoice ->
                    decision = userChoice
                    latch.countDown()
                }
                latch.await()
            }

            when (decision) {
                StepDecision.RUN -> {
                    return performExecution(cmd, stepIndex, totalSteps, onUpdate)
                }
                StepDecision.SKIP -> {
                    onUpdate(AgentStepUpdate.StepSkipped(stepIndex, totalSteps))
                    return true
                }
                StepDecision.STOP -> {
                    isCancelled = true
                    onUpdate(AgentStepUpdate.Aborted("Stopped by user at step $stepIndex"))
                    return false
                }
            }
        }

        // 4. Mode: SILENT
        if (cmd.action.tier == SafetyTier.TIER_2_CONFIRM) {
            onUpdate(
                AgentStepUpdate.AwaitingConfirmation(
                    stepIndex,
                    totalSteps,
                    cmd,
                    "Tier 2 Action '${cmd.action.wireName}' requires confirmation"
                )
            )

            var decision = StepDecision.RUN
            if (onConfirmStep != null) {
                val latch = CountDownLatch(1)
                onConfirmStep(cmd, stepIndex, totalSteps) { userChoice ->
                    decision = userChoice
                    latch.countDown()
                }
                latch.await()
            }

            if (decision != StepDecision.RUN) {
                onUpdate(AgentStepUpdate.Aborted("Tier 2 confirmation denied"))
                return false
            }
        }

        return performExecution(cmd, stepIndex, totalSteps, onUpdate)
    }

    private fun performExecution(
        cmd: ActionCommand,
        stepIndex: Int,
        totalSteps: Int,
        onUpdate: (AgentStepUpdate) -> Unit
    ): Boolean {
        onUpdate(AgentStepUpdate.StepStarted(stepIndex, totalSteps, cmd))
        return when (val res = executor.execute(cmd, confirmed = true)) {
            is AndroidExecutionResult.Success -> {
                onUpdate(AgentStepUpdate.StepCompleted(stepIndex, totalSteps, res.details))
                true
            }
            is AndroidExecutionResult.ConfirmationRequired -> {
                onUpdate(AgentStepUpdate.StepFailed(stepIndex, totalSteps, "Confirmation unexpectedly missing"))
                false
            }
            is AndroidExecutionResult.Blocked -> {
                onUpdate(AgentStepUpdate.StepFailed(stepIndex, totalSteps, "Blocked: ${res.reason}"))
                false
            }
            is AndroidExecutionResult.Error -> {
                onUpdate(AgentStepUpdate.StepFailed(stepIndex, totalSteps, res.message))
                false
            }
        }
    }

    private fun obtainDecision(prompt: String, fallbackGoal: String): String {
        decider?.let { return it(prompt) }

        if (settings.inferenceSource == InferenceSource.OPENAI_COMPAT) {
            val client = cloudApiClient ?: CloudApiClient(
                baseUrl = settings.openAiApiBase,
                model = settings.openAiApiModel,
                apiKey = settings.openAiApiKey
            )
            val res = client.generateCompletion(
                systemPrompt = PromptFormatter.DEFAULT_SYSTEM_PROMPT,
                userPrompt = prompt
            )
            val raw = res.getOrThrow()
            return PromptFormatter.extractActionJson(raw)
        }

        if (settings.inferenceSource == InferenceSource.LAN_BRIDGE && bridgeClient != null) {
            val host = settings.bridgeHost
            val port = settings.bridgePort
            val token = settings.bridgeToken
            if (host.isNotBlank()) {
                val res = bridgeClient.executeRemote(
                    host = host,
                    port = port,
                    token = token,
                    prompt = prompt,
                    confirm = settings.executionMode == ExecutionMode.SILENT,
                    dryRun = settings.executionMode == ExecutionMode.DRY_RUN
                )
                return res.getOrThrow()
            }
        }

        if (llamaEngine != null && llamaEngine.isLoaded()) {
            val res = llamaEngine.inferActionCommand(
                userQuery = prompt,
                grammarText = grammarText,
                maxTokens = 192
            )
            val cmd = res.getOrThrow()
            return ActionJson.toJson(cmd)
        }

        // Fallback: heuristic mapping
        val fallback = parseLocalIntentFallback(fallbackGoal)
        return ActionJson.toJson(fallback)
    }

    private fun parseLocalIntentFallback(text: String): ActionCommand {
        val lower = text.lowercase(Locale.ROOT).trim()
        val action = when {
            lower == "ارجع" || lower == "back" -> ActionType.UI_BACK
            lower == "الرئيسية" || lower == "الشاشة الرئيسية" || lower == "home" -> ActionType.UI_HOME
            lower.contains("انزل") || lower.contains("scroll down") -> ActionType.UI_SCROLL
            lower.startsWith("افتح ") || lower.startsWith("شغل ") || lower.startsWith("open ") -> {
                if (lower.contains("http") || lower.contains(".com")) ActionType.OPEN_URL else ActionType.OPEN_ANDROID_APP
            }
            lower.contains("صوت") || lower.contains("volume") -> ActionType.SET_VOLUME
            lower.contains("انسخ") || lower.contains("copy") -> ActionType.COPY_TO_CLIPBOARD
            lower.contains("اعدادات") || lower.contains("settings") -> ActionType.TOGGLE_SETTING
            lower.contains("رسالة") || lower.contains("sms") -> ActionType.SEND_MESSAGE
            lower.contains("ملاحظة") || lower.contains("note") -> ActionType.CREATE_NOTE
            else -> ActionType.OPEN_ANDROID_APP
        }

        val target = text.replace(Regex("^(افتح|شغل|open|انسخ|copy|اكتب|write)\\s+", RegexOption.IGNORE_CASE), "").trim()

        return ActionCommand(
            action = action,
            target = target.ifBlank { text },
            platform = Platform.ANDROID,
            confidence = 0.90
        )
    }
}
