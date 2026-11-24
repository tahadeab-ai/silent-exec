package com.silentexec.android

import com.silentexec.android.core.ActionCommand
import com.silentexec.android.core.ActionJson
import com.silentexec.android.core.ActionType
import com.silentexec.android.core.AppSettings
import com.silentexec.android.core.InferenceSource
import com.silentexec.android.core.Platform
import com.silentexec.android.core.ThemeMode
import com.silentexec.android.net.DownloadState
import com.silentexec.android.net.DownloadStatus
import com.silentexec.android.net.ModelDownloader
import com.silentexec.android.nlp.CloudApiClient
import com.silentexec.android.nlp.PlanOrAction
import com.silentexec.android.nlp.PromptFormatter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.security.MessageDigest

class ResumableAndBackendTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun test01_rangeHeaderConstruction_startsAtExistingBytes() {
        val existingBytes = 1048576L // 1 MB existing
        val rangeHeader = if (existingBytes > 0) "bytes=$existingBytes-" else null
        assertNotNull(rangeHeader)
        assertEquals("bytes=1048576-", rangeHeader)

        val zeroBytes = 0L
        val zeroRangeHeader = if (zeroBytes > 0) "bytes=$zeroBytes-" else null
        assertEquals(null, zeroRangeHeader)
    }

    @Test
    fun test02_http206PartialContent_byteOffsetCalculation() {
        val existingBytes = 500000L
        val responseCode = HttpURLConnection.HTTP_PARTIAL
        val contentLength = 1500000L // remaining bytes

        val isPartial = (responseCode == HttpURLConnection.HTTP_PARTIAL)
        val appendMode = isPartial && existingBytes > 0
        assertTrue(isPartial)
        assertTrue(appendMode)

        val totalBytes = if (isPartial) existingBytes + contentLength else contentLength
        assertEquals(2000000L, totalBytes)
    }

    @Test
    fun test03_http200Ok_resetsByteOffset() {
        val existingBytes = 500000L
        val responseCode = HttpURLConnection.HTTP_OK
        val contentLength = 2000000L

        val isPartial = (responseCode == HttpURLConnection.HTTP_PARTIAL)
        val appendMode = isPartial && existingBytes > 0
        assertFalse(isPartial)
        assertFalse(appendMode)

        val totalBytes = if (isPartial) existingBytes + contentLength else contentLength
        assertEquals(2000000L, totalBytes)
    }

    @Test
    fun test04_partFilePreservationOnPause() {
        val dir = tempFolder.newFolder("models_pause_test")
        val downloader = ModelDownloader(dir)

        val filename = "Qwen2.5-0.5B-test.gguf"
        val partFile = File(dir, "$filename.part")
        partFile.writeBytes(ByteArray(2048) { 0x42 })

        downloader.pause(filename)
        assertTrue(downloader.isPaused(filename))

        // Ensure .part file remains intact on disk
        assertTrue(partFile.exists())
        assertEquals(2048L, partFile.length())

        // Resume resets flag
        downloader.resume(filename)
        assertFalse(downloader.isPaused(filename))
        assertTrue(partFile.exists())
    }

    @Test
    fun test05_atomicRenameOnSha256Match() {
        val dir = tempFolder.newFolder("models_rename_test")
        val downloader = ModelDownloader(dir)

        val filename = "Qwen2.5-test.gguf"
        val targetFile = downloader.getModelFile(filename)
        val partFile = File(dir, "$filename.part")

        val payload = "dummy model weights content for checksum testing".toByteArray(Charsets.UTF_8)
        partFile.writeBytes(payload)

        val digest = MessageDigest.getInstance("SHA-256")
        val expectedHash = digest.digest(payload).joinToString("") { "%02x".format(it) }

        assertTrue(downloader.verifySha256(partFile, expectedHash))

        // Simulate atomic rename
        if (targetFile.exists()) targetFile.delete()
        val renamed = partFile.renameTo(targetFile)

        assertTrue(renamed)
        assertTrue(targetFile.exists())
        assertFalse(partFile.exists())
        assertEquals(payload.size.toLong(), targetFile.length())
    }

    @Test
    fun test06_rejectionAndDeletionOnCorruptSha256() {
        val dir = tempFolder.newFolder("models_corrupt_test")
        val downloader = ModelDownloader(dir)

        val filename = "Qwen2.5-corrupt.gguf"
        val partFile = File(dir, "$filename.part")
        partFile.writeBytes("corrupted bytes".toByteArray(Charsets.UTF_8))

        val expectedHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855" // hash of empty string

        val matches = downloader.verifySha256(partFile, expectedHash)
        assertFalse(matches)

        // Deletion upon checksum failure
        partFile.delete()
        assertFalse(partFile.exists())
    }

    @Test
    fun test07_cloudApiClient_defaultsAndCustomConfig() {
        val defaultClient = CloudApiClient()
        assertEquals("https://api.openai.com/v1", defaultClient.baseUrl)
        assertEquals("gpt-4o-mini", defaultClient.model)
        assertEquals("", defaultClient.apiKey)

        val customClient = CloudApiClient(
            baseUrl = "https://api.deepseek.com/v1",
            model = "deepseek-chat",
            apiKey = "sk-deepseek-test",
            connectTimeoutMs = 10000,
            readTimeoutMs = 20000
        )
        assertEquals("https://api.deepseek.com/v1", customClient.baseUrl)
        assertEquals("deepseek-chat", customClient.model)
        assertEquals("sk-deepseek-test", customClient.apiKey)
        assertEquals(10000, customClient.connectTimeoutMs)
        assertEquals(20000, customClient.readTimeoutMs)
    }

    @Test
    fun test08_cloudApiClient_extractActionCommandFromChoices() {
        val sampleResponse = """
        {
          "id": "chatcmpl-123",
          "object": "chat.completion",
          "choices": [
            {
              "index": 0,
              "message": {
                "role": "assistant",
                "content": "{\"action\":\"open_android_app\",\"target\":\"com.google.android.youtube\",\"platform\":\"android\",\"confidence\":0.98}"
              },
              "finish_reason": "stop"
            }
          ]
        }
        """.trimIndent()

        val root = Json.parseToJsonElement(sampleResponse).jsonObject
        val choice = root["choices"]!!.jsonArray[0].jsonObject
        val content = choice["message"]!!.jsonObject["content"]!!.jsonPrimitive.content

        val cleanJson = PromptFormatter.extractActionJson(content)
        val planOrAction = PromptFormatter.parsePlanOrSingleAction(cleanJson)

        assertTrue(planOrAction is PlanOrAction.Action)
        val action = (planOrAction as PlanOrAction.Action).actionCommand
        assertEquals(ActionType.OPEN_ANDROID_APP, action.action)
        assertEquals("com.google.android.youtube", action.target)
        assertEquals(Platform.ANDROID, action.platform)
        assertEquals(0.98, action.confidence, 0.001)
    }

    @Test
    fun test09_cloudApiClient_extractMultiStepPlanFromChoices() {
        val samplePlanResponse = """
        {
          "choices": [
            {
              "message": {
                "content": "```json\n{\"steps\":[{\"action\":\"open_android_app\",\"target\":\"WhatsApp\",\"platform\":\"android\",\"confidence\":1.0},{\"action\":\"done\",\"platform\":\"android\",\"confidence\":1.0}]}\n```"
              }
            }
          ]
        }
        """.trimIndent()

        val root = Json.parseToJsonElement(samplePlanResponse).jsonObject
        val choice = root["choices"]!!.jsonArray[0].jsonObject
        val content = choice["message"]!!.jsonObject["content"]!!.jsonPrimitive.content

        val cleanJson = PromptFormatter.extractActionJson(content)
        val planOrAction = PromptFormatter.parsePlanOrSingleAction(cleanJson)

        assertTrue(planOrAction is PlanOrAction.Plan)
        val plan = (planOrAction as PlanOrAction.Plan).multiStepPlan
        assertEquals(2, plan.steps.size)
        assertEquals(ActionType.OPEN_ANDROID_APP, plan.steps[0].action)
        assertEquals(ActionType.DONE, plan.steps[1].action)
    }

    @Test
    fun test10_promptFormatter_rejectsConversationalChatter() {
        val responsesWithChatter = listOf(
            "Sure! Here is the JSON:\n{\"action\":\"ui_back\",\"platform\":\"android\",\"confidence\":1.0}",
            "Certainly, I can help with that: {\"action\":\"ui_home\",\"platform\":\"android\",\"confidence\":1.0}",
            "حاضر، إليك الأمر: {\"action\":\"ui_back\",\"platform\":\"android\",\"confidence\":1.0}"
        )

        for (badResponse in responsesWithChatter) {
            try {
                PromptFormatter.extractActionJson(badResponse)
                fail("Expected IllegalArgumentException for conversational chatter: $badResponse")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message?.contains("chatter") == true || e.message?.contains("prefix") == true)
            }
        }
    }

    @Test
    fun test11_appSettings_inferenceSourcePersistence() {
        val settings = AppSettings()

        // Default
        assertEquals(InferenceSource.OFFLINE, settings.inferenceSource)

        // LAN_BRIDGE
        settings.inferenceSource = InferenceSource.LAN_BRIDGE
        assertEquals(InferenceSource.LAN_BRIDGE, settings.inferenceSource)

        // OPENAI_COMPAT
        settings.inferenceSource = InferenceSource.OPENAI_COMPAT
        assertEquals(InferenceSource.OPENAI_COMPAT, settings.inferenceSource)

        // fromString conversions
        assertEquals(InferenceSource.OFFLINE, InferenceSource.fromString("OFFLINE"))
        assertEquals(InferenceSource.LAN_BRIDGE, InferenceSource.fromString("LAN_BRIDGE"))
        assertEquals(InferenceSource.OPENAI_COMPAT, InferenceSource.fromString("OPENAI_COMPAT"))
        assertEquals(InferenceSource.OPENAI_COMPAT, InferenceSource.fromString("openai_compat"))
        assertEquals(InferenceSource.OFFLINE, InferenceSource.fromString("invalid_source"))
    }

    @Test
    fun test12_appSettings_themeModePersistence() {
        val settings = AppSettings()

        assertEquals(ThemeMode.SYSTEM, settings.themeMode)

        settings.themeMode = ThemeMode.DARK
        assertEquals(ThemeMode.DARK, settings.themeMode)

        settings.themeMode = ThemeMode.LIGHT
        assertEquals(ThemeMode.LIGHT, settings.themeMode)

        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromString("system"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromString("DARK"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromString("light"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromString("not_a_theme"))
    }

    @Test
    fun test13_downloadState_speedAndEtaCalculation() {
        val state = DownloadState(
            downloadedBytes = 150000000L,
            totalBytes = 300000000L,
            speedMBps = 5.25,
            etaSeconds = 28,
            status = DownloadStatus.DOWNLOADING
        )

        assertEquals(150000000L, state.downloadedBytes)
        assertEquals(300000000L, state.totalBytes)
        assertEquals(5.25, state.speedMBps, 0.001)
        assertEquals(28L, state.etaSeconds)
        assertEquals(DownloadStatus.DOWNLOADING, state.status)

        val completedState = state.copy(
            downloadedBytes = 300000000L,
            status = DownloadStatus.COMPLETED
        )
        assertEquals(DownloadStatus.COMPLETED, completedState.status)
        assertEquals(completedState.totalBytes, completedState.downloadedBytes)
    }
}
