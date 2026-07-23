package com.example.earnitv2

import android.content.Context
import androidx.core.content.edit

enum class OnboardingStep {
    Value,
    Example,
    PermissionIntroduction,
    EarningRationale,
    EarningAllowed,
    BlockingRationale,
    BlockingAllowed,
    Ready
}

data class OnboardingPermissionState(
    val earningProgressAllowed: Boolean,
    val appBlockingAllowed: Boolean
) {
    val isReady: Boolean = earningProgressAllowed && appBlockingAllowed
}

internal fun firstPermissionStep(state: OnboardingPermissionState): OnboardingStep =
    if (state.earningProgressAllowed) OnboardingStep.EarningAllowed else OnboardingStep.EarningRationale

internal fun blockingPermissionStep(state: OnboardingPermissionState): OnboardingStep =
    if (state.appBlockingAllowed) OnboardingStep.BlockingAllowed else OnboardingStep.BlockingRationale

internal fun focusedRepairStep(state: OnboardingPermissionState): OnboardingStep = when {
    !state.earningProgressAllowed -> OnboardingStep.EarningRationale
    !state.appBlockingAllowed -> OnboardingStep.BlockingRationale
    else -> OnboardingStep.Ready
}

internal fun nextOnboardingStep(
    current: OnboardingStep,
    permissions: OnboardingPermissionState
): OnboardingStep = when (current) {
    OnboardingStep.Value -> OnboardingStep.Example
    OnboardingStep.Example -> OnboardingStep.PermissionIntroduction
    OnboardingStep.PermissionIntroduction -> firstPermissionStep(permissions)
    OnboardingStep.EarningRationale -> if (permissions.earningProgressAllowed) {
        OnboardingStep.EarningAllowed
    } else {
        OnboardingStep.EarningRationale
    }
    OnboardingStep.EarningAllowed -> blockingPermissionStep(permissions)
    OnboardingStep.BlockingRationale -> if (permissions.appBlockingAllowed) {
        OnboardingStep.BlockingAllowed
    } else {
        OnboardingStep.BlockingRationale
    }
    OnboardingStep.BlockingAllowed -> if (permissions.isReady) OnboardingStep.Ready else focusedRepairStep(permissions)
    OnboardingStep.Ready -> OnboardingStep.Ready
}

internal fun previousOnboardingStep(current: OnboardingStep): OnboardingStep? = when (current) {
    OnboardingStep.Value -> null
    OnboardingStep.Example -> OnboardingStep.Value
    OnboardingStep.PermissionIntroduction -> OnboardingStep.Example
    OnboardingStep.EarningRationale -> OnboardingStep.PermissionIntroduction
    OnboardingStep.EarningAllowed -> OnboardingStep.EarningRationale
    OnboardingStep.BlockingRationale -> OnboardingStep.EarningAllowed
    OnboardingStep.BlockingAllowed -> OnboardingStep.BlockingRationale
    OnboardingStep.Ready -> OnboardingStep.BlockingAllowed
}

internal fun reconcileOnboardingStep(
    current: OnboardingStep,
    permissions: OnboardingPermissionState
): OnboardingStep = when {
    current == OnboardingStep.Ready && !permissions.isReady -> focusedRepairStep(permissions)
    current == OnboardingStep.EarningAllowed && !permissions.earningProgressAllowed -> OnboardingStep.EarningRationale
    current == OnboardingStep.BlockingAllowed && !permissions.appBlockingAllowed -> OnboardingStep.BlockingRationale
    current == OnboardingStep.EarningRationale && permissions.earningProgressAllowed -> OnboardingStep.EarningAllowed
    current == OnboardingStep.BlockingRationale && permissions.appBlockingAllowed -> OnboardingStep.BlockingAllowed
    else -> current
}

data class OnboardingMigrationDecision(
    val seen: Boolean,
    val shouldPersist: Boolean
)

enum class OnboardingExitAction { CreateFirstRule, GoHome, NotNow, AccidentalExit }

enum class OnboardingExitDestination { RuleBuilder, Home }

data class OnboardingExitEffect(
    val markSeen: Boolean,
    val destination: OnboardingExitDestination?
)

internal fun onboardingExitEffect(action: OnboardingExitAction): OnboardingExitEffect = when (action) {
    OnboardingExitAction.CreateFirstRule -> OnboardingExitEffect(true, OnboardingExitDestination.RuleBuilder)
    OnboardingExitAction.GoHome,
    OnboardingExitAction.NotNow -> OnboardingExitEffect(true, OnboardingExitDestination.Home)
    OnboardingExitAction.AccidentalExit -> OnboardingExitEffect(false, null)
}

internal fun onboardingMigrationDecision(
    storedSeen: Boolean?,
    legacySeen: Boolean,
    hasDurablePriorUse: Boolean
): OnboardingMigrationDecision = if (storedSeen != null) {
    OnboardingMigrationDecision(storedSeen, shouldPersist = false)
} else {
    OnboardingMigrationDecision(
        seen = legacySeen || hasDurablePriorUse,
        shouldPersist = true
    )
}

class EarnItOnboardingStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun initialize(hasDurablePriorUse: Boolean): Boolean {
        val storedSeen = if (prefs.contains(KEY_SEEN)) prefs.getBoolean(KEY_SEEN, false) else null
        val decision = onboardingMigrationDecision(
            storedSeen = storedSeen,
            legacySeen = prefs.getBoolean(KEY_LEGACY_FIRST_LAUNCH_COMPLETE, false),
            hasDurablePriorUse = hasDurablePriorUse
        )
        if (decision.shouldPersist) {
            prefs.edit(commit = true) { putBoolean(KEY_SEEN, decision.seen) }
        }
        return decision.seen
    }

    fun isSeen(): Boolean = prefs.getBoolean(KEY_SEEN, false)

    fun markSeen() {
        prefs.edit {
            putBoolean(KEY_SEEN, true)
            putBoolean(KEY_REPLAY_ACTIVE, false)
        }
    }

    fun step(): OnboardingStep {
        val saved = prefs.getString(KEY_STEP, null)
        return OnboardingStep.entries.firstOrNull { it.name == saved } ?: OnboardingStep.Value
    }

    fun saveStep(step: OnboardingStep) {
        prefs.edit { putString(KEY_STEP, step.name) }
    }

    fun beginReplay() {
        prefs.edit {
            putBoolean(KEY_REPLAY_ACTIVE, true)
            putString(KEY_STEP, OnboardingStep.Value.name)
        }
    }

    fun replayActive(): Boolean = prefs.getBoolean(KEY_REPLAY_ACTIVE, false)

    fun finishReplay() {
        prefs.edit { putBoolean(KEY_REPLAY_ACTIVE, false) }
    }

    companion object {
        private const val PREFS_NAME = "earnit_setup"
        private const val KEY_SEEN = "onboarding_seen"
        private const val KEY_STEP = "onboarding_step"
        private const val KEY_REPLAY_ACTIVE = "onboarding_replay_active"
        private const val KEY_LEGACY_FIRST_LAUNCH_COMPLETE = "first_launch_complete"
    }
}
