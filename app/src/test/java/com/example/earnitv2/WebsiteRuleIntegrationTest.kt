package com.kaleel.earnitv2

import org.junit.Assert.*
import org.junit.Test

class WebsiteRuleIntegrationTest {
    private fun rule(
        apps: List<EarnItRuleStore.RuleApp> = emptyList(),
        domains: List<String> = listOf("youtube.com"),
        enabled: Boolean = true,
        type: EarnItRuleStore.RuleType = EarnItRuleStore.RuleType.EarnRewardTime
    ) = EarnItRuleStore.Rule(
        id = "r", productivePackage = "learn", productiveName = "Learn", blockedApps = apps,
        blockedDomains = domains, rewardSecondsPerProductiveSecond = 2,
        activeDays = setOf(1), startMinute = 0, endMinute = 1_440, enabled = enabled, type = type
    )

    @Test fun oldSerializationMigratesToEmptyDomainsAndNewSerializationRoundTrips() {
        val currentEmpty = EarnItRuleStore.encodeRules(listOf(rule(apps = listOf(EarnItRuleStore.RuleApp("social", "Social")), domains = emptyList())))
        val old = currentEmpty.substringBeforeLast("\u001F")
        assertTrue(EarnItRuleStore.decodeRules(old).single().normalizedBlockedDomains.isEmpty())
        val encoded = EarnItRuleStore.encodeRules(listOf(rule(domains = listOf("WWW.YouTube.com", "bad value"))))
        assertEquals(listOf("youtube.com"), EarnItRuleStore.decodeRules(encoded).single().normalizedBlockedDomains)
        assertFalse(encoded.contains("watch?v="))
    }

    @Test fun domainOnlyRuleBlocksAtZeroAndAllowsWithRewardTime() {
        val r = rule()
        val denied = RuleAccessEvaluator.evaluateDomain(listOf(r), "music.youtube.com", 1, 600) {
            RuleAccessEvaluator.RuleRuntimeState(remainingRewardSeconds = 0)
        }
        assertFalse(denied.allowed)
        assertEquals(RuleAccessEvaluator.DenialReason.OutOfRewardTime, denied.primaryDenial?.reason)
        val allowed = RuleAccessEvaluator.evaluateDomain(listOf(r), "youtube.com", 1, 600) {
            RuleAccessEvaluator.RuleRuntimeState(remainingRewardSeconds = 10)
        }
        assertTrue(allowed.allowed)
        assertEquals(r, allowed.spendRule)
    }

    @Test fun pauseAndScheduleAreSharedWithWebsites() {
        val paused = rule(enabled = false)
        assertTrue(RuleAccessEvaluator.evaluateDomain(listOf(paused), "youtube.com", 1, 600) {
            RuleAccessEvaluator.RuleRuntimeState(0)
        }.allowed)
        val active = rule(type = EarnItRuleStore.RuleType.ScheduledBlock)
        assertFalse(RuleAccessEvaluator.evaluateDomain(listOf(active), "youtube.com", 1, 600) {
            RuleAccessEvaluator.RuleRuntimeState()
        }.allowed)
        assertTrue(RuleAccessEvaluator.evaluateDomain(listOf(active), "youtube.com", 2, 600) {
            RuleAccessEvaluator.RuleRuntimeState()
        }.allowed)
    }

    @Test fun strictModeClassifiesDomainAddsAndRemovals() {
        val base = rule(domains = listOf("youtube.com"))
        assertNotEquals(StrictModeFingerprint.rule(base), StrictModeFingerprint.rule(base.copy(blockedDomains = listOf("reddit.com"))))
        assertEquals(RestrictionClassification.Stricter,
            RuleRestrictionPolicy.compare(base, base.copy(blockedDomains = listOf("youtube.com", "reddit.com"))).classification)
        assertEquals(RestrictionClassification.LessRestrictive,
            RuleRestrictionPolicy.compare(base, base.copy(blockedDomains = emptyList())).classification)
        assertEquals(RestrictionClassification.Equivalent,
            RuleRestrictionPolicy.compare(base, base.copy(blockedDomains = listOf("WWW.YouTube.com"))).classification)
    }

    @Test fun websiteTargetWinsOverChromeAppToPreventDoubleCharging() {
        val r = rule(apps = listOf(EarnItRuleStore.RuleApp(ChromeBrowserAdapter.CHROME_PACKAGE, "Chrome")))
        assertEquals(ProtectedTarget.Website("youtube.com"),
            ProtectedTargetResolver.resolve(listOf(r), ChromeBrowserAdapter.CHROME_PACKAGE, "m.youtube.com"))
        assertEquals(ProtectedTarget.App(ChromeBrowserAdapter.CHROME_PACKAGE, "Chrome"),
            ProtectedTargetResolver.resolve(listOf(r), ChromeBrowserAdapter.CHROME_PACKAGE, "example.org"))
        assertNull(ProtectedTargetResolver.resolve(listOf(r), "other", "example.org"))
    }

    @Test fun chromeParsingIsPrivacySafeAndToleratesMissingValues() {
        assertEquals("youtube.com", ChromeBrowserAdapter.parseAddressBarText("https://www.youtube.com/watch?v=private"))
        assertNull(ChromeBrowserAdapter.parseAddressBarText(null))
        assertNull(ChromeBrowserAdapter.parseAddressBarText("Search or type URL"))
    }

    @Test fun browserObserverClearsStalePageWhenBrowserIsLeftOrParsingFails() {
        var page: BrowserPage? = BrowserPage(ChromeBrowserAdapter.CHROME_PACKAGE, "youtube.com")
        val fake = object : BrowserAdapter {
            override fun supports(packageName: String) = packageName == ChromeBrowserAdapter.CHROME_PACKAGE
            override fun currentPage(root: android.view.accessibility.AccessibilityNodeInfo?) = page
            override fun redirectCurrentPageToPlaceholder(root: android.view.accessibility.AccessibilityNodeInfo?) = false
            override fun isPlaceholderVisible(root: android.view.accessibility.AccessibilityNodeInfo?) = false
        }
        val observer = CurrentBrowserPageObserver(listOf(fake))
        assertEquals("youtube.com", observer.observe(ChromeBrowserAdapter.CHROME_PACKAGE, null)?.normalizedHost)
        page = null
        assertNull(observer.observe(ChromeBrowserAdapter.CHROME_PACKAGE, null))
        assertNull(observer.current())
        page = BrowserPage(ChromeBrowserAdapter.CHROME_PACKAGE, "reddit.com")
        observer.observe(ChromeBrowserAdapter.CHROME_PACKAGE, null)
        observer.clear()
        assertNull(observer.current())
    }

    @Test fun foregroundUsageClockStopsAndSwitchesWithoutDoubleCounting() {
        val clock = ProtectedUsageClock()
        clock.start(ProtectedUsageClock.Key("website-rule", "youtube.com"), 1_000L)
        assertEquals(2L, clock.tick(3_600L))
        assertEquals(0L, clock.tick(3_900L))
        clock.start(ProtectedUsageClock.Key("website-rule", "reddit.com"), 4_000L)
        assertEquals(1L, clock.tick(5_100L))
        clock.clear()
        assertEquals(0L, clock.tick(10_000L))
        assertNull(clock.activeKey())
    }

    @Test fun websiteRedirectGateSuppressesLoopsButAllowsLaterAttempts() {
        val gate = WebsiteRedirectGate()
        assertTrue(gate.begin("rule", "youtube.com"))
        assertTrue(gate.isPending())
        assertFalse(gate.begin("rule", "youtube.com"))
        gate.complete()
        assertTrue(gate.begin("rule", "youtube.com"))
        gate.clear()
        assertFalse(gate.isPending())
        assertEquals("about:blank", ChromeBrowserAdapter.LOCAL_PLACEHOLDER)
        assertFalse(ChromeBrowserAdapter.LOCAL_PLACEHOLDER.contains("youtube.com"))
    }
}
