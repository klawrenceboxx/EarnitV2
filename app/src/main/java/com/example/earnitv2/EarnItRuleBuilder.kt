package com.example.earnitv2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class RuleBuilderStep(val label: String) {
    Earn("Earn"),
    Reward("Reward"),
    Exchange("Exchange"),
    Schedule("Schedule"),
    Review("Review");

    fun previous(): RuleBuilderStep? {
        val index = entries.indexOf(this)
        return entries.getOrNull(index - 1)
    }

    fun next(): RuleBuilderStep? {
        val index = entries.indexOf(this)
        return entries.getOrNull(index + 1)
    }
}

@Composable
fun EarnItRuleBuilder(
    rule: EarnItRuleStore.Rule,
    apps: List<EarnItRuleStore.LaunchableApp>,
    selectedProductivePackage: String,
    selectedBlockedPackages: Set<String>,
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
    onToggleBlockedApp: (String) -> Unit,
    onSelectRatio: (Int) -> Unit,
    onToggleActiveDay: (Int) -> Unit,
    onEditStartTime: () -> Unit,
    onEditEndTime: () -> Unit,
    onSaveRule: () -> Unit,
    onCancel: () -> Unit
) {
    val draft = ruleDraftUiState(
        rule = rule,
        apps = apps,
        selectedProductivePackage = selectedProductivePackage,
        selectedBlockedPackages = selectedBlockedPackages,
        selectedRatio = selectedRatio,
        selectedActiveDays = selectedActiveDays,
        selectedStartMinute = selectedStartMinute,
        selectedEndMinute = selectedEndMinute
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BuilderHeader(
            title = if (rule.id == "default" || rule.id.startsWith("rule_")) "Create Rule" else "Edit Rule",
            currentStep = builderStep
        )
        RuleSoFar(draft = draft)

        when (builderStep) {
            RuleBuilderStep.Earn -> EarnStep(
                rule = rule,
                apps = apps,
                selectedProductivePackage = selectedProductivePackage,
                productivePickerOpen = productivePickerOpen,
                productiveSearch = productiveSearch,
                onOpenProductivePicker = onOpenProductivePicker,
                onCloseProductivePicker = onCloseProductivePicker,
                onProductiveSearchChange = onProductiveSearchChange,
                onSelectProductiveApp = onSelectProductiveApp
            )
            RuleBuilderStep.Reward -> RewardStep(
                rule = rule,
                apps = apps,
                selectedBlockedPackages = selectedBlockedPackages,
                blockedPickerOpen = blockedPickerOpen,
                blockedSearch = blockedSearch,
                onOpenBlockedPicker = onOpenBlockedPicker,
                onCloseBlockedPicker = onCloseBlockedPicker,
                onBlockedSearchChange = onBlockedSearchChange,
                onToggleBlockedApp = onToggleBlockedApp
            )
            RuleBuilderStep.Exchange -> ExchangeStep(
                selectedRatio = selectedRatio,
                onSelectRatio = onSelectRatio
            )
            RuleBuilderStep.Schedule -> ScheduleStep(
                selectedActiveDays = selectedActiveDays,
                selectedStartMinute = selectedStartMinute,
                selectedEndMinute = selectedEndMinute,
                onToggleActiveDay = onToggleActiveDay,
                onEditStartTime = onEditStartTime,
                onEditEndTime = onEditEndTime
            )
            RuleBuilderStep.Review -> ReviewStep(draft = draft)
        }

        BuilderActions(
            currentStep = builderStep,
            canContinue = canContinue(builderStep, draft),
            canSave = draft.canSave,
            onBack = {
                val previous = builderStep.previous()
                if (previous == null) onCancel() else onBuilderStepChange(previous)
            },
            onContinue = {
                builderStep.next()?.let(onBuilderStepChange)
            },
            onSaveRule = onSaveRule,
            onCancel = onCancel
        )
    }
}

fun ruleDraftUiState(
    rule: EarnItRuleStore.Rule,
    apps: List<EarnItRuleStore.LaunchableApp>,
    selectedProductivePackage: String,
    selectedBlockedPackages: Set<String>,
    selectedRatio: Int,
    selectedActiveDays: Set<Int>,
    selectedStartMinute: Int,
    selectedEndMinute: Int
): RuleDraftUiState {
    val selectedEarnApp = apps.firstOrNull { it.packageName == selectedProductivePackage }
        ?: EarnItRuleStore.LaunchableApp(rule.productivePackage, rule.productiveName)
            .takeIf { it.packageName == selectedProductivePackage }
    val savedBlockedApps = rule.blockedApps.associateBy { it.packageName }
    val launchableApps = apps.associateBy { it.packageName }
    val selectedRewardApps = selectedBlockedPackages.mapNotNull { packageName ->
        launchableApps[packageName]?.let { app ->
            EarnItRuleStore.RuleApp(packageName = app.packageName, name = app.name)
        } ?: savedBlockedApps[packageName]
    }
    return EarnItUiStateAdapters.ruleDraft(
        selectedEarnApp = selectedEarnApp,
        selectedRewardApps = selectedRewardApps,
        exchangeSelection = selectedRatio,
        activeDays = selectedActiveDays,
        startMinute = selectedStartMinute,
        endMinute = selectedEndMinute
    )
}

@Composable
private fun BuilderHeader(title: String, currentStep: RuleBuilderStep) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RuleBuilderStep.entries.forEach { step ->
                val selected = step == currentStep
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = step.label,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleSoFar(draft: RuleDraftUiState) {
    if (draft.selectedEarnApp == null && draft.selectedRewardApps.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = "Your Rule so far", style = MaterialTheme.typography.titleSmall)
            draft.selectedEarnApp?.let { app ->
                Text(text = "Earn App: ${app.name}", style = MaterialTheme.typography.bodyMedium)
            }
            if (draft.selectedRewardApps.isNotEmpty()) {
                Text(
                    text = "Reward Apps: ${draft.selectedRewardApps.joinToString(", ") { it.name }}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(text = EarnItUiFormatters.exchangeSummary(draft.exchangeSelection), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EarnStep(
    rule: EarnItRuleStore.Rule,
    apps: List<EarnItRuleStore.LaunchableApp>,
    selectedProductivePackage: String,
    productivePickerOpen: Boolean,
    productiveSearch: String,
    onOpenProductivePicker: () -> Unit,
    onCloseProductivePicker: () -> Unit,
    onProductiveSearchChange: (String) -> Unit,
    onSelectProductiveApp: (String) -> Unit
) {
    val selectedAppName = apps.firstOrNull { it.packageName == selectedProductivePackage }?.name
        ?: if (rule.productivePackage == selectedProductivePackage) rule.productiveName else null
    val visibleApps = apps.builderFilteredBy(productiveSearch)
    EditorSection(
        title = "How will you earn Reward Time?",
        helperText = "Choose one Earn App where productive time should count."
    ) {
        if (selectedAppName == null) {
            Text(text = "No Earn App selected yet.", style = MaterialTheme.typography.bodyMedium)
        } else {
            AppSelectionSummary(
                label = "Selected Earn App",
                text = selectedAppName
            )
        }
        BuilderAppSearchField(
            value = productiveSearch,
            onValueChange = onProductiveSearchChange,
            label = "Search Earn Apps"
        )
        BuilderAppList(
            apps = visibleApps,
            selectedPackages = setOf(selectedProductivePackage),
            multiSelect = false,
            emptyText = "No Earn Apps match your search.",
            onClickApp = { packageName ->
                onSelectProductiveApp(packageName)
                onCloseProductivePicker()
            }
        )
    }
}

@Composable
private fun RewardStep(
    rule: EarnItRuleStore.Rule,
    apps: List<EarnItRuleStore.LaunchableApp>,
    selectedBlockedPackages: Set<String>,
    blockedPickerOpen: Boolean,
    blockedSearch: String,
    onOpenBlockedPicker: () -> Unit,
    onCloseBlockedPicker: () -> Unit,
    onBlockedSearchChange: (String) -> Unit,
    onToggleBlockedApp: (String) -> Unit
) {
    val namesByPackage = rule.blockedApps.associate { it.packageName to it.name } +
        apps.associate { it.packageName to it.name }
    val selectedNames = selectedBlockedPackages.mapNotNull { namesByPackage[it] }
    val visibleApps = apps.builderFilteredBy(blockedSearch)
    EditorSection(
        title = "Where can Reward Time be spent?",
        helperText = "Choose one or more Reward Apps that share this Rule balance."
    ) {
        AppSelectionSummary(
            label = "Selected Reward Apps",
            text = if (selectedNames.isEmpty()) {
                "0 selected"
            } else {
                "${selectedNames.size} selected: ${selectedNames.take(3).joinToString(", ")}" +
                    if (selectedNames.size > 3) " +${selectedNames.size - 3} more" else ""
            }
        )
        BuilderAppSearchField(
            value = blockedSearch,
            onValueChange = onBlockedSearchChange,
            label = "Search Reward Apps"
        )
        BuilderAppList(
            apps = visibleApps,
            selectedPackages = selectedBlockedPackages,
            multiSelect = true,
            emptyText = "No Reward Apps match your search.",
            onClickApp = onToggleBlockedApp
        )
        if (blockedPickerOpen) {
            OutlinedButton(onClick = onCloseBlockedPicker, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Done selecting Reward Apps")
            }
        }
    }
}

@Composable
private fun AppSelectionSummary(label: String, text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall)
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
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
    emptyText: String,
    onClickApp: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (apps.isEmpty()) {
            Text(text = emptyText, style = MaterialTheme.typography.bodyMedium)
        }
        apps.forEach { app ->
            BuilderAppRow(
                app = app,
                selected = app.packageName in selectedPackages,
                multiSelect = multiSelect,
                onClickApp = onClickApp
            )
        }
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
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = app.name)
            Text(
                text = when {
                    selected && multiSelect -> "Selected"
                    selected -> "Selected Earn App"
                    multiSelect -> "Add"
                    else -> "Choose"
                },
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

private fun List<EarnItRuleStore.LaunchableApp>.builderFilteredBy(query: String): List<EarnItRuleStore.LaunchableApp> {
    val trimmedQuery = query.trim()
    if (trimmedQuery.isEmpty()) return this
    return filter { it.name.contains(trimmedQuery, ignoreCase = true) }
}

@Composable
private fun ExchangeStep(selectedRatio: Int, onSelectRatio: (Int) -> Unit) {
    EditorSection(
        title = "How much Reward Time should you earn?",
        helperText = "Choose the exchange for productive time."
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EarnItRuleStore.allowedRatios.forEach { ratio ->
                Button(onClick = { onSelectRatio(ratio) }) {
                    Text(text = if (selectedRatio == ratio) "Selected ${EarnItUiFormatters.exchangeSummary(ratio)}" else EarnItUiFormatters.exchangeSummary(ratio))
                }
            }
        }
    }
}

@Composable
private fun ScheduleStep(
    selectedActiveDays: Set<Int>,
    selectedStartMinute: Int,
    selectedEndMinute: Int,
    onToggleActiveDay: (Int) -> Unit,
    onEditStartTime: () -> Unit,
    onEditEndTime: () -> Unit
) {
    EditorSection(
        title = "When should this Rule apply?",
        helperText = "Outside these times, Reward Apps are unrestricted by this Rule."
    ) {
        DayButtons(selectedActiveDays = selectedActiveDays, onToggleActiveDay = onToggleActiveDay)
        Button(onClick = onEditStartTime, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Start: ${EarnItRuleStore.formatMinute(selectedStartMinute)}")
        }
        Button(onClick = onEditEndTime, modifier = Modifier.fillMaxWidth()) {
            Text(text = "End: ${EarnItRuleStore.formatMinute(selectedEndMinute)}")
        }
    }
}

@Composable
private fun ReviewStep(draft: RuleDraftUiState) {
    EditorSection(
        title = "Review Rule",
        helperText = "Save this Rule when the agreement looks right."
    ) {
        if (draft.reviewSummary.isBlank()) {
            Text(text = "Finish the previous steps to review this Rule.")
        } else {
            draft.reviewSummary.lines().forEach { line ->
                Text(text = line, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun BuilderActions(
    currentStep: RuleBuilderStep,
    canContinue: Boolean,
    canSave: Boolean,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onSaveRule: () -> Unit,
    onCancel: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text(text = "Back")
            }
            if (currentStep == RuleBuilderStep.Review) {
                Button(onClick = onSaveRule, enabled = canSave, modifier = Modifier.weight(1f)) {
                    Text(text = "Save Rule")
                }
            } else {
                Button(onClick = onContinue, enabled = canContinue, modifier = Modifier.weight(1f)) {
                    Text(text = "Continue")
                }
            }
        }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Cancel")
        }
    }
}

private fun canContinue(step: RuleBuilderStep, draft: RuleDraftUiState): Boolean {
    return when (step) {
        RuleBuilderStep.Earn -> draft.selectedEarnApp != null
        RuleBuilderStep.Reward -> draft.selectedRewardApps.isNotEmpty()
        RuleBuilderStep.Exchange -> draft.exchangeSelection in EarnItRuleStore.allowedRatios
        RuleBuilderStep.Schedule -> draft.activeDays.isNotEmpty() && draft.startMinute in 0..1_439 && draft.endMinute in 1..1_440
        RuleBuilderStep.Review -> draft.canSave
    }
}
