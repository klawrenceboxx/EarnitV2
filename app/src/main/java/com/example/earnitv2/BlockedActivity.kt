package com.example.earnitv2

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.earnitv2.ui.theme.EarnitV2Theme

class BlockedActivity : ComponentActivity() {
    private var ruleId by mutableStateOf<String?>(null)
    private var blockedAppName by mutableStateOf("Instagram")
    private var blockedPackage by mutableStateOf<String?>(null)
    private var earnApps by mutableStateOf(emptyList<BlockedEarnAppUiState>())
    private var blockedReason by mutableStateOf(RuleAccessEvaluator.DenialReason.OutOfRewardTime)
    private var fallbackMessage by mutableStateOf<String?>(null)
    private var incompleteRequirements by mutableStateOf(emptyList<BlockedRequirementUiState>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        updateRuleFromIntent(intent)
        setContent {
            EarnitV2Theme {
                BackHandler(onBack = ::returnHome)
                BlockedScreen(
                    blockedAppName = blockedAppName,
                    blockedPackage = blockedPackage,
                    earnApps = earnApps,
                    blockedReason = blockedReason,
                    incompleteRequirements = incompleteRequirements,
                    fallbackMessage = fallbackMessage,
                    onOpenEarnApp = ::openEarnApp,
                    onOpenRequirementApp = ::openRequirementApp,
                    onViewMoreEarningApps = ::openRuleDetail,
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
        val requestedRuleId = intent?.getStringExtra(EXTRA_RULE_ID)
        val rule = requestedRuleId?.let { EarnItRuleStore.findRule(this, it) } ?: EarnItRuleStore.getRule(this)
        ruleId = rule.id
        blockedAppName = intent?.getStringExtra(EXTRA_BLOCKED_APP_NAME)
            ?: rule.blockedApps.firstOrNull()?.name
            ?: "Reward App"
        blockedPackage = intent?.getStringExtra(EXTRA_BLOCKED_PACKAGE)
            ?: rule.blockedApps.firstOrNull { it.name == blockedAppName }?.packageName
        earnApps = blockedEarnAppUiStates(
            rule = rule,
            legacyEarnAppName = intent?.getStringExtra(EXTRA_PRODUCTIVE_APP_NAME) ?: rule.productiveName,
            legacyEarnAppPackage = intent?.getStringExtra(EXTRA_PRODUCTIVE_PACKAGE) ?: rule.productivePackage
        )
        blockedReason = intent?.getStringExtra(EXTRA_BLOCKED_REASON)?.let { rawReason ->
            RuleAccessEvaluator.DenialReason.entries.firstOrNull { it.name == rawReason }
        } ?: RuleAccessEvaluator.DenialReason.OutOfRewardTime
        incompleteRequirements = blockedRequirementUiStates(
            rule = rule,
            progressSeconds = RewardLedger.completionProgress(this, rule)
        )
    }

    private fun openEarnApp(app: BlockedEarnAppUiState) {
        openApp(app.packageName, app.name)
    }

    private fun openRequirementApp(requirement: BlockedRequirementUiState) {
        openApp(requirement.packageName, requirement.name)
    }

    private fun openRuleDetail() {
        val targetRuleId = ruleId ?: return
        val detailIntent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_RULE_DETAIL_ID, targetRuleId)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(detailIntent)
        finish()
    }

    private fun openApp(packageName: String, appName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            fallbackMessage = "$appName is not installed on this device."
            return
        }

        fallbackMessage = null
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        TrackedAppLaunchStore.registerPendingLaunch(
            context = this,
            logicalPackageName = packageName,
            launchedPackageName = launchIntent.targetPackageName(packageName)
        )
        try {
            startActivity(launchIntent)
        } catch (_: ActivityNotFoundException) {
            TrackedAppLaunchStore.savePendingLaunch(this, null)
            fallbackMessage = "$appName could not be opened on this device."
        }
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

data class BlockedEarnAppUiState(
    val packageName: String,
    val name: String,
    val exchangeSummary: String
)

data class BlockedOptionsPresentation<T>(
    val visibleOptions: List<T>,
    val hiddenCount: Int
)

fun <T> blockedOptionsPresentation(options: List<T>): BlockedOptionsPresentation<T> {
    return BlockedOptionsPresentation(
        visibleOptions = options.take(MAX_BLOCKED_ACTIONABLE_OPTIONS),
        hiddenCount = (options.size - MAX_BLOCKED_ACTIONABLE_OPTIONS).coerceAtLeast(0)
    )
}

fun blockedOverflowLabel(hiddenCount: Int): String {
    val noun = if (hiddenCount == 1) "earning app" else "earning apps"
    return "View ${hiddenCount.coerceAtLeast(0)} more $noun"
}

fun blockedEarnAppUiStates(
    rule: EarnItRuleStore.Rule,
    legacyEarnAppName: String? = null,
    legacyEarnAppPackage: String? = null
): List<BlockedEarnAppUiState> {
    val configuredApps = rule.earnApps
        .filter { it.packageName.isNotBlank() }
        .distinctBy { it.packageName }
    val apps = configuredApps.ifEmpty {
        val packageName = legacyEarnAppPackage.orEmpty()
        if (packageName.isBlank()) {
            emptyList()
        } else {
            listOf(EarnItRuleStore.RuleApp(packageName, legacyEarnAppName ?: "Earn App"))
        }
    }
    val exchangeSummary = EarnItUiFormatters.exchangeSummary(rule.rewardSecondsPerProductiveSecond)
    return apps.map { app ->
        BlockedEarnAppUiState(
            packageName = app.packageName,
            name = app.name,
            exchangeSummary = exchangeSummary
        )
    }
}

@Composable
fun BlockedScreen(
    blockedAppName: String,
    blockedPackage: String?,
    earnApps: List<BlockedEarnAppUiState>,
    blockedReason: RuleAccessEvaluator.DenialReason,
    incompleteRequirements: List<BlockedRequirementUiState>,
    fallbackMessage: String?,
    onOpenEarnApp: (BlockedEarnAppUiState) -> Unit,
    onOpenRequirementApp: (BlockedRequirementUiState) -> Unit,
    onViewMoreEarningApps: () -> Unit,
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
                when (blockedReason) {
                    RuleAccessEvaluator.DenialReason.OutOfRewardTime -> {
                        Text(text = "Earn more with", style = MaterialTheme.typography.bodyMedium)
                        EarnAppsAvailable(
                            earnApps = earnApps,
                            onOpenEarnApp = onOpenEarnApp,
                            onViewMore = onViewMoreEarningApps
                        )
                    }
                    RuleAccessEvaluator.DenialReason.CompleteToUnlockIncomplete -> {
                        RequirementsRemaining(
                            requirements = incompleteRequirements,
                            onOpenRequirementApp = onOpenRequirementApp,
                            onViewMore = onViewMoreEarningApps
                        )
                    }
                    RuleAccessEvaluator.DenialReason.ScheduledBlockActive -> Unit
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

fun blockedTitle(reason: RuleAccessEvaluator.DenialReason): String {
    return when (reason) {
        RuleAccessEvaluator.DenialReason.ScheduledBlockActive -> "Blocked by schedule"
        RuleAccessEvaluator.DenialReason.CompleteToUnlockIncomplete -> "Complete requirements to unlock"
        RuleAccessEvaluator.DenialReason.OutOfRewardTime -> "You're out of Reward Time"
    }
}

fun blockedDescription(reason: RuleAccessEvaluator.DenialReason, blockedAppName: String): String {
    return when (reason) {
        RuleAccessEvaluator.DenialReason.ScheduledBlockActive -> "$blockedAppName is blocked by a Scheduled Block Rule right now."
        RuleAccessEvaluator.DenialReason.CompleteToUnlockIncomplete -> "$blockedAppName unlocks after this Rule's requirements are complete."
        RuleAccessEvaluator.DenialReason.OutOfRewardTime -> "$blockedAppName uses Reward Time from this Rule."
    }
}

@Composable
private fun EarnAppsAvailable(
    earnApps: List<BlockedEarnAppUiState>,
    onOpenEarnApp: (BlockedEarnAppUiState) -> Unit,
    onViewMore: () -> Unit
) {
    if (earnApps.size == 1) {
        val app = earnApps.single()
        EarnAppCard(app = app)
        Button(
            onClick = { onOpenEarnApp(app) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Open ${app.name}", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        val presentation = blockedOptionsPresentation(earnApps)
        presentation.visibleOptions.forEach { app ->
            BlockedAppActionRow(
                packageName = app.packageName,
                name = app.name,
                supportingText = app.exchangeSummary,
                onOpen = { onOpenEarnApp(app) }
            )
        }
        if (presentation.hiddenCount > 0) {
            ViewMoreEarningOptionsRow(hiddenCount = presentation.hiddenCount, onClick = onViewMore)
        }
    }
}

@Composable
private fun RequirementsRemaining(
    requirements: List<BlockedRequirementUiState>,
    onOpenRequirementApp: (BlockedRequirementUiState) -> Unit,
    onViewMore: () -> Unit
) {
    if (requirements.isEmpty()) {
        Text(
            text = "Requirements remaining",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Complete all requirements:", style = MaterialTheme.typography.bodyMedium)
        if (requirements.size == 1) {
            val requirement = requirements.single()
            RequirementCard(requirement = requirement)
            Button(
                onClick = { onOpenRequirementApp(requirement) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Open ${requirement.name}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            val presentation = blockedOptionsPresentation(requirements)
            presentation.visibleOptions.forEach { requirement ->
                RequirementRow(
                    requirement = requirement,
                    onOpen = { onOpenRequirementApp(requirement) }
                )
            }
            if (presentation.hiddenCount > 0) {
                ViewMoreEarningOptionsRow(hiddenCount = presentation.hiddenCount, onClick = onViewMore)
            }
        }
    }
}

@Composable
private fun ViewMoreEarningOptionsRow(hiddenCount: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text(text = blockedOverflowLabel(hiddenCount), style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun RequirementCard(
    requirement: BlockedRequirementUiState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        RequirementContent(requirement = requirement, modifier = Modifier.padding(14.dp))
    }
}

@Composable
private fun RequirementRow(
    requirement: BlockedRequirementUiState,
    onOpen: () -> Unit
) {
    BlockedAppActionRow(
        packageName = requirement.packageName,
        name = requirement.name,
        supportingText = requirement.progressLabel,
        onOpen = onOpen
    )
}

@Composable
private fun BlockedAppActionRow(
    packageName: String,
    name: String,
    supportingText: String,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EarnItAppIcon(packageName = packageName, appName = name, size = 40.dp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(text = supportingText, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
                onClick = onOpen,
                modifier = Modifier.widthIn(max = 128.dp)
            ) {
                Text(
                    text = "Open $name",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RequirementContent(
    requirement: BlockedRequirementUiState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EarnItAppIcon(packageName = requirement.packageName, appName = requirement.name, size = 40.dp)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Text(
                text = requirement.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(text = requirement.progressLabel, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EarnAppCard(
    app: BlockedEarnAppUiState,
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
            EarnItAppIcon(packageName = app.packageName, appName = app.name, size = 48.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(text = app.exchangeSummary, style = MaterialTheme.typography.bodySmall)
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
            earnApps = listOf(
                BlockedEarnAppUiState(
                    packageName = "com.duolingo",
                    name = "Duolingo",
                    exchangeSummary = "Every 10 min earns 20 min Reward Time"
                ),
                BlockedEarnAppUiState(
                    packageName = "com.ichi2.anki",
                    name = "AnkiDroid",
                    exchangeSummary = "Every 10 min earns 20 min Reward Time"
                )
            ),
            blockedReason = RuleAccessEvaluator.DenialReason.OutOfRewardTime,
            incompleteRequirements = emptyList(),
            fallbackMessage = null,
            onOpenEarnApp = {},
            onOpenRequirementApp = {},
            onViewMoreEarningApps = {},
            onNotNow = {}
        )
    }
}

private const val MAX_BLOCKED_ACTIONABLE_OPTIONS = 4
