package com.kaleel.earnitv2

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToLong

enum class AnalyticsRange { Today, SevenDays }
enum class AnalyticsClassification { Earn, Reward, Other }

internal fun defaultAnalyticsRange(): AnalyticsRange = AnalyticsRange.Today

data class AnalyticsRangeDecision(
    val rangeToLoad: AnalyticsRange?,
    val openPremiumGate: Boolean
)

internal fun analyticsRangeDecision(
    requested: AnalyticsRange,
    sevenDayEnabled: Boolean
): AnalyticsRangeDecision =
    if (requested == AnalyticsRange.SevenDays && !sevenDayEnabled) {
        AnalyticsRangeDecision(null, openPremiumGate = true)
    } else {
        AnalyticsRangeDecision(requested, openPremiumGate = false)
    }

data class AnalyticsPeriod(val startMillis: Long, val endMillis: Long, val dates: List<LocalDate>)

data class DailyUsageSummary(
    val date: LocalDate,
    val totalSeconds: Long = 0,
    val earnSeconds: Long = 0,
    val rewardSeconds: Long = 0,
    val otherSeconds: Long = 0,
    val hourlyRewardSeconds: List<Long> = List(24) { 0L }
)

data class AppUsageSummary(
    val packageName: String,
    val appName: String,
    val classification: AnalyticsClassification,
    val totalSeconds: Long,
    val dailySeconds: List<Long>,
    val hourlySeconds: List<Long>
) {
    val highestUsageDayIndex: Int? = dailySeconds.indices.maxByOrNull { dailySeconds[it] }
        ?.takeIf { dailySeconds[it] > 0 }
}

data class RulePerformanceSummary(
    val ruleId: String,
    val type: EarnItRuleStore.RuleType,
    val name: String,
    val primaryMetric: String?,
    val supportingText: String?
)

data class PeakUsageWindow(val startHour: Int, val endHourExclusive: Int, val seconds: Long)

data class RuleActivityMetrics(
    val rewardEarnedSeconds: Long? = null,
    val rewardSpentSeconds: Long? = null,
    val remainingRewardSeconds: Long? = null,
    val blockedAttempts: Int = 0
)

data class AnalyticsSummary(
    val range: AnalyticsRange,
    val period: AnalyticsPeriod,
    val totalSeconds: Long,
    val earnSeconds: Long,
    val rewardSeconds: Long,
    val otherSeconds: Long,
    val dailyUsage: List<DailyUsageSummary>,
    val apps: List<AppUsageSummary>,
    val rulePerformance: List<RulePerformanceSummary>,
    val peakRewardWindow: PeakUsageWindow?,
    val previousEarnSeconds: Long? = null,
    val previousRewardSeconds: Long? = null,
    val previousTotalSeconds: Long? = null,
    val ruleActivity: RuleActivityMetrics? = null
)

sealed interface AnalyticsUiState {
    data object Loading : AnalyticsUiState
    data class Ready(val summary: AnalyticsSummary) : AnalyticsUiState
    data object PermissionRequired : AnalyticsUiState
    data class Unavailable(val message: String) : AnalyticsUiState
}

object AnalyticsPeriods {
    fun current(range: AnalyticsRange, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): AnalyticsPeriod {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val first = if (range == AnalyticsRange.Today) today else today.minusDays(6)
        return period(first, today, zone, nowMillis)
    }

    fun previous(range: AnalyticsRange, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): AnalyticsPeriod {
        val current = current(range, nowMillis, zone)
        val days = current.dates.size.toLong()
        val last = current.dates.first().minusDays(1)
        val first = last.minusDays(days - 1)
        // Like-for-like: compare the same elapsed local-day duration, shifted by the range length.
        val elapsed = nowMillis - current.startMillis
        val start = first.atStartOfDay(zone).toInstant().toEpochMilli()
        return AnalyticsPeriod(start, start + elapsed, generateSequence(first) { it.plusDays(1) }.take(days.toInt()).toList())
    }

    fun selectedDay(
        date: LocalDate,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): AnalyticsPeriod {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = if (date == today) nowMillis else date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return AnalyticsPeriod(start, end, listOf(date))
    }

    fun previousDay(
        date: LocalDate,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): AnalyticsPeriod {
        val current = selectedDay(date, nowMillis, zone)
        val previousDate = date.minusDays(1)
        val start = previousDate.atStartOfDay(zone).toInstant().toEpochMilli()
        return AnalyticsPeriod(start, start + (current.endMillis - current.startMillis), listOf(previousDate))
    }

    private fun period(first: LocalDate, last: LocalDate, zone: ZoneId, nowMillis: Long) = AnalyticsPeriod(
        startMillis = first.atStartOfDay(zone).toInstant().toEpochMilli(),
        endMillis = nowMillis,
        dates = generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
    )
}

data class UsageSlice(val packageName: String, val startMillis: Long, val endMillis: Long)

object AnalyticsAggregator {
    fun aggregate(
        range: AnalyticsRange,
        period: AnalyticsPeriod,
        slices: List<UsageSlice>,
        appNames: Map<String, String>,
        classifications: Map<String, AnalyticsClassification>,
        rules: List<EarnItRuleStore.Rule>,
        previousEarnSeconds: Long? = null,
        previousRewardSeconds: Long? = null,
        previousTotalSeconds: Long? = null,
        blockedAttempts: Map<String, Int> = emptyMap(),
        zone: ZoneId = ZoneId.systemDefault()
    ): AnalyticsSummary {
        val clean = slices.filter { it.endMillis > it.startMillis && it.packageName.isNotBlank() }
        val appDaily = mutableMapOf<String, LongArray>()
        val appHourly = mutableMapOf<String, LongArray>()
        val dailyEarn = LongArray(period.dates.size)
        val dailyReward = LongArray(period.dates.size)
        val dailyOther = LongArray(period.dates.size)
        val dailyHourlyReward = Array(period.dates.size) { LongArray(24) }

        clean.forEach { slice ->
            splitAtHourBoundaries(slice, zone).forEach { part ->
                val at = Instant.ofEpochMilli(part.startMillis).atZone(zone)
                val dayIndex = period.dates.indexOf(at.toLocalDate())
                if (dayIndex < 0) return@forEach
                val seconds = ((part.endMillis - part.startMillis) / 1_000L).coerceAtLeast(0)
                if (seconds == 0L) return@forEach
                val classification = classifications[part.packageName] ?: AnalyticsClassification.Other
                appDaily.getOrPut(part.packageName) { LongArray(period.dates.size) }[dayIndex] += seconds
                appHourly.getOrPut(part.packageName) { LongArray(24) }[at.hour] += seconds
                when (classification) {
                    AnalyticsClassification.Earn -> dailyEarn[dayIndex] += seconds
                    AnalyticsClassification.Reward -> {
                        dailyReward[dayIndex] += seconds
                        dailyHourlyReward[dayIndex][at.hour] += seconds
                    }
                    AnalyticsClassification.Other -> dailyOther[dayIndex] += seconds
                }
            }
        }

        val apps = appDaily.map { (packageName, days) ->
            AppUsageSummary(
                packageName, appNames[packageName] ?: fallbackAppName(packageName),
                classifications[packageName] ?: AnalyticsClassification.Other,
                days.sum(), days.toList(), appHourly.getValue(packageName).toList()
            )
        }.filter { it.totalSeconds > 0 }.sortedByDescending { it.totalSeconds }
        val daily = period.dates.indices.map { index ->
            DailyUsageSummary(period.dates[index], dailyEarn[index] + dailyReward[index] + dailyOther[index],
                dailyEarn[index], dailyReward[index], dailyOther[index], dailyHourlyReward[index].toList())
        }
        val earn = dailyEarn.sum()
        val reward = dailyReward.sum()
        val other = dailyOther.sum()
        return AnalyticsSummary(range, period, earn + reward + other, earn, reward, other, daily, apps,
            rulePerformance(rules, apps, blockedAttempts), detectPeakWindow(daily.flatMap { it.hourlyRewardSeconds }),
            previousEarnSeconds, previousRewardSeconds, previousTotalSeconds)
    }

    private fun splitAtHourBoundaries(slice: UsageSlice, zone: ZoneId): List<UsageSlice> {
        val result = mutableListOf<UsageSlice>()
        var cursor = slice.startMillis
        while (cursor < slice.endMillis) {
            val at = Instant.ofEpochMilli(cursor).atZone(zone)
            val nextHour = at.plusHours(1).withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli()
            val end = minOf(slice.endMillis, nextHour)
            result += UsageSlice(slice.packageName, cursor, end)
            cursor = end
        }
        return result
    }

    private fun rulePerformance(rules: List<EarnItRuleStore.Rule>, apps: List<AppUsageSummary>, attempts: Map<String, Int>) = rules.map { rule ->
        val byPackage = apps.associateBy { it.packageName }
        when (rule.type) {
            EarnItRuleStore.RuleType.EarnRewardTime -> {
                val earn = rule.earnAppPackages.sumOf { byPackage[it]?.totalSeconds ?: 0 }
                val reward = rule.blockedApps.sumOf { byPackage[it.packageName]?.totalSeconds ?: 0 }
                RulePerformanceSummary(rule.id, rule.type, ruleTypePresentation(rule.type).label,
                    if (earn + reward > 0) "${analyticsDuration(earn)} Earn · ${analyticsDuration(reward)} Reward" else null,
                    if (earn + reward > 0) "Active on ${apps.filter { it.packageName in rule.earnAppPackages && it.totalSeconds > 0 }.flatMap { app -> app.dailySeconds.withIndex().filter { it.value > 0 }.map { it.index } }.distinct().size} days" else null)
            }
            EarnItRuleStore.RuleType.CompleteToUnlock -> RulePerformanceSummary(rule.id, rule.type,
                ruleTypePresentation(rule.type).label, null, "Your Rule is active. Performance will appear after EarnIt collects enough history.")
            EarnItRuleStore.RuleType.ScheduledBlock -> {
                val count = attempts[rule.id] ?: 0
                RulePerformanceSummary(rule.id, rule.type, ruleTypePresentation(rule.type).label,
                    count.takeIf { it > 0 }?.let { "Blocked $it launch attempt${if (it == 1) "" else "s"}" }, rule.scheduleLabel)
            }
        }
    }
}

object AnalyticsThresholds {
    const val MIN_CHANGE_SECONDS = 15 * 60L
    const val MIN_RELATIVE_CHANGE = 0.10
    const val MIN_PEAK_REWARD_SECONDS = 20 * 60L
    const val PEAK_OVER_AVERAGE_RATIO = 1.5
    const val MIN_PROJECTION_SECONDS = 30 * 60L
}

fun detectPeakWindow(hourValues: List<Long>): PeakUsageWindow? {
    val hourly = if (hourValues.size == 24) hourValues else List(24) { hour -> hourValues.filterIndexed { i, _ -> i % 24 == hour }.sum() }
    val total = hourly.sum()
    if (total < AnalyticsThresholds.MIN_PEAK_REWARD_SECONDS) return null
    val windows = (0 until 24).map { hour -> hour to (hourly[hour] + hourly[(hour + 1) % 24]) }
    val best = windows.maxByOrNull { it.second } ?: return null
    val average = total / 12.0
    if (best.second < average * AnalyticsThresholds.PEAK_OVER_AVERAGE_RATIO || best.second < total * 0.20) return null
    return PeakUsageWindow(best.first, (best.first + 2) % 24, best.second)
}

fun fallbackAppName(packageName: String): String = packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }.ifBlank { "Unknown app" }
fun analyticsDuration(seconds: Long): String {
    val minutes = (seconds.coerceAtLeast(0) / 60)
    return when { minutes < 1 -> "<1m"; minutes < 60 -> "${minutes}m"; minutes % 60 == 0L -> "${minutes / 60}h"; else -> "${minutes / 60}h ${minutes % 60}m" }
}
fun formatHour(hour: Int): String = when (val h = ((hour % 24) + 24) % 24) { 0 -> "12 AM"; in 1..11 -> "$h AM"; 12 -> "12 PM"; else -> "${h - 12} PM" }
fun projectedYearlyHours(weeklyImprovementSeconds: Long): Long? = weeklyImprovementSeconds.takeIf { it >= AnalyticsThresholds.MIN_PROJECTION_SECONDS }?.let { (it * 52.0 / 3_600.0).roundToLong() }
