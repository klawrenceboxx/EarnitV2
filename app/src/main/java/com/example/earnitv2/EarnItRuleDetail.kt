package com.example.earnitv2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

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
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        RuleDetailTopBar(onBack = onBack)

        if (permissionState.needsAttention) {
            RuleDetailAttention(
                permissionState = permissionState,
                onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings
            )
        }

        RuleDetailLiveState(homeRule = homeRule)
        RuleDetailRewardApps(apps = detail.card.rewardApps)
        RuleDetailEarnAction(
            earnAppName = detail.card.earnAppName,
            earnAppPackage = detail.card.earnAppPackage,
            onOpenEarnApp = onOpenEarnApp
        )
        RuleDetailAgreement(summary = detail.ruleAgreementSummary)
        RuleDetailSchedule(
            summary = detail.scheduleSummary,
            explanation = detail.scheduleExplanation
        )
        RuleDetailManagement(
            rule = rule,
            canPause = detail.canPause,
            canDelete = detail.canDelete,
            onEditRule = onEditRule,
            onToggleRuleEnabled = onToggleRuleEnabled,
            onDeleteRule = onDeleteRule
        )
    }
}

@Composable
private fun RuleDetailTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) {
            Text(text = "Back")
        }
        Text(text = "Rule Detail", style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun RuleDetailAttention(
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
private fun RuleDetailLiveState(homeRule: HomeRuleUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = homeRule.primaryText, style = MaterialTheme.typography.headlineMedium)
            if (homeRule.secondaryText != null) {
                Text(text = homeRule.secondaryText, style = MaterialTheme.typography.bodyMedium)
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
private fun RuleDetailRewardApps(apps: List<EarnItAppUiState>) {
    RuleDetailSection(title = "Reward Apps this applies to") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            apps.take(5).forEach { app ->
                DetailAppInitialTile(name = app.name, size = 32)
            }
            if (apps.size > 5) {
                DetailCountTile(count = apps.size - 5)
            }
        }
        Text(
            text = apps.joinToString(", ") { it.name },
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun RuleDetailEarnAction(
    earnAppName: String,
    earnAppPackage: String,
    onOpenEarnApp: (String) -> Unit
) {
    RuleDetailSection(title = "Earn with") {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable { onOpenEarnApp(earnAppPackage) }
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DetailAppInitialTile(name = earnAppName, size = 28)
            Text(text = earnAppName, style = MaterialTheme.typography.bodyLarge)
            Text(text = "Open", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun RuleDetailAgreement(summary: String) {
    RuleDetailSection(title = "Rule agreement") {
        Text(text = summary, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun RuleDetailSchedule(summary: String, explanation: String) {
    RuleDetailSection(title = "Applies") {
        Text(text = summary, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = explanation,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RuleDetailManagement(
    rule: EarnItRuleStore.Rule,
    canPause: Boolean,
    canDelete: Boolean,
    onEditRule: (EarnItRuleStore.Rule) -> Unit,
    onToggleRuleEnabled: (EarnItRuleStore.Rule) -> Unit,
    onDeleteRule: (EarnItRuleStore.Rule) -> Unit
) {
    RuleDetailSection(title = "Manage Rule") {
        OutlinedButton(onClick = { onEditRule(rule) }, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Edit Rule")
        }
        OutlinedButton(
            onClick = { onToggleRuleEnabled(rule) },
            enabled = canPause || !rule.enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = if (rule.enabled) "Pause Rule" else "Resume Rule")
        }
        OutlinedButton(
            onClick = { onDeleteRule(rule) },
            enabled = canDelete,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Delete Rule")
        }
    }
}

@Composable
private fun RuleDetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun DetailAppInitialTile(name: String, size: Int) {
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

@Composable
private fun DetailCountTile(count: Int) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "+$count", style = MaterialTheme.typography.labelSmall)
    }
}
