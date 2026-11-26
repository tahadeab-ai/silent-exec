package com.silentexec.android.nlp

import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

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
            if (useToolCalling && responseCode in 400..422) {
                return generateCompletion(systemPrompt, userPrompt, useToolCalling = false)
            }
            throw IllegalStateException("Cloud API error HTTP $responseCode: $errorBody")
        }

        val responseText = conn.inputStream.bufferedReader().use { it.readText() }
        extractResponseContent(responseText)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun buildChatCompletionsUrl(baseUrl: String): String {
            val clean = baseUrl.trim().trimEnd('/')
            return if (clean.endsWith("/chat/completions")) clean else "$clean/chat/completions"
        }

        fun buildRequestBody(
            model: String,
            systemPrompt: String,
            userPrompt: String,
            useToolCalling: Boolean
        ): JsonObject {
            val messages = buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", userPrompt)
                })
            }

            return buildJsonObject {
                put("model", model)
                put("messages", messages)
                put("temperature", 0.0)
                if (useToolCalling) {
                    put("tools", buildDeviceActionTools())
                    put("tool_choice", "auto")
                } else {
                    put("response_format", buildJsonObject {
                        put("type", "json_object")
                    })
                }
            }
        }

        fun buildDeviceActionTools(): JsonArray {
            val actionEnum = listOf(
                "ui_click", "ui_type", "ui_scroll", "open_android_app", "open_url",
                "ui_back", "ui_home", "set_volume", "create_note", "copy_to_clipboard",
                "web_fetch", "read_file", "write_file", "memory_save",
                "delete_file", "reply", "done"
            )
            val parameters = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("thought", buildJsonObject {
                        put("type", "string")
                        put("description", "Short 1-sentence reasoning about the current screen and next step.")
                    })
                    put("action", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray { actionEnum.forEach { add(JsonPrimitive(it)) } })
                    })
                    put("target", buildJsonObject {
                        put("type", "string")
                        put("description", "Element index (#0, #1), visible label, app name, URL, or final reply text.")
                    })
                    put("args", buildJsonObject {
                        put("type", "object")
                        put("description", "Optional arguments such as {\"text\":\"...\"} or {\"direction\":\"down\"}.")
                    })
                    put("platform", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add(JsonPrimitive("android"))
                            add(JsonPrimitive("any"))
                        })
                    })
                    put("confidence", buildJsonObject {
                        put("type", "number")
                    })
                })
                put("required", buildJsonArray {
                    add(JsonPrimitive("thought"))
                    add(JsonPrimitive("action"))
                    add(JsonPrimitive("platform"))
                    add(JsonPrimitive("confidence"))
                })
            }

            return buildJsonArray {
                add(buildJsonObject {
                    put("type", "function")
                    put("function", buildJsonObject {
                        put("name", "execute_device_action")
                        put("description", "Execute a single Android accessibility or system action on the device.")
                        put("parameters", parameters)
                    })
                })
            }
        }

        fun extractResponseContent(responseText: String): String {
            val root = runCatching { json.parseToJsonElement(responseText).jsonObject }.getOrNull()
                ?: return responseText
            val choices = root["choices"]?.jsonArray ?: return responseText
            if (choices.isEmpty()) return responseText

            val first = choices[0].jsonObject
            val message = first["message"]?.jsonObject ?: return responseText

            val toolCalls = runCatching { message["tool_calls"]?.jsonArray }.getOrNull()
            if (toolCalls != null && toolCalls.isNotEmpty()) {
                val fn = toolCalls[0].jsonObject["function"]?.jsonObject
                val argsStr = fn?.get("arguments")?.jsonPrimitive?.contentOrNull
                if (!argsStr.isNullOrBlank()) {
                    return argsStr
                }
            }

            val content = runCatching { message["content"]?.jsonPrimitive?.contentOrNull }.getOrNull()
            if (!content.isNullOrBlank()) {
                return content
            }
            return responseText
        }
    }
}
