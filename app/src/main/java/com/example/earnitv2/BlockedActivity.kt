package com.example.earnitv2

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.earnitv2.ui.theme.EarnitV2Theme
import com.example.earnitv2.ui.theme.WarmInk
import java.util.Calendar

class BlockedActivity : ComponentActivity() {
    private var ruleId by mutableStateOf<String?>(null)
    private var blockedAppName by mutableStateOf("Instagram")
    private var blockedPackage by mutableStateOf<String?>(null)
    private var blockedDomain by mutableStateOf<String?>(null)
    private var earnApps by mutableStateOf(emptyList<BlockedEarnAppUiState>())
    private var blockedReason by mutableStateOf(RuleAccessEvaluator.DenialReason.OutOfRewardTime)
    private var fallbackMessage by mutableStateOf<String?>(null)
    private var incompleteRequirements by mutableStateOf(emptyList<BlockedRequirementUiState>())
    private var scheduleStatus by mutableStateOf<BlockedScheduleUiState?>(null)
    private var availableRewardTimeLabel by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(WarmInk.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(WarmInk.toArgb())
        )
        updateRuleFromIntent(intent)
        setContent {
            EarnitV2Theme(darkTheme = true, dynamicColor = false) {
                BackHandler(onBack = ::returnHome)
                if (blockedReason == RuleAccessEvaluator.DenialReason.DailyCommitmentMissing) {
                    DailyCommitmentScreen(onCommit = { commitment, minutes, importance ->
                        BenjaminFranklinStore.saveToday(this, commitment, minutes, importance)
                        updateRuleFromIntent(intent)
                        blockedPackage?.let { packageName -> openApp(packageName, blockedAppName) } ?: returnHome()
                    })
                } else BlockedScreen(
                    blockedAppName = blockedAppName,
                    blockedPackage = blockedPackage,
                    blockedDomain = blockedDomain,
                    earnApps = earnApps,
                    blockedReason = blockedReason,
                    incompleteRequirements = incompleteRequirements,
                    scheduleStatus = scheduleStatus,
                    fallbackMessage = fallbackMessage,
                    availableRewardTimeLabel = availableRewardTimeLabel,
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
        val domain = blockedDomain
        if (domain != null) {
            val calendar = Calendar.getInstance()
            val result = RuleAccessEvaluator.evaluateDomain(
                EarnItRuleStore.getRules(this), domain, when (calendar.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.MONDAY -> 1; Calendar.TUESDAY -> 2; Calendar.WEDNESDAY -> 3
                    Calendar.THURSDAY -> 4; Calendar.FRIDAY -> 5; Calendar.SATURDAY -> 6; else -> 7
                },
                calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
            ) { rule -> RuleAccessEvaluator.RuleRuntimeState(
                RewardLedger.snapshot(this, rule).remainingRewardSeconds,
                RewardLedger.completionProgress(this, rule),
                BenjaminFranklinStore.today(this) != null
            ) }
            if (result.allowed) returnToChrome()
        }
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
        blockedDomain = intent?.getStringExtra(EXTRA_BLOCKED_DOMAIN)?.let(DomainNormalizer::normalize)
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
        scheduleStatus = blockedScheduleUiState(rule)
        availableRewardTimeLabel = if (rule.type == EarnItRuleStore.RuleType.EarnRewardTime) {
            EarnItUiFormatters.rewardTimeAvailability(RewardLedger.snapshot(this, rule).remainingRewardSeconds)
        } else null
    }

    private fun openEarnApp(app: BlockedEarnAppUiState) {
        openApp(app.packageName, app.name)
    }

    private fun openRequirementApp(requirement: BlockedRequirementUiState) {
        openApp(requirement.packageName, requirement.name)
    }

    private fun openRuleDetail() {
        val targetRuleId = blockedRuleDetailTarget(ruleId) ?: return
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

    private fun returnToChrome() {
        packageManager.getLaunchIntentForPackage(ChromeBrowserAdapter.CHROME_PACKAGE)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(it)
        }
        finish()
    }

    companion object {
        const val EXTRA_RULE_ID = "com.example.earnitv2.extra.RULE_ID"
        const val EXTRA_BLOCKED_APP_NAME = "com.example.earnitv2.extra.BLOCKED_APP_NAME"
        const val EXTRA_BLOCKED_PACKAGE = "com.example.earnitv2.extra.BLOCKED_PACKAGE"
        const val EXTRA_BLOCKED_DOMAIN = "com.example.earnitv2.extra.BLOCKED_DOMAIN"
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

data class BlockedScheduleUiState(
    val activeDays: String,
    val activeTimeRange: String,
    val remainingTime: String?
)

data class BlockedScreenPresentation(
    val ruleType: EarnItRuleStore.RuleType,
    val title: String,
    val sectionTitle: String?
)

fun blockedScreenPresentation(reason: RuleAccessEvaluator.DenialReason): BlockedScreenPresentation {
    return when (reason) {
        RuleAccessEvaluator.DenialReason.OutOfRewardTime -> BlockedScreenPresentation(
            ruleType = EarnItRuleStore.RuleType.EarnRewardTime,
            title = "You're out of Reward Time",
            sectionTitle = "Earn more with"
        )
        RuleAccessEvaluator.DenialReason.CompleteToUnlockIncomplete -> BlockedScreenPresentation(
            ruleType = EarnItRuleStore.RuleType.CompleteToUnlock,
            title = "Complete requirements to unlock",
            sectionTitle = "Complete all requirements"
        )
        RuleAccessEvaluator.DenialReason.DailyCommitmentMissing -> BlockedScreenPresentation(
            ruleType = EarnItRuleStore.RuleType.CompleteToUnlock,
            title = "Set today's commitment",
            sectionTitle = null
        )
        RuleAccessEvaluator.DenialReason.ScheduledBlockActive -> BlockedScreenPresentation(
            ruleType = EarnItRuleStore.RuleType.ScheduledBlock,
            title = "Blocked by schedule",
            sectionTitle = null
        )
    }
}

fun blockedScheduleUiState(rule: EarnItRuleStore.Rule): BlockedScheduleUiState {
    val calendar = Calendar.getInstance()
    val day = ((calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
    val minuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    return blockedScheduleUiState(rule, day, minuteOfDay)
}

fun blockedScheduleUiState(
    rule: EarnItRuleStore.Rule,
    day: Int,
    minuteOfDay: Int
): BlockedScheduleUiState {
    val detailLines = EarnItRuleStore.scheduleDetailLines(rule.activeDays, rule.effectiveTimeWindows)
    val remainingMinutes = remainingMinutesUntilScheduleEnds(rule, day, minuteOfDay)
    return BlockedScheduleUiState(
        activeDays = detailLines.firstOrNull().orEmpty(),
        activeTimeRange = detailLines.drop(1).joinToString(" · "),
        remainingTime = remainingMinutes?.let(::blockedScheduleRemainingLabel)
    )
}

fun remainingMinutesUntilScheduleEnds(
    rule: EarnItRuleStore.Rule,
    day: Int,
    minuteOfDay: Int
): Int? {
    if (!rule.isActiveAt(day, minuteOfDay)) return null
    for (offset in 1..MINUTES_PER_WEEK) {
        val absoluteMinute = minuteOfDay + offset
        val futureDay = ((day - 1 + absoluteMinute / MINUTES_PER_DAY) % EarnItRuleStore.allDays.size) + 1
        val futureMinute = absoluteMinute % MINUTES_PER_DAY
        if (!rule.isActiveAt(futureDay, futureMinute)) return offset
    }
    return null
}

fun blockedScheduleRemainingLabel(totalMinutes: Int): String {
    val safeMinutes = totalMinutes.coerceAtLeast(1)
    val hours = safeMinutes / 60
    val minutes = safeMinutes % 60
    val duration = when {
        hours == 0 -> "$minutes min"
        minutes == 0 -> "$hours hr"
        else -> "$hours hr $minutes min"
    }
    return "Ends in $duration"
}

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

fun blockedRequirementOverflowLabel(hiddenCount: Int): String {
    val noun = if (hiddenCount == 1) "requirement" else "requirements"
    return "View ${hiddenCount.coerceAtLeast(0)} more $noun"
}

fun blockedRuleDetailTarget(ruleId: String?): String? = ruleId?.takeIf { it.isNotBlank() }

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
    blockedDomain: String? = null,
    earnApps: List<BlockedEarnAppUiState>,
    blockedReason: RuleAccessEvaluator.DenialReason,
    incompleteRequirements: List<BlockedRequirementUiState>,
    scheduleStatus: BlockedScheduleUiState?,
    fallbackMessage: String?,
    onOpenEarnApp: (BlockedEarnAppUiState) -> Unit,
    onOpenRequirementApp: (BlockedRequirementUiState) -> Unit,
    onViewMoreEarningApps: () -> Unit,
    onNotNow: () -> Unit,
    availableRewardTimeLabel: String? = null,
    modifier: Modifier = Modifier
) {
    val presentation = blockedScreenPresentation(blockedReason)
    val accentColor = ruleTypePresentation(presentation.ruleType).accentColor
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            BlockedRuleHeader(
                ruleType = presentation.ruleType,
                title = presentation.title
            )
            BlockedAppIdentity(
                packageName = blockedPackage,
                isWebsite = blockedDomain != null,
                appName = blockedAppName,
                description = blockedDescription(blockedReason, blockedAppName)
            )
            if (availableRewardTimeLabel != null) {
                Text(
                    text = availableRewardTimeLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = accentColor,
                    textAlign = TextAlign.Center
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (blockedReason) {
                    RuleAccessEvaluator.DenialReason.OutOfRewardTime -> {
                        BlockedSectionTitle(presentation.sectionTitle.orEmpty(), accentColor)
                        EarnAppsAvailable(
                            earnApps = earnApps,
                            onOpenEarnApp = onOpenEarnApp,
                            onViewMore = onViewMoreEarningApps,
                            accentColor = accentColor
                        )
                    }
                    RuleAccessEvaluator.DenialReason.CompleteToUnlockIncomplete -> {
                        BlockedSectionTitle(presentation.sectionTitle.orEmpty(), accentColor)
                        RequirementsRemaining(
                            requirements = incompleteRequirements,
                            onOpenRequirementApp = onOpenRequirementApp,
                            onViewMore = onViewMoreEarningApps,
                            accentColor = accentColor
                        )
                    }
                    RuleAccessEvaluator.DenialReason.DailyCommitmentMissing -> Unit
                    RuleAccessEvaluator.DenialReason.ScheduledBlockActive -> {
                        if (scheduleStatus != null) {
                            BlockedScheduleStatusCard(scheduleStatus, accentColor)
                        }
                    }
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
            BlockedNotNowAction(onClick = onNotNow)
        }
    }
}

@Composable
private fun BlockedRuleHeader(
    ruleType: EarnItRuleStore.RuleType,
    title: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RuleTypeIcon(ruleType = ruleType, size = 54.dp)
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun BlockedAppIdentity(
    packageName: String?,
    isWebsite: Boolean,
    appName: String,
    description: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (isWebsite) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Text(
                    "🌐",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(14.dp).semantics { contentDescription = "Website" }
                )
            }
        } else {
            EarnItAppIcon(packageName = packageName, appName = appName, size = 72.dp)
        }
        Text(
            text = appName,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun BlockedSectionTitle(title: String, accentColor: Color) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = accentColor,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun BlockedScheduleStatusCard(status: BlockedScheduleUiState, accentColor: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.42f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BlockedScheduleStatusLine(label = "Active days", value = status.activeDays)
            BlockedScheduleStatusLine(label = "Active time", value = status.activeTimeRange)
            if (status.remainingTime != null) {
                BlockedScheduleStatusLine(
                    label = "Current block",
                    value = status.remainingTime,
                    valueColor = accentColor
                )
            }
        }
    }
}

@Composable
private fun BlockedScheduleStatusLine(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge, color = valueColor)
    }
}

@Composable
private fun BlockedNotNowAction(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(text = "Not now", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun blockedTitle(reason: RuleAccessEvaluator.DenialReason): String {
    return blockedScreenPresentation(reason).title
}

fun blockedDescription(reason: RuleAccessEvaluator.DenialReason, blockedAppName: String): String {
    return when (reason) {
        RuleAccessEvaluator.DenialReason.ScheduledBlockActive -> "$blockedAppName is blocked by a Scheduled Block Rule right now."
        RuleAccessEvaluator.DenialReason.CompleteToUnlockIncomplete -> "$blockedAppName unlocks after this Rule's requirements are complete."
        RuleAccessEvaluator.DenialReason.DailyCommitmentMissing -> "Set today's commitment before this app can unlock."
        RuleAccessEvaluator.DenialReason.OutOfRewardTime -> "$blockedAppName uses Reward Time from this Rule."
    }
}

@Composable
private fun DailyCommitmentScreen(onCommit: (String, Int, String) -> Unit) {
    var commitment by remember { mutableStateOf("") }
    var estimatedMinutes by remember { mutableStateOf("60") }
    var importance by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Benjamin Franklin Mode", style = MaterialTheme.typography.titleMedium)
        Text(text = "What good shall I accomplish today?", style = MaterialTheme.typography.headlineSmall)
        Text(text = "Choose one intentional commitment before unlocking this app.", style = MaterialTheme.typography.bodyMedium)
        TextField(value = commitment, onValueChange = { commitment = it }, label = { Text("Today's commitment") }, modifier = Modifier.fillMaxWidth())
        TextField(value = estimatedMinutes, onValueChange = { estimatedMinutes = it.filter(Char::isDigit) }, label = { Text("Estimated duration (minutes)") }, modifier = Modifier.fillMaxWidth())
        TextField(value = importance, onValueChange = { importance = it }, label = { Text("Why is this important? (optional)") }, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = { onCommit(commitment.trim(), estimatedMinutes.toIntOrNull() ?: 0, importance) },
            enabled = commitment.isNotBlank() && (estimatedMinutes.toIntOrNull() ?: 0) > 0,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Commit for today") }
    }
}

@Composable
private fun EarnAppsAvailable(
    earnApps: List<BlockedEarnAppUiState>,
    onOpenEarnApp: (BlockedEarnAppUiState) -> Unit,
    onViewMore: () -> Unit,
    accentColor: Color
) {
    val presentation = blockedOptionsPresentation(earnApps)
    presentation.visibleOptions.forEach { app ->
        BlockedAppActionRow(
            packageName = app.packageName,
            name = app.name,
            supportingText = app.exchangeSummary,
            accentColor = accentColor,
            onOpen = { onOpenEarnApp(app) }
        )
    }
    if (presentation.hiddenCount > 0) {
        ViewMoreEarningOptionsRow(
            label = blockedOverflowLabel(presentation.hiddenCount),
            accentColor = accentColor,
            onClick = onViewMore
        )
    }
}

@Composable
private fun RequirementsRemaining(
    requirements: List<BlockedRequirementUiState>,
    onOpenRequirementApp: (BlockedRequirementUiState) -> Unit,
    onViewMore: () -> Unit,
    accentColor: Color
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
        val presentation = blockedOptionsPresentation(requirements)
        presentation.visibleOptions.forEach { requirement ->
            RequirementRow(
                requirement = requirement,
                accentColor = accentColor,
                onOpen = { onOpenRequirementApp(requirement) }
            )
        }
        if (presentation.hiddenCount > 0) {
            ViewMoreEarningOptionsRow(
                label = blockedRequirementOverflowLabel(presentation.hiddenCount),
                accentColor = accentColor,
                onClick = onViewMore
            )
        }
    }
}

@Composable
private fun ViewMoreEarningOptionsRow(label: String, accentColor: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.32f))
    ) {
        TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text(text = label, style = MaterialTheme.typography.titleSmall, color = accentColor)
        }
    }
}

@Composable
private fun RequirementRow(
    requirement: BlockedRequirementUiState,
    accentColor: Color,
    onOpen: () -> Unit
) {
    BlockedAppActionRow(
        packageName = requirement.packageName,
        name = requirement.name,
        supportingText = requirement.progressLabel,
        accentColor = accentColor,
        onOpen = onOpen
    )
}

@Composable
private fun BlockedAppActionRow(
    packageName: String,
    name: String,
    supportingText: String,
    accentColor: Color,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.32f))
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
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
                onClick = onOpen,
                modifier = Modifier
                    .widthIn(max = 128.dp)
                    .semantics { contentDescription = "Open $name" }
            ) {
                Text(
                    text = "Open",
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
            scheduleStatus = null,
            fallbackMessage = null,
            onOpenEarnApp = {},
            onOpenRequirementApp = {},
            onViewMoreEarningApps = {},
            onNotNow = {}
        )
    }
}

private const val MAX_BLOCKED_ACTIONABLE_OPTIONS = 4
private const val MINUTES_PER_DAY = 1_440
private const val MINUTES_PER_WEEK = 7 * MINUTES_PER_DAY
