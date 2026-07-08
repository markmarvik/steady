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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.steady.habittracker.data.AppData
import com.steady.habittracker.data.HabitType
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.*

@Composable
fun HistoryScreen(appData: AppData) {
    // Build last 30 days for the graph (rich graphical view)
    val graphData = computeLastNDaysDetailed(appData, 30)

    // All dates that have any stored entries, newest first (full data access)
    val allDatesSorted = appData.entries.keys.sortedDescending()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "History & Trends",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // ========== GRAPHICAL REPRESENTATION ==========
        Text(
            "Completion over last ${graphData.size} days",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(bottom = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            CompletionChart(
                data = graphData,
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
        // Date range under chart (graphical context)
        if (graphData.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(graphData.first().first, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(graphData.last().first, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Quick stats
        val totalLoggedDays = allDatesSorted.size
        val totalEntries = appData.entries.values.sumOf { it.size }
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "$totalLoggedDays days with data",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
            Text(
                "$totalEntries total logs",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }

        // ========== FULL DATA LIST - ALL STORED VALUES ==========
        Text(
            "All stored data (precise values & notes)",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        if (allDatesSorted.isEmpty()) {
            Text(
                "No entries yet. Log habits in the Today tab to see full history here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(8.dp)
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(allDatesSorted, key = { it }) { date ->
                val dayMap = appData.entries[date] ?: emptyMap()
                if (dayMap.isEmpty()) return@items

                // Date header
                Text(
                    formatDateHeader(date),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp)
                )

                // All entries for this exact day with precise values
                dayMap.forEach { (habitId, entry) ->
                    val habit = appData.habits.find { it.id == habitId }
                    val name = habit?.name ?: habitId
                    val unit = habit?.unit ?: ""

                    val valueStr = when {
                        entry.skipped -> "skipped"
                        entry.value >= 0.5 && habit?.type == HabitType.CHECKBOX -> "done"
                        else -> "${entry.value}${if (unit.isNotBlank()) " $unit" else ""}"
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (entry.skipped)
                                MaterialTheme.colorScheme.surfaceVariant
                            else
                                MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    name,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    valueStr,
                                    color = if (entry.skipped) Color(0xFFF87171) else MaterialTheme.colorScheme.primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (entry.note.isNotBlank()) {
                                Text(
                                    "“${entry.note}”",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            if (entry.loggedAt > 0) {
                                val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(entry.loggedAt))
                                Text(
                                    timeStr,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "All historical values and notes are shown above. Tap Today tab to edit or add backfill entries.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
    }
}

/** Compute last N days of completion rates. Returns oldest -> newest. */
private fun computeLastNDaysDetailed(appData: AppData, n: Int): List<Pair<String, Float>> {
    val result = mutableListOf<Pair<String, Float>>()
    var d = LocalDate.now()
    repeat(n) {
        val key = d.toString()
        val dayMap = appData.entries[key] ?: emptyMap()
        val done = dayMap.count { (_, e) -> e.value >= 0.5 }
        val total = appData.habits.size.coerceAtLeast(1)
        val rate = if (total > 0) done.toFloat() / total else 0f
        result.add(key to rate)
        d = d.minusDays(1)
    }
    return result.reversed()
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

/** Nice graphical bar + line chart for completion history. */
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

        // Draw horizontal grid lines (0%, 50%, 100%)
        for (pct in listOf(0f, 0.5f, 1f)) {
            val y = h - (pct * h)
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f
            )
        }

        // Draw bars
        data.forEachIndexed { i, (_, rate) ->
            val barH = rate.coerceIn(0f, maxRate) * h
            val x = spacing + i * (barWidth + spacing)
            val left = x
            val top = h - barH

            // Bar
            drawRoundRect(
                color = barColor.copy(alpha = if (rate >= 0.5f) 0.9f else 0.55f),
                topLeft = Offset(left, top),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(3.dp.toPx())
            )
        }

        // Draw connecting line + dots for trend
        val path = Path()
        data.forEachIndexed { i, (_, rate) ->
            val xCenter = spacing + i * (barWidth + spacing) + barWidth / 2
            val y = h - (rate.coerceIn(0f, maxRate) * h)

            if (i == 0) {
                path.moveTo(xCenter, y)
            } else {
                path.lineTo(xCenter, y)
            }

            // Dot
            drawCircle(
                color = lineColor,
                radius = 3.5.dp.toPx(),
                center = Offset(xCenter, y)
            )
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )

        // (X labels intentionally omitted from canvas to avoid nativeCanvas dependency issues;
        // the bar + line trend itself provides the required graphical representation.)
    }
}
