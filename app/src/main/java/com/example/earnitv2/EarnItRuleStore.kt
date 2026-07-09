package com.example.earnitv2

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Calendar
import java.util.UUID

object EarnItRuleStore {
    private const val PREFS_NAME = "earnit_rule"
    private const val KEY_RULES = "rules"
    private const val KEY_RULES_INITIALIZED = "rules_initialized"
    private const val KEY_PRODUCTIVE_PACKAGE = "productive_package"
    private const val KEY_PRODUCTIVE_NAME = "productive_name"
    private const val KEY_BLOCKED_PACKAGE = "blocked_package"
    private const val KEY_BLOCKED_NAME = "blocked_name"
    private const val KEY_BLOCKED_APPS = "blocked_apps"
    private const val KEY_REWARD_SECONDS_PER_PRODUCTIVE_SECOND = "reward_seconds_per_productive_second"
    private const val KEY_ACTIVE_DAYS = "active_days"
    private const val KEY_START_MINUTE = "start_minute"
    private const val KEY_END_MINUTE = "end_minute"
    private const val APP_FIELD_SEPARATOR = "\t"
    private const val APP_RECORD_SEPARATOR = "\n"
    private const val DAY_SEPARATOR = ","
    private const val RULE_FIELD_SEPARATOR = "\u001F"
    private const val RULE_RECORD_SEPARATOR = "\u001E"
    private const val MIGRATED_RULE_ID = "default"

    val allowedRatios = listOf(1, 2, 4, 5)
    val allDays = listOf(1, 2, 3, 4, 5, 6, 7)

    enum class RuleType {
        EarnRewardTime,
        CompleteToUnlock,
        ScheduledBlock
    }

    data class RuleRequirement(
        val app: RuleApp,
        val requiredSeconds: Long
    )

    data class Rule(
        val id: String = newRuleId(),
        val productivePackage: String,
        val productiveName: String,
        val blockedApps: List<RuleApp>,
        val rewardSecondsPerProductiveSecond: Int,
        val activeDays: Set<Int>,
        val startMinute: Int,
        val endMinute: Int,
        val enabled: Boolean = true,
        val type: RuleType = RuleType.EarnRewardTime,
        val productiveApps: List<RuleApp> = emptyList(),
        val requirements: List<RuleRequirement> = emptyList()
    ) {
        val earnApps: List<RuleApp> = productiveApps
            .ifEmpty { listOf(RuleApp(productivePackage, productiveName)) }
            .distinctBy { it.packageName }
        val earnAppPackages: Set<String> = earnApps.map { it.packageName }.toSet()
        val ratioLabel: String = "1:$rewardSecondsPerProductiveSecond"
        val blockedAppCount: Int = blockedApps.size
        val blockedSummary: String = if (blockedApps.size == 1) {
            blockedApps.first().name
        } else {
            "${blockedApps.size} blocked apps"
        }
        val scheduleLabel: String = if (activeDays == allDays.toSet() && startMinute == 0 && endMinute == 1_440) {
            "Every day, all day"
        } else {
            "${activeDays.sorted().joinToString(" ") { dayShortName(it) }} ${formatMinute(startMinute)}-${formatMinute(endMinute)}"
        }

        fun blockedAppForPackage(packageName: String): RuleApp? {
            return blockedApps.firstOrNull { it.packageName == packageName }
        }

        fun earnAppForPackage(packageName: String): RuleApp? {
            return earnApps.firstOrNull { it.packageName == packageName }
        }

        fun isActiveNow(): Boolean {
            val calendar = Calendar.getInstance()
            return isActiveAt(
                day = calendar.toEarnItDay(),
                minuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
            )
        }

        fun isActiveAt(day: Int, minuteOfDay: Int): Boolean {
            val safeMinute = minuteOfDay.coerceIn(0, 1_439)
            return if (startMinute == endMinute) {
                day in activeDays
            } else if (startMinute < endMinute) {
                day in activeDays && safeMinute in startMinute until endMinute
            } else {
                (day in activeDays && safeMinute >= startMinute) ||
                    (previousDay(day) in activeDays && safeMinute < endMinute)
            }
        }
    }

    data class RuleApp(
        val packageName: String,
        val name: String
    )

    data class LaunchableApp(
        val packageName: String,
        val name: String
    )

    fun getRules(context: Context): List<Rule> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedRules = decodeRules(prefs.getString(KEY_RULES, null))
        if (savedRules.isNotEmpty() || prefs.getBoolean(KEY_RULES_INITIALIZED, false)) return savedRules

        val migratedRule = migratedSingleRule(context)
        saveRules(context, listOf(migratedRule))
        return listOf(migratedRule)
    }

    fun getRule(context: Context): Rule {
        return getRules(context).firstOrNull() ?: migratedSingleRule(context)
    }

    fun findRule(context: Context, ruleId: String): Rule? {
        return getRules(context).firstOrNull { it.id == ruleId }
    }

    fun saveRules(context: Context, rules: List<Rule>) {
        val cleanRules = rules
            .filter { it.blockedApps.isNotEmpty() }
            .distinctBy { it.id }
            .withoutEnabledEarnRewardConflicts()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RULES, encodeRules(cleanRules))
            .putBoolean(KEY_RULES_INITIALIZED, true)
            .commit()
    }

    fun saveRule(context: Context, rule: Rule) {
        val rules = getRules(context)
        val updatedRules = if (rules.any { it.id == rule.id }) {
            rules.map { if (it.id == rule.id) rule else it }
        } else {
            rules + rule
        }
        saveRules(context, updatedRules)
    }

    fun deleteRule(context: Context, ruleId: String) {
        saveRules(context, getRules(context).filterNot { it.id == ruleId })
        RewardLedger.deleteRuleState(context, ruleId)
    }

    fun setRuleEnabled(context: Context, ruleId: String, enabled: Boolean) {
        saveRules(context, getRules(context).map { rule ->
            if (rule.id == ruleId) rule.copy(enabled = enabled) else rule
        })
    }

    fun newRuleFromDefault(context: Context, type: RuleType = RuleType.EarnRewardTime): Rule {
        val baseRule = getRule(context)
        return Rule(
            id = newRuleId(),
            productivePackage = "",
            productiveName = "",
            blockedApps = emptyList(),
            rewardSecondsPerProductiveSecond = if (type == RuleType.EarnRewardTime) {
                baseRule.rewardSecondsPerProductiveSecond
            } else {
                1
            },
            activeDays = baseRule.activeDays,
            startMinute = baseRule.startMinute,
            endMinute = baseRule.endMinute,
            enabled = true,
            type = type,
            productiveApps = emptyList(),
            requirements = emptyList()
        )
    }

    fun launchableApps(context: Context): List<LaunchableApp> {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return packageManager.queryIntentActivities(intent, 0)
            .map { resolveInfo ->
                LaunchableApp(
                    packageName = resolveInfo.activityInfo.packageName,
                    name = resolveInfo.loadLabel(packageManager).toString()
                )
            }
            .distinctBy { it.packageName }
            .filterNot { it.packageName == context.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    fun appInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun dayShortName(day: Int): String {
        return when (day) {
            1 -> "Mon"
            2 -> "Tue"
            3 -> "Wed"
            4 -> "Thu"
            5 -> "Fri"
            6 -> "Sat"
            7 -> "Sun"
            else -> "?"
        }
    }

    fun formatMinute(minute: Int): String {
        val safeMinute = minute.coerceIn(0, 1_440)
        val hour = if (safeMinute == 1_440) 24 else safeMinute / 60
        val minutes = if (safeMinute == 1_440) 0 else safeMinute % 60
        return hour.toString().padStart(2, '0') + ":" + minutes.toString().padStart(2, '0')
    }

    private fun migratedSingleRule(context: Context): Rule {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val productivePackage = prefs.getString(KEY_PRODUCTIVE_PACKAGE, null) ?: AppPackages.DEFAULT_PRODUCTIVE_APP
        val productiveName = prefs.getString(KEY_PRODUCTIVE_NAME, null) ?: "Duolingo"
        val blockedApps = decodeBlockedApps(prefs.getString(KEY_BLOCKED_APPS, null))
            .ifEmpty {
                listOf(
                    RuleApp(
                        packageName = prefs.getString(KEY_BLOCKED_PACKAGE, null) ?: AppPackages.DEFAULT_BLOCKED_APP,
                        name = prefs.getString(KEY_BLOCKED_NAME, null) ?: "Instagram"
                    )
                )
            }

        return Rule(
            id = MIGRATED_RULE_ID,
            productivePackage = productivePackage,
            productiveName = productiveName,
            blockedApps = blockedApps,
            rewardSecondsPerProductiveSecond = prefs.getInt(KEY_REWARD_SECONDS_PER_PRODUCTIVE_SECOND, 1)
                .takeIf { it in allowedRatios } ?: 1,
            activeDays = decodeActiveDays(prefs.getString(KEY_ACTIVE_DAYS, null)),
            startMinute = prefs.getInt(KEY_START_MINUTE, 0).coerceIn(0, 1_439),
            endMinute = prefs.getInt(KEY_END_MINUTE, 1_440).coerceIn(1, 1_440),
            enabled = true,
            type = RuleType.EarnRewardTime,
            productiveApps = listOf(RuleApp(productivePackage, productiveName))
        )
    }

    private fun encodeBlockedApps(blockedApps: List<RuleApp>): String {
        return blockedApps.joinToString(APP_RECORD_SEPARATOR) { app ->
            app.packageName + APP_FIELD_SEPARATOR + app.name.replace(APP_RECORD_SEPARATOR, " ")
        }
    }

    private fun decodeBlockedApps(rawValue: String?): List<RuleApp> {
        if (rawValue.isNullOrBlank()) return emptyList()
        return rawValue.lines()
            .mapNotNull { line ->
                val parts = line.split(APP_FIELD_SEPARATOR, limit = 2)
                val packageName = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val name = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: packageName
                RuleApp(packageName = packageName, name = name)
            }
            .distinctBy { it.packageName }
    }

    private fun encodeRequirements(requirements: List<RuleRequirement>): String {
        return requirements.joinToString(APP_RECORD_SEPARATOR) { requirement ->
            requirement.app.packageName + APP_FIELD_SEPARATOR +
                requirement.app.name.replace(APP_RECORD_SEPARATOR, " ") + APP_FIELD_SEPARATOR +
                requirement.requiredSeconds.coerceAtLeast(0L).toString()
        }
    }

    private fun decodeRequirements(rawValue: String?): List<RuleRequirement> {
        if (rawValue.isNullOrBlank()) return emptyList()
        return rawValue.lines()
            .mapNotNull { line ->
                val parts = line.split(APP_FIELD_SEPARATOR, limit = 3)
                val packageName = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val name = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: packageName
                val requiredSeconds = parts.getOrNull(2)?.toLongOrNull()?.takeIf { it > 0L } ?: return@mapNotNull null
                RuleRequirement(RuleApp(packageName = packageName, name = name), requiredSeconds = requiredSeconds)
            }
            .distinctBy { it.app.packageName }
    }

    private fun encodeActiveDays(activeDays: Set<Int>): String {
        return activeDays.sorted().joinToString(DAY_SEPARATOR)
    }

    private fun decodeActiveDays(rawValue: String?): Set<Int> {
        if (rawValue.isNullOrBlank()) return allDays.toSet()
        return rawValue.split(DAY_SEPARATOR)
            .mapNotNull { it.toIntOrNull() }
            .filter { it in allDays }
            .toSet()
            .ifEmpty { allDays.toSet() }
    }

    private fun encodeRules(rules: List<Rule>): String {
        return rules.joinToString(RULE_RECORD_SEPARATOR) { rule ->
            listOf(
                rule.id,
                rule.productivePackage,
                rule.productiveName,
                encodeBlockedApps(rule.blockedApps),
                rule.rewardSecondsPerProductiveSecond.toString(),
                encodeActiveDays(rule.activeDays),
                rule.startMinute.toString(),
                rule.endMinute.toString(),
                rule.enabled.toString(),
                rule.type.name,
                encodeBlockedApps(rule.earnApps),
                encodeRequirements(rule.requirements)
            ).joinToString(RULE_FIELD_SEPARATOR) { encodeField(it) }
        }
    }

    private fun decodeRules(rawValue: String?): List<Rule> {
        if (rawValue.isNullOrBlank()) return emptyList()
        return rawValue.split(RULE_RECORD_SEPARATOR)
            .mapNotNull { record ->
                val fields = record.split(RULE_FIELD_SEPARATOR).map { decodeField(it) }
                val id = fields.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val productivePackage = fields.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val productiveName = fields.getOrNull(2)?.takeIf { it.isNotBlank() } ?: productivePackage
                val blockedApps = decodeBlockedApps(fields.getOrNull(3)).ifEmpty { return@mapNotNull null }
                val ratio = fields.getOrNull(4)?.toIntOrNull()?.takeIf { it in allowedRatios } ?: 1
                val activeDays = decodeActiveDays(fields.getOrNull(5))
                val startMinute = fields.getOrNull(6)?.toIntOrNull()?.coerceIn(0, 1_439) ?: 0
                val endMinute = fields.getOrNull(7)?.toIntOrNull()?.coerceIn(1, 1_440) ?: 1_440
                val enabled = fields.getOrNull(8)?.toBooleanStrictOrNull() ?: true
                val type = fields.getOrNull(9)?.let { rawType ->
                    RuleType.entries.firstOrNull { it.name == rawType }
                } ?: RuleType.EarnRewardTime
                val productiveApps = decodeBlockedApps(fields.getOrNull(10)).ifEmpty {
                    listOf(RuleApp(productivePackage, productiveName))
                }
                val requirements = decodeRequirements(fields.getOrNull(11))
                Rule(
                    id = id,
                    productivePackage = productivePackage,
                    productiveName = productiveName,
                    blockedApps = blockedApps,
                    rewardSecondsPerProductiveSecond = ratio,
                    activeDays = activeDays,
                    startMinute = startMinute,
                    endMinute = endMinute,
                    enabled = enabled,
                    type = type,
                    productiveApps = productiveApps,
                    requirements = requirements
                )
            }
            .distinctBy { it.id }
    }

    private fun List<Rule>.withoutEnabledEarnRewardConflicts(): List<Rule> {
        val claimedRewardPackages = mutableSetOf<String>()
        return map { rule ->
            if (!rule.enabled || rule.type != RuleType.EarnRewardTime) return@map rule
            val conflicts = rule.blockedApps.any { it.packageName in claimedRewardPackages }
            rule.blockedApps.forEach { claimedRewardPackages += it.packageName }
            if (conflicts) rule.copy(enabled = false) else rule
        }
    }

    private fun encodeField(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
    }

    private fun decodeField(value: String): String {
        return URLDecoder.decode(value, "UTF-8")
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

    private fun previousDay(day: Int): Int {
        return if (day == 1) 7 else (day - 1).coerceIn(1, 7)
    }

    private fun newRuleId(): String {
        return "rule_" + UUID.randomUUID().toString()
    }
}
