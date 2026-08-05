package com.kaleel.earnitv2

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.kaleel.earnitv2.ui.theme.EarnitV2Theme
import org.junit.Rule
import org.junit.Test

class FeedbackScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun requiredFieldsAndSelectedCategoryAreClear() {
        val viewModel = FeedbackViewModel(ApplicationProvider.getApplicationContext<Application>())
        viewModel.discard()
        compose.setContent {
            EarnitV2Theme {
                FeedbackScreen(
                    viewModel = viewModel,
                    strictModeEnabled = false,
                    onClose = {}
                )
            }
        }

        compose.onNodeWithText("Required").assertExists()
        compose.onNodeWithText("Submit Feedback").assertIsNotEnabled()
        compose.onNodeWithText("Bug").performClick().assertIsSelected()
        compose.onNodeWithText("Tell us what happened or what you would like to see…")
            .performTextInput("Instagram stayed open after Reward Time ran out.")
        compose.onNodeWithText("Submit Feedback").assertIsEnabled()
    }
}
