package com.silentexec.android.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.silentexec.android.core.ActionCommand
import com.silentexec.android.core.ExecutionMode
import com.silentexec.android.core.ThemeMode
import com.silentexec.android.net.AppReleaseItem
import com.silentexec.android.net.DownloadStatus
import com.silentexec.android.net.ModelReleaseItem
import java.util.UUID

enum class AgentTab {
    AGENT,
    MODELS,
    SETTINGS
}

sealed interface TraceItem {
    val id: String

    data class UserGoal(
        override val id: String = UUID.randomUUID().toString(),
        val text: String
    ) : TraceItem

    data class Observation(
        override val id: String = UUID.randomUUID().toString(),
        val step: Int,
        val packageName: String,
        val elementCount: Int,
        val screenChanged: Boolean?
    ) : TraceItem

    data class Reasoning(
        override val id: String = UUID.randomUUID().toString(),
        val step: Int,
        val thought: String,
        val actionWire: String,
        val target: String
    ) : TraceItem

    data class ActionResult(
        override val id: String = UUID.randomUUID().toString(),
        val step: Int,
        val success: Boolean,
        val message: String
    ) : TraceItem

    data class Confirmation(
        override val id: String = UUID.randomUUID().toString(),
        val step: Int,
        val command: ActionCommand,
        val reason: String,
        val resolved: Boolean = false,
        val approved: Boolean? = null,
        val onRespond: (Boolean) -> Unit
    ) : TraceItem

    data class Summary(
        override val id: String = UUID.randomUUID().toString(),
        val text: String,
        val stepsTaken: Int
    ) : TraceItem

    data class Error(
        override val id: String = UUID.randomUUID().toString(),
        val message: String
    ) : TraceItem
}

data class ModelCardState(
    val release: ModelReleaseItem,
    val isDownloaded: Boolean,
    val isActive: Boolean,
    val status: DownloadStatus? = null,
    val progressPercent: Int = 0,
    val speedMBps: Double = 0.0,
    val errorMessage: String? = null
)

class AgentUiState {
    var activeTab by mutableStateOf(AgentTab.AGENT)
    var isArabic by mutableStateOf(true)
    var themeMode by mutableStateOf(ThemeMode.SYSTEM)
    var executionMode by mutableStateOf(ExecutionMode.SILENT)

    var selectedPresetId by mutableStateOf("GEMINI")
    var activeProviderLabel by mutableStateOf("GEMINI · gemini-2.5-flash")
    var apiBaseUrl by mutableStateOf("https://generativelanguage.googleapis.com/v1beta/openai")
    var apiModel by mutableStateOf("gemini-2.5-flash")
    var apiKey by mutableStateOf("")
    var connectionTestMessage by mutableStateOf<String?>(null)
    var isTestingConnection by mutableStateOf(false)

    var accessibilityEnabled by mutableStateOf(false)
    var overlayEnabled by mutableStateOf(false)

    var composerText by mutableStateOf("")
    var isRunning by mutableStateOf(false)
    var isListening by mutableStateOf(false)

    val traceItems = mutableStateListOf<TraceItem>()
    val models = mutableStateListOf<ModelCardState>()

    var isCheckingUpdate by mutableStateOf(false)
    var updateMessage by mutableStateOf<String?>(null)
    var availableRelease by mutableStateOf<AppReleaseItem?>(null)
    var updateDownloadProgress by mutableStateOf<Int?>(null)
}
