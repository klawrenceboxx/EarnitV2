package com.kaleel.earnitv2

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object BenjaminFranklinNotificationScheduler {

    fun rescheduleAll(context: Context) {
        val prefs = BenjaminFranklinStore.getReminderPreferences(context)
        if (prefs.morningEnabled) scheduleMorning(context, prefs) else cancelMorning(context)
        if (prefs.eveningEnabled) scheduleEvening(context, prefs) else cancelEvening(context)
    }

    fun scheduleMorning(context: Context, prefs: BfReminderPreferences) {
        schedule(context, BenjaminFranklinReceiver.ACTION_MORNING_REMINDER, 100, prefs.morningHour, prefs.morningMinute)
    }

    fun scheduleEvening(context: Context, prefs: BfReminderPreferences) {
        schedule(context, BenjaminFranklinReceiver.ACTION_EVENING_REMINDER, 101, prefs.eveningHour, prefs.eveningMinute)
    }

    fun snoozeMorning(context: Context) {
        val triggerMillis = System.currentTimeMillis() + AlarmManager.INTERVAL_HOUR
        alarmManager(context).setWindow(
            AlarmManager.RTC_WAKEUP,
            triggerMillis,
            AlarmManager.INTERVAL_FIFTEEN_MINUTES,
            pendingIntent(context, BenjaminFranklinReceiver.ACTION_MORNING_REMINDER, 100)
        )
    }

    fun cancelMorning(context: Context) {
        alarmManager(context).cancel(pendingIntent(context, BenjaminFranklinReceiver.ACTION_MORNING_REMINDER, 100))
    }

    fun cancelEvening(context: Context) {
        alarmManager(context).cancel(pendingIntent(context, BenjaminFranklinReceiver.ACTION_EVENING_REMINDER, 101))
    }

    private fun schedule(context: Context, action: String, requestCode: Int, hour: Int, minute: Int) {
        alarmManager(context).setWindow(
            AlarmManager.RTC_WAKEUP,
            nextTriggerMillis(hour, minute),
            AlarmManager.INTERVAL_FIFTEEN_MINUTES,
            pendingIntent(context, action, requestCode)
        )
    }

    private fun nextTriggerMillis(hour: Int, minute: Int): Long {
        val now = LocalDateTime.now()
        var trigger = LocalDateTime.of(LocalDate.now(), LocalTime.of(hour, minute))
        if (!trigger.isAfter(now)) trigger = trigger.plusDays(1)
        return trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun pendingIntent(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, BenjaminFranklinReceiver::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun alarmManager(context: Context): AlarmManager =
        context.getSystemService(AlarmManager::class.java)
}
