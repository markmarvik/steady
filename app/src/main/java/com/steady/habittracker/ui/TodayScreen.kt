package com.steady.habittracker.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.MaterialTheme
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
    onShowSkipPrompt: (habitId: String) -> Unit = {},
    onQuickCapture: (title: String, note: String) -> Unit = { _, _ -> }  // #10 quick capture for inbox
) {
    if (appData.habits.isEmpty() || appData.groups.isEmpty()) {
        Text("No habits yet. Add via Manage tab!", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        return
    }

    // Quick capture entry point (#10) - taps into capture system + shows pending count
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = { onQuickCapture("Quick idea " + (System.currentTimeMillis() % 1000), "captured from Today") }) {
            Text("+ Capture", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        }
        if (appData.captures.isNotEmpty()) {
            Text(" (${appData.captures.count { !it.processed }} inbox)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterVertically))
        }
    }

    // Filter to only pending (not yet completed) items for a cleaner Today list (#2)
    // Completed items (value >=0.5 or skipped) are hidden until next day (entries keyed by date).
    val grouped = appData.groups.filter { !it.archived }.sortedBy { it.order }.map { g ->
        val habitsForGroup = appData.habits.filter { it.groupId == g.id && !it.archived }.sortedBy { it.order }
        val pending = habitsForGroup.filter { h ->
            val e = todayEntries[h.id]
            (e?.value ?: 0.0) < 0.5 && e?.skipped != true
        }
        g to pending
    }.filter { it.second.isNotEmpty() }  // hide groups with nothing left to do today

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
                val isSimpleTapAdd = habit.type == HabitType.COUNTER &&
                    ((habit.target ?: 0.0) <= 2.0 || habit.isSupplement || habit.name.contains("supp", ignoreCase = true) || habit.name.contains("magnesium", ignoreCase = true))

                HabitRow(
                    habit = habit,
                    entry = entry,
                    isDone = isDone,
                    isSkipped = isSkipped,
                    onToggle = { onToggle(habit.id) },
                    onLog = {
                        if (isSimpleTapAdd) {
                            // Simple tap-to-add for supplements (#5): log default immediately, no dialog/note
                            val v = habit.target ?: 1.0
                            onLogEntry(habit.id, v, "")
                        } else {
                            onRequestLog(habit)
                        }
                    },
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
@OptIn(ExperimentalFoundationApi::class)
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
        isSkipped -> MaterialTheme.colorScheme.surfaceVariant
        isDone && habit.type == HabitType.CHECKBOX -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (habit.type == HabitType.CHECKBOX) onToggle() else onLog()
                },
                onLongClick = {
                    if (habit.canSkip) {
                        onSkip()
                        showSkipPrompt()
                    } else {
                        onSkip()
                    }
                }
            ),
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
                        if (showCheck) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (showCheck) {
                    Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                } else if (isSkipped) {
                    Text("⏭", color = Color(0xFFF87171), fontSize = 12.sp) // keep a soft red for skip state
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
                    habit.name +
                        (if (!habit.canSkip) " (essential)" else "") +
                        (if (habit.isSupplement) " [supp]" else ""),
                    color = if (isSkipped) Color(0xFFF87171) else MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (habit.why.isNotBlank()) {
                    Text(habit.why, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 14.sp)
                }
                if (entry != null && entry.note.isNotBlank()) {
                    Text("“${entry.note}”", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                }
                if (entry != null && habit.type != HabitType.CHECKBOX && entry.value > 0) {
                    Text("${entry.value} ${habit.unit}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
                // Exact time
                if (entry != null && entry.loggedAt > 0) {
                    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(entry.loggedAt))
                    Text("at $timeStr", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                }
            }

            if (habit.type != HabitType.CHECKBOX) {
                TextButton(onClick = onLog) {
                    Text("Log", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                }
            }

            // Skip is now long-press only (hold  ~1s+ on the row) per #1. No always-visible button.
            // Long-press handled on the Card via combinedClickable.
        }
    }
}
