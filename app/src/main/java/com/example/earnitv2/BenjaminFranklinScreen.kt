package com.kaleel.earnitv2

import android.app.NotificationManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val BfGreen = Color(0xFF79C957)
private val BfAmber = Color(0xFFFFA33D)
private val BfGray = Color(0xFF9E9E9E)

// ─── Dashboard ───────────────────────────────────────────────────────────────

@Composable
fun BenjaminFranklinDashboard(
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val today = LocalDate.now()

    var todayCommitment by remember { mutableStateOf(BenjaminFranklinStore.today(context)) }
    var reminders by remember { mutableStateOf(BenjaminFranklinStore.getReminderPreferences(context)) }
    var showReviewDialog by remember { mutableStateOf(false) }
    var showSetCommitmentDialog by remember { mutableStateOf(false) }
    var showMorningTimePicker by remember { mutableStateOf(false) }
    var showEveningTimePicker by remember { mutableStateOf(false) }
    var pendingReminderUpdate by remember { mutableStateOf<BfReminderPreferences?>(null) }

    // Android 13+ requires POST_NOTIFICATIONS at runtime. The launcher result applies the pending
    // preference update if granted, or reverts the switch state if denied.
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val pending = pendingReminderUpdate ?: return@rememberLauncherForActivityResult
        pendingReminderUpdate = null
        if (isGranted) {
            BenjaminFranklinStore.saveReminderPreferences(context, pending)
            BenjaminFranklinNotificationScheduler.rescheduleAll(context)
            reminders = pending
        } else {
            // Permission denied — revert the optimistic switch state
            reminders = BenjaminFranklinStore.getReminderPreferences(context)
        }
    }
    val applyReminderToggle: (BfReminderPreferences, Boolean) -> Unit = { updated, isEnabling ->
        if (isEnabling &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
            reminders = updated  // Optimistic UI — switch shows ON while requesting
            pendingReminderUpdate = updated
            notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            reminders = updated
            BenjaminFranklinStore.saveReminderPreferences(context, updated)
            BenjaminFranklinNotificationScheduler.rescheduleAll(context)
        }
    }

    // Milestone notifications fire automatically (no toggle), so we request POST_NOTIFICATIONS
    // proactively here. If the user grants via this path, both milestones and BF reminders work.
    // If they dismiss, milestone notifications are silently skipped until permission is granted
    // through the reminder toggle path. LaunchedEffect(Unit) fires once per composition entry.
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val history = remember {
        (0..59).mapNotNull { BenjaminFranklinStore.get(context, today.minusDays(it.toLong())) }
    }
    val streak = bfStreak(history, today)

    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onBack,
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
            Text(
                "Benjamin Franklin Mode",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        // Today's commitment
        BfCard {
            Text(
                "Today",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (todayCommitment != null) {
                val c = todayCommitment!!
                Text(
                    c.commitment,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                c.importance?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val (label, color) = statusPresentation(c.completionStatus)
                    Surface(color = color.copy(alpha = .12f), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            label,
                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = color,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (c.completionStatus == CompletionStatus.Pending) {
                        TextButton(onClick = { showReviewDialog = true }) { Text("Review") }
                    }
                }
            } else {
                Text(
                    "No commitment set today.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Button(onClick = { showSetCommitmentDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Set today's commitment")
                }
            }
        }

        // Streak
        BfStreakCard(streak)

        // Reminders
        BfCard {
            Text(
                "Reminders",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            BfReminderRow(
                label = "Morning reminder",
                subtitle = "Set your commitment before the day starts",
                enabled = reminders.morningEnabled,
                time = bfTimeLabel(reminders.morningHour, reminders.morningMinute),
                onToggle = { enabled ->
                    applyReminderToggle(reminders.copy(morningEnabled = enabled), enabled)
                },
                onEditTime = { showMorningTimePicker = true }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            BfReminderRow(
                label = "Evening review",
                subtitle = "Reflect and mark your commitment complete or missed",
                enabled = reminders.eveningEnabled,
                time = bfTimeLabel(reminders.eveningHour, reminders.eveningMinute),
                onToggle = { enabled ->
                    applyReminderToggle(reminders.copy(eveningEnabled = enabled), enabled)
                },
                onEditTime = { showEveningTimePicker = true }
            )
        }

        // History link
        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenHistory),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Commitment History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Calendar view of all past commitments", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("›", modifier = Modifier.clearAndSetSemantics { }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleLarge)
            }
        }
    }

    if (showReviewDialog) {
        todayCommitment?.let { c ->
            CommitmentReviewDialog(
                commitment = c,
                onDismiss = { showReviewDialog = false },
                onSave = { completed, reflection ->
                    BenjaminFranklinStore.reviewToday(context, completed, reflection)
                    todayCommitment = BenjaminFranklinStore.today(context)
                    showReviewDialog = false
                }
            )
        }
    }

    if (showSetCommitmentDialog) {
        SetCommitmentDialog(
            onDismiss = { showSetCommitmentDialog = false },
            onSave = { commitment, minutes, importance ->
                BenjaminFranklinStore.saveToday(context, commitment, minutes, importance)
                todayCommitment = BenjaminFranklinStore.today(context)
                showSetCommitmentDialog = false
            }
        )
    }

    if (showMorningTimePicker) {
        BfTimePickerDialog(
            initialHour = reminders.morningHour,
            initialMinute = reminders.morningMinute,
            onDismiss = { showMorningTimePicker = false },
            onConfirm = { h, m ->
                val updated = reminders.copy(morningHour = h, morningMinute = m)
                reminders = updated
                BenjaminFranklinStore.saveReminderPreferences(context, updated)
                if (updated.morningEnabled) BenjaminFranklinNotificationScheduler.scheduleMorning(context, updated)
                showMorningTimePicker = false
            }
        )
    }

    if (showEveningTimePicker) {
        BfTimePickerDialog(
            initialHour = reminders.eveningHour,
            initialMinute = reminders.eveningMinute,
            onDismiss = { showEveningTimePicker = false },
            onConfirm = { h, m ->
                val updated = reminders.copy(eveningHour = h, eveningMinute = m)
                reminders = updated
                BenjaminFranklinStore.saveReminderPreferences(context, updated)
                if (updated.eveningEnabled) BenjaminFranklinNotificationScheduler.scheduleEvening(context, updated)
                showEveningTimePicker = false
            }
        )
    }
}

// ─── Calendar history ────────────────────────────────────────────────────────

@Composable
fun BenjaminFranklinCalendarScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val today = LocalDate.now()
    var selectedDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var displayMonth by rememberSaveable { mutableStateOf(YearMonth.from(today)) }

    BackHandler(onBack = onBack)
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text("‹", style = MaterialTheme.typography.headlineSmall)
            }
            Text(
                "Commitment History",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        // Streak card
        val history = remember(displayMonth) {
            (0..59).mapNotNull { BenjaminFranklinStore.get(context, today.minusDays(it.toLong())) }
        }
        BfStreakCard(bfStreak(history, today))

        // Month navigation
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Month header
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { displayMonth = displayMonth.minusMonths(1) }) {
                        Text("‹", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        displayMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(
                        onClick = { displayMonth = displayMonth.plusMonths(1) },
                        enabled = displayMonth.isBefore(YearMonth.from(today))
                    ) {
                        Text(
                            "›",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (displayMonth.isBefore(YearMonth.from(today)))
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .38f)
                        )
                    }
                }

                // Day-of-week headers (Mon–Sun)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
                        Text(
                            day,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Calendar grid
                val firstOfMonth = displayMonth.atDay(1)
                // offset: Mon=0, Tue=1, ... Sun=6
                val startOffset = (firstOfMonth.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
                val daysInMonth = displayMonth.lengthOfMonth()
                val totalCells = startOffset + daysInMonth
                val rows = (totalCells + 6) / 7

                for (row in 0 until rows) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        for (col in 0 until 7) {
                            val cellIndex = row * 7 + col
                            val dayNumber = cellIndex - startOffset + 1
                            if (dayNumber < 1 || dayNumber > daysInMonth) {
                                Box(Modifier.weight(1f).aspectRatio(1f))
                            } else {
                                val date = displayMonth.atDay(dayNumber)
                                val isFuture = date.isAfter(today)
                                val commitment = if (!isFuture) BenjaminFranklinStore.get(context, date) else null
                                val isSelected = date == selectedDate
                                CalendarDayCell(
                                    day = dayNumber,
                                    isToday = date == today,
                                    isSelected = isSelected,
                                    isFuture = isFuture,
                                    commitment = commitment,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        selectedDate = if (isSelected) null else date
                                    }
                                )
                            }
                        }
                    }
                }

                // Legend
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf(
                        "Completed" to BfGreen,
                        "Missed" to MaterialTheme.colorScheme.error,
                        "Not reviewed" to BfGray,
                        "Pending" to BfAmber
                    ).forEach { (label, color) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(8.dp).background(color, CircleShape))
                            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Day detail
        selectedDate?.let { date ->
            val commitment = BenjaminFranklinStore.get(context, date)
            BfDayDetail(
                date = date,
                commitment = commitment,
                isToday = date == today
            )
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: Int,
    isToday: Boolean,
    isSelected: Boolean,
    isFuture: Boolean,
    commitment: DailyCommitment?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val dotColor = when (commitment?.completionStatus) {
        CompletionStatus.Completed -> BfGreen
        CompletionStatus.Missed -> MaterialTheme.colorScheme.error
        CompletionStatus.NotReviewed -> BfGray
        CompletionStatus.Pending -> BfAmber
        null -> Color.Transparent
    }
    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isToday -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .then(if (!isFuture) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            day.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = if (isFuture) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .38f)
                    else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
        )
        Box(Modifier.size(6.dp).background(dotColor, CircleShape))
    }
}

@Composable
private fun BfDayDetail(
    date: LocalDate,
    commitment: DailyCommitment?,
    isToday: Boolean
) {
    BfCard {
        Text(
            bfDateLabel(date),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (commitment == null) {
            Text(
                "No commitment was set.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(commitment.commitment, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            commitment.importance?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val (label, color) = statusPresentation(commitment.completionStatus)
                Surface(color = color.copy(alpha = .12f), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        label,
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    "~${commitment.estimatedMinutes}m",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            commitment.reflection?.let { reflection ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
                Text("\"$reflection\"", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ─── Legacy list history (kept for EarnItRuleDetail compatibility) ─────────────

@Composable
fun BenjaminFranklinHistoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BenjaminFranklinCalendarScreen(onBack = onBack, modifier = modifier)
}

// ─── Dialogs ─────────────────────────────────────────────────────────────────

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
                Text(commitment.commitment, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilterChip(selected = completed, onClick = { completed = true }, label = { Text("Completed") }, modifier = Modifier.weight(1f))
                    FilterChip(selected = !completed, onClick = { completed = false }, label = { Text("Missed") }, modifier = Modifier.weight(1f))
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
            Button(onClick = { onSave(completed, reflection.trim().takeIf { it.isNotBlank() }) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SetCommitmentDialog(
    onDismiss: () -> Unit,
    onSave: (commitment: String, estimatedMinutes: Int, importance: String?) -> Unit
) {
    var commitment by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("60") }
    var importance by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set today's commitment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = commitment,
                    onValueChange = { commitment = it },
                    label = { Text("Today's commitment") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter(Char::isDigit) },
                    label = { Text("Estimated duration (minutes)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = importance,
                    onValueChange = { importance = it },
                    label = { Text("Why is this important? (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(commitment.trim(), minutes.toIntOrNull() ?: 60, importance.trim().takeIf { it.isNotBlank() }) },
                enabled = commitment.isNotBlank() && (minutes.toIntOrNull() ?: 0) > 0
            ) { Text("Set commitment") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BfTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set reminder time") },
        text = { TimePicker(state = state) },
        confirmButton = {
            Button(onClick = { onConfirm(state.hour, state.minute) }) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ─── Shared sub-composables ──────────────────────────────────────────────────

@Composable
private fun BfReminderRow(
    label: String,
    subtitle: String,
    enabled: Boolean,
    time: String,
    onToggle: (Boolean) -> Unit,
    onEditTime: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
        if (enabled) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .4f),
                modifier = Modifier.clickable(onClick = onEditTime)
            ) {
                Text(
                    time,
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
internal fun BfStreakCard(streak: Int) {
    BfCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier.size(48.dp).background(BfGreen.copy(alpha = .15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(if (streak > 0) "🔥" else "○", style = MaterialTheme.typography.titleLarge)
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("$streak-day streak", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
fun BfCommitmentRow(commitment: DailyCommitment) {
    val (statusLabel, statusColor) = statusPresentation(commitment.completionStatus)
    BfCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(bfDateLabel(commitment.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(commitment.commitment, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                commitment.importance?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                commitment.reflection?.let { reflection ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
                    Text("\"$reflection\"", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

@Composable
private fun statusPresentation(status: CompletionStatus): Pair<String, Color> = when (status) {
    CompletionStatus.Completed -> "Completed" to BfGreen
    CompletionStatus.Missed -> "Missed" to MaterialTheme.colorScheme.error
    CompletionStatus.Pending -> "Pending" to BfAmber
    CompletionStatus.NotReviewed -> "Not reviewed" to BfGray
}

internal fun bfStreak(history: List<DailyCommitment>, today: LocalDate): Int {
    var streak = 0
    var cursor = today
    while (true) {
        val entry = history.firstOrNull { it.date == cursor }
        if (entry?.completionStatus == CompletionStatus.Completed) {
            streak++
            cursor = cursor.minusDays(1)
        } else break
    }
    return streak
}

internal fun bfDateLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault()))
    }
}

private fun bfTimeLabel(hour: Int, minute: Int): String =
    "%02d:%02d".format(hour, minute)
