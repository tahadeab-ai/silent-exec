package com.silentexec.android

import com.silentexec.android.core.ActionCommand
import com.silentexec.android.core.ActionJson
import com.silentexec.android.core.ActionType
import com.silentexec.android.core.MemoryStore
import com.silentexec.android.core.Platform
import com.silentexec.android.core.SafetyDecision
import com.silentexec.android.core.SafetyGate
import com.silentexec.android.core.SafetyTier
import com.silentexec.android.exec.AndroidActionExecutor
import com.silentexec.android.exec.AndroidExecutionResult
import com.silentexec.android.nlp.FastRuleRouter
import com.silentexec.android.nlp.OnDeviceLlamaEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class HermesAgentAndBugfixTest {

    @Before
    fun setup() {
        MemoryStore.clear()
    }

    @Test
    fun test01_volumePercentageAccurateCalculation() {
        // 80% on a 15-step stream should be exactly 12/15
        val index80 = AndroidActionExecutor.calculateVolumeIndex(80, 15)
        assertEquals(12, index80)

        // 50% on 15-step should be 8/15 (Math.round(7.5) = 8)
        val index50 = AndroidActionExecutor.calculateVolumeIndex(50, 15)
        assertEquals(8, index50)

        // 0% on 15-step should be 0/15
        val index0 = AndroidActionExecutor.calculateVolumeIndex(0, 15)
        assertEquals(0, index0)

        // Simulated executor execution
        val executor = AndroidActionExecutor(context = null)
        val cmd = ActionCommand(
            action = ActionType.SET_VOLUME,
            target = "80",
            platform = Platform.ANDROID,
            confidence = 0.95
        )
        val result = executor.execute(cmd)
        assertTrue(result is AndroidExecutionResult.Success)
        val success = result as AndroidExecutionResult.Success
        assertEquals("12", success.targetResolved)
    }

    @Test
    fun test02_muteArabicRouting() {
        val cmd = FastRuleRouter.route("اطفئ الصوت")
        assertNotNull(cmd)
        assertEquals(ActionType.SET_VOLUME, cmd!!.action)
        assertEquals("0", cmd.target)

        val cmdKatm = FastRuleRouter.route("اكتم الصوت")
        assertNotNull(cmdKatm)
        assertEquals(ActionType.SET_VOLUME, cmdKatm!!.action)
        assertEquals("0", cmdKatm.target)
    }

    @Test
    fun test03_muteEnglishRouting() {
        val cmd = FastRuleRouter.route("mute")
        assertNotNull(cmd)
        assertEquals(ActionType.SET_VOLUME, cmd!!.action)
        assertEquals("0", cmd.target)

        val cmdSilence = FastRuleRouter.route("silence volume")
        assertNotNull(cmdSilence)
        assertEquals(ActionType.SET_VOLUME, cmdSilence!!.action)
        assertEquals("0", cmdSilence.target)
    }

    @Test
    fun test04_lenientPlatformYouTubeDoesNotCrash() {
        // LLMs frequently emit application names like "YouTube" in the platform field
        val json = """
            {
                "action": "ui_click",
                "target": "Subscribe",
                "platform": "YouTube",
                "confidence": 0.95
            }
        """.trimIndent()

        val cmd = ActionJson.fromJson(json)
        assertEquals(ActionType.UI_CLICK, cmd.action)
        assertEquals("Subscribe", cmd.target)
        assertEquals(Platform.ANY, cmd.platform)
        assertEquals(0.95, cmd.confidence, 0.001)
    }

    @Test
    fun test05_lenientPlatformWindowsMapping() {
        val json = """
            {
                "action": "launch_app",
                "target": "notepad",
                "platform": "Windows",
                "confidence": 0.90
            }
        """.trimIndent()

        val cmd = ActionJson.fromJson(json)
        assertEquals(ActionType.LAUNCH_APP, cmd.action)
        assertEquals(Platform.PC, cmd.platform)
    }

    @Test
    fun test06_missingPlatformDefaultsToAndroid() {
        val json = """
            {
                "action": "ui_home",
                "confidence": 0.90
            }
        """.trimIndent()

        val cmd = ActionJson.fromJson(json)
        assertEquals(ActionType.UI_HOME, cmd.action)
        assertEquals(Platform.ANDROID, cmd.platform)
    }

    @Test
    fun test07_missingConfidenceDefaultsTo85Percent() {
        val json = """
            {
                "action": "ui_back",
                "platform": "android"
            }
        """.trimIndent()

        val cmd = ActionJson.fromJson(json)
        assertEquals(ActionType.UI_BACK, cmd.action)
        assertEquals(0.85, cmd.confidence, 0.001)
    }

    @Test
    fun test08_memoryStoreSaveAndList() {
        val saved = MemoryStore.saveMemory("preferred_language", "Arabic", "preferences")
        assertEquals("preferred_language", saved.key)
        assertEquals("Arabic", saved.content)

        val list = MemoryStore.listMemories()
        assertTrue(list.any { it.key == "preferred_language" && it.content == "Arabic" })
    }

    @Test
    fun test09_memoryStoreDelete() {
        MemoryStore.saveMemory("temp_token", "xyz123", "tokens")
        assertTrue(MemoryStore.listMemories().any { it.key == "temp_token" })

        val deleted = MemoryStore.deleteMemory("temp_token")
        assertTrue(deleted)
        assertTrue(MemoryStore.listMemories().none { it.key == "temp_token" })
    }

    @Test
    fun test10_memoryStoreRelevanceQuery() {
        MemoryStore.saveMemory("projects_path", "D:\\Projects\\Android", "paths")
        MemoryStore.saveMemory("user_name", "Taha", "profile")

        val relevant = MemoryStore.getRelevantMemories("projects")
        assertEquals(1, relevant.size)
        assertEquals("projects_path", relevant[0].key)
    }

    @Test
    fun test11_replyIsTier1Silent() {
        assertEquals(SafetyTier.TIER_1_SILENT, ActionType.REPLY.tier)
        val cmd = ActionCommand(
            action = ActionType.REPLY,
            target = "Here is the response text",
            platform = Platform.ANDROID,
            confidence = 0.95
        )
        val decision = SafetyGate.evaluate(cmd)
        assertTrue(decision is SafetyDecision.AllowSilent)
    }

    @Test
    fun test12_writeFileIsTier2Confirm() {
        assertEquals(SafetyTier.TIER_2_CONFIRM, ActionType.WRITE_FILE.tier)
        val cmd = ActionCommand(
            action = ActionType.WRITE_FILE,
            target = "/sdcard/output.txt",
            platform = Platform.ANDROID,
            confidence = 0.95
        )
        val decision = SafetyGate.evaluate(cmd)
        assertTrue(decision is SafetyDecision.RequireConfirm)
    }

    @Test
    fun test13_onDeviceLlamaEngineHandlesMissingModelGracefully() {
        val engine = OnDeviceLlamaEngine()
        val missingFile = File("/tmp/non_existent_model_${System.currentTimeMillis()}.gguf")

        // Loading non-existent model returns false cleanly without crashing
        val loadOk = engine.loadModel(missingFile)
        assertEquals(false, loadOk)

        // Inferring with no model loaded returns Result.failure with helpful message
        val inferResult = engine.inferActionCommand("open settings")
        assertTrue(inferResult.isFailure)
        val msg = inferResult.exceptionOrNull()?.message ?: ""
        assertTrue(
            "Message should direct user to download model: $msg",
            msg.contains("يرجى تحميل نموذج")
        )
    }

    @Test
    fun test14_fastRuleRouterGuardsCompoundCommands() {
        val compound = "افتح اليوتيوب وابحث عن قطط"
        assertTrue(FastRuleRouter.isCompoundCommand(compound))
        assertNull(FastRuleRouter.route(compound))

        val compoundEn = "open youtube and search for cats"
        assertTrue(FastRuleRouter.isCompoundCommand(compoundEn))
        assertNull(FastRuleRouter.route(compoundEn))
    }

    @Test
    fun test15_lfm3DuplicateKeysExtraIndexAndOpenUrlNormalization() {
        val rawLfm3Output =
            """{"action":"open_url","platform":"android","confidence":0.88,"thought":"navigate to YouTube","target":"ابحث عن nlp","platform":"android","index":10}"""

        val parsed = com.silentexec.android.nlp.PromptFormatter.parsePlanOrSingleAction(rawLfm3Output)
        assertTrue(parsed is com.silentexec.android.nlp.PlanOrAction.Action)
        val cmd = (parsed as com.silentexec.android.nlp.PlanOrAction.Action).actionCommand

        assertEquals(ActionType.OPEN_URL, cmd.action)
        assertEquals(Platform.ANDROID, cmd.platform)
        assertEquals(0.88, cmd.confidence, 0.001)
        assertEquals("navigate to YouTube", cmd.thought)
        assertEquals("https://www.youtube.com/results?search_query=nlp", cmd.target)
    }
}
