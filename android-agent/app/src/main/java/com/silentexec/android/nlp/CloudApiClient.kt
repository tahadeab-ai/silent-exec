package com.silentexec.android.nlp

import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/**
 * OpenAI-compatible Client for SilentExec Android.
 * Supports standard function/tool calling (Gemini, Groq, OpenRouter, OpenAI, Ollama)
 * with clean JSON object fallback.
 */
class CloudApiClient(
    val baseUrl: String = "https://api.openai.com/v1",
    val model: String = "gpt-4o-mini",
    val apiKey: String = "",
    val connectTimeoutMs: Int = 15000,
    val readTimeoutMs: Int = 30000
) {

    fun generateCompletion(
        systemPrompt: String,
        userPrompt: String,
        useToolCalling: Boolean = true
    ): Result<String> = runCatching {
        val endpoint = buildChatCompletionsUrl(baseUrl)
        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = connectTimeoutMs
        conn.readTimeout = readTimeoutMs
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("Content-Type", "application/json")

        if (apiKey.isNotBlank()) {
            conn.setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
        }

        val body = buildRequestBody(
            model = model,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            useToolCalling = useToolCalling
        )

        conn.doOutput = true
        OutputStreamWriter(conn.outputStream).use { writer ->
            writer.write(body.toString())
            writer.flush()
        }

        val responseCode = conn.responseCode
        if (responseCode !in 200..299) {
            val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            // Retry once without tool calling if provider/model rejects tools
            if (useToolCalling && responseCode in 400..422) {
                return generateCompletion(systemPrompt, userPrompt, useToolCalling = false)
            }
            throw IllegalStateException("Cloud API error HTTP $responseCode: $errorBody")
        }

        val responseText = conn.inputStream.bufferedReader().use { it.readText() }
        extractResponseContent(responseText)
    }

    companion object {
        fun buildChatCompletionsUrl(baseUrl: String): String {
            val clean = baseUrl.trim().trimEnd('/')
            return if (clean.endsWith("/chat/completions")) clean else "$clean/chat/completions"
        }

        fun buildRequestBody(
            model: String,
            systemPrompt: String,
            userPrompt: String,
            useToolCalling: Boolean
        ): JSONObject {
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                })
            }

            return JSONObject().apply {
                put("model", model)
                put("messages", messages)
                put("temperature", 0.0)
                if (useToolCalling) {
                    put("tools", buildDeviceActionTools())
                    put("tool_choice", "auto")
                } else {
                    put("response_format", JSONObject().apply {
                        put("type", "json_object")
                    })
                }
            }
        }

        fun buildDeviceActionTools(): JSONArray {
            val parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("thought", JSONObject().apply {
                        put("type", "string")
                        put("description", "Short 1-sentence reasoning about the current screen and next step.")
                    })
                    put("action", JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray(listOf(
                            "ui_click", "ui_type", "ui_scroll", "open_android_app", "open_url",
                            "ui_back", "ui_home", "set_volume", "create_note",
                            "delete_file", "reply", "done"
                        )))
                    })
                    put("target", JSONObject().apply {
                        put("type", "string")
                        put("description", "Element index (#0, #1), visible label, app name, URL, or final reply text.")
                    })
                    put("args", JSONObject().apply {
                        put("type", "object")
                        put("description", "Optional arguments such as {\"text\":\"...\"} or {\"direction\":\"down\"}.")
                    })
                    put("platform", JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray(listOf("android", "any")))
                    })
                    put("confidence", JSONObject().apply {
                        put("type", "number")
                    })
                })
                put("required", JSONArray(listOf("thought", "action", "platform", "confidence")))
            }

            val functionObj = JSONObject().apply {
                put("name", "execute_device_action")
                put("description", "Execute a single Android accessibility or system action on the device.")
                put("parameters", parameters)
            }

            return JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "function")
                    put("function", functionObj)
                })
            }
        }

        fun extractResponseContent(responseText: String): String {
            val root = JSONObject(responseText)
            val choices = root.optJSONArray("choices") ?: return responseText
            if (choices.length() == 0) return responseText

            val first = choices.getJSONObject(0)
            val message = first.optJSONObject("message") ?: return responseText

            val toolCalls = message.optJSONArray("tool_calls")
            if (toolCalls != null && toolCalls.length() > 0) {
                val firstCall = toolCalls.getJSONObject(0)
                val fn = firstCall.optJSONObject("function")
                val argsStr = fn?.optString("arguments")
                if (!argsStr.isNullOrBlank()) {
                    return argsStr
                }
            }

            val content = message.optString("content")
            if (!content.isNullOrBlank()) {
                return content
            }
            return responseText
        }
    }
}
