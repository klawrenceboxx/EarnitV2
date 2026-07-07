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
    private var productiveAppName by mutableStateOf("Duolingo")
    private var productivePackage by mutableStateOf(AppPackages.DEFAULT_PRODUCTIVE_APP)
    private var availableRewardSeconds by mutableStateOf(0L)
    private var fallbackMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        updateRuleFromIntent(intent)
        refreshRewardBalance()
        setContent {
            EarnitV2Theme {
                BlockedScreen(
                    blockedAppName = blockedAppName,
                    productiveAppName = productiveAppName,
                    availableRewardSeconds = availableRewardSeconds,
                    fallbackMessage = fallbackMessage,
                    onOpenProductiveApp = ::openProductiveApp
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        updateRuleFromIntent(intent)
        refreshRewardBalance()
    }

    override fun onResume() {
        super.onResume()
        refreshRewardBalance()
    }

    private fun updateRuleFromIntent(intent: Intent?) {
        val rule = EarnItRuleStore.getRule(this)
        blockedAppName = intent?.getStringExtra(EXTRA_BLOCKED_APP_NAME) ?: rule.blockedName
        productiveAppName = intent?.getStringExtra(EXTRA_PRODUCTIVE_APP_NAME) ?: rule.productiveName
        productivePackage = intent?.getStringExtra(EXTRA_PRODUCTIVE_PACKAGE) ?: rule.productivePackage
    }

    private fun refreshRewardBalance() {
        val rule = EarnItRuleStore.getRule(this)
        availableRewardSeconds = RewardLedger.snapshot(this, rule).remainingRewardSeconds
    }

    private fun openProductiveApp() {
        val launchIntent = packageManager.getLaunchIntentForPackage(productivePackage)
        if (launchIntent == null) {
            fallbackMessage = "$productiveAppName is not installed on this device."
            return
        }

        fallbackMessage = null
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launchIntent)
    }

    companion object {
        const val EXTRA_BLOCKED_APP_NAME = "com.example.earnitv2.extra.BLOCKED_APP_NAME"
        const val EXTRA_PRODUCTIVE_APP_NAME = "com.example.earnitv2.extra.PRODUCTIVE_APP_NAME"
        const val EXTRA_PRODUCTIVE_PACKAGE = "com.example.earnitv2.extra.PRODUCTIVE_PACKAGE"
    }
}

@Composable
fun BlockedScreen(
    blockedAppName: String,
    productiveAppName: String,
    availableRewardSeconds: Long,
    fallbackMessage: String?,
    onOpenProductiveApp: () -> Unit,
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
            text = "Use $productiveAppName to earn access to $blockedAppName.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 12.dp)
        )
        Button(
            onClick = onOpenProductiveApp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
        ) {
            Text(text = "Open $productiveAppName")
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
            productiveAppName = "Duolingo",
            availableRewardSeconds = 0,
            fallbackMessage = null,
            onOpenProductiveApp = {}
        )
    }
}
