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
import com.silentexec.android.exec.AndroidActionExecutor
import com.silentexec.android.exec.AndroidExecutionResult
import com.silentexec.android.exec.SilentExecAccessibilityService
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
    private lateinit var cbFastRouter: CheckBox
    private lateinit var cardBridgeConfig: View
    private lateinit var etBridgeHost: EditText
    private lateinit var etBridgePort: EditText
    private lateinit var etBridgeToken: EditText
    private lateinit var btnSaveBridge: Button
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
        cbFastRouter = findViewById(R.id.cbFastRouter)
        cardBridgeConfig = findViewById(R.id.cardBridgeConfig)
        etBridgeHost = findViewById(R.id.etBridgeHost)
        etBridgePort = findViewById(R.id.etBridgePort)
        etBridgeToken = findViewById(R.id.etBridgeToken)
        btnSaveBridge = findViewById(R.id.btnSaveBridge)
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

        val modelStr = if (settings.inferenceSource == InferenceSource.LAN_BRIDGE) {
            "LAN Bridge"
        } else {
            val file = settings.activeModelFile
            if (file.isNotBlank()) file.removeSuffix(".gguf") else "None"
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
        isExecutionCancelled = false

        overlayController.show(
            initialText = rawInput,
            onCancel = {
                isExecutionCancelled = true
                addFeedItem("⏹ Cancelled", R.color.warning_yellow)
            }
        )

        // 1. Raw JSON MultiStepPlan?
        val plan = runCatching { ActionJson.planFromJson(rawInput) }.getOrNull()
        if (plan != null) {
            dispatchPlan(plan.steps)
            return
        }

        // 2. Raw JSON single ActionCommand?
        val jsonCmd = runCatching { ActionJson.fromJson(rawInput) }.getOrNull()
        if (jsonCmd != null) {
            dispatchPlan(listOf(jsonCmd))
            return
        }

        // 3. Fast Rule Router (<15ms)
        if (settings.fastRouterEnabled) {
            val fastAction = FastRuleRouter.route(rawInput)
            if (fastAction != null) {
                addFeedItem(getString(R.string.stage_routing_fast, fastAction.action.wireName), R.color.accent_blue)
                dispatchPlan(listOf(fastAction))
                return
            }
        }

        // 4. Routing by InferenceSource
        if (settings.inferenceSource == InferenceSource.LAN_BRIDGE) {
            handleBridgeExecution(rawInput)
        } else {
            handleOfflineExecution(rawInput)
        }
    }

    private fun handleOfflineExecution(rawInput: String) {
        addFeedItem(getString(R.string.stage_understanding), R.color.text_secondary)

        if (llamaEngine.isLoaded()) {
            thread {
                val inferred = llamaEngine.inferActionCommand(
                    userQuery = rawInput,
                    grammarText = grammarText,
                    maxTokens = 160
                )
                mainHandler.post {
                    inferred.onSuccess { cmd ->
                        dispatchPlan(listOf(cmd))
                    }.onFailure {
                        val fallback = parseLocalIntent(rawInput)
                        dispatchPlan(listOf(fallback))
                    }
                }
            }
        } else {
            val fallback = parseLocalIntent(rawInput)
            dispatchPlan(listOf(fallback))
        }
    }

    private fun handleBridgeExecution(rawInput: String) {
        val host = settings.bridgeHost
        val port = settings.bridgePort
        val token = settings.bridgeToken

        if (host.isBlank()) {
            addFeedItem(getString(R.string.stage_error, "PC IP not configured in Settings"), R.color.danger_red)
            bottomNav.selectedItemId = R.id.nav_settings
            return
        }

        addFeedItem(getString(R.string.stage_sending_bridge), R.color.accent_blue)

        thread {
            val result = bridgeClient.executeRemote(
                host = host,
                port = port,
                token = token,
                prompt = rawInput,
                confirm = settings.executionMode == ExecutionMode.SILENT,
                dryRun = settings.executionMode == ExecutionMode.DRY_RUN
            )

            mainHandler.post {
                result.onSuccess { resp ->
                    // Attempt to parse response as MultiStepPlan or ActionCommand JSON
                    val remotePlan = runCatching { ActionJson.planFromJson(resp) }.getOrNull()
                    if (remotePlan != null) {
                        dispatchPlan(remotePlan.steps)
                    } else {
                        val remoteCmd = runCatching { ActionJson.fromJson(resp) }.getOrNull()
                        if (remoteCmd != null) {
                            dispatchPlan(listOf(remoteCmd))
                        } else {
                            addFeedItem(getString(R.string.stage_completed, resp), R.color.accent_green)
                            etCommand.text.clear()
                        }
                    }
                }.onFailure { err ->
                    addFeedItem(getString(R.string.stage_error, err.message ?: ""), R.color.danger_red)
                }
            }
        }
    }

    /**
     * Executes a list of action steps sequentially, honoring:
     * - SILENT: auto-executes Tier 1 steps, pauses only for Tier 2 confirmation.
     * - INTERACTIVE: displays step confirmation preview before EVERY step with [Run | Skip | Stop].
     * - DRY_RUN: verifies entire plan with SafetyGate and displays [DRY-RUN] badges without modifying anything.
     */
    private fun dispatchPlan(steps: List<ActionCommand>) {
        if (steps.isEmpty()) return

        val isDryRun = settings.executionMode == ExecutionMode.DRY_RUN
        val isInteractive = settings.executionMode == ExecutionMode.INTERACTIVE

        val timelineItems = steps.mapIndexed { i, cmd ->
            TimelineStep(
                stepIndex = i + 1,
                totalSteps = steps.size,
                actionName = cmd.action.wireName,
                target = cmd.target,
                tierName = cmd.action.tier.name.replace("TIER_", "Tier "),
                status = if (isDryRun) StepStatus.DRY_RUN else StepStatus.PENDING,
                isDryRun = isDryRun
            )
        }

        tvTimelineEmpty.visibility = View.GONE
        rvStepTimeline.visibility = View.VISIBLE
        stepTimelineAdapter.setItems(timelineItems)

        addFeedItem(getString(R.string.stage_planned, steps.size), R.color.accent_blue)

        // DRY-RUN MODE: immediate validation without execution
        if (isDryRun) {
            steps.forEachIndexed { i, cmd ->
                when (val decision = SafetyGate.evaluate(cmd)) {
                    is SafetyDecision.Block -> {
                        stepTimelineAdapter.updateStep(i, StepStatus.FAILED, "Blocked: ${decision.reason}")
                        addFeedItem(getString(R.string.stage_blocked, decision.reason), R.color.danger_red)
                    }
                    is SafetyDecision.RequireConfirm -> {
                        stepTimelineAdapter.updateStep(i, StepStatus.DRY_RUN, "Valid (Tier 2 Confirm required)")
                        addFeedItem(getString(R.string.stage_dry_run, "${cmd.action.wireName} -> Requires Confirmation"), R.color.warning_yellow)
                    }
                    is SafetyDecision.AllowSilent -> {
                        stepTimelineAdapter.updateStep(i, StepStatus.DRY_RUN, "Valid (Tier 1 Silent)")
                        addFeedItem(getString(R.string.stage_dry_run, "${cmd.action.wireName} -> target=${cmd.target}"), R.color.accent_green)
                    }
                }
            }
            etCommand.text.clear()
            FloatingBubbleService.updateProgress("✓ Dry Run (${steps.size} steps valid)")
            mainHandler.postDelayed({ FloatingBubbleService.resetToBubble() }, 2000)
            return
        }

        // REAL EXECUTION (SILENT or INTERACTIVE)
        executePlanStep(steps, 0, isInteractive)
    }

    private fun executePlanStep(steps: List<ActionCommand>, index: Int, isInteractive: Boolean) {
        if (isExecutionCancelled) {
            addFeedItem("⏹ Execution aborted", R.color.warning_yellow)
            FloatingBubbleService.resetToBubble()
            return
        }

        if (index >= steps.size) {
            addFeedItem(getString(R.string.stage_all_done), R.color.accent_green)
            etCommand.text.clear()
            FloatingBubbleService.updateProgress("✓ All steps completed")
            mainHandler.postDelayed({ FloatingBubbleService.resetToBubble() }, 2500)
            return
        }

        val cmd = steps[index]
        val stepNum = index + 1
        val total = steps.size

        // Handle ReAct DONE action
        if (cmd.action == ActionType.DONE) {
            stepTimelineAdapter.updateStep(index, StepStatus.COMPLETED, "Done")
            executePlanStep(steps, index + 1, isInteractive)
            return
        }

        val stepProgressText = "⚡ $stepNum/$total ${cmd.action.wireName}"
        FloatingBubbleService.updateProgress(stepProgressText) {
            isExecutionCancelled = true
        }

        if (isInteractive) {
            // Interactive Mode: preview card / confirmation dialog before EVERY step
            stepTimelineAdapter.updateStep(index, StepStatus.AWAITING_CONFIRM, "Awaiting approval")
            showInteractiveStepDialog(cmd, stepNum, total,
                onRun = {
                    executeStepDirectly(cmd, steps, index, isInteractive)
                },
                onSkip = {
                    stepTimelineAdapter.updateStep(index, StepStatus.SKIPPED, getString(R.string.stage_step_skipped, stepNum, total))
                    addFeedItem(getString(R.string.stage_step_skipped, stepNum, total), R.color.text_secondary)
                    executePlanStep(steps, index + 1, isInteractive)
                },
                onStop = {
                    isExecutionCancelled = true
                    addFeedItem("⏹ Stopped at step $stepNum/$total", R.color.danger_red)
                    FloatingBubbleService.resetToBubble()
                }
            )
        } else {
            // Silent Mode: execute Tier 1 directly, pause only for Tier 2 confirmation
            val isTier2 = cmd.action.tier == SafetyTier.TIER_2_CONFIRM
            if (isTier2) {
                stepTimelineAdapter.updateStep(index, StepStatus.AWAITING_CONFIRM, "Requires Confirmation (Tier 2)")
                showConfirmationDialog("Confirm Tier-2 Action: ${cmd.action.wireName} on ${cmd.target}") {
                    executeStepDirectly(cmd, steps, index, isInteractive)
                }
            } else {
                executeStepDirectly(cmd, steps, index, isInteractive)
            }
        }
    }

    private fun executeStepDirectly(
        cmd: ActionCommand,
        steps: List<ActionCommand>,
        index: Int,
        isInteractive: Boolean
    ) {
        val stepNum = index + 1
        val total = steps.size

        stepTimelineAdapter.updateStep(index, StepStatus.RUNNING, getString(R.string.status_running))
        addFeedItem(getString(R.string.stage_step_running, stepNum, total, cmd.action.wireName), R.color.accent_blue)

        when (val res = executor.execute(cmd, confirmed = true)) {
            is AndroidExecutionResult.Success -> {
                stepTimelineAdapter.updateStep(index, StepStatus.COMPLETED, res.details)
                addFeedItem(getString(R.string.stage_step_done, stepNum, total, res.details), R.color.accent_green)
                mainHandler.postDelayed({
                    executePlanStep(steps, index + 1, isInteractive)
                }, 200)
            }
            is AndroidExecutionResult.ConfirmationRequired -> {
                showConfirmationDialog(res.prompt) {
                    executeStepDirectly(cmd, steps, index, isInteractive)
                }
            }
            is AndroidExecutionResult.Blocked -> {
                stepTimelineAdapter.updateStep(index, StepStatus.FAILED, "Blocked: ${res.reason}")
                addFeedItem(getString(R.string.stage_blocked, res.reason), R.color.danger_red)
                FloatingBubbleService.resetToBubble()
            }
            is AndroidExecutionResult.Error -> {
                stepTimelineAdapter.updateStep(index, StepStatus.FAILED, res.message)
                addFeedItem(getString(R.string.stage_step_failed, stepNum, total, res.message), R.color.danger_red)
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
            onDownloadClick = { item, onProgress, onDone ->
                thread {
                    val res = modelDownloader.downloadModelItem(item, onProgress)
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
                    val parsed = releaseClient.parseModelReleases(jsonStr)
                    modelItems.clear()
                    modelItems.addAll(parsed)
                    if (modelItems.isEmpty()) {
                        tvModelsEmpty.visibility = View.VISIBLE
                    } else {
                        tvModelsEmpty.visibility = View.GONE
                        modelAdapter.updateData(modelItems, settings.activeModelFile)
                    }
                }.onFailure { err ->
                    tvModelsEmpty.visibility = View.VISIBLE
                    tvModelsEmpty.text = getString(R.string.stage_error, err.message ?: "")
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
        if (settings.inferenceSource == InferenceSource.LAN_BRIDGE) {
            rbEngineBridge.isChecked = true
            cardBridgeConfig.visibility = View.VISIBLE
        } else {
            rbEngineOffline.isChecked = true
            cardBridgeConfig.visibility = View.GONE
        }

        rgInferenceSource.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbEngineBridge) {
                settings.inferenceSource = InferenceSource.LAN_BRIDGE
                cardBridgeConfig.visibility = View.VISIBLE
            } else {
                settings.inferenceSource = InferenceSource.OFFLINE
                cardBridgeConfig.visibility = View.GONE
                loadActiveModelIfAvailable()
            }
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
