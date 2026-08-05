package com.kaleel.earnitv2

import android.content.Context
import java.time.LocalDate
import kotlin.math.abs

enum class InsightType {
    RewardUsageDecreased, RewardUsageIncreased, EarnUsageIncreased, EarnUsageDecreased,
    ProductiveReplacementPattern, MostProductiveDay, PeakDistractionWindow,
    MostUsedRewardApp, LargestAppImprovement, LargestAppIncrease,
    CompleteUnlockSuccess, ScheduledBlockSuccess, NewPersonalBest
}

data class InsightCandidate(val type: InsightType, val title: String, val supportingText: String? = null, val score: Int, val relatedId: String? = null)
data class InsightHistoryEntry(val type: InsightType, val relatedId: String? = null, val timesShown: Int = 1, val lastShownEpochDay: Long = Long.MAX_VALUE)

object AnalyticsInsightHistoryStore {
    private const val PREFS = "earnit_analytics_insights"
    private const val KEY = "history"

    fun load(context: Context): List<InsightHistoryEntry> = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getStringSet(KEY, emptySet()).orEmpty().mapNotNull { raw ->
            val fields = raw.split('|')
            val type = fields.getOrNull(0)?.let { runCatching { InsightType.valueOf(it) }.getOrNull() } ?: return@mapNotNull null
            InsightHistoryEntry(type, fields.getOrNull(1)?.takeIf { it.isNotBlank() }, fields.getOrNull(2)?.toIntOrNull() ?: 1, fields.getOrNull(3)?.toLongOrNull() ?: Long.MIN_VALUE)
        }

    fun record(context: Context, shown: List<InsightCandidate>) {
        if (shown.isEmpty()) return
        val existing = load(context).associateBy { it.type to it.relatedId }.toMutableMap()
        shown.forEach { candidate ->
            val key = candidate.type to candidate.relatedId
            val old = existing[key]
            existing[key] = InsightHistoryEntry(candidate.type, candidate.relatedId, (old?.timesShown ?: 0) + 1, LocalDate.now().toEpochDay())
        }
        val encoded = existing.values.map { "${it.type.name}|${it.relatedId.orEmpty()}|${it.timesShown}|${it.lastShownEpochDay}" }.toSet()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY, encoded).apply()
    }
}

object AnalyticsInsightEngine {
    fun generate(summary: AnalyticsSummary, history: List<InsightHistoryEntry> = emptyList(), limit: Int = 3): List<InsightCandidate> {
        val candidates = mutableListOf<InsightCandidate>()
        val previousReward = summary.previousRewardSeconds
        val previousEarn = summary.previousEarnSeconds
        if (previousReward != null) changeCandidate(previousReward, summary.rewardSeconds, true)?.let(candidates::add)
        if (previousEarn != null) changeCandidate(previousEarn, summary.earnSeconds, false)?.let(candidates::add)
        if (previousReward != null && previousEarn != null && previousReward - summary.rewardSeconds >= AnalyticsThresholds.MIN_CHANGE_SECONDS && summary.earnSeconds - previousEarn >= AnalyticsThresholds.MIN_CHANGE_SECONDS) {
            candidates += InsightCandidate(InsightType.ProductiveReplacementPattern,
                "You spent ${analyticsDuration(previousReward - summary.rewardSeconds)} less on Reward Apps and ${analyticsDuration(summary.earnSeconds - previousEarn)} more in Earn Apps.",
                "These changes happened in the same period; EarnIt does not assume one caused the other.", 92)
        }
        summary.dailyUsage.maxByOrNull { it.earnSeconds }?.takeIf { best -> best.earnSeconds >= 15 * 60 && summary.dailyUsage.count { it.earnSeconds >= best.earnSeconds * .8 } == 1 }?.let {
            candidates += InsightCandidate(InsightType.MostProductiveDay, "${it.date.dayOfWeek.name.lowercase().replaceFirstChar(Char::uppercase)} was your most productive day.", score = 68)
        }
        summary.peakRewardWindow?.let {
            candidates += InsightCandidate(InsightType.PeakDistractionWindow, "Most of your Reward App usage happens between ${formatHour(it.startHour)} and ${formatHour(it.endHourExclusive)}.", score = 76)
        }
        summary.apps.firstOrNull { it.classification == AnalyticsClassification.Reward }?.takeIf { it.totalSeconds >= 20 * 60 }?.let {
            candidates += InsightCandidate(InsightType.MostUsedRewardApp, "${it.appName} was your most-used Reward App at ${analyticsDuration(it.totalSeconds)}.", score = 62, relatedId = it.packageName)
        }
        summary.rulePerformance.filter { it.type == EarnItRuleStore.RuleType.ScheduledBlock && it.primaryMetric != null }.forEach {
            candidates += InsightCandidate(InsightType.ScheduledBlockSuccess, it.primaryMetric!!, "Your Scheduled Block caught these launches during its active times.", 72, it.ruleId)
        }
        return candidates.map { candidate ->
            val today = LocalDate.now().toEpochDay()
            val repeats = history.filter { it.type == candidate.type && it.relatedId == candidate.relatedId && it.lastShownEpochDay >= today - 1 }.sumOf { it.timesShown }
            candidate.copy(score = candidate.score - repeats * 18)
        }.filter { it.score >= 45 }.sortedWith(compareByDescending<InsightCandidate> { it.score }.thenBy { it.type.name }).take(limit.coerceIn(2, 4))
    }

    private fun changeCandidate(previous: Long, current: Long, reward: Boolean): InsightCandidate? {
        if (previous < 10 * 60) return null
        val delta = current - previous
        val meaningful = abs(delta) >= AnalyticsThresholds.MIN_CHANGE_SECONDS && abs(delta).toDouble() / previous >= AnalyticsThresholds.MIN_RELATIVE_CHANGE
        if (!meaningful) return null
        val decreased = delta < 0
        val type = when { reward && decreased -> InsightType.RewardUsageDecreased; reward -> InsightType.RewardUsageIncreased; decreased -> InsightType.EarnUsageDecreased; else -> InsightType.EarnUsageIncreased }
        val noun = if (reward) "Reward Apps" else "Earn Apps"
        val direction = if (decreased) "less" else "more"
        val amount = abs(delta)
        val projection = if (reward && decreased) projectedYearlyHours(amount)?.let { "If that continues, that is roughly $it hours over a year." } else null
        return InsightCandidate(type, "You spent ${analyticsDuration(amount)} $direction on $noun in this period.", projection, 80 + (amount / 900).toInt().coerceAtMost(15))
    }
}
