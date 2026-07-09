package com.example.earnitv2

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.earnitv2.ui.theme.EarnitV2Theme

class BlockedActivity : ComponentActivity() {
    private var ruleId by mutableStateOf<String?>(null)
    private var blockedAppName by mutableStateOf("Instagram")
    private var blockedPackage by mutableStateOf<String?>(null)
    private var productiveAppName by mutableStateOf("Duolingo")
    private var productivePackage by mutableStateOf(AppPackages.DEFAULT_PRODUCTIVE_APP)
    private var exchangeLabel by mutableStateOf("Every 10 min earns 10 min Reward Time")
    private var blockedReason by mutableStateOf(RuleAccessEvaluator.DenialReason.OutOfRewardTime)
    private var fallbackMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        updateRuleFromIntent(intent)
        setContent {
            EarnitV2Theme {
                BlockedScreen(
                    blockedAppName = blockedAppName,
                    blockedPackage = blockedPackage,
                    productiveAppName = productiveAppName,
                    productivePackage = productivePackage,
                    exchangeLabel = exchangeLabel,
                    blockedReason = blockedReason,
                    fallbackMessage = fallbackMessage,
                    onOpenProductiveApp = ::openProductiveApp,
                    onNotNow = ::returnHome
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        updateRuleFromIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        updateRuleFromIntent(intent)
    }

    private fun updateRuleFromIntent(intent: Intent?) {
        ruleId = intent?.getStringExtra(EXTRA_RULE_ID)
        val rule = ruleId?.let { EarnItRuleStore.findRule(this, it) } ?: EarnItRuleStore.getRule(this)
        blockedAppName = intent?.getStringExtra(EXTRA_BLOCKED_APP_NAME)
            ?: rule.blockedApps.firstOrNull()?.name
            ?: "Reward App"
        blockedPackage = intent?.getStringExtra(EXTRA_BLOCKED_PACKAGE)
            ?: rule.blockedApps.firstOrNull { it.name == blockedAppName }?.packageName
        val primaryEarnApp = rule.earnApps.firstOrNull()
        productiveAppName = intent?.getStringExtra(EXTRA_PRODUCTIVE_APP_NAME) ?: primaryEarnApp?.name ?: rule.productiveName
        productivePackage = intent?.getStringExtra(EXTRA_PRODUCTIVE_PACKAGE) ?: primaryEarnApp?.packageName ?: rule.productivePackage
        blockedReason = intent?.getStringExtra(EXTRA_BLOCKED_REASON)?.let { rawReason ->
            RuleAccessEvaluator.DenialReason.entries.firstOrNull { it.name == rawReason }
        } ?: RuleAccessEvaluator.DenialReason.OutOfRewardTime
        exchangeLabel = EarnItUiFormatters.exchangeSummary(rule.rewardSecondsPerProductiveSecond)
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

    private fun returnHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(homeIntent)
        finish()
    }

    companion object {
        const val EXTRA_RULE_ID = "com.example.earnitv2.extra.RULE_ID"
        const val EXTRA_BLOCKED_APP_NAME = "com.example.earnitv2.extra.BLOCKED_APP_NAME"
        const val EXTRA_BLOCKED_PACKAGE = "com.example.earnitv2.extra.BLOCKED_PACKAGE"
        const val EXTRA_PRODUCTIVE_APP_NAME = "com.example.earnitv2.extra.PRODUCTIVE_APP_NAME"
        const val EXTRA_PRODUCTIVE_PACKAGE = "com.example.earnitv2.extra.PRODUCTIVE_PACKAGE"
        const val EXTRA_BLOCKED_REASON = "com.example.earnitv2.extra.BLOCKED_REASON"
    }
}

@Composable
fun BlockedScreen(
    blockedAppName: String,
    blockedPackage: String?,
    productiveAppName: String,
    productivePackage: String,
    exchangeLabel: String,
    blockedReason: RuleAccessEvaluator.DenialReason,
    fallbackMessage: String?,
    onOpenProductiveApp: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = blockedTitle(blockedReason),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                EarnItAppIcon(packageName = blockedPackage, appName = blockedAppName, size = 64.dp)
                Text(text = blockedAppName, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = blockedDescription(blockedReason, blockedAppName),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (blockedReason == RuleAccessEvaluator.DenialReason.OutOfRewardTime) {
                    Text(text = "Earn more with", style = MaterialTheme.typography.bodyMedium)
                    EarnAppCard(
                        productiveAppName = productiveAppName,
                        productivePackage = productivePackage,
                        exchangeLabel = exchangeLabel
                    )
                    Button(
                        onClick = onOpenProductiveApp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Open $productiveAppName")
                    }
                }
                TextButton(
                    onClick = onNotNow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Not now")
                }
                if (fallbackMessage != null) {
                    Text(
                        text = fallbackMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private fun blockedTitle(reason: RuleAccessEvaluator.DenialReason): String {
    return when (reason) {
        RuleAccessEvaluator.DenialReason.ScheduledBlockActive -> "Blocked by schedule"
        RuleAccessEvaluator.DenialReason.CompleteToUnlockIncomplete -> "Complete requirements to unlock"
        RuleAccessEvaluator.DenialReason.OutOfRewardTime -> "You're out of Reward Time"
    }
}

private fun blockedDescription(reason: RuleAccessEvaluator.DenialReason, blockedAppName: String): String {
    return when (reason) {
        RuleAccessEvaluator.DenialReason.ScheduledBlockActive -> "$blockedAppName is blocked by a Scheduled Block Rule right now."
        RuleAccessEvaluator.DenialReason.CompleteToUnlockIncomplete -> "$blockedAppName unlocks after this Rule's requirements are complete."
        RuleAccessEvaluator.DenialReason.OutOfRewardTime -> "$blockedAppName uses Reward Time from this Rule."
    }
}

@Composable
private fun EarnAppCard(
    productiveAppName: String,
    productivePackage: String,
    exchangeLabel: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EarnItAppIcon(packageName = productivePackage, appName = productiveAppName, size = 48.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = productiveAppName, style = MaterialTheme.typography.titleMedium)
                Text(text = exchangeLabel, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BlockedScreenPreview() {
    EarnitV2Theme {
        BlockedScreen(
            blockedAppName = "Instagram",
            blockedPackage = "com.instagram.android",
            productiveAppName = "Duolingo",
            productivePackage = "com.duolingo",
            exchangeLabel = "Every 10 min earns 20 min Reward Time",
            blockedReason = RuleAccessEvaluator.DenialReason.OutOfRewardTime,
            fallbackMessage = null,
            onOpenProductiveApp = {},
            onNotNow = {}
        )
    }
}
