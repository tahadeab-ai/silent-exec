package com.silentexec.android

import com.silentexec.android.core.ActionType
import com.silentexec.android.core.Platform
import com.silentexec.android.exec.CompoundIntentGuard
import com.silentexec.android.exec.PhoneState
import com.silentexec.android.exec.SensitiveScreenGuard
import com.silentexec.android.exec.SilentExecAccessibilityService
import com.silentexec.android.exec.UiElementInfo
import com.silentexec.android.nlp.PlanOrAction
import com.silentexec.android.nlp.PromptFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstrainedInferenceAndA11yTest {

    @Test
    fun test01_thinkStrippingAndEmptyRetryTrigger() {
        // Unclosed <think> trace (token budget exhausted) must trigger non-thinking retry, never crash on blank
        val unclosedThink = "<think>Let me analyze the screen elements carefully..."
        val parsedUnclosed = PromptFormatter.stripThinkBlocks(unclosedThink)
        assertEquals("", parsedUnclosed.cleanContent)
        assertTrue(PromptFormatter.needsNonThinkingRetry(unclosedThink))
        assertTrue(PromptFormatter.needsNonThinkingRetry(""))
        assertTrue(PromptFormatter.needsNonThinkingRetry("   <think>done</think>   "))

        val nonThinkingPrompt = PromptFormatter.buildNonThinkingPrompt("افتح اليوتيوب")
        assertTrue(nonThinkingPrompt.contains("/no_think"))
        assertTrue(PromptFormatter.ACTION_COMMAND_GBNF.contains("root ::="))

        // Closed <think> followed by valid JSON parses cleanly without needing retry
        val closedWithJson = """<think>Need to tap search</think>{"thought":"Tap search","action":"ui_click","target":"#0","platform":"android","confidence":0.95}"""
        assertFalse(PromptFormatter.needsNonThinkingRetry(closedWithJson))
        val result = PromptFormatter.parsePlanOrSingleAction(closedWithJson)
        assertTrue(result is PlanOrAction.Action)
        val cmd = (result as PlanOrAction.Action).actionCommand
        assertEquals(ActionType.UI_CLICK, cmd.action)
        assertEquals("#0", cmd.target)
    }

    @Test
    fun test02_xmlToolCallAndJsonActionCommandParsing() {
        val lfm3Xml = """
            <think>Searching for nlp on YouTube</think>
            <tool_call>
            <function=ui_type>
            <parameter=target>nlp</parameter>
            <parameter=platform>android</parameter>
            <parameter=confidence>0.92</parameter>
            </function>
            </tool_call>
        """.trimIndent()

        val xmlParsed = PromptFormatter.parsePlanOrSingleAction(lfm3Xml)
        assertTrue(xmlParsed is PlanOrAction.Action)
        val xmlCmd = (xmlParsed as PlanOrAction.Action).actionCommand
        assertEquals(ActionType.UI_TYPE, xmlCmd.action)
        assertEquals("nlp", xmlCmd.target)
        assertEquals(Platform.ANDROID, xmlCmd.platform)
        assertEquals(0.92, xmlCmd.confidence, 0.001)

        val jsonRaw = """{"thought":"Open YouTube","action":"open_android_app","target":"com.google.android.youtube","platform":"android","confidence":0.98}"""
        val jsonParsed = PromptFormatter.parsePlanOrSingleAction(jsonRaw)
        assertTrue(jsonParsed is PlanOrAction.Action)
        val jsonCmd = (jsonParsed as PlanOrAction.Action).actionCommand
        assertEquals(ActionType.OPEN_ANDROID_APP, jsonCmd.action)
        assertEquals("com.google.android.youtube", jsonCmd.target)
    }

    @Test
    fun test03_arabicCompoundPromptDecomposition() {
        val compound = "افتح اليوتيوب و ابحث عن nlp و افتح اول فيديو و اضغط لايك"
        val result = CompoundIntentGuard.decompose(compound)

        assertTrue(result.isCompound)
        assertEquals(4, result.steps.size)
        assertEquals("افتح اليوتيوب", result.steps[0].description)
        assertEquals("ابحث عن nlp", result.steps[1].description)
        assertEquals("افتح اول فيديو", result.steps[2].description)
        assertEquals("اضغط لايك", result.steps[3].description)

        assertEquals(null, result.steps[0].dependencyId)
        assertEquals("step_1", result.steps[1].dependencyId)
        assertEquals("step_2", result.steps[2].dependencyId)
        assertEquals("step_3", result.steps[3].dependencyId)
    }

    @Test
    fun test04_sparseA11yNodesTriggersVisionFallback() {
        assertTrue(SilentExecAccessibilityService.shouldTriggerVisionFallback(0))
        assertTrue(SilentExecAccessibilityService.shouldTriggerVisionFallback(2))
        assertTrue(SilentExecAccessibilityService.shouldTriggerVisionFallback(3))
        assertFalse(SilentExecAccessibilityService.shouldTriggerVisionFallback(4))
        assertFalse(SilentExecAccessibilityService.shouldTriggerVisionFallback(15))
    }

    @Test
    fun test05_sensitiveScreenGuardDetectsSecureAndPayment() {
        val secureState = PhoneState(
            packageName = "com.bank.app",
            activityName = "LoginActivity",
            focusedElement = null,
            keyboardVisible = false,
            isSecureScreen = true
        )
        val secureReq = SensitiveScreenGuard.evaluate(1, null, secureState)
        assertNotNull(secureReq)
        assertTrue(secureReq!!.isSecureScreen)

        val paymentReq = SensitiveScreenGuard.evaluate(
            stepIndex = 2,
            cmd = null,
            phoneState = PhoneState("com.shop.app", "Checkout", null, false, false),
            uiElements = emptyList(),
            uiStrings = listOf("[0] button: Pay Now [500,1200]")
        )
        assertNotNull(paymentReq)
        assertFalse(paymentReq!!.isSecureScreen)
    }
}
