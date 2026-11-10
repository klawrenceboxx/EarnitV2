package com.kaleel.earnitv2

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal data class CloudHttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String?
)

internal data class CloudHttpResponse(val code: Int, val body: String)

/** Performs one HTTP call. Throws on network failure. Swapped for a fake in tests. */
internal typealias CloudHttpTransport = (CloudHttpRequest) -> CloudHttpResponse

internal data class CloudSyncConfig(val projectUrl: String, val anonKey: String) {
    val isConfigured: Boolean get() = projectUrl.startsWith("https://") && anonKey.isNotBlank()

    companion object {
        /** Sync lives in the same Supabase project as feedback, so it reuses those build values. */
        fun fromBuild() = CloudSyncConfig(
            projectUrl = BuildConfig.FEEDBACK_SUPABASE_URL.trimEnd('/'),
            anonKey = BuildConfig.FEEDBACK_SUPABASE_ANON_KEY
        )
    }
}

/**
 * Supabase implementation: email one-time-code auth (GoTrue) and the `sync_records` table
 * (PostgREST). Row-level security scopes every row to the signed-in user; see
 * supabase/migrations/202611100001_cloud_sync.sql.
 */
internal class SupabaseCloudSyncBackend(
    private val config: CloudSyncConfig = CloudSyncConfig.fromBuild(),
    private val transport: CloudHttpTransport = ::urlConnectionTransport
) : CloudSyncBackend {
    override val isConfigured: Boolean get() = config.isConfigured

    override fun requestEmailCode(email: String): CloudResult<Unit> {
        val body = SyncJson.encode(linkedMapOf("email" to email.trim(), "create_user" to true))
        return call(post("/auth/v1/otp", body, bearer = null)) { }
    }

    override fun verifyEmailCode(email: String, code: String, nowMillis: Long): CloudResult<CloudSession> {
        val body = SyncJson.encode(
            linkedMapOf("type" to "email", "email" to email.trim(), "token" to code.trim())
        )
        return call(post("/auth/v1/verify", body, bearer = null), unauthorizedIsRejection = true) { response ->
            parseSession(response, fallbackEmail = email.trim(), nowMillis = nowMillis)
        }
    }

    override fun refresh(session: CloudSession, nowMillis: Long): CloudResult<CloudSession> {
        val body = SyncJson.encode(linkedMapOf("refresh_token" to session.refreshToken))
        val request = post("/auth/v1/token?grant_type=refresh_token", body, bearer = null)
        return when (val result = call(request) { parseSession(it, session.email, nowMillis) }) {
            // GoTrue answers 400 for a revoked or already-used refresh token.
            is CloudResult.Rejected -> CloudResult.Unauthorized
            else -> result
        }
    }

    override fun signOut(session: CloudSession) {
        runCatching { transport(post("/auth/v1/logout", "{}", bearer = session.accessToken)) }
    }

    override fun fetch(session: CloudSession, collection: String): CloudResult<List<SyncRecord>> {
        val query = "select=collection,record_id,payload,updated_at_ms,deleted,device_id,platform" +
            "&collection=eq.${URLEncoder.encode(collection, "UTF-8")}"
        val request = CloudHttpRequest(
            method = "GET",
            url = "${config.projectUrl}/rest/v1/sync_records?$query",
            headers = headers(session.accessToken),
            body = null
        )
        return call(request) { response ->
            val rows = SyncJson.decode(response) as? List<*> ?: throw IllegalArgumentException("Expected a list")
            rows.mapNotNull { row ->
                val fields = row as? Map<*, *> ?: return@mapNotNull null
                val recordId = fields["record_id"] as? String ?: return@mapNotNull null
                SyncRecord(
                    collection = fields["collection"] as? String ?: collection,
                    recordId = recordId,
                    payloadJson = SyncJson.encode(SyncJson.canonicalize(fields["payload"] ?: emptyMap<String, Any?>())),
                    updatedAtMillis = (fields["updated_at_ms"] as? Number)?.toLong() ?: 0L,
                    deleted = fields["deleted"] == true,
                    deviceId = fields["device_id"] as? String ?: "",
                    platform = fields["platform"] as? String ?: ""
                )
            }
        }
    }

    override fun push(session: CloudSession, records: List<SyncRecord>): CloudResult<Unit> {
        // Tombstones are sent without a payload column so the server keeps the last payload of a
        // deleted record. Every object in one bulk upsert must carry the same keys, hence two calls.
        val (tombstones, upserts) = records.partition { it.deleted }
        listOf(upserts to true, tombstones to false).forEach { (batch, includePayload) ->
            if (batch.isEmpty()) return@forEach
            val body = SyncJson.encode(batch.map { record ->
                val row = linkedMapOf<String, Any?>(
                    "collection" to record.collection,
                    "record_id" to record.recordId,
                    "updated_at_ms" to record.updatedAtMillis,
                    "deleted" to record.deleted,
                    "device_id" to record.deviceId,
                    "platform" to record.platform
                )
                if (includePayload) row["payload"] = SyncJson.decode(record.payloadJson)
                row
            })
            val request = CloudHttpRequest(
                method = "POST",
                url = "${config.projectUrl}/rest/v1/sync_records?on_conflict=user_id,collection,record_id",
                headers = headers(session.accessToken) + mapOf(
                    "Content-Type" to "application/json",
                    "Prefer" to "resolution=merge-duplicates,return=minimal"
                ),
                body = body
            )
            val result = call(request) { }
            if (result !is CloudResult.Ok) return result
        }
        return CloudResult.Ok(Unit)
    }

    private fun post(path: String, body: String, bearer: String?) = CloudHttpRequest(
        method = "POST",
        url = "${config.projectUrl}$path",
        headers = headers(bearer) + mapOf("Content-Type" to "application/json"),
        body = body
    )

    private fun headers(bearer: String?): Map<String, String> = mapOf(
        "apikey" to config.anonKey,
        "Authorization" to "Bearer ${bearer ?: config.anonKey}"
    )

    private fun <T> call(
        request: CloudHttpRequest,
        unauthorizedIsRejection: Boolean = false,
        parse: (String) -> T
    ): CloudResult<T> {
        if (!config.isConfigured) return CloudResult.Rejected("Cloud sync is not set up in this build.")
        val response = try {
            transport(request)
        } catch (_: Exception) {
            return CloudResult.Retryable("Couldn't reach the server.")
        }
        return when {
            response.code in 200..299 -> try {
                CloudResult.Ok(parse(response.body))
            } catch (_: Exception) {
                CloudResult.Retryable("The server sent an unexpected response.")
            }
            (response.code == 401 || response.code == 403) && !unauthorizedIsRejection -> CloudResult.Unauthorized
            response.code == 408 || response.code == 429 || response.code >= 500 ->
                CloudResult.Retryable("The server is busy. Try again shortly.")
            else -> CloudResult.Rejected(errorMessage(response.body))
        }
    }

    private fun parseSession(body: String, fallbackEmail: String, nowMillis: Long): CloudSession {
        val fields = SyncJson.decode(body) as? Map<*, *> ?: throw IllegalArgumentException("Expected an object")
        val user = fields["user"] as? Map<*, *>
        val accessToken = fields["access_token"] as? String ?: throw IllegalArgumentException("No access token")
        val refreshToken = fields["refresh_token"] as? String ?: throw IllegalArgumentException("No refresh token")
        val userId = user?.get("id") as? String ?: throw IllegalArgumentException("No user id")
        val expiresInSeconds = (fields["expires_in"] as? Number)?.toLong() ?: 3_600L
        return CloudSession(
            userId = userId,
            email = (user["email"] as? String)?.takeIf { it.isNotBlank() } ?: fallbackEmail,
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtMillis = nowMillis + expiresInSeconds * 1_000L
        )
    }

    private fun errorMessage(body: String): String {
        val fields = runCatching { SyncJson.decode(body) as? Map<*, *> }.getOrNull()
        val message = listOf("msg", "error_description", "message", "error")
            .firstNotNullOfOrNull { key -> (fields?.get(key) as? String)?.takeIf { it.isNotBlank() } }
        return message ?: "The request was not accepted."
    }
}

internal fun urlConnectionTransport(request: CloudHttpRequest): CloudHttpResponse {
    val connection = URL(request.url).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = request.method
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        if (request.body != null) {
            connection.doOutput = true
            connection.outputStream.use { it.write(request.body.toByteArray(Charsets.UTF_8)) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        return CloudHttpResponse(code, body)
    } finally {
        connection.disconnect()
    }
}
