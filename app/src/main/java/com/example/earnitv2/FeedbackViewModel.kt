package com.example.earnitv2

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.posthog.PostHog
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

enum class FeedbackPhase { EDITING, VALIDATING, PREPARING_ATTACHMENT, SENDING, QUEUED_OFFLINE, SUCCESS, RECOVERABLE_FAILURE, PERMANENT_FAILURE }

data class FeedbackUiState(
    val category: FeedbackCategory? = null,
    val message: String = "",
    val contactEmail: String = "",
    val screenshotPath: String? = null,
    val screenshotError: String? = null,
    val categoryError: String? = null,
    val messageError: String? = null,
    val emailError: String? = null,
    val phase: FeedbackPhase = FeedbackPhase.EDITING,
    val userMessage: String? = null,
    val referenceId: String? = null,
    val diagnostics: FeedbackDiagnostics? = null,
    val entrySource: FeedbackEntrySource = FeedbackEntrySource.SETTINGS,
    val currentRoute: String = "Settings",
    val crash: CrashDiagnostics? = null
) {
    val hasDraft get() = category != null || message.isNotBlank() || contactEmail.isNotBlank() || screenshotPath != null
    val busy get() = phase in setOf(FeedbackPhase.VALIDATING, FeedbackPhase.PREPARING_ATTACHMENT, FeedbackPhase.SENDING)
    val canSubmit get() = feedbackCanSubmit(category, message, contactEmail)
}

class FeedbackViewModel(application: Application) : AndroidViewModel(application) {
    private val executor = Executors.newSingleThreadExecutor()
    private val repository = FeedbackRepository(application)
    private val prefs = application.getSharedPreferences("feedback_draft", 0)
    var state by mutableStateOf(restoreDraft())
        private set

    fun begin(source: FeedbackEntrySource, route: String, crash: CrashDiagnostics? = null) {
        state = state.copy(
            entrySource = source,
            currentRoute = route,
            crash = crash,
            category = if (source == FeedbackEntrySource.CRASH_FOLLOW_UP) FeedbackCategory.BUG else state.category,
            diagnostics = null,
            phase = FeedbackPhase.EDITING,
            userMessage = null
        )
        persist()
    }
    fun selectCategory(value: FeedbackCategory) { state = state.copy(category = value, categoryError = null); persist() }
    fun setMessage(value: String) { if (value.length <= FeedbackValidation.MAX_MESSAGE_LENGTH) { state = state.copy(message = value, messageError = null); persist() } }
    fun setEmail(value: String) { state = state.copy(contactEmail = value, emailError = null); persist() }
    fun validateEmail() { state = state.copy(emailError = FeedbackValidation.emailError(state.contactEmail)) }
    fun removeScreenshot() { state.screenshotPath?.let { File(it).delete() }; state = state.copy(screenshotPath = null, screenshotError = null); persist() }

    fun processScreenshot(uri: Uri) {
        if (state.busy) return
        state = state.copy(phase = FeedbackPhase.PREPARING_ATTACHMENT, screenshotError = null)
        executor.execute {
            val result = FeedbackImageProcessor(getApplication()).process(uri)
            state.screenshotPath?.let { File(it).delete() }
            state = result.fold(
                { state.copy(screenshotPath = it.absolutePath, phase = FeedbackPhase.EDITING) },
                { state.copy(screenshotError = it.message ?: "We couldn't prepare that image.", phase = FeedbackPhase.EDITING) }
            )
            persist()
        }
    }

    fun loadDiagnostics(strictModeEnabled: Boolean) {
        state = state.copy(diagnostics = FeedbackDiagnosticsCollector(getApplication()).collect(state.currentRoute, state.entrySource, strictModeEnabled, state.crash))
    }

    fun submit(strictModeEnabled: Boolean) {
        if (state.busy) return
        val categoryError = if (state.category == null) "Choose Bug, Suggestion, or Other." else null
        val messageError = FeedbackValidation.messageError(state.message)
        val emailError = FeedbackValidation.emailError(state.contactEmail)
        state = state.copy(phase = FeedbackPhase.VALIDATING, categoryError = categoryError, messageError = messageError, emailError = emailError)
        if (categoryError != null || messageError != null || emailError != null) {
            state = state.copy(phase = FeedbackPhase.EDITING)
            return
        }
        val diagnostics = FeedbackDiagnosticsCollector(getApplication()).collect(state.currentRoute, state.entrySource, strictModeEnabled, state.crash)
        state = state.copy(phase = FeedbackPhase.SENDING, diagnostics = diagnostics, userMessage = null)
        val submission = FeedbackSubmission(
            category = requireNotNull(state.category),
            message = state.message.trim(),
            contactEmail = state.contactEmail.trim().takeIf(String::isNotEmpty),
            screenshotPath = state.screenshotPath,
            entrySource = state.entrySource,
            diagnostics = diagnostics
        )
        executor.execute {
            state = when (val result = repository.submit(submission)) {
                is FeedbackSubmitResult.Success -> {
                    PostHog.capture(
                        "feedback_submitted",
                        properties = mapOf(
                            "category" to state.category?.name.orEmpty(),
                            "entry_source" to state.entrySource.name,
                            "has_screenshot" to (state.screenshotPath != null),
                            "is_crash_follow_up" to (state.crash != null)
                        )
                    )
                    clearDraft(deleteAttachment = true)
                    FeedbackUiState(phase = FeedbackPhase.SUCCESS, referenceId = result.referenceId, entrySource = state.entrySource)
                }
                is FeedbackSubmitResult.Queued -> {
                    clearDraft(deleteAttachment = false)
                    FeedbackUiState(phase = FeedbackPhase.QUEUED_OFFLINE, entrySource = state.entrySource)
                }
                is FeedbackSubmitResult.MissingConfiguration -> state.copy(
                    phase = FeedbackPhase.RECOVERABLE_FAILURE,
                    userMessage = "Feedback sending isn't available in this development build yet. Your report is still here."
                )
                is FeedbackSubmitResult.RecoverableFailure -> state.copy(phase = FeedbackPhase.RECOVERABLE_FAILURE, userMessage = result.userMessage)
                is FeedbackSubmitResult.PermanentFailure -> state.copy(phase = FeedbackPhase.PERMANENT_FAILURE, userMessage = result.userMessage)
            }
        }
    }

    fun resumeEditing() { state = state.copy(phase = FeedbackPhase.EDITING, userMessage = null) }
    fun discard() { clearDraft(true); state = FeedbackUiState(entrySource = state.entrySource) }
    fun pendingCount() = repository.pendingCount()
    fun clearDebugQueue() = repository.clearDebugQueue()

    private fun persist() {
        val json = JSONObject()
            .put("category", state.category?.name)
            .put("message", state.message)
            .put("email", state.contactEmail)
            .put("screenshot_path", state.screenshotPath)
        prefs.edit().putString("draft", json.toString()).apply()
    }
    private fun restoreDraft(): FeedbackUiState = runCatching {
        val json = JSONObject(prefs.getString("draft", "{}")!!)
        FeedbackUiState(
            category = json.optString("category").takeIf(String::isNotBlank)?.let(FeedbackCategory::valueOf),
            message = json.optString("message"),
            contactEmail = json.optString("email"),
            screenshotPath = json.optString("screenshot_path").takeIf(String::isNotBlank)
        )
    }.getOrDefault(FeedbackUiState())
    private fun clearDraft(deleteAttachment: Boolean) {
        if (deleteAttachment) state.screenshotPath?.let { File(it).delete() }
        prefs.edit().remove("draft").apply()
    }
    override fun onCleared() { executor.shutdown(); super.onCleared() }
}
