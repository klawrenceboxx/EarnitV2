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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
    selectedProductivePackage: String,
    selectedProductivePackages: Set<String>,
    selectedBlockedPackages: Set<String>,
    selectedRequirements: List<EarnItRuleStore.RuleRequirement>,
    selectedRequirementPackage: String?,
    selectedRequirementMinutes: Int,
    editingRequirementIndex: Int?,
    selectedRatio: Int,
    selectedActiveDays: Set<Int>,
    selectedStartMinute: Int,
    selectedEndMinute: Int,
    selectedTimeWindows: List<EarnItRuleStore.TimeWindow>,
    scheduleWindowEditorOpen: Boolean,
    editingScheduleWindowIndex: Int?,
    scheduleEditorStartMinute: Int,
    scheduleEditorEndMinute: Int,
    builderStep: RuleBuilderStep,
    onBuilderStepChange: (RuleBuilderStep) -> Unit,
    onOpenProductivePicker: () -> Unit,
    onOpenBlockedPicker: () -> Unit,
    onOpenRequirementPicker: () -> Unit,
    onCloseRequirementPicker: () -> Unit,
    onSelectRequirementMinutes: (Int) -> Unit,
    onSaveRequirement: () -> Unit,
    onEditRequirement: (Int) -> Unit,
    onDeleteRequirement: (Int) -> Unit,
    onSelectRatio: (Int) -> Unit,
    onToggleActiveDay: (Int) -> Unit,
    onSelectActiveDays: (Set<Int>) -> Unit,
    onSelectAllDay: () -> Unit,
    onSetHours: () -> Unit,
    onAddTimeWindow: () -> Unit,
    onEditTimeWindow: (Int) -> Unit,
    onRemoveTimeWindow: (Int) -> Unit,
    onSaveTimeWindow: () -> Unit,
    onCancelTimeWindow: () -> Unit,
    onEditStartTime: () -> Unit,
    onEditEndTime: () -> Unit,
    onSaveRule: () -> Unit,
    onCancel: () -> Unit,
    selectedBlockedDomains: List<String> = emptyList()
) {
    val steps = builderStepsFor(rule.type)
    val currentStep = if (builderStep in steps) builderStep else steps.first()
    val logicalBack = {
        when (val action = ruleBuilderBackAction(rule.type, currentStep)) {
            RuleBuilderBackAction.ExitBuilder -> onCancel()
            is RuleBuilderBackAction.PreviousStep -> onBuilderStepChange(action.step)
        }
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
        selectedEndMinute = selectedEndMinute,
        selectedTimeWindows = selectedTimeWindows,
        selectedBlockedDomains = selectedBlockedDomains
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
                    requirements = selectedRequirements,
                    selectedPackage = selectedRequirementPackage,
                    selectedMinutes = selectedRequirementMinutes,
                    editingIndex = editingRequirementIndex,
                    onOpenPicker = onOpenRequirementPicker,
                    onClosePicker = onCloseRequirementPicker,
                    onSelectMinutes = onSelectRequirementMinutes,
                    onSaveRequirement = onSaveRequirement,
                    onEditRequirement = onEditRequirement,
                    onDeleteRequirement = onDeleteRequirement
                )
            } else {
                EarnStep(
                rule = rule,
                apps = apps,
                selectedProductivePackages = selectedProductivePackages,
                onOpenProductivePicker = onOpenProductivePicker
                )
            }
            RuleBuilderStep.Reward -> RewardStep(
                rule = rule,
                apps = apps,
                selectedBlockedPackages = selectedBlockedPackages,
                selectedBlockedDomains = selectedBlockedDomains,
                onOpenBlockedPicker = onOpenBlockedPicker,
                ruleType = rule.type
            )
            RuleBuilderStep.Exchange -> ExchangeStep(
                rule = rule,
                selectedRatio = selectedRatio,
                selectedEarnApps = draft.selectedEarnApps,
                selectedRewardAppCount = draft.selectedRewardApps.size,
                onSelectRatio = onSelectRatio
            )
            RuleBuilderStep.Schedule -> ScheduleStep(
                ruleType = rule.type,
                selectedActiveDays = selectedActiveDays,
                selectedStartMinute = selectedStartMinute,
                selectedEndMinute = selectedEndMinute,
                selectedTimeWindows = selectedTimeWindows,
                editorOpen = scheduleWindowEditorOpen,
                editingWindowIndex = editingScheduleWindowIndex,
                editorStartMinute = scheduleEditorStartMinute,
                editorEndMinute = scheduleEditorEndMinute,
                onToggleActiveDay = onToggleActiveDay,
                onSelectActiveDays = onSelectActiveDays,
                onSelectAllDay = onSelectAllDay,
                onSetHours = onSetHours,
                onAddTimeWindow = onAddTimeWindow,
                onEditTimeWindow = onEditTimeWindow,
                onRemoveTimeWindow = onRemoveTimeWindow,
                onSaveTimeWindow = onSaveTimeWindow,
                onCancelTimeWindow = onCancelTimeWindow,
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
    selectedEndMinute: Int,
    selectedTimeWindows: List<EarnItRuleStore.TimeWindow> = listOf(EarnItRuleStore.TimeWindow(selectedStartMinute, selectedEndMinute)),
    selectedBlockedDomains: List<String> = rule.normalizedBlockedDomains
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
        timeWindows = selectedTimeWindows,
        selectedBlockedDomains = selectedBlockedDomains
    )
}

enum class RewardTargetTab { Apps, Websites }

data class DomainSelectionResult(val domains: List<String>, val error: String? = null)

fun addDomainToSelection(current: List<String>, input: String): DomainSelectionResult {
    val normalized = DomainNormalizer.normalize(input)
        ?: return DomainSelectionResult(current, "Enter a valid website domain")
    if (normalized in current) return DomainSelectionResult(current, "$normalized is already selected")
    return DomainSelectionResult((current + normalized).distinct().sorted())
}

@Composable
internal fun RewardTargetPickerSurface(
    title: String,
    supportingText: String?,
    searchLabel: String,
    apps: List<EarnItRuleStore.LaunchableApp>,
    selectedPackages: Set<String>,
    searchQuery: String,
    loading: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onToggleApp: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    multiSelect: Boolean,
    saveLabel: String,
    selectedDomains: List<String>,
    onDomainsChange: (List<String>) -> Unit,
    accessibilityEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var tab by remember { mutableStateOf(RewardTargetTab.Apps) }
    if (tab == RewardTargetTab.Apps) {
        Column(modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ tab = RewardTargetTab.Apps }, Modifier.weight(1f)) { Text("Apps") }
                OutlinedButton({ tab = RewardTargetTab.Websites }, Modifier.weight(1f)) { Text("Websites") }
            }
            BuilderAppPickerSurface(
                title = title.replace("Apps", "Apps & Websites"), supportingText = supportingText,
                searchLabel = searchLabel, apps = apps, selectedPackages = selectedPackages,
                searchQuery = searchQuery, loading = loading, onSearchQueryChange = onSearchQueryChange,
                onToggleApp = onToggleApp, onSave = onSave, onBack = onBack,
                multiSelect = multiSelect, saveLabel = saveLabel, modifier = Modifier.weight(1f)
            )
        }
    } else {
        val context = androidx.compose.ui.platform.LocalContext.current
        val chromeInstalled = remember(context) {
            EarnItRuleStore.appInstalled(context, ChromeBrowserAdapter.CHROME_PACKAGE)
        }
        var input by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        BackHandler(onBack = onBack)
        Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ tab = RewardTargetTab.Apps }, Modifier.weight(1f)) { Text("Apps") }
                Button({ tab = RewardTargetTab.Websites }, Modifier.weight(1f)) { Text("Websites") }
            }
            Text("Add websites", style = MaterialTheme.typography.headlineMedium)
            Text("Protected websites share this Rule's Reward Time and schedule. Chrome is supported first.")
            if (!accessibilityEnabled) {
                Text(
                    "Accessibility access is off, so websites are not currently protected. You can still configure them.",
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (!chromeInstalled) {
                Text(
                    "Google Chrome is not installed. These websites will become protected when Chrome is installed.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextField(
                value = input, onValueChange = { input = it; error = null },
                label = { Text("Enter a website such as youtube.com") },
                isError = error != null,
                supportingText = if (error != null) ({ Text(error.orEmpty()) }) else null,
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Button(onClick = {
                val result = addDomainToSelection(selectedDomains, input)
                error = result.error
                if (result.error == null) { onDomainsChange(result.domains); input = "" }
            }, modifier = Modifier.fillMaxWidth()) { Text("Add") }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(selectedDomains, key = { it }) { domain ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🌐  $domain", Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            TextButton({ onDomainsChange(selectedDomains - domain) }) { Text("Remove") }
                        }
                    }
                }
            }
            Text("${selectedPackages.size} apps · ${selectedDomains.size} websites selected")
            Button(onSave, Modifier.fillMaxWidth(), enabled = selectedPackages.isNotEmpty() || selectedDomains.isNotEmpty()) { Text(saveLabel) }
        }
    }
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
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                RuleTypeBadge(ruleType = ruleType, iconSize = 24.dp)
            }
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
    val hasApps = draft.selectedEarnApps.isNotEmpty() || draft.hasProtectedTargets()
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
    requirements: List<EarnItRuleStore.RuleRequirement>,
    selectedPackage: String?,
    selectedMinutes: Int,
    editingIndex: Int?,
    onOpenPicker: () -> Unit,
    onClosePicker: () -> Unit,
    onSelectMinutes: (Int) -> Unit,
    onSaveRequirement: () -> Unit,
    onEditRequirement: (Int) -> Unit,
    onDeleteRequirement: (Int) -> Unit
) {
    val namesByPackage = apps.associate { it.packageName to it.name } +
        requirements.associate { it.app.packageName to it.app.name }
    val selectedName = selectedPackage?.let { namesByPackage[it] }
    val editorOpen = selectedPackage != null || editingIndex != null

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
                onOpenPicker = onOpenPicker,
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
    onOpenPicker: () -> Unit,
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
            if (selectedPackage != null) {
                SelectedRequirementAppRow(
                    packageName = selectedPackage,
                    appName = selectedName ?: selectedPackage,
                    onChange = onOpenPicker
                )
            } else {
                OutlinedButton(onClick = onOpenPicker, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Choose app")
                }
            }
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
private fun SelectedRequirementAppRow(
    packageName: String,
    appName: String,
    onChange: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EarnItAppIcon(packageName = packageName, appName = appName, size = 36.dp)
            Text(
                text = appName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(onClick = onChange) { Text(text = "Change") }
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

internal sealed class RuleBuilderBackAction {
    data class PreviousStep(val step: RuleBuilderStep) : RuleBuilderBackAction()
    data object ExitBuilder : RuleBuilderBackAction()
}

internal fun ruleBuilderBackAction(
    ruleType: EarnItRuleStore.RuleType,
    currentStep: RuleBuilderStep
): RuleBuilderBackAction {
    return logicalPreviousStep(ruleType, currentStep)?.let { previousStep ->
        RuleBuilderBackAction.PreviousStep(previousStep)
    } ?: RuleBuilderBackAction.ExitBuilder
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
                if (reviewScheduleIsValid(draft)) add(reviewScheduleSummary(draft))
            }
            EarnItRuleStore.RuleType.CompleteToUnlock -> {
                if (requirements.isNotEmpty()) add("${requirements.size} ${if (requirements.size == 1) "requirement" else "requirements"}")
                if (requirements.isNotEmpty() && draft.selectedRewardApps.isNotEmpty()) add("Complete all")
                if (reviewScheduleIsValid(draft)) add(reviewScheduleSummary(draft))
            }
            EarnItRuleStore.RuleType.ScheduledBlock -> {
                if (draft.selectedRewardApps.isNotEmpty()) add("Blocked")
                if (reviewScheduleIsValid(draft)) add(reviewScheduleSummary(draft))
            }
        }
    }
}
@Composable
private fun EarnStep(
    rule: EarnItRuleStore.Rule,
    apps: List<EarnItRuleStore.LaunchableApp>,
    selectedProductivePackages: Set<String>,
    onOpenProductivePicker: () -> Unit
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
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onOpenProductivePicker),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = if (selectedApps.isEmpty()) "Choose Earn Apps" else "${selectedApps.size} selected",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = selectedAppPreviewLabel(selectedApps.map { it.name }),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(text = "\u203A", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun BuilderAppPickerSurface(
    title: String,
    supportingText: String? = null,
    searchLabel: String,
    apps: List<EarnItRuleStore.LaunchableApp>,
    selectedPackages: Set<String>,
    searchQuery: String,
    loading: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onToggleApp: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit = onSave,
    multiSelect: Boolean = true,
    disabledPackages: Set<String> = emptySet(),
    saveLabel: String = "Save",
    saveEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember(title) { mutableStateOf(AppPickerCategory.All) }
    val visibleApps = remember(apps, selectedCategory, searchQuery) {
        filterLaunchableApps(apps, selectedCategory, searchQuery)
    }
    BackHandler(onBack = onBack)

    Column(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text(text = "Back") }
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
        }
        if (supportingText != null) {
            Text(
                text = supportingText,
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BuilderAppSearchField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                label = searchLabel
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
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
            Text(
                text = if (multiSelect) {
                    selectedAppCountLabel(selectedPackages.size)
                } else {
                    selectedPackages.singleOrNull()?.let { selectedPackage ->
                        apps.firstOrNull { it.packageName == selectedPackage }?.name
                    } ?: "Select one app"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when {
                loading && apps.isEmpty() -> item {
                    Text(text = "Loading apps...", style = MaterialTheme.typography.bodyMedium)
                }
                visibleApps.isEmpty() -> item {
                    Text(
                        text = appPickerEmptyText(selectedCategory, searchQuery),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                else -> items(visibleApps, key = { it.packageName }) { app ->
                    BuilderAppRow(
                        app = app,
                        selected = app.packageName in selectedPackages,
                        multiSelect = multiSelect,
                        enabled = app.packageName !in disabledPackages,
                        onClickApp = onToggleApp
                    )
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 3.dp,
            shadowElevation = 6.dp
        ) {
            Button(
                onClick = onSave,
                enabled = saveEnabled,
                modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 56.dp)
            ) {
                Text(text = saveLabel)
            }
        }
    }
}

@Composable
private fun RewardStep(
    rule: EarnItRuleStore.Rule,
    apps: List<EarnItRuleStore.LaunchableApp>,
    selectedBlockedPackages: Set<String>,
    selectedBlockedDomains: List<String>,
    onOpenBlockedPicker: () -> Unit,
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
                val total = selectedApps.size + selectedBlockedDomains.size
                Text(text = if (total == 0) "Choose Apps & Websites" else "Manage Apps & Websites")
                if (total > 0) {
                    Text(
                        text = "${selectedApps.size} apps · ${selectedBlockedDomains.size} websites",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(text = ">", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

    }
}

internal fun rewardAppPickerTitle(ruleType: EarnItRuleStore.RuleType): String {
    return when (ruleType) {
        EarnItRuleStore.RuleType.EarnRewardTime -> "Choose Reward Apps"
        EarnItRuleStore.RuleType.CompleteToUnlock -> "Choose Apps to Unlock"
        EarnItRuleStore.RuleType.ScheduledBlock -> "Choose Blocked Apps"
    }
}

internal fun rewardAppPickerSupportingText(ruleType: EarnItRuleStore.RuleType): String {
    return when (ruleType) {
        EarnItRuleStore.RuleType.EarnRewardTime -> "Choose one or more apps that spend this Rule's Reward Time."
        EarnItRuleStore.RuleType.CompleteToUnlock -> "Choose the apps that unlock after every requirement is complete."
        EarnItRuleStore.RuleType.ScheduledBlock -> "Choose one or more apps this Rule blocks during its active schedule."
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

internal fun rewardAppPickerSearchLabel(ruleType: EarnItRuleStore.RuleType): String {
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
    enabled: Boolean = true,
    onClickApp: (String) -> Unit
) {
    OutlinedButton(
        onClick = { onClickApp(app.packageName) },
        enabled = enabled,
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
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = app.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!enabled) {
                    Text(
                        text = "Already added",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (multiSelect) {
                Checkbox(
                    checked = selected,
                    enabled = enabled,
                    onCheckedChange = { onClickApp(app.packageName) }
                )
            } else {
                RadioButton(
                    selected = selected,
                    enabled = enabled,
                    onClick = { onClickApp(app.packageName) }
                )
            }
        }
    }
}

@Composable
private fun ExchangeStep(
    rule: EarnItRuleStore.Rule,
    selectedRatio: Int,
    selectedEarnApps: List<EarnItAppUiState>,
    selectedRewardAppCount: Int,
    onSelectRatio: (Int) -> Unit
) {
    val earnAppContext = EarnItUiFormatters.earnAppContext(selectedEarnApps.map { it.name })
    EditorSection(
        title = "How much Reward Time should you earn?",
        helperText = "Choose the exchange for productive time."
    ) {
        ExchangeStatement(
            earnAppContext = earnAppContext,
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
        DeepWorkRuleSetting(rule = rule.copy(rewardSecondsPerProductiveSecond = selectedRatio))
    }
}

@Composable
private fun ExchangeStatement(earnAppContext: String, ratio: Int) {
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
            Text(text = "$earnAppContext earns", style = MaterialTheme.typography.bodyMedium)
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
    selectedTimeWindows: List<EarnItRuleStore.TimeWindow>,
    editorOpen: Boolean,
    editingWindowIndex: Int?,
    editorStartMinute: Int,
    editorEndMinute: Int,
    onToggleActiveDay: (Int) -> Unit,
    onSelectActiveDays: (Set<Int>) -> Unit,
    onSelectAllDay: () -> Unit,
    onSetHours: () -> Unit,
    onAddTimeWindow: () -> Unit,
    onEditTimeWindow: (Int) -> Unit,
    onRemoveTimeWindow: (Int) -> Unit,
    onSaveTimeWindow: () -> Unit,
    onCancelTimeWindow: () -> Unit,
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
            windows = selectedTimeWindows,
            editorOpen = editorOpen,
            editingWindowIndex = editingWindowIndex,
            editorStartMinute = editorStartMinute,
            editorEndMinute = editorEndMinute,
            onSelectAllDay = onSelectAllDay,
            onSetHours = onSetHours,
            onAddTimeWindow = onAddTimeWindow,
            onEditTimeWindow = onEditTimeWindow,
            onRemoveTimeWindow = onRemoveTimeWindow,
            onSaveTimeWindow = onSaveTimeWindow,
            onCancelTimeWindow = onCancelTimeWindow,
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
    windows: List<EarnItRuleStore.TimeWindow>,
    editorOpen: Boolean,
    editingWindowIndex: Int?,
    editorStartMinute: Int,
    editorEndMinute: Int,
    onSelectAllDay: () -> Unit,
    onSetHours: () -> Unit,
    onAddTimeWindow: () -> Unit,
    onEditTimeWindow: (Int) -> Unit,
    onRemoveTimeWindow: (Int) -> Unit,
    onSaveTimeWindow: () -> Unit,
    onCancelTimeWindow: () -> Unit,
    onEditStartTime: () -> Unit,
    onEditEndTime: () -> Unit
) {
    val normalizedWindows = EarnItRuleStore.normalizeTimeWindows(windows)
    val allDay = normalizedWindows.size == 1 && normalizedWindows.first() == EarnItRuleStore.TimeWindow(0, 1_440)
    val validationMessage = scheduleWindowValidationMessage(
        windows = normalizedWindows,
        editingIndex = editingWindowIndex,
        candidate = EarnItRuleStore.TimeWindow(editorStartMinute, editorEndMinute)
    )
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
            onClick = onSetHours
        )
        if (!allDay) {
            TimeWindowList(
                windows = normalizedWindows,
                onEditTimeWindow = onEditTimeWindow,
                onRemoveTimeWindow = onRemoveTimeWindow
            )
            OutlinedButton(onClick = onAddTimeWindow, modifier = Modifier.fillMaxWidth()) {
                Text(text = "+ Add time window")
            }
            if (editorOpen) {
                TimeWindowEditor(
                    editing = editingWindowIndex != null,
                    startMinute = editorStartMinute,
                    endMinute = editorEndMinute,
                    validationMessage = validationMessage,
                    onEditStartTime = onEditStartTime,
                    onEditEndTime = onEditEndTime,
                    onSaveTimeWindow = onSaveTimeWindow,
                    onCancelTimeWindow = onCancelTimeWindow
                )
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
private fun TimeWindowList(
    windows: List<EarnItRuleStore.TimeWindow>,
    onEditTimeWindow: (Int) -> Unit,
    onRemoveTimeWindow: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Time windows", style = MaterialTheme.typography.labelSmall)
        windows.forEachIndexed { index, window ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = timeWindowLabel(window), style = MaterialTheme.typography.bodyLarge)
                    if (window.startMinute > window.endMinute) {
                        Text(
                            text = "Overnight",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(onClick = { onEditTimeWindow(index) }) {
                    Text(text = "Edit")
                }
                TextButton(onClick = { onRemoveTimeWindow(index) }) {
                    Text(text = "Remove")
                }
            }
        }
    }
}

@Composable
private fun TimeWindowEditor(
    editing: Boolean,
    startMinute: Int,
    endMinute: Int,
    validationMessage: String?,
    onEditStartTime: () -> Unit,
    onEditEndTime: () -> Unit,
    onSaveTimeWindow: () -> Unit,
    onCancelTimeWindow: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = if (editing) "Edit time window" else "Add time window", style = MaterialTheme.typography.titleSmall)
        TimePickerTrigger(label = "Start", minute = startMinute, onClick = onEditStartTime)
        TimePickerTrigger(label = "End", minute = endMinute, onClick = onEditEndTime)
        if (validationMessage != null) {
            Text(
                text = validationMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        Button(
            onClick = onSaveTimeWindow,
            enabled = validationMessage == null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = if (editing) "Save changes" else "Add window")
        }
        TextButton(onClick = onCancelTimeWindow, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Cancel")
        }
    }
}

@Composable
private fun TimePickerTrigger(label: String, minute: Int, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label)
            Text(text = EarnItRuleStore.formatMinute(minute))
        }
    }
}

private fun timeWindowLabel(window: EarnItRuleStore.TimeWindow): String {
    return "${EarnItRuleStore.formatMinute(window.startMinute)}-${EarnItRuleStore.formatMinute(window.endMinute)}"
}

internal fun scheduleWindowValidationMessage(
    windows: List<EarnItRuleStore.TimeWindow>,
    editingIndex: Int?,
    candidate: EarnItRuleStore.TimeWindow
): String? {
    if (candidate.startMinute == candidate.endMinute) return "Start and end must be different."
    val others = windows.filterIndexed { index, _ -> index != editingIndex }
    if (candidate in others) return "This time window already exists."
    if (others.any { windowsOverlap(it, candidate) }) return "Time windows cannot overlap."
    return null
}

private fun windowsOverlap(a: EarnItRuleStore.TimeWindow, b: EarnItRuleStore.TimeWindow): Boolean {
    return expandedWindowRanges(a).any { first ->
        expandedWindowRanges(b).any { second ->
            first.first < second.second && second.first < first.second
        }
    }
}

private fun expandedWindowRanges(window: EarnItRuleStore.TimeWindow): List<Pair<Int, Int>> {
    return if (window.startMinute < window.endMinute) {
        listOf(window.startMinute to window.endMinute)
    } else {
        listOf(window.startMinute to 1_440, 0 to window.endMinute)
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
            ReviewWebsitesSection(draft.selectedBlockedDomains)
            ReviewAgreementSection(
                label = "ACTIVE",
                value = reviewScheduleDetail(draft)
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
            ReviewWebsitesSection(draft.selectedBlockedDomains)
            ReviewAgreementSection(
                label = "BLOCK DURING",
                value = reviewScheduleDetail(draft)
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
                ReviewAppsSection(
                    label = "WHEN I USE",
                    apps = draft.selectedEarnApps,
                    missingValue = "Choose an Earn App before saving."
                )
                ReviewAgreementSection(
                    label = "I EARN",
                    value = if (draft.exchangeSelection > 0) {
                        EarnItUiFormatters.exchangeAgreement(
                            earnAppNames = draft.selectedEarnApps.map { it.name },
                            exchangeSelection = draft.exchangeSelection
                        )
                    } else {
                        "Choose a Reward Time exchange before saving."
                    }
                )
                ReviewAppsSection(
                    label = "FOR",
                    apps = draft.selectedRewardApps,
                    missingValue = "Choose at least one Reward App before saving."
                )
                ReviewWebsitesSection(draft.selectedBlockedDomains)
                ReviewAgreementSection(
                    label = "ACTIVE",
                    value = if (reviewScheduleIsValid(draft)) {
                        reviewScheduleDetail(draft)
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
        if (draft.selectedEarnApps.isEmpty()) add("Earn App")
        if (!draft.hasProtectedTargets()) add("Reward App or Website")
        if (draft.exchangeSelection <= 0) add("Reward Time exchange")
        if (!reviewScheduleIsValid(draft)) add("Active schedule")
    }
}

private fun reviewScheduleIsValid(draft: RuleDraftUiState): Boolean {
    return draft.activeDays.any { it in EarnItRuleStore.allDays } &&
        draft.timeWindows.isNotEmpty()
}

@Composable
private fun ReviewWebsitesSection(domains: List<String>) {
    if (domains.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("WEBSITES", style = MaterialTheme.typography.labelSmall)
        domains.take(5).forEach { Text("🌐  $it", style = MaterialTheme.typography.bodyMedium) }
        if (domains.size > 5) Text("+${domains.size - 5} more", style = MaterialTheme.typography.bodySmall)
    }
}

private fun reviewScheduleSummary(draft: RuleDraftUiState): String {
    return EarnItRuleStore.scheduleSummary(draft.activeDays, draft.timeWindows)
}

private fun reviewScheduleDetail(draft: RuleDraftUiState): String {
    return EarnItRuleStore.scheduleDetailLines(draft.activeDays, draft.timeWindows).joinToString("\n")
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
            EarnItRuleStore.RuleType.EarnRewardTime -> draft.selectedEarnApps.isNotEmpty()
            EarnItRuleStore.RuleType.CompleteToUnlock -> requirements.isNotEmpty()
            EarnItRuleStore.RuleType.ScheduledBlock -> true
        }
        RuleBuilderStep.Reward -> draft.hasProtectedTargets()
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
            EarnItRuleStore.RuleType.EarnRewardTime -> draft.selectedEarnApps.isNotEmpty()
            EarnItRuleStore.RuleType.CompleteToUnlock -> requirements.isNotEmpty()
            EarnItRuleStore.RuleType.ScheduledBlock -> true
        }
        RuleBuilderStep.Exchange -> draft.selectedEarnApps.isNotEmpty() && draft.hasProtectedTargets()
        RuleBuilderStep.Schedule -> when (ruleType) {
            EarnItRuleStore.RuleType.EarnRewardTime -> draft.selectedEarnApps.isNotEmpty() &&
                draft.hasProtectedTargets() &&
                draft.exchangeSelection > 0
            EarnItRuleStore.RuleType.CompleteToUnlock -> requirements.isNotEmpty() && draft.hasProtectedTargets()
            EarnItRuleStore.RuleType.ScheduledBlock -> draft.hasProtectedTargets()
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
            EarnItRuleStore.RuleType.EarnRewardTime -> draft.selectedEarnApps.isNotEmpty()
            EarnItRuleStore.RuleType.CompleteToUnlock -> requirements.isNotEmpty()
            EarnItRuleStore.RuleType.ScheduledBlock -> true
        }
        RuleBuilderStep.Reward -> draft.hasProtectedTargets()
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
        EarnItRuleStore.RuleType.CompleteToUnlock -> requirements.isNotEmpty() && draft.hasProtectedTargets() && validSchedule
        EarnItRuleStore.RuleType.ScheduledBlock -> draft.hasProtectedTargets() && validSchedule
    }
}

private fun RuleDraftUiState.hasProtectedTargets(): Boolean =
    selectedRewardApps.isNotEmpty() || selectedBlockedDomains.isNotEmpty()
