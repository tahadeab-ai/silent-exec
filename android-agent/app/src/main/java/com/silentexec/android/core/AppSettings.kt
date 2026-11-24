package com.silentexec.android.core

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

enum class ExecutionMode {
    SILENT,
    INTERACTIVE,
    DRY_RUN;

    companion object {
        fun fromString(value: String): ExecutionMode =
            runCatching { valueOf(value.trim().uppercase()) }.getOrDefault(SILENT)
    }
}

enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT;

    companion object {
        fun fromString(value: String): ThemeMode =
            runCatching { valueOf(value.trim().uppercase()) }.getOrDefault(SYSTEM)
    }
}

enum class InferenceSource {
    OFFLINE,
    LAN_BRIDGE;

    companion object {
        fun fromString(value: String): InferenceSource =
            runCatching { valueOf(value.trim().uppercase()) }.getOrDefault(OFFLINE)
    }
}

class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var language: String
        get() = prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    var themeMode: ThemeMode
        get() = ThemeMode.fromString(prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
        set(value) {
            prefs.edit().putString(KEY_THEME_MODE, value.name).apply()
            applyTheme()
        }

    var executionMode: ExecutionMode
        get() = ExecutionMode.fromString(prefs.getString(KEY_EXECUTION_MODE, ExecutionMode.SILENT.name) ?: ExecutionMode.SILENT.name)
        set(value) = prefs.edit().putString(KEY_EXECUTION_MODE, value.name).apply()

    var inferenceSource: InferenceSource
        get() = InferenceSource.fromString(prefs.getString(KEY_INFERENCE_SOURCE, InferenceSource.OFFLINE.name) ?: InferenceSource.OFFLINE.name)
        set(value) = prefs.edit().putString(KEY_INFERENCE_SOURCE, value.name).apply()

    var activeModelFile: String
        get() = prefs.getString(KEY_ACTIVE_MODEL_FILE, DEFAULT_MODEL_FILE) ?: DEFAULT_MODEL_FILE
        set(value) = prefs.edit().putString(KEY_ACTIVE_MODEL_FILE, value).apply()

    var fastRouterEnabled: Boolean
        get() = prefs.getBoolean(KEY_FAST_ROUTER, true)
        set(value) = prefs.edit().putBoolean(KEY_FAST_ROUTER, value).apply()

    var floatingBubbleEnabled: Boolean
        get() = prefs.getBoolean(KEY_FLOATING_BUBBLE, false)
        set(value) = prefs.edit().putBoolean(KEY_FLOATING_BUBBLE, value).apply()

    var bridgeHost: String
        get() = prefs.getString(KEY_BRIDGE_HOST, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BRIDGE_HOST, value).apply()

    var bridgePort: Int
        get() = prefs.getInt(KEY_BRIDGE_PORT, 8765)
        set(value) = prefs.edit().putInt(KEY_BRIDGE_PORT, value).apply()

    var bridgeToken: String
        get() = prefs.getString(KEY_BRIDGE_TOKEN, "silent-exec-secret") ?: "silent-exec-secret"
        set(value) = prefs.edit().putString(KEY_BRIDGE_TOKEN, value).apply()

    fun applyLocale() {
        val appLocale = LocaleListCompat.forLanguageTags(language)
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

    fun applyTheme() {
        val nightMode = when (themeMode) {
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    fun setLanguageAndApply(lang: String) {
        val validLang = if (lang.equals("en", ignoreCase = true)) "en" else "ar"
        this.language = validLang
        applyLocale()
    }

    companion object {
        const val PREFS_NAME = "silent_exec_settings"
        const val KEY_LANGUAGE = "setting_language"
        const val KEY_THEME_MODE = "setting_theme_mode"
        const val KEY_EXECUTION_MODE = "setting_execution_mode"
        const val KEY_INFERENCE_SOURCE = "setting_inference_source"
        const val KEY_ACTIVE_MODEL_FILE = "setting_active_model_file"
        const val KEY_FAST_ROUTER = "setting_fast_router"
        const val KEY_FLOATING_BUBBLE = "setting_floating_bubble"
        const val KEY_BRIDGE_HOST = "setting_bridge_host"
        const val KEY_BRIDGE_PORT = "setting_bridge_port"
        const val KEY_BRIDGE_TOKEN = "setting_bridge_token"

        const val DEFAULT_LANGUAGE = "ar"
        const val DEFAULT_MODEL_FILE = "Qwen2.5-0.5B-Instruct.Q4_K_M.gguf"
    }
}
