package com.kaleel.earnitv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementPolicyTest {
    @Test
    fun free_hasOnlyCoreAccessAndTwoRuleLimit() {
        val policy = FeatureAccessPolicy(EntitlementState.Free)

        PremiumFeature.entries.forEach { assertFalse(policy.canUse(it)) }
        assertEquals(2, policy.activeRuleLimit)
        assertEquals(AnalyticsRangeAccess.TodayOnly, policy.analyticsRangeAccess)
    }

    @Test
    fun purchasedBetaDebugAndGraceGrantPremiumAccess() {
        val states = listOf(
            EntitlementState(EntitlementStatus.Active, EntitlementSource.Purchase),
            EntitlementState(EntitlementStatus.Active, EntitlementSource.Beta),
            EntitlementState(EntitlementStatus.Active, EntitlementSource.Debug),
            EntitlementState(EntitlementStatus.GracePeriod, EntitlementSource.Purchase)
        )

        states.forEach { state ->
            val policy = FeatureAccessPolicy(state)
            PremiumFeature.entries.forEach { assertTrue(policy.canUse(it)) }
            assertNull(policy.activeRuleLimit)
            assertEquals(AnalyticsRangeAccess.SevenDays, policy.analyticsRangeAccess)
        }
    }

    @Test
    fun expiredAndUnknownFailSafelyToFreePolicy() {
        listOf(
            EntitlementState(EntitlementStatus.Expired, EntitlementSource.Purchase),
            EntitlementState.Unknown
        ).forEach {
            assertFalse(FeatureAccessPolicy(it).canUse(PremiumFeature.StrictMode))
            assertEquals(2, FeatureAccessPolicy(it).activeRuleLimit)
        }
    }

    @Test
    fun productionDefaultCannotBecomeBetaOrDebugPremium() {
        val default = EntitlementDefaults.forBuild(betaEntitlement = false)
        assertEquals(EntitlementState.Free, default)
        assertFalse(default.grantsPremium)
        assertEquals(EntitlementSource.None, default.source)
    }

    @Test
    fun explicitBetaBuildGetsBetaSource() {
        val state = EntitlementDefaults.forBuild(betaEntitlement = true)
        assertTrue(state.grantsPremium)
        assertEquals(EntitlementSource.Beta, state.source)
    }

    @Test
    fun placeholderPricingCalculatesSavingsAndModelsDisabledTrial() {
        val config = SubscriptionConfig.Placeholder
        assertEquals(52, config.yearlySavingsPercent)
        assertEquals("3.33", config.annualMonthlyEquivalent.toPlainString())
        assertEquals(7, config.annual.trialDays)
        assertFalse(config.annualTrialEnabled)
    }

    @Test
    fun strictAndDeepWorkStartOnlyWithPremiumButRunningSessionsContinue() {
        val free = FeatureAccessPolicy(EntitlementState.Free)
        val premium = FeatureAccessPolicy(
            EntitlementState(EntitlementStatus.Active, EntitlementSource.Purchase)
        )
        assertFalse(PremiumSessionPolicy.canStartDeepWork(free))
        assertTrue(PremiumSessionPolicy.canStartDeepWork(premium))
        assertFalse(PremiumSessionPolicy.canOpenStrictMode(free, restrictiveSessionRunning = false))
        assertTrue(PremiumSessionPolicy.canOpenStrictMode(premium, restrictiveSessionRunning = false))
        assertTrue(PremiumSessionPolicy.canOpenStrictMode(free, restrictiveSessionRunning = true))
        assertTrue(PremiumSessionPolicy.shouldContinueRestrictiveSession())
    }
}
