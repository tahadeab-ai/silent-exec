package com.silentexec.android.net

import com.silentexec.android.core.ActionCommand
import com.silentexec.android.core.ActionJson
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class LanBridgeClient {

    fun checkHealth(host: String, port: Int): Result<Boolean> {
        return runCatching {
            val url = URL("http://$host:$port/health")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3000
            }
            conn.responseCode == 200
        }
    }

    fun executeRemote(
        host: String,
        port: Int,
        token: String,
        prompt: String? = null,
        jsonCommand: ActionCommand? = null,
        confirm: Boolean = false,
        dryRun: Boolean = false
    ): Result<String> {
        return runCatching {
            val url = URL("http://$host:$port/v1/execute")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $token")
            }

            val requestJson = buildJsonObject {
                prompt?.let { put("prompt", it) }
                jsonCommand?.let { put("json_command", ActionJson.strict.parseToJsonElement(ActionJson.toJson(it))) }
                put("confirm", confirm)
                put("dry_run", dryRun)
            }.toString()

            OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                writer.write(requestJson)
                writer.flush()
            }

            val responseCode = conn.responseCode
            val inputStream = if (responseCode in 200..299) {
                conn.inputStream
            } else {
                conn.errorStream ?: conn.inputStream
            }

            val responseBody = BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                reader.readText()
            }

            if (responseCode !in 200..299) {
                throw IllegalStateException("HTTP $responseCode: $responseBody")
            }

            responseBody
        }
    }
}
