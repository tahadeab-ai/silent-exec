package com.silentexec.android.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@Serializable
enum class ActionType {
    @SerialName("open_folder")
    OPEN_FOLDER,

    @SerialName("open_file")
    OPEN_FILE,

    @SerialName("launch_app")
    LAUNCH_APP,

    @SerialName("search_files")
    SEARCH_FILES,

    @SerialName("set_volume")
    SET_VOLUME,

    @SerialName("set_brightness")
    SET_BRIGHTNESS,

    @SerialName("create_note")
    CREATE_NOTE,

    @SerialName("copy_to_clipboard")
    COPY_TO_CLIPBOARD,

    @SerialName("delete_file")
    DELETE_FILE,

    @SerialName("move_file")
    MOVE_FILE,

    @SerialName("system_power")
    SYSTEM_POWER,

    @SerialName("open_android_app")
    OPEN_ANDROID_APP,

    @SerialName("toggle_setting")
    TOGGLE_SETTING,

    @SerialName("send_message")
    SEND_MESSAGE,

    @SerialName("open_url")
    OPEN_URL,

    @SerialName("ui_click")
    UI_CLICK,

    @SerialName("ui_type")
    UI_TYPE,

    @SerialName("ui_scroll")
    UI_SCROLL,

    @SerialName("ui_back")
    UI_BACK,

    @SerialName("ui_home")
    UI_HOME;

    val tier: SafetyTier
        get() = when (this) {
            DELETE_FILE,
            MOVE_FILE,
            SYSTEM_POWER,
            TOGGLE_SETTING,
            SEND_MESSAGE -> SafetyTier.TIER_2_CONFIRM
            else -> SafetyTier.TIER_1_SILENT
        }

    val wireName: String
        get() = when (this) {
            OPEN_FOLDER -> "open_folder"
            OPEN_FILE -> "open_file"
            LAUNCH_APP -> "launch_app"
            SEARCH_FILES -> "search_files"
            SET_VOLUME -> "set_volume"
            SET_BRIGHTNESS -> "set_brightness"
            CREATE_NOTE -> "create_note"
            COPY_TO_CLIPBOARD -> "copy_to_clipboard"
            DELETE_FILE -> "delete_file"
            MOVE_FILE -> "move_file"
            SYSTEM_POWER -> "system_power"
            OPEN_ANDROID_APP -> "open_android_app"
            TOGGLE_SETTING -> "toggle_setting"
            SEND_MESSAGE -> "send_message"
            OPEN_URL -> "open_url"
            UI_CLICK -> "ui_click"
            UI_TYPE -> "ui_type"
            UI_SCROLL -> "ui_scroll"
            UI_BACK -> "ui_back"
            UI_HOME -> "ui_home"
        }
}

@Serializable
enum class Platform {
    @SerialName("pc")
    PC,

    @SerialName("android")
    ANDROID,

    @SerialName("any")
    ANY
}

enum class SafetyTier {
    TIER_1_SILENT,
    TIER_2_CONFIRM
}

@Serializable
data class ActionCommand(
    val action: ActionType,
    val target: String? = null,
    val folder: String? = null,
    val args: JsonElement? = null,
    val platform: Platform,
    val confidence: Double
) {
    init {
        require(confidence in 0.0..1.0) {
            "Confidence score $confidence is out of valid range [0.0, 1.0]"
        }
    }
}

object ActionJson {
    val strict = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = true
    }

    fun fromJson(jsonStr: String): ActionCommand {
        return strict.decodeFromString<ActionCommand>(jsonStr)
    }

    fun toJson(cmd: ActionCommand): String {
        return strict.encodeToString(cmd)
    }
}
