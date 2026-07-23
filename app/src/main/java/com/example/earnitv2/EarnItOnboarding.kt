package com.example.earnitv2

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.earnitv2.ui.theme.OnboardingSuccessContainer
import com.example.earnitv2.ui.theme.OnboardingSuccessIcon

@Composable
fun EarnItOnboarding(
    currentStep: OnboardingStep,
    permissions: OnboardingPermissionState,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onNotNow: () -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onCreateFirstRule: () -> Unit,
    onGoHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = currentStep != OnboardingStep.Value, onBack = onBack)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OnboardingProgress(currentStep)
        Spacer(Modifier.height(26.dp))

        when (currentStep) {
            OnboardingStep.Value -> ValueStep(onContinue)
            OnboardingStep.Example -> ExampleStep(onContinue)
            OnboardingStep.PermissionIntroduction -> PermissionIntroductionStep(onContinue, onNotNow)
            OnboardingStep.EarningRationale -> PermissionRationaleStep(
                title = "Allow earning progress",
                body = "EarnIt uses app activity data to count time in your Earn Apps and calculate Reward Time.",
                missingBody = "EarnIt cannot count time in your Earn Apps until you allow usage access.",
                isAllowed = permissions.earningProgressAllowed,
                onOpenSettings = onOpenUsageAccessSettings,
                onContinue = onContinue,
                onNotNow = onNotNow
            )
            OnboardingStep.EarningAllowed -> PermissionAllowedStep(
                title = if (permissions.earningProgressAllowed) "Earning progress allowed" else "Earning progress not allowed",
                body = if (permissions.earningProgressAllowed) {
                    "Great! EarnIt can now count time in your Earn Apps."
                } else {
                    "Return to Android Settings to allow earning progress."
                },
                onContinue = onContinue
            )
            OnboardingStep.BlockingRationale -> PermissionRationaleStep(
                title = "Allow app blocking",
                body = "EarnIt uses Accessibility access to detect when a Reward App opens and show the blocked screen when your Rule applies.",
                missingBody = "EarnIt cannot block Reward Apps until you allow Accessibility access.",
                isAllowed = permissions.appBlockingAllowed,
                onOpenSettings = onOpenAccessibilitySettings,
                onContinue = onContinue,
                onNotNow = onNotNow
            )
            OnboardingStep.BlockingAllowed -> PermissionAllowedStep(
                title = if (permissions.appBlockingAllowed) "App blocking allowed" else "App blocking not allowed",
                body = if (permissions.appBlockingAllowed) {
                    "EarnIt can now protect Reward Apps when your Rules apply."
                } else {
                    "Return to Android Settings to allow app blocking."
                },
                onContinue = onContinue
            )
            OnboardingStep.Ready -> ReadyStep(
                ready = permissions.isReady,
                onCreateFirstRule = onCreateFirstRule,
                onGoHome = onGoHome
            )
        }
    }
}

@Composable
private fun OnboardingProgress(step: OnboardingStep) {
    val position = when (step) {
        OnboardingStep.Value -> 0
        OnboardingStep.Example -> 1
        OnboardingStep.PermissionIntroduction,
        OnboardingStep.EarningRationale,
        OnboardingStep.EarningAllowed,
        OnboardingStep.BlockingRationale,
        OnboardingStep.BlockingAllowed -> 2
        OnboardingStep.Ready -> 3
    }
    Row(
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = "Onboarding step ${position + 1} of 4"
        },
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        repeat(4) { index ->
            Box(
                Modifier
                    .size(if (index == position) 9.dp else 7.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == position) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}

@Composable
private fun ValueStep(onContinue: () -> Unit) {
    OnboardingPage(
        title = "Do the work.\nEarn the time.",
        body = "Use productive apps to earn access to the apps that distract you.",
        illustration = { EarnTimeRelationship(compact = false) },
        primaryLabel = "See how it works",
        onPrimary = onContinue
    )
}

@Composable
private fun ExampleStep(onContinue: () -> Unit) {
    OnboardingPage(
        title = "Your time becomes the key.",
        body = "Every 10 minutes in Duolingo earns 2 minutes of Reward Time.",
        illustration = { ExampleRelationship() },
        supporting = "When Reward Time reaches zero, your Reward Apps are blocked until you earn more.",
        callout = "You’ll choose your own Earn and Reward Apps.",
        primaryLabel = "Set up EarnIt",
        onPrimary = onContinue
    )
}

@Composable
private fun PermissionIntroductionStep(onContinue: () -> Unit, onNotNow: () -> Unit) {
    OnboardingPage(
        title = "EarnIt needs two permissions",
        body = "These let EarnIt count your earning progress and protect your Reward Time.",
        illustration = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CapabilityCard(R.drawable.ic_earnit_timer, "Earning progress", "Counts time in your Earn Apps.")
                CapabilityCard(R.drawable.ic_earnit_shield, "App blocking", "Stops Reward Apps when your Rule applies.")
            }
        },
        primaryLabel = "Continue setup",
        onPrimary = onContinue,
        secondaryLabel = "Not now",
        onSecondary = onNotNow
    )
}

@Composable
private fun PermissionRationaleStep(
    title: String,
    body: String,
    missingBody: String,
    isAllowed: Boolean,
    onOpenSettings: () -> Unit,
    onContinue: () -> Unit,
    onNotNow: () -> Unit
) {
    OnboardingPage(
        title = title,
        body = body,
        illustration = {
            if (isAllowed) SuccessMedallion() else PermissionMissingCard(missingBody)
        },
        primaryLabel = if (isAllowed) "Continue" else "Open Android Settings",
        onPrimary = if (isAllowed) onContinue else onOpenSettings,
        secondaryLabel = if (isAllowed) null else "Not now",
        onSecondary = onNotNow
    )
}

@Composable
private fun PermissionAllowedStep(title: String, body: String, onContinue: () -> Unit) {
    OnboardingPage(
        title = title,
        body = body,
        illustration = { SuccessMedallion() },
        primaryLabel = "Continue",
        onPrimary = onContinue
    )
}

@Composable
private fun ReadyStep(ready: Boolean, onCreateFirstRule: () -> Unit, onGoHome: () -> Unit) {
    OnboardingPage(
        title = if (ready) "EarnIt is ready" else "Finish setup",
        body = if (ready) {
            "Create your first Rule to choose how productive activity earns Reward Time."
        } else {
            "Both permissions are needed before EarnIt is ready."
        },
        illustration = { if (ready) SuccessMedallion() else PermissionMissingCard("Both permissions are still required.") },
        primaryLabel = if (ready) "Create First Rule" else "Continue setup",
        onPrimary = if (ready) onCreateFirstRule else onGoHome,
        secondaryLabel = if (ready) "Go to Home" else null,
        onSecondary = onGoHome
    )
}

@Composable
private fun OnboardingPage(
    title: String,
    body: String,
    illustration: @Composable () -> Unit,
    primaryLabel: String,
    onPrimary: () -> Unit,
    supporting: String? = null,
    callout: String? = null,
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(30.dp))
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) { illustration() }
        if (supporting != null) {
            Spacer(Modifier.height(24.dp))
            Text(supporting, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        if (callout != null) {
            Spacer(Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .5f)
            ) {
                Text(
                    callout,
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(Modifier.height(34.dp))
        Button(
            onClick = onPrimary,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(16.dp)
        ) { Text(primaryLabel, fontWeight = FontWeight.SemiBold) }
        if (secondaryLabel != null) {
            TextButton(
                onClick = onSecondary,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text(secondaryLabel) }
        }
    }
}

@Composable
private fun EarnTimeRelationship(compact: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Earn App earns Reward Time, which unlocks a Reward App" },
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppConcept(AppPackages.DEFAULT_PRODUCTIVE_APP, "Duolingo", "Earn App", if (compact) 42 else 54)
        RelationshipArrow()
        TimeToken()
        RelationshipArrow()
        AppConcept(AppPackages.DEFAULT_BLOCKED_APP, "Instagram", "Reward App", if (compact) 42 else 54)
    }
}

@Composable
private fun ExampleRelationship() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        EarnTimeRelationship(compact = true)
        Spacer(Modifier.height(18.dp))
        Text("Reward Apps", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppBadge(AppPackages.DEFAULT_BLOCKED_APP, "Instagram")
            AppBadge("com.google.android.youtube", "YouTube")
            AppBadge("com.snapchat.android", "Snapchat")
        }
    }
}

@Composable
private fun AppConcept(packageName: String, appName: String, label: String, size: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EarnItAppIcon(packageName, appName, size.dp)
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AppBadge(packageName: String, appName: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Box(Modifier.padding(8.dp)) { EarnItAppIcon(packageName, appName, 36.dp) }
    }
}

@Composable
private fun RelationshipArrow() {
    Text("→", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun TimeToken() {
    Surface(
        modifier = Modifier.size(58.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .35f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("10:2", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CapabilityCard(iconRes: Int, title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Surface(Modifier.size(46.dp), CircleShape, MaterialTheme.colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SuccessMedallion() {
    Surface(
        modifier = Modifier.size(92.dp),
        shape = CircleShape,
        color = OnboardingSuccessContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_earnit_check),
                contentDescription = "Allowed",
                modifier = Modifier.size(42.dp),
                tint = OnboardingSuccessIcon
            )
        }
    }
}

@Composable
private fun PermissionMissingCard(body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = .55f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_earnit_warning),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Permission not enabled yet",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
