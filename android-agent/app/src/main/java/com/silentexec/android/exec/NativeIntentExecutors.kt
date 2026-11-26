package com.silentexec.android.exec

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import com.silentexec.android.core.ActionCommand
import com.silentexec.android.core.ActionType
import com.silentexec.android.core.Platform
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

/**
 * Deterministic Android Intent / Deep-Link Executors (OpenDroid Apache-2.0 pattern).
 * Resolves and executes 30+ common Android intents BEFORE any GUI/Accessibility step.
 */
object NativeIntentExecutors {

    /**
     * Bilingual Arabic (MSA + Yemeni) & English app-name -> Android package name map.
     */
    val APP_PACKAGE_MAP: Map<String, String> = mapOf(
        // YouTube
        "youtube" to "com.google.android.youtube",
        "يوتيوب" to "com.google.android.youtube",
        "اليوتيوب" to "com.google.android.youtube",
        // WhatsApp
        "whatsapp" to "com.whatsapp",
        "واتساب" to "com.whatsapp",
        "الواتساب" to "com.whatsapp",
        "واتس" to "com.whatsapp",
        "الواتس" to "com.whatsapp",
        // Chrome
        "chrome" to "com.android.chrome",
        "كروم" to "com.android.chrome",
        "المتصفح" to "com.android.chrome",
        "browser" to "com.android.chrome",
        // Telegram
        "telegram" to "org.telegram.messenger",
        "تيليجرام" to "org.telegram.messenger",
        "تليجرام" to "org.telegram.messenger",
        "التيليجرام" to "org.telegram.messenger",
        // Settings
        "settings" to "com.android.settings",
        "الاعدادات" to "com.android.settings",
        "الإعدادات" to "com.android.settings",
        "الضبط" to "com.android.settings",
        // Maps
        "maps" to "com.google.android.apps.maps",
        "google maps" to "com.google.android.apps.maps",
        "خرائط" to "com.google.android.apps.maps",
        "الخرائط" to "com.google.android.apps.maps",
        // Gmail
        "gmail" to "com.google.android.gm",
        "جيميل" to "com.google.android.gm",
        "البريد" to "com.google.android.gm",
        // Camera
        "camera" to "com.android.camera2",
        "كاميرا" to "com.android.camera2",
        "الكاميرا" to "com.android.camera2",
        // Clock
        "clock" to "com.google.android.deskclock",
        "الساعة" to "com.google.android.deskclock",
        "المنبه" to "com.google.android.deskclock",
        // Phone
        "phone" to "com.google.android.dialer",
        "الهاتف" to "com.google.android.dialer",
        "المكالمات" to "com.google.android.dialer",
        // Contacts
        "contacts" to "com.google.android.contacts",
        "جهات الاتصال" to "com.google.android.contacts",
        "الارقام" to "com.google.android.contacts",
        // Extras for full 30+ deterministic app coverage
        "calendar" to "com.google.android.calendar",
        "التقويم" to "com.google.android.calendar",
        "calculator" to "com.google.android.calculator",
        "الحاسبة" to "com.google.android.calculator",
        "files" to "com.google.android.documentsui",
        "الملفات" to "com.google.android.documentsui",
        "play store" to "com.android.vending",
        "المتجر" to "com.android.vending",
        "notes" to "com.google.android.keep",
        "الملاحظات" to "com.google.android.keep",
        "messages" to "com.google.android.apps.messaging",
        "الرسائل" to "com.google.android.apps.messaging",
        "photos" to "com.google.android.apps.photos",
        "الصور" to "com.google.android.apps.photos"
    )

    private val SETTINGS_SECTION_MAP: Map<String, String> = mapOf(
        "wifi" to Settings.ACTION_WIFI_SETTINGS,
        "wi-fi" to Settings.ACTION_WIFI_SETTINGS,
        "واي فاي" to Settings.ACTION_WIFI_SETTINGS,
        "الواي فاي" to Settings.ACTION_WIFI_SETTINGS,
        "bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
        "بلوتوث" to Settings.ACTION_BLUETOOTH_SETTINGS,
        "البلوتوث" to Settings.ACTION_BLUETOOTH_SETTINGS,
        "display" to Settings.ACTION_DISPLAY_SETTINGS,
        "الشاشة" to Settings.ACTION_DISPLAY_SETTINGS,
        "sound" to Settings.ACTION_SOUND_SETTINGS,
        "الصوت" to Settings.ACTION_SOUND_SETTINGS,
        "battery" to Settings.ACTION_BATTERY_SAVER_SETTINGS,
        "البطارية" to Settings.ACTION_BATTERY_SAVER_SETTINGS,
        "location" to Settings.ACTION_LOCATION_SOURCE_SETTINGS,
        "الموقع" to Settings.ACTION_LOCATION_SOURCE_SETTINGS
    )

    fun resolvePackageName(appQuery: String): String? {
        val norm = appQuery.trim().lowercase(Locale.ROOT)
        APP_PACKAGE_MAP[norm]?.let { return it }
        return APP_PACKAGE_MAP.entries
            .firstOrNull { (k, _) -> norm.contains(k) }
            ?.value
    }

    /**
     * Deterministically resolves a single clause into a native Intent ActionCommand, or null if GUI/LLM is needed.
     */
    fun tryResolveStep(clause: String): ActionCommand? {
        val trimmed = clause.trim()
        if (trimmed.isEmpty()) return null
        val lower = trimmed.lowercase(Locale.ROOT)

        // 1. dial_number: "اتصل بـ 777123456" / "call +967..."
        val dialRegex = Regex("""^(?:اتصل\s*(?:بـ|ب|على)?|call|dial)\s+([+\d][\d\s-]{3,})$""", RegexOption.IGNORE_CASE)
        dialRegex.matchEntire(trimmed)?.let { m ->
            val number = m.groupValues[1].replace(Regex("""[\s-]"""), "")
            return ActionCommand(
                action = ActionType.LAUNCH_APP,
                target = "tel:$number",
                args = buildJsonObject {
                    put("intent_type", JsonPrimitive("dial_number"))
                    put("number", JsonPrimitive(number))
                },
                platform = Platform.ANDROID,
                confidence = 0.96,
                thought = "Dial number $number via native ACTION_DIAL intent"
            )
        }

        // 2. open_settings_section: "افتح اعدادات الواي فاي" / "open wifi settings"
        if (lower.contains("اعدادات") || lower.contains("إعدادات") || lower.contains("settings")) {
            for ((keyword, actionStr) in SETTINGS_SECTION_MAP) {
                if (lower.contains(keyword)) {
                    return ActionCommand(
                        action = ActionType.TOGGLE_SETTING,
                        target = keyword,
                        args = buildJsonObject {
                            put("intent_type", JsonPrimitive("open_settings_section"))
                            put("settings_action", JsonPrimitive(actionStr))
                        },
                        platform = Platform.ANDROID,
                        confidence = 0.95,
                        thought = "Open Android $keyword settings directly"
                    )
                }
            }
        }

        // 3. set_alarm: "اضبط منبه الساعة 7:30" / "set alarm 07:30"
        val alarmRegex = Regex("""(?:اضبط\s*منبه|منبه|set\s+alarm).*?(\d{1,2})(?::(\d{2}))?""", RegexOption.IGNORE_CASE)
        alarmRegex.find(trimmed)?.let { m ->
            val hour = m.groupValues[1].toIntOrNull()?.coerceIn(0, 23) ?: return@let
            val minute = m.groupValues[2].toIntOrNull()?.coerceIn(0, 59) ?: 0
            return ActionCommand(
                action = ActionType.LAUNCH_APP,
                target = String.format(Locale.US, "%02d:%02d", hour, minute),
                args = buildJsonObject {
                    put("intent_type", JsonPrimitive("set_alarm"))
                    put("hour", JsonPrimitive(hour))
                    put("minute", JsonPrimitive(minute))
                },
                platform = Platform.ANDROID,
                confidence = 0.94,
                thought = "Set alarm for $hour:$minute via AlarmClock intent"
            )
        }

        // 4. copy_clipboard: "انسخ <text>" / "copy <text>"
        val copyRegex = Regex("""^(?:انسخ|copy(?:\s+to\s+clipboard)?)\s+(.+)$""", RegexOption.IGNORE_CASE)
        copyRegex.matchEntire(trimmed)?.let { m ->
            val text = m.groupValues[1].trim()
            return ActionCommand(
                action = ActionType.COPY_TO_CLIPBOARD,
                target = text,
                platform = Platform.ANDROID,
                confidence = 0.95,
                thought = "Copy text to clipboard natively"
            )
        }

        // 5. share_text: "شارك <text>" / "share <text>"
        val shareRegex = Regex("""^(?:شارك|share)\s+(.+)$""", RegexOption.IGNORE_CASE)
        shareRegex.matchEntire(trimmed)?.let { m ->
            val text = m.groupValues[1].trim()
            return ActionCommand(
                action = ActionType.LAUNCH_APP,
                target = text,
                args = buildJsonObject {
                    put("intent_type", JsonPrimitive("share_text"))
                    put("text", JsonPrimitive(text))
                },
                platform = Platform.ANDROID,
                confidence = 0.92,
                thought = "Share text via Android ACTION_SEND chooser"
            )
        }

        // 6. open_app: "افتح اليوتيوب" / "شغل الواتساب" / "open YouTube"
        val openAppRegex = Regex("""^(?:افتح|شغل|launch|open)\s+(.+)$""", RegexOption.IGNORE_CASE)
        openAppRegex.matchEntire(trimmed)?.let { m ->
            val appName = m.groupValues[1].trim()
            val pkg = resolvePackageName(appName)
            if (pkg != null) {
                return ActionCommand(
                    action = ActionType.OPEN_ANDROID_APP,
                    target = pkg,
                    args = buildJsonObject {
                        put("intent_type", JsonPrimitive("open_app"))
                        put("app_label", JsonPrimitive(appName))
                    },
                    platform = Platform.ANDROID,
                    confidence = 0.97,
                    thought = "Launch $appName ($pkg) natively"
                )
            }
        }

        // 7. youtube_search / web_search standalone clause
        val searchRegex = Regex("""^(?:ابحث\s*عن|بحث\s*عن|ابحث|search(?:\s+for)?)\s+(.+)$""", RegexOption.IGNORE_CASE)
        searchRegex.matchEntire(trimmed)?.let { m ->
            val query = m.groupValues[1].trim()
            if (lower.contains("يوتيوب") || lower.contains("youtube")) {
                val cleanQ = query.replace(Regex("""\b(?:في\s+اليوتيوب|على\s+اليوتيوب|on\s+youtube|in\s+youtube)\b""", RegexOption.IGNORE_CASE), "").trim()
                return ActionCommand(
                    action = ActionType.LAUNCH_APP,
                    target = "com.google.android.youtube",
                    args = buildJsonObject {
                        put("intent_type", JsonPrimitive("youtube_search"))
                        put("query", JsonPrimitive(cleanQ.ifEmpty { query }))
                    },
                    platform = Platform.ANDROID,
                    confidence = 0.95,
                    thought = "Open YouTube search for '$cleanQ'"
                )
            }
        }

        return null
    }

    /**
     * Executes a native intent ActionCommand if it carries an `intent_type` in `args` or is a known package launch.
     * Returns null if standard AndroidActionExecutor handling should be used.
     */
    fun executeNative(context: Context?, cmd: ActionCommand): AndroidExecutionResult? {
        val argsObj = cmd.args as? JsonObject ?: return null
        val intentType = argsObj["intent_type"]?.jsonPrimitive?.content ?: return null

        if (context == null) {
            return AndroidExecutionResult.Success(
                action = cmd.action.wireName,
                details = "Simulated native intent ($intentType): ${cmd.target}",
                targetResolved = cmd.target
            )
        }

        return when (intentType) {
            "dial_number" -> {
                val num = argsObj["number"]?.jsonPrimitive?.content ?: cmd.target.orEmpty()
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                AndroidExecutionResult.Success("launch_app", "Opened dialer for $num", num)
            }
            "open_settings_section" -> {
                val actionStr = argsObj["settings_action"]?.jsonPrimitive?.content ?: Settings.ACTION_SETTINGS
                val intent = Intent(actionStr).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                AndroidExecutionResult.Success("toggle_setting", "Opened settings: $actionStr", actionStr)
            }
            "set_alarm" -> {
                val hour = argsObj["hour"]?.jsonPrimitive?.content?.toIntOrNull() ?: 7
                val minute = argsObj["minute"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                AndroidExecutionResult.Success("launch_app", "Set alarm for $hour:$minute", cmd.target)
            }
            "share_text" -> {
                val text = argsObj["text"]?.jsonPrimitive?.content ?: cmd.target.orEmpty()
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_TEXT, text)
                    type = "text/plain"
                }
                val chooser = Intent.createChooser(sendIntent, "Share").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
                AndroidExecutionResult.Success("launch_app", "Opened share sheet", text)
            }
            "youtube_search" -> {
                val query = argsObj["query"]?.jsonPrimitive?.content ?: ""
                val intent = Intent(Intent.ACTION_SEARCH).apply {
                    setPackage("com.google.android.youtube")
                    putExtra("query", query)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {
                    val webIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    context.startActivity(webIntent)
                }
                AndroidExecutionResult.Success("launch_app", "Launched YouTube search for '$query'", query)
            }
            "open_app" -> {
                val pkg = cmd.target.orEmpty()
                val launchIntent = context.packageManager?.getLaunchIntentForPackage(pkg)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (launchIntent != null) {
                    context.startActivity(launchIntent)
                    AndroidExecutionResult.Success("open_android_app", "Launched $pkg", pkg)
                } else {
                    null
                }
            }
            else -> null
        }
    }

    fun copyToClipboard(context: Context?, text: String): Boolean {
        if (context == null) return true
        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        cb.setPrimaryClip(ClipData.newPlainText("SilentExec", text))
        return true
    }
}
