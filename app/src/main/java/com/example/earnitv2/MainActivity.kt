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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
    private var usageAccessGranted by mutableStateOf(false)
    private var duolingoUsageMinutes by mutableStateOf(0L)
    private var usageStatusMessage by mutableStateOf("")
    private var accessibilityServiceEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refreshDashboardState()
        setContent {
            EarnitV2Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Dashboard(
                        usageAccessGranted = usageAccessGranted,
                        duolingoUsageMinutes = duolingoUsageMinutes,
                        usageStatusMessage = usageStatusMessage,
                        accessibilityServiceEnabled = accessibilityServiceEnabled,
                        onOpenUsageAccessSettings = ::openUsageAccessSettings,
                        onOpenAccessibilitySettings = ::openAccessibilitySettings,
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
        refreshUsageStats()
        accessibilityServiceEnabled = isAccessibilityServiceEnabled()
    }

    private fun refreshUsageStats() {
        usageAccessGranted = hasUsageAccess()
        if (!usageAccessGranted) {
            duolingoUsageMinutes = 0L
            usageStatusMessage = "Usage Access is off. Enable it for EarnitV2 to track Duolingo time."
            return
        }

        val foregroundMillis = getTodayForegroundUsageMillis(AppPackages.PRODUCTIVE_APP)
        duolingoUsageMinutes = TimeUnit.MILLISECONDS.toMinutes(foregroundMillis)
        usageStatusMessage = if (foregroundMillis == 0L) {
            "No Duolingo usage recorded today. Android usage data can be delayed."
        } else {
            "Tracking Duolingo usage today."
        }
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

    private fun getTodayForegroundUsageMillis(packageName: String): Long {
        val usageStatsManager = getSystemService(UsageStatsManager::class.java)
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        val now = System.currentTimeMillis()
        val usageStats = usageStatsManager.queryAndAggregateUsageStats(startOfDay, now)
        return usageStats[packageName]?.totalTimeInForeground ?: 0L
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
    usageAccessGranted: Boolean,
    duolingoUsageMinutes: Long,
    usageStatusMessage: String,
    accessibilityServiceEnabled: Boolean,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "EarnIt",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Duolingo today",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "$duolingoUsageMinutes minutes",
            style = MaterialTheme.typography.displaySmall
        )
        Text(
            text = usageStatusMessage,
            style = MaterialTheme.typography.bodyMedium
        )
        Button(
            onClick = onOpenUsageAccessSettings,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (usageAccessGranted) {
                    "Open Usage Access Settings"
                } else {
                    "Enable Usage Access"
                }
            )
        }
        Text(
            text = if (accessibilityServiceEnabled) {
                "Accessibility Service is on."
            } else {
                "Accessibility Service is off. Enable it to lock Instagram."
            },
            style = MaterialTheme.typography.bodyMedium
        )
        Button(
            onClick = onOpenAccessibilitySettings,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (accessibilityServiceEnabled) {
                    "Open Accessibility Settings"
                } else {
                    "Enable Accessibility Service"
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DashboardPreview() {
    EarnitV2Theme {
        Dashboard(
            usageAccessGranted = true,
            duolingoUsageMinutes = 12,
            usageStatusMessage = "Tracking Duolingo usage today.",
            accessibilityServiceEnabled = true,
            onOpenUsageAccessSettings = {},
            onOpenAccessibilitySettings = {}
        )
    }
}
