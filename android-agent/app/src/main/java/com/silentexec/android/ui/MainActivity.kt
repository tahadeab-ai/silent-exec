package com.silentexec.android.ui

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.silentexec.android.R
import com.silentexec.android.core.ActionCommand
import com.silentexec.android.core.ActionJson
import com.silentexec.android.core.ActionType
import com.silentexec.android.core.Platform
import com.silentexec.android.exec.AndroidActionExecutor
import com.silentexec.android.exec.AndroidExecutionResult
import com.silentexec.android.net.LanBridgeClient
import com.silentexec.android.net.ModelDownloader
import com.silentexec.android.nlp.OnDeviceLlamaEngine
import kotlin.concurrent.thread

/**
 * MainActivity for SilentExec Android v0.2.0.
 * Implements zero-chatter OS execution across dual modes:
 *  1. Offline (On-Device via local llama.cpp + GBNF grammar)
 *  2. LAN Bridge (PC execution via authenticated HTTP JSON-RPC)
 */
class MainActivity : AppCompatActivity() {

    private lateinit var rgExecutionMode: RadioGroup
    private lateinit var rbOffline: RadioButton
    private lateinit var rbBridge: RadioButton

    private lateinit var layoutModelDownload: View
    private lateinit var btnDownloadModel: Button
    private lateinit var pbDownload: ProgressBar
    private lateinit var tvDownloadStatus: TextView

    private lateinit var etCommand: EditText
    private lateinit var btnExecute: Button
    private lateinit var btnMic: Button
    private lateinit var btnBridgeSettings: Button
    private lateinit var tvStatus: TextView

    private lateinit var executor: AndroidActionExecutor
    private val bridgeClient = LanBridgeClient()
    private val llamaEngine = OnDeviceLlamaEngine()
    private lateinit var modelDownloader: ModelDownloader
    private var voiceInputHelper: VoiceInputHelper? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var grammarText: String = ""

    companion object {
        private const val REQUEST_RECORD_AUDIO = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        executor = AndroidActionExecutor(this)
        modelDownloader = ModelDownloader(this)

        bindViews()
        loadGrammarAsset()
        setupVoiceInput()
        setupModeSwitch()
        setupModelDownloader()
        setupExecution()

        updateModelUiState()
    }

    private fun bindViews() {
        rgExecutionMode = findViewById(R.id.rgExecutionMode)
        rbOffline = findViewById(R.id.rbOffline)
        rbBridge = findViewById(R.id.rbBridge)

        layoutModelDownload = findViewById(R.id.layoutModelDownload)
        btnDownloadModel = findViewById(R.id.btnDownloadModel)
        pbDownload = findViewById(R.id.pbDownload)
        tvDownloadStatus = findViewById(R.id.tvDownloadStatus)

        etCommand = findViewById(R.id.etCommand)
        btnExecute = findViewById(R.id.btnExecute)
        btnMic = findViewById(R.id.btnMic)
        btnBridgeSettings = findViewById(R.id.btnBridgeSettings)
        tvStatus = findViewById(R.id.tvStatus)
    }

    private fun loadGrammarAsset() {
        thread {
            runCatching {
                assets.open("action.gbnf").bufferedReader().use { it.readText() }
            }.onSuccess { grammar ->
                grammarText = grammar
            }
        }
    }

    private fun setupVoiceInput() {
        voiceInputHelper = VoiceInputHelper(
            context = this,
            onResult = { recognizedText ->
                etCommand.setText(recognizedText)
                handleCommand(recognizedText)
            },
            onError = { errMsg ->
                showFeedback("! صوت: $errMsg", isSuccess = false)
            }
        )

        btnMic.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    REQUEST_RECORD_AUDIO
                )
            } else {
                startListeningVoice()
            }
        }
    }

    private fun startListeningVoice() {
        showFeedback("🎤 جارٍ الاستماع...", isSuccess = true)
        voiceInputHelper?.startListening(preferArabic = true)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startListeningVoice()
        } else {
            showFeedback("! تم رفض إذن الميكروفون", isSuccess = false)
        }
    }

    private fun setupModeSwitch() {
        rgExecutionMode.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbOffline) {
                updateModelUiState()
            } else {
                layoutModelDownload.visibility = View.GONE
            }
        }
    }

    private fun updateModelUiState() {
        if (!rbOffline.isChecked) {
            layoutModelDownload.visibility = View.GONE
            return
        }

        val downloaded = modelDownloader.isModelDownloaded()
        if (downloaded) {
            layoutModelDownload.visibility = View.GONE
            if (!llamaEngine.isLoaded() && OnDeviceLlamaEngine.isNativeLoaded) {
                thread {
                    val file = modelDownloader.getModelFile()
                    llamaEngine.loadModel(file)
                }
            }
        } else {
            layoutModelDownload.visibility = View.VISIBLE
            btnDownloadModel.isEnabled = true
            pbDownload.visibility = View.GONE
            tvDownloadStatus.visibility = View.GONE
        }
    }

    private fun setupModelDownloader() {
        btnDownloadModel.setOnClickListener {
            btnDownloadModel.isEnabled = false
            pbDownload.visibility = View.VISIBLE
            pbDownload.progress = 0
            tvDownloadStatus.visibility = View.VISIBLE
            tvDownloadStatus.text = "جارٍ الاتصال بـ GitHub Releases..."

            thread {
                val result = modelDownloader.downloadModelBlocking { progress ->
                    mainHandler.post {
                        pbDownload.progress = progress
                        tvDownloadStatus.text = "جارٍ التحميل: $progress% (379 MB)"
                    }
                }

                mainHandler.post {
                    result.onSuccess { modelFile ->
                        tvDownloadStatus.text = "✓ تم التحميل والتحقق من SHA-256 بنجاح"
                        showFeedback("✓ تم تحميل النموذج بنجاح", isSuccess = true)
                        thread {
                            if (OnDeviceLlamaEngine.isNativeLoaded) {
                                llamaEngine.loadModel(modelFile)
                            }
                        }
                        layoutModelDownload.visibility = View.GONE
                    }.onFailure { err ->
                        tvDownloadStatus.text = "✗ خطأ: ${err.message}"
                        btnDownloadModel.isEnabled = true
                        showFeedback("✗ فشل التحميل: ${err.message}", isSuccess = false)
                    }
                }
            }
        }
    }

    private fun setupExecution() {
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

        // 2. Mode routing
        if (rbBridge.isChecked) {
            handleBridgeExecution(rawInput)
        } else {
            handleOfflineExecution(rawInput)
        }
    }

    private fun handleOfflineExecution(rawInput: String) {
        if (llamaEngine.isLoaded()) {
            tvStatus.visibility = View.VISIBLE
            tvStatus.text = "جارٍ التفكير محلياً..."
            thread {
                val inferred = llamaEngine.inferActionCommand(
                    userQuery = rawInput,
                    grammarText = grammarText,
                    maxTokens = 160
                )
                mainHandler.post {
                    tvStatus.visibility = View.GONE
                    inferred.onSuccess { action ->
                        executeCommand(action, confirmed = false)
                    }.onFailure {
                        // Fallback to local heuristic
                        val localCmd = parseLocalIntent(rawInput)
                        executeCommand(localCmd, confirmed = false)
                    }
                }
            }
        } else {
            // Local intent fallback parsing
            val localCmd = parseLocalIntent(rawInput)
            executeCommand(localCmd, confirmed = false)
        }
    }

    private fun handleBridgeExecution(rawInput: String) {
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
            showFeedback("! يرجى ضبط IP الخاص بالـ PC أولاً", isSuccess = false)
            showBridgeSettingsDialog()
        }
    }

    fun parseLocalIntent(text: String): ActionCommand {
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

    override fun onDestroy() {
        super.onDestroy()
        voiceInputHelper?.destroy()
        llamaEngine.unload()
    }
}
