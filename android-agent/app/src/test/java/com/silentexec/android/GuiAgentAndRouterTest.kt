package com.silentexec.android

import com.silentexec.android.core.ActionCommand
import com.silentexec.android.core.ActionJson
import com.silentexec.android.core.ActionType
import com.silentexec.android.core.MultiStepPlan
import com.silentexec.android.core.Platform
import com.silentexec.android.core.SafetyDecision
import com.silentexec.android.core.SafetyGate
import com.silentexec.android.core.SafetyTier
import com.silentexec.android.exec.SilentExecAccessibilityService
import com.silentexec.android.nlp.FastRuleRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuiAgentAndRouterTest {

    @Test
    fun test01_allTwentyActionsExactDeserialization() {
        assertEquals(26, ActionType.values().size)

        val wireNames = listOf(
            "open_folder", "open_file", "launch_app", "search_files",
            "set_volume", "set_brightness", "create_note", "copy_to_clipboard",
            "delete_file", "move_file", "system_power", "open_android_app",
            "toggle_setting", "send_message", "open_url",
            "ui_click", "ui_type", "ui_scroll", "ui_back", "ui_home", "done",
            "reply", "read_file", "write_file", "memory_save", "web_fetch"
        )

        for (wire in wireNames) {
            val json = """
                {
                    "action": "$wire",
                    "target": "sample",
                    "platform": "android",
                    "confidence": 0.95
                }
            """.trimIndent()

            val cmd = ActionJson.fromJson(json)
            assertEquals(wire, cmd.action.wireName)
            assertEquals(Platform.ANDROID, cmd.platform)
        }

        // Assert tier classification parity
        val tier2Actions = setOf(
            ActionType.DELETE_FILE,
            ActionType.MOVE_FILE,
            ActionType.SYSTEM_POWER,
            ActionType.TOGGLE_SETTING,
            ActionType.SEND_MESSAGE,
            ActionType.WRITE_FILE
        )

        for (action in ActionType.values()) {
            if (action in tier2Actions) {
                assertEquals(SafetyTier.TIER_2_CONFIRM, action.tier)
            } else {
                assertEquals(SafetyTier.TIER_1_SILENT, action.tier)
            }
        }
    }

    @Test
    fun test02_uiClickActionTierAndValidation() {
        val clickCmd = ActionCommand(
            action = ActionType.UI_CLICK,
            target = "زر الإرسال",
            platform = Platform.ANDROID,
            confidence = 0.92
        )

        assertEquals(SafetyTier.TIER_1_SILENT, clickCmd.action.tier)
        val decision = SafetyGate.evaluate(clickCmd)
        assertTrue(decision is SafetyDecision.AllowSilent)
    }

    @Test
    fun test03_uiTypeActionTierAndInjectionBlocking() {
        // Safe typing
        val safeType = ActionCommand(
            action = ActionType.UI_TYPE,
            target = "مرحبا بكم في صنعاء",
            platform = Platform.ANDROID,
            confidence = 0.95
        )
        val decisionSafe = SafetyGate.evaluate(safeType)
        assertTrue(decisionSafe is SafetyDecision.AllowSilent)

        // Metacharacter injection in target
        val injectionType = ActionCommand(
            action = ActionType.UI_TYPE,
            target = "hello; rm -rf /",
            platform = Platform.ANDROID,
            confidence = 0.95
        )
        val decisionInj = SafetyGate.evaluate(injectionType)
        assertTrue(decisionInj is SafetyDecision.Block)
        assertTrue((decisionInj as SafetyDecision.Block).reason.contains("Shell injection"))
    }

    @Test
    fun test04_multiStepPlanAllowSilent() {
        val plan = MultiStepPlan(
            steps = listOf(
                ActionCommand(
                    action = ActionType.OPEN_ANDROID_APP,
                    target = "WhatsApp",
                    platform = Platform.ANDROID,
                    confidence = 0.95
                ),
                ActionCommand(
                    action = ActionType.UI_TYPE,
                    target = "السلام عليكم",
                    platform = Platform.ANDROID,
                    confidence = 0.90
                ),
                ActionCommand(
                    action = ActionType.UI_CLICK,
                    target = "إرسال",
                    platform = Platform.ANDROID,
                    confidence = 0.90
                )
            )
        )

        val decision = SafetyGate.evaluatePlan(plan)
        assertTrue(decision is SafetyDecision.AllowSilent)
    }

    @Test
    fun test05_multiStepPlanRequireConfirm() {
        val plan = MultiStepPlan(
            steps = listOf(
                ActionCommand(
                    action = ActionType.UI_CLICK,
                    target = "تحديد الكل",
                    platform = Platform.ANDROID,
                    confidence = 0.90
                ),
                ActionCommand(
                    action = ActionType.DELETE_FILE,
                    target = "old_cache.tmp",
                    platform = Platform.ANDROID,
                    confidence = 0.95
                )
            )
        )

        val decision = SafetyGate.evaluatePlan(plan)
        assertTrue(decision is SafetyDecision.RequireConfirm)
        assertTrue((decision as SafetyDecision.RequireConfirm).reason.contains("Tier 2"))
    }

    @Test
    fun test06_multiStepPlanBlock() {
        val plan = MultiStepPlan(
            steps = listOf(
                ActionCommand(
                    action = ActionType.UI_CLICK,
                    target = "Next",
                    platform = Platform.ANDROID,
                    confidence = 0.90
                ),
                ActionCommand(
                    action = ActionType.OPEN_FILE,
                    target = "../../etc/shadow",
                    platform = Platform.ANDROID,
                    confidence = 0.95
                )
            )
        )

        val decision = SafetyGate.evaluatePlan(plan)
        assertTrue(decision is SafetyDecision.Block)
        assertTrue((decision as SafetyDecision.Block).reason.contains("Path traversal"))
    }

    @Test
    fun test07_fastRuleRouterArabicNavigation() {
        for (q in listOf("ارجع", "رجوع", "للخلف", "ارجع للخلف", "الخلف")) {
            val cmd = FastRuleRouter.route(q)
            assertNotNull("Expected match for '$q'", cmd)
            assertEquals(ActionType.UI_BACK, cmd!!.action)
        }

        for (q in listOf("الرئيسية", "الشاشة الرئيسية", "الصفحه الرئيسيه", "سطح المكتب")) {
            val cmd = FastRuleRouter.route(q)
            assertNotNull("Expected match for '$q'", cmd)
            assertEquals(ActionType.UI_HOME, cmd!!.action)
        }
    }

    @Test
    fun test08_fastRuleRouterArabicScroll() {
        for (q in listOf("انزل", "انزل تحت", "انزل لتحت", "اسفل")) {
            val cmd = FastRuleRouter.route(q)
            assertNotNull("Expected match for '$q'", cmd)
            assertEquals(ActionType.UI_SCROLL, cmd!!.action)
            assertEquals("down", cmd.target)
        }

        for (q in listOf("اطلع", "ارفع", "اطلع فوق", "اطلع لفوق", "اعلى")) {
            val cmd = FastRuleRouter.route(q)
            assertNotNull("Expected match for '$q'", cmd)
            assertEquals(ActionType.UI_SCROLL, cmd!!.action)
            assertEquals("up", cmd.target)
        }
    }

    @Test
    fun test09_fastRuleRouterVolumeAndClick() {
        val cmdVol = FastRuleRouter.route("اضبط الصوت 70")
        assertNotNull(cmdVol)
        assertEquals(ActionType.SET_VOLUME, cmdVol!!.action)
        assertEquals("70", cmdVol.target)

        val cmdClickAr = FastRuleRouter.route("اضغط على حفظ التغييرات")
        assertNotNull(cmdClickAr)
        assertEquals(ActionType.UI_CLICK, cmdClickAr!!.action)
        assertEquals("حفظ التغييرات", cmdClickAr.target)

        val cmdClickEn = FastRuleRouter.route("click on Submit")
        assertNotNull(cmdClickEn)
        assertEquals(ActionType.UI_CLICK, cmdClickEn!!.action)
        assertEquals("Submit", cmdClickEn.target)
    }

    @Test
    fun test10_arabicTextNormalizationParity() {
        // Alef variants
        assertEquals("احمد", SilentExecAccessibilityService.normalizeText("أحمد"))
        assertEquals("ابراهيم", SilentExecAccessibilityService.normalizeText("إبراهيم"))
        assertEquals("اخر", SilentExecAccessibilityService.normalizeText("آخر"))

        // Teh Marbuta & Alef Maksura
        assertEquals("شاشه", SilentExecAccessibilityService.normalizeText("شاشة"))
        assertEquals("علي", SilentExecAccessibilityService.normalizeText("على"))

        // Tashkeel removal
        assertEquals("مرحبا بك", SilentExecAccessibilityService.normalizeText("مَرْحَبَاً بِكَ"))

        // Tatweel removal
        assertEquals("تحميل", SilentExecAccessibilityService.normalizeText("تـحـمـيـل"))
    }

    @Test
    fun test11_fastRuleRouterEnglishAndFallback() {
        val cmdBack = FastRuleRouter.route("back")
        assertNotNull(cmdBack)
        assertEquals(ActionType.UI_BACK, cmdBack!!.action)

        val cmdHome = FastRuleRouter.route("home screen")
        assertNotNull(cmdHome)
        assertEquals(ActionType.UI_HOME, cmdHome!!.action)

        val cmdApp = FastRuleRouter.route("open Telegram")
        assertNotNull(cmdApp)
        assertEquals(ActionType.OPEN_ANDROID_APP, cmdApp!!.action)
        assertEquals("Telegram", cmdApp.target)

        // Complex natural input must yield null
        val complex = FastRuleRouter.route("قم بتحليل المستندات وارسال تقرير الى احمد")
        assertNull(complex)
    }

    @Test
    fun test12_compoundCommandInterceptionAndFastRuleTiming() {
        // Compound commands must be intercepted and return null
        val compoundAr = "افتح واتساب وابحث عن أحمد وارسل له مرحبا"
        assertTrue(FastRuleRouter.isCompoundCommand(compoundAr))
        assertNull(FastRuleRouter.route(compoundAr))

        val compoundEn = "open chrome and search for rust"
        assertTrue(FastRuleRouter.isCompoundCommand(compoundEn))
        assertNull(FastRuleRouter.route(compoundEn))

        // Simple commands must match in < 15ms
        val startBack = System.nanoTime()
        val cmdBack = FastRuleRouter.route("ارجع")
        val elapsedBackMs = (System.nanoTime() - startBack) / 1_000_000.0
        assertNotNull(cmdBack)
        assertEquals(ActionType.UI_BACK, cmdBack!!.action)
        assertTrue("Expected < 15ms, took ${elapsedBackMs}ms", elapsedBackMs < 15.0)

        // Simple navigation and app commands match in < 15ms
        val startHome = System.nanoTime()
        val cmdHome = FastRuleRouter.route("الرئيسية")
        val elapsedHomeMs = (System.nanoTime() - startHome) / 1_000_000.0
        assertNotNull(cmdHome)
        assertEquals(ActionType.UI_HOME, cmdHome!!.action)
        assertTrue("Expected < 15ms, took ${elapsedHomeMs}ms", elapsedHomeMs < 15.0)
    }
}
