package com.example.earnitv2

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.earnitv2.ui.theme.EarnitV2Theme
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private var activeRule by mutableStateOf<EarnItRuleStore.Rule?>(null)
    private var launchableApps by mutableStateOf(emptyList<EarnItRuleStore.LaunchableApp>())
    private var selectedProductivePackage by mutableStateOf("")
    private var selectedBlockedPackage by mutableStateOf("")
    private var selectedRatio by mutableStateOf(1)
    private var editingRule by mutableStateOf(false)
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
                        selectedBlockedPackage = selectedBlockedPackage,
                        selectedRatio = selectedRatio,
                        editingRule = editingRule,
                        usageAccessGranted = usageAccessGranted,
                        productiveUsageSeconds = productiveUsageSeconds,
                        remainingRewardSeconds = remainingRewardSeconds,
                        usageStatusMessage = usageStatusMessage,
                        ruleStatusMessage = ruleStatusMessage,
                        accessibilityServiceEnabled = accessibilityServiceEnabled,
                        onOpenUsageAccessSettings = ::openUsageAccessSettings,
                        onOpenAccessibilitySettings = ::openAccessibilitySettings,
                        onStartEditingRule = ::startEditingRule,
                        onCancelEditingRule = { editingRule = false },
                        onSelectProductiveApp = { selectedProductivePackage = it },
                        onSelectBlockedApp = { selectedBlockedPackage = it },
                        onSelectRatio = { selectedRatio = it },
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
            selectedBlockedPackage = rule.blockedPackage
            selectedRatio = rule.rewardSecondsPerProductiveSecond
        }
        refreshUsageStats(rule)
        accessibilityServiceEnabled = isAccessibilityServiceEnabled()
    }

    private fun refreshUsageStats(rule: EarnItRuleStore.Rule) {
        usageAccessGranted = hasUsageAccess()
        ruleStatusMessage = when {
            !EarnItRuleStore.appInstalled(this, rule.productivePackage) -> "Productive app is not installed: ${rule.productiveName}."
            !EarnItRuleStore.appInstalled(this, rule.blockedPackage) -> "Blocked app is not installed: ${rule.blockedName}."
            else -> "Active rule: ${rule.productiveName} earns ${rule.blockedName} at ${rule.ratioLabel}."
        }

        if (!usageAccessGranted) {
            productiveUsageSeconds = 0L
            val snapshot = RewardLedger.snapshot(this, rule)
            remainingRewardSeconds = snapshot.remainingRewardSeconds
            usageStatusMessage = "Usage Access is off."
            return
        }

        productiveUsageSeconds = getTodayForegroundUsageSeconds(rule.productivePackage)
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
        selectedBlockedPackage = rule.blockedPackage
        selectedRatio = rule.rewardSecondsPerProductiveSecond
        editingRule = true
    }

    private fun saveRule() {
        val productiveApp = launchableApps.firstOrNull { it.packageName == selectedProductivePackage } ?: return
        val blockedApp = launchableApps.firstOrNull { it.packageName == selectedBlockedPackage } ?: return
        val rule = EarnItRuleStore.Rule(
            productivePackage = productiveApp.packageName,
            productiveName = productiveApp.name,
            blockedPackage = blockedApp.packageName,
            blockedName = blockedApp.name,
            rewardSecondsPerProductiveSecond = selectedRatio
        )
        EarnItRuleStore.saveRule(this, rule)
        editingRule = false
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

    private fun getTodayForegroundUsageSeconds(packageName: String): Long {
        val usageStatsManager = getSystemService(UsageStatsManager::class.java)
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val usageStats = usageStatsManager.queryAndAggregateUsageStats(
            calendar.timeInMillis,
            System.currentTimeMillis()
        )
        val foregroundMillis = usageStats[packageName]?.totalTimeInForeground ?: 0L
        return TimeUnit.MILLISECONDS.toSeconds(foregroundMillis)
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
    selectedBlockedPackage: String,
    selectedRatio: Int,
    editingRule: Boolean,
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
    onSelectProductiveApp: (String) -> Unit,
    onSelectBlockedApp: (String) -> Unit,
    onSelectRatio: (Int) -> Unit,
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
                apps = apps,
                selectedProductivePackage = selectedProductivePackage,
                selectedBlockedPackage = selectedBlockedPackage,
                selectedRatio = selectedRatio,
                onSelectProductiveApp = onSelectProductiveApp,
                onSelectBlockedApp = onSelectBlockedApp,
                onSelectRatio = onSelectRatio,
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
    apps: List<EarnItRuleStore.LaunchableApp>,
    selectedProductivePackage: String,
    selectedBlockedPackage: String,
    selectedRatio: Int,
    onSelectProductiveApp: (String) -> Unit,
    onSelectBlockedApp: (String) -> Unit,
    onSelectRatio: (Int) -> Unit,
    onSaveRule: () -> Unit,
    onCancel: () -> Unit
) {
    Text(text = "Productive app", style = MaterialTheme.typography.titleSmall)
    AppChoiceList(apps, selectedProductivePackage, onSelectProductiveApp)
    Text(text = "Blocked app", style = MaterialTheme.typography.titleSmall)
    AppChoiceList(apps, selectedBlockedPackage, onSelectBlockedApp)
    Text(text = "Ratio", style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EarnItRuleStore.allowedRatios.forEach { ratio ->
            Button(onClick = { onSelectRatio(ratio) }) {
                Text(text = if (selectedRatio == ratio) "1:$ratio *" else "1:$ratio")
            }
        }
    }
    Button(onClick = onSaveRule, modifier = Modifier.fillMaxWidth()) {
        Text(text = "Save Rule")
    }
    Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
        Text(text = "Cancel")
    }
}

@Composable
private fun AppChoiceList(
    apps: List<EarnItRuleStore.LaunchableApp>,
    selectedPackage: String,
    onSelectApp: (String) -> Unit
) {
    apps.forEach { app ->
        Button(onClick = { onSelectApp(app.packageName) }, modifier = Modifier.fillMaxWidth()) {
            Text(text = if (app.packageName == selectedPackage) "${app.name} *" else app.name)
        }
    }
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
            rule = EarnItRuleStore.Rule("com.duolingo", "Duolingo", "com.instagram.android", "Instagram", 1),
            apps = emptyList(),
            selectedProductivePackage = "com.duolingo",
            selectedBlockedPackage = "com.instagram.android",
            selectedRatio = 1,
            editingRule = false,
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
            onSelectProductiveApp = {},
            onSelectBlockedApp = {},
            onSelectRatio = {},
            onSaveRule = {}
        )
    }
}
