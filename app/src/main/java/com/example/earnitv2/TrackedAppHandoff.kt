package com.example.earnitv2

import android.content.Context
import android.content.Intent

data class PendingTrackedAppLaunch(
    val logicalPackageName: String,
    val launchedPackageName: String,
    val launchedAtMillis: Long,
    val expiresAtMillis: Long
)

data class ActiveTrackedAppSession(
    val logicalPackageName: String,
    val actualForegroundPackageName: String,
    val actualForegroundClassName: String? = null,
    val startedAtMillis: Long
)

data class TrackedAppMatchRule(
    val packageName: String,
    val classNameMatcher: ((String?) -> Boolean)? = null
) {
    fun matches(packageName: String, className: String?): Boolean {
        if (this.packageName != packageName) return false
        return classNameMatcher?.invoke(className) ?: true
    }
}

object TrackedAppMatchPolicy {
    const val GEMINI_PACKAGE = "com.google.android.apps.bard"
    const val GOOGLE_PACKAGE = "com.google.android.googlequicksearchbox"
    const val PENDING_LAUNCH_WINDOW_MILLIS = 5_000L

    fun matchRulesFor(logicalPackageName: String): List<TrackedAppMatchRule> {
        return when (logicalPackageName) {
            GEMINI_PACKAGE -> listOf(
                TrackedAppMatchRule(GEMINI_PACKAGE),
                TrackedAppMatchRule(GOOGLE_PACKAGE) { className -> className?.contains(".robin.") == true }
            )
            else -> listOf(TrackedAppMatchRule(logicalPackageName))
        }
    }

    fun matchingRule(
        logicalPackageName: String,
        actualPackageName: String,
        actualClassName: String?
    ): TrackedAppMatchRule? {
        return matchRulesFor(logicalPackageName).firstOrNull { rule ->
            rule.matches(actualPackageName, actualClassName)
        }
    }

    fun canIgnoreUnmatchedClassNoise(
        logicalPackageName: String,
        activeActualPackageName: String,
        actualPackageName: String,
        actualClassName: String?
    ): Boolean {
        if (activeActualPackageName != actualPackageName) return false
        if (actualClassName == null) return true
        return matchRulesFor(logicalPackageName).any { it.packageName == actualPackageName && it.classNameMatcher != null } &&
            !actualClassName.contains("Activity")
    }
}

data class TrackedAppForegroundResult(
    val endedSession: ActiveTrackedAppSession? = null,
    val endedAtMillis: Long? = null,
    val startedSession: ActiveTrackedAppSession? = null,
    val clearedExpiredPending: PendingTrackedAppLaunch? = null,
    val resolvedPending: PendingTrackedAppLaunch? = null
)

class TrackedAppHandoffTracker(
    private var pendingLaunch: PendingTrackedAppLaunch? = null,
    private var activeSession: ActiveTrackedAppSession? = null
) {
    fun pendingLaunch(): PendingTrackedAppLaunch? = pendingLaunch

    fun activeSession(): ActiveTrackedAppSession? = activeSession

    fun registerPendingLaunch(pending: PendingTrackedAppLaunch) {
        pendingLaunch = pending
    }

    fun onForegroundPackage(
        actualPackageName: String,
        actualClassName: String?,
        nowMillis: Long,
        relevantLogicalPackageNames: Set<String> = emptySet(),
        ignoredForegroundPackageNames: Set<String> = emptySet()
    ): TrackedAppForegroundResult {
        val active = activeSession
        val activeStillMatches = active?.let {
            TrackedAppMatchPolicy.matchingRule(it.logicalPackageName, actualPackageName, actualClassName) != null
        } == true
        val ignoreClassNoise = active?.let {
            TrackedAppMatchPolicy.canIgnoreUnmatchedClassNoise(
                logicalPackageName = it.logicalPackageName,
                activeActualPackageName = it.actualForegroundPackageName,
                actualPackageName = actualPackageName,
                actualClassName = actualClassName
            )
        } == true
        val ignoreForegroundPackage = active != null && actualPackageName in ignoredForegroundPackageNames
        val ended = active?.takeIf {
            !activeStillMatches && !ignoreClassNoise && !ignoreForegroundPackage
        }
        if (ended != null) {
            activeSession = null
        }

        if (activeSession != null) {
            val pending = pendingLaunch
            if (pending != null && nowMillis > pending.expiresAtMillis) {
                pendingLaunch = null
                return TrackedAppForegroundResult(clearedExpiredPending = pending)
            }
            if (pending != null && TrackedAppMatchPolicy.matchingRule(
                    logicalPackageName = pending.logicalPackageName,
                    actualPackageName = actualPackageName,
                    actualClassName = actualClassName
                ) != null
            ) {
                pendingLaunch = null
                return TrackedAppForegroundResult(resolvedPending = pending)
            }
            return TrackedAppForegroundResult()
        }

        val pending = pendingLaunch
        if (pending == null) {
            val started = directMatchedSession(
                actualPackageName = actualPackageName,
                actualClassName = actualClassName,
                nowMillis = nowMillis,
                relevantLogicalPackageNames = relevantLogicalPackageNames
            )
            if (started != null) {
                activeSession = started
            }
            return TrackedAppForegroundResult(
                endedSession = ended,
                endedAtMillis = ended?.let { nowMillis },
                startedSession = started
            )
        }

        if (nowMillis > pending.expiresAtMillis) {
            pendingLaunch = null
            val started = directMatchedSession(
                actualPackageName = actualPackageName,
                actualClassName = actualClassName,
                nowMillis = nowMillis,
                relevantLogicalPackageNames = relevantLogicalPackageNames
            )
            if (started != null) {
                activeSession = started
            }
            return TrackedAppForegroundResult(
                endedSession = ended,
                endedAtMillis = ended?.let { nowMillis },
                startedSession = started,
                clearedExpiredPending = pending
            )
        }

        val matchingRule = TrackedAppMatchPolicy.matchingRule(
            logicalPackageName = pending.logicalPackageName,
            actualPackageName = actualPackageName,
            actualClassName = actualClassName
        )
        if (matchingRule != null && matchingRule.packageName == pending.logicalPackageName) {
            pendingLaunch = null
            return TrackedAppForegroundResult(
                endedSession = ended,
                endedAtMillis = ended?.let { nowMillis },
                resolvedPending = pending
            )
        }

        if (matchingRule != null) {
            pendingLaunch = null
            val started = ActiveTrackedAppSession(
                logicalPackageName = pending.logicalPackageName,
                actualForegroundPackageName = actualPackageName,
                actualForegroundClassName = actualClassName,
                startedAtMillis = nowMillis
            )
            activeSession = started
            return TrackedAppForegroundResult(
                endedSession = ended,
                endedAtMillis = ended?.let { nowMillis },
                startedSession = started,
                resolvedPending = pending
            )
        }

        return TrackedAppForegroundResult(
            endedSession = ended,
            endedAtMillis = ended?.let { nowMillis }
        )
    }

    private fun directMatchedSession(
        actualPackageName: String,
        actualClassName: String?,
        nowMillis: Long,
        relevantLogicalPackageNames: Set<String>
    ): ActiveTrackedAppSession? {
        val logicalPackageName = relevantLogicalPackageNames.firstOrNull { candidate ->
            val match = TrackedAppMatchPolicy.matchingRule(candidate, actualPackageName, actualClassName)
            match != null && match.packageName != candidate
        } ?: return null
        return ActiveTrackedAppSession(
            logicalPackageName = logicalPackageName,
            actualForegroundPackageName = actualPackageName,
            actualForegroundClassName = actualClassName,
            startedAtMillis = nowMillis
        )
    }

    fun stopActiveSession(nowMillis: Long): TrackedAppForegroundResult {
        val ended = activeSession ?: return TrackedAppForegroundResult()
        activeSession = null
        return TrackedAppForegroundResult(endedSession = ended, endedAtMillis = nowMillis)
    }
}

object TrackedAppLaunchStore {
    private const val PREFS_NAME = "earnit_tracked_app_handoff"
    private const val KEY_PENDING_LOGICAL = "pending_logical_package"
    private const val KEY_PENDING_LAUNCHED = "pending_launched_package"
    private const val KEY_PENDING_LAUNCHED_AT = "pending_launched_at"
    private const val KEY_PENDING_EXPIRES_AT = "pending_expires_at"
    private const val KEY_ACTIVE_LOGICAL = "active_logical_package"
    private const val KEY_ACTIVE_ACTUAL = "active_actual_package"
    private const val KEY_ACTIVE_CLASS = "active_class"
    private const val KEY_ACTIVE_STARTED_AT = "active_started_at"

    fun registerPendingLaunch(
        context: Context,
        logicalPackageName: String,
        launchedPackageName: String,
        nowMillis: Long = System.currentTimeMillis()
    ): PendingTrackedAppLaunch {
        val pending = PendingTrackedAppLaunch(
            logicalPackageName = logicalPackageName,
            launchedPackageName = launchedPackageName,
            launchedAtMillis = nowMillis,
            expiresAtMillis = nowMillis + TrackedAppMatchPolicy.PENDING_LAUNCH_WINDOW_MILLIS
        )
        savePendingLaunch(context, pending)
        return pending
    }

    fun savePendingLaunch(context: Context, pending: PendingTrackedAppLaunch?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            if (pending == null) {
                remove(KEY_PENDING_LOGICAL)
                remove(KEY_PENDING_LAUNCHED)
                remove(KEY_PENDING_LAUNCHED_AT)
                remove(KEY_PENDING_EXPIRES_AT)
            } else {
                putString(KEY_PENDING_LOGICAL, pending.logicalPackageName)
                putString(KEY_PENDING_LAUNCHED, pending.launchedPackageName)
                putLong(KEY_PENDING_LAUNCHED_AT, pending.launchedAtMillis)
                putLong(KEY_PENDING_EXPIRES_AT, pending.expiresAtMillis)
            }
        }.commit()
    }

    fun readPendingLaunch(context: Context): PendingTrackedAppLaunch? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val logical = prefs.getString(KEY_PENDING_LOGICAL, null)?.takeIf { it.isNotBlank() } ?: return null
        val launched = prefs.getString(KEY_PENDING_LAUNCHED, null)?.takeIf { it.isNotBlank() } ?: return null
        val launchedAt = prefs.getLong(KEY_PENDING_LAUNCHED_AT, -1L).takeIf { it >= 0L } ?: return null
        val expiresAt = prefs.getLong(KEY_PENDING_EXPIRES_AT, -1L).takeIf { it >= launchedAt } ?: return null
        return PendingTrackedAppLaunch(logical, launched, launchedAt, expiresAt)
    }

    fun saveActiveSession(context: Context, session: ActiveTrackedAppSession?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            if (session == null) {
                remove(KEY_ACTIVE_LOGICAL)
                remove(KEY_ACTIVE_ACTUAL)
                remove(KEY_ACTIVE_CLASS)
                remove(KEY_ACTIVE_STARTED_AT)
            } else {
                putString(KEY_ACTIVE_LOGICAL, session.logicalPackageName)
                putString(KEY_ACTIVE_ACTUAL, session.actualForegroundPackageName)
                putString(KEY_ACTIVE_CLASS, session.actualForegroundClassName)
                putLong(KEY_ACTIVE_STARTED_AT, session.startedAtMillis)
            }
        }.commit()
    }

    fun readActiveSession(context: Context): ActiveTrackedAppSession? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val logical = prefs.getString(KEY_ACTIVE_LOGICAL, null)?.takeIf { it.isNotBlank() } ?: return null
        val actual = prefs.getString(KEY_ACTIVE_ACTUAL, null)?.takeIf { it.isNotBlank() } ?: return null
        val actualClass = prefs.getString(KEY_ACTIVE_CLASS, null)
        val startedAt = prefs.getLong(KEY_ACTIVE_STARTED_AT, -1L).takeIf { it >= 0L } ?: return null
        return ActiveTrackedAppSession(
            logicalPackageName = logical,
            actualForegroundPackageName = actual,
            actualForegroundClassName = actualClass,
            startedAtMillis = startedAt
        )
    }
}

internal fun Intent.targetPackageName(fallbackPackageName: String): String {
    return `package` ?: component?.packageName ?: fallbackPackageName
}
