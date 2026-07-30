package com.example.earnitv2

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.earnitv2.ui.theme.EarnitV2Theme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PremiumScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun contextualGateUsesFeatureCopyAndActions() {
        var upgraded = false
        var dismissed = false
        compose.setContent {
            EarnitV2Theme {
                PremiumGateDialog(
                    entryPoint = PremiumEntryPoint.DeepWork,
                    onUpgrade = { upgraded = true },
                    onDismiss = { dismissed = true }
                )
            }
        }
        compose.onNodeWithText("Deep Work is part of EarnIt Pro").assertIsDisplayed()
        compose.onNodeWithContentDescription("Deep Work timer").assertIsDisplayed()
        compose.onNodeWithText("See Pro plans").performClick()
        compose.runOnIdle { assertTrue(upgraded) }
        compose.onNodeWithText("Maybe later").performClick()
        compose.runOnIdle { assertTrue(dismissed) }
    }

    @Test fun planScreenDefaultsAnnualAndCanSelectMonthly() {
        var flow = ProFlowState(ProRoute.Plans)
        val provider = LocalPurchaseProvider(
            SubscriptionConfig.Placeholder,
            object : DebugEntitlementController {
                override fun simulate(state: EntitlementState) = Unit
                override fun reset() = Unit
            },
            simulationEnabled = true
        )
        val purchaseState = provider.state.value
        compose.setContent {
            EarnitV2Theme {
                EarnItProScreen(
                    flow = flow,
                    config = SubscriptionConfig.Placeholder,
                    entitlement = EntitlementState.Free,
                    purchaseState = purchaseState,
                    onFlowChange = { flow = it },
                    onPurchase = {},
                    onRestore = {},
                    onClose = {},
                    modifier = Modifier.size(width = 320.dp, height = 480.dp)
                )
            }
        }
        compose.onNodeWithText("Upgrade to EarnIt Pro").assertIsDisplayed()
        assertEquals(SubscriptionConfig.Placeholder.annual.id, flow.selectedPlanId)
        compose.onNodeWithText("Monthly").performClick()
        compose.runOnIdle {
            assertEquals(SubscriptionConfig.Placeholder.monthly.id, flow.selectedPlanId)
        }
        compose.onNodeWithText("Terms of Service  ·  Privacy Policy").performScrollTo().assertIsDisplayed()
    }
}
