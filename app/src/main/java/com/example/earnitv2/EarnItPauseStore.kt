package com.kaleel.earnitv2

import android.content.Context

object EarnItPauseStore {
    private const val PREFS_NAME = "earnit_rule_pauses"
    private const val REASON_SUFFIX = ":reason"

    fun pauseUntil(context: Context, ruleId: String, expiresAtMillis: Long, reason: String? = null) {
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putLong(ruleId, expiresAtMillis)
        if (reason.isNullOrBlank()) {
            editor.remove(reasonKey(ruleId))
        } else {
            editor.putString(reasonKey(ruleId), reason)
        }
        editor
            .apply()
    }

    fun clearPause(context: Context, ruleId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(ruleId)
            .remove(reasonKey(ruleId))
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

    fun expiredRuleIds(context: Context, nowMillis: Long = System.currentTimeMillis()): Set<String> {
        return pauseExpirations(context)
            .filterValues { it <= nowMillis }
            .keys
    }

    private fun reasonKey(ruleId: String): String = "$ruleId$REASON_SUFFIX"
}
