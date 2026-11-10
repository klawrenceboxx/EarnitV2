package com.kaleel.earnitv2

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Sign in with an emailed code, see sync status, sync now, sign out. */
@Composable
internal fun CloudSyncScreen(
    onBack: () -> Unit,
    onRulesChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val store = remember { CloudSyncStore(context) }
    val backend = remember { CloudSync.backend() }
    val scope = rememberCoroutineScope()

    var signedInEmail by remember { mutableStateOf(store.signedInEmail()) }
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf(sessionExpiredMessage(store)) }
    var lastSyncAtMillis by remember { mutableStateOf(store.lastSyncAtMillis()) }

    fun runSync() {
        busy = true
        scope.launch {
            val report = withContext(Dispatchers.IO) { CloudSync.engine(context).sync() }
            busy = false
            lastSyncAtMillis = store.lastSyncAtMillis()
            signedInEmail = store.signedInEmail()
            message = cloudSyncReportMessage(report)
            if (report.pulled > 0) onRulesChanged()
        }
    }

    BackHandler(onBack = onBack)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "Cloud Sync", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onBack) { Text(text = "Done") }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!backend.isConfigured) {
                    Text("Cloud Sync isn't available in this build.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "This build has no sync server configured.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (signedInEmail != null) {
                    Text("Signed in", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(signedInEmail.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (lastSyncAtMillis > 0L) {
                            "Last synced " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(lastSyncAtMillis))
                        } else {
                            "Not synced yet"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = { runSync() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(if (busy) "Syncing…" else "Sync now")
                    }
                    OutlinedButton(
                        onClick = {
                            val session = store.session()
                            store.clearSession()
                            CloudSyncWorker.cancel(context)
                            signedInEmail = null
                            codeSent = false
                            code = ""
                            message = "Signed out. Your Rules stay on this phone."
                            if (session != null) {
                                scope.launch { withContext(Dispatchers.IO) { backend.signOut(session) } }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Sign out")
                    }
                } else {
                    Text("Back up and sync your Rules", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Sign in with your email to keep your Rules backed up and matched across your Android devices. " +
                            "Only your Rules are uploaded. Usage history and Reward Time stay on this phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.trim() },
                        label = { Text("Email") },
                        singleLine = true,
                        enabled = !busy && !codeSent,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!codeSent) {
                        Button(
                            onClick = {
                                busy = true
                                message = null
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) { backend.requestEmailCode(email) }
                                    busy = false
                                    when (result) {
                                        is CloudResult.Ok -> {
                                            codeSent = true
                                            message = "We emailed a 6-digit code to $email."
                                        }
                                        else -> message = cloudResultMessage(result)
                                    }
                                }
                            },
                            enabled = !busy && isPlausibleEmail(email),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (busy) "Sending…" else "Email me a code")
                        }
                    } else {
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it.filter(Char::isDigit).take(8) },
                            label = { Text("Code from email") },
                            singleLine = true,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                busy = true
                                message = null
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        backend.verifyEmailCode(email, code, System.currentTimeMillis())
                                    }
                                    when (result) {
                                        is CloudResult.Ok -> {
                                            store.saveSession(result.value)
                                            CloudSyncWorker.schedule(context)
                                            signedInEmail = result.value.email
                                            code = ""
                                            codeSent = false
                                            runSync()
                                        }
                                        else -> {
                                            busy = false
                                            message = cloudResultMessage(result)
                                        }
                                    }
                                }
                            },
                            enabled = !busy && code.length >= 6,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (busy) "Checking…" else "Sign in")
                        }
                        TextButton(onClick = { codeSent = false; code = ""; message = null }, enabled = !busy) {
                            Text("Use a different email")
                        }
                    }
                }
                message?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

internal fun isPlausibleEmail(value: String): Boolean {
    val at = value.indexOf('@')
    return at > 0 && value.indexOf('.', at) > at + 1 && !value.endsWith(".") && value.none(Char::isWhitespace)
}

private fun sessionExpiredMessage(store: CloudSyncStore): String? =
    if (store.session() == null && store.lastOutcome() == CloudSyncOutcome.SessionExpired) {
        "Your sign-in expired. Sign in again to keep syncing."
    } else {
        null
    }

internal fun cloudSyncReportMessage(report: CloudSyncReport): String = when (report.outcome) {
    CloudSyncOutcome.Synced -> buildString {
        append(if (report.pulled == 0 && report.pushed == 0) "Everything is up to date." else "Synced. ${report.pushed} sent, ${report.pulled} received.")
        if (report.heldBackByStrictMode > 0) {
            append(" Strict Mode kept ${report.heldBackByStrictMode} Rule${if (report.heldBackByStrictMode == 1) "" else "s"} unchanged.")
        }
    }
    CloudSyncOutcome.Offline -> "Couldn't reach the server. Your Rules will sync when you're back online."
    CloudSyncOutcome.SessionExpired -> "Your sign-in expired. Sign in again to keep syncing."
    CloudSyncOutcome.SignedOut -> "Sign in to sync."
    CloudSyncOutcome.NotConfigured -> "Cloud Sync isn't available in this build."
    CloudSyncOutcome.Failed -> report.message ?: "Sync didn't finish. Try again."
}

private fun cloudResultMessage(result: CloudResult<*>): String = when (result) {
    is CloudResult.Rejected -> result.message
    is CloudResult.Retryable -> result.message
    CloudResult.Unauthorized -> "That didn't work. Request a new code and try again."
    is CloudResult.Ok -> ""
}
