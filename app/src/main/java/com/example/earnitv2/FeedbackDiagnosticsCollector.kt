package com.kaleel.earnitv2

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import java.util.Locale
import java.util.UUID

class FeedbackDiagnosticsCollector(private val context: Context) {
    fun collect(
        currentScreen: String,
        entrySource: FeedbackEntrySource,
        strictModeEnabled: Boolean,
        crash: CrashDiagnostics? = null
    ): FeedbackDiagnostics {
        val rules = EarnItRuleStore.getRules(context)
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return FeedbackDiagnostics(
            appVersion = packageInfo.versionName ?: "Unknown",
            buildNumber = if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode else @Suppress("DEPRECATION") packageInfo.versionCode.toLong(),
            androidVersion = Build.VERSION.RELEASE,
            deviceManufacturer = Build.MANUFACTURER,
            deviceModel = Build.MODEL,
            currentScreen = currentScreen,
            entrySource = entrySource,
            activeRuleCount = rules.count { it.enabled },
            ruleTypeCounts = rules.filter { it.enabled }.groupingBy { it.type.name }.eachCount(),
            strictModeEnabled = strictModeEnabled,
            usageAccessGranted = usageAccessGranted(),
            accessibilityServiceEnabled = accessibilityEnabled(),
            notificationPermissionGranted = null,
            online = isOnline(),
            installationId = installationId(),
            locale = Locale.getDefault().toLanguageTag(),
            sessionId = SESSION_ID,
            processUptimeMillis = SystemClock.elapsedRealtime(),
            crash = crash
        )
    }

    private fun installationId(): String {
        val prefs = context.getSharedPreferences("feedback_identity", Context.MODE_PRIVATE)
        return prefs.getString("installation_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("installation_id", it).apply()
        }
    }

    private fun usageAccessGranted(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }

    private fun accessibilityEnabled(): Boolean {
        val expected = ComponentName(context, EarnItAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return enabled.split(':').mapNotNull(ComponentName::unflattenFromString).any { it == expected }
    }

    private fun isOnline(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    companion object { private val SESSION_ID = UUID.randomUUID().toString() }
}
