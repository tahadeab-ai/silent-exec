package com.silentexec.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.text.BidiFormatter
import com.silentexec.android.ui.AgentUiState
import com.silentexec.android.ui.TraceItem

private fun wrapBidi(text: String): String = BidiFormatter.getInstance().unicodeWrap(text)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreen(
    state: AgentUiState,
    onOpenModels: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onSendGoal: (String) -> Unit,
    onStop: () -> Unit,
    onVoiceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ar = state.isArabic
    val listState = rememberLazyListState()

    LaunchedEffect(state.traceItems.size) {
        if (state.traceItems.isNotEmpty()) {
            listState.animateScrollToItem(state.traceItems.size - 1)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Compact Top Status Bar: Provider Pill + Accessibility Badge (non-crowding RTL-safe spacing)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onOpenModels,
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = wrapBidi(state.activeProviderLabel),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                onClick = onOpenAccessibility,
                shape = RoundedCornerShape(20.dp),
                color = if (state.accessibilityEnabled) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                },
                modifier = Modifier.wrapContentWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessibilityNew,
                        contentDescription = if (ar) "إمكانية الوصول" else "Accessibility",
                        tint = if (state.accessibilityEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = if (state.accessibilityEnabled) {
                            if (ar) "مفعّل" else "Ready"
                        } else {
                            if (ar) "تفعيل" else "Enable"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        color = if (state.accessibilityEnabled) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
            }
        }

        // Chat & Live Action Trace
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.traceItems.isEmpty()) {
                item {
                    EmptyAgentHero(isArabic = ar)
                }
            }
            items(state.traceItems, key = { it.id }) { item ->
                TraceItemCard(item = item, isArabic = ar)
            }
        }

        // Bottom Composer
        Surface(
            tonalElevation = 3.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledIconButton(
                    onClick = onVoiceClick,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (state.isListening) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = if (ar) "إدخال صوتي" else "Voice input"
                    )
                }

                OutlinedTextField(
                    value = state.composerText,
                    onValueChange = { state.composerText = it },
                    placeholder = {
                        Text(
                            if (ar) "اطلب من الوكيل تنفيذ مهمة على جهازك..."
                            else "Ask SilentExec to operate your phone..."
                        )
                    },
                    singleLine = false,
                    maxLines = 3,
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (!state.isRunning && state.composerText.isNotBlank()) {
                                onSendGoal(state.composerText)
                            }
                        }
                    ),
                    modifier = Modifier.weight(1f)
                )

                if (state.isRunning) {
                    FilledIconButton(
                        onClick = onStop,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = if (ar) "إيقاف" else "Stop"
                        )
                    }
                } else {
                    FilledIconButton(
                        onClick = {
                            if (state.composerText.isNotBlank()) {
                                onSendGoal(state.composerText)
                            }
                        },
                        enabled = state.composerText.isNotBlank(),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = if (ar) "إرسال" else "Send"
                        )
                    }
                }
            }
        }

        // 3-Button Approval / Takeover Bottom Sheet
        state.activeTakeOverPrompt?.let { prompt ->
            ModalBottomSheet(
                onDismissRequest = { prompt.onCancel() }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = prompt.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = wrapBidi(prompt.reason),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { prompt.onApprove() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (ar) "تأكيد" else "Approve")
                        }
                        FilledTonalButton(
                            onClick = { prompt.onTakeOver() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (ar) "تولي التحكم" else "Take Over")
                        }
                        OutlinedButton(
                            onClick = { prompt.onCancel() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (ar) "إلغاء" else "Cancel")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun EmptyAgentHero(isArabic: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, bottom = 24.dp, start = 12.dp, end = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(44.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = if (isArabic) "SilentExec v3" else "SilentExec v3",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (isArabic) {
                "وكيل تحكم فعلي بالشاشة يقرأ عناصر الواجهة وينفذ خطواتك بأمان."
            } else {
                "Real Accessibility screen-control agent that observes, reasons, and acts safely."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TechnicalDetailsAccordion(
    technicalDetails: String?,
    isArabic: Boolean
) {
    if (technicalDetails.isNullOrBlank()) return
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (isArabic) "التفاصيل التقنية والسجل" else "Technical Details & Log",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
        AnimatedVisibility(visible = expanded) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = technicalDetails,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TraceItemCard(item: TraceItem, isArabic: Boolean) {
    when (item) {
        is TraceItem.UserGoal -> {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(start = 40.dp)
                ) {
                    Text(
                        text = wrapBidi(item.text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }

        is TraceItem.Observation -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            val pkg = wrapBidi(item.packageName.substringAfterLast('.').ifBlank { "screen" })
                            val headline = when {
                                item.screenChanged == false ->
                                    if (isArabic) "⚠️ لم تستجب الشاشة — جارٍ المحاولة البديلة" else "⚠️ Screen unchanged — trying fallback"
                                isArabic -> "✓ تم رصد ${item.elementCount} عنصراً ($pkg)"
                                else -> "✓ Observed ${item.elementCount} elements ($pkg)"
                            }
                            Text(
                                text = headline,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        item.elapsedMs?.let { ms ->
                            Text(
                                text = wrapBidi("${ms}ms"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TechnicalDetailsAccordion(item.technicalDetails, isArabic)
                }
            }
        }

        is TraceItem.Reasoning -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            val stepTitle = if (isArabic) {
                                "⏳ ${item.thought.ifBlank { wrapBidi(item.actionWire) }}"
                            } else {
                                "⏳ ${item.thought.ifBlank { wrapBidi(item.actionWire) }}"
                            }
                            Text(
                                text = wrapBidi(stepTitle),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        item.elapsedMs?.let { ms ->
                            Text(
                                text = wrapBidi("${ms}ms"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    val detailText = buildString {
                        append(item.actionWire)
                        if (item.target.isNotBlank()) append(" -> ${item.target}")
                        if (!item.technicalDetails.isNullOrBlank()) {
                            append("\n")
                            append(item.technicalDetails)
                        }
                    }
                    TechnicalDetailsAccordion(detailText, isArabic)
                }
            }
        }

        is TraceItem.ActionResult -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (item.success) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = if (item.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = item.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        is TraceItem.Confirmation -> {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFF59E0B).copy(alpha = 0.14f),
                border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isArabic) "تأكيد إجراء حساس" else "Confirm Sensitive Action",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = item.reason,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (!item.resolved) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { item.onRespond(true) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text(if (isArabic) "موافقة" else "Approve")
                            }
                            OutlinedButton(onClick = { item.onRespond(false) }) {
                                Text(if (isArabic) "إلغاء" else "Cancel")
                            }
                        }
                    } else {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (item.approved == true) {
                                if (isArabic) "✓ تمت الموافقة" else "✓ Approved"
                            } else {
                                if (isArabic) "✕ تم الإلغاء" else "✕ Cancelled"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        is TraceItem.Summary -> {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        is TraceItem.Error -> {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.65f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = wrapBidi(
                                if (item.message.contains("<think>", ignoreCase = true) || item.message.length > 140) {
                                    if (isArabic) "⚠️ تعذر إكمال الخطوة — راجع التفاصيل التقنية" else "⚠️ Step interrupted — see technical details"
                                } else {
                                    item.message
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    TechnicalDetailsAccordion(
                        item.technicalDetails ?: item.message.takeIf { it.contains("<think>", ignoreCase = true) || it.length > 140 },
                        isArabic
                    )
                }
            }
        }
    }
}
