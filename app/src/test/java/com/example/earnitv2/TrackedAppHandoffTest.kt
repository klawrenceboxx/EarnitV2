package com.kaleel.earnitv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackedAppHandoffTest {
    @Test
    fun defaultAppIdentityMatchesByPackageOnly() {
        val rule = TrackedAppMatchPolicy.matchingRule(
            logicalPackageName = "com.example.productive",
            actualPackageName = "com.example.productive",
            actualClassName = "com.example.AnyActivity"
        )

        assertEquals("com.example.productive", rule?.packageName)
    }

    @Test
    fun normalGeminiPackageUsageResolvesPendingWithoutHandoff() {
        val tracker = TrackedAppHandoffTracker(
            pendingLaunch = pendingGeminiLaunch(launchedAtMillis = 1_000L)
        )

        val result = tracker.onForegroundPackage(
            actualPackageName = TrackedAppMatchPolicy.GEMINI_PACKAGE,
            actualClassName = "com.google.android.apps.bard.MainActivity",
            nowMillis = 2_000L
        )

        assertEquals(TrackedAppMatchPolicy.GEMINI_PACKAGE, result.resolvedPending?.logicalPackageName)
        assertNull(result.startedSession)
        assertNull(tracker.pendingLaunch())
        assertNull(tracker.activeSession())
    }

    @Test
    fun verifiedGeminiRobinClassHandoffStartsLogicalGeminiSession() {
        val tracker = TrackedAppHandoffTracker(
            pendingLaunch = pendingGeminiLaunch(launchedAtMillis = 1_000L)
        )

        val result = tracker.onForegroundPackage(
            actualPackageName = TrackedAppMatchPolicy.GOOGLE_PACKAGE,
            actualClassName = GEMINI_ROBIN_ACTIVITY,
            nowMillis = 2_000L
        )

        assertEquals(TrackedAppMatchPolicy.GEMINI_PACKAGE, result.startedSession?.logicalPackageName)
        assertEquals(TrackedAppMatchPolicy.GOOGLE_PACKAGE, result.startedSession?.actualForegroundPackageName)
        assertEquals(GEMINI_ROBIN_ACTIVITY, result.startedSession?.actualForegroundClassName)
        assertEquals(2_000L, result.startedSession?.startedAtMillis)
        assertNull(tracker.pendingLaunch())
    }

    @Test
    fun currentGeminiGatewayStartsFromPendingGeminiLaunch() {
        val tracker = TrackedAppHandoffTracker(
            pendingLaunch = pendingGeminiLaunch(launchedAtMillis = 1_000L)
        )

        val result = tracker.onForegroundPackage(
            actualPackageName = TrackedAppMatchPolicy.GOOGLE_PACKAGE,
            actualClassName = TrackedAppMatchPolicy.GOOGLE_ANIMATED_GATEWAY_ACTIVITY,
            nowMillis = 2_000L,
            relevantLogicalPackageNames = setOf(TrackedAppMatchPolicy.GEMINI_PACKAGE)
        )

        assertEquals(TrackedAppMatchPolicy.GEMINI_PACKAGE, result.startedSession?.logicalPackageName)
        assertEquals(TrackedAppMatchPolicy.GOOGLE_PACKAGE, result.startedSession?.actualForegroundPackageName)
        assertNull(tracker.pendingLaunch())
    }

    @Test
    fun geminiEntryEvidenceSurvivesDelayedGoogleWrapperTransition() {
        val tracker = TrackedAppHandoffTracker(
            pendingLaunch = pendingGeminiLaunch(launchedAtMillis = 1_000L)
        )

        val entry = tracker.onForegroundPackage(
            actualPackageName = TrackedAppMatchPolicy.GEMINI_PACKAGE,
            actualClassName = TrackedAppMatchPolicy.GEMINI_ENTRY_POINT_ACTIVITY,
            nowMillis = 2_000L
        )
        val gateway = tracker.onForegroundPackage(
            actualPackageName = TrackedAppMatchPolicy.GOOGLE_PACKAGE,
            actualClassName = TrackedAppMatchPolicy.GOOGLE_ANIMATED_GATEWAY_ACTIVITY,
            nowMillis = 12_000L,
            relevantLogicalPackageNames = setOf(TrackedAppMatchPolicy.GEMINI_PACKAGE)
        )

        assertEquals("gemini-entry-evidence-preserved", entry.decision)
        assertEquals(17_000L, entry.updatedPending?.expiresAtMillis)
        assertEquals(TrackedAppMatchPolicy.GEMINI_PACKAGE, gateway.startedSession?.logicalPackageName)
        assertEquals(12_000L, gateway.startedSession?.startedAtMillis)
    }

    @Test
    fun keyboardDuringGeminiLaunchDoesNotDiscardWrapperEvidence() {
        val tracker = TrackedAppHandoffTracker(
            pendingLaunch = pendingGeminiLaunch(launchedAtMillis = 1_000L)
        )
        tracker.onForegroundPackage(
            TrackedAppMatchPolicy.GEMINI_PACKAGE,
            TrackedAppMatchPolicy.GEMINI_ENTRY_POINT_ACTIVITY,
            nowMillis = 2_000L
        )

        tracker.onForegroundPackage(
            actualPackageName = DEFAULT_KEYBOARD_PACKAGE,
            actualClassName = "android.inputmethodservice.SoftInputWindow",
            nowMillis = 5_000L,
            ignoredForegroundPackageNames = setOf(DEFAULT_KEYBOARD_PACKAGE)
        )
        val gateway = tracker.onForegroundPackage(
            actualPackageName = TrackedAppMatchPolicy.GOOGLE_PACKAGE,
            actualClassName = TrackedAppMatchPolicy.GOOGLE_GATEWAY_ACTIVITY,
            nowMillis = 8_000L
        )

        assertEquals(TrackedAppMatchPolicy.GEMINI_PACKAGE, gateway.startedSession?.logicalPackageName)
    }

    @Test
    fun persistedGeminiEntryEvidenceSurvivesTrackerRecreation() {
        val originalTracker = TrackedAppHandoffTracker(
            pendingLaunch = pendingGeminiLaunch(launchedAtMillis = 1_000L)
        )
        originalTracker.onForegroundPackage(
            TrackedAppMatchPolicy.GEMINI_PACKAGE,
            TrackedAppMatchPolicy.GEMINI_ENTRY_POINT_ACTIVITY,
            nowMillis = 2_000L
        )

        val restoredTracker = TrackedAppHandoffTracker(pendingLaunch = originalTracker.pendingLaunch())
        val result = restoredTracker.onForegroundPackage(
            TrackedAppMatchPolicy.GOOGLE_PACKAGE,
            TrackedAppMatchPolicy.GOOGLE_ANIMATED_GATEWAY_ACTIVITY,
            nowMillis = 12_000L
        )

        assertEquals(TrackedAppMatchPolicy.GEMINI_PACKAGE, result.startedSession?.logicalPackageName)
    }

    @Test
    fun realGoogleSearchAfterGeminiEntryDoesNotStartOrConsumePendingGeminiSession() {
        val tracker = TrackedAppHandoffTracker(
            pendingLaunch = pendingGeminiLaunch(launchedAtMillis = 1_000L)
        )
        tracker.onForegroundPackage(
            TrackedAppMatchPolicy.GEMINI_PACKAGE,
            TrackedAppMatchPolicy.GEMINI_ENTRY_POINT_ACTIVITY,
            nowMillis = 2_000L
        )

        val result = tracker.onForegroundPackage(
            TrackedAppMatchPolicy.GOOGLE_PACKAGE,
            GOOGLE_APP_ACTIVITY,
            nowMillis = 4_000L
        )

        assertNull(result.startedSession)
        assertEquals(TrackedAppMatchPolicy.GEMINI_PACKAGE, tracker.pendingLaunch()?.logicalPackageName)
    }

    @Test
    fun currentGeminiGatewayStartsDirectlyOnlyWithRecentGeminiEntryEvidence() {
        val tracker = TrackedAppHandoffTracker()

        val result = tracker.onForegroundPackage(
            actualPackageName = TrackedAppMatchPolicy.GOOGLE_PACKAGE,
            actualClassName = TrackedAppMatchPolicy.GOOGLE_ANIMATED_GATEWAY_ACTIVITY,
            nowMillis = 2_000L,
            relevantLogicalPackageNames = setOf(TrackedAppMatchPolicy.GEMINI_PACKAGE),
            recentlyForegroundLogicalPackageNames = setOf(TrackedAppMatchPolicy.GEMINI_PACKAGE)
        )

        assertEquals(TrackedAppMatchPolicy.GEMINI_PACKAGE, result.startedSession?.logicalPackageName)
    }

    @Test
    fun gatewayThenRobinActivityKeepsOneContinuousGeminiSession() {
        val tracker = TrackedAppHandoffTracker()
        tracker.onForegroundPackage(
            actualPackageName = TrackedAppMatchPolicy.GOOGLE_PACKAGE,
            actualClassName = TrackedAppMatchPolicy.GOOGLE_ANIMATED_GATEWAY_ACTIVITY,
            nowMillis = 2_000L,
            relevantLogicalPackageNames = setOf(TrackedAppMatchPolicy.GEMINI_PACKAGE),
            recentlyForegroundLogicalPackageNames = setOf(TrackedAppMatchPolicy.GEMINI_PACKAGE)
        )

        val robinResult = tracker.onForegroundPackage(
            actualPackageName = TrackedAppMatchPolicy.GOOGLE_PACKAGE,
            actualClassName = GEMINI_ROBIN_ACTIVITY,
            nowMillis = 2_500L,
            relevantLogicalPackageNames = setOf(TrackedAppMatchPolicy.GEMINI_PACKAGE)
        )

        assertNull(robinResult.endedSession)
        assertNull(robinResult.startedSession)
        assertEquals(2_000L, tracker.activeSession()?.startedAtMillis)
    }

    @Test
    fun genericGoogleGatewayDoesNotStartGeminiWithoutLaunchEvidence() {
        val tracker = TrackedAppHandoffTracker()

        val result = tracker.onForegroundPackage(
            actualPackageName = TrackedAppMatchPolicy.GOOGLE_PACKAGE,
            actualClassName = TrackedAppMatchPolicy.GOOGLE_ANIMATED_GATEWAY_ACTIVITY,
            nowMillis = 2_000L,
            relevantLogicalPackageNames = setOf(TrackedAppMatchPolicy.GEMINI_PACKAGE)
        )

        assertNull(result.startedSession)
        assertNull(tracker.activeSession())
    }

    @Test
    fun restoredGeminiSessionSurvivesGatewayDuringConfirmedGeminiRelaunch() {
        val tracker = activeGeminiTracker(startedAtMillis = 1_000L)

        val result = tracker.onForegroundPackage(
            actualPackageName = TrackedAppMatchPolicy.GOOGLE_PACKAGE,
            actualClassName = TrackedAppMatchPolicy.GOOGLE_ANIMATED_GATEWAY_ACTIVITY,
            nowMillis = 2_000L,
            relevantLogicalPackageNames = setOf(TrackedAppMatchPolicy.GEMINI_PACKAGE),
            recentlyForegroundLogicalPackageNames = setOf(TrackedAppMatchPolicy.GEMINI_PACKAGE)
        )

        assertNull(result.endedSession)
        assertNull(result.startedSession)
        assertEquals(1_000L, tracker.activeSession()?.startedAtMillis)
    }

    @Test
    fun staleGeminiSessionStillEndsOnUnconfirmedGenericGoogleGateway() {
        val tracker = activeGeminiTracker(startedAtMillis = 1_000L)

        val result = tracker.onForegroundPackage(
            actualPackageName = TrackedAppMatchPolicy.GOOGLE_PACKAGE,
            actualClassName = TrackedAppMatchPolicy.GOOGLE_ANIMATED_GATEWAY_ACTIVITY,
            nowMillis = 2_000L,
            relevantLogicalPackageNames = setOf(TrackedAppMatchPolicy.GEMINI_PACKAGE)
        )

        assertEquals(TrackedAppMatchPolicy.GEMINI_PACKAGE, result.endedSession?.logicalPackageName)
        assertNull(tracker.activeSession())
    }

    @Test
    fun regularGoogleActivityDoesNotResolveAsGemini() {
        val tracker = TrackedAppHandoffTracker(
            pendingLaunch = pendingGeminiLaunch(launchedAtMillis = 1_000L)
        )

        val result = tracker.onForegroundPackage(
            actualPackageName = TrackedAppMatchPolicy.GOOGLE_PACKAGE,
            actualClassName = GOOGLE_APP_ACTIVITY,
            nowMillis = 2_000L
        )

        assertNull(result.startedSession)
        assertNull(result.resolvedPending)
        assertEquals(TrackedAppMatchPolicy.GEMINI_PACKAGE, tracker.pendingLaunch()?.logicalPackageName)
    }

    @Test
    fun directVerifiedGeminiForegroundStartsWhenGeminiIsRelevant() {
        val tracker = TrackedAppHandoffTracker()

        val result = tracker.onForegroundPackage(
            actualPackageName = TrackedAppMatchPolicy.GOOGLE_PACKAGE,
            actualClassName = GEMINI_ROBIN_ACTIVITY,
            nowMillis = 2_000L,
            relevantLogicalPackageNames = setOf(TrackedAppMatchPolicy.GEMINI_PACKAGE)
        )

        assertEquals(TrackedAppMatchPolicy.GEMINI_PACKAGE, result.startedSession?.logicalPackageName)
        assertEquals(TrackedAppMatchPolicy.GOOGLE_PACKAGE, result.startedSession?.actualForegroundPackageName)
        assertEquals(GEMINI_ROBIN_ACTIVITY, result.startedSession?.actualForegroundClassName)
    }

    @Test
    fun directVerifiedGeminiForegroundDoesNotStartWhenGeminiIsNotRelevant() {
        val tracker = TrackedAppHandoffTracker()

        val result = tracker.onForegroundPackage(
            actualPackageName = TrackedAppMatchPolicy.GOOGLE_PACKAGE,
            actualClassName = GEMINI_ROBIN_ACTIVITY,
            nowMillis = 2_000L,
            relevantLogicalPackageNames = setOf("com.example.other")
        )

        assertNull(result.startedSession)
        assertNull(tracker.activeSession())
    }

    @Test
    fun regularGoogleActivityDoesNotStartEvenWhenGeminiIsRelevant() {
        val tracker = TrackedAppHandoffTracker()

        val result = tracker.onForegroundPackage(
            actualPackageName = TrackedAppMatchPolicy.GOOGLE_PACKAGE,
            actualClassName = GOOGLE_APP_ACTIVITY,
            nowMillis = 2_000L,
            relevantLogicalPackageNames = setOf(TrackedAppMatchPolicy.GEMINI_PACKAGE)
        )

        assertNull(result.startedSession)
        assertNull(tracker.activeSession())
    }

    @Test
    fun expiredPendingGeminiLaunchDoesNotCreditGoogleAsGemini() {
        val tracker = TrackedAppHandoffTracker(
            pendingLaunch = pendingGeminiLaunch(launchedAtMillis = 1_000L)
        )

        val result = tracker.onForegroundPackage(
            actualPackageName = TrackedAppMatchPolicy.GOOGLE_PACKAGE,
            actualClassName = GEMINI_ROBIN_ACTIVITY,
            nowMillis = 7_001L
        )

        assertEquals(TrackedAppMatchPolicy.GEMINI_PACKAGE, result.clearedExpiredPending?.logicalPackageName)
        assertNull(result.startedSession)
        assertNull(tracker.pendingLaunch())
    }

    @Test
    fun frameLayoutNoiseFromSamePackageDoesNotEndGeminiSession() {
        val tracker = TrackedAppHandoffTracker(
            activeSession = ActiveTrackedAppSession(
                logicalPackageName = TrackedAppMatchPolicy.GEMINI_PACKAGE,
                actualForegroundPackageName = TrackedAppMatchPolicy.GOOGLE_PACKAGE,
                actualForegroundClassName = GEMINI_ROBIN_ACTIVITY,
                startedAtMillis = 2_000L
            )
        )

        val result = tracker.onForegroundPackage(
            actualPackageName = TrackedAppMatchPolicy.GOOGLE_PACKAGE,
            actualClassName = "android.widget.FrameLayout",
            nowMillis = 3_000L
        )

        assertNull(result.endedSession)
        assertEquals(TrackedAppMatchPolicy.GEMINI_PACKAGE, tracker.activeSession()?.logicalPackageName)
    }

    @Test
    fun keyboardEventDoesNotEndActiveGeminiSession() {
        val tracker = activeGeminiTracker(startedAtMillis = 2_000L)

        val result = tracker.onForegroundPackage(
            actualPackageName = DEFAULT_KEYBOARD_PACKAGE,
            actualClassName = "android.widget.LinearLayout",
            nowMillis = 3_000L,
            ignoredForegroundPackageNames = setOf(DEFAULT_KEYBOARD_PACKAGE)
        )

        assertNull(result.endedSession)
        assertNull(result.startedSession)
        assertEquals(2_000L, tracker.activeSession()?.startedAtMillis)
    }

    @Test
    fun realAppSwitchAfterKeyboardEndsSessionAtFullElapsedTime() {
        val tracker = activeGeminiTracker(startedAtMillis = 2_000L)
        tracker.onForegroundPackage(
            actualPackageName = DEFAULT_KEYBOARD_PACKAGE,
            actualClassName = "android.widget.LinearLayout",
            nowMillis = 3_000L,
            ignoredForegroundPackageNames = setOf(DEFAULT_KEYBOARD_PACKAGE)
        )

        val result = tracker.onForegroundPackage(
            actualPackageName = "com.example.other",
            actualClassName = "com.example.OtherActivity",
            nowMillis = 8_000L,
            ignoredForegroundPackageNames = setOf(DEFAULT_KEYBOARD_PACKAGE)
        )

        assertEquals(2_000L, result.endedSession?.startedAtMillis)
        assertEquals(8_000L, result.endedAtMillis)
        assertEquals(6_000L, result.endedAtMillis!! - result.endedSession!!.startedAtMillis)
        assertNull(tracker.activeSession())
    }

    @Test
    fun duplicateGeminiActivityDoesNotRestartActiveSession() {
        val tracker = activeGeminiTracker(startedAtMillis = 2_000L)

        val result = tracker.onForegroundPackage(
            actualPackageName = TrackedAppMatchPolicy.GOOGLE_PACKAGE,
            actualClassName = GEMINI_ROBIN_ACTIVITY,
            nowMillis = 4_000L,
            relevantLogicalPackageNames = setOf(TrackedAppMatchPolicy.GEMINI_PACKAGE)
        )

        assertNull(result.endedSession)
        assertNull(result.startedSession)
        assertEquals(2_000L, tracker.activeSession()?.startedAtMillis)
    }

    @Test
    fun regularGoogleActivityEndsActiveGeminiHandoff() {
        val tracker = TrackedAppHandoffTracker(
            activeSession = ActiveTrackedAppSession(
                logicalPackageName = TrackedAppMatchPolicy.GEMINI_PACKAGE,
                actualForegroundPackageName = TrackedAppMatchPolicy.GOOGLE_PACKAGE,
                actualForegroundClassName = GEMINI_ROBIN_ACTIVITY,
                startedAtMillis = 2_000L
            )
        )

        val result = tracker.onForegroundPackage(
            actualPackageName = TrackedAppMatchPolicy.GOOGLE_PACKAGE,
            actualClassName = GOOGLE_APP_ACTIVITY,
            nowMillis = 5_500L
        )

        assertEquals(TrackedAppMatchPolicy.GEMINI_PACKAGE, result.endedSession?.logicalPackageName)
        assertEquals(2_000L, result.endedSession?.startedAtMillis)
        assertEquals(5_500L, result.endedAtMillis)
        assertNull(tracker.activeSession())
    }

    @Test
    fun handoffSessionEndsWhenAnotherPackageBecomesForeground() {
        val tracker = TrackedAppHandoffTracker(
            pendingLaunch = pendingGeminiLaunch(launchedAtMillis = 1_000L)
        )
        tracker.onForegroundPackage(TrackedAppMatchPolicy.GOOGLE_PACKAGE, GEMINI_ROBIN_ACTIVITY, nowMillis = 2_000L)

        val result = tracker.onForegroundPackage(
            actualPackageName = "com.example.other",
            actualClassName = "com.example.OtherActivity",
            nowMillis = 5_500L
        )

        assertEquals(TrackedAppMatchPolicy.GEMINI_PACKAGE, result.endedSession?.logicalPackageName)
        assertEquals(2_000L, result.endedSession?.startedAtMillis)
        assertEquals(5_500L, result.endedAtMillis)
        assertNull(tracker.activeSession())
    }

    @Test
    fun launcherAndRecentsEndGeminiWithoutRestartingItsOriginalSession() {
        listOf("com.example.launcher", "com.android.systemui").forEach { foregroundPackage ->
            val tracker = activeGeminiTracker(startedAtMillis = 2_000L)

            val result = tracker.onForegroundPackage(
                actualPackageName = foregroundPackage,
                actualClassName = "com.example.OverviewActivity",
                nowMillis = 5_000L
            )

            assertEquals(2_000L, result.endedSession?.startedAtMillis)
            assertEquals(5_000L, result.endedAtMillis)
            assertNull(result.startedSession)
            assertNull(tracker.activeSession())
        }
    }

    @Test
    fun trackedHandoffCreditsActiveScheduleExactlyOnce() {
        val rule = completeToUnlockRule(activeDays = EarnItRuleStore.allDays.toSet())

        val first = RewardLedger.trackedHandoffCreditDecision(
            rule = rule,
            startedAtMillis = 1_000L,
            endedAtMillis = 11_000L,
            creditCursor = 1_000L
        )
        val duplicate = RewardLedger.trackedHandoffCreditDecision(
            rule = rule,
            startedAtMillis = 1_000L,
            endedAtMillis = 11_000L,
            creditCursor = 11_000L
        )

        assertEquals(10L, first.activeSeconds)
        assertNull(first.rejectionReason)
        assertEquals(0L, duplicate.activeSeconds)
        assertEquals("duplicate-interval", duplicate.rejectionReason)
    }

    @Test
    fun trackedHandoffRejectsScheduleInactiveInterval() {
        val rule = completeToUnlockRule(activeDays = emptySet())

        val result = RewardLedger.trackedHandoffCreditDecision(
            rule = rule,
            startedAtMillis = 1_000L,
            endedAtMillis = 11_000L,
            creditCursor = 1_000L
        )

        assertEquals(0L, result.activeSeconds)
        assertEquals("outside-active-schedule", result.rejectionReason)
    }

    @Test
    fun stoppingSessionTwiceDoesNotDoubleCredit() {
        val tracker = TrackedAppHandoffTracker(
            activeSession = ActiveTrackedAppSession(
                logicalPackageName = TrackedAppMatchPolicy.GEMINI_PACKAGE,
                actualForegroundPackageName = TrackedAppMatchPolicy.GOOGLE_PACKAGE,
                actualForegroundClassName = GEMINI_ROBIN_ACTIVITY,
                startedAtMillis = 2_000L
            )
        )

        val first = tracker.stopActiveSession(nowMillis = 5_000L)
        val second = tracker.stopActiveSession(nowMillis = 6_000L)

        assertEquals(TrackedAppMatchPolicy.GEMINI_PACKAGE, first.endedSession?.logicalPackageName)
        assertNull(second.endedSession)
    }

    @Test
    fun policyDoesNotBroadlyAliasGoogleToGemini() {
        assertFalse(
            TrackedAppMatchPolicy.matchingRule(
                logicalPackageName = TrackedAppMatchPolicy.GEMINI_PACKAGE,
                actualPackageName = TrackedAppMatchPolicy.GOOGLE_PACKAGE,
                actualClassName = GOOGLE_APP_ACTIVITY
            ) != null
        )
        assertTrue(
            TrackedAppMatchPolicy.matchingRule(
                logicalPackageName = TrackedAppMatchPolicy.GEMINI_PACKAGE,
                actualPackageName = TrackedAppMatchPolicy.GOOGLE_PACKAGE,
                actualClassName = GEMINI_ROBIN_ACTIVITY
            ) != null
        )
    }

    @Test
    fun recentGeminiEntryEvidenceRequiresExactPackageClassTypeAndTimeWindow() {
        val now = 10_000L

        assertTrue(
            TrackedAppMatchPolicy.isRecentGeminiLaunchEvidence(
                packageName = TrackedAppMatchPolicy.GEMINI_PACKAGE,
                className = TrackedAppMatchPolicy.GEMINI_ENTRY_POINT_ACTIVITY,
                eventType = android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED,
                eventAtMillis = now - 1_000L,
                nowMillis = now
            )
        )
        assertFalse(
            TrackedAppMatchPolicy.isRecentGeminiLaunchEvidence(
                packageName = TrackedAppMatchPolicy.GEMINI_PACKAGE,
                className = TrackedAppMatchPolicy.GEMINI_ENTRY_POINT_ACTIVITY,
                eventType = android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED,
                eventAtMillis = now - TrackedAppMatchPolicy.RECENT_LAUNCH_EVIDENCE_WINDOW_MILLIS - 1L,
                nowMillis = now
            )
        )
        assertFalse(
            TrackedAppMatchPolicy.isRecentGeminiLaunchEvidence(
                packageName = TrackedAppMatchPolicy.GOOGLE_PACKAGE,
                className = TrackedAppMatchPolicy.GEMINI_ENTRY_POINT_ACTIVITY,
                eventType = android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED,
                eventAtMillis = now,
                nowMillis = now
            )
        )
    }

    private fun pendingGeminiLaunch(launchedAtMillis: Long): PendingTrackedAppLaunch {
        return PendingTrackedAppLaunch(
            logicalPackageName = TrackedAppMatchPolicy.GEMINI_PACKAGE,
            launchedPackageName = TrackedAppMatchPolicy.GEMINI_PACKAGE,
            launchedAtMillis = launchedAtMillis,
            expiresAtMillis = launchedAtMillis + TrackedAppMatchPolicy.PENDING_LAUNCH_WINDOW_MILLIS
        )
    }

    private fun activeGeminiTracker(startedAtMillis: Long): TrackedAppHandoffTracker {
        return TrackedAppHandoffTracker(
            activeSession = ActiveTrackedAppSession(
                logicalPackageName = TrackedAppMatchPolicy.GEMINI_PACKAGE,
                actualForegroundPackageName = TrackedAppMatchPolicy.GOOGLE_PACKAGE,
                actualForegroundClassName = GEMINI_ROBIN_ACTIVITY,
                startedAtMillis = startedAtMillis
            )
        )
    }

    private fun completeToUnlockRule(activeDays: Set<Int>): EarnItRuleStore.Rule {
        val gemini = EarnItRuleStore.RuleApp(TrackedAppMatchPolicy.GEMINI_PACKAGE, "Gemini")
        return EarnItRuleStore.Rule(
            id = "complete-test",
            productivePackage = "",
            productiveName = "",
            blockedApps = listOf(EarnItRuleStore.RuleApp("com.example.blocked", "Blocked")),
            rewardSecondsPerProductiveSecond = 1,
            activeDays = activeDays,
            startMinute = 0,
            endMinute = 1_440,
            type = EarnItRuleStore.RuleType.CompleteToUnlock,
            requirements = listOf(EarnItRuleStore.RuleRequirement(gemini, 300L))
        )
    }

    private companion object {
        const val GEMINI_ROBIN_ACTIVITY = "com.google.android.apps.search.assistant.surfaces.voice.robin.main.MainActivity"
        const val GOOGLE_APP_ACTIVITY = "com.google.android.apps.search.googleapp.activity.GoogleAppActivity"
        const val DEFAULT_KEYBOARD_PACKAGE = "com.example.keyboard"
    }
}
