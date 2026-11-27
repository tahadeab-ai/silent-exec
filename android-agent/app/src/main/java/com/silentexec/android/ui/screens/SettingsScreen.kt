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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.silentexec.android.BuildConfig
import com.silentexec.android.core.ExecutionMode
import com.silentexec.android.core.ThemeMode
import com.silentexec.android.ui.AgentUiState
import com.silentexec.android.ui.components.LtrCodeBlock
import com.silentexec.android.ui.components.LtrTechnicalBanner
import com.silentexec.android.ui.theme.SilentExecColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: AgentUiState,
    onSetLanguage: (Boolean) -> Unit,
    onSetTheme: (ThemeMode) -> Unit,
    onSetExecutionMode: (ExecutionMode) -> Unit,
    onSetExecutionEngine: (String) -> Unit,
    onSetThinkingMode: (Boolean) -> Unit,
    onSetSafeMode: (Boolean) -> Unit,
    onRequestShizuku: () -> Unit,
    onSaveBridgeConfig: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onToggleDeveloperDebugOverlay: (Boolean) -> Unit = {},
    onCheckForUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ar = state.isArabic

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Execution Engine & Thinking Mode Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (ar) "محرك التنفيذ المحلي" else "On-Device Inference Engine",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Cactus", "llama.cpp").forEach { engine ->
                            FilterChip(
                                selected = state.executionEngine == engine,
                                onClick = { onSetExecutionEngine(engine) },
                                label = { LtrTechnicalBanner(text = engine) }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (ar) "وضع التفكير العميق (<think>)" else "Thinking Mode (<think>)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (ar) "معطّل افتراضياً لتسريع استدعاء الأدوات" else "Disabled by default for fast tool-calling",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.thinkingModeEnabled,
                            onCheckedChange = onSetThinkingMode
                        )
                    }
                }
            }
        }

        // 2. Safe Mode & Execution Mode Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = SilentExecColors.Success)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (ar) "الأمان ووضع التنفيذ" else "Safety & Execution Mode",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExecutionMode.entries.forEach { mode ->
                            FilterChip(
                                selected = state.executionMode == mode,
                                onClick = { onSetExecutionMode(mode) },
                                label = { Text(mode.name) }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (ar) "الوضع الآمن (تأكيد الشاشات الحساسة)" else "Safe Mode (Confirm Sensitive Screens)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (ar) "يوقف الوكيل تلقائياً عند كلمات المرور والدفع" else "Pauses on passwords, FLAG_SECURE & payments",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.safeModeEnabled,
                            onCheckedChange = onSetSafeMode
                        )
                    }
                }
            }
        }

        // 3. Accessibility, Overlay & Shizuku Fallback Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccessibilityNew, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (ar) "أذونات النظام و Shizuku" else "System Permissions & Shizuku",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onOpenAccessibilitySettings, modifier = Modifier.weight(1f)) {
                            Text(
                                if (state.accessibilityEnabled) {
                                    if (ar) "إمكانية الوصول ✓" else "A11y ✓"
                                } else {
                                    if (ar) "تفعيل الوصول" else "Enable A11y"
                                }
                            )
                        }
                        OutlinedButton(onClick = onOpenOverlaySettings, modifier = Modifier.weight(1f)) {
                            Text(
                                if (state.overlayEnabled) {
                                    if (ar) "العرض العلوي ✓" else "Overlay ✓"
                                } else {
                                    if (ar) "إذن العرض" else "Overlay"
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Terminal, contentDescription = null, tint = SilentExecColors.Warning)
                        Text(
                            text = if (state.shizukuAuthorized) {
                                if (ar) "Shizuku مفعّل (بديل بدون روت)" else "Shizuku Authorized"
                            } else {
                                if (ar) "Shizuku غير مصرح" else "Shizuku Not Authorized"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = onRequestShizuku) {
                            Text(if (ar) "تصريح" else "Authorize")
                        }
                    }
                }
            }
        }

        // 4. Desktop LAN Bridge Pairing Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Computer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (ar) "اقتران جسر الحاسوب (LAN Bridge)" else "Desktop LAN Bridge Pairing",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.bridgeHost,
                            onValueChange = { state.bridgeHost = it },
                            label = { Text("Host IP") },
                            singleLine = true,
                            modifier = Modifier.weight(2f)
                        )
                        OutlinedTextField(
                            value = state.bridgePort,
                            onValueChange = { state.bridgePort = it },
                            label = { Text("Port") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.bridgeToken,
                        onValueChange = { state.bridgeToken = it },
                        label = { Text(if (ar) "رمز الاقتران" else "Pairing Token") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onSaveBridgeConfig, modifier = Modifier.fillMaxWidth()) {
                        Text(if (ar) "حفظ إعدادات الجسر" else "Save Bridge Pairing")
                    }
                }
            }
        }

        // 5. Language, Theme & Updates Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (ar) "اللغة والمظهر والتحديثات" else "Language, Theme & Updates",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = ar, onClick = { onSetLanguage(true) }, label = { Text("العربية (RTL)") })
                        FilterChip(selected = !ar, onClick = { onSetLanguage(false) }, label = { Text("English (LTR)") })
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = state.themeMode == ThemeMode.DARK, onClick = { onSetTheme(ThemeMode.DARK) }, label = { Text(if (ar) "داكن" else "Dark") })
                        FilterChip(selected = state.themeMode == ThemeMode.LIGHT, onClick = { onSetTheme(ThemeMode.LIGHT) }, label = { Text(if (ar) "فاتح" else "Light") })
                        FilterChip(selected = state.themeMode == ThemeMode.SYSTEM, onClick = { onSetTheme(ThemeMode.SYSTEM) }, label = { Text(if (ar) "النظام" else "System") })
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        LtrTechnicalBanner(text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(onClick = onCheckForUpdate, enabled = !state.isCheckingUpdate) {
                            Text(if (ar) "فحص التحديث" else "Check Update")
                        }
                    }
                    state.updateMessage?.let { msg ->
                        Spacer(Modifier.height(8.dp))
                        LtrCodeBlock(text = msg)
                    }
                    state.updateDownloadProgress?.let { pct ->
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                    if (state.availableRelease != null && state.updateDownloadProgress == null) {
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onInstallUpdate, modifier = Modifier.fillMaxWidth()) {
                            Text(if (ar) "تنزيل وتثبيت التحديث" else "Download & Install Update")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (ar) "تراكب تصحيح الأخطاء" else "Developer Debug Overlay",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = state.developerDebugOverlay,
                            onCheckedChange = onToggleDeveloperDebugOverlay
                        )
                    }
                }
            }
        }
    }
}
