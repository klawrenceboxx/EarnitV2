package com.kaleel.earnitv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSyncBackendTest {
    private val config = CloudSyncConfig("https://example.supabase.co", "anon-key")
    private val session = CloudSession("user-1", "me@example.com", "access", "refresh", 10_000L)
    private val requests = ArrayList<CloudHttpRequest>()

    private fun backend(vararg responses: CloudHttpResponse): SupabaseCloudSyncBackend {
        val queue = ArrayDeque(responses.toList())
        return SupabaseCloudSyncBackend(config) { request ->
            requests += request
            queue.removeFirst()
        }
    }

    @Test
    fun requestingACodeUsesTheAnonKeyAndCreatesTheUser() {
        val result = backend(CloudHttpResponse(200, "{}")).requestEmailCode(" me@example.com ")
        assertTrue(result is CloudResult.Ok)
        val request = requests.single()
        assertEquals("https://example.supabase.co/auth/v1/otp", request.url)
        assertEquals("anon-key", request.headers["apikey"])
        assertEquals(mapOf("email" to "me@example.com", "create_user" to true), SyncJson.decode(request.body!!))
    }

    @Test
    fun verifyingACodeProducesASession() {
        val body = """{"access_token":"a1","token_type":"bearer","expires_in":3600,"refresh_token":"r1","user":{"id":"u1","email":"me@example.com"}}"""
        val result = backend(CloudHttpResponse(200, body)).verifyEmailCode("me@example.com", " 123456 ", nowMillis = 1_000L)
        val created = (result as CloudResult.Ok).value
        assertEquals(CloudSession("u1", "me@example.com", "a1", "r1", 1_000L + 3_600_000L), created)
        assertEquals(
            mapOf("type" to "email", "email" to "me@example.com", "token" to "123456"),
            SyncJson.decode(requests.single().body!!)
        )
    }

    @Test
    fun aWrongCodeIsAReasonTheUserCanReadNotASignOut() {
        val result = backend(CloudHttpResponse(403, """{"code":403,"error_code":"otp_expired","msg":"Token has expired or is invalid"}"""))
            .verifyEmailCode("me@example.com", "000000", 0L)
        assertEquals(CloudResult.Rejected("Token has expired or is invalid"), result)
    }

    @Test
    fun aRevokedRefreshTokenMeansSignInAgain() {
        val result = backend(CloudHttpResponse(400, """{"error_code":"refresh_token_not_found","msg":"Invalid Refresh Token"}"""))
            .refresh(session, 0L)
        assertEquals(CloudResult.Unauthorized, result)
        assertEquals("https://example.supabase.co/auth/v1/token?grant_type=refresh_token", requests.single().url)
    }

    @Test
    fun networkFailuresAndServerErrorsAreRetryable() {
        val offline = SupabaseCloudSyncBackend(config) { throw java.io.IOException("no route") }
        assertTrue(offline.fetch(session, SyncCollections.RULES) is CloudResult.Retryable)
        assertTrue(backend(CloudHttpResponse(503, "")).fetch(session, SyncCollections.RULES) is CloudResult.Retryable)
        assertEquals(CloudResult.Unauthorized, backend(CloudHttpResponse(401, "{}")).fetch(session, SyncCollections.RULES))
    }

    @Test
    fun fetchedPayloadsAreCanonicalRegardlessOfServerKeyOrder() {
        val rows = """[{"collection":"rules","record_id":"r1","payload":{"v":1,"type":"ScheduledBlock","ratio":1},"updated_at_ms":77,"deleted":false,"device_id":"d","platform":"android"}]"""
        val records = (backend(CloudHttpResponse(200, rows)).fetch(session, SyncCollections.RULES) as CloudResult.Ok).value
        assertEquals("""{"ratio":1,"type":"ScheduledBlock","v":1}""", records.single().payloadJson)
        assertEquals(77L, records.single().updatedAtMillis)
        val request = requests.single()
        assertTrue(request.url.startsWith("https://example.supabase.co/rest/v1/sync_records?select="))
        assertTrue(request.url.endsWith("&collection=eq.rules"))
        assertEquals("Bearer access", request.headers["Authorization"])
    }

    @Test
    fun pushUpsertsLiveRecordsAndSendsTombstonesWithoutAPayload() {
        val live = SyncRecord(SyncCollections.RULES, "r1", """{"v":1}""", 5L, false, "d", "android")
        val gone = SyncRecord(SyncCollections.RULES, "r2", "{}", 6L, true, "d", "android")
        val result = backend(CloudHttpResponse(201, ""), CloudHttpResponse(201, "")).push(session, listOf(live, gone))
        assertTrue(result is CloudResult.Ok)
        assertEquals(2, requests.size)
        requests.forEach { request ->
            assertEquals("https://example.supabase.co/rest/v1/sync_records?on_conflict=user_id,collection,record_id", request.url)
            assertEquals("resolution=merge-duplicates,return=minimal", request.headers["Prefer"])
        }
        val liveRow = (SyncJson.decode(requests[0].body!!) as List<*>).single() as Map<*, *>
        assertEquals(mapOf("v" to 1L), liveRow["payload"])
        assertFalse(liveRow.containsKey("user_id"))
        val tombstoneRow = (SyncJson.decode(requests[1].body!!) as List<*>).single() as Map<*, *>
        assertEquals(true, tombstoneRow["deleted"])
        assertFalse(tombstoneRow.containsKey("payload"))
    }

    @Test
    fun anUnconfiguredBuildNeverTouchesTheNetwork() {
        val unconfigured = SupabaseCloudSyncBackend(CloudSyncConfig("", "")) { error("must not be called") }
        assertFalse(unconfigured.isConfigured)
        assertTrue(unconfigured.requestEmailCode("me@example.com") is CloudResult.Rejected)
    }
}
