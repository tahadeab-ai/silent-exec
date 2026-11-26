package com.silentexec.android.net

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

@Serializable
data class McpEndpointConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val enabled: Boolean = true
)

data class McpToolInfo(
    val name: String,
    val description: String = ""
)

object McpServerStore {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private const val FILE_NAME = "mcp_servers.json"
    private val servers = mutableMapOf<String, McpEndpointConfig>()
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        loadFromDisk()
    }

    @Synchronized
    fun listServers(): List<McpEndpointConfig> {
        return servers.values.toList()
    }

    @Synchronized
    fun saveServer(name: String, url: String, enabled: Boolean = true): McpEndpointConfig {
        require(name.isNotBlank()) { "MCP server name cannot be blank" }
        require(url.isNotBlank()) { "MCP server URL cannot be blank" }
        val existing = servers.values.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
        val id = existing?.id ?: UUID.randomUUID().toString()
        val cfg = McpEndpointConfig(
            id = id,
            name = name.trim(),
            url = url.trim(),
            enabled = enabled
        )
        servers[id] = cfg
        saveToDisk()
        return cfg
    }

    @Synchronized
    fun deleteServer(idOrName: String): Boolean {
        val removed = servers.remove(idOrName) != null || run {
            val match = servers.values.firstOrNull { it.name.equals(idOrName.trim(), ignoreCase = true) }
            if (match != null) servers.remove(match.id) != null else false
        }
        if (removed) saveToDisk()
        return removed
    }

    @Synchronized
    fun clear() {
        servers.clear()
        saveToDisk()
    }

    private fun getFile(): File? {
        val ctx = appContext ?: return null
        return File(ctx.filesDir, FILE_NAME)
    }

    private fun loadFromDisk() {
        val file = getFile() ?: return
        if (!file.exists()) return
        try {
            val list = json.decodeFromString<List<McpEndpointConfig>>(file.readText())
            servers.clear()
            list.forEach { servers[it.id] = it }
        } catch (_: Exception) {}
    }

    private fun saveToDisk() {
        val file = getFile() ?: return
        try {
            file.writeText(json.encodeToString(servers.values.toList()))
        } catch (_: Exception) {}
    }
}

/**
 * Lightweight HTTP/SSE JSON-RPC 2.0 client for Android MCP connectors.
 */
object McpHttpClient {

    private val json = Json { ignoreUnknownKeys = true }

    fun buildInitializeRequest(id: Int = 1): String {
        val obj = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", "initialize")
            put("params", buildJsonObject {
                put("protocolVersion", "2024-11-05")
                put("capabilities", buildJsonObject {})
                put("clientInfo", buildJsonObject {
                    put("name", "SilentExec-Android")
                    put("version", "2.0.0")
                })
            })
        }
        return obj.toString()
    }

    fun buildToolsListRequest(id: Int = 2): String {
        val obj = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", "tools/list")
            put("params", buildJsonObject {})
        }
        return obj.toString()
    }

    /**
     * Parses a `tools/list` JSON-RPC response from either plain JSON or SSE (`data: {...}`) format.
     */
    fun parseToolsListResponse(rawResponse: String): List<McpToolInfo> {
        val trimmed = rawResponse.trim()
        if (trimmed.isEmpty()) return emptyList()

        val jsonCandidate = if (trimmed.contains("data:")) {
            trimmed.lines()
                .map { it.trim() }
                .filter { it.startsWith("data:") }
                .map { it.removePrefix("data:").trim() }
                .lastOrNull { it.startsWith("{") } ?: trimmed
        } else {
            trimmed
        }

        val root = json.parseToJsonElement(jsonCandidate).jsonObject
        if (root.containsKey("error")) {
            val errObj = root["error"] as? JsonObject
            val msg = errObj?.get("message")?.jsonPrimitive?.content ?: "MCP error"
            throw IllegalStateException(msg)
        }

        val resultObj = root["result"] as? JsonObject ?: return emptyList()
        val toolsArr = resultObj["tools"] as? JsonArray ?: return emptyList()

        return toolsArr.mapNotNull { elem ->
            val obj = elem as? JsonObject ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val desc = obj["description"]?.jsonPrimitive?.content ?: ""
            McpToolInfo(name = name, description = desc)
        }
    }

    fun testConnection(endpointUrl: String, timeoutMs: Int = 8000): Result<List<McpToolInfo>> {
        return runCatching {
            val initPayload = buildInitializeRequest(1)
            postJsonRpc(endpointUrl, initPayload, timeoutMs)

            val toolsPayload = buildToolsListRequest(2)
            val toolsRaw = postJsonRpc(endpointUrl, toolsPayload, timeoutMs)
            parseToolsListResponse(toolsRaw)
        }
    }

    private fun postJsonRpc(endpointUrl: String, payload: String, timeoutMs: Int): String {
        val conn = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json, text/event-stream")
        }
        try {
            conn.outputStream.use { os ->
                os.write(payload.toByteArray(Charsets.UTF_8))
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code: ${body.take(200)}")
            }
            return body
        } finally {
            conn.disconnect()
        }
    }
}
