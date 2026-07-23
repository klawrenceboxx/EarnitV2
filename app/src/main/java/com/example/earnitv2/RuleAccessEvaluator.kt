package com.example.earnitv2

object RuleAccessEvaluator {
    enum class DenialReason {
        ScheduledBlockActive,
        CompleteToUnlockIncomplete,
        OutOfRewardTime
    }

    data class RuleRuntimeState(
        val remainingRewardSeconds: Long = 0L,
        val requirementProgressSeconds: Map<String, Long> = emptyMap()
    )

    data class RuleDenial(
        val rule: EarnItRuleStore.Rule,
        val reason: DenialReason
    )

    data class Result(
        val denials: List<RuleDenial>,
        val spendRule: EarnItRuleStore.Rule?
    ) {
        val allowed: Boolean = denials.isEmpty()
        val primaryDenial: RuleDenial? = denials.minByOrNull { denialPriority(it.reason) }
    }

    fun evaluate(
        rules: List<EarnItRuleStore.Rule>,
        blockedPackage: String,
        day: Int,
        minuteOfDay: Int,
        runtimeState: (EarnItRuleStore.Rule) -> RuleRuntimeState
    ): Result {
        return evaluateMatching(rules.filter { rule ->
            rule.enabled && rule.blockedAppForPackage(blockedPackage) != null
        }, day, minuteOfDay, runtimeState)
    }

    fun evaluateDomain(
        rules: List<EarnItRuleStore.Rule>,
        hostname: String,
        day: Int,
        minuteOfDay: Int,
        runtimeState: (EarnItRuleStore.Rule) -> RuleRuntimeState
    ): Result = evaluateMatching(
        rules.filter { it.enabled && it.blockedDomainForHost(hostname) != null },
        day,
        minuteOfDay,
        runtimeState
    )

    private fun evaluateMatching(
        matchingRules: List<EarnItRuleStore.Rule>,
        day: Int,
        minuteOfDay: Int,
        runtimeState: (EarnItRuleStore.Rule) -> RuleRuntimeState
    ): Result {
        val denials = matchingRules.mapNotNull { rule ->
            evaluateRule(rule, day, minuteOfDay, runtimeState(rule))
        }
        val spendRule = if (denials.isEmpty()) {
            matchingRules.firstOrNull { rule ->
                rule.type == EarnItRuleStore.RuleType.EarnRewardTime &&
                    rule.isActiveAt(day, minuteOfDay) &&
                    runtimeState(rule).remainingRewardSeconds > 0L
            }
        } else {
            null
        }
        return Result(denials = denials, spendRule = spendRule)
    }

    fun evaluateRule(
        rule: EarnItRuleStore.Rule,
        day: Int,
        minuteOfDay: Int,
        state: RuleRuntimeState
    ): RuleDenial? {
        if (!rule.enabled || (rule.blockedApps.isEmpty() && rule.normalizedBlockedDomains.isEmpty())) return null
        val active = rule.isActiveAt(day, minuteOfDay)
        return when (rule.type) {
            EarnItRuleStore.RuleType.ScheduledBlock -> {
                if (active) RuleDenial(rule, DenialReason.ScheduledBlockActive) else null
            }
            EarnItRuleStore.RuleType.CompleteToUnlock -> {
                if (active && !requirementsComplete(rule, state.requirementProgressSeconds)) {
                    RuleDenial(rule, DenialReason.CompleteToUnlockIncomplete)
                } else {
                    null
                }
            }
            EarnItRuleStore.RuleType.EarnRewardTime -> {
                if (active && state.remainingRewardSeconds <= 0L) {
                    RuleDenial(rule, DenialReason.OutOfRewardTime)
                } else {
                    null
                }
            }
        }
    }

    fun requirementsComplete(
        rule: EarnItRuleStore.Rule,
        progressSeconds: Map<String, Long>
    ): Boolean {
        return rule.requirements.isNotEmpty() && rule.requirements.all { requirement ->
            (progressSeconds[requirement.app.packageName] ?: 0L) >= requirement.requiredSeconds
        }
    }

    private fun denialPriority(reason: DenialReason): Int {
        return when (reason) {
            DenialReason.ScheduledBlockActive -> 0
            DenialReason.CompleteToUnlockIncomplete -> 1
            DenialReason.OutOfRewardTime -> 2
        }
    }
}
