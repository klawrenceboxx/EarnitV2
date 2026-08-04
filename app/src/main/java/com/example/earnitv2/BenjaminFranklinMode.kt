package com.example.earnitv2

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

enum class CompletionStatus { Pending, Completed, Missed }

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
        return DailyCommitment(
            date = date,
            commitment = commitment,
            estimatedMinutes = values.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
            importance = values.getOrNull(2)?.takeIf { it.isNotBlank() },
            completionStatus = values.getOrNull(3)?.let { runCatching { CompletionStatus.valueOf(it) }.getOrNull() }
                ?: CompletionStatus.Pending,
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

    private fun save(context: Context, record: DailyCommitment) {
        val raw = listOf(
            record.commitment.replace(SEPARATOR, " "), record.estimatedMinutes.toString(),
            record.importance.orEmpty().replace(SEPARATOR, " "), record.completionStatus.name,
            record.reflection.orEmpty().replace(SEPARATOR, " "), record.createdAtMillis.toString(),
            record.completedAtMillis?.toString().orEmpty()
        ).joinToString(SEPARATOR)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(PREFIX + record.date, raw).apply()
    }
}
