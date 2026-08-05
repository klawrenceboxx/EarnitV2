package com.kaleel.earnitv2

import org.json.JSONObject
import java.util.UUID

enum class FeedbackCategory(val wireValue: String, val label: String) {
    BUG("BUG", "Bug"),
    SUGGESTION("SUGGESTION", "Suggestion"),
    OTHER("OTHER", "Other");

    companion object {
        fun fromWire(value: String) = entries.firstOrNull { it.wireValue == value }
    }
}

enum class FeedbackEntrySource { SETTINGS, SHAKE, CRASH_FOLLOW_UP }

enum class QueuedFeedbackState { PENDING, RETRYING, PERMANENT_FAILURE }

data class FeedbackDiagnostics(
    val diagnosticsSchemaVersion: Int = SCHEMA_VERSION,
    val appVersion: String,
    val buildNumber: Long,
    val androidVersion: String,
    val deviceManufacturer: String,
    val deviceModel: String,
    val currentScreen: String,
    val entrySource: FeedbackEntrySource,
    val activeRuleCount: Int,
    val ruleTypeCounts: Map<String, Int>,
    val strictModeEnabled: Boolean,
    val usageAccessGranted: Boolean,
    val accessibilityServiceEnabled: Boolean,
    val notificationPermissionGranted: Boolean?,
    val online: Boolean,
    val installationId: String,
    val locale: String,
    val sessionId: String,
    val processUptimeMillis: Long,
    val crash: CrashDiagnostics? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("diagnostics_schema_version", diagnosticsSchemaVersion)
        put("app_version", appVersion)
        put("build_number", buildNumber)
        put("android_version", androidVersion)
        put("device_manufacturer", deviceManufacturer)
        put("device_model", deviceModel)
        put("current_screen", currentScreen)
        put("entry_source", entrySource.name)
        put("active_rule_count", activeRuleCount)
        put("rule_type_counts", JSONObject(ruleTypeCounts))
        put("strict_mode_enabled", strictModeEnabled)
        put("usage_access_granted", usageAccessGranted)
        put("accessibility_service_enabled", accessibilityServiceEnabled)
        notificationPermissionGranted?.let { put("notification_permission_granted", it) }
        put("online", online)
        put("installation_id", installationId)
        put("locale", locale)
        put("session_id", sessionId)
        put("process_uptime_millis", processUptimeMillis)
        crash?.let { put("crash", it.toJson()) }
    }

    companion object {
        const val SCHEMA_VERSION = 1
        fun fromJson(json: JSONObject) = FeedbackDiagnostics(
            diagnosticsSchemaVersion = json.getInt("diagnostics_schema_version"),
            appVersion = json.getString("app_version"),
            buildNumber = json.getLong("build_number"),
            androidVersion = json.getString("android_version"),
            deviceManufacturer = json.getString("device_manufacturer"),
            deviceModel = json.getString("device_model"),
            currentScreen = json.getString("current_screen"),
            entrySource = FeedbackEntrySource.valueOf(json.getString("entry_source")),
            activeRuleCount = json.getInt("active_rule_count"),
            ruleTypeCounts = json.getJSONObject("rule_type_counts").let { counts ->
                counts.keys().asSequence().associateWith { counts.getInt(it) }
            },
            strictModeEnabled = json.getBoolean("strict_mode_enabled"),
            usageAccessGranted = json.getBoolean("usage_access_granted"),
            accessibilityServiceEnabled = json.getBoolean("accessibility_service_enabled"),
            notificationPermissionGranted = if (json.has("notification_permission_granted")) json.getBoolean("notification_permission_granted") else null,
            online = json.getBoolean("online"),
            installationId = json.getString("installation_id"),
            locale = json.getString("locale"),
            sessionId = json.getString("session_id"),
            processUptimeMillis = json.getLong("process_uptime_millis"),
            crash = json.optJSONObject("crash")?.let(CrashDiagnostics::fromJson)
        )
    }
}

data class CrashDiagnostics(
    val exceptionClass: String,
    val sanitizedStackTrace: String,
    val lastKnownRoute: String,
    val sessionId: String
) {
    fun toJson() = JSONObject()
        .put("exception_class", exceptionClass)
        .put("sanitized_stack_trace", sanitizedStackTrace)
        .put("last_known_route", lastKnownRoute)
        .put("session_id", sessionId)

    companion object {
        fun fromJson(json: JSONObject) = CrashDiagnostics(
            json.getString("exception_class"),
            json.getString("sanitized_stack_trace"),
            json.getString("last_known_route"),
            json.getString("session_id")
        )
    }
}

data class FeedbackSubmission(
    val category: FeedbackCategory,
    val message: String,
    val contactEmail: String?,
    val screenshotPath: String?,
    val entrySource: FeedbackEntrySource,
    val diagnostics: FeedbackDiagnostics,
    val idempotencyKey: String = UUID.randomUUID().toString(),
    val clientCreatedAt: Long = System.currentTimeMillis()
)

data class QueuedFeedback(
    val queueId: String,
    val submission: FeedbackSubmission,
    val retryCount: Int = 0,
    val state: QueuedFeedbackState = QueuedFeedbackState.PENDING,
    val createdAt: Long = System.currentTimeMillis()
)

sealed interface FeedbackSubmitResult {
    data class Success(val referenceId: String) : FeedbackSubmitResult
    data class Queued(val queueId: String) : FeedbackSubmitResult
    data class RecoverableFailure(val userMessage: String) : FeedbackSubmitResult
    data class PermanentFailure(val userMessage: String) : FeedbackSubmitResult
    data object MissingConfiguration : FeedbackSubmitResult
}

object FeedbackValidation {
    const val MAX_MESSAGE_LENGTH = 2_000
    private val emailPattern = Regex("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,63}$", RegexOption.IGNORE_CASE)
    fun messageError(message: String): String? = when {
        message.isBlank() -> "Tell us what happened or what you’d like to suggest."
        message.length > MAX_MESSAGE_LENGTH -> "Keep feedback under $MAX_MESSAGE_LENGTH characters."
        else -> null
    }
    fun emailError(email: String): String? =
        email.trim().takeIf { it.isNotEmpty() && !emailPattern.matches(it) }?.let { "Enter a valid email address or leave it blank." }
}

internal fun feedbackCanSubmit(
    category: FeedbackCategory?,
    message: String,
    email: String
): Boolean = category != null &&
    FeedbackValidation.messageError(message) == null &&
    FeedbackValidation.emailError(email) == null
