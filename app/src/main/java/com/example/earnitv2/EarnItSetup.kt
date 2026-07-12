package com.example.earnitv2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
    onOpenStrictMode: () -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onCreateFirstRule: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "Strict Mode", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Status: ${strictModeSettingsStatus(strictModeState.lifecycleState)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(onClick = onOpenStrictMode, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Open Strict Mode")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = if (permissionState.isReady) "Setup complete" else "Finish setup", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = if (permissionState.isReady) {
                        "Earning progress and app blocking are ready."
                    } else {
                        permissionState.repairTargetLabels.joinToString(" and ") + " needs attention."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "About EarnIt", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "EarnIt turns time in your Earn App into Reward Time for your Reward Apps. Rules stay on this device.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private fun strictModeSettingsStatus(state: StrictModeLifecycleState): String {
    return when (state) {
        StrictModeLifecycleState.Inactive -> "Off"
        StrictModeLifecycleState.Activating -> "Activating"
        StrictModeLifecycleState.Active -> "Active"
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
