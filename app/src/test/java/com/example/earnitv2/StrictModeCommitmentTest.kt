package com.example.earnitv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictModeCommitmentTest {
    @Test
    fun fixedCommitmentPresetsMapToExactDurations() {
        assertEquals(60L * 60_000L, StrictModeCommitmentPreset.OneHour.durationMillis())
        assertEquals(24L * 60L * 60_000L, StrictModeCommitmentPreset.TwentyFourHours.durationMillis())
        assertEquals(7L * 24L * 60L * 60_000L, StrictModeCommitmentPreset.SevenDays.durationMillis())
    }

    @Test
    fun customCommitmentPreservesTheExistingCustomDuration() {
        val customDuration = 13L * 60L * 60_000L

        assertEquals(customDuration, StrictModeCommitmentPreset.Custom.durationMillis(customDuration))
        assertNull(StrictModeCommitmentPreset.Custom.durationMillis())
    }

    @Test
    fun fixedToCustomClearsTheFixedSelectionAndOpensARequiredCustomValue() {
        val fixed = setup().selectCommitmentPreset(StrictModeCommitmentPreset.OneHour)

        val custom = fixed.selectCommitmentPreset(StrictModeCommitmentPreset.Custom)

        assertEquals(StrictModeCommitmentPreset.Custom, custom.selectedCommitmentPreset)
        assertNull(custom.timedDurationMillis)
        assertEquals("", custom.customTimedHours)
        assertTrue(custom.timedCustomVisible)
        assertFalse(custom.isValid)

        val applied = custom.copy(
            timedDurationMillis = 2L * 60L * 60_000L,
            customTimedHours = "2"
        )
        val restored = StrictModeSetupState.from(applied.toConfiguration())
        assertEquals(StrictModeCommitmentPreset.Custom, restored.selectedCommitmentPreset)
        assertEquals("2", restored.customTimedHours)
    }

    @Test
    fun reopeningAnAppliedCustomCommitmentPreservesItsAcceptedValue() {
        val applied = setup()
            .selectCommitmentPreset(StrictModeCommitmentPreset.Custom)
            .copy(
                timedDurationMillis = 3L * 60L * 60_000L,
                customTimedHours = "3",
                timedCustomVisible = false
            )

        val reopened = applied.selectCommitmentPreset(StrictModeCommitmentPreset.Custom)

        assertEquals(3L * 60L * 60_000L, reopened.timedDurationMillis)
        assertEquals("3", reopened.customTimedHours)
        assertTrue(reopened.timedCustomVisible)
    }

    @Test
    fun indefiniteToFixedAndCustomHaveDistinctSelections() {
        val setup = StrictModeSetupState(
            durationType = StrictModeDurationType.Indefinite,
            timedDurationMillis = null,
            deactivationCountdownMillis = 10L * 60_000L
        )

        assertNull(setup.selectedCommitmentPreset)

        val fixed = setup.selectCommitmentPreset(StrictModeCommitmentPreset.TwentyFourHours)
        assertEquals(StrictModeDurationType.Timed, fixed.durationType)
        assertEquals(StrictModeCommitmentPreset.TwentyFourHours, fixed.selectedCommitmentPreset)
        assertFalse(fixed.timedCustomVisible)
        assertEquals(
            StrictModeCommitmentPreset.TwentyFourHours,
            StrictModeSetupState.from(fixed.toConfiguration()).selectedCommitmentPreset
        )

        val custom = setup.selectCommitmentPreset(StrictModeCommitmentPreset.Custom)
        assertEquals(StrictModeDurationType.Timed, custom.durationType)
        assertEquals(StrictModeCommitmentPreset.Custom, custom.selectedCommitmentPreset)
        assertTrue(custom.timedCustomVisible)
        val appliedCustom = custom.copy(
            timedDurationMillis = 5L * 60L * 60_000L,
            customTimedHours = "5"
        )
        assertEquals(
            StrictModeCommitmentPreset.Custom,
            StrictModeSetupState.from(appliedCustom.toConfiguration()).selectedCommitmentPreset
        )
    }

    @Test
    fun togglingIndefinitePreservesThePreviousFixedCommitment() {
        val fixed = setup().selectCommitmentPreset(StrictModeCommitmentPreset.TwentyFourHours)

        val restored = fixed
            .copy(durationType = StrictModeDurationType.Indefinite)
            .returnToTimedCommitment()

        assertEquals(StrictModeDurationType.Timed, restored.durationType)
        assertEquals(StrictModeCommitmentPreset.TwentyFourHours, restored.selectedCommitmentPreset)
        assertEquals(fixed.timedDurationMillis, restored.timedDurationMillis)
    }

    @Test
    fun togglingIndefinitePreservesThePreviousAppliedCustomCommitment() {
        val custom = setup()
            .selectCommitmentPreset(StrictModeCommitmentPreset.Custom)
            .copy(
                timedDurationMillis = 5L * 60L * 60_000L,
                customTimedHours = "5"
            )

        val restored = custom
            .copy(durationType = StrictModeDurationType.Indefinite)
            .returnToTimedCommitment()

        assertEquals(StrictModeCommitmentPreset.Custom, restored.selectedCommitmentPreset)
        assertEquals("5", restored.customTimedHours)
        assertEquals(5L * 60L * 60_000L, restored.timedDurationMillis)
        assertTrue(restored.isValid)
    }

    @Test
    fun legacyFixedDurationsRestoreAsUsableCustomSelections() {
        listOf(4L, 8L, 72L).forEach { legacyHours ->
            val legacyDurationMillis = legacyHours * 60L * 60_000L

            val restored = StrictModeSetupState.from(
                StrictModeConfiguration(
                    durationType = StrictModeDurationType.Timed,
                    timedDurationMillis = legacyDurationMillis
                )
            )

            assertEquals(StrictModeCommitmentPreset.Custom, restored.selectedCommitmentPreset)
            assertTrue(restored.timedCustomVisible)
            assertEquals(legacyHours.toString(), restored.customTimedHours)
            assertEquals(legacyDurationMillis, restored.toConfiguration().timedDurationMillis)
            assertTrue(restored.isValid)
        }
    }

    @Test
    fun approvedPresetEnumHasOneFixedSourceOfTruth() {
        assertEquals(
            listOf(
                StrictModeCommitmentPreset.OneHour to 60L * 60_000L,
                StrictModeCommitmentPreset.TwentyFourHours to 24L * 60L * 60_000L,
                StrictModeCommitmentPreset.SevenDays to 7L * 24L * 60L * 60_000L
            ),
            StrictModeCommitmentPreset.entries.mapNotNull { preset ->
                preset.durationMillis()?.let { preset to it }
            }
        )
    }

    @Test
    fun persistedDurationsRestoreToTheMatchingCommitmentPreset() {
        assertEquals(
            StrictModeCommitmentPreset.OneHour,
            strictModeCommitmentPresetFor(60L * 60_000L)
        )
        assertEquals(
            StrictModeCommitmentPreset.TwentyFourHours,
            strictModeCommitmentPresetFor(24L * 60L * 60_000L)
        )
        assertEquals(
            StrictModeCommitmentPreset.SevenDays,
            strictModeCommitmentPresetFor(7L * 24L * 60L * 60_000L)
        )
        assertEquals(
            StrictModeCommitmentPreset.Custom,
            strictModeCommitmentPresetFor(13L * 60L * 60_000L)
        )
    }

    @Test
    fun fixedPresetsUseExistingActivationAndExpirationArchitecture() {
        StrictModeCommitmentPreset.entries
            .filter { it != StrictModeCommitmentPreset.Custom }
            .forEach { preset ->
                var now = 1_000L
                val store = StrictModeStore(
                    persistence = MemoryPersistence(),
                    nowMillis = { now }
                )
                val durationMillis = requireNotNull(preset.durationMillis())

                store.beginActivation(
                    StrictModeConfiguration(
                        durationType = StrictModeDurationType.Timed,
                        timedDurationMillis = durationMillis
                    )
                )
                now += StrictModeStore.ACTIVATION_GRACE_MILLIS

                val active = store.state()
                assertEquals(StrictModeLifecycleState.Active, active.lifecycleState)
                assertEquals(now, active.activatedAtMillis)
                assertEquals(now + durationMillis, active.expiresAtMillis)
            }
    }

    @Test
    fun selectedPresetConfigurationAndExpirationSurviveStoreRecreation() {
        var now = 1_000L
        val persistence = MemoryPersistence()
        val durationMillis = requireNotNull(StrictModeCommitmentPreset.SevenDays.durationMillis())
        StrictModeStore(persistence = persistence, nowMillis = { now }).beginActivation(
            StrictModeConfiguration(
                durationType = StrictModeDurationType.Timed,
                timedDurationMillis = durationMillis
            )
        )
        now += StrictModeStore.ACTIVATION_GRACE_MILLIS

        val restored = StrictModeStore(persistence = persistence, nowMillis = { now }).state()

        assertEquals(durationMillis, restored.configuration.timedDurationMillis)
        assertEquals(StrictModeCommitmentPreset.SevenDays, strictModeCommitmentPresetFor(restored.configuration.timedDurationMillis))
        assertEquals(now + durationMillis, restored.expiresAtMillis)
    }

    @Test
    fun untilTurnedOffStillProducesNoAutomaticExpiration() {
        var now = 1_000L
        val store = StrictModeStore(
            persistence = MemoryPersistence(),
            nowMillis = { now }
        )

        store.beginActivation(
            StrictModeConfiguration(
                durationType = StrictModeDurationType.Indefinite,
                timedDurationMillis = null
            )
        )
        now += StrictModeStore.ACTIVATION_GRACE_MILLIS

        val active = store.state()
        assertEquals(StrictModeLifecycleState.Active, active.lifecycleState)
        assertNull(active.expiresAtMillis)
        now += StrictModeStore.MAX_TIMED_DURATION_MILLIS * 2L
        assertTrue(store.state().lifecycleState == StrictModeLifecycleState.Active)
    }

    private fun setup(): StrictModeSetupState {
        return StrictModeSetupState(
            durationType = StrictModeDurationType.Timed,
            timedDurationMillis = 60L * 60_000L,
            deactivationCountdownMillis = 10L * 60_000L
        )
    }

    private class MemoryPersistence : StrictModePersistence {
        private val values = mutableMapOf<String, String>()

        override fun read(key: String): String? = values[key]

        override fun write(values: Map<String, String?>) {
            values.forEach { (key, value) ->
                if (value == null) this.values.remove(key) else this.values[key] = value
            }
        }
    }
}
