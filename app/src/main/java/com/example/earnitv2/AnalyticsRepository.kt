package com.kaleel.earnitv2

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class AnalyticsRepository(private val context: Context) {
    fun load(
        range: AnalyticsRange,
        selectedDate: LocalDate = LocalDate.now(),
        nowMillis: Long = System.currentTimeMillis()
    ): AnalyticsSummary {
        val rules = EarnItRuleStore.getRules(context)
        val zone = ZoneId.systemDefault()
        val current = if (range == AnalyticsRange.Today) {
            AnalyticsPeriods.selectedDay(selectedDate, nowMillis, zone)
        } else {
            AnalyticsPeriods.current(range, nowMillis, zone)
        }
        val previous = if (range == AnalyticsRange.Today) {
            AnalyticsPeriods.previousDay(selectedDate, nowMillis, zone)
        } else {
            AnalyticsPeriods.previous(range, nowMillis, zone)
        }
        val manager = context.getSystemService(UsageStatsManager::class.java)
        val allSlices = queryForegroundSlices(manager, previous.startMillis, current.endMillis)
        val currentSlices = allSlices.clipTo(current.startMillis, current.endMillis)
        val previousSlices = allSlices.clipTo(previous.startMillis, previous.endMillis)
        val classifications = classificationSnapshot(rules)
        val packages = allSlices.map { it.packageName }.toSet()
        val names = packages.associateWith(::appName)
        val previousSummary = AnalyticsAggregator.aggregate(range, previous, previousSlices, names, classifications, rules, zone = zone)
        val blockedAttempts = AnalyticsEventStore.blockedAttempts(context, current.dates)
        val base = AnalyticsAggregator.aggregate(
            range, current, currentSlices, names, classifications, rules,
            previousEarnSeconds = previousSummary.earnSeconds,
            previousRewardSeconds = previousSummary.rewardSeconds,
            previousTotalSeconds = previousSummary.totalSeconds,
            blockedAttempts = blockedAttempts,
            zone = zone
        )
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val ledgerMetrics = if (range == AnalyticsRange.Today && selectedDate == today) {
            val snapshots = rules.filter { it.type == EarnItRuleStore.RuleType.EarnRewardTime }
                .map { RewardLedger.snapshot(context, it) }
            RuleActivityMetrics(
                rewardEarnedSeconds = snapshots.sumOf { it.rewardIssuedSeconds },
                rewardSpentSeconds = snapshots.sumOf { it.rewardConsumedSeconds },
                remainingRewardSeconds = snapshots.sumOf { it.remainingRewardSeconds },
                blockedAttempts = blockedAttempts.values.sum()
            )
        } else {
            blockedAttempts.values.sum().takeIf { it > 0 }?.let { RuleActivityMetrics(blockedAttempts = it) }
        }
        return base.copy(ruleActivity = ledgerMetrics)
    }

    /** Existing storage has no historical classification snapshots, so MVP analytics applies the
     * current Rule classification consistently. New usage is never written back or reclassified. */
    private fun classificationSnapshot(rules: List<EarnItRuleStore.Rule>): Map<String, AnalyticsClassification> {
        val reward = rules.flatMap { it.blockedApps }.map { it.packageName }.toSet()
        val earn = rules.flatMap { it.earnApps }.map { it.packageName }.toSet() +
            rules.flatMap { it.requirements }.map { it.app.packageName }
        return (reward.associateWith { AnalyticsClassification.Reward } + earn.associateWith { AnalyticsClassification.Earn })
    }

    private fun appName(packageName: String): String = runCatching {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    }.getOrElse { fallbackAppName(packageName) }

    private fun queryForegroundSlices(manager: UsageStatsManager, start: Long, end: Long): List<UsageSlice> {
        val events = manager.queryEvents(start, end)
        val event = UsageEvents.Event()
        val active = mutableMapOf<String, Long>()
        val slices = mutableListOf<UsageSlice>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val packageName = event.packageName ?: continue
            if (isNoise(packageName)) continue
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED, UsageEvents.Event.MOVE_TO_FOREGROUND -> active[packageName] = maxOf(start, event.timeStamp)
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.MOVE_TO_BACKGROUND -> active.remove(packageName)?.let { began ->
                    if (event.timeStamp > began) slices += UsageSlice(packageName, began, minOf(end, event.timeStamp))
                }
            }
        }
        active.forEach { (packageName, began) -> if (end > began) slices += UsageSlice(packageName, began, end) }
        return slices
    }

    private fun isNoise(packageName: String): Boolean = packageName == context.packageName || NOISE_PACKAGES.any { packageName == it || packageName.startsWith("$it.") }

    private fun List<UsageSlice>.clipTo(start: Long, end: Long) = mapNotNull {
        val clippedStart = maxOf(start, it.startMillis)
        val clippedEnd = minOf(end, it.endMillis)
        if (clippedEnd > clippedStart) it.copy(startMillis = clippedStart, endMillis = clippedEnd) else null
    }

    companion object {
        private val NOISE_PACKAGES = setOf(
            "com.android.systemui", "com.android.launcher", "com.google.android.apps.nexuslauncher",
            "com.android.inputmethod", "com.google.android.inputmethod", "android"
        )
    }
}

object AnalyticsEventStore {
    private const val PREFS = "earnit_analytics_events"

    fun recordBlockedAttempt(context: Context, ruleId: String, nowMillis: Long = System.currentTimeMillis()) {
        val date = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "blocked|$date|$ruleId"
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    fun blockedAttempts(context: Context, dates: List<LocalDate>): Map<String, Int> {
        val allowed = dates.map { "blocked|$it|" }
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.entries
            .filter { (key, _) -> allowed.any(key::startsWith) }
            .groupingBy { it.key.substringAfterLast('|') }
            .fold(0) { total, entry -> total + (entry.value as? Int ?: 0) }
    }
}
