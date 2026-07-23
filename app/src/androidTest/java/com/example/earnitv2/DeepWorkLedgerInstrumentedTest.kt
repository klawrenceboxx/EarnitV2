package com.example.earnitv2

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeepWorkLedgerInstrumentedTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before fun clearState() {
        context.getSharedPreferences("earnit_reward_ledger", 0).edit().clear().commit()
    }

    @Test fun manualEndCreditsPersistentlyAndRepeatedCallbacksCreditOnce() {
        val rule = rule("manual")
        val first = RewardLedger.creditDeepWork(context, rule, "session-1", 300L)
        val duplicate = RewardLedger.creditDeepWork(context, rule, "session-1", 300L)

        assertEquals(30L, first.creditedRewardSeconds)
        assertEquals(0L, duplicate.creditedRewardSeconds)
        assertEquals(30L, RewardLedger.snapshot(context, rule).remainingRewardSeconds)
    }

    @Test fun separateShortSessionsEachReceiveTheirOwnCredit() {
        val rule = rule("separate")
        RewardLedger.creditDeepWork(context, rule, "session-a", 300L)
        RewardLedger.creditDeepWork(context, rule, "session-b", 300L)
        assertEquals(60L, RewardLedger.snapshot(context, rule).remainingRewardSeconds)
    }

    @Test fun invalidRuleTypeAndBlankSessionFailClosed() {
        val rule = rule("invalid").copy(type = EarnItRuleStore.RuleType.ScheduledBlock)
        assertEquals(0L, RewardLedger.creditDeepWork(context, rule, "session", 300L).creditedRewardSeconds)
        assertEquals(0L, RewardLedger.creditDeepWork(context, rule.copy(type = EarnItRuleStore.RuleType.EarnRewardTime), "", 300L).creditedRewardSeconds)
    }

    private fun rule(id: String) = EarnItRuleStore.Rule(
        id = id,
        productivePackage = "focus",
        productiveName = "Focus",
        blockedApps = listOf(EarnItRuleStore.RuleApp("reward", "Reward")),
        rewardSecondsPerProductiveSecond = 1,
        activeDays = EarnItRuleStore.allDays.toSet(),
        startMinute = 0,
        endMinute = 1_440,
        enabled = true
    )
}
