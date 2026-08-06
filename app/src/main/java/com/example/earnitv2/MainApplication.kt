package com.kaleel.earnitv2

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import java.util.UUID

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
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

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            BenjaminFranklinReceiver.CHANNEL_ID,
            "Daily Commitment Reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Morning and evening reminders for Benjamin Franklin Mode commitments."
        }
        manager.createNotificationChannel(channel)
    }

    private fun requirePostHogConfiguration(variableName: String) {
        if (BuildConfig.DEBUG) {
            error("$variableName is missing. PostHog events will be silently dropped until it is configured.")
        }
    }
}
