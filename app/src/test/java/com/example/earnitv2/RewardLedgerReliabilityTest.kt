package com.kaleel.earnitv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RewardLedgerReliabilityTest {
    @Test
    fun manySmallCreditsIssueTheSameRewardAsOneLargeCredit() {
        // 10 minutes of productive time at 10:1 must earn exactly 1 minute, however it is credited.
        for (rate in EarnItRuleStore.allowedRatios) {
            var credited = 0L
            var issued = 0L
            for (step in listOf(1L, 3L, 7L, 2L, 9L, 4L)) {
                repeat(100) {
                    issued += incrementalRewardSeconds(credited, credited + step, rate)
                    credited += step
                }
            }
            assertEquals(credited * rate / 10L, issued)
        }
    }

    @Test
    fun oneSecondCreditsAtTheLowestRatioAreNotLost() {
        var issued = 0L
        for (second in 0L until 600L) {
            issued += incrementalRewardSeconds(second, second + 1L, 1)
        }
        assertEquals(60L, issued)
    }

    @Test
    fun creditNeverGoesBackwards() {
        assertEquals(0L, incrementalRewardSeconds(500L, 400L, 5))
        assertEquals(0L, incrementalRewardSeconds(-5L, -1L, 5))
    }

    @Test
    fun editingARuleKeepsTimeAlreadySpentToday() {
        val plan = ledgerResetPlan(
            storedDay = "20261110",
            currentDay = "20261110",
            storedConsumedSeconds = 900L,
            storedDeepWorkCreditedSeconds = 0L,
            rewardSecondsPerProductiveSecond = 2
        )
        assertEquals(900L, plan.rewardConsumedSeconds)
        assertEquals(0L, plan.rewardIssuedSeconds)
        assertTrue(plan.keepProcessedDeepWorkSessions)
    }

    @Test
    fun editingARuleKeepsDeepWorkRewardAtTheNewRate() {
        val plan = ledgerResetPlan("20261110", "20261110", 120L, 1_800L, 5)
        assertEquals(1_800L, plan.deepWorkCreditedSeconds)
        assertEquals(900L, plan.rewardIssuedSeconds)
        assertEquals(120L, plan.rewardConsumedSeconds)
    }

    @Test
    fun aNewDayStartsFromZero() {
        val plan = ledgerResetPlan("20261109", "20261110", 900L, 1_800L, 5)
        assertEquals(0L, plan.rewardConsumedSeconds)
        assertEquals(0L, plan.rewardIssuedSeconds)
        assertEquals(0L, plan.deepWorkCreditedSeconds)
        assertFalse(plan.keepProcessedDeepWorkSessions)
    }

    @Test
    fun firstUseStartsFromZero() {
        val plan = ledgerResetPlan(null, "20261110", 0L, 0L, 2)
        assertEquals(0L, plan.rewardConsumedSeconds)
        assertEquals(0L, plan.rewardIssuedSeconds)
    }

    @Test
    fun usageClockChargesNormalTicksExactly() {
        val clock = ProtectedUsageClock()
        clock.start(ProtectedUsageClock.Key("rule", "app"), 1_000L)
        assertEquals(1L, clock.tick(2_000L, maxGapSeconds = 10L))
        assertEquals(0L, clock.tick(2_400L, maxGapSeconds = 10L))
        assertEquals(1L, clock.tick(3_050L, maxGapSeconds = 10L))
    }

    @Test
    fun usageClockDoesNotChargeTimeTheDeviceWasAsleep() {
        val clock = ProtectedUsageClock()
        clock.start(ProtectedUsageClock.Key("rule", "app"), 1_000L)
        // Eight hours pass with no ticks, then the handler resumes.
        assertEquals(10L, clock.tick(1_000L + 8 * 3_600_000L, maxGapSeconds = 10L))
        // The clock re-anchored: the next normal tick charges one second, not the backlog.
        assertEquals(1L, clock.tick(2_000L + 8 * 3_600_000L, maxGapSeconds = 10L))
    }
}
