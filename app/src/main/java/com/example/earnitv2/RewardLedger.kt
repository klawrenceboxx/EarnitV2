package com.example.earnitv2

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RewardLedger {
    private const val PREFS_NAME = "earnit_reward_ledger"
    private const val KEY_ACCOUNTING_DAY = "accounting_day"
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
    fun creditProductiveUsage(context: Context, productiveUsageSecondsToday: Long): Snapshot {
        val prefs = currentDayPrefs(context)
        val creditedSeconds = prefs.getLong(KEY_PRODUCTIVE_CREDITED_SECONDS, 0L)
        val rewardIssuedSeconds = prefs.getLong(KEY_REWARD_ISSUED_SECONDS, 0L)
        val rewardConsumedSeconds = prefs.getLong(KEY_REWARD_CONSUMED_SECONDS, 0L)
        val safeProductiveSeconds = productiveUsageSecondsToday.coerceAtLeast(0L)
        val newlyEarnedSeconds = (safeProductiveSeconds - creditedSeconds).coerceAtLeast(0L)

        if (newlyEarnedSeconds <= 0L) {
            return Snapshot(
                productiveCreditedSeconds = creditedSeconds,
                rewardIssuedSeconds = rewardIssuedSeconds,
                rewardConsumedSeconds = rewardConsumedSeconds
            )
        }

        val updatedCreditedSeconds = safeProductiveSeconds
        val updatedRewardIssuedSeconds = rewardIssuedSeconds + issueRewardSeconds(newlyEarnedSeconds)
        prefs.edit()
            .putLong(KEY_PRODUCTIVE_CREDITED_SECONDS, updatedCreditedSeconds)
            .putLong(KEY_REWARD_ISSUED_SECONDS, updatedRewardIssuedSeconds)
            .commit()

        return Snapshot(
            productiveCreditedSeconds = updatedCreditedSeconds,
            rewardIssuedSeconds = updatedRewardIssuedSeconds,
            rewardConsumedSeconds = rewardConsumedSeconds
        )
    }

    @Synchronized
    fun consumeRewardSeconds(context: Context, consumedSeconds: Long): Snapshot {
        val prefs = currentDayPrefs(context)
        val productiveCreditedSeconds = prefs.getLong(KEY_PRODUCTIVE_CREDITED_SECONDS, 0L)
        val rewardIssuedSeconds = prefs.getLong(KEY_REWARD_ISSUED_SECONDS, 0L)
        val rewardConsumedSeconds = prefs.getLong(KEY_REWARD_CONSUMED_SECONDS, 0L)
        val remainingRewardSeconds = (rewardIssuedSeconds - rewardConsumedSeconds).coerceAtLeast(0L)
        val secondsToConsume = consumedSeconds.coerceAtLeast(0L).coerceAtMost(remainingRewardSeconds)

        if (secondsToConsume <= 0L) {
            return Snapshot(
                productiveCreditedSeconds = productiveCreditedSeconds,
                rewardIssuedSeconds = rewardIssuedSeconds,
                rewardConsumedSeconds = rewardConsumedSeconds
            )
        }

        val updatedRewardConsumedSeconds = rewardConsumedSeconds + secondsToConsume
        prefs.edit()
            .putLong(KEY_REWARD_CONSUMED_SECONDS, updatedRewardConsumedSeconds)
            .commit()

        return Snapshot(
            productiveCreditedSeconds = productiveCreditedSeconds,
            rewardIssuedSeconds = rewardIssuedSeconds,
            rewardConsumedSeconds = updatedRewardConsumedSeconds
        )
    }

    @Synchronized
    fun snapshot(context: Context): Snapshot {
        val prefs = currentDayPrefs(context)
        return Snapshot(
            productiveCreditedSeconds = prefs.getLong(KEY_PRODUCTIVE_CREDITED_SECONDS, 0L),
            rewardIssuedSeconds = prefs.getLong(KEY_REWARD_ISSUED_SECONDS, 0L),
            rewardConsumedSeconds = prefs.getLong(KEY_REWARD_CONSUMED_SECONDS, 0L)
        )
    }

    private fun currentDayPrefs(context: Context): SharedPreferences {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = todayKey()
        if (prefs.getString(KEY_ACCOUNTING_DAY, null) != today) {
            prefs.edit()
                .putString(KEY_ACCOUNTING_DAY, today)
                .putLong(KEY_PRODUCTIVE_CREDITED_SECONDS, 0L)
                .putLong(KEY_REWARD_ISSUED_SECONDS, 0L)
                .putLong(KEY_REWARD_CONSUMED_SECONDS, 0L)
                .commit()
        }
        return prefs
    }

    private fun issueRewardSeconds(productiveSeconds: Long): Long {
        return productiveSeconds
    }

    private fun todayKey(): String {
        return SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    }
}
