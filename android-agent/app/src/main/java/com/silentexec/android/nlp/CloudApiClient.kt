package com.silentexec.android.nlp

import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lightweight OpenAI-compatible Client for SilentExec Android.
 * Compatible with OpenAI, Gemini OpenAI endpoint, OpenRouter, Groq, and DeepSeek.
 */
class CloudApiClient(
    val baseUrl: String = "https://api.openai.com/v1",
    val model: String = "gpt-4o-mini",
    val apiKey: String = "",
    val connectTimeoutMs: Int = 15000,
    val readTimeoutMs: Int = 30000
) {

    fun generateCompletion(systemPrompt: String, userPrompt: String): Result<String> = runCatching {
        val endpoint = if (baseUrl.endsWith("/chat/completions")) {
            baseUrl
        } else {
            "${baseUrl.trimEnd('/')}/chat/completions"
        }

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

        val body = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("temperature", 0.0)
            put("response_format", JSONObject().apply {
                put("type", "json_object")
            })
        }

        conn.doOutput = true
        OutputStreamWriter(conn.outputStream).use { writer ->
            writer.write(body.toString())
            writer.flush()
        }

        val responseCode = conn.responseCode
        if (responseCode !in 200..299) {
            val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw IllegalStateException("Cloud API error HTTP $responseCode: $errorBody")
        }

        val responseText = conn.inputStream.bufferedReader().use { it.readText() }
        val root = JSONObject(responseText)
        val choices = root.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            val first = choices.getJSONObject(0)
            val message = first.optJSONObject("message")
            val content = message?.optString("content")
            if (!content.isNullOrEmpty()) {
                return@runCatching content
            }
        }

        responseText
    }
}
