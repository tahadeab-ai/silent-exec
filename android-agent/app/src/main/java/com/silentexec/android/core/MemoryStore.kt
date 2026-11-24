package com.silentexec.android.core

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class MemoryItem(
    val id: String = UUID.randomUUID().toString(),
    val key: String,
    val content: String,
    val category: String = "general",
    val updatedAt: Long = System.currentTimeMillis()
)

object MemoryStore {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private const val MAX_MEMORIES = 200
    private const val MEMORY_FILE_NAME = "memory.json"

    // In-memory cache & JVM fallback for unit tests
    private val inMemoryStore = mutableMapOf<String, MemoryItem>()
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        loadFromDisk()
    }

    @Synchronized
    fun listMemories(): List<MemoryItem> {
        return inMemoryStore.values.sortedByDescending { it.updatedAt }
    }

    @Synchronized
    fun saveMemory(key: String, content: String, category: String = "general"): MemoryItem {
        val existing = inMemoryStore.values.firstOrNull { it.key.equals(key.trim(), ignoreCase = true) }
        val id = existing?.id ?: UUID.randomUUID().toString()
        val item = MemoryItem(
            id = id,
            key = key.trim(),
            content = content.trim(),
            category = category.trim(),
            updatedAt = System.currentTimeMillis()
        )
        inMemoryStore[id] = item

        // Prune oldest if exceeds capacity
        if (inMemoryStore.size > MAX_MEMORIES) {
            val oldest = inMemoryStore.values.minByOrNull { it.updatedAt }
            if (oldest != null) {
                inMemoryStore.remove(oldest.id)
            }
        }

        saveToDisk()
        return item
    }

    @Synchronized
    fun deleteMemory(idOrKey: String): Boolean {
        val removed = inMemoryStore.remove(idOrKey) != null || run {
            val match = inMemoryStore.values.firstOrNull { it.key.equals(idOrKey, ignoreCase = true) }
            if (match != null) {
                inMemoryStore.remove(match.id) != null
            } else false
        }
        if (removed) {
            saveToDisk()
        }
        return removed
    }

    @Synchronized
    fun clear() {
        inMemoryStore.clear()
        saveToDisk()
    }

    @Synchronized
    fun getRelevantMemories(query: String, limit: Int = 4): List<MemoryItem> {
        val qTokens = query.lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (qTokens.isEmpty()) return inMemoryStore.values.take(limit)

        return inMemoryStore.values
            .map { item ->
                val text = (item.key + " " + item.content + " " + item.category).lowercase()
                val score = qTokens.count { text.contains(it) }
                item to score
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    private fun getFile(): File? {
        val ctx = appContext ?: return null
        return File(ctx.filesDir, MEMORY_FILE_NAME)
    }

    private fun loadFromDisk() {
        val file = getFile() ?: return
        if (!file.exists()) return
        try {
            val content = file.readText()
            val list = json.decodeFromString<List<MemoryItem>>(content)
            inMemoryStore.clear()
            for (item in list) {
                inMemoryStore[item.id] = item
            }
        } catch (_: Exception) {}
    }

    private fun saveToDisk() {
        val file = getFile() ?: return
        try {
            val list = inMemoryStore.values.toList()
            val content = json.encodeToString(list)
            file.writeText(content)
        } catch (_: Exception) {}
    }
}
