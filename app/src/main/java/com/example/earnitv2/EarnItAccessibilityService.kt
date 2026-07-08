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
                if (!rule.enabled || !rule.isActiveNow()) {
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

        val rules = EarnItRuleStore.getRules(this)
        creditLatestProductiveUsage(rules)

        val matchingRules = rules.filter { rule ->
            rule.enabled && rule.isActiveNow() && rule.blockedAppForPackage(foregroundPackage) != null
        }
        if (matchingRules.isEmpty()) {
            stopActiveBlockedUsage()
            return
        }

        val ruleWithReward = matchingRules.firstOrNull { rule ->
            RewardLedger.snapshot(this, rule).remainingRewardSeconds > 0L
        }
        if (ruleWithReward != null) {
            val blockedApp = ruleWithReward.blockedAppForPackage(foregroundPackage) ?: return
            startActiveBlockedUsage(ruleWithReward, blockedApp)
            return
        }

        stopActiveBlockedUsage()
        val blockingRule = matchingRules.first()
        val blockedApp = blockingRule.blockedAppForPackage(foregroundPackage) ?: return
        launchBlockedActivity(blockingRule, blockedApp.name)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        stopActiveBlockedUsage()
        super.onDestroy()
    }

    private fun startActiveBlockedUsage(rule: EarnItRuleStore.Rule, blockedApp: EarnItRuleStore.RuleApp) {
        if (activeBlockedPackage == blockedApp.packageName && activeRule?.id == rule.id) return

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

    private fun creditLatestProductiveUsage(rules: List<EarnItRuleStore.Rule>) {
        if (!hasUsageAccess()) return

        val usageStatsManager = getSystemService(UsageStatsManager::class.java)
        rules.filter { it.enabled }.forEach { rule ->
            val productiveSecondsToday = RewardLedger.activeProductiveUsageSecondsToday(usageStatsManager, rule)
            RewardLedger.creditProductiveUsage(this, rule, productiveSecondsToday)
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

    private fun launchBlockedActivity(
        rule: EarnItRuleStore.Rule,
        blockedAppName: String,
        ignoreDebounce: Boolean = false
    ) {
        if (!ignoreDebounce && !canLaunchBlockedActivity()) return

        lastBlockedLaunchAt = System.currentTimeMillis()
        val intent = Intent(this, BlockedActivity::class.java).apply {
            putExtra(BlockedActivity.EXTRA_RULE_ID, rule.id)
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
