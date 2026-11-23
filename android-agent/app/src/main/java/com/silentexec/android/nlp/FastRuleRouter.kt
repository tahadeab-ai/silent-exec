package com.silentexec.android.nlp

import com.silentexec.android.core.ActionCommand
import com.silentexec.android.core.ActionType
import com.silentexec.android.core.Platform

object FastRuleRouter {

    private val RE_SET_VOLUME_AR = Regex("""(?:ارفع|خفض|اضبط|عدل|غير)\s+(?:مستوى\s+)?الصوت\s+(?:الى\s+|إلى\s+)?(\d{1,3})""", RegexOption.IGNORE_CASE)
    private val RE_SET_VOLUME_EN = Regex("""(?:set|change|adjust)\s+volume\s+(?:to\s+)?(\d{1,3})""", RegexOption.IGNORE_CASE)

    private val RE_CLICK_AR = Regex("""^(?:اضغط على|انقر على|دوس على|اضغط|انقر|دوس)\s+(?:على\s+)?(.+)$""", RegexOption.IGNORE_CASE)
    private val RE_CLICK_EN = Regex("""^(?:click\s+on|tap\s+on|click|tap|press)\s+(?:on\s+)?(.+)$""", RegexOption.IGNORE_CASE)

    private val RE_LAUNCH_AR = Regex("""^(?:افتح|شغل|شغل لي|افتح لي)\s+(?:تطبيق\s+)?(.+)$""", RegexOption.IGNORE_CASE)
    private val RE_LAUNCH_EN = Regex("""^(?:open|launch)\s+(?:app\s+)?(.+)$""", RegexOption.IGNORE_CASE)

    /**
     * Normalizes Arabic text:
     * - Strips Tashkeel
     * - Normalizes Alef variants (أ / إ / آ -> ا)
     * - Normalizes Teh Marbuta (ة -> ه)
     * - Normalizes Alef Maksura (ى -> ي)
     * - Strips Tatweel (ـ)
     * - English lowercase & whitespace trim
     */
    fun normalize(text: String): String {
        return text
            .replace(Regex("""[\u064B-\u065F\u0670]"""), "")
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ة', 'ه')
            .replace('ى', 'ي')
            .replace("ـ", "")
            .lowercase()
            .trim()
    }

    /**
     * Deterministic, sub-15ms fast rule router.
     * Returns an ActionCommand if a known direct pattern is matched, or null to fall back to LLM inference.
     */
    fun route(input: String): ActionCommand? {
        val trimmed = input.trim()
        val norm = normalize(trimmed)

        // 1. ui_back
        if (norm in listOf("ارجع", "رجوع", "للخلف", "ارجع للخلف", "ارجع ورا", "الخلف", "back", "go back", "exit")) {
            return ActionCommand(
                action = ActionType.UI_BACK,
                target = null,
                platform = Platform.ANDROID,
                confidence = 1.0
            )
        }

        // 2. ui_home
        if (norm in listOf("الشاشه الرئيسيه", "الرئيسيه", "الصفحه الرئيسيه", "روح للرئيسيه", "سطح المكتب", "home", "home screen", "desktop")) {
            return ActionCommand(
                action = ActionType.UI_HOME,
                target = null,
                platform = Platform.ANDROID,
                confidence = 1.0
            )
        }

        // 3. ui_scroll
        if (norm in listOf("انزل", "انزل تحت", "انزل لتحت", "تحت", "اسفل", "scroll down", "down")) {
            return ActionCommand(
                action = ActionType.UI_SCROLL,
                target = "down",
                platform = Platform.ANDROID,
                confidence = 1.0
            )
        }
        if (norm in listOf("اطلع", "ارفع", "اطلع فوق", "اطلع لفوق", "فوق", "اعلي", "اعلى", "scroll up", "up")) {
            return ActionCommand(
                action = ActionType.UI_SCROLL,
                target = "up",
                platform = Platform.ANDROID,
                confidence = 1.0
            )
        }

        // 4. set_volume
        RE_SET_VOLUME_AR.find(norm)?.let { match ->
            val level = match.groupValues[1].toIntOrNull()
            if (level != null && level in 0..100) {
                return ActionCommand(
                    action = ActionType.SET_VOLUME,
                    target = level.toString(),
                    platform = Platform.ANDROID,
                    confidence = 1.0
                )
            }
        }
        RE_SET_VOLUME_EN.find(norm)?.let { match ->
            val level = match.groupValues[1].toIntOrNull()
            if (level != null && level in 0..100) {
                return ActionCommand(
                    action = ActionType.SET_VOLUME,
                    target = level.toString(),
                    platform = Platform.ANDROID,
                    confidence = 1.0
                )
            }
        }

        // 5. ui_click
        RE_CLICK_AR.find(trimmed)?.let { match ->
            val target = match.groupValues[1].trim()
            if (target.isNotBlank()) {
                return ActionCommand(
                    action = ActionType.UI_CLICK,
                    target = target,
                    platform = Platform.ANDROID,
                    confidence = 1.0
                )
            }
        }
        RE_CLICK_EN.find(trimmed)?.let { match ->
            val target = match.groupValues[1].trim()
            if (target.isNotBlank()) {
                return ActionCommand(
                    action = ActionType.UI_CLICK,
                    target = target,
                    platform = Platform.ANDROID,
                    confidence = 1.0
                )
            }
        }

        // 6. open_url
        if (norm.startsWith("open ") || norm.startsWith("افتح ") || norm.startsWith("شغل ")) {
            val rest = trimmed.replace(Regex("^(open|افتح|شغل|launch)\\s+", RegexOption.IGNORE_CASE), "").trim()
            if (rest.startsWith("http://", ignoreCase = true) || rest.startsWith("https://", ignoreCase = true)) {
                return ActionCommand(
                    action = ActionType.OPEN_URL,
                    target = rest,
                    platform = Platform.ANDROID,
                    confidence = 1.0
                )
            }
        }

        // 7. open_android_app
        RE_LAUNCH_AR.find(trimmed)?.let { match ->
            val appTarget = match.groupValues[1].trim()
            if (appTarget.isNotBlank() && !appTarget.startsWith("http", ignoreCase = true)) {
                return ActionCommand(
                    action = ActionType.OPEN_ANDROID_APP,
                    target = appTarget,
                    platform = Platform.ANDROID,
                    confidence = 0.95
                )
            }
        }
        RE_LAUNCH_EN.find(trimmed)?.let { match ->
            val appTarget = match.groupValues[1].trim()
            if (appTarget.isNotBlank() && !appTarget.startsWith("http", ignoreCase = true)) {
                return ActionCommand(
                    action = ActionType.OPEN_ANDROID_APP,
                    target = appTarget,
                    platform = Platform.ANDROID,
                    confidence = 0.95
                )
            }
        }

        return null
    }
}
