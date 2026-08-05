package com.kaleel.earnitv2

internal object StrictModePolicy {
    fun isRuleProtected(
        strictModeState: StrictModeState,
        rule: EarnItRuleStore.Rule,
        pauseExpirations: Map<String, Long> = emptyMap()
    ): Boolean {
        return strictModeState.lifecycleState.isStrictModeProtecting() &&
            rule.enabled &&
            rule.id !in pauseExpirations
    }

    fun canEditRule(strictModeState: StrictModeState, rule: EarnItRuleStore.Rule, pauseExpirations: Map<String, Long> = emptyMap()): Boolean {
        return !isRuleProtected(strictModeState, rule, pauseExpirations)
    }

    fun canPauseRule(strictModeState: StrictModeState, rule: EarnItRuleStore.Rule, pauseExpirations: Map<String, Long> = emptyMap()): Boolean {
        return !isRuleProtected(strictModeState, rule, pauseExpirations)
    }

    fun canDisableRule(strictModeState: StrictModeState, rule: EarnItRuleStore.Rule, pauseExpirations: Map<String, Long> = emptyMap()): Boolean {
        return !isRuleProtected(strictModeState, rule, pauseExpirations)
    }

    fun canDeleteRule(strictModeState: StrictModeState, rule: EarnItRuleStore.Rule, pauseExpirations: Map<String, Long> = emptyMap()): Boolean {
        return !isRuleProtected(strictModeState, rule, pauseExpirations)
    }

    fun canEnableRule(rule: EarnItRuleStore.Rule): Boolean = !rule.enabled

    fun canResumeRule(rule: EarnItRuleStore.Rule): Boolean = !rule.enabled
}

internal fun StrictModeLifecycleState.isStrictModeProtecting(): Boolean {
    return this == StrictModeLifecycleState.Active ||
        this == StrictModeLifecycleState.DeactivationCounting ||
        this == StrictModeLifecycleState.DeactivationReady
}

internal data class StrictModeRuleProtectionSummary(
    val protectedRules: List<EarnItRuleStore.Rule>,
    val unprotectedRules: List<EarnItRuleStore.Rule>,
    val pausedRuleIds: Set<String> = emptySet()
)

internal fun strictModeRuleProtectionSummary(
    strictModeState: StrictModeState,
    rules: List<EarnItRuleStore.Rule>,
    pauseExpirations: Map<String, Long> = emptyMap()
): StrictModeRuleProtectionSummary {
    val protectedRules = rules.filter { StrictModePolicy.isRuleProtected(strictModeState, it, pauseExpirations) }
    return StrictModeRuleProtectionSummary(
        protectedRules = protectedRules,
        unprotectedRules = rules - protectedRules.toSet(),
        pausedRuleIds = pauseExpirations.keys
    )
}
