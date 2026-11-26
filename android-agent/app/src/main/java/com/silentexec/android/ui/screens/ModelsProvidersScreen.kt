package com.silentexec.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.silentexec.android.net.DownloadStatus
import com.silentexec.android.nlp.ProviderCatalog
import com.silentexec.android.nlp.ProviderPreset
import com.silentexec.android.ui.AgentUiState
import com.silentexec.android.ui.ModelCardState
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelsProvidersScreen(
    state: AgentUiState,
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
    modifier: Modifier = Modifier
) {
    val ar = state.isArabic
    val selectedPreset = ProviderCatalog.findById(state.selectedPresetId)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Honest Guidance Banner
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (ar) {
                        "توصية: مزودو السحابة (Gemini المجاني / Groq / OpenRouter) أو Ollama يمنحون موثوقية +95% فوراً للمهام متعددة الخطوات؛ بينما نماذج GGUF المحلية تعمل 100% بدون إنترنت بخصوصية تامة."
                    } else {
                        "Guidance: Cloud APIs (Gemini Free Tier, Groq, OpenRouter) and Ollama deliver instant 95%+ multi-step reliability; Local GGUF runs 100% offline & private."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        // Cloud & Ollama Providers Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (ar) "المزودون السحابيون و Ollama" else "Cloud & Ollama Providers",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ProviderCatalog.PRESETS.forEach { preset ->
                            FilterChip(
                                selected = state.selectedPresetId == preset.id,
                                onClick = { onSelectPreset(preset) },
                                label = {
                                    Text(if (ar) preset.displayNameAr else preset.displayName)
                                }
                            )
                        }
                    }

                    if (selectedPreset != null && selectedPreset.id == "LOCAL_GGUF") {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = if (ar) selectedPreset.badgeAr else selectedPreset.badgeEn,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(6.dp))
                        val downloadedModel = state.models.firstOrNull { it.isDownloaded }
                        if (downloadedModel != null && !downloadedModel.isActive) {
                            val label = downloadedModel.release.filename.removeSuffix(".gguf")
                            Button(onClick = { onActivateModel(downloadedModel) }) {
                                Text(
                                    if (ar) "تفعيل $label"
                                    else "Activate $label"
                                )
                            }
                        } else {
                            Text(
                                text = if (ar) {
                                    "اختر نموذج LFM3-1.7B أو Qwen3-1.7B بالأسفل للتحميل والتشغيل المحلي."
                                } else {
                                    "Download and activate LFM3-1.7B or Qwen3-1.7B below for 100% offline inference."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (selectedPreset != null && selectedPreset.id != "LOCAL_GGUF") {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = if (ar) selectedPreset.badgeAr else selectedPreset.badgeEn,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = state.apiBaseUrl,
                            onValueChange = { state.apiBaseUrl = it },
                            label = { Text(if (ar) "رابط API الأساسي" else "Base URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = state.apiModel,
                            onValueChange = { state.apiModel = it },
                            label = { Text(if (ar) "اسم النموذج" else "Model ID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))

                        if (selectedPreset.requiresApiKey) {
                            OutlinedTextField(
                                value = state.apiKey,
                                onValueChange = { state.apiKey = it },
                                label = { Text(if (ar) "مفتاح API" else "API Key") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(10.dp))
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onSaveCloudConfig) {
                                Text(if (ar) "حفظ وتفعيل" else "Save & Activate")
                            }
                            OutlinedButton(
                                onClick = onTestConnection,
                                enabled = !state.isTestingConnection
                            ) {
                                Text(
                                    if (state.isTestingConnection) {
                                        if (ar) "جارٍ الاختبار..." else "Testing..."
                                    } else {
                                        if (ar) "اختبار الاتصال" else "Test Connection"
                                    }
                                )
                            }
                        }

                        state.connectionTestMessage?.let { msg ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Local GGUF Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (ar) "نماذج GGUF المحلية (بدون إنترنت)" else "Local GGUF Models (Offline)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onRefreshModels) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = if (ar) "تحديث" else "Refresh"
                    )
                }
            }
        }

        items(state.models, key = { it.release.filename }) { model ->
            ModelItemCard(
                model = model,
                isArabic = ar,
                onDownload = { onDownloadModel(model) },
                onPause = { onPauseModel(model) },
                onResume = { onResumeModel(model) },
                onCancel = { onCancelModel(model) },
                onActivate = { onActivateModel(model) },
                onDelete = { onDeleteModel(model) }
            )
        }
    }
}

@Composable
private fun ModelItemCard(
    model: ModelCardState,
    isArabic: Boolean,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onActivate: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (model.isActive) 1.5.dp else 1.dp,
            color = if (model.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.release.filename.removeSuffix(".gguf"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    val sizeText = String.format(Locale.US, "%.0f MB", model.release.sizeMb)
                    val desc = model.release.description?.takeIf { it.isNotBlank() } ?: sizeText
                    Text(
                        text = "$sizeText · $desc",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (model.isActive) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = if (isArabic) "نشط" else "Active",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (model.status == DownloadStatus.DOWNLOADING || model.status == DownloadStatus.PAUSED || model.status == DownloadStatus.VERIFYING) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (model.progressPercent.coerceIn(0, 100)) / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                val statusLabel = when (model.status) {
                    DownloadStatus.DOWNLOADING -> String.format(Locale.US, "%d%% · %.1f MB/s", model.progressPercent, model.speedMBps)
                    DownloadStatus.PAUSED -> if (isArabic) "متوقف مؤقتاً (${model.progressPercent}%)" else "Paused (${model.progressPercent}%)"
                    DownloadStatus.VERIFYING -> if (isArabic) "التحقق من SHA-256..." else "Verifying SHA-256..."
                    else -> ""
                }
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            model.errorMessage?.let { err ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    model.status == DownloadStatus.DOWNLOADING -> {
                        OutlinedButton(onClick = onPause) {
                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isArabic) "إيقاف مؤقت" else "Pause")
                        }
                        TextButton(onClick = onCancel) {
                            Text(if (isArabic) "إلغاء" else "Cancel")
                        }
                    }
                    model.status == DownloadStatus.PAUSED -> {
                        Button(onClick = onResume) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isArabic) "استئناف" else "Resume")
                        }
                        TextButton(onClick = onCancel) {
                            Text(if (isArabic) "إلغاء" else "Cancel")
                        }
                    }
                    model.isDownloaded -> {
                        if (!model.isActive) {
                            Button(onClick = onActivate) {
                                Text(if (isArabic) "تفعيل محلي" else "Use Offline")
                            }
                        }
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = if (isArabic) "حذف" else "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    else -> {
                        Button(onClick = onDownload) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isArabic) "تنزيل (HTTP 206)" else "Download")
                        }
                    }
                }
            }
        }
    }
}
