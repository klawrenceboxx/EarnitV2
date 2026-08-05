package com.kaleel.earnitv2

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val BfGreen = Color(0xFF79C957)
private val BfAmber = Color(0xFFFFA33D)

@Composable
fun BenjaminFranklinHistoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val today = LocalDate.now()
    val history = remember {
        (0..59).mapNotNull { daysBack ->
            BenjaminFranklinStore.get(context, today.minusDays(daysBack.toLong()))
        }
    }
    val streak = bfStreak(history, today)

    BackHandler(onBack = onBack)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onBack,
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
                Text(
                    "Commitment History",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        item { BfStreakCard(streak) }
        if (history.isEmpty()) {
            item {
                BfCard {
                    Text(
                        "No commitments yet. Enable Benjamin Franklin Mode on a Complete to Unlock Rule and set your first commitment.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(history) { commitment ->
                BfCommitmentRow(commitment)
            }
        }
    }
}

@Composable
fun CommitmentReviewDialog(
    commitment: DailyCommitment,
    onDismiss: () -> Unit,
    onSave: (completed: Boolean, reflection: String?) -> Unit
) {
    var completed by remember { mutableStateOf(true) }
    var reflection by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review today's commitment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    commitment.commitment,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = completed,
                        onClick = { completed = true },
                        label = { Text("Completed") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = !completed,
                        onClick = { completed = false },
                        label = { Text("Missed") },
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = reflection,
                    onValueChange = { if (it.length <= 500) reflection = it },
                    label = { Text("Reflection (optional)") },
                    placeholder = { Text("What did you learn or notice?") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(completed, reflection.trim().takeIf { it.isNotBlank() })
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun BfStreakCard(streak: Int) {
    BfCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .background(BfGreen.copy(alpha = .15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (streak > 0) "🔥" else "○",
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "$streak-day streak",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (streak == 0) "Complete a commitment today to start your streak."
                    else "Consecutive days with a completed commitment.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BfCommitmentRow(commitment: DailyCommitment) {
    val (statusLabel, statusColor) = when (commitment.completionStatus) {
        CompletionStatus.Completed -> "Completed" to BfGreen
        CompletionStatus.Missed -> "Missed" to MaterialTheme.colorScheme.error
        CompletionStatus.Pending -> "Pending" to BfAmber
    }
    BfCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    bfDateLabel(commitment.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    commitment.commitment,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                commitment.importance?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                commitment.reflection?.let { reflection ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
                    Text(
                        "\"$reflection\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Surface(color = statusColor.copy(alpha = .12f), shape = RoundedCornerShape(8.dp)) {
                Text(
                    statusLabel,
                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun BfCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .8f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) { content() }
    }
}

internal fun bfStreak(history: List<DailyCommitment>, today: LocalDate): Int {
    var streak = 0
    var cursor = today
    while (true) {
        val entry = history.firstOrNull { it.date == cursor }
        if (entry?.completionStatus == CompletionStatus.Completed) {
            streak++
            cursor = cursor.minusDays(1)
        } else {
            break
        }
    }
    return streak
}

private fun bfDateLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault()))
    }
}
