package com.example.earnitv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictModePolicyTest {
    @Test
    fun activeStrictModeProtectsEnabledRule() {
        assertTrue(StrictModePolicy.isRuleProtected(activeStrictMode(), rule(enabled = true)))
    }

    @Test
    fun activatingStrictModeDoesNotProtectRule() {
        assertFalse(
            StrictModePolicy.isRuleProtected(
                StrictModeState(lifecycleState = StrictModeLifecycleState.Activating),
                rule(enabled = true)
            )
        )
    }

    @Test
    fun activeStrictModeDoesNotProtectDisabledRule() {
        assertFalse(StrictModePolicy.isRuleProtected(activeStrictMode(), rule(enabled = false)))
    }

    @Test
    fun activeStrictModeDoesNotProtectPausedRule() {
        val paused = rule(id = "paused", enabled = false)

        assertFalse(
            StrictModePolicy.isRuleProtected(
                strictModeState = activeStrictMode(),
                rule = paused,
                pauseExpirations = mapOf("paused" to 10_000L)
            )
        )
    }

    @Test
    fun enabledOrResumedRuleBecomesProtectedImmediately() {
        val resumed = rule(id = "resumed", enabled = true)

        assertTrue(StrictModePolicy.canEnableRule(rule(enabled = false)))
        assertTrue(StrictModePolicy.canResumeRule(rule(enabled = false)))
        assertTrue(StrictModePolicy.isRuleProtected(activeStrictMode(), resumed))
    }

    @Test
    fun newlyCreatedEnabledRuleIsProtected() {
        val newRule = rule(id = "new", enabled = true)

        assertTrue(StrictModePolicy.isRuleProtected(activeStrictMode(), newRule))
    }

    @Test
    fun protectedRuleBlocksEditPauseDisableAndDelete() {
        val protected = rule(enabled = true)
        val strictMode = activeStrictMode()

        assertFalse(StrictModePolicy.canEditRule(strictMode, protected))
        assertFalse(StrictModePolicy.canPauseRule(strictMode, protected))
        assertFalse(StrictModePolicy.canDisableRule(strictMode, protected))
        assertFalse(StrictModePolicy.canDeleteRule(strictMode, protected))
    }

    @Test
    fun deactivationStatesStillProtectEnabledRules() {
        val enabled = rule(enabled = true)

        assertTrue(
            StrictModePolicy.isRuleProtected(
                StrictModeState(lifecycleState = StrictModeLifecycleState.DeactivationCounting),
                enabled
            )
        )
        assertTrue(
            StrictModePolicy.isRuleProtected(
                StrictModeState(lifecycleState = StrictModeLifecycleState.DeactivationReady),
                enabled
            )
        )
    }

    @Test
    fun actionsBecomeAvailableAgainWhenStrictModeExpires() {
        val inactive = StrictModeState(lifecycleState = StrictModeLifecycleState.Inactive)
        val protected = rule(enabled = true)

        assertTrue(StrictModePolicy.canEditRule(inactive, protected))
        assertTrue(StrictModePolicy.canPauseRule(inactive, protected))
        assertTrue(StrictModePolicy.canDisableRule(inactive, protected))
        assertTrue(StrictModePolicy.canDeleteRule(inactive, protected))
    }

    @Test
    fun protectionSummaryUpdatesWithRuleState() {
        val enabled = rule(id = "enabled", enabled = true)
        val disabled = rule(id = "disabled", enabled = false)
        val paused = rule(id = "paused", enabled = false)

        val summary = strictModeRuleProtectionSummary(
            strictModeState = activeStrictMode(),
            rules = listOf(enabled, disabled, paused),
            pauseExpirations = mapOf("paused" to 10_000L)
        )

        assertEquals(listOf(enabled), summary.protectedRules)
        assertEquals(listOf(disabled, paused), summary.unprotectedRules)
        assertEquals("Paused", strictModeRuleStateLabel(paused, summary.pausedRuleIds))
        assertEquals("Disabled", strictModeRuleStateLabel(disabled, summary.pausedRuleIds))
    }

    private fun activeStrictMode(): StrictModeState {
        return StrictModeState(lifecycleState = StrictModeLifecycleState.Active)
    }

    private fun rule(id: String = "rule", enabled: Boolean): EarnItRuleStore.Rule {
        return EarnItRuleStore.Rule(
            id = id,
            productivePackage = "duo",
            productiveName = "Duolingo",
            blockedApps = listOf(EarnItRuleStore.RuleApp("ig", "Instagram")),
            rewardSecondsPerProductiveSecond = 1,
            activeDays = EarnItRuleStore.allDays.toSet(),
            startMinute = 0,
            endMinute = 1_440,
            enabled = enabled
        )
    }
}
