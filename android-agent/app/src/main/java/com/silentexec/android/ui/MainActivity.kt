package com.silentexec.android.ui

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButtonToggleGroup
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
import com.silentexec.android.core.MemoryItem
import com.silentexec.android.core.MemoryStore
import com.silentexec.android.skills.SkillStore
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
    private lateinit var btnMemorySheet: Button
    private lateinit var etCommand: EditText
    private lateinit var btnExecute: Button
    private lateinit var btnMic: Button
    private lateinit var layoutHistoryChips: com.google.android.material.chip.ChipGroup
    private lateinit var btnClearStream: Button
    private lateinit var tvStreamEmpty: TextView
    private lateinit var rvAgentStream: RecyclerView
    private lateinit var agentStreamAdapter: AgentStreamAdapter

    // Tab 2 Views
    private lateinit var rvModels: RecyclerView
    private lateinit var btnRefreshModels: Button
    private lateinit var pbModelsLoading: ProgressBar
    private lateinit var tvModelsEmpty: TextView
    private lateinit var modelAdapter: ModelAdapter
    private val modelItems = mutableListOf<ModelReleaseItem>()

    // Tab 3 Views
    private lateinit var toggleLanguage: MaterialButtonToggleGroup
    private lateinit var btnLangAr: Button
    private lateinit var btnLangEn: Button
    private lateinit var toggleThemeMode: MaterialButtonToggleGroup
    private lateinit var btnThemeSystem: Button
    private lateinit var btnThemeDark: Button
    private lateinit var btnThemeLight: Button
    private lateinit var swFloatingBubble: MaterialSwitch
    private lateinit var toggleExecutionMode: MaterialButtonToggleGroup
    private lateinit var btnModeSilent: Button
    private lateinit var btnModeInteractive: Button
    private lateinit var btnModeDryRun: Button
    private lateinit var tvExecutionModeDesc: TextView
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
    private lateinit var tvPermAccessibilityBadge: TextView
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
        private const val KEY_ACTIVE_TAB_ID = "active_tab_id"
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
        MemoryStore.init(this)
        SkillStore.init(this)

        setContentView(R.layout.activity_main)
        applySystemBarAppearance()

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

        val activeTabId = savedInstanceState?.getInt(KEY_ACTIVE_TAB_ID) ?: R.id.nav_execute
        selectTab(activeTabId)

        updateHeaderStatus()
        loadActiveModelIfAvailable()
        handleIncomingIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::bottomNav.isInitialized) {
            outState.putInt(KEY_ACTIVE_TAB_ID, bottomNav.selectedItemId)
        }
    }

    private fun applySystemBarAppearance() {
        val isDarkTheme = when (settings.themeMode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> {
                val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                nightModeFlags == Configuration.UI_MODE_NIGHT_YES
            }
        }
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = !isDarkTheme
        controller.isAppearanceLightNavigationBars = !isDarkTheme
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
        btnMemorySheet = findViewById(R.id.btnMemorySheet)
        etCommand = findViewById(R.id.etCommand)
        btnExecute = findViewById(R.id.btnExecute)
        btnMic = findViewById(R.id.btnMic)
        layoutHistoryChips = findViewById(R.id.layoutHistoryChips)
        btnClearStream = findViewById(R.id.btnClearStream)
        tvStreamEmpty = findViewById(R.id.tvStreamEmpty)
        rvAgentStream = findViewById(R.id.rvAgentStream)

        agentStreamAdapter = AgentStreamAdapter()
        rvAgentStream.layoutManager = LinearLayoutManager(this)
        rvAgentStream.adapter = agentStreamAdapter

        // Tab 2
        rvModels = findViewById(R.id.rvModels)
        btnRefreshModels = findViewById(R.id.btnRefreshModels)
        pbModelsLoading = findViewById(R.id.pbModelsLoading)
        tvModelsEmpty = findViewById(R.id.tvModelsEmpty)

        // Tab 3
        toggleLanguage = findViewById(R.id.toggleLanguage)
        btnLangAr = findViewById(R.id.btnLangAr)
        btnLangEn = findViewById(R.id.btnLangEn)
        toggleThemeMode = findViewById(R.id.toggleThemeMode)
        btnThemeSystem = findViewById(R.id.btnThemeSystem)
        btnThemeDark = findViewById(R.id.btnThemeDark)
        btnThemeLight = findViewById(R.id.btnThemeLight)
        swFloatingBubble = findViewById(R.id.swFloatingBubble)
        toggleExecutionMode = findViewById(R.id.toggleExecutionMode)
        btnModeSilent = findViewById(R.id.btnModeSilent)
        btnModeInteractive = findViewById(R.id.btnModeInteractive)
        btnModeDryRun = findViewById(R.id.btnModeDryRun)
        tvExecutionModeDesc = findViewById(R.id.tvExecutionModeDesc)
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
        tvPermAccessibilityBadge = findViewById(R.id.tvPermAccessibilityBadge)
        btnOpenAccessibilitySettings = findViewById(R.id.btnOpenAccessibilitySettings)
        tvAppVersion = findViewById(R.id.tvAppVersion)
        btnCheckAppUpdates = findViewById(R.id.btnCheckAppUpdates)
        btnInstallUpdate = findViewById(R.id.btnInstallUpdate)
        pbAppUpdate = findViewById(R.id.pbAppUpdate)
        tvAppUpdateStatus = findViewById(R.id.tvAppUpdateStatus)
    }

    private fun setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener { item ->
            selectTabLayout(item.itemId)
            true
        }
    }

    private fun selectTab(tabId: Int) {
        if (bottomNav.selectedItemId != tabId) {
            bottomNav.selectedItemId = tabId
        }
        selectTabLayout(tabId)
    }

    private fun selectTabLayout(tabId: Int) {
        layoutTabExecute.visibility = if (tabId == R.id.nav_execute) View.VISIBLE else View.GONE
        layoutTabModels.visibility = if (tabId == R.id.nav_models) View.VISIBLE else View.GONE
        layoutTabSettings.visibility = if (tabId == R.id.nav_settings) View.VISIBLE else View.GONE
        if (tabId == R.id.nav_models && modelItems.isEmpty()) {
            fetchGitHubModels()
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
        tvHeaderModel.text = "${getString(R.string.header_model_label, modelStr)} ▾"

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
            llamaEngine.setModelFile(file)
            if (llamaEngine.validateModelFile(file) == null && OnDeviceLlamaEngine.isNativeLoaded && !llamaEngine.isLoaded()) {
                llamaEngine.loadModelAsync(file) { _ -> }
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

        btnMemorySheet.setOnClickListener {
            showMemorySheetDialog()
        }

        tvHeaderModel.setOnClickListener {
            showModelSwitcherBottomSheet()
        }

        btnClearStream.setOnClickListener {
            agentStreamAdapter.clear()
            tvStreamEmpty.visibility = View.VISIBLE
            rvAgentStream.visibility = View.GONE
            btnClearStream.visibility = View.GONE
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

    private var currentStepActionType: ActionType? = null

    private fun handleCommand(rawInput: String) {
        recordHistory(rawInput)
        currentStepActionType = null

        tvStreamEmpty.visibility = View.GONE
        rvAgentStream.visibility = View.VISIBLE
        btnClearStream.visibility = View.VISIBLE

        val timeStr = SimpleDateFormat("HH:mm", Locale.US).format(Date())
        agentStreamAdapter.addUserPrompt(rawInput)
        agentStreamAdapter.startAgentCard(
            goal = rawInput,
            timestamp = timeStr,
            initialStatus = getString(R.string.stream_thinking)
        )
        rvAgentStream.smoothScrollToPosition(agentStreamAdapter.itemCount - 1)

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
        val card = agentStreamAdapter.getCurrentAgentCard()

        when (update) {
            is AgentStepUpdate.Understanding -> {
                card?.statusText = getString(R.string.stage_understanding)
                addFeedItem(getString(R.string.stage_understanding), R.color.text_secondary)
            }
            is AgentStepUpdate.FastRuleMatched -> {
                currentStepActionType = update.command.action
                val label = "${update.command.action.wireName}${update.command.target?.let { ": $it" } ?: ""}"
                card?.stepPills?.add(
                    AgentStepPill(1, 1, label, PillState.RUNNING)
                )
                addFeedItem(getString(R.string.stage_routing_fast, update.command.action.wireName), R.color.accent_blue)
            }
            is AgentStepUpdate.PlanResolved -> {
                if (card != null) {
                    card.stepPills.clear()
                    val isDryRun = settings.executionMode == ExecutionMode.DRY_RUN
                    update.steps.forEachIndexed { i, cmd ->
                        val label = "${cmd.action.wireName}${cmd.target?.let { ": $it" } ?: ""}"
                        card.stepPills.add(
                            AgentStepPill(
                                stepIndex = i + 1,
                                totalSteps = update.totalSteps,
                                actionLabel = label,
                                state = if (isDryRun) PillState.DRY_RUN else PillState.PENDING
                            )
                        )
                    }
                }
                addFeedItem(getString(R.string.stage_planned, update.totalSteps), R.color.accent_blue)
                FloatingBubbleService.updateProgress("⚡ 1/${update.totalSteps} ${update.steps[0].action.wireName}") {
                    agentLoopRunner.cancel()
                }
            }
            is AgentStepUpdate.StepStarted -> {
                currentStepActionType = update.command.action
                val stepNum = update.stepIndex
                val total = update.totalSteps
                if (card != null) {
                    val existing = card.stepPills.getOrNull(stepNum - 1)
                    if (existing != null) {
                        existing.state = PillState.RUNNING
                    } else {
                        val label = "${update.command.action.wireName}${update.command.target?.let { ": $it" } ?: ""}"
                        card.stepPills.add(AgentStepPill(stepNum, total, label, PillState.RUNNING))
                    }
                    card.statusText = getString(R.string.stage_step_running, stepNum, total, update.command.action.wireName)
                }
                addFeedItem(getString(R.string.stage_step_running, stepNum, total, update.command.action.wireName), R.color.accent_blue)
                FloatingBubbleService.updateProgress("⚡ $stepNum/$total ${update.command.action.wireName}") {
                    agentLoopRunner.cancel()
                }
            }
            is AgentStepUpdate.AwaitingConfirmation -> {
                val stepNum = update.stepIndex
                card?.stepPills?.getOrNull(stepNum - 1)?.let {
                    it.state = PillState.PENDING
                    it.detail = update.reason
                }
            }
            is AgentStepUpdate.StepCompleted -> {
                val stepNum = update.stepIndex
                card?.stepPills?.getOrNull(stepNum - 1)?.let {
                    it.state = PillState.DONE
                    it.detail = update.details
                }
                if (currentStepActionType == ActionType.REPLY && update.details.isNotBlank()) {
                    card?.replyText = update.details
                }
                addFeedItem(getString(R.string.stage_step_done, stepNum, update.totalSteps, update.details), R.color.accent_green)
            }
            is AgentStepUpdate.StepSkipped -> {
                val stepNum = update.stepIndex
                card?.stepPills?.getOrNull(stepNum - 1)?.state = PillState.SKIPPED
                addFeedItem(getString(R.string.stage_step_skipped, stepNum, update.totalSteps), R.color.text_secondary)
            }
            is AgentStepUpdate.StepFailed -> {
                val stepNum = update.stepIndex
                if (card != null) {
                    val pill = card.stepPills.getOrNull(stepNum - 1)
                    if (pill != null) {
                        pill.state = PillState.FAILED
                        pill.detail = update.error
                    } else {
                        card.replyText = update.error
                    }
                    card.isRunning = false
                    card.isFailed = true
                    card.statusText = getString(R.string.stream_failed)
                }
                addFeedItem(getString(R.string.stage_step_failed, stepNum, update.totalSteps, update.error), R.color.danger_red)
                FloatingBubbleService.resetToBubble()
            }
            is AgentStepUpdate.DryRunValidated -> {
                val stepNum = update.stepIndex
                card?.stepPills?.getOrNull(stepNum - 1)?.let {
                    it.state = PillState.DRY_RUN
                    it.detail = update.note
                }
                addFeedItem(getString(R.string.stage_dry_run, "${update.command.action.wireName} -> ${update.note}"), R.color.warning_yellow)
            }
            is AgentStepUpdate.AllCompleted -> {
                if (card != null) {
                    card.isRunning = false
                    if (!card.isFailed) {
                        card.statusText = getString(R.string.stream_completed)
                    }
                }
                addFeedItem(getString(R.string.stage_all_done), R.color.accent_green)
                etCommand.text.clear()
                FloatingBubbleService.updateProgress("✓ All steps completed")
                mainHandler.postDelayed({ FloatingBubbleService.resetToBubble() }, 2000)
            }
            is AgentStepUpdate.Aborted -> {
                if (card != null) {
                    card.isRunning = false
                    card.isFailed = true
                    card.statusText = update.reason
                }
                addFeedItem("⏹ ${update.reason}", R.color.warning_yellow)
                FloatingBubbleService.resetToBubble()
            }
        }

        agentStreamAdapter.notifyCurrentAgentCardChanged()
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
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        overlayController.update(message)

        val card = agentStreamAdapter.getCurrentAgentCard()
        if (card != null) {
            card.technicalLogs.add("[$timeStr] $message")
            agentStreamAdapter.notifyCurrentAgentCardChanged()
        } else {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showMemorySheetDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_memory_sheet, null)
        val rvMemories = view.findViewById<RecyclerView>(R.id.rvMemories)
        val tvMemoryEmpty = view.findViewById<TextView>(R.id.tvMemoryEmpty)
        val etMemoryKey = view.findViewById<EditText>(R.id.etMemoryKey)
        val etMemoryContent = view.findViewById<EditText>(R.id.etMemoryContent)
        val btnSaveMemory = view.findViewById<Button>(R.id.btnSaveMemory)

        val memoryList = MemoryStore.listMemories().toMutableList()
        fun syncEmptyState() {
            tvMemoryEmpty.visibility = if (memoryList.isEmpty()) View.VISIBLE else View.GONE
            rvMemories.visibility = if (memoryList.isEmpty()) View.GONE else View.VISIBLE
        }
        syncEmptyState()

        val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount(): Int = memoryList.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item_memory, parent, false)
                return object : RecyclerView.ViewHolder(itemView) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val item = memoryList[position]
                holder.itemView.findViewById<TextView>(R.id.tvMemKey).text = item.key
                holder.itemView.findViewById<TextView>(R.id.tvMemContent).text = item.content
                holder.itemView.findViewById<Button>(R.id.btnDeleteMemory).setOnClickListener {
                    MemoryStore.deleteMemory(item.id)
                    memoryList.clear()
                    memoryList.addAll(MemoryStore.listMemories())
                    notifyDataSetChanged()
                    syncEmptyState()
                }
            }
        }
        rvMemories.layoutManager = LinearLayoutManager(this)
        rvMemories.adapter = adapter

        btnSaveMemory.setOnClickListener {
            val k = etMemoryKey.text.toString().trim()
            val c = etMemoryContent.text.toString().trim()
            if (k.isNotEmpty() && c.isNotEmpty()) {
                MemoryStore.saveMemory(k, c)
                etMemoryKey.text.clear()
                etMemoryContent.text.clear()
                memoryList.clear()
                memoryList.addAll(MemoryStore.listMemories())
                adapter.notifyDataSetChanged()
                syncEmptyState()
                Toast.makeText(this, R.string.memory_saved_toast, Toast.LENGTH_SHORT).show()
            }
        }

        AlertDialog.Builder(this)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun recordHistory(cmd: String) {
        if (!commandHistory.contains(cmd)) {
            commandHistory.add(0, cmd)
            if (commandHistory.size > 8) commandHistory.removeAt(commandHistory.lastIndex)
            populateHistoryChips()
        }
    }

    private fun showModelSwitcherBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 40, 48, 48)
        }

        val titleView = TextView(this).apply {
            text = getString(R.string.model_switcher_title)
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            setPadding(0, 0, 0, 24)
        }
        container.addView(titleView)

        val downloadedFiles = modelDownloader.listDownloadedModels().map { it.name }.toSet()
        val curatedFilenames = linkedSetOf(
            "LFM3-1.7B-Q4_K_M.gguf",
            "Qwen3-1.7B-Q4_K_M.gguf",
            "Qwen2.5-1.5B-Instruct.Q4_K_M.gguf",
            "Qwen2.5-0.5B-Instruct.Q4_K_M.gguf"
        )
        curatedFilenames.addAll(downloadedFiles)

        for (filename in curatedFilenames) {
            val isReady = downloadedFiles.contains(filename)
            val isActive = settings.inferenceSource == InferenceSource.OFFLINE &&
                settings.activeModelFile.equals(filename, ignoreCase = true)

            val shortName = filename.removeSuffix(".gguf")
            val statusStr = if (isReady) getString(R.string.model_status_ready) else getString(R.string.model_status_not_downloaded)
            val label = if (isActive) "✓ $shortName ($statusStr)" else "$shortName • $statusStr"

            val btn = com.google.android.material.button.MaterialButton(
                this,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = label
                isAllCaps = false
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 12) }
                setOnClickListener {
                    dialog.dismiss()
                    if (isReady) {
                        settings.inferenceSource = InferenceSource.OFFLINE
                        settings.activeModelFile = filename
                        updateHeaderStatus()
                        loadActiveModelIfAvailable()
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.backend_switched_toast, shortName),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        bottomNav.selectedItemId = R.id.nav_models
                        selectTab(R.id.nav_models)
                    }
                }
            }
            container.addView(btn)
        }

        val bridgeLabel = if (settings.inferenceSource == InferenceSource.LAN_BRIDGE) {
            "✓ ${getString(R.string.engine_bridge)}"
        } else {
            getString(R.string.engine_bridge)
        }
        val btnBridge = com.google.android.material.button.MaterialButton(
            this,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = bridgeLabel
            isAllCaps = false
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 0, 12) }
            setOnClickListener {
                dialog.dismiss()
                settings.inferenceSource = InferenceSource.LAN_BRIDGE
                updateHeaderStatus()
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.backend_switched_toast, "LAN Bridge"),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        container.addView(btnBridge)

        val cloudLabel = if (settings.inferenceSource == InferenceSource.OPENAI_COMPAT) {
            "✓ ${getString(R.string.engine_cloud)}"
        } else {
            getString(R.string.engine_cloud)
        }
        val btnCloud = com.google.android.material.button.MaterialButton(
            this,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = cloudLabel
            isAllCaps = false
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                dialog.dismiss()
                settings.inferenceSource = InferenceSource.OPENAI_COMPAT
                updateHeaderStatus()
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.backend_switched_toast, "Cloud API"),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        container.addView(btnCloud)

        dialog.setContentView(container)
        dialog.show()
    }

    private fun populateHistoryChips() {
        layoutHistoryChips.removeAllViews()

        data class SkillChipItem(val label: String, val prompt: String, val executeImmediately: Boolean)

        val skillChips = mutableListOf(
            SkillChipItem(getString(R.string.skill_chip_youtube), getString(R.string.skill_prompt_youtube), false),
            SkillChipItem(getString(R.string.skill_chip_web), getString(R.string.skill_prompt_web), false),
            SkillChipItem(getString(R.string.skill_chip_mute), getString(R.string.skill_prompt_mute), true),
            SkillChipItem(getString(R.string.skill_chip_summarize), getString(R.string.skill_prompt_summarize), false)
        )

        for (routine in SkillStore.listSkills()) {
            skillChips.add(SkillChipItem("⚡ ${routine.name}", routine.name, true))
        }

        for (historyCmd in commandHistory.take(4)) {
            if (skillChips.none { it.prompt.equals(historyCmd, ignoreCase = true) }) {
                skillChips.add(SkillChipItem(historyCmd, historyCmd, true))
            }
        }

        for (item in skillChips) {
            val chip = com.google.android.material.chip.Chip(this).apply {
                text = item.label
                isCheckable = false
                isClickable = true
                textSize = 12f
                setEnsureMinTouchTargetSize(false)
                setOnClickListener {
                    etCommand.setText(item.prompt)
                    etCommand.setSelection(item.prompt.length)
                    if (item.executeImmediately) {
                        handleCommand(item.prompt)
                    } else {
                        etCommand.requestFocus()
                    }
                }
            }
            layoutHistoryChips.addView(chip)
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
            val catalogModels = jsonResult.fold(
                onSuccess = { jsonStr ->
                    releaseClient.getAllCatalogModels(jsonStr, resolveHuggingFace = true)
                },
                onFailure = {
                    releaseClient.getAllCatalogModels(null, resolveHuggingFace = true)
                }
            )
            mainHandler.post {
                pbModelsLoading.visibility = View.GONE
                modelItems.clear()
                modelItems.addAll(catalogModels)
                if (modelItems.isEmpty()) {
                    tvModelsEmpty.visibility = View.VISIBLE
                } else {
                    tvModelsEmpty.visibility = View.GONE
                    modelAdapter.updateData(modelItems, settings.activeModelFile)
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
            toggleLanguage.check(R.id.btnLangEn)
        } else {
            toggleLanguage.check(R.id.btnLangAr)
        }

        toggleLanguage.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val newLang = if (checkedId == R.id.btnLangEn) "en" else "ar"
            if (newLang != settings.language) {
                settings.setLanguageAndApply(newLang)
                recreate()
            }
        }

        // 2. Theme Mode Toggle (System, Dark, Light)
        when (settings.themeMode) {
            ThemeMode.SYSTEM -> toggleThemeMode.check(R.id.btnThemeSystem)
            ThemeMode.DARK -> toggleThemeMode.check(R.id.btnThemeDark)
            ThemeMode.LIGHT -> toggleThemeMode.check(R.id.btnThemeLight)
        }

        toggleThemeMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val newTheme = when (checkedId) {
                R.id.btnThemeDark -> ThemeMode.DARK
                R.id.btnThemeLight -> ThemeMode.LIGHT
                else -> ThemeMode.SYSTEM
            }
            if (settings.themeMode != newTheme) {
                settings.themeMode = newTheme
                settings.applyTheme()
                applySystemBarAppearance()
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
        fun updateModeDesc(mode: ExecutionMode) {
            tvExecutionModeDesc.text = when (mode) {
                ExecutionMode.SILENT -> getString(R.string.mode_silent_desc)
                ExecutionMode.INTERACTIVE -> getString(R.string.mode_interactive_desc)
                ExecutionMode.DRY_RUN -> getString(R.string.mode_dry_run_desc)
            }
        }

        when (settings.executionMode) {
            ExecutionMode.SILENT -> toggleExecutionMode.check(R.id.btnModeSilent)
            ExecutionMode.INTERACTIVE -> toggleExecutionMode.check(R.id.btnModeInteractive)
            ExecutionMode.DRY_RUN -> toggleExecutionMode.check(R.id.btnModeDryRun)
        }
        updateModeDesc(settings.executionMode)

        toggleExecutionMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val newMode = when (checkedId) {
                R.id.btnModeSilent -> ExecutionMode.SILENT
                R.id.btnModeInteractive -> ExecutionMode.INTERACTIVE
                R.id.btnModeDryRun -> ExecutionMode.DRY_RUN
                else -> ExecutionMode.SILENT
            }
            settings.executionMode = newMode
            updateModeDesc(newMode)
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
            tvPermAccessibilityBadge.visibility = View.VISIBLE
            btnOpenAccessibilitySettings.visibility = View.GONE
        } else {
            tvPermAccessibilityBadge.visibility = View.GONE
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
