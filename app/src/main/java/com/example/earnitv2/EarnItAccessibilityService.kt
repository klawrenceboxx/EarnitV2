package com.example.earnitv2

import android.accessibilityservice.AccessibilityService
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
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
    private var activeBlockedDomain: String? = null
    private var activeRule: EarnItRuleStore.Rule? = null
    private val usageClock = ProtectedUsageClock()
    private var handoffTracker: TrackedAppHandoffTracker? = null
    private val browserPageObserver = CurrentBrowserPageObserver()
    private val websiteRedirectGate = WebsiteRedirectGate()
    private var pendingWebsiteBlock: PendingWebsiteBlock? = null
    private var websiteRedirectAttemptCount = 0
    private var activeBrowserPackage: String? = null
    private var browserRetryCount = 0
    private var foregroundRecheckCount = 0

    private val browserRetryRunnable = object : Runnable {
        override fun run() {
            val browserPackage = activeBrowserPackage ?: return
            val root = rootInActiveWindow
            if (root?.packageName?.toString() == packageName) {
                clearBrowserObservation()
                stopActiveBlockedUsage()
                return
            }
            if (root?.packageName?.toString() != browserPackage) {
                browserPageObserver.clear()
                stopActiveBlockedUsage()
                scheduleBrowserRetry(browserPackage)
                return
            }
            handleBrowserForeground(EarnItRuleStore.getRules(this@EarnItAccessibilityService), browserPackage)
        }
    }

    private val foregroundWindowRecheckRunnable: Runnable = object : Runnable {
        override fun run() {
            val rootPackage = rootInActiveWindow?.packageName?.toString()
            if (rootPackage == null || !browserPageObserver.isSupportedBrowser(rootPackage)) {
                if (foregroundRecheckCount < MAX_FOREGROUND_RECHECKS) {
                    foregroundRecheckCount += 1
                    handler.postDelayed(this, BROWSER_RETRY_DELAY_MILLIS)
                }
                return
            }
            foregroundRecheckCount = 0
            if (activeBrowserPackage != rootPackage) {
                handler.removeCallbacks(browserRetryRunnable)
                browserRetryCount = 0
                activeBrowserPackage = rootPackage
            }
            handleBrowserForeground(EarnItRuleStore.getRules(this@EarnItAccessibilityService), rootPackage)
        }
    }

    private val websiteRedirectRetryRunnable = object : Runnable {
        override fun run() {
            val pending = pendingWebsiteBlock ?: return
            val browserPackage = activeBrowserPackage ?: ChromeBrowserAdapter.CHROME_PACKAGE
            val root = rootInActiveWindow
            if (root?.packageName?.toString() != browserPackage) {
                cancelPendingWebsiteBlock()
                return
            }
            if (browserPageObserver.isPlaceholderVisible(browserPackage, root)) {
                launchPendingWebsiteBlock()
                return
            }
            if (websiteRedirectAttemptCount >= MAX_WEBSITE_REDIRECT_ATTEMPTS) {
                cancelPendingWebsiteBlock()
                return
            }
            websiteRedirectAttemptCount += 1
            browserPageObserver.redirectCurrentPageToPlaceholder(browserPackage, root)
            handler.postDelayed(this, WEBSITE_REDIRECT_RETRY_MILLIS)
        }
    }

    private val consumeRunnable = object : Runnable {
        override fun run() {
            consumeActiveBlockedUsage()
            val packageName = activeBlockedPackage
            val blockedAppName = activeBlockedAppName
            if (packageName != null && blockedAppName != null) {
                val rules = EarnItRuleStore.getRules(this@EarnItAccessibilityService)
                creditLatestProgress(rules)
                val domain = activeBlockedDomain
                val result = if (domain != null) evaluateDomainAccess(rules, domain) else evaluateAccess(rules, packageName)
                val spendRule = result.spendRule
                if (!result.allowed || spendRule == null) {
                    clearActiveBlockedApp()
                    if (result.primaryDenial != null) {
                        if (domain != null) {
                            redirectWebsiteThenLaunchBlocked(
                                result.primaryDenial.rule,
                                domain,
                                result.primaryDenial.reason,
                                ignoreDebounce = true
                            )
                        } else {
                            launchBlockedActivity(
                                result.primaryDenial.rule,
                                blockedAppName,
                                packageName,
                                result.primaryDenial.reason,
                                ignoreDebounce = true
                            )
                        }
                    }
                    return
                }
                activeRule = spendRule
                handler.postDelayed(this, CONSUMPTION_TICK_MILLIS)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType !in SUPPORTED_EVENT_TYPES) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            handler.removeCallbacks(foregroundWindowRecheckRunnable)
            foregroundRecheckCount = 0
            handler.postDelayed(foregroundWindowRecheckRunnable, BROWSER_RETRY_DELAY_MILLIS)
        }

        val foregroundPackage = event.packageName?.toString()
            ?: rootInActiveWindow?.packageName?.toString()
            ?: return
        val foregroundClass = event.className?.toString()

        val eventAtMillis = System.currentTimeMillis()
        val rules = EarnItRuleStore.getRules(this)
        if (browserPageObserver.isSupportedBrowser(foregroundPackage)) {
            val activeRootPackage = rootInActiveWindow?.packageName?.toString()
            if (activeBrowserPackage == null && activeRootPackage != foregroundPackage &&
                event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            ) return
            if (activeBrowserPackage != foregroundPackage) {
                handler.removeCallbacks(browserRetryRunnable)
                browserRetryCount = 0
                activeBrowserPackage = foregroundPackage
            }
            handleBrowserForeground(rules, foregroundPackage)
            return
        } else {
            clearBrowserObservation()
        }

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
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
        handler.removeCallbacks(foregroundWindowRecheckRunnable)
        handler.removeCallbacks(websiteRedirectRetryRunnable)
        pendingWebsiteBlock = null
        websiteRedirectGate.clear()
        clearBrowserObservation()
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

    private fun evaluateDomainAccess(
        rules: List<EarnItRuleStore.Rule>,
        hostname: String
    ): RuleAccessEvaluator.Result {
        val calendar = Calendar.getInstance()
        return RuleAccessEvaluator.evaluateDomain(
            rules = rules,
            hostname = hostname,
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

    /** Website matches take precedence over a separately protected Chrome app to avoid double charging. */
    private fun handleBrowserForeground(rules: List<EarnItRuleStore.Rule>, browserPackage: String) {
        val root = rootInActiveWindow
        if (root?.packageName?.toString() == browserPackage &&
            browserPageObserver.isPlaceholderVisible(browserPackage, root) &&
            pendingWebsiteBlock != null
        ) {
            stopActiveBlockedUsage()
            launchPendingWebsiteBlock()
            return
        }
        val page = if (root?.packageName?.toString() == browserPackage) {
            browserPageObserver.observe(browserPackage, root)
        } else null
        if (page == null) {
            stopActiveBlockedUsage()
            scheduleBrowserRetry(browserPackage)
            return
        }
        handler.removeCallbacks(browserRetryRunnable)
        browserRetryCount = 0
        val matchingDomain = rules.firstNotNullOfOrNull { it.blockedDomainForHost(page.normalizedHost) }
        if (matchingDomain == null) {
            creditLatestProgress(rules)
            handleForegroundApp(rules, browserPackage)
            return
        }

        val deepWork = DeepWorkStore.load(this)
        val deepWorkRule = deepWork.linkedRuleId?.let { id -> rules.firstOrNull { it.id == id } }
        if ((deepWork.phase == DeepWorkPhase.Active ||
                deepWork.displayPhase(SystemClock.elapsedRealtime(), System.currentTimeMillis()) == DeepWorkPhase.GoalComplete) &&
            deepWorkRule?.blockedDomainForHost(page.normalizedHost) != null
        ) {
            stopActiveBlockedUsage()
            redirectWebsiteThenLaunchBlocked(
                deepWorkRule, matchingDomain, RuleAccessEvaluator.DenialReason.OutOfRewardTime
            )
            return
        }

        creditLatestProgress(rules)
        val result = evaluateDomainAccess(rules, page.normalizedHost)
        if (result.allowed) {
            result.spendRule?.let { startActiveBlockedUsage(it, matchingDomain, matchingDomain, matchingDomain) }
                ?: stopActiveBlockedUsage()
            return
        }
        stopActiveBlockedUsage()
        result.primaryDenial?.let { denial ->
            redirectWebsiteThenLaunchBlocked(denial.rule, matchingDomain, denial.reason)
        }
    }

    private fun redirectWebsiteThenLaunchBlocked(
        rule: EarnItRuleStore.Rule,
        domain: String,
        reason: RuleAccessEvaluator.DenialReason,
        ignoreDebounce: Boolean = false
    ) {
        if (!websiteRedirectGate.begin(rule.id, domain)) return
        val browserPackage = activeBrowserPackage ?: ChromeBrowserAdapter.CHROME_PACKAGE
        val root = rootInActiveWindow
        pendingWebsiteBlock = PendingWebsiteBlock(rule, domain, reason, ignoreDebounce)
        websiteRedirectAttemptCount = 0
        handler.removeCallbacks(websiteRedirectRetryRunnable)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            launchPendingWebsiteBlock()
            handler.postDelayed({ websiteRedirectGate.complete() }, WEBSITE_REDIRECT_GUARD_MILLIS)
            return
        }
        if (root?.packageName?.toString() == browserPackage) {
            websiteRedirectRetryRunnable.run()
        } else {
            cancelPendingWebsiteBlock()
        }
        handler.postDelayed({ websiteRedirectGate.complete() }, WEBSITE_REDIRECT_GUARD_MILLIS)
    }

    private fun launchPendingWebsiteBlock() {
        val pending = pendingWebsiteBlock ?: return
        pendingWebsiteBlock = null
        websiteRedirectAttemptCount = 0
        handler.removeCallbacks(websiteRedirectRetryRunnable)
        launchBlockedActivity(
            rule = pending.rule,
            blockedAppName = pending.domain,
            blockedAppPackage = null,
            reason = pending.reason,
            ignoreDebounce = pending.ignoreDebounce,
            blockedDomain = pending.domain
        )
    }

    private fun cancelPendingWebsiteBlock() {
        pendingWebsiteBlock = null
        websiteRedirectAttemptCount = 0
        handler.removeCallbacks(websiteRedirectRetryRunnable)
        websiteRedirectGate.clear()
    }

    private fun scheduleBrowserRetry(browserPackage: String) {
        if (activeBrowserPackage != browserPackage || browserRetryCount >= MAX_BROWSER_RETRIES) return
        handler.removeCallbacks(browserRetryRunnable)
        browserRetryCount += 1
        handler.postDelayed(browserRetryRunnable, BROWSER_RETRY_DELAY_MILLIS)
    }

    private fun clearBrowserObservation() {
        handler.removeCallbacks(browserRetryRunnable)
        activeBrowserPackage = null
        browserRetryCount = 0
        browserPageObserver.clear()
    }

    private fun handleForegroundApp(rules: List<EarnItRuleStore.Rule>, foregroundPackage: String) {
        val result = evaluateAccess(rules, foregroundPackage)
        val blockedApp = rules.firstNotNullOfOrNull { it.blockedAppForPackage(foregroundPackage) }
        if (blockedApp == null) {
            stopActiveBlockedUsage()
        } else if (result.allowed) {
            result.spendRule?.let { startActiveBlockedUsage(it, blockedApp.packageName, blockedApp.name) }
                ?: stopActiveBlockedUsage()
        } else {
            stopActiveBlockedUsage()
            result.primaryDenial?.let {
                launchBlockedActivity(it.rule, blockedApp.name, blockedApp.packageName, it.reason)
            }
        }
    }

    private fun startActiveBlockedUsage(rule: EarnItRuleStore.Rule, blockedApp: EarnItRuleStore.RuleApp) {
        startActiveBlockedUsage(rule, blockedApp.packageName, blockedApp.name)
    }

    private fun startActiveBlockedUsage(rule: EarnItRuleStore.Rule, targetId: String, displayName: String, domain: String? = null) {
        if (activeBlockedPackage == targetId && activeRule?.id == rule.id) return

        stopActiveBlockedUsage()
        activeBlockedPackage = targetId
        activeBlockedAppName = displayName
        activeBlockedDomain = domain
        activeRule = rule
        usageClock.start(ProtectedUsageClock.Key(rule.id, targetId), SystemClock.elapsedRealtime())
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
        activeBlockedDomain = null
        activeRule = null
        usageClock.clear()
    }

    private fun consumeActiveBlockedUsage() {
        val rule = activeRule ?: return
        if (activeBlockedPackage == null) return
        if (rule.type != EarnItRuleStore.RuleType.EarnRewardTime) return

        val elapsedSeconds = usageClock.tick(SystemClock.elapsedRealtime())
        if (elapsedSeconds <= 0L) return
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

        if (result.resolvedPending != null || result.clearedExpiredPending != null || result.updatedPending != null) {
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
            activeStartedAtMillis = tracker.activeSession()?.startedAtMillis,
            startedHandoff = result.startedSession != null,
            endedHandoff = result.endedSession != null,
            resolvedPending = result.resolvedPending != null,
            clearedExpiredPending = result.clearedExpiredPending != null,
            recentLaunchEvidence = recentlyForegroundLogicalPackages,
            decision = result.decision,
            isKeyboardEvent = foregroundPackage == defaultInputMethodPackageName(),
            rules = rules
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
        activeStartedAtMillis: Long?,
        startedHandoff: Boolean,
        endedHandoff: Boolean,
        resolvedPending: Boolean,
        clearedExpiredPending: Boolean,
        recentLaunchEvidence: Set<String>,
        decision: String,
        isKeyboardEvent: Boolean,
        rules: List<EarnItRuleStore.Rule>
    ) {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val geminiClassification = when {
            packageName == TrackedAppMatchPolicy.GEMINI_PACKAGE &&
                className == TrackedAppMatchPolicy.GEMINI_ENTRY_POINT_ACTIVITY -> "gemini-entry"
            packageName == TrackedAppMatchPolicy.GOOGLE_PACKAGE &&
                TrackedAppMatchPolicy.isGeminiGatewayClass(className) -> "google-gateway"
            TrackedAppMatchPolicy.matchingRule(
                logicalPackageName = TrackedAppMatchPolicy.GEMINI_PACKAGE,
                actualPackageName = packageName,
                actualClassName = className
            ) != null -> "verified-gemini"
            packageName == TrackedAppMatchPolicy.GOOGLE_PACKAGE -> "ordinary-google"
            else -> "other"
        }
        val ruleStatus = rules.filter { rule ->
            rule.earnAppPackages.contains(TrackedAppMatchPolicy.GEMINI_PACKAGE) ||
                rule.requirements.any { it.app.packageName == TrackedAppMatchPolicy.GEMINI_PACKAGE }
        }.joinToString(prefix = "[", postfix = "]") { rule ->
            "${rule.id}:${rule.type}:enabled=${rule.enabled}:scheduleActive=${rule.isActiveNow()}"
        }
        Log.d(
            TAG,
            "foreground timestamp=$timestamp package=$packageName class=$className eventType=$eventType " +
                "classification=$geminiClassification keyboardEvent=$isKeyboardEvent decision=$decision " +
                "pendingLogical=$pendingLogicalPackage activeLogical=$activeLogicalPackage " +
                "activeActual=$activeActualPackage activeStartedAt=$activeStartedAtMillis " +
                "startedHandoff=$startedHandoff endedHandoff=$endedHandoff " +
                "resolvedPending=$resolvedPending clearedExpiredPending=$clearedExpiredPending " +
                "recentLaunchEvidence=$recentLaunchEvidence ruleStatus=$ruleStatus"
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
                "eventType=${event.eventType} windowId=${event.windowId} " +
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
        ignoreDebounce: Boolean = false,
        blockedDomain: String? = null
    ) {
        if (!ignoreDebounce && !canLaunchBlockedActivity()) return

        lastBlockedLaunchAt = System.currentTimeMillis()
        AnalyticsEventStore.recordBlockedAttempt(this, rule.id, lastBlockedLaunchAt)
        val primaryEarnApp = rule.earnApps.firstOrNull()
        val intent = Intent(this, BlockedActivity::class.java).apply {
            putExtra(BlockedActivity.EXTRA_RULE_ID, rule.id)
            putExtra(BlockedActivity.EXTRA_BLOCKED_APP_NAME, blockedAppName)
            putExtra(BlockedActivity.EXTRA_BLOCKED_REASON, reason.name)
            if (blockedAppPackage != null) {
                putExtra(BlockedActivity.EXTRA_BLOCKED_PACKAGE, blockedAppPackage)
            }
            if (blockedDomain != null) putExtra(BlockedActivity.EXTRA_BLOCKED_DOMAIN, blockedDomain)
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
        const val BROWSER_RETRY_DELAY_MILLIS = 200L
        const val MAX_BROWSER_RETRIES = 6
        const val MAX_FOREGROUND_RECHECKS = 25
        const val WEBSITE_REDIRECT_RETRY_MILLIS = 200L
        const val MAX_WEBSITE_REDIRECT_ATTEMPTS = 10
        const val WEBSITE_REDIRECT_GUARD_MILLIS = 2_500L
        val SUPPORTED_EVENT_TYPES = setOf(
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED
        )
    }

    private data class PendingWebsiteBlock(
        val rule: EarnItRuleStore.Rule,
        val domain: String,
        val reason: RuleAccessEvaluator.DenialReason,
        val ignoreDebounce: Boolean
    )
}

internal fun isEarnItPackage(foregroundPackage: String, earnItPackage: String): Boolean {
    return foregroundPackage == earnItPackage
}
