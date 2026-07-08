package com.example.earnitv2

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object RewardLedger {
    private const val PREFS_NAME = "earnit_reward_ledger"
    private const val KEY_ACCOUNTING_DAY = "accounting_day"
    private const val KEY_RULE_SIGNATURE = "rule_signature"
    private const val KEY_PRODUCTIVE_CREDITED_SECONDS = "productive_credited_seconds"
    private const val KEY_REWARD_ISSUED_SECONDS = "reward_issued_seconds"
    private const val KEY_REWARD_CONSUMED_SECONDS = "reward_consumed_seconds"

    data class Snapshot(
        val productiveCreditedSeconds: Long,
        val rewardIssuedSeconds: Long,
        val rewardConsumedSeconds: Long
    ) {
        val remainingRewardSeconds: Long = (rewardIssuedSeconds - rewardConsumedSeconds).coerceAtLeast(0L)
        val isUnlocked: Boolean = remainingRewardSeconds > 0L
    }

    @Synchronized
    fun creditProductiveUsage(
        context: Context,
        rule: EarnItRuleStore.Rule,
        productiveUsageSecondsToday: Long
    ): Snapshot {
        val prefs = currentPrefs(context, rule)
        val creditedSeconds = prefs.getLong(ruleKey(rule, KEY_PRODUCTIVE_CREDITED_SECONDS), 0L)
        val rewardIssuedSeconds = prefs.getLong(ruleKey(rule, KEY_REWARD_ISSUED_SECONDS), 0L)
        val rewardConsumedSeconds = prefs.getLong(ruleKey(rule, KEY_REWARD_CONSUMED_SECONDS), 0L)
        val safeProductiveSeconds = productiveUsageSecondsToday.coerceAtLeast(0L)
        val newlyEarnedSeconds = (safeProductiveSeconds - creditedSeconds).coerceAtLeast(0L)

        if (newlyEarnedSeconds <= 0L) {
            return Snapshot(creditedSeconds, rewardIssuedSeconds, rewardConsumedSeconds)
        }

        val updatedRewardIssuedSeconds = rewardIssuedSeconds + issueRewardSeconds(newlyEarnedSeconds, rule)
        prefs.edit()
            .putLong(ruleKey(rule, KEY_PRODUCTIVE_CREDITED_SECONDS), safeProductiveSeconds)
            .putLong(ruleKey(rule, KEY_REWARD_ISSUED_SECONDS), updatedRewardIssuedSeconds)
            .commit()

        return Snapshot(safeProductiveSeconds, updatedRewardIssuedSeconds, rewardConsumedSeconds)
    }

    @Synchronized
    fun consumeRewardSeconds(
        context: Context,
        rule: EarnItRuleStore.Rule,
        consumedSeconds: Long
    ): Snapshot {
        val prefs = currentPrefs(context, rule)
        val productiveCreditedSeconds = prefs.getLong(ruleKey(rule, KEY_PRODUCTIVE_CREDITED_SECONDS), 0L)
        val rewardIssuedSeconds = prefs.getLong(ruleKey(rule, KEY_REWARD_ISSUED_SECONDS), 0L)
        val rewardConsumedSeconds = prefs.getLong(ruleKey(rule, KEY_REWARD_CONSUMED_SECONDS), 0L)
        val remainingRewardSeconds = (rewardIssuedSeconds - rewardConsumedSeconds).coerceAtLeast(0L)
        val secondsToConsume = consumedSeconds.coerceAtLeast(0L).coerceAtMost(remainingRewardSeconds)

        if (secondsToConsume <= 0L) {
            return Snapshot(productiveCreditedSeconds, rewardIssuedSeconds, rewardConsumedSeconds)
        }

        val updatedRewardConsumedSeconds = rewardConsumedSeconds + secondsToConsume
        prefs.edit()
            .putLong(ruleKey(rule, KEY_REWARD_CONSUMED_SECONDS), updatedRewardConsumedSeconds)
            .commit()

        return Snapshot(productiveCreditedSeconds, rewardIssuedSeconds, updatedRewardConsumedSeconds)
    }

    @Synchronized
    fun snapshot(context: Context, rule: EarnItRuleStore.Rule): Snapshot {
        val prefs = currentPrefs(context, rule)
        return Snapshot(
            productiveCreditedSeconds = prefs.getLong(ruleKey(rule, KEY_PRODUCTIVE_CREDITED_SECONDS), 0L),
            rewardIssuedSeconds = prefs.getLong(ruleKey(rule, KEY_REWARD_ISSUED_SECONDS), 0L),
            rewardConsumedSeconds = prefs.getLong(ruleKey(rule, KEY_REWARD_CONSUMED_SECONDS), 0L)
        )
    }

    fun activeProductiveUsageSecondsToday(
        usageStatsManager: UsageStatsManager,
        rule: EarnItRuleStore.Rule
    ): Long {
        val now = System.currentTimeMillis()
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val events = usageStatsManager.queryEvents(startOfDay, now)
        val event = UsageEvents.Event()
        var foregroundStartedAt: Long? = null
        var totalMillis = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName != rule.productivePackage) continue

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED, UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    foregroundStartedAt = event.timeStamp
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val startedAt = foregroundStartedAt
                    if (startedAt != null && event.timeStamp > startedAt) {
                        totalMillis += activeOverlapMillis(startedAt, event.timeStamp, rule)
                    }
                    foregroundStartedAt = null
                }
            }
        }

        val startedAt = foregroundStartedAt
        if (startedAt != null && now > startedAt) {
            totalMillis += activeOverlapMillis(startedAt, now, rule)
        }

        return totalMillis / 1_000L
    }

    private fun activeOverlapMillis(startMillis: Long, endMillis: Long, rule: EarnItRuleStore.Rule): Long {
        if (endMillis <= startMillis) return 0L
        var total = 0L
        val cursor = Calendar.getInstance().apply { timeInMillis = startMillis }
        while (cursor.timeInMillis < endMillis) {
            val day = cursor.toEarnItDay()
            val minute = cursor.get(Calendar.HOUR_OF_DAY) * 60 + cursor.get(Calendar.MINUTE)
            val nextMinute = cursor.clone() as Calendar
            nextMinute.set(Calendar.SECOND, 0)
            nextMinute.set(Calendar.MILLISECOND, 0)
            nextMinute.add(Calendar.MINUTE, 1)
            val segmentEnd = minOf(endMillis, nextMinute.timeInMillis)
            if (rule.isActiveAt(day, minute)) {
                total += segmentEnd - cursor.timeInMillis
            }
            cursor.timeInMillis = segmentEnd
        }
        return total
    }

    private fun currentPrefs(context: Context, rule: EarnItRuleStore.Rule): SharedPreferences {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = todayKey()
        val dayKey = ruleKey(rule, KEY_ACCOUNTING_DAY)
        val signatureKey = ruleKey(rule, KEY_RULE_SIGNATURE)
        val ruleSignature = rule.signature()
        if (!prefs.contains(dayKey)) {
            migrateLegacyLedgerIfPresent(prefs, rule, today, ruleSignature)
        }
        if (prefs.getString(dayKey, null) != today || prefs.getString(signatureKey, null) != ruleSignature) {
            prefs.edit()
                .putString(dayKey, today)
                .putString(signatureKey, ruleSignature)
                .putLong(ruleKey(rule, KEY_PRODUCTIVE_CREDITED_SECONDS), 0L)
                .putLong(ruleKey(rule, KEY_REWARD_ISSUED_SECONDS), 0L)
                .putLong(ruleKey(rule, KEY_REWARD_CONSUMED_SECONDS), 0L)
                .commit()
        }
        return prefs
    }

    private fun migrateLegacyLedgerIfPresent(
        prefs: SharedPreferences,
        rule: EarnItRuleStore.Rule,
        today: String,
        ruleSignature: String
    ) {
        val legacyDay = prefs.getString(KEY_ACCOUNTING_DAY, null)
        if (rule.id != "default" || legacyDay != today) return

        prefs.edit()
            .putString(ruleKey(rule, KEY_ACCOUNTING_DAY), legacyDay)
            .putString(ruleKey(rule, KEY_RULE_SIGNATURE), ruleSignature)
            .putLong(
                ruleKey(rule, KEY_PRODUCTIVE_CREDITED_SECONDS),
                prefs.getLong(KEY_PRODUCTIVE_CREDITED_SECONDS, 0L)
            )
            .putLong(
                ruleKey(rule, KEY_REWARD_ISSUED_SECONDS),
                prefs.getLong(KEY_REWARD_ISSUED_SECONDS, 0L)
            )
            .putLong(
                ruleKey(rule, KEY_REWARD_CONSUMED_SECONDS),
                prefs.getLong(KEY_REWARD_CONSUMED_SECONDS, 0L)
            )
            .commit()
    }

    private fun issueRewardSeconds(productiveSeconds: Long, rule: EarnItRuleStore.Rule): Long {
        return productiveSeconds * rule.rewardSecondsPerProductiveSecond
    }

    private fun ruleKey(rule: EarnItRuleStore.Rule, key: String): String {
        return "${rule.id}_$key"
    }

    private fun EarnItRuleStore.Rule.signature(): String {
        val blockedRuleId = blockedApps.map { it.packageName }.sorted().joinToString(",")
        val activeDaysId = activeDays.sorted().joinToString(",")
        return "$productivePackage|$blockedRuleId|$rewardSecondsPerProductiveSecond|$activeDaysId|$startMinute|$endMinute"
    }

    private fun todayKey(): String {
        return SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    }

    private fun Calendar.toEarnItDay(): Int {
        return when (get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> 7
        }
    }
}
