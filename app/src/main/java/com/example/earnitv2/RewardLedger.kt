package com.example.earnitv2

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RewardLedger {
    private const val PREFS_NAME = "earnit_reward_ledger"
    private const val KEY_ACCOUNTING_DAY = "accounting_day"
    private const val KEY_RULE_ID = "rule_id"
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
        val creditedSeconds = prefs.getLong(KEY_PRODUCTIVE_CREDITED_SECONDS, 0L)
        val rewardIssuedSeconds = prefs.getLong(KEY_REWARD_ISSUED_SECONDS, 0L)
        val rewardConsumedSeconds = prefs.getLong(KEY_REWARD_CONSUMED_SECONDS, 0L)
        val safeProductiveSeconds = productiveUsageSecondsToday.coerceAtLeast(0L)
        val newlyEarnedSeconds = (safeProductiveSeconds - creditedSeconds).coerceAtLeast(0L)

        if (newlyEarnedSeconds <= 0L) {
            return Snapshot(creditedSeconds, rewardIssuedSeconds, rewardConsumedSeconds)
        }

        val updatedRewardIssuedSeconds = rewardIssuedSeconds + issueRewardSeconds(newlyEarnedSeconds, rule)
        prefs.edit()
            .putLong(KEY_PRODUCTIVE_CREDITED_SECONDS, safeProductiveSeconds)
            .putLong(KEY_REWARD_ISSUED_SECONDS, updatedRewardIssuedSeconds)
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
        val productiveCreditedSeconds = prefs.getLong(KEY_PRODUCTIVE_CREDITED_SECONDS, 0L)
        val rewardIssuedSeconds = prefs.getLong(KEY_REWARD_ISSUED_SECONDS, 0L)
        val rewardConsumedSeconds = prefs.getLong(KEY_REWARD_CONSUMED_SECONDS, 0L)
        val remainingRewardSeconds = (rewardIssuedSeconds - rewardConsumedSeconds).coerceAtLeast(0L)
        val secondsToConsume = consumedSeconds.coerceAtLeast(0L).coerceAtMost(remainingRewardSeconds)

        if (secondsToConsume <= 0L) {
            return Snapshot(productiveCreditedSeconds, rewardIssuedSeconds, rewardConsumedSeconds)
        }

        val updatedRewardConsumedSeconds = rewardConsumedSeconds + secondsToConsume
        prefs.edit()
            .putLong(KEY_REWARD_CONSUMED_SECONDS, updatedRewardConsumedSeconds)
            .commit()

        return Snapshot(productiveCreditedSeconds, rewardIssuedSeconds, updatedRewardConsumedSeconds)
    }

    @Synchronized
    fun snapshot(context: Context, rule: EarnItRuleStore.Rule): Snapshot {
        val prefs = currentPrefs(context, rule)
        return Snapshot(
            productiveCreditedSeconds = prefs.getLong(KEY_PRODUCTIVE_CREDITED_SECONDS, 0L),
            rewardIssuedSeconds = prefs.getLong(KEY_REWARD_ISSUED_SECONDS, 0L),
            rewardConsumedSeconds = prefs.getLong(KEY_REWARD_CONSUMED_SECONDS, 0L)
        )
    }

    private fun currentPrefs(context: Context, rule: EarnItRuleStore.Rule): SharedPreferences {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = todayKey()
        val ruleId = rule.id()
        if (prefs.getString(KEY_ACCOUNTING_DAY, null) != today || prefs.getString(KEY_RULE_ID, null) != ruleId) {
            prefs.edit()
                .putString(KEY_ACCOUNTING_DAY, today)
                .putString(KEY_RULE_ID, ruleId)
                .putLong(KEY_PRODUCTIVE_CREDITED_SECONDS, 0L)
                .putLong(KEY_REWARD_ISSUED_SECONDS, 0L)
                .putLong(KEY_REWARD_CONSUMED_SECONDS, 0L)
                .commit()
        }
        return prefs
    }

    private fun issueRewardSeconds(productiveSeconds: Long, rule: EarnItRuleStore.Rule): Long {
        return productiveSeconds * rule.rewardSecondsPerProductiveSecond
    }

    private fun EarnItRuleStore.Rule.id(): String {
        return "$productivePackage|$blockedPackage|$rewardSecondsPerProductiveSecond"
    }

    private fun todayKey(): String {
        return SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    }
}
