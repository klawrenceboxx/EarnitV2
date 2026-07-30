package com.example.earnitv2

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.example.earnitv2.ui.theme.EarnitV2Theme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AnalyticsScreenTest {
    @get:Rule val compose = createComposeRule()

    private val permissionReady = PermissionSetupUiState(
        earningProgressStatus = EarnItPermissionStatus.Granted,
        appBlockingStatus = EarnItPermissionStatus.Granted,
        isReady = true,
        needsAttention = false,
        repairTargetLabels = emptyList()
    )

    @Test fun homeHeaderDoesNotExposeAnalytics() {
        compose.setContent {
            EarnitV2Theme {
                EarnItHome(
                    rules = emptyList(), permissionState = permissionReady, manageRulesOpen = false,
                    deepWorkActive = false, onOpenDeepWork = {}, onAddRule = {}, onOpenEarnApp = {},
                    onOpenUsageAccessSettings = {}, onOpenAccessibilitySettings = {}, onContinueSetup = {}, onOpenSettings = {},
                    onToggleManageRules = {}, onOpenRuleDetail = {}, onEditRule = {},
                    onToggleRuleEnabled = {}, onDeleteRule = {}
                )
            }
        }
        compose.onNodeWithText("EarnIt").assertIsDisplayed()
        compose.onNodeWithText("Analytics").assertDoesNotExist()
        compose.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test fun settingsAnalyticsRowOpensAnalytics() {
        var opened = false
        compose.setContent {
            EarnitV2Theme {
                EarnItSettings(
                    permissionState = permissionReady, hasRules = true, strictModeState = StrictModeState(),
                    onBack = {}, onOpenAnalytics = { opened = true }, onOpenStrictMode = {},
                    onOpenUsageAccessSettings = {}, onOpenAccessibilitySettings = {}, onCreateFirstRule = {},
                    showDeveloperTools = false, onReplayOnboarding = {}
                )
            }
        }
        compose.onNodeWithText("Analytics").performClick()
        compose.runOnIdle { assertTrue(opened) }
    }

    @Test fun periodSwitchingAndNoRulesStateAreVisible() {
        var range by mutableStateOf(defaultAnalyticsRange())
        compose.setContent {
            EarnitV2Theme {
                AnalyticsScreen(
                    range = range, selectedDate = LocalDate.of(2026, 7, 21),
                    state = AnalyticsUiState.Ready(emptySummary(range)), insights = emptyList(),
                    rules = emptyList(), selectedAppPackage = null, onRangeChange = { range = it },
                    onDateChange = {},
                    onOpenApp = {}, onBackFromApp = {}, onBack = {}, onCreateRule = {}, onRepairPermission = {}
                )
            }
        }
        compose.onNodeWithText("7 Days").assertIsDisplayed()
        compose.onNodeWithText("No Rules created yet").assertIsDisplayed()
        compose.onNodeWithText("Today").performClick()
        compose.runOnIdle { assertTrue(range == AnalyticsRange.Today) }
    }

    @Test fun appRowOpensDenseAppDetail() {
        var selected: String? by mutableStateOf(null)
        val summary = populatedSummary()
        compose.setContent {
            EarnitV2Theme {
                AnalyticsScreen(
                    range = AnalyticsRange.SevenDays, selectedDate = LocalDate.of(2026, 7, 21),
                    state = AnalyticsUiState.Ready(summary), insights = emptyList(),
                    rules = emptyList(), selectedAppPackage = selected, onRangeChange = {},
                    onDateChange = {},
                    onOpenApp = { selected = it }, onBackFromApp = { selected = null }, onBack = {},
                    onCreateRule = {}, onRepairPermission = {}
                )
            }
        }
        compose.onNodeWithText("Example App").performClick()
        compose.onNodeWithText("Time spent").assertIsDisplayed()
        compose.onNodeWithText("Usage over time").assertIsDisplayed()
    }

    @Test fun selectedDayNavigationUpdatesTheVisibleDate() {
        var selected by mutableStateOf(LocalDate.now())
        compose.setContent {
            EarnitV2Theme {
                AnalyticsScreen(
                    range = AnalyticsRange.Today,
                    selectedDate = selected,
                    state = AnalyticsUiState.Ready(emptySummary(AnalyticsRange.Today)),
                    insights = emptyList(),
                    rules = emptyList(),
                    selectedAppPackage = null,
                    onRangeChange = {},
                    onDateChange = { selected = it },
                    onOpenApp = {},
                    onBackFromApp = {},
                    onBack = {},
                    onCreateRule = {},
                    onRepairPermission = {}
                )
            }
        }
        compose.onNodeWithText("Today, ${selected.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())} ${selected.dayOfMonth}")
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("Previous day").performClick()
        compose.onNodeWithText("Yesterday, ${selected.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())} ${selected.dayOfMonth}")
            .assertIsDisplayed()
    }

    private fun emptySummary(range: AnalyticsRange): AnalyticsSummary {
        val dates = if (range == AnalyticsRange.Today) listOf(LocalDate.of(2026, 7, 21))
        else (0..6).map { LocalDate.of(2026, 7, 15).plusDays(it.toLong()) }
        val zone = ZoneId.of("America/Toronto")
        val period = AnalyticsPeriod(
            dates.first().atStartOfDay(zone).toInstant().toEpochMilli(),
            dates.last().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            dates
        )
        return AnalyticsSummary(range, period, 0, 0, 0, 0, dates.map(::DailyUsageSummary), emptyList(), emptyList(), null)
    }

    private fun populatedSummary(): AnalyticsSummary {
        val base = emptySummary(AnalyticsRange.SevenDays)
        val daily = List(7) { 600L * (it + 1) }
        val app = AppUsageSummary("com.example.app", "Example App", AnalyticsClassification.Reward, daily.sum(), daily, List(24) { if (it == 20) 1_800 else 0 })
        return base.copy(
            totalSeconds = daily.sum(), rewardSeconds = daily.sum(), apps = listOf(app),
            dailyUsage = base.period.dates.mapIndexed { index, date -> DailyUsageSummary(date, daily[index], rewardSeconds = daily[index], hourlyRewardSeconds = app.hourlySeconds) }
        )
    }
}
