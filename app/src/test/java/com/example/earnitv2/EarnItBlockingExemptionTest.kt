package com.example.earnitv2

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EarnItBlockingExemptionTest {
    @Test
    fun earnItPackage_isAlwaysRecognizedAsExempt() {
        assertTrue(isEarnItPackage("com.example.earnitv2", "com.example.earnitv2"))
    }

    @Test
    fun otherPackages_areNotExemptFromBlocking() {
        assertFalse(isEarnItPackage("com.instagram.android", "com.example.earnitv2"))
    }
}
