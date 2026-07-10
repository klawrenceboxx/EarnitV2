package com.example.earnitv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EarnItRuleStoreSerializationTest {
    @Test
    fun completeToUnlock_roundTripsMultipleRequirementsAndUnlockApps() {
        val rule = completeRule(
            requirements = listOf(
                requirement("duo", "Duolingo", 10),
                requirement("headspace", "Headspace", 20)
            )
        )

        val decoded = EarnItRuleStore.decodeRules(EarnItRuleStore.encodeRules(listOf(rule))).single()

        assertEquals(EarnItRuleStore.RuleType.CompleteToUnlock, decoded.type)
        assertEquals(listOf("duo", "headspace"), decoded.requirements.map { it.app.packageName })
        assertEquals(listOf(10L * 60L, 20L * 60L), decoded.requirements.map { it.requiredSeconds })
        assertEquals(listOf("ig", "snap"), decoded.blockedApps.map { it.packageName })
        assertEquals(setOf(1, 2, 3, 4, 5), decoded.activeDays)
        assertEquals(21 * 60, decoded.startMinute)
        assertEquals(8 * 60, decoded.endMinute)
    }

    @Test
    fun completeToUnlock_roundTripsCustomRequirementDuration() {
        val rule = completeRule(requirements = listOf(requirement("duo", "Duolingo", 45)))

        val decoded = EarnItRuleStore.decodeRules(EarnItRuleStore.encodeRules(listOf(rule))).single()

        assertEquals(45L * 60L, decoded.requirements.single().requiredSeconds)
    }

    @Test
    fun scheduledBlock_roundTripsWithoutEarnRewardFields() {
        val rule = scheduledRule()

        val decoded = EarnItRuleStore.decodeRules(EarnItRuleStore.encodeRules(listOf(rule))).single()

        assertEquals(EarnItRuleStore.RuleType.ScheduledBlock, decoded.type)
        assertTrue(decoded.earnApps.isEmpty())
        assertEquals(listOf("ig", "snap"), decoded.blockedApps.map { it.packageName })
        assertEquals(setOf(1, 2, 3, 4, 5), decoded.activeDays)
        assertEquals(21 * 60, decoded.startMinute)
        assertEquals(8 * 60, decoded.endMinute)
    }

    @Test
    fun editingCompleteToUnlockKeepsRuleIdAndDoesNotDuplicate() {
        val original = completeRule(
            id = "rule_complete",
            requirements = listOf(
                requirement("duo", "Duolingo", 10),
                requirement("headspace", "Headspace", 20)
            )
        )
        val edited = original.copy(
            requirements = listOf(
                requirement("duo", "Duolingo", 10),
                requirement("headspace", "Headspace", 30)
            )
        )

        val updated = listOf(original).map { if (it.id == edited.id) edited else it }
        val decoded = EarnItRuleStore.decodeRules(EarnItRuleStore.encodeRules(updated))

        assertEquals(1, decoded.size)
        assertEquals("rule_complete", decoded.single().id)
        assertEquals(listOf(10L * 60L, 30L * 60L), decoded.single().requirements.map { it.requiredSeconds })
        assertEquals(listOf("ig", "snap"), decoded.single().blockedApps.map { it.packageName })
    }

    @Test
    fun editingScheduledBlockKeepsRuleIdAndDoesNotDuplicate() {
        val original = scheduledRule(id = "rule_schedule")
        val edited = original.copy(activeDays = EarnItRuleStore.allDays.toSet(), startMinute = 0, endMinute = 1_440)

        val updated = listOf(original).map { if (it.id == edited.id) edited else it }
        val decoded = EarnItRuleStore.decodeRules(EarnItRuleStore.encodeRules(updated))

        assertEquals(1, decoded.size)
        assertEquals("rule_schedule", decoded.single().id)
        assertEquals(EarnItRuleStore.allDays.toSet(), decoded.single().activeDays)
        assertEquals(EarnItRuleStore.TimeWindow(0, 1_440), decoded.single().effectiveTimeWindows.single())
    }

    private fun completeRule(
        id: String = "rule_complete",
        requirements: List<EarnItRuleStore.RuleRequirement>
    ): EarnItRuleStore.Rule {
        return EarnItRuleStore.Rule(
            id = id,
            productivePackage = "",
            productiveName = "",
            blockedApps = listOf(app("ig", "Instagram"), app("snap", "Snapchat")),
            rewardSecondsPerProductiveSecond = 1,
            activeDays = setOf(1, 2, 3, 4, 5),
            startMinute = 21 * 60,
            endMinute = 8 * 60,
            type = EarnItRuleStore.RuleType.CompleteToUnlock,
            requirements = requirements
        )
    }

    private fun scheduledRule(id: String = "rule_schedule"): EarnItRuleStore.Rule {
        return EarnItRuleStore.Rule(
            id = id,
            productivePackage = "",
            productiveName = "",
            blockedApps = listOf(app("ig", "Instagram"), app("snap", "Snapchat")),
            rewardSecondsPerProductiveSecond = 1,
            activeDays = setOf(1, 2, 3, 4, 5),
            startMinute = 21 * 60,
            endMinute = 8 * 60,
            type = EarnItRuleStore.RuleType.ScheduledBlock
        )
    }

    private fun requirement(packageName: String, name: String, minutes: Long): EarnItRuleStore.RuleRequirement {
        return EarnItRuleStore.RuleRequirement(app(packageName, name), minutes * 60L)
    }

    private fun app(packageName: String, name: String): EarnItRuleStore.RuleApp {
        return EarnItRuleStore.RuleApp(packageName = packageName, name = name)
    }
}
