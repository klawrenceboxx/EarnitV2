package com.example.earnitv2

internal enum class StrictModeCommitmentPreset(
    val fixedDurationMillis: Long?
) {
    OneHour(60L * 60_000L),
    TwentyFourHours(24L * 60L * 60_000L),
    SevenDays(7L * 24L * 60L * 60_000L),
    Custom(null)
}

internal fun StrictModeCommitmentPreset.durationMillis(
    customDurationMillis: Long? = null
): Long? {
    return fixedDurationMillis ?: customDurationMillis
}

internal fun strictModeCommitmentPresetFor(durationMillis: Long?): StrictModeCommitmentPreset {
    return StrictModeCommitmentPreset.entries.firstOrNull {
        it != StrictModeCommitmentPreset.Custom && it.fixedDurationMillis == durationMillis
    } ?: StrictModeCommitmentPreset.Custom
}

internal val StrictModeSetupState.selectedCommitmentPreset: StrictModeCommitmentPreset?
    get() = when {
        durationType == StrictModeDurationType.Indefinite -> null
        timedCustomVisible -> StrictModeCommitmentPreset.Custom
        else -> strictModeCommitmentPresetFor(timedDurationMillis)
    }

internal fun StrictModeSetupState.selectCommitmentPreset(
    preset: StrictModeCommitmentPreset
): StrictModeSetupState {
    return if (preset == StrictModeCommitmentPreset.Custom) {
        val preserveExistingCustom = durationType == StrictModeDurationType.Timed && timedCustomVisible
        copy(
            durationType = StrictModeDurationType.Timed,
            timedDurationMillis = if (preserveExistingCustom) timedDurationMillis else null,
            customTimedHours = if (preserveExistingCustom) customTimedHours else "",
            timedCustomVisible = true
        )
    } else {
        copy(
            durationType = StrictModeDurationType.Timed,
            timedDurationMillis = preset.durationMillis(),
            timedCustomVisible = false
        )
    }
}

internal fun StrictModeSetupState.returnToTimedCommitment(): StrictModeSetupState {
    val hasUsablePreviousCommitment = (timedDurationMillis ?: 0L) in
        1L..StrictModeStore.MAX_TIMED_DURATION_MILLIS
    return if (hasUsablePreviousCommitment) {
        copy(durationType = StrictModeDurationType.Timed)
    } else {
        selectCommitmentPreset(StrictModeCommitmentPreset.OneHour)
    }
}

internal fun StrictModeSetupState.editCustomCommitmentDraft(value: String): StrictModeSetupState {
    return copy(
        customTimedHours = value.filter(Char::isDigit).take(4),
        timedDurationMillis = null,
        timedCustomVisible = true
    )
}

internal fun StrictModeSetupState.chooseCustomCountdown(): StrictModeSetupState {
    return copy(
        deactivationCountdownMillis = null,
        customCountdownMinutes = "",
        countdownCustomVisible = true
    )
}

internal fun StrictModeSetupState.editCustomCountdownDraft(value: String): StrictModeSetupState {
    return copy(
        customCountdownMinutes = value.filter(Char::isDigit).take(4),
        deactivationCountdownMillis = null,
        countdownCustomVisible = true
    )
}
