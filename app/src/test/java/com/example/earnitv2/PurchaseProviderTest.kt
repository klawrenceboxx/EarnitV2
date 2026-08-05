package com.kaleel.earnitv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseProviderTest {
    @Test
    fun successUpdatesEntitlementBeforeSuccessState() {
        val controller = RecordingController()
        val provider = provider(controller)
        provider.nextOutcome = MockPurchaseOutcome.Success

        provider.purchase(SubscriptionConfig.Placeholder.annual)

        assertTrue(controller.state.grantsPremium)
        assertEquals(PurchaseState.Success, provider.state.value)
    }

    @Test
    fun processingPendingFailureAndCancellationAreDistinct() {
        val expected = mapOf(
            MockPurchaseOutcome.Processing to PurchaseState.Processing,
            MockPurchaseOutcome.Pending to PurchaseState.Pending,
            MockPurchaseOutcome.Cancelled to PurchaseState.Cancelled
        )
        expected.forEach { (outcome, state) ->
            val provider = provider()
            provider.nextOutcome = outcome
            provider.purchase(SubscriptionConfig.Placeholder.monthly)
            assertEquals(state, provider.state.value)
        }

        val failed = provider()
        failed.nextOutcome = MockPurchaseOutcome.Failed
        failed.purchase(SubscriptionConfig.Placeholder.monthly)
        assertTrue(failed.state.value is PurchaseState.Failed)
    }

    @Test
    fun duplicateTapWhileProcessingDoesNotChangeOutcome() {
        val provider = provider()
        provider.nextOutcome = MockPurchaseOutcome.Processing
        provider.purchase(SubscriptionConfig.Placeholder.annual)
        provider.nextOutcome = MockPurchaseOutcome.Success
        provider.purchase(SubscriptionConfig.Placeholder.annual)
        assertEquals(PurchaseState.Processing, provider.state.value)
    }

    @Test
    fun restoreSuccessUpdatesEntitlementAndNotFoundDoesNot() {
        val successController = RecordingController()
        val success = provider(successController)
        success.nextOutcome = MockPurchaseOutcome.RestoreSuccess
        success.restorePurchases()
        assertEquals(PurchaseState.RestoreSuccess, success.state.value)
        assertTrue(successController.state.grantsPremium)

        val missingController = RecordingController()
        val missing = provider(missingController)
        missing.nextOutcome = MockPurchaseOutcome.RestoreNotFound
        missing.restorePurchases()
        assertEquals(PurchaseState.RestoreNotFound, missing.state.value)
        assertFalse(missingController.state.grantsPremium)
    }

    @Test
    fun releaseProviderFailsSafelyWithoutGrantingPremium() {
        val controller = RecordingController()
        val provider = LocalPurchaseProvider(
            SubscriptionConfig.Placeholder,
            controller,
            simulationEnabled = false
        )
        provider.purchase(SubscriptionConfig.Placeholder.annual)
        assertTrue(provider.state.value is PurchaseState.Unavailable)
        assertFalse(controller.state.grantsPremium)
    }

    @Test
    fun offlineCachedStatesRemainExplicit() {
        val premium = EntitlementState(
            EntitlementStatus.Active,
            EntitlementSource.Purchase,
            lastVerifiedAtMillis = 100,
            offline = true
        )
        val unknown = EntitlementState.Unknown.copy(offline = true)
        assertTrue(premium.grantsPremium)
        assertTrue(premium.offline)
        assertFalse(unknown.grantsPremium)
        assertTrue(unknown.offline)
    }

    private fun provider(controller: RecordingController = RecordingController()) =
        LocalPurchaseProvider(SubscriptionConfig.Placeholder, controller, simulationEnabled = true)

    private class RecordingController : DebugEntitlementController {
        var state: EntitlementState = EntitlementState.Free
        override fun simulate(state: EntitlementState) {
            this.state = state
        }
        override fun reset() {
            state = EntitlementState.Free
        }
    }
}
