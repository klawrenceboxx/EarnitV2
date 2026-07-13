package com.example.earnitv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class EarnItUiStateTest {
    @Test
    fun rewardTimeAvailability_formatsZeroAsNoRewardTime() {
        assertEquals("No Reward Time", EarnItUiFormatters.rewardTimeAvailability(0))
    }

    @Test
    fun rewardTimeAvailability_formatsWholeMinutes() {
        assertEquals("12 min available", EarnItUiFormatters.rewardTimeAvailability(735))
    }

    @Test
    fun permissionSetup_reportsReadyWhenBothPermissionsAreGranted() {
        val state = EarnItUiStateAdapters.permissionSetup(
            usageAccessGranted = true,
            appBlockingEnabled = true
        )

        assertTrue(state.isReady)
        assertFalse(state.needsAttention)
        assertEquals(EarnItPermissionStatus.Granted, state.earningProgressStatus)
        assertEquals(EarnItPermissionStatus.Granted, state.appBlockingStatus)
        assertTrue(state.repairTargetLabels.isEmpty())
    }

    @Test
    fun permissionSetup_reportsRepairTargetsWhenPermissionsAreMissing() {
        val state = EarnItUiStateAdapters.permissionSetup(
            usageAccessGranted = false,
            appBlockingEnabled = false
        )

        assertFalse(state.isReady)
        assertTrue(state.needsAttention)
        assertEquals(listOf("Earning progress", "App blocking"), state.repairTargetLabels)
    }

    @Test
    fun ruleCard_includesPermissionAttentionWithoutChangingRuleValues() {
        val rule = sampleRule()

        val state = EarnItUiStateAdapters.ruleCard(
            rule = rule,
            productiveUsageSeconds = 600,
            remainingRewardSeconds = 120,
            usageAccessGranted = false,
            appBlockingEnabled = true,
            isActiveNow = true
        )

        assertEquals("rule_1", state.ruleId)
        assertEquals("Duolingo", state.earnAppName)
        assertEquals("com.duolingo", state.earnAppPackage)
        assertEquals(2, state.rewardAppCount)
        assertEquals("2 min available", state.availableRewardTimeLabel)
        assertEquals("10 min productive today", state.productiveUsageLabel)
        assertEquals("Active now", state.scheduleStatusLabel)
        assertEquals("Earning progress needs attention", state.attentionLabel)
    }

    @Test
    fun ruleCard_hasNoAttentionWhenPermissionsAreReady() {
        val state = EarnItUiStateAdapters.ruleCard(
            rule = sampleRule(),
            productiveUsageSeconds = null,
            remainingRewardSeconds = 0,
            usageAccessGranted = true,
            appBlockingEnabled = true,
            isActiveNow = false
        )

        assertNull(state.attentionLabel)
        assertEquals("No Reward Time", state.availableRewardTimeLabel)
        assertEquals("Unrestricted right now", state.scheduleStatusLabel)
    }

    @Test
    fun ruleDraft_isReadyWhenAllRequiredSelectionsAreValid() {
        val state = EarnItUiStateAdapters.ruleDraft(
            selectedEarnApps = listOf(EarnItRuleStore.LaunchableApp("com.duolingo", "Duolingo")),
            selectedRewardApps = listOf(EarnItRuleStore.RuleApp("com.instagram.android", "Instagram")),
            exchangeSelection = 2,
            activeDays = setOf(1, 2, 3, 4, 5),
            timeWindows = listOf(EarnItRuleStore.TimeWindow(9 * 60, 17 * 60))
        )

        assertTrue(state.canReview)
        assertTrue(state.canSave)
        assertTrue(state.reviewSummary.contains("When I use Duolingo"))
        assertTrue(state.reviewSummary.contains("Every 10 min earns 2 min Reward Time"))
        assertTrue(state.reviewSummary.contains("For Instagram"))
    }

    @Test
    fun ruleDraft_isNotReadyWithoutRewardApps() {
        val state = EarnItUiStateAdapters.ruleDraft(
            selectedEarnApps = listOf(EarnItRuleStore.LaunchableApp("com.duolingo", "Duolingo")),
            selectedRewardApps = emptyList(),
            exchangeSelection = 2,
            activeDays = EarnItRuleStore.allDays.toSet(),
            timeWindows = listOf(EarnItRuleStore.TimeWindow(0, 1_440))
        )

        assertFalse(state.canReview)
        assertFalse(state.canSave)
    }

    @Test
    fun homeRuleUiState_completeToUnlockDoesNotUseRewardTimeBalanceCopy() {
        val state = homeRuleUiState(
            state = RuleDashboardState(
                rule = completeToUnlockRule(),
                productiveUsageSeconds = 0,
                remainingRewardSeconds = 0,
                requirementProgressSeconds = mapOf("com.duolingo" to 0L, "com.headspace" to 0L)
            ),
            usageAccessGranted = true,
            appBlockingEnabled = true
        )

        assertEquals("Locked", state.primaryText)
        assertEquals("2 requirements remaining", state.secondaryText)
        assertEquals("Requirements incomplete", state.statusText)
        assertNull(state.earnContextText)
    }

    @Test
    fun homeRuleUiState_completeToUnlockUsesLivePartialProgress() {
        val rule = completeToUnlockRule().copy(
            requirements = listOf(
                requirement("com.duolingo", "Duolingo", 5),
                requirement("com.gemini", "Gemini", 10),
                requirement("com.headspace", "Headspace", 10),
                requirement("com.notion", "Notion", 5)
            )
        )

        val state = homeRuleUiState(
            state = RuleDashboardState(
                rule = rule,
                productiveUsageSeconds = 0,
                remainingRewardSeconds = 0,
                requirementProgressSeconds = mapOf(
                    "com.duolingo" to 5 * 60L,
                    "com.gemini" to 0L,
                    "com.headspace" to 1 * 60L,
                    "com.notion" to 0L
                )
            ),
            usageAccessGranted = true,
            appBlockingEnabled = true
        )

        val progress = state.completeToUnlockProgress!!
        assertEquals("Locked", state.primaryText)
        assertEquals("3 requirements remaining", state.secondaryText)
        assertEquals(3, progress.remainingRequirementCount)
        assertEquals(listOf("com.gemini", "com.headspace", "com.notion"), progress.incompleteRequirements.map { it.packageName })
        assertEquals(listOf("0 / 10 min", "1 / 10 min", "0 / 5 min"), progress.incompleteRequirements.map { it.progressLabel })
        assertEquals(1, progress.incompleteRequirements.drop(2).size)
    }

    @Test
    fun homeRuleUiState_completeToUnlockUsesSingularRemainingCount() {
        val rule = completeToUnlockRule().copy(
            requirements = listOf(
                requirement("com.duolingo", "Duolingo", 5),
                requirement("com.headspace", "Headspace", 10)
            )
        )

        val state = homeRuleUiState(
            state = RuleDashboardState(
                rule = rule,
                productiveUsageSeconds = 0,
                remainingRewardSeconds = 0,
                requirementProgressSeconds = mapOf(
                    "com.duolingo" to 5 * 60L,
                    "com.headspace" to 1 * 60L
                )
            ),
            usageAccessGranted = true,
            appBlockingEnabled = true
        )

        assertEquals("1 requirement remaining", state.secondaryText)
    }

    @Test
    fun homeRuleUiState_completeToUnlockShowsUnlockedWhenAllRequirementsComplete() {
        val rule = completeToUnlockRule()

        val state = homeRuleUiState(
            state = RuleDashboardState(
                rule = rule,
                productiveUsageSeconds = 0,
                remainingRewardSeconds = 0,
                requirementProgressSeconds = mapOf(
                    "com.duolingo" to 10 * 60L,
                    "com.headspace" to 20 * 60L
                )
            ),
            usageAccessGranted = true,
            appBlockingEnabled = true
        )

        assertEquals("Unlocked", state.primaryText)
        assertEquals("Requirements complete", state.secondaryText)
        assertEquals("Requirements complete", state.statusText)
        assertTrue(state.completeToUnlockProgress!!.incompleteRequirements.isEmpty())
    }

    @Test
    fun homeRuleUiState_completeToUnlockUpdatesWhenProgressMapChanges() {
        val rule = completeToUnlockRule()
        val initial = homeRuleUiState(
            state = RuleDashboardState(rule, 0, 0, mapOf("com.duolingo" to 0L, "com.headspace" to 0L)),
            usageAccessGranted = true,
            appBlockingEnabled = true
        )
        val updated = homeRuleUiState(
            state = RuleDashboardState(rule, 0, 0, mapOf("com.duolingo" to 10 * 60L, "com.headspace" to 20 * 60L)),
            usageAccessGranted = true,
            appBlockingEnabled = true
        )

        assertEquals("2 requirements remaining", initial.secondaryText)
        assertEquals("Unlocked", updated.primaryText)
        assertEquals("Requirements complete", updated.secondaryText)
    }

    @Test
    fun completeToUnlockSharedMapperMatchesBlockedRequirementRows() {
        val rule = completeToUnlockRule()
        val progress = mapOf("com.duolingo" to 10 * 60L, "com.headspace" to 1 * 60L)

        val homeProgress = completeToUnlockProgressUiState(rule, progress)
        val blockedRows = blockedRequirementUiStates(rule, progress)

        assertEquals(homeProgress.incompleteRequirements, blockedRows)
        assertEquals(1, homeProgress.remainingRequirementCount)
        assertEquals("1 / 20 min", blockedRows.single().progressLabel)
    }

    @Test
    fun earnRewardTimeHomeCardUsesRequestedSemanticOrder() {
        assertEquals(
            listOf("Earn with", "Earn Apps", "Unlocks", "Reward Apps", "Exchange"),
            earnRewardTimeHomeCardSemanticOrder()
        )
    }

    @Test
    fun earnRewardTimeEarnAppsBuildSeparatePackageSpecificRows() {
        val card = EarnItUiStateAdapters.ruleCard(
            rule = sampleRule().copy(
                productiveApps = listOf(
                    EarnItRuleStore.RuleApp("com.duolingo", "Duolingo"),
                    EarnItRuleStore.RuleApp("com.ichi2.anki", "AnkiDroid")
                )
            ),
            productiveUsageSeconds = 420,
            remainingRewardSeconds = 0,
            usageAccessGranted = true,
            appBlockingEnabled = true,
            isActiveNow = true
        )

        val rows = earnRewardTimeEarnAppRows(card, supportingText = null)

        assertEquals(2, rows.size)
        assertEquals(listOf("Duolingo", "AnkiDroid"), rows.map { it.name })
        assertEquals(listOf("com.duolingo", "com.ichi2.anki"), rows.map { it.packageName })
        assertFalse(rows.any { it.name.contains(",") || it.name.contains("‼") || it.name.contains("!!") })
    }

    @Test
    fun earnRewardTimeRewardAppsBuildSeparateRowsWithoutConcatenatedNames() {
        val card = EarnItUiStateAdapters.ruleCard(
            rule = sampleRule(),
            productiveUsageSeconds = 0,
            remainingRewardSeconds = 0,
            usageAccessGranted = true,
            appBlockingEnabled = true,
            isActiveNow = true
        )

        val rows = earnRewardTimeRewardAppRows(card)

        assertEquals(2, rows.size)
        assertEquals(listOf("Instagram", "YouTube"), rows.map { it.name })
        assertFalse(rows.any { it.name.contains(",") })
    }

    @Test
    fun ruleAppActionRowsPreservePackageSpecificOpenTargets() {
        val rows = listOf(
            HomeRuleAppActionRowState("com.duolingo", "Duolingo", null),
            HomeRuleAppActionRowState("com.ichi2.anki", "AnkiDroid", null)
        )

        val openedPackages = rows.map { it.packageName }

        assertEquals(listOf("com.duolingo", "com.ichi2.anki"), openedPackages)
    }

    @Test
    fun longEarnAppNamesStayInTheirOwnRows() {
        val longName = "A Very Long Productive App Name That Should Ellipsize Before Open"
        val card = EarnItUiStateAdapters.ruleCard(
            rule = sampleRule().copy(
                productiveApps = listOf(
                    EarnItRuleStore.RuleApp("com.example.long", longName),
                    EarnItRuleStore.RuleApp("com.ichi2.anki", "AnkiDroid")
                )
            ),
            productiveUsageSeconds = 0,
            remainingRewardSeconds = 0,
            usageAccessGranted = true,
            appBlockingEnabled = true,
            isActiveNow = true
        )

        val rows = earnRewardTimeEarnAppRows(card, supportingText = null)

        assertEquals(longName, rows.first().name)
        assertEquals("com.example.long", rows.first().packageName)
        assertEquals("com.ichi2.anki", rows[1].packageName)
    }

    @Test
    fun homeRuleUiState_completeToUnlockPreservesPausedAndSchedulePrecedence() {
        val inactiveDay = if (todayEarnItDay() == 1) 2 else 1
        val completeProgress = mapOf("com.duolingo" to 10 * 60L, "com.headspace" to 20 * 60L)

        val paused = homeRuleUiState(
            state = RuleDashboardState(
                rule = completeToUnlockRule().copy(enabled = false),
                productiveUsageSeconds = 0,
                remainingRewardSeconds = 0,
                requirementProgressSeconds = completeProgress
            ),
            usageAccessGranted = true,
            appBlockingEnabled = true
        )
        val outsideSchedule = homeRuleUiState(
            state = RuleDashboardState(
                rule = completeToUnlockRule().copy(
                    activeDays = setOf(inactiveDay),
                    startMinute = 9 * 60,
                    endMinute = 10 * 60,
                    timeWindows = listOf(EarnItRuleStore.TimeWindow(9 * 60, 10 * 60))
                ),
                productiveUsageSeconds = 0,
                remainingRewardSeconds = 0,
                requirementProgressSeconds = completeProgress
            ),
            usageAccessGranted = true,
            appBlockingEnabled = true
        )

        assertEquals("Rule paused", paused.primaryText)
        assertEquals("Scheduled", outsideSchedule.primaryText)
        assertEquals("Apps unrestricted right now", outsideSchedule.statusText)
    }

    @Test
    fun homeRuleUiState_scheduledBlockDoesNotUseRewardTimeBalanceCopy() {
        val state = homeRuleUiState(
            state = RuleDashboardState(
                rule = scheduledBlockRule(),
                productiveUsageSeconds = 0,
                remainingRewardSeconds = 0
            ),
            usageAccessGranted = true,
            appBlockingEnabled = true
        )

        assertEquals("Blocking now", state.primaryText)
        assertEquals("Within block schedule", state.statusText)
        assertEquals("Every day · All day", state.secondaryText)
        assertNull(state.earnContextText)
    }

    @Test
    fun requirementSummaryLabel_usesConciseGrammar() {
        assertEquals("No requirements", requirementSummaryLabel(0))
        assertEquals("1 requirement remaining", requirementSummaryLabel(1))
        assertEquals("2 requirements remaining", requirementSummaryLabel(2))
    }

    @Test
    fun homeRuleUiState_earnRewardTimePreservesBalanceAndExchangeContext() {
        val state = homeRuleUiState(
            state = RuleDashboardState(
                rule = sampleRule().copy(
                    activeDays = EarnItRuleStore.allDays.toSet(),
                    startMinute = 0,
                    endMinute = 1_440,
                    timeWindows = listOf(EarnItRuleStore.TimeWindow(0, 1_440))
                ),
                productiveUsageSeconds = 0,
                remainingRewardSeconds = 0
            ),
            usageAccessGranted = true,
            appBlockingEnabled = true
        )

        assertEquals("No Reward Time", state.primaryText)
        assertEquals("Active now", state.secondaryText)
        assertEquals("Every 10 min earns 2 min Reward Time", state.earnContextText)
    }

    @Test
    fun homeRuleUiState_earnRewardTimeKeepsExchangeCopyOutOfStatusArea() {
        val state = homeRuleUiState(
            state = RuleDashboardState(
                rule = sampleRule().copy(
                    activeDays = EarnItRuleStore.allDays.toSet(),
                    startMinute = 0,
                    endMinute = 1_440,
                    timeWindows = listOf(EarnItRuleStore.TimeWindow(0, 1_440))
                ),
                productiveUsageSeconds = 0,
                remainingRewardSeconds = 0
            ),
            usageAccessGranted = true,
            appBlockingEnabled = true
        )

        assertFalse(state.secondaryText.orEmpty().contains("Every 10 min earns"))
        assertEquals("Every 10 min earns 2 min Reward Time", state.earnContextText)
    }

    @Test
    fun homeRuleUiState_pausedEarnRewardTimeUsesCompactPausedCopy() {
        val state = homeRuleUiState(
            state = RuleDashboardState(
                rule = sampleRule().copy(enabled = false),
                productiveUsageSeconds = 0,
                remainingRewardSeconds = 0
            ),
            usageAccessGranted = true,
            appBlockingEnabled = true
        )

        assertEquals("Rule paused", state.primaryText)
        assertEquals("No Reward Time saved today", state.secondaryText)
        assertEquals("Available if resumed today", state.statusText)
    }

    @Test
    fun homeRuleUiState_scheduledBlockInactiveUsesClearState() {
        val inactiveDay = if (todayEarnItDay() == 1) 2 else 1
        val state = homeRuleUiState(
            state = RuleDashboardState(
                rule = scheduledBlockRule().copy(
                    activeDays = setOf(inactiveDay),
                    startMinute = 9 * 60,
                    endMinute = 10 * 60,
                    timeWindows = listOf(EarnItRuleStore.TimeWindow(9 * 60, 10 * 60))
                ),
                productiveUsageSeconds = 0,
                remainingRewardSeconds = 0
            ),
            usageAccessGranted = true,
            appBlockingEnabled = true
        )

        assertEquals("Not blocking now", state.primaryText)
        assertTrue(state.secondaryText.orEmpty().startsWith(earnItDayLabel(inactiveDay)))
        assertTrue(state.secondaryText.orEmpty().endsWith("9:00 AM-10:00 AM"))
        assertEquals("Outside block schedule", state.statusText)
        assertNull(state.earnContextText)
    }

    private fun todayEarnItDay(): Int {
        return when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> 7
        }
    }

    private fun earnItDayLabel(day: Int): String {
        return when (day) {
            1 -> "Mon"
            2 -> "Tue"
            3 -> "Wed"
            4 -> "Thu"
            5 -> "Fri"
            6 -> "Sat"
            else -> "Sun"
        }
    }

    private fun sampleRule(): EarnItRuleStore.Rule {
        return EarnItRuleStore.Rule(
            id = "rule_1",
            productivePackage = "com.duolingo",
            productiveName = "Duolingo",
            blockedApps = listOf(
                EarnItRuleStore.RuleApp("com.instagram.android", "Instagram"),
                EarnItRuleStore.RuleApp("com.youtube.android", "YouTube")
            ),
            rewardSecondsPerProductiveSecond = 2,
            activeDays = setOf(1, 2, 3, 4, 5),
            startMinute = 9 * 60,
            endMinute = 17 * 60,
            enabled = true
        )
    }

    private fun completeToUnlockRule(): EarnItRuleStore.Rule {
        return EarnItRuleStore.Rule(
            id = "rule_complete",
            productivePackage = "",
            productiveName = "",
            blockedApps = listOf(
                EarnItRuleStore.RuleApp("com.instagram.android", "Instagram"),
                EarnItRuleStore.RuleApp("com.snapchat.android", "Snapchat")
            ),
            rewardSecondsPerProductiveSecond = 1,
            activeDays = EarnItRuleStore.allDays.toSet(),
            startMinute = 0,
            endMinute = 1_440,
            enabled = true,
            type = EarnItRuleStore.RuleType.CompleteToUnlock,
            requirements = listOf(
                requirement("com.duolingo", "Duolingo", 10),
                requirement("com.headspace", "Headspace", 20)
            )
        )
    }

    private fun requirement(packageName: String, name: String, minutes: Long): EarnItRuleStore.RuleRequirement {
        return EarnItRuleStore.RuleRequirement(EarnItRuleStore.RuleApp(packageName, name), minutes * 60L)
    }

    private fun scheduledBlockRule(): EarnItRuleStore.Rule {
        return EarnItRuleStore.Rule(
            id = "rule_schedule",
            productivePackage = "",
            productiveName = "",
            blockedApps = listOf(
                EarnItRuleStore.RuleApp("com.instagram.android", "Instagram"),
                EarnItRuleStore.RuleApp("com.snapchat.android", "Snapchat")
            ),
            rewardSecondsPerProductiveSecond = 1,
            activeDays = EarnItRuleStore.allDays.toSet(),
            startMinute = 0,
            endMinute = 1_440,
            enabled = true,
            type = EarnItRuleStore.RuleType.ScheduledBlock
        )
    }
}
