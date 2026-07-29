package com.example.earnitv2

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

private val AnalyticsEarn = Color(0xFF79C957)
private val AnalyticsReward = Color(0xFFE85D91)
private val AnalyticsOther = Color(0xFF858585)
private val AnalyticsBlue = Color(0xFF68B7FF)
private val AnalyticsOrange = Color(0xFFFFA33D)

@Composable
fun AnalyticsScreen(
    range: AnalyticsRange,
    state: AnalyticsUiState,
    insights: List<InsightCandidate>,
    rules: List<EarnItRuleStore.Rule>,
    selectedAppPackage: String?,
    onRangeChange: (AnalyticsRange) -> Unit,
    onOpenApp: (String) -> Unit,
    onBackFromApp: () -> Unit,
    onBack: () -> Unit,
    onCreateRule: () -> Unit,
    onRepairPermission: () -> Unit,
    premiumInsightsEnabled: Boolean = true,
    onPremiumGate: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val summary = (state as? AnalyticsUiState.Ready)?.summary
    val selectedApp = selectedAppPackage?.let { pkg -> summary?.apps?.firstOrNull { it.packageName == pkg } }
    if (selectedApp != null && summary != null) {
        AppAnalyticsDetailScreen(selectedApp, summary, onRangeChange, onBackFromApp, modifier)
        return
    }

    BackHandler(onBack = onBack)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { AnalyticsPageHeader(title = "Analytics", onBack = onBack) }
        item {
            AnalyticsRangeSelector(
                selected = range,
                onSelected = onRangeChange,
                sevenDayEnabled = premiumInsightsEnabled
            )
        }
        when (state) {
            AnalyticsUiState.Loading -> item { AnalyticsLoadingState() }
            AnalyticsUiState.PermissionRequired -> item {
                AnalyticsStateCard(
                    title = "Usage access needed",
                    body = "EarnIt needs usage access to build Analytics.",
                    action = "Fix access",
                    onAction = onRepairPermission
                )
            }
            is AnalyticsUiState.Unavailable -> item {
                AnalyticsStateCard("Analytics unavailable", state.message)
            }
            is AnalyticsUiState.Ready -> {
                val data = state.summary
                if (data.totalSeconds == 0L) {
                    item {
                        AnalyticsStateCard(
                            title = "Not enough history yet",
                            body = if (range == AnalyticsRange.Today) {
                                "Use your phone normally and today’s activity will appear here."
                            } else {
                                "Keep using EarnIt and your 7-day trends will appear here."
                            }
                        )
                    }
                } else {
                    item { AnalyticsOverviewCard(data) }
                }
                item {
                    if (premiumInsightsEnabled) {
                        AnalyticsInsightsSection(insights)
                    } else {
                        LockedAnalyticsInsights(onPremiumGate)
                    }
                }
                if (data.apps.isNotEmpty()) item { MostUsedAppsSection(data.apps, onOpenApp) }
                item { RulePerformanceSection(data.rulePerformance, rules.isEmpty(), onCreateRule) }
                if (data.rewardSeconds > 0L) item { PeakDistractionSection(data) }
            }
        }
    }
}

@Composable
private fun LockedAnalyticsInsights(onPremiumGate: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AnalyticsSectionTitle("Insights")
        Surface(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onPremiumGate),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .55f))
        ) {
            Row(
                Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnalyticsIconBadge("♛", MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text("Unlock weekly insights", fontWeight = FontWeight.SemiBold)
                    Text(
                        "See patterns and progress across your Rules with Pro.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("›", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
internal fun AnalyticsRangeSelector(
    selected: AnalyticsRange,
    onSelected: (AnalyticsRange) -> Unit,
    sevenDayEnabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .38f), RoundedCornerShape(14.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(AnalyticsRange.Today to "Today", AnalyticsRange.SevenDays to "7 Days").forEach { (range, label) ->
            val active = selected == range
            Surface(
                modifier = Modifier.weight(1f).height(38.dp).clickable { onSelected(range) },
                shape = RoundedCornerShape(11.dp),
                color = if (active) AnalyticsEarn.copy(alpha = .16f) else Color.Transparent,
                border = if (active) androidx.compose.foundation.BorderStroke(1.dp, AnalyticsEarn.copy(alpha = .82f)) else null
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (range == AnalyticsRange.SevenDays && !sevenDayEnabled) "$label  ♛ Pro" else label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalyticsOverviewCard(summary: AnalyticsSummary) = AnalyticsCard {
    AnalyticsSectionTitle("Overview")
    Text("Total screen time", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(
        analyticsDuration(summary.totalSeconds),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold
    )
    AnalyticsComparisonLabel(summary.totalSeconds, summary.previousTotalSeconds, summary.range)
    AnalyticsBreakdownBar(summary.earnSeconds, summary.rewardSeconds, summary.otherSeconds)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AnalyticsMetric("Earn Apps", summary.earnSeconds, AnalyticsEarn, Modifier.weight(1f))
        AnalyticsMetric("Reward Apps", summary.rewardSeconds, AnalyticsReward, Modifier.weight(1f))
        AnalyticsMetric("Other Apps", summary.otherSeconds, AnalyticsOther, Modifier.weight(1f))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f))
    Text(
        if (summary.range == AnalyticsRange.SevenDays) "Weekly trend" else "Reward activity today",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )
    if (summary.range == AnalyticsRange.SevenDays) {
        AnalyticsDailyBarChart(summary.dailyUsage)
        AnalyticsLegend()
    } else {
        AnalyticsHourlyBarChart(summary.dailyUsage.firstOrNull()?.hourlyRewardSeconds.orEmpty(), AnalyticsReward)
    }
}

@Composable
private fun AnalyticsInsightsSection(insights: List<InsightCandidate>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AnalyticsSectionTitle("Insights")
        if (insights.isEmpty()) {
            AnalyticsCard {
                Text("Insights will appear as EarnIt learns your patterns.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            insights.take(3).forEach { insight -> InsightRow(insight) }
        }
    }
}

@Composable
private fun InsightRow(insight: InsightCandidate) {
    val color = when (insight.type) {
        InsightType.RewardUsageDecreased, InsightType.EarnUsageIncreased,
        InsightType.ProductiveReplacementPattern, InsightType.MostProductiveDay -> AnalyticsEarn
        InsightType.PeakDistractionWindow, InsightType.RewardUsageIncreased,
        InsightType.MostUsedRewardApp, InsightType.LargestAppIncrease -> AnalyticsReward
        InsightType.ScheduledBlockSuccess -> AnalyticsOrange
        else -> AnalyticsBlue
    }
    val symbol = when (insight.type) {
        InsightType.PeakDistractionWindow -> "◔"
        InsightType.MostProductiveDay -> "☆"
        InsightType.ScheduledBlockSuccess -> "◷"
        else -> "↗"
    }
    AnalyticsCard(contentPadding = 12.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            AnalyticsIconBadge(symbol, color)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(insight.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                insight.supportingText?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MostUsedAppsSection(apps: List<AppUsageSummary>, onOpenApp: (String) -> Unit) {
    AnalyticsCard {
        AnalyticsSectionTitle("Most used apps")
        apps.take(5).forEachIndexed { index, app ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .clickable { onOpenApp(app.packageName) }
                    .semantics { contentDescription = "${app.appName}, ${analyticsDuration(app.totalSeconds)}" },
                verticalAlignment = Alignment.CenterVertically
            ) {
                EarnItAppIcon(app.packageName, app.appName, 36.dp)
                Spacer(Modifier.width(12.dp))
                Text(app.appName, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(analyticsDuration(app.totalSeconds), fontWeight = FontWeight.SemiBold)
                Text("  ›", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun RulePerformanceSection(
    performance: List<RulePerformanceSummary>,
    noRules: Boolean,
    onCreateRule: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AnalyticsSectionTitle("Rule performance")
        if (noRules) {
            AnalyticsCard {
                Box(
                    Modifier.size(52.dp).background(AnalyticsEarn.copy(alpha = .14f), CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text("♧", color = AnalyticsEarn, style = MaterialTheme.typography.headlineSmall) }
                Text("No Rules created yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Create your first Rule to begin tracking your progress.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onCreateRule, modifier = Modifier.fillMaxWidth()) { Text("Create Rule") }
            }
        } else {
            performance.forEach { item -> RulePerformanceCard(item) }
        }
    }
}

@Composable
private fun RulePerformanceCard(item: RulePerformanceSummary) {
    val accent = when (item.type) {
        EarnItRuleStore.RuleType.EarnRewardTime -> AnalyticsEarn
        EarnItRuleStore.RuleType.CompleteToUnlock -> AnalyticsBlue
        EarnItRuleStore.RuleType.ScheduledBlock -> AnalyticsOrange
    }
    AnalyticsCard(contentPadding = 12.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            RuleTypeIcon(item.type, size = 38.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                item.primaryMetric?.let { Text(cleanAnalyticsCopy(it), color = accent, fontWeight = FontWeight.SemiBold) }
                Text(
                    cleanAnalyticsCopy(item.supportingText ?: "More performance data will appear after a few active days."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PeakDistractionSection(summary: AnalyticsSummary) {
    AnalyticsCard {
        AnalyticsSectionTitle("Peak distraction times")
        Text(
            if (summary.range == AnalyticsRange.SevenDays) "Reward App activity by day and time" else "Reward App activity by hour",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (summary.range == AnalyticsRange.SevenDays) AnalyticsHeatmap(summary.dailyUsage)
        else AnalyticsHourlyBarChart(summary.dailyUsage.firstOrNull()?.hourlyRewardSeconds.orEmpty(), AnalyticsReward)
        summary.peakRewardWindow?.let {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .38f)
            ) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    AnalyticsIconBadge("◔", AnalyticsReward)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Most of your Reward App usage happens between ${formatHour(it.startHour)} and ${formatHour(it.endHourExclusive)}.",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Consider a Scheduled Block during this time to protect your focus.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppAnalyticsDetailScreen(
    app: AppUsageSummary,
    summary: AnalyticsSummary,
    onRangeChange: (AnalyticsRange) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val accent = when (app.classification) {
        AnalyticsClassification.Earn -> AnalyticsEarn
        AnalyticsClassification.Reward -> AnalyticsReward
        AnalyticsClassification.Other -> AnalyticsOther
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { AnalyticsAppHeader(app, onBack) }
        item { AnalyticsRangeSelector(summary.range, onSelected = onRangeChange) }
        item {
            AnalyticsCard {
                Text("Time spent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(analyticsDuration(app.totalSeconds), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f))
                Text("Usage over time", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (summary.range == AnalyticsRange.SevenDays) {
                    AnalyticsSingleSeriesDailyChart(app.dailySeconds, summary.period.dates.map { it.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }, accent)
                } else {
                    AnalyticsHourlyBarChart(app.hourlySeconds, accent)
                }
            }
        }
        val peak = detectPeakWindow(app.hourlySeconds)
        if (peak != null || (summary.range == AnalyticsRange.SevenDays && app.highestUsageDayIndex != null)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    peak?.let {
                        CompactMetricCard("Peak usage time", "${formatHour(it.startHour)}–${formatHour(it.endHourExclusive)}", AnalyticsReward, Modifier.weight(1f))
                    }
                    if (summary.range == AnalyticsRange.SevenDays) app.highestUsageDayIndex?.let { index ->
                        CompactMetricCard(
                            "Most active day",
                            summary.period.dates[index].dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                            AnalyticsBlue,
                            Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        item {
            AnalyticsCard {
                Text("About ${app.appName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                AnalyticsAboutRow("Category", when (app.classification) {
                    AnalyticsClassification.Earn -> "Earn App"
                    AnalyticsClassification.Reward -> "Reward App"
                    AnalyticsClassification.Other -> "Other App"
                })
                AnalyticsAboutRow("Days used", "${app.dailySeconds.count { it > 0 }}")
                AnalyticsAboutRow("Average per active day", analyticsDuration(app.totalSeconds / app.dailySeconds.count { it > 0 }.coerceAtLeast(1)))
            }
        }
    }
}

@Composable
private fun AnalyticsPageHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 4.dp)) {
            Text("‹", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AnalyticsAppHeader(app: AppUsageSummary, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 4.dp)) {
            Text("‹", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.width(8.dp))
        EarnItAppIcon(app.packageName, app.appName, 34.dp)
        Spacer(Modifier.width(10.dp))
        Text(app.appName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AnalyticsCard(contentPadding: androidx.compose.ui.unit.Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .8f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun AnalyticsSectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun AnalyticsMetric(label: String, seconds: Long, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
        Text(analyticsDuration(seconds), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AnalyticsComparisonLabel(current: Long, previous: Long?, range: AnalyticsRange) {
    if (previous == null || previous == 0L) {
        Text("Comparison appears when enough history is available.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val delta = current - previous
    val down = delta < 0
    Text(
        "${if (down) "↓" else "↑"} ${analyticsDuration(abs(delta))} from the previous ${if (range == AnalyticsRange.Today) "day" else "7 days"}",
        style = MaterialTheme.typography.bodySmall,
        color = if (down) AnalyticsEarn else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun AnalyticsBreakdownBar(earn: Long, reward: Long, other: Long) {
    val total = (earn + reward + other).coerceAtLeast(1)
    Row(
        Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
            .semantics { contentDescription = "Screen time breakdown: Earn ${analyticsDuration(earn)}, Reward ${analyticsDuration(reward)}, Other ${analyticsDuration(other)}" }
    ) {
        listOf(earn to AnalyticsEarn, reward to AnalyticsReward, other to AnalyticsOther).forEach { (value, color) ->
            if (value > 0) Box(Modifier.weight(value.toFloat() / total).height(8.dp).background(color))
        }
    }
}

@Composable
private fun AnalyticsDailyBarChart(days: List<DailyUsageSummary>) {
    val max = days.maxOfOrNull { it.totalSeconds }?.coerceAtLeast(1) ?: 1
    Row(Modifier.fillMaxWidth().height(132.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.Bottom) {
        days.forEach { day ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Canvas(
                    Modifier.fillMaxWidth().height(100.dp).semantics {
                        contentDescription = "${day.date.dayOfWeek}, ${analyticsDuration(day.totalSeconds)} total"
                    }
                ) {
                    val barWidth = size.width.coerceAtMost(22.dp.toPx())
                    val left = (size.width - barWidth) / 2f
                    var bottom = size.height
                    listOf(day.earnSeconds to AnalyticsEarn, day.rewardSeconds to AnalyticsReward, day.otherSeconds to AnalyticsOther.copy(alpha = .72f)).forEach { (value, color) ->
                        if (value > 0L) {
                            val h = (size.height * value / max.toFloat()).coerceAtLeast(2.dp.toPx())
                            bottom -= h
                            drawRoundRect(color, Offset(left, bottom), Size(barWidth, h), CornerRadius(4.dp.toPx()))
                        }
                    }
                }
                Text(day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AnalyticsSingleSeriesDailyChart(values: List<Long>, labels: List<String>, color: Color) {
    val max = values.maxOrNull()?.coerceAtLeast(1) ?: 1
    Row(Modifier.fillMaxWidth().height(132.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { index, value ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Canvas(Modifier.fillMaxWidth().height(100.dp).semantics { contentDescription = "${labels.getOrNull(index)}, ${analyticsDuration(value)}" }) {
                    val width = size.width.coerceAtMost(22.dp.toPx())
                    val height = if (value == 0L) 0f else (size.height * value / max.toFloat()).coerceAtLeast(3.dp.toPx())
                    drawRoundRect(color, Offset((size.width - width) / 2f, size.height - height), Size(width, height), CornerRadius(5.dp.toPx()))
                }
                Text(labels.getOrNull(index).orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AnalyticsHourlyBarChart(values: List<Long>, color: Color) {
    val padded = List(24) { values.getOrElse(it) { 0L } }
    val max = padded.maxOrNull()?.coerceAtLeast(1) ?: 1
    Canvas(Modifier.fillMaxWidth().height(104.dp).semantics { contentDescription = "Hourly usage chart" }) {
        val gap = 3.dp.toPx()
        val width = ((size.width - gap * 23) / 24).coerceAtLeast(1f)
        padded.forEachIndexed { index, value ->
            val h = if (value == 0L) 0f else (size.height * value / max.toFloat()).coerceAtLeast(3.dp.toPx())
            drawRoundRect(color.copy(alpha = if (value == 0L) 0f else .9f), Offset(index * (width + gap), size.height - h), Size(width, h), CornerRadius(width / 2f))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("12 AM", "12 PM", "11 PM").forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun AnalyticsHeatmap(days: List<DailyUsageSummary>) {
    val buckets = days.map { day -> List(6) { bucket -> day.hourlyRewardSeconds.drop(bucket * 4).take(4).sum() } }
    val max = buckets.flatten().maxOrNull()?.coerceAtLeast(1) ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(34.dp))
            listOf("12A", "4A", "8A", "12P", "4P", "8P").forEach { label ->
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        days.forEachIndexed { dayIndex, day ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), Modifier.width(29.dp), style = MaterialTheme.typography.labelSmall)
                buckets[dayIndex].forEach { value ->
                    val intensity = if (value == 0L) .06f else (.18f + .82f * value / max.toFloat()).coerceIn(.18f, 1f)
                    Box(
                        Modifier.weight(1f).height(22.dp)
                            .background(AnalyticsReward.copy(alpha = intensity), RoundedCornerShape(4.dp))
                            .semantics { contentDescription = "${day.date.dayOfWeek}, ${analyticsDuration(value)} Reward App usage" }
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text("Low", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            (1..5).forEach { Box(Modifier.padding(horizontal = 2.dp).size(12.dp).background(AnalyticsReward.copy(alpha = it / 5f), RoundedCornerShape(3.dp))) }
            Spacer(Modifier.width(6.dp))
            Text("High", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AnalyticsLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf("Earn Apps" to AnalyticsEarn, "Reward Apps" to AnalyticsReward, "Other Apps" to AnalyticsOther).forEach { (label, color) ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(color, CircleShape))
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CompactMetricCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.heightIn(min = 112.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(26.dp).background(color.copy(alpha = .14f), CircleShape))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2)
        }
    }
}

@Composable
private fun AnalyticsAboutRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AnalyticsIconBadge(symbol: String, color: Color) {
    Box(Modifier.size(34.dp).background(color.copy(alpha = .15f), CircleShape), contentAlignment = Alignment.Center) {
        Text(symbol, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AnalyticsLoadingState() {
    AnalyticsCard {
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(24.dp), color = AnalyticsEarn, strokeWidth = 2.dp)
            Column {
                Text("Preparing your analytics", fontWeight = FontWeight.SemiBold)
                Text("Reading local usage history…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AnalyticsStateCard(title: String, body: String, action: String? = null, onAction: (() -> Unit)? = null) {
    AnalyticsCard {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null && onAction != null) Button(onClick = onAction) { Text(action) }
    }
}

private fun cleanAnalyticsCopy(value: String): String = value
    .replace(" Â· ", " · ")
    .replace(" Earn · ", " Earn Apps · ")
    .replace(" Reward", " Reward Apps")
    .replace("Your Rule is active. Performance will appear after EarnIt collects enough history.", "More performance data will appear after a few active days.")
