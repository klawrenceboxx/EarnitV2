package com.kaleel.earnitv2

data class CompleteRequirementUiState(
    val packageName: String,
    val name: String,
    val progressSeconds: Long?,
    val requiredSeconds: Long
) {
    val complete: Boolean = progressSeconds?.let { it >= requiredSeconds } ?: false
    val isComplete: Boolean = complete
    val progressLabel: String = progressSeconds?.let { progress ->
        "${formatRequirementProgressMinutes(progress)} / ${formatRequirementWholeMinutes(requiredSeconds)}"
    } ?: "${formatRequirementWholeMinutes(requiredSeconds)} required"
    val progressFraction: Float = when {
        requiredSeconds <= 0L -> if (complete) 1f else 0f
        else -> ((progressSeconds ?: 0L).toFloat() / requiredSeconds.toFloat()).coerceIn(0f, 1f)
    }
}

data class CompleteToUnlockRuleProgressUiState(
    val requirements: List<CompleteRequirementUiState>,
    val incompleteRequirements: List<CompleteRequirementUiState>,
    val remainingRequirementCount: Int,
    val isUnlocked: Boolean
)

data class CompactIncompleteRequirementsUiState(
    val visibleRequirements: List<CompleteRequirementUiState>,
    val remainingCount: Int
)

fun compactIncompleteRequirements(
    progress: CompleteToUnlockRuleProgressUiState
): CompactIncompleteRequirementsUiState {
    val incomplete = progress.incompleteRequirements
    return CompactIncompleteRequirementsUiState(
        visibleRequirements = incomplete.take(HOME_REQUIREMENT_LIMIT),
        remainingCount = (incomplete.size - HOME_REQUIREMENT_LIMIT).coerceAtLeast(0)
    )
}

fun completeToUnlockDetailRequirements(
    progress: CompleteToUnlockRuleProgressUiState
): List<CompleteRequirementUiState> = progress.requirements

typealias BlockedRequirementUiState = CompleteRequirementUiState

fun completeToUnlockProgressUiState(
    rule: EarnItRuleStore.Rule,
    progressSeconds: Map<String, Long>
): CompleteToUnlockRuleProgressUiState {
    if (rule.type != EarnItRuleStore.RuleType.CompleteToUnlock) {
        return CompleteToUnlockRuleProgressUiState(
            requirements = emptyList(),
            incompleteRequirements = emptyList(),
            remainingRequirementCount = 0,
            isUnlocked = false
        )
    }

    val requirements = rule.requirements.map { requirement ->
        val packageName = requirement.app.packageName
        CompleteRequirementUiState(
            packageName = packageName,
            name = requirement.app.name,
            progressSeconds = progressSeconds[packageName]?.coerceAtLeast(0L) ?: 0L,
            requiredSeconds = requirement.requiredSeconds.coerceAtLeast(0L)
        )
    }
    val incomplete = requirements.filterNot { it.complete }
    return CompleteToUnlockRuleProgressUiState(
        requirements = requirements,
        incompleteRequirements = incomplete,
        remainingRequirementCount = incomplete.size,
        isUnlocked = requirements.isNotEmpty() && incomplete.isEmpty()
    )
}

fun blockedRequirementUiStates(
    rule: EarnItRuleStore.Rule,
    progressSeconds: Map<String, Long>
): List<BlockedRequirementUiState> {
    return completeToUnlockProgressUiState(rule, progressSeconds).incompleteRequirements
}

internal fun formatRequirementWholeMinutes(totalSeconds: Long): String {
    val minutes = totalSeconds.coerceAtLeast(0L) / 60L
    return when {
        totalSeconds <= 0L -> "0 min"
        minutes > 0L -> "${minutes} min"
        else -> "<1 min"
    }
}

private fun formatRequirementProgressMinutes(totalSeconds: Long): String {
    val minutes = totalSeconds.coerceAtLeast(0L) / 60L
    return minutes.toString()
}

private const val HOME_REQUIREMENT_LIMIT = 2
