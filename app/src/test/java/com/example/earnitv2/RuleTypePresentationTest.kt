package com.kaleel.earnitv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RuleTypePresentationTest {
    @Test
    fun earnRewardTimeMapsToPlantGreenIdentity() {
        val presentation = ruleTypePresentation(EarnItRuleStore.RuleType.EarnRewardTime)

        assertEquals("Earn Reward Time", presentation.label)
        assertEquals(RuleTypeIconIdentity.Sprout, presentation.iconIdentity)
        assertEquals(RuleTypeAccentRole.Green, presentation.accentRole)
        assertEquals("Earn Reward Time Rule", presentation.contentDescription)
    }

    @Test
    fun completeToUnlockMapsToLockBlueIdentity() {
        val presentation = ruleTypePresentation(EarnItRuleStore.RuleType.CompleteToUnlock)

        assertEquals("Complete to Unlock", presentation.label)
        assertEquals(RuleTypeIconIdentity.Lock, presentation.iconIdentity)
        assertEquals(RuleTypeAccentRole.Blue, presentation.accentRole)
        assertEquals("Complete to Unlock Rule", presentation.contentDescription)
    }

    @Test
    fun scheduledBlockMapsToClockAmberIdentity() {
        val presentation = ruleTypePresentation(EarnItRuleStore.RuleType.ScheduledBlock)

        assertEquals("Scheduled Block", presentation.label)
        assertEquals(RuleTypeIconIdentity.Clock, presentation.iconIdentity)
        assertEquals(RuleTypeAccentRole.Amber, presentation.accentRole)
        assertEquals("Scheduled Block Rule", presentation.contentDescription)
    }

    @Test
    fun ruleTypeSelectionUsesProductionIconIdentitiesNotTemporaryLetters() {
        val temporaryLetterTiles = setOf("ER", "CU", "SB")
        val iconNames = RuleTypeOption.entries.map { option ->
            ruleTypePresentation(option.ruleType).iconIdentity.name
        }

        iconNames.forEach { iconName ->
            assertFalse(temporaryLetterTiles.contains(iconName))
        }
    }
}
