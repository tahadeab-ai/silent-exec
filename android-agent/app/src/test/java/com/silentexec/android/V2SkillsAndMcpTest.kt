package com.silentexec.android

import com.silentexec.android.core.ActionCommand
import com.silentexec.android.core.ActionType
import com.silentexec.android.core.Platform
import com.silentexec.android.net.McpHttpClient
import com.silentexec.android.net.McpServerStore
import com.silentexec.android.skills.BuiltInSkills
import com.silentexec.android.skills.SkillStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class V2SkillsAndMcpTest {

    @Before
    fun setup() {
        SkillStore.clear()
        McpServerStore.clear()
    }

    @Test
    fun test01_mcpHttpClientJsonRpcFraming() {
        val initReq = McpHttpClient.buildInitializeRequest(1)
        val initObj = Json.parseToJsonElement(initReq).jsonObject
        assertEquals("2.0", initObj["jsonrpc"]?.jsonPrimitive?.content)
        assertEquals("1", initObj["id"]?.jsonPrimitive?.content)
        assertEquals("initialize", initObj["method"]?.jsonPrimitive?.content)

        val toolsReq = McpHttpClient.buildToolsListRequest(2)
        val toolsObj = Json.parseToJsonElement(toolsReq).jsonObject
        assertEquals("2.0", toolsObj["jsonrpc"]?.jsonPrimitive?.content)
        assertEquals("2", toolsObj["id"]?.jsonPrimitive?.content)
        assertEquals("tools/list", toolsObj["method"]?.jsonPrimitive?.content)
    }

    @Test
    fun test02_mcpParseToolsListJsonAndSse() {
        val plainJson = """
            {
              "jsonrpc": "2.0",
              "id": 2,
              "result": {
                "tools": [
                  {"name": "fetch", "description": "Fetch a URL"},
                  {"name": "read_file", "description": "Read a file"}
                ]
              }
            }
        """.trimIndent()
        val tools = McpHttpClient.parseToolsListResponse(plainJson)
        assertEquals(2, tools.size)
        assertEquals("fetch", tools[0].name)
        assertEquals("Fetch a URL", tools[0].description)

        val sseResponse = """
            event: message
            data: {"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"memory_search","description":"Search memory"}]}}
        """.trimIndent()
        val sseTools = McpHttpClient.parseToolsListResponse(sseResponse)
        assertEquals(1, sseTools.size)
        assertEquals("memory_search", sseTools[0].name)
    }

    @Test
    fun test03_mcpServerStoreCrud() {
        val saved = McpServerStore.saveServer("Fetch Web MCP", "http://127.0.0.1:8765/mcp/fetch")
        assertEquals("Fetch Web MCP", saved.name)
        assertEquals(1, McpServerStore.listServers().size)

        // Updating same name replaces in-place
        McpServerStore.saveServer("Fetch Web MCP", "http://192.168.1.50:8765/mcp/fetch")
        assertEquals(1, McpServerStore.listServers().size)
        assertEquals("http://192.168.1.50:8765/mcp/fetch", McpServerStore.listServers()[0].url)

        assertTrue(McpServerStore.deleteServer("Fetch Web MCP"))
        assertEquals(0, McpServerStore.listServers().size)
        assertFalse(McpServerStore.deleteServer("NonExistent"))
    }

    @Test
    fun test04_customSkillStoreCrud() {
        val step = ActionCommand(
            action = ActionType.SET_VOLUME,
            target = "20",
            platform = Platform.ANDROID,
            confidence = 0.95
        )
        val skill = SkillStore.saveSkill(
            name = "وضع الدراسة",
            triggers = listOf("study mode"),
            steps = listOf(step),
            description = "Lower volume to 20%"
        )
        assertEquals("وضع الدراسة", skill.name)
        assertEquals(1, SkillStore.listSkills().size)

        val found = SkillStore.findMatchingSkill("study mode")
        assertNotNull(found)
        assertEquals(ActionType.SET_VOLUME, found!!.steps[0].action)

        assertTrue(SkillStore.deleteSkill(skill.id))
        assertEquals(0, SkillStore.listSkills().size)
    }

    @Test
    fun test05_builtInSkillsRoutingArabicAndEnglish() {
        val ytAr = BuiltInSkills.resolve("افتح اليوتيوب وابحث عن nlp")
        assertNotNull(ytAr)
        assertTrue(ytAr!!.any { it.action == ActionType.OPEN_URL && it.target!!.contains("youtube.com") })

        val ytEn = BuiltInSkills.resolve("open YouTube and search for Rust")
        assertNotNull(ytEn)
        assertTrue(ytEn!!.any { it.action == ActionType.OPEN_URL && it.target!!.contains("youtube.com") })

        val webEn = BuiltInSkills.resolve("search google for edge AI")
        assertNotNull(webEn)
        assertTrue(webEn!!.any { it.action == ActionType.OPEN_URL && it.target!!.contains("google.com") })
    }
}
