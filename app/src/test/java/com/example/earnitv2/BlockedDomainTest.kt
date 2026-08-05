package com.kaleel.earnitv2

import org.junit.Assert.*
import org.junit.Test

class BlockedDomainTest {
    @Test fun normalizesUrlsAndCommonPrefixes() {
        assertEquals("youtube.com", DomainNormalizer.normalize("https://www.YouTube.com/watch?v=123"))
        assertEquals("reddit.com", DomainNormalizer.normalize("www.reddit.com/r/android"))
        assertEquals("youtube.com", DomainNormalizer.normalize("m.youtube.com"))
        assertEquals("example.com", DomainNormalizer.normalize("https://example.com:8443/a?q=1#x"))
    }

    @Test fun handlesInternationalDomainsAsAscii() {
        assertEquals("xn--bcher-kva.de", DomainNormalizer.normalize("https://bücher.de/lesen"))
    }

    @Test fun rejectsMalformedOrUnsafeInput() {
        listOf("", "hello", "javascript:alert(1)", "https://bad domain.com", "https://-bad.com", "127.0.0.1")
            .forEach { assertNull(it, DomainNormalizer.normalize(it)) }
    }

    @Test fun exactAndSubdomainMatchingUsesHostnameBoundaries() {
        assertTrue(DomainMatcher.matches("youtube.com", "youtube.com"))
        assertTrue(DomainMatcher.matches("youtube.com", "music.youtube.com"))
        assertFalse(DomainMatcher.matches("youtube.com", "notyoutube.com"))
        assertFalse(DomainMatcher.matches("youtube.com", "youtube.com.example.org"))
        assertFalse(DomainMatcher.matches("youtube.com", "myyoutube.com"))
    }

    @Test fun selectionRejectsDuplicatesAfterNormalization() {
        val first = addDomainToSelection(emptyList(), "https://www.YouTube.com/watch?v=1")
        assertEquals(listOf("youtube.com"), first.domains)
        val duplicate = addDomainToSelection(first.domains, "m.youtube.com")
        assertNotNull(duplicate.error)
        assertEquals(first.domains, duplicate.domains)
    }
}
