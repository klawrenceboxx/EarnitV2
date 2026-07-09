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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.earnitv2.ui.theme.EarnitV2Theme

data class RuleDashboardState(
    val rule: EarnItRuleStore.Rule,
    val productiveUsageSeconds: Long,
    val remainingRewardSeconds: Long
)

class MainActivity : ComponentActivity() {
    private var rules by mutableStateOf(emptyList<EarnItRuleStore.Rule>())
    private var ruleStates by mutableStateOf(emptyList<RuleDashboardState>())
    private var editingRuleTemplate by mutableStateOf<EarnItRuleStore.Rule?>(null)
    private var launchableApps by mutableStateOf(emptyList<EarnItRuleStore.LaunchableApp>())
    private var selectedProductivePackage by mutableStateOf("")
    private var selectedProductivePackages by mutableStateOf(emptySet<String>())
    private var selectedBlockedPackages by mutableStateOf(emptySet<String>())
    private var selectedRatio by mutableStateOf(1)
    private var selectedActiveDays by mutableStateOf(EarnItRuleStore.allDays.toSet())
    private var selectedStartMinute by mutableStateOf(0)
    private var selectedEndMinute by mutableStateOf(1_440)
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
    private var ruleTypeSelectionOpen by mutableStateOf(false)
    private var unavailableRuleType by mutableStateOf<RuleTypeOption?>(null)
    private var firstLaunchComplete by mutableStateOf(false)
    private var firstLaunchStep by mutableStateOf(FirstLaunchStep.ValueIntroduction)
    private var builderStep by mutableStateOf(RuleBuilderStep.Earn)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
                            selectedProductivePackage = selectedProductivePackage,
                            selectedProductivePackages = selectedProductivePackages,
                            selectedBlockedPackages = selectedBlockedPackages,
                            selectedRatio = selectedRatio,
                            selectedActiveDays = selectedActiveDays,
                            selectedStartMinute = selectedStartMinute,
                            selectedEndMinute = selectedEndMinute,
                            productivePickerOpen = productivePickerOpen,
                            blockedPickerOpen = blockedPickerOpen,
                            productiveSearch = productiveSearch,
                            blockedSearch = blockedSearch,
                            usageAccessGranted = usageAccessGranted,
                            usageStatusMessage = usageStatusMessage,
                            ruleStatusMessage = ruleStatusMessage,
                            accessibilityServiceEnabled = accessibilityServiceEnabled,
                            manageRulesOpen = manageRulesOpen,
                            selectedRuleDetailId = selectedRuleDetailId,
                            settingsOpen = settingsOpen,
                            ruleTypeSelectionOpen = ruleTypeSelectionOpen,
                            unavailableRuleType = unavailableRuleType,
                            onOpenUsageAccessSettings = ::openUsageAccessSettings,
                            onOpenAccessibilitySettings = ::openAccessibilitySettings,
                            onOpenSettings = { settingsOpen = true },
                            onCloseSettings = { settingsOpen = false },
                            onOpenEarnApp = ::openEarnApp,
                            onAddRule = ::startAddingRule,
                            onBackFromRuleTypeSelection = ::closeRuleTypeSelection,
                            onBackFromUnavailableRuleType = { unavailableRuleType = null },
                            onSelectRuleType = ::startRuleType,
                            onEditRule = ::startEditingRule,
                            onToggleRuleEnabled = ::toggleRuleEnabled,
                            onDeleteRule = ::deleteRule,
                            onToggleManageRules = { manageRulesOpen = !manageRulesOpen },
                            onOpenRuleDetail = { selectedRuleDetailId = it },
                            onBackFromRuleDetail = { selectedRuleDetailId = null },
                            onCancelEditingRule = ::cancelEditingRule,
                            onOpenProductivePicker = { productivePickerOpen = true },
                            onCloseProductivePicker = { productivePickerOpen = false },
                            onOpenBlockedPicker = { blockedPickerOpen = true },
                            onCloseBlockedPicker = { blockedPickerOpen = false },
                            onProductiveSearchChange = { productiveSearch = it },
                            onBlockedSearchChange = { blockedSearch = it },
                            onSelectProductiveApp = ::selectProductiveApp,
                            onToggleBlockedApp = ::toggleBlockedApp,
                            onSelectRatio = { selectedRatio = it },
                            onToggleActiveDay = ::toggleActiveDay,
                            onSelectActiveDays = { selectedActiveDays = it },
                            onSelectAllDay = { selectedStartMinute = 0; selectedEndMinute = 1_440 },
                            onEditStartTime = ::showStartTimePicker,
                            onEditEndTime = ::showEndTimePicker,
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
        val savedRules = EarnItRuleStore.getRules(this)
        rules = savedRules
        launchableApps = EarnItRuleStore.launchableApps(this)
        refreshUsageStats(savedRules)
        accessibilityServiceEnabled = isAccessibilityServiceEnabled()
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
        ruleTypeSelectionOpen = true
    }

    private fun closeRuleTypeSelection() {
        unavailableRuleType = null
        ruleTypeSelectionOpen = false
    }

    private fun startRuleType(ruleType: EarnItRuleStore.RuleType) {
        ruleTypeSelectionOpen = false
        unavailableRuleType = null
        startEditingRule(EarnItRuleStore.newRuleFromDefault(this, ruleType))
    }

    private fun startEditingRule(rule: EarnItRuleStore.Rule) {
        settingsOpen = false
        selectedRuleDetailId = null
        ruleTypeSelectionOpen = false
        unavailableRuleType = null
        builderStep = if (rule.type == EarnItRuleStore.RuleType.ScheduledBlock) RuleBuilderStep.Reward else RuleBuilderStep.Earn
        manageRulesOpen = false
        editingRuleTemplate = rule
        selectedProductivePackage = rule.earnApps.firstOrNull()?.packageName ?: rule.productivePackage
        selectedProductivePackages = rule.earnApps.map { it.packageName }.toSet()
        selectedBlockedPackages = rule.blockedApps.map { it.packageName }.toSet()
        selectedRatio = rule.rewardSecondsPerProductiveSecond
        selectedActiveDays = rule.activeDays
        selectedStartMinute = rule.startMinute
        selectedEndMinute = rule.endMinute
        productivePickerOpen = false
        blockedPickerOpen = false
        productiveSearch = ""
        blockedSearch = ""
    }

    private fun cancelEditingRule() {
        builderStep = RuleBuilderStep.Earn
        editingRuleTemplate = null
        unavailableRuleType = null
        productivePickerOpen = false
        blockedPickerOpen = false
    }

    private fun selectProductiveApp(packageName: String) {
        selectedProductivePackages = if (packageName in selectedProductivePackages) {
            (selectedProductivePackages - packageName).ifEmpty { setOf(packageName) }
        } else {
            selectedProductivePackages + packageName
        }
        selectedProductivePackage = selectedProductivePackages.firstOrNull() ?: packageName
        productiveSearch = ""
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
        EarnItRuleStore.setRuleEnabled(this, rule.id, !rule.enabled)
        refreshDashboardState()
    }

    private fun deleteRule(rule: EarnItRuleStore.Rule) {
        if (selectedRuleDetailId == rule.id) {
            selectedRuleDetailId = null
        }
        EarnItRuleStore.deleteRule(this, rule.id)
        if (editingRuleTemplate?.id == rule.id) {
            cancelEditingRule()
        }
        refreshDashboardState()
    }

    private fun showStartTimePicker() {
        showTimePicker(selectedStartMinute) { selectedStartMinute = it.coerceIn(0, 1_439) }
    }

    private fun showEndTimePicker() {
        val dialogMinute = if (selectedEndMinute == 1_440) 0 else selectedEndMinute
        showTimePicker(dialogMinute) { selectedMinute ->
            selectedEndMinute = if (selectedMinute == 0) 1_440 else selectedMinute.coerceIn(1, 1_439)
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
            enabled = editingRule.enabled
        )
        EarnItRuleStore.saveRule(this, rule)
        editingRuleTemplate = null
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
    }
}

@Composable
fun Dashboard(
    ruleStates: List<RuleDashboardState>,
    editingRule: EarnItRuleStore.Rule?,
    apps: List<EarnItRuleStore.LaunchableApp>,
    selectedProductivePackage: String,
    selectedProductivePackages: Set<String>,
    selectedBlockedPackages: Set<String>,
    selectedRatio: Int,
    selectedActiveDays: Set<Int>,
    selectedStartMinute: Int,
    selectedEndMinute: Int,
    productivePickerOpen: Boolean,
    blockedPickerOpen: Boolean,
    productiveSearch: String,
    blockedSearch: String,
    usageAccessGranted: Boolean,
    usageStatusMessage: String,
    ruleStatusMessage: String,
    accessibilityServiceEnabled: Boolean,
    manageRulesOpen: Boolean,
    selectedRuleDetailId: String?,
    settingsOpen: Boolean,
    ruleTypeSelectionOpen: Boolean,
    unavailableRuleType: RuleTypeOption?,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    onOpenEarnApp: (String) -> Unit,
    onAddRule: () -> Unit,
    onBackFromRuleTypeSelection: () -> Unit,
    onBackFromUnavailableRuleType: () -> Unit,
    onSelectRuleType: (EarnItRuleStore.RuleType) -> Unit,
    onEditRule: (EarnItRuleStore.Rule) -> Unit,
    onToggleRuleEnabled: (EarnItRuleStore.Rule) -> Unit,
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
    onToggleBlockedApp: (String) -> Unit,
    onSelectRatio: (Int) -> Unit,
    onToggleActiveDay: (Int) -> Unit,
    onSelectActiveDays: (Set<Int>) -> Unit,
    onSelectAllDay: () -> Unit,
    onEditStartTime: () -> Unit,
    onEditEndTime: () -> Unit,
    builderStep: RuleBuilderStep,
    onBuilderStepChange: (RuleBuilderStep) -> Unit,
    onSaveRule: () -> Unit,
    modifier: Modifier = Modifier
) {
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
        } else if (settingsOpen) {
            EarnItSettings(
                permissionState = permissionState,
                hasRules = homeRules.isNotEmpty(),
                onBack = onCloseSettings,
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
                permissionState = permissionState,
                onBack = onBackFromRuleDetail,
                onOpenEarnApp = onOpenEarnApp,
                onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onEditRule = onEditRule,
                onToggleRuleEnabled = onToggleRuleEnabled,
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
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RuleEditor(
                rule = editingRule,
                apps = apps,
                selectedProductivePackage = selectedProductivePackage,
                selectedProductivePackages = selectedProductivePackages,
                selectedBlockedPackages = selectedBlockedPackages,
                selectedRatio = selectedRatio,
                selectedActiveDays = selectedActiveDays,
                selectedStartMinute = selectedStartMinute,
                selectedEndMinute = selectedEndMinute,
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
                onToggleBlockedApp = onToggleBlockedApp,
                onSelectRatio = onSelectRatio,
                onToggleActiveDay = onToggleActiveDay,
                onSelectActiveDays = onSelectActiveDays,
                onSelectAllDay = onSelectAllDay,
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
    selectedProductivePackage: String,
    selectedProductivePackages: Set<String>,
    selectedBlockedPackages: Set<String>,
    selectedRatio: Int,
    selectedActiveDays: Set<Int>,
    selectedStartMinute: Int,
    selectedEndMinute: Int,
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
    onToggleBlockedApp: (String) -> Unit,
    onSelectRatio: (Int) -> Unit,
    onToggleActiveDay: (Int) -> Unit,
    onSelectActiveDays: (Set<Int>) -> Unit,
    onSelectAllDay: () -> Unit,
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
        selectedProductivePackage = selectedProductivePackage,
        selectedProductivePackages = selectedProductivePackages,
        selectedBlockedPackages = selectedBlockedPackages,
        selectedRatio = selectedRatio,
        selectedActiveDays = selectedActiveDays,
        selectedStartMinute = selectedStartMinute,
        selectedEndMinute = selectedEndMinute,
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
        onToggleBlockedApp = onToggleBlockedApp,
        onSelectRatio = onSelectRatio,
        onToggleActiveDay = onToggleActiveDay,
        onSelectActiveDays = onSelectActiveDays,
        onSelectAllDay = onSelectAllDay,
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(text = helperText, style = MaterialTheme.typography.bodyMedium)
            content()
        }
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
        Text(text = if (day in selectedActiveDays) "${EarnItRuleStore.dayShortName(day)} selected" else EarnItRuleStore.dayShortName(day))
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
            selectedProductivePackage = "com.duolingo",
            selectedProductivePackages = setOf("com.duolingo"),
            selectedBlockedPackages = setOf("com.instagram.android"),
            selectedRatio = 1,
            selectedActiveDays = EarnItRuleStore.allDays.toSet(),
            selectedStartMinute = 0,
            selectedEndMinute = 1_440,
            productivePickerOpen = false,
            blockedPickerOpen = false,
            productiveSearch = "",
            blockedSearch = "",
            usageAccessGranted = true,
            usageStatusMessage = "Tracking enabled rules today.",
            ruleStatusMessage = "1 rule saved. Enabled.",
            accessibilityServiceEnabled = true,
            manageRulesOpen = false,
            selectedRuleDetailId = null,
            settingsOpen = false,
            ruleTypeSelectionOpen = false,
            unavailableRuleType = null,
            onOpenUsageAccessSettings = {},
            onOpenAccessibilitySettings = {},
            onOpenSettings = {},
            onCloseSettings = {},
            onOpenEarnApp = {},
            onAddRule = {},
            onBackFromRuleTypeSelection = {},
            onBackFromUnavailableRuleType = {},
            onSelectRuleType = {},
            onEditRule = {},
            onToggleRuleEnabled = {},
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
            onToggleBlockedApp = {},
            onSelectRatio = {},
            onToggleActiveDay = {},
            onSelectActiveDays = {},
            onSelectAllDay = {},
            onEditStartTime = {},
            onEditEndTime = {},
            builderStep = RuleBuilderStep.Earn,
            onBuilderStepChange = {},
            onSaveRule = {}
        )
    }
}
