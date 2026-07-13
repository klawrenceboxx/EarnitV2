package com.example.earnitv2

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

    private companion object {
        const val GEMINI_ROBIN_ACTIVITY = "com.google.android.apps.search.assistant.surfaces.voice.robin.main.MainActivity"
        const val GOOGLE_APP_ACTIVITY = "com.google.android.apps.search.googleapp.activity.GoogleAppActivity"
        const val DEFAULT_KEYBOARD_PACKAGE = "com.example.keyboard"
    }
}
