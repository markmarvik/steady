package com.steady.habittracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import com.steady.habittracker.data.AppData
import com.steady.habittracker.data.DisplayIcon
import com.steady.habittracker.data.HabitDomain
import com.steady.habittracker.data.HabitType
import com.steady.habittracker.data.SleepNightSession
import com.steady.habittracker.data.TagIds
import com.steady.habittracker.data.WorkoutSession
import com.steady.habittracker.sensors.ScreenTimeReader
import com.steady.habittracker.util.GrokContextBuilder
import com.steady.habittracker.util.HabitSquareMetric
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

/**
 * History inspired by Anki stats: heatmap, multi charts, clear summary cards.
 */
@Composable
fun HistoryScreen(
    appData: AppData,
    onOpenSleepNight: (SleepNightSession) -> Unit = {},
    onAskGrok: () -> Unit = {}
) {
    val graphData = remember(appData) { HabitDomain.computeLastNDays(appData, 30) }
    val logCounts = remember(appData) { HabitDomain.computeLastNDaysLogCounts(appData, 30) }
    val heatmap = remember(appData) { HabitDomain.computeHeatmap(appData, weeks = 16) }
    val hourly = remember(appData) { HabitDomain.computeHourlyLogCounts(appData) }
    val streak = remember(appData) { HabitDomain.computeStreak(appData) }
    val daysActive = remember(appData) { HabitDomain.daysWithActivity(appData) }
    val totalDone = remember(appData) { HabitDomain.totalCompletedLogs(appData) }
    val avg30 = remember(graphData) {
        if (graphData.isEmpty()) 0f else graphData.map { it.second }.average().toFloat()
    }
    val activeTags = remember(appData) { HabitDomain.getActiveTags(appData) }
    val allDatesSorted = remember(appData) { appData.entries.keys.sortedDescending() }
    val recentWorkouts = remember(appData) { HabitDomain.recentWorkoutSessions(appData, 20) }
    val workoutDays7 = remember(appData) { HabitDomain.workoutDaysInWindow(appData, 7) }
    val accent = MaterialTheme.colorScheme.primary
    var expandedSessionId by remember { mutableStateOf<String?>(null) }
    var screenUsageExpanded by remember { mutableStateOf(false) }
    var habitsExpanded by remember { mutableStateOf(true) }
    val sleepNights = remember(appData.sleepNights) {
        appData.sleepNights.sortedByDescending { it.startedAt }.take(10)
    }
    val habitSquares = remember(appData) { GrokContextBuilder.habitSquareMetrics(appData) }
    val context = LocalContext.current
    val hasScreenUsage = remember(appData) { GrokContextBuilder.hasScreenUsageBlock(appData) }
    var screenWindowDays by remember { mutableStateOf(7) }
    val liveScreen = remember(hasScreenUsage, appData.habits) {
        if (!hasScreenUsage || !ScreenTimeReader.hasUsageAccess(context)) {
            emptyMap()
        } else {
            val map = mutableMapOf<String, Long?>()
            var d = LocalDate.now()
            // Pull live UsageStats for last 30 days so History isn't empty until a manual log
            repeat(30) {
                map[d.toString()] = ScreenTimeReader.screenOnMinutes(context, d)
                d = d.minusDays(1)
            }
            map
        }
    }
    val screenDaily = remember(appData, liveScreen) {
        GrokContextBuilder.dailyScreenMinutes(appData, days = 30, liveMinutes = liveScreen)
    }
    val screenWindowSlice = remember(screenDaily, screenWindowDays) {
        screenDaily.takeLast(screenWindowDays)
    }
    val screenTotal = remember(screenWindowSlice) {
        GrokContextBuilder.screenTotalMinutes(screenWindowSlice, screenWindowDays)
    }
    val screenAvg = remember(screenWindowSlice, screenWindowDays) {
        GrokContextBuilder.screenAvgMinutes(screenWindowSlice, screenWindowDays)
    }
    val screenLimit = remember(appData) { HabitDomain.screenDailyLimitMinutes(appData) }
    val todayScreenPenalty = remember(appData, liveScreen) {
        val today = LocalDate.now().toString()
        val live = liveScreen[today]
        HabitDomain.screenOveragePenalty(appData, today, minutesOverride = live)
    }
    val showWearableFrames = remember(appData) {
        appData.gadgetbridgePrefs.showHistoryFrames &&
            (appData.gadgetbridgePrefs.enabled ||
                appData.habits.any {
                    !it.archived &&
                        it.extensionType == com.steady.habittracker.data.ExtensionType.GADGETBRIDGE_SYNC
                } ||
                appData.wearableDays.isNotEmpty())
    }
    val wearableRecent = remember(appData.wearableDays) {
        appData.wearableDays.sortedByDescending { it.date }.take(28)
    }
    var wearableExpanded by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "History & Stats",
                    color = accent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = onAskGrok) {
                    Text("Chat with Grok", fontWeight = FontWeight.SemiBold, color = accent)
                }
            }
        }

        // Sleep audio nights
        if (sleepNights.isNotEmpty()) {
            item {
                SectionTitle("Sleep audio")
            }
            items(sleepNights, key = { it.id }) { night ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSleepNight(night) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Night → ${night.wakeDate}",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Quiet ${night.quietScore}/100 · ${night.eventCount} events · " +
                                "${night.snoreLikeCount} snore-like · ${"%.1f".format(night.loudMinutes)} loud min",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (night.completed) "Tap for events & playback · ${night.codec}"
                            else "In progress… · ${night.codec}",
                            fontSize = 11.sp,
                            color = accent
                        )
                    }
                }
            }
        }

        // Workout sessions (#22)
        if (recentWorkouts.isNotEmpty()) {
            item {
                Text(
                    "Workouts · $workoutDays7/7 exercise days",
                    color = accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(recentWorkouts.take(12), key = { it.id }) { session ->
                val routine = appData.routines.find { it.id == session.routineId }
                WorkoutSessionHistoryCard(
                    session = session,
                    routineName = routine?.name ?: session.routineId,
                    exerciseNames = routine?.exercises?.associate { it.id to it.name } ?: emptyMap(),
                    expanded = expandedSessionId == session.id,
                    onToggle = {
                        expandedSessionId =
                            if (expandedSessionId == session.id) null else session.id
                    },
                    accent = accent
                )
            }
        }

        // —— Summary strip (Anki-style big numbers) ——
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard("Streak", "$streak", "day${if (streak == 1) "" else "s"}", Modifier.weight(1f), accent)
                StatCard("Active", "$daysActive", dayWord(daysActive), Modifier.weight(1f), accent)
                StatCard("Done", "$totalDone", "logs", Modifier.weight(1f), accent)
                StatCard("30d avg", "${(avg30 * 100).toInt()}%", "complete", Modifier.weight(1f), accent)
            }
        }

        // —— #42 Habits as variable-size sorted squares ——
        if (habitSquares.isNotEmpty()) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { habitsExpanded = !habitsExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle("Habits · by 30d completion")
                    Text(
                        if (habitsExpanded) "Hide" else "Show",
                        color = accent,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }
            if (habitsExpanded) {
                item {
                    Text(
                        "Larger squares = higher 30-day due-day completion. Sorted strongest first.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    HabitSquareGrid(metrics = habitSquares, accent = accent)
                }
            }
        }

        // —— Wearable (Gadgetbridge) frames: steps / sleep / HR ——
        if (showWearableFrames) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { wearableExpanded = !wearableExpanded },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Wearables · steps · sleep · HR",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = accent
                            )
                            Text(
                                if (wearableExpanded) "Collapse" else "Expand",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val latest = wearableRecent.firstOrNull()
                        if (latest == null) {
                            Text(
                                "No Gadgetbridge data yet. Enable the block and set the export path in Manage → Blocks.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                "Latest ${latest.date}: " +
                                    listOfNotNull(
                                        latest.steps?.let { "$it steps" },
                                        latest.sleepMinutes?.let { "${it / 60}h ${it % 60}m sleep" }
                                    ).joinToString(" · "),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            // HR summary for latest day: min / avg / max
                            if (latest.minHeartRate != null || latest.avgHeartRate != null ||
                                latest.maxHeartRate != null
                            ) {
                                Spacer(Modifier.height(4.dp))
                                HeartRateDayStrip(
                                    minBpm = latest.minHeartRate,
                                    avgBpm = latest.avgHeartRate,
                                    maxBpm = latest.maxHeartRate,
                                    restingBpm = latest.restingHeartRate,
                                    accent = accent
                                )
                            }
                            WearableMetricHeatstrip(
                                values = wearableRecent.asReversed().map { it.date to (it.steps?.toFloat()) },
                                accent = accent,
                                empty = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                            )
                            if (wearableExpanded) {
                                Text(
                                    "Heart rate · min / avg / max (bpm)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                wearableRecent.take(14).forEach { d ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Column(
                                            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    d.date,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    listOfNotNull(
                                                        d.steps?.let { "$it steps" },
                                                        d.sleepMinutes?.let {
                                                            "${it / 60}h ${it % 60}m sleep"
                                                        }
                                                    ).joinToString(" · ").ifBlank { "—" },
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            HeartRateDayStrip(
                                                minBpm = d.minHeartRate,
                                                avgBpm = d.avgHeartRate,
                                                maxBpm = d.maxHeartRate,
                                                restingBpm = d.restingHeartRate,
                                                accent = accent
                                            )
                                        }
                                    }
                                }
                                val sleepVals = wearableRecent.asReversed().map {
                                    it.date to it.sleepMinutes?.toFloat()
                                }
                                if (sleepVals.any { it.second != null }) {
                                    Text(
                                        "Sleep (minutes)",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    WearableMetricHeatstrip(
                                        values = sleepVals,
                                        accent = Color(0xFF60A5FA),
                                        empty = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(22.dp)
                                    )
                                }
                                val hrAvgVals = wearableRecent.asReversed().map {
                                    it.date to it.avgHeartRate?.toFloat()
                                }
                                if (hrAvgVals.any { it.second != null }) {
                                    Text(
                                        "Avg HR trend",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    WearableMetricHeatstrip(
                                        values = hrAvgVals,
                                        accent = Color(0xFFF87171),
                                        empty = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // —— #43 Screen usage expandable (live UsageStats + snapshots; 3 / 7 / 30d) ——
        if (hasScreenUsage) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { screenUsageExpanded = !screenUsageExpanded },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Screen usage",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = accent
                            )
                            Text(
                                if (screenUsageExpanded) "Collapse" else "Expand",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(3, 7, 30).forEach { d ->
                                FilterChip(
                                    selected = screenWindowDays == d,
                                    onClick = { screenWindowDays = d },
                                    label = { Text("${d}d", fontSize = 11.sp) }
                                )
                            }
                        }
                        val totalLabel = formatScreenMinutes(screenTotal)
                        val avgLabel = screenAvg?.let { avg ->
                            formatScreenMinutes(avg.toLong()) + " avg/day"
                        } ?: "No samples yet"
                        Text(
                            "Last ${screenWindowDays}d · $totalLabel total · $avgLabel",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        screenLimit?.let { lim ->
                            val penNote = if (todayScreenPenalty > 0) {
                                " · today −$todayScreenPenalty pts over limit"
                            } else {
                                " · under limit today"
                            }
                            Text(
                                "Soft limit ${lim}m/day · −${HabitDomain.SCREEN_OVERAGE_PER_15MIN} pts / 15m over (cap ${HabitDomain.SCREEN_OVERAGE_CAP})$penNote",
                                fontSize = 10.sp,
                                color = if (todayScreenPenalty > 0) Color(0xFFF87171)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        ScreenUsageHeatstrip(
                            daily = screenWindowSlice.map { (date, min) -> date to min },
                            accent = accent,
                            empty = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                        )
                        if (screenUsageExpanded) {
                            Text(
                                "Daily totals (newest first)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            screenWindowSlice.asReversed().forEach { (date, min) ->
                                val time = when {
                                    min == null -> "—"
                                    min < 0 -> "n/a"
                                    else -> formatScreenMinutes(min)
                                }
                                val over = screenLimit != null && min != null && min > screenLimit
                                val pen = if (over) {
                                    HabitDomain.screenOveragePenalty(appData, date, minutesOverride = min)
                                } else 0
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(date, fontSize = 12.sp)
                                    Text(
                                        if (pen > 0) "$time · −$pen pts" else time,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (pen > 0) Color(0xFFF87171) else accent
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // —— Steady Momentum ——
        item {
            val todayPts = remember(appData) {
                HabitDomain.computeDayPoints(appData, HabitDomain.getToday())
            }
            val lifetime = remember(appData) { HabitDomain.effectiveLifetimePoints(appData) }
            val level = remember(lifetime) { HabitDomain.computeLevel(lifetime) }
            val title = HabitDomain.levelTitle(level)
            val best = remember(appData) { HabitDomain.bestDayScore(appData) }
            val pointSeries = remember(appData) { HabitDomain.lastNDayPoints(appData, 30) }
            SectionTitle("Momentum")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatCard("Level", "$level", title, Modifier.weight(1f), accent)
                        StatCard("Today", "$todayPts", "pts", Modifier.weight(1f), accent)
                        StatCard("Lifetime", "$lifetime", "pts", Modifier.weight(1f), accent)
                        StatCard(
                            "Best day",
                            "${best?.points ?: 0}",
                            best?.date?.takeLast(5) ?: "—",
                            Modifier.weight(1f),
                            accent
                        )
                    }
                    Text(
                        "Last 30 days · Steady points",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    PointsChart(
                        data = pointSeries,
                        accent = accent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                    )
                    Text(
                        "${HabitDomain.pointsToNextLevel(lifetime)} pts to level ${level + 1}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // —— Calendar heatmap ——
        item {
            SectionTitle("Activity calendar")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    HeatmapChart(
                        columns = heatmap,
                        accent = accent,
                        empty = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Less", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        listOf(0.15f, 0.4f, 0.7f, 1f).forEach { a ->
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .padding(1.dp)
                                    .background(accent.copy(alpha = a), RoundedCornerShape(2.dp))
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Text("More", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // —— Daily completion % ——
        item {
            SectionTitle("Daily completion · last ${graphData.size} ${dayWord(graphData.size)}")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                CompletionChart(
                    data = graphData,
                    accent = accent,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
            if (graphData.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(graphData.first().first.takeLast(5), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(graphData.last().first.takeLast(5), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // —— Logs per day (volume, like Anki reviews/day) ——
        item {
            SectionTitle("Logs per day")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                CountBarChart(
                    values = logCounts.map { it.second },
                    accent = accent,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }

        // —— Hour of day (Anki hourly breakdown) ——
        item {
            val maxH = hourly.maxOrNull() ?: 0
            if (maxH > 0) {
                SectionTitle("When you log (hour of day)")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    CountBarChart(
                        values = hourly.toList(),
                        accent = accent.copy(alpha = 0.9f),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("00", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("06", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("12", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("18", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("23", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // —— By tag (category bars) ——
        if (activeTags.isNotEmpty()) {
            item {
                SectionTitle("By tag")
            }
            items(activeTags, key = { it.id }) { tag ->
                val avg7 = HabitDomain.computeTag7DayAvg(appData, tag.id)
                val todayRate = HabitDomain.computeTagDayCompletion(appData, tag.id)
                val dueToday = HabitDomain.habitsDueOn(appData).count { h ->
                    tag.id in h.tags || (tag.id == TagIds.SUPPLEMENTS && h.isSupplement)
                }
                if (dueToday == 0 && avg7 == 0f) return@items
                val barColor = tag.color?.let { Color(it) } ?: accent
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(tag.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(
                                "today ${(todayRate * 100).toInt()}% · 7d ${(avg7 * 100).toInt()}%",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(avg7.coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .background(barColor, RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }
            }
        }

        // —— Recent log list ——
        item {
            SectionTitle("Recent logs")
        }
        if (allDatesSorted.isEmpty()) {
            item {
                Text(
                    "No entries yet. Log habits in Today to build your charts.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(8.dp)
                )
            }
        } else {
            items(allDatesSorted.take(14), key = { it }) { date ->
                val dayMap = appData.entries[date] ?: emptyMap()
                if (dayMap.isEmpty()) return@items
                Column {
                    Text(
                        formatDateHeader(date),
                        color = accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp)
                    )
                    dayMap.forEach { (habitId, entry) ->
                        val habit = appData.habits.find { it.id == habitId }
                        val name = habit?.let {
                            com.steady.habittracker.data.DisplayIcon.label(it.icon, it.name)
                        } ?: habitId
                        val unit = habit?.unit ?: ""
                        val tagStr = habit?.let { HabitDomain.tagNamesForHabit(appData, it).joinToString(", ") }.orEmpty()
                        val valueStr = when {
                            entry.skipped -> "skipped"
                            entry.value >= 0.5 && habit?.type == HabitType.CHECKBOX -> "done"
                            else -> "${entry.value}${if (unit.isNotBlank()) " $unit" else ""}"
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (entry.skipped)
                                    MaterialTheme.colorScheme.surfaceVariant
                                else
                                    MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    if (tagStr.isNotBlank()) {
                                        Text(tagStr, fontSize = 10.sp, color = accent.copy(alpha = 0.85f))
                                    }
                                    if (entry.note.isNotBlank()) {
                                        Text("“${entry.note}”", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Text(
                                    valueStr,
                                    color = if (entry.skipped) Color(0xFFF87171) else accent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

private fun dayWord(n: Int): String = if (n == 1) "day" else "days"

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    accent: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, color = accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            Text(unit, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
        }
    }
}

@Composable
private fun HeatmapChart(
    columns: List<List<Pair<String, Float?>>>,
    accent: Color,
    empty: Color,
    modifier: Modifier = Modifier
) {
    if (columns.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("No data", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        return
    }
    Canvas(modifier = modifier) {
        val cols = columns.size
        val rows = 7
        val gap = 2.dp.toPx()
        val cellW = ((size.width - gap * (cols - 1)) / cols).coerceAtLeast(2f)
        val cellH = ((size.height - gap * (rows - 1)) / rows).coerceAtLeast(2f)
        columns.forEachIndexed { ci, week ->
            week.forEachIndexed { ri, (_, rate) ->
                val x = ci * (cellW + gap)
                val y = ri * (cellH + gap)
                val color = when {
                    rate == null -> empty.copy(alpha = 0.35f)
                    rate <= 0f -> empty.copy(alpha = 0.55f)
                    else -> accent.copy(alpha = (0.25f + rate * 0.75f).coerceIn(0.25f, 1f))
                }
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, y),
                    size = Size(cellW, cellH),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
            }
        }
    }
}

@Composable
private fun CountBarChart(
    values: List<Int>,
    accent: Color,
    modifier: Modifier = Modifier
) {
    if (values.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("No data", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        return
    }
    val maxV = (values.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
    val grid = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val n = values.size
        val barW = w / (n * 1.4f)
        val spacing = (w - barW * n) / (n + 1)
        drawLine(grid, Offset(0f, h), Offset(w, h), 1f)
        values.forEachIndexed { i, v ->
            val barH = (v / maxV) * h * 0.95f
            val x = spacing + i * (barW + spacing)
            drawRoundRect(
                color = accent.copy(alpha = if (v > 0) 0.85f else 0.2f),
                topLeft = Offset(x, h - barH),
                size = Size(barW, barH.coerceAtLeast(1f)),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
        }
    }
}

private fun formatDateHeader(yyyyMmDd: String): String {
    return try {
        val sdfIn = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sdfOut = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
        sdfOut.format(sdfIn.parse(yyyyMmDd) ?: Date())
    } catch (_: Exception) {
        yyyyMmDd
    }
}

@Composable
private fun WorkoutSessionHistoryCard(
    session: WorkoutSession,
    routineName: String,
    exerciseNames: Map<String, String>,
    expanded: Boolean,
    onToggle: () -> Unit,
    accent: Color
) {
    val sets = HabitDomain.sessionSetCount(session)
    val exCount = HabitDomain.sessionExerciseCount(session)
    val dur = session.totalDurationMin?.let { "$it min" } ?: "—"
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(routineName, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        "${session.date} · $dur · $exCount ex · $sets sets" +
                            if (session.completed) "" else " · incomplete",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                TextButton(onClick = onToggle) {
                    Text(if (expanded) "Hide" else "Detail", color = accent, fontSize = 12.sp)
                }
            }
            if (expanded) {
                if (session.overallNote.isNotBlank()) {
                    Text("“${session.overallNote}”", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                session.performedExercises.forEach { (exId, setLogs) ->
                    if (setLogs.isEmpty()) return@forEach
                    val label = exerciseNames[exId] ?: exId
                    Text(
                        "$label — " + setLogs.joinToString { s ->
                            "S${s.setNumber}:${s.actualReps ?: "?"}r" +
                                (s.weightKg?.let { " ${it}kg" } ?: "")
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompletionChart(
    data: List<Pair<String, Float>>,
    accent: Color,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No data for chart", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        return
    }

    val maxRate = 1f
    val barColor = accent
    val lineColor = accent.copy(alpha = 0.85f)
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val n = data.size
        if (n == 0) return@Canvas

        val barWidth = w / (n * 1.6f)
        val spacing = (w - barWidth * n) / (n + 1)

        for (pct in listOf(0f, 0.5f, 1f)) {
            val y = h - (pct * h)
            drawLine(gridColor, Offset(0f, y), Offset(w, y), 1f)
        }

        data.forEachIndexed { i, (_, rate) ->
            val barH = rate.coerceIn(0f, maxRate) * h
            val x = spacing + i * (barWidth + spacing)
            drawRoundRect(
                color = barColor.copy(alpha = if (rate >= 0.5f) 0.9f else 0.45f),
                topLeft = Offset(x, h - barH),
                size = Size(barWidth, barH.coerceAtLeast(1f)),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
        }

        val path = Path()
        data.forEachIndexed { i, (_, rate) ->
            val xCenter = spacing + i * (barWidth + spacing) + barWidth / 2
            val y = h - (rate.coerceIn(0f, maxRate) * h)
            if (i == 0) path.moveTo(xCenter, y) else path.lineTo(xCenter, y)
            drawCircle(lineColor, 2.5.dp.toPx(), Offset(xCenter, y))
        }
        drawPath(path, lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun PointsChart(
    data: List<Pair<String, Int>>,
    accent: Color,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No points yet", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        return
    }
    val maxPts = (data.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1).toFloat()
    val barColor = accent
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val n = data.size
        if (n == 0) return@Canvas

        val barWidth = w / (n * 1.6f)
        val spacing = (w - barWidth * n) / (n + 1)

        for (pct in listOf(0f, 0.5f, 1f)) {
            val y = h - (pct * h)
            drawLine(gridColor, Offset(0f, y), Offset(w, y), 1f)
        }

        data.forEachIndexed { i, (_, pts) ->
            val rate = (pts / maxPts).coerceIn(0f, 1f)
            val barH = rate * h
            val x = spacing + i * (barWidth + spacing)
            drawRoundRect(
                color = barColor.copy(alpha = if (pts > 0) 0.9f else 0.25f),
                topLeft = Offset(x, h - barH),
                size = Size(barWidth, barH.coerceAtLeast(1f)),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
        }
    }
}

/** #42 Variable-size habit squares sorted by 30d completion (larger = better). */
@Composable
private fun HabitSquareGrid(
    metrics: List<HabitSquareMetric>,
    accent: Color
) {
    val maxScore = metrics.maxOfOrNull { it.score }?.coerceAtLeast(0.01f) ?: 1f
    // Flow-style wrap: 4 columns of equal base, scale 0.72–1.0 of cell
    val columns = 4
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        metrics.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                row.forEach { m ->
                    val scale = (0.62f + 0.38f * (m.score / maxScore)).coerceIn(0.62f, 1f)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f / scale)
                            .background(
                                accent.copy(alpha = 0.12f + m.score * 0.55f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val glyph = DisplayIcon.glyph(m.habit.icon, m.habit.name)
                            Text(
                                glyph.take(2),
                                fontSize = if (scale > 0.85f) 18.sp else 14.sp
                            )
                            Text(
                                m.habit.name,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "${(m.avg30 * 100).toInt()}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = accent
                            )
                        }
                    }
                }
                repeat(columns - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private fun formatScreenMinutes(min: Long): String {
    if (min < 0) return "n/a"
    // Never display >24h for a single day (guards bad OS buckets)
    val capped = min.coerceAtMost(24 * 60L)
    val h = capped / 60
    val m = capped % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

/** Min / avg / max heart-rate strip for one day. */
@Composable
private fun HeartRateDayStrip(
    minBpm: Int?,
    avgBpm: Int?,
    maxBpm: Int?,
    restingBpm: Int? = null,
    accent: Color
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            HrStatChip("Min", minBpm, Color(0xFF60A5FA), surface, Modifier.weight(1f))
            HrStatChip("Avg", avgBpm, accent, surface, Modifier.weight(1f))
            HrStatChip("Max", maxBpm, Color(0xFFF87171), surface, Modifier.weight(1f))
        }
        if (restingBpm != null) {
            Text(
                "Resting ~$restingBpm bpm",
                fontSize = 10.sp,
                color = muted
            )
        }
    }
}

@Composable
private fun HrStatChip(
    label: String,
    bpm: Int?,
    color: Color,
    surface: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(surface, RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            bpm?.toString() ?: "—",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = if (bpm != null) color else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("bpm", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** #43 Compact screen-time intensity strip for the selected window. */
@Composable
private fun ScreenUsageHeatstrip(
    daily: List<Pair<String, Long?>>,
    accent: Color,
    empty: Color,
    modifier: Modifier = Modifier
) {
    val maxMin = daily.mapNotNull { it.second }.filter { it > 0 }.maxOrNull()?.toFloat() ?: 1f
    Canvas(modifier = modifier) {
        val n = daily.size.coerceAtLeast(1)
        val gap = 2.dp.toPx()
        val cellW = ((size.width - gap * (n - 1)) / n).coerceAtLeast(2f)
        val cellH = size.height
        daily.forEachIndexed { i, (_, min) ->
            val x = i * (cellW + gap)
            val color = when {
                min == null || min < 0 -> empty.copy(alpha = 0.4f)
                min == 0L -> empty.copy(alpha = 0.55f)
                else -> accent.copy(alpha = (0.25f + (min / maxMin) * 0.75f).coerceIn(0.25f, 1f))
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(x, 0f),
                size = Size(cellW, cellH),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
        }
    }
}

/** Compact heatstrip for wearable metrics (steps / sleep / HR series). */
@Composable
private fun WearableMetricHeatstrip(
    values: List<Pair<String, Float?>>,
    accent: Color,
    empty: Color,
    modifier: Modifier = Modifier
) {
    val maxV = values.mapNotNull { it.second }.filter { it > 0f }.maxOrNull() ?: 1f
    Canvas(modifier = modifier) {
        val n = values.size.coerceAtLeast(1)
        val gap = 2.dp.toPx()
        val cellW = ((size.width - gap * (n - 1)) / n).coerceAtLeast(2f)
        val cellH = size.height
        values.forEachIndexed { i, (_, v) ->
            val x = i * (cellW + gap)
            val color = when {
                v == null -> empty.copy(alpha = 0.4f)
                v <= 0f -> empty.copy(alpha = 0.55f)
                else -> accent.copy(alpha = (0.25f + (v / maxV) * 0.75f).coerceIn(0.25f, 1f))
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(x, 0f),
                size = Size(cellW, cellH),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
        }
    }
}
