package com.example.earnitv2

import android.content.Context

object EarnItPauseStore {
    private const val PREFS_NAME = "earnit_rule_pauses"

    fun pauseUntil(context: Context, ruleId: String, expiresAtMillis: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(ruleId, expiresAtMillis)
            .apply()
    }

    fun clearPause(context: Context, ruleId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(ruleId)
            .apply()
    }

    fun pauseExpirations(context: Context): Map<String, Long> {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .all
            .mapNotNull { (ruleId, value) ->
                (value as? Long)?.let { ruleId to it }
            }
            .toMap()
    }
}
