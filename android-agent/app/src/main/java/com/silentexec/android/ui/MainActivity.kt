package com.silentexec.android.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.silentexec.android.core.AppSettings
import com.silentexec.android.core.InferenceSource
import com.silentexec.android.core.ThemeMode
import com.silentexec.android.exec.AccessibilityAgentEngine
import com.silentexec.android.exec.ActionPerformer
import com.silentexec.android.exec.AgentTraceEvent
import com.silentexec.android.exec.AndroidActionExecutor
import com.silentexec.android.exec.SilentExecAccessibilityService
import com.silentexec.android.net.AppUpdater
import com.silentexec.android.net.DownloadStatus
import com.silentexec.android.net.GitHubReleaseClient
import com.silentexec.android.net.ModelDownloader
import com.silentexec.android.net.UpdateCheckResult
import com.silentexec.android.nlp.CloudApiClient
import com.silentexec.android.nlp.OnDeviceLlamaEngine
import com.silentexec.android.nlp.ProviderCatalog
import com.silentexec.android.nlp.ProviderPreset
import com.silentexec.android.ui.screens.AgentScreen
import com.silentexec.android.ui.screens.ModelsProvidersScreen
import com.silentexec.android.ui.screens.SettingsScreen
import com.silentexec.android.ui.theme.SilentExecTheme
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var settings: AppSettings
    private lateinit var executor: AndroidActionExecutor
    private lateinit var modelDownloader: ModelDownloader
    private lateinit var appUpdater: AppUpdater
    private lateinit var overlayController: LiveOverlayController
    private val releaseClient = GitHubReleaseClient()
    private val llamaEngine = OnDeviceLlamaEngine()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val uiState = AgentUiState()
    private var activeEngine: AccessibilityAgentEngine? = null
    private var voiceInputHelper: VoiceInputHelper? = null

    private val recordAudioLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceListening()
        } else {
            uiState.traceItems.add(
                TraceItem.Error(
                    message = if (uiState.isArabic) "يرجى منح إذن الميكروفون للإدخال الصوتي" else "Microphone permission required for voice input"
                )
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AppSettings(this)
        settings.applyLocale()
        settings.applyTheme()

        executor = AndroidActionExecutor(this)
        modelDownloader = ModelDownloader(this)
        appUpdater = AppUpdater(this, releaseClient)
        overlayController = LiveOverlayController(this)

        syncStateFromSettings()
        refreshModelsList()
        loadActiveModelIfAvailable()

        setContent {
            SilentExecTheme(
                themeMode = uiState.themeMode,
                isArabic = uiState.isArabic
            ) {
                SilentExecApp(
                    state = uiState,
                    onOpenAccessibility = { openAccessibilitySettings() },
                    onOpenOverlay = { openOverlaySettings() },
                    onSendGoal = { goal -> runAgentGoal(goal) },
                    onStop = { stopAgent() },
                    onVoiceClick = { onVoiceButtonClicked() },
                    onSelectPreset = { preset -> selectPreset(preset) },
                    onSaveCloudConfig = { saveCloudConfig() },
                    onTestConnection = { testCloudConnection() },
                    onRefreshModels = { refreshModelsList() },
                    onDownloadModel = { model -> startModelDownload(model) },
                    onPauseModel = { model -> modelDownloader.pause(model.release.filename); updateModelStatus(model.release.filename, DownloadStatus.PAUSED) },
                    onResumeModel = { model -> modelDownloader.resume(model.release.filename); startModelDownload(model) },
                    onCancelModel = { model -> modelDownloader.cancel(model.release.filename); refreshModelsList() },
                    onActivateModel = { model -> activateLocalModel(model) },
                    onDeleteModel = { model -> modelDownloader.deleteModel(model.release.filename); refreshModelsList() },
                    onSetLanguage = { isAr ->
                        settings.setLanguageAndApply(if (isAr) "ar" else "en")
                        uiState.isArabic = isAr
                    },
                    onSetTheme = { mode ->
                        settings.themeMode = mode
                        uiState.themeMode = mode
                    },
                    onSetExecutionMode = { mode ->
                        settings.executionMode = mode
                        uiState.executionMode = mode
                    },
                    onCheckForUpdate = { checkForAppUpdate() },
                    onInstallUpdate = { downloadAndInstallAppUpdate() }
                )
            }
        }

        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        uiState.accessibilityEnabled = SilentExecAccessibilityService.get() != null
        uiState.overlayEnabled = Settings.canDrawOverlays(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        activeEngine?.cancel()
        voiceInputHelper?.destroy()
        overlayController.hide()
        llamaEngine.unload()
    }

    private fun syncStateFromSettings() {
        uiState.isArabic = settings.language != "en"
        uiState.themeMode = settings.themeMode
        uiState.executionMode = settings.executionMode
        uiState.selectedPresetId = ProviderCatalog.inferPresetId(settings)
        uiState.apiBaseUrl = settings.openAiApiBase
        uiState.apiModel = settings.openAiApiModel
        uiState.apiKey = settings.openAiApiKey
        uiState.activeProviderLabel = ProviderCatalog.activeDisplayLabel(settings)
        uiState.accessibilityEnabled = SilentExecAccessibilityService.get() != null
        uiState.overlayEnabled = Settings.canDrawOverlays(this)
    }

    private fun runAgentGoal(goal: String) {
        val clean = goal.trim()
        if (clean.isEmpty() || uiState.isRunning) return

        uiState.composerText = ""
        uiState.isRunning = true
        uiState.traceItems.add(TraceItem.UserGoal(text = clean))

        overlayController.show(
            initialText = if (uiState.isArabic) "SilentExec: جارٍ التنفيذ..." else "SilentExec: Running...",
            onCancel = { stopAgent() }
        )

        val engine = AccessibilityAgentEngine(
            settings = settings,
            performer = ActionPerformer { cmd, mode -> executor.execute(cmd, mode) },
            llamaEngine = llamaEngine
        )
        activeEngine = engine

        thread(name = "silentexec-agent") {
            engine.run(clean) { event ->
                mainHandler.post { handleAgentEvent(event) }
            }
        }
    }

    private fun handleAgentEvent(event: AgentTraceEvent) {
        when (event) {
            is AgentTraceEvent.Started -> Unit
            is AgentTraceEvent.Observed -> {
                uiState.traceItems.add(
                    TraceItem.Observation(
                        step = event.step,
                        packageName = event.packageName,
                        elementCount = event.elementCount,
                        screenChanged = event.previousScreenChanged
                    )
                )
                overlayController.update(
                    if (uiState.isArabic) "خطوة ${event.step}: قراءة الشاشة" else "Step ${event.step}: Observing screen"
                )
            }
            is AgentTraceEvent.Reasoned -> {
                uiState.traceItems.add(
                    TraceItem.Reasoning(
                        step = event.step,
                        thought = event.thought,
                        actionWire = event.command.action.wireName,
                        target = event.command.target.orEmpty()
                    )
                )
                overlayController.update("${event.command.action.wireName}: ${event.command.target.orEmpty()}")
            }
            is AgentTraceEvent.ConfirmRequired -> {
                lateinit var confirmItem: TraceItem.Confirmation
                confirmItem = TraceItem.Confirmation(
                    step = event.step,
                    command = event.command,
                    reason = event.reason,
                    onRespond = { approved ->
                        val idx = uiState.traceItems.indexOfFirst { it.id == confirmItem.id }
                        if (idx >= 0) {
                            uiState.traceItems[idx] = confirmItem.copy(resolved = true, approved = approved)
                        }
                        event.respond(approved)
                    }
                )
                uiState.traceItems.add(confirmItem)
            }
            is AgentTraceEvent.Acted -> {
                uiState.traceItems.add(
                    TraceItem.ActionResult(
                        step = event.step,
                        success = event.result.success,
                        message = event.result.message
                    )
                )
            }
            is AgentTraceEvent.Finished -> {
                uiState.isRunning = false
                overlayController.hide()
                uiState.traceItems.add(TraceItem.Summary(text = event.summary, stepsTaken = event.stepsTaken))
            }
            is AgentTraceEvent.Failed -> {
                uiState.isRunning = false
                overlayController.hide()
                uiState.traceItems.add(TraceItem.Error(message = event.message))
            }
            AgentTraceEvent.Cancelled -> {
                uiState.isRunning = false
                overlayController.hide()
                uiState.traceItems.add(
                    TraceItem.Error(message = if (uiState.isArabic) "تم إيقاف المهمة" else "Task cancelled")
                )
            }
        }
    }

    private fun stopAgent() {
        activeEngine?.cancel()
        uiState.isRunning = false
        overlayController.hide()
    }

    private fun onVoiceButtonClicked() {
        if (uiState.isListening) {
            voiceInputHelper?.stopListening()
            uiState.isListening = false
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        startVoiceListening()
    }

    private fun startVoiceListening() {
        if (voiceInputHelper == null) {
            voiceInputHelper = VoiceInputHelper(
                context = this,
                onResult = { text ->
                    mainHandler.post {
                        uiState.isListening = false
                        uiState.composerText = text
                        runAgentGoal(text)
                    }
                },
                onError = { err ->
                    mainHandler.post {
                        uiState.isListening = false
                        uiState.traceItems.add(TraceItem.Error(message = err))
                    }
                }
            )
        }
        uiState.isListening = true
        voiceInputHelper?.startListening(preferArabic = uiState.isArabic)
    }

    private fun selectPreset(preset: ProviderPreset) {
        uiState.selectedPresetId = preset.id
        uiState.apiBaseUrl = preset.baseUrl
        uiState.apiModel = preset.defaultModel
        uiState.connectionTestMessage = null
    }

    private fun saveCloudConfig() {
        settings.providerPreset = uiState.selectedPresetId
        settings.inferenceSource = InferenceSource.OPENAI_COMPAT
        settings.openAiApiBase = uiState.apiBaseUrl.trim()
        settings.openAiApiModel = uiState.apiModel.trim()
        settings.openAiApiKey = uiState.apiKey.trim()
        uiState.activeProviderLabel = ProviderCatalog.activeDisplayLabel(settings)
        uiState.connectionTestMessage = if (uiState.isArabic) "✓ تم الحفظ والتفعيل" else "✓ Saved & activated"
    }

    private fun testCloudConnection() {
        uiState.isTestingConnection = true
        uiState.connectionTestMessage = null
        val client = CloudApiClient(
            baseUrl = uiState.apiBaseUrl.trim(),
            model = uiState.apiModel.trim(),
            apiKey = uiState.apiKey.trim()
        )
        thread(name = "cloud-test") {
            val res = client.generateCompletion(
                systemPrompt = "Reply with JSON: {\"action\":\"done\",\"target\":\"ok\",\"platform\":\"android\",\"confidence\":1.0}",
                userPrompt = "ping",
                useToolCalling = false
            )
            mainHandler.post {
                uiState.isTestingConnection = false
                uiState.connectionTestMessage = res.fold(
                    onSuccess = { if (uiState.isArabic) "✓ الاتصال ناجح" else "✓ Connection succeeded" },
                    onFailure = { e -> "✕ ${e.message ?: "Connection failed"}" }
                )
            }
        }
    }

    private fun refreshModelsList() {
        val localFiles = modelDownloader.listDownloadedModels().map { it.name }.toSet()
        val curated = GitHubReleaseClient.CURATED_EDGE_MODELS
        uiState.models.clear()
        for (item in curated) {
            val downloaded = localFiles.contains(item.filename)
            val active = settings.inferenceSource == InferenceSource.OFFLINE && settings.activeModelFile == item.filename
            uiState.models.add(
                ModelCardState(
                    release = item,
                    isDownloaded = downloaded,
                    isActive = active
                )
            )
        }
    }

    private fun startModelDownload(model: ModelCardState) {
        val filename = model.release.filename
        updateModelStatus(filename, DownloadStatus.DOWNLOADING, progress = 0)
        thread(name = "gguf-download") {
            val res = modelDownloader.downloadModelResumable(
                model = model.release,
                onProgress = { state ->
                    val pct = if (state.totalBytes > 0) ((state.downloadedBytes * 100) / state.totalBytes).toInt() else 0
                    mainHandler.post {
                        updateModelStatus(filename, state.status, pct, state.speedMBps)
                    }
                }
            )
            mainHandler.post {
                res.fold(
                    onSuccess = {
                        refreshModelsList()
                        activateLocalModel(model)
                    },
                    onFailure = { e ->
                        val idx = uiState.models.indexOfFirst { it.release.filename == filename }
                        if (idx >= 0) {
                            uiState.models[idx] = uiState.models[idx].copy(
                                status = if (modelDownloader.isPaused(filename)) DownloadStatus.PAUSED else DownloadStatus.ERROR,
                                errorMessage = e.message
                            )
                        }
                    }
                )
            }
        }
    }

    private fun updateModelStatus(filename: String, status: DownloadStatus, progress: Int = 0, speed: Double = 0.0) {
        val idx = uiState.models.indexOfFirst { it.release.filename == filename }
        if (idx >= 0) {
            uiState.models[idx] = uiState.models[idx].copy(
                status = status,
                progressPercent = progress,
                speedMBps = speed,
                errorMessage = null
            )
        }
    }

    private fun activateLocalModel(model: ModelCardState) {
        val file = modelDownloader.getModelFile(model.release.filename)
        settings.providerPreset = "LOCAL_GGUF"
        settings.inferenceSource = InferenceSource.OFFLINE
        settings.activeModelFile = model.release.filename
        llamaEngine.setModelFile(file)
        uiState.selectedPresetId = "LOCAL_GGUF"
        uiState.activeProviderLabel = ProviderCatalog.activeDisplayLabel(settings)
        refreshModelsList()
        loadActiveModelIfAvailable()
    }

    private fun loadActiveModelIfAvailable() {
        if (settings.inferenceSource == InferenceSource.OFFLINE && settings.activeModelFile.isNotBlank()) {
            val file = modelDownloader.getModelFile(settings.activeModelFile)
            llamaEngine.setModelFile(file)
            if (llamaEngine.validateModelFile(file) == null && OnDeviceLlamaEngine.isNativeLoaded && !llamaEngine.isLoaded()) {
                llamaEngine.loadModelAsync(file) { }
            }
        }
    }

    private fun checkForAppUpdate() {
        uiState.isCheckingUpdate = true
        uiState.updateMessage = null
        thread(name = "app-update-check") {
            val res = appUpdater.checkForUpdate()
            mainHandler.post {
                uiState.isCheckingUpdate = false
                res.fold(
                    onSuccess = { result ->
                        when (result) {
                            is UpdateCheckResult.UpdateAvailable -> {
                                uiState.availableRelease = result.release
                                uiState.updateMessage = if (uiState.isArabic) {
                                    "تحديث متوفر: v${result.release.versionName}"
                                } else {
                                    "Update available: v${result.release.versionName}"
                                }
                            }
                            is UpdateCheckResult.UpToDate -> {
                                uiState.availableRelease = null
                                uiState.updateMessage = if (uiState.isArabic) {
                                    "أنت على أحدث إصدار (v${result.currentVersion})"
                                } else {
                                    "You are up to date (v${result.currentVersion})"
                                }
                            }
                        }
                    },
                    onFailure = { e ->
                        uiState.updateMessage = "✕ ${e.message ?: "Update check failed"}"
                    }
                )
            }
        }
    }

    private fun downloadAndInstallAppUpdate() {
        val release = uiState.availableRelease ?: return
        uiState.updateDownloadProgress = 0
        thread(name = "app-update-download") {
            val res = appUpdater.downloadAndInstallUpdate(release) { pct ->
                mainHandler.post { uiState.updateDownloadProgress = pct }
            }
            mainHandler.post {
                uiState.updateDownloadProgress = null
                res.fold(
                    onSuccess = { apk -> appUpdater.triggerApkInstall(apk) },
                    onFailure = { e -> uiState.updateMessage = "✕ ${e.message ?: "Download failed"}" }
                )
            }
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val text = intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            ?: intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (!text.isNullOrBlank()) {
            uiState.activeTab = AgentTab.AGENT
            uiState.composerText = text
        }
    }
}

@Composable
private fun SilentExecApp(
    state: AgentUiState,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit,
    onSendGoal: (String) -> Unit,
    onStop: () -> Unit,
    onVoiceClick: () -> Unit,
    onSelectPreset: (ProviderPreset) -> Unit,
    onSaveCloudConfig: () -> Unit,
    onTestConnection: () -> Unit,
    onRefreshModels: () -> Unit,
    onDownloadModel: (ModelCardState) -> Unit,
    onPauseModel: (ModelCardState) -> Unit,
    onResumeModel: (ModelCardState) -> Unit,
    onCancelModel: (ModelCardState) -> Unit,
    onActivateModel: (ModelCardState) -> Unit,
    onDeleteModel: (ModelCardState) -> Unit,
    onSetLanguage: (Boolean) -> Unit,
    onSetTheme: (ThemeMode) -> Unit,
    onSetExecutionMode: (com.silentexec.android.core.ExecutionMode) -> Unit,
    onCheckForUpdate: () -> Unit,
    onInstallUpdate: () -> Unit
) {
    val ar = state.isArabic
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = state.activeTab == AgentTab.AGENT,
                    onClick = { state.activeTab = AgentTab.AGENT },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                    label = { Text(if (ar) "الوكيل" else "Agent") }
                )
                NavigationBarItem(
                    selected = state.activeTab == AgentTab.MODELS,
                    onClick = { state.activeTab = AgentTab.MODELS },
                    icon = { Icon(Icons.Default.Hub, contentDescription = null) },
                    label = { Text(if (ar) "النماذج" else "Models") }
                )
                NavigationBarItem(
                    selected = state.activeTab == AgentTab.SETTINGS,
                    onClick = { state.activeTab = AgentTab.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(if (ar) "الإعدادات" else "Settings") }
                )
            }
        }
    ) { innerPadding ->
        when (state.activeTab) {
            AgentTab.AGENT -> AgentScreen(
                state = state,
                onOpenModels = { state.activeTab = AgentTab.MODELS },
                onOpenAccessibility = onOpenAccessibility,
                onSendGoal = onSendGoal,
                onStop = onStop,
                onVoiceClick = onVoiceClick,
                modifier = Modifier.padding(innerPadding)
            )
            AgentTab.MODELS -> ModelsProvidersScreen(
                state = state,
                onSelectPreset = onSelectPreset,
                onSaveCloudConfig = onSaveCloudConfig,
                onTestConnection = onTestConnection,
                onRefreshModels = onRefreshModels,
                onDownloadModel = onDownloadModel,
                onPauseModel = onPauseModel,
                onResumeModel = onResumeModel,
                onCancelModel = onCancelModel,
                onActivateModel = onActivateModel,
                onDeleteModel = onDeleteModel,
                modifier = Modifier.padding(innerPadding)
            )
            AgentTab.SETTINGS -> SettingsScreen(
                state = state,
                onSetLanguage = onSetLanguage,
                onSetTheme = onSetTheme,
                onSetExecutionMode = onSetExecutionMode,
                onOpenAccessibilitySettings = onOpenAccessibility,
                onOpenOverlaySettings = onOpenOverlay,
                onCheckForUpdate = onCheckForUpdate,
                onInstallUpdate = onInstallUpdate,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
