package com.kaleel.earnitv2

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
    private const val DOMAIN_SERIALIZATION_VERSION = "v1"
    private const val MIGRATED_RULE_ID = "default"

    val allowedRatios = listOf(1, 2, 5)
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

    data class TimeWindow(
        val startMinute: Int,
        val endMinute: Int
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
        val requirements: List<RuleRequirement> = emptyList(),
        val timeWindows: List<TimeWindow> = emptyList(),
        /** Versioned by its serialized field position; values are canonical hostnames only. */
        val blockedDomains: List<String> = emptyList(),
        val requiresDailyCommitment: Boolean = false,
        val lastActivatedAtMillis: Long = 0L,
        val inactiveReason: RuleInactiveReason = RuleInactiveReason.None
    ) {
        val normalizedBlockedDomains: List<String> = blockedDomains
            .mapNotNull(DomainNormalizer::normalize)
            .distinct()
            .sorted()
        val earnApps: List<RuleApp> = productiveApps
            .ifEmpty { listOf(RuleApp(productivePackage, productiveName)) }
            .filter { it.packageName.isNotBlank() }
            .distinctBy { it.packageName }
        val earnAppPackages: Set<String> = earnApps.map { it.packageName }.toSet()
        val effectiveTimeWindows: List<TimeWindow> = normalizeTimeWindows(
            timeWindows.ifEmpty { listOf(TimeWindow(startMinute, endMinute)) }
        )
        val ratioLabel: String = "10:$rewardSecondsPerProductiveSecond min"
        val blockedAppCount: Int = blockedApps.size
        val protectedTargetCount: Int = blockedApps.size + normalizedBlockedDomains.size
        val blockedSummary: String = if (protectedTargetCount == 1 && blockedApps.size == 1) {
            blockedApps.first().name
        } else {
            "$protectedTargetCount protected items"
        }
        val scheduleLabel: String = scheduleSummary(activeDays, effectiveTimeWindows)

        fun blockedAppForPackage(packageName: String): RuleApp? {
            return blockedApps.firstOrNull { it.packageName == packageName }
        }

        fun blockedDomainForHost(host: String): String? {
            return normalizedBlockedDomains.firstOrNull { DomainMatcher.matches(it, host) }
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
            return effectiveTimeWindows.any { window ->
                if (window.startMinute == 0 && window.endMinute == 1_440) {
                    day in activeDays
                } else if (window.startMinute < window.endMinute) {
                    day in activeDays && safeMinute in window.startMinute until window.endMinute
                } else {
                    (day in activeDays && safeMinute >= window.startMinute) ||
                        (previousDay(day) in activeDays && safeMinute < window.endMinute)
                }
            }
        }
    }

    data class RuleApp(
        val packageName: String,
        val name: String
    )

    data class LaunchableApp(
        val packageName: String,
        val name: String,
        val applicationCategory: Int? = null
    )

    fun getRules(
        context: Context,
        policy: FeatureAccessPolicy = FeatureAccessPolicy.current(context)
    ): List<Rule> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val decodedRules = decodeRules(prefs.getString(KEY_RULES, null))
        val expiredPauseRuleIds = EarnItPauseStore.expiredRuleIds(context)
        val savedRules = if (expiredPauseRuleIds.isEmpty()) {
            decodedRules
        } else {
            val resolution = RuleEntitlementPolicy.resolveExpiredPauses(
                rules = decodedRules,
                expiredRuleIds = expiredPauseRuleIds,
                policy = policy,
                activatedAtMillis = System.currentTimeMillis()
            )
            resolution.resolvedRuleIds.forEach { EarnItPauseStore.clearPause(context, it) }
            saveRules(context, resolution.rules)
            resolution.rules
        }
        if (savedRules.isNotEmpty() || prefs.getBoolean(KEY_RULES_INITIALIZED, false)) return savedRules
        if (!hasLegacySingleRuleState(context)) return emptyList()

        val migratedRule = migratedSingleRule(context)
        saveRules(context, listOf(migratedRule))
        return listOf(migratedRule)
    }

    fun hasDurablePriorUse(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_RULES_INITIALIZED, false) ||
            !prefs.getString(KEY_RULES, null).isNullOrBlank() ||
            hasLegacySingleRuleState(context)
    }

    private fun hasLegacySingleRuleState(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return listOf(
            KEY_PRODUCTIVE_PACKAGE,
            KEY_PRODUCTIVE_NAME,
            KEY_BLOCKED_PACKAGE,
            KEY_BLOCKED_NAME,
            KEY_BLOCKED_APPS,
            KEY_REWARD_SECONDS_PER_PRODUCTIVE_SECOND,
            KEY_ACTIVE_DAYS,
            KEY_START_MINUTE,
            KEY_END_MINUTE
        ).any(prefs::contains)
    }

    fun getRule(context: Context): Rule {
        return getRules(context).firstOrNull() ?: migratedSingleRule(context)
    }

    fun findRule(context: Context, ruleId: String): Rule? {
        return getRules(context).firstOrNull { it.id == ruleId }
    }

    fun saveRules(context: Context, rules: List<Rule>) {
        val cleanRules = rules
            .map { it.copy(blockedDomains = it.normalizedBlockedDomains) }
            .filter { it.blockedApps.isNotEmpty() || it.normalizedBlockedDomains.isNotEmpty() }
            .distinctBy { it.id }
            .withoutEnabledEarnRewardConflicts()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RULES, encodeRules(cleanRules))
            .putBoolean(KEY_RULES_INITIALIZED, true)
            .commit()
    }

    fun saveRule(
        context: Context,
        rule: Rule,
        policy: FeatureAccessPolicy = FeatureAccessPolicy.current(context)
    ): RuleActivationResult {
        val rules = getRules(context)
        val result = RuleEntitlementPolicy.save(
            rules = rules,
            rule = rule,
            policy = policy,
            activatedAtMillis = System.currentTimeMillis()
        )
        if (result is RuleActivationResult.Allowed) {
            saveRules(context, result.rules)
        }
        return result
    }

    fun deleteRule(context: Context, ruleId: String) {
        saveRules(context, getRules(context).filterNot { it.id == ruleId })
        RewardLedger.deleteRuleState(context, ruleId)
    }

    fun setRuleEnabled(
        context: Context,
        ruleId: String,
        enabled: Boolean,
        policy: FeatureAccessPolicy = FeatureAccessPolicy.current(context)
    ): RuleActivationResult {
        val rules = getRules(context)
        val result = if (enabled) {
            RuleEntitlementPolicy.activate(rules, ruleId, policy, System.currentTimeMillis())
        } else {
            RuleActivationResult.Allowed(rules.map { rule ->
                if (rule.id == ruleId) {
                    rule.copy(enabled = false, inactiveReason = RuleInactiveReason.None)
                } else {
                    rule
                }
            })
        }
        if (result is RuleActivationResult.Allowed) saveRules(context, result.rules)
        return result
    }

    fun reconcileForEntitlement(context: Context, policy: FeatureAccessPolicy): List<Rule> {
        val current = getRules(context)
        val limit = policy.activeRuleLimit
        val reconciled = if (limit == null) {
            current
        } else {
            RuleEntitlementPolicy.reconcileDowngrade(current, limit)
        }
        if (reconciled != current) saveRules(context, reconciled)
        return reconciled
    }

    fun newRuleFromDefault(context: Context, type: RuleType = RuleType.EarnRewardTime): Rule {
        return Rule(
            id = newRuleId(),
            productivePackage = "",
            productiveName = "",
            blockedApps = emptyList(),
            rewardSecondsPerProductiveSecond = if (type == RuleType.EarnRewardTime) {
                2
            } else {
                1
            },
            activeDays = allDays.toSet(),
            startMinute = 0,
            endMinute = 1_440,
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
                    name = resolveInfo.loadLabel(packageManager).toString(),
                    applicationCategory = resolveInfo.activityInfo.applicationInfo?.category
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
        val safeMinute = if (minute == 1_440) 0 else minute.coerceIn(0, 1_439)
        val hour24 = safeMinute / 60
        val minutes = safeMinute % 60
        val suffix = if (hour24 < 12) "AM" else "PM"
        val hour12 = when (val raw = hour24 % 12) {
            0 -> 12
            else -> raw
        }
        return "$hour12:${minutes.toString().padStart(2, '0')} $suffix"
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
            rewardSecondsPerProductiveSecond = prefs.getInt(KEY_REWARD_SECONDS_PER_PRODUCTIVE_SECOND, 2)
                .takeIf { it > 0 } ?: 2,
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


    private fun encodeTimeWindows(windows: List<TimeWindow>): String {
        return normalizeTimeWindows(windows).joinToString(APP_RECORD_SEPARATOR) { window ->
            "${window.startMinute}-${window.endMinute}"
        }
    }

    private fun decodeTimeWindows(rawValue: String?): List<TimeWindow> {
        if (rawValue.isNullOrBlank()) return emptyList()
        return normalizeTimeWindows(rawValue.lines().mapNotNull { line ->
            val parts = line.split("-", limit = 2)
            val start = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val end = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            TimeWindow(start, end)
        })
    }

    fun normalizeTimeWindows(windows: List<TimeWindow>): List<TimeWindow> {
        return windows
            .mapNotNull { window ->
                val start = window.startMinute.coerceIn(0, 1_439)
                val end = window.endMinute.coerceIn(1, 1_440)
                if (start == end) null else TimeWindow(start, end)
            }
            .distinct()
            .sortedWith(compareBy<TimeWindow> { it.startMinute }.thenBy { it.endMinute })
            .ifEmpty { listOf(TimeWindow(0, 1_440)) }
    }

    private fun encodeBlockedDomains(domains: List<String>): String = buildList {
        add(DOMAIN_SERIALIZATION_VERSION)
        addAll(domains.mapNotNull(DomainNormalizer::normalize).distinct().sorted())
    }.joinToString(APP_RECORD_SEPARATOR)

    private fun decodeBlockedDomains(rawValue: String?): List<String> {
        if (rawValue.isNullOrBlank()) return emptyList()
        val lines = rawValue.lines()
        val values = if (lines.firstOrNull() == DOMAIN_SERIALIZATION_VERSION) lines.drop(1) else lines
        return values.mapNotNull(DomainNormalizer::normalize).distinct().sorted()
    }

    fun normalizeActiveDays(activeDays: Set<Int>): Set<Int> {
        val validDays = activeDays.filter { it in allDays }.toSet()
        return when (validDays) {
            allDays.toSet() -> allDays.toSet()
            setOf(1, 2, 3, 4, 5) -> setOf(1, 2, 3, 4, 5)
            else -> validDays.ifEmpty { allDays.toSet() }
        }
    }

    fun scheduleDaysLabel(activeDays: Set<Int>): String {
        val days = normalizeActiveDays(activeDays)
        return when (days) {
            allDays.toSet() -> "Every day"
            setOf(1, 2, 3, 4, 5) -> "Weekdays"
            else -> days.sorted().joinToString(", ") { dayShortName(it) }
        }
    }

    fun scheduleWindowLabels(windows: List<TimeWindow>): List<String> {
        val normalizedWindows = normalizeTimeWindows(windows)
        return if (normalizedWindows.size == 1 && normalizedWindows.first() == TimeWindow(0, 1_440)) {
            listOf("All day")
        } else {
            normalizedWindows.map { "${formatMinute(it.startMinute)}-${formatMinute(it.endMinute)}" }
        }
    }

    fun scheduleSummary(activeDays: Set<Int>, windows: List<TimeWindow>): String {
        val dayLabel = scheduleDaysLabel(activeDays)
        val normalizedWindows = normalizeTimeWindows(windows)
        val timeLabel = when {
            normalizedWindows.size == 1 && normalizedWindows.first() == TimeWindow(0, 1_440) -> "All day"
            normalizedWindows.size == 1 -> scheduleWindowLabels(normalizedWindows).first()
            else -> "${normalizedWindows.size} time windows"
        }
        return "$dayLabel · $timeLabel"
    }

    fun scheduleDetailLines(activeDays: Set<Int>, windows: List<TimeWindow>): List<String> {
        return listOf(scheduleDaysLabel(activeDays)) + scheduleWindowLabels(windows)
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

    internal fun encodeRules(rules: List<Rule>): String {
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
                encodeRequirements(rule.requirements),
                encodeTimeWindows(rule.effectiveTimeWindows),
                encodeBlockedDomains(rule.normalizedBlockedDomains),
                rule.lastActivatedAtMillis.toString(),
                rule.inactiveReason.name,
                rule.requiresDailyCommitment.toString()
            ).joinToString(RULE_FIELD_SEPARATOR) { encodeField(it) }
        }
    }

    internal fun decodeRules(rawValue: String?): List<Rule> {
        if (rawValue.isNullOrBlank()) return emptyList()
        return rawValue.split(RULE_RECORD_SEPARATOR)
            .mapNotNull { record ->
                val fields = record.split(RULE_FIELD_SEPARATOR).map { decodeField(it) }
                val id = fields.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val type = fields.getOrNull(9)?.let { rawType ->
                    RuleType.entries.firstOrNull { it.name == rawType }
                } ?: RuleType.EarnRewardTime
                val productivePackage = fields.getOrNull(1)?.takeIf { it.isNotBlank() }
                    ?: if (type == RuleType.EarnRewardTime) return@mapNotNull null else ""
                val productiveName = fields.getOrNull(2)?.takeIf { it.isNotBlank() }
                    ?: productivePackage.takeIf { it.isNotBlank() }.orEmpty()
                val blockedApps = decodeBlockedApps(fields.getOrNull(3))
                val blockedDomains = decodeBlockedDomains(fields.getOrNull(13))
                if (blockedApps.isEmpty() && blockedDomains.isEmpty()) return@mapNotNull null
                val ratio = fields.getOrNull(4)?.toIntOrNull()?.takeIf { it > 0 } ?: 2
                val activeDays = decodeActiveDays(fields.getOrNull(5))
                val startMinute = fields.getOrNull(6)?.toIntOrNull()?.coerceIn(0, 1_439) ?: 0
                val endMinute = fields.getOrNull(7)?.toIntOrNull()?.coerceIn(1, 1_440) ?: 1_440
                val enabled = fields.getOrNull(8)?.toBooleanStrictOrNull() ?: true
                val productiveApps = decodeBlockedApps(fields.getOrNull(10)).ifEmpty {
                    if (productivePackage.isBlank()) emptyList() else listOf(RuleApp(productivePackage, productiveName))
                }
                val requirements = decodeRequirements(fields.getOrNull(11))
                val timeWindows = decodeTimeWindows(fields.getOrNull(12)).ifEmpty {
                    listOf(TimeWindow(startMinute, endMinute))
                }
                val lastActivatedAtMillis = fields.getOrNull(14)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                val inactiveReason = fields.getOrNull(15)?.let { rawReason ->
                    RuleInactiveReason.entries.firstOrNull { it.name == rawReason }
                } ?: RuleInactiveReason.None
                val requiresDailyCommitment = fields.getOrNull(16)?.toBooleanStrictOrNull() ?: false
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
                    requirements = requirements,
                    timeWindows = timeWindows,
                    blockedDomains = blockedDomains,
                    requiresDailyCommitment = requiresDailyCommitment && type == RuleType.CompleteToUnlock,
                    lastActivatedAtMillis = lastActivatedAtMillis,
                    inactiveReason = inactiveReason
                )
            }
            .distinctBy { it.id }
    }

    private fun List<Rule>.withoutEnabledEarnRewardConflicts(): List<Rule> {
        val claimedRewardPackages = mutableSetOf<String>()
        val claimedRewardDomains = mutableSetOf<String>()
        return map { rule ->
            if (!rule.enabled || rule.type != RuleType.EarnRewardTime) return@map rule
            val conflicts = rule.blockedApps.any { it.packageName in claimedRewardPackages } ||
                rule.normalizedBlockedDomains.any { domain ->
                    claimedRewardDomains.any { claimed ->
                        DomainMatcher.matches(claimed, domain) || DomainMatcher.matches(domain, claimed)
                    }
                }
            rule.blockedApps.forEach { claimedRewardPackages += it.packageName }
            claimedRewardDomains += rule.normalizedBlockedDomains
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
