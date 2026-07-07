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
    private const val KEY_REWARD_SECONDS_PER_PRODUCTIVE_SECOND = "reward_seconds_per_productive_second"

    val allowedRatios = listOf(1, 2, 4, 5)

    data class Rule(
        val productivePackage: String,
        val productiveName: String,
        val blockedPackage: String,
        val blockedName: String,
        val rewardSecondsPerProductiveSecond: Int
    ) {
        val ratioLabel: String = "1:$rewardSecondsPerProductiveSecond"
    }

    data class LaunchableApp(
        val packageName: String,
        val name: String
    )

    fun getRule(context: Context): Rule {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return Rule(
            productivePackage = prefs.getString(KEY_PRODUCTIVE_PACKAGE, null) ?: AppPackages.DEFAULT_PRODUCTIVE_APP,
            productiveName = prefs.getString(KEY_PRODUCTIVE_NAME, null) ?: "Duolingo",
            blockedPackage = prefs.getString(KEY_BLOCKED_PACKAGE, null) ?: AppPackages.DEFAULT_BLOCKED_APP,
            blockedName = prefs.getString(KEY_BLOCKED_NAME, null) ?: "Instagram",
            rewardSecondsPerProductiveSecond = prefs.getInt(KEY_REWARD_SECONDS_PER_PRODUCTIVE_SECOND, 1)
                .takeIf { it in allowedRatios } ?: 1
        )
    }

    fun saveRule(context: Context, rule: Rule) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PRODUCTIVE_PACKAGE, rule.productivePackage)
            .putString(KEY_PRODUCTIVE_NAME, rule.productiveName)
            .putString(KEY_BLOCKED_PACKAGE, rule.blockedPackage)
            .putString(KEY_BLOCKED_NAME, rule.blockedName)
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
}
