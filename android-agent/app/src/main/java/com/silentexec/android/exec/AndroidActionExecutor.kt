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
import com.silentexec.android.core.MemoryStore
import com.silentexec.android.core.SafetyDecision
import com.silentexec.android.core.SafetyGate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

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

class AndroidActionExecutor(private val context: Context? = null) {

    companion object {
        fun calculateVolumeIndex(pct: Int, maxVolume: Int = 15): Int {
            return Math.round((pct.coerceIn(0, 100) / 100.0) * maxVolume).toInt()
        }
    }

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
                    handleOpenApp(cmd.target ?: "", cmd.args)
                }
                ActionType.OPEN_URL -> {
                    handleOpenUrl(cmd.target ?: "")
                }
                ActionType.SET_VOLUME -> {
                    handleSetVolume(cmd.target, cmd.args)
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
                ActionType.REPLY -> {
                    val replyText = when {
                        cmd.args is JsonObject && (cmd.args as JsonObject)["text"] != null -> {
                            (cmd.args as JsonObject)["text"]?.jsonPrimitive?.content ?: ""
                        }
                        else -> cmd.target ?: ""
                    }
                    AndroidExecutionResult.Success(
                        action = "reply",
                        details = replyText,
                        targetResolved = replyText
                    )
                }
                ActionType.READ_FILE -> {
                    handleReadFile(cmd.target ?: "")
                }
                ActionType.WRITE_FILE -> {
                    val content = when {
                        cmd.args is JsonObject && (cmd.args as JsonObject)["content"] != null -> {
                            (cmd.args as JsonObject)["content"]?.jsonPrimitive?.content ?: ""
                        }
                        else -> ""
                    }
                    handleWriteFile(cmd.target ?: "", content)
                }
                ActionType.MEMORY_SAVE -> {
                    handleMemorySave(cmd.target, cmd.args)
                }
                ActionType.DONE -> {
                    AndroidExecutionResult.Success(
                        action = "done",
                        details = "ReAct loop completed; task done",
                        targetResolved = null
                    )
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

    private fun launchYouTubeSearch(query: String): AndroidExecutionResult {
        val ctx = context ?: return AndroidExecutionResult.Success(
            action = "launch_app",
            details = "Simulated YouTube search for '$query'",
            targetResolved = query
        )

        return try {
            val intent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            AndroidExecutionResult.Success(
                action = "launch_app",
                details = "Launched YouTube search for '$query'",
                targetResolved = query
            )
        } catch (_: Exception) {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(webIntent)
            AndroidExecutionResult.Success(
                action = "open_url",
                details = "Launched YouTube web search for '$query'",
                targetResolved = query
            )
        }
    }

    private fun handleOpenApp(target: String, args: JsonElement? = null): AndroidExecutionResult {
        if (target.isBlank()) {
            return AndroidExecutionResult.Error("open_android_app", "Target application name is empty")
        }

        val targetNorm = target.trim().lowercase()

        // Support native YouTube search intent if query present in args or target
        val searchParam = if (args is JsonObject) {
            args["query"]?.jsonPrimitive?.content ?: args["search"]?.jsonPrimitive?.content
        } else null

        if (!searchParam.isNullOrBlank() && (targetNorm.contains("youtube") || targetNorm.contains("يوتيوب"))) {
            return launchYouTubeSearch(searchParam)
        }

        if (targetNorm.startsWith("youtube:") || targetNorm.startsWith("youtube search:")) {
            val q = target.substringAfter(":").trim()
            if (q.isNotBlank()) return launchYouTubeSearch(q)
        }

        val ctx = context ?: return AndroidExecutionResult.Success(
            action = "open_android_app",
            details = "Simulated application launch: $target",
            targetResolved = target
        )

        val pm = ctx.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)

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
            ctx.startActivity(launchIntent)
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
        val uriString = com.silentexec.android.nlp.PromptFormatter.normalizeOpenUrlTarget(target, null)
            ?: if (!target.startsWith("http://") && !target.startsWith("https://")) {
                "https://$target"
            } else {
                target
            }

        val ctx = context ?: return AndroidExecutionResult.Success(
            action = "open_url",
            details = "Simulated browser URI: $uriString",
            targetResolved = uriString
        )

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)

        return AndroidExecutionResult.Success(
            action = "open_url",
            details = "Opened browser URI",
            targetResolved = uriString
        )
    }

    private fun handleSetVolume(target: String?, args: JsonElement? = null): AndroidExecutionResult {
        val pct: Int = when {
            target?.equals("mute", ignoreCase = true) == true || target == "0" -> 0
            args is JsonObject && args["level"] != null -> {
                args["level"]?.jsonPrimitive?.intOrNull ?: 0
            }
            else -> {
                target?.filter { it.isDigit() }?.toIntOrNull() ?: 50
            }
        }

        val ctx = context
        val maxVolume = if (ctx != null) {
            val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        } else {
            15 // Standard Android default stream max volume for tests and simulation
        }

        val targetIndex = calculateVolumeIndex(pct, maxVolume)

        if (ctx != null) {
            val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, AudioManager.FLAG_SHOW_UI)
        }

        return AndroidExecutionResult.Success(
            action = "set_volume",
            details = "Volume set to $targetIndex/$maxVolume (pct=$pct%)",
            targetResolved = targetIndex.toString()
        )
    }

    private fun handleReadFile(filePath: String): AndroidExecutionResult {
        if (filePath.isBlank()) {
            return AndroidExecutionResult.Error("read_file", "File path is empty")
        }
        val ctx = context ?: return AndroidExecutionResult.Success(
            action = "read_file",
            details = "Simulated read file: $filePath",
            targetResolved = filePath
        )
        val file = File(filePath)
        if (!file.exists()) {
            return AndroidExecutionResult.Error("read_file", "File not found: $filePath")
        }
        return try {
            val text = file.readText().take(32768)
            AndroidExecutionResult.Success(
                action = "read_file",
                details = text,
                targetResolved = filePath
            )
        } catch (e: Exception) {
            AndroidExecutionResult.Error("read_file", "Failed to read file: ${e.message}")
        }
    }

    private fun handleWriteFile(filePath: String, content: String): AndroidExecutionResult {
        if (filePath.isBlank()) {
            return AndroidExecutionResult.Error("write_file", "File path is empty")
        }
        val ctx = context ?: return AndroidExecutionResult.Success(
            action = "write_file",
            details = "Simulated write file to: $filePath (${content.length} chars)",
            targetResolved = filePath
        )
        return try {
            val file = File(filePath)
            file.parentFile?.mkdirs()
            file.writeText(content)
            AndroidExecutionResult.Success(
                action = "write_file",
                details = "Wrote ${content.length} characters to $filePath",
                targetResolved = filePath
            )
        } catch (e: Exception) {
            AndroidExecutionResult.Error("write_file", "Failed to write file: ${e.message}")
        }
    }

    private fun handleMemorySave(target: String?, args: JsonElement?): AndroidExecutionResult {
        val key = when {
            args is JsonObject && args["key"] != null -> {
                args["key"]?.jsonPrimitive?.content ?: "preference"
            }
            target != null && target.contains(":") -> {
                target.substringBefore(":").trim()
            }
            else -> target ?: "preference"
        }
        val content = when {
            args is JsonObject && args["content"] != null -> {
                args["content"]?.jsonPrimitive?.content ?: ""
            }
            args is JsonObject && args["value"] != null -> {
                args["value"]?.jsonPrimitive?.content ?: ""
            }
            target != null && target.contains(":") -> {
                target.substringAfter(":").trim()
            }
            else -> target ?: ""
        }
        val saved = MemoryStore.saveMemory(key, content)
        return AndroidExecutionResult.Success(
            action = "memory_save",
            details = "Saved memory '${saved.key}': ${saved.content}",
            targetResolved = saved.key
        )
    }

    private fun handleCopyToClipboard(target: String): AndroidExecutionResult {
        val ctx = context ?: return AndroidExecutionResult.Success(
            action = "copy_to_clipboard",
            details = "Simulated clipboard copy: $target",
            targetResolved = target
        )

        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("SilentExec", target)
        clipboard.setPrimaryClip(clip)

        return AndroidExecutionResult.Success(
            action = "copy_to_clipboard",
            details = "Copied text to clipboard",
            targetResolved = target
        )
    }

    private fun handleToggleSetting(target: String): AndroidExecutionResult {
        val ctx = context ?: return AndroidExecutionResult.Success(
            action = "toggle_setting",
            details = "Simulated setting toggle: $target",
            targetResolved = target
        )

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
        ctx.startActivity(intent)

        return AndroidExecutionResult.Success(
            action = "toggle_setting",
            details = "Opened system settings panel for $target",
            targetResolved = target
        )
    }

    private fun handleSendMessage(target: String): AndroidExecutionResult {
        val ctx = context ?: return AndroidExecutionResult.Success(
            action = "send_message",
            details = "Simulated send message: $target",
            targetResolved = target
        )

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$target")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)

        return AndroidExecutionResult.Success(
            action = "send_message",
            details = "Dispatched SMS composer",
            targetResolved = target
        )
    }

    private fun handleOpenFileOrFolder(target: String): AndroidExecutionResult {
        val ctx = context ?: return AndroidExecutionResult.Success(
            action = "open_file",
            details = "Simulated storage view: $target",
            targetResolved = target
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("content://"), "*/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            ctx.startActivity(intent)
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
        val ctx = context ?: return AndroidExecutionResult.Success(
            action = "create_note",
            details = "Simulated note creation: $target",
            targetResolved = target
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, target)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(Intent.createChooser(intent, "Save Note").apply {
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
        val handled = svc?.performUiBack() ?: false
        return AndroidExecutionResult.Success(
            action = "ui_back",
            details = if (handled) "Navigated back via accessibility service" else "Dispatched back action",
            targetResolved = null
        )
    }

    private fun handleUiHome(): AndroidExecutionResult {
        val svc = SilentExecAccessibilityService.get()
        val handled = svc?.performUiHome() ?: false
        if (!handled) {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context?.startActivity(homeIntent)
        }
        return AndroidExecutionResult.Success(
            action = "ui_home",
            details = "Navigated to home screen",
            targetResolved = null
        )
    }

    private fun handleUiScroll(direction: String): AndroidExecutionResult {
        val svc = SilentExecAccessibilityService.get()
        val scrolled = svc?.performUiScroll(direction) ?: false
        return AndroidExecutionResult.Success(
            action = "ui_scroll",
            details = if (scrolled) "Scrolled $direction successfully" else "Scroll $direction dispatched",
            targetResolved = direction
        )
    }

    private fun handleUiClick(target: String): AndroidExecutionResult {
        val svc = SilentExecAccessibilityService.get()
        val clicked = svc?.performUiClick(target) ?: false
        return AndroidExecutionResult.Success(
            action = "ui_click",
            details = if (clicked) "Clicked UI element '$target'" else "Click dispatched for '$target'",
            targetResolved = target
        )
    }

    private fun handleUiType(text: String): AndroidExecutionResult {
        val svc = SilentExecAccessibilityService.get()
        val typed = svc?.performUiType(text) ?: false
        return AndroidExecutionResult.Success(
            action = "ui_type",
            details = if (typed) "Typed text into input field" else "Type text '$text' dispatched",
            targetResolved = text
        )
    }
}
