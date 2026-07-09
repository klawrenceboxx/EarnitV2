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
    private var activeRule: EarnItRuleStore.Rule? = null
    private var lastConsumptionAt = 0L

    private val consumeRunnable = object : Runnable {
        override fun run() {
            consumeActiveBlockedUsage()
            val packageName = activeBlockedPackage
            val blockedAppName = activeBlockedAppName
            if (packageName != null && blockedAppName != null) {
                val rules = EarnItRuleStore.getRules(this@EarnItAccessibilityService)
                creditLatestProgress(rules)
                val result = evaluateAccess(rules, packageName)
                val spendRule = result.spendRule
                if (!result.allowed || spendRule == null) {
                    clearActiveBlockedApp()
                    if (result.primaryDenial != null) {
                        launchBlockedActivity(result.primaryDenial.rule, blockedAppName, packageName, result.primaryDenial.reason, ignoreDebounce = true)
                    }
                    return
                }
                activeRule = spendRule
                handler.postDelayed(this, CONSUMPTION_TICK_MILLIS)
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
        creditLatestProgress(rules)

        val result = evaluateAccess(rules, foregroundPackage)
        val blockedApp = rules.firstNotNullOfOrNull { it.blockedAppForPackage(foregroundPackage) }
        if (blockedApp == null) {
            stopActiveBlockedUsage()
            return
        }

        if (result.allowed) {
            val spendRule = result.spendRule
            if (spendRule != null) {
                startActiveBlockedUsage(spendRule, blockedApp)
            } else {
                stopActiveBlockedUsage()
            }
            return
        }

        stopActiveBlockedUsage()
        val primaryDenial = result.primaryDenial ?: return
        launchBlockedActivity(primaryDenial.rule, blockedApp.name, blockedApp.packageName, primaryDenial.reason)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        stopActiveBlockedUsage()
        super.onDestroy()
    }

    private fun evaluateAccess(
        rules: List<EarnItRuleStore.Rule>,
        foregroundPackage: String
    ): RuleAccessEvaluator.Result {
        val calendar = Calendar.getInstance()
        return RuleAccessEvaluator.evaluate(
            rules = rules,
            blockedPackage = foregroundPackage,
            day = calendar.toEarnItDay(),
            minuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE),
            runtimeState = { rule ->
                RuleAccessEvaluator.RuleRuntimeState(
                    remainingRewardSeconds = RewardLedger.snapshot(this, rule).remainingRewardSeconds,
                    requirementProgressSeconds = RewardLedger.completionProgress(this, rule)
                )
            }
        )
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
        if (rule.type != EarnItRuleStore.RuleType.EarnRewardTime) return

        val now = SystemClock.elapsedRealtime()
        val elapsedSeconds = (now - lastConsumptionAt) / 1_000L
        if (elapsedSeconds <= 0L) return

        lastConsumptionAt += elapsedSeconds * 1_000L
        RewardLedger.consumeRewardSeconds(this, rule, elapsedSeconds)
    }

    private fun creditLatestProgress(rules: List<EarnItRuleStore.Rule>) {
        if (!hasUsageAccess()) return

        val usageStatsManager = getSystemService(UsageStatsManager::class.java)
        rules.filter { it.enabled }.forEach { rule ->
            when (rule.type) {
                EarnItRuleStore.RuleType.EarnRewardTime -> {
                    val productiveSecondsToday = RewardLedger.activeProductiveUsageSecondsToday(usageStatsManager, rule)
                    RewardLedger.creditProductiveUsage(this, rule, productiveSecondsToday)
                }
                EarnItRuleStore.RuleType.CompleteToUnlock -> {
                    RewardLedger.creditCompletionProgress(this, rule, usageStatsManager)
                }
                EarnItRuleStore.RuleType.ScheduledBlock -> Unit
            }
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
        blockedAppPackage: String?,
        reason: RuleAccessEvaluator.DenialReason,
        ignoreDebounce: Boolean = false
    ) {
        if (!ignoreDebounce && !canLaunchBlockedActivity()) return

        lastBlockedLaunchAt = System.currentTimeMillis()
        val primaryEarnApp = rule.earnApps.firstOrNull()
        val intent = Intent(this, BlockedActivity::class.java).apply {
            putExtra(BlockedActivity.EXTRA_RULE_ID, rule.id)
            putExtra(BlockedActivity.EXTRA_BLOCKED_APP_NAME, blockedAppName)
            putExtra(BlockedActivity.EXTRA_BLOCKED_REASON, reason.name)
            if (blockedAppPackage != null) {
                putExtra(BlockedActivity.EXTRA_BLOCKED_PACKAGE, blockedAppPackage)
            }
            if (primaryEarnApp != null) {
                putExtra(BlockedActivity.EXTRA_PRODUCTIVE_APP_NAME, primaryEarnApp.name)
                putExtra(BlockedActivity.EXTRA_PRODUCTIVE_PACKAGE, primaryEarnApp.packageName)
            }
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

    private fun Calendar.toEarnItDay(): Int {
        return when (get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> 7
        }
    }

    private companion object {
        const val CONSUMPTION_TICK_MILLIS = 1_000L
        const val BLOCKED_ACTIVITY_DEBOUNCE_MILLIS = 2_000L
    }
}
