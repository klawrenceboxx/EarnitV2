package com.example.earnitv2

import android.accessibilityservice.AccessibilityService
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.util.Calendar

class EarnItAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastBlockedLaunchAt = 0L
    private var activeBlockedPackage: String? = null
    private var activeBlockedAppName: String? = null
    private var activeRule: EarnItRuleStore.Rule? = null
    private var lastConsumptionAt = 0L
    private var handoffTracker: TrackedAppHandoffTracker? = null

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
        val foregroundClass = event.className?.toString()

        val eventAtMillis = System.currentTimeMillis()
        val rules = EarnItRuleStore.getRules(this)
        val relevantLogicalPackages = trackedLogicalPackages(rules)
        val recentlyForegroundLogicalPackages = recentlyForegroundLogicalPackages(
            relevantLogicalPackages = relevantLogicalPackages,
            foregroundPackage = foregroundPackage,
            foregroundClass = foregroundClass,
            eventAtMillis = eventAtMillis
        )
        logRawTrackedAppForeground(
            timestamp = eventAtMillis,
            packageName = foregroundPackage,
            className = foregroundClass,
            event = event,
            relevantLogicalPackages = relevantLogicalPackages,
            recentlyForegroundLogicalPackages = recentlyForegroundLogicalPackages
        )
        handleTrackedAppForeground(
            rules = rules,
            foregroundPackage = foregroundPackage,
            className = foregroundClass,
            eventType = event.eventType,
            eventAtMillis = eventAtMillis,
            relevantLogicalPackages = relevantLogicalPackages,
            recentlyForegroundLogicalPackages = recentlyForegroundLogicalPackages
        )

        if (isEarnItPackage(foregroundPackage, packageName)) {
            stopActiveBlockedUsage()
            return
        }

        val deepWork = DeepWorkStore.load(this)
        if (deepWork.phase == DeepWorkPhase.Active || deepWork.displayPhase(SystemClock.elapsedRealtime(), eventAtMillis) == DeepWorkPhase.GoalComplete) {
            val deepWorkRule = deepWork.linkedRuleId?.let { EarnItRuleStore.findRule(this, it) }
            val deepWorkBlockedApp = deepWorkRule?.blockedAppForPackage(foregroundPackage)
            if (deepWorkRule != null && deepWorkBlockedApp != null) {
                stopActiveBlockedUsage()
                launchBlockedActivity(deepWorkRule, deepWorkBlockedApp.name, deepWorkBlockedApp.packageName, RuleAccessEvaluator.DenialReason.OutOfRewardTime)
                return
            }
            if (deepWorkRule == null && foregroundPackage in DeepWorkStore.standaloneBlockedPackages(this)) {
                val appName = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(foregroundPackage, 0)).toString() }.getOrDefault("Blocked app")
                val standaloneRule = EarnItRuleStore.Rule(productivePackage = "", productiveName = "Deep Work", blockedApps = listOf(EarnItRuleStore.RuleApp(foregroundPackage, appName)), rewardSecondsPerProductiveSecond = 1, activeDays = EarnItRuleStore.allDays.toSet(), startMinute = 0, endMinute = 1_440, type = EarnItRuleStore.RuleType.ScheduledBlock)
                stopActiveBlockedUsage()
                launchBlockedActivity(standaloneRule, appName, foregroundPackage, RuleAccessEvaluator.DenialReason.ScheduledBlockActive)
                return
            }
        }

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

    override fun onInterrupt() {
        stopTrackedAppHandoffSession()
    }

    override fun onDestroy() {
        stopTrackedAppHandoffSession()
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
                    val deepWork = DeepWorkStore.load(this)
                    if (deepWork.phase == DeepWorkPhase.Active && deepWork.linkedRuleId == rule.id) return@forEach
                    val productiveSecondsToday = RewardLedger.activeProductiveUsageSecondsToday(this, usageStatsManager, rule)
                    RewardLedger.creditProductiveUsage(this, rule, productiveSecondsToday)
                }
                EarnItRuleStore.RuleType.CompleteToUnlock -> {
                    RewardLedger.creditCompletionProgress(this, rule, usageStatsManager, includeTrackedHandoffs = true)
                }
                EarnItRuleStore.RuleType.ScheduledBlock -> Unit
            }
        }
    }

    private fun handleTrackedAppForeground(
        rules: List<EarnItRuleStore.Rule>,
        foregroundPackage: String,
        className: String?,
        eventType: Int,
        eventAtMillis: Long,
        relevantLogicalPackages: Set<String>,
        recentlyForegroundLogicalPackages: Set<String>
    ) {
        val tracker = handoffTracker ?: TrackedAppHandoffTracker(
            pendingLaunch = TrackedAppLaunchStore.readPendingLaunch(this),
            activeSession = TrackedAppLaunchStore.readActiveSession(this)
        ).also { handoffTracker = it }
        val result = tracker.onForegroundPackage(
            actualPackageName = foregroundPackage,
            actualClassName = className,
            nowMillis = eventAtMillis,
            relevantLogicalPackageNames = relevantLogicalPackages,
            ignoredForegroundPackageNames = setOfNotNull(defaultInputMethodPackageName()),
            recentlyForegroundLogicalPackageNames = recentlyForegroundLogicalPackages
        )

        result.endedSession?.let { session ->
            RewardLedger.creditTrackedAppHandoff(
                context = this,
                rules = rules,
                logicalPackageName = session.logicalPackageName,
                startedAtMillis = session.startedAtMillis,
                endedAtMillis = result.endedAtMillis ?: eventAtMillis
            )
        }

        if (result.resolvedPending != null || result.clearedExpiredPending != null) {
            TrackedAppLaunchStore.savePendingLaunch(this, tracker.pendingLaunch())
        }
        if (result.startedSession != null || result.endedSession != null) {
            TrackedAppLaunchStore.saveActiveSession(this, tracker.activeSession())
        }

        logTrackedAppForeground(
            timestamp = eventAtMillis,
            packageName = foregroundPackage,
            className = className,
            eventType = eventType,
            pendingLogicalPackage = tracker.pendingLaunch()?.logicalPackageName,
            activeLogicalPackage = tracker.activeSession()?.logicalPackageName,
            activeActualPackage = tracker.activeSession()?.actualForegroundPackageName,
            startedHandoff = result.startedSession != null,
            endedHandoff = result.endedSession != null,
            resolvedPending = result.resolvedPending != null,
            clearedExpiredPending = result.clearedExpiredPending != null,
            recentLaunchEvidence = recentlyForegroundLogicalPackages
        )
    }

    private fun stopTrackedAppHandoffSession() {
        val tracker = handoffTracker ?: TrackedAppHandoffTracker(
            pendingLaunch = TrackedAppLaunchStore.readPendingLaunch(this),
            activeSession = TrackedAppLaunchStore.readActiveSession(this)
        ).also { handoffTracker = it }
        val result = tracker.stopActiveSession(System.currentTimeMillis())
        val session = result.endedSession ?: return
        RewardLedger.creditTrackedAppHandoff(
            context = this,
            rules = EarnItRuleStore.getRules(this),
            logicalPackageName = session.logicalPackageName,
            startedAtMillis = session.startedAtMillis,
            endedAtMillis = result.endedAtMillis ?: System.currentTimeMillis()
        )
        TrackedAppLaunchStore.saveActiveSession(this, null)
    }

    private fun trackedLogicalPackages(rules: List<EarnItRuleStore.Rule>): Set<String> {
        return rules.filter { it.enabled }.flatMap { rule ->
            when (rule.type) {
                EarnItRuleStore.RuleType.EarnRewardTime -> rule.earnAppPackages
                EarnItRuleStore.RuleType.CompleteToUnlock -> rule.requirements.map { it.app.packageName }
                EarnItRuleStore.RuleType.ScheduledBlock -> emptyList()
            }
        }.filter { it.isNotBlank() }.toSet()
    }

    private fun defaultInputMethodPackageName(): String? {
        val component = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        ) ?: return null
        return ComponentName.unflattenFromString(component)?.packageName
    }

    private fun recentlyForegroundLogicalPackages(
        relevantLogicalPackages: Set<String>,
        foregroundPackage: String,
        foregroundClass: String?,
        eventAtMillis: Long
    ): Set<String> {
        if (TrackedAppMatchPolicy.GEMINI_PACKAGE !in relevantLogicalPackages ||
            foregroundPackage != TrackedAppMatchPolicy.GOOGLE_PACKAGE ||
            !TrackedAppMatchPolicy.isGeminiGatewayClass(foregroundClass) ||
            !hasUsageAccess()
        ) {
            return emptySet()
        }
        val usageEvents = getSystemService(UsageStatsManager::class.java).queryEvents(
            eventAtMillis - TrackedAppMatchPolicy.RECENT_LAUNCH_EVIDENCE_WINDOW_MILLIS,
            eventAtMillis
        )
        val usageEvent = android.app.usage.UsageEvents.Event()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(usageEvent)
            if (TrackedAppMatchPolicy.isRecentGeminiLaunchEvidence(
                    packageName = usageEvent.packageName,
                    className = usageEvent.className,
                    eventType = usageEvent.eventType,
                    eventAtMillis = usageEvent.timeStamp,
                    nowMillis = eventAtMillis
                )
            ) {
                return setOf(TrackedAppMatchPolicy.GEMINI_PACKAGE)
            }
        }
        return emptySet()
    }

    private fun logTrackedAppForeground(
        timestamp: Long,
        packageName: String,
        className: String?,
        eventType: Int,
        pendingLogicalPackage: String?,
        activeLogicalPackage: String?,
        activeActualPackage: String?,
        startedHandoff: Boolean,
        endedHandoff: Boolean,
        resolvedPending: Boolean,
        clearedExpiredPending: Boolean,
        recentLaunchEvidence: Set<String>
    ) {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        Log.d(
            TAG,
            "foreground timestamp=$timestamp package=$packageName class=$className eventType=$eventType " +
                "pendingLogical=$pendingLogicalPackage activeLogical=$activeLogicalPackage " +
                "activeActual=$activeActualPackage startedHandoff=$startedHandoff endedHandoff=$endedHandoff " +
                "resolvedPending=$resolvedPending clearedExpiredPending=$clearedExpiredPending " +
                "recentLaunchEvidence=$recentLaunchEvidence"
        )
    }

    private fun logRawTrackedAppForeground(
        timestamp: Long,
        packageName: String,
        className: String?,
        event: AccessibilityEvent,
        relevantLogicalPackages: Set<String>,
        recentlyForegroundLogicalPackages: Set<String>
    ) {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        Log.d(
            TAG,
            "foregroundRaw timestamp=$timestamp package=$packageName class=$className " +
                "text=${event.text} description=${event.contentDescription} windowId=${event.windowId} " +
                "relevantLogical=$relevantLogicalPackages recentLaunchEvidence=$recentlyForegroundLogicalPackages " +
                "keyboard=${defaultInputMethodPackageName()}"
        )
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
        const val TAG = "EarnItAccessibility"
        const val CONSUMPTION_TICK_MILLIS = 1_000L
        const val BLOCKED_ACTIVITY_DEBOUNCE_MILLIS = 2_000L
    }
}

internal fun isEarnItPackage(foregroundPackage: String, earnItPackage: String): Boolean {
    return foregroundPackage == earnItPackage
}
