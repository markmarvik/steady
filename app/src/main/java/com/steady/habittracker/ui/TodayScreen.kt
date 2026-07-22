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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.steady.habittracker.data.CaptureItem
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
import com.steady.habittracker.data.openTodoCaptures
import com.steady.habittracker.sensors.AutoLogEngine
import com.steady.habittracker.sensors.AutoLogMapper
import com.steady.habittracker.util.SteadyHaptics
import com.steady.habittracker.util.rememberSteadyHaptics
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut

@Composable
fun TodayScreen(
    appData: AppData,
    todayEntries: Map<String, HabitEntry>,
    onToggle: (String) -> Unit,
    onLogEntry: (habitId: String, value: Double, note: String, date: String) -> Unit,  // support backfill date for metrics
    onRequestLog: (Habit) -> Unit = { h ->
        onLogEntry(h.id, 1.0, "", HabitDomain.logicalToday(appData))
    },
    onSkip: (String) -> Unit,
    onShowSkipPrompt: (habitId: String) -> Unit = {},
    onQuickCapture: (title: String, note: String, tags: List<String>) -> Unit = { _, _, _ -> },
    onUpdateCapture: (id: String, title: String, note: String, tags: List<String>) -> Unit = { _, _, _, _ -> },
    onProcessCapture: (id: String) -> Unit = {},
    onDeleteCapture: (id: String) -> Unit = {},
    onReopenCapture: (id: String) -> Unit = {},
    onOpenWrite: () -> Unit = {},
    onOpenJournal: () -> Unit = {},
    onChatWithGrok: () -> Unit = {},
    onSetTodayGridColumns: (Int) -> Unit = {},
    // manual metric logging support (#19)
    onCreateMetric: (name: String) -> Unit = {},
    onLogMetric: (habitId: String, value: Double, note: String, date: String) -> Unit = { _, _, _, _ -> },
    onStartRoutine: (com.steady.habittracker.data.ExerciseRoutine) -> Unit = {},
    /** Widget / deep-link: open + Log dialog once. */
    openLogRequest: Boolean = false,
    onOpenLogConsumed: () -> Unit = {},
    onAcceptAutoSuggestion: (AutoSuggestion) -> Unit = {},
    onDismissAutoSuggestion: (AutoSuggestion) -> Unit = {},
    onRunAutoLog: () -> Unit = {},
    /** Mark a Todo capture done (process). */
    onCompleteTodo: (String) -> Unit = {}
) {
    if (appData.habits.none { !it.archived } || appData.groups.isEmpty()) {
        Text(
            "No habits yet. Open Manage to build your list.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
        return
    }

    var showMetricDialog by remember { mutableStateOf(false) }
    // #44: temporary 30s flash for newly created notes/ideas
    var flashCaptureId by remember { mutableStateOf<String?>(null) }
    var knownCaptureIds by remember { mutableStateOf(appData.captures.map { it.id }.toSet()) }

    LaunchedEffect(appData.captures) {
        val ids = appData.captures.map { it.id }.toSet()
        val newId = ids.firstOrNull { it !in knownCaptureIds }
        knownCaptureIds = ids
        if (newId != null) {
            flashCaptureId = newId
            delay(30_000)
            if (flashCaptureId == newId) flashCaptureId = null
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

    // Show completed tiles (grayed + streak flame). Session-persisted across config changes.
    var showDoneHabits by rememberSaveable { mutableStateOf(false) }

    // Logical Steady day (respects dayStartHour, default 4am) — must match ViewModel.today / log keys
    val dateKey = HabitDomain.logicalToday(appData)
    val logicalDate = HabitDomain.logicalTodayDate(appData)
    // Merge live todayEntries so filtering sees the same map as the tiles (no calendar-vs-logical drift)
    val effectiveEntriesForDay = remember(appData.entries, todayEntries, dateKey) {
        val base = appData.entries[dateKey].orEmpty()
        if (todayEntries.isEmpty()) base else base + todayEntries
    }
    val dataForTimeline = remember(appData, effectiveEntriesForDay, dateKey) {
        appData.copy(entries = appData.entries + (dateKey to effectiveEntriesForDay))
    }
    val sections = remember(
        dataForTimeline.habits,
        dataForTimeline.groups,
        dataForTimeline.schedules,
        dataForTimeline.activeScheduleId,
        dataForTimeline.sleep,
        effectiveEntriesForDay,
        showDoneHabits,
        dateKey
    ) {
        HabitDomain.timelineSectionsForToday(
            dataForTimeline,
            logicalDate,
            LocalTime.now(),
            includeCompleted = showDoneHabits
        )
    }
    // Day cue always based on pending-only view so "now / next" stays action-oriented
    val cue = remember(
        dataForTimeline.habits,
        dataForTimeline.groups,
        dataForTimeline.schedules,
        dataForTimeline.activeScheduleId,
        dataForTimeline.sleep,
        effectiveEntriesForDay,
        dateKey
    ) {
        HabitDomain.dayProgressionCue(
            dataForTimeline,
            logicalDate,
            LocalTime.now()
        )
    }
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
    val todayRoutines = remember(appData.routines, appData.workoutSessions, dateKey, logicalDate) {
        HabitDomain.routinesDueOn(appData, logicalDate)
    }
    val workoutDays = remember(appData.workoutSessions) { HabitDomain.workoutDaysInWindow(appData, 7) }
    val routineDoneIds = remember(appData.workoutSessions, dateKey) {
        todayRoutines.mapNotNull { rt ->
            if (HabitDomain.isRoutineCompletedOn(appData, rt.id, dateKey)) rt.id else null
        }.toSet()
    }
    // Done count for the toggle label (due today and finished)
    val doneTodayCount = remember(appData.habits, effectiveEntriesForDay, dateKey, logicalDate) {
        val due = HabitDomain.habitsDueOn(appData, logicalDate)
        due.count { h ->
            val e = effectiveEntriesForDay[h.id]
            e != null && !e.skipped && e.value >= 0.5
        }
    }
    val androidContext = androidx.compose.ui.platform.LocalContext.current
    val openTodos = remember(appData.captures, appData.capturePrefs) {
        appData.openTodoCaptures()
    }
    // Per-section row models so items don't re-run domain / string work while scrolling
    val sectionRows = remember(
        sections,
        effectiveEntriesForDay,
        nameById,
        tagLabels,
        appData,
        androidContext,
        showDoneHabits
    ) {
        sections.map { section ->
            val rows = section.habits.map { habit ->
                val entry = effectiveEntriesForDay[habit.id] ?: todayEntries[habit.id]
                val isSimpleTapAdd = habit.type == HabitType.COUNTER &&
                    (
                        (habit.target ?: 0.0) <= 2.0 ||
                            habit.isSupplement ||
                            habit.name.contains("supp", ignoreCase = true) ||
                            habit.name.contains("magnesium", ignoreCase = true)
                        )
                val isDone = entry != null && !entry.skipped && entry.value >= 0.5
                val isSkipped = entry?.skipped == true
                val title = if (!habit.canSkip) "${habit.name} (essential)" else habit.name
                val desc = habit.effectiveDescription()
                val streak = HabitDomain.computeHabitStreak(appData, habit.id)
                val meta = buildList {
                    if (desc.isNotBlank()) add(desc)
                    if (habit.extensionType != com.steady.habittracker.data.ExtensionType.NONE) {
                        add(com.steady.habittracker.data.ExtensionCatalog.label(habit.extensionType))
                        com.steady.habittracker.extensions.ExtensionManager
                            .statusLine(habit, appData, androidContext)?.let { add(it) }
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
                    streak = streak,
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
            // Show completed habits (grayed + streak flame)
            Surface(
                onClick = { showDoneHabits = !showDoneHabits },
                shape = RoundedCornerShape(20.dp),
                color = if (showDoneHabits) colors.primary.copy(alpha = 0.18f) else colors.surfaceVariant
            ) {
                Text(
                    buildString {
                        append(if (showDoneHabits) "Hide done" else "Show done")
                        if (doneTodayCount > 0) append(" · $doneTodayCount")
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = if (showDoneHabits) colors.primary else colors.onSurface,
                    fontSize = 12.sp,
                    fontWeight = if (showDoneHabits) FontWeight.SemiBold else FontWeight.Medium
                )
            }
            Spacer(Modifier.weight(1f))
            // Compact pill actions
            Surface(
                onClick = onOpenJournal,
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
                onClick = onOpenWrite,
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

        // Habit square density (2–4 cols): deliberate two-finger horizontal pinch
        var gridCols by remember {
            mutableIntStateOf(appData.todayGridColumns.coerceIn(2, 4))
        }
        var densityHintVisible by remember { mutableStateOf(false) }
        val haptics = rememberSteadyHaptics()
        LaunchedEffect(appData.todayGridColumns) {
            gridCols = appData.todayGridColumns.coerceIn(2, 4)
        }
        LaunchedEffect(densityHintVisible) {
            if (densityHintVisible) {
                delay(900)
                densityHintVisible = false
            }
        }

        Box(Modifier.weight(1f)) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
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
                            densityHintVisible = true
                            haptics(SteadyHaptics.Kind.ZOOM)
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
                        onEdit = onOpenWrite,
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
                                .clickable(onClick = onOpenJournal)
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

            if (sections.isEmpty() && openTodos.isEmpty()) {
                item(key = "empty", contentType = "empty") {
                    Text(
                        "Nothing left for today — enjoy the space.\nAdd or configure items in Manage.",
                        color = colors.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // If Work section has no pending habits, still show Todos in a Work-like header
            val workSectionPresent = sectionRows.any {
                isWorkGroup(it.section.group.timeHint, it.section.group.name)
            }

            sectionRows.forEach { bundle ->
                item(key = "sec_${bundle.section.group.id}", contentType = "sec") {
                    TimelineSectionHeader(section = bundle.section, colors = colors)
                }
                // Square habit grid — pinch horizontally to change columns (2–4)
                val cols = gridCols
                val isWorkSection = isWorkGroup(bundle.section.group.timeHint, bundle.section.group.name)
                // Todos appear as habit-like squares inside Work (same size / density)
                val todoRows = if (isWorkSection) openTodos else emptyList()
                val habitChunks = bundle.rows.chunked(cols)
                items(
                    habitChunks.size,
                    key = { idx -> "grid_${bundle.section.group.id}_${cols}_$idx" },
                    contentType = { "habit_row" }
                ) { idx ->
                    val chunk = habitChunks[idx]
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
                                logicalToday = dateKey,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(cols - chunk.count()) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                if (todoRows.isNotEmpty()) {
                    item(key = "todos_hdr_${bundle.section.group.id}", contentType = "todo_hdr") {
                        Text(
                            "Todos · ${todoRows.size}",
                            color = colors.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp)
                        )
                    }
                    val todoChunks = todoRows.chunked(cols)
                    items(
                        todoChunks.size,
                        key = { idx -> "todo_grid_${bundle.section.group.id}_${cols}_$idx" },
                        contentType = { "todo_row" }
                    ) { idx ->
                        val chunk = todoChunks[idx]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            chunk.forEach { todo ->
                                TodoHabitSquare(
                                    cap = todo,
                                    colors = colors,
                                    onComplete = { onCompleteTodo(todo.id) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(cols - chunk.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            if (!workSectionPresent && openTodos.isNotEmpty()) {
                item(key = "todos_standalone_hdr", contentType = "todo_hdr") {
                    Text(
                        "Work · Todos · ${openTodos.size}",
                        color = colors.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                    )
                }
                val cols = gridCols
                val todoChunks = openTodos.chunked(cols)
                items(
                    todoChunks.size,
                    key = { idx -> "todo_standalone_${cols}_$idx" },
                    contentType = { "todo_row" }
                ) { idx ->
                    val chunk = todoChunks[idx]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        chunk.forEach { todo ->
                            TodoHabitSquare(
                                cap = todo,
                                colors = colors,
                                onComplete = { onCompleteTodo(todo.id) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(cols - chunk.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Floating density feedback while pinching (2–4 cols)
        androidx.compose.animation.AnimatedVisibility(
            visible = densityHintVisible,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.primary.copy(alpha = 0.92f),
                shadowElevation = 6.dp
            ) {
                Text(
                    when (gridCols) {
                        2 -> "Large · 2 wide"
                        3 -> "Medium · 3 wide"
                        else -> "Dense · 4 wide"
                    },
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    color = colors.onPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        } // Box
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
    /** Consecutive due-day completions for flame marker. */
    val streak: Int = 0,
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
 *
 * Less sensitive than a 1:1 scale: requires a deliberate ~30% span change
 * and a minimum finger distance so casual scroll/jitter doesn't flip density.
 * One-finger scroll is left alone (only consumes when ≥2 pointers).
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectHorizontalGridZoom(
    onStep: (zoomInLargerTiles: Boolean) -> Unit
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var lastSpanX: Float? = null
        var accumRatio = 1f
        var steppedThisGesture = false
        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size < 2) {
                lastSpanX = null
                accumRatio = 1f
                steppedThisGesture = false
                if (pressed.isEmpty()) break
                continue
            }
            val xs = pressed.map { it.position.x }
            val spanX = (xs.maxOrNull()!! - xs.minOrNull()!!).coerceAtLeast(1f)
            // Ignore tiny two-finger contact (accidental)
            if (spanX < 72f) {
                lastSpanX = spanX
                continue
            }
            val prev = lastSpanX
            lastSpanX = spanX
            if (prev != null && prev > 48f) {
                val step = spanX / prev
                // Clamp single-frame jumps so a jumpy event can't fire multiple steps
                accumRatio *= step.coerceIn(0.85f, 1.18f)
                when {
                    // ~32% larger span → larger tiles (fewer columns)
                    accumRatio >= 1.32f -> {
                        onStep(true)
                        accumRatio = 1f
                        steppedThisGesture = true
                        // Small cooldown: reset baseline so one continuous pinch can still step again
                        lastSpanX = spanX
                    }
                    // ~28% smaller span → denser (more columns)
                    accumRatio <= 0.72f -> {
                        onStep(false)
                        accumRatio = 1f
                        steppedThisGesture = true
                        lastSpanX = spanX
                    }
                }
            }
            // Steal the gesture only while pinching so vertical fling still works one-finger
            // Always consume multi-touch movement so LazyColumn doesn't fight the zoom
            pressed.forEach { change ->
                if (change.positionChanged() || steppedThisGesture) change.consume()
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

private fun isWorkGroup(timeHint: String, name: String): Boolean {
    val hint = timeHint.uppercase()
    val n = name.lowercase()
    return hint == "WORK" ||
        n.contains("work") ||
        n.contains("focus") ||
        n.contains("deep") ||
        n.contains("office")
}

/** Todo capture rendered like a habit square inside the Work section. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TodoHabitSquare(
    cap: CaptureItem,
    colors: TodayListColors,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberSteadyHaptics()
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .background(colors.surface, RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = {
                    haptics(SteadyHaptics.Kind.SUCCESS)
                    onComplete()
                },
                onLongClick = {
                    haptics(SteadyHaptics.Kind.SUCCESS)
                    onComplete()
                }
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(CaptureTags.glyph(CaptureTags.TODO), fontSize = 22.sp)
        Spacer(Modifier.height(6.dp))
        BasicText(
            text = cap.title.ifBlank { "Todo" },
            style = TextStyle(
                color = colors.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (cap.note.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            BasicText(
                text = cap.note,
                style = TextStyle(color = colors.onSurfaceVariant, fontSize = 9.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Spacer(Modifier.height(2.dp))
            BasicText(
                text = "tap to done",
                style = TextStyle(color = colors.primary, fontSize = 9.sp)
            )
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
    /** Logical Steady day (yyyy-MM-dd) — must match log keys / dayStartHour. */
    logicalToday: String,
    modifier: Modifier = Modifier
) {
    val haptics = rememberSteadyHaptics()
    val habit = model.habit
    val isDone = model.isDone
    val isSkipped = model.isSkipped
    val streak = model.streak

    val container = when {
        isSkipped -> colors.surfaceVariant
        // Done tiles: muted / grayed so they recede behind pending work
        isDone -> colors.surfaceVariant.copy(alpha = 0.55f)
        else -> colors.surface
    }
    val showCheck = habit.type == HabitType.CHECKBOX && isDone && !isSkipped
    val titleColor = when {
        isSkipped -> Color(0xFFF87171)
        isDone -> colors.onSurfaceVariant.copy(alpha = 0.72f)
        else -> colors.onSurface
    }
    val iconTint = if (isDone && !isSkipped) {
        colors.onSurfaceVariant.copy(alpha = 0.55f)
    } else {
        colors.primary
    }
    val statusGlyph = when {
        isSkipped -> "›"
        showCheck -> "✓"
        isDone -> "✓"
        else -> ""
    }
    val flame = when {
        streak <= 0 -> ""
        streak < 3 -> "🕯️"
        streak < 7 -> "🔥"
        streak < 14 -> "🔥"
        streak < 30 -> "🔥🔥"
        else -> "🔥🔥"
    }
    // Flame “grows” with streak length
    val flameSize = when {
        streak <= 0 -> 0.sp
        streak < 3 -> 10.sp
        streak < 7 -> 12.sp
        streak < 14 -> 14.sp
        streak < 30 -> 15.sp
        else -> 16.sp
    }

    Column(
        modifier = modifier
            .aspectRatio(1f)
            .alpha(if (isDone && !isSkipped) 0.72f else 1f)
            .background(container, RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = {
                    if (habit.type == HabitType.CHECKBOX) {
                        // Completing feels rewarding; unchecking is a light tick
                        if (!isDone) {
                            haptics(
                                if (streak >= 6) SteadyHaptics.Kind.REWARD
                                else SteadyHaptics.Kind.SUCCESS
                            )
                        } else {
                            haptics(SteadyHaptics.Kind.TICK)
                        }
                        onToggle(habit.id)
                    } else if (model.isSimpleTapAdd) {
                        haptics(SteadyHaptics.Kind.SUCCESS)
                        onLogEntry(
                            habit.id,
                            habit.target ?: 1.0,
                            habit.defaultLogNote(),
                            logicalToday
                        )
                    } else {
                        haptics(SteadyHaptics.Kind.TICK)
                        onRequestLog(habit)
                    }
                },
                onLongClick = {
                    haptics(SteadyHaptics.Kind.WARN)
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
                tintForSimple = iconTint,
                modifier = Modifier
                    .align(Alignment.Center)
                    .alpha(if (isDone && !isSkipped) 0.55f else 1f)
            )
            if (statusGlyph.isNotEmpty()) {
                BasicText(
                    text = statusGlyph,
                    style = TextStyle(
                        color = if (isSkipped) Color(0xFFF87171) else colors.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
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
        if (streak > 0 && flame.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                BasicText(
                    text = flame,
                    style = TextStyle(fontSize = flameSize)
                )
                Spacer(Modifier.width(2.dp))
                BasicText(
                    text = streak.toString(),
                    style = TextStyle(
                        color = if (isDone) {
                            colors.onSurfaceVariant.copy(alpha = 0.85f)
                        } else {
                            colors.primary
                        },
                        fontSize = when {
                            streak < 7 -> 10.sp
                            streak < 14 -> 11.sp
                            else -> 12.sp
                        },
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        } else if (model.metaLine.isNotBlank() && !isDone) {
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
