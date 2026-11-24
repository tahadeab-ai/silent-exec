package com.silentexec.android.ui

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.materialswitch.MaterialSwitch
import com.silentexec.android.BuildConfig
import com.silentexec.android.R
import com.silentexec.android.core.ActionCommand
import com.silentexec.android.core.ActionJson
import com.silentexec.android.core.ActionType
import com.silentexec.android.core.AppSettings
import com.silentexec.android.core.ExecutionMode
import com.silentexec.android.core.FastRuleRouter
import com.silentexec.android.core.InferenceSource
import com.silentexec.android.core.MultiStepPlan
import com.silentexec.android.core.Platform
import com.silentexec.android.core.SafetyDecision
import com.silentexec.android.core.SafetyGate
import com.silentexec.android.core.SafetyTier
import com.silentexec.android.core.ThemeMode
import com.silentexec.android.exec.AgentLoopRunner
import com.silentexec.android.exec.AgentStepUpdate
import com.silentexec.android.exec.AndroidActionExecutor
import com.silentexec.android.exec.AndroidExecutionResult
import com.silentexec.android.exec.SilentExecAccessibilityService
import com.silentexec.android.exec.StepDecision
import com.silentexec.android.net.AppReleaseItem
import com.silentexec.android.net.AppUpdater
import com.silentexec.android.net.GitHubReleaseClient
import com.silentexec.android.net.LanBridgeClient
import com.silentexec.android.net.ModelDownloader
import com.silentexec.android.net.ModelReleaseItem
import com.silentexec.android.net.UpdateCheckResult
import com.silentexec.android.nlp.OnDeviceLlamaEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * SilentExec Android Agent v0.4.0
 * Modern Material 3 UI with 3 Navigation Tabs:
 *  1. Execute (Live Step-by-Step Timeline, Live Feed, Command input, History chips)
 *  2. Models (Dynamic Model Hub via GitHub Releases, Download/Activate/Delete)
 *  3. Settings (Dark/Light/System Theme, Floating Bubble overlay, Execution modes, LAN Bridge, In-App Self-Updater)
 */
class MainActivity : AppCompatActivity() {

    private lateinit var settings: AppSettings
    private lateinit var executor: AndroidActionExecutor
    private lateinit var modelDownloader: ModelDownloader
    private val bridgeClient = LanBridgeClient()
    private val llamaEngine = OnDeviceLlamaEngine()
    private val releaseClient = GitHubReleaseClient()
    private lateinit var appUpdater: AppUpdater
    private lateinit var overlayController: LiveOverlayController
    private lateinit var agentLoopRunner: AgentLoopRunner

    private var voiceInputHelper: VoiceInputHelper? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var grammarText: String = ""

    // Containers
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var layoutTabExecute: View
    private lateinit var layoutTabModels: View
    private lateinit var layoutTabSettings: View

    // Tab 1 Views
    private lateinit var tvHeaderMode: TextView
    private lateinit var tvHeaderModel: TextView
    private lateinit var tvHeaderAccessibility: TextView
    private lateinit var dotAccessibility: View
    private lateinit var etCommand: EditText
    private lateinit var btnExecute: Button
    private lateinit var btnMic: Button
    private lateinit var layoutHistoryChips: LinearLayout
    private lateinit var tvTimelineEmpty: TextView
    private lateinit var rvStepTimeline: RecyclerView
    private lateinit var stepTimelineAdapter: StepTimelineAdapter
    private lateinit var layoutExecutionFeed: LinearLayout
    private lateinit var tvLiveFeedEmpty: TextView

    // Tab 2 Views
    private lateinit var rvModels: RecyclerView
    private lateinit var btnRefreshModels: Button
    private lateinit var pbModelsLoading: ProgressBar
    private lateinit var tvModelsEmpty: TextView
    private lateinit var modelAdapter: ModelAdapter
    private val modelItems = mutableListOf<ModelReleaseItem>()

    // Tab 3 Views
    private lateinit var rgLanguage: RadioGroup
    private lateinit var rbLangAr: RadioButton
    private lateinit var rbLangEn: RadioButton
    private lateinit var rgThemeMode: RadioGroup
    private lateinit var rbThemeSystem: RadioButton
    private lateinit var rbThemeDark: RadioButton
    private lateinit var rbThemeLight: RadioButton
    private lateinit var swFloatingBubble: MaterialSwitch
    private lateinit var rgExecutionMode: RadioGroup
    private lateinit var rbModeSilent: RadioButton
    private lateinit var rbModeInteractive: RadioButton
    private lateinit var rbModeDryRun: RadioButton
    private lateinit var rgInferenceSource: RadioGroup
    private lateinit var rbEngineOffline: RadioButton
    private lateinit var rbEngineBridge: RadioButton
    private lateinit var rbEngineCloud: RadioButton
    private lateinit var cbFastRouter: CheckBox
    private lateinit var cardBridgeConfig: View
    private lateinit var etBridgeHost: EditText
    private lateinit var etBridgePort: EditText
    private lateinit var etBridgeToken: EditText
    private lateinit var btnTestBridge: Button
    private lateinit var btnSaveBridge: Button
    private lateinit var tvBridgeTestStatus: TextView
    private lateinit var cardCloudConfig: View
    private lateinit var etCloudApiBase: EditText
    private lateinit var etCloudApiModel: EditText
    private lateinit var etCloudApiKey: EditText
    private lateinit var btnTestCloud: Button
    private lateinit var btnSaveCloud: Button
    private lateinit var tvCloudTestStatus: TextView
    private lateinit var tvPermAccessibilityStatus: TextView
    private lateinit var btnOpenAccessibilitySettings: Button
    private lateinit var tvAppVersion: TextView
    private lateinit var btnCheckAppUpdates: Button
    private lateinit var btnInstallUpdate: Button
    private lateinit var pbAppUpdate: ProgressBar
    private lateinit var tvAppUpdateStatus: TextView
    private var pendingUpdateRelease: AppReleaseItem? = null

    private val commandHistory = mutableListOf<String>()
    private var isExecutionCancelled = false

    companion object {
        private const val REQUEST_RECORD_AUDIO = 101
        const val ACTION_EXECUTE_COMMAND = "com.silentexec.android.ACTION_EXECUTE_COMMAND"
        const val ACTION_TRIGGER_MIC = "com.silentexec.android.ACTION_TRIGGER_MIC"
        const val EXTRA_COMMAND = "extra_command"

        var activeInstance: MainActivity? = null
            private set
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activeInstance = this
        settings = AppSettings(this)
        settings.applyLocale()
        settings.applyTheme()

        setContentView(R.layout.activity_main)

        executor = AndroidActionExecutor(this)
        modelDownloader = ModelDownloader(this)
        appUpdater = AppUpdater(this, releaseClient)
        overlayController = LiveOverlayController(this)
        agentLoopRunner = AgentLoopRunner(
            executor = executor,
            settings = settings,
            llamaEngine = llamaEngine,
            bridgeClient = bridgeClient,
            grammarText = grammarText,
            stepDelayMs = 600L
        )

        bindViews()
        loadGrammarAsset()
        setupBottomNavigation()
        setupExecuteTab()
        setupModelsTab()
        setupSettingsTab()

        updateHeaderStatus()
        loadActiveModelIfAvailable()
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(SilentExecTileService.EXTRA_TRIGGER_VOICE, false) ||
            intent.action == ACTION_TRIGGER_MIC
        ) {
            bottomNav.selectedItemId = R.id.nav_execute
            startVoiceInput()
            return
        }

        if (intent.action == ACTION_EXECUTE_COMMAND) {
            val cmd = intent.getStringExtra(EXTRA_COMMAND).orEmpty()
            if (cmd.isNotBlank()) {
                handleCommandFromExternal(cmd)
            }
        }
    }

    fun handleCommandFromExternal(command: String) {
        mainHandler.post {
            bottomNav.selectedItemId = R.id.nav_execute
            etCommand.setText(command)
            handleCommand(command)
        }
    }

    override fun onResume() {
        super.onResume()
        activeInstance = this
        updateHeaderStatus()
        updateAccessibilityCard()

        if (settings.floatingBubbleEnabled && Settings.canDrawOverlays(this) && !FloatingBubbleService.isRunning()) {
            FloatingBubbleService.start(this)
        }
        if (::swFloatingBubble.isInitialized) {
            swFloatingBubble.isChecked = settings.floatingBubbleEnabled && Settings.canDrawOverlays(this)
        }
    }

    private fun bindViews() {
        bottomNav = findViewById(R.id.bottomNav)
        layoutTabExecute = findViewById(R.id.layoutTabExecute)
        layoutTabModels = findViewById(R.id.layoutTabModels)
        layoutTabSettings = findViewById(R.id.layoutTabSettings)

        // Tab 1
        tvHeaderMode = findViewById(R.id.tvHeaderMode)
        tvHeaderModel = findViewById(R.id.tvHeaderModel)
        tvHeaderAccessibility = findViewById(R.id.tvHeaderAccessibility)
        dotAccessibility = findViewById(R.id.dotAccessibility)
        etCommand = findViewById(R.id.etCommand)
        btnExecute = findViewById(R.id.btnExecute)
        btnMic = findViewById(R.id.btnMic)
        layoutHistoryChips = findViewById(R.id.layoutHistoryChips)
        tvTimelineEmpty = findViewById(R.id.tvTimelineEmpty)
        rvStepTimeline = findViewById(R.id.rvStepTimeline)
        layoutExecutionFeed = findViewById(R.id.layoutExecutionFeed)
        tvLiveFeedEmpty = findViewById(R.id.tvLiveFeedEmpty)

        stepTimelineAdapter = StepTimelineAdapter()
        rvStepTimeline.layoutManager = LinearLayoutManager(this)
        rvStepTimeline.adapter = stepTimelineAdapter

        // Tab 2
        rvModels = findViewById(R.id.rvModels)
        btnRefreshModels = findViewById(R.id.btnRefreshModels)
        pbModelsLoading = findViewById(R.id.pbModelsLoading)
        tvModelsEmpty = findViewById(R.id.tvModelsEmpty)

        // Tab 3
        rgLanguage = findViewById(R.id.rgLanguage)
        rbLangAr = findViewById(R.id.rbLangAr)
        rbLangEn = findViewById(R.id.rbLangEn)
        rgThemeMode = findViewById(R.id.rgThemeMode)
        rbThemeSystem = findViewById(R.id.rbThemeSystem)
        rbThemeDark = findViewById(R.id.rbThemeDark)
        rbThemeLight = findViewById(R.id.rbThemeLight)
        swFloatingBubble = findViewById(R.id.swFloatingBubble)
        rgExecutionMode = findViewById(R.id.rgExecutionMode)
        rbModeSilent = findViewById(R.id.rbModeSilent)
        rbModeInteractive = findViewById(R.id.rbModeInteractive)
        rbModeDryRun = findViewById(R.id.rbModeDryRun)
        rgInferenceSource = findViewById(R.id.rgInferenceSource)
        rbEngineOffline = findViewById(R.id.rbEngineOffline)
        rbEngineBridge = findViewById(R.id.rbEngineBridge)
        rbEngineCloud = findViewById(R.id.rbEngineCloud)
        cbFastRouter = findViewById(R.id.cbFastRouter)
        cardBridgeConfig = findViewById(R.id.cardBridgeConfig)
        etBridgeHost = findViewById(R.id.etBridgeHost)
        etBridgePort = findViewById(R.id.etBridgePort)
        etBridgeToken = findViewById(R.id.etBridgeToken)
        btnTestBridge = findViewById(R.id.btnTestBridge)
        btnSaveBridge = findViewById(R.id.btnSaveBridge)
        tvBridgeTestStatus = findViewById(R.id.tvBridgeTestStatus)
        cardCloudConfig = findViewById(R.id.cardCloudConfig)
        etCloudApiBase = findViewById(R.id.etCloudApiBase)
        etCloudApiModel = findViewById(R.id.etCloudApiModel)
        etCloudApiKey = findViewById(R.id.etCloudApiKey)
        btnTestCloud = findViewById(R.id.btnTestCloud)
        btnSaveCloud = findViewById(R.id.btnSaveCloud)
        tvCloudTestStatus = findViewById(R.id.tvCloudTestStatus)
        tvPermAccessibilityStatus = findViewById(R.id.tvPermAccessibilityStatus)
        btnOpenAccessibilitySettings = findViewById(R.id.btnOpenAccessibilitySettings)
        tvAppVersion = findViewById(R.id.tvAppVersion)
        btnCheckAppUpdates = findViewById(R.id.btnCheckAppUpdates)
        btnInstallUpdate = findViewById(R.id.btnInstallUpdate)
        pbAppUpdate = findViewById(R.id.pbAppUpdate)
        tvAppUpdateStatus = findViewById(R.id.tvAppUpdateStatus)
    }

    private fun setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_execute -> {
                    layoutTabExecute.visibility = View.VISIBLE
                    layoutTabModels.visibility = View.GONE
                    layoutTabSettings.visibility = View.GONE
                    true
                }
                R.id.nav_models -> {
                    layoutTabExecute.visibility = View.GONE
                    layoutTabModels.visibility = View.VISIBLE
                    layoutTabSettings.visibility = View.GONE
                    if (modelItems.isEmpty()) {
                        fetchGitHubModels()
                    }
                    true
                }
                R.id.nav_settings -> {
                    layoutTabExecute.visibility = View.GONE
                    layoutTabModels.visibility = View.GONE
                    layoutTabSettings.visibility = View.VISIBLE
                    true
                }
                else -> false
            }
        }
    }

    private fun loadGrammarAsset() {
        thread {
            grammarText = runCatching {
                assets.open("action.gbnf").bufferedReader().use { it.readText() }
            }.getOrDefault("")
        }
    }

    private fun updateHeaderStatus() {
        val modeStr = when (settings.executionMode) {
            ExecutionMode.SILENT -> getString(R.string.mode_silent).substringBefore(" ")
            ExecutionMode.INTERACTIVE -> getString(R.string.mode_interactive).substringBefore(" ")
            ExecutionMode.DRY_RUN -> getString(R.string.mode_dry_run).substringBefore(" ")
        }
        tvHeaderMode.text = getString(R.string.header_mode_label, modeStr)

        val modelStr = when (settings.inferenceSource) {
            InferenceSource.LAN_BRIDGE -> "LAN Bridge"
            InferenceSource.OPENAI_COMPAT -> settings.openAiApiModel.ifBlank { "Cloud API" }
            InferenceSource.OFFLINE -> {
                val file = settings.activeModelFile
                if (file.isNotBlank()) file.removeSuffix(".gguf") else "None"
            }
        }
        tvHeaderModel.text = getString(R.string.header_model_label, modelStr)

        val isAccessActive = SilentExecAccessibilityService.get() != null
        dotAccessibility.setBackgroundResource(
            if (isAccessActive) R.drawable.dot_active else R.drawable.dot_inactive
        )
        tvHeaderAccessibility.text = if (isAccessActive) {
            getString(R.string.status_accessibility_on)
        } else {
            getString(R.string.status_accessibility_off)
        }
    }

    private fun loadActiveModelIfAvailable() {
        if (settings.inferenceSource == InferenceSource.OFFLINE) {
            val file = modelDownloader.getModelFile(settings.activeModelFile)
            if (file.exists() && OnDeviceLlamaEngine.isNativeLoaded && !llamaEngine.isLoaded()) {
                thread {
                    llamaEngine.loadModel(file)
                }
            }
        }
    }

    // =========================================================================
    // TAB 1: EXECUTE
    // =========================================================================

    private fun setupExecuteTab() {
        voiceInputHelper = VoiceInputHelper(
            context = this,
            onResult = { recognizedText ->
                etCommand.setText(recognizedText)
                handleCommand(recognizedText)
            },
            onError = { errMsg ->
                addFeedItem(getString(R.string.mic_error, errMsg), R.color.danger_red)
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
                startVoiceInput()
            }
        }

        btnExecute.setOnClickListener {
            val text = etCommand.text.toString().trim()
            if (text.isNotEmpty()) {
                handleCommand(text)
            }
        }

        populateHistoryChips()
    }

    private fun startVoiceInput() {
        addFeedItem(getString(R.string.mic_listening), R.color.accent_blue)
        val preferArabic = settings.language == "ar"
        voiceInputHelper?.startListening(preferArabic = preferArabic)
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
            startVoiceInput()
        } else {
            addFeedItem(getString(R.string.mic_permission_denied), R.color.danger_red)
        }
    }

    private fun handleCommand(rawInput: String) {
        recordHistory(rawInput)

        overlayController.show(
            initialText = rawInput,
            onCancel = {
                agentLoopRunner.cancel()
                addFeedItem("⏹ Cancelled", R.color.warning_yellow)
            }
        )

        thread {
            agentLoopRunner.run(
                userGoal = rawInput,
                onUpdate = { update ->
                    mainHandler.post {
                        handleAgentStepUpdate(update)
                    }
                },
                onConfirmStep = { step, index, total, callback ->
                    mainHandler.post {
                        showInteractiveStepDialog(
                            cmd = step,
                            stepIndex = index,
                            totalSteps = total,
                            onRun = { callback(StepDecision.RUN) },
                            onSkip = { callback(StepDecision.SKIP) },
                            onStop = { callback(StepDecision.STOP) }
                        )
                    }
                }
            )
        }
    }

    private fun handleAgentStepUpdate(update: AgentStepUpdate) {
        when (update) {
            is AgentStepUpdate.Understanding -> {
                addFeedItem(getString(R.string.stage_understanding), R.color.text_secondary)
            }
            is AgentStepUpdate.FastRuleMatched -> {
                addFeedItem(getString(R.string.stage_routing_fast, update.command.action.wireName), R.color.accent_blue)
            }
            is AgentStepUpdate.PlanResolved -> {
                tvTimelineEmpty.visibility = View.GONE
                rvStepTimeline.visibility = View.VISIBLE
                val items = update.steps.mapIndexed { i, cmd ->
                    TimelineStep(
                        stepIndex = i + 1,
                        totalSteps = update.totalSteps,
                        actionName = cmd.action.wireName,
                        target = cmd.target,
                        tierName = cmd.action.tier.name.replace("TIER_", "Tier "),
                        status = if (settings.executionMode == ExecutionMode.DRY_RUN) StepStatus.DRY_RUN else StepStatus.PENDING,
                        isDryRun = settings.executionMode == ExecutionMode.DRY_RUN
                    )
                }
                stepTimelineAdapter.setItems(items)
                addFeedItem(getString(R.string.stage_planned, update.totalSteps), R.color.accent_blue)
                FloatingBubbleService.updateProgress("⚡ 1/${update.totalSteps} ${update.steps[0].action.wireName}") {
                    agentLoopRunner.cancel()
                }
            }
            is AgentStepUpdate.StepStarted -> {
                val stepNum = update.stepIndex
                val total = update.totalSteps
                stepTimelineAdapter.updateStep(stepNum - 1, StepStatus.RUNNING, getString(R.string.status_running))
                addFeedItem(getString(R.string.stage_step_running, stepNum, total, update.command.action.wireName), R.color.accent_blue)
                FloatingBubbleService.updateProgress("⚡ $stepNum/$total ${update.command.action.wireName}") {
                    agentLoopRunner.cancel()
                }
            }
            is AgentStepUpdate.AwaitingConfirmation -> {
                val stepNum = update.stepIndex
                stepTimelineAdapter.updateStep(stepNum - 1, StepStatus.AWAITING_CONFIRM, update.reason)
            }
            is AgentStepUpdate.StepCompleted -> {
                val stepNum = update.stepIndex
                stepTimelineAdapter.updateStep(stepNum - 1, StepStatus.COMPLETED, update.details)
                addFeedItem(getString(R.string.stage_step_done, stepNum, update.totalSteps, update.details), R.color.accent_green)
            }
            is AgentStepUpdate.StepSkipped -> {
                val stepNum = update.stepIndex
                stepTimelineAdapter.updateStep(stepNum - 1, StepStatus.SKIPPED, getString(R.string.stage_step_skipped, stepNum, update.totalSteps))
                addFeedItem(getString(R.string.stage_step_skipped, stepNum, update.totalSteps), R.color.text_secondary)
            }
            is AgentStepUpdate.StepFailed -> {
                val stepNum = update.stepIndex
                stepTimelineAdapter.updateStep(stepNum - 1, StepStatus.FAILED, update.error)
                addFeedItem(getString(R.string.stage_step_failed, stepNum, update.totalSteps, update.error), R.color.danger_red)
                FloatingBubbleService.resetToBubble()
            }
            is AgentStepUpdate.DryRunValidated -> {
                val stepNum = update.stepIndex
                stepTimelineAdapter.updateStep(stepNum - 1, StepStatus.DRY_RUN, update.note)
                addFeedItem(getString(R.string.stage_dry_run, "${update.command.action.wireName} -> ${update.note}"), R.color.warning_yellow)
            }
            is AgentStepUpdate.AllCompleted -> {
                addFeedItem(getString(R.string.stage_all_done), R.color.accent_green)
                etCommand.text.clear()
                FloatingBubbleService.updateProgress("✓ All steps completed")
                mainHandler.postDelayed({ FloatingBubbleService.resetToBubble() }, 2000)
            }
            is AgentStepUpdate.Aborted -> {
                addFeedItem("⏹ ${update.reason}", R.color.warning_yellow)
                FloatingBubbleService.resetToBubble()
            }
        }
    }

    private fun showInteractiveStepDialog(
        cmd: ActionCommand,
        stepIndex: Int,
        totalSteps: Int,
        onRun: () -> Unit,
        onSkip: () -> Unit,
        onStop: () -> Unit
    ) {
        val targetText = cmd.target.orEmpty().ifBlank { "-" }
        val tierText = cmd.action.tier.name.replace("TIER_", "Tier ")
        val msg = getString(R.string.confirm_step_msg, cmd.action.wireName, targetText, tierText)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.step_confirm_title, stepIndex, totalSteps))
            .setMessage(msg)
            .setPositiveButton(R.string.step_run) { _, _ -> onRun() }
            .setNeutralButton(R.string.step_skip) { _, _ -> onSkip() }
            .setNegativeButton(R.string.step_stop) { _, _ -> onStop() }
            .setCancelable(false)
            .show()
    }

    fun cancelAgentLoop() {
        agentLoopRunner.cancel()
    }

    private fun showConfirmationDialog(prompt: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_title)
            .setMessage(prompt)
            .setPositiveButton(R.string.confirm) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun addFeedItem(message: String, colorRes: Int) {
        tvLiveFeedEmpty.visibility = View.GONE
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

        overlayController.update(message)

        val tv = TextView(this).apply {
            text = "[$timeStr] $message"
            setTextColor(ContextCompat.getColor(context, colorRes))
            textSize = 13f
            setPadding(0, 4, 0, 4)
        }
        layoutExecutionFeed.addView(tv, 0)
    }

    private fun recordHistory(cmd: String) {
        if (!commandHistory.contains(cmd)) {
            commandHistory.add(0, cmd)
            if (commandHistory.size > 8) commandHistory.removeAt(commandHistory.lastIndex)
            populateHistoryChips()
        }
    }

    private fun populateHistoryChips() {
        layoutHistoryChips.removeAllViews()
        val defaultChips = if (commandHistory.isEmpty()) {
            if (settings.language == "ar") {
                listOf("ارجع", "الشاشة الرئيسية", "انزل", "ارفع الصوت 80", "افتح الواتساب")
            } else {
                listOf("back", "home", "scroll down", "set volume 80", "open whatsapp")
            }
        } else {
            commandHistory
        }

        for (text in defaultChips) {
            val btn = Button(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                this.text = text
                textSize = 11f
                isAllCaps = false
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 8, 0)
                }
                layoutParams = params
                setOnClickListener {
                    etCommand.setText(text)
                    handleCommand(text)
                }
            }
            layoutHistoryChips.addView(btn)
        }
    }

    private fun parseLocalIntent(text: String): ActionCommand {
        val lower = text.lowercase(Locale.ROOT).trim()

        val action = when {
            lower == "ارجع" || lower == "back" -> ActionType.UI_BACK
            lower == "الرئيسية" || lower == "الشاشة الرئيسية" || lower == "home" -> ActionType.UI_HOME
            lower.contains("انزل") || lower.contains("scroll down") -> ActionType.UI_SCROLL
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

    // =========================================================================
    // TAB 2: MODELS HUB
    // =========================================================================

    private fun setupModelsTab() {
        rvModels.layoutManager = LinearLayoutManager(this)
        modelAdapter = ModelAdapter(
            items = modelItems,
            activeModelFilename = settings.activeModelFile,
            modelDownloader = modelDownloader,
            onDownloadClick = { item, onStateUpdate, onDone ->
                thread {
                    val res = modelDownloader.downloadModelItemWithState(item, onStateUpdate)
                    mainHandler.post {
                        res.onSuccess {
                            Toast.makeText(this, R.string.model_download_success, Toast.LENGTH_SHORT).show()
                            onDone(Result.success(Unit))
                        }.onFailure { err ->
                            Toast.makeText(this, getString(R.string.model_download_failed, err.message ?: ""), Toast.LENGTH_LONG).show()
                            onDone(Result.failure(err))
                        }
                    }
                }
            },
            onPauseClick = { item ->
                modelDownloader.pause(item.filename)
            },
            onResumeClick = { item, onStateUpdate, onDone ->
                modelDownloader.resume(item.filename)
                thread {
                    val res = modelDownloader.downloadModelItemWithState(item, onStateUpdate)
                    mainHandler.post {
                        res.onSuccess {
                            Toast.makeText(this, R.string.model_download_success, Toast.LENGTH_SHORT).show()
                            onDone(Result.success(Unit))
                        }.onFailure { err ->
                            Toast.makeText(this, getString(R.string.model_download_failed, err.message ?: ""), Toast.LENGTH_LONG).show()
                            onDone(Result.failure(err))
                        }
                    }
                }
            },
            onCancelClick = { item ->
                modelDownloader.cancel(item.filename)
            },
            onActivateClick = { item ->
                settings.activeModelFile = item.filename
                updateHeaderStatus()
                modelAdapter.updateData(modelItems, settings.activeModelFile)
                Toast.makeText(this, getString(R.string.model_activated, item.filename), Toast.LENGTH_SHORT).show()
                loadActiveModelIfAvailable()
            },
            onDeleteClick = { item ->
                AlertDialog.Builder(this)
                    .setTitle(R.string.model_delete)
                    .setMessage(item.filename)
                    .setPositiveButton(R.string.confirm) { _, _ ->
                        modelDownloader.deleteModel(item.filename)
                        modelAdapter.updateData(modelItems, settings.activeModelFile)
                        Toast.makeText(this, R.string.model_deleted, Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        )
        rvModels.adapter = modelAdapter

        btnRefreshModels.setOnClickListener {
            fetchGitHubModels()
        }
    }

    private fun fetchGitHubModels() {
        pbModelsLoading.visibility = View.VISIBLE
        tvModelsEmpty.visibility = View.GONE

        thread {
            val jsonResult = releaseClient.fetchReleasesJson()
            mainHandler.post {
                pbModelsLoading.visibility = View.GONE
                jsonResult.onSuccess { jsonStr ->
                    val parsed = releaseClient.getAllCatalogModels(jsonStr)
                    modelItems.clear()
                    modelItems.addAll(parsed)
                    if (modelItems.isEmpty()) {
                        tvModelsEmpty.visibility = View.VISIBLE
                    } else {
                        tvModelsEmpty.visibility = View.GONE
                        modelAdapter.updateData(modelItems, settings.activeModelFile)
                    }
                }.onFailure { err ->
                    // Fallback to offline curated models on network failure
                    val curated = releaseClient.getAllCatalogModels(null)
                    if (curated.isNotEmpty()) {
                        modelItems.clear()
                        modelItems.addAll(curated)
                        tvModelsEmpty.visibility = View.GONE
                        modelAdapter.updateData(modelItems, settings.activeModelFile)
                    } else {
                        tvModelsEmpty.visibility = View.VISIBLE
                        tvModelsEmpty.text = getString(R.string.stage_error, err.message ?: "")
                    }
                }
            }
        }
    }

    // =========================================================================
    // TAB 3: SETTINGS
    // =========================================================================

    private fun setupSettingsTab() {
        // 1. Language Toggle
        if (settings.language == "en") {
            rbLangEn.isChecked = true
        } else {
            rbLangAr.isChecked = true
        }

        rgLanguage.setOnCheckedChangeListener { _, checkedId ->
            val newLang = if (checkedId == R.id.rbLangEn) "en" else "ar"
            if (newLang != settings.language) {
                settings.setLanguageAndApply(newLang)
                recreate()
            }
        }

        // 2. Theme Mode Toggle (System, Dark, Light)
        when (settings.themeMode) {
            ThemeMode.SYSTEM -> rbThemeSystem.isChecked = true
            ThemeMode.DARK -> rbThemeDark.isChecked = true
            ThemeMode.LIGHT -> rbThemeLight.isChecked = true
        }

        rgThemeMode.setOnCheckedChangeListener { _, checkedId ->
            val newTheme = when (checkedId) {
                R.id.rbThemeDark -> ThemeMode.DARK
                R.id.rbThemeLight -> ThemeMode.LIGHT
                else -> ThemeMode.SYSTEM
            }
            if (settings.themeMode != newTheme) {
                settings.themeMode = newTheme
                settings.applyTheme()
            }
        }

        // 3. Floating Assistant Bubble Toggle
        swFloatingBubble.isChecked = settings.floatingBubbleEnabled && Settings.canDrawOverlays(this)
        swFloatingBubble.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (Settings.canDrawOverlays(this)) {
                    settings.floatingBubbleEnabled = true
                    FloatingBubbleService.start(this)
                } else {
                    swFloatingBubble.isChecked = false
                    Toast.makeText(this, R.string.bubble_permission_required, Toast.LENGTH_SHORT).show()
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
            } else {
                settings.floatingBubbleEnabled = false
                FloatingBubbleService.stop(this)
            }
        }

        // 4. Execution Mode
        when (settings.executionMode) {
            ExecutionMode.SILENT -> rbModeSilent.isChecked = true
            ExecutionMode.INTERACTIVE -> rbModeInteractive.isChecked = true
            ExecutionMode.DRY_RUN -> rbModeDryRun.isChecked = true
        }

        rgExecutionMode.setOnCheckedChangeListener { _, checkedId ->
            val newMode = when (checkedId) {
                R.id.rbModeSilent -> ExecutionMode.SILENT
                R.id.rbModeInteractive -> ExecutionMode.INTERACTIVE
                R.id.rbModeDryRun -> ExecutionMode.DRY_RUN
                else -> ExecutionMode.SILENT
            }
            settings.executionMode = newMode
            updateHeaderStatus()
        }

        // 5. Inference Engine
        when (settings.inferenceSource) {
            InferenceSource.LAN_BRIDGE -> {
                rbEngineBridge.isChecked = true
                cardBridgeConfig.visibility = View.VISIBLE
                cardCloudConfig.visibility = View.GONE
            }
            InferenceSource.OPENAI_COMPAT -> {
                rbEngineCloud.isChecked = true
                cardBridgeConfig.visibility = View.GONE
                cardCloudConfig.visibility = View.VISIBLE
            }
            InferenceSource.OFFLINE -> {
                rbEngineOffline.isChecked = true
                cardBridgeConfig.visibility = View.GONE
                cardCloudConfig.visibility = View.GONE
            }
        }

        rgInferenceSource.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbEngineBridge -> {
                    settings.inferenceSource = InferenceSource.LAN_BRIDGE
                    cardBridgeConfig.visibility = View.VISIBLE
                    cardCloudConfig.visibility = View.GONE
                }
                R.id.rbEngineCloud -> {
                    settings.inferenceSource = InferenceSource.OPENAI_COMPAT
                    cardBridgeConfig.visibility = View.GONE
                    cardCloudConfig.visibility = View.VISIBLE
                }
                else -> {
                    settings.inferenceSource = InferenceSource.OFFLINE
                    cardBridgeConfig.visibility = View.GONE
                    cardCloudConfig.visibility = View.GONE
                    loadActiveModelIfAvailable()
                }
            }
            updateHeaderStatus()
        }

        cbFastRouter.isChecked = settings.fastRouterEnabled
        cbFastRouter.setOnCheckedChangeListener { _, isChecked ->
            settings.fastRouterEnabled = isChecked
        }

        // 6. LAN Bridge Config
        etBridgeHost.setText(settings.bridgeHost)
        etBridgePort.setText(settings.bridgePort.toString())
        etBridgeToken.setText(settings.bridgeToken)

        btnSaveBridge.setOnClickListener {
            settings.bridgeHost = etBridgeHost.text.toString().trim()
            settings.bridgePort = etBridgePort.text.toString().toIntOrNull() ?: 8765
            settings.bridgeToken = etBridgeToken.text.toString().trim()
            Toast.makeText(this, R.string.bridge_saved, Toast.LENGTH_SHORT).show()
        }

        btnTestBridge.setOnClickListener {
            val host = etBridgeHost.text.toString().trim()
            val port = etBridgePort.text.toString().toIntOrNull() ?: 8765
            tvBridgeTestStatus.visibility = View.VISIBLE
            tvBridgeTestStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_blue))
            tvBridgeTestStatus.text = getString(R.string.test_connection_testing)
            btnTestBridge.isEnabled = false

            thread {
                val res = bridgeClient.checkHealth(host, port)
                mainHandler.post {
                    btnTestBridge.isEnabled = true
                    res.onSuccess { isHealthy ->
                        if (isHealthy) {
                            tvBridgeTestStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
                            tvBridgeTestStatus.text = getString(R.string.test_connection_success)
                        } else {
                            tvBridgeTestStatus.setTextColor(ContextCompat.getColor(this, R.color.danger_red))
                            tvBridgeTestStatus.text = getString(R.string.test_connection_failed, "Health check failed")
                        }
                    }.onFailure { err ->
                        tvBridgeTestStatus.setTextColor(ContextCompat.getColor(this, R.color.danger_red))
                        tvBridgeTestStatus.text = getString(R.string.test_connection_failed, err.message ?: "")
                    }
                }
            }
        }

        // 6b. Cloud API Config
        etCloudApiBase.setText(settings.openAiApiBase)
        etCloudApiModel.setText(settings.openAiApiModel)
        etCloudApiKey.setText(settings.openAiApiKey)

        btnSaveCloud.setOnClickListener {
            settings.openAiApiBase = etCloudApiBase.text.toString().trim()
            settings.openAiApiModel = etCloudApiModel.text.toString().trim()
            settings.openAiApiKey = etCloudApiKey.text.toString().trim()
            updateHeaderStatus()
            Toast.makeText(this, R.string.cloud_saved, Toast.LENGTH_SHORT).show()
        }

        btnTestCloud.setOnClickListener {
            val base = etCloudApiBase.text.toString().trim()
            val model = etCloudApiModel.text.toString().trim()
            val key = etCloudApiKey.text.toString().trim()

            tvCloudTestStatus.visibility = View.VISIBLE
            tvCloudTestStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_blue))
            tvCloudTestStatus.text = getString(R.string.test_connection_testing)
            btnTestCloud.isEnabled = false

            thread {
                val client = com.silentexec.android.nlp.CloudApiClient(
                    baseUrl = base,
                    model = model,
                    apiKey = key
                )
                val res = client.generateCompletion(
                    systemPrompt = com.silentexec.android.nlp.PromptFormatter.DEFAULT_SYSTEM_PROMPT,
                    userPrompt = "ping"
                )
                mainHandler.post {
                    btnTestCloud.isEnabled = true
                    res.onSuccess {
                        tvCloudTestStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
                        tvCloudTestStatus.text = getString(R.string.test_connection_success)
                    }.onFailure { err ->
                        tvCloudTestStatus.setTextColor(ContextCompat.getColor(this, R.color.danger_red))
                        tvCloudTestStatus.text = getString(R.string.test_connection_failed, err.message ?: "")
                    }
                }
            }
        }

        // 7. Accessibility Permission Card
        updateAccessibilityCard()
        btnOpenAccessibilitySettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // 8. In-App Self-Updater
        tvAppVersion.text = getString(R.string.current_version_label, BuildConfig.VERSION_NAME)
        btnCheckAppUpdates.setOnClickListener {
            checkAppUpdate()
        }
        btnInstallUpdate.setOnClickListener {
            pendingUpdateRelease?.let { release ->
                startDownloadAndInstallUpdate(release)
            }
        }
    }

    private fun updateAccessibilityCard() {
        val isGranted = SilentExecAccessibilityService.get() != null
        if (isGranted) {
            tvPermAccessibilityStatus.text = getString(R.string.perm_granted)
            tvPermAccessibilityStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            btnOpenAccessibilitySettings.visibility = View.GONE
        } else {
            tvPermAccessibilityStatus.text = getString(R.string.perm_missing)
            tvPermAccessibilityStatus.setTextColor(ContextCompat.getColor(this, R.color.danger_red))
            btnOpenAccessibilitySettings.visibility = View.VISIBLE
        }
    }

    private fun checkAppUpdate() {
        btnCheckAppUpdates.isEnabled = false
        tvAppUpdateStatus.visibility = View.VISIBLE
        tvAppUpdateStatus.text = getString(R.string.checking_update)
        btnInstallUpdate.visibility = View.GONE

        thread {
            val checkRes = appUpdater.checkForUpdate()
            mainHandler.post {
                btnCheckAppUpdates.isEnabled = true
                checkRes.onSuccess { outcome ->
                    when (outcome) {
                        is UpdateCheckResult.UpdateAvailable -> {
                            pendingUpdateRelease = outcome.release
                            tvAppUpdateStatus.text = getString(R.string.update_available, outcome.release.versionName)
                            btnInstallUpdate.visibility = View.VISIBLE
                        }
                        is UpdateCheckResult.UpToDate -> {
                            tvAppUpdateStatus.text = getString(R.string.app_up_to_date, outcome.currentVersion)
                            btnInstallUpdate.visibility = View.GONE
                        }
                    }
                }.onFailure { err ->
                    tvAppUpdateStatus.text = getString(R.string.update_error, err.message ?: "")
                    btnInstallUpdate.visibility = View.GONE
                }
            }
        }
    }

    private fun startDownloadAndInstallUpdate(release: AppReleaseItem) {
        btnInstallUpdate.isEnabled = false
        btnCheckAppUpdates.isEnabled = false
        pbAppUpdate.visibility = View.VISIBLE
        pbAppUpdate.progress = 0

        thread {
            val res = appUpdater.downloadAndInstallUpdate(release) { percent ->
                mainHandler.post {
                    pbAppUpdate.progress = percent
                    tvAppUpdateStatus.text = getString(R.string.update_downloading, percent)
                }
            }

            mainHandler.post {
                btnInstallUpdate.isEnabled = true
                btnCheckAppUpdates.isEnabled = true
                pbAppUpdate.visibility = View.GONE
                res.onSuccess {
                    tvAppUpdateStatus.text = getString(R.string.update_ready_to_install)
                }.onFailure { err ->
                    tvAppUpdateStatus.text = getString(R.string.update_error, err.message ?: "")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activeInstance == this) {
            activeInstance = null
        }
        overlayController.hide()
        voiceInputHelper?.destroy()
        llamaEngine.unload()
    }
}
