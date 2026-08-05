package com.kaleel.earnitv2

import org.junit.Assert.assertEquals
import org.junit.Test

class DeepWorkSessionTest {
    @Test fun timedSessionCountsDownFromPersistedStart() {
        val session = DeepWorkSession(phase = DeepWorkPhase.Active, goalSeconds = 1_800, startedElapsedRealtime = 10_000, startedWallClock = 100_000)
        val elapsed = session.elapsedSeconds(310_000, 400_000)
        assertEquals(300, elapsed)
        assertEquals(1_500, session.goalSeconds!! - elapsed)
    }

    @Test fun noGoalSessionCountsUp() {
        val session = DeepWorkSession(phase = DeepWorkPhase.Active, goalSeconds = null, startedElapsedRealtime = 1_000, startedWallClock = 1_000)
        assertEquals(42, session.elapsedSeconds(43_000, 43_000))
    }

    @Test fun goalCompletesAtZero() {
        val session = DeepWorkSession(phase = DeepWorkPhase.Active, goalSeconds = 30, startedElapsedRealtime = 1_000, startedWallClock = 1_000)
        assertEquals(DeepWorkPhase.GoalComplete, session.displayPhase(31_000, 31_000))
    }

    @Test fun restoredSessionFallsBackToWallClockAfterReboot() {
        val session = DeepWorkSession(phase = DeepWorkPhase.Active, goalSeconds = 300, startedElapsedRealtime = 50_000, startedWallClock = 100_000, baseElapsedSeconds = 10)
        assertEquals(40, session.elapsedSeconds(1_000, 130_000))
    }

    @Test fun continuePreservesElapsedTime() {
        val continued = DeepWorkSession(phase = DeepWorkPhase.Active, goalSeconds = null, baseElapsedSeconds = 1_800, startedElapsedRealtime = 10_000, startedWallClock = 10_000)
        assertEquals(1_830, continued.elapsedSeconds(40_000, 40_000))
    }

    @Test fun deepWorkEarningIsVisibleOnlyForEarnRewardRules() {
        assertEquals(true, supportsDeepWorkEarning(EarnItRuleStore.RuleType.EarnRewardTime))
        assertEquals(false, supportsDeepWorkEarning(EarnItRuleStore.RuleType.CompleteToUnlock))
        assertEquals(false, supportsDeepWorkEarning(EarnItRuleStore.RuleType.ScheduledBlock))
    }

    @Test fun fiveMinuteSessionEarnsProportionalReward() {
        assertEquals(30L, deepWorkRewardSeconds(5 * 60L, 1))
        assertEquals("30 sec", formatDeepWorkReward(30L))
    }

    @Test fun configuredRateUsesTheSameCalculationAsDisplayedEarnings() {
        assertEquals(600L, deepWorkRewardSeconds(20 * 60L, 5))
        assertEquals("10 min", formatDeepWorkReward(deepWorkRewardSeconds(20 * 60L, 5)))
    }

    @Test fun timedSessionNeverCreditsPastItsGoal() {
        val session = DeepWorkSession(goalSeconds = 300L)
        assertEquals(300L, eligibleDeepWorkSeconds(session, 420L))
    }
}
