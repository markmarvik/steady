package com.steady.habittracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.steady.habittracker.data.AppData
import com.steady.habittracker.data.AutoLogMode
import com.steady.habittracker.data.AutoSource
import com.steady.habittracker.data.Group
import com.steady.habittracker.data.Habit
import com.steady.habittracker.data.HabitDomain
import com.steady.habittracker.data.HabitType
import com.steady.habittracker.data.NotificationPrefs
import com.steady.habittracker.data.Reminder
import com.steady.habittracker.data.SleepAudioPrefs
import com.steady.habittracker.sensors.AutoLogMapper
import com.steady.habittracker.data.Schedule
import com.steady.habittracker.data.ShowPreset
import com.steady.habittracker.data.SleepSettings
import com.steady.habittracker.data.Tag
import com.steady.habittracker.data.TagIds
import com.steady.habittracker.data.TimeBlock
import com.steady.habittracker.data.displayGlyph
import com.steady.habittracker.data.displayLabel
import java.time.LocalDate

/**
 * Manage = four focused areas:
 * - Habits: create / edit / archive habits (+ tags, exercise routines)
 * - Groups: timeline groups + membership (attach existing habits, order, move)
 * - Blocks: special habit extensions (#33, #37)
 * - Planner: sleep/schedule, reminders, backup
 */
@Composable
fun ManageScreen(
    appData: AppData,
    onAddGroup: (String, String, String?, String) -> Unit,
    onAddHabit: (
        name: String,
        groupId: String,
        type: HabitType,
        isSupplement: Boolean,
        tags: List<String>,
        showPreset: ShowPreset,
        weekdays: Set<Int>,
        intervalDays: Int,
        specificDates: List<String>,
        additionalGroupIds: List<String>,
        icon: String
    ) -> Unit,
    onAddExtensionBlock: (com.steady.habittracker.data.ExtensionType, String?) -> Unit = { _, _ -> },
    onUpdateLocalWebPrefs: (com.steady.habittracker.data.LocalWebPrefs) -> Unit = {},
    onUpdateCapturePrefs: (com.steady.habittracker.data.CapturePrefs) -> Unit = {},
    onDeleteHabit: (String) -> Unit,  // now archives
    onSetReminder: (Reminder) -> Unit,
    onToggleReminder: (String) -> Unit,
    onSetRemindersMasterEnabled: (Boolean) -> Unit = {},
    onUpdateNotificationPrefs: (NotificationPrefs) -> Unit = {},
    onSetAutoLogMasterEnabled: (Boolean) -> Unit = {},
    onRunAutoLogNow: () -> Unit = {},
    onUpdateSleepAudioPrefs: (SleepAudioPrefs) -> Unit = {},
    onStartSleepAudio: () -> Unit = {},
    onStopSleepAudio: () -> Unit = {},
    onAlignRemindersToSchedule: () -> Unit = {},
    onArchiveGroup: (String) -> Unit = {},
    onExportCsv: () -> Unit = {},
    onImportCsv: () -> Unit = {},
    onUpdateHabit: (Habit) -> Unit = {},
    onUpdateGroup: (Group) -> Unit = {},
    onUnarchiveGroup: (String) -> Unit = {},
    onUnarchiveHabit: (String) -> Unit = {},
    onReorderHabit: (habitId: String, direction: Int, withinGroupId: String) -> Unit = { _, _, _ -> },
    onAddHabitToGroup: (habitId: String, groupId: String) -> Unit = { _, _ -> },
    onRemoveHabitFromGroup: (habitId: String, groupId: String) -> Unit = { _, _ -> },
    onMoveHabitToGroup: (habitId: String, groupId: String) -> Unit = { _, _ -> },
    onApplySchedulePreset: (name: String, blocks: List<TimeBlock>) -> Unit = { _, _ -> },
    onSetActiveSchedule: (String?) -> Unit = {},
    onUpdateScheduleBlocks: (scheduleId: String, blocks: List<TimeBlock>) -> Unit = { _, _ -> },
    onAddTag: (String) -> Unit = {},
    onApplySleepSchedule: (SleepSettings) -> Unit = {},
    onSaveRoutine: (com.steady.habittracker.data.ExerciseRoutine) -> Unit = {},
    onArchiveRoutine: (String) -> Unit = {},
    onLoadBlueprintRoutines: () -> Unit = {},
    onStartRoutine: (com.steady.habittracker.data.ExerciseRoutine) -> Unit = {},
    schedules: List<Schedule> = emptyList(),
    activeScheduleId: String? = null
) {
    // 0 Habits · 1 Groups · 2 Blocks · 3 Planner
    var manageTab by remember { mutableIntStateOf(0) }
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    var showAddGroup by remember { mutableStateOf(false) }
    /** Habits tab: create a new habit (optional initial group id). */
    var showCreateHabit by remember { mutableStateOf(false) }
    /** Groups tab: attach an existing habit into this group. */
    var attachHabitToGroupId by remember { mutableStateOf<String?>(null) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var reminderDialogGroup by remember { mutableStateOf<Group?>(null) }
    var showEditHabit by remember { mutableStateOf<Habit?>(null) }
    var showEditGroup by remember { mutableStateOf<Group?>(null) }
    var showRoutineEditor by remember { mutableStateOf<com.steady.habittracker.data.ExerciseRoutine?>(null) }
    var showNewRoutine by remember { mutableStateOf(false) }
    var habitSearch by remember { mutableStateOf("") }

    // Confirmation for archive (do not archive immediately; tap away / cancel = nothing)
    var confirmArchiveGroupId by remember { mutableStateOf<String?>(null) }
    var confirmArchiveHabitId by remember { mutableStateOf<String?>(null) }

    // Hoisted 24h schedule editor state (must be at composable root, not inside Lazy item)
    var isEditingSchedule by remember { mutableStateOf(false) }
    var editBlocks by remember(activeScheduleId) {
        mutableStateOf(schedules.firstOrNull { it.id == activeScheduleId }?.timeBlocks ?: emptyList())
    }

    val activeGroups = remember(appData.groups) {
        appData.groups.filter { !it.archived }.sortedBy { it.order }
    }
    val archivedGroups = remember(appData.groups) {
        appData.groups.filter { it.archived }.sortedBy { it.order }
    }
    val archivedHabits = remember(appData.habits) {
        appData.habits.filter { it.archived }
    }
    val groupOrder = remember(activeGroups) { activeGroups.mapIndexed { i, g -> g.id to i }.toMap() }
    val groupNameById = remember(activeGroups) { activeGroups.associate { it.id to it.name } }
    val tagLabelsByHabit = remember(appData.habits, appData.tags) {
        HabitDomain.tagLabelByHabitId(appData)
    }
    val activeHabitsSorted = remember(appData.habits, groupOrder) {
        appData.habits
            .filter { !it.archived }
            .sortedWith(
                compareBy<Habit> { groupOrder[it.groupId] ?: Int.MAX_VALUE }
                    .thenBy { it.order }
                    .thenBy { it.name.lowercase() }
            )
    }
    val q = habitSearch.trim()
    val filteredHabitRows = remember(activeHabitsSorted, q, groupNameById, tagLabelsByHabit) {
        val source = if (q.isEmpty()) {
            activeHabitsSorted
        } else {
            activeHabitsSorted.filter { h ->
                h.name.contains(q, ignoreCase = true) ||
                    HabitDomain.membershipGroupIds(h).any { gid ->
                        groupNameById[gid]?.contains(q, ignoreCase = true) == true
                    }
            }
        }
        source.map { h ->
            val groupNames = HabitDomain.membershipGroupIds(h)
                .mapNotNull { groupNameById[it] }
                .joinToString(", ")
            val tags = tagLabelsByHabit[h.id].orEmpty().let { full ->
                full.split(" · ").take(2).joinToString(", ")
            }
            ManageHabitRowModel(
                habit = h,
                glyph = h.displayGlyph(),
                subtitle = listOfNotNull(
                    groupNames.ifBlank { null },
                    HabitDomain.showRuleLabel(h),
                    if (!h.canSkip) "essential" else null,
                    tags.ifBlank { null }
                ).joinToString(" · ")
            )
        }
    }
    val activeRoutines = remember(appData.routines) { HabitDomain.getActiveRoutines(appData) }
    val activeTags = remember(appData.tags) { HabitDomain.getActiveTags(appData) }
    // Precompute group list rows (habit counts once, not per scroll frame)
    val groupListRows = remember(activeGroups, appData.habits, appData.sleep, appData.groups) {
        val habitsActive = appData.habits.filter { !it.archived }
        activeGroups.map { group ->
            val subCount = appData.groups.count { it.parentId == group.id && !it.archived }
            val count = habitsActive.count { HabitDomain.belongsToGroup(it, group.id) }
            val linked = when (group.id) {
                appData.sleep.morningGroupId -> " · wake"
                appData.sleep.bedtimeGroupId -> " · bed"
                appData.sleep.sleepGroupId -> " · sleep"
                else -> ""
            }
            ManageGroupRowModel(
                group = group,
                glyph = group.displayGlyph(),
                subtitle = "$count items · ${group.timeHint}$linked" +
                    if (subCount > 0) " · $subCount subgroups" else ""
            )
        }
    }
    val scheme = MaterialTheme.colorScheme
    val onSurface = scheme.onSurface
    val onVariant = scheme.onSurfaceVariant
    val primary = scheme.primary
    val surface = scheme.surface
    val error = scheme.error
    val surfaceVariant = scheme.surfaceVariant

    // Leaving Groups clears drill-down so Habits always feels top-level
    LaunchedEffect(manageTab) {
        if (manageTab != 1) selectedGroupId = null
    }

    // Sub-tabs sit directly under the main app tabs (no extra "Manage" title)
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(surfaceVariant, RoundedCornerShape(50))
                .padding(4.dp)
        ) {
            TabButton("Habits", manageTab == 0, modifier = Modifier.weight(1f)) { manageTab = 0 }
            TabButton("Groups", manageTab == 1, modifier = Modifier.weight(1f)) { manageTab = 1 }
            TabButton("Blocks", manageTab == 2, modifier = Modifier.weight(1f)) { manageTab = 2 }
            TabButton("Planner", manageTab == 3, modifier = Modifier.weight(1f)) { manageTab = 3 }
        }

        Spacer(Modifier.height(8.dp))

        when (manageTab) {
            0 -> {
                // Search + chrome outside LazyColumn so typing doesn't rebuild list structure as heavily
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${activeHabitsSorted.size} habits",
                        color = onVariant,
                        fontSize = 12.sp
                    )
                    TextButton(
                        onClick = { showCreateHabit = true },
                        enabled = activeGroups.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Add, null, tint = primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("Habit", color = primary, fontSize = 12.sp)
                    }
                }
                OutlinedTextField(
                    value = habitSearch,
                    onValueChange = { habitSearch = it },
                    label = { Text("Search habits") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (habitSearch.isNotEmpty()) {
                            IconButton(onClick = { habitSearch = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    }
                )
                Spacer(Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (activeGroups.isEmpty()) {
                        item(key = "no_groups", contentType = "msg") {
                            Text(
                                "Create a timeline group first (Groups tab), then add habits.",
                                color = onVariant,
                                fontSize = 13.sp
                            )
                        }
                    } else if (filteredHabitRows.isEmpty()) {
                        item(key = "no_habits", contentType = "msg") {
                            Text(
                                if (q.isEmpty()) "No habits yet. Tap + Habit to create one."
                                else "No matches for “$q”.",
                                color = onVariant,
                                fontSize = 13.sp
                            )
                        }
                    }
                    items(
                        filteredHabitRows,
                        key = { it.habit.id },
                        contentType = { "habit" }
                    ) { row ->
                        ManageHabitCatalogRow(
                            row = row,
                            onSurface = onSurface,
                            onVariant = onVariant,
                            surface = surface,
                            error = error,
                            onEdit = { showEditHabit = row.habit },
                            onArchive = { confirmArchiveHabitId = row.habit.id }
                        )
                    }

                    if (archivedHabits.isNotEmpty()) {
                        item(key = "arch_hdr", contentType = "hdr") {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Archived habits",
                                color = error,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        items(
                            archivedHabits,
                            key = { "arch_${it.id}" },
                            contentType = { "arch" }
                        ) { h ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "• ${h.displayLabel()}",
                                    color = onVariant,
                                    modifier = Modifier.weight(1f),
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
                                Text(
                                    "Restore",
                                    color = primary,
                                    fontSize = 11.sp,
                                    modifier = Modifier
                                        .clickable { onUnarchiveHabit(h.id) }
                                        .padding(8.dp)
                                )
                            }
                        }
                    }

                    item(key = "tags", contentType = "tags") {
                        Spacer(Modifier.height(8.dp))
                        TagManagerCard(
                            tags = activeTags,
                            habits = activeHabitsSorted,
                            onAddTag = onAddTag
                        )
                    }

                    item(key = "routines_hdr", contentType = "hdr") {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Exercise routines",
                                    color = primary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Structured workouts · Start from Today or here",
                                    color = onVariant,
                                    fontSize = 10.sp
                                )
                            }
                            Row {
                                TextButton(onClick = onLoadBlueprintRoutines) {
                                    Text("Templates", color = primary, fontSize = 11.sp)
                                }
                                TextButton(onClick = { showNewRoutine = true }) {
                                    Text("+ New", color = primary, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    if (activeRoutines.isEmpty()) {
                        item(key = "no_rt", contentType = "msg") {
                            Text(
                                "No routines yet. Load Blueprint templates or create one.",
                                color = onVariant,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        items(
                            activeRoutines,
                            key = { it.id },
                            contentType = { "routine" }
                        ) { rt ->
                            val rule = remember(rt.id, rt.showPreset, rt.weekdays) {
                                HabitDomain.showRuleLabel(
                                    Habit(
                                        id = rt.id, name = rt.name, groupId = "",
                                        showPreset = rt.showPreset, weekdays = rt.weekdays
                                    )
                                )
                            }
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = surface,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        rt.name,
                                        color = onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        listOfNotNull(
                                            "${rt.exercises.size} exercises",
                                            "~${rt.estimatedDurationMin} min",
                                            rule,
                                            rt.tags.take(2).joinToString(", ").ifBlank { null }
                                        ).joinToString(" · "),
                                        color = onVariant,
                                        fontSize = 11.sp
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(
                                            "Start",
                                            color = primary,
                                            fontSize = 12.sp,
                                            modifier = Modifier
                                                .clickable { onStartRoutine(rt) }
                                                .padding(vertical = 6.dp)
                                        )
                                        Text(
                                            "Edit",
                                            color = onVariant,
                                            fontSize = 12.sp,
                                            modifier = Modifier
                                                .clickable { showRoutineEditor = rt }
                                                .padding(vertical = 6.dp)
                                        )
                                        Text(
                                            "Archive",
                                            color = error,
                                            fontSize = 11.sp,
                                            modifier = Modifier
                                                .clickable { onArchiveRoutine(rt.id) }
                                                .padding(vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item(key = "pad", contentType = "pad") { Spacer(Modifier.height(12.dp)) }
                }
            }

            1 -> {
                // ——— Groups: list or drill-down ———
                if (selectedGroupId == null) {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item(key = "g_hdr", contentType = "hdr") {
                            ManageSectionHeader(
                                title = "Timeline groups",
                                subtitle = "When on the day · attach habits from the Habits catalog",
                                action = {
                                    TextButton(onClick = { showAddGroup = true }) {
                                        Icon(Icons.Default.Add, null, tint = primary, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(2.dp))
                                        Text("Group", color = primary, fontSize = 12.sp)
                                    }
                                }
                            )
                        }
                        items(
                            groupListRows,
                            key = { it.group.id },
                            contentType = { "group" }
                        ) { row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(surface, RoundedCornerShape(12.dp))
                                    .clickable { selectedGroupId = row.group.id }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GlyphIcon(
                                    glyph = row.glyph,
                                    size = 24.dp,
                                    tintForSimple = primary,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                                Column(Modifier.weight(1f)) {
                                    BasicText(
                                        text = row.group.name,
                                        style = TextStyle(
                                            color = onSurface,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    BasicText(
                                        text = row.subtitle,
                                        style = TextStyle(color = onVariant, fontSize = 11.sp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { showEditGroup = row.group }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit group", tint = onVariant)
                                }
                            }
                        }
                        if (archivedGroups.isNotEmpty()) {
                            item(key = "ag_hdr", contentType = "hdr") {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Archived groups",
                                    color = error,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            items(
                                archivedGroups,
                                key = { "ag_${it.id}" },
                                contentType = { "arch_g" }
                            ) { g ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = surfaceVariant,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "${g.displayGlyph()} ${g.name}",
                                            color = onSurface,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1
                                        )
                                        Text(
                                            "Restore",
                                            color = primary,
                                            fontSize = 12.sp,
                                            modifier = Modifier
                                                .clickable { onUnarchiveGroup(g.id) }
                                                .padding(8.dp)
                                        )
                                    }
                                }
                            }
                        }
                        item(key = "g_pad", contentType = "pad") { Spacer(Modifier.height(12.dp)) }
                    }
                } else {
                    val group = activeGroups.find { it.id == selectedGroupId } ?: run {
                        selectedGroupId = null
                        return@Column
                    }
                    val habitsInGroup = remember(appData.habits, group.id) {
                        HabitDomain.getActiveHabitsForGroup(appData, group.id)
                    }
                    val groupMemberRows = remember(habitsInGroup, tagLabelsByHabit) {
                        habitsInGroup.map { h ->
                            val memberCount = HabitDomain.membershipGroupIds(h).size
                            val tags = tagLabelsByHabit[h.id].orEmpty()
                                .split(" · ").take(2).joinToString(", ")
                            ManageGroupHabitRowModel(
                                habit = h,
                                glyph = h.displayGlyph(),
                                memberCount = memberCount,
                                subtitle = listOfNotNull(
                                    HabitDomain.showRuleLabel(h),
                                    if (memberCount > 1) "$memberCount groups" else null,
                                    tags.ifBlank { null }
                                ).joinToString(" · ")
                            )
                        }
                    }
                    val subs = remember(appData.groups, group.id) {
                        appData.groups.filter { it.parentId == group.id && !it.archived }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedGroupId = null }
                                .padding(vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to groups",
                                tint = onSurface,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                group.displayLabel(),
                                color = onSurface,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = { showEditGroup = group }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit group", tint = onVariant)
                        }
                        Text(
                            "Archive",
                            color = error,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clickable { confirmArchiveGroupId = group.id }
                                .padding(8.dp)
                        )
                    }

                    if (subs.isNotEmpty()) {
                        Text("Subgroups / Plans", color = onVariant, fontSize = 12.sp)
                        subs.forEach { sub ->
                            Text("  • ${sub.name}", color = onSurface, modifier = Modifier.padding(4.dp))
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Habits here appear on Today in this block. Same habit can be in many groups — once each. Create new habits on the Habits tab.",
                        color = onVariant,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    TextButton(onClick = { attachHabitToGroupId = group.id }) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp), tint = primary)
                        Spacer(Modifier.width(2.dp))
                        Text("Add habit", color = primary, fontSize = 12.sp)
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f)
                    ) {
                        items(
                            groupMemberRows,
                            key = { it.habit.id },
                            contentType = { "gh" }
                        ) { row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .background(surface, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GlyphIcon(
                                    glyph = row.glyph,
                                    size = 20.dp,
                                    tintForSimple = onVariant,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Column(Modifier.weight(1f)) {
                                    BasicText(
                                        text = row.habit.name,
                                        style = TextStyle(
                                            color = onSurface,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    BasicText(
                                        text = row.subtitle,
                                        style = TextStyle(color = onVariant, fontSize = 11.sp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                BasicText(
                                    text = "Up",
                                    style = TextStyle(color = onVariant, fontSize = 12.sp),
                                    modifier = Modifier
                                        .clickable { onReorderHabit(row.habit.id, -1, group.id) }
                                        .padding(8.dp)
                                )
                                BasicText(
                                    text = "Dn",
                                    style = TextStyle(color = onVariant, fontSize = 12.sp),
                                    modifier = Modifier
                                        .clickable { onReorderHabit(row.habit.id, 1, group.id) }
                                        .padding(8.dp)
                                )
                                if (row.memberCount > 1) {
                                    BasicText(
                                        text = "Remove",
                                        style = TextStyle(color = error, fontSize = 11.sp),
                                        modifier = Modifier
                                            .clickable { onRemoveHabitFromGroup(row.habit.id, group.id) }
                                            .padding(8.dp)
                                    )
                                }
                            }
                        }
                        if (groupMemberRows.isEmpty()) {
                            item(key = "empty_g", contentType = "msg") {
                                Text(
                                    "No habits in this group yet. Create habits on the Habits tab, then add them here.",
                                    color = onVariant,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                        }
                    }
                }
            }

            2 -> {
                // ——— Blocks: special habit extensions (#33, #37) ———
                BlocksConfigSection(
                    appData = appData,
                    groups = activeGroups,
                    onAddExtension = onAddExtensionBlock,
                    onEditHabit = { showEditHabit = it },
                    onUpdateLocalWebPrefs = onUpdateLocalWebPrefs,
                    onUpdateNotificationPrefs = onUpdateNotificationPrefs,
                    onUpdateCapturePrefs = onUpdateCapturePrefs
                )
            }

            else -> {
                // ——— Planner: schedule + reminders + backup ———
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        DailyPlannerCard(
                            appData = appData,
                            groups = activeGroups,
                            schedules = schedules,
                            activeScheduleId = activeScheduleId,
                            isEditingSchedule = isEditingSchedule,
                            editBlocks = editBlocks,
                            onEditBlocksChange = { editBlocks = it },
                            onToggleEditing = {
                                if (!isEditingSchedule) {
                                    editBlocks = schedules.firstOrNull { it.id == activeScheduleId }?.timeBlocks
                                        ?: emptyList()
                                }
                                isEditingSchedule = !isEditingSchedule
                            },
                            onCloseEditing = { isEditingSchedule = false },
                            onApplySleep = onApplySleepSchedule,
                            onSetActiveSchedule = onSetActiveSchedule,
                            onUpdateScheduleBlocks = onUpdateScheduleBlocks,
                            onApplySchedulePreset = onApplySchedulePreset
                        )
                    }
                    item {
                        RemindersCard(
                            appData = appData,
                            onToggleMaster = onSetRemindersMasterEnabled,
                            onToggleReminder = onToggleReminder,
                            onEditReminder = { rem ->
                                reminderDialogGroup = rem.groupId?.let { gid ->
                                    appData.groups.find { it.id == gid }
                                }
                                showReminderDialog = true
                            },
                            onAlignToSchedule = onAlignRemindersToSchedule,
                            onUpdateNotificationPrefs = onUpdateNotificationPrefs
                        )
                    }
                    item {
                        AutoLogCard(
                            appData = appData,
                            onToggleMaster = onSetAutoLogMasterEnabled,
                            onSyncNow = onRunAutoLogNow
                        )
                    }
                    item {
                        SleepAudioCard(
                            appData = appData,
                            onUpdatePrefs = onUpdateSleepAudioPrefs,
                            onStartNow = onStartSleepAudio,
                            onStopNow = onStopSleepAudio
                        )
                    }
                    item {
                        Spacer(Modifier.height(4.dp))
                        Text("Backup", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onExportCsv,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Export Backup")
                            }
                            OutlinedButton(onClick = onImportCsv) {
                                Text("Import Backup")
                            }
                        }
                        Text(
                            "Full JSON backup: habits, logs, Momentum score, reminders, Path, settings. Import replaces current data.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
    }

    // Dialogs
    if (showAddGroup) {
        AddGroupDialogWithParent(
            onDismiss = { showAddGroup = false },
            onAdd = { n, h, p, icon -> onAddGroup(n, h, p, icon); showAddGroup = false }
        )
    }
    showEditGroup?.let { g ->
        EditGroupDialog(
            group = g,
            onDismiss = { showEditGroup = null },
            onSave = { onUpdateGroup(it); showEditGroup = null }
        )
    }
    if (showCreateHabit) {
        AddHabitWithTypeDialogLocal(
            // No default group (#30) — user must select at least one
            initialGroupId = "",
            groups = activeGroups,
            tags = HabitDomain.getActiveTags(appData),
            onDismiss = { showCreateHabit = false },
            onAdd = onAddHabit
        )
    }
    attachHabitToGroupId?.let { gid ->
        val groupName = activeGroups.find { it.id == gid }?.name ?: "group"
        val candidates = appData.habits
            .filter { !it.archived }
            .filter { h -> !HabitDomain.belongsToGroup(h, gid) }
            .sortedBy { it.name.lowercase() }
        AlertDialog(
            onDismissRequest = { attachHabitToGroupId = null },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text("Add habit to $groupName") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Pick from your Habits catalog. Each habit joins a group only once, but can be in as many groups as you want. Still one log per day.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    if (candidates.isEmpty()) {
                        Text(
                            "Every habit is already in this group, or you have no habits yet. Create habits on the Habits tab first.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        candidates.forEach { h ->
                            val inGroups = HabitDomain.membershipGroupIds(h).mapNotNull { id ->
                                activeGroups.find { it.id == id }?.name
                            }.joinToString(", ")
                            TextButton(
                                onClick = {
                                    onAddHabitToGroup(h.id, gid)
                                    attachHabitToGroupId = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (inGroups.isBlank()) h.displayLabel()
                                    else "${h.displayLabel()}  ·  $inGroups",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { attachHabitToGroupId = null }) { Text("Close") }
            }
        )
    }
    if (showReminderDialog) {
        val g = reminderDialogGroup
        val ex = if (g != null) {
            appData.reminders.firstOrNull { it.groupId == g.id }
        } else {
            appData.reminders.firstOrNull { it.groupId == null }
        }
        ReminderDialog(
            group = g,
            existing = ex,
            appData = appData,
            onDismiss = { showReminderDialog = false },
            onSave = onSetReminder
        )
    }
    showEditHabit?.let { h ->
        EditHabitDialog(
            habit = h,
            groups = activeGroups,
            tags = HabitDomain.getActiveTags(appData),
            onDismiss = { showEditHabit = null },
            onSave = { updated ->
                onUpdateHabit(updated)
                showEditHabit = null
            },
            onArchive = { confirmArchiveHabitId = h.id; showEditHabit = null }
        )
    }
    if (showNewRoutine) {
        RoutineEditorDialog(
            existing = null,
            onDismiss = { showNewRoutine = false },
            onSave = { rt -> onSaveRoutine(rt); showNewRoutine = false }
        )
    }
    showRoutineEditor?.let { rt ->
        RoutineEditorDialog(
            existing = rt,
            onDismiss = { showRoutineEditor = null },
            onSave = { updated -> onSaveRoutine(updated); showRoutineEditor = null },
            onArchive = { onArchiveRoutine(rt.id); showRoutineEditor = null }
        )
    }
    // Archive confirmations - proper color, not immediate, tap away = nothing
    confirmArchiveGroupId?.let { gid ->
        AlertDialog(
            onDismissRequest = { confirmArchiveGroupId = null },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text("Archive this group?") },
            text = { Text("The group and its habits will be moved to Archived. You can restore them later from the Archived section. Tap outside or Cancel to do nothing.") },
            confirmButton = {
                TextButton(onClick = {
                    onArchiveGroup(gid)
                    if (selectedGroupId == gid) selectedGroupId = null
                    confirmArchiveGroupId = null
                }) { Text("Archive", color = Color(0xFFF87171)) }
            },
            dismissButton = { TextButton(onClick = { confirmArchiveGroupId = null }) { Text("Cancel") } }
        )
    }

    confirmArchiveHabitId?.let { hid ->
        AlertDialog(
            onDismissRequest = { confirmArchiveHabitId = null },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text("Archive this habit?") },
            text = { Text("The habit will be moved to Archived (history preserved). You can restore it later. Tap outside or Cancel to do nothing.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteHabit(hid)
                    confirmArchiveHabitId = null
                }) { Text("Archive", color = Color(0xFFF87171)) }
            },
            dismissButton = { TextButton(onClick = { confirmArchiveHabitId = null }) { Text("Cancel") } }
        )
    }
}

// Simple add group supporting parent (for workouts sub groups)
@Composable
private fun AddGroupDialogWithParent(
    onDismiss: () -> Unit,
    onAdd: (name: String, hint: String, parent: String?, icon: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("ANY") }
    var parent by remember { mutableStateOf<String?>(null) }
    var icon by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text("New Group / Plan") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                EmojiIconPicker(selected = icon, onSelect = { icon = it })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = hint, onValueChange = { hint = it }, label = { Text("Time hint") }, modifier = Modifier.fillMaxWidth())
                Text("Parent (leave empty or set for Workout subgroup)", fontSize = 11.sp)
                OutlinedTextField(value = parent ?: "", onValueChange = { parent = it.ifBlank { null } }, label = { Text("Parent ID (e.g. g_workout)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onAdd(name, hint, parent, icon) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun EditGroupDialog(
    group: Group,
    onDismiss: () -> Unit,
    onSave: (Group) -> Unit
) {
    var name by remember { mutableStateOf(group.name) }
    var hint by remember { mutableStateOf(group.timeHint) }
    var icon by remember { mutableStateOf(group.icon) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text("Edit group") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                EmojiIconPicker(selected = icon, onSelect = { icon = it })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = hint,
                    onValueChange = { hint = it },
                    label = { Text("Time hint") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(group.copy(name = name.trim(), timeHint = hint.trim(), icon = icon.trim()))
                    }
                },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun ShowWhenPicker(
    preset: ShowPreset,
    weekdays: Set<Int>,
    intervalDays: Int,
    specificDates: List<String>,
    onPreset: (ShowPreset) -> Unit,
    onWeekdays: (Set<Int>) -> Unit,
    onInterval: (Int) -> Unit,
    onDates: (List<String>) -> Unit
) {
    Text("When (on Today)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(4.dp))
    val chips = listOf(
        ShowPreset.DAILY to "Daily",
        ShowPreset.WEEKDAYS to "Weekdays",
        ShowPreset.WEEKENDS to "Weekends",
        ShowPreset.CUSTOM_DAYS to "Custom",
        ShowPreset.EVERY_N_DAYS to "Every N",
        ShowPreset.SPECIFIC_DATES to "Dates"
    )
    chips.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 4.dp)) {
            row.forEach { (pr, label) ->
                ThemedFilterChip(selected = preset == pr, onClick = { onPreset(pr) }, label = { Text(label, fontSize = 11.sp) })
            }
        }
    }
    when (preset) {
        ShowPreset.CUSTOM_DAYS -> {
            // Wrap weekdays so Sat/Sun fit (#30)
            listOf(
                listOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu"),
                listOf(5 to "Fri", 6 to "Sat", 7 to "Sun")
            ).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                ) {
                    row.forEach { (d, lab) ->
                        ThemedFilterChip(
                            selected = d in weekdays,
                            onClick = {
                                onWeekdays(if (d in weekdays) weekdays - d else weekdays + d)
                            },
                            label = { Text(lab, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        ShowPreset.EVERY_N_DAYS -> {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(2, 3, 7).forEach { n ->
                    ThemedFilterChip(
                        selected = intervalDays == n && intervalDays in listOf(2, 3, 7),
                        onClick = { onInterval(n); onPreset(ShowPreset.EVERY_N_DAYS) },
                        label = { Text("Every ${n}d", fontSize = 11.sp) }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            var customN by remember(intervalDays) {
                mutableStateOf(if (intervalDays in listOf(2, 3, 7)) "" else intervalDays.toString())
            }
            OutlinedTextField(
                value = customN,
                onValueChange = { v ->
                    customN = v.filter { it.isDigit() }.take(3)
                    val n = customN.toIntOrNull()
                    if (n != null && n >= 1) {
                        onInterval(n)
                        onPreset(ShowPreset.EVERY_N_DAYS)
                    }
                },
                label = { Text("Custom every N days") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. 5") }
            )
        }
        ShowPreset.SPECIFIC_DATES -> {
            var dateInput by remember { mutableStateOf(LocalDate.now().toString()) }
            OutlinedTextField(
                value = dateInput,
                onValueChange = { dateInput = it },
                label = { Text("Add date yyyy-MM-dd") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(onClick = {
                try {
                    val d = LocalDate.parse(dateInput).toString()
                    if (d !in specificDates) onDates(specificDates + d)
                } catch (_: Exception) {}
            }) { Text("Add date", fontSize = 11.sp) }
            specificDates.forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(d, modifier = Modifier.weight(1f), fontSize = 12.sp)
                    TextButton(onClick = { onDates(specificDates.filter { it != d }) }) {
                        Text("x", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        else -> {}
    }
}


private fun habitTypeLabel(type: HabitType): String = when (type) {
    HabitType.CHECKBOX -> "Check"
    HabitType.COUNTER -> "Counter"
    HabitType.DURATION_MIN -> "Duration"
    HabitType.SCALE_1_5 -> "Scale"
    HabitType.NOTE -> "Note"
}

@Composable
private fun HabitTypeChipRow(
    selected: HabitType,
    onSelect: (HabitType) -> Unit
) {
    HabitType.entries.chunked(3).forEach { row ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp)
        ) {
            row.forEach { t ->
                ThemedFilterChip(
                    selected = selected == t,
                    onClick = { onSelect(t) },
                    label = { Text(habitTypeLabel(t), fontSize = 12.sp) }
                )
            }
        }
    }
}

/**
 * Flat multi-group membership editor for habit create/edit.
 * Shows every group the habit is in as a full row; + Add joins another group once.
 * Storage: first id → groupId, rest → additionalGroupIds (transparent to the user).
 */
@Composable
private fun GroupMembershipSection(
    groups: List<Group>,
    selectedIds: List<String>,
    onSelectedIdsChange: (List<String>) -> Unit
) {
    var showAddPicker by remember { mutableStateOf(false) }
    val byId = remember(groups) { groups.associateBy { it.id } }
    val available = groups.filter { it.id !in selectedIds }

    Text("Groups", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    Text(
        "Appears on Today in each listed group · once per group · one log per day",
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(6.dp))

    selectedIds.forEach { id ->
        val g = byId[id]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                g?.name ?: id,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            if (selectedIds.size > 1) {
                TextButton(
                    onClick = { onSelectedIdsChange(selectedIds.filter { it != id }) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            } else {
                Text(
                    "required",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
    }

    if (selectedIds.isEmpty()) {
        Text(
            "Add at least one group",
            color = MaterialTheme.colorScheme.error,
            fontSize = 11.sp
        )
    }

    if (available.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = { showAddPicker = true },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add to another group", fontSize = 13.sp)
        }
    }

    if (showAddPicker) {
        AlertDialog(
            onDismissRequest = { showAddPicker = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text("Add to group") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Choose a group this habit is not in yet.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    available.forEach { g ->
                        TextButton(
                            onClick = {
                                onSelectedIdsChange((selectedIds + g.id).distinct())
                                showAddPicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(g.name, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddPicker = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun EditHabitDialog(
    habit: Habit,
    groups: List<Group>,
    tags: List<Tag>,
    onDismiss: () -> Unit,
    onSave: (Habit) -> Unit,
    onArchive: () -> Unit
) {
    var name by remember { mutableStateOf(habit.name) }
    var icon by remember { mutableStateOf(habit.icon) }
    var type by remember { mutableStateOf(habit.type) }
    var canSkip by remember { mutableStateOf(habit.canSkip) }
    var defaultValue by remember { mutableStateOf(habit.target?.toString() ?: "") }
    var unit by remember { mutableStateOf(habit.unit) }
    var selectedTags by remember {
        mutableStateOf(
            habit.tags.toSet() + if (habit.isSupplement) setOf(TagIds.SUPPLEMENTS) else emptySet()
        )
    }
    // Flat membership set (first id is storage primary only)
    var selectedGroupIds by remember {
        mutableStateOf(HabitDomain.membershipGroupIds(habit))
    }
    var showPreset by remember { mutableStateOf(habit.showPreset) }
    var weekdays by remember { mutableStateOf(habit.weekdays) }
    var intervalDays by remember { mutableIntStateOf(habit.intervalDays.coerceAtLeast(1)) }
    var specificDates by remember { mutableStateOf(habit.specificDates) }
    var autoSource by remember { mutableStateOf(habit.autoSource) }
    var autoMode by remember { mutableStateOf(habit.autoMode) }
    var autoThreshold by remember {
        mutableStateOf(habit.autoThreshold?.toString() ?: "")
    }
    var autoMetricKey by remember { mutableStateOf(habit.autoMetricKey) }
    var remEnabled by remember { mutableStateOf(habit.habitReminder.enabled) }
    var remTime by remember { mutableStateOf(habit.habitReminder.time) }
    var remMissed by remember { mutableStateOf(habit.habitReminder.remindOnMissed) }
    var remStrength by remember { mutableStateOf(habit.habitReminder.strength) }
    var extType by remember { mutableStateOf(habit.extensionType) }
    var extSensors by remember {
        mutableStateOf(habit.extensionConfig.sensors.toSet())
    }
    var extChainAfter by remember {
        mutableStateOf(habit.extensionConfig.chainAfterHabitId.orEmpty())
    }
    var extLimitMin by remember {
        mutableStateOf(habit.extensionConfig.dailyLimitMinutes?.toString().orEmpty())
    }
    var extPomodoroWork by remember {
        mutableIntStateOf(habit.extensionConfig.pomodoroWorkMin)
    }
    var extIncludeApps by remember {
        mutableStateOf(habit.extensionConfig.includeAppBreakdown)
    }
    var extPackages by remember {
        mutableStateOf(habit.extensionConfig.packages)
    }
    var showAppPicker by remember { mutableStateOf(false) }

    val tagIds = remember(tags) { tags.map { it.id }.toSet() }
    LaunchedEffect(tagIds) {
        selectedTags = selectedTags.filter { it in tagIds || it == TagIds.SUPPLEMENTS }.toSet()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text("Edit ${habit.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                EmojiIconPicker(selected = icon, onSelect = { icon = it })
                Spacer(Modifier.height(8.dp))
                Text("Type", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                HabitTypeChipRow(selected = type, onSelect = { type = it })
                Spacer(Modifier.height(8.dp))
                GroupMembershipSection(
                    groups = groups,
                    selectedIds = selectedGroupIds,
                    onSelectedIdsChange = { selectedGroupIds = it }
                )
                Spacer(Modifier.height(8.dp))
                ShowWhenPicker(
                    preset = showPreset,
                    weekdays = weekdays,
                    intervalDays = intervalDays,
                    specificDates = specificDates,
                    onPreset = { showPreset = it },
                    onWeekdays = { weekdays = it },
                    onInterval = { intervalDays = it },
                    onDates = { specificDates = it }
                )
                Spacer(Modifier.height(8.dp))
                Text("Tags (what — for History)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                TagChipRow(tags = tags, selected = selectedTags) { id ->
                    selectedTags = if (id in selectedTags) selectedTags - id else selectedTags + id
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ThemedCheckbox(checked = !canSkip, onCheckedChange = { canSkip = !it })
                    Text("Essential (harder to skip)", fontSize = 12.sp)
                }
                if (type != HabitType.CHECKBOX && type != HabitType.NOTE) {
                    OutlinedTextField(
                        value = defaultValue,
                        onValueChange = { defaultValue = it },
                        label = { Text("Default value") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("Reminder (this habit)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ThemedCheckbox(checked = remEnabled, onCheckedChange = { remEnabled = it })
                    Text("Enable reminder", fontSize = 12.sp)
                }
                if (remEnabled) {
                    OutlinedTextField(
                        value = remTime,
                        onValueChange = { remTime = it },
                        label = { Text("Time HH:mm") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ThemedCheckbox(checked = remMissed, onCheckedChange = { remMissed = it })
                        Text("Also for missed (pending only fires)", fontSize = 11.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        com.steady.habittracker.data.ReminderStrength.entries.forEach { s ->
                            FilterChip(
                                selected = remStrength == s,
                                onClick = { remStrength = s },
                                label = { Text(s.name.lowercase(), fontSize = 10.sp) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Special block type (#33)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    com.steady.habittracker.data.ExtensionCatalog.label(extType),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                com.steady.habittracker.data.ExtensionType.entries.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                        row.forEach { t ->
                            FilterChip(
                                selected = extType == t,
                                onClick = { extType = t },
                                label = {
                                    Text(
                                        com.steady.habittracker.data.ExtensionCatalog.label(t),
                                        fontSize = 9.sp
                                    )
                                }
                            )
                        }
                    }
                }
                if (extType == com.steady.habittracker.data.ExtensionType.SENSOR_AUTO_READ) {
                    Spacer(Modifier.height(6.dp))
                    Text("Sensors to capture", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    com.steady.habittracker.data.SensorKind.entries.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                            row.forEach { sk ->
                                FilterChip(
                                    selected = sk in extSensors,
                                    onClick = {
                                        extSensors = if (sk in extSensors) extSensors - sk else extSensors + sk
                                    },
                                    label = { Text(sk.name, fontSize = 9.sp) }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = extChainAfter,
                        onValueChange = { extChainAfter = it },
                        label = { Text("Chain after habit id (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (extType == com.steady.habittracker.data.ExtensionType.SCREEN_USAGE) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = extLimitMin,
                        onValueChange = { extLimitMin = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text("Soft daily limit (minutes)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ThemedCheckbox(checked = extIncludeApps, onCheckedChange = { extIncludeApps = it })
                        Text("Include apps breakdown", fontSize = 12.sp)
                    }
                    if (extIncludeApps) {
                        Text(
                            if (extPackages.isEmpty()) "Tracking: top apps overall"
                            else "Tracking ${extPackages.size} app(s)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { showAppPicker = true }) {
                            Text("Pick apps…", fontSize = 12.sp)
                        }
                        if (extPackages.isNotEmpty()) {
                            TextButton(onClick = { extPackages = emptyList() }) {
                                Text("Clear app filter", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                if (showAppPicker) {
                    AppPackagePickerDialog(
                        selectedPackages = extPackages,
                        onDismiss = { showAppPicker = false },
                        onConfirm = { extPackages = it; showAppPicker = false }
                    )
                }
                if (extType == com.steady.habittracker.data.ExtensionType.POMODORO) {
                    Spacer(Modifier.height(6.dp))
                    Text("Work minutes: $extPomodoroWork", fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(15, 25, 45, 50).forEach { n ->
                            FilterChip(
                                selected = extPomodoroWork == n,
                                onClick = { extPomodoroWork = n },
                                label = { Text("${n}m", fontSize = 11.sp) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Auto-log (sensors)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "On-device only. Grant permissions in Planner if needed.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                AutoSourceChipRow(selected = autoSource, onSelect = { autoSource = it })
                if (autoSource != AutoSource.NONE) {
                    Spacer(Modifier.height(6.dp))
                    Text("When data arrives", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = autoMode == AutoLogMode.SUGGEST,
                            onClick = { autoMode = AutoLogMode.SUGGEST },
                            label = { Text("Suggest", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = autoMode == AutoLogMode.AUTO_APPLY,
                            onClick = { autoMode = AutoLogMode.AUTO_APPLY },
                            label = { Text("Auto-apply", fontSize = 11.sp) }
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = autoThreshold,
                        onValueChange = { autoThreshold = it },
                        label = {
                            Text(
                                when (autoSource) {
                                    AutoSource.LIGHT_DARK_CHECK, AutoSource.LIGHT_BEDTIME_AVG -> "Max lux (dark = under)"
                                    AutoSource.SCREEN_AFTER_WINDDOWN, AutoSource.SCREEN_MINUTES -> "Max minutes to pass (checkbox)"
                                    AutoSource.NOISE_EVENING_DB -> "Max ~dB to pass (checkbox)"
                                    AutoSource.PHONE_STEPS, AutoSource.GADGETBRIDGE_STEPS -> "Step goal (checkbox)"
                                    else -> "Threshold (optional)"
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (autoSource == AutoSource.EXTERNAL_METRIC || autoSource == AutoSource.GADGETBRIDGE_STEPS) {
                        OutlinedTextField(
                            value = autoMetricKey,
                            onValueChange = { autoMetricKey = it },
                            label = { Text("Metric key (e.g. steps)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("steps") }
                        )
                    }
                    Text(
                        AutoLogMapper.sourceLabel(autoSource),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (selectedGroupIds.isEmpty()) return@TextButton
                    val target = defaultValue.toDoubleOrNull()
                    val anchor = if (showPreset == ShowPreset.EVERY_N_DAYS) {
                        habit.anchorDate ?: LocalDate.now().toString()
                    } else habit.anchorDate
                    val tagList = selectedTags.filter { it in tagIds || it == TagIds.SUPPLEMENTS }
                    val withGroups = HabitDomain.withMembership(habit, selectedGroupIds)
                    onSave(
                        withGroups.copy(
                            name = name.trim(),
                            type = type,
                            canSkip = canSkip,
                            target = target,
                            unit = unit,
                            isSupplement = TagIds.SUPPLEMENTS in tagList,
                            tags = tagList,
                            showPreset = showPreset,
                            weekdays = if (weekdays.isEmpty()) setOf(1, 2, 3, 4, 5, 6, 7) else weekdays,
                            intervalDays = intervalDays,
                            anchorDate = anchor,
                            specificDates = specificDates,
                            icon = icon.trim(),
                            autoSource = autoSource,
                            autoMode = autoMode,
                            autoThreshold = autoThreshold.toDoubleOrNull(),
                            autoMetricKey = autoMetricKey.trim(),
                            extensionType = extType,
                            extensionConfig = habit.extensionConfig.copy(
                                sensors = if (extType == com.steady.habittracker.data.ExtensionType.SENSOR_AUTO_READ) {
                                    extSensors.toList().ifEmpty {
                                        listOf(
                                            com.steady.habittracker.data.SensorKind.STEPS,
                                            com.steady.habittracker.data.SensorKind.SCREEN
                                        )
                                    }
                                } else habit.extensionConfig.sensors,
                                chainAfterHabitId = extChainAfter.trim().ifBlank { null },
                                dailyLimitMinutes = extLimitMin.toIntOrNull(),
                                includeAppBreakdown = extIncludeApps,
                                packages = if (extType == com.steady.habittracker.data.ExtensionType.SCREEN_USAGE) {
                                    extPackages
                                } else habit.extensionConfig.packages,
                                pomodoroWorkMin = extPomodoroWork
                            ),
                            habitReminder = com.steady.habittracker.data.HabitReminderPrefs(
                                enabled = remEnabled,
                                time = remTime.ifBlank { "09:00" },
                                strength = remStrength,
                                remindOnMissed = remMissed
                            )
                        )
                    )
                },
                enabled = selectedGroupIds.isNotEmpty() && name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onArchive) { Text("Archive", color = Color(0xFFF87171)) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun AutoSourceChipRow(selected: AutoSource, onSelect: (AutoSource) -> Unit) {
    val options = listOf(
        AutoSource.NONE to "Off",
        AutoSource.SCREEN_MINUTES to "Screen min",
        AutoSource.SCREEN_AFTER_WINDDOWN to "Eve screen",
        AutoSource.LIGHT_BEDTIME_AVG to "Light lux",
        AutoSource.LIGHT_DARK_CHECK to "Dark room",
        AutoSource.NOISE_EVENING_DB to "Noise",
        AutoSource.PHONE_STEPS to "Phone steps",
        AutoSource.GADGETBRIDGE_STEPS to "GB steps",
        AutoSource.EXTERNAL_METRIC to "External"
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { (src, label) ->
                    FilterChip(
                        selected = selected == src,
                        onClick = { onSelect(src) },
                        label = { Text(label, fontSize = 10.sp) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AddHabitWithTypeDialogLocal(
    initialGroupId: String,
    groups: List<Group>,
    tags: List<Tag>,
    onDismiss: () -> Unit,
    onAdd: (
        name: String,
        groupId: String,
        type: HabitType,
        isSupplement: Boolean,
        tags: List<String>,
        showPreset: ShowPreset,
        weekdays: Set<Int>,
        intervalDays: Int,
        specificDates: List<String>,
        additionalGroupIds: List<String>,
        icon: String
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(HabitType.CHECKBOX) }
    var selectedGroupIds by remember {
        mutableStateOf(if (initialGroupId.isNotBlank()) listOf(initialGroupId) else emptyList())
    }
    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    var showPreset by remember { mutableStateOf(ShowPreset.DAILY) }
    var weekdays by remember { mutableStateOf(setOf(1, 2, 3, 4, 5, 6, 7)) }
    var intervalDays by remember { mutableIntStateOf(2) }
    var specificDates by remember { mutableStateOf(listOf<String>()) }
    var addedCount by remember { mutableIntStateOf(0) }

    val tagIds = remember(tags) { tags.map { it.id }.toSet() }
    LaunchedEffect(tagIds) {
        selectedTags = selectedTags.filter { it in tagIds }.toSet()
    }

    fun submit(close: Boolean) {
        val primary = selectedGroupIds.firstOrNull()
        if (name.isNotBlank() && primary != null) {
            val list = selectedTags.filter { it in tagIds }
            onAdd(
                name.trim(), primary, type, TagIds.SUPPLEMENTS in list, list,
                showPreset, weekdays, intervalDays, specificDates,
                selectedGroupIds.drop(1), icon.trim()
            )
            name = ""
            icon = ""
            selectedTags = emptySet()
            addedCount++
        }
        if (close) onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = {
            Text(if (addedCount > 0) "New habit (+$addedCount)" else "New habit")
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                EmojiIconPicker(selected = icon, onSelect = { icon = it })
                Spacer(Modifier.height(8.dp))
                Text("Type", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                HabitTypeChipRow(selected = type, onSelect = { type = it })
                Spacer(Modifier.height(8.dp))
                GroupMembershipSection(
                    groups = groups,
                    selectedIds = selectedGroupIds,
                    onSelectedIdsChange = { selectedGroupIds = it }
                )
                Spacer(Modifier.height(8.dp))
                ShowWhenPicker(
                    preset = showPreset,
                    weekdays = weekdays,
                    intervalDays = intervalDays,
                    specificDates = specificDates,
                    onPreset = { showPreset = it },
                    onWeekdays = { weekdays = it },
                    onInterval = { intervalDays = it },
                    onDates = { specificDates = it }
                )
                Spacer(Modifier.height(8.dp))
                Text("Tags (History category)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                TagChipRow(tags = tags, selected = selectedTags) { id ->
                    selectedTags = if (id in selectedTags) selectedTags - id else selectedTags + id
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(
                    onClick = { submit(false) },
                    enabled = name.isNotBlank() && selectedGroupIds.isNotEmpty()
                ) { Text("Add another") }
                TextButton(
                    onClick = { submit(true) },
                    enabled = (name.isNotBlank() && selectedGroupIds.isNotEmpty()) || addedCount > 0
                ) {
                    Text(if (name.isNotBlank() || addedCount == 0) "Add & close" else "Done")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CapturePrefsCard(
    prefs: com.steady.habittracker.data.CapturePrefs,
    onUpdate: (com.steady.habittracker.data.CapturePrefs) -> Unit
) {
    var customInput by remember { mutableStateOf("") }
    val enabled = prefs.enabledTags.ifEmpty { com.steady.habittracker.data.CaptureTags.PRESETS }.toSet()
    val defaults = prefs.defaultTags.toSet()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Quick Capture",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp
            )
            Text(
                "Configure the Today capture sheet: tags, defaults, note field, energy scale.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Show note field", fontSize = 13.sp)
                    Text("Extra details under the title", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = prefs.showNoteField,
                    onCheckedChange = { onUpdate(prefs.copy(showNoteField = it)) }
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Multi-tag", fontSize = 13.sp)
                    Text("Select several tags at once", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = prefs.multiTag,
                    onCheckedChange = { onUpdate(prefs.copy(multiTag = it)) }
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Energy scale", fontSize = 13.sp)
                    Text("1–5 check-in on capture", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = prefs.showEnergyScale,
                    onCheckedChange = { onUpdate(prefs.copy(showEnergyScale = it)) }
                )
            }

            Text("Visible tags", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("Tap to show/hide · long-press sets default", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            com.steady.habittracker.data.CaptureTags.PRESETS.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { tag ->
                        val isOn = tag in enabled
                        val isDefault = tag in defaults
                        FilterChip(
                            selected = isOn,
                            onClick = {
                                val nextEnabled = if (isOn) {
                                    (enabled - tag).toList().ifEmpty { listOf(tag) }
                                } else {
                                    (enabled + tag).toList()
                                }
                                val nextDefaults = defaults.filter { it in nextEnabled.toSet() }
                                    .ifEmpty { listOf(nextEnabled.first()) }
                                onUpdate(
                                    prefs.copy(
                                        enabledTags = nextEnabled,
                                        defaultTags = nextDefaults
                                    )
                                )
                            },
                            label = {
                                Text(
                                    buildString {
                                        append(com.steady.habittracker.data.CaptureTags.glyph(tag))
                                        append(' ')
                                        append(tag)
                                        if (isDefault) append(" ★")
                                    },
                                    fontSize = 11.sp
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            // Default tags row
            Text("Default tag(s)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                enabled.forEach { tag ->
                    FilterChip(
                        selected = tag in defaults,
                        onClick = {
                            val next = if (prefs.multiTag) {
                                if (tag in defaults) (defaults - tag).ifEmpty { setOf(tag) }
                                else defaults + tag
                            } else {
                                setOf(tag)
                            }
                            onUpdate(prefs.copy(defaultTags = next.toList()))
                        },
                        label = { Text(tag, fontSize = 11.sp) }
                    )
                }
            }

            Text("Custom tags", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = customInput,
                    onValueChange = { customInput = it },
                    label = { Text("Add tag") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        val t = customInput.trim()
                        if (t.isNotEmpty() && t !in prefs.customTags) {
                            onUpdate(prefs.copy(customTags = prefs.customTags + t))
                            customInput = ""
                        }
                    }
                ) { Text("Add") }
            }
            if (prefs.customTags.isNotEmpty()) {
                prefs.customTags.forEach { tag ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(tag, modifier = Modifier.weight(1f), fontSize = 13.sp)
                        TextButton(onClick = {
                            onUpdate(prefs.copy(customTags = prefs.customTags.filter { it != tag }))
                        }) {
                            Text("Remove", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = prefs.placeholderTitle,
                onValueChange = { onUpdate(prefs.copy(placeholderTitle = it.take(80))) },
                label = { Text("Title placeholder") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = prefs.saveLabel,
                onValueChange = { onUpdate(prefs.copy(saveLabel = it.take(24).ifBlank { "Save" })) },
                label = { Text("Save button label") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun BlocksConfigSection(
    appData: AppData,
    groups: List<Group>,
    onAddExtension: (com.steady.habittracker.data.ExtensionType, String?) -> Unit,
    onEditHabit: (Habit) -> Unit,
    onUpdateLocalWebPrefs: (com.steady.habittracker.data.LocalWebPrefs) -> Unit,
    onUpdateNotificationPrefs: (NotificationPrefs) -> Unit,
    onUpdateCapturePrefs: (com.steady.habittracker.data.CapturePrefs) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activeBlocks = remember(appData.habits) {
        com.steady.habittracker.data.ExtensionCatalog.activeExtensionHabits(appData)
    }
    val prefs = appData.notificationPrefs
    val web = appData.localWebPrefs
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "Special habit blocks",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp
            )
            Text(
                "Extensions appear on Today & the widget like normal habits. Add a template to a suggested group, then schedule it in Planner.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(com.steady.habittracker.data.ExtensionCatalog.TEMPLATES, key = { it.type.name }) { t ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${t.defaultIcon} ${t.title}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(t.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(t.category, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                    val count = appData.habits.count { !it.archived && it.extensionType == t.type }
                    TextButton(
                        onClick = { onAddExtension(t.type, null) },
                        enabled = groups.isNotEmpty()
                    ) {
                        Text(
                            if (count > 0) "Add another to planner ($count active)" else "Add to planner",
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
        if (activeBlocks.isNotEmpty()) {
            item {
                Text("Active blocks", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            items(activeBlocks, key = { it.id }) { h ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEditHabit(h) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(
                            "${h.icon.ifBlank { "◆" }} ${h.name}",
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                        Text(
                            com.steady.habittracker.data.ExtensionCatalog.label(h.extensionType) +
                                " · " + (groups.find { it.id == h.groupId }?.name ?: h.groupId),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item {
            CapturePrefsCard(
                prefs = appData.capturePrefs,
                onUpdate = onUpdateCapturePrefs
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Awareness & quotes", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Daily motivational quotes", fontSize = 13.sp)
                            Text("Consistency quotes each morning", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = prefs.motivationalQuotesEnabled,
                            onCheckedChange = {
                                onUpdateNotificationPrefs(prefs.copy(motivationalQuotesEnabled = it))
                            }
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Random check-ins (ESM)", fontSize = 13.sp)
                            Text("Gentle “what are you doing?” polls", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = prefs.randomCheckInsEnabled,
                            onCheckedChange = {
                                onUpdateNotificationPrefs(prefs.copy(randomCheckInsEnabled = it))
                            }
                        )
                    }
                    if (prefs.randomCheckInsEnabled) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("low", "medium", "high").forEach { f ->
                                FilterChip(
                                    selected = prefs.randomCheckInFrequency == f,
                                    onClick = {
                                        onUpdateNotificationPrefs(prefs.copy(randomCheckInFrequency = f))
                                    },
                                    label = { Text(f, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Missed habit evening nudge", fontSize = 13.sp)
                            Text("20:00 if items still pending", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = prefs.missedHabitReminders,
                            onCheckedChange = {
                                onUpdateNotificationPrefs(prefs.copy(missedHabitReminders = it))
                            }
                        )
                    }
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Local web UI (LAN)", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "Desktop browser on the same Wi‑Fi. Pomodoro + Today mirror.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable server", fontSize = 13.sp)
                        Switch(
                            checked = web.enabled,
                            onCheckedChange = {
                                val next = web.copy(enabled = it)
                                onUpdateLocalWebPrefs(next)
                            }
                        )
                    }
                    if (web.enabled) {
                        val url = remember(web.port, web.enabled) {
                            try {
                                com.steady.habittracker.web.LocalWebServer.localAddressHint(context)
                            } catch (_: Exception) {
                                "http://<phone-ip>:${web.port}"
                            }
                        }
                        Text("Open on desktop: $url", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(
                            value = web.port.toString(),
                            onValueChange = { v ->
                                v.toIntOrNull()?.let { p ->
                                    if (p in 1024..65535) onUpdateLocalWebPrefs(web.copy(port = p))
                                }
                            },
                            label = { Text("Port") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = web.pin,
                            onValueChange = { onUpdateLocalWebPrefs(web.copy(pin = it.take(12))) },
                            label = { Text("Optional PIN") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Immutable
private data class ManageHabitRowModel(
    val habit: Habit,
    val glyph: String,
    val subtitle: String
)

@Immutable
private data class ManageGroupRowModel(
    val group: Group,
    val glyph: String,
    val subtitle: String
)

@Immutable
private data class ManageGroupHabitRowModel(
    val habit: Habit,
    val glyph: String,
    val memberCount: Int,
    val subtitle: String
)

@Composable
private fun ManageHabitCatalogRow(
    row: ManageHabitRowModel,
    onSurface: Color,
    onVariant: Color,
    surface: Color,
    error: Color,
    onEdit: () -> Unit,
    onArchive: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(surface, RoundedCornerShape(12.dp))
            .clickable(onClick = onEdit)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlyphIcon(
            glyph = row.glyph,
            size = 22.dp,
            tintForSimple = onVariant,
            modifier = Modifier.padding(end = 10.dp)
        )
        Column(Modifier.weight(1f)) {
            BasicText(
                text = row.habit.name,
                style = TextStyle(
                    color = onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (row.subtitle.isNotBlank()) {
                BasicText(
                    text = row.subtitle,
                    style = TextStyle(color = onVariant, fontSize = 11.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = onVariant)
        }
        BasicText(
            text = "Archive",
            style = TextStyle(color = error, fontSize = 11.sp),
            modifier = Modifier
                .clickable(onClick = onArchive)
                .padding(8.dp)
        )
    }
}

@Composable
private fun TagChipRow(tags: List<Tag>, selected: Set<String>, onToggle: (String) -> Unit) {
    // Flow-ish wrap via simple column of rows
    val chunked = tags.chunked(3)
    Column {
        chunked.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { t ->
                    ThemedFilterChip(
                        selected = t.id in selected,
                        onClick = { onToggle(t.id) },
                        label = { Text(t.name, fontSize = 10.sp) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TagManagerCard(
    tags: List<Tag>,
    habits: List<Habit>,
    onAddTag: (String) -> Unit
) {
    var newName by remember { mutableStateOf("") }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Tags (categories for History)", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "E.g. put Omega-3 in Morning group, tag Supplements — History still tracks supplement completion.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
            Spacer(Modifier.height(6.dp))
            tags.forEach { t ->
                val n = habits.count { t.id in it.tags || (t.id == TagIds.SUPPLEMENTS && it.isSupplement) }
                Text("• ${t.name} ($n items)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New tag") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                )
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        onAddTag(newName.trim())
                        newName = ""
                    }
                }) { Text("Add", fontSize = 12.sp) }
            }
        }
    }
}


// Helpers for 24h timeline editor (used in scheduling visual block editor)
private fun parseHhMmToMin(hhmm: String): Int? {
    val parts = hhmm.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val m = parts.getOrNull(1)?.toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

private fun normalizeTime(hhmm: String): String {
    val p = hhmm.split(":")
    val h = (p.getOrNull(0)?.toIntOrNull() ?: 0).coerceIn(0, 23)
    val m = (p.getOrNull(1)?.toIntOrNull() ?: 0).coerceIn(0, 59)
    return "%02d:%02d".format(h, m)
}
