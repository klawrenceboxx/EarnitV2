package com.kaleel.earnitv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
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
    fun editingAppliedCustomCommitmentInvalidatesTheOldDurationUntilUse() {
        val applied = StrictModeSetupState(
            durationType = StrictModeDurationType.Timed,
            timedDurationMillis = 5L * 60L * 60_000L,
            deactivationCountdownMillis = 10L * 60_000L,
            customTimedHours = "5",
            timedCustomVisible = true
        )

        val draft = applied.editCustomCommitmentDraft("6")

        assertEquals("6", draft.customTimedHours)
        assertNull(draft.timedDurationMillis)
        assertFalse(draft.isValid)
        assertNull(draft.toConfiguration().timedDurationMillis)

        val reapplied = draft.copy(timedDurationMillis = 6L * 60L * 60_000L)
        assertTrue(reapplied.isValid)
    }

    @Test
    fun choosingOrEditingCustomCountdownInvalidatesTheOldWaitUntilUse() {
        val fixed = StrictModeSetupState(
            durationType = StrictModeDurationType.Timed,
            timedDurationMillis = 60L * 60_000L,
            deactivationCountdownMillis = 10L * 60_000L
        )

        val custom = fixed.chooseCustomCountdown()
        assertTrue(custom.countdownCustomVisible)
        assertNull(custom.deactivationCountdownMillis)
        assertFalse(custom.isValid)

        val draft = custom.editCustomCountdownDraft("45")
        assertEquals("45", draft.customCountdownMinutes)
        assertNull(draft.deactivationCountdownMillis)
        assertFalse(draft.isValid)

        val reapplied = draft.copy(deactivationCountdownMillis = 45L * 60_000L)
        assertTrue(reapplied.isValid)
    }

    @Test
    fun durationFormattingIsReadableForCountdownsAndMixedTime() {
        assertEquals("Less than 1 minute", durationLabel(30_000L))
        assertEquals("1 minute", durationLabel(60_000L))
        assertEquals("1 hour 30 minutes", durationLabel(90 * 60_000L))
        assertEquals("1 day 2 hours", durationLabel((24 * 60 + 120) * 60_000L))
    }

    @Test
    fun activationCountdownUsesCeilingSecondsUntilThePersistedDeadline() {
        val deadline = 31_000L

        assertEquals(30L, strictModeRemainingSeconds(deadline, nowMillis = 1_000L))
        assertEquals(1L, strictModeRemainingSeconds(deadline, nowMillis = 30_999L))
        assertEquals(0L, strictModeRemainingSeconds(deadline, nowMillis = 31_000L))
        assertEquals(0L, strictModeRemainingSeconds(deadline, nowMillis = 40_000L))
    }

    @Test
    fun appliedCustomCommitmentHasPersistentChipConfirmationWhileInputIsCollapsed() {
        val setup = StrictModeSetupState(
            durationType = StrictModeDurationType.Timed,
            timedDurationMillis = 3L * 60L * 60_000L,
            deactivationCountdownMillis = 10L * 60_000L,
            customTimedHours = "3",
            timedCustomVisible = false
        )

        assertEquals(StrictModeCommitmentPreset.Custom, setup.selectedCommitmentPreset)
        assertEquals("Custom (3 hours)", customCommitmentChipLabel(setup))
        assertEquals("\u2713 Custom duration set to 3 hours", customDurationConfirmationMessage(setup.timedDurationMillis!!))
        assertFalse(setup.timedCustomVisible)
    }

    @Test
    fun settingsBadgeDistinguishesActivationGraceFromActive() {
        assertEquals("Off", strictModeSettingsBadge(StrictModeLifecycleState.Inactive))
        assertEquals("Activating", strictModeSettingsBadge(StrictModeLifecycleState.Activating))
        assertEquals("Active", strictModeSettingsBadge(StrictModeLifecycleState.Active))
        assertEquals("Active", strictModeSettingsBadge(StrictModeLifecycleState.DeactivationCounting))
        assertEquals("Active", strictModeSettingsBadge(StrictModeLifecycleState.DeactivationReady))
    }
}
