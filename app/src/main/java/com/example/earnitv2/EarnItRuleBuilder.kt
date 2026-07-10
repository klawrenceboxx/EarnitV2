package com.example.earnitv2

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
enum class RuleBuilderStep(val label: String) {
    Earn("Earn"),
    Reward("Reward"),
    Exchange("Exchange"),
    Schedule("Schedule"),
    Review("Review");

    fun previous(steps: List<RuleBuilderStep>): RuleBuilderStep? {
        val index = steps.indexOf(this)
        return steps.getOrNull(index - 1)
    }

    fun next(steps: List<RuleBuilderStep>): RuleBuilderStep? {
        val index = steps.indexOf(this)
        return steps.getOrNull(index + 1)
    }
}

@Composable
fun EarnItRuleBuilder(
    rule: EarnItRuleStore.Rule,
    apps: List<EarnItRuleStore.LaunchableApp>,
    appsLoading: Boolean,
    selectedProductivePackage: String,
    selectedProductivePackages: Set<String>,
    selectedBlockedPackages: Set<String>,
    selectedRequirements: List<EarnItRuleStore.RuleRequirement>,
    requirementPickerOpen: Boolean,
    requirementSearch: String,
    selectedRequirementPackage: String?,
    selectedRequirementMinutes: Int,
    editingRequirementIndex: Int?,
    selectedRatio: Int,
    selectedActiveDays: Set<Int>,
    selectedStartMinute: Int,
    selectedEndMinute: Int,
    productivePickerOpen: Boolean,
    blockedPickerOpen: Boolean,
    productiveSearch: String,
    blockedSearch: String,
    builderStep: RuleBuilderStep,
    onBuilderStepChange: (RuleBuilderStep) -> Unit,
    onOpenProductivePicker: () -> Unit,
    onCloseProductivePicker: () -> Unit,
    onOpenBlockedPicker: () -> Unit,
    onCloseBlockedPicker: () -> Unit,
    onProductiveSearchChange: (String) -> Unit,
    onBlockedSearchChange: (String) -> Unit,
    onSelectProductiveApp: (String) -> Unit,
    onOpenRequirementPicker: () -> Unit,
    onCloseRequirementPicker: () -> Unit,
    onRequirementSearchChange: (String) -> Unit,
    onSelectRequirementApp: (String) -> Unit,
    onSelectRequirementMinutes: (Int) -> Unit,
    onSaveRequirement: () -> Unit,
    onEditRequirement: (Int) -> Unit,
    onDeleteRequirement: (Int) -> Unit,
    onToggleBlockedApp: (String) -> Unit,
    onSelectRatio: (Int) -> Unit,
    onToggleActiveDay: (Int) -> Unit,
    onSelectActiveDays: (Set<Int>) -> Unit,
    onSelectAllDay: () -> Unit,
    onEditStartTime: () -> Unit,
    onEditEndTime: () -> Unit,
    onSaveRule: () -> Unit,
    onCancel: () -> Unit
) {
    val steps = builderStepsFor(rule.type)
    val currentStep = if (builderStep in steps) builderStep else steps.first()
    val logicalBack = {
        currentStep.previous(steps)?.let(onBuilderStepChange) ?: onCancel()
    }
    val draft = ruleDraftUiState(
        rule = rule,
        apps = apps,
        selectedProductivePackage = selectedProductivePackage,
        selectedProductivePackages = selectedProductivePackages,
        selectedBlockedPackages = selectedBlockedPackages,
        selectedRatio = selectedRatio,
        selectedActiveDays = selectedActiveDays,
        selectedStartMinute = selectedStartMinute,
        selectedEndMinute = selectedEndMinute
    )
    BackHandler(onBack = logicalBack)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BuilderHeader(
            title = if (rule.id == "default" || rule.id.startsWith("rule_")) "Create Rule" else "Edit Rule",
            currentStep = currentStep,
            steps = steps,
            ruleType = rule.type,
            draft = draft,
            requirements = selectedRequirements,
            onBack = logicalBack,
            onStepClick = onBuilderStepChange
        )
        if (currentStep != RuleBuilderStep.Review) {
            RuleSoFar(
                draft = draft,
                ruleType = rule.type,
                requirements = selectedRequirements
            )
        }

        when (currentStep) {
            RuleBuilderStep.Earn -> if (rule.type == EarnItRuleStore.RuleType.CompleteToUnlock) {
                RequirementsStep(
                    apps = apps,
                    appsLoading = appsLoading,
                    requirements = selectedRequirements,
                    pickerOpen = requirementPickerOpen,
                    search = requirementSearch,
                    selectedPackage = selectedRequirementPackage,
                    selectedMinutes = selectedRequirementMinutes,
                    editingIndex = editingRequirementIndex,
                    onOpenPicker = onOpenRequirementPicker,
                    onClosePicker = onCloseRequirementPicker,
                    onSearchChange = onRequirementSearchChange,
                    onSelectApp = onSelectRequirementApp,
                    onSelectMinutes = onSelectRequirementMinutes,
                    onSaveRequirement = onSaveRequirement,
                    onEditRequirement = onEditRequirement,
                    onDeleteRequirement = onDeleteRequirement
                )
            } else {
                EarnStep(
                rule = rule,
                apps = apps,
                appsLoading = appsLoading,
                selectedProductivePackage = selectedProductivePackage,
                selectedProductivePackages = selectedProductivePackages,
                productivePickerOpen = productivePickerOpen,
                productiveSearch = productiveSearch,
                onOpenProductivePicker = onOpenProductivePicker,
                onCloseProductivePicker = onCloseProductivePicker,
                onProductiveSearchChange = onProductiveSearchChange,
                onSelectProductiveApp = onSelectProductiveApp
                )
            }
            RuleBuilderStep.Reward -> RewardStep(
                rule = rule,
                apps = apps,
                appsLoading = appsLoading,
                selectedBlockedPackages = selectedBlockedPackages,
                blockedPickerOpen = blockedPickerOpen,
                blockedSearch = blockedSearch,
                onOpenBlockedPicker = onOpenBlockedPicker,
                onCloseBlockedPicker = onCloseBlockedPicker,
                onBlockedSearchChange = onBlockedSearchChange,
                onToggleBlockedApp = onToggleBlockedApp,
                ruleType = rule.type
            )
            RuleBuilderStep.Exchange -> ExchangeStep(
                selectedRatio = selectedRatio,
                selectedEarnAppName = draft.selectedEarnApp?.name,
                selectedRewardAppCount = draft.selectedRewardApps.size,
                onSelectRatio = onSelectRatio
            )
            RuleBuilderStep.Schedule -> ScheduleStep(
                ruleType = rule.type,
                selectedActiveDays = selectedActiveDays,
                selectedStartMinute = selectedStartMinute,
                selectedEndMinute = selectedEndMinute,
                onToggleActiveDay = onToggleActiveDay,
                onSelectActiveDays = onSelectActiveDays,
                onSelectAllDay = onSelectAllDay,
                onEditStartTime = onEditStartTime,
                onEditEndTime = onEditEndTime
            )
            RuleBuilderStep.Review -> ReviewStep(draft = draft, rule = rule, requirements = selectedRequirements)
        }

        BuilderActions(
            currentStep = currentStep,
            steps = steps,
            ruleType = rule.type,
            canContinue = canContinue(rule.type, currentStep, draft, selectedRequirements),
            canSave = canSaveRule(rule.type, draft, selectedRequirements),
            onContinue = {
                currentStep.next(steps)?.let(onBuilderStepChange)
            },
            onSaveRule = onSaveRule)
    }
}

fun ruleDraftUiState(
    rule: EarnItRuleStore.Rule,
    apps: List<EarnItRuleStore.LaunchableApp>,
    selectedProductivePackage: String,
    selectedProductivePackages: Set<String>,
    selectedBlockedPackages: Set<String>,
    selectedRatio: Int,
    selectedActiveDays: Set<Int>,
    selectedStartMinute: Int,
    selectedEndMinute: Int
): RuleDraftUiState {
    val savedEarnApps = rule.earnApps.associateBy { it.packageName }
    val launchableApps = apps.associateBy { it.packageName }
    val selectedEarnApps = selectedProductivePackages.mapNotNull { selectedPackage ->
        launchableApps[selectedPackage] ?: savedEarnApps[selectedPackage]?.let {
            EarnItRuleStore.LaunchableApp(it.packageName, it.name)
        }
    }
    val savedBlockedApps = rule.blockedApps.associateBy { it.packageName }
    val selectedRewardApps = selectedBlockedPackages.mapNotNull { packageName ->
        launchableApps[packageName]?.let { app ->
            EarnItRuleStore.RuleApp(packageName = app.packageName, name = app.name)
        } ?: savedBlockedApps[packageName]
    }
    return EarnItUiStateAdapters.ruleDraft(
        selectedEarnApps = selectedEarnApps,
        selectedRewardApps = selectedRewardApps,
        exchangeSelection = selectedRatio,
        activeDays = selectedActiveDays,
        timeWindows = listOf(EarnItRuleStore.TimeWindow(selectedStartMinute, selectedEndMinute))
    )
}

@Composable
private fun BuilderHeader(
    title: String,
    currentStep: RuleBuilderStep,
    steps: List<RuleBuilderStep>,
    ruleType: EarnItRuleStore.RuleType,
    draft: RuleDraftUiState,
    requirements: List<EarnItRuleStore.RuleRequirement>,
    onBack: () -> Unit,
    onStepClick: (RuleBuilderStep) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text(text = "<")
            }
            Text(text = title, style = MaterialTheme.typography.titleLarge)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            steps.forEach { step ->
                val state = builderStageState(ruleType, step, currentStep, draft, requirements)
                StageButton(
                    label = stageLabel(ruleType, step),
                    state = state,
                    onClick = { if (state.clickable) onStepClick(step) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StageButton(
    label: String,
    state: BuilderStageState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lineColor = when {
        state == BuilderStageState.Current -> MaterialTheme.colorScheme.onSurface
        state == BuilderStageState.Completed -> MaterialTheme.colorScheme.primary
        state == BuilderStageState.Available -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val textColor = when {
        state == BuilderStageState.Current || state == BuilderStageState.Completed -> MaterialTheme.colorScheme.onSurface
        state == BuilderStageState.Available -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .clickable(enabled = state.clickable, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(lineColor)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = state.statusLabel,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
@Composable
private fun RuleSoFar(
    draft: RuleDraftUiState,
    ruleType: EarnItRuleStore.RuleType,
    requirements: List<EarnItRuleStore.RuleRequirement>
) {
    val lines = compactRuleSoFarLines(ruleType, draft, requirements)
    val hasApps = draft.selectedEarnApps.isNotEmpty() || draft.selectedRewardApps.isNotEmpty()
    if (lines.isEmpty() && !hasApps) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "YOUR RULE SO FAR",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        when (ruleType) {
            EarnItRuleStore.RuleType.EarnRewardTime -> RuleIconSummary(
                earnApps = draft.selectedEarnApps,
                rewardApps = draft.selectedRewardApps
            )
            EarnItRuleStore.RuleType.CompleteToUnlock -> RuleRequirementSummary(
                requirementCount = requirements.size,
                unlockApps = draft.selectedRewardApps
            )
            EarnItRuleStore.RuleType.ScheduledBlock -> RuleBlockedSummary(apps = draft.selectedRewardApps)
        }
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RuleIconSummary(
    earnApps: List<EarnItAppUiState>,
    rewardApps: List<EarnItAppUiState>
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        earnApps.take(2).forEach { app ->
            EarnItAppIcon(packageName = app.packageName, appName = app.name, size = 24.dp)
        }
        if (earnApps.size > 2) CountChip(count = earnApps.size - 2)
        if (earnApps.isNotEmpty() && rewardApps.isNotEmpty()) {
            Text(text = "->", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        rewardApps.take(2).forEach { app ->
            EarnItAppIcon(packageName = app.packageName, appName = app.name, size = 24.dp)
        }
        if (rewardApps.size > 2) {
            CountChip(count = rewardApps.size - 2)
        }
    }
}

@Composable
private fun RuleRequirementSummary(requirementCount: Int, unlockApps: List<EarnItAppUiState>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (requirementCount > 0) {
            Text(text = "$requirementCount ${if (requirementCount == 1) "requirement" else "requirements"}")
        }
        if (requirementCount > 0 && unlockApps.isNotEmpty()) {
            Text(text = "->", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        unlockApps.take(2).forEach { app ->
            EarnItAppIcon(packageName = app.packageName, appName = app.name, size = 24.dp)
        }
        if (unlockApps.size > 2) CountChip(count = unlockApps.size - 2)
    }
}

@Composable
private fun RuleBlockedSummary(apps: List<EarnItAppUiState>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        apps.take(3).forEach { app ->
            EarnItAppIcon(packageName = app.packageName, appName = app.name, size = 24.dp)
        }
        if (apps.size > 3) CountChip(count = apps.size - 3)
    }
}

@Composable
private fun CountChip(count: Int) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "+$count", style = MaterialTheme.typography.labelSmall)
    }
}
@Composable
private fun RequirementsStep(
    apps: List<EarnItRuleStore.LaunchableApp>,
    appsLoading: Boolean,
    requirements: List<EarnItRuleStore.RuleRequirement>,
    pickerOpen: Boolean,
    search: String,
    selectedPackage: String?,
    selectedMinutes: Int,
    editingIndex: Int?,
    onOpenPicker: () -> Unit,
    onClosePicker: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSelectApp: (String) -> Unit,
    onSelectMinutes: (Int) -> Unit,
    onSaveRequirement: () -> Unit,
    onEditRequirement: (Int) -> Unit,
    onDeleteRequirement: (Int) -> Unit
) {
    val namesByPackage = apps.associate { it.packageName to it.name } +
        requirements.associate { it.app.packageName to it.app.name }
    val selectedName = selectedPackage?.let { namesByPackage[it] }
    val editorOpen = pickerOpen || selectedPackage != null || editingIndex != null

    EditorSection(
        title = "What must you complete?",
        helperText = "Add one or more requirements. All requirements must be completed."
    ) {
        if (requirements.isEmpty() && !editorOpen) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "No requirements yet", style = MaterialTheme.typography.titleSmall)
                    Text(text = "Add one productive activity to unlock apps.", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onOpenPicker, modifier = Modifier.fillMaxWidth()) {
                        Text(text = "Add requirement")
                    }
                }
            }
        } else if (requirements.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                requirements.forEachIndexed { index, requirement ->
                    RequirementCard(
                        requirement = requirement,
                        onEdit = { onEditRequirement(index) },
                        onDelete = { onDeleteRequirement(index) }
                    )
                }
                OutlinedButton(onClick = onOpenPicker, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Add requirement")
                }
            }
        }

        if (editorOpen) {
            RequirementEditor(
                selectedName = selectedName ?: selectedPackage,
                selectedPackage = selectedPackage,
                selectedMinutes = selectedMinutes,
                editing = editingIndex != null,
                pickerOpen = pickerOpen,
                search = search,
                apps = apps,
                appsLoading = appsLoading,
                onOpenPicker = onOpenPicker,
                onSearchChange = onSearchChange,
                onSelectApp = onSelectApp,
                onSelectMinutes = onSelectMinutes,
                onSaveRequirement = onSaveRequirement,
                onCancel = onClosePicker
            )
        }
    }
}

@Composable
private fun RequirementEditor(
    selectedName: String?,
    selectedPackage: String?,
    selectedMinutes: Int,
    editing: Boolean,
    pickerOpen: Boolean,
    search: String,
    apps: List<EarnItRuleStore.LaunchableApp>,
    appsLoading: Boolean,
    onOpenPicker: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSelectApp: (String) -> Unit,
    onSelectMinutes: (Int) -> Unit,
    onSaveRequirement: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (editing) "Edit requirement" else "New requirement",
            style = MaterialTheme.typography.titleSmall
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = "App", style = MaterialTheme.typography.labelSmall)
            OutlinedButton(onClick = onOpenPicker, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = selectedName ?: "Choose app")
                    Text(text = ">", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (pickerOpen) {
            BuilderAppSearchField(
                value = search,
                onValueChange = onSearchChange,
                label = "Search productive apps"
            )
            BuilderAppList(
                apps = apps,
                selectedPackages = selectedPackage?.let { setOf(it) } ?: emptySet(),
                multiSelect = false,
                searchQuery = search,
                loading = appsLoading,
                selectedCountLabel = null,
                onClickApp = onSelectApp
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = "Required time", style = MaterialTheme.typography.labelSmall)
            RequirementDurationSelector(
                selectedMinutes = selectedMinutes,
                onSelectMinutes = onSelectMinutes
            )
        }
        Button(
            onClick = onSaveRequirement,
            enabled = selectedPackage != null && selectedMinutes > 0,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = if (editing) "Save changes" else "Add requirement")
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Cancel")
        }
    }
}

@Composable
private fun RequirementDurationSelector(
    selectedMinutes: Int,
    onSelectMinutes: (Int) -> Unit
) {
    val presets = listOf(5, 10, 20, 30)
    val customSelected = selectedMinutes !in presets
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            presets.take(2).forEach { minutes ->
                RequirementDurationChip(
                    label = "$minutes min",
                    selected = selectedMinutes == minutes,
                    onClick = { onSelectMinutes(minutes) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            presets.drop(2).forEach { minutes ->
                RequirementDurationChip(
                    label = "$minutes min",
                    selected = selectedMinutes == minutes,
                    onClick = { onSelectMinutes(minutes) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        RequirementDurationChip(
            label = "Custom",
            selected = customSelected,
            onClick = { if (!customSelected) onSelectMinutes(45) },
            modifier = Modifier.fillMaxWidth()
        )
        if (customSelected) {
            TextField(
                value = selectedMinutes.coerceAtLeast(1).toString(),
                onValueChange = { rawValue ->
                    rawValue.filter { it.isDigit() }.toIntOrNull()?.takeIf { it > 0 }?.let(onSelectMinutes)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "Custom minutes") },
                singleLine = true
            )
        }
    }
}

@Composable
private fun RequirementDurationChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Text(text = label)
    }
}

@Composable
private fun RequirementCard(
    requirement: EarnItRuleStore.RuleRequirement,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EarnItAppIcon(packageName = requirement.app.packageName, appName = requirement.app.name, size = 36.dp)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = requirement.app.name, style = MaterialTheme.typography.titleSmall)
            Text(text = "${requirement.requiredSeconds / 60L} min required", style = MaterialTheme.typography.bodyMedium)
        }
        TextButton(onClick = onEdit) {
            Text(text = "Edit")
        }
        TextButton(onClick = onDelete) {
            Text(text = "Remove")
        }
    }
}

private fun builderStepsFor(ruleType: EarnItRuleStore.RuleType): List<RuleBuilderStep> {
    return when (ruleType) {
        EarnItRuleStore.RuleType.EarnRewardTime -> listOf(
            RuleBuilderStep.Earn,
            RuleBuilderStep.Reward,
            RuleBuilderStep.Exchange,
            RuleBuilderStep.Schedule,
            RuleBuilderStep.Review
        )
        EarnItRuleStore.RuleType.CompleteToUnlock -> listOf(
            RuleBuilderStep.Earn,
            RuleBuilderStep.Reward,
            RuleBuilderStep.Schedule,
            RuleBuilderStep.Review
        )
        EarnItRuleStore.RuleType.ScheduledBlock -> listOf(
            RuleBuilderStep.Reward,
            RuleBuilderStep.Schedule,
            RuleBuilderStep.Review
        )
    }
}

internal enum class BuilderStageState(val statusLabel: String, val clickable: Boolean) {
    Current("Current", false),
    Completed("Done", true),
    Available("Open", true),
    Locked("Locked", false)
}

internal fun builderStageState(
    ruleType: EarnItRuleStore.RuleType,
    step: RuleBuilderStep,
    currentStep: RuleBuilderStep,
    draft: RuleDraftUiState,
    requirements: List<EarnItRuleStore.RuleRequirement>
): BuilderStageState {
    val steps = builderStepsFor(ruleType)
    val stepIndex = steps.indexOf(step)
    val currentIndex = steps.indexOf(currentStep)
    return when {
        step == currentStep -> BuilderStageState.Current
        stepIndex >= 0 && currentIndex >= 0 &&
            stepIndex < currentIndex &&
            stepIsComplete(ruleType, step, draft, requirements) -> BuilderStageState.Completed
        stepIsEnabled(ruleType, step, draft, requirements) -> BuilderStageState.Available
        else -> BuilderStageState.Locked
    }
}

internal fun stageLabel(ruleType: EarnItRuleStore.RuleType, step: RuleBuilderStep): String {
    return when {
        ruleType == EarnItRuleStore.RuleType.ScheduledBlock && step == RuleBuilderStep.Reward -> "Apps"
        else -> step.label
    }
}

internal fun logicalPreviousStep(
    ruleType: EarnItRuleStore.RuleType,
    currentStep: RuleBuilderStep
): RuleBuilderStep? {
    return currentStep.previous(builderStepsFor(ruleType))
}

internal fun reviewActionLabel(ruleType: EarnItRuleStore.RuleType): String {
    return when (ruleType) {
        EarnItRuleStore.RuleType.ScheduledBlock -> "Review Block Rule"
        else -> "Review Rule"
    }
}

private fun scheduleHelperText(ruleType: EarnItRuleStore.RuleType): String {
    return when (ruleType) {
        EarnItRuleStore.RuleType.EarnRewardTime -> "Outside these times, Reward Apps are unrestricted by this Rule."
        EarnItRuleStore.RuleType.CompleteToUnlock -> "Outside these times, Apps to Unlock are unrestricted by this Rule."
        EarnItRuleStore.RuleType.ScheduledBlock -> "Outside these times, Blocked Apps are unrestricted by this Rule."
    }
}

internal fun compactRuleSoFarLines(
    ruleType: EarnItRuleStore.RuleType,
    draft: RuleDraftUiState,
    requirements: List<EarnItRuleStore.RuleRequirement>
): List<String> {
    return buildList {
        when (ruleType) {
            EarnItRuleStore.RuleType.EarnRewardTime -> {
                if (draft.selectedEarnApps.isNotEmpty() && draft.selectedRewardApps.isNotEmpty()) {
                    add(EarnItUiFormatters.exchangeSummary(draft.exchangeSelection))
                }
                if (reviewScheduleIsValid(draft)) add(reviewScheduleSummary(draft.activeDays, draft.startMinute, draft.endMinute))
            }
            EarnItRuleStore.RuleType.CompleteToUnlock -> {
                if (requirements.isNotEmpty()) add("${requirements.size} ${if (requirements.size == 1) "requirement" else "requirements"}")
                if (requirements.isNotEmpty() && draft.selectedRewardApps.isNotEmpty()) add("Complete all")
                if (reviewScheduleIsValid(draft)) add(reviewScheduleSummary(draft.activeDays, draft.startMinute, draft.endMinute))
            }
            EarnItRuleStore.RuleType.ScheduledBlock -> {
                if (draft.selectedRewardApps.isNotEmpty()) add("Blocked")
                if (reviewScheduleIsValid(draft)) add(reviewScheduleSummary(draft.activeDays, draft.startMinute, draft.endMinute))
            }
        }
    }
}
@Composable
private fun EarnStep(
    rule: EarnItRuleStore.Rule,
    apps: List<EarnItRuleStore.LaunchableApp>,
    appsLoading: Boolean,
    selectedProductivePackage: String,
    selectedProductivePackages: Set<String>,
    productivePickerOpen: Boolean,
    productiveSearch: String,
    onOpenProductivePicker: () -> Unit,
    onCloseProductivePicker: () -> Unit,
    onProductiveSearchChange: (String) -> Unit,
    onSelectProductiveApp: (String) -> Unit
) {
    val savedEarnApps = rule.earnApps.associateBy { it.packageName }
    val namesByPackage = apps.associate { it.packageName to it.name } +
        savedEarnApps.mapValues { it.value.name }
    val selectedApps = selectedProductivePackages.mapNotNull { packageName ->
        namesByPackage[packageName]?.let { EarnItAppUiState(packageName = packageName, name = it) }
    }
    EditorSection(
        title = "How will you earn Reward Time?",
        helperText = "Choose one or more Earn Apps where productive time should count."
    ) {
        OutlinedButton(onClick = onOpenProductivePicker, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = if (selectedApps.isEmpty()) "Choose Earn Apps" else "Manage Earn Apps")
                if (selectedApps.isNotEmpty()) {
                    Text(
                        text = selectedAppCountLabel(selectedApps.size),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(text = ">", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (productivePickerOpen) {
            BuilderAppSearchField(
                value = productiveSearch,
                onValueChange = onProductiveSearchChange,
                label = "Search Earn Apps"
            )
            BuilderAppList(
                apps = apps,
                selectedPackages = selectedProductivePackages,
                multiSelect = true,
                searchQuery = productiveSearch,
                loading = appsLoading,
                selectedCountLabel = selectedAppCountLabel(selectedProductivePackages.size),
                onClickApp = onSelectProductiveApp
            )
        }
    }
}

@Composable
private fun RewardStep(
    rule: EarnItRuleStore.Rule,
    apps: List<EarnItRuleStore.LaunchableApp>,
    appsLoading: Boolean,
    selectedBlockedPackages: Set<String>,
    blockedPickerOpen: Boolean,
    blockedSearch: String,
    onOpenBlockedPicker: () -> Unit,
    onCloseBlockedPicker: () -> Unit,
    onBlockedSearchChange: (String) -> Unit,
    onToggleBlockedApp: (String) -> Unit,
    ruleType: EarnItRuleStore.RuleType
) {
    val namesByPackage = rule.blockedApps.associate { it.packageName to it.name } +
        apps.associate { it.packageName to it.name }
    val selectedApps = selectedBlockedPackages.mapNotNull { packageName ->
        namesByPackage[packageName]?.let { EarnItAppUiState(packageName = packageName, name = it) }
    }
    EditorSection(
        title = when (ruleType) {
            EarnItRuleStore.RuleType.ScheduledBlock -> "Which apps should be blocked?"
            EarnItRuleStore.RuleType.CompleteToUnlock -> "What should completing them unlock?"
            EarnItRuleStore.RuleType.EarnRewardTime -> "Where can Reward Time be spent?"
        },
        helperText = when (ruleType) {
            EarnItRuleStore.RuleType.ScheduledBlock -> "Choose one or more apps this Rule blocks during its active schedule."
            EarnItRuleStore.RuleType.CompleteToUnlock -> "Choose the apps that become available after every requirement is complete."
            EarnItRuleStore.RuleType.EarnRewardTime -> "Choose one or more Reward Apps that share this Rule balance."
        }
    ) {
        OutlinedButton(onClick = onOpenBlockedPicker, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = if (selectedApps.isEmpty()) chooseAppsLabel(ruleType) else manageAppsLabel(ruleType))
                if (selectedApps.isNotEmpty()) {
                    Text(
                        text = selectedAppCountLabel(selectedApps.size),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(text = ">", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (blockedPickerOpen) {
            BuilderAppSearchField(
                value = blockedSearch,
                onValueChange = onBlockedSearchChange,
                label = searchAppsLabel(ruleType)
            )
            BuilderAppList(
                apps = apps,
                selectedPackages = selectedBlockedPackages,
                multiSelect = true,
                searchQuery = blockedSearch,
                loading = appsLoading,
                selectedCountLabel = selectedAppCountLabel(selectedBlockedPackages.size),
                onClickApp = onToggleBlockedApp
            )
            OutlinedButton(onClick = onCloseBlockedPicker, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Done")
            }
        }
    }
}

private fun selectedAppsLabel(ruleType: EarnItRuleStore.RuleType): String {
    return when (ruleType) {
        EarnItRuleStore.RuleType.ScheduledBlock -> "Selected Blocked Apps"
        EarnItRuleStore.RuleType.CompleteToUnlock -> "Selected Apps to Unlock"
        EarnItRuleStore.RuleType.EarnRewardTime -> "Selected Reward Apps"
    }
}

private fun chooseAppsLabel(ruleType: EarnItRuleStore.RuleType): String {
    return when (ruleType) {
        EarnItRuleStore.RuleType.ScheduledBlock -> "Choose Blocked Apps"
        EarnItRuleStore.RuleType.CompleteToUnlock -> "Choose Apps to Unlock"
        EarnItRuleStore.RuleType.EarnRewardTime -> "Choose Reward Apps"
    }
}

private fun manageAppsLabel(ruleType: EarnItRuleStore.RuleType): String {
    return when (ruleType) {
        EarnItRuleStore.RuleType.ScheduledBlock -> "Manage Blocked Apps"
        EarnItRuleStore.RuleType.CompleteToUnlock -> "Manage Apps to Unlock"
        EarnItRuleStore.RuleType.EarnRewardTime -> "Manage Reward Apps"
    }
}

private fun searchAppsLabel(ruleType: EarnItRuleStore.RuleType): String {
    return when (ruleType) {
        EarnItRuleStore.RuleType.ScheduledBlock -> "Search Blocked Apps"
        EarnItRuleStore.RuleType.CompleteToUnlock -> "Search Apps to Unlock"
        EarnItRuleStore.RuleType.EarnRewardTime -> "Search Reward Apps"
    }
}

@Composable
private fun AppSelectionSummary(
    label: String,
    text: String,
    app: EarnItAppUiState? = null,
    apps: List<EarnItAppUiState> = emptyList()
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall)
            when {
                app != null -> Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EarnItAppIcon(packageName = app.packageName, appName = app.name, size = 32.dp)
                    Text(text = text, style = MaterialTheme.typography.bodyMedium)
                }
                apps.isNotEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        apps.take(5).forEach { selectedApp ->
                            EarnItAppIcon(packageName = selectedApp.packageName, appName = selectedApp.name, size = 28.dp)
                        }
                    }
                    Text(text = text, style = MaterialTheme.typography.bodyMedium)
                }
                else -> Text(text = text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
@Composable
private fun BuilderAppSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
private fun BuilderAppList(
    apps: List<EarnItRuleStore.LaunchableApp>,
    selectedPackages: Set<String>,
    multiSelect: Boolean,
    searchQuery: String,
    loading: Boolean,
    selectedCountLabel: String?,
    onClickApp: (String) -> Unit
) {
    var selectedCategory by remember { mutableStateOf(AppPickerCategory.All) }
    val visibleApps = remember(apps, selectedCategory, searchQuery) {
        filterLaunchableApps(apps = apps, category = selectedCategory, query = searchQuery)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AppPickerCategory.entries.forEach { category ->
                CategoryChip(
                    label = category.label,
                    selected = category == selectedCategory,
                    onClick = { selectedCategory = category }
                )
            }
        }

        selectedCountLabel?.let { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when {
                loading && apps.isEmpty() -> Text(text = "Loading apps...", style = MaterialTheme.typography.bodyMedium)
                visibleApps.isEmpty() -> Text(
                    text = appPickerEmptyText(selectedCategory, searchQuery),
                    style = MaterialTheme.typography.bodyMedium
                )
                else -> visibleApps.forEach { app ->
                    BuilderAppRow(
                        app = app,
                        selected = app.packageName in selectedPackages,
                        multiSelect = multiSelect,
                        onClickApp = onClickApp
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        )
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun BuilderAppRow(
    app: EarnItRuleStore.LaunchableApp,
    selected: Boolean,
    multiSelect: Boolean,
    onClickApp: (String) -> Unit
) {
    OutlinedButton(
        onClick = { onClickApp(app.packageName) },
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EarnItAppIcon(packageName = app.packageName, appName = app.name, size = 32.dp)
            Text(
                text = app.name,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Checkbox(
                checked = selected,
                onCheckedChange = { onClickApp(app.packageName) }
            )
        }
    }
}

@Composable
private fun ExchangeStep(
    selectedRatio: Int,
    selectedEarnAppName: String?,
    selectedRewardAppCount: Int,
    onSelectRatio: (Int) -> Unit
) {
    val earnAppName = selectedEarnAppName ?: "your Earn App"
    EditorSection(
        title = "How much Reward Time should you earn?",
        helperText = "Choose the exchange for productive time."
    ) {
        ExchangeStatement(
            earnAppName = earnAppName,
            ratio = selectedRatio
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EarnItRuleStore.allowedRatios.forEach { ratio ->
                ExchangeOption(
                    ratio = ratio,
                    selected = selectedRatio == ratio,
                    onSelectRatio = onSelectRatio
                )
            }
        }
        Text(
            text = if (selectedRewardAppCount == 1) {
                "This Reward Time applies to 1 selected Reward App."
            } else {
                "This Reward Time applies to $selectedRewardAppCount selected Reward Apps."
            },
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ExchangeStatement(earnAppName: String, ratio: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = "Every", style = MaterialTheme.typography.labelSmall)
            Text(text = "10 min", style = MaterialTheme.typography.titleMedium)
            Text(text = "in $earnAppName earns", style = MaterialTheme.typography.bodyMedium)
            Text(text = "${ratio.coerceAtLeast(1)} min Reward Time", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ExchangeOption(
    ratio: Int,
    selected: Boolean,
    onSelectRatio: (Int) -> Unit
) {
    OutlinedButton(
        onClick = { onSelectRatio(ratio) },
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        )
    ) {
        Text(text = "${ratio.coerceAtLeast(1)} min Reward Time")
    }
}

@Composable
private fun ScheduleStep(
    ruleType: EarnItRuleStore.RuleType,
    selectedActiveDays: Set<Int>,
    selectedStartMinute: Int,
    selectedEndMinute: Int,
    onToggleActiveDay: (Int) -> Unit,
    onSelectActiveDays: (Set<Int>) -> Unit,
    onSelectAllDay: () -> Unit,
    onEditStartTime: () -> Unit,
    onEditEndTime: () -> Unit
) {
    var customDaysOpen by remember(selectedActiveDays) {
        mutableStateOf(dayPreset(selectedActiveDays) == ScheduleDayPreset.Custom)
    }

    EditorSection(
        title = "When should this Rule apply?",
        helperText = scheduleHelperText(ruleType)
    ) {
        ScheduleDayPresets(
            selectedActiveDays = selectedActiveDays,
            customDaysOpen = customDaysOpen,
            onSelectActiveDays = { days ->
                customDaysOpen = false
                onSelectActiveDays(days)
            },
            onShowCustomDays = {
                customDaysOpen = true
            }
        )
        if (customDaysOpen || dayPreset(selectedActiveDays) == ScheduleDayPreset.Custom) {
            CompactDayButtons(selectedActiveDays = selectedActiveDays, onToggleActiveDay = onToggleActiveDay)
        }
        ScheduleTimePresets(
            ruleType = ruleType,
            selectedStartMinute = selectedStartMinute,
            selectedEndMinute = selectedEndMinute,
            onSelectAllDay = onSelectAllDay,
            onEditStartTime = onEditStartTime,
            onEditEndTime = onEditEndTime
        )
    }
}

@Composable
private fun CompactDayButtons(selectedActiveDays: Set<Int>, onToggleActiveDay: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        EarnItRuleStore.allDays.forEach { day ->
            val selected = day in selectedActiveDays
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onToggleActiveDay(day) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = compactDayLabel(day),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun compactDayLabel(day: Int): String {
    return when (day) {
        1 -> "M"
        2 -> "T"
        3 -> "W"
        4 -> "T"
        5 -> "F"
        6 -> "S"
        7 -> "S"
        else -> "?"
    }
}
private enum class ScheduleDayPreset {
    EveryDay,
    Weekdays,
    Custom
}

@Composable
private fun ScheduleDayPresets(
    selectedActiveDays: Set<Int>,
    customDaysOpen: Boolean,
    onSelectActiveDays: (Set<Int>) -> Unit,
    onShowCustomDays: () -> Unit
) {
    val selectedPreset = dayPreset(selectedActiveDays)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Active days", style = MaterialTheme.typography.titleSmall)
        SchedulePresetButton(
            label = "Every day",
            selected = selectedPreset == ScheduleDayPreset.EveryDay && !customDaysOpen,
            onClick = { onSelectActiveDays(EarnItRuleStore.allDays.toSet()) }
        )
        SchedulePresetButton(
            label = "Weekdays",
            selected = selectedPreset == ScheduleDayPreset.Weekdays && !customDaysOpen,
            onClick = { onSelectActiveDays(setOf(1, 2, 3, 4, 5)) }
        )
        SchedulePresetButton(
            label = "Custom",
            selected = customDaysOpen || selectedPreset == ScheduleDayPreset.Custom,
            onClick = onShowCustomDays
        )
    }
}

@Composable
private fun ScheduleTimePresets(
    ruleType: EarnItRuleStore.RuleType,
    selectedStartMinute: Int,
    selectedEndMinute: Int,
    onSelectAllDay: () -> Unit,
    onEditStartTime: () -> Unit,
    onEditEndTime: () -> Unit
) {
    val allDay = selectedStartMinute == 0 && selectedEndMinute == 1_440
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "During", style = MaterialTheme.typography.titleSmall)
        SchedulePresetButton(
            label = "All day",
            selected = allDay,
            onClick = onSelectAllDay
        )
        SchedulePresetButton(
            label = "Set hours",
            selected = !allDay,
            onClick = { if (allDay) onEditStartTime() }
        )
        if (!allDay) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEditStartTime, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Start")
                        Text(text = EarnItRuleStore.formatMinute(selectedStartMinute))
                    }
                }
                OutlinedButton(onClick = onEditEndTime, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "End")
                        Text(text = EarnItRuleStore.formatMinute(selectedEndMinute))
                    }
                }
            }
        }
        Text(
            text = scheduleHelperText(ruleType),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SchedulePresetButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(text = label)
        }
    }
}
private fun dayPreset(activeDays: Set<Int>): ScheduleDayPreset {
    return when (activeDays) {
        EarnItRuleStore.allDays.toSet() -> ScheduleDayPreset.EveryDay
        setOf(1, 2, 3, 4, 5) -> ScheduleDayPreset.Weekdays
        else -> ScheduleDayPreset.Custom
    }
}

@Composable
private fun ReviewStep(
    draft: RuleDraftUiState,
    rule: EarnItRuleStore.Rule,
    requirements: List<EarnItRuleStore.RuleRequirement>
) {
    when (rule.type) {
        EarnItRuleStore.RuleType.EarnRewardTime -> EarnRewardReviewStep(draft)
        EarnItRuleStore.RuleType.CompleteToUnlock -> CompleteToUnlockReviewStep(draft, requirements)
        EarnItRuleStore.RuleType.ScheduledBlock -> ScheduledBlockReviewStep(draft)
    }
}

@Composable
private fun CompleteToUnlockReviewStep(
    draft: RuleDraftUiState,
    requirements: List<EarnItRuleStore.RuleRequirement>
) {
    EditorSection(
        title = "Review Rule",
        helperText = "All requirements must be complete before these apps unlock."
    ) {
        ReviewCard {
            ReviewRequirementsSection(requirements = requirements)
            ReviewAppsSection(
                label = "TO UNLOCK",
                apps = draft.selectedRewardApps,
                missingValue = "Choose at least one app before saving."
            )
            ReviewAgreementSection(
                label = "ACTIVE",
                value = reviewScheduleSummary(draft.activeDays, draft.startMinute, draft.endMinute)
            )
        }
    }
}

@Composable
private fun ScheduledBlockReviewStep(draft: RuleDraftUiState) {
    EditorSection(
        title = "Review Block Rule",
        helperText = "These apps are blocked only during the selected schedule."
    ) {
        ReviewCard {
            ReviewAppsSection(
                label = "BLOCK",
                apps = draft.selectedRewardApps,
                missingValue = "Choose at least one app before saving."
            )
            ReviewAgreementSection(
                label = "DURING",
                value = reviewScheduleSummary(draft.activeDays, draft.startMinute, draft.endMinute)
            )
        }
    }
}

@Composable
private fun ReviewCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}
@Composable
private fun EarnRewardReviewStep(draft: RuleDraftUiState) {
    val missingItems = reviewMissingItems(draft)
    EditorSection(
        title = "Review Rule",
        helperText = "Read this as the agreement EarnIt will enforce."
    ) {
        ReviewCard {
                ReviewAppSection(
                    label = "WHEN I USE",
                    app = draft.selectedEarnApp,
                    missingValue = "Choose an Earn App before saving."
                )
                ReviewAgreementSection(
                    label = "I EARN",
                    value = if (draft.exchangeSelection > 0) {
                        EarnItUiFormatters.exchangeSummary(draft.exchangeSelection)
                    } else {
                        "Choose a Reward Time exchange before saving."
                    }
                )
                ReviewAppsSection(
                    label = "FOR",
                    apps = draft.selectedRewardApps,
                    missingValue = "Choose at least one Reward App before saving."
                )
                ReviewAgreementSection(
                    label = "ACTIVE",
                    value = if (reviewScheduleIsValid(draft)) {
                        reviewScheduleSummary(draft.activeDays, draft.startMinute, draft.endMinute)
                    } else {
                        "Choose when this Rule should be active before saving."
                    }
                )
        }

        if (missingItems.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = "Before saving, finish:", style = MaterialTheme.typography.titleSmall)
                    missingItems.forEach { item ->
                        Text(text = item, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewRequirementsSection(requirements: List<EarnItRuleStore.RuleRequirement>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "COMPLETE ALL", style = MaterialTheme.typography.labelSmall)
        if (requirements.isEmpty()) {
            Text(text = "Add at least one requirement before saving.", style = MaterialTheme.typography.bodyLarge)
        } else {
            requirements.forEach { requirement ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    EarnItAppIcon(packageName = requirement.app.packageName, appName = requirement.app.name, size = 32.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = requirement.app.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${requirement.requiredSeconds / 60L} min",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewAgreementSection(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ReviewAppSection(label: String, app: EarnItAppUiState?, missingValue: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall)
        if (app == null) {
            Text(text = missingValue, style = MaterialTheme.typography.bodyLarge)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                EarnItAppIcon(packageName = app.packageName, appName = app.name, size = 36.dp)
                Text(text = app.name, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun ReviewAppsSection(label: String, apps: List<EarnItAppUiState>, missingValue: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall)
        if (apps.isEmpty()) {
            Text(text = missingValue, style = MaterialTheme.typography.bodyLarge)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                apps.forEach { app ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        EarnItAppIcon(packageName = app.packageName, appName = app.name, size = 32.dp)
                        Text(text = app.name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
private fun reviewMissingItems(draft: RuleDraftUiState): List<String> {
    return buildList {
        if (draft.selectedEarnApp == null) add("Earn App")
        if (draft.selectedRewardApps.isEmpty()) add("Reward App")
        if (draft.exchangeSelection <= 0) add("Reward Time exchange")
        if (!reviewScheduleIsValid(draft)) add("Active schedule")
    }
}

private fun reviewScheduleIsValid(draft: RuleDraftUiState): Boolean {
    return draft.activeDays.any { it in EarnItRuleStore.allDays } &&
        draft.startMinute in 0..1_439 &&
        draft.endMinute in 1..1_440
}

private fun reviewScheduleSummary(activeDays: Set<Int>, startMinute: Int, endMinute: Int): String {
    val validDays = activeDays.filter { it in EarnItRuleStore.allDays }.toSet()
    val dayLabel = when (validDays) {
        EarnItRuleStore.allDays.toSet() -> "Every day"
        setOf(1, 2, 3, 4, 5) -> "Weekdays"
        else -> validDays.sorted().joinToString(" ") { EarnItRuleStore.dayShortName(it) }
    }
    val timeLabel = if (startMinute == 0 && endMinute == 1_440) {
        "all day"
    } else {
        "${EarnItRuleStore.formatMinute(startMinute)}-${EarnItRuleStore.formatMinute(endMinute)}"
    }
    return "$dayLabel, $timeLabel"
}
@Composable
private fun BuilderActions(
    currentStep: RuleBuilderStep,
    steps: List<RuleBuilderStep>,
    ruleType: EarnItRuleStore.RuleType,
    canContinue: Boolean,
    canSave: Boolean,
    onContinue: () -> Unit,
    onSaveRule: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (currentStep == RuleBuilderStep.Review) {
            Button(
                onClick = onSaveRule,
                enabled = canSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Save Rule")
            }
        } else {
            val nextStep = currentStep.next(steps)
            Button(
                onClick = onContinue,
                enabled = canContinue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = if (nextStep == RuleBuilderStep.Review) reviewActionLabel(ruleType) else "Continue")
            }
        }
    }
}
private fun stepIsComplete(
    ruleType: EarnItRuleStore.RuleType,
    step: RuleBuilderStep,
    draft: RuleDraftUiState,
    requirements: List<EarnItRuleStore.RuleRequirement>
): Boolean {
    return when (step) {
        RuleBuilderStep.Earn -> when (ruleType) {
            EarnItRuleStore.RuleType.EarnRewardTime -> draft.selectedEarnApp != null
            EarnItRuleStore.RuleType.CompleteToUnlock -> requirements.isNotEmpty()
            EarnItRuleStore.RuleType.ScheduledBlock -> true
        }
        RuleBuilderStep.Reward -> draft.selectedRewardApps.isNotEmpty()
        RuleBuilderStep.Exchange -> draft.exchangeSelection > 0
        RuleBuilderStep.Schedule -> reviewScheduleIsValid(draft)
        RuleBuilderStep.Review -> false
    }
}

private fun stepIsEnabled(
    ruleType: EarnItRuleStore.RuleType,
    step: RuleBuilderStep,
    draft: RuleDraftUiState,
    requirements: List<EarnItRuleStore.RuleRequirement>
): Boolean {
    return when (step) {
        RuleBuilderStep.Earn -> true
        RuleBuilderStep.Reward -> when (ruleType) {
            EarnItRuleStore.RuleType.EarnRewardTime -> draft.selectedEarnApp != null
            EarnItRuleStore.RuleType.CompleteToUnlock -> requirements.isNotEmpty()
            EarnItRuleStore.RuleType.ScheduledBlock -> true
        }
        RuleBuilderStep.Exchange -> draft.selectedEarnApp != null && draft.selectedRewardApps.isNotEmpty()
        RuleBuilderStep.Schedule -> when (ruleType) {
            EarnItRuleStore.RuleType.EarnRewardTime -> draft.selectedEarnApp != null &&
                draft.selectedRewardApps.isNotEmpty() &&
                draft.exchangeSelection > 0
            EarnItRuleStore.RuleType.CompleteToUnlock -> requirements.isNotEmpty() && draft.selectedRewardApps.isNotEmpty()
            EarnItRuleStore.RuleType.ScheduledBlock -> draft.selectedRewardApps.isNotEmpty()
        }
        RuleBuilderStep.Review -> canSaveRule(ruleType, draft, requirements)
    }
}
private fun canContinue(
    ruleType: EarnItRuleStore.RuleType,
    step: RuleBuilderStep,
    draft: RuleDraftUiState,
    requirements: List<EarnItRuleStore.RuleRequirement>
): Boolean {
    return when (step) {
        RuleBuilderStep.Earn -> when (ruleType) {
            EarnItRuleStore.RuleType.EarnRewardTime -> draft.selectedEarnApp != null
            EarnItRuleStore.RuleType.CompleteToUnlock -> requirements.isNotEmpty()
            EarnItRuleStore.RuleType.ScheduledBlock -> true
        }
        RuleBuilderStep.Reward -> draft.selectedRewardApps.isNotEmpty()
        RuleBuilderStep.Exchange -> draft.exchangeSelection > 0
        RuleBuilderStep.Schedule -> draft.activeDays.isNotEmpty() && draft.startMinute in 0..1_439 && draft.endMinute in 1..1_440
        RuleBuilderStep.Review -> canSaveRule(ruleType, draft, requirements)
    }
}

private fun canSaveRule(
    ruleType: EarnItRuleStore.RuleType,
    draft: RuleDraftUiState,
    requirements: List<EarnItRuleStore.RuleRequirement>
): Boolean {
    val validSchedule = draft.activeDays.isNotEmpty() && draft.startMinute in 0..1_439 && draft.endMinute in 1..1_440
    return when (ruleType) {
        EarnItRuleStore.RuleType.EarnRewardTime -> draft.canSave
        EarnItRuleStore.RuleType.CompleteToUnlock -> requirements.isNotEmpty() && draft.selectedRewardApps.isNotEmpty() && validSchedule
        EarnItRuleStore.RuleType.ScheduledBlock -> draft.selectedRewardApps.isNotEmpty() && validSchedule
    }
}
