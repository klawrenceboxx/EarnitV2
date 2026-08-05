package com.kaleel.earnitv2

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.posthog.PostHog

/**
 * Thin wrapper around PostHog and Firebase Crashlytics.
 * Call [identify] once at startup (done by MainApplication) and [capture] anywhere.
 */
object ProductAnalytics {
    /** Sets the distinct ID in PostHog and the user ID in Crashlytics. */
    fun identify(installationId: String) {
        PostHog.identify(installationId)
        FirebaseCrashlytics.getInstance().setUserId(installationId)
    }

    /** Sends a named event with optional properties to PostHog. */
    fun capture(event: String, properties: Map<String, Any> = emptyMap()) {
        PostHog.capture(event, properties = properties)
    }
}
