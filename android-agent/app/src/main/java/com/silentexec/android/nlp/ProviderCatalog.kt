package com.silentexec.android.nlp

import com.silentexec.android.core.AppSettings
import com.silentexec.android.core.InferenceSource

enum class ProviderTier {
    CLOUD_FAST,
    LOCAL_LAN,
    LOCAL_GGUF
}

data class ProviderPreset(
    val id: String,
    val displayName: String,
    val displayNameAr: String,
    val inferenceSource: InferenceSource,
    val baseUrl: String,
    val defaultModel: String,
    val requiresApiKey: Boolean,
    val tier: ProviderTier,
    val badgeEn: String,
    val badgeAr: String
)

data class AgentToolDescriptor(
    val id: String,
    val titleEn: String,
    val titleAr: String
)

object ProviderCatalog {
    val AUTO_ENABLED_TOOLBELT: List<AgentToolDescriptor> = listOf(
        AgentToolDescriptor("screen", "Screen & UI Control", "التحكم بالشاشة واللمس"),
        AgentToolDescriptor("apps", "App & URL Launch", "فتح التطبيقات والروابط"),
        AgentToolDescriptor("web_fetch", "Web Search & Fetch", "البحث وجلب الويب"),
        AgentToolDescriptor("clipboard", "Clipboard Read/Write", "الحافظة"),
        AgentToolDescriptor("files", "Notes & Files", "الملاحظات والملفات"),
        AgentToolDescriptor("memory", "Persistent Memory", "الذاكرة الدائمة"),
        AgentToolDescriptor("device", "Volume & Settings", "الصوت والإعدادات"),
        AgentToolDescriptor("reply", "Markdown & Code Reply", "إجابات وأكواد مفصلة")
    )

    val PRESETS: List<ProviderPreset> = listOf(
        ProviderPreset(
            id = "GEMINI",
            displayName = "Gemini (Free Tier)",
            displayNameAr = "Gemini (المجاني)",
            inferenceSource = InferenceSource.OPENAI_COMPAT,
            baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
            defaultModel = "gemini-2.5-flash",
            requiresApiKey = true,
            tier = ProviderTier.CLOUD_FAST,
            badgeEn = "Instant 95%+ multi-step",
            badgeAr = "موثوقية +95% متعددة الخطوات"
        ),
        ProviderPreset(
            id = "GROQ",
            displayName = "Groq",
            displayNameAr = "Groq فائق السرعة",
            inferenceSource = InferenceSource.OPENAI_COMPAT,
            baseUrl = "https://api.groq.com/openai/v1",
            defaultModel = "llama-3.3-70b-versatile",
            requiresApiKey = true,
            tier = ProviderTier.CLOUD_FAST,
            badgeEn = "Instant 95%+ multi-step",
            badgeAr = "موثوقية +95% متعددة الخطوات"
        ),
        ProviderPreset(
            id = "OPENROUTER",
            displayName = "OpenRouter",
            displayNameAr = "OpenRouter",
            inferenceSource = InferenceSource.OPENAI_COMPAT,
            baseUrl = "https://openrouter.ai/api/v1",
            defaultModel = "google/gemini-2.5-flash",
            requiresApiKey = true,
            tier = ProviderTier.CLOUD_FAST,
            badgeEn = "Instant 95%+ multi-step",
            badgeAr = "موثوقية +95% متعددة الخطوات"
        ),
        ProviderPreset(
            id = "OPENAI",
            displayName = "OpenAI",
            displayNameAr = "OpenAI",
            inferenceSource = InferenceSource.OPENAI_COMPAT,
            baseUrl = "https://api.openai.com/v1",
            defaultModel = "gpt-4o-mini",
            requiresApiKey = true,
            tier = ProviderTier.CLOUD_FAST,
            badgeEn = "Instant 95%+ multi-step",
            badgeAr = "موثوقية +95% متعددة الخطوات"
        ),
        ProviderPreset(
            id = "OLLAMA",
            displayName = "Ollama (Local / LAN)",
            displayNameAr = "Ollama (محلي / شبكة)",
            inferenceSource = InferenceSource.OPENAI_COMPAT,
            baseUrl = "http://127.0.0.1:11434/v1",
            defaultModel = "qwen3.5:4b",
            requiresApiKey = false,
            tier = ProviderTier.LOCAL_LAN,
            badgeEn = "Instant local/LAN server",
            badgeAr = "سيرفر محلي سريع بدون مفتاح"
        ),
        ProviderPreset(
            id = "LOCAL_GGUF",
            displayName = "Local GGUF (On-Device)",
            displayNameAr = "GGUF محلي (على الجهاز)",
            inferenceSource = InferenceSource.OFFLINE,
            baseUrl = "",
            defaultModel = "Qwen3.5-2B-Q4_K_M.gguf",
            requiresApiKey = false,
            tier = ProviderTier.LOCAL_GGUF,
            badgeEn = "100% Offline & Private",
            badgeAr = "خاص 100% بدون إنترنت"
        )
    )

    fun findById(id: String): ProviderPreset? =
        PRESETS.firstOrNull { it.id.equals(id.trim(), ignoreCase = true) }

    fun inferPresetId(settings: AppSettings): String {
        val explicit = settings.providerPreset
        if (explicit.isNotBlank() && findById(explicit) != null) {
            return explicit.uppercase()
        }
        if (settings.inferenceSource == InferenceSource.OFFLINE) return "LOCAL_GGUF"
        val base = settings.openAiApiBase.lowercase()
        return when {
            base.contains("generativelanguage.googleapis.com") -> "GEMINI"
            base.contains("groq.com") -> "GROQ"
            base.contains("openrouter.ai") -> "OPENROUTER"
            base.contains(":11434") || base.contains("ollama") -> "OLLAMA"
            else -> "OPENAI"
        }
    }

    fun applyPreset(settings: AppSettings, presetId: String) {
        val preset = findById(presetId) ?: return
        settings.providerPreset = preset.id
        settings.inferenceSource = preset.inferenceSource
        if (preset.inferenceSource == InferenceSource.OPENAI_COMPAT) {
            settings.openAiApiBase = preset.baseUrl
            settings.openAiApiModel = preset.defaultModel
        }
    }

    fun activeDisplayLabel(settings: AppSettings): String {
        return when (settings.inferenceSource) {
            InferenceSource.OFFLINE -> {
                val file = settings.activeModelFile
                if (file.isNotBlank()) file.removeSuffix(".gguf") else "Local GGUF"
            }
            InferenceSource.LAN_BRIDGE -> "LAN Bridge"
            InferenceSource.OPENAI_COMPAT -> {
                val preset = findById(inferPresetId(settings))
                val model = settings.openAiApiModel.ifBlank { preset?.defaultModel ?: "Cloud" }
                "${preset?.id ?: "API"} · $model"
            }
        }
    }
}
