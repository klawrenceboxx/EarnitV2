package com.kaleel.earnitv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EarnItOnboardingStateTest {
    private val denied = OnboardingPermissionState(false, false)
    private val usageOnly = OnboardingPermissionState(true, false)
    private val granted = OnboardingPermissionState(true, true)

    @Test
    fun freshInstallRoutesToOnboardingAndPersistsMigrationDecision() {
        val decision = onboardingMigrationDecision(
            storedSeen = null,
            legacySeen = false,
            hasDurablePriorUse = false
        )

        assertFalse(decision.seen)
        assertTrue(decision.shouldPersist)
    }

    @Test
    fun establishedUserSkipsOnboardingWithoutChangingData() {
        val decision = onboardingMigrationDecision(
            storedSeen = null,
            legacySeen = false,
            hasDurablePriorUse = true
        )

        assertTrue(decision.seen)
        assertTrue(decision.shouldPersist)
    }

    @Test
    fun legacyCompletedUserSkipsOnboarding() {
        assertTrue(
            onboardingMigrationDecision(
                storedSeen = null,
                legacySeen = true,
                hasDurablePriorUse = false
            ).seen
        )
    }

    @Test
    fun migrationIsIdempotentOnceSeenStateExists() {
        val existing = onboardingMigrationDecision(
            storedSeen = true,
            legacySeen = false,
            hasDurablePriorUse = false
        )
        val intentionallyIncomplete = onboardingMigrationDecision(
            storedSeen = false,
            legacySeen = true,
            hasDurablePriorUse = true
        )

        assertEquals(OnboardingMigrationDecision(true, false), existing)
        assertEquals(OnboardingMigrationDecision(false, false), intentionallyIncomplete)
    }

    @Test
    fun educationStagesMoveForwardAndBackInOrder() {
        assertEquals(OnboardingStep.Example, nextOnboardingStep(OnboardingStep.Value, denied))
        assertEquals(OnboardingStep.PermissionIntroduction, nextOnboardingStep(OnboardingStep.Example, denied))
        assertEquals(OnboardingStep.EarningRationale, nextOnboardingStep(OnboardingStep.PermissionIntroduction, denied))

        assertEquals(OnboardingStep.Example, previousOnboardingStep(OnboardingStep.PermissionIntroduction))
        assertEquals(OnboardingStep.Value, previousOnboardingStep(OnboardingStep.Example))
        assertNull(previousOnboardingStep(OnboardingStep.Value))
    }

    @Test
    fun alreadyGrantedUsageAccessIsRecognizedWithoutOpeningSettings() {
        assertEquals(
            OnboardingStep.EarningAllowed,
            nextOnboardingStep(OnboardingStep.PermissionIntroduction, usageOnly)
        )
    }

    @Test
    fun deniedUsageAccessRemainsIncomplete() {
        assertEquals(
            OnboardingStep.EarningRationale,
            nextOnboardingStep(OnboardingStep.EarningRationale, denied)
        )
    }

    @Test
    fun permissionResumeRecheckMovesToSuccessOnlyFromRealGrantedState() {
        assertEquals(
            OnboardingStep.EarningRationale,
            reconcileOnboardingStep(OnboardingStep.EarningRationale, denied)
        )
        assertEquals(
            OnboardingStep.EarningAllowed,
            reconcileOnboardingStep(OnboardingStep.EarningRationale, usageOnly)
        )
        assertEquals(
            OnboardingStep.BlockingAllowed,
            reconcileOnboardingStep(OnboardingStep.BlockingRationale, granted)
        )
    }

    @Test
    fun deniedAccessibilityAccessRemainsIncomplete() {
        assertEquals(
            OnboardingStep.BlockingRationale,
            nextOnboardingStep(OnboardingStep.BlockingRationale, usageOnly)
        )
    }

    @Test
    fun bothPermissionsUnlockReadyState() {
        assertEquals(
            OnboardingStep.Ready,
            nextOnboardingStep(OnboardingStep.BlockingAllowed, granted)
        )
    }

    @Test
    fun revokedPermissionDemotesReadyToFocusedRepairInsteadOfReplayingEducation() {
        assertEquals(
            OnboardingStep.BlockingRationale,
            reconcileOnboardingStep(OnboardingStep.Ready, usageOnly)
        )
        assertEquals(
            OnboardingStep.EarningRationale,
            reconcileOnboardingStep(OnboardingStep.Ready, denied)
        )
    }

    @Test
    fun focusedRepairAlwaysChoosesFirstMissingCapability() {
        assertEquals(OnboardingStep.EarningRationale, focusedRepairStep(denied))
        assertEquals(OnboardingStep.BlockingRationale, focusedRepairStep(usageOnly))
        assertEquals(OnboardingStep.Ready, focusedRepairStep(granted))
    }

    @Test
    fun intentionalExitActionsMarkEducationSeenAndRouteCorrectly() {
        assertEquals(
            OnboardingExitEffect(true, OnboardingExitDestination.RuleBuilder),
            onboardingExitEffect(OnboardingExitAction.CreateFirstRule)
        )
        assertEquals(
            OnboardingExitEffect(true, OnboardingExitDestination.Home),
            onboardingExitEffect(OnboardingExitAction.GoHome)
        )
        assertEquals(
            OnboardingExitEffect(true, OnboardingExitDestination.Home),
            onboardingExitEffect(OnboardingExitAction.NotNow)
        )
    }

    @Test
    fun accidentalBackOrProcessExitDoesNotMarkOnboardingSeen() {
        assertEquals(
            OnboardingExitEffect(false, null),
            onboardingExitEffect(OnboardingExitAction.AccidentalExit)
        )
    }

    @Test
    fun routingFunctionsArePureAndDoNotEmitDuplicateNavigation() {
        val first = nextOnboardingStep(OnboardingStep.PermissionIntroduction, denied)
        val recomposed = nextOnboardingStep(OnboardingStep.PermissionIntroduction, denied)

        assertEquals(first, recomposed)
        assertEquals(OnboardingStep.EarningRationale, first)
    }
}
