package com.steady.habittracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.steady.habittracker.data.AppData
import com.steady.habittracker.data.HabitDomain
import com.steady.habittracker.data.HabitType
import com.steady.habittracker.data.TagIds
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

/**
 * History inspired by Anki stats: heatmap, multi charts, clear summary cards.
 */
@Composable
fun HistoryScreen(appData: AppData) {
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
    val accent = MaterialTheme.colorScheme.primary

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "History & Stats",
                color = accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
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
                        val name = habit?.name ?: habitId
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
