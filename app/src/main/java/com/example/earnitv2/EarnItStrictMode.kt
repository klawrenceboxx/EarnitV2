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
    onBeginDeactivation: () -> Unit,
    onCancelDeactivation: () -> Unit,
    onConfirmDeactivation: () -> Unit,
    onKeepStrictModeActive: () -> Unit,
    onTick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var setup by remember(state.lifecycleState) { mutableStateOf(StrictModeSetupState.from(state.configuration)) }
    var step by remember(state.lifecycleState) { mutableStateOf(StrictModeScreenStep.Setup) }

    LaunchedEffect(state.lifecycleState, state.activationGraceEndsAtMillis, state.expiresAtMillis) {
        while (state.lifecycleState == StrictModeLifecycleState.Activating ||
            (state.lifecycleState.isStrictModeProtecting() && state.expiresAtMillis != null) ||
            state.lifecycleState == StrictModeLifecycleState.DeactivationCounting
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
                protectionSummary = protectionSummary,
                onBeginDeactivation = onBeginDeactivation
            )
            StrictModeLifecycleState.DeactivationCounting -> StrictModeDeactivationCountdown(
                state = state,
                onCancelDeactivation = onCancelDeactivation
            )
            StrictModeLifecycleState.DeactivationReady -> StrictModeDeactivateConfirmation(
                onConfirmDeactivation = onConfirmDeactivation,
                onKeepStrictModeActive = onKeepStrictModeActive
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
        Text(text = "Status: Off", style = MaterialTheme.typography.titleSmall)
        Text(
            text = "Strict Mode protects changes made inside EarnIt. Android system settings can still affect protection.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
    StrictModeCard {
        Text(text = "Duration", style = MaterialTheme.typography.titleSmall)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                onSelect = { onSetupChange(setup.copy(timedDurationMillis = it, timedCustomVisible = false)) },
                onUseCustom = { millis, value ->
                    onSetupChange(
                        setup.copy(
                            timedDurationMillis = millis,
                            customTimedHours = value,
                            timedCustomVisible = true
                        )
                    )
                },
                onShowCustom = { onSetupChange(setup.copy(timedCustomVisible = true)) },
                customVisible = setup.timedCustomVisible,
                customLabel = "Custom hours",
                maxValue = StrictModeStore.MAX_TIMED_DURATION_MILLIS / (60L * 60_000L)
            )
            if (!setup.timedDurationValid) {
                Text(text = "Choose a duration from 1 hour to 30 days.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    StrictModeCard {
        Text(text = "Deactivation method", style = MaterialTheme.typography.titleSmall)
        DeactivationMethodRow(
            title = "Countdown selected",
            description = "Wait for a chosen period before Strict Mode can be deactivated.",
            enabled = true
        )
        DurationPresetRows(
            selectedMillis = setup.deactivationCountdownMillis,
            customValue = setup.customCountdownMinutes,
            onCustomValueChange = { onSetupChange(setup.copy(customCountdownMinutes = it.filter(Char::isDigit).take(4))) },
            onSelect = { onSetupChange(setup.copy(deactivationCountdownMillis = it, countdownCustomVisible = false)) },
            onUseCustom = { millis, value ->
                onSetupChange(
                    setup.copy(
                        deactivationCountdownMillis = millis,
                        customCountdownMinutes = value,
                        countdownCustomVisible = true
                    )
                )
            },
            onShowCustom = { onSetupChange(setup.copy(countdownCustomVisible = true)) },
            customVisible = setup.countdownCustomVisible,
            customLabel = "Custom minutes",
            presets = countdownPresets(),
            maxValue = StrictModeStore.MAX_DEACTIVATION_COUNTDOWN_MILLIS / 60_000L
        )
        if (!setup.countdownDurationValid) {
            Text(text = "Choose a countdown from 1 minute to 24 hours.", style = MaterialTheme.typography.bodySmall)
        }
        DeactivationMethodRow(
            title = "Charger + wait",
            description = "Connect your charger and complete a waiting period.",
            enabled = false
        )
        DeactivationMethodRow(
            title = "PIN",
            description = "Require a PIN before deactivation.",
            enabled = false
        )
        DeactivationMethodRow(
            title = "Email Approval",
            description = "Approve deactivation through a secure email link.",
            enabled = false
        )
        DeactivationMethodRow(
            title = "NFC Tag or Security Fob",
            description = "Require a physical tag or compatible security device.",
            enabled = false
        )
    }
    StrictModeCard {
        Text(text = "What Strict Mode Protects", style = MaterialTheme.typography.titleSmall)
        Text(
            text = "When active, Strict Mode prevents protected Rules from being edited, paused, disabled, or deleted.",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Disabled and paused Rules stay unchanged. If enabled or resumed later, they become protected immediately.",
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
    onUseCustom: (Long, String) -> Unit,
    onShowCustom: () -> Unit,
    customVisible: Boolean,
    customLabel: String,
    presets: List<Pair<String, Long>> = timedDurationPresets(),
    maxValue: Long
) {
    presets.forEach { (label, millis) ->
        OutlinedButton(onClick = { onSelect(millis) }, modifier = Modifier.fillMaxWidth()) {
            Text(text = if (selectedMillis == millis) "$label selected" else label)
        }
    }
    OutlinedButton(onClick = onShowCustom, modifier = Modifier.fillMaxWidth()) {
        Text(text = if (customVisible) "Custom selected" else "Custom")
    }
    if (customVisible) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = customValue,
                onValueChange = onCustomValueChange,
                label = { Text(text = customLabel) },
                supportingText = { Text(text = "1 to $maxValue") },
                isError = customValue.toLongOrNull()?.let { it !in 1L..maxValue } == true,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = {
                customValue.toLongOrNull()?.takeIf { it in 1L..maxValue }?.let { value ->
                    val millis = if (customLabel.contains("hours")) value * 60L * 60_000L else value * 60_000L
                    onUseCustom(millis, value.toString())
                }
            }) {
                Text(text = "Use")
            }
        }
    }
}

@Composable
private fun DeactivationMethodRow(
    title: String,
    description: String,
    enabled: Boolean
) {
    OutlinedButton(
        onClick = {},
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title)
            Text(text = description, style = MaterialTheme.typography.bodySmall)
            if (!enabled) {
                Text(text = "Coming Soon", style = MaterialTheme.typography.labelMedium)
            }
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
        Text(text = "Disabled and paused Rules will remain unchanged. If enabled or resumed while Strict Mode is active, they become protected immediately.")
        Text(text = "Strict Mode activates after a 30-second review period.")
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
        Text(text = "This is your final opportunity to cancel before Strict Mode becomes active.")
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
    protectionSummary: StrictModeRuleProtectionSummary,
    onBeginDeactivation: () -> Unit
) {
    var beginDeactivationDialogOpen by remember { mutableStateOf(false) }
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
        Text(text = "Protected Rules cannot be edited, paused, disabled, or deleted. Enable and Resume stay available.")
        Button(onClick = { beginDeactivationDialogOpen = true }, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Begin Deactivation")
        }
    }
    if (beginDeactivationDialogOpen) {
        BeginDeactivationDialog(
            countdownLabel = durationLabel(state.configuration.deactivationCountdownMillis),
            onStartCountdown = {
                beginDeactivationDialogOpen = false
                onBeginDeactivation()
            },
            onCancel = { beginDeactivationDialogOpen = false }
        )
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
private fun BeginDeactivationDialog(
    countdownLabel: String,
    onStartCountdown: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(text = "Begin deactivation?") },
        text = {
            Text(
                text = "Strict Mode will remain active for the next $countdownLabel. Your protected Rules cannot be changed during this time."
            )
        },
        confirmButton = {
            TextButton(onClick = onStartCountdown) {
                Text(text = "Start Countdown")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(text = "Cancel")
            }
        }
    )
}

@Composable
private fun StrictModeDeactivationCountdown(
    state: StrictModeState,
    onCancelDeactivation: () -> Unit
) {
    StrictModeCard {
        Text(text = "Strict Mode Active", style = MaterialTheme.typography.titleLarge)
        Text(text = "Deactivation in progress", style = MaterialTheme.typography.titleSmall)
        Text(
            text = "${durationLabel(strictModeRemainingMillis(state.deactivationAvailableAtMillis))} remaining",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(text = "Deactivate with: Countdown")
        Text(
            text = "Your Rules are still protected. Strict Mode will not turn off automatically when this timer reaches zero. You will need to confirm deactivation.",
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedButton(onClick = onCancelDeactivation, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Cancel Deactivation")
        }
    }
}

@Composable
private fun StrictModeDeactivateConfirmation(
    onConfirmDeactivation: () -> Unit,
    onKeepStrictModeActive: () -> Unit
) {
    StrictModeCard {
        Text(text = "Deactivate Strict Mode?", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "The waiting period is complete. Deactivating Strict Mode will allow your Rules to be edited, paused, disabled, or deleted again.",
            style = MaterialTheme.typography.bodyMedium
        )
        Button(onClick = onConfirmDeactivation, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Deactivate Strict Mode")
        }
        OutlinedButton(onClick = onKeepStrictModeActive, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Keep Strict Mode Active")
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
    val customCountdownMinutes: String = "",
    val timedCustomVisible: Boolean = false,
    val countdownCustomVisible: Boolean = false
) {
    val timedDurationValid: Boolean
        get() = durationType == StrictModeDurationType.Indefinite ||
            (timedDurationMillis ?: 0L) in 1L..StrictModeStore.MAX_TIMED_DURATION_MILLIS

    val countdownDurationValid: Boolean
        get() = (deactivationCountdownMillis ?: 0L) in 1L..StrictModeStore.MAX_DEACTIVATION_COUNTDOWN_MILLIS

    val isValid: Boolean
        get() {
            return timedDurationValid && countdownDurationValid
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
            val timedCustom = configuration.timedDurationMillis != null &&
                timedDurationPresets().none { it.second == configuration.timedDurationMillis }
            val countdownCustom = configuration.deactivationCountdownMillis != null &&
                countdownPresets().none { it.second == configuration.deactivationCountdownMillis }
            return StrictModeSetupState(
                durationType = configuration.durationType,
                timedDurationMillis = configuration.timedDurationMillis,
                deactivationCountdownMillis = configuration.deactivationCountdownMillis,
                customTimedHours = configuration.timedDurationMillis
                    ?.takeIf { timedCustom }
                    ?.let { (it / (60L * 60_000L)).toString() }
                    .orEmpty(),
                customCountdownMinutes = configuration.deactivationCountdownMillis
                    ?.takeIf { countdownCustom }
                    ?.let { (it / 60_000L).toString() }
                    .orEmpty(),
                timedCustomVisible = timedCustom,
                countdownCustomVisible = countdownCustom
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
    if (millis in 1L until 60_000L) return "Less than 1 minute"
    val minutes = millis / 60_000L
    if (minutes <= 0L) return "0 minutes"
    val days = minutes / 1_440L
    val hours = (minutes % 1_440L) / 60L
    val remainingMinutes = minutes % 60L
    return when {
        days > 0L && hours > 0L -> "${plural(days, "day")} ${plural(hours, "hour")}"
        days > 0L -> plural(days, "day")
        hours > 0L && remainingMinutes > 0L -> "${plural(hours, "hour")} ${plural(remainingMinutes, "minute")}"
        hours > 0L -> plural(hours, "hour")
        else -> "$minutes minute${if (minutes == 1L) "" else "s"}"
    }
}

private fun plural(value: Long, unit: String): String {
    return "$value $unit${if (value == 1L) "" else "s"}"
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
