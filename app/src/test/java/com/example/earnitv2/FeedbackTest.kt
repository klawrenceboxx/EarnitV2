package com.example.earnitv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackTest {
    @Test fun categorySerialization_roundTripsTypedValues() {
        FeedbackCategory.entries.forEach { category ->
            assertEquals(category, FeedbackCategory.fromWire(category.wireValue))
        }
        assertNull(FeedbackCategory.fromWire("AI_GUESSED"))
    }

    @Test fun validation_rejectsBlankAndInvalidEmail() {
        assertTrue(FeedbackValidation.messageError("   ") != null)
        assertNull(FeedbackValidation.messageError("YouTube did not lock."))
        assertTrue(FeedbackValidation.emailError("not-an-email") != null)
        assertNull(FeedbackValidation.emailError(""))
        assertNull(FeedbackValidation.emailError("person@example.com"))
    }

    @Test fun diagnostics_areVersionedAndExcludeSensitiveFields() {
        val diagnostics = sampleDiagnostics()
        val json = diagnostics.toJson()
        assertEquals(FeedbackDiagnostics.SCHEMA_VERSION, json.getInt("diagnostics_schema_version"))
        val serialized = json.toString().lowercase()
        listOf("pin", "password", "clipboard", "message_content", "browsing_history", "token", "android_id")
            .forEach { assertFalse("Unexpected sensitive key: $it", serialized.contains("\"$it\"")) }
        assertEquals(diagnostics, FeedbackDiagnostics.fromJson(json))
    }

    @Test fun submissionPayload_hasIdempotencyAndNoStatus() {
        val payload = FeedbackSubmission(
            FeedbackCategory.BUG,
            "A report",
            null,
            null,
            FeedbackEntrySource.SETTINGS,
            sampleDiagnostics(),
            idempotencyKey = "47d112da-96ad-4d9e-87ca-ea17c61ff990"
        ).toJson(includeScreenshot = false)
        assertEquals("47d112da-96ad-4d9e-87ca-ea17c61ff990", payload.getString("idempotency_key"))
        assertFalse(payload.has("status"))
    }

    @Test fun shakeDetector_requiresDeliberateBurstAndLocksUntilRearmed() {
        var time = 1_000L
        var triggers = 0
        val detector = ShakeDetector(onShake = { triggers++ }, now = { time })
        detector.sample(0f, 0f, 9.8f)
        repeat(3) { time += 100; detector.sample(30f, 0f, 0f) }
        assertEquals(1, triggers)
        repeat(4) { time += 3_000; detector.sample(30f, 0f, 0f) }
        assertEquals(1, triggers)
        detector.rearm()
        repeat(3) { time += 100; detector.sample(30f, 0f, 0f) }
        assertEquals(2, triggers)
    }

    private fun sampleDiagnostics() = FeedbackDiagnostics(
        appVersion = "1.0",
        buildNumber = 1,
        androidVersion = "16",
        deviceManufacturer = "Google",
        deviceModel = "Pixel",
        currentScreen = "Settings",
        entrySource = FeedbackEntrySource.SETTINGS,
        activeRuleCount = 2,
        ruleTypeCounts = mapOf("EarnRewardTime" to 2),
        strictModeEnabled = true,
        usageAccessGranted = true,
        accessibilityServiceEnabled = true,
        notificationPermissionGranted = null,
        online = true,
        installationId = "c1e173e0-fdb9-4fe3-9948-fe9e6ab885e7",
        locale = "en-CA",
        sessionId = "session",
        processUptimeMillis = 123
    )
}
