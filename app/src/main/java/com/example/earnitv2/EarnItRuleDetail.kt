package com.example.earnitv2

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DateRange
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

internal enum class RuleDetailTone {
    Active,
    Paused
}

internal enum class RuleDetailOverflowAction {
    Edit,
    QuickPause,
    MorePauseOptions,
    StrictMode,
    Delete
}

private enum class EditGateState {
    Hidden,
    Confirm,
    Counting,
    Complete
}

private enum class PauseSheetState {
    Hidden,
    Options,
    Counting,
    Reason
}

internal data class PauseOption(
    val label: String,
    val durationMillis: Long
)

internal data class RuleDetailStatusCardState(
    val title: String,
    val metric: String,
    val stateLabel: String?,
    val body: String,
    val tone: RuleDetailTone,
    val showResume: Boolean
)

@Composable
internal fun EarnItRuleDetail(
    homeRule: HomeRuleUiState,
    detail: RuleDetailUiState,
    pausedUntilMillis: Long?,
    permissionState: PermissionSetupUiState,
    onBack: () -> Unit,
    onOpenEarnApp: (String) -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onEditRule: (EarnItRuleStore.Rule) -> Unit,
    onPauseRuleFor: (EarnItRuleStore.Rule, Long, String?) -> Unit,
    onResumeRule: (EarnItRuleStore.Rule) -> Unit,
    onToggleDailyCommitment: (EarnItRuleStore.Rule, Boolean) -> Unit,
    isProtectedByStrictMode: Boolean,
    strictModeConfiguration: GlobalStrictModeConfiguration?,
    onOpenStrictMode: () -> Unit,
    onStrictModeTick: () -> Unit,
    onProtectedActionBlocked: () -> Unit,
    onDeleteRule: (EarnItRuleStore.Rule) -> Unit,
    modifier: Modifier = Modifier
) {
    val rule = homeRule.rule
    var nowMillis by remember(rule.id) { mutableStateOf(System.currentTimeMillis()) }
    var editGateState by remember(rule.id) { mutableStateOf(EditGateState.Hidden) }
    var editCountdownSeconds by remember(rule.id) { mutableStateOf(10) }
    var pauseSheetState by remember(rule.id) { mutableStateOf(PauseSheetState.Hidden) }
    var selectedPauseOption by remember(rule.id) { mutableStateOf<PauseOption?>(null) }
    var pauseReason by remember(rule.id) { mutableStateOf<String?>(null) }
    var otherPauseReason by remember(rule.id) { mutableStateOf("") }
    var pauseCountdownSeconds by remember(rule.id) { mutableStateOf(10) }
    var customPauseMinutes by remember(rule.id) { mutableStateOf("45") }

    LaunchedEffect(pausedUntilMillis) {
        while (pausedUntilMillis != null && pausedUntilMillis > System.currentTimeMillis()) {
            nowMillis = System.currentTimeMillis()
            delay(1_000)
        }
        nowMillis = System.currentTimeMillis()
    }
    LaunchedEffect(editGateState) {
        if (editGateState == EditGateState.Counting) {
            editCountdownSeconds = 10
            while (editCountdownSeconds > 0 && editGateState == EditGateState.Counting) {
                delay(1_000)
                editCountdownSeconds -= 1
            }
            if (editGateState == EditGateState.Counting) editGateState = EditGateState.Complete
        }
    }
    LaunchedEffect(pauseSheetState, selectedPauseOption) {
        if (pauseSheetState == PauseSheetState.Counting && selectedPauseOption != null) {
            pauseCountdownSeconds = 10
            while (pauseCountdownSeconds > 0 && pauseSheetState == PauseSheetState.Counting) {
                delay(1_000)
                pauseCountdownSeconds -= 1
            }
            if (pauseSheetState == PauseSheetState.Counting) pauseSheetState = PauseSheetState.Reason
        }
    }

    val statusState = ruleDetailStatusCardState(
        rule = rule,
        availableRewardTimeLabel = detail.card.availableRewardTimeLabel,
        isActiveNow = rule.enabled && rule.isActiveNow(),
        pauseCountdownLabel = pausedUntilMillis?.let { pauseCountdownLabel(it, nowMillis) }
    )
    val logicalBack = {
        when {
            editGateState != EditGateState.Hidden -> editGateState = EditGateState.Hidden
            pauseSheetState != PauseSheetState.Hidden -> pauseSheetState = PauseSheetState.Hidden
            else -> onBack()
        }
    }
    BackHandler(onBack = logicalBack)

    if (editGateState != EditGateState.Hidden) {
        RuleManagementSurface(
            title = "Edit Rule",
            onBack = logicalBack,
            modifier = modifier
        ) {
            EditRuleGateCard(
                state = editGateState,
                countdownSeconds = editCountdownSeconds,
                onStartCountdown = { editGateState = EditGateState.Counting },
                onContinue = { onEditRule(rule) },
                onCancel = { editGateState = EditGateState.Hidden }
            )
        }
        return
    }

    if (pauseSheetState != PauseSheetState.Hidden) {
        RuleManagementSurface(
            title = "Pause this Rule?",
            onBack = logicalBack,
            modifier = modifier
        ) {
            PauseOptionsCard(
                rule = rule,
                state = pauseSheetState,
                selectedOption = selectedPauseOption,
                reason = pauseReason,
                otherReason = otherPauseReason,
                countdownSeconds = pauseCountdownSeconds,
                customPauseMinutes = customPauseMinutes,
                onCustomPauseMinutesChange = { customPauseMinutes = it.filter(Char::isDigit).take(4) },
                onSelectOption = { option -> selectedPauseOption = option },
                onContinueFromDuration = { pauseSheetState = PauseSheetState.Counting },
                onUseCustomOption = {
                    val minutes = customPauseMinutes.toLongOrNull()?.coerceAtLeast(1L) ?: 0L
                    if (minutes > 0L) selectedPauseOption = PauseOption("Custom duration", minutes * 60_000L)
                },
                onSelectReason = { pauseReason = it },
                onOtherReasonChange = { otherPauseReason = it.take(120) },
                onConfirmPause = {
                    selectedPauseOption?.let { onPauseRuleFor(rule, it.durationMillis, pauseReasonValue(pauseReason, otherPauseReason)) }
                    pauseSheetState = PauseSheetState.Hidden
                },
                onCancel = { pauseSheetState = PauseSheetState.Hidden }
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        RuleDetailTopBar(
            rule = rule,
            onBack = logicalBack,
            onEditRule = { editGateState = EditGateState.Confirm },
            onQuickPause = { onPauseRuleFor(rule, FIVE_MINUTES_MILLIS, null) },
            onMorePauseOptions = { pauseSheetState = PauseSheetState.Options },
            onOpenStrictMode = onOpenStrictMode,
            onDeleteRule = onDeleteRule
        )

        if (permissionState.needsAttention) {
            RuleDetailAttention(
                rule = rule,
                permissionState = permissionState,
                onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings
            )
        }

        RuleStrictModeStatusRow(strictModeConfiguration, onOpenStrictMode, onStrictModeTick)

        RuleStatusCard(
            ruleType = rule.type,
            state = statusState,
            onResume = { onResumeRule(rule) }
        )

        when (rule.type) {
            EarnItRuleStore.RuleType.EarnRewardTime -> EarnRewardTimeDetailCard(
                rule = rule,
                detail = detail,
                productiveUsageSeconds = homeRulePrimaryUsageSeconds(homeRule),
                onOpenEarnApp = onOpenEarnApp,
                isProtectedByStrictMode = isProtectedByStrictMode,
                onProtectedActionBlocked = onProtectedActionBlocked
            )
            EarnItRuleStore.RuleType.CompleteToUnlock -> CompleteToUnlockDetailCard(
                rule = rule,
                progress = homeRule.completeToUnlockProgress
                    ?: completeToUnlockProgressUiState(rule, emptyMap()),
                rewardApps = detail.card.rewardApps,
                onOpenRequirementApp = onOpenEarnApp,
                onToggleDailyCommitment = { enabled -> onToggleDailyCommitment(rule, enabled) }
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
private fun RuleStrictModeStatusRow(
    configuration: GlobalStrictModeConfiguration?,
    onOpenStrictMode: () -> Unit,
    onStrictModeTick: () -> Unit
) {
    val deadline = configuration?.deactivationAvailableAtMillis
    var nowMillis by remember(deadline) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(configuration?.lifecycle, deadline) {
        while (configuration?.lifecycle == RuleStrictModeLifecycle.DeactivationCounting && deadline != null) {
            val now = System.currentTimeMillis()
            nowMillis = now
            val remaining = strictModeRemainingMillis(deadline, now)
            if (remaining == 0L) {
                onStrictModeTick()
                break
            }
            delay(remaining.coerceAtMost(1_000L))
        }
    }
    val baseStatus = ruleStrictModeStatusUi(configuration)
    val status = if (configuration?.lifecycle == RuleStrictModeLifecycle.DeactivationCounting) {
        baseStatus.copy(detail = "${strictModeTimerLabel(deadline, nowMillis)} remaining")
    } else baseStatus
    SectionContainer(
        modifier = Modifier
            .clickable(onClick = onOpenStrictMode)
            .semantics(mergeDescendants = true) {
                contentDescription = "${status.title}. ${status.detail}. ${status.actionLabel}"
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "\u25C7", modifier = Modifier.clearAndSetSemantics { }, style = MaterialTheme.typography.titleLarge)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = status.title, style = MaterialTheme.typography.titleSmall)
                Text(text = status.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(text = status.actionLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

internal data class RuleStrictModeStatusUi(val title: String, val detail: String, val actionLabel: String)

internal fun ruleStrictModeStatusUi(
    configuration: GlobalStrictModeConfiguration?,
    formatActivationTime: (Long) -> String = { SimpleDateFormat("MMM d 'at' h:mm a", Locale.getDefault()).format(Date(it)) }
): RuleStrictModeStatusUi {
    when (configuration?.lifecycle ?: RuleStrictModeLifecycle.Disabled) {
        RuleStrictModeLifecycle.Disabled -> return RuleStrictModeStatusUi(
            "Strict Mode", "Protect all Rules from weaker changes.", "Set up"
        )
        RuleStrictModeLifecycle.PendingActivation -> {
            val date = configuration?.activeFromMillis?.let(formatActivationTime) ?: "the saved activation time"
            return RuleStrictModeStatusUi("Strict Mode starts soon", "All Rules will be protected at $date.", "View")
        }
        RuleStrictModeLifecycle.Active -> return if (configuration?.protectionMethod == StrictModeProtectionMethod.Countdown) {
            RuleStrictModeStatusUi(
                "Strict Mode is active",
                "All Rules are protected.",
                "View"
            )
        } else {
            RuleStrictModeStatusUi(
                "Strict Mode is active",
                "Charger required for protected changes",
                "View"
            )
        }
        RuleStrictModeLifecycle.DeactivationCounting -> return if (configuration?.protectionMethod == StrictModeProtectionMethod.Charger) {
            RuleStrictModeStatusUi("Deactivation in progress", "Waiting for charger", "View")
        } else RuleStrictModeStatusUi(
            "Deactivation in progress", "Strict Mode remains active while the countdown continues.", "View"
        )
        RuleStrictModeLifecycle.DeactivationReady -> return RuleStrictModeStatusUi(
            "Deactivation ready", "Confirmation required.", "View"
        )
        RuleStrictModeLifecycle.Invalid -> return RuleStrictModeStatusUi(
            "Strict Mode needs attention", "Your Rules remain protected.", "Review"
        )
    }
}

@Composable
private fun RuleDetailTopBar(
    rule: EarnItRuleStore.Rule,
    onBack: () -> Unit,
    onEditRule: (EarnItRuleStore.Rule) -> Unit,
    onQuickPause: () -> Unit,
    onMorePauseOptions: () -> Unit,
    onOpenStrictMode: () -> Unit,
    onDeleteRule: (EarnItRuleStore.Rule) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        EarnItBackButton(onBack)
        Text(text = "Rule Detail", style = MaterialTheme.typography.headlineSmall)
        RuleDetailOverflowMenu(
            actions = ruleDetailOverflowActions(rule),
            onEdit = { onEditRule(rule) },
            onQuickPause = onQuickPause,
            onMorePauseOptions = onMorePauseOptions,
            onOpenStrictMode = onOpenStrictMode,
            onDelete = { onDeleteRule(rule) }
        )
    }
}

@Composable
private fun RuleDetailOverflowMenu(
    actions: List<RuleDetailOverflowAction>,
    onEdit: () -> Unit,
    onQuickPause: () -> Unit,
    onMorePauseOptions: () -> Unit,
    onOpenStrictMode: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = "More options" }
        ) {
            Text(text = "⋮")
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
                            RuleDetailOverflowAction.QuickPause -> onQuickPause()
                            RuleDetailOverflowAction.MorePauseOptions -> onMorePauseOptions()
                            RuleDetailOverflowAction.StrictMode -> onOpenStrictMode()
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
    rule: EarnItRuleStore.Rule,
    permissionState: PermissionSetupUiState,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    SectionContainer {
        Text(text = "EarnIt needs attention", style = MaterialTheme.typography.titleSmall)
        Text(
            text = if (rule.normalizedBlockedDomains.isNotEmpty() &&
                permissionState.appBlockingStatus == EarnItPermissionStatus.NeedsAttention
            ) {
                "Accessibility access is off, so this Rule's websites are not currently protected."
            } else permissionState.repairTargetLabels.joinToString(" and ") + " needs setup",
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
    ruleType: EarnItRuleStore.RuleType,
    state: RuleDetailStatusCardState,
    onResume: () -> Unit
) {
    val accent = ruleDetailAccentColor(state.tone)
    SectionContainer(borderColor = accent) {
        RuleTypeBadge(ruleType = ruleType, iconSize = 26.dp)
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
            Button(
                onClick = onResume,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Resume Rule" }
            ) {
                Text(text = "Resume now")
            }
        }
    }
}

@Composable
private fun RuleManagementSurface(
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EarnItBackButton(onBack)
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
            Box(modifier = Modifier.size(64.dp))
        }
        content()
    }
}

@Composable
internal fun EarnItBackButton(onBack: () -> Unit) {
    TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(text = EARNIT_BACK_LABEL)
    }
}

internal const val EARNIT_BACK_LABEL = "< Back"

@Composable
private fun EditRuleGateCard(
    state: EditGateState,
    countdownSeconds: Int,
    onStartCountdown: () -> Unit,
    onContinue: () -> Unit,
    onCancel: () -> Unit
) {
    SectionContainer {
        Text(text = "Edit this Rule?", style = MaterialTheme.typography.titleLarge)
        when (state) {
            EditGateState.Confirm -> {
                Text(
                    text = "Editing is available after a short wait to help prevent accidental or impulsive changes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onStartCountdown, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Continue to edit")
                }
            }
            EditGateState.Counting -> {
                CountdownBlock(title = "Edit available in", seconds = countdownSeconds)
                Text(
                    text = "You can still cancel if you change your mind.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Continue to edit")
                }
            }
            EditGateState.Complete -> {
                Text(text = "OK", style = MaterialTheme.typography.headlineMedium, color = ruleDetailAccentColor(RuleDetailTone.Active))
                Text(text = "You can now edit this Rule.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Continue to edit")
                }
            }
            EditGateState.Hidden -> Unit
        }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Cancel")
        }
    }
}

@Composable
private fun PauseOptionsCard(
    rule: EarnItRuleStore.Rule,
    state: PauseSheetState,
    selectedOption: PauseOption?,
    reason: String?,
    otherReason: String,
    countdownSeconds: Int,
    customPauseMinutes: String,
    onCustomPauseMinutesChange: (String) -> Unit,
    onSelectOption: (PauseOption) -> Unit,
    onContinueFromDuration: () -> Unit,
    onUseCustomOption: () -> Unit,
    onSelectReason: (String) -> Unit,
    onOtherReasonChange: (String) -> Unit,
    onConfirmPause: () -> Unit,
    onCancel: () -> Unit
) {
    SectionContainer {
        when (state) {
            PauseSheetState.Options -> {
                Text(text = "Pause this Rule?", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Longer pauses require a short wait and a reason.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                pauseOptionsForRule(rule).forEach { option ->
                    OutlinedButton(onClick = { onSelectOption(option) }, modifier = Modifier.fillMaxWidth()) {
                        Text(text = if (selectedOption == option) "${option.label} selected" else option.label)
                    }
                }
                Text(text = "Custom duration", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customPauseMinutes,
                        onValueChange = onCustomPauseMinutesChange,
                        label = { Text(text = "Minutes") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = onUseCustomOption) {
                        Text(text = "Select")
                    }
                }
                Button(
                    onClick = onContinueFromDuration,
                    enabled = selectedOption != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Continue")
                }
            }
            PauseSheetState.Counting -> {
                Text(text = finalPauseActionLabel(selectedOption), style = MaterialTheme.typography.titleLarge)
                CountdownBlock(title = "Pause available in", seconds = countdownSeconds)
                Text(
                    text = "You can still cancel if you change your mind.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    Text(text = finalPauseActionLabel(selectedOption))
                }
            }
            PauseSheetState.Reason -> {
                Text(text = "Why are you pausing this Rule?", style = MaterialTheme.typography.titleLarge)
                pauseReasonOptions().forEach { option ->
                    OutlinedButton(onClick = { onSelectReason(option) }, modifier = Modifier.fillMaxWidth()) {
                        Text(text = if (reason == option) "$option selected" else option)
                    }
                }
                if (reason == "Other") {
                    OutlinedTextField(
                        value = otherReason,
                        onValueChange = onOtherReasonChange,
                        label = { Text(text = "Optional note") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Button(
                    onClick = onConfirmPause,
                    enabled = reason != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = finalPauseActionLabel(selectedOption))
                }
            }
            PauseSheetState.Hidden -> Unit
        }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Cancel")
        }
    }
}

@Composable
private fun CountdownBlock(title: String, seconds: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
        Text(text = seconds.coerceAtLeast(0).toString(), style = MaterialTheme.typography.headlineLarge)
        Text(text = "seconds", style = MaterialTheme.typography.bodySmall)
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
    onOpenEarnApp: (String) -> Unit,
    isProtectedByStrictMode: Boolean,
    onProtectedActionBlocked: () -> Unit
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
        WebsiteDomainList(rule.normalizedBlockedDomains)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        DeepWorkRuleSetting(rule, isProtectedByStrictMode, onProtectedActionBlocked)
    }
}

@Composable
private fun CompleteToUnlockDetailCard(
    rule: EarnItRuleStore.Rule,
    progress: CompleteToUnlockRuleProgressUiState,
    rewardApps: List<EarnItAppUiState>,
    onOpenRequirementApp: (String) -> Unit,
    onToggleDailyCommitment: (Boolean) -> Unit
) {
    SectionContainer(title = "Complete to unlock") {
        completeToUnlockDetailRequirements(progress).forEach { requirement ->
            AppActionRow(
                app = EarnItAppUiState(requirement.packageName, requirement.name),
                secondaryText = requirement.progressLabel,
                progress = requirement.progressFraction,
                completed = requirement.complete,
                onOpen = { onOpenRequirementApp(requirement.packageName) }
            )
        }
        Text(
            text = "Complete all requirements to unlock the apps below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(text = "Benjamin Franklin Mode", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Complete today's commitment before this Unlock Rule can activate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val status = when {
                    !rule.requiresDailyCommitment -> "Off for this Rule"
                    BenjaminFranklinStore.today(androidx.compose.ui.platform.LocalContext.current) == null -> "Waiting for today's commitment"
                    else -> "Commitment set for today"
                }
                Text(text = status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Switch(checked = rule.requiresDailyCommitment, onCheckedChange = onToggleDailyCommitment)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        AppIconRow(
            title = "Unlocks",
            apps = rewardApps,
            body = "These apps unlock after all requirements are completed."
        )
        WebsiteDomainList(rule.normalizedBlockedDomains)
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
        WebsiteDomainList(rule.normalizedBlockedDomains)
    }
}

@Composable
private fun WebsiteDomainList(domains: List<String>) {
    if (domains.isEmpty()) return
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("Websites", style = MaterialTheme.typography.titleSmall)
        domains.take(6).forEach { domain ->
            Text("🌐  $domain", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (domains.size > 6) Text("+${domains.size - 6} more", style = MaterialTheme.typography.bodySmall)
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
                Icon(
                    imageVector = Icons.Rounded.DateRange,
                    contentDescription = null,
                    tint = ruleDetailAccentColor(if (rule.enabled) RuleDetailTone.Active else RuleDetailTone.Paused)
                )
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
    completed: Boolean = false,
    onOpen: () -> Unit
) {
    Column(
        modifier = Modifier.alpha(if (completed) 0.68f else 1f),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (completed) {
                    Text(
                        text = "✓ Complete",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                OutlinedButton(onClick = onOpen) {
                    Text(text = "Open")
                }
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
        if (rule.enabled) {
            add(RuleDetailOverflowAction.QuickPause)
            add(RuleDetailOverflowAction.MorePauseOptions)
        }
        add(RuleDetailOverflowAction.StrictMode)
        add(RuleDetailOverflowAction.Delete)
    }
}

internal fun ruleDetailOverflowActionLabel(action: RuleDetailOverflowAction): String {
    return when (action) {
        RuleDetailOverflowAction.Edit -> "Edit Rule"
        RuleDetailOverflowAction.QuickPause -> "Pause for 5 minutes"
        RuleDetailOverflowAction.MorePauseOptions -> "More pause options"
        RuleDetailOverflowAction.StrictMode -> "Strict Mode"
        RuleDetailOverflowAction.Delete -> "Delete Rule"
    }
}

internal fun ruleDetailStatusCardState(
    rule: EarnItRuleStore.Rule,
    availableRewardTimeLabel: String,
    isActiveNow: Boolean,
    pauseCountdownLabel: String? = null
): RuleDetailStatusCardState {
    if (rule.inactiveReason == RuleInactiveReason.PremiumExpired) {
        return RuleDetailStatusCardState(
            title = "Premium inactive",
            metric = "Saved",
            stateLabel = null,
            body = "This Rule is saved, but Free supports up to 2 active Rules.",
            tone = RuleDetailTone.Paused,
            showResume = true
        )
    }
    if (!rule.enabled) {
        return RuleDetailStatusCardState(
            title = "Rule paused",
            metric = pauseCountdownLabel ?: pausedStatusMetric(rule, availableRewardTimeLabel),
            stateLabel = if (pauseCountdownLabel != null) "Resumes in" else null,
            body = if (pauseCountdownLabel != null) "This Rule will resume automatically." else pausedStatusBody(rule),
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

internal fun pauseCountdownLabel(expiresAtMillis: Long, nowMillis: Long): String {
    val remainingSeconds = ((expiresAtMillis - nowMillis).coerceAtLeast(0L) + 999L) / 1_000L
    val minutes = remainingSeconds / 60L
    val seconds = remainingSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

internal fun pauseOptionsForRule(rule: EarnItRuleStore.Rule, nowMillis: Long = System.currentTimeMillis()): List<PauseOption> {
    return listOf(
        PauseOption("15 minutes", 15 * 60_000L),
        PauseOption("30 minutes", 30 * 60_000L),
        PauseOption("1 hour", 60 * 60_000L),
        PauseOption("Until next scheduled period", nextScheduledPeriodDelayMillis(rule, nowMillis)),
        PauseOption("Until tomorrow", untilTomorrowDelayMillis(nowMillis))
    )
}

internal fun pauseReasonOptions(): List<String> {
    return listOf(
        "My plans changed unexpectedly",
        "I need to handle something important right now",
        "This Rule does not fit what I need to do today",
        "The Rule may be too strict",
        "Something is not working correctly",
        "Other"
    )
}

internal fun finalPauseActionLabel(option: PauseOption?): String {
    return when (option?.label) {
        null -> "Pause Rule"
        "Until tomorrow" -> "Pause until tomorrow"
        "Until next scheduled period" -> "Pause until next scheduled period"
        "Custom duration" -> "Pause for custom duration"
        else -> "Pause for ${option.label.lowercase()}"
    }
}

internal fun pauseReasonValue(reason: String?, otherReason: String): String? {
    return if (reason == "Other" && otherReason.isNotBlank()) {
        "Other: ${otherReason.trim()}"
    } else {
        reason
    }
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

private fun nextScheduledPeriodDelayMillis(rule: EarnItRuleStore.Rule, nowMillis: Long): Long {
    val calendar = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val today = calendar.toEarnItDay()
    val currentMinute = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    val currentSecond = calendar.get(Calendar.SECOND)
    val currentMillis = calendar.get(Calendar.MILLISECOND)
    var bestDelayMinutes: Long? = null

    for (dayOffset in 0..8) {
        val day = ((today - 1 + dayOffset) % 7) + 1
        if (day !in rule.activeDays) continue
        rule.effectiveTimeWindows.forEach { window ->
            val startMinute = window.startMinute
            val delayMinutes = dayOffset * 1_440L + startMinute - currentMinute
            if (delayMinutes > 0L) {
                bestDelayMinutes = minOf(bestDelayMinutes ?: delayMinutes, delayMinutes)
            }
        }
    }

    val delayMillis = (bestDelayMinutes ?: (24L * 60L)) * 60_000L -
        currentSecond * 1_000L -
        currentMillis
    return delayMillis.coerceAtLeast(60_000L)
}

private fun untilTomorrowDelayMillis(nowMillis: Long): Long {
    val calendar = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val tomorrow = calendar.clone() as Calendar
    tomorrow.add(Calendar.DAY_OF_YEAR, 1)
    tomorrow.set(Calendar.HOUR_OF_DAY, 0)
    tomorrow.set(Calendar.MINUTE, 0)
    tomorrow.set(Calendar.SECOND, 0)
    tomorrow.set(Calendar.MILLISECOND, 0)
    return (tomorrow.timeInMillis - nowMillis).coerceAtLeast(60_000L)
}

private fun Calendar.toEarnItDay(): Int {
    return when (get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        else -> 7
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
private const val FIVE_MINUTES_MILLIS = 5 * 60_000L
