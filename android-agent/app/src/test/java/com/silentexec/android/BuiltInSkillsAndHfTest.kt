package com.silentexec.android

import com.silentexec.android.core.ActionCommand
import com.silentexec.android.core.ActionType
import com.silentexec.android.core.AppSettings
import com.silentexec.android.core.ExecutionMode
import com.silentexec.android.core.InferenceSource
import com.silentexec.android.core.Platform
import com.silentexec.android.exec.AgentLoopRunner
import com.silentexec.android.exec.AgentStepUpdate
import com.silentexec.android.exec.AndroidActionExecutor
import com.silentexec.android.net.GitHubReleaseClient
import com.silentexec.android.nlp.OnDeviceLlamaEngine
import com.silentexec.android.skills.BuiltInSkills
import com.silentexec.android.skills.SkillStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BuiltInSkillsAndHfTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setup() {
        SkillStore.clear()
    }

    @Test
    fun test01_youtubeCompoundSkillArabicAndEnglish() {
        val arSteps = BuiltInSkills.resolve("افتح اليوتيوب و ابحث عن nlp")
        assertNotNull(arSteps)
        assertEquals(1, arSteps!!.size)
        assertEquals(ActionType.OPEN_URL, arSteps[0].action)
        assertEquals("https://www.youtube.com/results?search_query=nlp", arSteps[0].target)

        val enSteps = BuiltInSkills.resolve("open YouTube and search for nlp")
        assertNotNull(enSteps)
        assertEquals(1, enSteps!!.size)
        assertEquals(ActionType.OPEN_URL, enSteps[0].action)
        assertEquals("https://www.youtube.com/results?search_query=nlp", enSteps[0].target)
    }

    @Test
    fun test02_webSearchAndCustomSkillRoutine() {
        val webSteps = BuiltInSkills.resolve("افتح المتصفح وابحث عن kotlin coroutines")
        assertNotNull(webSteps)
        assertEquals(ActionType.OPEN_URL, webSteps!![0].action)
        assertEquals("https://www.google.com/search?q=kotlin+coroutines", webSteps[0].target)

        SkillStore.saveSkill(
            name = "وضع الدراسة",
            triggers = listOf("Work Mode"),
            steps = listOf(
                ActionCommand(action = ActionType.SET_VOLUME, target = "25", platform = Platform.ANDROID),
                ActionCommand(action = ActionType.OPEN_URL, target = "https://github.com", platform = Platform.ANDROID)
            ),
            description = "Study routine"
        )

        val custom = BuiltInSkills.resolve("Work Mode")
        assertNotNull(custom)
        assertEquals(2, custom!!.size)
        assertEquals(ActionType.SET_VOLUME, custom[0].action)
        assertEquals(ActionType.OPEN_URL, custom[1].action)
    }

    @Test
    fun test03_huggingfaceTreeJsonParsingForLfm3AndVerifiedQwen3Url() {
        val sampleHfTree = """
            [
              {"type":"file","size":3466626336,"path":"LFM3-1.7B-BF16.gguf"},
              {"type":"file","size":1099627808,"path":"LFM3-1.7B-Q4_K_M.gguf"},
              {"type":"file","size":1258663200,"path":"LFM3-1.7B-Q5_K_M.gguf"}
            ]
        """.trimIndent()

        val client = GitHubReleaseClient()
        val parsed = client.parseHuggingFaceTreeQ4KM(
            repoId = "LiquidAI/LFM3-1.7B-GGUF",
            jsonText = sampleHfTree,
            description = "LiquidAI/LFM3-1.7B (Fast Edge Agent)"
        )

        assertNotNull(parsed)
        assertEquals("LFM3-1.7B-Q4_K_M.gguf", parsed!!.filename)
        assertEquals(1_099_627_808L, parsed.sizeBytes)
        assertEquals(
            "https://huggingface.co/LiquidAI/LFM3-1.7B-GGUF/resolve/main/LFM3-1.7B-Q4_K_M.gguf?download=true",
            parsed.downloadUrl
        )

        val qwenCurated = GitHubReleaseClient.CURATED_EDGE_MODELS.first { it.filename.contains("Qwen3") }
        assertEquals("Qwen3-1.7B-Q4_K_M.gguf", qwenCurated.filename)
        assertEquals(
            "https://huggingface.co/unsloth/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf?download=true",
            qwenCurated.downloadUrl
        )
    }

    @Test
    fun test04_agentLoopRunnerDuplicateStepAndEmptyTargetGuard() {
        val settings = AppSettings().apply {
            fastRouterEnabled = false
            executionMode = ExecutionMode.SILENT
            inferenceSource = InferenceSource.OFFLINE
        }
        val executor = AndroidActionExecutor(null)

        // 1. Empty target on open_android_app halts cleanly with model upgrade hint
        var callCount1 = 0
        val runner1 = AgentLoopRunner(
            executor = executor,
            settings = settings,
            stepDelayMs = 0L,
            uiObserver = { emptyList() },
            decider = {
                callCount1++
                if (callCount1 == 1) {
                    """{"action":"open_android_app","target":"Notes","platform":"android","confidence":0.9}"""
                } else {
                    """{"action":"open_android_app","target":"","platform":"android","confidence":0.5}"""
                }
            }
        )

        val updates1 = mutableListOf<AgentStepUpdate>()
        runner1.run("افتح الملاحظات ثم افعل شيئا") { updates1.add(it) }
        val failed1 = updates1.filterIsInstance<AgentStepUpdate.StepFailed>().lastOrNull()
        assertNotNull(failed1)
        assertTrue(failed1!!.error.contains("Qwen3-1.7B"))
        assertFalse(failed1.error.contains("Target application name is empty"))

        // 2. Identical duplicate step halts cleanly
        val runner2 = AgentLoopRunner(
            executor = executor,
            settings = settings,
            stepDelayMs = 0L,
            uiObserver = { emptyList() },
            decider = {
                """{"action":"open_android_app","target":"YouTube","platform":"android","confidence":0.8}"""
            }
        )
        val updates2 = mutableListOf<AgentStepUpdate>()
        runner2.run("كرر فتح اليوتيوب") { updates2.add(it) }
        val failed2 = updates2.filterIsInstance<AgentStepUpdate.StepFailed>().lastOrNull()
        assertNotNull(failed2)
        assertTrue(failed2!!.error.contains("Qwen3-1.7B"))
    }

    @Test
    fun test05_llamaEngineValidatesMinimum5MbFileSize() {
        val engine = OnDeviceLlamaEngine()
        val tinyFile = File(tempFolder.root, "tiny.gguf")
        tinyFile.writeBytes(ByteArray(1024)) // 1 KB < 5 MB

        val err = engine.validateModelFile(tinyFile)
        assertNotNull(err)
        assertTrue(err!!.contains("5"))
        assertFalse(engine.loadModel(tinyFile))
    }
}
