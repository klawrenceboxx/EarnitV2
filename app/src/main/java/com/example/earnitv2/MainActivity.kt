package com.example.earnitv2

import android.app.AppOpsManager
import android.app.TimePickerDialog
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.earnitv2.ui.theme.EarnitV2Theme
import kotlin.concurrent.thread
import kotlinx.coroutines.delay

data class RuleDashboardState(
    val rule: EarnItRuleStore.Rule,
    val productiveUsageSeconds: Long,
    val remainingRewardSeconds: Long
)

internal enum class RuleBuilderEntryContext {
    Create,
    Edit
}

internal enum class RuleBuilderExitTarget {
    RuleTypeSelection,
    RuleDetail
}

internal data class RuleBuilderExitDestination(
    val target: RuleBuilderExitTarget,
    val ruleDetailId: String? = null
)

internal fun firstStageBuilderExitDestination(
    entryContext: RuleBuilderEntryContext?,
    editingRuleId: String?
): RuleBuilderExitDestination {
    return when (entryContext) {
        RuleBuilderEntryContext.Edit -> RuleBuilderExitDestination(
            target = RuleBuilderExitTarget.RuleDetail,
            ruleDetailId = editingRuleId
        )
        RuleBuilderEntryContext.Create,
        null -> RuleBuilderExitDestination(target = RuleBuilderExitTarget.RuleTypeSelection)
    }
}

class MainActivity : ComponentActivity() {
    private var rules by mutableStateOf(emptyList<EarnItRuleStore.Rule>())
    private var ruleStates by mutableStateOf(emptyList<RuleDashboardState>())
    private var pauseExpirations by mutableStateOf(emptyMap<String, Long>())
    private lateinit var strictModeStore: StrictModeStore
    private var strictModeState by mutableStateOf(StrictModeState())
    private var editingRuleTemplate by mutableStateOf<EarnItRuleStore.Rule?>(null)
    private var builderEntryContext by mutableStateOf<RuleBuilderEntryContext?>(null)
    private var builderReturnRuleDetailId by mutableStateOf<String?>(null)
    private var launchableApps by mutableStateOf(emptyList<EarnItRuleStore.LaunchableApp>())
    private var appLoadInProgress = false
    private var appListLoading by mutableStateOf(false)
    private var appListLoadedAtMillis = 0L
    private var selectedProductivePackage by mutableStateOf("")
    private var selectedProductivePackages by mutableStateOf(emptySet<String>())
    private var selectedBlockedPackages by mutableStateOf(emptySet<String>())
    private var selectedRequirements by mutableStateOf(emptyList<EarnItRuleStore.RuleRequirement>())
    private var requirementPickerOpen by mutableStateOf(false)
    private var requirementSearch by mutableStateOf("")
    private var selectedRequirementPackage by mutableStateOf<String?>(null)
    private var selectedRequirementMinutes by mutableStateOf(10)
    private var editingRequirementIndex by mutableStateOf<Int?>(null)
    private var selectedRatio by mutableStateOf(1)
    private var selectedActiveDays by mutableStateOf(EarnItRuleStore.allDays.toSet())
    private var selectedStartMinute by mutableStateOf(0)
    private var selectedEndMinute by mutableStateOf(1_440)
    private var selectedTimeWindows by mutableStateOf(listOf(EarnItRuleStore.TimeWindow(0, 1_440)))
    private var scheduleWindowEditorOpen by mutableStateOf(false)
    private var editingScheduleWindowIndex by mutableStateOf<Int?>(null)
    private var scheduleEditorStartMinute by mutableStateOf(9 * 60)
    private var scheduleEditorEndMinute by mutableStateOf(17 * 60)
    private var productivePickerOpen by mutableStateOf(false)
    private var blockedPickerOpen by mutableStateOf(false)
    private var productiveSearch by mutableStateOf("")
    private var blockedSearch by mutableStateOf("")
    private var usageAccessGranted by mutableStateOf(false)
    private var usageStatusMessage by mutableStateOf("")
    private var ruleStatusMessage by mutableStateOf("")
    private var accessibilityServiceEnabled by mutableStateOf(false)
    private var manageRulesOpen by mutableStateOf(false)
    private var selectedRuleDetailId by mutableStateOf<String?>(null)
    private var settingsOpen by mutableStateOf(false)
    private var strictModeOpen by mutableStateOf(false)
    private var ruleTypeSelectionOpen by mutableStateOf(false)
    private var unavailableRuleType by mutableStateOf<RuleTypeOption?>(null)
    private var firstLaunchComplete by mutableStateOf(false)
    private var firstLaunchStep by mutableStateOf(FirstLaunchStep.ValueIntroduction)
    private var builderStep by mutableStateOf(RuleBuilderStep.Earn)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        strictModeStore = StrictModeStore(SharedPreferencesStrictModePersistence(this))
        firstLaunchComplete = isFirstLaunchComplete() || EarnItRuleStore.getRules(this).isNotEmpty()
        refreshDashboardState()
        setContent {
            EarnitV2Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (!firstLaunchComplete && editingRuleTemplate == null) {
                        EarnItFirstLaunch(
                            currentStep = firstLaunchStep,
                            permissionState = EarnItUiStateAdapters.permissionSetup(
                                usageAccessGranted = usageAccessGranted,
                                appBlockingEnabled = accessibilityServiceEnabled
                            ),
                            onStepChange = { firstLaunchStep = it },
                            onOpenUsageAccessSettings = ::openUsageAccessSettings,
                            onOpenAccessibilitySettings = ::openAccessibilitySettings,
                            onCreateFirstRule = ::completeFirstLaunchAndCreateRule,
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        Dashboard(
                            ruleStates = ruleStates,
                            editingRule = editingRuleTemplate,
                            apps = launchableApps,
                            appsLoading = appListLoading,
                            selectedProductivePackage = selectedProductivePackage,
                            selectedProductivePackages = selectedProductivePackages,
                            selectedBlockedPackages = selectedBlockedPackages,
                            selectedRequirements = selectedRequirements,
                            requirementPickerOpen = requirementPickerOpen,
                            requirementSearch = requirementSearch,
                            selectedRequirementPackage = selectedRequirementPackage,
                            selectedRequirementMinutes = selectedRequirementMinutes,
                            editingRequirementIndex = editingRequirementIndex,
                            selectedRatio = selectedRatio,
                            selectedActiveDays = selectedActiveDays,
                            selectedStartMinute = selectedStartMinute,
                            selectedEndMinute = selectedEndMinute,
                            selectedTimeWindows = selectedTimeWindows,
                            scheduleWindowEditorOpen = scheduleWindowEditorOpen,
                            editingScheduleWindowIndex = editingScheduleWindowIndex,
                            scheduleEditorStartMinute = scheduleEditorStartMinute,
                            scheduleEditorEndMinute = scheduleEditorEndMinute,
                            productivePickerOpen = productivePickerOpen,
                            blockedPickerOpen = blockedPickerOpen,
                            productiveSearch = productiveSearch,
                            blockedSearch = blockedSearch,
                            usageAccessGranted = usageAccessGranted,
                            usageStatusMessage = usageStatusMessage,
                            ruleStatusMessage = ruleStatusMessage,
                            accessibilityServiceEnabled = accessibilityServiceEnabled,
                            manageRulesOpen = manageRulesOpen,
                            pauseExpirations = pauseExpirations,
                            selectedRuleDetailId = selectedRuleDetailId,
                            settingsOpen = settingsOpen,
                            strictModeOpen = strictModeOpen,
                            strictModeState = strictModeState,
                            ruleTypeSelectionOpen = ruleTypeSelectionOpen,
                            unavailableRuleType = unavailableRuleType,
                            onOpenUsageAccessSettings = ::openUsageAccessSettings,
                            onOpenAccessibilitySettings = ::openAccessibilitySettings,
                            onOpenSettings = { settingsOpen = true },
                            onCloseSettings = { settingsOpen = false },
                            onOpenStrictMode = {
                                settingsOpen = false
                                strictModeOpen = true
                            },
                            onCloseStrictMode = {
                                strictModeOpen = false
                                settingsOpen = true
                            },
                            onSaveStrictModeConfiguration = ::saveStrictModeConfiguration,
                            onBeginStrictModeActivation = ::beginStrictModeActivation,
                            onCancelStrictModeActivation = ::cancelStrictModeActivation,
                            onStrictModeTick = ::refreshStrictModeState,
                            onOpenEarnApp = ::openEarnApp,
                            onAddRule = ::startAddingRule,
                            onBackFromRuleTypeSelection = ::closeRuleTypeSelection,
                            onBackFromUnavailableRuleType = { unavailableRuleType = null },
                            onSelectRuleType = ::startRuleType,
                            onEditRule = ::startEditingRule,
                            onToggleRuleEnabled = ::toggleRuleEnabled,
                            onPauseRuleFor = ::pauseRuleFor,
                            onResumeRule = ::resumeRule,
                            onPauseTimerTick = ::refreshDashboardState,
                            onDeleteRule = ::deleteRule,
                            onToggleManageRules = { manageRulesOpen = !manageRulesOpen },
                            onOpenRuleDetail = { selectedRuleDetailId = it },
                            onBackFromRuleDetail = { selectedRuleDetailId = null },
                            onCancelEditingRule = ::exitBuilderFromFirstStage,
                            onOpenProductivePicker = {
                                productivePickerOpen = true
                                refreshLaunchableApps()
                            },
                            onCloseProductivePicker = { productivePickerOpen = false },
                            onOpenBlockedPicker = {
                                blockedPickerOpen = true
                                refreshLaunchableApps()
                            },
                            onCloseBlockedPicker = { blockedPickerOpen = false },
                            onProductiveSearchChange = { productiveSearch = it },
                            onBlockedSearchChange = { blockedSearch = it },
                            onSelectProductiveApp = ::selectProductiveApp,
                            onOpenRequirementPicker = ::openRequirementPicker,
                            onCloseRequirementPicker = ::cancelRequirementEditor,
                            onRequirementSearchChange = { requirementSearch = it },
                            onSelectRequirementApp = ::selectRequirementApp,
                            onSelectRequirementMinutes = { selectedRequirementMinutes = it },
                            onSaveRequirement = ::saveRequirement,
                            onEditRequirement = ::editRequirement,
                            onDeleteRequirement = ::deleteRequirement,
                            onToggleBlockedApp = ::toggleBlockedApp,
                            onSelectRatio = { selectedRatio = it },
                            onToggleActiveDay = ::toggleActiveDay,
                            onSelectActiveDays = { selectedActiveDays = it },
                            onSelectAllDay = ::selectAllDaySchedule,
                            onSetHours = ::setHoursSchedule,
                            onAddTimeWindow = ::addScheduleWindow,
                            onEditTimeWindow = ::editScheduleWindow,
                            onRemoveTimeWindow = ::removeScheduleWindow,
                            onSaveTimeWindow = ::saveScheduleWindow,
                            onCancelTimeWindow = ::cancelScheduleWindow,
                            onEditStartTime = ::showScheduleEditorStartPicker,
                            onEditEndTime = ::showScheduleEditorEndPicker,
                            builderStep = builderStep,
                            onBuilderStepChange = { builderStep = it },
                            onSaveRule = ::saveRule,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshDashboardState()
    }

    private fun refreshDashboardState() {
        resumeExpiredPauses()
        refreshStrictModeState()
        val savedRules = EarnItRuleStore.getRules(this)
        pauseExpirations = EarnItPauseStore.pauseExpirations(this)
        rules = savedRules
        refreshLaunchableApps()
        refreshUsageStats(savedRules)
        accessibilityServiceEnabled = isAccessibilityServiceEnabled()
    }

    private fun refreshStrictModeState() {
        if (::strictModeStore.isInitialized) {
            strictModeState = strictModeStore.state()
        }
    }

    private fun saveStrictModeConfiguration(configuration: StrictModeConfiguration) {
        strictModeState = strictModeStore.saveConfiguration(configuration)
    }

    private fun beginStrictModeActivation(configuration: StrictModeConfiguration) {
        strictModeState = strictModeStore.beginActivation(configuration)
    }

    private fun cancelStrictModeActivation() {
        strictModeState = strictModeStore.cancelActivation()
    }

    private fun refreshLaunchableApps(force: Boolean = false) {
        if (appLoadInProgress) return
        val now = System.currentTimeMillis()
        val stale = now - appListLoadedAtMillis > APP_LIST_REFRESH_INTERVAL_MS
        if (!force && launchableApps.isNotEmpty() && !stale) return

        appLoadInProgress = true
        appListLoading = launchableApps.isEmpty()
        thread(name = "EarnItAppLoader") {
            val loadedApps = EarnItRuleStore.launchableApps(this)
            runOnUiThread {
                launchableApps = loadedApps
                appListLoadedAtMillis = System.currentTimeMillis()
                appLoadInProgress = false
                appListLoading = false
            }
        }
    }

    private fun refreshUsageStats(savedRules: List<EarnItRuleStore.Rule>) {
        usageAccessGranted = hasUsageAccess()
        val enabledCount = savedRules.count { it.enabled }
        ruleStatusMessage = when {
            savedRules.isEmpty() -> "No rules saved. Add a rule to start."
            savedRules.size == 1 -> "1 rule saved. ${if (enabledCount == 1) "Enabled" else "Disabled"}."
            else -> "${savedRules.size} rules saved. $enabledCount enabled."
        }

        if (!usageAccessGranted) {
            ruleStates = savedRules.map { rule ->
                RuleDashboardState(
                    rule = rule,
                    productiveUsageSeconds = 0L,
                    remainingRewardSeconds = RewardLedger.snapshot(this, rule).remainingRewardSeconds
                )
            }
            usageStatusMessage = "Usage Access is off."
            return
        }

        ruleStates = savedRules.map { rule ->
            val productiveSeconds = if (rule.enabled) getTodayActiveProductiveUsageSeconds(rule) else 0L
            val snapshot = if (rule.enabled) {
                RewardLedger.creditProductiveUsage(this, rule, productiveSeconds)
            } else {
                RewardLedger.snapshot(this, rule)
            }
            RuleDashboardState(
                rule = rule,
                productiveUsageSeconds = productiveSeconds,
                remainingRewardSeconds = snapshot.remainingRewardSeconds
            )
        }
        usageStatusMessage = if (savedRules.any { it.enabled }) {
            "Tracking enabled rules today. Android usage data can be delayed."
        } else {
            "All rules are disabled."
        }
    }

    private fun startAddingRule() {
        settingsOpen = false
        selectedRuleDetailId = null
        builderStep = RuleBuilderStep.Earn
        manageRulesOpen = false
        unavailableRuleType = null
        builderEntryContext = null
        builderReturnRuleDetailId = null
        ruleTypeSelectionOpen = true
    }

    private fun closeRuleTypeSelection() {
        unavailableRuleType = null
        ruleTypeSelectionOpen = false
    }

    private fun startRuleType(ruleType: EarnItRuleStore.RuleType) {
        ruleTypeSelectionOpen = false
        unavailableRuleType = null
        startBuilder(
            rule = EarnItRuleStore.newRuleFromDefault(this, ruleType),
            entryContext = RuleBuilderEntryContext.Create
        )
    }

    private fun startEditingRule(rule: EarnItRuleStore.Rule) {
        startBuilder(rule = rule, entryContext = RuleBuilderEntryContext.Edit)
    }

    private fun startBuilder(rule: EarnItRuleStore.Rule, entryContext: RuleBuilderEntryContext) {
        settingsOpen = false
        selectedRuleDetailId = null
        ruleTypeSelectionOpen = false
        unavailableRuleType = null
        builderStep = if (rule.type == EarnItRuleStore.RuleType.ScheduledBlock) RuleBuilderStep.Reward else RuleBuilderStep.Earn
        manageRulesOpen = false
        editingRuleTemplate = rule
        builderEntryContext = entryContext
        builderReturnRuleDetailId = if (entryContext == RuleBuilderEntryContext.Edit) rule.id else null
        val hasEarnSelection = rule.productiveApps.isNotEmpty() || rule.productivePackage.isNotBlank()
        selectedProductivePackage = if (hasEarnSelection) {
            rule.earnApps.firstOrNull()?.packageName.orEmpty()
        } else {
            ""
        }
        selectedProductivePackages = if (hasEarnSelection) {
            rule.earnApps.map { it.packageName }.filter { it.isNotBlank() }.toSet()
        } else {
            emptySet()
        }
        selectedBlockedPackages = rule.blockedApps.map { it.packageName }.toSet()
        selectedRequirements = rule.requirements
        requirementPickerOpen = false
        requirementSearch = ""
        selectedRequirementPackage = null
        selectedRequirementMinutes = 10
        editingRequirementIndex = null
        selectedRatio = rule.rewardSecondsPerProductiveSecond
        selectedActiveDays = rule.activeDays
        selectedTimeWindows = rule.effectiveTimeWindows
        selectedStartMinute = selectedTimeWindows.first().startMinute
        selectedEndMinute = selectedTimeWindows.first().endMinute
        scheduleWindowEditorOpen = false
        editingScheduleWindowIndex = null
        scheduleEditorStartMinute = 9 * 60
        scheduleEditorEndMinute = 17 * 60
        productivePickerOpen = false
        blockedPickerOpen = false
        productiveSearch = ""
        blockedSearch = ""
    }

    private fun cancelEditingRule() {
        builderStep = RuleBuilderStep.Earn
        editingRuleTemplate = null
        builderEntryContext = null
        builderReturnRuleDetailId = null
        unavailableRuleType = null
        productivePickerOpen = false
        blockedPickerOpen = false
        requirementPickerOpen = false
        cancelScheduleWindow()
    }

    private fun exitBuilderFromFirstStage() {
        val destination = firstStageBuilderExitDestination(
            entryContext = builderEntryContext,
            editingRuleId = builderReturnRuleDetailId ?: editingRuleTemplate?.id
        )
        cancelEditingRule()
        when (destination.target) {
            RuleBuilderExitTarget.RuleTypeSelection -> ruleTypeSelectionOpen = true
            RuleBuilderExitTarget.RuleDetail -> selectedRuleDetailId = destination.ruleDetailId
        }
    }

    private fun returnToRuleTypeSelection() {
        builderStep = RuleBuilderStep.Earn
        editingRuleTemplate = null
        builderEntryContext = null
        builderReturnRuleDetailId = null
        unavailableRuleType = null
        productivePickerOpen = false
        blockedPickerOpen = false
        requirementPickerOpen = false
        cancelScheduleWindow()
        ruleTypeSelectionOpen = true
    }

    private fun selectProductiveApp(packageName: String) {
        selectedProductivePackages = if (packageName in selectedProductivePackages) {
            selectedProductivePackages - packageName
        } else {
            selectedProductivePackages + packageName
        }
        selectedProductivePackage = selectedProductivePackages.firstOrNull().orEmpty()
        productiveSearch = ""
    }

    private fun openRequirementPicker() {
        requirementPickerOpen = true
        requirementSearch = ""
        if (selectedRequirementPackage == null && editingRequirementIndex == null) {
            selectedRequirementMinutes = selectedRequirementMinutes.coerceAtLeast(1)
        }
        refreshLaunchableApps()
    }

    private fun selectRequirementApp(packageName: String) {
        selectedRequirementPackage = packageName
        requirementPickerOpen = false
        requirementSearch = ""
    }

    private fun saveRequirement() {
        val packageName = selectedRequirementPackage ?: return
        val app = launchableApps.firstOrNull { it.packageName == packageName }?.let {
            EarnItRuleStore.RuleApp(it.packageName, it.name)
        } ?: selectedRequirements.firstOrNull { it.app.packageName == packageName }?.app
            ?: return
        val requirement = EarnItRuleStore.RuleRequirement(
            app = app,
            requiredSeconds = selectedRequirementMinutes.coerceAtLeast(1) * 60L
        )
        val editIndex = editingRequirementIndex
        selectedRequirements = if (editIndex != null && editIndex in selectedRequirements.indices) {
            selectedRequirements.mapIndexed { index, existing -> if (index == editIndex) requirement else existing }
        } else {
            (selectedRequirements.filterNot { it.app.packageName == requirement.app.packageName } + requirement)
        }
        selectedRequirementPackage = null
        selectedRequirementMinutes = 10
        editingRequirementIndex = null
        requirementPickerOpen = false
        requirementSearch = ""
    }

    private fun editRequirement(index: Int) {
        val requirement = selectedRequirements.getOrNull(index) ?: return
        editingRequirementIndex = index
        selectedRequirementPackage = requirement.app.packageName
        selectedRequirementMinutes = (requirement.requiredSeconds / 60L).toInt().coerceAtLeast(1)
        requirementPickerOpen = false
        requirementSearch = ""
    }

    private fun deleteRequirement(index: Int) {
        selectedRequirements = selectedRequirements.filterIndexed { itemIndex, _ -> itemIndex != index }
        if (editingRequirementIndex == index) {
            editingRequirementIndex = null
            selectedRequirementPackage = null
            selectedRequirementMinutes = 10
            requirementPickerOpen = false
            requirementSearch = ""
        }
    }

    private fun cancelRequirementEditor() {
        requirementPickerOpen = false
        requirementSearch = ""
        selectedRequirementPackage = null
        selectedRequirementMinutes = 10
        editingRequirementIndex = null
    }
    private fun toggleActiveDay(day: Int) {
        selectedActiveDays = if (day in selectedActiveDays) {
            (selectedActiveDays - day).ifEmpty { setOf(day) }
        } else {
            selectedActiveDays + day
        }
    }

    private fun toggleBlockedApp(packageName: String) {
        selectedBlockedPackages = if (packageName in selectedBlockedPackages) {
            selectedBlockedPackages - packageName
        } else {
            selectedBlockedPackages + packageName
        }
    }

    private fun toggleRuleEnabled(rule: EarnItRuleStore.Rule) {
        if (!rule.enabled) {
            EarnItPauseStore.clearPause(this, rule.id)
        }
        EarnItRuleStore.setRuleEnabled(this, rule.id, !rule.enabled)
        refreshDashboardState()
    }

    private fun pauseRuleFor(rule: EarnItRuleStore.Rule, durationMillis: Long, reason: String? = null) {
        val expiresAt = System.currentTimeMillis() + durationMillis.coerceAtLeast(1L)
        EarnItPauseStore.pauseUntil(this, rule.id, expiresAt, reason)
        EarnItRuleStore.setRuleEnabled(this, rule.id, false)
        refreshDashboardState()
    }

    private fun resumeRule(rule: EarnItRuleStore.Rule) {
        EarnItPauseStore.clearPause(this, rule.id)
        EarnItRuleStore.setRuleEnabled(this, rule.id, true)
        refreshDashboardState()
    }

    private fun resumeExpiredPauses() {
        val now = System.currentTimeMillis()
        EarnItPauseStore.pauseExpirations(this)
            .filterValues { it <= now }
            .keys
            .forEach { ruleId ->
                EarnItPauseStore.clearPause(this, ruleId)
                EarnItRuleStore.setRuleEnabled(this, ruleId, true)
            }
    }

    private fun deleteRule(rule: EarnItRuleStore.Rule) {
        if (selectedRuleDetailId == rule.id) {
            selectedRuleDetailId = null
        }
        EarnItRuleStore.deleteRule(this, rule.id)
        EarnItPauseStore.clearPause(this, rule.id)
        if (editingRuleTemplate?.id == rule.id) {
            cancelEditingRule()
        }
        refreshDashboardState()
    }

    private fun selectAllDaySchedule() {
        selectedTimeWindows = listOf(EarnItRuleStore.TimeWindow(0, 1_440))
        selectedStartMinute = 0
        selectedEndMinute = 1_440
        cancelScheduleWindow()
    }

    private fun setHoursSchedule() {
        if (selectedTimeWindows.size == 1 && selectedTimeWindows.first() == EarnItRuleStore.TimeWindow(0, 1_440)) {
            selectedTimeWindows = listOf(EarnItRuleStore.TimeWindow(9 * 60, 17 * 60))
        }
        selectedStartMinute = selectedTimeWindows.first().startMinute
        selectedEndMinute = selectedTimeWindows.first().endMinute
    }

    private fun addScheduleWindow() {
        scheduleWindowEditorOpen = true
        editingScheduleWindowIndex = null
        scheduleEditorStartMinute = 9 * 60
        scheduleEditorEndMinute = 17 * 60
    }

    private fun editScheduleWindow(index: Int) {
        val window = selectedTimeWindows.getOrNull(index) ?: return
        scheduleWindowEditorOpen = true
        editingScheduleWindowIndex = index
        scheduleEditorStartMinute = window.startMinute
        scheduleEditorEndMinute = window.endMinute
    }

    private fun removeScheduleWindow(index: Int) {
        val updated = selectedTimeWindows.filterIndexed { itemIndex, _ -> itemIndex != index }
        selectedTimeWindows = updated.ifEmpty { listOf(EarnItRuleStore.TimeWindow(9 * 60, 17 * 60)) }
        selectedStartMinute = selectedTimeWindows.first().startMinute
        selectedEndMinute = selectedTimeWindows.first().endMinute
        if (editingScheduleWindowIndex == index) cancelScheduleWindow()
    }

    private fun saveScheduleWindow() {
        val window = EarnItRuleStore.TimeWindow(scheduleEditorStartMinute, scheduleEditorEndMinute)
        if (window.startMinute == window.endMinute) return
        val editIndex = editingScheduleWindowIndex
        val updated = if (editIndex != null && editIndex in selectedTimeWindows.indices) {
            selectedTimeWindows.mapIndexed { index, existing -> if (index == editIndex) window else existing }
        } else {
            selectedTimeWindows.filterNot { it == EarnItRuleStore.TimeWindow(0, 1_440) } + window
        }
        selectedTimeWindows = EarnItRuleStore.normalizeTimeWindows(updated)
        selectedStartMinute = selectedTimeWindows.first().startMinute
        selectedEndMinute = selectedTimeWindows.first().endMinute
        cancelScheduleWindow()
    }

    private fun cancelScheduleWindow() {
        scheduleWindowEditorOpen = false
        editingScheduleWindowIndex = null
    }

    private fun showScheduleEditorStartPicker() {
        showTimePicker(scheduleEditorStartMinute) { scheduleEditorStartMinute = it.coerceIn(0, 1_439) }
    }

    private fun showScheduleEditorEndPicker() {
        val dialogMinute = if (scheduleEditorEndMinute == 1_440) 0 else scheduleEditorEndMinute
        showTimePicker(dialogMinute) { selectedMinute ->
            scheduleEditorEndMinute = if (selectedMinute == 0) 1_440 else selectedMinute.coerceIn(1, 1_439)
        }
    }

    private fun showTimePicker(initialMinute: Int, onTimeSelected: (Int) -> Unit) {
        val safeMinute = initialMinute.coerceIn(0, 1_439)
        TimePickerDialog(
            this,
            { _, hourOfDay, minute -> onTimeSelected(hourOfDay * 60 + minute) },
            safeMinute / 60,
            safeMinute % 60,
            DateFormat.is24HourFormat(this)
        ).show()
    }

    private fun saveRule() {
        val editingRule = editingRuleTemplate ?: return
        val savedEarnApps = editingRule.earnApps.associateBy { it.packageName }
        val launchableEarnApps = launchableApps.associateBy { it.packageName }
        val productiveApps = selectedProductivePackages.mapNotNull { packageName ->
            launchableEarnApps[packageName]?.let {
                EarnItRuleStore.RuleApp(packageName = it.packageName, name = it.name)
            } ?: savedEarnApps[packageName]
        }
        val primaryProductiveApp = productiveApps.firstOrNull()
            ?: EarnItRuleStore.RuleApp(editingRule.productivePackage, editingRule.productiveName)
        if (editingRule.type == EarnItRuleStore.RuleType.EarnRewardTime && productiveApps.isEmpty()) return
        val savedBlockedApps = editingRule.blockedApps.associateBy { it.packageName }
        val launchableBlockedApps = launchableApps.associateBy { it.packageName }
        val blockedApps = selectedBlockedPackages.mapNotNull { packageName ->
            launchableBlockedApps[packageName]?.let {
                EarnItRuleStore.RuleApp(packageName = it.packageName, name = it.name)
            } ?: savedBlockedApps[packageName]
        }
        if (blockedApps.isEmpty()) return

        val rule = EarnItRuleStore.Rule(
            id = editingRule.id,
            productivePackage = primaryProductiveApp.packageName,
            productiveName = primaryProductiveApp.name,
            blockedApps = blockedApps,
            rewardSecondsPerProductiveSecond = selectedRatio,
            activeDays = selectedActiveDays,
            startMinute = selectedStartMinute,
            endMinute = selectedEndMinute,
            timeWindows = selectedTimeWindows,
            enabled = editingRule.enabled,
            type = editingRule.type,
            productiveApps = if (editingRule.type == EarnItRuleStore.RuleType.EarnRewardTime) productiveApps else emptyList(),
            requirements = if (editingRule.type == EarnItRuleStore.RuleType.CompleteToUnlock) selectedRequirements else emptyList()
        )
        EarnItRuleStore.saveRule(this, rule)
        editingRuleTemplate = null
        builderEntryContext = null
        builderReturnRuleDetailId = null
        builderStep = RuleBuilderStep.Earn
        productivePickerOpen = false
        blockedPickerOpen = false
        refreshDashboardState()
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(AppOpsManager::class.java)
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun getTodayActiveProductiveUsageSeconds(rule: EarnItRuleStore.Rule): Long {
        val usageStatsManager = getSystemService(UsageStatsManager::class.java)
        return RewardLedger.activeProductiveUsageSecondsToday(usageStatsManager, rule)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedService = ComponentName(this, EarnItAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        splitter.forEach { enabledService ->
            val componentName = ComponentName.unflattenFromString(enabledService)
            if (componentName == expectedService) {
                return true
            }
        }
        return false
    }

    private fun openUsageAccessSettings() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openEarnApp(packageName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        startActivity(launchIntent)
    }

    private fun completeFirstLaunchAndCreateRule() {
        setFirstLaunchComplete()
        startAddingRule()
    }

    private fun setFirstLaunchComplete() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FIRST_LAUNCH_COMPLETE, true)
            .apply()
        firstLaunchComplete = true
    }

    private fun isFirstLaunchComplete(): Boolean {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getBoolean(KEY_FIRST_LAUNCH_COMPLETE, false)
    }

    private companion object {
        const val PREFS_NAME = "earnit_setup"
        const val KEY_FIRST_LAUNCH_COMPLETE = "first_launch_complete"
        const val APP_LIST_REFRESH_INTERVAL_MS = 60_000L
    }
}

@Composable
internal fun Dashboard(
    ruleStates: List<RuleDashboardState>,
    editingRule: EarnItRuleStore.Rule?,
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
    selectedTimeWindows: List<EarnItRuleStore.TimeWindow>,
    scheduleWindowEditorOpen: Boolean,
    editingScheduleWindowIndex: Int?,
    scheduleEditorStartMinute: Int,
    scheduleEditorEndMinute: Int,
    productivePickerOpen: Boolean,
    blockedPickerOpen: Boolean,
    productiveSearch: String,
    blockedSearch: String,
    usageAccessGranted: Boolean,
    usageStatusMessage: String,
    ruleStatusMessage: String,
    accessibilityServiceEnabled: Boolean,
    manageRulesOpen: Boolean,
    pauseExpirations: Map<String, Long>,
    strictModeOpen: Boolean,
    strictModeState: StrictModeState,
    selectedRuleDetailId: String?,
    settingsOpen: Boolean,
    ruleTypeSelectionOpen: Boolean,
    unavailableRuleType: RuleTypeOption?,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    onOpenStrictMode: () -> Unit,
    onCloseStrictMode: () -> Unit,
    onSaveStrictModeConfiguration: (StrictModeConfiguration) -> Unit,
    onBeginStrictModeActivation: (StrictModeConfiguration) -> Unit,
    onCancelStrictModeActivation: () -> Unit,
    onStrictModeTick: () -> Unit,
    onOpenEarnApp: (String) -> Unit,
    onAddRule: () -> Unit,
    onBackFromRuleTypeSelection: () -> Unit,
    onBackFromUnavailableRuleType: () -> Unit,
    onSelectRuleType: (EarnItRuleStore.RuleType) -> Unit,
    onEditRule: (EarnItRuleStore.Rule) -> Unit,
    onToggleRuleEnabled: (EarnItRuleStore.Rule) -> Unit,
    onPauseRuleFor: (EarnItRuleStore.Rule, Long, String?) -> Unit,
    onResumeRule: (EarnItRuleStore.Rule) -> Unit,
    onPauseTimerTick: () -> Unit,
    onDeleteRule: (EarnItRuleStore.Rule) -> Unit,
    onToggleManageRules: () -> Unit,
    onOpenRuleDetail: (String) -> Unit,
    onBackFromRuleDetail: () -> Unit,
    onCancelEditingRule: () -> Unit,
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
    onSetHours: () -> Unit,
    onAddTimeWindow: () -> Unit,
    onEditTimeWindow: (Int) -> Unit,
    onRemoveTimeWindow: (Int) -> Unit,
    onSaveTimeWindow: () -> Unit,
    onCancelTimeWindow: () -> Unit,
    onEditStartTime: () -> Unit,
    onEditEndTime: () -> Unit,
    builderStep: RuleBuilderStep,
    onBuilderStepChange: (RuleBuilderStep) -> Unit,
    onSaveRule: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(pauseExpirations) {
        while (pauseExpirations.isNotEmpty()) {
            delay(1_000)
            onPauseTimerTick()
        }
    }
    LaunchedEffect(strictModeState.lifecycleState, strictModeState.activationGraceEndsAtMillis, strictModeState.expiresAtMillis) {
        while (strictModeState.lifecycleState == StrictModeLifecycleState.Activating ||
            (strictModeState.lifecycleState == StrictModeLifecycleState.Active && strictModeState.expiresAtMillis != null)
        ) {
            delay(1_000)
            onStrictModeTick()
        }
    }

    if (editingRule == null) {
        val homeRules = ruleStates.map { state ->
            homeRuleUiState(
                state = state,
                usageAccessGranted = usageAccessGranted,
                appBlockingEnabled = accessibilityServiceEnabled
            )
        }
        val permissionState = EarnItUiStateAdapters.permissionSetup(
            usageAccessGranted = usageAccessGranted,
            appBlockingEnabled = accessibilityServiceEnabled
        )
        val selectedHomeRule = selectedRuleDetailId?.let { selectedRuleId ->
            homeRules.firstOrNull { it.rule.id == selectedRuleId }
        }

        if (ruleTypeSelectionOpen) {
            EarnItRuleTypeSelection(
                onBack = onBackFromRuleTypeSelection,
                onSelectRuleType = onSelectRuleType,
                modifier = modifier
            )
        } else if (strictModeOpen) {
            EarnItStrictModeScreen(
                state = strictModeState,
                enabledRuleCount = ruleStates.count { it.rule.enabled },
                disabledRuleCount = ruleStates.count { !it.rule.enabled },
                onBack = onCloseStrictMode,
                onSaveConfiguration = onSaveStrictModeConfiguration,
                onBeginActivation = onBeginStrictModeActivation,
                onCancelActivation = onCancelStrictModeActivation,
                onTick = onStrictModeTick,
                modifier = modifier
            )
        } else if (settingsOpen) {
            EarnItSettings(
                permissionState = permissionState,
                hasRules = homeRules.isNotEmpty(),
                strictModeState = strictModeState,
                onBack = onCloseSettings,
                onOpenStrictMode = onOpenStrictMode,
                onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onCreateFirstRule = onAddRule,
                modifier = modifier
            )
        } else if (selectedHomeRule != null) {
            EarnItRuleDetail(
                homeRule = selectedHomeRule,
                detail = EarnItUiStateAdapters.ruleDetail(
                    card = selectedHomeRule.card,
                    rule = selectedHomeRule.rule
                ),
                pausedUntilMillis = pauseExpirations[selectedHomeRule.rule.id],
                permissionState = permissionState,
                onBack = onBackFromRuleDetail,
                onOpenEarnApp = onOpenEarnApp,
                onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onEditRule = onEditRule,
                onPauseRuleFor = onPauseRuleFor,
                onResumeRule = onResumeRule,
                onDeleteRule = { rule ->
                    onBackFromRuleDetail()
                    onDeleteRule(rule)
                },
                modifier = modifier
            )
        } else {
            EarnItHome(
                rules = homeRules,
                permissionState = permissionState,
                manageRulesOpen = manageRulesOpen,
                onAddRule = onAddRule,
                onOpenEarnApp = onOpenEarnApp,
                onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onOpenSettings = onOpenSettings,
                onToggleManageRules = onToggleManageRules,
                onOpenRuleDetail = onOpenRuleDetail,
                onEditRule = onEditRule,
                onToggleRuleEnabled = onToggleRuleEnabled,
                onDeleteRule = onDeleteRule,
                modifier = modifier
            )
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RuleEditor(
                rule = editingRule,
                apps = apps,
                appsLoading = appsLoading,
                selectedProductivePackage = selectedProductivePackage,
                selectedProductivePackages = selectedProductivePackages,
                selectedBlockedPackages = selectedBlockedPackages,
                selectedRequirements = selectedRequirements,
                requirementPickerOpen = requirementPickerOpen,
                requirementSearch = requirementSearch,
                selectedRequirementPackage = selectedRequirementPackage,
                selectedRequirementMinutes = selectedRequirementMinutes,
                editingRequirementIndex = editingRequirementIndex,
                selectedRatio = selectedRatio,
                selectedActiveDays = selectedActiveDays,
                selectedStartMinute = selectedStartMinute,
                selectedEndMinute = selectedEndMinute,
                selectedTimeWindows = selectedTimeWindows,
                scheduleWindowEditorOpen = scheduleWindowEditorOpen,
                editingScheduleWindowIndex = editingScheduleWindowIndex,
                scheduleEditorStartMinute = scheduleEditorStartMinute,
                scheduleEditorEndMinute = scheduleEditorEndMinute,
                productivePickerOpen = productivePickerOpen,
                blockedPickerOpen = blockedPickerOpen,
                productiveSearch = productiveSearch,
                blockedSearch = blockedSearch,
                onOpenProductivePicker = onOpenProductivePicker,
                onCloseProductivePicker = onCloseProductivePicker,
                onOpenBlockedPicker = onOpenBlockedPicker,
                onCloseBlockedPicker = onCloseBlockedPicker,
                onProductiveSearchChange = onProductiveSearchChange,
                onBlockedSearchChange = onBlockedSearchChange,
                onSelectProductiveApp = onSelectProductiveApp,
                onOpenRequirementPicker = onOpenRequirementPicker,
                onCloseRequirementPicker = onCloseRequirementPicker,
                onRequirementSearchChange = onRequirementSearchChange,
                onSelectRequirementApp = onSelectRequirementApp,
                onSelectRequirementMinutes = onSelectRequirementMinutes,
                onSaveRequirement = onSaveRequirement,
                onEditRequirement = onEditRequirement,
                onDeleteRequirement = onDeleteRequirement,
                onToggleBlockedApp = onToggleBlockedApp,
                onSelectRatio = onSelectRatio,
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
                onEditEndTime = onEditEndTime,
                builderStep = builderStep,
                onBuilderStepChange = onBuilderStepChange,
                onSaveRule = onSaveRule,
                onCancel = onCancelEditingRule
            )
        }
    }
}

@Composable
private fun RuleRow(
    state: RuleDashboardState,
    onEditRule: (EarnItRuleStore.Rule) -> Unit,
    onToggleRuleEnabled: (EarnItRuleStore.Rule) -> Unit,
    onDeleteRule: (EarnItRuleStore.Rule) -> Unit
) {
    val rule = state.rule
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = rule.productiveName, style = MaterialTheme.typography.titleSmall)
            Text(text = "Blocked: ${rule.blockedSummary}")
            Text(text = "Ratio: ${rule.ratioLabel}")
            Text(text = "Schedule: ${rule.scheduleLabel}")
            Text(text = "State: ${if (rule.enabled) "Enabled" else "Disabled"}")
            Text(text = "Productive today: ${formatDuration(state.productiveUsageSeconds)}")
            Text(text = "Available reward: ${formatDuration(state.remainingRewardSeconds)}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onEditRule(rule) }) {
                    Text(text = "Edit")
                }
                Button(onClick = { onToggleRuleEnabled(rule) }) {
                    Text(text = if (rule.enabled) "Disable" else "Enable")
                }
                Button(onClick = { onDeleteRule(rule) }) {
                    Text(text = "Delete")
                }
            }
        }
    }
}

@Composable
private fun RuleEditor(
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
    selectedTimeWindows: List<EarnItRuleStore.TimeWindow>,
    scheduleWindowEditorOpen: Boolean,
    editingScheduleWindowIndex: Int?,
    scheduleEditorStartMinute: Int,
    scheduleEditorEndMinute: Int,
    productivePickerOpen: Boolean,
    blockedPickerOpen: Boolean,
    productiveSearch: String,
    blockedSearch: String,
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
    onSetHours: () -> Unit,
    onAddTimeWindow: () -> Unit,
    onEditTimeWindow: (Int) -> Unit,
    onRemoveTimeWindow: (Int) -> Unit,
    onSaveTimeWindow: () -> Unit,
    onCancelTimeWindow: () -> Unit,
    onEditStartTime: () -> Unit,
    onEditEndTime: () -> Unit,
    builderStep: RuleBuilderStep,
    onBuilderStepChange: (RuleBuilderStep) -> Unit,
    onSaveRule: () -> Unit,
    onCancel: () -> Unit
) {
    EarnItRuleBuilder(
        rule = rule,
        apps = apps,
        appsLoading = appsLoading,
        selectedProductivePackage = selectedProductivePackage,
        selectedProductivePackages = selectedProductivePackages,
        selectedBlockedPackages = selectedBlockedPackages,
        selectedRequirements = selectedRequirements,
        requirementPickerOpen = requirementPickerOpen,
        requirementSearch = requirementSearch,
        selectedRequirementPackage = selectedRequirementPackage,
        selectedRequirementMinutes = selectedRequirementMinutes,
        editingRequirementIndex = editingRequirementIndex,
        selectedRatio = selectedRatio,
        selectedActiveDays = selectedActiveDays,
        selectedStartMinute = selectedStartMinute,
        selectedEndMinute = selectedEndMinute,
        selectedTimeWindows = selectedTimeWindows,
        scheduleWindowEditorOpen = scheduleWindowEditorOpen,
        editingScheduleWindowIndex = editingScheduleWindowIndex,
        scheduleEditorStartMinute = scheduleEditorStartMinute,
        scheduleEditorEndMinute = scheduleEditorEndMinute,
        productivePickerOpen = productivePickerOpen,
        blockedPickerOpen = blockedPickerOpen,
        productiveSearch = productiveSearch,
        blockedSearch = blockedSearch,
        builderStep = builderStep,
        onBuilderStepChange = onBuilderStepChange,
        onOpenProductivePicker = onOpenProductivePicker,
        onCloseProductivePicker = onCloseProductivePicker,
        onOpenBlockedPicker = onOpenBlockedPicker,
        onCloseBlockedPicker = onCloseBlockedPicker,
        onProductiveSearchChange = onProductiveSearchChange,
        onBlockedSearchChange = onBlockedSearchChange,
        onSelectProductiveApp = onSelectProductiveApp,
        onOpenRequirementPicker = onOpenRequirementPicker,
        onCloseRequirementPicker = onCloseRequirementPicker,
        onRequirementSearchChange = onRequirementSearchChange,
        onSelectRequirementApp = onSelectRequirementApp,
        onSelectRequirementMinutes = onSelectRequirementMinutes,
        onSaveRequirement = onSaveRequirement,
        onEditRequirement = onEditRequirement,
        onDeleteRequirement = onDeleteRequirement,
        onToggleBlockedApp = onToggleBlockedApp,
        onSelectRatio = onSelectRatio,
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
        onEditEndTime = onEditEndTime,
        onSaveRule = onSaveRule,
        onCancel = onCancel
    )
}


@Composable
fun EditorSection(
    title: String,
    helperText: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = helperText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}

@Composable
fun ProductiveAppSection(
    rule: EarnItRuleStore.Rule,
    apps: List<EarnItRuleStore.LaunchableApp>,
    selectedProductivePackage: String,
    pickerOpen: Boolean,
    search: String,
    onOpenPicker: () -> Unit,
    onClosePicker: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSelectApp: (String) -> Unit
) {
    val selectedName = apps.firstOrNull { it.packageName == selectedProductivePackage }?.name
        ?: if (rule.productivePackage == selectedProductivePackage) rule.productiveName else "None selected"
    Text(text = "Selected: $selectedName", style = MaterialTheme.typography.bodyLarge)
    Button(onClick = onOpenPicker, modifier = Modifier.fillMaxWidth()) {
        Text(text = "Choose Productive App")
    }
    if (pickerOpen) {
        AppSearchField(value = search, onValueChange = onSearchChange)
        AppPickerList(
            apps = apps.filteredBy(search),
            selectedPackages = setOf(selectedProductivePackage),
            onClickApp = onSelectApp
        )
        Button(onClick = onClosePicker, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Done")
        }
    }
}
@Composable
fun BlockedAppsSection(
    rule: EarnItRuleStore.Rule,
    apps: List<EarnItRuleStore.LaunchableApp>,
    selectedBlockedPackages: Set<String>,
    pickerOpen: Boolean,
    search: String,
    onOpenPicker: () -> Unit,
    onClosePicker: () -> Unit,
    onSearchChange: (String) -> Unit,
    onToggleApp: (String) -> Unit
) {
    val selectedNamesByPackage = rule.blockedApps.associate { it.packageName to it.name } +
        apps.associate { it.packageName to it.name }
    val selectedNames = selectedBlockedPackages.mapNotNull { selectedNamesByPackage[it] }
    val previewText = if (selectedNames.isEmpty()) {
        "No blocked apps selected"
    } else {
        selectedNames.take(3).joinToString(", ") + if (selectedNames.size > 3) " +${selectedNames.size - 3} more" else ""
    }
    Text(text = "Selected: ${selectedBlockedPackages.size} app${if (selectedBlockedPackages.size == 1) "" else "s"}", style = MaterialTheme.typography.bodyLarge)
    Text(text = previewText, style = MaterialTheme.typography.bodyMedium)
    Button(onClick = onOpenPicker, modifier = Modifier.fillMaxWidth()) {
        Text(text = "Choose Blocked Apps")
    }
    if (pickerOpen) {
        AppSearchField(value = search, onValueChange = onSearchChange)
        Button(onClick = onClosePicker, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Done")
        }
        Text(text = "Selected: ${selectedBlockedPackages.size}")
        AppPickerList(
            apps = apps.filteredBy(search),
            selectedPackages = selectedBlockedPackages,
            onClickApp = onToggleApp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState())
        )
    }
}
@Composable
private fun AppSearchField(value: String, onValueChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = "Search apps") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
private fun AppPickerList(
    apps: List<EarnItRuleStore.LaunchableApp>,
    selectedPackages: Set<String>,
    onClickApp: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        apps.forEach { app ->
            Button(onClick = { onClickApp(app.packageName) }, modifier = Modifier.fillMaxWidth()) {
                Text(text = if (app.packageName in selectedPackages) "${app.name} *" else app.name)
            }
        }
    }
}

@Composable
fun DayButtons(selectedActiveDays: Set<Int>, onToggleActiveDay: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EarnItRuleStore.allDays.take(4).forEach { day ->
            DayButton(day = day, selectedActiveDays = selectedActiveDays, onToggleActiveDay = onToggleActiveDay)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EarnItRuleStore.allDays.drop(4).forEach { day ->
            DayButton(day = day, selectedActiveDays = selectedActiveDays, onToggleActiveDay = onToggleActiveDay)
        }
    }
}

@Composable
private fun DayButton(day: Int, selectedActiveDays: Set<Int>, onToggleActiveDay: (Int) -> Unit) {
    Button(onClick = { onToggleActiveDay(day) }) {
        Text(text = EarnItRuleStore.dayShortName(day).take(1))
    }
}

private fun List<EarnItRuleStore.LaunchableApp>.filteredBy(query: String): List<EarnItRuleStore.LaunchableApp> {
    val trimmedQuery = query.trim()
    if (trimmedQuery.isEmpty()) return this
    return filter { it.name.contains(trimmedQuery, ignoreCase = true) }
}

private fun formatDuration(totalSeconds: Long): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0L)
    val minutes = safeSeconds / 60L
    val seconds = safeSeconds % 60L
    return if (minutes > 0L) "${minutes}m ${seconds}s" else "${seconds}s"
}

@Preview(showBackground = true)
@Composable
fun DashboardPreview() {
    val rule = EarnItRuleStore.Rule(
        id = "preview",
        productivePackage = "com.duolingo",
        productiveName = "Duolingo",
        blockedApps = listOf(EarnItRuleStore.RuleApp("com.instagram.android", "Instagram")),
        rewardSecondsPerProductiveSecond = 1,
        activeDays = EarnItRuleStore.allDays.toSet(),
        startMinute = 0,
        endMinute = 1_440,
        enabled = true
    )
    EarnitV2Theme {
        Dashboard(
            ruleStates = listOf(RuleDashboardState(rule, 735, 180)),
            editingRule = null,
            apps = emptyList(),
            appsLoading = false,
            selectedProductivePackage = "com.duolingo",
            selectedProductivePackages = setOf("com.duolingo"),
            selectedBlockedPackages = setOf("com.instagram.android"),
            selectedRequirements = emptyList(),
            requirementPickerOpen = false,
            requirementSearch = "",
            selectedRequirementPackage = null,
            selectedRequirementMinutes = 10,
            editingRequirementIndex = null,
            selectedRatio = 1,
            selectedActiveDays = EarnItRuleStore.allDays.toSet(),
            selectedStartMinute = 0,
            selectedEndMinute = 1_440,
            selectedTimeWindows = listOf(EarnItRuleStore.TimeWindow(0, 1_440)),
            scheduleWindowEditorOpen = false,
            editingScheduleWindowIndex = null,
            scheduleEditorStartMinute = 9 * 60,
            scheduleEditorEndMinute = 17 * 60,
            productivePickerOpen = false,
            blockedPickerOpen = false,
            productiveSearch = "",
            blockedSearch = "",
            usageAccessGranted = true,
            usageStatusMessage = "Tracking enabled rules today.",
            ruleStatusMessage = "1 rule saved. Enabled.",
            accessibilityServiceEnabled = true,
            manageRulesOpen = false,
            pauseExpirations = emptyMap(),
            strictModeOpen = false,
            strictModeState = StrictModeState(),
            selectedRuleDetailId = null,
            settingsOpen = false,
            ruleTypeSelectionOpen = false,
            unavailableRuleType = null,
            onOpenUsageAccessSettings = {},
            onOpenAccessibilitySettings = {},
            onOpenSettings = {},
            onCloseSettings = {},
            onOpenStrictMode = {},
            onCloseStrictMode = {},
            onSaveStrictModeConfiguration = {},
            onBeginStrictModeActivation = {},
            onCancelStrictModeActivation = {},
            onStrictModeTick = {},
            onOpenEarnApp = {},
            onAddRule = {},
            onBackFromRuleTypeSelection = {},
            onBackFromUnavailableRuleType = {},
            onSelectRuleType = {},
            onEditRule = {},
            onToggleRuleEnabled = {},
            onPauseRuleFor = { _, _, _ -> },
            onResumeRule = {},
            onPauseTimerTick = {},
            onDeleteRule = {},
            onToggleManageRules = {},
            onOpenRuleDetail = {},
            onBackFromRuleDetail = {},
            onCancelEditingRule = {},
            onOpenProductivePicker = {},
            onCloseProductivePicker = {},
            onOpenBlockedPicker = {},
            onCloseBlockedPicker = {},
            onProductiveSearchChange = {},
            onBlockedSearchChange = {},
            onSelectProductiveApp = {},
            onOpenRequirementPicker = {},
            onCloseRequirementPicker = {},
            onRequirementSearchChange = {},
            onSelectRequirementApp = {},
            onSelectRequirementMinutes = {},
            onSaveRequirement = {},
            onEditRequirement = {},
            onDeleteRequirement = {},
            onToggleBlockedApp = {},
            onSelectRatio = {},
            onToggleActiveDay = {},
            onSelectActiveDays = {},
            onSelectAllDay = {},
            onSetHours = {},
            onAddTimeWindow = {},
            onEditTimeWindow = {},
            onRemoveTimeWindow = {},
            onSaveTimeWindow = {},
            onCancelTimeWindow = {},
            onEditStartTime = {},
            onEditEndTime = {},
            builderStep = RuleBuilderStep.Earn,
            onBuilderStepChange = {},
            onSaveRule = {}
        )
    }
}
