package com.example.earnitv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EarnItUiStateTest {
    @Test
    fun rewardTimeAvailability_formatsZeroAsNoRewardTime() {
        assertEquals("No Reward Time", EarnItUiFormatters.rewardTimeAvailability(0))
    }

    @Test
    fun rewardTimeAvailability_formatsWholeMinutes() {
        assertEquals("12 min available", EarnItUiFormatters.rewardTimeAvailability(735))
    }

    @Test
    fun permissionSetup_reportsReadyWhenBothPermissionsAreGranted() {
        val state = EarnItUiStateAdapters.permissionSetup(
            usageAccessGranted = true,
            appBlockingEnabled = true
        )

        assertTrue(state.isReady)
        assertFalse(state.needsAttention)
        assertEquals(EarnItPermissionStatus.Granted, state.earningProgressStatus)
        assertEquals(EarnItPermissionStatus.Granted, state.appBlockingStatus)
        assertTrue(state.repairTargetLabels.isEmpty())
    }

    @Test
    fun permissionSetup_reportsRepairTargetsWhenPermissionsAreMissing() {
        val state = EarnItUiStateAdapters.permissionSetup(
            usageAccessGranted = false,
            appBlockingEnabled = false
        )

        assertFalse(state.isReady)
        assertTrue(state.needsAttention)
        assertEquals(listOf("Earning progress", "App blocking"), state.repairTargetLabels)
    }

    @Test
    fun ruleCard_includesPermissionAttentionWithoutChangingRuleValues() {
        val rule = sampleRule()

        val state = EarnItUiStateAdapters.ruleCard(
            rule = rule,
            productiveUsageSeconds = 600,
            remainingRewardSeconds = 120,
            usageAccessGranted = false,
            appBlockingEnabled = true,
            isActiveNow = true
        )

        assertEquals("rule_1", state.ruleId)
        assertEquals("Duolingo", state.earnAppName)
        assertEquals("com.duolingo", state.earnAppPackage)
        assertEquals(2, state.rewardAppCount)
        assertEquals("2 min available", state.availableRewardTimeLabel)
        assertEquals("10 min productive today", state.productiveUsageLabel)
        assertEquals("Active now", state.scheduleStatusLabel)
        assertEquals("Earning progress needs attention", state.attentionLabel)
    }

    @Test
    fun ruleCard_hasNoAttentionWhenPermissionsAreReady() {
        val state = EarnItUiStateAdapters.ruleCard(
            rule = sampleRule(),
            productiveUsageSeconds = null,
            remainingRewardSeconds = 0,
            usageAccessGranted = true,
            appBlockingEnabled = true,
            isActiveNow = false
        )

        assertNull(state.attentionLabel)
        assertEquals("No Reward Time", state.availableRewardTimeLabel)
        assertEquals("Unrestricted right now", state.scheduleStatusLabel)
    }

    @Test
    fun ruleDraft_isReadyWhenAllRequiredSelectionsAreValid() {
        val state = EarnItUiStateAdapters.ruleDraft(
            selectedEarnApps = listOf(EarnItRuleStore.LaunchableApp("com.duolingo", "Duolingo")),
            selectedRewardApps = listOf(EarnItRuleStore.RuleApp("com.instagram.android", "Instagram")),
            exchangeSelection = 2,
            activeDays = setOf(1, 2, 3, 4, 5),
            timeWindows = listOf(EarnItRuleStore.TimeWindow(9 * 60, 17 * 60))
        )

        assertTrue(state.canReview)
        assertTrue(state.canSave)
        assertTrue(state.reviewSummary.contains("When I use Duolingo"))
        assertTrue(state.reviewSummary.contains("Every 10 min earns 2 min Reward Time"))
        assertTrue(state.reviewSummary.contains("For Instagram"))
    }

    @Test
    fun ruleDraft_isNotReadyWithoutRewardApps() {
        val state = EarnItUiStateAdapters.ruleDraft(
            selectedEarnApps = listOf(EarnItRuleStore.LaunchableApp("com.duolingo", "Duolingo")),
            selectedRewardApps = emptyList(),
            exchangeSelection = 2,
            activeDays = EarnItRuleStore.allDays.toSet(),
            timeWindows = listOf(EarnItRuleStore.TimeWindow(0, 1_440))
        )

        assertFalse(state.canReview)
        assertFalse(state.canSave)
    }

    @Test
    fun homeRuleUiState_completeToUnlockDoesNotUseRewardTimeBalanceCopy() {
        val state = homeRuleUiState(
            state = RuleDashboardState(
                rule = completeToUnlockRule(),
                productiveUsageSeconds = 0,
                remainingRewardSeconds = 0
            ),
            usageAccessGranted = true,
            appBlockingEnabled = true
        )

        assertEquals("Complete requirements to unlock", state.primaryText)
        assertEquals("2 requirements", state.secondaryText)
        assertNull(state.earnContextText)
    }

    @Test
    fun homeRuleUiState_scheduledBlockDoesNotUseRewardTimeBalanceCopy() {
        val state = homeRuleUiState(
            state = RuleDashboardState(
                rule = scheduledBlockRule(),
                productiveUsageSeconds = 0,
                remainingRewardSeconds = 0
            ),
            usageAccessGranted = true,
            appBlockingEnabled = true
        )

        assertEquals("Blocked now", state.primaryText)
        assertEquals("Every day, all day", state.secondaryText)
        assertNull(state.earnContextText)
    }

    private fun sampleRule(): EarnItRuleStore.Rule {
        return EarnItRuleStore.Rule(
            id = "rule_1",
            productivePackage = "com.duolingo",
            productiveName = "Duolingo",
            blockedApps = listOf(
                EarnItRuleStore.RuleApp("com.instagram.android", "Instagram"),
                EarnItRuleStore.RuleApp("com.youtube.android", "YouTube")
            ),
            rewardSecondsPerProductiveSecond = 2,
            activeDays = setOf(1, 2, 3, 4, 5),
            startMinute = 9 * 60,
            endMinute = 17 * 60,
            enabled = true
        )
    }

    private fun completeToUnlockRule(): EarnItRuleStore.Rule {
        return EarnItRuleStore.Rule(
            id = "rule_complete",
            productivePackage = "",
            productiveName = "",
            blockedApps = listOf(
                EarnItRuleStore.RuleApp("com.instagram.android", "Instagram"),
                EarnItRuleStore.RuleApp("com.snapchat.android", "Snapchat")
            ),
            rewardSecondsPerProductiveSecond = 1,
            activeDays = EarnItRuleStore.allDays.toSet(),
            startMinute = 0,
            endMinute = 1_440,
            enabled = true,
            type = EarnItRuleStore.RuleType.CompleteToUnlock,
            requirements = listOf(
                EarnItRuleStore.RuleRequirement(EarnItRuleStore.RuleApp("com.duolingo", "Duolingo"), 10 * 60L),
                EarnItRuleStore.RuleRequirement(EarnItRuleStore.RuleApp("com.headspace", "Headspace"), 20 * 60L)
            )
        )
    }

    private fun scheduledBlockRule(): EarnItRuleStore.Rule {
        return EarnItRuleStore.Rule(
            id = "rule_schedule",
            productivePackage = "",
            productiveName = "",
            blockedApps = listOf(
                EarnItRuleStore.RuleApp("com.instagram.android", "Instagram"),
                EarnItRuleStore.RuleApp("com.snapchat.android", "Snapchat")
            ),
            rewardSecondsPerProductiveSecond = 1,
            activeDays = EarnItRuleStore.allDays.toSet(),
            startMinute = 0,
            endMinute = 1_440,
            enabled = true,
            type = EarnItRuleStore.RuleType.ScheduledBlock
        )
    }
}
