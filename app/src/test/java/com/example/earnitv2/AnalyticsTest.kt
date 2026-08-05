package com.kaleel.earnitv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class AnalyticsTest {
    private val zone = ZoneId.of("America/Toronto")
    private val now = ZonedDateTime.of(2026, 7, 21, 15, 30, 0, 0, zone).toInstant().toEpochMilli()

    @Test fun analyticsDefaultsToToday() {
        assertEquals(AnalyticsRange.Today, defaultAnalyticsRange())
    }

    @Test fun selectedDayAndPreviousDayUseTruthfulCalendarWindows() {
        val selected = LocalDate.of(2026, 7, 19)
        val current = AnalyticsPeriods.selectedDay(selected, now, zone)
        val previous = AnalyticsPeriods.previousDay(selected, now, zone)
        assertEquals(listOf(selected), current.dates)
        assertEquals(listOf(selected.minusDays(1)), previous.dates)
        assertEquals(24 * 60 * 60 * 1_000L, current.endMillis - current.startMillis)
    }

    @Test fun dateNavigationBlocksFutureAndFormatsContext() {
        val today = LocalDate.of(2026, 7, 28)
        assertEquals(today.minusDays(1), analyticsDateAfterSwipe(today, today, -100f))
        assertNull(analyticsDateAfterSwipe(today, today, 100f))
        assertEquals(today, analyticsDateAfterSwipe(today.minusDays(1), today, 100f))
        assertEquals("Today, July 28", analyticsDateLabel(today, today, java.util.Locale.ENGLISH))
        assertEquals(
            "Yesterday, July 27",
            analyticsDateLabel(today.minusDays(1), today, java.util.Locale.ENGLISH)
        )
        assertEquals(
            "Sunday, July 26",
            analyticsDateLabel(today.minusDays(2), today, java.util.Locale.ENGLISH)
        )
    }

    @Test fun freeSevenDayTapOpensGateWhilePremiumLoadsRange() {
        val free = analyticsRangeDecision(AnalyticsRange.SevenDays, sevenDayEnabled = false)
        assertTrue(free.openPremiumGate)
        assertNull(free.rangeToLoad)

        val premium = analyticsRangeDecision(AnalyticsRange.SevenDays, sevenDayEnabled = true)
        assertFalse(premium.openPremiumGate)
        assertEquals(AnalyticsRange.SevenDays, premium.rangeToLoad)
        assertEquals(AnalyticsRange.Today, analyticsRangeDecision(AnalyticsRange.Today, false).rangeToLoad)
    }

    @Test fun selectedDayAggregationUpdatesAppsAndPreviousDayComparison() {
        val firstDate = LocalDate.of(2026, 7, 20)
        val secondDate = firstDate.plusDays(1)
        val first = AnalyticsPeriods.selectedDay(firstDate, now, zone)
        val second = AnalyticsPeriods.selectedDay(secondDate, now, zone)
        val firstSummary = AnalyticsAggregator.aggregate(
            AnalyticsRange.Today,
            first,
            listOf(UsageSlice("first", first.startMillis, first.startMillis + 120_000)),
            emptyMap(),
            emptyMap(),
            emptyList(),
            zone = zone
        )
        val secondSummary = AnalyticsAggregator.aggregate(
            AnalyticsRange.Today,
            second,
            listOf(UsageSlice("second", second.startMillis, second.startMillis + 300_000)),
            emptyMap(),
            emptyMap(),
            emptyList(),
            previousTotalSeconds = firstSummary.totalSeconds,
            zone = zone
        )
        assertEquals(listOf("second"), secondSummary.apps.map { it.packageName })
        assertEquals(120L, secondSummary.previousTotalSeconds)
    }

    @Test fun todayUsesLocalCalendarBoundaries() {
        val period = AnalyticsPeriods.current(AnalyticsRange.Today, now, zone)
        assertEquals(listOf(LocalDate.of(2026, 7, 21)), period.dates)
        assertEquals(ZonedDateTime.of(2026, 7, 21, 0, 0, 0, 0, zone).toInstant().toEpochMilli(), period.startMillis)
        assertEquals(now, period.endMillis)
    }

    @Test fun sevenDaysIncludesTodayAndPreviousSixDays() {
        val period = AnalyticsPeriods.current(AnalyticsRange.SevenDays, now, zone)
        assertEquals(LocalDate.of(2026, 7, 15), period.dates.first())
        assertEquals(LocalDate.of(2026, 7, 21), period.dates.last())
        assertEquals(7, period.dates.size)
    }

    @Test fun previousPeriodUsesSameElapsedDuration() {
        val current = AnalyticsPeriods.current(AnalyticsRange.SevenDays, now, zone)
        val previous = AnalyticsPeriods.previous(AnalyticsRange.SevenDays, now, zone)
        assertEquals(current.endMillis - current.startMillis, previous.endMillis - previous.startMillis)
        assertEquals(LocalDate.of(2026, 7, 8), previous.dates.first())
        assertEquals(LocalDate.of(2026, 7, 14), previous.dates.last())
    }

    @Test fun aggregationSeparatesEarnRewardAndOther() {
        val period = AnalyticsPeriods.current(AnalyticsRange.Today, now, zone)
        val start = period.startMillis
        val summary = AnalyticsAggregator.aggregate(AnalyticsRange.Today, period, listOf(
            UsageSlice("earn", start, start + 600_000), UsageSlice("reward", start, start + 300_000), UsageSlice("other", start, start + 120_000)
        ), mapOf("earn" to "Earn", "reward" to "Reward", "other" to "Other"), mapOf(
            "earn" to AnalyticsClassification.Earn, "reward" to AnalyticsClassification.Reward
        ), emptyList(), zone = zone)
        assertEquals(600, summary.earnSeconds)
        assertEquals(300, summary.rewardSeconds)
        assertEquals(120, summary.otherSeconds)
        assertEquals(1_020, summary.totalSeconds)
    }

    @Test fun aggregationPreservesPreviousTotalForOverviewComparison() {
        val period = AnalyticsPeriods.current(AnalyticsRange.Today, now, zone)
        val summary = AnalyticsAggregator.aggregate(
            AnalyticsRange.Today,
            period,
            emptyList(),
            emptyMap(),
            emptyMap(),
            emptyList(),
            previousTotalSeconds = 3_600,
            zone = zone
        )
        assertEquals(3_600L, summary.previousTotalSeconds)
    }

    @Test fun mostUsedAppsAreSortedDescending() {
        val period = AnalyticsPeriods.current(AnalyticsRange.Today, now, zone)
        val start = period.startMillis
        val summary = AnalyticsAggregator.aggregate(AnalyticsRange.Today, period, listOf(
            UsageSlice("small", start, start + 60_000), UsageSlice("large", start, start + 600_000)
        ), emptyMap(), emptyMap(), emptyList(), zone = zone)
        assertEquals(listOf("large", "small"), summary.apps.map { it.packageName })
    }

    @Test fun rulePerformanceContainsOnlySavedRuleTypes() {
        val period = AnalyticsPeriods.current(AnalyticsRange.Today, now, zone)
        val rule = rule(EarnItRuleStore.RuleType.ScheduledBlock)
        val summary = AnalyticsAggregator.aggregate(AnalyticsRange.Today, period, emptyList(), emptyMap(), emptyMap(), listOf(rule), blockedAttempts = mapOf(rule.id to 3), zone = zone)
        assertEquals(1, summary.rulePerformance.size)
        assertEquals(EarnItRuleStore.RuleType.ScheduledBlock, summary.rulePerformance.single().type)
        assertTrue(summary.rulePerformance.single().primaryMetric!!.contains("3"))
    }

    @Test fun noRulesProducesEmptyAdaptivePerformance() {
        val period = AnalyticsPeriods.current(AnalyticsRange.Today, now, zone)
        val summary = AnalyticsAggregator.aggregate(AnalyticsRange.Today, period, emptyList(), emptyMap(), emptyMap(), emptyList(), zone = zone)
        assertTrue(summary.rulePerformance.isEmpty())
    }

    @Test fun peakDetectionRejectsSparseAndEvenUsage() {
        assertNull(detectPeakWindow(List(24) { 30L }))
        assertNull(detectPeakWindow(List(24) { 100L }))
    }

    @Test fun peakDetectionFindsStrongTwoHourWindow() {
        val values = MutableList(24) { 60L }.apply { this[20] = 900; this[21] = 900 }
        assertEquals(20, detectPeakWindow(values)?.startHour)
    }

    @Test fun insightThresholdRejectsWeakChanges() {
        val summary = summary(reward = 3_100, previousReward = 3_600)
        assertFalse(AnalyticsInsightEngine.generate(summary).any { it.type == InsightType.RewardUsageDecreased })
    }

    @Test fun insightScoringPrioritizesMeaningfulReplacementPattern() {
        val summary = summary(earn = 3_600, reward = 1_800, previousEarn = 1_800, previousReward = 3_600)
        assertEquals(InsightType.ProductiveReplacementPattern, AnalyticsInsightEngine.generate(summary).first().type)
    }

    @Test fun repetitionPenaltyCanRotateCandidate() {
        val summary = summary(earn = 3_600, reward = 1_800, previousEarn = 1_800, previousReward = 3_600)
        val normal = AnalyticsInsightEngine.generate(summary)
        val repeated = AnalyticsInsightEngine.generate(summary, listOf(InsightHistoryEntry(normal.first().type, timesShown = 3)))
        assertFalse(repeated.first().type == normal.first().type)
    }

    @Test fun projectionsAreCorrectAndIgnoreSmallChanges() {
        assertEquals(52L, projectedYearlyHours(3_600))
        assertNull(projectedYearlyHours(10 * 60))
    }

    @Test fun productiveReplacementCopyDoesNotClaimCausation() {
        val insight = AnalyticsInsightEngine.generate(summary(earn = 3_600, reward = 1_800, previousEarn = 1_800, previousReward = 3_600))
            .first { it.type == InsightType.ProductiveReplacementPattern }
        assertFalse(insight.title.contains("replaced", ignoreCase = true))
        assertTrue(insight.supportingText!!.contains("does not assume"))
    }

    @Test fun missingMetadataHasReadableFallback() {
        assertEquals("Instagram", fallbackAppName("com.example.instagram"))
        assertEquals("Unknown app", fallbackAppName(""))
    }

    @Test fun incompleteHistoryOmitsComparisonInsights() {
        val insights = AnalyticsInsightEngine.generate(summary(earn = 900, reward = 900, previousEarn = null, previousReward = null))
        assertFalse(insights.any { it.type in setOf(InsightType.RewardUsageDecreased, InsightType.RewardUsageIncreased, InsightType.EarnUsageIncreased, InsightType.EarnUsageDecreased) })
    }

    private fun rule(type: EarnItRuleStore.RuleType) = EarnItRuleStore.Rule(
        id = "rule-${type.name}", productivePackage = "earn", productiveName = "Earn",
        productiveApps = listOf(EarnItRuleStore.RuleApp("earn", "Earn")),
        blockedApps = listOf(EarnItRuleStore.RuleApp("reward", "Reward")), rewardSecondsPerProductiveSecond = 1,
        activeDays = EarnItRuleStore.allDays.toSet(), startMinute = 0, endMinute = 1_440, type = type
    )

    private fun summary(earn: Long = 0, reward: Long = 0, previousEarn: Long? = null, previousReward: Long? = null): AnalyticsSummary {
        val period = AnalyticsPeriods.current(AnalyticsRange.SevenDays, now, zone)
        return AnalyticsSummary(AnalyticsRange.SevenDays, period, earn + reward, earn, reward, 0,
            period.dates.map { DailyUsageSummary(it) }, emptyList(), emptyList(), null, previousEarn, previousReward)
    }
}
