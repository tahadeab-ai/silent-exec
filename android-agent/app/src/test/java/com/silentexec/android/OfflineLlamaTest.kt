package com.silentexec.android

import com.silentexec.android.core.ActionCommand
import com.silentexec.android.core.ActionJson
import com.silentexec.android.core.ActionType
import com.silentexec.android.core.Platform
import com.silentexec.android.net.ModelDownloader
import com.silentexec.android.nlp.OnDeviceLlamaEngine
import com.silentexec.android.nlp.PromptFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.StandardCharsets

class OfflineLlamaTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun test01_chatmlPromptEnglish() {
        val query = "Open Firefox"
        val prompt = PromptFormatter.formatPrompt(query)

        assertTrue(prompt.startsWith("<|im_start|>system\n"))
        assertTrue(prompt.contains(PromptFormatter.DEFAULT_SYSTEM_PROMPT))
        assertTrue(prompt.contains("<|im_end|>\n<|im_start|>user\nOpen Firefox<|im_end|>\n<|im_start|>assistant\n"))
    }

    @Test
    fun test02_chatmlPromptMsaArabic() {
        val query = "افتح المتصفح"
        val prompt = PromptFormatter.formatPrompt(query)

        assertTrue(prompt.contains("<|im_start|>user\nافتح المتصفح<|im_end|>"))
        assertTrue(prompt.endsWith("<|im_start|>assistant\n"))
    }

    @Test
    fun test03_chatmlPromptYemeniArabic() {
        val query = "شغل لي تطبيق الواتساب الان"
        val prompt = PromptFormatter.formatPrompt(query)

        assertTrue(prompt.contains("<|im_start|>user\nشغل لي تطبيق الواتساب الان<|im_end|>"))
        assertTrue(prompt.endsWith("<|im_start|>assistant\n"))
    }

    @Test
    fun test04_extractActionJsonClean() {
        val raw = """{"action":"open_android_app","target":"com.whatsapp","platform":"android","confidence":0.95}"""
        val extracted = PromptFormatter.extractActionJson(raw)

        val cmd = ActionJson.fromJson(extracted)
        assertEquals(ActionType.OPEN_ANDROID_APP, cmd.action)
        assertEquals("com.whatsapp", cmd.target)
        assertEquals(Platform.ANDROID, cmd.platform)
        assertEquals(0.95, cmd.confidence, 0.001)
    }

    @Test
    fun test05_extractActionJsonWithCodeFencesAndEosToken() {
        val raw = """
            ```json
            {
                "action": "open_url",
                "target": "https://news.ycombinator.com",
                "platform": "android",
                "confidence": 0.99
            }
            ```
            <|im_end|>
        """.trimIndent()

        val extracted = PromptFormatter.extractActionJson(raw)
        val cmd = ActionJson.fromJson(extracted)
        assertEquals(ActionType.OPEN_URL, cmd.action)
        assertEquals("https://news.ycombinator.com", cmd.target)
    }

    @Test
    fun test06_extractActionJsonRejectsEnglishChatter() {
        val chatterOutputs = listOf(
            """Sure! Here is the JSON: {"action":"open_url","target":"https://google.com","platform":"android","confidence":0.9}""",
            """{"action":"open_url","target":"https://google.com","platform":"android","confidence":0.9} Here you go!""",
            """Certainly, I can do that for you: {"action":"launch_app","target":"calc","platform":"android","confidence":0.9}"""
        )

        for (chatter in chatterOutputs) {
            try {
                PromptFormatter.extractActionJson(chatter)
                fail("Expected IllegalArgumentException for conversational chatter: $chatter")
            } catch (expected: IllegalArgumentException) {
                assertTrue(
                    expected.message!!.contains("Conversational chatter") ||
                    expected.message!!.contains("prefix") ||
                    expected.message!!.contains("suffix")
                )
            }
        }
    }

    @Test
    fun test07_extractActionJsonRejectsArabicChatter() {
        val arabicChatter = listOf(
            """تفضل هذا هو الأمر: {"action":"set_volume","target":"50","platform":"android","confidence":0.9}""",
            """{"action":"set_volume","target":"50","platform":"android","confidence":0.9} حاضر سأقوم بذلك""",
            """بالتأكيد! إليك الناتج المطلوبة: {"action":"open_android_app","target":"camera","platform":"android","confidence":0.9}"""
        )

        for (chatter in arabicChatter) {
            try {
                PromptFormatter.extractActionJson(chatter)
                fail("Expected IllegalArgumentException for Arabic chatter: $chatter")
            } catch (expected: IllegalArgumentException) {
                assertTrue(
                    expected.message!!.contains("Conversational chatter") ||
                    expected.message!!.contains("prefix") ||
                    expected.message!!.contains("suffix")
                )
            }
        }
    }

    @Test
    fun test08_modelDownloaderSha256VerificationValidAndCorrupt() {
        val modelsDir = tempFolder.newFolder("models")
        val downloader = ModelDownloader(modelsDir)

        val testFile = File(modelsDir, "test-model.gguf")
        testFile.writeText("SilentExec GGUF dummy weights test payload", StandardCharsets.UTF_8)

        // Known SHA-256 for this exact string:
        val expectedSha = downloader.computeSha256(testFile)
        assertNotNull(expectedSha)
        assertEquals(64, expectedSha.length)

        // Valid verification
        assertTrue(downloader.verifySha256(testFile, expectedSha))
        assertTrue(downloader.verifySha256(testFile, expectedSha.uppercase()))

        // Corrupt verification
        val corruptSha = "0000000000000000000000000000000000000000000000000000000000000000"
        assertFalse(downloader.verifySha256(testFile, corruptSha))
    }

    @Test
    fun test09_modelDownloaderParseChecksumsTxt() {
        val checksums = """
            a1b2c3d4e5f67890123456789abcdef0123456789abcdef0123456789abcdef0  Qwen2.5-0.5B-Instruct.Q4_K_M.gguf
            11223344556677889900aabbccddeeff11223344556677889900aabbccddeeff *other-model.gguf
        """.trimIndent()

        val downloader = ModelDownloader(tempFolder.newFolder("models_parse"))

        val hash1 = downloader.parseExpectedSha256(checksums, "Qwen2.5-0.5B-Instruct.Q4_K_M.gguf")
        assertEquals("a1b2c3d4e5f67890123456789abcdef0123456789abcdef0123456789abcdef0", hash1)

        val hash2 = downloader.parseExpectedSha256(checksums, "other-model.gguf")
        assertEquals("11223344556677889900aabbccddeeff11223344556677889900aabbccddeeff", hash2)

        val hashMissing = downloader.parseExpectedSha256(checksums, "non-existent.gguf")
        assertEquals(null, hashMissing)
    }

    @Test
    fun test10_llamaEngineGracefulNativeFallback() {
        val engine = OnDeviceLlamaEngine()
        // In JVM unit tests without native lib, isNativeLoaded is false
        if (!OnDeviceLlamaEngine.isNativeLoaded) {
            assertFalse(engine.isLoaded())
            val dummyFile = File(tempFolder.root, "dummy.gguf")
            assertFalse(engine.loadModel(dummyFile))

            val inferResult = engine.inferActionCommand("Open Chrome")
            assertTrue(inferResult.isFailure)
        }
    }
}
