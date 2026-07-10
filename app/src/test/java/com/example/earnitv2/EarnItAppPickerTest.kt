package com.example.earnitv2

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EarnItAppPickerTest {
    @Test
    fun classifyLaunchableApp_usesAndroidApplicationCategoryFirst() {
        val app = EarnItRuleStore.LaunchableApp(
            packageName = "com.example.anything",
            name = "Anything",
            applicationCategory = ApplicationInfo.CATEGORY_SOCIAL
        )

        assertEquals(AppPickerCategory.Social, classifyLaunchableApp(app))
    }

    @Test
    fun classifyLaunchableApp_usesKnownPackageFallbackWhenMetadataIsMissing() {
        val app = EarnItRuleStore.LaunchableApp(
            packageName = "com.instagram.android",
            name = "Instagram"
        )

        assertEquals(AppPickerCategory.Social, classifyLaunchableApp(app))
    }

    @Test
    fun filterLaunchableApps_appliesCategoryBeforeSearch() {
        val apps = listOf(
            EarnItRuleStore.LaunchableApp("com.instagram.android", "Instagram"),
            EarnItRuleStore.LaunchableApp("com.spotify.music", "Spotify"),
            EarnItRuleStore.LaunchableApp("com.notion.id", "Notion")
        )

        val result = filterLaunchableApps(
            apps = apps,
            category = AppPickerCategory.Social,
            query = "inst"
        )

        assertEquals(listOf("Instagram"), result.map { it.name })
    }

    @Test
    fun filterLaunchableApps_doesNotMutateSelectionState() {
        val selectedPackages = setOf("com.instagram.android")
        val apps = listOf(
            EarnItRuleStore.LaunchableApp("com.instagram.android", "Instagram"),
            EarnItRuleStore.LaunchableApp("com.youtube.android", "YouTube")
        )

        filterLaunchableApps(apps, AppPickerCategory.Entertainment, "")
        filterLaunchableApps(apps, AppPickerCategory.All, "inst")

        assertEquals(setOf("com.instagram.android"), selectedPackages)
    }

    @Test
    fun selectedAppCountLabel_usesSingularAndPluralGrammar() {
        assertEquals("0 apps selected", selectedAppCountLabel(0))
        assertEquals("1 app selected", selectedAppCountLabel(1))
        assertEquals("2 apps selected", selectedAppCountLabel(2))
    }

    @Test
    fun ruleDraft_retainsMultipleEarnApps() {
        val state = EarnItUiStateAdapters.ruleDraft(
            selectedEarnApps = listOf(
                EarnItRuleStore.LaunchableApp("com.duolingo", "Duolingo"),
                EarnItRuleStore.LaunchableApp("com.kindle", "Kindle")
            ),
            selectedRewardApps = listOf(EarnItRuleStore.RuleApp("com.instagram.android", "Instagram")),
            exchangeSelection = 2,
            activeDays = EarnItRuleStore.allDays.toSet(),
            timeWindows = listOf(EarnItRuleStore.TimeWindow(0, 1_440))
        )

        assertTrue(state.canSave)
        assertEquals(listOf("Duolingo", "Kindle"), state.selectedEarnApps.map { it.name })
    }
}
