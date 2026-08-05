package com.kaleel.earnitv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleAccessEvaluatorTest {
    @Test
    fun ruleTypeSelectionOptions_routeToTypedRuleTypes() {
        assertEquals(EarnItRuleStore.RuleType.EarnRewardTime, RuleTypeOption.EarnRewardTime.ruleType)
        assertEquals(EarnItRuleStore.RuleType.CompleteToUnlock, RuleTypeOption.CompleteToUnlock.ruleType)
        assertEquals(EarnItRuleStore.RuleType.ScheduledBlock, RuleTypeOption.ScheduledBlock.ruleType)
    }

    @Test
    fun earnRewardTime_multipleEarnAppsShareOneRuleExchange() {
        val rule = earnRule(
            productiveApps = listOf(app("anki", "Anki"), app("kindle", "Kindle"), app("duo", "Duolingo")),
            ratio = 2
        )

        assertEquals(setOf("anki", "kindle", "duo"), rule.earnAppPackages)
        assertEquals(2, rule.rewardSecondsPerProductiveSecond)
    }

    @Test
    fun completeToUnlock_incompleteRequirementDeniesAccess() {
        val rule = completeRule()
        val denial = RuleAccessEvaluator.evaluateRule(
            rule = rule,
            day = 1,
            minuteOfDay = 10 * 60,
            state = RuleAccessEvaluator.RuleRuntimeState(
                requirementProgressSeconds = mapOf("duo" to 10 * 60L, "kindle" to 5 * 60L)
            )
        )

        assertEquals(RuleAccessEvaluator.DenialReason.CompleteToUnlockIncomplete, denial?.reason)
    }

    @Test
    fun completeToUnlock_allRequirementsCompleteAllowsFromThisRule() {
        val rule = completeRule()
        val denial = RuleAccessEvaluator.evaluateRule(
            rule = rule,
            day = 1,
            minuteOfDay = 10 * 60,
            state = RuleAccessEvaluator.RuleRuntimeState(
                requirementProgressSeconds = mapOf("duo" to 10 * 60L, "kindle" to 30 * 60L)
            )
        )

        assertNull(denial)
    }

    @Test
    fun completeToUnlock_dailyCommitmentIsRequiredBeforeOtherRequirements() {
        val rule = completeRule().copy(requiresDailyCommitment = true)

        val denial = RuleAccessEvaluator.evaluateRule(
            rule = rule,
            day = 1,
            minuteOfDay = 10 * 60,
            state = RuleAccessEvaluator.RuleRuntimeState(
                requirementProgressSeconds = mapOf("duo" to 10 * 60L, "kindle" to 30 * 60L),
                hasDailyCommitment = false
            )
        )

        assertEquals(RuleAccessEvaluator.DenialReason.DailyCommitmentMissing, denial?.reason)
    }

    @Test
    fun completeToUnlock_dailyCommitmentDoesNotChangeRulesWithoutTheSetting() {
        val denial = RuleAccessEvaluator.evaluateRule(
            rule = completeRule(),
            day = 1,
            minuteOfDay = 10 * 60,
            state = RuleAccessEvaluator.RuleRuntimeState(
                requirementProgressSeconds = mapOf("duo" to 10 * 60L, "kindle" to 30 * 60L)
            )
        )

        assertNull(denial)
    }

    @Test
    fun completeToUnlock_inactiveScheduleDoesNotDeny() {
        val rule = completeRule(startMinute = 8 * 60, endMinute = 18 * 60)
        val denial = RuleAccessEvaluator.evaluateRule(
            rule = rule,
            day = 1,
            minuteOfDay = 20 * 60,
            state = RuleAccessEvaluator.RuleRuntimeState(requirementProgressSeconds = emptyMap())
        )

        assertNull(denial)
    }

    @Test
    fun scheduledBlock_activeWindowDeniesAccess() {
        val denial = RuleAccessEvaluator.evaluateRule(
            rule = scheduledRule(),
            day = 1,
            minuteOfDay = 10 * 60,
            state = RuleAccessEvaluator.RuleRuntimeState()
        )

        assertEquals(RuleAccessEvaluator.DenialReason.ScheduledBlockActive, denial?.reason)
    }

    @Test
    fun scheduledBlock_inactiveWindowDoesNotDeny() {
        val denial = RuleAccessEvaluator.evaluateRule(
            rule = scheduledRule(),
            day = 1,
            minuteOfDay = 18 * 60,
            state = RuleAccessEvaluator.RuleRuntimeState()
        )

        assertNull(denial)
    }

    @Test
    fun overnightWindow_startDayOwnsCrossMidnightWindow() {
        val rule = scheduledRule(activeDays = setOf(1), startMinute = 21 * 60, endMinute = 8 * 60)

        assertTrue(rule.isActiveAt(day = 1, minuteOfDay = 22 * 60))
        assertTrue(rule.isActiveAt(day = 2, minuteOfDay = 7 * 60))
        assertFalse(rule.isActiveAt(day = 2, minuteOfDay = 9 * 60))
    }

    @Test
    fun composition_oneDenialAmongMultipleRulesBlocksAccess() {
        val allowRule = earnRule(id = "earn", ratio = 1)
        val denyRule = scheduledRule(id = "schedule")
        val result = RuleAccessEvaluator.evaluate(
            rules = listOf(allowRule, denyRule),
            blockedPackage = "ig",
            day = 1,
            minuteOfDay = 10 * 60,
            runtimeState = { rule ->
                RuleAccessEvaluator.RuleRuntimeState(remainingRewardSeconds = if (rule.id == "earn") 600 else 0)
            }
        )

        assertFalse(result.allowed)
        assertEquals(RuleAccessEvaluator.DenialReason.ScheduledBlockActive, result.primaryDenial?.reason)
    }

    @Test
    fun precedence_scheduledBlockOutranksIncompleteRequirements() {
        val result = RuleAccessEvaluator.evaluate(
            rules = listOf(completeRule(id = "complete"), scheduledRule(id = "schedule")),
            blockedPackage = "ig",
            day = 1,
            minuteOfDay = 10 * 60,
            runtimeState = { RuleAccessEvaluator.RuleRuntimeState(requirementProgressSeconds = emptyMap()) }
        )

        assertEquals(RuleAccessEvaluator.DenialReason.ScheduledBlockActive, result.primaryDenial?.reason)
    }

    @Test
    fun precedence_incompleteRequirementsOutrankZeroRewardTime() {
        val result = RuleAccessEvaluator.evaluate(
            rules = listOf(earnRule(id = "earn"), completeRule(id = "complete")),
            blockedPackage = "ig",
            day = 1,
            minuteOfDay = 10 * 60,
            runtimeState = { RuleAccessEvaluator.RuleRuntimeState(remainingRewardSeconds = 0, requirementProgressSeconds = emptyMap()) }
        )

        assertEquals(RuleAccessEvaluator.DenialReason.CompleteToUnlockIncomplete, result.primaryDenial?.reason)
    }

    @Test
    fun rewardPreservation_scheduledBlockDoesNotSpendOrDenyRewardState() {
        val earn = earnRule(id = "earn")
        val scheduled = scheduledRule(id = "schedule")
        val result = RuleAccessEvaluator.evaluate(
            rules = listOf(earn, scheduled),
            blockedPackage = "ig",
            day = 1,
            minuteOfDay = 10 * 60,
            runtimeState = { RuleAccessEvaluator.RuleRuntimeState(remainingRewardSeconds = 1_800) }
        )

        assertFalse(result.allowed)
        assertNull(result.spendRule)
        assertEquals(RuleAccessEvaluator.DenialReason.ScheduledBlockActive, result.primaryDenial?.reason)
    }

    @Test
    fun pause_disabledRuleDoesNotDenyAccess() {
        val denial = RuleAccessEvaluator.evaluateRule(
            rule = scheduledRule(enabled = false),
            day = 1,
            minuteOfDay = 10 * 60,
            state = RuleAccessEvaluator.RuleRuntimeState()
        )

        assertNull(denial)
    }

    @Test
    fun enabledEarnRewardConflict_secondRuleIsDisabledByNormalization() {
        val normalized = listOf(earnRule(id = "one"), earnRule(id = "two")).normalizeForTest()

        assertTrue(normalized.first { it.id == "one" }.enabled)
        assertFalse(normalized.first { it.id == "two" }.enabled)
    }

    private fun List<EarnItRuleStore.Rule>.normalizeForTest(): List<EarnItRuleStore.Rule> {
        val claimed = mutableSetOf<String>()
        return map { rule ->
            if (!rule.enabled || rule.type != EarnItRuleStore.RuleType.EarnRewardTime) return@map rule
            val conflict = rule.blockedApps.any { it.packageName in claimed }
            rule.blockedApps.forEach { claimed += it.packageName }
            if (conflict) rule.copy(enabled = false) else rule
        }
    }

    private fun app(packageName: String, name: String = packageName): EarnItRuleStore.RuleApp {
        return EarnItRuleStore.RuleApp(packageName, name)
    }

    private fun earnRule(
        id: String = "earn",
        productiveApps: List<EarnItRuleStore.RuleApp> = listOf(app("duo", "Duolingo")),
        ratio: Int = 1,
        enabled: Boolean = true
    ): EarnItRuleStore.Rule {
        val primary = productiveApps.first()
        return EarnItRuleStore.Rule(
            id = id,
            productivePackage = primary.packageName,
            productiveName = primary.name,
            blockedApps = listOf(app("ig", "Instagram")),
            rewardSecondsPerProductiveSecond = ratio,
            activeDays = EarnItRuleStore.allDays.toSet(),
            startMinute = 0,
            endMinute = 1_440,
            enabled = enabled,
            type = EarnItRuleStore.RuleType.EarnRewardTime,
            productiveApps = productiveApps
        )
    }

    private fun completeRule(
        id: String = "complete",
        startMinute: Int = 8 * 60,
        endMinute: Int = 18 * 60,
        enabled: Boolean = true
    ): EarnItRuleStore.Rule {
        return EarnItRuleStore.Rule(
            id = id,
            productivePackage = "duo",
            productiveName = "Duolingo",
            blockedApps = listOf(app("ig", "Instagram")),
            rewardSecondsPerProductiveSecond = 1,
            activeDays = EarnItRuleStore.allDays.toSet(),
            startMinute = startMinute,
            endMinute = endMinute,
            enabled = enabled,
            type = EarnItRuleStore.RuleType.CompleteToUnlock,
            requirements = listOf(
                EarnItRuleStore.RuleRequirement(app("duo", "Duolingo"), 10 * 60L),
                EarnItRuleStore.RuleRequirement(app("kindle", "Kindle"), 30 * 60L)
            )
        )
    }

    private fun scheduledRule(
        id: String = "schedule",
        activeDays: Set<Int> = setOf(1, 2, 3, 4, 5),
        startMinute: Int = 9 * 60,
        endMinute: Int = 17 * 60,
        enabled: Boolean = true
    ): EarnItRuleStore.Rule {
        return EarnItRuleStore.Rule(
            id = id,
            productivePackage = "",
            productiveName = "",
            blockedApps = listOf(app("ig", "Instagram")),
            rewardSecondsPerProductiveSecond = 1,
            activeDays = activeDays,
            startMinute = startMinute,
            endMinute = endMinute,
            enabled = enabled,
            type = EarnItRuleStore.RuleType.ScheduledBlock
        )
    }
}
