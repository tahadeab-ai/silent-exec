package com.silentexec.android.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.silentexec.android.ui.AgentUiState
import com.silentexec.android.ui.TraceItem
import com.silentexec.android.ui.components.ApprovalBanner
import com.silentexec.android.ui.components.LtrCodeBlock
import com.silentexec.android.ui.components.LtrTechnicalBanner
import com.silentexec.android.ui.components.PlanCard
import com.silentexec.android.ui.components.ReasoningAccordion
import com.silentexec.android.ui.components.ToolCallCard
import com.silentexec.android.ui.theme.SilentExecColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AgentScreen(
    state: AgentUiState,
    onOpenModels: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onNewSession: () -> Unit,
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Status Bar: Model Chip + Accessibility & Shizuku Dots + New Session
        Surface(
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onOpenModels,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        LtrTechnicalBanner(
                            text = state.activeProviderLabel.ifBlank { "Qwen3.5-2B" },
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Accessibility + Shizuku status dots
                Surface(
                    onClick = onOpenAccessibility,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatusDot(active = state.accessibilityEnabled)
                        Text(
                            text = "A11y",
                            style = MaterialTheme.typography.labelMedium
                        )
                        StatusDot(active = state.shizukuAuthorized)
                        Text(
                            text = "Shizuku",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                IconButton(
                    onClick = onNewSession,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddComment,
                        contentDescription = if (ar) "جلسة جديدة" else "New Session",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Main Conversation / Execution Stream
        Box(modifier = Modifier.weight(1f)) {
            if (state.traceItems.isEmpty()) {
                EmptyAgentState(
                    isArabic = ar,
                    onQuickAction = onSendGoal,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (state.planSteps.isNotEmpty()) {
                        item(key = "live_plan_card") {
                            PlanCard(steps = state.planSteps, isArabic = ar)
                        }
                    }

                    items(state.traceItems, key = { it.id }) { item ->
                        when (item) {
                            is TraceItem.UserGoal -> UserGoalBubble(item.text)
                            is TraceItem.Observation -> ObservationPill(item, ar)
                            is TraceItem.Reasoning -> {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (item.thought.isNotBlank()) {
                                        ReasoningAccordion(
                                            step = item.step,
                                            thought = item.thought,
                                            isArabic = ar
                                        )
                                    }
                                    ToolCallCard(
                                        step = item.step,
                                        toolName = item.actionWire,
                                        target = item.target,
                                        elapsedMs = item.elapsedMs,
                                        success = null,
                                        details = item.technicalDetails,
                                        isArabic = ar
                                    )
                                }
                            }
                            is TraceItem.ActionResult -> ToolCallCard(
                                step = item.step,
                                toolName = if (item.success) "OK" else "ERR",
                                target = item.message,
                                elapsedMs = item.elapsedMs,
                                success = item.success,
                                details = item.technicalDetails,
                                isArabic = ar
                            )
                            is TraceItem.Confirmation -> {
                                if (item.resolved) {
                                    LtrTechnicalBanner(
                                        text = if (item.approved == true) "Step ${item.step}: Approved" else "Step ${item.step}: Denied",
                                        contentColor = if (item.approved == true) SilentExecColors.Success else SilentExecColors.Error
                                    )
                                }
                            }
                            is TraceItem.Summary -> SummaryCard(item.text, item.stepsTaken, ar)
                            is TraceItem.Error -> ErrorAccordionCard(item.message, item.technicalDetails, ar)
                        }
                    }

                    state.activeTakeOverPrompt?.let { prompt ->
                        item(key = "inline_approval") {
                            ApprovalBanner(prompt = prompt, isArabic = ar)
                        }
                    }
                }
            }
        }

        // Floating Bottom Composer Bar
        Surface(
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onVoiceClick) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = if (ar) "إدخال صوتي" else "Voice Input",
                        tint = if (state.isListening) SilentExecColors.Error else MaterialTheme.colorScheme.primary
                    )
                }
                OutlinedTextField(
                    value = state.composerText,
                    onValueChange = { state.composerText = it },
                    placeholder = {
                        Text(
                            text = if (ar) "اطلب مهمة من الوكيل..." else "Ask SilentExec to act...",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (!state.isRunning && state.composerText.isNotBlank()) {
                            onSendGoal(state.composerText)
                        }
                    }),
                    modifier = Modifier.weight(1f)
                )

                AnimatedContent(targetState = state.isRunning, label = "send_stop_morph") { running ->
                    if (running) {
                        FilledIconButton(
                            onClick = onStop,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = SilentExecColors.Error
                            )
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = if (ar) "إيقاف" else "Stop")
                        }
                    } else {
                        FilledIconButton(
                            onClick = { onSendGoal(state.composerText) },
                            enabled = state.composerText.isNotBlank(),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = if (ar) "إرسال" else "Send"
                            )
                        }
                    }
                }
            }
        }
    }

    // Human-in-the-Loop Takeover / Approval Modal Bottom Sheet
    state.activeTakeOverPrompt?.let { prompt ->
        ModalBottomSheet(
            onDismissRequest = prompt.onCancel,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = SilentExecColors.Warning
                    )
                    Text(
                        text = prompt.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                LtrCodeBlock(text = prompt.reason, borderColor = SilentExecColors.Warning)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = prompt.onApprove,
                        colors = ButtonDefaults.buttonColors(containerColor = SilentExecColors.Success),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (ar) "استئناف" else "Resume")
                    }
                    OutlinedButton(
                        onClick = prompt.onTakeOver,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (ar) "تولي التحكم" else "Take Over")
                    }
                    OutlinedButton(
                        onClick = prompt.onCancel,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (ar) "إلغاء" else "Cancel", color = SilentExecColors.Error)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun StatusDot(active: Boolean) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(if (active) SilentExecColors.Success else SilentExecColors.Warning)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyAgentState(
    isArabic: Boolean,
    onQuickAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val chips = if (isArabic) {
        listOf(
            "افتح اليوتيوب وابحث عن NLP",
            "افتح الإعدادات وفعّل Wi-Fi",
            "لخّص الشاشة الحالية",
            "تحكم بالحاسوب عبر الجسر"
        )
    } else {
        listOf(
            "Open YouTube and search NLP",
            "Open Settings and toggle Wi-Fi",
            "Summarize current screen",
            "Control desktop via LAN Bridge"
        )
    }

    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(44.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (isArabic) "SilentExec — الوكيل الذكي المستقل" else "SilentExec — Autonomous Edge Agent",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (isArabic) "اختر إجراءً سريعاً أو اكتب مهمتك متعددة الخطوات" else "Tap a quick action or type your multi-step task",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            chips.forEach { label ->
                AssistChip(
                    onClick = { onQuickAction(label) },
                    label = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                )
            }
        }
    }
}

@Composable
private fun UserGoalBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.wrapContentWidth()
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun ObservationPill(item: TraceItem.Observation, isArabic: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.Visibility,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = if (isArabic) "رصد ${item.elementCount} عنصراً" else "Observed ${item.elementCount} nodes",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (item.packageName.isNotBlank()) {
            LtrTechnicalBanner(text = item.packageName)
        }
    }
}

@Composable
private fun SummaryCard(text: String, stepsTaken: Int, isArabic: Boolean) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        border = BorderStroke(1.dp, SilentExecColors.Success.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = SilentExecColors.Success
                )
                Text(
                    text = if (isArabic) "اكتملت المهمة ($stepsTaken خطوات)" else "Task Completed ($stepsTaken steps)",
                    style = MaterialTheme.typography.titleMedium,
                    color = SilentExecColors.Success
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun ErrorAccordionCard(message: String, technicalDetails: String?, isArabic: Boolean) {
    ToolCallCard(
        step = 0,
        toolName = "ERROR",
        target = if (isArabic) "تعذر إكمال الخطوة" else "Step failed",
        elapsedMs = null,
        success = false,
        details = listOfNotNull(message, technicalDetails).joinToString("\n"),
        isArabic = isArabic
    )
}
