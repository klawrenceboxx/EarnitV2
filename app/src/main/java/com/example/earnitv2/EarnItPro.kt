package com.example.earnitv2

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.earnitv2.ui.theme.WarmCoral
import com.example.earnitv2.ui.theme.WarmInk
import com.example.earnitv2.ui.theme.WarmOutline
import com.example.earnitv2.ui.theme.WarmSurface
import com.example.earnitv2.ui.theme.WarmSurfaceRaised
import com.example.earnitv2.ui.theme.WarmText
import com.example.earnitv2.ui.theme.WarmTextMuted

enum class ProRoute { Gate, Intro, Plans, Compare, Restore, PurchaseStatus }

data class ProFlowState(
    val route: ProRoute,
    val entryPoint: PremiumEntryPoint = PremiumEntryPoint.Settings,
    val selectedPlanId: String = SubscriptionConfig.Placeholder.annual.id
)

data class PremiumGateContent(
    val title: String,
    val body: String,
    val benefits: List<String>,
    val icon: String,
    val iconSemantic: String
)

internal fun premiumGateContent(entryPoint: PremiumEntryPoint): PremiumGateContent = when (entryPoint) {
    PremiumEntryPoint.DeepWork -> PremiumGateContent(
        "Deep Work is part of EarnIt Pro",
        "Start focused sessions with stronger protection against distractions.",
        listOf("Longer focus sessions", "Stronger blocking", "Fewer ways to exit early"),
        "◎",
        "Deep Work timer"
    )
    PremiumEntryPoint.StrictMode -> PremiumGateContent(
        "Strict Mode is part of EarnIt Pro",
        "Lock your Rules and make impulsive changes harder.",
        listOf("Protect active Rules", "Prevent quick deactivation", "Stay committed"),
        "◇",
        "Protected Rule"
    )
    PremiumEntryPoint.Analytics, PremiumEntryPoint.Insights -> PremiumGateContent(
        "Unlock your weekly view",
        "See how your habits change across seven days.",
        listOf("Daily screen-time trends", "Earn versus Reward usage", "Most-used apps and insights"),
        "▥",
        "Weekly analytics"
    )
    PremiumEntryPoint.RuleLimit -> PremiumGateContent(
        "You've reached the free limit",
        "Free includes up to 2 active Rules.",
        listOf("Create unlimited active Rules", "Keep separate routines", "Build different focus systems"),
        "≡",
        "Unlimited Rules"
    )
    PremiumEntryPoint.Settings -> PremiumGateContent(
        "Upgrade to EarnIt Pro",
        "Unlock unlimited Rules, Deep Work, Strict Mode, and weekly insights.",
        listOf("Unlimited active Rules", "Stronger focus tools", "Seven-day insights"),
        "✦",
        "EarnIt Pro"
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumGateDialog(
    entryPoint: PremiumEntryPoint,
    onUpgrade: () -> Unit,
    onDismiss: () -> Unit
) {
    val copy = premiumGateContent(entryPoint)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = WarmInk,
        contentColor = WarmText,
        dragHandle = {
            Surface(
                modifier = Modifier.padding(top = 10.dp).width(42.dp).heightIn(min = 4.dp),
                color = WarmOutline,
                shape = RoundedCornerShape(99.dp)
            ) {}
        }
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = WarmSurfaceRaised,
                border = BorderStroke(1.dp, WarmOutline),
                modifier = Modifier.semantics { contentDescription = copy.iconSemantic }
            ) {
                Text(copy.icon, Modifier.padding(horizontal = 17.dp, vertical = 12.dp), color = WarmCoral, style = MaterialTheme.typography.headlineSmall)
            }
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(copy.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = WarmText)
                Text(copy.body, style = MaterialTheme.typography.bodyLarge, color = WarmTextMuted)
            }
            Surface(
                color = WarmSurface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, WarmOutline)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    copy.benefits.forEach { benefit ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("✓", color = WarmCoral, fontWeight = FontWeight.Bold)
                            Text(benefit, color = WarmText, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            Button(
                onClick = onUpgrade,
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WarmCoral, contentColor = WarmInk)
            ) {
                Text("See Pro plans")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Maybe later", color = WarmTextMuted)
            }
        }
    }
}

internal fun premiumGateCopy(entryPoint: PremiumEntryPoint): Pair<String, String> =
    premiumGateContent(entryPoint).let { it.title to it.body }

internal fun proGateUpgradeFlow(flow: ProFlowState): ProFlowState = flow.copy(route = ProRoute.Plans)

internal fun selectedSubscriptionPlan(config: SubscriptionConfig, selectedPlanId: String): SubscriptionPlan =
    listOf(config.annual, config.monthly).first { it.id == selectedPlanId }

@Composable
fun EarnItProScreen(
    flow: ProFlowState,
    config: SubscriptionConfig,
    entitlement: EntitlementState,
    purchaseState: PurchaseState,
    onFlowChange: (ProFlowState) -> Unit,
    onPurchase: (SubscriptionPlan) -> Unit,
    onRestore: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        when (flow.route) {
            ProRoute.Gate, ProRoute.Intro -> onClose()
            ProRoute.Plans -> onFlowChange(flow.copy(route = ProRoute.Intro))
            ProRoute.Compare -> onFlowChange(flow.copy(route = ProRoute.Plans))
            ProRoute.Restore, ProRoute.PurchaseStatus -> onFlowChange(flow.copy(route = ProRoute.Plans))
        }
    }
    when (flow.route) {
        ProRoute.Gate -> PremiumGateDialog(
            entryPoint = flow.entryPoint,
            onUpgrade = { onFlowChange(proGateUpgradeFlow(flow)) },
            onDismiss = onClose
        )
        ProRoute.Intro -> ProIntro(
            onSeePlans = { onFlowChange(flow.copy(route = ProRoute.Plans)) },
            onClose = onClose,
            modifier = modifier
        )
        ProRoute.Plans -> PlanSelection(
            flow = flow,
            config = config,
            onSelect = { onFlowChange(flow.copy(selectedPlanId = it.id)) },
            onContinue = {
                val selected = selectedSubscriptionPlan(config, flow.selectedPlanId)
                onPurchase(selected)
                onFlowChange(flow.copy(route = ProRoute.PurchaseStatus))
            },
            onCompare = { onFlowChange(flow.copy(route = ProRoute.Compare)) },
            onRestore = { onFlowChange(flow.copy(route = ProRoute.Restore)) },
            onBack = { onFlowChange(flow.copy(route = ProRoute.Intro)) },
            modifier = modifier
        )
        ProRoute.Compare -> ComparePlans(
            onBack = { onFlowChange(flow.copy(route = ProRoute.Plans)) },
            modifier = modifier
        )
        ProRoute.Restore -> RestorePurchases(
            state = purchaseState,
            onRestore = onRestore,
            onClose = onClose,
            modifier = modifier
        )
        ProRoute.PurchaseStatus -> PurchaseStatusScreen(
            state = purchaseState,
            entitlement = entitlement,
            onTryAgain = { onFlowChange(flow.copy(route = ProRoute.Plans)) },
            onClose = onClose,
            modifier = modifier
        )
    }
}

@Composable
private fun ProIntro(onSeePlans: () -> Unit, onClose: () -> Unit, modifier: Modifier) {
    ProPage("EarnIt Pro", onClose, modifier) {
        Text("♛", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.displaySmall)
        Text(
            "More freedom.\nMore focus. More you.",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        listOf(
            "Unlimited active Rules" to "Create as many Rules as you need.",
            "Deep Work Mode" to "Start focused sessions with stronger distraction blocking.",
            "Strict Mode" to "Lock your Rules and stay committed.",
            "7-Day Analytics & Insights" to "See patterns and progress over time."
        ).forEach { (title, detail) -> ProFeatureRow(title, detail) }
        Spacer(Modifier.weight(1f))
        Button(onClick = onSeePlans, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text("See Plans")
        }
        TextButton(onClick = onClose) { Text("Maybe later") }
    }
}

@Composable
private fun PlanSelection(
    flow: ProFlowState,
    config: SubscriptionConfig,
    onSelect: (SubscriptionPlan) -> Unit,
    onContinue: () -> Unit,
    onCompare: () -> Unit,
    onRestore: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier
) {
    ProPage("", onBack, modifier, spacing = 12) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Text("✦", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.primary)
        }
        Text(
            "Upgrade to EarnIt Pro",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            "Unlock unlimited Rules, Deep Work, Strict Mode, and weekly insights.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        PlanCard(
            plan = config.annual,
            selected = flow.selectedPlanId == config.annual.id,
            badge = "Most Popular",
            supporting = "$${config.annualMonthlyEquivalent}/month equivalent",
            savings = "Save ${config.yearlySavingsPercent}%",
            trialText = if (config.annualTrialEnabled) "${config.annual.trialDays}-day free trial" else null,
            onSelect = { onSelect(config.annual) }
        )
        PlanCard(
            plan = config.monthly,
            selected = flow.selectedPlanId == config.monthly.id,
            onSelect = { onSelect(config.monthly) }
        )
        listOf("Cancel anytime", "Works across your devices", "Secure payment with Google Play")
            .forEach { Text("✓  $it", style = MaterialTheme.typography.bodySmall) }
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WarmCoral, contentColor = WarmInk)
        ) {
            Text("Continue")
        }
        ProActionRow("Compare Plans", onCompare)
        ProActionRow("Restore Purchases", onRestore)
        Text(
            "Terms of Service  ·  Privacy Policy",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProActionRow(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = androidx.compose.ui.graphics.Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), color = WarmCoral, fontWeight = FontWeight.SemiBold)
            Text("›", color = WarmCoral)
        }
    }
}

@Composable
private fun PlanCard(
    plan: SubscriptionPlan,
    selected: Boolean,
    badge: String? = null,
    supporting: String? = null,
    savings: String? = null,
    trialText: String? = null,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onSelect)
            .semantics { contentDescription = "${plan.displayName} plan, ${plan.formattedPrice}, ${if (selected) "selected" else "not selected"}" },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) WarmCoral.copy(alpha = .16f)
            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) WarmCoral else MaterialTheme.colorScheme.outlineVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            badge?.let { Text(it, color = WarmCoral, style = MaterialTheme.typography.labelMedium) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(plan.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${plan.formattedPrice}/${if (plan.billingPeriod == BillingPeriod.Annual) "year" else "month"}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(if (selected) "◉" else "○", color = WarmCoral, style = MaterialTheme.typography.titleLarge)
            }
            supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            savings?.let { Text(it, color = WarmCoral, style = MaterialTheme.typography.labelMedium) }
            trialText?.let { Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
private fun ComparePlans(onBack: () -> Unit, modifier: Modifier) {
    ProPage("Compare Plans", onBack, modifier) {
        Row(Modifier.fillMaxWidth()) {
            Text("Feature", Modifier.weight(1.35f), fontWeight = FontWeight.Bold)
            Text("Free", Modifier.weight(.7f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            Box(
                Modifier.weight(.85f).background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = .46f),
                    RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)
                ).padding(8.dp),
                contentAlignment = Alignment.Center
            ) { Text("♛ Pro", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
        }
        listOf(
            Triple("Active Rules", "Up to 2", "Unlimited"),
            Triple("Deep Work", "—", "✓"),
            Triple("Strict Mode", "—", "✓"),
            Triple("Analytics", "Today", "7-Day & Insights"),
            Triple("App Blocking", "✓", "✓"),
            Triple("Website Blocking", "✓", "✓"),
            Triple("Scheduled Rules", "✓", "✓")
        ).forEach { (feature, free, pro) ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(feature, Modifier.weight(1.35f), style = MaterialTheme.typography.bodySmall)
                Text(free, Modifier.weight(.7f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                Box(
                    Modifier.weight(.85f).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .28f))
                        .padding(vertical = 14.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) { Text(pro, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            }
        }
    }
}

@Composable
private fun RestorePurchases(
    state: PurchaseState,
    onRestore: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier
) {
    ProPage("Restore Purchases", onClose, modifier) {
        Text("☁", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Text(
            when (state) {
                PurchaseState.RestoreSuccess -> "EarnIt Pro was restored."
                PurchaseState.RestoreNotFound -> "No active purchase was found."
                is PurchaseState.Unavailable -> state.message
                else -> "If you've already purchased EarnIt Pro, we'll check and restore your access."
            },
            textAlign = TextAlign.Center
        )
        Button(
            onClick = onRestore,
            enabled = state != PurchaseState.Processing,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) { Text(if (state == PurchaseState.Processing) "Restoring…" else "Restore") }
        TextButton(onClick = onClose) { Text("Maybe later") }
    }
}

@Composable
private fun PurchaseStatusScreen(
    state: PurchaseState,
    entitlement: EntitlementState,
    onTryAgain: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier
) {
    val title: String
    val body: String
    val symbol: String
    when (state) {
        PurchaseState.Idle, PurchaseState.LoadingPlans, PurchaseState.Processing -> {
            title = "Processing your purchase…"; body = "Please wait while we set up your subscription."; symbol = ""
        }
        PurchaseState.Success -> {
            title = "You're all set!"; body = "Enjoy your Premium features."; symbol = "✓"
        }
        PurchaseState.Pending -> {
            title = "Purchase pending"; body = "Your purchase is still being processed. Premium will unlock after it is confirmed."; symbol = "◷"
        }
        is PurchaseState.Failed -> {
            title = "Purchase failed"; body = state.message; symbol = "!"
        }
        PurchaseState.Cancelled -> {
            title = "Purchase cancelled"; body = "You can try again anytime."; symbol = "×"
        }
        PurchaseState.RestoreSuccess -> {
            title = "Purchase restored"; body = "EarnIt Pro is active again."; symbol = "✓"
        }
        PurchaseState.RestoreNotFound -> {
            title = "No purchase found"; body = "We couldn't find an active EarnIt Pro purchase."; symbol = "○"
        }
        is PurchaseState.Unavailable -> {
            title = "Purchases unavailable"; body = state.message; symbol = "i"
        }
    }
    ProPage("EarnIt Pro", onClose, modifier) {
        if (state == PurchaseState.Processing || state == PurchaseState.LoadingPlans || state == PurchaseState.Idle) {
            CircularProgressIndicator()
        } else {
            Box(
                Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape).padding(20.dp),
                contentAlignment = Alignment.Center
            ) { Text(symbol, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary) }
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(body, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state == PurchaseState.Success && !entitlement.grantsPremium) {
            Text("Verifying access…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (state is PurchaseState.Failed || state is PurchaseState.Unavailable) {
            Button(onClick = onTryAgain, modifier = Modifier.fillMaxWidth()) { Text("Try Again") }
            TextButton(onClick = onClose) { Text("Maybe later") }
        } else if (state != PurchaseState.Processing) {
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}

@Composable
private fun ProFeatureRow(title: String, detail: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Text("✦", Modifier.padding(10.dp), color = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier,
    spacing: Int = 16,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Spacer(Modifier.width(8.dp))
            if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        content()
    }
}

@Composable
fun DebugEntitlementSimulator(
    entitlement: EntitlementState,
    purchaseProvider: LocalPurchaseProvider,
    onEntitlement: (EntitlementState) -> Unit,
    onReset: () -> Unit
) {
    val selectedPurchaseOutcome by purchaseProvider.nextPurchaseOutcomeState.collectAsState()
    val selectedRestoreOutcome by purchaseProvider.nextRestoreOutcomeState.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "These controls simulate subscription states for testing. They do not create real purchases.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SimulatorGroupTitle("Entitlement")
        listOf(
            "Free" to EntitlementState.Free,
            "Purchased Premium" to EntitlementState(EntitlementStatus.Active, EntitlementSource.Purchase),
            "Beta Premium" to EntitlementState(EntitlementStatus.Active, EntitlementSource.Beta),
            "Grace period" to EntitlementState(EntitlementStatus.GracePeriod, EntitlementSource.Purchase),
            "Expired" to EntitlementState(EntitlementStatus.Expired, EntitlementSource.Purchase),
            "Unknown" to EntitlementState.Unknown,
            "Offline Premium" to EntitlementState(EntitlementStatus.Active, EntitlementSource.Purchase, offline = true),
            "Offline Free" to EntitlementState(EntitlementStatus.Free, offline = true)
        ).chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, value) ->
                    val selected = entitlement.sameSimulatorState(value)
                    OutlinedButton(
                        onClick = { onEntitlement(value) },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                    ) {
                        Text(label, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        SimulatorGroupTitle("Next purchase result")
        listOf(
            MockPurchaseOutcome.Success to "Success",
            MockPurchaseOutcome.Processing to "Processing",
            MockPurchaseOutcome.Pending to "Pending",
            MockPurchaseOutcome.Failed to "Failed",
            MockPurchaseOutcome.Cancelled to "Cancelled"
        ).chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (outcome, label) ->
                    val selected = selectedPurchaseOutcome == outcome
                    OutlinedButton(
                        onClick = { purchaseProvider.nextPurchaseOutcome = outcome },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                    ) {
                        Text(label, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        SimulatorGroupTitle("Restore result")
        listOf(
            MockPurchaseOutcome.RestoreSuccess to "Restored",
            MockPurchaseOutcome.RestoreNotFound to "Nothing found"
        ).forEach { (outcome, label) ->
            val selected = selectedRestoreOutcome == outcome
            OutlinedButton(
                onClick = { purchaseProvider.nextRestoreOutcome = outcome },
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            ) { Text(label) }
        }
        TextButton(
            onClick = {
                purchaseProvider.nextPurchaseOutcome = MockPurchaseOutcome.Success
                purchaseProvider.nextRestoreOutcome = MockPurchaseOutcome.RestoreSuccess
                onReset()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Reset simulator") }
    }
}

@Composable
fun PremiumSimulatorScreen(
    entitlement: EntitlementState,
    purchaseProvider: LocalPurchaseProvider,
    onEntitlement: (EntitlementState) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    ProPage("Premium simulator", onBack, modifier) {
        DebugEntitlementSimulator(entitlement, purchaseProvider, onEntitlement, onReset)
    }
}

internal fun EntitlementState.sameSimulatorState(other: EntitlementState): Boolean =
    status == other.status && source == other.source && offline == other.offline

internal fun humanReadableEntitlement(state: EntitlementState): String = when {
    state.offline && state.grantsPremium -> "Offline Premium"
    state.offline -> "Offline Free"
    state.status == EntitlementStatus.Active && state.source == EntitlementSource.Purchase -> "Purchased Premium"
    state.status == EntitlementStatus.Active && state.source == EntitlementSource.Beta -> "Beta Premium"
    state.status == EntitlementStatus.GracePeriod -> "Grace period"
    state.status == EntitlementStatus.Expired -> "Expired"
    state.status == EntitlementStatus.Unknown -> "Unknown"
    else -> "Free"
}

@Composable
private fun SimulatorGroupTitle(value: String) {
    Text(value.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
