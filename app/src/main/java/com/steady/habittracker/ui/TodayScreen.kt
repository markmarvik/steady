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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
    onLogEntry: (habitId: String, value: Double, note: String, date: String) -> Unit,  // support backfill date for metrics
    onRequestLog: (Habit) -> Unit = { h -> onLogEntry(h.id, 1.0, "", java.time.LocalDate.now().toString()) },  // preferred for dialog popup + keyboard
    onSkip: (String) -> Unit,
    onShowSkipPrompt: (habitId: String) -> Unit = {},
    onQuickCapture: (title: String, note: String) -> Unit = { _, _ -> },  // #10 quick capture for inbox
    onProcessCapture: (id: String) -> Unit = {},
    onDeleteCapture: (id: String) -> Unit = {},
    // manual metric logging support (#19)
    onCreateMetric: (name: String) -> Unit = {},
    onLogMetric: (habitId: String, value: Double, note: String, date: String) -> Unit = { _, _, _, _ -> }
) {
    if (appData.habits.isEmpty() || appData.groups.isEmpty()) {
        Text("No habits yet. Add via Manage tab!", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        return
    }

    // Dialog states (must declare before use)
    var showCaptureDialog by remember { mutableStateOf(false) }
    var showMetricDialog by remember { mutableStateOf(false) }

    // Quick capture entry point (#10) - now opens proper dialog + shows inbox count
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = { showCaptureDialog = true }) {
            Text("+ Capture", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        }
        val pending = appData.captures.count { !it.processed }
        if (pending > 0) {
            Text(" ($pending inbox)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterVertically))
        }
        Spacer(Modifier.width(4.dp))
        TextButton(onClick = { showMetricDialog = true }) {
            Text("+ Log", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        }
    }

    if (showMetricDialog) {
        MetricLogPickerDialog(
            appData = appData,
            onDismiss = { showMetricDialog = false },
            onLog = { hid, v, n, d -> onLogMetric(hid, v, n, d); showMetricDialog = false },
            onCreateNew = { name ->
                onCreateMetric(name)
                showMetricDialog = false
            }
        )
    }

    if (showCaptureDialog) {
        CaptureDialog(
            onDismiss = { showCaptureDialog = false },
            onCapture = { title, note ->
                onQuickCapture(title, note)
                showCaptureDialog = false
            }
        )
    }

    // Visible actionable captures inbox (unprocessed) at top of Today
    val pendingCaptures = appData.captures.filter { !it.processed }
    if (pendingCaptures.isNotEmpty()) {
        Text("Quick Captures / Inbox", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
        pendingCaptures.take(5).forEach { cap ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(Modifier.padding(8.dp)) {
                    Text(cap.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    if (cap.note.isNotBlank()) {
                        Text(cap.note, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        TextButton(onClick = { onProcessCapture(cap.id) }) {
                            Text("Mark Done", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                        }
                        TextButton(onClick = { onDeleteCapture(cap.id) }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
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
                            onLogEntry(habit.id, v, "", java.time.LocalDate.now().toString())
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

/** Proper capture dialog for Today +Capture (addresses issue: no input before, no visibility) */
@Composable
fun CaptureDialog(
    onDismiss: () -> Unit,
    onCapture: (title: String, note: String) -> Unit
) {
    var title by remember { mutableStateOf("Quick idea ${System.currentTimeMillis() % 10000}") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text("Quick Capture") },
        text = {
            Column {
                Text("Capture an idea, todo, metric note or thought to process later.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title / idea") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note / details (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isNotBlank()) onCapture(title.trim(), note.trim())
            }) { Text("Capture") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/** Searchable metric / habit logger for ad-hoc / sporadic entries (e.g. weight, HRV). Supports date backfill + quick create. */
@Composable
fun MetricLogPickerDialog(
    appData: AppData,
    onDismiss: () -> Unit,
    onLog: (habitId: String, value: Double, note: String, date: String) -> Unit,
    onCreateNew: (name: String) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var valueText by remember { mutableStateOf("1") }
    var note by remember { mutableStateOf("") }
    var dateStr by remember { mutableStateOf(LocalDate.now().toString()) }

    val activeHabits = appData.habits.filter { !it.archived }
    val filtered = if (search.isBlank()) activeHabits else activeHabits.filter { h ->
        h.name.contains(search, ignoreCase = true) ||
            appData.groups.find { it.id == h.groupId }?.name?.contains(search, ignoreCase = true) == true ||
            h.unit.contains(search, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text("Log Metric / Ad-hoc Entry") },
        text = {
            Column {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Search habits, groups or units (e.g. weight)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))

                // Create new quick
                Row {
                    OutlinedTextField(
                        value = search.ifBlank { "Body weight" },
                        onValueChange = { search = it },
                        label = { Text("New metric name") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(6.dp))
                    Button(onClick = { onCreateNew(search.ifBlank { "New Metric" }) }, modifier = Modifier.align(Alignment.CenterVertically)) {
                        Text("Create", fontSize = 11.sp)
                    }
                }
                Text("Or pick existing below and log (supports past dates for backfill)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))

                Spacer(Modifier.height(6.dp))
                // List of filtered
                LazyColumn(modifier = Modifier.heightIn(max = 140.dp)) {
                    items(filtered.take(8)) { h ->
                        val g = appData.groups.find { it.id == h.groupId }?.name ?: ""
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedId = h.id; valueText = h.target?.toString() ?: "1" }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedId == h.id, onClick = { selectedId = h.id; valueText = h.target?.toString() ?: "1" })
                            Column(Modifier.weight(1f)) {
                                Text("${h.name} (${h.type.name})", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                                Text("$g ${h.unit}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                            }
                            TextButton(onClick = { selectedId = h.id; valueText = h.target?.toString() ?: "1" }) { Text("Pick", fontSize = 10.sp) }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                if (selectedId != null) {
                    val selH = appData.habits.find { it.id == selectedId }
                    if (selH != null) {
                        OutlinedTextField(value = valueText, onValueChange = { valueText = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Value (${selH.unit})") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note (opt)") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = dateStr, onValueChange = { dateStr = it }, label = { Text("Date yyyy-MM-dd (backfill)") }, modifier = Modifier.fillMaxWidth())
                    }
                } else {
                    Text("Select a habit above or create new to log.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val hid = selectedId
                if (hid != null) {
                    val v = valueText.toDoubleOrNull() ?: 1.0
                    // basic date validate
                    val d = try { LocalDate.parse(dateStr).toString() } catch (_: Exception) { LocalDate.now().toString() }
                    onLog(hid, v, note, d)
                }
            }) { Text("Log") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
