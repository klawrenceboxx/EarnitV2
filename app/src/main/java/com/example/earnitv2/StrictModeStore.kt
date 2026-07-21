package com.example.earnitv2

import android.content.Context

internal enum class StrictModeLifecycleState {
    Inactive,
    Activating,
    Active,
    DeactivationCounting,
    DeactivationReady
}

internal enum class StrictModeDurationType {
    Timed,
    Indefinite
}

internal enum class StrictModeDeactivationMethod {
    Countdown,
    ChargerWait,
    Pin,
    EmailApproval,
    NfcTagOrSecurityFob
}

internal data class StrictModeConfiguration(
    val durationType: StrictModeDurationType = StrictModeDurationType.Timed,
    val timedDurationMillis: Long? = 60 * 60_000L,
    val deactivationMethod: StrictModeDeactivationMethod = StrictModeDeactivationMethod.Countdown,
    val deactivationCountdownMillis: Long? = 10 * 60_000L
)

internal data class StrictModeState(
    val lifecycleState: StrictModeLifecycleState = StrictModeLifecycleState.Inactive,
    val configuration: StrictModeConfiguration = StrictModeConfiguration(),
    val activationGraceStartedAtMillis: Long? = null,
    val activationGraceEndsAtMillis: Long? = null,
    val activatedAtMillis: Long? = null,
    val expiresAtMillis: Long? = null,
    val deactivationStartedAtMillis: Long? = null,
    val deactivationAvailableAtMillis: Long? = null
)

internal interface StrictModePersistence {
    fun read(key: String): String?
    fun write(values: Map<String, String?>)
}

internal class SharedPreferencesStrictModePersistence(context: Context) : StrictModePersistence {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun read(key: String): String? = prefs.getString(key, null)

    override fun write(values: Map<String, String?>) {
        val editor = prefs.edit()
        values.forEach { (key, value) ->
            if (value == null) editor.remove(key) else editor.putString(key, value)
        }
        editor.apply()
    }

    private companion object {
        const val PREFS_NAME = "earnit_strict_mode"
    }
}

internal class StrictModeStore(
    private val persistence: StrictModePersistence,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    fun state(): StrictModeState {
        val state = readState()
        val normalized = normalize(state, nowMillis())
        if (normalized != state) saveState(normalized)
        return normalized
    }

    fun saveConfiguration(configuration: StrictModeConfiguration): StrictModeState {
        require(isValidConfiguration(configuration)) { "Invalid Strict Mode configuration" }
        val updated = state().copy(configuration = configuration)
        saveState(updated)
        return updated
    }

    fun beginActivation(configuration: StrictModeConfiguration): StrictModeState {
        require(isValidConfiguration(configuration)) { "Invalid Strict Mode configuration" }
        val now = nowMillis()
        val graceEndsAt = safeAdd(now, ACTIVATION_GRACE_MILLIS) ?: now
        val updated = StrictModeState(
            lifecycleState = StrictModeLifecycleState.Activating,
            configuration = configuration,
            activationGraceStartedAtMillis = now,
            activationGraceEndsAtMillis = graceEndsAt
        )
        saveState(updated)
        return updated
    }

    fun cancelActivation(): StrictModeState {
        val updated = StrictModeState(configuration = state().configuration)
        saveState(updated)
        return updated
    }

    fun beginDeactivation(): StrictModeState {
        val current = state()
        if (current.lifecycleState == StrictModeLifecycleState.DeactivationCounting ||
            current.lifecycleState == StrictModeLifecycleState.DeactivationReady
        ) {
            return current
        }
        if (current.lifecycleState != StrictModeLifecycleState.Active) return current
        val countdownMillis = current.configuration.deactivationCountdownMillis ?: return current
        if (countdownMillis <= 0L) return current
        val now = nowMillis()
        val availableAt = safeAdd(now, countdownMillis) ?: return current
        val updated = current.copy(
            lifecycleState = StrictModeLifecycleState.DeactivationCounting,
            deactivationStartedAtMillis = now,
            deactivationAvailableAtMillis = availableAt
        )
        saveState(updated)
        return updated
    }

    fun cancelDeactivation(): StrictModeState {
        val current = state()
        val updated = if (current.lifecycleState == StrictModeLifecycleState.DeactivationCounting ||
            current.lifecycleState == StrictModeLifecycleState.DeactivationReady
        ) {
            current.copy(
                lifecycleState = StrictModeLifecycleState.Active,
                deactivationStartedAtMillis = null,
                deactivationAvailableAtMillis = null
            )
        } else {
            current
        }
        saveState(updated)
        return updated
    }

    fun keepStrictModeActive(): StrictModeState = cancelDeactivation()

    fun confirmDeactivation(): StrictModeState {
        val current = state()
        val updated = if (current.lifecycleState == StrictModeLifecycleState.DeactivationReady) {
            StrictModeState(configuration = current.configuration)
        } else {
            current
        }
        saveState(updated)
        return updated
    }

    fun isValidConfiguration(configuration: StrictModeConfiguration): Boolean {
        val durationValid = when (configuration.durationType) {
            StrictModeDurationType.Timed -> (configuration.timedDurationMillis ?: 0L) in 1L..MAX_TIMED_DURATION_MILLIS
            StrictModeDurationType.Indefinite -> true
        }
        val countdownValid = configuration.deactivationMethod == StrictModeDeactivationMethod.Countdown &&
            (configuration.deactivationCountdownMillis ?: 0L) in 1L..MAX_DEACTIVATION_COUNTDOWN_MILLIS
        return durationValid && countdownValid
    }

    private fun normalize(state: StrictModeState, now: Long): StrictModeState {
        if (!isValidConfiguration(state.configuration)) {
            return StrictModeState(configuration = StrictModeConfiguration())
        }
        if (state.activationGraceStartedAtMillis != null && state.activationGraceStartedAtMillis < 0L) {
            return StrictModeState(configuration = state.configuration)
        }
        if (state.activatedAtMillis != null && state.activatedAtMillis < 0L) {
            return StrictModeState(configuration = state.configuration)
        }
        if (state.expiresAtMillis != null && state.expiresAtMillis < 0L) {
            return StrictModeState(configuration = state.configuration)
        }
        if (state.deactivationStartedAtMillis != null && state.deactivationStartedAtMillis < 0L) {
            return state.copy(
                lifecycleState = if (state.isProtectionLifecycle()) StrictModeLifecycleState.Active else state.lifecycleState,
                deactivationStartedAtMillis = null,
                deactivationAvailableAtMillis = null
            )
        }
        if (state.deactivationAvailableAtMillis != null && state.deactivationAvailableAtMillis < 0L) {
            return state.copy(
                lifecycleState = if (state.isProtectionLifecycle()) StrictModeLifecycleState.Active else state.lifecycleState,
                deactivationStartedAtMillis = null,
                deactivationAvailableAtMillis = null
            )
        }
        if (state.lifecycleState == StrictModeLifecycleState.Activating) {
            val graceEndsAt = state.activationGraceEndsAtMillis ?: return StrictModeState(configuration = state.configuration)
            val graceStartedAt = state.activationGraceStartedAtMillis ?: return StrictModeState(configuration = state.configuration)
            if (graceEndsAt < graceStartedAt) return StrictModeState(configuration = state.configuration)
            if (now >= graceEndsAt) {
                val expiresAt = if (state.configuration.durationType == StrictModeDurationType.Timed) {
                    safeAdd(
                        graceEndsAt,
                        state.configuration.timedDurationMillis ?: return StrictModeState(configuration = state.configuration)
                    ) ?: return StrictModeState(configuration = state.configuration)
                } else {
                    null
                }
                return normalize(
                    StrictModeState(
                    lifecycleState = StrictModeLifecycleState.Active,
                    configuration = state.configuration,
                    activatedAtMillis = graceEndsAt,
                    expiresAtMillis = expiresAt
                    ),
                    now
                )
            }
        }
        if (state.isProtectionLifecycle() &&
            state.configuration.durationType == StrictModeDurationType.Timed &&
            (state.expiresAtMillis ?: Long.MAX_VALUE) <= now
        ) {
            return StrictModeState(configuration = state.configuration)
        }
        if (state.isProtectionLifecycle() &&
            state.configuration.durationType == StrictModeDurationType.Timed &&
            (state.expiresAtMillis == null ||
                (state.activatedAtMillis != null && state.expiresAtMillis < state.activatedAtMillis))
        ) {
            return StrictModeState(configuration = state.configuration)
        }
        if (state.lifecycleState == StrictModeLifecycleState.DeactivationCounting) {
            val availableAt = state.deactivationAvailableAtMillis
            val startedAt = state.deactivationStartedAtMillis
            if (availableAt == null || startedAt == null || availableAt < startedAt) {
                return state.copy(
                    lifecycleState = StrictModeLifecycleState.Active,
                    deactivationStartedAtMillis = null,
                    deactivationAvailableAtMillis = null
                )
            }
            if (now >= availableAt) {
                return state.copy(lifecycleState = StrictModeLifecycleState.DeactivationReady)
            }
        }
        if (state.lifecycleState == StrictModeLifecycleState.DeactivationReady &&
            state.deactivationAvailableAtMillis == null
        ) {
            return state.copy(lifecycleState = StrictModeLifecycleState.Active)
        }
        return state
    }

    private fun readState(): StrictModeState {
        val deactivationMethod = persistence.read(KEY_DEACTIVATION_METHOD)
            ?.toDeactivationMethod()
            ?.takeIf { it == StrictModeDeactivationMethod.Countdown }
            ?: StrictModeDeactivationMethod.Countdown
        val configuration = StrictModeConfiguration(
            durationType = persistence.read(KEY_DURATION_TYPE)?.toDurationType() ?: StrictModeDurationType.Timed,
            timedDurationMillis = persistence.read(KEY_TIMED_DURATION_MILLIS)?.toLongOrNull() ?: 60 * 60_000L,
            deactivationMethod = deactivationMethod,
            deactivationCountdownMillis = persistence.read(KEY_DEACTIVATION_COUNTDOWN_MILLIS)?.toLongOrNull() ?: 10 * 60_000L
        )
        return StrictModeState(
            lifecycleState = persistence.read(KEY_LIFECYCLE)?.toLifecycleState() ?: StrictModeLifecycleState.Inactive,
            configuration = configuration,
            activationGraceStartedAtMillis = persistence.read(KEY_GRACE_STARTED_AT)?.toLongOrNull(),
            activationGraceEndsAtMillis = persistence.read(KEY_GRACE_ENDS_AT)?.toLongOrNull(),
            activatedAtMillis = persistence.read(KEY_ACTIVATED_AT)?.toLongOrNull(),
            expiresAtMillis = persistence.read(KEY_EXPIRES_AT)?.toLongOrNull(),
            deactivationStartedAtMillis = persistence.read(KEY_DEACTIVATION_STARTED_AT)?.toLongOrNull(),
            deactivationAvailableAtMillis = persistence.read(KEY_DEACTIVATION_AVAILABLE_AT)?.toLongOrNull()
        )
    }

    private fun saveState(state: StrictModeState) {
        persistence.write(
            mapOf(
                KEY_LIFECYCLE to state.lifecycleState.name,
                KEY_DURATION_TYPE to state.configuration.durationType.name,
                KEY_TIMED_DURATION_MILLIS to state.configuration.timedDurationMillis?.toString(),
                KEY_DEACTIVATION_METHOD to state.configuration.deactivationMethod.name,
                KEY_DEACTIVATION_COUNTDOWN_MILLIS to state.configuration.deactivationCountdownMillis?.toString(),
                KEY_GRACE_STARTED_AT to state.activationGraceStartedAtMillis?.toString(),
                KEY_GRACE_ENDS_AT to state.activationGraceEndsAtMillis?.toString(),
                KEY_ACTIVATED_AT to state.activatedAtMillis?.toString(),
                KEY_EXPIRES_AT to state.expiresAtMillis?.toString(),
                KEY_DEACTIVATION_STARTED_AT to state.deactivationStartedAtMillis?.toString(),
                KEY_DEACTIVATION_AVAILABLE_AT to state.deactivationAvailableAtMillis?.toString()
            )
        )
    }

    private fun StrictModeState.isProtectionLifecycle(): Boolean {
        return lifecycleState == StrictModeLifecycleState.Active ||
            lifecycleState == StrictModeLifecycleState.DeactivationCounting ||
            lifecycleState == StrictModeLifecycleState.DeactivationReady
    }

    private fun String.toLifecycleState(): StrictModeLifecycleState? {
        return StrictModeLifecycleState.entries.firstOrNull { it.name == this }
    }

    private fun String.toDurationType(): StrictModeDurationType? {
        return StrictModeDurationType.entries.firstOrNull { it.name == this }
    }

    private fun String.toDeactivationMethod(): StrictModeDeactivationMethod? {
        return StrictModeDeactivationMethod.entries.firstOrNull { it.name == this }
    }

    private fun safeAdd(left: Long, right: Long): Long? {
        if (right < 0L) return null
        return if (left > Long.MAX_VALUE - right) null else left + right
    }

    companion object {
        const val ACTIVATION_GRACE_MILLIS = 30_000L
        const val MAX_TIMED_DURATION_MILLIS = 30L * 24L * 60L * 60_000L
        const val MAX_DEACTIVATION_COUNTDOWN_MILLIS = 30L * 24L * 60L * 60_000L
        private const val KEY_LIFECYCLE = "lifecycle"
        private const val KEY_DURATION_TYPE = "duration_type"
        private const val KEY_TIMED_DURATION_MILLIS = "timed_duration_millis"
        private const val KEY_DEACTIVATION_METHOD = "deactivation_method"
        private const val KEY_DEACTIVATION_COUNTDOWN_MILLIS = "deactivation_countdown_millis"
        private const val KEY_GRACE_STARTED_AT = "grace_started_at"
        private const val KEY_GRACE_ENDS_AT = "grace_ends_at"
        private const val KEY_ACTIVATED_AT = "activated_at"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_DEACTIVATION_STARTED_AT = "deactivation_started_at"
        private const val KEY_DEACTIVATION_AVAILABLE_AT = "deactivation_available_at"
    }
}
