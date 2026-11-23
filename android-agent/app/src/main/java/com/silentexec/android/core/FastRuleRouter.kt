package com.silentexec.android.core

object FastRuleRouter {

    private val RE_SET_VOLUME_AR = Regex("""(?:ارفع|خفض|اضبط|عدل|غير)\s+(?:مستوى\s+)?الصوت\s+(?:الى\s+|إلى\s+)?(\d{1,3})""", RegexOption.IGNORE_CASE)
    private val RE_SET_VOLUME_EN = Regex("""(?:set|change|adjust)\s+volume\s+(?:to\s+)?(\d{1,3})""", RegexOption.IGNORE_CASE)

    fun route(input: String): ActionCommand? {
        val trimmed = input.trim()
        val lower = trimmed.lowercase()

        // 1. ui_back
        if (lower in listOf("ارجع", "رجوع", "للخلف", "ارجع للخلف", "ارجع ورا", "back", "go back", "exit")) {
            return ActionCommand(
                action = ActionType.UI_BACK,
                target = null,
                platform = Platform.ANDROID,
                confidence = 1.0
            )
        }

        // 2. ui_home
        if (lower in listOf("الشاشة الرئيسية", "الرئيسية", "الصفحة الرئيسية", "روح للرئيسية", "home", "home screen", "desktop")) {
            return ActionCommand(
                action = ActionType.UI_HOME,
                target = null,
                platform = Platform.ANDROID,
                confidence = 1.0
            )
        }

        // 3. ui_scroll
        if (lower in listOf("انزل", "انزل تحت", "انزل لتحت", "تحت", "scroll down", "down")) {
            return ActionCommand(
                action = ActionType.UI_SCROLL,
                target = "down",
                platform = Platform.ANDROID,
                confidence = 1.0
            )
        }
        if (lower in listOf("اطلع", "ارفع", "اطلع فوق", "اطلع لفوق", "فوق", "scroll up", "up")) {
            return ActionCommand(
                action = ActionType.UI_SCROLL,
                target = "up",
                platform = Platform.ANDROID,
                confidence = 1.0
            )
        }

        // 4. set_volume
        RE_SET_VOLUME_AR.find(trimmed)?.let { match ->
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
        RE_SET_VOLUME_EN.find(trimmed)?.let { match ->
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

        // 5. open_url
        if (lower.startsWith("open ") || lower.startsWith("افتح ") || lower.startsWith("شغل ")) {
            val rest = trimmed.replace(Regex("^(open|افتح|شغل)\\s+", RegexOption.IGNORE_CASE), "").trim()
            if (rest.startsWith("http://", ignoreCase = true) || rest.startsWith("https://", ignoreCase = true)) {
                return ActionCommand(
                    action = ActionType.OPEN_URL,
                    target = rest,
                    platform = Platform.ANDROID,
                    confidence = 1.0
                )
            }
        }

        return null
    }
}
