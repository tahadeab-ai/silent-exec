package com.silentexec.android

import com.silentexec.android.core.ActionCommand
import com.silentexec.android.core.ActionJson
import com.silentexec.android.core.ActionType
import com.silentexec.android.core.AppSettings
import com.silentexec.android.core.ExecutionMode
import com.silentexec.android.core.InferenceSource
import com.silentexec.android.core.Platform
import com.silentexec.android.core.SafetyDecision
import com.silentexec.android.core.SafetyGate
import com.silentexec.android.exec.ActionOutcome
import com.silentexec.android.exec.AgentLoopRunner
import com.silentexec.android.exec.AgentStepUpdate
import com.silentexec.android.exec.AndroidActionExecutor
import com.silentexec.android.exec.AndroidExecutionResult
import com.silentexec.android.exec.CompoundIntentGuard
import com.silentexec.android.exec.NativeIntentExecutors
import com.silentexec.android.exec.Notetaker
import com.silentexec.android.exec.PhoneState
import com.silentexec.android.exec.StateHashReflector
import com.silentexec.android.exec.TakeOverGate
import com.silentexec.android.nlp.PromptFormatter
import com.silentexec.android.nlp.ToolRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StateMachineAgentTest {

    @Test
    fun test01_compoundIntentGuardSplitsArabicAndEnglishAndResolvesNativeStep1() {
        val goal = "افتح اليوتيوب و ابحث عن nlp و افتح اول فيديو و اضغط لايك"
        val plan = CompoundIntentGuard.decompose(goal)

        assertTrue(plan.isCompound)
        assertEquals(4, plan.steps.size)
        assertEquals("step_1", plan.steps[0].id)
        assertNull(plan.steps[0].dependencyId)
        assertEquals("step_1", plan.steps[1].dependencyId)

        // Step 1 ("افتح اليوتيوب") must resolve deterministically to com.google.android.youtube
        val step1Hint = plan.steps[0].actionHint
        assertNotNull(step1Hint)
        assertEquals(ActionType.OPEN_ANDROID_APP, step1Hint!!.action)
        assertEquals("com.google.android.youtube", step1Hint.target)

        // Verify AgentLoopRunner executes Step 1 natively in 0 LLM calls before calling decider for Step 2
        var llmCalls = 0
        val settings = AppSettings().apply {
            fastRouterEnabled = true
            executionMode = ExecutionMode.SILENT
            inferenceSource = InferenceSource.OFFLINE
        }
        val runner = AgentLoopRunner(
            executor = AndroidActionExecutor(null),
            settings = settings,
            stepDelayMs = 0L,
            uiObserver = { listOf("[0] button: Search") },
            decider = {
                llmCalls++
                """{"thought":"Done","action":"done","platform":"android","confidence":1.0}"""
            }
        )
        val updates = mutableListOf<AgentStepUpdate>()
        runner.run(goal, onUpdate = { updates.add(it) })

        val started = updates.filterIsInstance<AgentStepUpdate.StepStarted>()
        assertTrue(started.isNotEmpty())
        assertEquals(ActionType.OPEN_ANDROID_APP, started[0].command.action)
        assertEquals("com.google.android.youtube", started[0].command.target)
        assertEquals(1, llmCalls) // 0 LLM calls for Step 1; 1 LLM call for remaining GUI step
    }

    @Test
    fun test02_nativeIntentExecutorsResolveBilingualIntents() {
        assertEquals("com.whatsapp", NativeIntentExecutors.resolvePackageName("الواتساب"))
        assertEquals("org.telegram.messenger", NativeIntentExecutors.resolvePackageName("Telegram"))
        assertEquals("com.android.settings", NativeIntentExecutors.resolvePackageName("الإعدادات"))

        val dial = NativeIntentExecutors.tryResolveStep("اتصل بـ 777123456")
        assertNotNull(dial)
        assertEquals("tel:777123456", dial!!.target)

        val wifi = NativeIntentExecutors.tryResolveStep("افتح اعدادات الواي فاي")
        assertNotNull(wifi)
        assertEquals(ActionType.TOGGLE_SETTING, wifi!!.action)

        val alarm = NativeIntentExecutors.tryResolveStep("set alarm 07:30")
        assertNotNull(alarm)
        assertEquals("07:30", alarm!!.target)

        val copy = NativeIntentExecutors.tryResolveStep("انسخ كود التفعيل")
        assertNotNull(copy)
        assertEquals(ActionType.COPY_TO_CLIPBOARD, copy!!.action)
        assertEquals("كود التفعيل", copy.target)
    }

    @Test
    fun test03_batchActionsSchemaSafetyAndExecution() {
        val batchJson = """
            {
              "action": "batch_actions",
              "actions": [
                {"action": "ui_click", "target": "#0", "platform": "android", "confidence": 0.95},
                {"action": "ui_type", "target": "nlp", "platform": "android", "confidence": 0.95}
              ],
              "platform": "android",
              "confidence": 0.95
            }
        """.trimIndent()
        val cmd = ActionJson.fromLlmJson(batchJson)
        assertEquals(ActionType.BATCH_ACTIONS, cmd.action)
        assertEquals(2, cmd.actions?.size)
        assertTrue(SafetyGate.evaluate(cmd) is SafetyDecision.AllowSilent)

        val res = AndroidActionExecutor(null).execute(cmd, confirmed = true)
        assertTrue(res is AndroidExecutionResult.Success)
        assertTrue((res as AndroidExecutionResult.Success).details.contains("2"))

        // Reject > 4 sub-actions
        val tooMany = cmd.copy(actions = List(5) {
            ActionCommand(ActionType.UI_BACK, platform = Platform.ANDROID, confidence = 0.9)
        })
        assertTrue(SafetyGate.evaluate(tooMany) is SafetyDecision.Block)
    }

    @Test
    fun test04_stateHashReflectorNotetakerAndTakeOverGate() {
        val reflector = StateHashReflector()
        reflector.recordBefore("sig_123", "com.example.app")
        val first = reflector.evaluateAfter(mutatesUi = true, afterSignature = "sig_123", afterPackage = "com.example.app")
        assertEquals(ActionOutcome.SILENT_FAILURE, first.outcome)
        assertFalse(first.shouldAbortOrEscalate)
        assertTrue(first.correctiveHint!!.contains("no UI change"))

        val second = reflector.evaluateAfter(mutatesUi = true, afterSignature = "sig_123", afterPackage = "com.example.app")
        assertEquals(ActionOutcome.SILENT_FAILURE, second.outcome)
        assertTrue(second.shouldAbortOrEscalate)

        val notetaker = Notetaker()
        notetaker.recordOnSuccess(ActionOutcome.SILENT_FAILURE, "otp", "9999")
        assertNull(notetaker.getFact("otp"))
        notetaker.recordOnSuccess(ActionOutcome.SUCCESS, "otp", "1234")
        assertEquals("1234", notetaker.getFact("otp"))

        val secureReq = TakeOverGate.evaluate(
            stepIndex = 1,
            cmd = null,
            phoneState = PhoneState("com.bank.app", "PayActivity", null, false, isSecureScreen = true)
        )
        assertNotNull(secureReq)
        assertTrue(secureReq!!.isSecureScreen)

        val payReq = TakeOverGate.evaluate(
            stepIndex = 2,
            cmd = ActionCommand(ActionType.UI_CLICK, target = "تأكيد الدفع", platform = Platform.ANDROID, confidence = 0.9),
            phoneState = null
        )
        assertNotNull(payReq)
        assertFalse(payReq!!.isSecureScreen)
    }

    @Test
    fun test05_toolRegistryAndStripThinkBlocks() {
        ToolRegistry.resetAll(true)
        ToolRegistry.setToolEnabled("web_fetch", false)
        val fastTools = ToolRegistry.activeToolsForTask("ابحث في يوتيوب", fastMode = true)
        assertTrue(fastTools.contains("batch_actions"))
        assertFalse(fastTools.contains("web_fetch"))
        ToolRegistry.resetAll(true)

        val stripped = PromptFormatter.stripThinkBlocks("<think>unclosed trace without end")
        assertEquals("", stripped.cleanContent)
        assertEquals("unclosed trace without end", stripped.reasoningTrace)
    }
}
