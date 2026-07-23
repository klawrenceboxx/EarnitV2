package com.example.earnitv2

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.earnitv2.ui.theme.EarnitV2Theme

enum class FirstLaunchStep {
    ValueIntroduction,
    TimeKey,
    Permissions,
    Ready
}

@Composable
fun EarnItFirstLaunch(
    currentStep: FirstLaunchStep,
    permissionState: PermissionSetupUiState,
    onStepChange: (FirstLaunchStep) -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onCreateFirstRule: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "EarnIt", style = MaterialTheme.typography.headlineMedium)
        Text(text = "Setup", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SetupProgress(currentStep = currentStep)

        when (currentStep) {
            FirstLaunchStep.ValueIntroduction -> SetupIntroStep(
                title = "Do the work. Earn the time.",
                body = "Choose an Earn App. Spend time there. EarnIt turns that time into Reward Time for the apps you choose.",
                primaryAction = "See how it works",
                showDiagram = true,
                onPrimaryAction = { onStepChange(FirstLaunchStep.TimeKey) }
            )
            FirstLaunchStep.TimeKey -> SetupIntroStep(
                title = "Your time becomes the key.",
                body = "Reward Apps stay closed until your Rule has Reward Time available. You control the exchange and the schedule.",
                primaryAction = "Continue",
                onPrimaryAction = { onStepChange(FirstLaunchStep.Permissions) }
            )
            FirstLaunchStep.Permissions -> SetupPermissionsStep(
                permissionState = permissionState,
                onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onContinue = { onStepChange(FirstLaunchStep.Ready) }
            )
            FirstLaunchStep.Ready -> SetupReadyStep(
                permissionState = permissionState,
                onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onCreateFirstRule = onCreateFirstRule
            )
        }
    }
}


@Composable
private fun SetupProgress(currentStep: FirstLaunchStep) {
    val steps = FirstLaunchStep.entries
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        steps.forEach { step ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .background(
                        if (steps.indexOf(step) <= steps.indexOf(currentStep)) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
            )
        }
    }
}

@Composable
private fun SetupValueDiagram() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        EarnItAppIcon(packageName = AppPackages.DEFAULT_PRODUCTIVE_APP, appName = "Duolingo", size = 56.dp)
        Text(text = "Duolingo", style = MaterialTheme.typography.bodySmall)
        Text(text = "earns", style = MaterialTheme.typography.labelSmall)
        Text(
            text = "Reward Time",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
        Text(text = "unlocks", style = MaterialTheme.typography.labelSmall)
        EarnItAppIcon(packageName = AppPackages.DEFAULT_BLOCKED_APP, appName = "Instagram", size = 56.dp)
        Text(text = "Instagram", style = MaterialTheme.typography.bodySmall)
    }
}
@Composable
private fun SetupIntroStep(
    title: String,
    body: String,
    primaryAction: String,
    showDiagram: Boolean = false,
    onPrimaryAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
            Text(text = body, style = MaterialTheme.typography.bodyLarge)
            if (showDiagram) {
                SetupValueDiagram()
            }
            Button(onClick = onPrimaryAction, modifier = Modifier.fillMaxWidth()) {
                Text(text = primaryAction)
            }
        }
    }
}

@Composable
private fun SetupPermissionsStep(
    permissionState: PermissionSetupUiState,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onContinue: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = "EarnIt needs a few Android permissions.", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "These let EarnIt count earning progress and close Reward Apps when Reward Time runs out.",
            style = MaterialTheme.typography.bodyLarge
        )
        PermissionRepairCard(
            title = "Allow earning progress",
            body = "EarnIt needs permission to count time in your Earn App.",
            granted = permissionState.earningProgressStatus == EarnItPermissionStatus.Granted,
            actionLabel = "Open Android Settings",
            onAction = onOpenUsageAccessSettings
        )
        PermissionRepairCard(
            title = "Allow app blocking",
            body = "EarnIt needs permission to close Reward Apps when this Rule says they are out of time.",
            granted = permissionState.appBlockingStatus == EarnItPermissionStatus.Granted,
            actionLabel = "Open Android Settings",
            onAction = onOpenAccessibilitySettings
        )
        Button(
            onClick = onContinue,
            enabled = permissionState.isReady,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = if (permissionState.isReady) "Continue" else "Finish permissions to continue")
        }
    }
}

@Composable
private fun SetupReadyStep(
    permissionState: PermissionSetupUiState,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onCreateFirstRule: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = if (permissionState.isReady) "EarnIt is ready." else "Finish setup",
            style = MaterialTheme.typography.headlineSmall
        )
        if (permissionState.isReady) {
            Text(text = "Create your first Rule to start earning Reward Time.", style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onCreateFirstRule, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Create First Rule")
            }
        } else {
            Text(text = "EarnIt still needs setup before your first Rule can work.", style = MaterialTheme.typography.bodyLarge)
            PermissionRepairRows(
                permissionState = permissionState,
                onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings
            )
        }
    }
}

@Composable
internal fun EarnItSettings(
    permissionState: PermissionSetupUiState,
    hasRules: Boolean,
    strictModeState: StrictModeState,
    onBack: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenStrictMode: () -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onCreateFirstRule: () -> Unit,
    showDeveloperTools: Boolean,
    onReplayOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "Settings", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onBack) {
                Text(text = "Done")
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenAnalytics),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 78.dp)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SettingsSymbolBadge(symbol = "▥", emphasized = true)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(text = "Analytics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "Understand your screen time and Rule performance",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "›",
                    modifier = Modifier.clearAndSetSemantics { },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsSymbolBadge(symbol = "\u25C7", emphasized = true)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(text = "Strict Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Protect your Rules from changes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    SettingsStatusBadge(status = strictModeSettingsBadge(strictModeState.lifecycleState))
                }
                Text(
                    text = strictModeSettingsDetail(strictModeState),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onOpenStrictMode),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .32f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Open Strict Mode", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "\u203A",
                            modifier = Modifier.clearAndSetSemantics { },
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
        }

        if (showDeveloperTools) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Developer", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Review first-launch education without changing Rules, Reward Time, permissions, or settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = onReplayOnboarding, modifier = Modifier.fillMaxWidth()) {
                        Text("Replay onboarding")
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsSymbolBadge(symbol = if (permissionState.isReady) "\u2713" else "!", success = permissionState.isReady)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = if (permissionState.isReady) "Setup Complete" else "Finish setup",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (permissionState.isReady) {
                                "Earning progress and app blocking are ready."
                            } else {
                                permissionState.repairTargetLabels.joinToString(" and ") + " needs attention."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!permissionState.isReady) {
                    PermissionRepairRows(
                        permissionState = permissionState,
                        onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                        onOpenAccessibilitySettings = onOpenAccessibilitySettings
                    )
                }
                if (!hasRules && permissionState.isReady) {
                    Button(onClick = onCreateFirstRule, modifier = Modifier.fillMaxWidth()) {
                        Text(text = "Create First Rule")
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 78.dp).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SettingsSymbolBadge(symbol = "i")
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(text = "About EarnIt", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "Learn how EarnIt helps you build better habits.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSymbolBadge(
    symbol: String,
    emphasized: Boolean = false,
    success: Boolean = false
) {
    val container = when {
        success -> MaterialTheme.colorScheme.tertiaryContainer
        emphasized -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when {
        success -> MaterialTheme.colorScheme.onTertiaryContainer
        emphasized -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier.size(46.dp).clearAndSetSemantics { },
        shape = CircleShape,
        color = container
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = symbol, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = content)
        }
    }
}

@Composable
private fun SettingsStatusBadge(status: String) {
    val active = status != "Off"
    Surface(
        shape = RoundedCornerShape(50),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = status,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun strictModeSettingsDetail(state: StrictModeState): String {
    return when {
        state.lifecycleState == StrictModeLifecycleState.Inactive -> "Choose a commitment and deactivation wait time."
        state.lifecycleState == StrictModeLifecycleState.Activating -> "Activating after the review countdown."
        state.lifecycleState == StrictModeLifecycleState.DeactivationCounting -> "Deactivation countdown in progress."
        state.lifecycleState == StrictModeLifecycleState.DeactivationReady -> "Ready to confirm deactivation."
        state.configuration.durationType == StrictModeDurationType.Indefinite -> "Active until you turn it off."
        state.expiresAtMillis != null -> "${durationLabel(strictModeRemainingMillis(state.expiresAtMillis))} remaining."
        else -> "Your enabled Rules are protected."
    }
}

internal fun strictModeSettingsBadge(state: StrictModeLifecycleState): String {
    return when (state) {
        StrictModeLifecycleState.Inactive -> "Off"
        StrictModeLifecycleState.Activating -> "Activating"
        StrictModeLifecycleState.Active,
        StrictModeLifecycleState.DeactivationCounting,
        StrictModeLifecycleState.DeactivationReady -> "Active"
    }
}

@Composable
private fun PermissionRepairRows(
    permissionState: PermissionSetupUiState,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (permissionState.earningProgressStatus == EarnItPermissionStatus.NeedsAttention) {
            OutlinedButton(onClick = onOpenUsageAccessSettings, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Allow earning progress")
            }
        }
        if (permissionState.appBlockingStatus == EarnItPermissionStatus.NeedsAttention) {
            OutlinedButton(onClick = onOpenAccessibilitySettings, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Allow app blocking")
            }
        }
    }
}

@Composable
private fun PermissionRepairCard(
    title: String,
    body: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(text = if (granted) "Allowed" else body, style = MaterialTheme.typography.bodyMedium)
            if (!granted) {
                OutlinedButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                    Text(text = actionLabel)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun EarnItFirstLaunchPreview() {
    EarnitV2Theme {
        EarnItFirstLaunch(
            currentStep = FirstLaunchStep.Permissions,
            permissionState = EarnItUiStateAdapters.permissionSetup(
                usageAccessGranted = false,
                appBlockingEnabled = true
            ),
            onStepChange = {},
            onOpenUsageAccessSettings = {},
            onOpenAccessibilitySettings = {},
            onCreateFirstRule = {}
        )
    }
}
