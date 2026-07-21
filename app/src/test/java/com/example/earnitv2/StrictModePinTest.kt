package com.example.earnitv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictModePinTest {
    @Test fun setupRequiresNumericMatchingPinAndCountdown() {
        val base = StrictModeSetupState(
            durationType = StrictModeDurationType.Indefinite,
            timedDurationMillis = null,
            deactivationCountdownMillis = 60L * 60_000L,
            protectionMethod = StrictModeProtectionMethod.Pin
        )
        assertFalse(base.copy(pin = "123", pinConfirmation = "123").isValid)
        assertFalse(base.copy(pin = "1234", pinConfirmation = "4321").isValid)
        assertFalse(base.copy(pin = "12a4", pinConfirmation = "12a4").isValid)
        assertTrue(base.copy(pin = "1234", pinConfirmation = "1234").isValid)
        assertTrue(base.copy(pin = "12345678", pinConfirmation = "12345678").isValid)
        assertFalse(base.copy(pin = "123456789", pinConfirmation = "123456789").isValid)
    }

    @Test fun incorrectPinKeepsRequestAwaitingAndAllowsRetry() {
        val fixture = fixture()
        val action = fixture.beginDeactivation()

        assertEquals(PinVerificationResult.Incorrect, fixture.store.verifyPin(action.id, "9999".toCharArray()))
        assertEquals(StrictModeAuthorizationStatus.AwaitingAuthorization, fixture.store.activePendingAction(GlobalStrictModeStore.GLOBAL_CONFIGURATION_ID)?.authorizationStatus)
        assertTrue(fixture.store.verifyPin(action.id, "2468".toCharArray()) is PinVerificationResult.Verified)
    }

    @Test fun correctPinStartsExistingCountdownAndAutomaticallyDisablesAtZero() {
        val fixture = fixture()
        val action = fixture.beginDeactivation()
        val verified = fixture.store.verifyPin(action.id, "2468".toCharArray()) as PinVerificationResult.Verified

        assertTrue(verified.countdownStarted)
        assertEquals(RuleStrictModeLifecycle.DeactivationCounting, fixture.store.globalConfiguration()?.lifecycle)
        assertEquals(StrictModeAuthorizationStatus.Authorized, fixture.store.activePendingAction(GlobalStrictModeStore.GLOBAL_CONFIGURATION_ID)?.authorizationStatus)

        fixture.clock.now += WAIT
        assertEquals(RuleStrictModeLifecycle.DeactivationReady, fixture.store.globalConfiguration()?.lifecycle)
        assertTrue(fixture.store.completePinDeactivationIfReady() is PendingActionValidation.Valid)
        assertEquals(RuleStrictModeLifecycle.Disabled, fixture.store.globalConfiguration()?.lifecycle)
    }

    @Test fun cancellingCountdownRequiresPinAgain() {
        val fixture = fixture()
        val first = fixture.beginDeactivation()
        fixture.store.verifyPin(first.id, "2468".toCharArray())
        fixture.store.cancelGlobalDeactivation()

        val second = fixture.beginDeactivation()
        assertTrue(second.id != first.id)
        assertEquals(StrictModeAuthorizationStatus.AwaitingAuthorization, second.authorizationStatus)
        assertEquals(RuleStrictModeLifecycle.Active, fixture.store.globalConfiguration()?.lifecycle)
    }

    private fun fixture(): Fixture {
        val clock = Clock()
        val pinStore = FakePinStore("2468")
        val store = GlobalStrictModeStore(
            persistence = MemoryPersistence(),
            pinStore = pinStore,
            nowMillis = { clock.now }
        )
        store.migrateToGlobal(StrictModeState())
        store.requestGlobalActivation(StrictModeProtectionMethod.Pin, 0L, WAIT)
        store.globalConfiguration()
        return Fixture(store, clock)
    }

    private data class Fixture(val store: GlobalStrictModeStore, val clock: Clock) {
        fun beginDeactivation(): PendingStrictModeAction = when (val result = store.beginGlobalDeactivation()) {
            is PendingActionCreationResult.Created -> result.action
            is PendingActionCreationResult.AlreadyPending -> result.action
            is PendingActionCreationResult.Rejected -> error(result.message)
        }
    }

    private class FakePinStore(private var pin: String?) : StrictModePinStore {
        override fun hasPin() = pin != null
        override fun save(pin: CharArray): Boolean { this.pin = pin.concatToString(); return true }
        override fun verify(pin: CharArray) = this.pin == pin.concatToString()
    }

    private class Clock(var now: Long = 1_000L)
    private class MemoryPersistence : StrictModeFoundationPersistence {
        var configurations: String? = null
        var pending: String? = null
        override fun readConfigurations() = configurations
        override fun writeConfigurations(value: String) { configurations = value }
        override fun readPendingActions() = pending
        override fun writePendingActions(value: String) { pending = value }
    }

    private companion object { const val WAIT = 60_000L }
}
