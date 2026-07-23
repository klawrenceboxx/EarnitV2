package com.example.earnitv2

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    globalConfiguration: GlobalStrictModeConfiguration? = null,
    pendingAction: PendingStrictModeAction? = null,
    chargerSession: ChargerAuthorizationSession? = null,
    chargingState: ChargingState = ChargingState(false, false),
    onBack: () -> Unit,
    onSaveConfiguration: (StrictModeConfiguration) -> Unit,
    onBeginActivation: (StrictModeConfiguration, StrictModeProtectionMethod, String?) -> Unit,
    onCancelActivation: () -> Unit,
    onBeginDeactivation: () -> Unit,
    onConfirmChargerDeactivation: () -> PendingActionValidation,
    onCancelDeactivation: () -> Unit,
    onConfirmDeactivation: () -> Unit,
    onKeepStrictModeActive: () -> Unit,
    onTick: () -> Unit,
    onAuthorizeCharger: () -> Unit = {},
    onVerifyPin: (String) -> PinVerificationResult = { PinVerificationResult.Rejected("PIN verification is unavailable.") },
    onConfirmProtectedAction: () -> Unit = {},
    onCancelProtectedRequest: () -> Unit = {},
    onRequestMethodChange: (StrictModeProtectionMethod, Long?, String?) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    var setup by remember(state.lifecycleState) {
        mutableStateOf(StrictModeSetupState.from(state.configuration).copy(
            protectionMethod = globalConfiguration?.protectionMethod ?: StrictModeProtectionMethod.Charger
        ))
    }
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
        } else if (pendingAction?.authorizationMethod == StrictModeProtectionMethod.Charger && chargerSession != null &&
            pendingAction.actionType != PendingStrictModeActionType.DisableStrictMode) {
            ChargerAuthorization(
                session = chargerSession,
                chargingState = chargingState,
                action = pendingAction,
                onAuthorize = onAuthorizeCharger,
                onConfirm = onConfirmProtectedAction,
                onCancel = onCancelProtectedRequest,
                onKeepStrictModeActive = onKeepStrictModeActive
            )
        } else if (pendingAction?.authorizationMethod == StrictModeProtectionMethod.Pin &&
            pendingAction.authorizationStatus == StrictModeAuthorizationStatus.AwaitingAuthorization) {
            PinAuthorization(
                action = pendingAction,
                onVerify = onVerifyPin,
                onCancel = onCancelProtectedRequest
            )
        } else if (pendingAction?.authorizationMethod == StrictModeProtectionMethod.Pin &&
            pendingAction.authorizationStatus == StrictModeAuthorizationStatus.AwaitingFinalConfirmation) {
            ChargerFinalConfirmation(
                action = pendingAction,
                onConfirm = onConfirmProtectedAction,
                onBack = {},
                onKeepStrictModeActive = onKeepStrictModeActive,
                onCancel = onCancelProtectedRequest
            )
        } else when (state.lifecycleState) {
            StrictModeLifecycleState.Inactive -> {
                if (step == StrictModeScreenStep.Review) {
                    StrictModeReview(
                        setup = setup,
                        onBack = logicalBack,
                        onActivate = { onBeginActivation(setup.toConfiguration(), setup.protectionMethod, setup.pin.takeIf { setup.protectionMethod == StrictModeProtectionMethod.Pin }) }
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
                protectionMethod = globalConfiguration?.protectionMethod ?: StrictModeProtectionMethod.Countdown,
                onCancel = onCancelActivation
            )
            StrictModeLifecycleState.Active -> StrictModeActive(
                state = state,
                configuration = globalConfiguration,
                chargingState = chargingState,
                onRequestMethodChange = onRequestMethodChange,
                onBeginDeactivation = onBeginDeactivation,
                onConfirmChargerDeactivation = onConfirmChargerDeactivation
            )
            StrictModeLifecycleState.DeactivationCounting -> StrictModeDeactivationCountdown(
                state = state,
                pendingAction = pendingAction,
                onCancelDeactivation = onCancelDeactivation,
                onCountdownComplete = onTick
            )
            StrictModeLifecycleState.DeactivationReady -> if (pendingAction?.actionType == PendingStrictModeActionType.ReplaceProtectionMethod) {
                StrictModeMethodChangeConfirmation(pendingAction, onConfirmProtectedAction, onCancelProtectedRequest)
            } else StrictModeDeactivateConfirmation(
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
        ChargerMethodCard(setup, onSetupChange)
        PinMethodCard(setup, onSetupChange)
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
    val selected = setup.protectionMethod == StrictModeProtectionMethod.Countdown
    Surface(
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton) {
            onSetupChange(setup.copy(protectionMethod = StrictModeProtectionMethod.Countdown))
        },
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .12f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SymbolBadge(symbol = "\u23F1", selected = selected)
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
            if (selected) Text(text = "Wait time", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            if (selected) {
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
}

@Composable
private fun ChargerMethodCard(setup: StrictModeSetupState, onSetupChange: (StrictModeSetupState) -> Unit) {
    val selected = setup.protectionMethod == StrictModeProtectionMethod.Charger
    Surface(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton) {
            onSetupChange(setup.copy(
                protectionMethod = StrictModeProtectionMethod.Charger,
                pin = "",
                pinConfirmation = ""
            ))
        },
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .12f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SymbolBadge(symbol = "\u26A1", selected = selected)
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Charger", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "Connect your phone to a charger before weakening or disabling Strict Mode.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PinMethodCard(setup: StrictModeSetupState, onSetupChange: (StrictModeSetupState) -> Unit) {
    val selected = setup.protectionMethod == StrictModeProtectionMethod.Pin
    Surface(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton) {
            val duration = setup.deactivationCountdownMillis.takeIf { current ->
                pinCountdownPresets().any { it.second == current }
            } ?: pinCountdownPresets().first().second
            onSetupChange(setup.copy(
                protectionMethod = StrictModeProtectionMethod.Pin,
                deactivationCountdownMillis = duration,
                countdownCustomVisible = false
            ))
        },
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .12f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SymbolBadge(symbol = "#", selected = selected)
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Enter PIN", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "Verify your PIN before the deactivation countdown begins.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (selected) {
                OutlinedTextField(
                    value = setup.pin,
                    onValueChange = { onSetupChange(setup.copy(pin = it.numericPinInput())) },
                    label = { Text("Create PIN") },
                    supportingText = { Text("4–8 digits") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = setup.pinConfirmation,
                    onValueChange = { onSetupChange(setup.copy(pinConfirmation = it.numericPinInput())) },
                    label = { Text("Confirm PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = setup.pinConfirmation.isNotEmpty() && setup.pin != setup.pinConfirmation,
                    supportingText = {
                        if (setup.pinConfirmation.isNotEmpty() && setup.pin != setup.pinConfirmation) Text("PINs do not match")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(text = "Deactivation wait", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pinCountdownPresets().forEach { (label, millis) ->
                        ChoiceChip(
                            label = label,
                            selected = setup.deactivationCountdownMillis == millis && !setup.countdownCustomVisible,
                            onClick = { onSetupChange(setup.copy(deactivationCountdownMillis = millis, countdownCustomVisible = false)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                ChoiceChip(
                    label = "Custom",
                    selected = setup.countdownCustomVisible,
                    onClick = { if (!setup.countdownCustomVisible) onSetupChange(setup.chooseCustomCountdown()) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (setup.countdownCustomVisible) {
                    CustomDurationInput(
                        value = setup.customCountdownMinutes,
                        label = "Custom minutes",
                        maxValue = StrictModeStore.MAX_DEACTIVATION_COUNTDOWN_MILLIS / 60_000L,
                        onValueChange = { onSetupChange(setup.editCustomCountdownDraft(it)) },
                        onUse = { minutes -> onSetupChange(setup.copy(
                            deactivationCountdownMillis = minutes * 60_000L,
                            customCountdownMinutes = minutes.toString(),
                            countdownCustomVisible = true
                        )) }
                    )
                }
            }
        }
    }
}

private fun String.numericPinInput(): String = filter(Char::isDigit).take(8)

internal fun pinCountdownPresets() = listOf(
    "1 hr" to 60L * 60_000L,
    "24 hrs" to 24L * 60L * 60_000L,
    "7 days" to 7L * 24L * 60L * 60_000L
)

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
    setup: StrictModeSetupState,
    onBack: () -> Unit,
    onActivate: () -> Unit
) {
    val configuration = setup.toConfiguration()
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
            symbol = if (setup.protectionMethod == StrictModeProtectionMethod.Charger) "\u26A1" else "#",
            label = "Turn off with",
            value = if (setup.protectionMethod == StrictModeProtectionMethod.Charger) "Plug in charger" else "Enter PIN",
            supportingValue = if (setup.protectionMethod == StrictModeProtectionMethod.Charger) "Physical charger required" else "${durationLabel(configuration.deactivationCountdownMillis)} wait after verification"
        )
    }
    if (setup.protectionMethod == StrictModeProtectionMethod.Charger) StrictModeCard {
        Text(text = "All Rules will be protected.", style = MaterialTheme.typography.titleMedium)
        Text(text = "1. Connect your phone to a charger.\n2. Press Continue.\n3. Confirm the requested change.")
    } else if (setup.protectionMethod == StrictModeProtectionMethod.Pin) StrictModeCard {
        Text(text = "All Rules will be protected.", style = MaterialTheme.typography.titleMedium)
        Text(text = "1. Enter your PIN once.\n2. Wait for the countdown.\n3. Strict Mode turns off automatically.")
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
    protectionMethod: StrictModeProtectionMethod,
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
                    text = when (protectionMethod) {
                        StrictModeProtectionMethod.Charger -> "After activation, all Rules are protected. A charger will be required for protected changes."
                        StrictModeProtectionMethod.Pin -> "After activation, all Rules are protected. Your PIN starts a ${durationLabel(deactivationWaitMillis)} deactivation countdown."
                        else -> "After activation, all Rules are protected from weaker edits, pausing, and deletion. Countdown \u00B7 ${durationLabel(deactivationWaitMillis)}."
                    },
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
    configuration: GlobalStrictModeConfiguration?,
    chargingState: ChargingState,
    onRequestMethodChange: (StrictModeProtectionMethod, Long?, String?) -> Unit,
    onBeginDeactivation: () -> Unit,
    onConfirmChargerDeactivation: () -> PendingActionValidation
) {
    var beginDeactivationDialogOpen by remember { mutableStateOf(false) }
    var chargerStatusMessage by remember { mutableStateOf<String?>(null) }
    var changeMethodOpen by remember { mutableStateOf(false) }
    var replacementPin by remember { mutableStateOf("") }
    var replacementPinConfirmation by remember { mutableStateOf("") }
    var replacementWait by remember { mutableStateOf(pinCountdownPresets().first().second) }
    val ui = strictModeActiveUiState(state, configuration)
    val chargerProtection = configuration?.protectionMethod == StrictModeProtectionMethod.Charger
    val chargerButtonUi = chargerDeactivationButtonUi(chargingState.isActivelyCharging)
    LaunchedEffect(chargingState.isActivelyCharging, beginDeactivationDialogOpen) {
        if (chargerProtection && beginDeactivationDialogOpen && !chargingState.isActivelyCharging) {
            beginDeactivationDialogOpen = false
            chargerStatusMessage = "Connect a charger to continue."
        }
    }
    StrictModeCard {
        Text(text = ui.title, style = MaterialTheme.typography.titleLarge)
        Text(text = ui.description)
        HorizontalDivider()
        Text(text = ui.protectionMethod, style = MaterialTheme.typography.titleMedium)
        Text(text = ui.deactivationWait, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (chargerProtection && !chargingState.isActivelyCharging) {
            Text(
                text = chargerStatusMessage ?: "Waiting for charger",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Button(
            onClick = { beginDeactivationDialogOpen = true; chargerStatusMessage = null },
            enabled = !chargerProtection || chargerButtonUi.enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = when {
                    !chargerProtection -> "Begin Deactivation"
                    else -> chargerButtonUi.label
                }
            )
        }
        OutlinedButton(onClick = { changeMethodOpen = !changeMethodOpen }, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Change Protection Method")
        }
    }
    if (changeMethodOpen) StrictModeCard {
        Text(text = "Protection method", style = MaterialTheme.typography.titleMedium)
        if (configuration?.protectionMethod != StrictModeProtectionMethod.Charger) {
            OutlinedButton(
                onClick = { changeMethodOpen = false; onRequestMethodChange(StrictModeProtectionMethod.Charger, null, null) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(text = "Plug in charger") }
        }
        if (configuration?.protectionMethod != StrictModeProtectionMethod.Pin) {
            Text(text = "Enter PIN", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = replacementPin,
                onValueChange = { replacementPin = it.numericPinInput() },
                label = { Text("Create PIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = replacementPinConfirmation,
                onValueChange = { replacementPinConfirmation = it.numericPinInput() },
                label = { Text("Confirm PIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                isError = replacementPinConfirmation.isNotEmpty() && replacementPin != replacementPinConfirmation,
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pinCountdownPresets().forEach { (label, millis) ->
                    ChoiceChip(label, replacementWait == millis, { replacementWait = millis }, Modifier.weight(1f))
                }
            }
            Button(
                onClick = {
                    onRequestMethodChange(StrictModeProtectionMethod.Pin, replacementWait, replacementPin)
                    replacementPin = ""
                    replacementPinConfirmation = ""
                    changeMethodOpen = false
                },
                enabled = replacementPin.length in 4..8 && replacementPin == replacementPinConfirmation,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Use Enter PIN") }
        }
        Text(
            text = "Reducing or replacing protection requires your current method first.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (beginDeactivationDialogOpen && chargerProtection) {
        AlertDialog(
            onDismissRequest = { beginDeactivationDialogOpen = false },
            title = { Text("Disable Strict Mode?") },
            text = { Text("Your Rules will allow weaker edits, pausing, and deletion without charger authorization.") },
            confirmButton = {
                TextButton(onClick = {
                    beginDeactivationDialogOpen = false
                    when (val result = onConfirmChargerDeactivation()) {
                        is PendingActionValidation.Valid -> chargerStatusMessage = null
                        is PendingActionValidation.Invalid -> chargerStatusMessage = result.message
                    }
                }) { Text("Disable") }
            },
            dismissButton = {
                TextButton(onClick = { beginDeactivationDialogOpen = false }) { Text("Stay Focused") }
            }
        )
    } else if (beginDeactivationDialogOpen) {
        BeginDeactivationDialog(
            method = configuration?.protectionMethod ?: StrictModeProtectionMethod.Countdown,
            waitLabel = durationLabel(state.configuration.deactivationCountdownMillis),
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

internal data class ChargerDeactivationButtonUi(val label: String, val enabled: Boolean)

internal fun chargerDeactivationButtonUi(isActivelyCharging: Boolean) = ChargerDeactivationButtonUi(
    label = if (isActivelyCharging) "Disable Strict Mode" else "Connect charger to deactivate",
    enabled = isActivelyCharging
)

internal fun strictModeActiveUiState(state: StrictModeState, configuration: GlobalStrictModeConfiguration? = null) = StrictModeActiveUiState(
    title = "Strict Mode is active",
    description = "All Rules are protected from weaker edits, pausing, and deletion.",
    protectionMethod = when (configuration?.protectionMethod) {
        StrictModeProtectionMethod.Charger -> "Charger"
        StrictModeProtectionMethod.Pin -> "Enter PIN"
        else -> "Countdown"
    },
    deactivationWait = when (configuration?.protectionMethod) {
        StrictModeProtectionMethod.Charger -> "A charger is required to disable Strict Mode or make protected changes."
        StrictModeProtectionMethod.Pin -> "PIN verification, then a ${durationLabel(state.configuration.deactivationCountdownMillis)} wait"
        else -> "${durationLabel(state.configuration.deactivationCountdownMillis)} deactivation wait"
    }
)

@Composable
private fun BeginDeactivationDialog(
    method: StrictModeProtectionMethod,
    waitLabel: String,
    onStartCountdown: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(text = "Begin deactivation?") },
        text = {
            Text(
                text = when (method) {
                    StrictModeProtectionMethod.Charger -> "Connect your phone to a charger, press Continue, then confirm. Strict Mode stays active until you confirm."
                    StrictModeProtectionMethod.Pin -> "Enter your PIN once, then Strict Mode will remain active across all Rules for the next $waitLabel."
                    else -> "Strict Mode will remain active across all Rules for the next $waitLabel."
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onStartCountdown) {
                Text(text = if (method == StrictModeProtectionMethod.Charger) "Continue" else if (method == StrictModeProtectionMethod.Pin) "Enter PIN" else "Start Countdown")
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
    pendingAction: PendingStrictModeAction? = null,
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
    val replacingMethod = pendingAction?.actionType == PendingStrictModeActionType.ReplaceProtectionMethod
    StrictModeCard {
        Text(text = if (replacingMethod) "Protection change in progress" else "Deactivation in progress", style = MaterialTheme.typography.titleLarge)
        Text(text = "Strict Mode remains active for:", style = MaterialTheme.typography.titleSmall)
        Text(
            text = strictModeTimerLabel(deadline, nowMillis),
            style = MaterialTheme.typography.displaySmall
        )
        Text(
            text = if (pendingAction?.authorizationMethod == StrictModeProtectionMethod.Pin && !replacingMethod) {
                "You can leave EarnIt. The countdown will continue, and Strict Mode will turn off automatically at zero."
            } else "You can leave EarnIt. The countdown will continue. No change will be made until you confirm.",
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedButton(onClick = onCancelDeactivation, modifier = Modifier.fillMaxWidth()) {
            Text(text = if (replacingMethod) "Cancel Request" else "Cancel Deactivation")
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
private fun StrictModeMethodChangeConfirmation(
    action: PendingStrictModeAction,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val replacement = action.descriptor as? StrictModeActionDescriptor.ReplaceMethod
    StrictModeCard {
        Text(text = "Review protection change", style = MaterialTheme.typography.titleLarge)
        Text(text = when (replacement?.newMethod) {
            StrictModeProtectionMethod.Charger -> "Strict Mode will remain active. The protection method will change to Charger."
            StrictModeProtectionMethod.Pin -> "Strict Mode will remain active. The protection method will change to Enter PIN with a ${durationLabel(replacement.newDurationMillis)} deactivation wait."
            else -> "Strict Mode will remain active. The protection method will change to Countdown \u00B7 ${durationLabel(replacement?.newDurationMillis)}."
        })
        Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) { Text(text = "Confirm Change") }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(text = "Cancel Request") }
    }
}

@Composable
private fun PinAuthorization(
    action: PendingStrictModeAction,
    onVerify: (String) -> PinVerificationResult,
    onCancel: () -> Unit
) {
    var pin by remember(action.id) { mutableStateOf("") }
    var error by remember(action.id) { mutableStateOf<String?>(null) }
    var forgotPinOpen by remember { mutableStateOf(false) }
    StrictModeCard {
        Text(text = "Enter your PIN", style = MaterialTheme.typography.titleLarge)
        Text(text = "Enter the PIN you created to begin the Strict Mode deactivation process.")
        OutlinedTextField(
            value = pin,
            onValueChange = {
                pin = it.numericPinInput()
                error = null
            },
            label = { Text("PIN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = error != null,
            supportingText = { error?.let { Text(it) } },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                when (val result = onVerify(pin)) {
                    PinVerificationResult.Incorrect -> {
                        pin = ""
                        error = "Incorrect PIN. Try again."
                    }
                    is PinVerificationResult.Rejected -> {
                        pin = ""
                        error = result.message
                    }
                    is PinVerificationResult.Verified -> pin = ""
                }
            },
            enabled = pin.length in 4..8,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Verify PIN") }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        TextButton(onClick = { forgotPinOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("Forgot PIN?") }
    }
    if (forgotPinOpen) AlertDialog(
        onDismissRequest = { forgotPinOpen = false },
        title = { Text("Forgot PIN?") },
        text = { Text("To preserve the purpose of Strict Mode, forgotten PINs cannot be reset from within the app. You'll need to reinstall EarnIt.") },
        confirmButton = { TextButton(onClick = { forgotPinOpen = false }) { Text("Understood") } }
    )
}

@Composable
private fun ChargerAuthorization(
    session: ChargerAuthorizationSession,
    chargingState: ChargingState,
    action: PendingStrictModeAction,
    onAuthorize: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onKeepStrictModeActive: () -> Unit
) {
    val isGlobalDeactivation = action.actionType == PendingStrictModeActionType.DisableStrictMode
    if (session.state == ChargerAuthorizationState.Authorized) {
        ChargerFinalConfirmation(action, onConfirm, {}, onKeepStrictModeActive, onCancel)
        return
    }
    when (session.state) {
        ChargerAuthorizationState.WaitingForCharger -> StrictModeCard {
            Text(text = "Charger required", style = MaterialTheme.typography.titleLarge)
            DecorativeSymbol(text = "\u26A1", style = MaterialTheme.typography.displaySmall)
            Text(text = "Connect your charger", style = MaterialTheme.typography.titleMedium)
            Text(text = "Plug in your phone to continue with this change.")
            Text(text = "Waiting for charger", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(text = "The button will become available once charging is detected.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text(text = "Connect charger to continue") }
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(text = "Cancel Request") }
        }
        ChargerAuthorizationState.Ready -> StrictModeCard {
            Text(text = "Charger connected", style = MaterialTheme.typography.titleLarge)
            DecorativeSymbol(text = "\u26A1", style = MaterialTheme.typography.displaySmall)
            Text(text = "You can now continue.")
            Text(text = "Charging detected", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Button(onClick = onAuthorize, enabled = chargingState.isActivelyCharging, modifier = Modifier.fillMaxWidth()) {
                Text(text = chargerContinueLabel(action.actionType))
            }
            OutlinedButton(
                onClick = if (isGlobalDeactivation) onKeepStrictModeActive else onCancel,
                modifier = Modifier.fillMaxWidth()
            ) { Text(text = if (isGlobalDeactivation) "Keep Strict Mode Active" else "Cancel Request") }
        }
        ChargerAuthorizationState.Expired -> StrictModeCard {
            Text(text = "Request expired", style = MaterialTheme.typography.titleLarge)
            Text(text = "No change was made. Start the protected action again when you're ready.")
            Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(text = "Close") }
        }
        ChargerAuthorizationState.Invalid -> StrictModeCard {
            Text(text = "Charger authorization needs attention", style = MaterialTheme.typography.titleLarge)
            Text(text = "The request could not be verified. No change was made, and Strict Mode remains active.")
            Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(text = "Close") }
        }
        ChargerAuthorizationState.Cancelled, ChargerAuthorizationState.Consumed -> StrictModeCard {
            Text(text = "Request closed", style = MaterialTheme.typography.titleLarge)
            Text(text = "This request can no longer be used.")
            Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(text = "Close") }
        }
        ChargerAuthorizationState.Authorized -> Unit
    }
}

internal fun chargerContinueLabel(type: PendingStrictModeActionType) = when (type) {
    PendingStrictModeActionType.UpdateRule, PendingStrictModeActionType.ReplaceProtectionMethod -> "Review Change"
    PendingStrictModeActionType.PauseRule -> "Continue to Pause"
    PendingStrictModeActionType.DeleteRule -> "Continue to Delete"
    PendingStrictModeActionType.DisableStrictMode -> "Continue to Disable"
}

@Composable
private fun ChargerFinalConfirmation(
    action: PendingStrictModeAction,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
    onKeepStrictModeActive: () -> Unit,
    onCancel: () -> Unit
) {
    val (title, description, confirmLabel) = when (action.actionType) {
        PendingStrictModeActionType.DisableStrictMode -> Triple(
            "Disable Strict Mode?",
            "All Rules will allow weaker edits, pausing, and deletion without authorization.",
            "Disable Strict Mode"
        )
        PendingStrictModeActionType.UpdateRule -> Triple("Review Rule change", "Apply only the weaker Rule change saved with this request?", "Confirm Change")
        PendingStrictModeActionType.PauseRule -> Triple("Pause this Rule?", "Only the Rule named in this request will be paused.", "Pause Rule")
        PendingStrictModeActionType.DeleteRule -> Triple("Delete this Rule?", "Only the Rule named in this request will be deleted.", "Delete Rule")
        PendingStrictModeActionType.ReplaceProtectionMethod -> Triple("Change protection method?", "Strict Mode stays active with the selected replacement method.", "Confirm Change")
    }
    StrictModeCard {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Text(text = description)
        Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) { Text(text = confirmLabel) }
        if (action.actionType == PendingStrictModeActionType.DisableStrictMode) {
            OutlinedButton(onClick = onKeepStrictModeActive, modifier = Modifier.fillMaxWidth()) { Text(text = "Keep Strict Mode Active") }
        } else {
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(text = "Back") }
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(text = "Cancel Request") }
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
    val protectionMethod: StrictModeProtectionMethod = StrictModeProtectionMethod.Countdown,
    val customTimedHours: String = "",
    val customCountdownMinutes: String = "",
    val timedCustomVisible: Boolean = false,
    val countdownCustomVisible: Boolean = false,
    val pin: String = "",
    val pinConfirmation: String = ""
) {
    val timedDurationValid: Boolean
        get() = durationType == StrictModeDurationType.Indefinite ||
            (timedDurationMillis ?: 0L) in 1L..StrictModeStore.MAX_TIMED_DURATION_MILLIS

    val countdownDurationValid: Boolean
        get() = (deactivationCountdownMillis ?: 0L) in 1L..StrictModeStore.MAX_DEACTIVATION_COUNTDOWN_MILLIS

    val isValid: Boolean
        get() {
            val methodValid = when (protectionMethod) {
                StrictModeProtectionMethod.Countdown -> countdownDurationValid
                StrictModeProtectionMethod.Charger -> true
                StrictModeProtectionMethod.Pin -> countdownDurationValid && pin.length in 4..8 && pin.all(Char::isDigit) && pin == pinConfirmation
            }
            return timedDurationValid && methodValid
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
