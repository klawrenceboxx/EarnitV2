package com.example.earnitv2

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.earnitv2.ui.theme.EarnitV2Theme

class BlockedActivity : ComponentActivity() {
    private var blockedAppName by mutableStateOf("Instagram")
    private var availableRewardSeconds by mutableStateOf(0L)
    private var fallbackMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        updateBlockedAppName(intent)
        refreshRewardBalance()
        setContent {
            EarnitV2Theme {
                BlockedScreen(
                    blockedAppName = blockedAppName,
                    availableRewardSeconds = availableRewardSeconds,
                    fallbackMessage = fallbackMessage,
                    onOpenDuolingo = ::openDuolingo
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        updateBlockedAppName(intent)
        refreshRewardBalance()
    }

    private fun updateBlockedAppName(intent: Intent?) {
        blockedAppName = intent?.getStringExtra(EXTRA_BLOCKED_APP_NAME) ?: "Instagram"
    }

    override fun onResume() {
        super.onResume()
        refreshRewardBalance()
    }

    private fun refreshRewardBalance() {
        availableRewardSeconds = RewardLedger.snapshot(this).remainingRewardSeconds
    }

    private fun openDuolingo() {
        val launchIntent = packageManager.getLaunchIntentForPackage(AppPackages.PRODUCTIVE_APP)
        if (launchIntent == null) {
            fallbackMessage = "Duolingo is not installed on this device."
            return
        }

        fallbackMessage = null
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launchIntent)
    }

    companion object {
        const val EXTRA_BLOCKED_APP_NAME = "com.example.earnitv2.extra.BLOCKED_APP_NAME"
    }
}

@Composable
fun BlockedScreen(
    blockedAppName: String,
    availableRewardSeconds: Long,
    fallbackMessage: String?,
    onOpenDuolingo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$blockedAppName is locked.",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Available reward time: ${formatBlockedDuration(availableRewardSeconds)}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            text = "Use Duolingo to earn access to $blockedAppName.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 12.dp)
        )
        Button(
            onClick = onOpenDuolingo,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
        ) {
            Text(text = "Open Duolingo")
        }
        if (fallbackMessage != null) {
            Text(
                text = fallbackMessage,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

private fun formatBlockedDuration(totalSeconds: Long): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0L)
    val minutes = safeSeconds / 60L
    val seconds = safeSeconds % 60L
    return if (minutes > 0L) {
        "${minutes}m ${seconds}s"
    } else {
        "${seconds}s"
    }
}
@Preview(showBackground = true)
@Composable
fun BlockedScreenPreview() {
    EarnitV2Theme {
        BlockedScreen(
            blockedAppName = "Instagram",
            availableRewardSeconds = 0,
            fallbackMessage = null,
            onOpenDuolingo = {}
        )
    }
}
