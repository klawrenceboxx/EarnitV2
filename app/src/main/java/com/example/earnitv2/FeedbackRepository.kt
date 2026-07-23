package com.example.earnitv2

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.TimeUnit

data class FeedbackBackendConfig(val projectUrl: String, val anonKey: String) {
    val isConfigured get() = projectUrl.startsWith("https://") && anonKey.isNotBlank()
    companion object {
        fun fromBuild() = FeedbackBackendConfig(BuildConfig.FEEDBACK_SUPABASE_URL.trimEnd('/'), BuildConfig.FEEDBACK_SUPABASE_ANON_KEY)
    }
}

class FeedbackRemoteDataSource(private val config: FeedbackBackendConfig) {
    fun submit(submission: FeedbackSubmission): FeedbackSubmitResult {
        if (!config.isConfigured) return FeedbackSubmitResult.MissingConfiguration
        val connection = URL("${config.projectUrl}/functions/v1/submit-feedback").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer ${config.anonKey}")
            connection.setRequestProperty("apikey", config.anonKey)
            connection.setRequestProperty("Idempotency-Key", submission.idempotencyKey)
            connection.outputStream.use { it.write(submission.toJson(includeScreenshot = true).toString().toByteArray()) }
            val code = connection.responseCode
            val body = runCatching {
                (if (code in 200..299) connection.inputStream else connection.errorStream).bufferedReader().use { it.readText() }
            }.getOrDefault("{}")
            when {
                code in 200..299 -> {
                    val reference = JSONObject(body).optString("referenceId")
                    if (reference.matches(Regex("^FB-[A-Z0-9]{6,12}$"))) FeedbackSubmitResult.Success(reference)
                    else FeedbackSubmitResult.RecoverableFailure("We couldn't confirm that your feedback was received.")
                }
                code in 400..499 && code != 408 && code != 429 ->
                    FeedbackSubmitResult.PermanentFailure("Please review the report and try again.")
                else -> FeedbackSubmitResult.RecoverableFailure("We couldn't send your feedback yet.")
            }
        } catch (_: Exception) {
            FeedbackSubmitResult.RecoverableFailure("We couldn't send your feedback yet.")
        } finally {
            connection.disconnect()
        }
    }
}

class FeedbackRepository(private val context: Context) {
    private val queue = FeedbackQueueStore(context)
    private val remote = FeedbackRemoteDataSource(FeedbackBackendConfig.fromBuild())

    fun submit(submission: FeedbackSubmission, queueOnFailure: Boolean = true): FeedbackSubmitResult {
        if (!FeedbackBackendConfig.fromBuild().isConfigured) {
            if (BuildConfig.DEBUG) Log.d("EarnItFeedback", "Feedback backend is not configured; submission disabled.")
            return FeedbackSubmitResult.MissingConfiguration
        }
        if (!submission.diagnostics.online) return queue(submission)
        return when (val result = remote.submit(submission)) {
            is FeedbackSubmitResult.RecoverableFailure -> if (queueOnFailure) queue(submission) else result
            else -> result
        }
    }

    fun retry(queueId: String): FeedbackSubmitResult {
        val queued = queue.find(queueId) ?: return FeedbackSubmitResult.PermanentFailure("Queued report was not found.")
        return when (val result = remote.submit(queued.submission)) {
            is FeedbackSubmitResult.Success -> {
                queue.remove(queueId)
                queued.submission.screenshotPath?.let { File(it).delete() }
                result
            }
            is FeedbackSubmitResult.PermanentFailure -> {
                queue.markPermanent(queueId)
                result
            }
            else -> result
        }
    }

    fun pendingCount() = queue.pendingCount()
    fun clearDebugQueue() = queue.clear()

    private fun queue(submission: FeedbackSubmission): FeedbackSubmitResult {
        val item = QueuedFeedback(UUID.randomUUID().toString(), submission)
        queue.add(item)
        FeedbackWorkScheduler.schedule(context, item.queueId)
        return FeedbackSubmitResult.Queued(item.queueId)
    }
}

class FeedbackQueueStore(private val context: Context) {
    private val prefs get() = context.getSharedPreferences("feedback_queue", Context.MODE_PRIVATE)
    @Synchronized fun all(): List<QueuedFeedback> {
        val array = runCatching { JSONArray(prefs.getString("items", "[]")) }.getOrDefault(JSONArray())
        return (0 until array.length()).mapNotNull { runCatching { queuedFromJson(array.getJSONObject(it)) }.getOrNull() }
    }
    @Synchronized fun add(item: QueuedFeedback) = save(all().filterNot { it.submission.idempotencyKey == item.submission.idempotencyKey } + item)
    @Synchronized fun find(id: String) = all().firstOrNull { it.queueId == id }
    @Synchronized fun remove(id: String) = save(all().filterNot { it.queueId == id })
    @Synchronized fun markPermanent(id: String) = save(all().map { if (it.queueId == id) it.copy(state = QueuedFeedbackState.PERMANENT_FAILURE) else it })
    fun pendingCount() = all().count { it.state != QueuedFeedbackState.PERMANENT_FAILURE }
    @Synchronized fun clear() {
        all().forEach { it.submission.screenshotPath?.let { path -> File(path).delete() } }
        save(emptyList())
    }
    private fun save(items: List<QueuedFeedback>) = prefs.edit().putString("items", JSONArray(items.map(::queuedToJson)).toString()).commit()
}

object FeedbackWorkScheduler {
    fun schedule(context: Context, queueId: String) {
        val request = OneTimeWorkRequestBuilder<FeedbackUploadWorker>()
            .setInputData(androidx.work.workDataOf("queue_id" to queueId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("feedback-$queueId", ExistingWorkPolicy.KEEP, request)
    }
}

internal fun FeedbackSubmission.toJson(includeScreenshot: Boolean): JSONObject = JSONObject().apply {
    put("category", category.wireValue)
    put("message", message)
    contactEmail?.let { put("contact_email", it) }
    put("entry_source", entrySource.name)
    put("installation_id", diagnostics.installationId)
    put("diagnostics_schema_version", diagnostics.diagnosticsSchemaVersion)
    put("diagnostics", diagnostics.toJson())
    put("client_created_at", clientCreatedAt)
    put("idempotency_key", idempotencyKey)
    if (includeScreenshot) screenshotPath?.let { path ->
        put("screenshot", JSONObject()
            .put("mime_type", "image/jpeg")
            .put("base64", Base64.encodeToString(File(path).readBytes(), Base64.NO_WRAP)))
    }
}

private fun queuedToJson(item: QueuedFeedback) = JSONObject()
    .put("queue_id", item.queueId)
    .put("retry_count", item.retryCount)
    .put("state", item.state.name)
    .put("created_at", item.createdAt)
    .put("submission", item.submission.toJson(includeScreenshot = false).put("screenshot_path", item.submission.screenshotPath))

private fun queuedFromJson(json: JSONObject): QueuedFeedback {
    val value = json.getJSONObject("submission")
    val diagnostics = FeedbackDiagnostics.fromJson(value.getJSONObject("diagnostics"))
    return QueuedFeedback(
        queueId = json.getString("queue_id"),
        retryCount = json.optInt("retry_count"),
        state = QueuedFeedbackState.valueOf(json.optString("state", QueuedFeedbackState.PENDING.name)),
        createdAt = json.getLong("created_at"),
        submission = FeedbackSubmission(
            category = requireNotNull(FeedbackCategory.fromWire(value.getString("category"))),
            message = value.getString("message"),
            contactEmail = value.optString("contact_email").takeIf(String::isNotBlank),
            screenshotPath = value.optString("screenshot_path").takeIf(String::isNotBlank),
            entrySource = FeedbackEntrySource.valueOf(value.getString("entry_source")),
            diagnostics = diagnostics,
            idempotencyKey = value.getString("idempotency_key"),
            clientCreatedAt = value.getLong("client_created_at")
        )
    )
}
