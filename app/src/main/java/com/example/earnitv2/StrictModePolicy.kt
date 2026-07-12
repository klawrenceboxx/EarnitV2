package com.example.earnitv2

internal object StrictModePolicy {
    fun isRuleProtected(
        strictModeState: StrictModeState,
        rule: EarnItRuleStore.Rule,
        pauseExpirations: Map<String, Long> = emptyMap()
    ): Boolean {
        return strictModeState.lifecycleState == StrictModeLifecycleState.Active &&
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
