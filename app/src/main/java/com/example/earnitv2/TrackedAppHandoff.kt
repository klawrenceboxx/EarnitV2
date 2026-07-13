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
    val startedAtMillis: Long
)

object TrackedAppHandoffPolicy {
    const val GEMINI_PACKAGE = "com.google.android.apps.bard"
    const val GOOGLE_PACKAGE = "com.google.android.googlequicksearchbox"
    const val PENDING_LAUNCH_WINDOW_MILLIS = 5_000L

    fun isAllowedHandoff(
        logicalPackageName: String,
        launchedPackageName: String,
        actualPackageName: String
    ): Boolean {
        return logicalPackageName == GEMINI_PACKAGE &&
            launchedPackageName == GEMINI_PACKAGE &&
            actualPackageName == GOOGLE_PACKAGE
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
        nowMillis: Long
    ): TrackedAppForegroundResult {
        val ended = activeSession?.takeIf { it.actualForegroundPackageName != actualPackageName }
        if (ended != null) {
            activeSession = null
        }

        val pending = pendingLaunch
        if (pending == null) {
            return TrackedAppForegroundResult(
                endedSession = ended,
                endedAtMillis = ended?.let { nowMillis }
            )
        }

        if (nowMillis > pending.expiresAtMillis) {
            pendingLaunch = null
            return TrackedAppForegroundResult(
                endedSession = ended,
                endedAtMillis = ended?.let { nowMillis },
                clearedExpiredPending = pending
            )
        }

        if (actualPackageName == pending.logicalPackageName) {
            pendingLaunch = null
            return TrackedAppForegroundResult(
                endedSession = ended,
                endedAtMillis = ended?.let { nowMillis },
                resolvedPending = pending
            )
        }

        if (TrackedAppHandoffPolicy.isAllowedHandoff(
                logicalPackageName = pending.logicalPackageName,
                launchedPackageName = pending.launchedPackageName,
                actualPackageName = actualPackageName
            )
        ) {
            pendingLaunch = null
            val started = ActiveTrackedAppSession(
                logicalPackageName = pending.logicalPackageName,
                actualForegroundPackageName = actualPackageName,
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
            expiresAtMillis = nowMillis + TrackedAppHandoffPolicy.PENDING_LAUNCH_WINDOW_MILLIS
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
                remove(KEY_ACTIVE_STARTED_AT)
            } else {
                putString(KEY_ACTIVE_LOGICAL, session.logicalPackageName)
                putString(KEY_ACTIVE_ACTUAL, session.actualForegroundPackageName)
                putLong(KEY_ACTIVE_STARTED_AT, session.startedAtMillis)
            }
        }.commit()
    }

    fun readActiveSession(context: Context): ActiveTrackedAppSession? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val logical = prefs.getString(KEY_ACTIVE_LOGICAL, null)?.takeIf { it.isNotBlank() } ?: return null
        val actual = prefs.getString(KEY_ACTIVE_ACTUAL, null)?.takeIf { it.isNotBlank() } ?: return null
        val startedAt = prefs.getLong(KEY_ACTIVE_STARTED_AT, -1L).takeIf { it >= 0L } ?: return null
        return ActiveTrackedAppSession(logical, actual, startedAt)
    }
}

internal fun Intent.targetPackageName(fallbackPackageName: String): String {
    return `package` ?: component?.packageName ?: fallbackPackageName
}
