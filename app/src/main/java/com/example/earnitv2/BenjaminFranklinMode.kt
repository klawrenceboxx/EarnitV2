package com.kaleel.earnitv2

import android.content.Context
import java.time.LocalDate

data class DailyCommitment(
    val date: LocalDate,
    val commitment: String,
    val estimatedMinutes: Int,
    val importance: String?,
    val completionStatus: CompletionStatus = CompletionStatus.Pending,
    val reflection: String? = null,
    val createdAtMillis: Long,
    val completedAtMillis: Long? = null
)

enum class CompletionStatus { Pending, Completed, Missed, NotReviewed }

data class BfReminderPreferences(
    val morningEnabled: Boolean = false,
    val morningHour: Int = 8,
    val morningMinute: Int = 0,
    val eveningEnabled: Boolean = false,
    val eveningHour: Int = 20,
    val eveningMinute: Int = 0
)

object BenjaminFranklinStore {
    private const val PREFS = "benjamin_franklin_mode"
    private const val PREFIX = "commitment_"
    private const val SEPARATOR = "\u001F"

    fun today(context: Context): DailyCommitment? = get(context, LocalDate.now())

    fun get(context: Context, date: LocalDate): DailyCommitment? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PREFIX + date, null) ?: return null
        val values = raw.split(SEPARATOR)
        val commitment = values.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
        val storedStatus = values.getOrNull(3)
            ?.let { runCatching { CompletionStatus.valueOf(it) }.getOrNull() }
            ?: CompletionStatus.Pending
        // Past entries left as Pending (e.g. app was uninstalled/cleared) are treated as NotReviewed.
        val resolvedStatus = if (storedStatus == CompletionStatus.Pending && date.isBefore(LocalDate.now())) {
            CompletionStatus.NotReviewed
        } else {
            storedStatus
        }
        return DailyCommitment(
            date = date,
            commitment = commitment,
            estimatedMinutes = values.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
            importance = values.getOrNull(2)?.takeIf { it.isNotBlank() },
            completionStatus = resolvedStatus,
            reflection = values.getOrNull(4)?.takeIf { it.isNotBlank() },
            createdAtMillis = values.getOrNull(5)?.toLongOrNull() ?: 0L,
            completedAtMillis = values.getOrNull(6)?.toLongOrNull()
        )
    }

    fun saveToday(context: Context, commitment: String, estimatedMinutes: Int, importance: String?) {
        val date = LocalDate.now()
        val record = DailyCommitment(
            date = date,
            commitment = commitment.trim(),
            estimatedMinutes = estimatedMinutes.coerceAtLeast(1),
            importance = importance?.trim()?.takeIf { it.isNotBlank() },
            createdAtMillis = System.currentTimeMillis()
        )
        save(context, record)
    }

    fun reviewToday(context: Context, completed: Boolean, reflection: String?) {
        val current = today(context) ?: return
        save(context, current.copy(
            completionStatus = if (completed) CompletionStatus.Completed else CompletionStatus.Missed,
            reflection = reflection?.trim()?.takeIf { it.isNotBlank() },
            completedAtMillis = System.currentTimeMillis()
        ))
    }

    fun getReminderPreferences(context: Context): BfReminderPreferences {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return BfReminderPreferences(
            morningEnabled = prefs.getBoolean("reminder_morning_enabled", false),
            morningHour = prefs.getInt("reminder_morning_hour", 8),
            morningMinute = prefs.getInt("reminder_morning_minute", 0),
            eveningEnabled = prefs.getBoolean("reminder_evening_enabled", false),
            eveningHour = prefs.getInt("reminder_evening_hour", 20),
            eveningMinute = prefs.getInt("reminder_evening_minute", 0)
        )
    }

    fun saveReminderPreferences(context: Context, prefs: BfReminderPreferences) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("reminder_morning_enabled", prefs.morningEnabled)
            .putInt("reminder_morning_hour", prefs.morningHour)
            .putInt("reminder_morning_minute", prefs.morningMinute)
            .putBoolean("reminder_evening_enabled", prefs.eveningEnabled)
            .putInt("reminder_evening_hour", prefs.eveningHour)
            .putInt("reminder_evening_minute", prefs.eveningMinute)
            .apply()
    }

    private fun save(context: Context, record: DailyCommitment) {
        val raw = listOf(
            record.commitment.replace(SEPARATOR, " "), record.estimatedMinutes.toString(),
            record.importance.orEmpty().replace(SEPARATOR, " "), record.completionStatus.name,
            record.reflection.orEmpty().replace(SEPARATOR, " "), record.createdAtMillis.toString(),
            record.completedAtMillis?.toString().orEmpty()
        ).joinToString(SEPARATOR)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(PREFIX + record.date, raw).apply()
    }
}
