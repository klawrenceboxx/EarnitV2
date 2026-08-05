package com.kaleel.earnitv2

sealed class ProtectedTarget {
    data class App(val packageName: String, val name: String) : ProtectedTarget()
    data class Website(val domain: String) : ProtectedTarget()
}

/** One precedence policy: a configured website wins over a separately protected browser app. */
object ProtectedTargetResolver {
    fun resolve(
        rules: List<EarnItRuleStore.Rule>,
        foregroundPackage: String,
        hostname: String?
    ): ProtectedTarget? {
        if (hostname != null) {
            rules.firstNotNullOfOrNull { it.blockedDomainForHost(hostname) }
                ?.let { return ProtectedTarget.Website(it) }
        }
        return rules.firstNotNullOfOrNull { it.blockedAppForPackage(foregroundPackage) }
            ?.let { ProtectedTarget.App(it.packageName, it.name) }
    }
}

/** Deterministic foreground-only clock; persistence remains owned by RewardLedger. */
class ProtectedUsageClock {
    data class Key(val ruleId: String, val targetId: String)
    private var key: Key? = null
    private var accountedAtMillis: Long = 0L

    fun start(newKey: Key, nowElapsedMillis: Long) {
        key = newKey
        accountedAtMillis = nowElapsedMillis
    }

    fun tick(nowElapsedMillis: Long): Long {
        if (key == null || accountedAtMillis == 0L || nowElapsedMillis <= accountedAtMillis) return 0L
        val seconds = (nowElapsedMillis - accountedAtMillis) / 1_000L
        if (seconds > 0L) accountedAtMillis += seconds * 1_000L
        return seconds
    }

    fun clear() {
        key = null
        accountedAtMillis = 0L
    }

    fun activeKey(): Key? = key
}
