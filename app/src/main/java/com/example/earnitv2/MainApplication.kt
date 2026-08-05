package com.kaleel.earnitv2

import android.app.Application
import android.content.Context
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import java.util.UUID

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val projectToken = BuildConfig.POSTHOG_PROJECT_TOKEN
        val host = BuildConfig.POSTHOG_HOST
        if (projectToken.isBlank()) {
            requirePostHogConfiguration("POSTHOG_PROJECT_TOKEN")
        } else if (host.isBlank()) {
            requirePostHogConfiguration("POSTHOG_HOST")
        } else {
            PostHogAndroid.setup(
                this,
                PostHogAndroidConfig(apiKey = projectToken, host = host).apply {
                    captureScreenViews = true
                    errorTrackingConfig.autoCapture = true
                }
            )
            ProductAnalytics.identify(installationId())
        }
    }

    // Reads the same UUID written by FeedbackDiagnosticsCollector so both share one stable ID.
    private fun installationId(): String {
        val prefs = getSharedPreferences("feedback_identity", Context.MODE_PRIVATE)
        return prefs.getString("installation_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("installation_id", it).apply()
        }
    }

    private fun requirePostHogConfiguration(variableName: String) {
        if (BuildConfig.DEBUG) {
            error("$variableName is missing. PostHog events will be silently dropped until it is configured.")
        }
    }
}
