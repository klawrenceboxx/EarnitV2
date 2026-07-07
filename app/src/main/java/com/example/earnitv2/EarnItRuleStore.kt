package com.example.earnitv2

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object EarnItRuleStore {
    private const val PREFS_NAME = "earnit_rule"
    private const val KEY_PRODUCTIVE_PACKAGE = "productive_package"
    private const val KEY_PRODUCTIVE_NAME = "productive_name"
    private const val KEY_BLOCKED_PACKAGE = "blocked_package"
    private const val KEY_BLOCKED_NAME = "blocked_name"
    private const val KEY_BLOCKED_APPS = "blocked_apps"
    private const val KEY_REWARD_SECONDS_PER_PRODUCTIVE_SECOND = "reward_seconds_per_productive_second"
    private const val APP_FIELD_SEPARATOR = "\t"
    private const val APP_RECORD_SEPARATOR = "\n"

    val allowedRatios = listOf(1, 2, 4, 5)

    data class Rule(
        val productivePackage: String,
        val productiveName: String,
        val blockedApps: List<RuleApp>,
        val rewardSecondsPerProductiveSecond: Int
    ) {
        val ratioLabel: String = "1:$rewardSecondsPerProductiveSecond"
        val blockedAppCount: Int = blockedApps.size
        val blockedSummary: String = if (blockedApps.size == 1) {
            blockedApps.first().name
        } else {
            "${blockedApps.size} blocked apps"
        }

        fun blockedAppForPackage(packageName: String): RuleApp? {
            return blockedApps.firstOrNull { it.packageName == packageName }
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
                .takeIf { it in allowedRatios } ?: 1
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
}
