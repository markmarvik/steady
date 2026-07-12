package com.steady.habittracker.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.time.LocalTime
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.steady.habittracker.data.AppData
import com.steady.habittracker.data.Habit
import com.steady.habittracker.data.HabitDomain
import com.steady.habittracker.data.HabitEntry
import com.steady.habittracker.data.HabitType
import com.steady.habittracker.data.TimelineSection
import com.steady.habittracker.data.displayGlyph
import com.steady.habittracker.data.displayLabel
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
    onLogMetric: (habitId: String, value: Double, note: String, date: String) -> Unit = { _, _, _, _ -> },
    onStartRoutine: (com.steady.habittracker.data.ExerciseRoutine) -> Unit = {}
) {
    if (appData.habits.isEmpty() || appData.groups.isEmpty()) {
        Text("No habits yet. Add via Manage tab!", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        return
    }

    // Dialog states (must declare before use)
    var showCaptureDialog by remember { mutableStateOf(false) }
    var showMetricDialog by remember { mutableStateOf(false) }

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

    // Pure visual day progression: Morning → … → Sleep, soft Now marker (no clock numbers)
    val sections = remember(appData, todayEntries) {
        HabitDomain.timelineSectionsForToday(appData, LocalDate.now(), LocalTime.now())
    }
    val cue = remember(appData, todayEntries) {
        HabitDomain.dayProgressionCue(appData, LocalDate.now(), LocalTime.now())
    }
    val nameById = remember(appData.habits) { appData.habits.associate { it.id to it.name } }
    val pendingCaptures = appData.captures.filter { !it.processed }
    val todayRoutines = remember(appData) {
        HabitDomain.routinesDueOn(appData, LocalDate.now())
    }
    val workoutDays = remember(appData) { HabitDomain.workoutDaysInWindow(appData, 7) }
    val listState = rememberLazyListState()

    // Scroll so the current section sits near the top of the day spine
    LaunchedEffect(sections.map { it.group.id to it.isNow }) {
        val nowIndex = sections.indexOfFirst { it.isNow }
        if (nowIndex < 0) return@LaunchedEffect
        // Count list items before that section (inbox + cue + prior sections)
        var itemIndex = 0
        if (pendingCaptures.isNotEmpty()) {
            itemIndex += 1 + pendingCaptures.take(5).size + 1 // header + rows + spacer
        }
        itemIndex += 1 // progression cue or spacer
        for (i in 0 until nowIndex) {
            itemIndex += 1 + sections[i].habits.size // header + habits
        }
        // header of now section
        listState.animateScrollToItem(itemIndex.coerceAtLeast(0))
    }

    Column(modifier = Modifier.fillMaxSize()) {
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

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Inbox above the day spine
            if (pendingCaptures.isNotEmpty()) {
                item(key = "inbox_header") {
                    Text(
                        "Quick Captures / Inbox",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                items(pendingCaptures.take(5), key = { "cap_${it.id}" }) { cap ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
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
            }

            // Day spine cue (names only — pure visual progression)
            item(key = "day_cue") {
                DayProgressionBanner(cue = cue)
            }

            // Workouts scheduled for today (#22)
            if (todayRoutines.isNotEmpty()) {
                item(key = "workouts_header") {
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
                                Text(
                                    "Workouts today",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Exercise days: $workoutDays/7",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                            todayRoutines.forEach { rt ->
                                val done = HabitDomain.isRoutineCompletedOn(
                                    appData, rt.id, LocalDate.now().toString()
                                )
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            (if (done) "✓ " else "") + rt.name,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            "${rt.exercises.size} exercises · ~${rt.estimatedDurationMin} min" +
                                                (if (rt.tags.isNotEmpty()) " · ${rt.tags.take(2).joinToString()}" else ""),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    }
                                    TextButton(onClick = { onStartRoutine(rt) }) {
                                        Text(
                                            if (done) "Again" else "Start",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (sections.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "Nothing left for today — enjoy the space.\nAdd or configure items in Manage.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            sections.forEach { section ->
                item(key = "sec_${section.group.id}") {
                    TimelineSectionHeader(section = section)
                }
                // Key includes group so multi-group habits (#24) don't collide in LazyColumn
                items(section.habits, key = { "${section.group.id}_${it.id}" }) { habit ->
                    val entry = todayEntries[habit.id]
                    val isDone = (entry?.value ?: 0.0) >= 0.5
                    val isSkipped = entry?.skipped == true
                    val isSimpleTapAdd = habit.type == HabitType.COUNTER &&
                        ((habit.target ?: 0.0) <= 2.0 || habit.isSupplement || habit.name.contains("supp", ignoreCase = true) || habit.name.contains("magnesium", ignoreCase = true))
                    val afterName = habit.afterHabitId?.let { nameById[it] }
                    val tagLabel = HabitDomain.tagNamesForHabit(appData, habit).joinToString(" · ")

                    HabitRow(
                        habit = habit,
                        entry = entry,
                        isDone = isDone,
                        isSkipped = isSkipped,
                        stackAfterLabel = afterName,
                        tagLabel = tagLabel,
                        onToggle = { onToggle(habit.id) },
                        onLog = {
                            if (isSimpleTapAdd) {
                                val v = habit.target ?: 1.0
                                onLogEntry(habit.id, v, "", LocalDate.now().toString())
                            } else {
                                onRequestLog(habit)
                            }
                        },
                        onSkip = { onSkip(habit.id) },
                        showSkipPrompt = { onShowSkipPrompt(habit.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DayProgressionBanner(cue: com.steady.habittracker.data.DayProgressionCue) {
    val nowName = cue.nowGroupName
    if (nowName == null && cue.nextGroupName == null) return
    val line = when {
        nowName != null && cue.nowHasPending -> "Now · $nowName"
        nowName != null && cue.nextGroupName != null -> "Now · $nowName (done) · next: ${cue.nextGroupName}"
        nowName != null -> "Now · $nowName"
        cue.nextGroupName != null -> "Up next · ${cue.nextGroupName}"
        else -> return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            line,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TimelineSectionHeader(section: TimelineSection) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val titleColor = when {
        section.isNow -> accent
        section.isPast -> muted.copy(alpha = 0.85f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (section.isNow) {
                    Modifier
                        .background(accent.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                } else {
                    Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (section.isNow) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(16.dp)
                    .background(accent, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "●  ${section.group.displayLabel()}",
                color = titleColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        } else {
            Text(
                section.group.displayLabel(),
                color = titleColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (section.isPast) {
                Spacer(Modifier.width(6.dp))
                Text("earlier", color = muted, fontSize = 10.sp)
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
    stackAfterLabel: String? = null,
    tagLabel: String = "",
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
                .padding(14.dp)
                .padding(start = if (stackAfterLabel != null) 12.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator — high contrast (no white-on-light-gray)
            val showCheck = habit.type == HabitType.CHECKBOX && isDone && !isSkipped
            val iconBg = when {
                isSkipped -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                showCheck -> MaterialTheme.colorScheme.primary
                isDone -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            }
            val iconFg = when {
                isSkipped -> Color(0xFFF87171)
                showCheck -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.primary
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(iconBg, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (showCheck) {
                    Icon(Icons.Default.Check, null, tint = iconFg, modifier = Modifier.size(16.dp))
                } else if (isSkipped) {
                    Text("⏭", color = iconFg, fontSize = 12.sp)
                } else if (habit.type != HabitType.CHECKBOX) {
                    Text(
                        when (habit.type) {
                            HabitType.COUNTER -> "#"
                            HabitType.DURATION_MIN -> "m"
                            HabitType.SCALE_1_5 -> "±"
                            HabitType.NOTE -> "✎"
                            else -> "•"
                        },
                        color = iconFg,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text("○", color = iconFg, fontSize = 14.sp)
                }
            }

            Spacer(Modifier.width(12.dp))

            if (habit.icon.isNotBlank()) {
                Text(habit.icon.trim(), fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
            }

            Column(Modifier.weight(1f)) {
                Text(
                    habit.name +
                        (if (!habit.canSkip) " (essential)" else ""),
                    color = if (isSkipped) Color(0xFFF87171) else MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (tagLabel.isNotBlank()) {
                    Text(
                        tagLabel,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        fontSize = 11.sp
                    )
                }
                if (stackAfterLabel != null) {
                    Text(
                        "↳ after $stackAfterLabel",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp
                    )
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
