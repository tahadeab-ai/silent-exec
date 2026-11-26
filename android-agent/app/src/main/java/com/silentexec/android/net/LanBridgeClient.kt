package com.silentexec.android.net

import com.silentexec.android.core.ActionCommand
import com.silentexec.android.core.ActionJson
import com.silentexec.android.nlp.PiiRedactor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.security.SecureRandom
import java.util.Base64

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

    /**
     * Offloads a complex multi-step planning turn over WebSocket JSON-RPC 2.0 (`planner.offload_step`)
     * to Desktop Ollama, applying PII redaction before transmission when enabled.
     */
    fun offloadPlannerStep(
        host: String,
        wsPort: Int = 8081,
        token: String,
        goal: String,
        a11yTree: List<String>,
        piiRedactionEnabled: Boolean = true
    ): Result<String> {
        val cleanGoal = PiiRedactor.redact(goal, piiRedactionEnabled)
        val cleanTree = PiiRedactor.redactList(a11yTree, piiRedactionEnabled)
        val params = buildJsonObject {
            put("goal", cleanGoal)
            put("a11y_tree", JsonArray(cleanTree.map { JsonPrimitive(it) }))
        }
        return sendJsonRpcOverWebSocket(host, wsPort, token, "planner.offload_step", params)
    }

    fun sendJsonRpcOverWebSocket(
        host: String,
        port: Int,
        token: String,
        method: String,
        params: JsonObject
    ): Result<String> {
        return runCatching {
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), 5000)
            socket.soTimeout = 15000
            socket.use { sock ->
                val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }
                val wsKey = Base64.getEncoder().encodeToString(nonce)
                val out = sock.getOutputStream()
                val inp = sock.getInputStream()

                val req = buildString {
                    append("GET / HTTP/1.1\r\n")
                    append("Host: $host:$port\r\n")
                    append("Upgrade: websocket\r\n")
                    append("Connection: Upgrade\r\n")
                    append("Authorization: Bearer $token\r\n")
                    append("Sec-WebSocket-Key: $wsKey\r\n")
                    append("Sec-WebSocket-Version: 13\r\n\r\n")
                }
                out.write(req.toByteArray(Charsets.UTF_8))
                out.flush()

                val reader = BufferedReader(InputStreamReader(inp, Charsets.UTF_8))
                val statusLine = reader.readLine() ?: throw IllegalStateException("Empty WS handshake")
                if (!statusLine.contains("101")) {
                    throw IllegalStateException("WebSocket upgrade failed: $statusLine")
                }
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                }

                val rpcPayload = buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("id", 1)
                    put("method", method)
                    put("params", params)
                }.toString().toByteArray(Charsets.UTF_8)

                // Masked client text frame
                val maskKey = ByteArray(4).also { SecureRandom().nextBytes(it) }
                out.write(0x81)
                when {
                    rpcPayload.size < 126 -> out.write(0x80 or rpcPayload.size)
                    rpcPayload.size <= 65535 -> {
                        out.write(0x80 or 126)
                        out.write((rpcPayload.size shr 8) and 0xFF)
                        out.write(rpcPayload.size and 0xFF)
                    }
                    else -> throw IllegalArgumentException("Payload too large")
                }
                out.write(maskKey)
                val masked = ByteArray(rpcPayload.size) { i ->
                    (rpcPayload[i].toInt() xor maskKey[i % 4].toInt()).toByte()
                }
                out.write(masked)
                out.flush()

                val b0 = inp.read()
                val b1 = inp.read()
                if (b0 < 0 || b1 < 0) throw IllegalStateException("WebSocket closed")
                var len = b1 and 0x7F
                if (len == 126) {
                    len = ((inp.read() and 0xFF) shl 8) or (inp.read() and 0xFF)
                }
                val buf = ByteArray(len)
                var read = 0
                while (read < len) {
                    val n = inp.read(buf, read, len - read)
                    if (n < 0) break
                    read += n
                }
                String(buf, 0, read, Charsets.UTF_8)
            }
        }
    }
}
