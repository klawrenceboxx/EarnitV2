package com.example.earnitv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictModeUiStateTest {
    @Test
    fun timedControlsAreNotRequiredForIndefiniteMode() {
        val setup = StrictModeSetupState(
            durationType = StrictModeDurationType.Indefinite,
            timedDurationMillis = null,
            deactivationCountdownMillis = 10 * 60_000L
        )

        assertTrue(setup.isValid)
        assertEquals(null, setup.toConfiguration().timedDurationMillis)
    }

    @Test
    fun invalidCustomValuesBlockReview() {
        val zeroTimed = StrictModeSetupState(
            durationType = StrictModeDurationType.Timed,
            timedDurationMillis = 0L,
            deactivationCountdownMillis = 10 * 60_000L
        )
        val oversizedCountdown = StrictModeSetupState(
            durationType = StrictModeDurationType.Indefinite,
            timedDurationMillis = null,
            deactivationCountdownMillis = StrictModeStore.MAX_DEACTIVATION_COUNTDOWN_MILLIS + 1L
        )

        assertFalse(zeroTimed.isValid)
        assertFalse(oversizedCountdown.isValid)
    }

    @Test
    fun customConfigurationRestoresCustomControls() {
        val setup = StrictModeSetupState.from(
            StrictModeConfiguration(
                durationType = StrictModeDurationType.Timed,
                timedDurationMillis = 2 * 60 * 60_000L,
                deactivationCountdownMillis = 45 * 60_000L
            )
        )

        assertTrue(setup.timedCustomVisible)
        assertTrue(setup.countdownCustomVisible)
        assertEquals("2", setup.customTimedHours)
        assertEquals("45", setup.customCountdownMinutes)
    }

    @Test
    fun durationFormattingIsReadableForCountdownsAndMixedTime() {
        assertEquals("Less than 1 minute", durationLabel(30_000L))
        assertEquals("1 minute", durationLabel(60_000L))
        assertEquals("1 hour 30 minutes", durationLabel(90 * 60_000L))
        assertEquals("1 day 2 hours", durationLabel((24 * 60 + 120) * 60_000L))
    }
}
