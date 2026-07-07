package com.example.earnitv2

import android.accessibilityservice.AccessibilityService
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

class EarnItAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastBlockedLaunchAt = 0L
    private var activeBlockedPackage: String? = null
    private var activeBlockedAppName: String? = null
    private var activeRule: EarnItRuleStore.Rule? = null
    private var lastConsumptionAt = 0L

    private val consumeRunnable = object : Runnable {
        override fun run() {
            consumeActiveBlockedUsage()
            val rule = activeRule
            val blockedAppName = activeBlockedAppName
            if (activeBlockedPackage != null && rule != null && blockedAppName != null) {
                if (!rule.isActiveNow()) {
                    clearActiveBlockedApp()
                    return
                }
                if (RewardLedger.snapshot(this@EarnItAccessibilityService, rule).remainingRewardSeconds <= 0L) {
                    clearActiveBlockedApp()
                    launchBlockedActivity(rule, blockedAppName, ignoreDebounce = true)
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

        val rule = EarnItRuleStore.getRule(this)
        val blockedApp = rule.blockedAppForPackage(foregroundPackage)
        if (blockedApp == null || !rule.isActiveNow()) {
            stopActiveBlockedUsage()
            return
        }

        creditLatestProductiveUsage(rule)
        if (RewardLedger.snapshot(this, rule).remainingRewardSeconds > 0L) {
            startActiveBlockedUsage(rule, blockedApp)
        } else {
            stopActiveBlockedUsage()
            launchBlockedActivity(rule, blockedApp.name)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        stopActiveBlockedUsage()
        super.onDestroy()
    }

    private fun startActiveBlockedUsage(rule: EarnItRuleStore.Rule, blockedApp: EarnItRuleStore.RuleApp) {
        if (activeBlockedPackage == blockedApp.packageName) return

        stopActiveBlockedUsage()
        activeBlockedPackage = blockedApp.packageName
        activeBlockedAppName = blockedApp.name
        activeRule = rule
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
        activeRule = null
        lastConsumptionAt = 0L
    }

    private fun consumeActiveBlockedUsage() {
        val rule = activeRule ?: return
        if (activeBlockedPackage == null || lastConsumptionAt == 0L) return

        val now = SystemClock.elapsedRealtime()
        val elapsedSeconds = (now - lastConsumptionAt) / 1_000L
        if (elapsedSeconds <= 0L) return

        lastConsumptionAt += elapsedSeconds * 1_000L
        RewardLedger.consumeRewardSeconds(this, rule, elapsedSeconds)
    }

    private fun creditLatestProductiveUsage(rule: EarnItRuleStore.Rule) {
        if (!hasUsageAccess()) return

        val usageStatsManager = getSystemService(UsageStatsManager::class.java)
        val productiveSecondsToday = RewardLedger.activeProductiveUsageSecondsToday(usageStatsManager, rule)
        RewardLedger.creditProductiveUsage(this, rule, productiveSecondsToday)
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

    private fun launchBlockedActivity(
        rule: EarnItRuleStore.Rule,
        blockedAppName: String,
        ignoreDebounce: Boolean = false
    ) {
        if (!ignoreDebounce && !canLaunchBlockedActivity()) return

        lastBlockedLaunchAt = System.currentTimeMillis()
        val intent = Intent(this, BlockedActivity::class.java).apply {
            putExtra(BlockedActivity.EXTRA_BLOCKED_APP_NAME, blockedAppName)
            putExtra(BlockedActivity.EXTRA_PRODUCTIVE_APP_NAME, rule.productiveName)
            putExtra(BlockedActivity.EXTRA_PRODUCTIVE_PACKAGE, rule.productivePackage)
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
