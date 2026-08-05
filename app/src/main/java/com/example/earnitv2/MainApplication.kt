package com.kaleel.earnitv2

import android.app.Application
import android.content.Context
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import java.util.UUID

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PostHogAndroid.setup(
            this,
            PostHogAndroidConfig(
                apiKey = "phc_xkRMrxG2F7PWh24BtpWPDxLgqZRdtY6hCnfaeHyAV3vT",
                host = "https://us.i.posthog.com"
            ).apply {
                captureScreenViews = true
            }
        )
        ProductAnalytics.identify(installationId())
    }

    // Reads the same UUID written by FeedbackDiagnosticsCollector so both share one stable ID.
    private fun installationId(): String {
        val prefs = getSharedPreferences("feedback_identity", Context.MODE_PRIVATE)
        return prefs.getString("installation_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("installation_id", it).apply()
        }
    }
}
