package com.kaleel.earnitv2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal enum class RuleTypeIconIdentity {
    Sprout,
    Lock,
    Clock
}

internal enum class RuleTypeAccentRole {
    Green,
    Blue,
    Amber
}

internal data class RuleTypePresentation(
    val label: String,
    val iconIdentity: RuleTypeIconIdentity,
    val accentRole: RuleTypeAccentRole,
    val contentDescription: String
) {
    val accentColor: Color
        get() = when (accentRole) {
            RuleTypeAccentRole.Green -> Color(0xFF79C957)
            RuleTypeAccentRole.Blue -> Color(0xFF68B7FF)
            RuleTypeAccentRole.Amber -> Color(0xFFFFB84D)
        }

    val containerColor: Color
        get() = accentColor.copy(alpha = 0.16f)
}

internal fun ruleTypePresentation(ruleType: EarnItRuleStore.RuleType): RuleTypePresentation {
    return when (ruleType) {
        EarnItRuleStore.RuleType.EarnRewardTime -> RuleTypePresentation(
            label = "Earn Reward Time",
            iconIdentity = RuleTypeIconIdentity.Sprout,
            accentRole = RuleTypeAccentRole.Green,
            contentDescription = "Earn Reward Time Rule"
        )
        EarnItRuleStore.RuleType.CompleteToUnlock -> RuleTypePresentation(
            label = "Complete to Unlock",
            iconIdentity = RuleTypeIconIdentity.Lock,
            accentRole = RuleTypeAccentRole.Blue,
            contentDescription = "Complete to Unlock Rule"
        )
        EarnItRuleStore.RuleType.ScheduledBlock -> RuleTypePresentation(
            label = "Scheduled Block",
            iconIdentity = RuleTypeIconIdentity.Clock,
            accentRole = RuleTypeAccentRole.Amber,
            contentDescription = "Scheduled Block Rule"
        )
    }
}

@Composable
internal fun RuleTypeBadge(
    ruleType: EarnItRuleStore.RuleType,
    modifier: Modifier = Modifier,
    iconSize: Dp = 30.dp
) {
    val presentation = ruleTypePresentation(ruleType)
    Row(
        modifier = modifier.semantics { contentDescription = presentation.contentDescription },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RuleTypeIcon(
            presentation = presentation,
            size = iconSize,
            includeContentDescription = false
        )
        Text(
            text = presentation.label,
            style = MaterialTheme.typography.labelSmall,
            color = presentation.accentColor
        )
    }
}

@Composable
internal fun RuleTypeIcon(
    ruleType: EarnItRuleStore.RuleType,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    RuleTypeIcon(
        presentation = ruleTypePresentation(ruleType),
        modifier = modifier,
        size = size,
        includeContentDescription = true
    )
}

@Composable
private fun RuleTypeIcon(
    presentation: RuleTypePresentation,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    includeContentDescription: Boolean
) {
    val semanticModifier = if (includeContentDescription) {
        Modifier.semantics { contentDescription = presentation.contentDescription }
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .then(semanticModifier)
            .size(size)
            .clip(RoundedCornerShape(size / 2))
            .background(presentation.containerColor),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.58f)) {
            when (presentation.iconIdentity) {
                RuleTypeIconIdentity.Sprout -> drawSproutIcon(presentation.accentColor)
                RuleTypeIconIdentity.Lock -> drawLockIcon(presentation.accentColor)
                RuleTypeIconIdentity.Clock -> drawClockIcon(presentation.accentColor)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSproutIcon(color: Color) {
    val strokeWidth = size.minDimension * 0.11f
    drawLine(
        color = color,
        start = Offset(size.width * 0.5f, size.height * 0.9f),
        end = Offset(size.width * 0.5f, size.height * 0.46f),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )

    val leftLeaf = Path().apply {
        moveTo(size.width * 0.5f, size.height * 0.5f)
        quadraticTo(size.width * 0.18f, size.height * 0.16f, size.width * 0.12f, size.height * 0.44f)
        quadraticTo(size.width * 0.26f, size.height * 0.62f, size.width * 0.5f, size.height * 0.5f)
        close()
    }
    val rightLeaf = Path().apply {
        moveTo(size.width * 0.5f, size.height * 0.5f)
        quadraticTo(size.width * 0.82f, size.height * 0.16f, size.width * 0.88f, size.height * 0.44f)
        quadraticTo(size.width * 0.74f, size.height * 0.62f, size.width * 0.5f, size.height * 0.5f)
        close()
    }
    drawPath(path = leftLeaf, color = color)
    drawPath(path = rightLeaf, color = color)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLockIcon(color: Color) {
    val strokeWidth = size.minDimension * 0.1f
    drawArc(
        color = color,
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(size.width * 0.25f, size.height * 0.08f),
        size = Size(size.width * 0.5f, size.height * 0.56f),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width * 0.18f, size.height * 0.46f),
        size = Size(size.width * 0.64f, size.height * 0.42f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.08f, size.width * 0.08f),
        style = Stroke(width = strokeWidth)
    )
    drawLine(
        color = color,
        start = Offset(size.width * 0.5f, size.height * 0.61f),
        end = Offset(size.width * 0.5f, size.height * 0.73f),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawClockIcon(color: Color) {
    val strokeWidth = size.minDimension * 0.1f
    drawCircle(
        color = color,
        radius = size.minDimension * 0.4f,
        center = center,
        style = Stroke(width = strokeWidth)
    )
    drawLine(
        color = color,
        start = center,
        end = Offset(size.width * 0.5f, size.height * 0.28f),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    drawLine(
        color = color,
        start = center,
        end = Offset(size.width * 0.68f, size.height * 0.58f),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
}
