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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
    onSelectActiveDays: (Set<Int>) -> Unit,
    onSelectAllDay: () -> Unit,
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
                selectedEarnAppName = draft.selectedEarnApp?.name,
                selectedRewardAppCount = draft.selectedRewardApps.size,
                onSelectRatio = onSelectRatio
            )
            RuleBuilderStep.Schedule -> ScheduleStep(
                selectedActiveDays = selectedActiveDays,
                selectedStartMinute = selectedStartMinute,
                selectedEndMinute = selectedEndMinute,
                onToggleActiveDay = onToggleActiveDay,
                onSelectActiveDays = onSelectActiveDays,
                onSelectAllDay = onSelectAllDay,
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
    val selectedApp = apps.firstOrNull { it.packageName == selectedProductivePackage }
        ?: EarnItRuleStore.LaunchableApp(rule.productivePackage, rule.productiveName)
            .takeIf { it.packageName == selectedProductivePackage }
    val selectedAppName = selectedApp?.name
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
                text = selectedAppName,
                app = selectedApp?.let { EarnItAppUiState(packageName = it.packageName, name = it.name) }
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
    val selectedApps = selectedBlockedPackages.mapNotNull { packageName ->
        namesByPackage[packageName]?.let { EarnItAppUiState(packageName = packageName, name = it) }
    }
    val selectedNames = selectedApps.map { it.name }
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
            },
            apps = selectedApps
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                EarnItAppIcon(packageName = app.packageName, appName = app.name, size = 32.dp)
                Text(text = app.name)
            }
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
            Text(text = "${ratio * 10} min Reward Time", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ExchangeOption(
    ratio: Int,
    selected: Boolean,
    onSelectRatio: (Int) -> Unit
) {
    OutlinedButton(onClick = { onSelectRatio(ratio) }, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "${ratio * 10} min Reward Time")
            Text(
                text = if (selected) "Selected" else "Choose",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun ScheduleStep(
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
        helperText = "Outside these times, Reward Apps are unrestricted by this Rule."
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
            DayButtons(selectedActiveDays = selectedActiveDays, onToggleActiveDay = onToggleActiveDay)
        }
        ScheduleTimePresets(
            selectedStartMinute = selectedStartMinute,
            selectedEndMinute = selectedEndMinute,
            onSelectAllDay = onSelectAllDay,
            onEditStartTime = onEditStartTime,
            onEditEndTime = onEditEndTime
        )
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEditStartTime, modifier = Modifier.weight(1f)) {
                    Text(text = "Start ${EarnItRuleStore.formatMinute(selectedStartMinute)}")
                }
                OutlinedButton(onClick = onEditEndTime, modifier = Modifier.weight(1f)) {
                    Text(text = "End ${EarnItRuleStore.formatMinute(selectedEndMinute)}")
                }
            }
        }
        Text(
            text = "Outside these times, Reward Apps are unrestricted by this Rule.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun SchedulePresetButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label)
            Text(text = if (selected) "Selected" else "Choose", style = MaterialTheme.typography.labelSmall)
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
private fun ReviewStep(draft: RuleDraftUiState) {
    val missingItems = reviewMissingItems(draft)
    EditorSection(
        title = "Review Rule",
        helperText = "Read this as the agreement EarnIt will enforce."
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ReviewAppSection(
                    label = "WHEN I USE",
                    app = draft.selectedEarnApp,
                    missingValue = "Choose an Earn App before saving."
                )
                ReviewAgreementSection(
                    label = "I EARN",
                    value = if (draft.exchangeSelection in EarnItRuleStore.allowedRatios) {
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
        if (draft.exchangeSelection !in EarnItRuleStore.allowedRatios) add("Reward Time exchange")
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
