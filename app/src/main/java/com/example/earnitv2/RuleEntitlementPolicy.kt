package com.example.earnitv2

enum class RuleInactiveReason { None, PremiumExpired }

sealed interface RuleActivationResult {
    data class Allowed(val rules: List<EarnItRuleStore.Rule>) : RuleActivationResult
    data class Denied(val reason: String) : RuleActivationResult
}

object RuleEntitlementPolicy {
    fun activate(
        rules: List<EarnItRuleStore.Rule>,
        ruleId: String,
        policy: FeatureAccessPolicy,
        activatedAtMillis: Long
    ): RuleActivationResult {
        val target = rules.firstOrNull { it.id == ruleId }
            ?: return RuleActivationResult.Denied("Rule not found.")
        if (target.enabled) return RuleActivationResult.Allowed(rules)
        val limit = policy.activeRuleLimit
        if (limit != null && rules.count { it.enabled } >= limit) {
            return RuleActivationResult.Denied("Free includes up to 2 active Rules.")
        }
        return RuleActivationResult.Allowed(
            rules.map {
                if (it.id == ruleId) it.copy(
                    enabled = true,
                    inactiveReason = RuleInactiveReason.None,
                    lastActivatedAtMillis = activatedAtMillis
                ) else it
            }
        )
    }

    fun save(
        rules: List<EarnItRuleStore.Rule>,
        rule: EarnItRuleStore.Rule,
        policy: FeatureAccessPolicy,
        activatedAtMillis: Long
    ): RuleActivationResult {
        val previous = rules.firstOrNull { it.id == rule.id }
        val activates = rule.enabled && previous?.enabled != true
        val base = if (rules.any { it.id == rule.id }) {
            rules.map { if (it.id == rule.id) rule.copy(enabled = false) else it }
        } else {
            rules + rule.copy(enabled = false)
        }
        return if (activates) activate(base, rule.id, policy, activatedAtMillis)
        else RuleActivationResult.Allowed(
            base.map { if (it.id == rule.id) rule else it }
        )
    }

    fun reconcileDowngrade(
        rules: List<EarnItRuleStore.Rule>,
        freeLimit: Int = FeatureAccessPolicy.FREE_ACTIVE_RULE_LIMIT
    ): List<EarnItRuleStore.Rule> {
        val enabled = rules.filter { it.enabled }
        if (enabled.size <= freeLimit) return rules
        val retainedIds = enabled
            .sortedWith(
                compareByDescending<EarnItRuleStore.Rule> { it.lastActivatedAtMillis }
                    .thenBy { it.id }
            )
            .take(freeLimit)
            .mapTo(mutableSetOf()) { it.id }
        return rules.map { rule ->
            if (rule.enabled && rule.id !in retainedIds) {
                rule.copy(enabled = false, inactiveReason = RuleInactiveReason.PremiumExpired)
            } else {
                rule
            }
        }
    }
}
