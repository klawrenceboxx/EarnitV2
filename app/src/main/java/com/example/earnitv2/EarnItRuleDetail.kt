package com.example.earnitv2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal enum class RuleDetailTone {
    Active,
    Paused
}

internal enum class RuleDetailOverflowAction {
    Edit,
    Pause,
    Delete
}

internal data class RuleDetailStatusCardState(
    val title: String,
    val metric: String,
    val stateLabel: String?,
    val body: String,
    val tone: RuleDetailTone,
    val showResume: Boolean
)

@Composable
fun EarnItRuleDetail(
    homeRule: HomeRuleUiState,
    detail: RuleDetailUiState,
    permissionState: PermissionSetupUiState,
    onBack: () -> Unit,
    onOpenEarnApp: (String) -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onEditRule: (EarnItRuleStore.Rule) -> Unit,
    onToggleRuleEnabled: (EarnItRuleStore.Rule) -> Unit,
    onDeleteRule: (EarnItRuleStore.Rule) -> Unit,
    modifier: Modifier = Modifier
) {
    val rule = homeRule.rule
    val statusState = ruleDetailStatusCardState(
        rule = rule,
        availableRewardTimeLabel = detail.card.availableRewardTimeLabel,
        isActiveNow = rule.enabled && rule.isActiveNow()
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        RuleDetailTopBar(
            rule = rule,
            onBack = onBack,
            onEditRule = onEditRule,
            onToggleRuleEnabled = onToggleRuleEnabled,
            onDeleteRule = onDeleteRule
        )

        if (permissionState.needsAttention) {
            RuleDetailAttention(
                permissionState = permissionState,
                onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings
            )
        }

        RuleStatusCard(
            state = statusState,
            onResume = { onToggleRuleEnabled(rule) }
        )

        when (rule.type) {
            EarnItRuleStore.RuleType.EarnRewardTime -> EarnRewardTimeDetailCard(
                rule = rule,
                detail = detail,
                productiveUsageSeconds = homeRulePrimaryUsageSeconds(homeRule),
                onOpenEarnApp = onOpenEarnApp
            )
            EarnItRuleStore.RuleType.CompleteToUnlock -> CompleteToUnlockDetailCard(
                rule = rule,
                rewardApps = detail.card.rewardApps,
                onOpenRequirementApp = onOpenEarnApp
            )
            EarnItRuleStore.RuleType.ScheduledBlock -> ScheduledBlockAppsCard(
                rule = rule,
                apps = detail.card.rewardApps
            )
        }

        RuleScheduleCard(rule = rule)

        if (ruleDetailShowsScheduledBlockPrecedenceNote(rule)) {
            RuleInfoNote(
                text = "This Rule takes priority over any Reward Time you've earned.",
                tone = statusState.tone
            )
        }
    }
}

@Composable
private fun RuleDetailTopBar(
    rule: EarnItRuleStore.Rule,
    onBack: () -> Unit,
    onEditRule: (EarnItRuleStore.Rule) -> Unit,
    onToggleRuleEnabled: (EarnItRuleStore.Rule) -> Unit,
    onDeleteRule: (EarnItRuleStore.Rule) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) {
            Text(text = "< Back")
        }
        Text(text = "Rule Detail", style = MaterialTheme.typography.headlineSmall)
        RuleDetailOverflowMenu(
            actions = ruleDetailOverflowActions(rule),
            onEdit = { onEditRule(rule) },
            onPause = { onToggleRuleEnabled(rule) },
            onDelete = { onDeleteRule(rule) }
        )
    }
}

@Composable
private fun RuleDetailOverflowMenu(
    actions: List<RuleDetailOverflowAction>,
    onEdit: () -> Unit,
    onPause: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(text = "...")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            actions.forEach { action ->
                val destructive = action == RuleDetailOverflowAction.Delete
                DropdownMenuItem(
                    text = {
                        Text(
                            text = ruleDetailOverflowActionLabel(action),
                            color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified
                        )
                    },
                    onClick = {
                        expanded = false
                        when (action) {
                            RuleDetailOverflowAction.Edit -> onEdit()
                            RuleDetailOverflowAction.Pause -> onPause()
                            RuleDetailOverflowAction.Delete -> onDelete()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun RuleDetailAttention(
    permissionState: PermissionSetupUiState,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    SectionContainer {
        Text(text = "EarnIt needs attention", style = MaterialTheme.typography.titleSmall)
        Text(
            text = permissionState.repairTargetLabels.joinToString(" and ") + " needs setup",
            style = MaterialTheme.typography.bodyMedium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (permissionState.earningProgressStatus == EarnItPermissionStatus.NeedsAttention) {
                Button(onClick = onOpenUsageAccessSettings) {
                    Text(text = "Fix earning")
                }
            }
            if (permissionState.appBlockingStatus == EarnItPermissionStatus.NeedsAttention) {
                Button(onClick = onOpenAccessibilitySettings) {
                    Text(text = "Fix blocking")
                }
            }
        }
    }
}

@Composable
private fun RuleStatusCard(
    state: RuleDetailStatusCardState,
    onResume: () -> Unit
) {
    val accent = ruleDetailAccentColor(state.tone)
    SectionContainer(borderColor = accent) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            StatusGlyph(tone = state.tone)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = state.title, style = MaterialTheme.typography.headlineSmall, color = accent)
                Text(text = state.metric, style = MaterialTheme.typography.titleLarge)
                if (state.stateLabel != null) {
                    Text(text = state.stateLabel, style = MaterialTheme.typography.bodyLarge, color = accent)
                }
                Text(
                    text = state.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (state.showResume) {
            Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Resume Rule")
            }
        }
    }
}

@Composable
private fun StatusGlyph(tone: RuleDetailTone) {
    val accent = ruleDetailAccentColor(tone)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(accent.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (tone == RuleDetailTone.Paused) "||" else "OK",
            style = MaterialTheme.typography.titleLarge,
            color = accent
        )
    }
}

@Composable
private fun EarnRewardTimeDetailCard(
    rule: EarnItRuleStore.Rule,
    detail: RuleDetailUiState,
    productiveUsageSeconds: Long?,
    onOpenEarnApp: (String) -> Unit
) {
    SectionContainer(title = "Earn Reward Time") {
        rule.earnApps.forEach { app ->
            AppActionRow(
                app = app.toUiState(),
                secondaryText = earnAppProgressLabel(productiveUsageSeconds, rule.earnApps.size),
                progress = earnAppProgress(productiveUsageSeconds, rule.earnApps.size),
                onOpen = { onOpenEarnApp(app.packageName) }
            )
        }
        Text(
            text = earnRewardExchangeCopy(rule),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        AppIconRow(
            title = "Reward Time works for",
            apps = detail.card.rewardApps,
            body = "Use Reward Time on these apps."
        )
    }
}

@Composable
private fun CompleteToUnlockDetailCard(
    rule: EarnItRuleStore.Rule,
    rewardApps: List<EarnItAppUiState>,
    onOpenRequirementApp: (String) -> Unit
) {
    SectionContainer(title = "Complete to unlock") {
        rule.requirements.forEach { requirement ->
            AppActionRow(
                app = requirement.app.toUiState(),
                secondaryText = requirementDurationLabel(requirement),
                progress = null,
                onOpen = { onOpenRequirementApp(requirement.app.packageName) }
            )
        }
        Text(
            text = "Complete all requirements to unlock the apps below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        AppIconRow(
            title = "Unlocks",
            apps = rewardApps,
            body = "These apps unlock after all requirements are completed."
        )
    }
}

@Composable
private fun ScheduledBlockAppsCard(
    rule: EarnItRuleStore.Rule,
    apps: List<EarnItAppUiState>
) {
    SectionContainer(title = "Blocked Apps") {
        AppIconRow(
            title = null,
            apps = apps,
            body = if (rule.enabled) {
                "These apps are blocked during your scheduled block time."
            } else {
                "These apps will be blocked when this Rule is resumed and active."
            }
        )
    }
}

@Composable
private fun RuleScheduleCard(rule: EarnItRuleStore.Rule) {
    val title = if (rule.type == EarnItRuleStore.RuleType.ScheduledBlock) "Block Schedule" else "Schedule"
    SectionContainer {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(ruleDetailAccentColor(if (rule.enabled) RuleDetailTone.Active else RuleDetailTone.Paused).copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Cal", style = MaterialTheme.typography.labelSmall)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                ruleDetailScheduleLines(rule).forEachIndexed { index, line ->
                    Text(
                        text = line,
                        style = if (index == 0) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                        color = if (index == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (rule.type == EarnItRuleStore.RuleType.ScheduledBlock) {
                    Text(
                        text = "Blocking is active during this time window.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleInfoNote(text: String, tone: RuleDetailTone) {
    SectionContainer(borderColor = ruleDetailAccentColor(tone).copy(alpha = 0.6f)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "i", style = MaterialTheme.typography.titleMedium, color = ruleDetailAccentColor(tone))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AppActionRow(
    app: EarnItAppUiState,
    secondaryText: String,
    progress: Float?,
    onOpen: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EarnItAppIcon(packageName = app.packageName, appName = app.name, size = 40.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = secondaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(onClick = onOpen) {
                Text(text = "Open")
            }
        }
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AppIconRow(
    title: String?,
    apps: List<EarnItAppUiState>,
    body: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            apps.take(6).forEach { app ->
                EarnItAppIcon(packageName = app.packageName, appName = app.name, size = 44.dp)
            }
            if (apps.size > 6) {
                DetailCountTile(count = apps.size - 6)
            }
        }
        Text(
            text = apps.joinToString(", ") { it.name },
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionContainer(
    modifier: Modifier = Modifier,
    title: String? = null,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (title != null) {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
            }
            content()
        }
    }
}

@Composable
private fun DetailCountTile(count: Int) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "+$count", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ruleDetailAccentColor(tone: RuleDetailTone): Color {
    return when (tone) {
        RuleDetailTone.Active -> Color(0xFF68C45B)
        RuleDetailTone.Paused -> Color(0xFFFFA0A5)
    }
}

internal fun ruleDetailOverflowActions(rule: EarnItRuleStore.Rule): List<RuleDetailOverflowAction> {
    return buildList {
        add(RuleDetailOverflowAction.Edit)
        if (rule.enabled) add(RuleDetailOverflowAction.Pause)
        add(RuleDetailOverflowAction.Delete)
    }
}

internal fun ruleDetailOverflowActionLabel(action: RuleDetailOverflowAction): String {
    return when (action) {
        RuleDetailOverflowAction.Edit -> "Edit Rule"
        RuleDetailOverflowAction.Pause -> "Pause Rule"
        RuleDetailOverflowAction.Delete -> "Delete Rule"
    }
}

internal fun ruleDetailStatusCardState(
    rule: EarnItRuleStore.Rule,
    availableRewardTimeLabel: String,
    isActiveNow: Boolean
): RuleDetailStatusCardState {
    if (!rule.enabled) {
        return RuleDetailStatusCardState(
            title = "Rule paused",
            metric = pausedStatusMetric(rule, availableRewardTimeLabel),
            stateLabel = null,
            body = pausedStatusBody(rule),
            tone = RuleDetailTone.Paused,
            showResume = true
        )
    }
    return when (rule.type) {
        EarnItRuleStore.RuleType.EarnRewardTime -> RuleDetailStatusCardState(
            title = "Rule active",
            metric = availableRewardTimeLabel,
            stateLabel = null,
            body = "Keep using your Earn Apps to build more Reward Time.",
            tone = RuleDetailTone.Active,
            showResume = false
        )
        EarnItRuleStore.RuleType.CompleteToUnlock -> RuleDetailStatusCardState(
            title = "Rule active",
            metric = requirementSummaryLabel(rule.requirements.size),
            stateLabel = null,
            body = completeToUnlockStatusBody(rule.requirements.size),
            tone = RuleDetailTone.Active,
            showResume = false
        )
        EarnItRuleStore.RuleType.ScheduledBlock -> RuleDetailStatusCardState(
            title = if (isActiveNow) "Blocking now" else "Not blocking now",
            metric = rule.scheduleLabel,
            stateLabel = if (isActiveNow) "Within block schedule" else "Outside block schedule",
            body = if (isActiveNow) {
                "Restricted apps are blocked right now."
            } else {
                "You're currently outside your block schedule."
            },
            tone = if (isActiveNow) RuleDetailTone.Active else RuleDetailTone.Paused,
            showResume = false
        )
    }
}

internal fun earnRewardExchangeCopy(rule: EarnItRuleStore.Rule): String {
    return if (rule.earnApps.size > 1) {
        "Every 10 min across selected Earn Apps earns ${rule.rewardSecondsPerProductiveSecond} min Reward Time."
    } else {
        EarnItUiFormatters.exchangeSummary(rule.rewardSecondsPerProductiveSecond)
    }
}

internal fun ruleDetailSectionTitles(rule: EarnItRuleStore.Rule): List<String> {
    return buildList {
        add("Status")
        add(
            when (rule.type) {
                EarnItRuleStore.RuleType.EarnRewardTime -> "Earn Reward Time"
                EarnItRuleStore.RuleType.CompleteToUnlock -> "Complete to unlock"
                EarnItRuleStore.RuleType.ScheduledBlock -> "Blocked Apps"
            }
        )
        add(if (rule.type == EarnItRuleStore.RuleType.ScheduledBlock) "Block Schedule" else "Schedule")
        if (ruleDetailShowsScheduledBlockPrecedenceNote(rule)) add("Priority note")
    }
}

internal fun ruleDetailShowsScheduledBlockPrecedenceNote(rule: EarnItRuleStore.Rule): Boolean {
    return rule.type == EarnItRuleStore.RuleType.ScheduledBlock
}

internal fun requirementDurationLabel(requirement: EarnItRuleStore.RuleRequirement): String {
    return "${(requirement.requiredSeconds / 60L).coerceAtLeast(1L)} min required"
}

internal fun earnAppProgressLabel(productiveUsageSeconds: Long?, earnAppCount: Int): String {
    if (productiveUsageSeconds == null || earnAppCount != 1) return "Selected Earn App"
    val progressMinutes = (productiveUsageSeconds.coerceAtLeast(0L) % TEN_MINUTES_SECONDS) / 60L
    return "$progressMinutes / 10 min toward next reward"
}

internal fun earnAppProgress(productiveUsageSeconds: Long?, earnAppCount: Int): Float? {
    if (productiveUsageSeconds == null || earnAppCount != 1) return null
    return ((productiveUsageSeconds.coerceAtLeast(0L) % TEN_MINUTES_SECONDS).toFloat() / TEN_MINUTES_SECONDS).coerceIn(0f, 1f)
}

private fun pausedStatusMetric(rule: EarnItRuleStore.Rule, availableRewardTimeLabel: String): String {
    return when (rule.type) {
        EarnItRuleStore.RuleType.EarnRewardTime -> availableRewardTimeLabel
        EarnItRuleStore.RuleType.CompleteToUnlock -> requirementSummaryLabel(rule.requirements.size)
        EarnItRuleStore.RuleType.ScheduledBlock -> "Not blocking now"
    }
}

private fun pausedStatusBody(rule: EarnItRuleStore.Rule): String {
    return when (rule.type) {
        EarnItRuleStore.RuleType.EarnRewardTime -> "Resume to continue earning Reward Time."
        EarnItRuleStore.RuleType.CompleteToUnlock -> "Resume to continue this rule today."
        EarnItRuleStore.RuleType.ScheduledBlock -> "Resume to reactivate this Rule."
    }
}

private fun completeToUnlockStatusBody(requirementCount: Int): String {
    return if (requirementCount == 1) {
        "Complete your requirement to unlock the apps below."
    } else {
        "Complete your requirements to unlock the apps below."
    }
}

private fun ruleDetailScheduleLines(rule: EarnItRuleStore.Rule): List<String> {
    return if (rule.type == EarnItRuleStore.RuleType.ScheduledBlock) {
        EarnItRuleStore.scheduleDetailLines(rule.activeDays, rule.effectiveTimeWindows)
    } else {
        listOf(rule.scheduleLabel)
    }
}

private fun homeRulePrimaryUsageSeconds(homeRule: HomeRuleUiState): Long? {
    return homeRule.card.productiveUsageLabel?.substringBefore(" min")?.toLongOrNull()?.times(60L)
}

private fun EarnItRuleStore.RuleApp.toUiState(): EarnItAppUiState {
    return EarnItAppUiState(packageName = packageName, name = name)
}

private const val TEN_MINUTES_SECONDS = 10 * 60L
