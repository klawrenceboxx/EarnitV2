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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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

class MainActivity : ComponentActivity() {
    private var activeRule by mutableStateOf<EarnItRuleStore.Rule?>(null)
    private var launchableApps by mutableStateOf(emptyList<EarnItRuleStore.LaunchableApp>())
    private var selectedProductivePackage by mutableStateOf("")
    private var selectedBlockedPackages by mutableStateOf(emptySet<String>())
    private var selectedRatio by mutableStateOf(1)
    private var selectedActiveDays by mutableStateOf(EarnItRuleStore.allDays.toSet())
    private var selectedStartMinute by mutableStateOf(0)
    private var selectedEndMinute by mutableStateOf(1_440)
    private var editingRule by mutableStateOf(false)
    private var productivePickerOpen by mutableStateOf(false)
    private var blockedPickerOpen by mutableStateOf(false)
    private var productiveSearch by mutableStateOf("")
    private var blockedSearch by mutableStateOf("")
    private var usageAccessGranted by mutableStateOf(false)
    private var productiveUsageSeconds by mutableStateOf(0L)
    private var remainingRewardSeconds by mutableStateOf(0L)
    private var usageStatusMessage by mutableStateOf("")
    private var ruleStatusMessage by mutableStateOf("")
    private var accessibilityServiceEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refreshDashboardState()
        setContent {
            EarnitV2Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Dashboard(
                        rule = activeRule ?: EarnItRuleStore.getRule(this),
                        apps = launchableApps,
                        selectedProductivePackage = selectedProductivePackage,
                        selectedBlockedPackages = selectedBlockedPackages,
                        selectedRatio = selectedRatio,
                        selectedActiveDays = selectedActiveDays,
                        selectedStartMinute = selectedStartMinute,
                        selectedEndMinute = selectedEndMinute,
                        editingRule = editingRule,
                        productivePickerOpen = productivePickerOpen,
                        blockedPickerOpen = blockedPickerOpen,
                        productiveSearch = productiveSearch,
                        blockedSearch = blockedSearch,
                        usageAccessGranted = usageAccessGranted,
                        productiveUsageSeconds = productiveUsageSeconds,
                        remainingRewardSeconds = remainingRewardSeconds,
                        usageStatusMessage = usageStatusMessage,
                        ruleStatusMessage = ruleStatusMessage,
                        accessibilityServiceEnabled = accessibilityServiceEnabled,
                        onOpenUsageAccessSettings = ::openUsageAccessSettings,
                        onOpenAccessibilitySettings = ::openAccessibilitySettings,
                        onStartEditingRule = ::startEditingRule,
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
                        onEditStartTime = ::showStartTimePicker,
                        onEditEndTime = ::showEndTimePicker,
                        onSaveRule = ::saveRule,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshDashboardState()
    }

    private fun refreshDashboardState() {
        val rule = EarnItRuleStore.getRule(this)
        activeRule = rule
        launchableApps = EarnItRuleStore.launchableApps(this)
        if (!editingRule) {
            selectedProductivePackage = rule.productivePackage
            selectedBlockedPackages = rule.blockedApps.map { it.packageName }.toSet()
            selectedRatio = rule.rewardSecondsPerProductiveSecond
            selectedActiveDays = rule.activeDays
            selectedStartMinute = rule.startMinute
            selectedEndMinute = rule.endMinute
        }
        refreshUsageStats(rule)
        accessibilityServiceEnabled = isAccessibilityServiceEnabled()
    }

    private fun refreshUsageStats(rule: EarnItRuleStore.Rule) {
        usageAccessGranted = hasUsageAccess()
        ruleStatusMessage = when {
            !EarnItRuleStore.appInstalled(this, rule.productivePackage) -> "Productive app is not installed: ${rule.productiveName}."
            rule.blockedApps.none { EarnItRuleStore.appInstalled(this, it.packageName) } -> "No selected blocked apps are installed."
            else -> "Active rule: ${rule.productiveName} earns access to ${rule.blockedAppCount} blocked app${if (rule.blockedAppCount == 1) "" else "s"} at ${rule.ratioLabel}. ${rule.scheduleLabel}."
        }

        if (!usageAccessGranted) {
            productiveUsageSeconds = 0L
            val snapshot = RewardLedger.snapshot(this, rule)
            remainingRewardSeconds = snapshot.remainingRewardSeconds
            usageStatusMessage = "Usage Access is off."
            return
        }

        productiveUsageSeconds = getTodayActiveProductiveUsageSeconds(rule)
        val snapshot = RewardLedger.creditProductiveUsage(this, rule, productiveUsageSeconds)
        remainingRewardSeconds = snapshot.remainingRewardSeconds
        usageStatusMessage = if (productiveUsageSeconds == 0L) {
            "No productive app usage recorded today. Android usage data can be delayed."
        } else {
            "Tracking productive app usage today."
        }
    }

    private fun startEditingRule() {
        val rule = activeRule ?: EarnItRuleStore.getRule(this)
        selectedProductivePackage = rule.productivePackage
        selectedBlockedPackages = rule.blockedApps.map { it.packageName }.toSet()
        selectedRatio = rule.rewardSecondsPerProductiveSecond
        selectedActiveDays = rule.activeDays
        selectedStartMinute = rule.startMinute
        selectedEndMinute = rule.endMinute
        productivePickerOpen = false
        blockedPickerOpen = false
        productiveSearch = ""
        blockedSearch = ""
        editingRule = true
    }

    private fun cancelEditingRule() {
        editingRule = false
        productivePickerOpen = false
        blockedPickerOpen = false
    }

    private fun selectProductiveApp(packageName: String) {
        selectedProductivePackage = packageName
        productivePickerOpen = false
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
        val currentRule = activeRule ?: EarnItRuleStore.getRule(this)
        val productiveApp = launchableApps.firstOrNull { it.packageName == selectedProductivePackage }
            ?: EarnItRuleStore.LaunchableApp(currentRule.productivePackage, currentRule.productiveName)
        val savedBlockedApps = currentRule.blockedApps.associateBy { it.packageName }
        val launchableBlockedApps = launchableApps.associateBy { it.packageName }
        val blockedApps = selectedBlockedPackages.mapNotNull { packageName ->
            launchableBlockedApps[packageName]?.let {
                EarnItRuleStore.RuleApp(packageName = it.packageName, name = it.name)
            } ?: savedBlockedApps[packageName]
        }
        if (blockedApps.isEmpty()) return

        val rule = EarnItRuleStore.Rule(
            productivePackage = productiveApp.packageName,
            productiveName = productiveApp.name,
            blockedApps = blockedApps,
            rewardSecondsPerProductiveSecond = selectedRatio,
            activeDays = selectedActiveDays,
            startMinute = selectedStartMinute,
            endMinute = selectedEndMinute
        )
        EarnItRuleStore.saveRule(this, rule)
        editingRule = false
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
}

@Composable
fun Dashboard(
    rule: EarnItRuleStore.Rule,
    apps: List<EarnItRuleStore.LaunchableApp>,
    selectedProductivePackage: String,
    selectedBlockedPackages: Set<String>,
    selectedRatio: Int,
    selectedActiveDays: Set<Int>,
    selectedStartMinute: Int,
    selectedEndMinute: Int,
    editingRule: Boolean,
    productivePickerOpen: Boolean,
    blockedPickerOpen: Boolean,
    productiveSearch: String,
    blockedSearch: String,
    usageAccessGranted: Boolean,
    productiveUsageSeconds: Long,
    remainingRewardSeconds: Long,
    usageStatusMessage: String,
    ruleStatusMessage: String,
    accessibilityServiceEnabled: Boolean,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onStartEditingRule: () -> Unit,
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
    onEditStartTime: () -> Unit,
    onEditEndTime: () -> Unit,
    onSaveRule: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "EarnIt", style = MaterialTheme.typography.headlineMedium)
        Text(text = ruleStatusMessage, style = MaterialTheme.typography.bodyMedium)
        Text(text = "Productive time today: ${formatDuration(productiveUsageSeconds)}")
        Text(text = "Available reward time: ${formatDuration(remainingRewardSeconds)}")
        Text(text = if (remainingRewardSeconds > 0L) "Status: Unlocked" else "Status: Locked")
        Text(text = if (usageAccessGranted) "Usage Access: On" else "Usage Access: Off")
        Text(text = usageStatusMessage, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onOpenUsageAccessSettings, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Open Usage Access Settings")
        }
        Text(text = if (accessibilityServiceEnabled) "Accessibility Service: On" else "Accessibility Service: Off")
        Button(onClick = onOpenAccessibilitySettings, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Open Accessibility Settings")
        }

        if (editingRule) {
            RuleEditor(
                rule = rule,
                apps = apps,
                selectedProductivePackage = selectedProductivePackage,
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
                onEditStartTime = onEditStartTime,
                onEditEndTime = onEditEndTime,
                onSaveRule = onSaveRule,
                onCancel = onCancelEditingRule
            )
        } else {
            Button(onClick = onStartEditingRule, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Edit EarnIt Rule")
            }
        }
    }
}

@Composable
private fun RuleEditor(
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
    ProductiveAppSection(
        rule = rule,
        apps = apps,
        selectedProductivePackage = selectedProductivePackage,
        pickerOpen = productivePickerOpen,
        search = productiveSearch,
        onOpenPicker = onOpenProductivePicker,
        onClosePicker = onCloseProductivePicker,
        onSearchChange = onProductiveSearchChange,
        onSelectApp = onSelectProductiveApp
    )
    BlockedAppsSection(
        rule = rule,
        apps = apps,
        selectedBlockedPackages = selectedBlockedPackages,
        pickerOpen = blockedPickerOpen,
        search = blockedSearch,
        onOpenPicker = onOpenBlockedPicker,
        onClosePicker = onCloseBlockedPicker,
        onSearchChange = onBlockedSearchChange,
        onToggleApp = onToggleBlockedApp
    )
    Text(text = "Ratio", style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EarnItRuleStore.allowedRatios.forEach { ratio ->
            Button(onClick = { onSelectRatio(ratio) }) {
                Text(text = if (selectedRatio == ratio) "1:$ratio *" else "1:$ratio")
            }
        }
    }
    Text(text = "Active days", style = MaterialTheme.typography.titleSmall)
    DayButtons(selectedActiveDays = selectedActiveDays, onToggleActiveDay = onToggleActiveDay)
    Text(text = "Start time", style = MaterialTheme.typography.titleSmall)
    Button(onClick = onEditStartTime, modifier = Modifier.fillMaxWidth()) {
        Text(text = EarnItRuleStore.formatMinute(selectedStartMinute))
    }
    Text(text = "End time", style = MaterialTheme.typography.titleSmall)
    Button(onClick = onEditEndTime, modifier = Modifier.fillMaxWidth()) {
        Text(text = EarnItRuleStore.formatMinute(selectedEndMinute))
    }
    Button(onClick = onSaveRule, modifier = Modifier.fillMaxWidth()) {
        Text(text = "Save Rule")
    }
    Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
        Text(text = "Cancel")
    }
}

@Composable
private fun ProductiveAppSection(
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
    Text(text = "Productive app", style = MaterialTheme.typography.titleSmall)
    Text(text = selectedName)
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
private fun BlockedAppsSection(
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
    Text(text = "Blocked apps", style = MaterialTheme.typography.titleSmall)
    Text(text = "${selectedBlockedPackages.size} app${if (selectedBlockedPackages.size == 1) "" else "s"} selected")
    if (selectedNames.isNotEmpty()) {
        Text(text = selectedNames.take(3).joinToString(", "))
    }
    Button(onClick = onOpenPicker, modifier = Modifier.fillMaxWidth()) {
        Text(text = "Choose Blocked Apps")
    }
    if (pickerOpen) {
        AppSearchField(value = search, onValueChange = onSearchChange)
        AppPickerList(
            apps = apps.filteredBy(search),
            selectedPackages = selectedBlockedPackages,
            onClickApp = onToggleApp
        )
        Button(onClick = onClosePicker, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Done")
        }
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
    onClickApp: (String) -> Unit
) {
    apps.forEach { app ->
        Button(onClick = { onClickApp(app.packageName) }, modifier = Modifier.fillMaxWidth()) {
            Text(text = if (app.packageName in selectedPackages) "${app.name} *" else app.name)
        }
    }
}

@Composable
private fun DayButtons(selectedActiveDays: Set<Int>, onToggleActiveDay: (Int) -> Unit) {
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
        Text(text = if (day in selectedActiveDays) "${EarnItRuleStore.dayShortName(day)} *" else EarnItRuleStore.dayShortName(day))
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
    EarnitV2Theme {
        Dashboard(
            rule = EarnItRuleStore.Rule(
                productivePackage = "com.duolingo",
                productiveName = "Duolingo",
                blockedApps = listOf(EarnItRuleStore.RuleApp("com.instagram.android", "Instagram")),
                rewardSecondsPerProductiveSecond = 1,
                activeDays = EarnItRuleStore.allDays.toSet(),
                startMinute = 0,
                endMinute = 1_440
            ),
            apps = emptyList(),
            selectedProductivePackage = "com.duolingo",
            selectedBlockedPackages = setOf("com.instagram.android"),
            selectedRatio = 1,
            selectedActiveDays = EarnItRuleStore.allDays.toSet(),
            selectedStartMinute = 0,
            selectedEndMinute = 1_440,
            editingRule = false,
            productivePickerOpen = false,
            blockedPickerOpen = false,
            productiveSearch = "",
            blockedSearch = "",
            usageAccessGranted = true,
            productiveUsageSeconds = 735,
            remainingRewardSeconds = 180,
            usageStatusMessage = "Tracking productive app usage today.",
            ruleStatusMessage = "Active rule: Duolingo earns Instagram at 1:1.",
            accessibilityServiceEnabled = true,
            onOpenUsageAccessSettings = {},
            onOpenAccessibilitySettings = {},
            onStartEditingRule = {},
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
            onEditStartTime = {},
            onEditEndTime = {},
            onSaveRule = {}
        )
    }
}
