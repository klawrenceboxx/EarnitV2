package com.kaleel.earnitv2

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/**
 * Tells the user when blocking has silently stopped because the Accessibility service was
 * switched off. Without this the app fails open with no signal until EarnIt is next opened.
 */
object ProtectionStatusNotifier {
    const val CHANNEL_ID = "protection_status"
    private const val NOTIFICATION_ID = 4_200

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Protection status",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts you when EarnIt can no longer enforce your Rules."
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun notifyProtectionOff(context: Context) {
        val hasEnabledRules = runCatching { EarnItRuleStore.getRules(context).any { it.enabled } }
            .getOrDefault(false)
        if (!hasEnabledRules) return
        createChannel(context)
        val contentIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Your Rules are not being enforced")
            .setContentText("Accessibility access for EarnIt is off. Tap to turn it back on.")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        // Posting fails quietly when notifications are denied; blocking state is unchanged either way.
        runCatching {
            context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        }
    }

    fun clear(context: Context) {
        runCatching {
            context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        }
    }
}
