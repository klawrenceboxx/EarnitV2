package com.example.earnitv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEntitlementPolicyTest {
    private val free = FeatureAccessPolicy(EntitlementState.Free)
    private val premium = FeatureAccessPolicy(
        EntitlementState(EntitlementStatus.Active, EntitlementSource.Purchase)
    )

    @Test
    fun freeCanActivateFirstAndSecondButNotThird() {
        var rules = listOf(rule("one", false), rule("two", false), rule("three", false))
        rules = (RuleEntitlementPolicy.activate(rules, "one", free, 1) as RuleActivationResult.Allowed).rules
        rules = (RuleEntitlementPolicy.activate(rules, "two", free, 2) as RuleActivationResult.Allowed).rules
        val denied = RuleEntitlementPolicy.activate(rules, "three", free, 3)

        assertTrue(denied is RuleActivationResult.Denied)
        assertEquals(listOf("one", "two"), rules.filter { it.enabled }.map { it.id })
    }

    @Test
    fun premiumCanActivateMoreThanTwo() {
        var rules = (1..4).map { rule("$it", false) }
        rules.indices.forEach { index ->
            rules = (RuleEntitlementPolicy.activate(rules, "${index + 1}", premium, index.toLong()) as RuleActivationResult.Allowed).rules
        }
        assertEquals(4, rules.count { it.enabled })
    }

    @Test
    fun inactiveRuleCanBeSavedBeyondFreeLimit() {
        val existing = listOf(rule("one", true), rule("two", true))
        val result = RuleEntitlementPolicy.save(existing, rule("draft", false), free, 3)
        assertTrue(result is RuleActivationResult.Allowed)
        assertEquals(3, (result as RuleActivationResult.Allowed).rules.size)
    }

    @Test
    fun deniedSaveDoesNotReturnPartiallyPersistableState() {
        val existing = listOf(rule("one", true), rule("two", true))
        val result = RuleEntitlementPolicy.save(existing, rule("three", true), free, 3)
        assertTrue(result is RuleActivationResult.Denied)
        assertEquals(2, existing.size)
        assertEquals(2, existing.count { it.enabled })
    }

    @Test
    fun deactivatingOneAllowsAnother() {
        val starting = listOf(rule("one", false), rule("two", true), rule("three", true))
        val result = RuleEntitlementPolicy.activate(starting, "one", free, 4)
        assertTrue(result is RuleActivationResult.Denied)

        val withSlot = starting.map { if (it.id == "two") it.copy(enabled = false) else it }
        val activated = RuleEntitlementPolicy.activate(withSlot, "one", free, 5) as RuleActivationResult.Allowed
        assertEquals(setOf("one", "three"), activated.rules.filter { it.enabled }.map { it.id }.toSet())
    }

    @Test
    fun downgradeKeepsTwoMostRecentlyActivatedAndDeletesNothing() {
        val rules = listOf(
            rule("oldest", true, 10),
            rule("newest", true, 50),
            rule("middle", true, 30),
            rule("second_newest", true, 40),
            rule("older", true, 20)
        )
        val downgraded = RuleEntitlementPolicy.reconcileDowngrade(rules)

        assertEquals(5, downgraded.size)
        assertEquals(setOf("newest", "second_newest"), downgraded.filter { it.enabled }.map { it.id }.toSet())
        assertTrue(
            downgraded.filterNot { it.enabled }
                .all { it.inactiveReason == RuleInactiveReason.PremiumExpired }
        )
    }

    @Test
    fun downgradeIsIdempotentAndRestoringPremiumDoesNotSurpriseActivate() {
        val first = RuleEntitlementPolicy.reconcileDowngrade(
            listOf(rule("a", true, 1), rule("b", true, 2), rule("c", true, 3))
        )
        val second = RuleEntitlementPolicy.reconcileDowngrade(first)

        assertEquals(first, second)
        assertFalse(second.first { it.id == "a" }.enabled)
        assertEquals(RuleInactiveReason.PremiumExpired, second.first { it.id == "a" }.inactiveReason)
    }

    @Test
    fun tieBreakIsStableByRuleIdWhenLegacyTimestampsAreMissing() {
        val result = RuleEntitlementPolicy.reconcileDowngrade(
            listOf(rule("c", true), rule("a", true), rule("b", true))
        )
        assertEquals(setOf("a", "b"), result.filter { it.enabled }.map { it.id }.toSet())
    }

    @Test
    fun premiumInactiveMetadataRoundTripsAndLegacyRecordsRemainReadable() {
        val premiumInactive = rule("saved", false, 42).copy(
            inactiveReason = RuleInactiveReason.PremiumExpired
        )
        val roundTripped = EarnItRuleStore.decodeRules(EarnItRuleStore.encodeRules(listOf(premiumInactive))).single()
        assertEquals(premiumInactive.id, roundTripped.id)
        assertEquals(premiumInactive.lastActivatedAtMillis, roundTripped.lastActivatedAtMillis)
        assertEquals(premiumInactive.inactiveReason, roundTripped.inactiveReason)

        val legacy = rule("legacy", true)
        val legacyEncodedWithoutNewFields = EarnItRuleStore.encodeRules(listOf(legacy))
            .split("\u001F")
            .dropLast(2)
            .joinToString("\u001F")
        val migrated = EarnItRuleStore.decodeRules(legacyEncodedWithoutNewFields).single()
        assertTrue(migrated.enabled)
        assertEquals(0L, migrated.lastActivatedAtMillis)
        assertEquals(RuleInactiveReason.None, migrated.inactiveReason)
    }

    private fun rule(id: String, enabled: Boolean, activatedAt: Long = 0) = EarnItRuleStore.Rule(
        id = id,
        productivePackage = "earn.$id",
        productiveName = id,
        blockedApps = listOf(EarnItRuleStore.RuleApp("blocked.$id", "Blocked")),
        rewardSecondsPerProductiveSecond = 1,
        activeDays = EarnItRuleStore.allDays.toSet(),
        startMinute = 0,
        endMinute = 1_440,
        enabled = enabled,
        lastActivatedAtMillis = activatedAt
    )
}
