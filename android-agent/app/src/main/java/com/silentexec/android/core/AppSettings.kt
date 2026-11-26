package com.silentexec.android.core

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.concurrent.ConcurrentHashMap

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
    LAN_BRIDGE,
    OPENAI_COMPAT;

    companion object {
        fun fromString(value: String): InferenceSource =
            runCatching { valueOf(value.trim().uppercase()) }.getOrDefault(OFFLINE)
    }
}

class InMemorySharedPreferences : SharedPreferences {
    private val data = ConcurrentHashMap<String, Any>()

    override fun getAll(): MutableMap<String, *> = HashMap(data)

    override fun getString(key: String?, defValue: String?): String? =
        (data[key] as? String) ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (data[key] as? MutableSet<String>) ?: defValues

    override fun getInt(key: String?, defValue: Int): Int =
        (data[key] as? Int) ?: defValue

    override fun getLong(key: String?, defValue: Long): Long =
        (data[key] as? Long) ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float =
        (data[key] as? Float) ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        (data[key] as? Boolean) ?: defValue

    override fun contains(key: String?): Boolean = data.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor(this)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    class Editor(private val parent: InMemorySharedPreferences) : SharedPreferences.Editor {
        private val pending = HashMap<String, Any?>()
        private var clear = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            if (key != null) pending[key] = values
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) pending[key] = this
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clear = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clear) parent.data.clear()
            for ((k, v) in pending) {
                if (v === this) {
                    parent.data.remove(k)
                } else if (v != null) {
                    parent.data[k] = v
                } else {
                    parent.data.remove(k)
                }
            }
        }
    }
}

class AppSettings(
    context: Context? = null,
    injectedPrefs: SharedPreferences? = null
) {

    private val prefs: SharedPreferences =
        injectedPrefs ?: context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        ?: InMemorySharedPreferences()

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
        get() = prefs.getString(KEY_ACTIVE_MODEL_FILE, "") ?: ""
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
        get() = prefs.getString(KEY_BRIDGE_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BRIDGE_TOKEN, value).apply()

    var openAiApiBase: String
        get() = prefs.getString(KEY_OPENAI_API_BASE, DEFAULT_OPENAI_API_BASE) ?: DEFAULT_OPENAI_API_BASE
        set(value) = prefs.edit().putString(KEY_OPENAI_API_BASE, value).apply()

    var openAiApiModel: String
        get() = prefs.getString(KEY_OPENAI_API_MODEL, DEFAULT_OPENAI_API_MODEL) ?: DEFAULT_OPENAI_API_MODEL
        set(value) = prefs.edit().putString(KEY_OPENAI_API_MODEL, value).apply()

    var openAiApiKey: String
        get() = prefs.getString(KEY_OPENAI_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_OPENAI_API_KEY, value).apply()

    var providerPreset: String
        get() = prefs.getString(KEY_PROVIDER_PRESET, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PROVIDER_PRESET, value).apply()

    fun applyLocale() {
        runCatching {
            val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(language)
            AppCompatDelegate.setApplicationLocales(appLocale)
        }
    }

    fun applyTheme() {
        runCatching {
            val nightMode = when (themeMode) {
                ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
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
        const val KEY_OPENAI_API_BASE = "setting_openai_api_base"
        const val KEY_OPENAI_API_MODEL = "setting_openai_api_model"
        const val KEY_OPENAI_API_KEY = "setting_openai_api_key"
        const val KEY_PROVIDER_PRESET = "setting_provider_preset"

        const val DEFAULT_LANGUAGE = "ar"
        const val DEFAULT_OPENAI_API_BASE = "https://api.openai.com/v1"
        const val DEFAULT_OPENAI_API_MODEL = "gpt-4o-mini"
    }
}
