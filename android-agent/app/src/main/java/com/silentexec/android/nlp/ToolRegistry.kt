package com.silentexec.android.nlp

import com.silentexec.android.core.ActionType
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-tool enable/disable registry (Mobile-MCP clean-room pattern) so small 2B models
 * only receive active tool schemas relevant to the current task, saving prompt tokens.
 */
object ToolRegistry {
    private val toolStates = ConcurrentHashMap<String, Boolean>().apply {
        for (action in ActionType.entries) {
            put(action.wireName, true)
        }
    }

    fun setToolEnabled(toolName: String, enabled: Boolean) {
        toolStates[toolName.trim().lowercase()] = enabled
    }

    fun isToolEnabled(toolName: String): Boolean {
        return toolStates[toolName.trim().lowercase()] ?: true
    }

    fun resetAll(enabled: Boolean = true) {
        for (action in ActionType.entries) {
            toolStates[action.wireName] = enabled
        }
    }

    fun activeToolNames(): List<String> {
        return ActionType.entries
            .map { it.wireName }
            .filter { isToolEnabled(it) }
    }

    /**
     * Filters active tools for a specific task + mode to minimize prompt token overhead on 2B models.
     */
    fun activeToolsForTask(userGoal: String, fastMode: Boolean): List<String> {
        val lower = userGoal.lowercase()
        val coreUi = mutableListOf("open_android_app", "open_url", "ui_click", "ui_type", "ui_scroll", "ui_back", "ui_home", "done", "reply")
        if (fastMode) {
            coreUi.add("batch_actions")
        }
        if (lower.contains("http") || lower.contains("search") || lower.contains("ابحث") || lower.contains("web")) {
            coreUi.add("web_fetch")
        }
        if (lower.contains("file") || lower.contains("ملف") || lower.contains("note") || lower.contains("ملاحظة")) {
            coreUi.add("read_file")
            coreUi.add("write_file")
            coreUi.add("create_note")
        }
        if (lower.contains("copy") || lower.contains("انسخ") || lower.contains("حافظة")) {
            coreUi.add("copy_to_clipboard")
        }
        if (lower.contains("remember") || lower.contains("تذكر") || lower.contains("احفظ")) {
            coreUi.add("memory_save")
        }
        return coreUi.distinct().filter { isToolEnabled(it) }
    }

    fun formatActiveToolsSummary(userGoal: String, fastMode: Boolean): String {
        val tools = activeToolsForTask(userGoal, fastMode)
        return "Active tools (${tools.size}): ${tools.joinToString(", ") { "\"$it\"" }}"
    }
}
