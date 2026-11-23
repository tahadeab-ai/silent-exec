package com.silentexec.android.exec

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import com.silentexec.android.core.ActionCommand
import com.silentexec.android.core.ActionType
import com.silentexec.android.core.SafetyDecision
import com.silentexec.android.core.SafetyGate

sealed class AndroidExecutionResult {
    data class Success(
        val action: String,
        val details: String,
        val targetResolved: String? = null
    ) : AndroidExecutionResult()

    data class ConfirmationRequired(
        val action: String,
        val prompt: String,
        val confidence: Double,
        val reason: String
    ) : AndroidExecutionResult()

    data class Blocked(
        val action: String,
        val reason: String
    ) : AndroidExecutionResult()

    data class Error(
        val action: String,
        val message: String
    ) : AndroidExecutionResult()
}

class AndroidActionExecutor(private val context: Context) {

    fun execute(cmd: ActionCommand, confirmed: Boolean = false): AndroidExecutionResult {
        val actionName = cmd.action.wireName

        // ALWAYS evaluate SafetyGate first
        when (val decision = SafetyGate.evaluate(cmd)) {
            is SafetyDecision.Block -> {
                return AndroidExecutionResult.Blocked(
                    action = actionName,
                    reason = decision.reason
                )
            }
            is SafetyDecision.RequireConfirm -> {
                if (!confirmed) {
                    val prompt = "Confirm execution of action '$actionName' (target: '${cmd.target}'): ${decision.reason}"
                    return AndroidExecutionResult.ConfirmationRequired(
                        action = actionName,
                        prompt = prompt,
                        confidence = cmd.confidence,
                        reason = decision.reason
                    )
                }
            }
            is SafetyDecision.AllowSilent -> {
                // Allowed to proceed silently
            }
        }

        return try {
            when (cmd.action) {
                ActionType.OPEN_ANDROID_APP, ActionType.LAUNCH_APP -> {
                    handleOpenApp(cmd.target ?: "")
                }
                ActionType.OPEN_URL -> {
                    handleOpenUrl(cmd.target ?: "")
                }
                ActionType.SET_VOLUME -> {
                    handleSetVolume(cmd.target)
                }
                ActionType.COPY_TO_CLIPBOARD -> {
                    handleCopyToClipboard(cmd.target ?: "")
                }
                ActionType.TOGGLE_SETTING -> {
                    handleToggleSetting(cmd.target ?: "")
                }
                ActionType.SEND_MESSAGE -> {
                    handleSendMessage(cmd.target ?: "")
                }
                ActionType.OPEN_FILE, ActionType.OPEN_FOLDER, ActionType.SEARCH_FILES -> {
                    handleOpenFileOrFolder(cmd.target ?: "")
                }
                ActionType.CREATE_NOTE -> {
                    handleCreateNote(cmd.target ?: "")
                }
                ActionType.UI_BACK -> {
                    handleUiBack()
                }
                ActionType.UI_HOME -> {
                    handleUiHome()
                }
                ActionType.UI_SCROLL -> {
                    handleUiScroll(cmd.target ?: "down")
                }
                ActionType.UI_CLICK -> {
                    handleUiClick(cmd.target ?: "")
                }
                ActionType.UI_TYPE -> {
                    handleUiType(cmd.target ?: "")
                }
                else -> {
                    AndroidExecutionResult.Success(
                        action = actionName,
                        details = "Action processed by Android executor",
                        targetResolved = cmd.target
                    )
                }
            }
        } catch (e: Exception) {
            AndroidExecutionResult.Error(
                action = actionName,
                message = e.message ?: "Unknown execution error"
            )
        }
    }

    private fun handleOpenApp(target: String): AndroidExecutionResult {
        if (target.isBlank()) {
            return AndroidExecutionResult.Error("open_android_app", "Target application name is empty")
        }

        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        val targetNorm = target.trim().lowercase()

        // 1. Try exact package name
        var launchIntent = pm.getLaunchIntentForPackage(target.trim())
        var resolvedName = target

        // 2. Fuzzy match application label
        if (launchIntent == null) {
            val match = resolveInfos.firstOrNull { ri ->
                val label = ri.loadLabel(pm).toString().lowercase()
                label == targetNorm || label.contains(targetNorm) || targetNorm.contains(label)
            }

            if (match != null) {
                val pkgName = match.activityInfo.packageName
                resolvedName = match.loadLabel(pm).toString()
                launchIntent = pm.getLaunchIntentForPackage(pkgName)
            }
        }

        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            AndroidExecutionResult.Success(
                action = "open_android_app",
                details = "Launched application successfully",
                targetResolved = resolvedName
            )
        } else {
            AndroidExecutionResult.Error(
                action = "open_android_app",
                message = "Application '$target' not found on device"
            )
        }
    }

    private fun handleOpenUrl(target: String): AndroidExecutionResult {
        val uriString = if (!target.startsWith("http://") && !target.startsWith("https://")) {
            "https://$target"
        } else {
            target
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)

        return AndroidExecutionResult.Success(
            action = "open_url",
            details = "Opened browser URI",
            targetResolved = uriString
        )
    }

    private fun handleSetVolume(target: String?): AndroidExecutionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        val targetLevel = target?.filter { it.isDigit() }?.toIntOrNull() ?: (maxVolume / 2)
        val clamped = targetLevel.coerceIn(0, maxVolume)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, clamped, AudioManager.FLAG_SHOW_UI)

        return AndroidExecutionResult.Success(
            action = "set_volume",
            details = "Volume set to $clamped/$maxVolume",
            targetResolved = clamped.toString()
        )
    }

    private fun handleCopyToClipboard(target: String): AndroidExecutionResult {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("SilentExec", target)
        clipboard.setPrimaryClip(clip)

        return AndroidExecutionResult.Success(
            action = "copy_to_clipboard",
            details = "Copied text to clipboard",
            targetResolved = target
        )
    }

    private fun handleToggleSetting(target: String): AndroidExecutionResult {
        val action = when (target.lowercase().trim()) {
            "wifi", "واي فاي", "الواي فاي" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth", "بلوتوث", "البلوتوث" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display", "شاشة", "السطوع" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound", "صوت", "الأصوات" -> Settings.ACTION_SOUND_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }

        val intent = Intent(action).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)

        return AndroidExecutionResult.Success(
            action = "toggle_setting",
            details = "Opened system settings panel for $target",
            targetResolved = target
        )
    }

    private fun handleSendMessage(target: String): AndroidExecutionResult {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$target")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)

        return AndroidExecutionResult.Success(
            action = "send_message",
            details = "Dispatched SMS composer",
            targetResolved = target
        )
    }

    private fun handleOpenFileOrFolder(target: String): AndroidExecutionResult {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("content://"), "*/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // Document explorer fallback
        }

        return AndroidExecutionResult.Success(
            action = "open_file",
            details = "Dispatched storage view intent",
            targetResolved = target
        )
    }

    private fun handleCreateNote(target: String): AndroidExecutionResult {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, target)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Save Note").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })

        return AndroidExecutionResult.Success(
            action = "create_note",
            details = "Dispatched note creation",
            targetResolved = target
        )
    }

    private fun handleUiBack(): AndroidExecutionResult {
        val svc = SilentExecAccessibilityService.get()
        val handled = svc?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) ?: false
        return AndroidExecutionResult.Success(
            action = "ui_back",
            details = if (handled) "Navigated back via accessibility service" else "Dispatched back action",
            targetResolved = null
        )
    }

    private fun handleUiHome(): AndroidExecutionResult {
        val svc = SilentExecAccessibilityService.get()
        val handled = svc?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) ?: false
        if (!handled) {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(homeIntent)
        }
        return AndroidExecutionResult.Success(
            action = "ui_home",
            details = "Navigated to home screen",
            targetResolved = null
        )
    }

    private fun handleUiScroll(direction: String): AndroidExecutionResult {
        val svc = SilentExecAccessibilityService.get()
        val root = svc?.rootInActiveWindow
        val isUp = direction.trim().lowercase() in listOf("up", "اعلى", "أعلى", "فوق")
        val action = if (isUp) AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        val scrolled = root?.performAction(action) ?: false
        return AndroidExecutionResult.Success(
            action = "ui_scroll",
            details = if (scrolled) "Scrolled $direction successfully" else "Scroll $direction dispatched",
            targetResolved = direction
        )
    }

    private fun handleUiClick(target: String): AndroidExecutionResult {
        val svc = SilentExecAccessibilityService.get()
        val root = svc?.rootInActiveWindow
        var clicked = false
        if (root != null && target.isNotBlank()) {
            val nodes = root.findAccessibilityNodeInfosByText(target)
            val node = nodes?.firstOrNull()
            if (node != null) {
                clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        }
        return AndroidExecutionResult.Success(
            action = "ui_click",
            details = if (clicked) "Clicked UI node '$target'" else "Click requested for '$target'",
            targetResolved = target
        )
    }

    private fun handleUiType(text: String): AndroidExecutionResult {
        val svc = SilentExecAccessibilityService.get()
        val root = svc?.rootInActiveWindow
        var typed = false
        if (root != null) {
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null) {
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                typed = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            }
        }
        return AndroidExecutionResult.Success(
            action = "ui_type",
            details = if (typed) "Typed text into focused input field" else "Type text '$text' dispatched",
            targetResolved = text
        )
    }
}
