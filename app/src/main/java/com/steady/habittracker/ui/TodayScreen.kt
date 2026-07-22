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
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.time.LocalTime
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.steady.habittracker.data.AppData
import com.steady.habittracker.data.AutoSuggestion
import com.steady.habittracker.data.CaptureTags
import com.steady.habittracker.data.Habit
import com.steady.habittracker.data.HabitDomain
import com.steady.habittracker.data.HabitEntry
import com.steady.habittracker.data.HabitType
import com.steady.habittracker.data.TimelineSection
import com.steady.habittracker.data.displayGlyph
import com.steady.habittracker.data.defaultLogNote
import com.steady.habittracker.data.displayLabel
import com.steady.habittracker.data.effectiveDescription
import com.steady.habittracker.data.inboxCaptures
import com.steady.habittracker.data.journalCaptures
import com.steady.habittracker.sensors.AutoLogEngine
import com.steady.habittracker.sensors.AutoLogMapper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TodayScreen(
    appData: AppData,
    todayEntries: Map<String, HabitEntry>,
    onToggle: (String) -> Unit,
    onLogEntry: (habitId: String, value: Double, note: String, date: String) -> Unit,  // support backfill date for metrics
    onRequestLog: (Habit) -> Unit = { h -> onLogEntry(h.id, 1.0, "", java.time.LocalDate.now().toString()) },  // preferred for dialog popup + keyboard
    onSkip: (String) -> Unit,
    onShowSkipPrompt: (habitId: String) -> Unit = {},
    onQuickCapture: (title: String, note: String, tags: List<String>) -> Unit = { _, _, _ -> },  // #10 quick capture
    onUpdateCapture: (id: String, title: String, note: String, tags: List<String>) -> Unit = { _, _, _, _ -> },
    onProcessCapture: (id: String) -> Unit = {},
    onDeleteCapture: (id: String) -> Unit = {},
    onReopenCapture: (id: String) -> Unit = {},
    onChatWithGrok: () -> Unit = {},
    onSetTodayGridColumns: (Int) -> Unit = {},
    // manual metric logging support (#19)
    onCreateMetric: (name: String) -> Unit = {},
    onLogMetric: (habitId: String, value: Double, note: String, date: String) -> Unit = { _, _, _, _ -> },
    onStartRoutine: (com.steady.habittracker.data.ExerciseRoutine) -> Unit = {},
    /** Widget / deep-link: open + Capture dialog once. */
    openCaptureRequest: Boolean = false,
    /** Optional tags to pre-select (and force on save) when opened from check-in. */
    openCapturePresetTags: List<String> = emptyList(),
    openCaptureDialogTitle: String? = null,
    onOpenCaptureConsumed: () -> Unit = {},
    /** Widget / deep-link: open + Log dialog once. */
    openLogRequest: Boolean = false,
    onOpenLogConsumed: () -> Unit = {},
    onAcceptAutoSuggestion: (AutoSuggestion) -> Unit = {},
    onDismissAutoSuggestion: (AutoSuggestion) -> Unit = {},
    onRunAutoLog: () -> Unit = {}
) {
    if (appData.habits.none { !it.archived } || appData.groups.isEmpty()) {
        Text(
            "No habits yet. Open Manage to build your list.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
        return
    }

    // Dialog states (must declare before use)
    var showCaptureDialog by remember { mutableStateOf(false) }
    var capturePresetTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var captureDialogTitle by remember { mutableStateOf<String?>(null) }
    var showMetricDialog by remember { mutableStateOf(false) }
    var showJournalDialog by remember { mutableStateOf(false) }
    // #44: temporary 30s flash for newly created notes/ideas
    var flashCaptureId by remember { mutableStateOf<String?>(null) }
    var flashCreatedAt by remember { mutableStateOf(0L) }
    var knownCaptureIds by remember { mutableStateOf(appData.captures.map { it.id }.toSet()) }
    var editFlashCapture by remember { mutableStateOf<com.steady.habittracker.data.CaptureItem?>(null) }

    LaunchedEffect(appData.captures) {
        val ids = appData.captures.map { it.id }.toSet()
        val newId = ids.firstOrNull { it !in knownCaptureIds }
        knownCaptureIds = ids
        if (newId != null) {
            flashCaptureId = newId
            flashCreatedAt = System.currentTimeMillis()
            delay(30_000)
            if (flashCaptureId == newId) flashCaptureId = null
        }
    }

    LaunchedEffect(openCaptureRequest) {
        if (openCaptureRequest) {
            capturePresetTags = openCapturePresetTags
            captureDialogTitle = openCaptureDialogTitle
            showCaptureDialog = true
            onOpenCaptureConsumed()
        }
    }
    LaunchedEffect(openLogRequest) {
        if (openLogRequest) {
            showMetricDialog = true
            onOpenLogConsumed()
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
        val isCheckIn = capturePresetTags.any {
            it.equals(com.steady.habittracker.data.CaptureTags.CHECKIN, ignoreCase = true)
        }
        CaptureDialog(
            prefs = appData.capturePrefs,
            presetTags = capturePresetTags.takeIf { it.isNotEmpty() },
            dialogTitle = captureDialogTitle ?: if (isCheckIn) "Awareness check-in" else null,
            forceTags = if (isCheckIn) {
                listOf(com.steady.habittracker.data.CaptureTags.CHECKIN)
            } else {
                emptyList()
            },
            onDismiss = {
                showCaptureDialog = false
                capturePresetTags = emptyList()
                captureDialogTitle = null
            },
            onCapture = { title, note, tags ->
                onQuickCapture(title, note, tags)
                showCaptureDialog = false
                capturePresetTags = emptyList()
                captureDialogTitle = null
            }
        )
    }
    // Re-open Write to edit a just-created flash item (#44)
    editFlashCapture?.let { cap ->
        CaptureDialog(
            prefs = appData.capturePrefs,
            presetTags = cap.tags,
            dialogTitle = "Edit note",
            initialTitle = cap.title,
            initialNote = cap.note,
            onDismiss = { editFlashCapture = null },
            onCapture = { title, note, tags ->
                onUpdateCapture(cap.id, title, note, tags)
                editFlashCapture = null
                flashCaptureId = null
            }
        )
    }
    if (showJournalDialog) {
        CaptureJournalDialog(
            appData = appData,
            onDismiss = { showJournalDialog = false },
            onDelete = onDeleteCapture,
            onReopenToInbox = onReopenCapture
        )
    }

    // Stable keys for domain work — avoid full AppData identity thrash when possible
    val dateKey = remember { LocalDate.now().toString() }
    val sections = remember(
        appData.habits,
        appData.groups,
        appData.schedules,
        appData.activeScheduleId,
        appData.sleep,
        appData.entries[dateKey],
        todayEntries
    ) {
        HabitDomain.timelineSectionsForToday(appData, LocalDate.now(), LocalTime.now())
    }
    val cue = remember(sections) { HabitDomain.dayProgressionCueFromSections(sections) }
    val nameById = remember(appData.habits) { appData.habits.associate { it.id to it.name } }
    val tagLabels = remember(appData.habits, appData.tags) { HabitDomain.tagLabelByHabitId(appData) }
    // Action inbox only (Ideas / Todo / Reminders) — journal tags never appear here
    val pendingCaptures = remember(appData.captures, appData.capturePrefs) {
        appData.inboxCaptures()
    }
    val journalCount = remember(appData.captures, appData.capturePrefs) {
        appData.journalCaptures().size
    }
    val autoSuggestions = remember(appData.autoSuggestions, dateKey) {
        AutoLogEngine.pendingSuggestions(appData, dateKey)
    }
    val habitNameById = remember(appData.habits) { appData.habits.associate { it.id to it.name } }
    val todayRoutines = remember(appData.routines, appData.workoutSessions, dateKey) {
        HabitDomain.routinesDueOn(appData, LocalDate.now())
    }
    val workoutDays = remember(appData.workoutSessions) { HabitDomain.workoutDaysInWindow(appData, 7) }
    val routineDoneIds = remember(appData.workoutSessions, dateKey) {
        todayRoutines.mapNotNull { rt ->
            if (HabitDomain.isRoutineCompletedOn(appData, rt.id, dateKey)) rt.id else null
        }.toSet()
    }
    // Per-section row models so items don't re-run domain / string work while scrolling
    val sectionRows = remember(sections, todayEntries, nameById, tagLabels) {
        sections.map { section ->
            val rows = section.habits.map { habit ->
                val entry = todayEntries[habit.id]
                val isSimpleTapAdd = habit.type == HabitType.COUNTER &&
                    (
                        (habit.target ?: 0.0) <= 2.0 ||
                            habit.isSupplement ||
                            habit.name.contains("supp", ignoreCase = true) ||
                            habit.name.contains("magnesium", ignoreCase = true)
                        )
                val isDone = (entry?.value ?: 0.0) >= 0.5
                val isSkipped = entry?.skipped == true
                val title = if (!habit.canSkip) "${habit.name} (essential)" else habit.name
                val desc = habit.effectiveDescription()
                val meta = buildList {
                    if (desc.isNotBlank()) add(desc)
                    if (habit.extensionType != com.steady.habittracker.data.ExtensionType.NONE) {
                        add(com.steady.habittracker.data.ExtensionCatalog.label(habit.extensionType))
                        com.steady.habittracker.extensions.ExtensionManager.statusLine(habit, appData)?.let { add(it) }
                    }
                    tagLabels[habit.id]?.takeIf { it.isNotBlank() }?.let { add(it) }
                    habit.afterHabitId?.let { nameById[it] }?.let { add("after $it") }
                    if (entry != null && habit.type != HabitType.CHECKBOX && entry.value > 0) {
                        add("${entry.value} ${habit.unit}".trim())
                    }
                    entry?.loggedAt?.takeIf { it > 0 }?.let { add(formatLoggedTime(it)) }
                }.joinToString(" · ")
                TodayHabitRowModel(
                    listKey = "${section.group.id}_${habit.id}",
                    habit = habit,
                    entry = entry,
                    isDone = isDone,
                    isSkipped = isSkipped,
                    isSimpleTapAdd = isSimpleTapAdd,
                    glyph = habit.icon.trim().ifEmpty { habit.displayGlyph() },
                    title = title,
                    metaLine = meta,
                    noteLine = entry?.note?.takeIf { it.isNotBlank() }
                )
            }
            TodaySectionBundle(section = section, rows = rows)
        }
    }
    val listState = rememberLazyListState()
    // Auto-scroll to "now" once — never re-animate while logging (kills scroll FPS)
    var didAutoScroll by remember { mutableStateOf(false) }
    val nowSectionKey = sectionRows.firstOrNull { it.section.isNow }?.section?.group?.id
    LaunchedEffect(nowSectionKey) {
        if (didAutoScroll || nowSectionKey == null) return@LaunchedEffect
        val nowIndex = sectionRows.indexOfFirst { it.section.isNow }
        if (nowIndex < 0) return@LaunchedEffect
        var itemIndex = 0
        if (pendingCaptures.isNotEmpty()) {
            itemIndex += 1 + pendingCaptures.take(5).size
        }
        itemIndex += 1 // day cue
        if (todayRoutines.isNotEmpty()) itemIndex += 1
        for (i in 0 until nowIndex) {
            itemIndex += 1 + sectionRows[i].rows.size
        }
        listState.scrollToItem(itemIndex.coerceAtLeast(0))
        didAutoScroll = true
    }

    // Hoist scheme colors once for list rows (avoids repeated theme lookups per item)
    val scheme = MaterialTheme.colorScheme
    val colors = remember(scheme) {
        TodayListColors(
            primary = scheme.primary,
            onPrimary = scheme.onPrimary,
            onSurface = scheme.onSurface,
            onSurfaceVariant = scheme.onSurfaceVariant,
            surface = scheme.surface,
            surfaceVariant = scheme.surfaceVariant,
            error = scheme.error
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.weight(1f))
            // Compact pill actions
            Surface(
                onClick = { showJournalDialog = true },
                shape = RoundedCornerShape(20.dp),
                color = colors.surfaceVariant
            ) {
                Text(
                    if (journalCount > 0) "Journal · $journalCount" else "Journal",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = colors.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Surface(
                onClick = {
                    capturePresetTags = emptyList()
                    captureDialogTitle = null
                    showCaptureDialog = true
                },
                shape = RoundedCornerShape(20.dp),
                color = colors.primary.copy(alpha = 0.14f)
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // #44 Write (pen) instead of Inbox
                    Text("✎", fontSize = 13.sp, color = colors.primary)
                    Text(
                        if (pendingCaptures.isEmpty()) "Write"
                        else "Write · ${pendingCaptures.size}",
                        color = colors.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Surface(
                onClick = { showMetricDialog = true },
                shape = RoundedCornerShape(20.dp),
                color = colors.surfaceVariant
            ) {
                Text(
                    "Log",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    color = colors.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Surface(
                onClick = onChatWithGrok,
                shape = RoundedCornerShape(20.dp),
                color = colors.surfaceVariant
            ) {
                Text(
                    "Grok",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = colors.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Habit square density (2–4 cols): two-finger horizontal pinch — no chrome buttons
        var gridCols by remember {
            mutableIntStateOf(appData.todayGridColumns.coerceIn(2, 4))
        }
        LaunchedEffect(appData.todayGridColumns) {
            gridCols = appData.todayGridColumns.coerceIn(2, 4)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .pointerInput(Unit) {
                    detectHorizontalGridZoom { zoomInLargerTiles ->
                        val next = if (zoomInLargerTiles) {
                            (gridCols - 1).coerceAtLeast(2)
                        } else {
                            (gridCols + 1).coerceAtMost(4)
                        }
                        if (next != gridCols) {
                            gridCols = next
                            onSetTodayGridColumns(next)
                        }
                    }
                },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // #44 ephemeral just-created note with edit + fade/flash
            val flashId = flashCaptureId
            val flashCap = flashId?.let { id -> appData.captures.find { it.id == id } }
            if (flashCap != null) {
                item(key = "flash_${flashCap.id}", contentType = "flash") {
                    RecentWriteFlashCard(
                        cap = flashCap,
                        colors = colors,
                        onEdit = { editFlashCapture = flashCap },
                        onDismiss = { flashCaptureId = null }
                    )
                }
            }

            if (autoSuggestions.isNotEmpty()) {
                item(key = "auto_log_header", contentType = "hdr") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Sensor suggestions",
                            color = colors.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(onClick = onRunAutoLog) {
                            Text("Refresh", fontSize = 11.sp, color = colors.primary)
                        }
                    }
                }
                items(autoSuggestions, key = { "sug_${it.habitId}_${it.date}" }) { sug ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                habitNameById[sug.habitId] ?: sug.habitId,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = colors.onSurface
                            )
                            Text(
                                "${AutoLogMapper.sourceLabel(sug.source)} · ${sug.note.ifBlank { sug.value.toString() }}",
                                fontSize = 11.sp,
                                color = colors.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { onAcceptAutoSuggestion(sug) }) {
                                    Text("Accept", fontSize = 12.sp)
                                }
                                TextButton(onClick = { onDismissAutoSuggestion(sug) }) {
                                    Text("Dismiss", fontSize = 12.sp, color = colors.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            if (pendingCaptures.isNotEmpty()) {
                item(key = "inbox_header", contentType = "hdr") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Write · ideas & todos",
                            color = colors.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Journal →",
                            color = colors.onSurfaceVariant,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clickable { showJournalDialog = true }
                                .padding(4.dp)
                        )
                    }
                }
                items(
                    pendingCaptures.take(8),
                    key = { "cap_${it.id}" },
                    contentType = { "cap" }
                ) { cap ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        color = colors.surfaceVariant,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(cap.title, color = colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            if (cap.note.isNotBlank()) {
                                // #44 auto-shorten long notes in list
                                Text(
                                    text = if (cap.note.length > 90) cap.note.take(90).trimEnd() + "…" else cap.note,
                                    color = colors.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (cap.tags.isNotEmpty()) {
                                Text(
                                    cap.tags.joinToString(" · ") { t ->
                                        "${CaptureTags.glyph(t)} $t"
                                    },
                                    color = colors.onSurfaceVariant,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                                Text(
                                    "Done",
                                    color = colors.primary,
                                    fontSize = 11.sp,
                                    modifier = Modifier
                                        .clickable { onProcessCapture(cap.id) }
                                        .padding(vertical = 6.dp, horizontal = 4.dp)
                                )
                                Text(
                                    "Delete",
                                    color = colors.error,
                                    fontSize = 11.sp,
                                    modifier = Modifier
                                        .clickable { onDeleteCapture(cap.id) }
                                        .padding(vertical = 6.dp, horizontal = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            item(key = "day_cue", contentType = "cue") {
                DayProgressionBanner(cue = cue, colors = colors)
            }

            if (todayRoutines.isNotEmpty()) {
                item(key = "workouts_header", contentType = "workouts") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.surface,
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
                                    color = colors.primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Exercise days: $workoutDays/7",
                                    color = colors.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                            todayRoutines.forEach { rt ->
                                val done = rt.id in routineDoneIds
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            (if (done) "✓ " else "") + rt.name,
                                            color = colors.onSurface,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            "${rt.exercises.size} exercises · ~${rt.estimatedDurationMin} min" +
                                                (if (rt.tags.isNotEmpty()) " · ${rt.tags.take(2).joinToString()}" else ""),
                                            color = colors.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Text(
                                        if (done) "Again" else "Start",
                                        color = colors.primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .clickable { onStartRoutine(rt) }
                                            .padding(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (sections.isEmpty()) {
                item(key = "empty", contentType = "empty") {
                    Text(
                        "Nothing left for today — enjoy the space.\nAdd or configure items in Manage.",
                        color = colors.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            sectionRows.forEach { bundle ->
                item(key = "sec_${bundle.section.group.id}", contentType = "sec") {
                    TimelineSectionHeader(section = bundle.section, colors = colors)
                }
                // Square habit grid — pinch horizontally to change columns (2–4)
                val cols = gridCols
                val chunks = bundle.rows.chunked(cols)
                items(
                    chunks.size,
                    key = { idx -> "grid_${bundle.section.group.id}_${cols}_$idx" },
                    contentType = { "habit_row" }
                ) { idx ->
                    val chunk = chunks[idx]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        chunk.forEach { row ->
                            HabitSquare(
                                model = row,
                                colors = colors,
                                onToggle = onToggle,
                                onLogEntry = onLogEntry,
                                onRequestLog = onRequestLog,
                                onSkip = onSkip,
                                onShowSkipPrompt = onShowSkipPrompt,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(cols - chunk.count()) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Immutable
private data class TodayListColors(
    val primary: Color,
    val onPrimary: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val error: Color
)

@Immutable
private data class TodayHabitRowModel(
    val listKey: String,
    val habit: Habit,
    val entry: HabitEntry?,
    val isDone: Boolean,
    val isSkipped: Boolean,
    val isSimpleTapAdd: Boolean,
    /** Icon/letter — drawn via [GlyphIcon] (emoji cached as bitmap). */
    val glyph: String,
    val title: String,
    /** Single secondary line (tags · stack · value · time) — one text node, no emoji. */
    val metaLine: String,
    val noteLine: String?
)

@Immutable
private data class TodaySectionBundle(
    val section: TimelineSection,
    val rows: List<TodayHabitRowModel>
)

private val loggedTimeFmt = ThreadLocal.withInitial {
    SimpleDateFormat("HH:mm", Locale.getDefault())
}

private fun formatLoggedTime(epochMs: Long): String =
    loggedTimeFmt.get()!!.format(Date(epochMs))

@Composable
private fun DayProgressionBanner(
    cue: com.steady.habittracker.data.DayProgressionCue,
    colors: TodayListColors
) {
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
            .background(colors.primary.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(colors.primary, RoundedCornerShape(50))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            line,
            color = colors.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TimelineSectionHeader(section: TimelineSection, colors: TodayListColors) {
    val titleColor = when {
        section.isNow -> colors.primary
        section.isPast -> colors.onSurfaceVariant.copy(alpha = 0.85f)
        else -> colors.onSurface
    }
    val glyph = remember(section.group.icon, section.group.name) {
        section.group.displayGlyph()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (section.isNow) {
                    Modifier
                        .background(colors.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
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
                    .background(colors.primary, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(8.dp))
        }
        GlyphIcon(
            glyph = glyph,
            size = 16.dp,
            tintForSimple = titleColor,
            modifier = Modifier.padding(end = 6.dp)
        )
        BasicText(
            text = section.group.name,
            style = TextStyle(
                color = titleColor,
                fontSize = if (section.isNow) 13.sp else 12.sp,
                fontWeight = if (section.isNow) FontWeight.Bold else FontWeight.SemiBold
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (section.isPast) {
            Spacer(Modifier.width(6.dp))
            BasicText(
                text = "earlier",
                style = TextStyle(color = colors.onSurfaceVariant, fontSize = 10.sp)
            )
        }
    }
}

/**
 * Two-finger horizontal pinch on the Today list:
 * - fingers move apart → fewer columns (larger squares)
 * - fingers move together → more columns (denser)
 * One-finger scroll is left alone (only consumes when ≥2 pointers).
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectHorizontalGridZoom(
    onStep: (zoomInLargerTiles: Boolean) -> Unit
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var lastSpanX: Float? = null
        var accumRatio = 1f
        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size < 2) {
                lastSpanX = null
                accumRatio = 1f
                if (pressed.isEmpty()) break
                continue
            }
            val xs = pressed.map { it.position.x }
            val spanX = (xs.maxOrNull()!! - xs.minOrNull()!!).coerceAtLeast(1f)
            val prev = lastSpanX
            lastSpanX = spanX
            if (prev != null && prev > 24f) {
                val step = spanX / prev
                accumRatio *= step
                when {
                    accumRatio >= 1.14f -> {
                        onStep(true)
                        accumRatio = 1f
                    }
                    accumRatio <= 0.88f -> {
                        onStep(false)
                        accumRatio = 1f
                    }
                }
            }
            // Steal the gesture only while pinching so vertical fling still works one-finger
            pressed.forEach { change ->
                if (change.positionChanged()) change.consume()
            }
        }
    }
}

/** #44 Temporary confirmation after Write: edit button, fade + accelerating flash ~30s. */
@Composable
private fun RecentWriteFlashCard(
    cap: com.steady.habittracker.data.CaptureItem,
    colors: TodayListColors,
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "writeFlash")
    val pulse by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 420),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val ageMs = (System.currentTimeMillis() - cap.createdAt).coerceAtLeast(0L)
    val life = (ageMs / 30_000f).coerceIn(0f, 1f)
    val fade = (1f - life * 0.85f).coerceIn(0.15f, 1f)
    val alpha = fade * (0.65f + 0.35f * pulse)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .padding(horizontal = 4.dp),
        color = colors.primary.copy(alpha = 0.18f + 0.12f * pulse),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Just wrote",
                    fontSize = 10.sp,
                    color = colors.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    cap.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (cap.note.isNotBlank()) {
                    Text(
                        if (cap.note.length > 80) cap.note.take(80) + "…" else cap.note,
                        fontSize = 11.sp,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            TextButton(onClick = onEdit) {
                Text("Edit", fontSize = 12.sp, color = colors.primary)
            }
            TextButton(onClick = onDismiss) {
                Text("OK", fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
        }
    }
}

/** #46 Square habit tile — denser Today layout (3-up grid). Tap = log; long-press = skip. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun HabitSquare(
    model: TodayHabitRowModel,
    colors: TodayListColors,
    onToggle: (String) -> Unit,
    onLogEntry: (habitId: String, value: Double, note: String, date: String) -> Unit,
    onRequestLog: (Habit) -> Unit,
    onSkip: (String) -> Unit,
    onShowSkipPrompt: (habitId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val habit = model.habit
    val isDone = model.isDone
    val isSkipped = model.isSkipped

    val container = when {
        isSkipped -> colors.surfaceVariant
        isDone && habit.type == HabitType.CHECKBOX -> colors.primary.copy(alpha = 0.28f)
        isDone -> colors.primary.copy(alpha = 0.18f)
        else -> colors.surface
    }
    val showCheck = habit.type == HabitType.CHECKBOX && isDone && !isSkipped
    val titleColor = if (isSkipped) Color(0xFFF87171) else colors.onSurface
    val statusGlyph = when {
        isSkipped -> "›"
        showCheck -> "✓"
        isDone -> "·"
        else -> ""
    }

    Column(
        modifier = modifier
            .aspectRatio(1f)
            .background(container, RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = {
                    if (habit.type == HabitType.CHECKBOX) {
                        onToggle(habit.id)
                    } else if (model.isSimpleTapAdd) {
                        onLogEntry(
                            habit.id,
                            habit.target ?: 1.0,
                            habit.defaultLogNote(),
                            LocalDate.now().toString()
                        )
                    } else {
                        onRequestLog(habit)
                    }
                },
                onLongClick = {
                    onSkip(habit.id)
                    if (habit.canSkip) onShowSkipPrompt(habit.id)
                }
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.TopEnd, modifier = Modifier.fillMaxWidth()) {
            GlyphIcon(
                glyph = model.glyph,
                size = 26.dp,
                tintForSimple = colors.primary,
                modifier = Modifier.align(Alignment.Center)
            )
            if (statusGlyph.isNotEmpty()) {
                BasicText(
                    text = statusGlyph,
                    style = TextStyle(
                        color = if (isSkipped) Color(0xFFF87171) else colors.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        BasicText(
            text = model.title,
            style = TextStyle(
                color = titleColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (model.metaLine.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            BasicText(
                text = model.metaLine,
                style = TextStyle(color = colors.onSurfaceVariant, fontSize = 9.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
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
