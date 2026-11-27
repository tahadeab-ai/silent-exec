package com.silentexec.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.silentexec.android.exec.PlanStep
import com.silentexec.android.exec.PlanStepStatus
import com.silentexec.android.ui.TakeOverPromptState
import com.silentexec.android.ui.theme.SilentExecColors

/**
 * Forces strict LTR layout & monospace rendering for code, JSON/XML payloads,
 * package names, latency badges, and English error logs so punctuation never flips in RTL.
 */
@Composable
fun LtrCodeBlock(
    text: String,
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.colorScheme.outline
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = SilentExecColors.CanvasDark,
            border = BorderStroke(1.dp, borderColor.copy(alpha = 0.7f)),
            modifier = modifier.fillMaxWidth()
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    textDirection = TextDirection.Ltr
                ),
                color = SilentExecColors.TextPrimaryDark,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            )
        }
    }
}

/**
 * Compact LTR badge/banner for latency (`<15ms`), package names, and technical identifiers.
 */
@Composable
fun LtrTechnicalBanner(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = containerColor,
            modifier = modifier
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    textDirection = TextDirection.Ltr
                ),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * AI Elements `PlanCard` — live numbered checklist from PlanManager with pulsing active step.
 */
@Composable
fun PlanCard(
    steps: List<PlanStep>,
    isArabic: Boolean,
    modifier: Modifier = Modifier
) {
    if (steps.isEmpty()) return
    val infiniteTransition = rememberInfiniteTransition(label = "plan_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (isArabic) "خطة التنفيذ (${steps.count { it.status == PlanStepStatus.DONE }}/${steps.size})" else "Execution Plan (${steps.count { it.status == PlanStepStatus.DONE }}/${steps.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(10.dp))
            steps.forEachIndexed { idx, step ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    when (step.status) {
                        PlanStepStatus.DONE -> Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = SilentExecColors.Success,
                            modifier = Modifier.size(18.dp)
                        )
                        PlanStepStatus.IN_PROGRESS -> Box(
                            modifier = Modifier
                                .size(18.dp)
                                .padding(2.dp)
                                .alpha(pulseAlpha)
                                .clip(CircleShape)
                                .background(SilentExecColors.PrimaryAccent)
                        )
                        PlanStepStatus.FAILED -> Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = SilentExecColors.Error,
                            modifier = Modifier.size(18.dp)
                        )
                        else -> Icon(
                            imageVector = Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "${idx + 1}. ${step.description}",
                        style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                        color = if (step.status == PlanStepStatus.DONE) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }
}

/**
 * AI Elements `ToolCallCard` — collapsible tool execution card with LTR technical details.
 */
@Composable
fun ToolCallCard(
    step: Int,
    toolName: String,
    target: String,
    elapsedMs: Long?,
    success: Boolean?,
    details: String?,
    isArabic: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val statusColor = when (success) {
        true -> SilentExecColors.Success
        false -> SilentExecColors.Error
        null -> SilentExecColors.PrimaryAccent
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !details.isNullOrBlank()) { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (isArabic) "خطوة $step" else "Step $step",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LtrTechnicalBanner(
                    text = toolName,
                    containerColor = statusColor.copy(alpha = 0.14f),
                    contentColor = statusColor
                )
                if (elapsedMs != null) {
                    LtrTechnicalBanner(text = "${elapsedMs}ms")
                }
                Spacer(Modifier.weight(1f))
                if (!details.isNullOrBlank()) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (target.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = target,
                    style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (expanded) 10 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            AnimatedVisibility(visible = expanded && !details.isNullOrBlank()) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    LtrCodeBlock(
                        text = details.orEmpty(),
                        borderColor = statusColor
                    )
                }
            }
        }
    }
}

/**
 * AI Elements `ReasoningAccordion` — collapsible thought trace.
 */
@Composable
fun ReasoningAccordion(
    step: Int,
    thought: String,
    isArabic: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = SilentExecColors.PrimaryAccent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (isArabic) "تفكير الوكيل (خطوة $step)" else "Agent Reasoning (Step $step)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            AnimatedVisibility(visible = expanded) {
                Text(
                    text = thought,
                    style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

/**
 * AI Elements `ApprovalBanner` — inline Human-in-the-Loop confirmation banner.
 */
@Composable
fun ApprovalBanner(
    prompt: TakeOverPromptState,
    isArabic: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, SilentExecColors.Warning),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = SilentExecColors.Warning,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = prompt.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = SilentExecColors.Warning
                )
            }
            Spacer(Modifier.height(6.dp))
            LtrCodeBlock(text = prompt.reason, borderColor = SilentExecColors.Warning)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = prompt.onApprove,
                    colors = ButtonDefaults.buttonColors(containerColor = SilentExecColors.Success)
                ) {
                    Text(if (isArabic) "استئناف" else "Resume")
                }
                OutlinedButton(onClick = prompt.onTakeOver) {
                    Text(if (isArabic) "تولي التحكم" else "Take Over")
                }
                OutlinedButton(onClick = prompt.onCancel) {
                    Text(if (isArabic) "إلغاء" else "Cancel", color = SilentExecColors.Error)
                }
            }
        }
    }
}
