package com.kaleel.earnitv2

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

class BenjaminFranklinReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_MORNING_REMINDER -> showMorningReminder(context)
            ACTION_EVENING_REMINDER -> showEveningReminder(context)
            ACTION_SNOOZE_MORNING -> BenjaminFranklinNotificationScheduler.snoozeMorning(context)
            android.content.Intent.ACTION_BOOT_COMPLETED -> BenjaminFranklinNotificationScheduler.rescheduleAll(context)
        }
    }

    private fun showMorningReminder(context: Context) {
        if (BenjaminFranklinStore.today(context) != null) return
        val openIntent = openAppIntent(context, requestCode = 0)
        val snoozeIntent = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, BenjaminFranklinReceiver::class.java).apply { action = ACTION_SNOOZE_MORNING },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        notify(
            context,
            id = MORNING_NOTIFICATION_ID,
            title = "Benjamin Franklin Mode",
            body = "What good shall you accomplish today?",
            contentIntent = openIntent,
            action = Pair("Snooze 1h", snoozeIntent)
        )
        // Reschedule for tomorrow
        BenjaminFranklinNotificationScheduler.scheduleMorning(
            context, BenjaminFranklinStore.getReminderPreferences(context)
        )
    }

    private fun showEveningReminder(context: Context) {
        val today = BenjaminFranklinStore.today(context)
        if (today != null && today.completionStatus != CompletionStatus.Pending) return
        val title = if (today != null) "Review your commitment" else "How did your day go?"
        val body = if (today != null) {
            "\"${today.commitment}\" — mark it complete or missed."
        } else {
            "Open EarnIt to reflect before the day ends."
        }
        notify(
            context,
            id = EVENING_NOTIFICATION_ID,
            title = title,
            body = body,
            contentIntent = openAppIntent(context, requestCode = 2)
        )
        // Reschedule for tomorrow
        BenjaminFranklinNotificationScheduler.scheduleEvening(
            context, BenjaminFranklinStore.getReminderPreferences(context)
        )
    }

    private fun openAppIntent(context: Context, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context, requestCode,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun notify(
        context: Context,
        id: Int,
        title: String,
        body: String,
        contentIntent: PendingIntent,
        action: Pair<String, PendingIntent>? = null
    ) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
        action?.let { (label, pi) -> builder.addAction(0, label, pi) }
        context.getSystemService(NotificationManager::class.java).notify(id, builder.build())
    }

    companion object {
        const val CHANNEL_ID = "bf_reminders"
        const val ACTION_MORNING_REMINDER = "com.kaleel.earnitv2.BF_MORNING_REMINDER"
        const val ACTION_EVENING_REMINDER = "com.kaleel.earnitv2.BF_EVENING_REMINDER"
        const val ACTION_SNOOZE_MORNING = "com.kaleel.earnitv2.BF_SNOOZE_MORNING"
        const val MORNING_NOTIFICATION_ID = 4200
        const val EVENING_NOTIFICATION_ID = 4201
    }
}
