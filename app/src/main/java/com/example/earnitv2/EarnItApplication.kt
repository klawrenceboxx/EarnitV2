package com.example.earnitv2

import android.app.Application
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig

class EarnItApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val projectToken = BuildConfig.POSTHOG_PROJECT_TOKEN
        val host = BuildConfig.POSTHOG_HOST
        if (projectToken.isBlank()) {
            requirePostHogConfiguration("POSTHOG_PROJECT_TOKEN")
            return
        }
        if (host.isBlank()) {
            requirePostHogConfiguration("POSTHOG_HOST")
            return
        }

        PostHogAndroid.setup(
            this,
            PostHogAndroidConfig(
                apiKey = projectToken,
                host = host
            ).apply {
                errorTrackingConfig.autoCapture = true
            }
        )
    }

    private fun requirePostHogConfiguration(variableName: String) {
        if (BuildConfig.DEBUG) {
            error("$variableName variable required by PostHog is missing or un-configured, this causes events to be silently missed. This error stops appearing once $variableName is configured")
        }
    }
}
