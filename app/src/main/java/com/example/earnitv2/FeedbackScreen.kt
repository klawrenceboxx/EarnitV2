package com.example.earnitv2

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

@Composable
fun FeedbackScreen(
    viewModel: FeedbackViewModel,
    strictModeEnabled: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = viewModel.state
    var showDiscard by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::processScreenshot)
    }
    fun requestClose() {
        if (state.hasDraft && state.phase !in setOf(FeedbackPhase.SUCCESS, FeedbackPhase.QUEUED_OFFLINE)) showDiscard = true else onClose()
    }
    BackHandler(onBack = ::requestClose)

    when (state.phase) {
        FeedbackPhase.SUCCESS -> FeedbackOutcome(
            title = "Feedback sent",
            body = "Thanks for helping improve EarnIt.",
            referenceId = state.referenceId,
            onDone = onClose
        )
        FeedbackPhase.QUEUED_OFFLINE -> FeedbackOutcome(
            title = "Feedback saved",
            body = "It will send automatically when your connection returns.",
            onDone = onClose
        )
        else -> Column(
            modifier = modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FeedbackHeader(onBack = ::requestClose)
            Text("Found a problem or have an idea? Let us know.", color = MaterialTheme.colorScheme.onSurfaceVariant)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Feedback type", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Required", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeedbackCategory.entries.forEach { category ->
                    val selected = state.category == category
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.selectCategory(category) },
                        label = { Text(category.label) },
                        leadingIcon = if (selected) ({ Text("✓", fontWeight = FontWeight.Bold) }) else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = MaterialTheme.colorScheme.outlineVariant,
                            selectedBorderColor = MaterialTheme.colorScheme.primary,
                            borderWidth = 1.dp,
                            selectedBorderWidth = 2.dp
                        ),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    )
                }
            }
            state.categoryError?.let { ErrorText(it) }
            if (state.category == null && state.categoryError == null) {
                Text(
                    "Choose Bug, Suggestion, or Other.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text("Tell us more", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = state.message,
                onValueChange = viewModel::setMessage,
                modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                label = { Text(if (state.entrySource == FeedbackEntrySource.CRASH_FOLLOW_UP) "What were you doing?" else "Feedback") },
                placeholder = { Text(if (state.entrySource == FeedbackEntrySource.CRASH_FOLLOW_UP) "Tell us what you were doing before EarnIt closed." else "Tell us what happened or what you would like to see…") },
                supportingText = {
                    val remaining = FeedbackValidation.MAX_MESSAGE_LENGTH - state.message.length
                    if (remaining <= 400) Text("$remaining characters remaining")
                },
                isError = state.messageError != null,
                minLines = 5,
                maxLines = 10
            )
            state.messageError?.let { ErrorText(it) }
            if (state.message.isBlank() && state.messageError == null) {
                Text(
                    "Required — tell us what happened or what you’d like to suggest.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = state.contactEmail,
                onValueChange = viewModel::setEmail,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Contact email (optional)") },
                supportingText = { Text("Add your email if you're open to a follow-up.") },
                isError = state.emailError != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.validateEmail() })
            )
            state.emailError?.let { ErrorText(it) }

            state.screenshotPath?.let { path ->
                ScreenshotPreview(path = path, onRemove = viewModel::removeScreenshot)
            } ?: OutlinedButton(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = state.canSubmit && !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) { Text(if (state.phase == FeedbackPhase.PREPARING_ATTACHMENT) "Preparing screenshot…" else "Attach screenshot (optional)") }
            state.screenshotError?.let { ErrorText(it) }

            DiagnosticsCard(
                crashIncluded = state.crash != null,
                onView = {
                    viewModel.loadDiagnostics(strictModeEnabled)
                    showDiagnostics = true
                }
            )

            Text(
                "Your report includes basic app, device, permission, and Rule-status details. It does not include your PIN, messages, browsing history, or text from other apps.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            state.userMessage?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive }
                ) { Text(it, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer) }
            }
            if (state.phase in setOf(FeedbackPhase.RECOVERABLE_FAILURE, FeedbackPhase.PERMANENT_FAILURE)) {
                OutlinedButton(onClick = viewModel::resumeEditing, modifier = Modifier.fillMaxWidth()) { Text("Review report") }
            }
            Button(
                onClick = { viewModel.submit(strictModeEnabled) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) { Text(if (state.phase == FeedbackPhase.SENDING) "Sending feedback…" else "Submit Feedback") }
            TextButton(onClick = ::requestClose, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showDiscard) AlertDialog(
        onDismissRequest = { showDiscard = false },
        title = { Text("Discard feedback?") },
        text = { Text("Your message and attachment will be removed.") },
        confirmButton = { TextButton(onClick = { viewModel.discard(); showDiscard = false; onClose() }) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { showDiscard = false }) { Text("Keep editing") } }
    )
    if (showDiagnostics) state.diagnostics?.let { diagnostics ->
        DiagnosticsDialog(diagnostics, onDismiss = { showDiagnostics = false })
    }
}

@Composable
fun CrashFollowUpPrompt(onReview: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("EarnIt closed unexpectedly") },
        text = { Text("Send an anonymous crash report to help us investigate? Nothing is sent unless you review and submit it.") },
        confirmButton = { TextButton(onClick = onReview) { Text("Review and Send") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not Now") } }
    )
}

@Composable
private fun FeedbackHeader(onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("‹ Back") }
        Text("Send Feedback", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ScreenshotPreview(path: String, onRemove: () -> Unit) {
    val bitmap = remember(path) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (bitmap != null) Image(bitmap, "Selected feedback screenshot", Modifier.size(80.dp))
            Text("Screenshot attached", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onRemove, modifier = Modifier.semantics { contentDescription = "Remove screenshot" }) { Text("Remove") }
        }
    }
}

@Composable
private fun DiagnosticsCard(crashIncluded: Boolean, onView: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onView)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("◇", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(if (crashIncluded) "Crash diagnostics included" else "Diagnostics included", fontWeight = FontWeight.SemiBold)
                Text("App and device details that can help us investigate.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("View details", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun DiagnosticsDialog(value: FeedbackDiagnostics, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What's included") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailSection("App", "Version ${value.appVersion}\nBuild ${value.buildNumber}\nCurrent screen: ${value.currentScreen}")
                DetailSection("Device", "${value.deviceManufacturer} ${value.deviceModel}\nAndroid ${value.androidVersion}\nLocale ${value.locale}")
                DetailSection("EarnIt status", "${value.activeRuleCount} active Rules\nStrict Mode ${enabledLabel(value.strictModeEnabled)}\nUsage access ${grantLabel(value.usageAccessGranted)}\nApp blocking ${enabledLabel(value.accessibilityServiceEnabled)}")
                if (value.crash != null) DetailSection("Previous close", "Exception type and a sanitized EarnIt stack trace")
                Text("EarnIt does not include your PIN, messages, browsing history, or text from other apps.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable private fun DetailSection(title: String, body: String) = Column {
    Text(title, fontWeight = FontWeight.SemiBold)
    Text(body, style = MaterialTheme.typography.bodyMedium)
}
private fun enabledLabel(value: Boolean) = if (value) "enabled" else "off"
private fun grantLabel(value: Boolean) = if (value) "granted" else "not granted"

@Composable
private fun FeedbackOutcome(title: String, body: String, referenceId: String? = null, onDone: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier.fillMaxSize().padding(24.dp).semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("✓", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.tertiary)
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        referenceId?.let {
            Text("Reference ID", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(it, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = { clipboard.setText(AnnotatedString(it)) }) { Text("Copy reference ID") }
        }
        Text(body, Modifier.padding(vertical = 16.dp))
        Button(onClick = onDone, Modifier.fillMaxWidth()) { Text("Done") }
    }
}

@Composable private fun ErrorText(value: String) {
    Text(value, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
}
