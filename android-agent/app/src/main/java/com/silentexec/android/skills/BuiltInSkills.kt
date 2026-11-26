package com.silentexec.android.skills

import android.content.Context
import com.silentexec.android.core.ActionCommand
import com.silentexec.android.core.ActionType
import com.silentexec.android.core.Platform
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID

@Serializable
data class SkillDefinition(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val triggers: List<String> = emptyList(),
    val steps: List<ActionCommand>,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

object SkillStore {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private const val SKILLS_FILE_NAME = "skills.json"
    private const val MAX_SKILLS = 100

    private val inMemorySkills = mutableMapOf<String, SkillDefinition>()
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        loadFromDisk()
    }

    @Synchronized
    fun listSkills(): List<SkillDefinition> {
        return inMemorySkills.values.sortedByDescending { it.createdAt }
    }

    @Synchronized
    fun saveSkill(
        name: String,
        triggers: List<String>,
        steps: List<ActionCommand>,
        description: String = ""
    ): SkillDefinition {
        require(name.isNotBlank()) { "Skill name cannot be blank" }
        require(steps.isNotEmpty()) { "Skill must contain at least one step" }

        val cleanName = name.trim()
        val existing = inMemorySkills.values.firstOrNull { it.name.equals(cleanName, ignoreCase = true) }
        val id = existing?.id ?: UUID.randomUUID().toString()
        val normalizedTriggers = (listOf(cleanName) + triggers)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(Locale.ROOT) }

        val skill = SkillDefinition(
            id = id,
            name = cleanName,
            triggers = normalizedTriggers,
            steps = steps,
            description = description.trim(),
            createdAt = System.currentTimeMillis()
        )
        inMemorySkills[id] = skill

        if (inMemorySkills.size > MAX_SKILLS) {
            val oldest = inMemorySkills.values.minByOrNull { it.createdAt }
            if (oldest != null) {
                inMemorySkills.remove(oldest.id)
            }
        }

        saveToDisk()
        return skill
    }

    @Synchronized
    fun deleteSkill(idOrName: String): Boolean {
        val removed = inMemorySkills.remove(idOrName) != null || run {
            val match = inMemorySkills.values.firstOrNull { it.name.equals(idOrName.trim(), ignoreCase = true) }
            if (match != null) inMemorySkills.remove(match.id) != null else false
        }
        if (removed) {
            saveToDisk()
        }
        return removed
    }

    @Synchronized
    fun clear() {
        inMemorySkills.clear()
        saveToDisk()
    }

    @Synchronized
    fun findMatchingSkill(query: String): SkillDefinition? {
        val normQuery = BuiltInSkills.normalizeText(query)
        if (normQuery.isEmpty()) return null

        return inMemorySkills.values.firstOrNull { skill ->
            BuiltInSkills.normalizeText(skill.name) == normQuery ||
                skill.triggers.any { BuiltInSkills.normalizeText(it) == normQuery }
        }
    }

    private fun getFile(): File? {
        val ctx = appContext ?: return null
        return File(ctx.filesDir, SKILLS_FILE_NAME)
    }

    private fun loadFromDisk() {
        val file = getFile() ?: return
        if (!file.exists()) return
        try {
            val content = file.readText()
            val list = json.decodeFromString<List<SkillDefinition>>(content)
            inMemorySkills.clear()
            for (skill in list) {
                inMemorySkills[skill.id] = skill
            }
        } catch (_: Exception) {}
    }

    private fun saveToDisk() {
        val file = getFile() ?: return
        try {
            val content = json.encodeToString(inMemorySkills.values.toList())
            file.writeText(content)
        } catch (_: Exception) {}
    }
}

/**
 * Deterministic Compound & Built-In Skills Router for SilentExec Android.
 * Runs BEFORE weak local LLM weights to resolve multi-step intents (YouTube Search, Web Search,
 * and custom user routines in skills.json) cleanly in Arabic (Yemeni + MSA) and English.
 */
object BuiltInSkills {

    private val DIACRITICS_REGEX = Regex("[\\u064B-\\u065F\\u0670\\u0640]")
    private val WHITESPACE_REGEX = Regex("\\s+")

    private val YOUTUBE_PREFIXES = listOf(
        "افتح اليوتيوب و ابحث عن ",
        "افتح اليوتيوب وابحث عن ",
        "افتح يوتيوب و ابحث عن ",
        "افتح يوتيوب وابحث عن ",
        "شغل اليوتيوب و ابحث عن ",
        "شغل اليوتيوب وابحث عن ",
        "شغل يوتيوب و ابحث عن ",
        "شغل يوتيوب وابحث عن ",
        "افتح اليوتيوب و ابحث ",
        "افتح اليوتيوب وابحث ",
        "افتح يوتيوب ابحث عن ",
        "ابحث في اليوتيوب عن ",
        "ابحث في يوتيوب عن ",
        "دور في اليوتيوب عن ",
        "دور في يوتيوب عن ",
        "open youtube and search for ",
        "open youtube and search ",
        "launch youtube and search for ",
        "launch youtube and search ",
        "search youtube for ",
        "youtube search for ",
        "youtube search "
    )

    private val WEB_SEARCH_PREFIXES = listOf(
        "افتح المتصفح و ابحث عن ",
        "افتح المتصفح وابحث عن ",
        "افتح المتصفح و ابحث ",
        "افتح المتصفح وابحث ",
        "افتح جوجل و ابحث عن ",
        "افتح جوجل وابحث عن ",
        "افتح قوقل و ابحث عن ",
        "افتح قوقل وابحث عن ",
        "ابحث في جوجل عن ",
        "ابحث في قوقل عن ",
        "ابحث في المتصفح عن ",
        "دور في جوجل عن ",
        "open browser and search for ",
        "open browser and search ",
        "open chrome and search for ",
        "open chrome and search ",
        "search google for ",
        "search the web for ",
        "search web for ",
        "google search "
    )

    fun normalizeText(input: String): String {
        val noDiacritics = DIACRITICS_REGEX.replace(input.trim(), "")
        val sb = StringBuilder(noDiacritics.length)
        for (ch in noDiacritics) {
            when (ch) {
                'أ', 'إ', 'آ' -> sb.append('ا')
                'ة' -> sb.append('ه')
                'ى' -> sb.append('ي')
                else -> sb.append(ch.lowercaseChar())
            }
        }
        return WHITESPACE_REGEX.replace(sb.toString().trim(), " ")
    }

    private val CHAINED_ACTION_MARKERS = listOf(
        " وافتح ",
        " و افتح ",
        " واضغط ",
        " و اضغط ",
        " واكتب ",
        " و اكتب ",
        " وانزل ",
        " و انزل ",
        " وارسل ",
        " و ارسل ",
        " ولايك ",
        " و لايك ",
        " وشغل ",
        " و شغل ",
        " ثم ",
        " بعدين ",
        " بعدها ",
        " and open ",
        " and click ",
        " and tap ",
        " and like ",
        " and type ",
        " and scroll ",
        " and send ",
        " then "
    )

    private fun hasChainedFollowupActions(normText: String): Boolean {
        val padded = " $normText "
        return CHAINED_ACTION_MARKERS.any { padded.contains(it) }
    }

    /**
     * Resolves a user query into deterministic ActionCommands if a built-in or custom skill matches.
     * Returns null if no skill matches or if the prompt contains chained follow-up actions
     * (so the Thinking ReAct Agent Loop handles multi-step goals).
     */
    fun resolve(query: String): List<ActionCommand>? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null

        // 1. Custom User Routines / Skills (skills.json)
        SkillStore.findMatchingSkill(trimmed)?.let { customSkill ->
            if (customSkill.steps.isNotEmpty()) {
                return customSkill.steps
            }
        }

        val norm = normalizeText(trimmed)

        // Compound-intent guard: let the Thinking ReAct Agent Loop handle multi-step goals
        if (hasChainedFollowupActions(norm)) {
            return null
        }

        // 2. YouTube Search Skill
        extractQueryAfterPrefix(norm, YOUTUBE_PREFIXES)?.let { searchQuery ->
            val encoded = URLEncoder.encode(searchQuery, StandardCharsets.UTF_8.name())
            return listOf(
                ActionCommand(
                    action = ActionType.OPEN_URL,
                    target = "https://www.youtube.com/results?search_query=$encoded",
                    platform = Platform.ANDROID,
                    confidence = 1.0
                )
            )
        }

        // Also support "ابحث عن <q> في اليوتيوب"
        val ytSuffixMatch = Regex("^(?:ابحث عن|دور عن|ابحث)\\s+(.+?)\\s+(?:في اليوتيوب|في يوتيوب|باليوتيوب)$")
            .find(norm)
        if (ytSuffixMatch != null) {
            val q = ytSuffixMatch.groupValues[1].trim()
            if (q.isNotEmpty()) {
                val encoded = URLEncoder.encode(q, StandardCharsets.UTF_8.name())
                return listOf(
                    ActionCommand(
                        action = ActionType.OPEN_URL,
                        target = "https://www.youtube.com/results?search_query=$encoded",
                        platform = Platform.ANDROID,
                        confidence = 1.0
                    )
                )
            }
        }

        // 3. Web Search Skill
        extractQueryAfterPrefix(norm, WEB_SEARCH_PREFIXES)?.let { searchQuery ->
            val encoded = URLEncoder.encode(searchQuery, StandardCharsets.UTF_8.name())
            return listOf(
                ActionCommand(
                    action = ActionType.OPEN_URL,
                    target = "https://www.google.com/search?q=$encoded",
                    platform = Platform.ANDROID,
                    confidence = 1.0
                )
            )
        }

        val webSuffixMatch = Regex("^(?:ابحث عن|دور عن|ابحث)\\s+(.+?)\\s+(?:في جوجل|في قوقل|في المتصفح|بجوجل)$")
            .find(norm)
        if (webSuffixMatch != null) {
            val q = webSuffixMatch.groupValues[1].trim()
            if (q.isNotEmpty()) {
                val encoded = URLEncoder.encode(q, StandardCharsets.UTF_8.name())
                return listOf(
                    ActionCommand(
                        action = ActionType.OPEN_URL,
                        target = "https://www.google.com/search?q=$encoded",
                        platform = Platform.ANDROID,
                        confidence = 1.0
                    )
                )
            }
        }

        return null
    }

    private fun extractQueryAfterPrefix(normText: String, prefixes: List<String>): String? {
        for (prefix in prefixes) {
            val normPrefix = normalizeText(prefix) + " "
            if (normText.startsWith(normPrefix)) {
                val q = normText.removePrefix(normPrefix).trim()
                if (q.isNotEmpty()) return q
            }
        }
        return null
    }
}
