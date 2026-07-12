package com.example.earnitv2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

internal enum class StrictModeScreenStep {
    Setup,
    Review
}

@Composable
internal fun EarnItStrictModeScreen(
    state: StrictModeState,
    enabledRuleCount: Int,
    disabledRuleCount: Int,
    protectionSummary: StrictModeRuleProtectionSummary,
    onBack: () -> Unit,
    onSaveConfiguration: (StrictModeConfiguration) -> Unit,
    onBeginActivation: (StrictModeConfiguration) -> Unit,
    onCancelActivation: () -> Unit,
    onTick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var setup by remember(state.lifecycleState) { mutableStateOf(StrictModeSetupState.from(state.configuration)) }
    var step by remember(state.lifecycleState) { mutableStateOf(StrictModeScreenStep.Setup) }

    LaunchedEffect(state.lifecycleState, state.activationGraceEndsAtMillis, state.expiresAtMillis) {
        while (state.lifecycleState == StrictModeLifecycleState.Activating ||
            (state.lifecycleState == StrictModeLifecycleState.Active && state.expiresAtMillis != null)
        ) {
            delay(1_000)
            onTick()
        }
    }

    StrictModeScaffold(title = "Strict Mode", onBack = onBack, modifier = modifier) {
        when (state.lifecycleState) {
            StrictModeLifecycleState.Inactive -> {
                if (step == StrictModeScreenStep.Review) {
                    StrictModeReview(
                        configuration = setup.toConfiguration(),
                        enabledRuleCount = enabledRuleCount,
                        disabledRuleCount = disabledRuleCount,
                        onBack = { step = StrictModeScreenStep.Setup },
                        onActivate = { onBeginActivation(setup.toConfiguration()) }
                    )
                } else {
                    StrictModeSetup(
                        setup = setup,
                        onSetupChange = {
                            setup = it
                            if (it.isValid) onSaveConfiguration(it.toConfiguration())
                        },
                        onReview = { if (setup.isValid) step = StrictModeScreenStep.Review }
                    )
                }
            }
            StrictModeLifecycleState.Activating -> StrictModeActivationCountdown(
                remainingSeconds = strictModeRemainingSeconds(state.activationGraceEndsAtMillis),
                onCancel = onCancelActivation
            )
            StrictModeLifecycleState.Active -> StrictModeActive(
                state = state,
                enabledRuleCount = enabledRuleCount,
                disabledRuleCount = disabledRuleCount,
                protectionSummary = protectionSummary
            )
        }
    }
}

@Composable
private fun StrictModeScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) {
                Text(text = "< Back")
            }
            Text(text = title, style = MaterialTheme.typography.headlineMedium)
        }
        content()
    }
}

@Composable
private fun StrictModeSetup(
    setup: StrictModeSetupState,
    onSetupChange: (StrictModeSetupState) -> Unit,
    onReview: () -> Unit
) {
    StrictModeCard {
        Text(text = "Protect your Rules from impulsive changes.", style = MaterialTheme.typography.bodyLarge)
        Text(text = "Status: Off", style = MaterialTheme.typography.titleSmall)
    }
    StrictModeCard {
        Text(text = "Duration type", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onSetupChange(setup.copy(durationType = StrictModeDurationType.Timed)) }) {
                Text(text = if (setup.durationType == StrictModeDurationType.Timed) "Timed selected" else "Timed")
            }
            OutlinedButton(onClick = { onSetupChange(setup.copy(durationType = StrictModeDurationType.Indefinite)) }) {
                Text(text = if (setup.durationType == StrictModeDurationType.Indefinite) "Indefinite selected" else "Indefinite")
            }
        }
        if (setup.durationType == StrictModeDurationType.Timed) {
            DurationPresetRows(
                selectedMillis = setup.timedDurationMillis,
                customValue = setup.customTimedHours,
                onCustomValueChange = { onSetupChange(setup.copy(customTimedHours = it.filter(Char::isDigit).take(4))) },
                onSelect = { onSetupChange(setup.copy(timedDurationMillis = it)) },
                customLabel = "Custom hours"
            )
        }
    }
    StrictModeCard {
        Text(text = "Deactivation method", style = MaterialTheme.typography.titleSmall)
        Text(text = "Countdown", style = MaterialTheme.typography.bodyLarge)
        DurationPresetRows(
            selectedMillis = setup.deactivationCountdownMillis,
            customValue = setup.customCountdownMinutes,
            onCustomValueChange = { onSetupChange(setup.copy(customCountdownMinutes = it.filter(Char::isDigit).take(4))) },
            onSelect = { onSetupChange(setup.copy(deactivationCountdownMillis = it)) },
            customLabel = "Custom minutes",
            presets = countdownPresets()
        )
    }
    StrictModeCard {
        Text(
            text = "When active, Strict Mode prevents protected Rules from being edited, paused, disabled, or deleted.",
            style = MaterialTheme.typography.bodyMedium
        )
        Button(onClick = onReview, enabled = setup.isValid, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Review and Activate")
        }
    }
}

@Composable
private fun DurationPresetRows(
    selectedMillis: Long?,
    customValue: String,
    onCustomValueChange: (String) -> Unit,
    onSelect: (Long) -> Unit,
    customLabel: String,
    presets: List<Pair<String, Long>> = timedDurationPresets()
) {
    presets.forEach { (label, millis) ->
        OutlinedButton(onClick = { onSelect(millis) }, modifier = Modifier.fillMaxWidth()) {
            Text(text = if (selectedMillis == millis) "$label selected" else label)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = customValue,
            onValueChange = onCustomValueChange,
            label = { Text(text = customLabel) },
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(onClick = {
            customValue.toLongOrNull()?.takeIf { it > 0L }?.let { value ->
                val millis = if (customLabel.contains("hours")) value * 60 * 60_000L else value * 60_000L
                onSelect(millis)
            }
        }) {
            Text(text = "Use")
        }
    }
}

@Composable
private fun StrictModeReview(
    configuration: StrictModeConfiguration,
    enabledRuleCount: Int,
    disabledRuleCount: Int,
    onBack: () -> Unit,
    onActivate: () -> Unit
) {
    StrictModeCard {
        Text(text = "Review activation", style = MaterialTheme.typography.titleLarge)
        Text(text = "Duration type: ${configuration.durationType.label}")
        if (configuration.durationType == StrictModeDurationType.Timed) {
            Text(text = "Duration: ${durationLabel(configuration.timedDurationMillis)}")
        }
        Text(text = "Deactivation method: Countdown")
        Text(text = "Countdown duration: ${durationLabel(configuration.deactivationCountdownMillis)}")
        Text(text = "Enabled Rules: $enabledRuleCount")
        Text(text = "Disabled or paused Rules: $disabledRuleCount")
        Text(text = "Enabled Rules will be protected while Strict Mode is active.")
        Text(text = "Disabled and paused Rules will become protected if they are enabled or resumed.")
        Button(onClick = onActivate, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Activate Strict Mode")
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Back")
        }
    }
}

@Composable
private fun StrictModeActivationCountdown(remainingSeconds: Long, onCancel: () -> Unit) {
    StrictModeCard {
        Text(text = "Strict Mode activates in", style = MaterialTheme.typography.titleLarge)
        Text(text = remainingSeconds.coerceAtLeast(0L).toString(), style = MaterialTheme.typography.displaySmall)
        Text(text = "After activation, Strict Mode cannot be normally changed until a deactivation flow succeeds.")
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Cancel")
        }
    }
}

@Composable
private fun StrictModeActive(
    state: StrictModeState,
    enabledRuleCount: Int,
    disabledRuleCount: Int,
    protectionSummary: StrictModeRuleProtectionSummary
) {
    StrictModeCard {
        Text(text = "Strict Mode Active", style = MaterialTheme.typography.titleLarge)
        Text(
            text = if (state.configuration.durationType == StrictModeDurationType.Timed) {
                "Remaining: ${durationLabel(strictModeRemainingMillis(state.expiresAtMillis))}"
            } else {
                "Duration: Indefinite"
            }
        )
        Text(text = "Deactivation method: Countdown")
        Text(text = "Configured countdown: ${durationLabel(state.configuration.deactivationCountdownMillis)}")
        Text(text = "Enabled Rules: $enabledRuleCount")
        Text(text = "Disabled or paused Rules: $disabledRuleCount")
        Text(text = "Rule protection will apply to enabled Rules.")
    }
    StrictModeRuleListCard(
        title = "Protected Rules",
        emptyText = "No Rules are currently protected.",
        rules = protectionSummary.protectedRules
    )
    if (protectionSummary.unprotectedRules.isNotEmpty()) {
        StrictModeRuleListCard(
            title = "Not currently protected",
            emptyText = "",
            rules = protectionSummary.unprotectedRules,
            pausedRuleIds = protectionSummary.pausedRuleIds,
            includeState = true
        )
        StrictModeCard {
            Text(text = "Disabled and paused Rules become protected automatically when enabled or resumed.")
        }
    }
}

@Composable
private fun StrictModeRuleListCard(
    title: String,
    emptyText: String,
    rules: List<EarnItRuleStore.Rule>,
    pausedRuleIds: Set<String> = emptySet(),
    includeState: Boolean = false
) {
    StrictModeCard {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        if (rules.isEmpty()) {
            Text(text = emptyText, style = MaterialTheme.typography.bodyMedium)
        } else {
            rules.forEach { rule ->
                Text(
                    text = if (includeState) {
                        "${strictModeRuleLabel(rule)} - ${strictModeRuleStateLabel(rule, pausedRuleIds)}"
                    } else {
                        strictModeRuleLabel(rule)
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
internal fun StrictModeProtectedActionDialog(
    onViewStrictMode: () -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(text = "Protected by Strict Mode") },
        text = { Text(text = "This Rule cannot be changed while Strict Mode is active.") },
        confirmButton = {
            TextButton(onClick = onViewStrictMode) {
                Text(text = "View Strict Mode")
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text(text = "Close")
            }
        }
    )
}

@Composable
private fun StrictModeCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            content()
        }
    }
}

internal data class StrictModeSetupState(
    val durationType: StrictModeDurationType,
    val timedDurationMillis: Long?,
    val deactivationCountdownMillis: Long?,
    val customTimedHours: String = "",
    val customCountdownMinutes: String = ""
) {
    val isValid: Boolean
        get() {
            val durationValid = durationType == StrictModeDurationType.Indefinite || (timedDurationMillis ?: 0L) > 0L
            return durationValid && (deactivationCountdownMillis ?: 0L) > 0L
        }

    fun toConfiguration(): StrictModeConfiguration {
        return StrictModeConfiguration(
            durationType = durationType,
            timedDurationMillis = if (durationType == StrictModeDurationType.Timed) timedDurationMillis else null,
            deactivationCountdownMillis = deactivationCountdownMillis
        )
    }

    companion object {
        fun from(configuration: StrictModeConfiguration): StrictModeSetupState {
            return StrictModeSetupState(
                durationType = configuration.durationType,
                timedDurationMillis = configuration.timedDurationMillis,
                deactivationCountdownMillis = configuration.deactivationCountdownMillis
            )
        }
    }
}

internal fun timedDurationPresets(): List<Pair<String, Long>> {
    return listOf(
        "1 hour" to 60 * 60_000L,
        "4 hours" to 4 * 60 * 60_000L,
        "8 hours" to 8 * 60 * 60_000L,
        "1 day" to 24 * 60 * 60_000L,
        "3 days" to 3 * 24 * 60 * 60_000L,
        "7 days" to 7 * 24 * 60 * 60_000L
    )
}

internal fun countdownPresets(): List<Pair<String, Long>> {
    return listOf(
        "10 minutes" to 10 * 60_000L,
        "30 minutes" to 30 * 60_000L,
        "1 hour" to 60 * 60_000L
    )
}

internal fun strictModeRemainingSeconds(targetMillis: Long?, nowMillis: Long = System.currentTimeMillis()): Long {
    return (strictModeRemainingMillis(targetMillis, nowMillis) + 999L) / 1_000L
}

internal fun strictModeRemainingMillis(targetMillis: Long?, nowMillis: Long = System.currentTimeMillis()): Long {
    return ((targetMillis ?: nowMillis) - nowMillis).coerceAtLeast(0L)
}

internal fun durationLabel(durationMillis: Long?): String {
    val millis = durationMillis ?: return "Indefinite"
    val minutes = millis / 60_000L
    return when {
        minutes >= 1_440L && minutes % 1_440L == 0L -> "${minutes / 1_440L} day${if (minutes == 1_440L) "" else "s"}"
        minutes >= 60L && minutes % 60L == 0L -> "${minutes / 60L} hour${if (minutes == 60L) "" else "s"}"
        else -> "$minutes minute${if (minutes == 1L) "" else "s"}"
    }
}

private val StrictModeDurationType.label: String
    get() = when (this) {
        StrictModeDurationType.Timed -> "Timed"
        StrictModeDurationType.Indefinite -> "Indefinite"
    }

internal fun strictModeRuleLabel(rule: EarnItRuleStore.Rule): String {
    return when (rule.type) {
        EarnItRuleStore.RuleType.EarnRewardTime -> "${rule.earnApps.joinToString(", ") { it.name }} -> ${rule.blockedSummary}"
        EarnItRuleStore.RuleType.CompleteToUnlock -> "Complete to Unlock -> ${rule.blockedSummary}"
        EarnItRuleStore.RuleType.ScheduledBlock -> "Scheduled Block -> ${rule.blockedSummary}"
    }
}

internal fun strictModeRuleStateLabel(rule: EarnItRuleStore.Rule, pausedRuleIds: Set<String>): String {
    return when {
        rule.id in pausedRuleIds -> "Paused"
        rule.enabled -> "Enabled"
        else -> "Disabled"
    }
}
