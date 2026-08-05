package com.kaleel.earnitv2

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

enum class RuleTypeOption(
    val title: String,
    val description: String,
    val example: String,
    val ruleType: EarnItRuleStore.RuleType
) {
    EarnRewardTime(
        title = "Earn Reward Time",
        description = "Use productive apps to earn time for distracting apps.",
        example = "10 min productive -> 2 min Reward Time",
        ruleType = EarnItRuleStore.RuleType.EarnRewardTime
    ),
    CompleteToUnlock(
        title = "Complete to Unlock",
        description = "Finish required productive activity before selected apps unlock.",
        example = "Complete all requirements to unlock",
        ruleType = EarnItRuleStore.RuleType.CompleteToUnlock
    ),
    ScheduledBlock(
        title = "Scheduled Block",
        description = "Block selected apps during chosen days and times.",
        example = "Weekdays - 9:00 AM-5:00 PM",
        ruleType = EarnItRuleStore.RuleType.ScheduledBlock
    )
}

@Composable
fun EarnItRuleTypeSelection(
    onBack: () -> Unit,
    onSelectRuleType: (EarnItRuleStore.RuleType) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    var selectedOption by remember { mutableStateOf<RuleTypeOption?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        RuleTypeTopBar(onBack = onBack)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = "What should this Rule do?", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Choose how EarnIt should control access to your apps.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RuleTypeOption.entries.forEach { option ->
                RuleTypeCard(
                    option = option,
                    selected = selectedOption == option,
                    onClick = {
                        selectedOption = option
                        onSelectRuleType(option.ruleType)
                    }
                )
            }
        }
    }
}

@Composable
private fun RuleTypeTopBar(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Text(text = "<")
        }
        Text(text = "Create Rule", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun RuleTypeCard(
    option: RuleTypeOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    val presentation = ruleTypePresentation(option.ruleType)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) presentation.accentColor else MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(6.dp)),
                color = presentation.containerColor,
                border = BorderStroke(1.dp, presentation.accentColor.copy(alpha = 0.5f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    RuleTypeIcon(ruleType = option.ruleType, size = 36.dp)
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(text = option.title, style = MaterialTheme.typography.titleMedium, color = presentation.accentColor)
                Text(text = option.description, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = option.example,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.size(4.dp))
            Text(text = ">", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
