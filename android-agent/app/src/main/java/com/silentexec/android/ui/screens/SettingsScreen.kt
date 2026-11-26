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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.silentexec.android.BuildConfig
import com.silentexec.android.R
import com.silentexec.android.core.ExecutionMode
import com.silentexec.android.core.ThemeMode
import com.silentexec.android.nlp.ProviderCatalog
import com.silentexec.android.ui.AgentUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: AgentUiState,
    onSetLanguage: (Boolean) -> Unit,
    onSetTheme: (ThemeMode) -> Unit,
    onSetExecutionMode: (ExecutionMode) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onCheckForUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ar = state.isArabic

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Language & Theme Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (ar) "اللغة والمظهر" else "Language & Appearance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))

                    Text(
                        text = if (ar) "اللغة (RTL / LTR)" else "Language (RTL / LTR)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = ar,
                            onClick = { onSetLanguage(true) },
                            label = { Text("العربية (RTL)") }
                        )
                        FilterChip(
                            selected = !ar,
                            onClick = { onSetLanguage(false) },
                            label = { Text("English (LTR)") }
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (ar) "المظهر" else "Theme",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.themeMode == ThemeMode.SYSTEM,
                            onClick = { onSetTheme(ThemeMode.SYSTEM) },
                            label = { Text(if (ar) "النظام" else "System") }
                        )
                        FilterChip(
                            selected = state.themeMode == ThemeMode.DARK,
                            onClick = { onSetTheme(ThemeMode.DARK) },
                            label = { Text(if (ar) "داكن" else "Dark") }
                        )
                        FilterChip(
                            selected = state.themeMode == ThemeMode.LIGHT,
                            onClick = { onSetTheme(ThemeMode.LIGHT) },
                            label = { Text(if (ar) "فاتح" else "Light") }
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (ar) "وضع التنفيذ والأمان" else "Execution & Safety Mode",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.executionMode == ExecutionMode.SILENT,
                            onClick = { onSetExecutionMode(ExecutionMode.SILENT) },
                            label = { Text(if (ar) "صامت" else "Silent") }
                        )
                        FilterChip(
                            selected = state.executionMode == ExecutionMode.INTERACTIVE,
                            onClick = { onSetExecutionMode(ExecutionMode.INTERACTIVE) },
                            label = { Text(if (ar) "تفاعلي" else "Interactive") }
                        )
                        FilterChip(
                            selected = state.executionMode == ExecutionMode.DRY_RUN,
                            onClick = { onSetExecutionMode(ExecutionMode.DRY_RUN) },
                            label = { Text(if (ar) "تجريبي" else "Dry Run") }
                        )
                    }
                }
            }
        }

        // Auto-Enabled Agent Toolbelt Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.toolbelt_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.toolbelt_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ProviderCatalog.AUTO_ENABLED_TOOLBELT.forEach { tool ->
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(if (ar) "✓ ${tool.titleAr}" else "✓ ${tool.titleEn}")
                                }
                            )
                        }
                    }
                }
            }
        }

        // Permissions Wizard Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (ar) "أذونات التحكم بالشاشة" else "Device Control Permissions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.AccessibilityNew, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (ar) "خدمة إمكانية الوصول" else "Accessibility Service",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = if (state.accessibilityEnabled) {
                                        if (ar) "مفعّلة وجاهزة" else "Active & connected"
                                    } else {
                                        if (ar) "مطلوبة لقراءة الشاشة والنقر" else "Required to observe & tap UI"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        OutlinedButton(onClick = onOpenAccessibilitySettings) {
                            Text(if (state.accessibilityEnabled) (if (ar) "إعدادات" else "Settings") else (if (ar) "تفعيل" else "Enable"))
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (ar) "النافذة العائمة (HUD)" else "Floating Overlay HUD",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = if (state.overlayEnabled) {
                                        if (ar) "مفعّلة" else "Enabled"
                                    } else {
                                        if (ar) "لعرض حالة الوكيل وزر الإيقاف" else "Shows live progress & Stop pill"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        OutlinedButton(onClick = onOpenOverlaySettings) {
                            Text(if (state.overlayEnabled) (if (ar) "إعدادات" else "Settings") else (if (ar) "منح الإذن" else "Grant"))
                        }
                    }
                }
            }
        }

        // In-App Auto-Updater Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (ar) "التحديث التلقائي من GitHub" else "GitHub Releases Auto-Updater",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (ar) {
                            "الإصدار الحالي: v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                        } else {
                            "Current version: v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    state.updateMessage?.let { msg ->
                        Spacer(Modifier.height(8.dp))
                        Text(text = msg, style = MaterialTheme.typography.bodySmall)
                    }

                    state.updateDownloadProgress?.let { pct ->
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { pct.coerceIn(0, 100) / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onCheckForUpdate,
                            enabled = !state.isCheckingUpdate
                        ) {
                            Text(
                                if (state.isCheckingUpdate) {
                                    if (ar) "جارٍ التحقق..." else "Checking..."
                                } else {
                                    if (ar) "تحقق من التحديثات" else "Check for Updates"
                                }
                            )
                        }
                        if (state.availableRelease != null && state.updateDownloadProgress == null) {
                            OutlinedButton(onClick = onInstallUpdate) {
                                Text(if (ar) "تنزيل وتثبيت" else "Download & Install")
                            }
                        }
                    }
                }
            }
        }
    }
}
