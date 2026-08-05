package com.kaleel.earnitv2

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EarnItBlockingExemptionTest {
    @Test
    fun earnItPackage_isAlwaysRecognizedAsExempt() {
        assertTrue(isEarnItPackage("com.kaleel.earnitv2", "com.kaleel.earnitv2"))
    }

    @Test
    fun otherPackages_areNotExemptFromBlocking() {
        assertFalse(isEarnItPackage("com.instagram.android", "com.kaleel.earnitv2"))
    }
}
