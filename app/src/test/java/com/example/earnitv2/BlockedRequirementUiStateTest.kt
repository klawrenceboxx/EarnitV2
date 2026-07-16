package com.example.earnitv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockedRequirementUiStateTest {
    @Test
    fun ctu_oneIncompleteRequirementBuildsSingleLaunchTarget() {
        val rows = blockedRequirementUiStates(
            rule = completeRule(requirements = listOf(requirement("duo", "Duolingo", 10))),
            progressSeconds = mapOf("duo" to 0L)
        )

        assertEquals(1, rows.size)
        assertEquals("duo", rows.single().packageName)
        assertEquals("Duolingo", rows.single().name)
        assertEquals("0 / 10 min", rows.single().progressLabel)
    }

    @Test
    fun ctu_multipleIncompleteRequirementsBuildSeparateLaunchTargets() {
        val rows = blockedRequirementUiStates(
            rule = completeRule(
                requirements = listOf(
                    requirement("duo", "Duolingo", 10),
                    requirement("headspace", "Headspace", 10)
                )
            ),
            progressSeconds = mapOf("duo" to 0L, "headspace" to 4 * 60L)
        )

        assertEquals(listOf("duo", "headspace"), rows.map { it.packageName })
        assertEquals(listOf("Open Duolingo", "Open Headspace"), rows.map { "Open ${it.name}" })
        assertEquals(listOf("0 / 10 min", "4 / 10 min"), rows.map { it.progressLabel })
    }

    @Test
    fun ctu_completedRequirementIsOmitted() {
        val rows = blockedRequirementUiStates(
            rule = completeRule(
                requirements = listOf(
                    requirement("duo", "Duolingo", 10),
                    requirement("headspace", "Headspace", 10)
                )
            ),
            progressSeconds = mapOf("duo" to 10 * 60L, "headspace" to 4 * 60L)
        )

        assertEquals(1, rows.size)
        assertEquals("headspace", rows.single().packageName)
        assertFalse(rows.any { it.packageName == "duo" })
    }

    @Test
    fun ctu_missingProgressShowsHonestConfiguredRequirement() {
        val rows = blockedRequirementUiStates(
            rule = completeRule(requirements = listOf(requirement("duo", "Duolingo", 10))),
            progressSeconds = emptyMap()
        )

        assertEquals("10 min required", rows.single().progressLabel)
    }

    @Test
    fun ctuBlockedCopyDoesNotUseRewardTimeWording() {
        val title = blockedTitle(RuleAccessEvaluator.DenialReason.CompleteToUnlockIncomplete)
        val description = blockedDescription(
            RuleAccessEvaluator.DenialReason.CompleteToUnlockIncomplete,
            blockedAppName = "Instagram"
        )

        assertEquals("Complete requirements to unlock", title)
        assertFalse(title.contains("Reward Time"))
        assertFalse(description.contains("Reward Time"))
        assertFalse(description.contains("Earn more"))
    }

    @Test
    fun earnRewardBlockedRowsIncludeEveryConfiguredEarnApp() {
        val rule = completeRule(emptyList()).copy(
            type = EarnItRuleStore.RuleType.EarnRewardTime,
            rewardSecondsPerProductiveSecond = 2,
            productiveApps = listOf(
                EarnItRuleStore.RuleApp("duo", "Duolingo"),
                EarnItRuleStore.RuleApp("anki", "AnkiDroid"),
                EarnItRuleStore.RuleApp("kindle", "Kindle")
            )
        )

        val rows = blockedEarnAppUiStates(rule)

        assertEquals(listOf("duo", "anki", "kindle"), rows.map { it.packageName })
        assertEquals(listOf("Duolingo", "AnkiDroid", "Kindle"), rows.map { it.name })
        assertTrue(rows.all { it.exchangeSummary == "Every 10 min earns 2 min Reward Time" })
    }

    @Test
    fun earnRewardBlockedRowsRetainLegacySingleAppFallback() {
        val rule = completeRule(emptyList()).copy(
            type = EarnItRuleStore.RuleType.EarnRewardTime,
            productivePackage = "",
            productiveName = "",
            productiveApps = emptyList()
        )

        val rows = blockedEarnAppUiStates(
            rule = rule,
            legacyEarnAppName = "Duolingo",
            legacyEarnAppPackage = "duo"
        )

        assertEquals(listOf(BlockedEarnAppUiState("duo", "Duolingo", "Every 10 min earns 1 min Reward Time")), rows)
    }

    @Test
    fun earnRewardBlockedPresentationShowsFourActionsAndDynamicOverflow() {
        val rule = completeRule(emptyList()).copy(
            type = EarnItRuleStore.RuleType.EarnRewardTime,
            productiveApps = (1..7).map { index ->
                EarnItRuleStore.RuleApp("earn.$index", "Earn App $index")
            }
        )

        val presentation = blockedOptionsPresentation(blockedEarnAppUiStates(rule))

        assertEquals(listOf("earn.1", "earn.2", "earn.3", "earn.4"), presentation.visibleOptions.map { it.packageName })
        assertEquals(3, presentation.hiddenCount)
        assertEquals("View 3 more earning apps", blockedOverflowLabel(presentation.hiddenCount))
    }

    @Test
    fun completeToUnlockBlockedPresentationUsesTheSameFourActionLimit() {
        val requirements = (1..6).map { index -> requirement("requirement.$index", "Requirement $index", 10) }
        val options = blockedRequirementUiStates(
            rule = completeRule(requirements),
            progressSeconds = requirements.associate { it.app.packageName to 0L }
        )

        val presentation = blockedOptionsPresentation(options)

        assertEquals(
            listOf("requirement.1", "requirement.2", "requirement.3", "requirement.4"),
            presentation.visibleOptions.map { it.packageName }
        )
        assertEquals(2, presentation.hiddenCount)
        assertEquals("View 2 more earning apps", blockedOverflowLabel(presentation.hiddenCount))
    }

    @Test
    fun blockedPresentationDoesNotAddOverflowAtFourAndUsesSingularCopyAtFive() {
        assertEquals(0, blockedOptionsPresentation(listOf(1, 2, 3, 4)).hiddenCount)
        assertEquals(1, blockedOptionsPresentation(listOf(1, 2, 3, 4, 5)).hiddenCount)
        assertEquals("View 1 more earning app", blockedOverflowLabel(1))
    }

    @Test
    fun nonCtuRulesDoNotBuildRequirementRows() {
        val rows = blockedRequirementUiStates(
            rule = completeRule(requirements = listOf(requirement("duo", "Duolingo", 10)))
                .copy(type = EarnItRuleStore.RuleType.EarnRewardTime),
            progressSeconds = mapOf("duo" to 0L)
        )

        assertTrue(rows.isEmpty())
    }

    private fun requirement(
        packageName: String,
        name: String,
        minutes: Long
    ): EarnItRuleStore.RuleRequirement {
        return EarnItRuleStore.RuleRequirement(
            app = EarnItRuleStore.RuleApp(packageName = packageName, name = name),
            requiredSeconds = minutes * 60L
        )
    }

    private fun completeRule(
        requirements: List<EarnItRuleStore.RuleRequirement>
    ): EarnItRuleStore.Rule {
        return EarnItRuleStore.Rule(
            id = "complete",
            productivePackage = "duo",
            productiveName = "Duolingo",
            blockedApps = listOf(EarnItRuleStore.RuleApp("ig", "Instagram")),
            rewardSecondsPerProductiveSecond = 1,
            activeDays = EarnItRuleStore.allDays.toSet(),
            startMinute = 0,
            endMinute = 1_440,
            enabled = true,
            type = EarnItRuleStore.RuleType.CompleteToUnlock,
            requirements = requirements
        )
    }
}
