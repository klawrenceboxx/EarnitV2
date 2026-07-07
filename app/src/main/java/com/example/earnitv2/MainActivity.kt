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
    private var duolingoUsageSeconds by mutableStateOf(0L)
    private var productiveCreditedSeconds by mutableStateOf(0L)
    private var rewardIssuedSeconds by mutableStateOf(0L)
    private var rewardConsumedSeconds by mutableStateOf(0L)
    private var remainingRewardSeconds by mutableStateOf(0L)
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
                        duolingoUsageSeconds = duolingoUsageSeconds,
                        remainingRewardSeconds = remainingRewardSeconds,
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
            duolingoUsageSeconds = 0L
            val snapshot = RewardLedger.snapshot(this)
            productiveCreditedSeconds = snapshot.productiveCreditedSeconds
            rewardIssuedSeconds = snapshot.rewardIssuedSeconds
            rewardConsumedSeconds = snapshot.rewardConsumedSeconds
            remainingRewardSeconds = snapshot.remainingRewardSeconds
            usageStatusMessage = "Usage Access is off. Enable it for EarnitV2 to track Duolingo time."
            return
        }

        duolingoUsageSeconds = getTodayForegroundUsageSeconds(AppPackages.PRODUCTIVE_APP)
        val snapshot = RewardLedger.creditProductiveUsage(this, duolingoUsageSeconds)
        productiveCreditedSeconds = snapshot.productiveCreditedSeconds
        rewardIssuedSeconds = snapshot.rewardIssuedSeconds
        rewardConsumedSeconds = snapshot.rewardConsumedSeconds
        remainingRewardSeconds = snapshot.remainingRewardSeconds
        usageStatusMessage = if (duolingoUsageSeconds == 0L) {
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

    private fun getTodayForegroundUsageSeconds(packageName: String): Long {
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
    usageAccessGranted: Boolean,
    duolingoUsageSeconds: Long,
    remainingRewardSeconds: Long,
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
            text = "Productive time today: ${formatDuration(duolingoUsageSeconds)}",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Available reward time: ${formatDuration(remainingRewardSeconds)}",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = if (remainingRewardSeconds > 0L) {
                "Status: Unlocked"
            } else {
                "Status: Locked"
            },
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = if (usageAccessGranted) {
                "Usage Access: On"
            } else {
                "Usage Access: Off"
            },
            style = MaterialTheme.typography.bodyMedium
        )
        Button(
            onClick = onOpenUsageAccessSettings,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Open Usage Access Settings")
        }
        Text(
            text = if (accessibilityServiceEnabled) {
                "Accessibility Service: On"
            } else {
                "Accessibility Service: Off"
            },
            style = MaterialTheme.typography.bodyMedium
        )
        Button(
            onClick = onOpenAccessibilitySettings,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Open Accessibility Settings")
        }
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0L)
    val minutes = safeSeconds / 60L
    val seconds = safeSeconds % 60L
    return if (minutes > 0L) {
        "${minutes}m ${seconds}s"
    } else {
        "${seconds}s"
    }
}

@Preview(showBackground = true)
@Composable
fun DashboardPreview() {
    EarnitV2Theme {
        Dashboard(
            usageAccessGranted = true,
            duolingoUsageSeconds = 735,
            remainingRewardSeconds = 180,
            accessibilityServiceEnabled = true,
            onOpenUsageAccessSettings = {},
            onOpenAccessibilitySettings = {}
        )
    }
}
