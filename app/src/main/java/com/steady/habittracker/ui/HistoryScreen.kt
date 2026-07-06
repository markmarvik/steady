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

    Text(
        "Last 14 Days",
        color = Color(0xFF22C55E),
        fontSize = 14.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    LazyColumn {
        items(history) { (date, rate) ->
            val color = when {
                rate >= 0.85 -> Color(0xFF22C55E)
                rate >= 0.5 -> Color(0xFF4ADE80)
                rate > 0 -> Color(0xFF166534)
                else -> Color(0xFF334155)
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    date,
                    color = Color(0xFF94A3B8),
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
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.width(44.dp),
                    textAlign = TextAlign.End
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    Text(
        "Track richer entries (notes, values) on Today. History shows overall rate.",
        color = Color(0xFF475569),
        fontSize = 11.sp
    )
}
