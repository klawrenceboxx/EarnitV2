package com.example.earnitv2

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
