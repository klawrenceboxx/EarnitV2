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
