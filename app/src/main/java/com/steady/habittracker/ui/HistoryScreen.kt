package com.steady.habittracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.steady.habittracker.data.AppData
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(appData: AppData) {
    val calendar = Calendar.getInstance()
    val history = mutableListOf<Pair<String, Float>>()
    
    repeat(14) { i ->
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
        val dayMap = appData.entries[date] ?: emptyMap()
        val completed = dayMap.count { (_, e) -> e.value >= 0.5 }
        val total = appData.habits.size.coerceAtLeast(1)
        val rate = completed.toFloat() / total
        history.add(date to rate)
        calendar.add(Calendar.DAY_OF_YEAR, -1)
    }
    history.reverse()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Last 14 Days",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(history) { (date, rate) ->
                val color = when {
                    rate >= 0.85 -> MaterialTheme.colorScheme.primary
                    rate >= 0.5 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    rate > 0 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        date,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.width(90.dp)
                    )

                    Box(
                        modifier = Modifier
                            .height(7.dp)
                            .weight(1f)
                            .clip(RoundedCornerShape(3.dp))
                            .background(color)
                    )

                    Text(
                        "${(rate * 100).toInt()}%",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        modifier = Modifier.width(44.dp),
                        textAlign = TextAlign.End
                    )
                }

                // Minimal enhancement for #6: show a few logged items for the day (name + value)
                val dayEntries = appData.entries[date] ?: emptyMap()
                if (dayEntries.isNotEmpty()) {
                    val logged = dayEntries.entries.take(3).joinToString(", ") { (hid, e) ->
                        val h = appData.habits.find { it.id == hid }?.name ?: hid
                        val v = if (e.value >= 0.5) (if (e.skipped) "skip" else "${e.value}".trimEnd('0','.')) else ""
                        "$h${if (v.isNotBlank()) "=$v" else ""}"
                    }
                    Text("  " + logged, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, modifier = Modifier.padding(start = 92.dp))
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Tap Today to edit notes/values per day. Heatmap planned (#13).",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
    }
}
