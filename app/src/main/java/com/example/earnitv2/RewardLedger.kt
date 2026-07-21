package com.example.earnitv2

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.util.Log
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
    private const val KEY_REQUIREMENT_PROGRESS_SECONDS = "requirement_progress_seconds"
    private const val KEY_TRACKED_HANDOFF_SECONDS = "tracked_handoff_seconds"
    private const val KEY_TRACKED_HANDOFF_CREDIT_CURSOR = "tracked_handoff_credit_cursor"
    private const val KEY_DEEP_WORK_CREDITED_SECONDS = "deep_work_credited_seconds"

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
        return rule.earnAppPackages.sumOf { packageName ->
            activeAppUsageSecondsToday(usageStatsManager, rule, packageName)
        }
    }

    internal data class TrackedHandoffCreditDecision(
        val creditStartMillis: Long,
        val activeSeconds: Long,
        val rejectionReason: String?
    )

    @Synchronized
    fun creditDeepWork(context: Context, rule: EarnItRuleStore.Rule, elapsedSeconds: Long): Snapshot {
        val prefs = currentPrefs(context, rule)
        val key = ruleKey(rule, KEY_DEEP_WORK_CREDITED_SECONDS)
        val credited = prefs.getLong(key, 0L)
        val completed = (elapsedSeconds.coerceAtLeast(0L) / 600L) * 600L
        val newlyCredited = (completed - credited).coerceAtLeast(0L)
        val current = snapshot(context, rule)
        if (newlyCredited == 0L) return current
        val issued = current.rewardIssuedSeconds + issueRewardSeconds(newlyCredited, rule)
        prefs.edit().putLong(key, completed)
            .putLong(ruleKey(rule, KEY_REWARD_ISSUED_SECONDS), issued).commit()
        return Snapshot(current.productiveCreditedSeconds, issued, current.rewardConsumedSeconds)
    }

    fun activeAppUsageSecondsToday(
        usageStatsManager: UsageStatsManager,
        rule: EarnItRuleStore.Rule,
        packageName: String
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
            if (event.packageName != packageName) continue

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

    fun activeProductiveUsageSecondsToday(
        context: Context,
        usageStatsManager: UsageStatsManager,
        rule: EarnItRuleStore.Rule
    ): Long {
        return rule.earnAppPackages.sumOf { packageName ->
            activeAppUsageSecondsToday(context, usageStatsManager, rule, packageName)
        }
    }

    fun activeAppUsageSecondsToday(
        context: Context,
        usageStatsManager: UsageStatsManager,
        rule: EarnItRuleStore.Rule,
        packageName: String
    ): Long {
        return activeAppUsageSecondsToday(usageStatsManager, rule, packageName) +
            trackedHandoffSeconds(context, rule, packageName)
    }

    @Synchronized
    fun creditCompletionProgress(
        context: Context,
        rule: EarnItRuleStore.Rule,
        usageStatsManager: UsageStatsManager
    ): Map<String, Long> {
        val prefs = currentPrefs(context, rule)
        val updates = rule.requirements.associate { requirement ->
            val progress = activeAppUsageSecondsToday(usageStatsManager, rule, requirement.app.packageName)
                .coerceAtLeast(0L)
            requirement.app.packageName to progress
        }
        val editor = prefs.edit()
        updates.forEach { (packageName, progress) ->
            editor.putLong(requirementKey(rule, packageName), progress)
        }
        editor.commit()
        return updates
    }

    @Synchronized
    fun creditCompletionProgress(
        context: Context,
        rule: EarnItRuleStore.Rule,
        usageStatsManager: UsageStatsManager,
        includeTrackedHandoffs: Boolean
    ): Map<String, Long> {
        if (!includeTrackedHandoffs) {
            return creditCompletionProgress(context, rule, usageStatsManager)
        }

        val prefs = currentPrefs(context, rule)
        val updates = rule.requirements.associate { requirement ->
            val progress = activeAppUsageSecondsToday(context, usageStatsManager, rule, requirement.app.packageName)
                .coerceAtLeast(0L)
            requirement.app.packageName to progress
        }
        val editor = prefs.edit()
        updates.forEach { (packageName, progress) ->
            editor.putLong(requirementKey(rule, packageName), progress)
        }
        editor.commit()
        return updates
    }

    @Synchronized
    fun creditTrackedAppHandoff(
        context: Context,
        rules: List<EarnItRuleStore.Rule>,
        logicalPackageName: String,
        startedAtMillis: Long,
        endedAtMillis: Long
    ) {
        if (endedAtMillis <= startedAtMillis) {
            logTrackedHandoffCredit(context, null, logicalPackageName, startedAtMillis, endedAtMillis, null, startedAtMillis, 0L, "invalid-interval")
            return
        }
        rules.filter { it.enabled }.forEach { rule ->
            val shouldCredit = when (rule.type) {
                EarnItRuleStore.RuleType.EarnRewardTime -> logicalPackageName in rule.earnAppPackages
                EarnItRuleStore.RuleType.CompleteToUnlock -> rule.requirements.any {
                    it.app.packageName == logicalPackageName
                }
                EarnItRuleStore.RuleType.ScheduledBlock -> false
            }
            if (!shouldCredit) return@forEach

            val prefs = currentPrefs(context, rule)
            val cursorKey = trackedHandoffCursorKey(rule, logicalPackageName)
            val creditCursor = prefs.getLong(cursorKey, startedAtMillis)
            val decision = trackedHandoffCreditDecision(
                rule = rule,
                startedAtMillis = startedAtMillis,
                endedAtMillis = endedAtMillis,
                creditCursor = creditCursor
            )
            if (decision.rejectionReason == "duplicate-interval") {
                logTrackedHandoffCredit(context, rule, logicalPackageName, startedAtMillis, endedAtMillis, creditCursor, decision.creditStartMillis, 0L, "duplicate-interval")
                return@forEach
            }
            val key = trackedHandoffKey(rule, logicalPackageName)
            val updated = prefs.getLong(key, 0L) + decision.activeSeconds
            prefs.edit()
                .putLong(cursorKey, endedAtMillis)
                .putLong(key, updated)
                .commit()
            logTrackedHandoffCredit(
                context = context,
                rule = rule,
                logicalPackageName = logicalPackageName,
                startedAtMillis = startedAtMillis,
                endedAtMillis = endedAtMillis,
                creditCursor = creditCursor,
                creditStartMillis = decision.creditStartMillis,
                creditedSeconds = decision.activeSeconds,
                rejectionReason = decision.rejectionReason
            )
        }
    }

    internal fun trackedHandoffCreditDecision(
        rule: EarnItRuleStore.Rule,
        startedAtMillis: Long,
        endedAtMillis: Long,
        creditCursor: Long
    ): TrackedHandoffCreditDecision {
        val creditStartMillis = maxOf(startedAtMillis, creditCursor)
        if (endedAtMillis <= creditStartMillis) {
            return TrackedHandoffCreditDecision(
                creditStartMillis = creditStartMillis,
                activeSeconds = 0L,
                rejectionReason = "duplicate-interval"
            )
        }
        val activeSeconds = activeOverlapMillis(creditStartMillis, endedAtMillis, rule) / 1_000L
        return TrackedHandoffCreditDecision(
            creditStartMillis = creditStartMillis,
            activeSeconds = activeSeconds,
            rejectionReason = if (activeSeconds == 0L) "outside-active-schedule" else null
        )
    }

    @Synchronized
    fun completionProgress(context: Context, rule: EarnItRuleStore.Rule): Map<String, Long> {
        val prefs = currentPrefs(context, rule)
        return rule.requirements.associate { requirement ->
            requirement.app.packageName to prefs.getLong(requirementKey(rule, requirement.app.packageName), 0L)
        }
    }

    @Synchronized
    fun deleteRuleState(context: Context, ruleId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val prefix = "${ruleId}_"
        val keys = prefs.all.keys.filter { it.startsWith(prefix) }
        val editor = prefs.edit()
        keys.forEach { editor.remove(it) }
        editor.commit()
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
        if (shouldResetRuleLedger(
                storedDay = prefs.getString(dayKey, null),
                currentDay = today,
                storedRuleSignature = prefs.getString(signatureKey, null),
                currentRuleSignature = ruleSignature
            )
        ) {
            val editor = prefs.edit()
            clearRulePackageKeys(prefs, editor, rule, KEY_REQUIREMENT_PROGRESS_SECONDS)
            clearRulePackageKeys(prefs, editor, rule, KEY_TRACKED_HANDOFF_SECONDS)
            clearRulePackageKeys(prefs, editor, rule, KEY_TRACKED_HANDOFF_CREDIT_CURSOR)
            editor
                .putString(dayKey, today)
                .putString(signatureKey, ruleSignature)
                .putLong(ruleKey(rule, KEY_PRODUCTIVE_CREDITED_SECONDS), 0L)
                .putLong(ruleKey(rule, KEY_REWARD_ISSUED_SECONDS), 0L)
                .putLong(ruleKey(rule, KEY_REWARD_CONSUMED_SECONDS), 0L)
                .putLong(ruleKey(rule, KEY_DEEP_WORK_CREDITED_SECONDS), 0L)
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
        return productiveSeconds * rule.rewardSecondsPerProductiveSecond.coerceAtLeast(1) / 10L
    }

    private fun ruleKey(rule: EarnItRuleStore.Rule, key: String): String {
        return "${rule.id}_$key"
    }

    private fun requirementKey(rule: EarnItRuleStore.Rule, packageName: String): String {
        return "${rule.id}_${KEY_REQUIREMENT_PROGRESS_SECONDS}_$packageName"
    }

    private fun trackedHandoffSeconds(context: Context, rule: EarnItRuleStore.Rule, packageName: String): Long {
        return currentPrefs(context, rule).getLong(trackedHandoffKey(rule, packageName), 0L)
    }

    private fun trackedHandoffKey(rule: EarnItRuleStore.Rule, packageName: String): String {
        return "${rule.id}_${KEY_TRACKED_HANDOFF_SECONDS}_$packageName"
    }

    private fun trackedHandoffCursorKey(rule: EarnItRuleStore.Rule, packageName: String): String {
        return "${rule.id}_${KEY_TRACKED_HANDOFF_CREDIT_CURSOR}_$packageName"
    }

    private fun logTrackedHandoffCredit(
        context: Context,
        rule: EarnItRuleStore.Rule?,
        logicalPackageName: String,
        startedAtMillis: Long,
        endedAtMillis: Long,
        creditCursor: Long?,
        creditStartMillis: Long,
        creditedSeconds: Long,
        rejectionReason: String?
    ) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        Log.d(
            "EarnItRewardLedger",
            "trackedHandoff rule=${rule?.id} type=${rule?.type} enabled=${rule?.enabled} " +
                "scheduleActive=${rule?.isActiveNow()} logicalPackage=$logicalPackageName " +
                "sessionStart=$startedAtMillis sessionEnd=$endedAtMillis cursor=$creditCursor " +
                "creditInterval=$creditStartMillis..$endedAtMillis creditedSeconds=$creditedSeconds " +
                "rejection=${rejectionReason ?: "none"}"
        )
    }

    private fun clearRulePackageKeys(
        prefs: SharedPreferences,
        editor: SharedPreferences.Editor,
        rule: EarnItRuleStore.Rule,
        key: String
    ) {
        val prefix = "${rule.id}_${key}_"
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
    }

    private fun EarnItRuleStore.Rule.signature(): String {
        val blockedRuleId = blockedApps.map { it.packageName }.sorted().joinToString(",")
        val activeDaysId = activeDays.sorted().joinToString(",")
        val earnRuleId = earnApps.map { it.packageName }.sorted().joinToString(",")
        val requirementsId = requirements.joinToString(",") { "${it.app.packageName}:${it.requiredSeconds}" }
        val windowsId = effectiveTimeWindows.joinToString(",") { "${it.startMinute}-${it.endMinute}" }
        return "$type|$earnRuleId|$blockedRuleId|$rewardSecondsPerProductiveSecond|$activeDaysId|$windowsId|$requirementsId"
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

internal fun shouldResetRuleLedger(
    storedDay: String?,
    currentDay: String,
    storedRuleSignature: String?,
    currentRuleSignature: String
): Boolean = storedDay != currentDay || storedRuleSignature != currentRuleSignature
