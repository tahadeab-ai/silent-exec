package com.silentexec.android.ui

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.silentexec.android.R
import com.silentexec.android.core.ActionCommand
import com.silentexec.android.core.ActionJson
import com.silentexec.android.core.ActionType
import com.silentexec.android.core.Platform
import com.silentexec.android.exec.AndroidActionExecutor
import com.silentexec.android.exec.AndroidExecutionResult
import com.silentexec.android.net.LanBridgeClient
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var etCommand: EditText
    private lateinit var btnExecute: Button
    private lateinit var btnBridgeSettings: Button
    private lateinit var tvStatus: TextView

    private lateinit var executor: AndroidActionExecutor
    private val bridgeClient = LanBridgeClient()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        executor = AndroidActionExecutor(this)

        etCommand = findViewById(R.id.etCommand)
        btnExecute = findViewById(R.id.btnExecute)
        btnBridgeSettings = findViewById(R.id.btnBridgeSettings)
        tvStatus = findViewById(R.id.tvStatus)

        btnExecute.setOnClickListener {
            val input = etCommand.text.toString().trim()
            if (input.isNotEmpty()) {
                handleCommand(input)
            }
        }

        btnBridgeSettings.setOnClickListener {
            showBridgeSettingsDialog()
        }
    }

    private fun handleCommand(rawInput: String) {
        // 1. Check if raw JSON was entered
        val jsonCmd = runCatching { ActionJson.fromJson(rawInput) }.getOrNull()

        if (jsonCmd != null) {
            executeCommand(jsonCmd, confirmed = false)
            return
        }

        // 2. Natural language prompt handling
        val prefs = getSharedPreferences("bridge_prefs", Context.MODE_PRIVATE)
        val host = prefs.getString("bridge_host", null)
        val port = prefs.getInt("bridge_port", 8765)
        val token = prefs.getString("bridge_token", "silent-exec-secret") ?: "silent-exec-secret"

        if (!host.isNullOrBlank()) {
            tvStatus.visibility = View.VISIBLE
            tvStatus.text = "جاري الإرسال إلى PC..."
            thread {
                val result = bridgeClient.executeRemote(
                    host = host,
                    port = port,
                    token = token,
                    prompt = rawInput,
                    confirm = false,
                    dryRun = false
                )
                mainHandler.post {
                    tvStatus.visibility = View.GONE
                    result.onSuccess { resp ->
                        showFeedback("✓ تم الإرسال: $resp", isSuccess = true)
                        etCommand.text.clear()
                    }.onFailure { err ->
                        showFeedback("✗ فشل الاتصال: ${err.message}", isSuccess = false)
                    }
                }
            }
        } else {
            // Local intent fallback parsing
            val localCmd = parseLocalIntent(rawInput)
            executeCommand(localCmd, confirmed = false)
        }
    }

    private fun parseLocalIntent(text: String): ActionCommand {
        val lower = text.lowercase().trim()
        val action = when {
            lower.startsWith("افتح ") || lower.startsWith("شغل ") || lower.startsWith("open ") -> {
                if (lower.contains("http") || lower.contains(".com") || lower.contains(".org")) {
                    ActionType.OPEN_URL
                } else {
                    ActionType.OPEN_ANDROID_APP
                }
            }
            lower.contains("صوت") || lower.contains("volume") -> ActionType.SET_VOLUME
            lower.contains("انسخ") || lower.contains("copy") -> ActionType.COPY_TO_CLIPBOARD
            lower.contains("اعدادات") || lower.contains("إعدادات") || lower.contains("settings") -> ActionType.TOGGLE_SETTING
            lower.contains("رسالة") || lower.contains("sms") -> ActionType.SEND_MESSAGE
            lower.contains("ملاحظة") || lower.contains("note") -> ActionType.CREATE_NOTE
            else -> ActionType.OPEN_ANDROID_APP
        }

        val target = text.replace(Regex("^(افتح|شغل|open|انسخ|copy|اكتب|write)\\s+", RegexOption.IGNORE_CASE), "").trim()

        return ActionCommand(
            action = action,
            target = target.ifBlank { text },
            platform = Platform.ANDROID,
            confidence = 0.90
        )
    }

    private fun executeCommand(cmd: ActionCommand, confirmed: Boolean) {
        when (val res = executor.execute(cmd, confirmed)) {
            is AndroidExecutionResult.Success -> {
                showFeedback("✓ ${res.action}", isSuccess = true)
                etCommand.text.clear()
            }
            is AndroidExecutionResult.ConfirmationRequired -> {
                showConfirmationDialog(res.prompt) {
                    executeCommand(cmd, confirmed = true)
                }
            }
            is AndroidExecutionResult.Blocked -> {
                showFeedback("✗ محظور: ${res.reason}", isSuccess = false)
            }
            is AndroidExecutionResult.Error -> {
                showFeedback("! خطأ: ${res.message}", isSuccess = false)
            }
        }
    }

    private fun showConfirmationDialog(prompt: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_title)
            .setMessage(prompt)
            .setPositiveButton(R.string.confirm) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showFeedback(message: String, isSuccess: Boolean) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        tvStatus.text = message
        tvStatus.visibility = View.VISIBLE
        tvStatus.setTextColor(getColor(if (isSuccess) R.color.accent_green else R.color.danger_red))
        mainHandler.postDelayed({ tvStatus.visibility = View.GONE }, 2000)
    }

    private fun showBridgeSettingsDialog() {
        val prefs = getSharedPreferences("bridge_prefs", Context.MODE_PRIVATE)
        val currentHost = prefs.getString("bridge_host", "") ?: ""
        val currentPort = prefs.getInt("bridge_port", 8765)
        val currentToken = prefs.getString("bridge_token", "silent-exec-secret") ?: "silent-exec-secret"

        val view = LayoutInflater.from(this).inflate(R.layout.activity_main, null)
        val etHost = EditText(this).apply {
            hint = getString(R.string.host_hint)
            setText(currentHost)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.bridge_settings)
            .setView(etHost)
            .setPositiveButton(R.string.save) { _, _ ->
                prefs.edit()
                    .putString("bridge_host", etHost.text.toString().trim())
                    .putInt("bridge_port", currentPort)
                    .putString("bridge_token", currentToken)
                    .apply()
                showFeedback("✓ تم حفظ إعدادات LAN Bridge", isSuccess = true)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
