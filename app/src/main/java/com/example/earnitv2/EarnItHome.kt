package com.example.earnitv2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

data class HomeRuleUiState(
    val rule: EarnItRuleStore.Rule,
    val card: RuleCardUiState,
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
    val primaryText = homePrimaryText(rule, card, state.remainingRewardSeconds, isActiveNow)
    val secondaryText = homeSecondaryText(rule, state.remainingRewardSeconds, isActiveNow)
    val statusText = when {
        card.attentionLabel != null -> "Protection needs attention"
        !rule.enabled -> "Available if resumed today"
        else -> card.scheduleStatusLabel
    }
    return HomeRuleUiState(
        rule = rule,
        card = card,
        primaryText = primaryText,
        secondaryText = secondaryText,
        earnContextText = if (rule.type == EarnItRuleStore.RuleType.EarnRewardTime && rule.enabled && isActiveNow) {
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
    isActiveNow: Boolean
): String {
    if (!rule.enabled) return "Rule paused"
    return when (rule.type) {
        EarnItRuleStore.RuleType.EarnRewardTime -> when {
            !isActiveNow -> "Unrestricted right now"
            remainingRewardSeconds <= 0L -> "No Reward Time"
            else -> card.availableRewardTimeLabel
        }
        EarnItRuleStore.RuleType.CompleteToUnlock -> {
            if (isActiveNow) "Complete requirements to unlock" else "Unlock rule scheduled"
        }
        EarnItRuleStore.RuleType.ScheduledBlock -> {
            if (isActiveNow) "Blocked now" else "Scheduled block"
        }
    }
}

private fun homeSecondaryText(
    rule: EarnItRuleStore.Rule,
    remainingRewardSeconds: Long,
    isActiveNow: Boolean
): String? {
    return when (rule.type) {
        EarnItRuleStore.RuleType.EarnRewardTime -> when {
            !rule.enabled -> EarnItUiFormatters.savedRewardTime(remainingRewardSeconds)
            !isActiveNow -> EarnItUiFormatters.remainingRewardTime(remainingRewardSeconds)
            remainingRewardSeconds <= 0L -> EarnItUiFormatters.exchangeSummary(rule.rewardSecondsPerProductiveSecond)
            else -> null
        }
        EarnItRuleStore.RuleType.CompleteToUnlock -> {
            val count = rule.requirements.size
            "$count ${if (count == 1) "requirement" else "requirements"}"
        }
        EarnItRuleStore.RuleType.ScheduledBlock -> rule.scheduleLabel
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenRuleDetail(homeRule.rule.id) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = homeRule.primaryText, style = MaterialTheme.typography.headlineSmall)
            if (homeRule.secondaryText != null) {
                Text(text = homeRule.secondaryText, style = MaterialTheme.typography.bodyMedium)
            }
            when (homeRule.rule.type) {
                EarnItRuleStore.RuleType.EarnRewardTime -> {
                    RewardAppsRow(label = "For", apps = card.rewardApps)
                    EarnAppRow(
                        appName = card.earnAppName,
                        packageName = card.earnAppPackage,
                        contextText = homeRule.earnContextText,
                        onOpenEarnApp = onOpenEarnApp
                    )
                }
                EarnItRuleStore.RuleType.CompleteToUnlock -> {
                    RequirementCountRow(requirements = homeRule.rule.requirements)
                    RewardAppsRow(label = "Unlocks", apps = card.rewardApps)
                }
                EarnItRuleStore.RuleType.ScheduledBlock -> {
                    RewardAppsRow(label = "Blocked Apps", apps = card.rewardApps)
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
private fun RequirementCountRow(requirements: List<EarnItRuleStore.RuleRequirement>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = "Requirements", style = MaterialTheme.typography.labelSmall)
        Text(
            text = "${requirements.size} ${if (requirements.size == 1) "requirement" else "requirements"} must be complete",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RewardAppsRow(label: String, apps: List<EarnItAppUiState>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            apps.take(5).forEach { app ->
                EarnItAppIcon(packageName = app.packageName, appName = app.name, size = 28.dp)
            }
            if (apps.size > 5) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "+${apps.size - 5}", style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(
                text = apps.joinToString(", ") { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EarnAppRow(
    appName: String,
    packageName: String,
    contextText: String?,
    onOpenEarnApp: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = "Earn with", style = MaterialTheme.typography.labelSmall)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable { onOpenEarnApp(packageName) }
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EarnItAppIcon(packageName = packageName, appName = appName, size = 24.dp)
            Text(text = appName, style = MaterialTheme.typography.bodyMedium)
            if (contextText != null) {
                Text(
                    text = contextText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.weight(1f, fill = false))
            Text(text = ">", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AppInitialTile(name: String, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?", style = MaterialTheme.typography.labelSmall)
    }
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
