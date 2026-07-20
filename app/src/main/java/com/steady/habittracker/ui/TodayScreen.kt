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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.steady.habittracker.data.AppData
import com.steady.habittracker.data.AutoSuggestion
import com.steady.habittracker.data.Habit
import com.steady.habittracker.data.HabitDomain
import com.steady.habittracker.data.HabitEntry
import com.steady.habittracker.data.HabitType
import com.steady.habittracker.data.TimelineSection
import com.steady.habittracker.data.displayGlyph
import com.steady.habittracker.data.displayLabel
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
    onProcessCapture: (id: String) -> Unit = {},
    onDeleteCapture: (id: String) -> Unit = {},
    // manual metric logging support (#19)
    onCreateMetric: (name: String) -> Unit = {},
    onLogMetric: (habitId: String, value: Double, note: String, date: String) -> Unit = { _, _, _, _ -> },
    onStartRoutine: (com.steady.habittracker.data.ExerciseRoutine) -> Unit = {},
    /** Widget / deep-link: open + Capture dialog once. */
    openCaptureRequest: Boolean = false,
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
    var showMetricDialog by remember { mutableStateOf(false) }

    LaunchedEffect(openCaptureRequest) {
        if (openCaptureRequest) {
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
        CaptureDialog(
            prefs = appData.capturePrefs,
            onDismiss = { showCaptureDialog = false },
            onCapture = { title, note, tags ->
                onQuickCapture(title, note, tags)
                showCaptureDialog = false
            }
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
    val pendingCaptures = remember(appData.captures) { appData.captures.filter { !it.processed } }
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
                val meta = buildList {
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
                onClick = { showCaptureDialog = true },
                shape = RoundedCornerShape(20.dp),
                color = colors.primary.copy(alpha = 0.14f)
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("✦", fontSize = 12.sp, color = colors.primary)
                    Text(
                        if (pendingCaptures.isEmpty()) "Capture"
                        else "Capture · ${pendingCaptures.size}",
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
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                    Text(
                        "Quick Captures / Inbox",
                        color = colors.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                items(
                    pendingCaptures.take(5),
                    key = { "cap_${it.id}" },
                    contentType = { "cap" }
                ) { cap ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        color = colors.surfaceVariant,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            Text(cap.title, color = colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            if (cap.note.isNotBlank()) {
                                Text(cap.note, color = colors.onSurfaceVariant, fontSize = 11.sp)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                                Text(
                                    "Mark Done",
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
                items(
                    bundle.rows,
                    key = { it.listKey },
                    contentType = { "habit" }
                ) { row ->
                    HabitRow(
                        model = row,
                        colors = colors,
                        onToggle = onToggle,
                        onLogEntry = onLogEntry,
                        onRequestLog = onRequestLog,
                        onSkip = onSkip,
                        onShowSkipPrompt = onShowSkipPrompt
                    )
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

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun HabitRow(
    model: TodayHabitRowModel,
    colors: TodayListColors,
    onToggle: (String) -> Unit,
    onLogEntry: (habitId: String, value: Double, note: String, date: String) -> Unit,
    onRequestLog: (Habit) -> Unit,
    onSkip: (String) -> Unit,
    onShowSkipPrompt: (habitId: String) -> Unit
) {
    val habit = model.habit
    val isDone = model.isDone
    val isSkipped = model.isSkipped

    val container = when {
        isSkipped -> colors.surfaceVariant
        isDone && habit.type == HabitType.CHECKBOX -> colors.primary.copy(alpha = 0.22f)
        else -> colors.surface
    }

    val showCheck = habit.type == HabitType.CHECKBOX && isDone && !isSkipped
    val iconBg = when {
        isSkipped -> colors.error.copy(alpha = 0.18f)
        showCheck -> colors.primary
        isDone -> colors.primary.copy(alpha = 0.32f)
        else -> colors.primary.copy(alpha = 0.16f)
    }
    val iconFg = when {
        isSkipped -> Color(0xFFF87171)
        showCheck -> colors.onPrimary
        else -> colors.primary
    }
    val titleColor = if (isSkipped) Color(0xFFF87171) else colors.onSurface
    val indent = if (model.metaLine.contains("after ")) 8.dp else 0.dp

    // Box + background (not Material Surface/Card) — cheaper during fling
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(container, RoundedCornerShape(12.dp))
            .padding(start = 12.dp + indent, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlyphIcon(
            glyph = model.glyph,
            size = 22.dp,
            tintForSimple = colors.primary,
            modifier = Modifier.padding(end = 10.dp)
        )

        Column(
            Modifier
                .weight(1f)
                .padding(end = 6.dp)
        ) {
            // BasicText avoids Material Text overhead; titles are plain (no emoji)
            BasicText(
                text = model.title,
                style = TextStyle(
                    color = titleColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (model.metaLine.isNotBlank()) {
                BasicText(
                    text = model.metaLine,
                    style = TextStyle(color = colors.onSurfaceVariant, fontSize = 11.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (model.noteLine != null) {
                BasicText(
                    text = model.noteLine,
                    style = TextStyle(color = colors.primary, fontSize = 11.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            modifier = Modifier
                .combinedClickable(
                    onClick = {
                        if (habit.type == HabitType.CHECKBOX) {
                            onToggle(habit.id)
                        } else if (model.isSimpleTapAdd) {
                            onLogEntry(habit.id, habit.target ?: 1.0, "", LocalDate.now().toString())
                        } else {
                            onRequestLog(habit)
                        }
                    },
                    onLongClick = {
                        onSkip(habit.id)
                        if (habit.canSkip) onShowSkipPrompt(habit.id)
                    }
                )
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .defaultMinSize(minWidth = 48.dp, minHeight = 44.dp),
            contentAlignment = Alignment.Center
        ) {
            if (habit.type == HabitType.CHECKBOX) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(iconBg, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        showCheck -> Icon(
                            Icons.Default.Check,
                            contentDescription = "Done",
                            tint = iconFg,
                            modifier = Modifier.size(18.dp)
                        )
                        // ASCII only — no emoji font in the hot path
                        isSkipped -> BasicText(
                            text = ">",
                            style = TextStyle(color = iconFg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        )
                        else -> BasicText(
                            text = "o",
                            style = TextStyle(color = iconFg, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        )
                    }
                }
            } else {
                val action = when {
                    isSkipped -> "Skip"
                    isDone -> "Edit"
                    else -> "Log"
                }
                BasicText(
                    text = action,
                    style = TextStyle(
                        color = iconFg,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier
                        .background(iconBg, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
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
