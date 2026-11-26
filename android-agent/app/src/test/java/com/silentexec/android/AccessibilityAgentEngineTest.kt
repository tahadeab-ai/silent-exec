package com.silentexec.android

import com.silentexec.android.core.ActionCommand
import com.silentexec.android.core.ActionType
import com.silentexec.android.core.AppSettings
import com.silentexec.android.core.ExecutionMode
import com.silentexec.android.core.InferenceSource
import com.silentexec.android.core.Platform
import com.silentexec.android.exec.AccessibilityAgentEngine
import com.silentexec.android.exec.ActionPerformer
import com.silentexec.android.exec.AgentActionResult
import com.silentexec.android.exec.AgentTraceEvent
import com.silentexec.android.exec.ScreenObservation
import com.silentexec.android.exec.ScreenObserver
import com.silentexec.android.nlp.CloudApiClient
import com.silentexec.android.nlp.ProviderCatalog
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityAgentEngineTest {

    @Test
    fun test01_observeReasonActVerifyScreenChangedUntilDone() {
        val settings = AppSettings().apply {
            fastRouterEnabled = false
            executionMode = ExecutionMode.SILENT
            inferenceSource = InferenceSource.OPENAI_COMPAT
        }

        var screenStep = 0
        val observer = ScreenObserver {
            when (screenStep) {
                0 -> ScreenObservation("com.android.launcher", listOf("[0] button: YouTube [100,200]"))
                1 -> ScreenObservation("com.google.android.youtube", listOf("[0] input: Search [300,120]"))
                else -> ScreenObservation("com.google.android.youtube", listOf("[0] text: Results [300,400]"))
            }
        }

        val performer = ActionPerformer { cmd, _ ->
            screenStep++
            AgentActionResult(true, "Executed ${cmd.action.wireName}")
        }

        var turn = 0
        val decider: (String) -> String = { prompt ->
            turn++
            when (turn) {
                1 -> """{"thought":"Open YouTube","action":"open_android_app","target":"YouTube","platform":"android","confidence":0.95}"""
                2 -> {
                    assertTrue(prompt.contains("Previous action changed the screen."))
                    """{"thought":"Type search query","action":"ui_type","target":"#0","args":{"text":"nlp"},"platform":"android","confidence":0.94}"""
                }
                else -> """{"thought":"Search results loaded","action":"done","target":"YouTube search completed","platform":"android","confidence":0.99}"""
            }
        }

        val events = mutableListOf<AgentTraceEvent>()
        val engine = AccessibilityAgentEngine(
            settings = settings,
            observer = observer,
            performer = performer,
            customDecider = decider,
            settleDelayMs = 0L
        )

        engine.run("Open YouTube and search nlp") { events.add(it) }

        assertTrue(events.any { it is AgentTraceEvent.Observed && it.step == 1 })
        assertTrue(events.any { it is AgentTraceEvent.Observed && it.step == 2 && it.previousScreenChanged == true })
        val finished = events.filterIsInstance<AgentTraceEvent.Finished>().firstOrNull()
        assertNotNull(finished)
        assertEquals("YouTube search completed", finished!!.summary)
        assertEquals(3, finished.stepsTaken)
    }

    @Test
    fun test02_safetyConfirmationGateForSensitiveActions() {
        val settings = AppSettings().apply {
            fastRouterEnabled = false
            executionMode = ExecutionMode.SILENT
        }

        var performed = false
        val engine = AccessibilityAgentEngine(
            settings = settings,
            observer = ScreenObserver { ScreenObservation("com.whatsapp", listOf("[0] button: Send [900,1800]")) },
            performer = ActionPerformer { _, _ ->
                performed = true
                AgentActionResult(true, "Sent")
            },
            customDecider = {
                """{"thought":"Tap Send button","action":"ui_click","target":"Send","platform":"android","confidence":0.92}"""
            },
            settleDelayMs = 0L
        )

        val events = mutableListOf<AgentTraceEvent>()
        engine.run("Send message") { ev ->
            events.add(ev)
            if (ev is AgentTraceEvent.ConfirmRequired) {
                ev.respond(false)
            }
        }

        assertFalse(performed)
        assertTrue(events.any { it is AgentTraceEvent.ConfirmRequired })
        assertTrue(events.any { it is AgentTraceEvent.Cancelled })
        assertTrue(
            AccessibilityAgentEngine.isSensitiveUiTarget(
                ActionCommand(action = ActionType.DELETE_FILE, target = "notes.txt", platform = Platform.ANDROID)
            )
        )
    }

    @Test
    fun test03_providerCatalogAndCloudToolCallingParsing() {
        val settings = AppSettings()
        ProviderCatalog.applyPreset(settings, "GEMINI")
        assertEquals(InferenceSource.OPENAI_COMPAT, settings.inferenceSource)
        assertTrue(settings.openAiApiBase.contains("generativelanguage.googleapis.com"))
        assertEquals("GEMINI · gemini-2.5-flash", ProviderCatalog.activeDisplayLabel(settings))

        ProviderCatalog.applyPreset(settings, "OLLAMA")
        assertTrue(settings.openAiApiBase.contains("11434"))
        assertEquals("OLLAMA", ProviderCatalog.inferPresetId(settings))

        val reqBody = CloudApiClient.buildRequestBody(
            model = "gemini-2.5-flash",
            systemPrompt = "sys",
            userPrompt = "usr",
            useToolCalling = true
        )
        assertTrue(reqBody.containsKey("tools"))
        assertEquals("auto", reqBody["tool_choice"]?.jsonPrimitive?.content)

        val mockToolResponse = """
            {
              "choices": [
                {
                  "message": {
                    "role": "assistant",
                    "tool_calls": [
                      {
                        "id": "call_1",
                        "type": "function",
                        "function": {
                          "name": "execute_device_action",
                          "arguments": "{\"thought\":\"Tap search\",\"action\":\"ui_click\",\"target\":\"#0\",\"platform\":\"android\",\"confidence\":0.95}"
                        }
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
        val extracted = CloudApiClient.extractResponseContent(mockToolResponse)
        assertTrue(extracted.contains("\"action\":\"ui_click\""))
    }

    @Test
    fun test04_repeatActionBanInsideYouTubeAndHaltAfterTwoRecoveryTurns() {
        val settings = AppSettings().apply {
            fastRouterEnabled = false
            executionMode = ExecutionMode.SILENT
            inferenceSource = InferenceSource.OPENAI_COMPAT
        }

        var openAppExecutions = 0
        val observer = ScreenObserver {
            ScreenObservation(
                "com.google.android.youtube",
                listOf("[0] button: Search [900,120]", "[1] text: Home")
            )
        }
        val performer = ActionPerformer { cmd, _ ->
            if (cmd.action == ActionType.OPEN_ANDROID_APP || cmd.action == ActionType.OPEN_URL) {
                openAppExecutions++
            }
            AgentActionResult(true, "Executed ${cmd.action.wireName}")
        }

        val promptsSeen = mutableListOf<String>()
        val decider: (String) -> String = { prompt ->
            promptsSeen.add(prompt)
            """{"thought":"Open YouTube","action":"open_android_app","target":"YouTube","platform":"android","confidence":0.92}"""
        }

        val events = mutableListOf<AgentTraceEvent>()
        val engine = AccessibilityAgentEngine(
            settings = settings,
            observer = observer,
            performer = performer,
            customDecider = decider,
            settleDelayMs = 0L
        )

        engine.run("Open YouTube and search nlp") { events.add(it) }

        assertEquals(0, openAppExecutions)
        assertTrue(promptsSeen.size == 2)
        assertTrue(promptsSeen[1].contains("BLOCKED REPEAT: You are ALREADY inside com.google.android.youtube"))
        val finished = events.filterIsInstance<AgentTraceEvent.Finished>().firstOrNull()
        assertNotNull(finished)
        assertEquals(2, finished!!.stepsTaken)
    }

    @Test
    fun test05_canonicalModelPathResolutionOnColdStart() {
        val tempFilesDir = java.nio.file.Files.createTempDirectory("silentexec_files").toFile()
        try {
            val resolved = com.silentexec.android.nlp.OnDeviceLlamaEngine.resolveCanonicalModelFile(
                tempFilesDir,
                "Qwen3-1.7B-Q4_K_M.gguf"
            )
            val expected = java.io.File(java.io.File(tempFilesDir, "models"), "Qwen3-1.7B-Q4_K_M.gguf").absolutePath
            assertEquals(expected, resolved.absolutePath)

            val engine = com.silentexec.android.nlp.OnDeviceLlamaEngine()
            assertNotNull(engine.validateModelFile(resolved))
        } finally {
            tempFilesDir.deleteRecursively()
        }
    }

    @Test
    fun test06_oneTapProviderPresetsAndAutoEnabledToolbelt() {
        val ids = ProviderCatalog.PRESETS.map { it.id }
        assertTrue(ids.containsAll(listOf("GEMINI", "GROQ", "OPENROUTER", "OLLAMA", "LOCAL_GGUF")))
        assertTrue(ProviderCatalog.AUTO_ENABLED_TOOLBELT.any { it.id == "web_fetch" })
        assertTrue(ProviderCatalog.AUTO_ENABLED_TOOLBELT.any { it.id == "memory" })
        assertTrue(ProviderCatalog.AUTO_ENABLED_TOOLBELT.any { it.id == "reply" })
    }
}
