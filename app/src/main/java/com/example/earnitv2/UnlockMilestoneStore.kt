package com.kaleel.earnitv2

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/**
 * Tracks lifetime device unlocks (ACTION_USER_PRESENT) and fires a one-shot
 * notification when the count crosses a milestone threshold.
 *
 * Counter design:
 *   - Lifetime, never resets. Milestones are achievements, not daily stats.
 *   - Each milestone fires exactly once (guarded by a persisted boolean).
 *   - Non-milestone unlocks are counted but produce no notification.
 *
 * Notification channel: reuses BenjaminFranklinReceiver.CHANNEL_ID ("bf_reminders"),
 * which is already created at IMPORTANCE_DEFAULT in MainApplication.
 */
object UnlockMilestoneStore {

    val MILESTONES = setOf(5, 10, 25, 50, 100, 200)

    private const val PREFS = "unlock_milestones"
    private const val KEY_COUNT = "lifetime_unlock_count"
    private const val KEY_FIRED_PREFIX = "milestone_fired_"

    // Notification IDs are NOTIFICATION_ID_BASE + milestone value (e.g. 5005, 5010, 5025…)
    // so they never collide with BF notification IDs (4200, 4201).
    private const val NOTIFICATION_ID_BASE = 5000

    /**
     * Call on every ACTION_USER_PRESENT broadcast.
     * Increments the lifetime counter; fires a milestone notification if this
     * unlock lands exactly on a threshold and that threshold has not fired before.
     */
    fun onUnlock(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val newCount = prefs.getInt(KEY_COUNT, 0) + 1
        prefs.edit().putInt(KEY_COUNT, newCount).apply()

        if (newCount in MILESTONES) {
            val firedKey = KEY_FIRED_PREFIX + newCount
            if (!prefs.getBoolean(firedKey, false)) {
                prefs.edit().putBoolean(firedKey, true).apply()
                fireNotification(context, newCount)
            }
        }
    }

    /** Returns the current lifetime unlock count without modifying it. */
    fun getCount(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_COUNT, 0)

    private fun fireNotification(context: Context, count: Int) {
        val (title, body) = milestoneNotificationCopy(count)
        val contentIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_BASE + count,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, BenjaminFranklinReceiver.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID_BASE + count, notification)
    }

    private fun milestoneNotificationCopy(count: Int): Pair<String, String> = when (count) {
        5 -> "First five" to
                "EarnIt is on the job. Five unlocks in — your Rules are running."
        10 -> "Ten unlocks" to
                "Ten phone unlocks with your focus tools active. You're building a habit."
        25 -> "25 unlocks" to
                "You're building real awareness. EarnIt has been there for all 25 of these unlocks."
        50 -> "Halfway to 100" to
                "Fifty unlocks with EarnIt guarding your time. That's a real habit forming."
        100 -> "100 unlocks" to
                "A hundred unlocks with EarnIt running. You're one of the few who sticks with it."
        200 -> "200 unlocks" to
                "Two hundred unlocks, still using EarnIt. You've made it part of your routine."
        else -> "Milestone reached" to "You've hit $count unlocks with EarnIt active."
    }
}
