package com.example.earnitv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EarnItRuleBuilderNavigationTest {
    @Test
    fun logicalPreviousStep_returnsNullFromFirstStage() {
        assertNull(logicalPreviousStep(EarnItRuleStore.RuleType.EarnRewardTime, RuleBuilderStep.Earn))
        assertNull(logicalPreviousStep(EarnItRuleStore.RuleType.ScheduledBlock, RuleBuilderStep.Reward))
    }

    @Test
    fun logicalPreviousStep_returnsPreviousRuleSpecificStage() {
        assertEquals(
            RuleBuilderStep.Exchange,
            logicalPreviousStep(EarnItRuleStore.RuleType.EarnRewardTime, RuleBuilderStep.Schedule)
        )
        assertEquals(
            RuleBuilderStep.Reward,
            logicalPreviousStep(EarnItRuleStore.RuleType.ScheduledBlock, RuleBuilderStep.Schedule)
        )
    }

    @Test
    fun stageLabel_usesAppsForScheduledBlockFirstStage() {
        assertEquals("Apps", stageLabel(EarnItRuleStore.RuleType.ScheduledBlock, RuleBuilderStep.Reward))
        assertEquals("Reward", stageLabel(EarnItRuleStore.RuleType.EarnRewardTime, RuleBuilderStep.Reward))
    }

    @Test
    fun builderStageState_reportsCurrentCompletedAvailableAndLocked() {
        val draft = earnRewardDraft()

        assertEquals(
            BuilderStageState.Current,
            builderStageState(
                EarnItRuleStore.RuleType.EarnRewardTime,
                RuleBuilderStep.Schedule,
                RuleBuilderStep.Schedule,
                draft,
                emptyList()
            )
        )
        assertEquals(
            BuilderStageState.Completed,
            builderStageState(
                EarnItRuleStore.RuleType.EarnRewardTime,
                RuleBuilderStep.Earn,
                RuleBuilderStep.Schedule,
                draft,
                emptyList()
            )
        )
        assertEquals(
            BuilderStageState.Available,
            builderStageState(
                EarnItRuleStore.RuleType.EarnRewardTime,
                RuleBuilderStep.Review,
                RuleBuilderStep.Schedule,
                draft,
                emptyList()
            )
        )

        val lockedDraft = EarnItUiStateAdapters.ruleDraft(
            selectedEarnApps = emptyList(),
            selectedRewardApps = emptyList(),
            exchangeSelection = 2,
            activeDays = EarnItRuleStore.allDays.toSet(),
            timeWindows = listOf(EarnItRuleStore.TimeWindow(0, 1_440))
        )
        assertEquals(
            BuilderStageState.Locked,
            builderStageState(
                EarnItRuleStore.RuleType.EarnRewardTime,
                RuleBuilderStep.Exchange,
                RuleBuilderStep.Earn,
                lockedDraft,
                emptyList()
            )
        )
    }

    @Test
    fun reviewActionLabel_isRuleTypeSpecific() {
        assertEquals("Review Rule", reviewActionLabel(EarnItRuleStore.RuleType.EarnRewardTime))
        assertEquals("Review Block Rule", reviewActionLabel(EarnItRuleStore.RuleType.ScheduledBlock))
    }

    @Test
    fun compactRuleSoFarLines_formatsEarnRewardTime() {
        val lines = compactRuleSoFarLines(
            ruleType = EarnItRuleStore.RuleType.EarnRewardTime,
            draft = earnRewardDraft(),
            requirements = emptyList()
        )

        assertEquals(
            listOf("Every 10 min earns 2 min Reward Time", "Every day · All day"),
            lines
        )
    }

    @Test
    fun compactRuleSoFarLines_formatsCompleteToUnlock() {
        val lines = compactRuleSoFarLines(
            ruleType = EarnItRuleStore.RuleType.CompleteToUnlock,
            draft = unlockDraft(),
            requirements = listOf(
                EarnItRuleStore.RuleRequirement(
                    app = EarnItRuleStore.RuleApp("com.duolingo", "Duolingo"),
                    requiredSeconds = 600
                ),
                EarnItRuleStore.RuleRequirement(
                    app = EarnItRuleStore.RuleApp("com.kindle", "Kindle"),
                    requiredSeconds = 900
                )
            )
        )

        assertEquals(listOf("2 requirements", "Complete all", "Every day · All day"), lines)
    }

    @Test
    fun compactRuleSoFarLines_formatsScheduledBlockWithoutRewardTimeLanguage() {
        val lines = compactRuleSoFarLines(
            ruleType = EarnItRuleStore.RuleType.ScheduledBlock,
            draft = unlockDraft(),
            requirements = emptyList()
        )

        assertEquals(listOf("Blocked", "Every day · All day"), lines)
    }

    private fun earnRewardDraft(): RuleDraftUiState {
        return EarnItUiStateAdapters.ruleDraft(
            selectedEarnApps = listOf(EarnItRuleStore.LaunchableApp("com.duolingo", "Duolingo")),
            selectedRewardApps = listOf(EarnItRuleStore.RuleApp("com.instagram.android", "Instagram")),
            exchangeSelection = 2,
            activeDays = EarnItRuleStore.allDays.toSet(),
            timeWindows = listOf(EarnItRuleStore.TimeWindow(0, 1_440))
        )
    }

    private fun unlockDraft(): RuleDraftUiState {
        return EarnItUiStateAdapters.ruleDraft(
            selectedEarnApps = emptyList(),
            selectedRewardApps = listOf(EarnItRuleStore.RuleApp("com.instagram.android", "Instagram")),
            exchangeSelection = 2,
            activeDays = EarnItRuleStore.allDays.toSet(),
            timeWindows = listOf(EarnItRuleStore.TimeWindow(0, 1_440))
        )
    }
}
