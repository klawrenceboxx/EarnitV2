package com.example.earnitv2

import android.accessibilityservice.AccessibilityService
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import java.util.Calendar

class EarnItAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastBlockedLaunchAt = 0L
    private var activeBlockedPackage: String? = null
    private var activeBlockedAppName: String? = null
    private var lastConsumptionAt = 0L

    private val consumeRunnable = object : Runnable {
        override fun run() {
            consumeActiveBlockedUsage()
            if (activeBlockedPackage != null) {
                if (RewardLedger.snapshot(this@EarnItAccessibilityService).remainingRewardSeconds <= 0L) {
                    val appName = activeBlockedAppName ?: "This app"
                    clearActiveBlockedApp()
                    launchBlockedActivity(appName, ignoreDebounce = true)
                } else {
                    handler.postDelayed(this, CONSUMPTION_TICK_MILLIS)
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val foregroundPackage = event.packageName?.toString() ?: return
        if (foregroundPackage == packageName) {
            stopActiveBlockedUsage()
            return
        }

        val blockedAppName = AppPackages.getBlockedAppName(foregroundPackage)
        if (blockedAppName == null) {
            stopActiveBlockedUsage()
            return
        }

        creditLatestProductiveUsage()
        if (RewardLedger.snapshot(this).remainingRewardSeconds > 0L) {
            startActiveBlockedUsage(foregroundPackage, blockedAppName)
        } else {
            stopActiveBlockedUsage()
            launchBlockedActivity(blockedAppName)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        stopActiveBlockedUsage()
        super.onDestroy()
    }

    private fun startActiveBlockedUsage(packageName: String, appName: String) {
        if (activeBlockedPackage == packageName) return

        stopActiveBlockedUsage()
        activeBlockedPackage = packageName
        activeBlockedAppName = appName
        lastConsumptionAt = SystemClock.elapsedRealtime()
        handler.postDelayed(consumeRunnable, CONSUMPTION_TICK_MILLIS)
    }

    private fun stopActiveBlockedUsage() {
        consumeActiveBlockedUsage()
        clearActiveBlockedApp()
    }

    private fun clearActiveBlockedApp() {
        handler.removeCallbacks(consumeRunnable)
        activeBlockedPackage = null
        activeBlockedAppName = null
        lastConsumptionAt = 0L
    }

    private fun consumeActiveBlockedUsage() {
        if (activeBlockedPackage == null || lastConsumptionAt == 0L) return

        val now = SystemClock.elapsedRealtime()
        val elapsedSeconds = (now - lastConsumptionAt) / 1_000L
        if (elapsedSeconds <= 0L) return

        lastConsumptionAt += elapsedSeconds * 1_000L
        RewardLedger.consumeRewardSeconds(this, elapsedSeconds)
    }

    private fun creditLatestProductiveUsage() {
        if (!hasUsageAccess()) return

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
        val productiveSecondsToday = (usageStats[AppPackages.PRODUCTIVE_APP]?.totalTimeInForeground ?: 0L) / 1_000L
        RewardLedger.creditProductiveUsage(this, productiveSecondsToday)
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

    private fun launchBlockedActivity(blockedAppName: String, ignoreDebounce: Boolean = false) {
        if (!ignoreDebounce && !canLaunchBlockedActivity()) return

        lastBlockedLaunchAt = System.currentTimeMillis()
        val intent = Intent(this, BlockedActivity::class.java).apply {
            putExtra(BlockedActivity.EXTRA_BLOCKED_APP_NAME, blockedAppName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    private fun canLaunchBlockedActivity(): Boolean {
        val elapsedMillis = System.currentTimeMillis() - lastBlockedLaunchAt
        return elapsedMillis > BLOCKED_ACTIVITY_DEBOUNCE_MILLIS
    }

    private companion object {
        const val CONSUMPTION_TICK_MILLIS = 1_000L
        const val BLOCKED_ACTIVITY_DEBOUNCE_MILLIS = 2_000L
    }
}
