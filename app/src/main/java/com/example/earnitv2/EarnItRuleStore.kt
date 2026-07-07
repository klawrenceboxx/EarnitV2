package com.example.earnitv2

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.util.Calendar

object EarnItRuleStore {
    private const val PREFS_NAME = "earnit_rule"
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

    val allowedRatios = listOf(1, 2, 4, 5)
    val allDays = listOf(1, 2, 3, 4, 5, 6, 7)

    data class Rule(
        val productivePackage: String,
        val productiveName: String,
        val blockedApps: List<RuleApp>,
        val rewardSecondsPerProductiveSecond: Int,
        val activeDays: Set<Int>,
        val startMinute: Int,
        val endMinute: Int
    ) {
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

        fun isActiveNow(): Boolean {
            val calendar = Calendar.getInstance()
            return isActiveAt(
                day = calendar.toEarnItDay(),
                minuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
            )
        }

        fun isActiveAt(day: Int, minuteOfDay: Int): Boolean {
            if (day !in activeDays) return false
            return if (startMinute == endMinute) {
                true
            } else if (startMinute < endMinute) {
                minuteOfDay in startMinute until endMinute
            } else {
                minuteOfDay >= startMinute || minuteOfDay < endMinute
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

    fun getRule(context: Context): Rule {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
            productivePackage = prefs.getString(KEY_PRODUCTIVE_PACKAGE, null) ?: AppPackages.DEFAULT_PRODUCTIVE_APP,
            productiveName = prefs.getString(KEY_PRODUCTIVE_NAME, null) ?: "Duolingo",
            blockedApps = blockedApps,
            rewardSecondsPerProductiveSecond = prefs.getInt(KEY_REWARD_SECONDS_PER_PRODUCTIVE_SECOND, 1)
                .takeIf { it in allowedRatios } ?: 1,
            activeDays = decodeActiveDays(prefs.getString(KEY_ACTIVE_DAYS, null)),
            startMinute = prefs.getInt(KEY_START_MINUTE, 0).coerceIn(0, 1_439),
            endMinute = prefs.getInt(KEY_END_MINUTE, 1_440).coerceIn(1, 1_440)
        )
    }

    fun saveRule(context: Context, rule: Rule) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PRODUCTIVE_PACKAGE, rule.productivePackage)
            .putString(KEY_PRODUCTIVE_NAME, rule.productiveName)
            .putString(KEY_BLOCKED_APPS, encodeBlockedApps(rule.blockedApps))
            .remove(KEY_BLOCKED_PACKAGE)
            .remove(KEY_BLOCKED_NAME)
            .putInt(KEY_REWARD_SECONDS_PER_PRODUCTIVE_SECOND, rule.rewardSecondsPerProductiveSecond)
            .putString(KEY_ACTIVE_DAYS, encodeActiveDays(rule.activeDays))
            .putInt(KEY_START_MINUTE, rule.startMinute)
            .putInt(KEY_END_MINUTE, rule.endMinute)
            .commit()
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
