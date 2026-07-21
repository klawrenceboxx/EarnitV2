package com.example.earnitv2

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class StrictModeScreenStep {
    Setup,
    Review
}

@Composable
internal fun EarnItStrictModeScreen(
    state: StrictModeState,
    foundationLifecycle: RuleStrictModeLifecycle? = null,
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
    var activationCountdownNowMillis by remember(state.activationGraceEndsAtMillis) {
        mutableStateOf(System.currentTimeMillis())
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val logicalBack = {
        if (state.lifecycleState == StrictModeLifecycleState.Inactive && step == StrictModeScreenStep.Review) {
            step = StrictModeScreenStep.Setup
        } else {
            onBack()
        }
    }
    BackHandler(onBack = logicalBack)

    LaunchedEffect(state.lifecycleState, state.activationGraceEndsAtMillis) {
        if (state.lifecycleState == StrictModeLifecycleState.Activating) {
            val deadline = state.activationGraceEndsAtMillis
            while (deadline != null) {
                val now = System.currentTimeMillis()
                activationCountdownNowMillis = now
                val remainingMillis = strictModeRemainingMillis(deadline, now)
                if (remainingMillis == 0L) {
                    onTick()
                    break
                }
                delay(remainingMillis.coerceAtMost(COUNTDOWN_REFRESH_MILLIS))
            }
        }
    }

    StrictModeScaffold(
        title = "Strict Mode",
        onBack = logicalBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier
    ) {
        if (foundationLifecycle == RuleStrictModeLifecycle.Invalid) {
            StrictModeInvalidConfiguration()
        } else when (state.lifecycleState) {
            StrictModeLifecycleState.Inactive -> {
                if (step == StrictModeScreenStep.Review) {
                    StrictModeReview(
                        configuration = setup.toConfiguration(),
                        onBack = logicalBack,
                        onActivate = { onBeginActivation(setup.toConfiguration()) }
                    )
                } else {
                    StrictModeSetup(
                        setup = setup,
                        onSetupChange = {
                            setup = it
                            if (it.isValid) onSaveConfiguration(it.toConfiguration())
                        },
                        onCustomDurationAccepted = { durationMillis ->
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    message = customDurationConfirmationMessage(durationMillis),
                                    duration = SnackbarDuration.Short
                                )
                            }
                        },
                        onReview = { if (setup.isValid) step = StrictModeScreenStep.Review }
                    )
                }
            }
            StrictModeLifecycleState.Activating -> StrictModeActivationCountdown(
                remainingSeconds = strictModeRemainingSeconds(
                    targetMillis = state.activationGraceEndsAtMillis,
                    nowMillis = activationCountdownNowMillis
                ),
                activeFromMillis = state.activationGraceEndsAtMillis,
                deactivationWaitMillis = state.configuration.deactivationCountdownMillis,
                onCancel = onCancelActivation
            )
            StrictModeLifecycleState.Active -> StrictModeActive(
                state = state,
                onBeginDeactivation = onBeginDeactivation
            )
            StrictModeLifecycleState.DeactivationCounting -> StrictModeDeactivationCountdown(
                state = state,
                onCancelDeactivation = onCancelDeactivation,
                onCountdownComplete = onTick
            )
            StrictModeLifecycleState.DeactivationReady -> StrictModeDeactivateConfirmation(
                onConfirmDeactivation = onConfirmDeactivation,
                onKeepStrictModeActive = onKeepStrictModeActive
            )
        }
    }
}

@Composable
private fun StrictModeInvalidConfiguration() {
    StrictModeCard {
        Text(text = "Strict Mode needs attention", style = MaterialTheme.typography.titleLarge)
        Text(text = "Your Rule remains protected. Review the Strict Mode setup before making less-restrictive changes.")
    }
}

@Composable
private fun StrictModeScaffold(
    title: String,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                EarnItBackButton(onBack)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = title, style = MaterialTheme.typography.headlineSmall)
            }
            content()
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
    }
}

@Composable
private fun StrictModeSetup(
    setup: StrictModeSetupState,
    onSetupChange: (StrictModeSetupState) -> Unit,
    onCustomDurationAccepted: (Long) -> Unit,
    onReview: () -> Unit
) {
    StrictModeCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Status", style = MaterialTheme.typography.titleSmall)
            StrictModeStatusBadge(active = false)
        }
        Text(
            text = "Make it harder to weaken your Rules during an impulsive moment.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    StrictModeCard {
        SectionHeading(
            title = "Commitment",
            supportingText = "How long do you want Strict Mode active?"
        )
        Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StrictModeSelectionCard(
                symbol = "\u25F7",
                title = "Until a timer ends",
                description = "Strict Mode turns off automatically.",
                selected = setup.durationType == StrictModeDurationType.Timed,
                onClick = {
                    if (setup.durationType != StrictModeDurationType.Timed) {
                        onSetupChange(setup.returnToTimedCommitment())
                    }
                }
            )
            StrictModeSelectionCard(
                symbol = "\u221E",
                title = "Until I turn it off",
                description = "Stays active until you deactivate it.",
                selected = setup.durationType == StrictModeDurationType.Indefinite,
                onClick = { onSetupChange(setup.copy(durationType = StrictModeDurationType.Indefinite)) }
            )
        }
        if (setup.durationType == StrictModeDurationType.Timed) {
            Text(
                text = "Quick picks",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            CommitmentQuickPicks(
                setup = setup,
                onSetupChange = onSetupChange,
                onCustomDurationAccepted = onCustomDurationAccepted
            )
            if (!setup.timedDurationValid) {
                Text(
                    text = "Choose a duration from 1 hour to 30 days.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
    StrictModeCard {
        SectionHeading(
            title = "Turn off with",
            supportingText = "Choose how you'll be able to deactivate Strict Mode."
        )
        CountdownMethodCard(
            setup = setup,
            onSetupChange = onSetupChange,
            title = "Countdown",
            description = "Wait for a chosen period before Strict Mode can be deactivated.",
            recommended = true
        )
        if (!setup.countdownDurationValid) {
            Text(text = "Choose a countdown from 1 minute to 24 hours.", style = MaterialTheme.typography.bodySmall)
        }
        UnavailableMethodRow(
            title = "Charger + wait",
            description = "Connect your charger and complete a waiting period."
        )
        UnavailableMethodRow(
            title = "PIN",
            description = "Require a PIN before deactivation."
        )
    }
    StrictModeCard {
        SectionHeading(
            title = "What Strict Mode protects",
            supportingText = "When enabled, all current and future Rules are protected."
        )
        ProtectionRow(symbol = "\u270E", label = "Weaker edits")
        HorizontalDivider()
        ProtectionRow(symbol = "\u23F8", label = "Pausing Rules")
        HorizontalDivider()
        ProtectionRow(symbol = "\u232B", label = "Deleting Rules")
        Text(
            text = "New Rules are protected automatically while Strict Mode is active.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Button(
        onClick = onReview,
        enabled = setup.isValid,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Text(text = "Review and Activate")
    }
}

@Composable
private fun SectionHeading(title: String, supportingText: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            text = supportingText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StrictModeStatusBadge(active: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = if (active) "ACTIVE" else "OFF",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StrictModeSelectionCard(
    symbol: String,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .18f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SymbolBadge(symbol = symbol, selected = selected)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Text(
                    text = "\u2713",
                    modifier = Modifier.clearAndSetSemantics { },
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SymbolBadge(symbol: String, selected: Boolean = false) {
    Surface(
        modifier = Modifier.size(44.dp).clearAndSetSemantics { },
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = symbol,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CommitmentQuickPicks(
    setup: StrictModeSetupState,
    onSetupChange: (StrictModeSetupState) -> Unit,
    onCustomDurationAccepted: (Long) -> Unit
) {
    val picks = listOf(
        StrictModeCommitmentPreset.OneHour to "1 hour",
        StrictModeCommitmentPreset.TwentyFourHours to "24 hours",
        StrictModeCommitmentPreset.SevenDays to "7 days",
        StrictModeCommitmentPreset.Custom to customCommitmentChipLabel(setup)
    )
    Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        picks.chunked(2).forEach { rowPicks ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowPicks.forEach { (preset, label) ->
                    ChoiceChip(
                        label = label,
                        selected = setup.selectedCommitmentPreset == preset,
                        onClick = { onSetupChange(setup.selectCommitmentPreset(preset)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
    if (setup.timedCustomVisible) {
        CustomDurationInput(
            value = setup.customTimedHours,
            label = "Custom hours",
            maxValue = StrictModeStore.MAX_TIMED_DURATION_MILLIS / (60L * 60_000L),
            onValueChange = { onSetupChange(setup.editCustomCommitmentDraft(it)) },
            onUse = { hours ->
                val durationMillis = hours * 60L * 60_000L
                onSetupChange(
                    setup.copy(
                        timedDurationMillis = durationMillis,
                        customTimedHours = hours.toString(),
                        timedCustomVisible = false
                    )
                )
                onCustomDurationAccepted(durationMillis)
            }
        )
    }
}

@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .heightIn(min = 52.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .30f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
            Text(text = label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun CustomDurationInput(
    value: String,
    label: String,
    maxValue: Long,
    onValueChange: (String) -> Unit,
    onUse: (Long) -> Unit
) {
    val parsed = value.toLongOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(text = label) },
            supportingText = { Text(text = "1 to $maxValue") },
            isError = parsed != null && parsed !in 1L..maxValue,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(
            onClick = { parsed?.takeIf { it in 1L..maxValue }?.let(onUse) },
            enabled = parsed != null && parsed in 1L..maxValue,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text(text = "Use")
        }
    }
}

@Composable
private fun CountdownMethodCard(
    setup: StrictModeSetupState,
    onSetupChange: (StrictModeSetupState) -> Unit,
    title: String,
    description: String,
    recommended: Boolean
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .12f),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SymbolBadge(symbol = "\u23F1", selected = true)
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        if (recommended) Text(
                            text = "Recommended",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(text = "Wait time", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                countdownPresets().chunked(2).forEach { rowPicks ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowPicks.forEach { (label, millis) ->
                            ChoiceChip(
                                label = when (label) { "10 minutes" -> "10 min"; "30 minutes" -> "30 min"; else -> label },
                                selected = setup.deactivationCountdownMillis == millis && !setup.countdownCustomVisible,
                                onClick = { onSetupChange(setup.copy(deactivationCountdownMillis = millis, countdownCustomVisible = false)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowPicks.size == 1) {
                            ChoiceChip(
                                label = "Custom",
                                selected = setup.countdownCustomVisible,
                                onClick = {
                                    if (!setup.countdownCustomVisible) {
                                        onSetupChange(setup.chooseCustomCountdown())
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            if (setup.countdownCustomVisible) {
                CustomDurationInput(
                    value = setup.customCountdownMinutes,
                    label = "Custom minutes",
                    maxValue = StrictModeStore.MAX_DEACTIVATION_COUNTDOWN_MILLIS / 60_000L,
                    onValueChange = { onSetupChange(setup.editCustomCountdownDraft(it)) },
                    onUse = { minutes ->
                        onSetupChange(
                            setup.copy(
                                deactivationCountdownMillis = minutes * 60_000L,
                                customCountdownMinutes = minutes.toString(),
                                countdownCustomVisible = true
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun UnavailableMethodRow(
    title: String,
    description: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().alpha(.72f),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SymbolBadge(symbol = "\u25CB")
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = "Coming Soon", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ProtectionRow(symbol: String, label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DecorativeSymbol(text = symbol)
        Text(text = label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        LockGlyph()
    }
}

@Composable
private fun StrictModeReview(
    configuration: StrictModeConfiguration,
    onBack: () -> Unit,
    onActivate: () -> Unit
) {
    SectionHeading(
        title = "Review Strict Mode",
        supportingText = "Confirm your settings before activating."
    )
    StrictModeCard {
        ReviewRow(
            symbol = "\u25F7",
            label = "Commitment",
            value = if (configuration.durationType == StrictModeDurationType.Timed) {
                durationLabel(configuration.timedDurationMillis)
            } else {
                "Until I turn it off"
            },
            supportingValue = if (configuration.durationType == StrictModeDurationType.Timed) {
                "Until a timer ends"
            } else null
        )
        HorizontalDivider()
        ReviewRow(
            symbol = "\u23F1",
            label = "Turn off with",
            value = "Countdown",
            supportingValue = durationLabel(configuration.deactivationCountdownMillis)
        )
    }
    StrictModeCard {
        ReviewNoticeRow(symbol = "\u25A1", text = "Strict Mode activates after a 30-second review period.")
        HorizontalDivider()
        ReviewNoticeRow(symbol = "\u25C7", text = "You can cancel before the countdown finishes.")
    }
    Button(
        onClick = onActivate,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Text(text = "Activate Strict Mode")
    }
    OutlinedButton(
        onClick = onBack,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Text(text = "Back")
    }
}

@Composable
private fun ReviewRow(
    symbol: String,
    label: String,
    value: String,
    supportingValue: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DecorativeSymbol(text = symbol)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(text = value, style = MaterialTheme.typography.bodyMedium)
            supportingValue?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ReviewNoticeRow(symbol: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DecorativeSymbol(text = symbol)
        Text(text = text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StrictModeActivationCountdown(
    remainingSeconds: Long,
    activeFromMillis: Long?,
    deactivationWaitMillis: Long?,
    onCancel: () -> Unit
) {
    val seconds = remainingSeconds.coerceIn(0L, 30L)
    val progress = seconds / 30f
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 560.dp),
        contentAlignment = Alignment.Center
    ) {
        StrictModeCard {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = activeFromMillis?.let {
                        "Strict Mode starts at ${SimpleDateFormat("MMM d 'at' h:mm a", Locale.getDefault()).format(Date(it))}"
                    } ?: "Strict Mode activates in",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = seconds.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(text = "seconds", style = MaterialTheme.typography.titleMedium)
                Box(modifier = Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 9.dp.toPx()
                        val radius = (size.minDimension - strokeWidth) / 2f
                        drawCircle(
                            color = trackColor,
                            radius = radius,
                            center = Offset(size.width / 2f, size.height / 2f),
                            style = Stroke(width = strokeWidth)
                        )
                        drawArc(
                            color = progressColor,
                            startAngle = -90f,
                            sweepAngle = progress * 360f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                    DecorativeSymbol(text = "\u25C7", style = MaterialTheme.typography.displaySmall)
                }
                Text(
                    text = "After activation, all Rules are protected from weaker edits, pausing, and deletion. Countdown \u00B7 ${durationLabel(deactivationWaitMillis)}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(text = "Cancel")
                }
            }
        }
    }
}

@Composable
private fun DecorativeSymbol(
    text: String,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium
) {
    Text(
        text = text,
        modifier = Modifier.clearAndSetSemantics { },
        style = style,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun LockGlyph() {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = Modifier.size(24.dp).clearAndSetSemantics { }) {
        val stroke = 2.dp.toPx()
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(size.width * .27f, size.height * .08f),
            size = androidx.compose.ui.geometry.Size(size.width * .46f, size.height * .55f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * .16f, size.height * .42f),
            size = androidx.compose.ui.geometry.Size(size.width * .68f, size.height * .48f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
            style = Stroke(width = stroke)
        )
    }
}

@Composable
private fun StrictModeActive(
    state: StrictModeState,
    onBeginDeactivation: () -> Unit
) {
    var beginDeactivationDialogOpen by remember { mutableStateOf(false) }
    val ui = strictModeActiveUiState(state)
    StrictModeCard {
        Text(text = ui.title, style = MaterialTheme.typography.titleLarge)
        Text(text = ui.description)
        HorizontalDivider()
        Text(text = ui.protectionMethod, style = MaterialTheme.typography.titleMedium)
        Text(text = ui.deactivationWait, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
}

internal data class StrictModeActiveUiState(
    val title: String,
    val description: String,
    val protectionMethod: String,
    val deactivationWait: String
)

internal fun strictModeActiveUiState(state: StrictModeState) = StrictModeActiveUiState(
    title = "Strict Mode is active",
    description = "All Rules are protected from weaker edits, pausing, and deletion.",
    protectionMethod = "Countdown",
    deactivationWait = "${durationLabel(state.configuration.deactivationCountdownMillis)} deactivation wait"
)

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
                text = "Strict Mode will remain active across all Rules for the next $countdownLabel."
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
    onCancelDeactivation: () -> Unit,
    onCountdownComplete: () -> Unit
) {
    val deadline = state.deactivationAvailableAtMillis
    var nowMillis by remember(deadline) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(deadline) {
        while (deadline != null) {
            val now = System.currentTimeMillis()
            nowMillis = now
            val remaining = strictModeRemainingMillis(deadline, now)
            if (remaining == 0L) {
                onCountdownComplete()
                break
            }
            delay(remaining.coerceAtMost(1_000L))
        }
    }
    StrictModeCard {
        Text(text = "Deactivation in progress", style = MaterialTheme.typography.titleLarge)
        Text(text = "Strict Mode remains active for:", style = MaterialTheme.typography.titleSmall)
        Text(
            text = strictModeTimerLabel(deadline, nowMillis),
            style = MaterialTheme.typography.displaySmall
        )
        Text(
            text = "You can leave EarnIt. The countdown will continue. Strict Mode will not turn off until you confirm.",
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
        Text(text = "Deactivation ready", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "You can now turn off Strict Mode. Disabling it will allow weaker edits, pausing, and deletion across all Rules without authorization.",
            style = MaterialTheme.typography.bodyMedium
        )
        Button(onClick = onConfirmDeactivation, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Disable Strict Mode")
        }
        OutlinedButton(onClick = onKeepStrictModeActive, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Keep Strict Mode Active")
        }
    }
}

@Composable
internal fun StrictModeProtectedActionDialog(
    message: String = "This Rule cannot be changed while Strict Mode is active.",
    dismissLabel: String = "Close",
    onViewStrictMode: () -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(text = "Protected by Strict Mode") },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(onClick = onViewStrictMode) {
                Text(text = "View Strict Mode")
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text(text = dismissLabel)
            }
        }
    )
}

@Composable
private fun StrictModeCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            val timedCustom = configuration.durationType == StrictModeDurationType.Timed &&
                configuration.timedDurationMillis != null &&
                strictModeCommitmentPresetFor(configuration.timedDurationMillis) == StrictModeCommitmentPreset.Custom
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

internal fun strictModeTimerLabel(targetMillis: Long?, nowMillis: Long = System.currentTimeMillis()): String {
    val totalSeconds = strictModeRemainingSeconds(targetMillis, nowMillis)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
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

internal fun customCommitmentChipLabel(setup: StrictModeSetupState): String {
    val durationMillis = setup.timedDurationMillis
    return if (setup.selectedCommitmentPreset == StrictModeCommitmentPreset.Custom &&
        durationMillis != null && durationMillis > 0L
    ) {
        "Custom (${durationLabel(durationMillis)})"
    } else {
        "Custom"
    }
}

internal fun customDurationConfirmationMessage(durationMillis: Long): String {
    return "\u2713 Custom duration set to ${durationLabel(durationMillis)}"
}

private const val COUNTDOWN_REFRESH_MILLIS = 250L

private fun plural(value: Long, unit: String): String {
    return "$value $unit${if (value == 1L) "" else "s"}"
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
