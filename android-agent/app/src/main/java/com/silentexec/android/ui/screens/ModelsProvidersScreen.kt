package com.silentexec.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.silentexec.android.net.DownloadStatus
import com.silentexec.android.nlp.ProviderCatalog
import com.silentexec.android.nlp.ProviderPreset
import com.silentexec.android.ui.AgentUiState
import com.silentexec.android.ui.ModelCardState
import com.silentexec.android.ui.components.LtrCodeBlock
import com.silentexec.android.ui.components.LtrTechnicalBanner
import com.silentexec.android.ui.theme.SilentExecColors
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
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section Header: On-Device Edge Models
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (ar) "النماذج المحلية (بدون إنترنت)" else "On-Device Edge Models",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRefreshModels) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                }
            }
        }

        items(state.models, key = { it.release.filename }) { model ->
            EdgeModelCard(
                model = model,
                activeEngine = state.executionEngine,
                isArabic = ar,
                onDownload = { onDownloadModel(model) },
                onPause = { onPauseModel(model) },
                onResume = { onResumeModel(model) },
                onCancel = { onCancelModel(model) },
                onActivate = { onActivateModel(model) },
                onDelete = { onDeleteModel(model) }
            )
        }

        // Cloud & Ollama Providers Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(Modifier.height(10.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProviderCatalog.PRESETS.filter { it.id != "LOCAL_GGUF" }.forEach { preset ->
                            FilterChip(
                                selected = state.selectedPresetId == preset.id,
                                onClick = { onSelectPreset(preset) },
                                label = {
                                    Text(if (ar) preset.displayNameAr else preset.displayNameEn)
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.apiBaseUrl,
                        onValueChange = { state.apiBaseUrl = it },
                        label = { Text(if (ar) "رابط API الأساسي" else "API Base URL") },
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
                    if (selectedPreset?.requiresApiKey != false) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.apiKey,
                            onValueChange = { state.apiKey = it },
                            label = { Text(if (ar) "مفتاح API" else "API Key") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onSaveCloudConfig,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (ar) "حفظ وتفعيل" else "Save & Activate")
                        }
                        OutlinedButton(
                            onClick = onTestConnection,
                            enabled = !state.isTestingConnection,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (ar) "اختبار الاتصال" else "Test Connection")
                        }
                    }

                    state.connectionTestMessage?.let { msg ->
                        Spacer(Modifier.height(10.dp))
                        LtrCodeBlock(text = msg)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EdgeModelCard(
    model: ModelCardState,
    activeEngine: String,
    isArabic: Boolean,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onActivate: () -> Unit,
    onDelete: () -> Unit
) {
    val filename = model.release.filename
    val isDefaultRecommended = filename.contains("Qwen3.5-2B", ignoreCase = true)
    val ramReq = when {
        filename.contains("2B", ignoreCase = true) -> "RAM ≥ 3 GB"
        filename.contains("1.7B", ignoreCase = true) -> "RAM ≥ 2.5 GB"
        else -> "RAM ≥ 1.5 GB"
    }
    val sizeGb = String.format(Locale.US, "%.2f GB", model.release.sizeBytes / (1024.0 * 1024.0 * 1024.0))

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (model.isActive) 1.5.dp else 1.dp,
            color = if (model.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                LtrTechnicalBanner(
                    text = filename,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (isDefaultRecommended) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SilentExecColors.Success.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = if (isArabic) "موصى به (افتراضي)" else "Recommended",
                            style = MaterialTheme.typography.labelMedium,
                            color = SilentExecColors.Success,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            model.release.description?.let { desc ->
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LtrTechnicalBanner(text = sizeGb)
                LtrTechnicalBanner(text = ramReq)
                LtrTechnicalBanner(
                    text = activeEngine,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (model.isDownloaded) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = SilentExecColors.Success,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isArabic) "تم التحقق SHA-256" else "SHA-256 Verified",
                            style = MaterialTheme.typography.labelMedium,
                            color = SilentExecColors.Success
                        )
                    }
                }
            }

            if (model.status == DownloadStatus.DOWNLOADING || model.status == DownloadStatus.PAUSED || model.status == DownloadStatus.VERIFYING) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (model.progressPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                LtrTechnicalBanner(
                    text = "${model.progressPercent}% · ${String.format(Locale.US, "%.2f MB/s", model.speedMBps)} · ETA ${model.etaSeconds}s"
                )
            }

            model.errorMessage?.let { err ->
                Spacer(Modifier.height(8.dp))
                LtrCodeBlock(text = err, borderColor = SilentExecColors.Error)
            }

            Spacer(Modifier.height(12.dp))
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
                            Text(if (isArabic) "إلغاء" else "Cancel", color = SilentExecColors.Error)
                        }
                    }
                    model.status == DownloadStatus.PAUSED -> {
                        Button(onClick = onResume) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isArabic) "استئناف" else "Resume")
                        }
                        TextButton(onClick = onCancel) {
                            Text(if (isArabic) "إلغاء" else "Cancel", color = SilentExecColors.Error)
                        }
                    }
                    model.isDownloaded -> {
                        Button(
                            onClick = onActivate,
                            enabled = !model.isActive
                        ) {
                            Text(
                                if (model.isActive) {
                                    if (isArabic) "نشط ✓" else "Active ✓"
                                } else {
                                    if (isArabic) "تفعيل" else "Activate"
                                }
                            )
                        }
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = if (isArabic) "حذف" else "Delete",
                                tint = SilentExecColors.Error
                            )
                        }
                    }
                    else -> {
                        Button(onClick = onDownload) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isArabic) "تنزيل" else "Download")
                        }
                    }
                }
            }
        }
    }
}
