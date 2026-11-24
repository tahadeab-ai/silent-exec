package com.silentexec.android

import com.silentexec.android.core.ActionCommand
import com.silentexec.android.core.ActionType
import com.silentexec.android.core.AppSettings
import com.silentexec.android.core.ExecutionMode
import com.silentexec.android.core.Platform
import com.silentexec.android.core.SafetyTier
import com.silentexec.android.core.ThemeMode
import com.silentexec.android.exec.AgentLoopRunner
import com.silentexec.android.exec.AgentStepUpdate
import com.silentexec.android.exec.AndroidActionExecutor
import com.silentexec.android.exec.StepDecision
import com.silentexec.android.nlp.FastRuleRouter
import com.silentexec.android.nlp.PlanOrAction
import com.silentexec.android.nlp.PromptFormatter
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentLoopAndThemeTest {

    @Test
    fun test01_fastRuleRouter_rejectsCompoundArabicCommand() {
        val compoundCommands = listOf(
            "افتح واتساب وابحث عن أحمد",
            "افتح واتساب وارسل له مرحبا",
            "شغل اليوتيوب وابحث عن بايثون",
            "افتح التنزيلات واحذف الملف",
            "افتح المتصفح واكتب جوجل ثم اضغط بحث"
        )

        for (cmd in compoundCommands) {
            val routed = FastRuleRouter.route(cmd)
            assertNull("Compound Arabic command '$cmd' MUST NOT be intercepted by FastRuleRouter", routed)
        }
    }

    @Test
    fun test02_fastRuleRouter_rejectsCompoundEnglishCommand() {
        val compoundCommands = listOf(
            "open youtube and search coding",
            "open chrome and download rust",
            "open files then delete report",
            "launch whatsapp, write hello and send"
        )

        for (cmd in compoundCommands) {
            val routed = FastRuleRouter.route(cmd)
            assertNull("Compound English command '$cmd' MUST NOT be intercepted by FastRuleRouter", routed)
        }
    }

    @Test
    fun test03_fastRuleRouter_matchesSimpleCommandsUnder15ms() {
        val simpleCommands = mapOf(
            "ارجع" to ActionType.UI_BACK,
            "back" to ActionType.UI_BACK,
            "الرئيسية" to ActionType.UI_HOME,
            "home" to ActionType.UI_HOME,
            "انزل" to ActionType.UI_SCROLL,
            "scroll down" to ActionType.UI_SCROLL,
            "افتح التنزيلات" to ActionType.OPEN_FOLDER,
            "open downloads" to ActionType.OPEN_FOLDER
        )

        for ((text, expectedAction) in simpleCommands) {
            val start = System.nanoTime()
            val routed = FastRuleRouter.route(text)
            val elapsedMs = (System.nanoTime() - start) / 1_000_000.0

            assertNotNull("Simple command '$text' should match FastRuleRouter", routed)
            assertEquals(expectedAction, routed?.action)
            assertTrue("Routing for '$text' took ${elapsedMs}ms (must be < 15ms)", elapsedMs < 15.0)
        }
    }

    @Test
    fun test04_promptFormatter_buildScreenAwarePrompt_arabicAndEnglishUi() {
        val goal = "افتح واتساب وابحث عن أحمد وارسل له مرحبا"
        val screenElements = listOf(
            "[0] بحث (clickable)",
            "[1] مربع الرسالة (editable)",
            "[2] إرسال (clickable)"
        )
        val history = listOf(
            "Step 1: open_android_app(WhatsApp) -> OK"
        )

        val prompt = PromptFormatter.buildScreenAwarePrompt(goal, screenElements, history)

        assertTrue(prompt.contains("<|im_start|>system"))
        assertTrue(prompt.contains("<|im_start|>user"))
        assertTrue(prompt.contains("<|im_start|>assistant"))
        assertTrue(prompt.contains("Goal: $goal"))
        assertTrue(prompt.contains("[0] بحث (clickable)"))
        assertTrue(prompt.contains("[1] مربع الرسالة (editable)"))
        assertTrue(prompt.contains("Step 1: open_android_app(WhatsApp) -> OK"))
        assertTrue(prompt.contains("done"))
    }

    @Test
    fun test05_promptFormatter_parsePlanOrSingleAction_multiStepPlan() {
        val rawJson = """
            {
              "steps": [
                {
                  "action": "open_android_app",
                  "target": "WhatsApp",
                  "platform": "android",
                  "confidence": 1.0
                },
                {
                  "action": "ui_click",
                  "target": "Search",
                  "platform": "android",
                  "confidence": 0.95
                },
                {
                  "action": "ui_type",
                  "target": "Ahmed",
                  "platform": "android",
                  "confidence": 0.90
                }
              ]
            }
        """.trimIndent()

        val parsed = PromptFormatter.parsePlanOrSingleAction(rawJson)
        assertTrue(parsed is PlanOrAction.Plan)
        val plan = (parsed as PlanOrAction.Plan).multiStepPlan

        assertEquals(3, plan.steps.size)
        assertEquals(ActionType.OPEN_ANDROID_APP, plan.steps[0].action)
        assertEquals("WhatsApp", plan.steps[0].target)
        assertEquals(ActionType.UI_CLICK, plan.steps[1].action)
        assertEquals("Search", plan.steps[1].target)
        assertEquals(ActionType.UI_TYPE, plan.steps[2].action)
        assertEquals("Ahmed", plan.steps[2].target)
    }

    @Test
    fun test06_promptFormatter_parsePlanOrSingleAction_singleAction() {
        val rawJson = """
            {
              "action": "ui_back",
              "platform": "android",
              "confidence": 1.0
            }
        """.trimIndent()

        val parsed = PromptFormatter.parsePlanOrSingleAction(rawJson)
        assertTrue(parsed is PlanOrAction.Action)
        val action = (parsed as PlanOrAction.Action).actionCommand

        assertEquals(ActionType.UI_BACK, action.action)
        assertEquals(Platform.ANDROID, action.platform)
        assertEquals(1.0, action.confidence, 0.001)
    }

    @Test
    fun test07_promptFormatter_parsePlanOrSingleAction_markdownFencesAndDoneAction() {
        val fenced = """
            ```json
            {
              "action": "done",
              "platform": "android",
              "confidence": 1.0
            }
            ```
        """.trimIndent()

        val parsed = PromptFormatter.parsePlanOrSingleAction(fenced)
        assertTrue(parsed is PlanOrAction.Action)
        val action = (parsed as PlanOrAction.Action).actionCommand

        assertEquals(ActionType.DONE, action.action)
        assertEquals(SafetyTier.TIER_1_SILENT, action.action.tier)
    }

    @Test
    fun test08_agentLoopRunner_silentModeExecution() {
        val settings = AppSettings().apply {
            executionMode = ExecutionMode.SILENT
            fastRouterEnabled = false
        }
        val executor = AndroidActionExecutor()
        val runner = AgentLoopRunner(
            executor = executor,
            settings = settings,
            stepDelayMs = 0L
        )

        val updates = mutableListOf<AgentStepUpdate>()
        val planJson = """
            {
              "steps": [
                {
                  "action": "open_android_app",
                  "target": "WhatsApp",
                  "platform": "android",
                  "confidence": 1.0
                },
                {
                  "action": "ui_click",
                  "target": "Search",
                  "platform": "android",
                  "confidence": 1.0
                }
              ]
            }
        """.trimIndent()

        runner.run(planJson, onUpdate = { updates.add(it) })

        assertTrue(updates.any { it is AgentStepUpdate.PlanResolved })
        val startedSteps = updates.filterIsInstance<AgentStepUpdate.StepStarted>()
        assertEquals(2, startedSteps.size)
        val completedSteps = updates.filterIsInstance<AgentStepUpdate.StepCompleted>()
        assertEquals(2, completedSteps.size)
        assertTrue(updates.last() is AgentStepUpdate.AllCompleted)
    }

    @Test
    fun test09_agentLoopRunner_interactiveModeExecution() {
        val settings = AppSettings().apply {
            executionMode = ExecutionMode.INTERACTIVE
            fastRouterEnabled = false
        }
        val executor = AndroidActionExecutor()
        val runner = AgentLoopRunner(
            executor = executor,
            settings = settings,
            stepDelayMs = 0L
        )

        val updates = mutableListOf<AgentStepUpdate>()
        val confirmations = mutableListOf<Int>()

        val planJson = """
            {
              "steps": [
                {
                  "action": "ui_click",
                  "target": "Next",
                  "platform": "android",
                  "confidence": 1.0
                },
                {
                  "action": "ui_type",
                  "target": "Confirm",
                  "platform": "android",
                  "confidence": 1.0
                }
              ]
            }
        """.trimIndent()

        runner.run(
            userGoal = planJson,
            onUpdate = { updates.add(it) },
            onConfirmStep = { _, index, _, callback ->
                confirmations.add(index)
                callback(StepDecision.RUN)
            }
        )

        assertEquals(2, confirmations.size)
        assertEquals(listOf(1, 2), confirmations)
        val awaiting = updates.filterIsInstance<AgentStepUpdate.AwaitingConfirmation>()
        assertEquals(2, awaiting.size)
        assertTrue(updates.last() is AgentStepUpdate.AllCompleted)
    }

    @Test
    fun test10_agentLoopRunner_interactiveModeSkipAndStop() {
        val settings = AppSettings().apply {
            executionMode = ExecutionMode.INTERACTIVE
            fastRouterEnabled = false
        }
        val executor = AndroidActionExecutor()
        val runner = AgentLoopRunner(
            executor = executor,
            settings = settings,
            stepDelayMs = 0L
        )

        // Test SKIP
        val skipUpdates = mutableListOf<AgentStepUpdate>()
        val planJson = """
            {
              "steps": [
                {
                  "action": "ui_scroll",
                  "target": "down",
                  "platform": "android",
                  "confidence": 1.0
                },
                {
                  "action": "ui_click",
                  "target": "Submit",
                  "platform": "android",
                  "confidence": 1.0
                }
              ]
            }
        """.trimIndent()

        runner.run(
            userGoal = planJson,
            onUpdate = { skipUpdates.add(it) },
            onConfirmStep = { _, index, _, callback ->
                if (index == 1) {
                    callback(StepDecision.SKIP)
                } else {
                    callback(StepDecision.RUN)
                }
            }
        )

        val skipped = skipUpdates.filterIsInstance<AgentStepUpdate.StepSkipped>()
        assertEquals(1, skipped.size)
        assertEquals(1, skipped[0].stepIndex)
        assertTrue(skipUpdates.last() is AgentStepUpdate.AllCompleted)

        // Test STOP
        val stopUpdates = mutableListOf<AgentStepUpdate>()
        runner.run(
            userGoal = planJson,
            onUpdate = { stopUpdates.add(it) },
            onConfirmStep = { _, _, _, callback ->
                callback(StepDecision.STOP)
            }
        )

        val aborted = stopUpdates.filterIsInstance<AgentStepUpdate.Aborted>()
        assertEquals(1, aborted.size)
    }

    @Test
    fun test11_agentLoopRunner_dryRunModeExecution() {
        val settings = AppSettings().apply {
            executionMode = ExecutionMode.DRY_RUN
            fastRouterEnabled = false
        }
        val executor = AndroidActionExecutor()
        val runner = AgentLoopRunner(
            executor = executor,
            settings = settings,
            stepDelayMs = 0L
        )

        val updates = mutableListOf<AgentStepUpdate>()
        val planJson = """
            {
              "steps": [
                {
                  "action": "open_android_app",
                  "target": "Settings",
                  "platform": "android",
                  "confidence": 1.0
                },
                {
                  "action": "delete_file",
                  "target": "old_backup.zip",
                  "platform": "android",
                  "confidence": 0.90
                }
              ]
            }
        """.trimIndent()

        runner.run(planJson, onUpdate = { updates.add(it) })

        val dryRuns = updates.filterIsInstance<AgentStepUpdate.DryRunValidated>()
        assertEquals(2, dryRuns.size)
        assertTrue(dryRuns[0].note.contains("Tier 1"))
        assertTrue(dryRuns[1].note.contains("Tier 2"))
        // In DRY_RUN mode, no StepStarted or StepCompleted should be emitted
        assertTrue(updates.none { it is AgentStepUpdate.StepStarted })
        assertTrue(updates.last() is AgentStepUpdate.AllCompleted)
    }

    @Test
    fun test12_agentLoopRunner_cancellationMidPlan() {
        val settings = AppSettings().apply {
            executionMode = ExecutionMode.SILENT
            fastRouterEnabled = false
        }
        val executor = AndroidActionExecutor()
        lateinit var runner: AgentLoopRunner
        val updates = mutableListOf<AgentStepUpdate>()

        runner = AgentLoopRunner(
            executor = executor,
            settings = settings,
            stepDelayMs = 0L
        )

        val planJson = """
            {
              "steps": [
                {
                  "action": "ui_click",
                  "target": "Step 1",
                  "platform": "android",
                  "confidence": 1.0
                },
                {
                  "action": "ui_click",
                  "target": "Step 2",
                  "platform": "android",
                  "confidence": 1.0
                },
                {
                  "action": "ui_click",
                  "target": "Step 3",
                  "platform": "android",
                  "confidence": 1.0
                }
              ]
            }
        """.trimIndent()

        // Cancel on step 1 update
        runner.run(
            userGoal = planJson,
            onUpdate = { update ->
                updates.add(update)
                if (update is AgentStepUpdate.StepCompleted && update.stepIndex == 1) {
                    runner.cancel()
                }
            }
        )

        assertTrue(runner.isExecutionCancelled())
        val started = updates.filterIsInstance<AgentStepUpdate.StepStarted>()
        assertEquals(1, started.size)
        assertTrue(updates.any { it is AgentStepUpdate.Aborted })
    }

    @Test
    fun test13_agentLoopRunner_safetyGateHaltsOnInjectionInPlan() {
        val settings = AppSettings().apply {
            executionMode = ExecutionMode.SILENT
            fastRouterEnabled = false
        }
        val executor = AndroidActionExecutor()
        val runner = AgentLoopRunner(
            executor = executor,
            settings = settings,
            stepDelayMs = 0L
        )

        val updates = mutableListOf<AgentStepUpdate>()
        val planJson = """
            {
              "steps": [
                {
                  "action": "open_android_app",
                  "target": "Chrome",
                  "platform": "android",
                  "confidence": 1.0
                },
                {
                  "action": "ui_type",
                  "target": "hello; rm -rf /",
                  "platform": "android",
                  "confidence": 1.0
                },
                {
                  "action": "ui_click",
                  "target": "Enter",
                  "platform": "android",
                  "confidence": 1.0
                }
              ]
            }
        """.trimIndent()

        runner.run(planJson, onUpdate = { updates.add(it) })

        val failed = updates.filterIsInstance<AgentStepUpdate.StepFailed>()
        assertEquals(1, failed.size)
        assertEquals(2, failed[0].stepIndex)
        assertTrue(failed[0].error.contains("Blocked"))
        // Step 3 should NEVER execute
        val started = updates.filterIsInstance<AgentStepUpdate.StepStarted>()
        assertEquals(1, started.size)
        assertTrue(updates.none { it is AgentStepUpdate.AllCompleted })
    }

    @Test
    fun test14_appSettings_themeModePersistence() {
        val settings = AppSettings()

        // Default is SYSTEM
        assertEquals(ThemeMode.SYSTEM, settings.themeMode)

        // Change to DARK
        settings.themeMode = ThemeMode.DARK
        assertEquals(ThemeMode.DARK, settings.themeMode)

        // Change to LIGHT
        settings.themeMode = ThemeMode.LIGHT
        assertEquals(ThemeMode.LIGHT, settings.themeMode)

        // Verify fromString helper
        assertEquals(ThemeMode.DARK, ThemeMode.fromString("DARK"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromString("dark"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromString("light"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromString("system"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromString("invalid_theme"))
    }
}
