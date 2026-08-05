package com.kaleel.earnitv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictModeStoreTest {
    @Test
    fun defaultStateIsInactive() {
        val store = store()

        assertEquals(StrictModeLifecycleState.Inactive, store.state().lifecycleState)
    }

    @Test
    fun timedConfigurationPersistsCorrectly() {
        val persistence = MemoryStrictModePersistence()
        val store = store(persistence = persistence)
        val configuration = StrictModeConfiguration(
            durationType = StrictModeDurationType.Timed,
            timedDurationMillis = 4 * 60 * 60_000L,
            deactivationCountdownMillis = 30 * 60_000L
        )

        store.saveConfiguration(configuration)

        assertEquals(configuration, store(persistence = persistence).state().configuration)
    }

    @Test
    fun indefiniteConfigurationPersistsCorrectly() {
        val persistence = MemoryStrictModePersistence()
        val configuration = StrictModeConfiguration(
            durationType = StrictModeDurationType.Indefinite,
            timedDurationMillis = null,
            deactivationCountdownMillis = 60 * 60_000L
        )

        store(persistence = persistence).saveConfiguration(configuration)

        val restored = store(persistence = persistence).state().configuration
        assertEquals(StrictModeDurationType.Indefinite, restored.durationType)
        assertEquals(60 * 60_000L, restored.deactivationCountdownMillis)
    }

    @Test
    fun activationEntersActivatingState() {
        val store = store(now = 1_000L)

        val state = store.beginActivation(validTimedConfiguration())

        assertEquals(StrictModeLifecycleState.Activating, state.lifecycleState)
        assertEquals(1_000L, state.activationGraceStartedAtMillis)
        assertEquals(31_000L, state.activationGraceEndsAtMillis)
    }

    @Test
    fun cancellingActivationReturnsToInactive() {
        val store = store(now = 1_000L)
        store.beginActivation(validTimedConfiguration())

        val state = store.cancelActivation()

        assertEquals(StrictModeLifecycleState.Inactive, state.lifecycleState)
    }

    @Test
    fun activationCompletesAfterPersistedGracePeriodAndCalculatesExpiration() {
        var now = 1_000L
        val persistence = MemoryStrictModePersistence()
        val store = store(persistence = persistence, nowProvider = { now })

        store.beginActivation(validTimedConfiguration(durationMillis = 60_000L))
        now = 31_000L

        val active = store.state()
        assertEquals(StrictModeLifecycleState.Active, active.lifecycleState)
        assertEquals(31_000L, active.activatedAtMillis)
        assertEquals(91_000L, active.expiresAtMillis)
    }

    @Test
    fun delayedRefreshActivatesFromPersistedDeadlineWithoutResettingOrExtendingDuration() {
        var now = 1_000L
        val store = store(nowProvider = { now })
        store.beginActivation(validTimedConfiguration(durationMillis = 60_000L))

        now = 36_000L
        val active = store.state()

        assertEquals(StrictModeLifecycleState.Active, active.lifecycleState)
        assertEquals(31_000L, active.activatedAtMillis)
        assertEquals(91_000L, active.expiresAtMillis)
    }

    @Test
    fun timedModeExpiresAutomatically() {
        var now = 1_000L
        val store = store(nowProvider = { now })
        store.beginActivation(validTimedConfiguration(durationMillis = 60_000L))
        now = 92_000L

        assertEquals(StrictModeLifecycleState.Inactive, store.state().lifecycleState)
    }

    @Test
    fun indefiniteModeDoesNotExpireAutomatically() {
        var now = 1_000L
        val store = store(nowProvider = { now })
        store.beginActivation(
            StrictModeConfiguration(
                durationType = StrictModeDurationType.Indefinite,
                timedDurationMillis = null,
                deactivationCountdownMillis = 10 * 60_000L
            )
        )
        now = 10_000_000L

        assertEquals(StrictModeLifecycleState.Active, store.state().lifecycleState)
    }

    @Test
    fun stateSurvivesStoreRecreation() {
        val persistence = MemoryStrictModePersistence()

        store(persistence = persistence, now = 1_000L).beginActivation(validTimedConfiguration())

        assertEquals(StrictModeLifecycleState.Activating, store(persistence = persistence, now = 2_000L).state().lifecycleState)
    }

    @Test
    fun invalidConfigurationsCannotActivate() {
        val store = store()
        val invalidTimed = StrictModeConfiguration(
            durationType = StrictModeDurationType.Timed,
            timedDurationMillis = 0L,
            deactivationCountdownMillis = 10 * 60_000L
        )
        val invalidCountdown = StrictModeConfiguration(
            durationType = StrictModeDurationType.Indefinite,
            timedDurationMillis = null,
            deactivationCountdownMillis = 0L
        )

        assertFalse(store.isValidConfiguration(invalidTimed))
        assertFalse(store.isValidConfiguration(invalidCountdown))
        assertTrue(store.isValidConfiguration(validTimedConfiguration()))
    }

    @Test
    fun excessiveDurationsAreRejected() {
        val store = store()
        val tooLongTimed = StrictModeConfiguration(
            durationType = StrictModeDurationType.Timed,
            timedDurationMillis = StrictModeStore.MAX_TIMED_DURATION_MILLIS + 1L,
            deactivationCountdownMillis = 10 * 60_000L
        )
        val tooLongCountdown = StrictModeConfiguration(
            durationType = StrictModeDurationType.Indefinite,
            timedDurationMillis = null,
            deactivationCountdownMillis = StrictModeStore.MAX_DEACTIVATION_COUNTDOWN_MILLIS + 1L
        )

        assertFalse(store.isValidConfiguration(tooLongTimed))
        assertFalse(store.isValidConfiguration(tooLongCountdown))
    }

    @Test
    fun unsupportedPersistedDeactivationMethodFallsBackToCountdown() {
        val persistence = MemoryStrictModePersistence()
        persistence.write(
            mapOf(
                "deactivation_method" to StrictModeDeactivationMethod.Pin.name,
                "deactivation_countdown_millis" to (10 * 60_000L).toString()
            )
        )

        val configuration = store(persistence = persistence).state().configuration

        assertEquals(StrictModeDeactivationMethod.Countdown, configuration.deactivationMethod)
        assertTrue(store(persistence = persistence).isValidConfiguration(configuration))
    }

    @Test
    fun invalidPersistedLifecycleRecoversToInactive() {
        val persistence = MemoryStrictModePersistence()
        persistence.write(mapOf("lifecycle" to "RemovedLifecycle"))

        assertEquals(StrictModeLifecycleState.Inactive, store(persistence = persistence).state().lifecycleState)
    }

    @Test
    fun invalidPersistedConfigurationClearsTransientState() {
        val persistence = MemoryStrictModePersistence()
        persistence.write(
            mapOf(
                "lifecycle" to StrictModeLifecycleState.Active.name,
                "duration_type" to StrictModeDurationType.Timed.name,
                "timed_duration_millis" to "0",
                "deactivation_countdown_millis" to (10 * 60_000L).toString(),
                "activated_at" to "31",
                "expires_at" to "91"
            )
        )

        val state = store(persistence = persistence).state()

        assertEquals(StrictModeLifecycleState.Inactive, state.lifecycleState)
        assertEquals(null, state.activatedAtMillis)
        assertEquals(null, state.expiresAtMillis)
    }

    @Test
    fun invalidPersistedTimestampsDoNotCrashAndFailSafely() {
        val persistence = MemoryStrictModePersistence()
        persistence.write(
            mapOf(
                "lifecycle" to StrictModeLifecycleState.DeactivationCounting.name,
                "duration_type" to StrictModeDurationType.Indefinite.name,
                "timed_duration_millis" to null,
                "deactivation_countdown_millis" to (10 * 60_000L).toString(),
                "deactivation_started_at" to "600000",
                "deactivation_available_at" to "1000"
            )
        )

        val state = store(persistence = persistence).state()

        assertEquals(StrictModeLifecycleState.Active, state.lifecycleState)
        assertEquals(null, state.deactivationStartedAtMillis)
        assertEquals(null, state.deactivationAvailableAtMillis)
    }

    @Test
    fun beginDeactivationEntersPendingCountdownStateAndPersistsTimestamps() {
        var now = 1_000L
        val persistence = MemoryStrictModePersistence()
        val store = store(persistence = persistence, nowProvider = { now })
        store.beginActivation(validTimedConfiguration())
        now = 31_000L
        store.state()

        val state = store.beginDeactivation()

        assertEquals(StrictModeLifecycleState.DeactivationCounting, state.lifecycleState)
        assertEquals(31_000L, state.deactivationStartedAtMillis)
        assertEquals(631_000L, state.deactivationAvailableAtMillis)
        assertEquals(StrictModeLifecycleState.DeactivationCounting, store(persistence = persistence, now = 32_000L).state().lifecycleState)
    }

    @Test
    fun deactivationCountdownSurvivesRecreationAndDoesNotDeactivateAutomatically() {
        var now = 1_000L
        val persistence = MemoryStrictModePersistence()
        val store = store(persistence = persistence, nowProvider = { now })
        store.beginActivation(validTimedConfiguration())
        now = 31_000L
        store.state()
        store.beginDeactivation()

        now = 700_000L
        val restored = store(persistence = persistence, nowProvider = { now }).state()

        assertEquals(StrictModeLifecycleState.DeactivationReady, restored.lifecycleState)
    }

    @Test
    fun cancellingDeactivationClearsPendingStateAndPreservesTimedExpiration() {
        val persistence = MemoryStrictModePersistence()
        var now = 1_000L
        val store = store(persistence = persistence, nowProvider = { now })
        store.beginActivation(validTimedConfiguration())
        now = 31_000L
        store.state()
        val originalExpiration = store.state().expiresAtMillis
        store.beginDeactivation()

        val cancelled = store.cancelDeactivation()

        assertEquals(StrictModeLifecycleState.Active, cancelled.lifecycleState)
        assertEquals(originalExpiration, cancelled.expiresAtMillis)
        assertEquals(null, cancelled.deactivationStartedAtMillis)
        assertEquals(null, cancelled.deactivationAvailableAtMillis)
    }

    @Test
    fun completedCountdownRequiresFinalConfirmationToDeactivate() {
        var now = 1_000L
        val store = store(nowProvider = { now })
        store.beginActivation(validTimedConfiguration())
        now = 31_000L
        store.state()
        store.beginDeactivation()
        now = 700_000L

        val ready = store.state()
        assertEquals(StrictModeLifecycleState.DeactivationReady, ready.lifecycleState)

        val inactive = store.confirmDeactivation()
        assertEquals(StrictModeLifecycleState.Inactive, inactive.lifecycleState)
        assertEquals(null, inactive.activatedAtMillis)
        assertEquals(null, inactive.expiresAtMillis)
    }

    @Test
    fun keepStrictModeActiveClearsCompletedRequestAndRequiresNewCountdown() {
        var now = 1_000L
        val store = store(nowProvider = { now })
        store.beginActivation(validTimedConfiguration())
        now = 31_000L
        store.state()
        store.beginDeactivation()
        now = 700_000L
        assertEquals(StrictModeLifecycleState.DeactivationReady, store.state().lifecycleState)

        val active = store.keepStrictModeActive()

        assertEquals(StrictModeLifecycleState.Active, active.lifecycleState)
        assertEquals(null, active.deactivationStartedAtMillis)
        assertEquals(null, active.deactivationAvailableAtMillis)
    }

    @Test
    fun timedStrictModeExpiryClearsRunningDeactivation() {
        var now = 1_000L
        val store = store(nowProvider = { now })
        store.beginActivation(validTimedConfiguration(durationMillis = 60_000L))
        now = 31_000L
        store.state()
        store.beginDeactivation()
        now = 100_000L

        assertEquals(StrictModeLifecycleState.Inactive, store.state().lifecycleState)
    }

    @Test
    fun timedStrictModeExpiryOverridesStaleFinalConfirmation() {
        var now = 1_000L
        val store = store(nowProvider = { now })
        store.beginActivation(validTimedConfiguration(durationMillis = 20 * 60_000L))
        now = 31_000L
        store.state()
        store.beginDeactivation()
        now = 700_000L
        assertEquals(StrictModeLifecycleState.DeactivationReady, store.state().lifecycleState)
        now = 1_300_001L

        assertEquals(StrictModeLifecycleState.Inactive, store.state().lifecycleState)
    }

    @Test
    fun indefiniteStrictModeStaysActiveUntilConfirmation() {
        var now = 1_000L
        val persistence = MemoryStrictModePersistence()
        val store = store(persistence = persistence, nowProvider = { now })
        store.beginActivation(
            StrictModeConfiguration(
                durationType = StrictModeDurationType.Indefinite,
                timedDurationMillis = null,
                deactivationCountdownMillis = 10 * 60_000L
            )
        )
        now = 31_000L
        store.beginDeactivation()
        now = 999_999_999L

        assertEquals(StrictModeLifecycleState.DeactivationReady, store.state().lifecycleState)
        assertEquals(StrictModeLifecycleState.Inactive, store.confirmDeactivation().lifecycleState)
    }

    @Test
    fun repeatedStartCountdownDoesNotCreateMultipleRequests() {
        var now = 1_000L
        val store = store(nowProvider = { now })
        store.beginActivation(validTimedConfiguration())
        now = 31_000L
        store.state()
        val first = store.beginDeactivation()
        val second = store.beginDeactivation()

        assertEquals(first.deactivationStartedAtMillis, second.deactivationStartedAtMillis)
        assertEquals(first.deactivationAvailableAtMillis, second.deactivationAvailableAtMillis)
    }

    @Test
    fun repeatedCancelAndConfirmOperationsAreIdempotent() {
        var now = 1_000L
        val store = store(nowProvider = { now })
        store.beginActivation(validTimedConfiguration())

        val firstCancel = store.cancelActivation()
        val secondCancel = store.cancelActivation()

        assertEquals(firstCancel, secondCancel)

        store.beginActivation(validTimedConfiguration())
        now = 31_000L
        store.state()
        store.beginDeactivation()
        now = 700_000L
        store.state()

        val firstConfirm = store.confirmDeactivation()
        val secondConfirm = store.confirmDeactivation()

        assertEquals(firstConfirm, secondConfirm)
    }

    private fun validTimedConfiguration(durationMillis: Long = 60 * 60_000L): StrictModeConfiguration {
        return StrictModeConfiguration(
            durationType = StrictModeDurationType.Timed,
            timedDurationMillis = durationMillis,
            deactivationCountdownMillis = 10 * 60_000L
        )
    }

    private fun store(
        persistence: MemoryStrictModePersistence = MemoryStrictModePersistence(),
        now: Long = 0L,
        nowProvider: () -> Long = { now }
    ): StrictModeStore {
        return StrictModeStore(persistence = persistence, nowMillis = nowProvider)
    }

    private class MemoryStrictModePersistence : StrictModePersistence {
        private val values = mutableMapOf<String, String>()

        override fun read(key: String): String? = values[key]

        override fun write(values: Map<String, String?>) {
            values.forEach { (key, value) ->
                if (value == null) this.values.remove(key) else this.values[key] = value
            }
        }
    }
}
