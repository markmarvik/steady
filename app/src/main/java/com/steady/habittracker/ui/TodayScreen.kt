package com.steady.habittracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.steady.habittracker.data.AppData
import com.steady.habittracker.data.Habit
import com.steady.habittracker.data.HabitEntry
import com.steady.habittracker.data.HabitType
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TodayScreen(
    appData: AppData,
    todayEntries: Map<String, HabitEntry>,
    onToggle: (String) -> Unit,
    onLogEntry: (habitId: String, value: Double, note: String) -> Unit,  // direct for some cases
    onRequestLog: (Habit) -> Unit = { h -> onLogEntry(h.id, 1.0, "") },  // preferred for dialog popup + keyboard
    onSkip: (String) -> Unit,
    onShowSkipPrompt: (habitId: String) -> Unit = {}
) {
    if (appData.habits.isEmpty() || appData.groups.isEmpty()) {
        Text("No habits yet. Add via Manage tab!", color = Color.Gray, modifier = Modifier.padding(16.dp))
        return
    }

    val grouped = appData.groups.filter { !it.archived }.sortedBy { it.order }.map { g ->
        g to appData.habits.filter { it.groupId == g.id && !it.archived }.sortedBy { it.order }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        grouped.forEach { (group, habits) ->
            item {
                Text(
                    group.name.uppercase(),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp)
                )
            }
            items(habits) { habit ->
                val entry = todayEntries[habit.id]
                val isDone = (entry?.value ?: 0.0) >= 0.5
                val isSkipped = entry?.skipped == true
                HabitRow(
                    habit = habit,
                    entry = entry,
                    isDone = isDone,
                    isSkipped = isSkipped,
                    onToggle = { onToggle(habit.id) },
                    onLog = { onRequestLog(habit) },
                    onSkip = {
                        onSkip(habit.id)
                        if (!habit.canSkip) {
                            // hygiene - still called but caller may have warned
                        } else if (/* caller decides */ false) {
                            // prompt handled in parent
                        }
                    },
                    showSkipPrompt = { onShowSkipPrompt(habit.id) }
                )
            }
        }
    }
}

@Composable
private fun HabitRow(
    habit: Habit,
    entry: HabitEntry?,
    isDone: Boolean,
    isSkipped: Boolean,
    onToggle: () -> Unit,
    onLog: () -> Unit,
    onSkip: () -> Unit,
    showSkipPrompt: () -> Unit
) {
    val container = when {
        isSkipped -> Color(0xFF3F2A2A)
        isDone && habit.type == HabitType.CHECKBOX -> Color(0xFF166534)
        else -> Color(0xFF1E2937)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (habit.type == HabitType.CHECKBOX) onToggle() else onLog()
            },
        colors = CardDefaults.cardColors(containerColor = container),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator
            val showCheck = habit.type == HabitType.CHECKBOX && isDone && !isSkipped
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(
                        if (showCheck) Color(0xFF22C55E) else Color(0xFF475569),
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (showCheck) {
                    Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                } else if (isSkipped) {
                    Text("⏭", color = Color(0xFFFCA5A5), fontSize = 12.sp)
                } else if (habit.type != HabitType.CHECKBOX) {
                    Text(
                        when (habit.type) {
                            HabitType.COUNTER -> "#"
                            HabitType.DURATION_MIN -> "⏱"
                            HabitType.SCALE_1_5 -> "1-5"
                            HabitType.NOTE -> "✎"
                            else -> "•"
                        },
                        color = Color.White, fontSize = 11.sp
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    habit.name + if (!habit.canSkip) " (essential)" else "",
                    color = if (isSkipped) Color(0xFFFCA5A5) else Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (habit.why.isNotBlank()) {
                    Text(habit.why, color = Color(0xFF64748B), fontSize = 11.sp, lineHeight = 14.sp)
                }
                if (entry != null && entry.note.isNotBlank()) {
                    Text("“${entry.note}”", color = Color(0xFF4ADE80), fontSize = 11.sp)
                }
                if (entry != null && habit.type != HabitType.CHECKBOX && entry.value > 0) {
                    Text("${entry.value} ${habit.unit}", color = Color(0xFF94A3B8), fontSize = 11.sp)
                }
                // Exact time
                if (entry != null && entry.loggedAt > 0) {
                    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(entry.loggedAt))
                    Text("at $timeStr", color = Color(0xFF475569), fontSize = 10.sp)
                }
            }

            if (habit.type != HabitType.CHECKBOX) {
                TextButton(onClick = onLog) {
                    Text("Log", color = Color(0xFF22C55E), fontSize = 12.sp)
                }
            }

            // SKIP instead of delete
            TextButton(
                onClick = {
                    if (habit.canSkip) {
                        onSkip()
                        showSkipPrompt()
                    } else {
                        // hygiene - still allow? caller warned, or just skip with note
                        onSkip()
                    }
                },
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFF87171))
            ) {
                Text("Skip", fontSize = 12.sp)
            }
        }
    }
}
