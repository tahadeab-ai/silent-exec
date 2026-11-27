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
import com.silentexec.android.nlp.ToolRegistry
import com.silentexec.android.skills.BuiltInSkills
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
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
    data class ModelTip(val message: String) : AgentStepUpdate()
    data class TakeOverRequired(val request: TakeOverRequest) : AgentStepUpdate()
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

        // 1.5. Built-In & Custom Skills Router (runs BEFORE weak 0.5B single-step weights)
        val skillSteps = BuiltInSkills.resolve(cleanGoal)
        if (!skillSteps.isNullOrEmpty()) {
            if (skillSteps.size == 1) {
                onUpdate(AgentStepUpdate.FastRuleMatched(skillSteps[0]))
                executeSingleStep(skillSteps[0], 1, 1, onUpdate, onConfirmStep)
            } else {
                executeMultiStepPlan(skillSteps, onUpdate, onConfirmStep)
            }
            return
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
        if (settings.inferenceSource == InferenceSource.OFFLINE &&
            settings.activeModelFile.contains("0.5B", ignoreCase = true)
        ) {
            onUpdate(
                AgentStepUpdate.ModelTip(
                    "💡 نصيحة: النموذج الحالي Qwen2.5-0.5B خفيف؛ للمهام متعددة الخطوات يُفضل التبديل إلى Qwen3.5-2B أو Qwen3-1.7B أو Cloud API."
                )
            )
        }

        // Prevent Android Self-Inspection Loop: call moveTaskToBack(true) with 450ms settle delay
        // BEFORE Step 1 observation whenever the task targets an external app or SilentExec is foreground.
        if (uiObserver == null) {
            val svc = SilentExecAccessibilityService.get()
            val selfPkg = executor.getAppPackageName() ?: "com.silentexec.android"
            val movedToBack = executor.moveTaskToBackIfActivity()
            if (movedToBack) {
                try { Thread.sleep(450L) } catch (_: InterruptedException) {}
            } else if (svc != null && (svc.activePackageName().isEmpty() || svc.activePackageName() == selfPkg)) {
                svc.performUiHome()
                try { Thread.sleep(450L) } catch (_: InterruptedException) {}
            }
            if (svc != null) {
                var waitedMs = 0L
                while (waitedMs < 900L && !isCancelled) {
                    val currentPkg = svc.activePackageName()
                    if (currentPkg.isNotBlank() && currentPkg != selfPkg) {
                        break
                    }
                    try { Thread.sleep(150L) } catch (_: InterruptedException) {}
                    waitedMs += 150L
                }
            }
        }

        val stepHistory = mutableListOf<String>()
        val maxSteps = 15
        val reflector = StateHashReflector()
        val notetaker = Notetaker()
        val fastMode = settings.fastRouterEnabled
        val compoundPlan = CompoundIntentGuard.decompose(cleanGoal)
        val planManager = compoundPlan.planManager

        var currentStepIndex = 1
        var isLoopFinished = false
        var lastExecutedCommand: ActionCommand? = null
        var lastExecutedPackage: String = ""

        // 2.5. Deterministic Native Intent Execution (0 LLM calls for leading native steps)
        while (!isCancelled && currentStepIndex <= maxSteps) {
            val nextStep = planManager.nextRunnableStep() ?: break
            val hintCmd = nextStep.actionHint ?: break
            planManager.markInProgress(nextStep.id)
            val ok = executeStepInLoop(hintCmd, currentStepIndex, maxSteps, onUpdate, onConfirmStep)
            if (!ok) {
                planManager.markFailed(nextStep.id)
                return
            }
            planManager.markDone(nextStep.id)
            lastExecutedCommand = hintCmd
            notetaker.recordOnSuccess(
                ActionOutcome.SUCCESS,
                nextStep.id,
                "${hintCmd.action.wireName}:${hintCmd.target ?: ""}"
            )
            stepHistory.add("Step $currentStepIndex (${nextStep.id}): ${hintCmd.action.wireName}(${hintCmd.target ?: ""}) -> OK")
            currentStepIndex++
            if (stepDelayMs > 0 && planManager.remainingCount() > 0 && !isCancelled) {
                try { Thread.sleep(maxOf(stepDelayMs, 800L)) } catch (_: InterruptedException) {}
            }
        }

        if (compoundPlan.steps.isNotEmpty() && planManager.remainingCount() == 0 && stepHistory.isNotEmpty()) {
            onUpdate(AgentStepUpdate.AllCompleted(currentStepIndex - 1))
            return
        }

        while (currentStepIndex <= maxSteps && !isLoopFinished && !isCancelled) {
            val svc = if (uiObserver == null) SilentExecAccessibilityService.get() else null
            val phoneState = svc?.getPhoneState()
            val uiTreeElements = svc?.extractPrunedUiTree().orEmpty()
            val screenElements = uiObserver?.invoke()
                ?: uiTreeElements.map { it.formatForPrompt() }

            // Vision / Screenshot Fallback when a11y_tree returns <= 3 interactive nodes
            val interactiveCount = uiTreeElements.count { it.isClickable || it.isEditable || it.isScrollable }
            val visionFallbackHint = if (svc != null && SilentExecAccessibilityService.shouldTriggerVisionFallback(interactiveCount)) {
                val screenshotB64 = svc.captureCompressedScreenshotBase64()
                if (!screenshotB64.isNullOrEmpty()) {
                    "VisionFallback: A11y tree sparse ($interactiveCount interactive nodes); compressed screenshot captured (${screenshotB64.length} b64 chars)."
                } else {
                    "VisionFallback: A11y tree sparse ($interactiveCount interactive nodes); use coordinate tap/scroll or Shizuku fallback."
                }
            } else {
                null
            }

            // SensitiveScreenGuard for FLAG_SECURE / password fields / payment screens
            SensitiveScreenGuard.evaluate(
                stepIndex = currentStepIndex,
                cmd = null,
                phoneState = phoneState,
                uiElements = uiTreeElements,
                uiStrings = screenElements
            )?.let { req ->
                onUpdate(AgentStepUpdate.TakeOverRequired(req))
                if (!awaitTakeOverDecision(req, currentStepIndex, maxSteps, onConfirmStep)) {
                    onUpdate(AgentStepUpdate.Aborted(req.reason))
                    return
                }
            }

            val toolSummary = ToolRegistry.formatActiveToolsSummary(cleanGoal, fastMode)
            val planSummary = if (!fastMode || compoundPlan.isCompound) {
                "Plan Progress:\n${planManager.formatProgressSummary()}"
            } else null

            val combinedNotes = listOfNotNull(
                notetaker.formatForPrompt().takeIf { it.isNotBlank() },
                visionFallbackHint
            ).joinToString("\n").takeIf { it.isNotBlank() }

            val prompt = PromptFormatter.buildScreenAwarePrompt(
                userGoal = cleanGoal,
                prunedUiTree = screenElements,
                stepHistory = stepHistory,
                toolSummary = toolSummary,
                planSummary = planSummary,
                sessionNotes = combinedNotes
            )

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

            var planOrAction = try {
                PromptFormatter.parsePlanOrSingleAction(rawDecision)
            } catch (_: Exception) {
                null
            }

            if (planOrAction == null) {
                // 1-Retry Self-Healing: Feed exact screen UI tree + correction hint back once before failing
                val healingPrompt = PromptFormatter.buildSelfHealingPrompt(
                    userGoal = cleanGoal,
                    prunedUiTree = screenElements,
                    stepHistory = stepHistory,
                    malformedOutput = rawDecision
                )
                val healedDecision = runCatching { obtainDecision(healingPrompt, cleanGoal) }.getOrNull()
                planOrAction = healedDecision?.let {
                    runCatching { PromptFormatter.parsePlanOrSingleAction(it) }.getOrNull()
                }
            }

            if (planOrAction == null) {
                // If model output still cannot be parsed, fallback to local heuristic
                val fallbackCmd = parseLocalIntentFallback(cleanGoal)
                planOrAction = PlanOrAction.Action(fallbackCmd)
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

                    // Guard against small local models emitting empty target on open_android_app or repeating Step 1 identically
                    if (cmd.action == ActionType.OPEN_ANDROID_APP && cmd.target.isNullOrBlank()) {
                        onUpdate(
                            AgentStepUpdate.StepFailed(
                                currentStepIndex,
                                maxSteps,
                                "توقف النموذج المحلي عند الخطوة $currentStepIndex بسبب هدف فارغ. يُنصح بتحميل Qwen3-1.7B أو LFM3-1.7B أو استخدام Cloud API للحصول على دقة أعلى."
                            )
                        )
                        return
                    }

                    val currentPkg = phoneState?.packageName.orEmpty()
                    val prev = lastExecutedCommand
                    if (prev != null &&
                        lastExecutedPackage == currentPkg &&
                        prev.action == cmd.action &&
                        (prev.target ?: "").trim().equals((cmd.target ?: "").trim(), ignoreCase = true) &&
                        prev.args == cmd.args
                    ) {
                        onUpdate(
                            AgentStepUpdate.StepFailed(
                                currentStepIndex,
                                maxSteps,
                                "تم إيقاف التكرار التلقائي للخطوة (${cmd.action.wireName}). يُنصح باستخدام Qwen3.5-2B أو Cloud API للمهام متعددة الخطوات."
                            )
                        )
                        return
                    }

                    // SensitiveScreenGuard for sensitive actions (payments, messages, installs, passwords, permissions)
                    SensitiveScreenGuard.evaluate(
                        stepIndex = currentStepIndex,
                        cmd = cmd,
                        phoneState = phoneState,
                        uiElements = uiTreeElements,
                        uiStrings = screenElements
                    )?.let { req ->
                        onUpdate(AgentStepUpdate.TakeOverRequired(req))
                        if (!awaitTakeOverDecision(req, currentStepIndex, maxSteps, onConfirmStep)) {
                            onUpdate(AgentStepUpdate.Aborted(req.reason))
                            return
                        }
                    }

                    val preSig = svc?.computeScreenSignature().orEmpty()
                    val prePkg = svc?.activePackageName().orEmpty()
                    reflector.recordBefore(preSig, prePkg)

                    val stepSuccess = executeStepInLoop(cmd, currentStepIndex, maxSteps, onUpdate, onConfirmStep)
                    if (!stepSuccess) {
                        return
                    }

                    val mutatesUi = StateHashReflector.isUiMutatingAction(cmd.action)
                    val settleMs = when {
                        cmd.action in setOf(ActionType.OPEN_URL, ActionType.OPEN_ANDROID_APP, ActionType.UI_CLICK) -> maxOf(stepDelayMs, 1200L)
                        mutatesUi -> maxOf(stepDelayMs, 500L)
                        else -> stepDelayMs
                    }
                    if (stepDelayMs > 0 && currentStepIndex <= maxSteps && !isCancelled) {
                        try { Thread.sleep(settleMs) } catch (_: InterruptedException) {}
                    }

                    val postSig = svc?.computeScreenSignature().orEmpty()
                    val postPkg = svc?.activePackageName().orEmpty()
                    val reflection = reflector.evaluateAfter(
                        mutatesUi = mutatesUi && svc != null,
                        afterSignature = postSig,
                        afterPackage = postPkg
                    )
                    if (reflection.outcome == ActionOutcome.SILENT_FAILURE) {
                        stepHistory.add("Step $currentStepIndex: ${cmd.action.wireName}(${cmd.target ?: ""}) -> SILENT_FAILURE (${reflection.correctiveHint})")
                        if (reflection.shouldAbortOrEscalate) {
                            onUpdate(
                                AgentStepUpdate.StepFailed(
                                    currentStepIndex,
                                    maxSteps,
                                    "توقف الوكيل بعد تكرار عدم تغير الشاشة: ${reflection.correctiveHint}"
                                )
                            )
                            return
                        }
                    } else {
                        notetaker.recordOnSuccess(
                            ActionOutcome.SUCCESS,
                            "step_$currentStepIndex",
                            "${cmd.action.wireName}:${cmd.target ?: ""}"
                        )
                        planManager.nextRunnableStep()?.let { pendingStep ->
                            planManager.markDone(pendingStep.id)
                        }
                        stepHistory.add("Step $currentStepIndex: ${cmd.action.wireName}(${cmd.target ?: ""}) -> OK")
                    }

                    lastExecutedCommand = cmd
                    lastExecutedPackage = prePkg
                    currentStepIndex++
                }
            }
        }

        if (isCancelled) {
            onUpdate(AgentStepUpdate.Aborted("Cancelled by user"))
        } else if (!isLoopFinished) {
            onUpdate(AgentStepUpdate.AllCompleted(currentStepIndex - 1))
        }
    }

    private fun awaitTakeOverDecision(
        req: TakeOverRequest,
        stepIndex: Int,
        maxSteps: Int,
        onConfirmStep: ((step: ActionCommand, index: Int, total: Int, callback: (StepDecision) -> Unit) -> Unit)?
    ): Boolean {
        if (onConfirmStep == null) return false
        val placeholder = req.sensitiveAction ?: ActionCommand(
            action = ActionType.UI_CLICK,
            target = req.reason,
            confidence = 1.0,
            thought = req.reason
        )
        var decision = StepDecision.STOP
        val latch = CountDownLatch(1)
        onConfirmStep(placeholder, stepIndex, maxSteps) { choice ->
            decision = choice
            latch.countDown()
        }
        latch.await()
        return decision == StepDecision.RUN
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

        if (settings.inferenceSource == InferenceSource.OFFLINE) {
            if (llamaEngine == null || !llamaEngine.isLoaded()) {
                throw IllegalStateException("يرجى تحميل نموذج من تبويب النماذج أو اختيار Cloud API من الإعدادات")
            }
            val res = llamaEngine.inferActionCommand(
                userQuery = prompt,
                grammarText = PromptFormatter.ACTION_COMMAND_GBNF,
                maxTokens = 384
            )
            val cmd = res.getOrThrow()
            return ActionJson.toJson(cmd)
        }

        if (llamaEngine != null && llamaEngine.isLoaded()) {
            val res = llamaEngine.inferActionCommand(
                userQuery = prompt,
                grammarText = PromptFormatter.ACTION_COMMAND_GBNF,
                maxTokens = 384
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

        // Check for compound YouTube search task: "افتح اليوتيوب وابحث عن <query>" or "open youtube and search <query>"
        if ((lower.contains("youtube") || lower.contains("يوتيوب")) &&
            (lower.contains("search") || lower.contains("ابحث") || lower.contains("بحث"))
        ) {
            val query = text.replace(
                Regex(".*(?:ابحث عن|بحث عن|ابحث|search for|search)\\s+", RegexOption.IGNORE_CASE),
                ""
            ).trim()
            return ActionCommand(
                action = ActionType.LAUNCH_APP,
                target = "com.google.android.youtube",
                args = buildJsonObject {
                    put("query", JsonPrimitive(query))
                },
                platform = Platform.ANDROID,
                confidence = 0.95
            )
        }

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
