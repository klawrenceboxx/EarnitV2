package com.example.earnitv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumUxTest {
    @Test
    fun contextualGatesHaveRequiredCopyBenefitsAndDistinctSemantics() {
        val deepWork = premiumGateContent(PremiumEntryPoint.DeepWork)
        val strict = premiumGateContent(PremiumEntryPoint.StrictMode)
        val analytics = premiumGateContent(PremiumEntryPoint.Analytics)
        val limit = premiumGateContent(PremiumEntryPoint.RuleLimit)

        assertEquals("Deep Work is part of EarnIt Pro", deepWork.title)
        assertEquals("Strict Mode is part of EarnIt Pro", strict.title)
        assertEquals("Unlock your weekly view", analytics.title)
        assertEquals("You've reached the free limit", limit.title)
        listOf(deepWork, strict, analytics, limit).forEach {
            assertEquals(3, it.benefits.size)
            assertTrue(it.iconSemantic.isNotBlank())
        }
        assertEquals(4, listOf(deepWork, strict, analytics, limit).map { it.iconSemantic }.distinct().size)
        assertNotEquals(deepWork.icon, strict.icon)
    }

    @Test
    fun gatePrimaryActionOpensPlansAndPreservesFlowContext() {
        val draftContext = ProFlowState(ProRoute.Gate, PremiumEntryPoint.RuleLimit)
        val result = proGateUpgradeFlow(draftContext)
        assertEquals(ProRoute.Plans, result.route)
        assertEquals(PremiumEntryPoint.RuleLimit, result.entryPoint)
    }

    @Test
    fun annualIsDefaultAndSelectedPlanIsUsed() {
        val config = SubscriptionConfig.Placeholder
        val flow = ProFlowState(ProRoute.Plans)
        assertEquals(config.annual.id, flow.selectedPlanId)
        assertEquals(config.annual, selectedSubscriptionPlan(config, flow.selectedPlanId))
        assertEquals(config.monthly, selectedSubscriptionPlan(config, config.monthly.id))
        assertEquals(52, config.yearlySavingsPercent)
    }

    @Test
    fun simulatorLabelsAreHumanReadable() {
        assertEquals(
            "Purchased Premium",
            humanReadableEntitlement(EntitlementState(EntitlementStatus.Active, EntitlementSource.Purchase))
        )
        assertEquals(
            "Offline Premium",
            humanReadableEntitlement(
                EntitlementState(EntitlementStatus.Active, EntitlementSource.Purchase, offline = true)
            )
        )
        assertFalse(humanReadableEntitlement(EntitlementState.Free).contains("EntitlementStatus"))
    }
}
