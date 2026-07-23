package com.example.earnitv2

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.earnitv2.ui.theme.EarnitV2Theme
import org.junit.Rule
import org.junit.Test

class WebsitePickerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun addNormalizeDuplicateAndRemoveWebsite() {
        var domains by mutableStateOf(emptyList<String>())
        compose.setContent {
            EarnitV2Theme {
                RewardTargetPickerSurface(
                    title = "Choose Reward Apps", supportingText = null, searchLabel = "Search",
                    apps = emptyList(), selectedPackages = emptySet(), searchQuery = "", loading = false,
                    onSearchQueryChange = {}, onToggleApp = {}, onSave = {}, onBack = {}, multiSelect = true,
                    saveLabel = "Save", selectedDomains = domains, onDomainsChange = { domains = it }
                )
            }
        }
        compose.onNodeWithText("Websites").performClick()
        compose.onNodeWithText("Enter a website such as youtube.com").performTextInput("not a domain")
        compose.onNodeWithText("Add").performClick()
        compose.onNodeWithText("Enter a valid website domain").assertIsDisplayed()
        compose.onNodeWithText("Enter a website such as youtube.com").performTextClearance()
        compose.onNodeWithText("Enter a website such as youtube.com").performTextInput("https://www.YouTube.com/watch?v=1")
        compose.onNodeWithText("Add").performClick()
        compose.onNodeWithText("🌐  youtube.com").assertIsDisplayed()
        compose.onNodeWithText("Enter a website such as youtube.com").performTextInput("m.youtube.com")
        compose.onNodeWithText("Add").performClick()
        compose.onNodeWithText("youtube.com is already selected").assertIsDisplayed()
        compose.onNodeWithText("Remove").performClick()
        compose.onNodeWithText("0 apps · 0 websites selected").assertIsDisplayed()
    }

    @Test fun blockedWebsiteShowsOnlyNormalizedDomainAndWebsiteIdentity() {
        compose.setContent {
            EarnitV2Theme {
                BlockedScreen(
                    blockedAppName = "youtube.com", blockedPackage = null, blockedDomain = "youtube.com",
                    earnApps = emptyList(), blockedReason = RuleAccessEvaluator.DenialReason.OutOfRewardTime,
                    incompleteRequirements = emptyList(), scheduleStatus = null, fallbackMessage = null,
                    onOpenEarnApp = {}, onOpenRequirementApp = {}, onViewMoreEarningApps = {}, onNotNow = {},
                    availableRewardTimeLabel = "No Reward Time"
                )
            }
        }
        compose.onNodeWithContentDescription("Website").assertIsDisplayed()
        compose.onNodeWithText("youtube.com").assertIsDisplayed()
        compose.onNodeWithText("No Reward Time").assertIsDisplayed()
        compose.onNodeWithText("https://youtube.com/watch?v=private").assertDoesNotExist()
    }
}
