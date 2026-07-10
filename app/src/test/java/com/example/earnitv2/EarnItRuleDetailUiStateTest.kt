package com.example.earnitv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EarnItRuleDetailUiStateTest {
    @Test
    fun activeOverflowMenu_keepsEditAndDeleteOnly() {
        assertEquals(
            listOf(
                RuleDetailOverflowAction.Edit,
                RuleDetailOverflowAction.Delete
            ),
            ruleDetailOverflowActions(earnRule(enabled = true))
        )
    }

    @Test
    fun pausedOverflowMenu_containsEditDeleteOnly() {
        assertEquals(
            listOf(RuleDetailOverflowAction.Edit, RuleDetailOverflowAction.Delete),
            ruleDetailOverflowActions(earnRule(enabled = false))
        )
    }

    @Test
    fun timedPauseStatusShowsResumeCountdown() {
        val state = ruleDetailStatusCardState(
            rule = earnRule(enabled = false),
            availableRewardTimeLabel = "<1 min available",
            isActiveNow = false,
            pauseCountdownLabel = "4:32"
        )

        assertEquals("Rule paused", state.title)
        assertEquals("Resumes in", state.stateLabel)
        assertEquals("4:32", state.metric)
        assertTrue(state.showResume)
    }

    @Test
    fun pauseCountdownLabelFormatsMinutesAndSeconds() {
        assertEquals("4:32", pauseCountdownLabel(expiresAtMillis = 272_000L, nowMillis = 0L))
        assertEquals("0:00", pauseCountdownLabel(expiresAtMillis = 0L, nowMillis = 1_000L))
    }

    @Test
    fun longerPauseOptionsExcludeManualResumePause() {
        val labels = pauseOptionsForRule(earnRule(), nowMillis = 0L).map { it.label }

        assertEquals(
            listOf("15 minutes", "30 minutes", "1 hour", "Until next scheduled period", "Until tomorrow"),
            labels
        )
        assertFalse(labels.contains("Pause until manually resumed"))
    }

    @Test
    fun resumeShownOnlyWhenPaused() {
        assertTrue(
            ruleDetailStatusCardState(
                rule = earnRule(enabled = false),
                availableRewardTimeLabel = "<1 min available",
                isActiveNow = false
            ).showResume
        )
        assertFalse(
            ruleDetailStatusCardState(
                rule = earnRule(enabled = true),
                availableRewardTimeLabel = "<1 min available",
                isActiveNow = true
            ).showResume
        )
    }

    @Test
    fun completeToUnlockRequirementCountUsesGrammar() {
        assertEquals(
            "1 requirement remaining",
            ruleDetailStatusCardState(
                rule = completeRule(requirements = listOf(requirement("anki", "AnkiDroid", 10))),
                availableRewardTimeLabel = "No Reward Time",
                isActiveNow = true
            ).metric
        )
        assertEquals(
            "2 requirements remaining",
            ruleDetailStatusCardState(
                rule = completeRule(
                    requirements = listOf(
                        requirement("anki", "AnkiDroid", 10),
                        requirement("headspace", "Headspace", 10)
                    )
                ),
                availableRewardTimeLabel = "No Reward Time",
                isActiveNow = true
            ).metric
        )
    }

    @Test
    fun completeToUnlockDoesNotUseRewardTimeLanguage() {
        val state = ruleDetailStatusCardState(
            rule = completeRule(requirements = listOf(requirement("anki", "AnkiDroid", 10))),
            availableRewardTimeLabel = "No Reward Time",
            isActiveNow = true
        )

        assertFalse(state.title.contains("Reward Time"))
        assertFalse(state.metric.contains("Reward Time"))
        assertFalse(state.body.contains("Reward Time"))
    }

    @Test
    fun completeToUnlockSectionsIncludeUnlocksWithoutManageRule() {
        val titles = ruleDetailSectionTitles(completeRule())

        assertEquals(listOf("Status", "Complete to unlock", "Schedule"), titles)
        assertFalse(titles.contains("Manage Rule"))
        assertEquals(1, titles.count { it == "Complete to unlock" })
    }

    @Test
    fun earnRewardTimeExchangeCopyShownOnceForOneOrMultipleEarnApps() {
        assertEquals(
            "Every 10 min earns 2 min Reward Time",
            earnRewardExchangeCopy(earnRule(earnApps = listOf(app("duo", "Duolingo"))))
        )
        assertEquals(
            "Every 10 min across selected Earn Apps earns 2 min Reward Time.",
            earnRewardExchangeCopy(
                earnRule(
                    earnApps = listOf(
                        app("duo", "Duolingo"),
                        app("anki", "AnkiDroid")
                    )
                )
            )
        )
    }

    @Test
    fun earnRewardTimeSectionsRemoveDuplicateRewardAppsAndAgreementCards() {
        val titles = ruleDetailSectionTitles(earnRule())

        assertEquals(listOf("Status", "Earn Reward Time", "Schedule"), titles)
        assertFalse(titles.contains("Reward Apps this applies to"))
        assertFalse(titles.contains("Rule agreement"))
        assertFalse(titles.contains("Manage Rule"))
    }

    @Test
    fun scheduledBlockPausedOutsideAndWithinStatesAreDistinct() {
        val paused = ruleDetailStatusCardState(
            rule = scheduledRule(enabled = false),
            availableRewardTimeLabel = "No Reward Time",
            isActiveNow = false
        )
        val outside = ruleDetailStatusCardState(
            rule = scheduledRule(enabled = true),
            availableRewardTimeLabel = "No Reward Time",
            isActiveNow = false
        )
        val within = ruleDetailStatusCardState(
            rule = scheduledRule(enabled = true),
            availableRewardTimeLabel = "No Reward Time",
            isActiveNow = true
        )

        assertEquals("Rule paused", paused.title)
        assertEquals("Not blocking now", paused.metric)
        assertNull(paused.stateLabel)
        assertEquals("Not blocking now", outside.title)
        assertEquals("Outside block schedule", outside.stateLabel)
        assertEquals("Blocking now", within.title)
        assertEquals("Within block schedule", within.stateLabel)
    }

    @Test
    fun scheduledBlockSectionsShowPrecedenceNoteOnlyForScheduledBlock() {
        assertTrue(ruleDetailShowsScheduledBlockPrecedenceNote(scheduledRule()))
        assertFalse(ruleDetailShowsScheduledBlockPrecedenceNote(earnRule()))
        assertFalse(ruleDetailShowsScheduledBlockPrecedenceNote(completeRule()))

        assertEquals(
            listOf("Status", "Blocked Apps", "Block Schedule", "Priority note"),
            ruleDetailSectionTitles(scheduledRule())
        )
    }

    @Test
    fun requirementAndEarnAppRowsExposeOpenTargetsByPackage() {
        val requirement = requirement("anki", "AnkiDroid", 10)
        val earn = app("duo", "Duolingo")

        assertEquals("anki", requirement.app.packageName)
        assertEquals("10 min required", requirementDurationLabel(requirement))
        assertEquals("duo", earn.packageName)
    }

    private fun earnRule(
        enabled: Boolean = true,
        earnApps: List<EarnItRuleStore.RuleApp> = listOf(app("duo", "Duolingo"))
    ): EarnItRuleStore.Rule {
        val primary = earnApps.first()
        return EarnItRuleStore.Rule(
            id = "earn",
            productivePackage = primary.packageName,
            productiveName = primary.name,
            blockedApps = listOf(app("ig", "Instagram"), app("snap", "Snapchat")),
            rewardSecondsPerProductiveSecond = 2,
            activeDays = EarnItRuleStore.allDays.toSet(),
            startMinute = 0,
            endMinute = 1_440,
            enabled = enabled,
            type = EarnItRuleStore.RuleType.EarnRewardTime,
            productiveApps = earnApps
        )
    }

    private fun completeRule(
        requirements: List<EarnItRuleStore.RuleRequirement> = listOf(requirement("anki", "AnkiDroid", 10))
    ): EarnItRuleStore.Rule {
        return EarnItRuleStore.Rule(
            id = "complete",
            productivePackage = "",
            productiveName = "",
            blockedApps = listOf(app("ig", "Instagram"), app("snap", "Snapchat")),
            rewardSecondsPerProductiveSecond = 1,
            activeDays = EarnItRuleStore.allDays.toSet(),
            startMinute = 0,
            endMinute = 1_440,
            enabled = true,
            type = EarnItRuleStore.RuleType.CompleteToUnlock,
            requirements = requirements
        )
    }

    private fun scheduledRule(enabled: Boolean = true): EarnItRuleStore.Rule {
        return EarnItRuleStore.Rule(
            id = "scheduled",
            productivePackage = "",
            productiveName = "",
            blockedApps = listOf(app("ig", "Instagram"), app("snap", "Snapchat")),
            rewardSecondsPerProductiveSecond = 1,
            activeDays = EarnItRuleStore.allDays.toSet(),
            startMinute = 22 * 60,
            endMinute = 8 * 60,
            enabled = enabled,
            type = EarnItRuleStore.RuleType.ScheduledBlock
        )
    }

    private fun requirement(
        packageName: String,
        name: String,
        minutes: Long
    ): EarnItRuleStore.RuleRequirement {
        return EarnItRuleStore.RuleRequirement(app(packageName, name), minutes * 60L)
    }

    private fun app(packageName: String, name: String): EarnItRuleStore.RuleApp {
        return EarnItRuleStore.RuleApp(packageName = packageName, name = name)
    }
}
