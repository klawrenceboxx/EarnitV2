package com.kaleel.earnitv2

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kaleel.earnitv2.ui.theme.EarnitV2Theme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EarnItOnboardingTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun valueScreenExplainsProductAndAdvancesOnce() {
        var continues = 0
        setScreen(step = OnboardingStep.Value, onContinue = { continues++ })

        composeRule.onNodeWithText("Do the work.\nEarn the time.").assertIsDisplayed()
        composeRule.onNodeWithText("See how it works").performClick()

        assertEquals(1, continues)
    }

    @Test
    fun permissionIntroductionAllowsIntentionalPostpone() {
        var postponed = 0
        setScreen(step = OnboardingStep.PermissionIntroduction, onNotNow = { postponed++ })

        composeRule.onNodeWithText("EarnIt needs two permissions").assertIsDisplayed()
        composeRule.onNodeWithText("Not now").performClick()

        assertEquals(1, postponed)
    }

    @Test
    fun deniedUsageRationaleKeepsSettingsActionAvailable() {
        var settingsLaunches = 0
        setScreen(
            step = OnboardingStep.EarningRationale,
            permissions = OnboardingPermissionState(false, false),
            onOpenUsage = { settingsLaunches++ }
        )

        composeRule.onNodeWithText("Permission not enabled yet").assertIsDisplayed()
        composeRule.onNodeWithText("EarnIt cannot count time in your Earn Apps until you allow usage access.").assertIsDisplayed()
        composeRule.onNodeWithText("Open Android Settings").performClick()

        assertEquals(1, settingsLaunches)
    }

    @Test
    fun readyScreenOffersExistingRuleBuilderAndHomeDestinations() {
        setScreen(step = OnboardingStep.Ready, permissions = OnboardingPermissionState(true, true))

        composeRule.onNodeWithText("EarnIt is ready").assertIsDisplayed()
        composeRule.onNodeWithText("Create First Rule").assertIsDisplayed()
        composeRule.onNodeWithText("Go to Home").assertIsDisplayed()
    }

    private fun setScreen(
        step: OnboardingStep,
        permissions: OnboardingPermissionState = OnboardingPermissionState(false, false),
        onContinue: () -> Unit = {},
        onNotNow: () -> Unit = {},
        onOpenUsage: () -> Unit = {}
    ) {
        composeRule.setContent {
            EarnitV2Theme(dynamicColor = false) {
                EarnItOnboarding(
                    currentStep = step,
                    permissions = permissions,
                    onBack = {},
                    onContinue = onContinue,
                    onNotNow = onNotNow,
                    onOpenUsageAccessSettings = onOpenUsage,
                    onOpenAccessibilitySettings = {},
                    onCreateFirstRule = {},
                    onGoHome = {}
                )
            }
        }
    }
}
