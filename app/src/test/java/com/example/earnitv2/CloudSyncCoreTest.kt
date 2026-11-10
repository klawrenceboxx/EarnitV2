package com.kaleel.earnitv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSyncCoreTest {
    private val android = SyncCollections.PLATFORM_ANDROID

    private fun rule(
        id: String = "rule-1",
        blocked: List<String> = listOf("com.instagram.android"),
        ratio: Int = 2,
        enabled: Boolean = true
    ) = EarnItRuleStore.Rule(
        id = id,
        productivePackage = "com.duolingo",
        productiveName = "Duolingo",
        blockedApps = blocked.map { EarnItRuleStore.RuleApp(it, it.substringAfterLast('.')) },
        rewardSecondsPerProductiveSecond = ratio,
        activeDays = setOf(1, 2, 3, 4, 5),
        startMinute = 9 * 60,
        endMinute = 17 * 60,
        enabled = enabled,
        blockedDomains = listOf("youtube.com")
    )

    private fun plan(
        local: Map<String, String>,
        journal: Map<String, SyncJournalEntry> = emptyMap(),
        remote: List<SyncRecord> = emptyList(),
        now: Long = 1_000L,
        device: String = "device-a",
        protectedIds: Set<String> = emptySet()
    ) = SyncPlanner.plan(SyncCollections.RULES, local, journal, remote, now, device, android, protectedIds)

    private fun remote(id: String, payload: String, at: Long, deleted: Boolean = false, device: String = "device-b") =
        SyncRecord(SyncCollections.RULES, id, payload, at, deleted, device, android)

    @Test
    fun jsonRoundTripsNestedValuesAndEscapes() {
        val text = """{"a":[1,2.5,true,null,"q\"\\\né"],"b":{"c":-7}}"""
        val decoded = SyncJson.decode(text)
        assertEquals(decoded, SyncJson.decode(SyncJson.encode(decoded)))
        assertEquals("q\"\\\né", ((decoded as Map<*, *>)["a"] as List<*>)[4])
    }

    @Test
    fun canonicalTextIgnoresKeyOrderAndWhitespace() {
        assertEquals(
            SyncJson.canonicalText("""{"b":1,"a":{"y":2,"x":[3,{"k":1,"j":2}]}}"""),
            SyncJson.canonicalText("""{ "a": {"x":[3,{"j":2,"k":1}], "y":2}, "b": 1 }""")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun malformedJsonIsRejected() {
        SyncJson.decode("""{"a":1,}""")
    }

    @Test
    fun ruleSurvivesEncodeDecodeEncode() {
        val original = rule().copy(
            type = EarnItRuleStore.RuleType.CompleteToUnlock,
            requirements = listOf(
                EarnItRuleStore.RuleRequirement(EarnItRuleStore.RuleApp("org.lichess.mobileV2", "Lichess"), 600L)
            ),
            timeWindows = listOf(EarnItRuleStore.TimeWindow(480, 720), EarnItRuleStore.TimeWindow(1_320, 120)),
            requiresDailyCommitment = true
        )
        val payload = RuleSyncCodec.encode(original)
        val decoded = RuleSyncCodec.decode(original.id, payload)!!
        assertEquals(payload, RuleSyncCodec.encode(decoded))
        assertEquals(original.type, decoded.type)
        assertEquals(original.blockedApps, decoded.blockedApps)
        assertEquals(original.requirements, decoded.requirements)
        assertEquals(original.effectiveTimeWindows, decoded.effectiveTimeWindows)
        assertEquals(original.activeDays, decoded.activeDays)
        assertEquals(original.normalizedBlockedDomains, decoded.normalizedBlockedDomains)
        assertTrue(decoded.requiresDailyCommitment)
    }

    @Test
    fun enabledStateIsNotPartOfTheSyncedPayload() {
        assertEquals(RuleSyncCodec.encode(rule(enabled = true)), RuleSyncCodec.encode(rule(enabled = false)))
    }

    @Test
    fun remoteTermsNeverSwitchALocalRuleOnOrOff() {
        val local = rule(enabled = false).copy(inactiveReason = RuleInactiveReason.PremiumExpired, lastActivatedAtMillis = 42L)
        val incoming = RuleSyncCodec.decode(local.id, RuleSyncCodec.encode(rule(ratio = 5)))!!
        val merged = RuleSyncCodec.mergeIntoLocal(local, incoming)
        assertFalse(merged.enabled)
        assertEquals(RuleInactiveReason.PremiumExpired, merged.inactiveReason)
        assertEquals(42L, merged.lastActivatedAtMillis)
        assertEquals(5, merged.rewardSecondsPerProductiveSecond)
    }

    @Test
    fun payloadsFromNewerClientsOrWithNothingToBlockAreIgnored() {
        assertNull(RuleSyncCodec.decode("x", """{"v":99,"type":"EarnRewardTime"}"""))
        assertNull(RuleSyncCodec.decode("x", """{"v":1,"type":"EarnRewardTime","blockedApps":[]}"""))
        assertNull(RuleSyncCodec.decode("x", "not json"))
    }

    @Test
    fun firstSyncPushesLocalRulesAndPullsRemoteOnes() {
        val mine = RuleSyncCodec.encode(rule("mine"))
        val theirs = RuleSyncCodec.encode(rule("theirs"))
        val result = plan(local = mapOf("mine" to mine), remote = listOf(remote("theirs", theirs, 500L)))
        assertEquals(listOf("mine"), result.pushes.map { it.recordId })
        assertEquals(listOf("theirs"), result.applyUpserts.map { it.recordId })
        assertTrue(result.applyDeletes.isEmpty())
        assertEquals(setOf("mine", "theirs"), result.journal.keys)
    }

    @Test
    fun nothingHappensWhenBothSidesAgree() {
        val payload = RuleSyncCodec.encode(rule())
        val journal = mapOf("rule-1" to SyncJournalEntry(SyncPlanner.contentHash(payload), 500L, false))
        val result = plan(mapOf("rule-1" to payload), journal, listOf(remote("rule-1", payload, 500L, device = "device-a")))
        assertTrue(result.pushes.isEmpty() && result.applyUpserts.isEmpty() && result.applyDeletes.isEmpty())
    }

    @Test
    fun aLocalEditIsDetectedWithoutAnySavePathRecordingIt() {
        val before = RuleSyncCodec.encode(rule(ratio = 2))
        val after = RuleSyncCodec.encode(rule(ratio = 5))
        val journal = mapOf("rule-1" to SyncJournalEntry(SyncPlanner.contentHash(before), 500L, false))
        val result = plan(mapOf("rule-1" to after), journal, listOf(remote("rule-1", before, 500L)), now = 900L)
        assertEquals(1, result.pushes.size)
        assertEquals(after, result.pushes.single().payloadJson)
        assertEquals(900L, result.pushes.single().updatedAtMillis)
        assertTrue(result.applyUpserts.isEmpty())
    }

    @Test
    fun aNewerRemoteEditReplacesAnUntouchedLocalRule() {
        val before = RuleSyncCodec.encode(rule(ratio = 2))
        val after = RuleSyncCodec.encode(rule(ratio = 5))
        val journal = mapOf("rule-1" to SyncJournalEntry(SyncPlanner.contentHash(before), 500L, false))
        val result = plan(mapOf("rule-1" to before), journal, listOf(remote("rule-1", after, 800L)))
        assertEquals(after, result.applyUpserts.single().payloadJson)
        assertTrue(result.pushes.isEmpty())
    }

    @Test
    fun whenBothSidesEditedTheLaterWriteWins() {
        val base = RuleSyncCodec.encode(rule(ratio = 1))
        val mine = RuleSyncCodec.encode(rule(ratio = 2))
        val theirs = RuleSyncCodec.encode(rule(ratio = 5))
        val journal = mapOf("rule-1" to SyncJournalEntry(SyncPlanner.contentHash(base), 100L, false))
        val iWin = plan(mapOf("rule-1" to mine), journal, listOf(remote("rule-1", theirs, 800L)), now = 900L)
        assertEquals(mine, iWin.pushes.single().payloadJson)
        val theyWin = plan(mapOf("rule-1" to mine), journal, listOf(remote("rule-1", theirs, 950L)), now = 900L)
        assertEquals(theirs, theyWin.applyUpserts.single().payloadJson)
    }

    @Test
    fun aLocalDeleteBecomesATombstone() {
        val payload = RuleSyncCodec.encode(rule())
        val journal = mapOf("rule-1" to SyncJournalEntry(SyncPlanner.contentHash(payload), 500L, false))
        val result = plan(emptyMap(), journal, listOf(remote("rule-1", payload, 500L)), now = 900L)
        assertTrue(result.pushes.single().deleted)
        assertTrue(result.journal.getValue("rule-1").deleted)
        // The tombstone is remembered, so the deleted Rule is not pulled back on the next pass.
        val next = plan(emptyMap(), result.journal, listOf(remote("rule-1", "{}", 900L, deleted = true, device = "device-a")))
        assertTrue(next.pushes.isEmpty() && next.applyUpserts.isEmpty() && next.applyDeletes.isEmpty())
    }

    @Test
    fun aRemoteDeleteRemovesTheLocalRule() {
        val payload = RuleSyncCodec.encode(rule())
        val journal = mapOf("rule-1" to SyncJournalEntry(SyncPlanner.contentHash(payload), 500L, false))
        val result = plan(mapOf("rule-1" to payload), journal, listOf(remote("rule-1", "{}", 800L, deleted = true)))
        assertEquals(listOf("rule-1"), result.applyDeletes)
        assertTrue(result.pushes.isEmpty())
    }

    @Test
    fun editingADeletedRuleLaterBringsItBack() {
        val edited = RuleSyncCodec.encode(rule(ratio = 5))
        val journal = mapOf("rule-1" to SyncJournalEntry(SyncPlanner.contentHash(RuleSyncCodec.encode(rule())), 500L, false))
        val result = plan(mapOf("rule-1" to edited), journal, listOf(remote("rule-1", "{}", 800L, deleted = true)), now = 900L)
        assertTrue(result.applyDeletes.isEmpty())
        assertFalse(result.pushes.single().deleted)
    }

    @Test
    fun strictModeHoldsBackRemoteEditsAndDeletesButStillAcceptsNewRules() {
        val payload = RuleSyncCodec.encode(rule())
        val weaker = RuleSyncCodec.encode(rule(blocked = emptyList()))
        val journal = mapOf("rule-1" to SyncJournalEntry(SyncPlanner.contentHash(payload), 500L, false))
        val local = mapOf("rule-1" to payload)

        val edit = plan(local, journal, listOf(remote("rule-1", weaker, 5_000L)), now = 900L, protectedIds = setOf("rule-1"))
        assertTrue(edit.applyUpserts.isEmpty())
        assertEquals(listOf("rule-1"), edit.heldBackIds)
        assertEquals(payload, edit.pushes.single().payloadJson)
        assertTrue("held-back local copy must become the newest write", edit.pushes.single().updatedAtMillis > 5_000L)

        val delete = plan(local, journal, listOf(remote("rule-1", "{}", 5_000L, deleted = true)), now = 900L, protectedIds = setOf("rule-1"))
        assertTrue(delete.applyDeletes.isEmpty())
        assertFalse(delete.pushes.single().deleted)

        val added = plan(local, journal, listOf(remote("rule-1", payload, 500L), remote("extra", RuleSyncCodec.encode(rule("extra")), 600L)), protectedIds = setOf("rule-1"))
        assertEquals(listOf("extra"), added.applyUpserts.map { it.recordId })
    }

    @Test
    fun recordsFromOtherPlatformsAreLeftAlone() {
        val ios = SyncRecord(SyncCollections.RULES, "ios-rule", """{"v":1}""", 800L, false, "iphone", "ios")
        val result = plan(emptyMap(), emptyMap(), listOf(ios))
        assertTrue(result.pushes.isEmpty() && result.applyUpserts.isEmpty() && result.applyDeletes.isEmpty())
        assertTrue(result.journal.isEmpty())
    }

    @Test
    fun journalRoundTrips() {
        val journal = mapOf(
            "a" to SyncJournalEntry("abc", 12L, false),
            "b" to SyncJournalEntry("", 34L, true)
        )
        assertEquals(journal, SyncJournalCodec.decode(SyncJournalCodec.encode(journal)))
        assertTrue(SyncJournalCodec.decode("garbage").isEmpty())
    }

    @Test
    fun contentHashIsStableAndSensitive() {
        assertEquals(SyncPlanner.contentHash("abc"), SyncPlanner.contentHash("abc"))
        assertNotEquals(SyncPlanner.contentHash("abc"), SyncPlanner.contentHash("abd"))
        assertEquals(64, SyncPlanner.contentHash("abc").length)
    }
}
