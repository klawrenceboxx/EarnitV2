package com.example.earnitv2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class HomeRuleUiState(
    val rule: EarnItRuleStore.Rule,
    val card: RuleCardUiState,
    val completeToUnlockProgress: CompleteToUnlockRuleProgressUiState?,
    val primaryText: String,
    val secondaryText: String?,
    val earnContextText: String?,
    val statusText: String
)

@Composable
fun EarnItHome(
    rules: List<HomeRuleUiState>,
    permissionState: PermissionSetupUiState,
    manageRulesOpen: Boolean,
    onAddRule: () -> Unit,
    onOpenEarnApp: (String) -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleManageRules: () -> Unit,
    onOpenRuleDetail: (String) -> Unit,
    onEditRule: (EarnItRuleStore.Rule) -> Unit,
    onToggleRuleEnabled: (EarnItRuleStore.Rule) -> Unit,
    onDeleteRule: (EarnItRuleStore.Rule) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        HomeTopBar(onOpenSettings = onOpenSettings)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (permissionState.needsAttention) {
                HomeAttentionBanner(
                    permissionState = permissionState,
                    onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings
                )
            }

            if (rules.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    HomeEmptyState(onAddRule = onAddRule)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    rules.forEach { homeRule ->
                        LiveRuleCard(
                            homeRule = homeRule,
                            onOpenRuleDetail = onOpenRuleDetail,
                            onOpenEarnApp = onOpenEarnApp
                        )
                    }
                }
                OutlinedButton(onClick = onAddRule, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "+ Add Rule")
                }
                TemporaryRuleManagement(
                    rules = rules.map { it.rule },
                    expanded = manageRulesOpen,
                    onToggleExpanded = onToggleManageRules,
                    onEditRule = onEditRule,
                    onToggleRuleEnabled = onToggleRuleEnabled,
                    onDeleteRule = onDeleteRule
                )
            }
        }
    }
}
fun homeRuleUiState(
    state: RuleDashboardState,
    usageAccessGranted: Boolean,
    appBlockingEnabled: Boolean
): HomeRuleUiState {
    val rule = state.rule
    val isActiveNow = rule.enabled && rule.isActiveNow()
    val card = EarnItUiStateAdapters.ruleCard(
        rule = rule,
        productiveUsageSeconds = state.productiveUsageSeconds,
        remainingRewardSeconds = state.remainingRewardSeconds,
        usageAccessGranted = usageAccessGranted,
        appBlockingEnabled = appBlockingEnabled,
        isActiveNow = isActiveNow
    )
    val completeToUnlockProgress = if (rule.type == EarnItRuleStore.RuleType.CompleteToUnlock) {
        completeToUnlockProgressUiState(rule, state.requirementProgressSeconds)
    } else {
        null
    }
    val primaryText = homePrimaryText(rule, card, state.remainingRewardSeconds, isActiveNow, completeToUnlockProgress)
    val secondaryText = homeSecondaryText(rule, state.remainingRewardSeconds, isActiveNow, completeToUnlockProgress)
    val statusText = when {
        card.attentionLabel != null -> "Protection needs attention"
        !rule.enabled -> "Available if resumed today"
        rule.type == EarnItRuleStore.RuleType.CompleteToUnlock && !isActiveNow -> card.scheduleStatusLabel
        rule.type == EarnItRuleStore.RuleType.CompleteToUnlock -> homeCompleteStatusText(rule, completeToUnlockProgress)
        rule.type == EarnItRuleStore.RuleType.ScheduledBlock -> homeScheduledBlockStatusText(isActiveNow)
        else -> card.scheduleStatusLabel
    }
    return HomeRuleUiState(
        rule = rule,
        card = card,
        completeToUnlockProgress = completeToUnlockProgress,
        primaryText = primaryText,
        secondaryText = secondaryText,
        earnContextText = if (rule.type == EarnItRuleStore.RuleType.EarnRewardTime) {
            EarnItUiFormatters.exchangeSummary(rule.rewardSecondsPerProductiveSecond)
        } else {
            null
        },
        statusText = statusText
    )
}

private fun homePrimaryText(
    rule: EarnItRuleStore.Rule,
    card: RuleCardUiState,
    remainingRewardSeconds: Long,
    isActiveNow: Boolean,
    completeToUnlockProgress: CompleteToUnlockRuleProgressUiState?
): String {
    if (!rule.enabled) return "Rule paused"
    return when (rule.type) {
        EarnItRuleStore.RuleType.EarnRewardTime -> when {
            !isActiveNow -> "Unrestricted right now"
            remainingRewardSeconds <= 0L -> "No Reward Time"
            else -> card.availableRewardTimeLabel
        }
        EarnItRuleStore.RuleType.CompleteToUnlock -> {
            if (!isActiveNow) {
                "Scheduled"
            } else if (completeToUnlockProgress?.isUnlocked == true) {
                "Unlocked"
            } else {
                "Locked"
            }
        }
        EarnItRuleStore.RuleType.ScheduledBlock -> {
            if (isActiveNow) "Blocking now" else "Not blocking now"
        }
    }
}

private fun homeSecondaryText(
    rule: EarnItRuleStore.Rule,
    remainingRewardSeconds: Long,
    isActiveNow: Boolean,
    completeToUnlockProgress: CompleteToUnlockRuleProgressUiState?
): String? {
    return when (rule.type) {
        EarnItRuleStore.RuleType.EarnRewardTime -> when {
            !rule.enabled -> EarnItUiFormatters.savedRewardTime(remainingRewardSeconds)
            !isActiveNow -> EarnItUiFormatters.remainingRewardTime(remainingRewardSeconds)
            else -> "Active now"
        }
        EarnItRuleStore.RuleType.CompleteToUnlock -> {
            if (completeToUnlockProgress?.isUnlocked == true) {
                "Requirements complete"
            } else {
                requirementSummaryLabel(completeToUnlockProgress?.remainingRequirementCount ?: rule.requirements.size)
            }
        }
        EarnItRuleStore.RuleType.ScheduledBlock -> rule.scheduleLabel
    }
}

private fun homeCompleteStatusText(
    rule: EarnItRuleStore.Rule,
    completeToUnlockProgress: CompleteToUnlockRuleProgressUiState?
): String {
    return when {
        rule.requirements.isEmpty() -> "No requirements saved"
        completeToUnlockProgress?.isUnlocked == true -> "Requirements complete"
        else -> "Requirements incomplete"
    }
}

private fun homeScheduledBlockStatusText(isActiveNow: Boolean): String {
    return if (isActiveNow) {
        "Within block schedule"
    } else {
        "Outside block schedule"
    }
}

internal fun requirementSummaryLabel(count: Int): String {
    return when (count) {
        0 -> "No requirements"
        1 -> "1 requirement remaining"
        else -> "$count requirements remaining"
    }
}

internal fun earnRewardTimeHomeCardSemanticOrder(): List<String> {
    return listOf("Earn with", "Earn Apps", "Unlocks", "Reward Apps", "Exchange")
}

data class HomeRuleAppActionRowState(
    val packageName: String,
    val name: String,
    val supportingText: String?
)

fun earnRewardTimeEarnAppRows(card: RuleCardUiState, supportingText: String?): List<HomeRuleAppActionRowState> {
    return card.earnApps.map { app ->
        HomeRuleAppActionRowState(
            packageName = app.packageName,
            name = app.name,
            supportingText = supportingText
        )
    }
}

fun earnRewardTimeRewardAppRows(card: RuleCardUiState): List<HomeRuleAppActionRowState> {
    return card.rewardApps.map { app ->
        HomeRuleAppActionRowState(
            packageName = app.packageName,
            name = app.name,
            supportingText = null
        )
    }
}

@Composable
private fun HomeTopBar(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "EarnIt", style = MaterialTheme.typography.headlineSmall)
        TextButton(onClick = onOpenSettings) {
            Text(text = "Settings")
        }
    }
}

@Composable
private fun HomeAttentionBanner(
    permissionState: PermissionSetupUiState,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
}

@Composable
private fun HomeEmptyState(onAddRule: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(text = "No Rules yet", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Create a Rule to start earning access to your\nReward Apps.",
            style = MaterialTheme.typography.bodyMedium
        )
        Button(onClick = onAddRule) {
            Text(text = "Create First Rule")
        }
    }
}

@Composable
private fun LiveRuleCard(
    homeRule: HomeRuleUiState,
    onOpenRuleDetail: (String) -> Unit,
    onOpenEarnApp: (String) -> Unit
) {
    val card = homeRule.card
    val presentation = ruleTypePresentation(homeRule.rule.type)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenRuleDetail(homeRule.rule.id) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, presentation.accentColor.copy(alpha = 0.30f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            RuleCardHeader(ruleType = homeRule.rule.type)
            RuleCardStatus(
                primaryText = homeRule.primaryText,
                secondaryText = homeRule.secondaryText,
                accentColor = presentation.accentColor
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (homeRule.rule.type) {
                    EarnItRuleStore.RuleType.EarnRewardTime -> {
                        EarnAppRow(
                            rows = earnRewardTimeEarnAppRows(card, supportingText = null),
                            onOpenEarnApp = onOpenEarnApp
                        )
                        RuleAppSection(label = "Unlocks", rows = earnRewardTimeRewardAppRows(card), onOpenApp = onOpenEarnApp)
                        if (homeRule.earnContextText != null) {
                            Text(
                                text = homeRule.earnContextText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    EarnItRuleStore.RuleType.CompleteToUnlock -> {
                        RequirementsRows(
                            requirements = homeRule.completeToUnlockProgress?.incompleteRequirements.orEmpty(),
                            onOpenEarnApp = onOpenEarnApp
                        )
                        RuleAppSection(
                            label = "Unlocks",
                            rows = card.rewardApps.map { HomeRuleAppActionRowState(it.packageName, it.name, null) },
                            onOpenApp = onOpenEarnApp
                        )
                    }
                    EarnItRuleStore.RuleType.ScheduledBlock -> {
                        RuleAppSection(
                            label = "Blocked Apps",
                            rows = card.rewardApps.map { HomeRuleAppActionRowState(it.packageName, it.name, null) },
                            onOpenApp = onOpenEarnApp
                        )
                    }
                }
            }
            Text(
                text = homeRule.statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RuleCardHeader(ruleType: EarnItRuleStore.RuleType) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        RuleTypeBadge(ruleType = ruleType, iconSize = 32.dp)
        Text(
            text = ">",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
        )
    }
}

@Composable
private fun RuleCardStatus(
    primaryText: String,
    secondaryText: String?,
    accentColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = primaryText,
            style = MaterialTheme.typography.headlineSmall,
            color = accentColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (secondaryText != null) {
            Text(
                text = secondaryText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RequirementsRows(
    requirements: List<CompleteRequirementUiState>,
    onOpenEarnApp: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(text = "Requirements")
        requirements.take(2).forEach { requirement ->
            RuleAppActionRow(
                row = HomeRuleAppActionRowState(requirement.packageName, requirement.name, requirement.progressLabel),
                onOpenApp = onOpenEarnApp
            )
        }
        if (requirements.size > 2) {
            Text(
                text = "+${requirements.size - 2} more",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EarnAppRow(
    rows: List<HomeRuleAppActionRowState>,
    onOpenEarnApp: (String) -> Unit
) {
    RuleAppSection(
        label = "Earn with",
        rows = rows,
        onOpenApp = onOpenEarnApp
    )
}

@Composable
private fun RuleAppSection(
    label: String,
    rows: List<HomeRuleAppActionRowState>,
    onOpenApp: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(text = label)
        rows.forEach { row ->
            RuleAppActionRow(
                row = row,
                onOpenApp = onOpenApp
            )
        }
    }
}

@Composable
private fun RuleAppActionRow(
    row: HomeRuleAppActionRowState,
    onOpenApp: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EarnItAppIcon(packageName = row.packageName, appName = row.name, size = 30.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = row.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (row.supportingText != null) {
                Text(
                    text = row.supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        TextButton(
            onClick = { onOpenApp(row.packageName) },
            modifier = Modifier.widthIn(min = 64.dp)
        ) {
            Text(text = "Open")
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}

private fun temporaryRuleTitle(rule: EarnItRuleStore.Rule): String {
    return when (rule.type) {
        EarnItRuleStore.RuleType.EarnRewardTime -> rule.earnApps.firstOrNull()?.name ?: rule.productiveName
        EarnItRuleStore.RuleType.CompleteToUnlock -> "Complete to Unlock"
        EarnItRuleStore.RuleType.ScheduledBlock -> "Scheduled Block"
    }
}

private fun temporaryRuleSubtitle(rule: EarnItRuleStore.Rule): String {
    return when (rule.type) {
        EarnItRuleStore.RuleType.EarnRewardTime -> "For ${rule.blockedSummary}"
        EarnItRuleStore.RuleType.CompleteToUnlock -> "${rule.requirements.size} requirements unlock ${rule.blockedSummary}"
        EarnItRuleStore.RuleType.ScheduledBlock -> "Blocks ${rule.blockedSummary}"
    }
}

@Composable
private fun TemporaryRuleManagement(
    rules: List<EarnItRuleStore.Rule>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onEditRule: (EarnItRuleStore.Rule) -> Unit,
    onToggleRuleEnabled: (EarnItRuleStore.Rule) -> Unit,
    onDeleteRule: (EarnItRuleStore.Rule) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton(onClick = onToggleExpanded, modifier = Modifier.fillMaxWidth()) {
            Text(text = if (expanded) "Hide temporary Rule management" else "Temporary Rule management")
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Temporary access until Rule Detail and Builder milestones replace this path.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                rules.forEach { rule ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = temporaryRuleTitle(rule), style = MaterialTheme.typography.titleSmall)
                            Text(text = temporaryRuleSubtitle(rule), style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onEditRule(rule) }) {
                                    Text(text = "Edit")
                                }
                                OutlinedButton(onClick = { onToggleRuleEnabled(rule) }) {
                                    Text(text = if (rule.enabled) "Pause" else "Resume")
                                }
                                OutlinedButton(onClick = { onDeleteRule(rule) }) {
                                    Text(text = "Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
