package com.example.earnitv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackedAppHandoffTest {
    @Test
    fun normalGeminiPackageUsageResolvesPendingWithoutHandoff() {
        val tracker = TrackedAppHandoffTracker(
            pendingLaunch = pendingGeminiLaunch(launchedAtMillis = 1_000L)
        )

        val result = tracker.onForegroundPackage(
            actualPackageName = TrackedAppHandoffPolicy.GEMINI_PACKAGE,
            nowMillis = 2_000L
        )

        assertEquals(TrackedAppHandoffPolicy.GEMINI_PACKAGE, result.resolvedPending?.logicalPackageName)
        assertNull(result.startedSession)
        assertNull(tracker.pendingLaunch())
        assertNull(tracker.activeSession())
    }

    @Test
    fun verifiedGeminiToGoogleHandoffStartsLogicalGeminiSession() {
        val tracker = TrackedAppHandoffTracker(
            pendingLaunch = pendingGeminiLaunch(launchedAtMillis = 1_000L)
        )

        val result = tracker.onForegroundPackage(
            actualPackageName = TrackedAppHandoffPolicy.GOOGLE_PACKAGE,
            nowMillis = 2_000L
        )

        assertEquals(TrackedAppHandoffPolicy.GEMINI_PACKAGE, result.startedSession?.logicalPackageName)
        assertEquals(TrackedAppHandoffPolicy.GOOGLE_PACKAGE, result.startedSession?.actualForegroundPackageName)
        assertEquals(2_000L, result.startedSession?.startedAtMillis)
        assertNull(tracker.pendingLaunch())
    }

    @Test
    fun ordinaryGoogleLaunchWithoutPendingGeminiDoesNotStartGeminiSession() {
        val tracker = TrackedAppHandoffTracker()

        val result = tracker.onForegroundPackage(
            actualPackageName = TrackedAppHandoffPolicy.GOOGLE_PACKAGE,
            nowMillis = 2_000L
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
            actualPackageName = TrackedAppHandoffPolicy.GOOGLE_PACKAGE,
            nowMillis = 7_001L
        )

        assertEquals(TrackedAppHandoffPolicy.GEMINI_PACKAGE, result.clearedExpiredPending?.logicalPackageName)
        assertNull(result.startedSession)
        assertNull(tracker.pendingLaunch())
    }

    @Test
    fun handoffSessionEndsWhenAnotherPackageBecomesForeground() {
        val tracker = TrackedAppHandoffTracker(
            pendingLaunch = pendingGeminiLaunch(launchedAtMillis = 1_000L)
        )
        tracker.onForegroundPackage(TrackedAppHandoffPolicy.GOOGLE_PACKAGE, nowMillis = 2_000L)

        val result = tracker.onForegroundPackage(
            actualPackageName = "com.example.other",
            nowMillis = 5_500L
        )

        assertEquals(TrackedAppHandoffPolicy.GEMINI_PACKAGE, result.endedSession?.logicalPackageName)
        assertEquals(2_000L, result.endedSession?.startedAtMillis)
        assertEquals(5_500L, result.endedAtMillis)
        assertNull(tracker.activeSession())
    }

    @Test
    fun stoppingSessionTwiceDoesNotDoubleCredit() {
        val tracker = TrackedAppHandoffTracker(
            activeSession = ActiveTrackedAppSession(
                logicalPackageName = TrackedAppHandoffPolicy.GEMINI_PACKAGE,
                actualForegroundPackageName = TrackedAppHandoffPolicy.GOOGLE_PACKAGE,
                startedAtMillis = 2_000L
            )
        )

        val first = tracker.stopActiveSession(nowMillis = 5_000L)
        val second = tracker.stopActiveSession(nowMillis = 6_000L)

        assertEquals(TrackedAppHandoffPolicy.GEMINI_PACKAGE, first.endedSession?.logicalPackageName)
        assertNull(second.endedSession)
    }

    @Test
    fun policyDoesNotBroadlyAliasGoogleToGemini() {
        assertFalse(
            TrackedAppHandoffPolicy.isAllowedHandoff(
                logicalPackageName = "com.example.other",
                launchedPackageName = "com.example.other",
                actualPackageName = TrackedAppHandoffPolicy.GOOGLE_PACKAGE
            )
        )
        assertTrue(
            TrackedAppHandoffPolicy.isAllowedHandoff(
                logicalPackageName = TrackedAppHandoffPolicy.GEMINI_PACKAGE,
                launchedPackageName = TrackedAppHandoffPolicy.GEMINI_PACKAGE,
                actualPackageName = TrackedAppHandoffPolicy.GOOGLE_PACKAGE
            )
        )
    }

    private fun pendingGeminiLaunch(launchedAtMillis: Long): PendingTrackedAppLaunch {
        return PendingTrackedAppLaunch(
            logicalPackageName = TrackedAppHandoffPolicy.GEMINI_PACKAGE,
            launchedPackageName = TrackedAppHandoffPolicy.GEMINI_PACKAGE,
            launchedAtMillis = launchedAtMillis,
            expiresAtMillis = launchedAtMillis + TrackedAppHandoffPolicy.PENDING_LAUNCH_WINDOW_MILLIS
        )
    }
}
