package com.steady.habittracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
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
 * Manage = three focused areas:
 * - Habits: groups as wide section headers + habits as a 2-column square grid
 * - Blocks: accordion modules with enable checkbox + per-block config (#33, #37)
 * - Time: day schedule, reminders, backup (block tools live under Blocks)
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
        icon: String,
        description: String
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
    onUpdateGadgetbridgePrefs: (com.steady.habittracker.data.GadgetbridgePrefs) -> Unit = {},
    onRunGadgetbridgeSyncNow: () -> Unit = {},
    onEnableGadgetbridgeFromLocation: (String, (String?) -> Unit) -> Unit = { _, _ -> },
    onDisableGadgetbridgeBlock: () -> Unit = {},
    onUpdateOralHygienePrefs: (com.steady.habittracker.data.OralHygienePrefs) -> Unit = {},
    onEnableOralHygieneBlock: () -> Unit = {},
    onDisableOralHygieneBlock: () -> Unit = {},
    onAlignRemindersToSchedule: () -> Unit = {},
    onArchiveGroup: (String) -> Unit = {},
    onExportCsv: () -> Unit = {},
    onImportCsv: () -> Unit = {},
    onUpdateHabit: (Habit) -> Unit = {},
    onUpdateGroup: (Group) -> Unit = {},
    onUnarchiveGroup: (String) -> Unit = {},
    onUnarchiveHabit: (String) -> Unit = {},
    /** Permanent delete (archived habits only). */
    onPermanentlyDeleteHabit: (String) -> Unit = {},
    onPermanentlyDeleteAllArchivedHabits: () -> Unit = {},
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
    // 0 Habits · 1 Blocks · 2 Time (schedule / reminders / sensors / backup)
    var manageTab by remember { mutableIntStateOf(0) }
    var timeSection by remember { mutableIntStateOf(0) } // which Time sub-panel is expanded
    var showAddGroup by remember { mutableStateOf(false) }
    /** Create a new habit (optional initial group id from a section header). */
    var showCreateHabit by remember { mutableStateOf(false) }
    var createHabitInitialGroupId by remember { mutableStateOf<String?>(null) }
    /** Attach an existing habit into this group. */
    var attachHabitToGroupId by remember { mutableStateOf<String?>(null) }
    /** Long-press menu on a habit square: (habitId, groupId). */
    var habitSquareMenu by remember { mutableStateOf<Pair<String, String>?>(null) }
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
    var confirmPermanentDeleteHabitId by remember { mutableStateOf<String?>(null) }
    var confirmPermanentDeleteAllArchived by remember { mutableStateOf(false) }

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

    // Habits under each group (for grid). Search filters habits; empty groups stay when not searching.
    val habitsByGroupId = remember(appData.habits, activeGroups, q) {
        activeGroups.associate { g ->
            val members = HabitDomain.getActiveHabitsForGroup(appData, g.id)
            val filtered = if (q.isEmpty()) {
                members
            } else {
                members.filter { h ->
                    h.name.contains(q, ignoreCase = true) ||
                        g.name.contains(q, ignoreCase = true)
                }
            }
            g.id to filtered
        }
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
            TabButton("Blocks", manageTab == 1, modifier = Modifier.weight(1f)) { manageTab = 1 }
            TabButton("Time", manageTab == 2, modifier = Modifier.weight(1f)) { manageTab = 2 }
        }

        Spacer(Modifier.height(8.dp))

        when (manageTab) {
            0 -> {
                // Combined groups + habits: wide group bars, 2-column square habit tiles
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${activeGroups.size} groups · ${activeHabitsSorted.size} habits",
                        color = onVariant,
                        fontSize = 12.sp
                    )
                    Row {
                        TextButton(onClick = { showAddGroup = true }) {
                            Icon(Icons.Default.Add, null, tint = primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Group", color = primary, fontSize = 12.sp)
                        }
                        TextButton(
                            onClick = {
                                createHabitInitialGroupId = activeGroups.firstOrNull()?.id
                                showCreateHabit = true
                            },
                            enabled = activeGroups.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Add, null, tint = primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Habit", color = primary, fontSize = 12.sp)
                        }
                    }
                }
                OutlinedTextField(
                    value = habitSearch,
                    onValueChange = { habitSearch = it },
                    label = { Text("Search groups & habits") },
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
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (activeGroups.isEmpty()) {
                        item(key = "no_groups", contentType = "msg") {
                            Text(
                                "Create a timeline group first, then add habits under it.",
                                color = onVariant,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        val visibleGroups = if (q.isEmpty()) {
                            activeGroups
                        } else {
                            activeGroups.filter { g ->
                                g.name.contains(q, ignoreCase = true) ||
                                    habitsByGroupId[g.id].orEmpty().isNotEmpty()
                            }
                        }
                        if (visibleGroups.isEmpty()) {
                            item(key = "no_match", contentType = "msg") {
                                Text(
                                    "No matches for “$q”.",
                                    color = onVariant,
                                    fontSize = 13.sp
                                )
                            }
                        }
                        visibleGroups.forEach { group ->
                            val rowMeta = groupListRows.find { it.group.id == group.id }
                            val habitsInGroup = habitsByGroupId[group.id].orEmpty()
                            item(key = "gh_${group.id}", contentType = "group_hdr") {
                                ManageGroupSectionBar(
                                    group = group,
                                    glyph = rowMeta?.glyph ?: group.displayGlyph(),
                                    subtitle = rowMeta?.subtitle
                                        ?: "${habitsInGroup.size} habits · ${group.timeHint}",
                                    onSurface = onSurface,
                                    onVariant = onVariant,
                                    primary = primary,
                                    surface = surface,
                                    error = error,
                                    onEdit = { showEditGroup = group },
                                    onArchive = { confirmArchiveGroupId = group.id },
                                    onAddHabit = {
                                        createHabitInitialGroupId = group.id
                                        showCreateHabit = true
                                    },
                                    onAttachHabit = { attachHabitToGroupId = group.id }
                                )
                            }
                            if (habitsInGroup.isEmpty()) {
                                item(key = "empty_${group.id}", contentType = "msg") {
                                    Text(
                                        if (q.isEmpty()) {
                                            "No habits yet — tap + on the group bar."
                                        } else {
                                            "No matching habits in this group."
                                        },
                                        color = onVariant,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                                    )
                                }
                            } else {
                                // Same square density as Today (2–4 columns from todayGridColumns)
                                val manageCols = appData.todayGridColumns.coerceIn(2, 4)
                                val pairs = habitsInGroup.chunked(manageCols)
                                items(
                                    pairs,
                                    key = { pair ->
                                        "row_${group.id}_${manageCols}_${pair.joinToString("_") { it.id }}"
                                    },
                                    contentType = { "habit_row" }
                                ) { pair ->
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        pair.forEach { h ->
                                            Box(Modifier.weight(1f)) {
                                                val menuOpen = habitSquareMenu?.first == h.id &&
                                                    habitSquareMenu?.second == group.id
                                                ManageHabitSquare(
                                                    habit = h,
                                                    glyph = h.displayGlyph(),
                                                    subtitle = HabitDomain.showRuleLabel(h),
                                                    onSurface = onSurface,
                                                    onVariant = onVariant,
                                                    primary = primary,
                                                    surface = surface,
                                                    onClick = { showEditHabit = h },
                                                    onLongClick = {
                                                        habitSquareMenu = h.id to group.id
                                                    },
                                                    menuExpanded = menuOpen,
                                                    onDismissMenu = { habitSquareMenu = null },
                                                    onArchive = {
                                                        habitSquareMenu = null
                                                        confirmArchiveHabitId = h.id
                                                    },
                                                    onMoveUp = {
                                                        habitSquareMenu = null
                                                        onReorderHabit(h.id, -1, group.id)
                                                    },
                                                    onMoveDown = {
                                                        habitSquareMenu = null
                                                        onReorderHabit(h.id, 1, group.id)
                                                    },
                                                    onRemoveFromGroup = {
                                                        habitSquareMenu = null
                                                        onRemoveHabitFromGroup(h.id, group.id)
                                                    },
                                                    canRemoveFromGroup =
                                                        HabitDomain.membershipGroupIds(h).size > 1
                                                )
                                            }
                                        }
                                        repeat(manageCols - pair.size) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (archivedHabits.isNotEmpty() || archivedGroups.isNotEmpty()) {
                        item(key = "arch_hdr", contentType = "hdr") {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Archived",
                                color = error,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    if (archivedGroups.isNotEmpty()) {
                        items(
                            archivedGroups,
                            key = { "ag_${it.id}" },
                            contentType = { "arch_g" }
                        ) { g ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(surfaceVariant, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${g.displayGlyph()} ${g.name}",
                                    color = onVariant,
                                    modifier = Modifier.weight(1f),
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
                                Text(
                                    "Restore group",
                                    color = primary,
                                    fontSize = 11.sp,
                                    modifier = Modifier
                                        .clickable { onUnarchiveGroup(g.id) }
                                        .padding(8.dp)
                                )
                            }
                        }
                    }
                    if (archivedHabits.isNotEmpty()) {
                        item(key = "arch_habits_hdr", contentType = "hdr") {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Archived habits (${archivedHabits.size})",
                                    color = onVariant,
                                    fontSize = 11.sp
                                )
                                Text(
                                    "Delete all…",
                                    color = error,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clickable { confirmPermanentDeleteAllArchived = true }
                                        .padding(8.dp)
                                )
                            }
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
                                Text(
                                    "Delete",
                                    color = error,
                                    fontSize = 11.sp,
                                    modifier = Modifier
                                        .clickable { confirmPermanentDeleteHabitId = h.id }
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
                // ——— Blocks: accordion modules (enable + config live here, not in Time) ———
                BlocksConfigSection(
                    appData = appData,
                    groups = activeGroups,
                    onAddExtension = onAddExtensionBlock,
                    onArchiveHabit = onDeleteHabit,
                    onEditHabit = { showEditHabit = it },
                    onUpdateLocalWebPrefs = onUpdateLocalWebPrefs,
                    onUpdateNotificationPrefs = onUpdateNotificationPrefs,
                    onUpdateCapturePrefs = onUpdateCapturePrefs,
                    onSetAutoLogMasterEnabled = onSetAutoLogMasterEnabled,
                    onRunAutoLogNow = onRunAutoLogNow,
                    onUpdateSleepAudioPrefs = onUpdateSleepAudioPrefs,
                    onStartSleepAudio = onStartSleepAudio,
                    onStopSleepAudio = onStopSleepAudio,
                    onUpdateGadgetbridgePrefs = onUpdateGadgetbridgePrefs,
                    onRunGadgetbridgeSyncNow = onRunGadgetbridgeSyncNow,
                    onEnableGadgetbridgeFromLocation = onEnableGadgetbridgeFromLocation,
                    onDisableGadgetbridgeBlock = onDisableGadgetbridgeBlock,
                    onUpdateOralHygienePrefs = onUpdateOralHygienePrefs,
                    onEnableOralHygieneBlock = onEnableOralHygieneBlock,
                    onDisableOralHygieneBlock = onDisableOralHygieneBlock,
                    onLoadBlueprintRoutines = onLoadBlueprintRoutines,
                    onSaveRoutine = onSaveRoutine,
                    onStartRoutine = onStartRoutine
                )
            }

            else -> {
                // ——— Time: schedule, reminders, backup only (block configs live under Blocks) ———
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            "When your day happens · block tools are under Blocks",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                    item {
                        TimePanel(
                            title = "Day schedule",
                            subtitle = "Sleep anchors · 24h timeline · group blocks",
                            expanded = timeSection == 0,
                            onToggle = { timeSection = if (timeSection == 0) -1 else 0 }
                        ) {
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
                    }
                    item {
                        TimePanel(
                            title = "Reminders",
                            subtitle = "Notifications timed to your schedule",
                            expanded = timeSection == 1,
                            onToggle = { timeSection = if (timeSection == 1) -1 else 1 }
                        ) {
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
                    }
                    item {
                        TimePanel(
                            title = "Backup",
                            subtitle = "Export / import full Steady JSON",
                            expanded = timeSection == 2,
                            onToggle = { timeSection = if (timeSection == 2) -1 else 2 }
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Includes habits, logs, Momentum, reminders, Path, and settings. Import replaces current data.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = onExportCsv,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Text("Export")
                                    }
                                    OutlinedButton(onClick = onImportCsv) {
                                        Text("Import")
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
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
            // Prefer group from section + button; still allow multi-select in dialog
            initialGroupId = createHabitInitialGroupId.orEmpty(),
            groups = activeGroups,
            tags = HabitDomain.getActiveTags(appData),
            onDismiss = {
                showCreateHabit = false
                createHabitInitialGroupId = null
            },
            onAdd = { name, groupId, type, isSupplement, tags, showPreset, weekdays, intervalDays, specificDates, additionalGroupIds, icon, description ->
                onAddHabit(
                    name, groupId, type, isSupplement, tags, showPreset, weekdays,
                    intervalDays, specificDates, additionalGroupIds, icon, description
                )
                showCreateHabit = false
                createHabitInitialGroupId = null
            }
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

    confirmPermanentDeleteHabitId?.let { hid ->
        val name = appData.habits.find { it.id == hid }?.name ?: "this habit"
        AlertDialog(
            onDismissRequest = { confirmPermanentDeleteHabitId = null },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text("Permanently delete?") },
            text = {
                Text(
                    "“$name” and all of its log history will be removed from this device. " +
                        "This cannot be undone (export a backup first if you might need it)."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onPermanentlyDeleteHabit(hid)
                    confirmPermanentDeleteHabitId = null
                }) { Text("Delete forever", color = Color(0xFFF87171)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmPermanentDeleteHabitId = null }) { Text("Cancel") }
            }
        )
    }

    if (confirmPermanentDeleteAllArchived) {
        val n = archivedHabits.size
        AlertDialog(
            onDismissRequest = { confirmPermanentDeleteAllArchived = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text("Delete all archived habits?") },
            text = {
                Text(
                    "$n archived habit(s) and their log history will be permanently removed. " +
                        "Active habits are not affected. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onPermanentlyDeleteAllArchivedHabits()
                    confirmPermanentDeleteAllArchived = false
                }) { Text("Delete all forever", color = Color(0xFFF87171)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmPermanentDeleteAllArchived = false }) { Text("Cancel") }
            }
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
    var description by remember {
        mutableStateOf(habit.description.ifBlank { habit.why })
    }
    var icon by remember { mutableStateOf(habit.icon) }
    var type by remember { mutableStateOf(habit.type) }
    var canSkip by remember { mutableStateOf(habit.canSkip) }
    var pointValue by remember { mutableStateOf(habit.effectivePointValue().toString()) }
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
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description / default note") },
                    placeholder = {
                        Text(
                            "e.g. 2000 IU vitamin D · with breakfast",
                            fontSize = 12.sp
                        )
                    },
                    supportingText = {
                        Text(
                            "Shown under the name and pre-filled when you log this habit (great for dosages).",
                            fontSize = 10.sp
                        )
                    },
                    minLines = 2,
                    maxLines = 4,
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
                Spacer(Modifier.height(8.dp))
                Text("Importance (Momentum points)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Higher = more points when completed. Default 10.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(5, 10, 15, 20, 30).forEach { pts ->
                        FilterChip(
                            selected = pointValue.toIntOrNull() == pts,
                            onClick = { pointValue = pts.toString() },
                            label = { Text("$pts", fontSize = 11.sp) }
                        )
                    }
                }
                OutlinedTextField(
                    value = pointValue,
                    onValueChange = { pointValue = it.filter { ch -> ch.isDigit() }.take(2) },
                    label = { Text("Custom points (1–50)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
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
                    "On-device only. Grant permissions in Time → Sensors if needed.",
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
                            description = description.trim(),
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
                            ),
                            pointValue = pointValue.toIntOrNull()?.coerceIn(1, 50) ?: 10
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
        icon: String,
        description: String
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
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
                selectedGroupIds.drop(1), icon.trim(), description.trim()
            )
            name = ""
            description = ""
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
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description / default note") },
                    placeholder = {
                        Text(
                            "e.g. 500 mg magnesium · evening",
                            fontSize = 12.sp
                        )
                    },
                    supportingText = {
                        Text(
                            "Optional. Pre-fills the note when you log (ideal for supplement dosages).",
                            fontSize = 10.sp
                        )
                    },
                    minLines = 2,
                    maxLines = 4,
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
                "Inbox is only for follow-ups (Ideas / Todo / Reminders). " +
                    "Memories, Thoughts, Gratitude, etc. go straight to Journal.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text("Inbox tags (need follow-up)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Selected tags open the Today Inbox. Others auto-archive to Journal.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val inboxSet = prefs.resolvedInboxTags()
            com.steady.habittracker.data.CaptureTags.PRESETS.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { tag ->
                        val on = tag in inboxSet
                        FilterChip(
                            selected = on,
                            onClick = {
                                val next = if (on) {
                                    (inboxSet - tag).toList().ifEmpty {
                                        com.steady.habittracker.data.CaptureTags.DEFAULT_INBOX_TAGS
                                    }
                                } else {
                                    (inboxSet + tag).toList()
                                }
                                onUpdate(prefs.copy(inboxTags = next))
                            },
                            label = {
                                Text(
                                    "${com.steady.habittracker.data.CaptureTags.glyph(tag)} $tag",
                                    fontSize = 10.sp
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            Text("Trash", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Deleted journal entries stay recoverable for this many days",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(7, 14, 30, 60, 90).forEach { d ->
                    FilterChip(
                        selected = prefs.trashRetainDays == d,
                        onClick = { onUpdate(prefs.copy(trashRetainDays = d)) },
                        label = { Text("${d}d", fontSize = 11.sp) }
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Show trash tab in Journal", fontSize = 13.sp)
                }
                Switch(
                    checked = prefs.showTrashInJournal,
                    onCheckedChange = { onUpdate(prefs.copy(showTrashInJournal = it)) }
                )
            }
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
    onArchiveHabit: (String) -> Unit,
    onEditHabit: (Habit) -> Unit,
    onUpdateLocalWebPrefs: (com.steady.habittracker.data.LocalWebPrefs) -> Unit,
    onUpdateNotificationPrefs: (NotificationPrefs) -> Unit,
    onUpdateCapturePrefs: (com.steady.habittracker.data.CapturePrefs) -> Unit = {},
    onSetAutoLogMasterEnabled: (Boolean) -> Unit = {},
    onRunAutoLogNow: () -> Unit = {},
    onUpdateSleepAudioPrefs: (SleepAudioPrefs) -> Unit = {},
    onStartSleepAudio: () -> Unit = {},
    onStopSleepAudio: () -> Unit = {},
    onUpdateGadgetbridgePrefs: (com.steady.habittracker.data.GadgetbridgePrefs) -> Unit = {},
    onRunGadgetbridgeSyncNow: () -> Unit = {},
    onEnableGadgetbridgeFromLocation: (String, (String?) -> Unit) -> Unit = { _, _ -> },
    onDisableGadgetbridgeBlock: () -> Unit = {},
    onUpdateOralHygienePrefs: (com.steady.habittracker.data.OralHygienePrefs) -> Unit = {},
    onEnableOralHygieneBlock: () -> Unit = {},
    onDisableOralHygieneBlock: () -> Unit = {},
    onLoadBlueprintRoutines: () -> Unit = {},
    onSaveRoutine: (com.steady.habittracker.data.ExerciseRoutine) -> Unit = {},
    onStartRoutine: (com.steady.habittracker.data.ExerciseRoutine) -> Unit = {}
) {
    // Only one accordion open at a time (block type name or tool key).
    var expandedKey by remember { mutableStateOf<String?>(null) }
    var routineEditor by remember {
        mutableStateOf<com.steady.habittracker.data.ExerciseRoutine?>(null)
    }
    var showNewRoutineEditor by remember { mutableStateOf(false) }
    val prefs = appData.notificationPrefs
    val web = appData.localWebPrefs
    val canAdd = groups.isNotEmpty()
    val context = LocalContext.current

    val gadgetbridgePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            Toast.makeText(context, "No file selected — Gadgetbridge stays off", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        // Persistable read so hourly sync keeps working after reboot
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Some providers only grant temporary access; still try import
        }
        onEnableGadgetbridgeFromLocation(uri.toString()) { err ->
            if (err != null) {
                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Gadgetbridge export linked", Toast.LENGTH_SHORT).show()
            }
        }
        expandedKey = com.steady.habittracker.data.ExtensionType.GADGETBRIDGE_SYNC.name
    }

    fun isTypeEnabled(type: com.steady.habittracker.data.ExtensionType): Boolean =
        when (type) {
            com.steady.habittracker.data.ExtensionType.GADGETBRIDGE_SYNC ->
                appData.gadgetbridgePrefs.enabled ||
                    appData.habits.any {
                        !it.archived && it.extensionType == type
                    }
            com.steady.habittracker.data.ExtensionType.ORAL_HYGIENE ->
                com.steady.habittracker.data.OralHygieneBlock.isEnabled(appData)
            else -> appData.habits.any { !it.archived && it.extensionType == type }
        }

    fun habitsOf(type: com.steady.habittracker.data.ExtensionType): List<Habit> =
        appData.habits.filter { !it.archived && it.extensionType == type }

    fun setTypeEnabled(type: com.steady.habittracker.data.ExtensionType, enabled: Boolean) {
        if (type == com.steady.habittracker.data.ExtensionType.ORAL_HYGIENE) {
            if (enabled) {
                onEnableOralHygieneBlock()
                expandedKey = type.name
            } else {
                onDisableOralHygieneBlock()
                if (expandedKey == type.name) expandedKey = null
            }
            return
        }
        // Gadgetbridge: pick/validate file first; never leave a half-enabled race
        if (type == com.steady.habittracker.data.ExtensionType.GADGETBRIDGE_SYNC) {
            if (enabled) {
                val existing = appData.gadgetbridgePrefs.exportLocation
                if (existing.isNotBlank() && appData.gadgetbridgePrefs.schemaValidatedAt > 0L) {
                    onEnableGadgetbridgeFromLocation(existing) { err ->
                        if (err != null) {
                            // Re-pick if stored path no longer valid
                            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            gadgetbridgePicker.launch(
                                arrayOf(
                                    "application/x-sqlite3",
                                    "application/vnd.sqlite3",
                                    "application/octet-stream",
                                    "application/*",
                                    "*/*"
                                )
                            )
                        } else {
                            Toast.makeText(context, "Gadgetbridge on", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(
                        context,
                        "Choose your Gadgetbridge export database",
                        Toast.LENGTH_SHORT
                    ).show()
                    gadgetbridgePicker.launch(
                        arrayOf(
                            "application/x-sqlite3",
                            "application/vnd.sqlite3",
                            "application/octet-stream",
                            "application/*",
                            "*/*"
                        )
                    )
                }
                expandedKey = type.name
            } else {
                onDisableGadgetbridgeBlock()
                if (expandedKey == type.name) expandedKey = null
            }
            return
        }

        if (enabled) {
            if (!isTypeEnabled(type) && canAdd) onAddExtension(type, null)
            when (type) {
                com.steady.habittracker.data.ExtensionType.SNORE_WATCH_ACTIVATE,
                com.steady.habittracker.data.ExtensionType.SNORE_WATCH_STOP -> {
                    if (!appData.sleepAudioPrefs.enabled) {
                        onUpdateSleepAudioPrefs(appData.sleepAudioPrefs.copy(enabled = true))
                    }
                }
                com.steady.habittracker.data.ExtensionType.SENSOR_AUTO_READ,
                com.steady.habittracker.data.ExtensionType.SCREEN_USAGE -> {
                    if (!appData.autoLogMasterEnabled) onSetAutoLogMasterEnabled(true)
                }
                else -> Unit
            }
            expandedKey = type.name
        } else {
            habitsOf(type).forEach { onArchiveHabit(it.id) }
            // After archiving this type, disable shared masters only when nothing else needs them.
            when (type) {
                com.steady.habittracker.data.ExtensionType.SNORE_WATCH_ACTIVATE,
                com.steady.habittracker.data.ExtensionType.SNORE_WATCH_STOP -> {
                    val otherSnore = appData.habits.any { h ->
                        !h.archived &&
                            h.extensionType != type &&
                            (
                                h.extensionType == com.steady.habittracker.data.ExtensionType.SNORE_WATCH_ACTIVATE ||
                                    h.extensionType == com.steady.habittracker.data.ExtensionType.SNORE_WATCH_STOP
                            )
                    }
                    if (!otherSnore && appData.sleepAudioPrefs.enabled) {
                        onUpdateSleepAudioPrefs(appData.sleepAudioPrefs.copy(enabled = false))
                    }
                }
                else -> Unit
            }
            if (expandedKey == type.name) expandedKey = null
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "Habit blocks",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp
            )
            Text(
                "Check a block to enable it. Tap the row to open its settings (one section at a time). " +
                    "Enabled blocks appear on Today & the widget — place them on the day timeline in Time.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(
            com.steady.habittracker.data.ExtensionCatalog.TEMPLATES,
            key = { it.type.name }
        ) { t ->
            val type = t.type
            val enabled = isTypeEnabled(type)
            val expanded = expandedKey == type.name
            val active = habitsOf(type)
            BlockAccordion(
                title = "${t.defaultIcon} ${t.title}",
                subtitle = if (enabled) {
                    "${t.category} · ${active.size} active"
                } else {
                    "${t.category} · off"
                },
                enabled = enabled,
                expanded = expanded,
                canEnable = canAdd || enabled,
                onToggleExpand = {
                    expandedKey = if (expanded) null else type.name
                },
                onEnabledChange = { on -> setTypeEnabled(type, on) }
            ) {
                Text(
                    t.description,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Always show permissions so users can grant before enabling
                BlockPermissionPanel(type = type)

                if (!enabled) {
                    Text(
                        "Check the box to add this block to your planner.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    when (type) {
                        com.steady.habittracker.data.ExtensionType.SNORE_WATCH_ACTIVATE,
                        com.steady.habittracker.data.ExtensionType.SNORE_WATCH_STOP -> {
                            SleepAudioCard(
                                appData = appData,
                                onUpdatePrefs = onUpdateSleepAudioPrefs,
                                onStartNow = onStartSleepAudio,
                                onStopNow = onStopSleepAudio
                            )
                        }
                        com.steady.habittracker.data.ExtensionType.SENSOR_AUTO_READ -> {
                            AutoLogCard(
                                appData = appData,
                                onToggleMaster = onSetAutoLogMasterEnabled,
                                onSyncNow = onRunAutoLogNow
                            )
                        }
                        com.steady.habittracker.data.ExtensionType.SCREEN_USAGE -> {
                            ScreenUsageBlockPanel(appData = appData)
                            AutoLogCard(
                                appData = appData,
                                onToggleMaster = onSetAutoLogMasterEnabled,
                                onSyncNow = onRunAutoLogNow
                            )
                        }
                        com.steady.habittracker.data.ExtensionType.ESM_CHECKIN -> {
                            EsmCheckInBlockPanel(
                                prefs = prefs,
                                onUpdate = onUpdateNotificationPrefs
                            )
                        }
                        com.steady.habittracker.data.ExtensionType.POMODORO -> {
                            Text(
                                "Work/break minutes are on each Focus habit (edit square). " +
                                    "Optional LAN timer UI is under Tools → Local web below.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        com.steady.habittracker.data.ExtensionType.WORKOUT_SESSION -> {
                            WorkoutBlockPanel(
                                appData = appData,
                                onLoadBlueprint = onLoadBlueprintRoutines,
                                onNewRoutine = { showNewRoutineEditor = true },
                                onEditRoutine = { routineEditor = it },
                                onStart = onStartRoutine
                            )
                        }
                        com.steady.habittracker.data.ExtensionType.GADGETBRIDGE_SYNC -> {
                            GadgetbridgeBlockPanel(
                                prefs = appData.gadgetbridgePrefs,
                                wearableDays = appData.wearableDays,
                                onUpdate = onUpdateGadgetbridgePrefs,
                                onSyncNow = onRunGadgetbridgeSyncNow,
                                onPickFile = {
                                    gadgetbridgePicker.launch(
                                        arrayOf(
                                            "application/x-sqlite3",
                                            "application/vnd.sqlite3",
                                            "application/octet-stream",
                                            "application/*",
                                            "*/*"
                                        )
                                    )
                                },
                                onRelink = { loc ->
                                    onEnableGadgetbridgeFromLocation(loc) { err ->
                                        if (err != null) {
                                            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Export verified",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            )
                        }
                        com.steady.habittracker.data.ExtensionType.ORAL_HYGIENE -> {
                            OralHygieneBlockPanel(
                                prefs = appData.oralHygienePrefs,
                                activeHabits = active,
                                groups = groups,
                                onUpdate = onUpdateOralHygienePrefs
                            )
                        }
                        else -> Unit
                    }

                    // Oral hygiene / Gadgetbridge manage their own planner rows
                    val managedBlock =
                        type == com.steady.habittracker.data.ExtensionType.ORAL_HYGIENE ||
                            type == com.steady.habittracker.data.ExtensionType.GADGETBRIDGE_SYNC
                    if (!managedBlock) {
                        if (active.isNotEmpty()) {
                            Text(
                                "On planner",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            active.forEach { h ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onEditHabit(h) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Column(Modifier.padding(10.dp)) {
                                        Text(
                                            "${h.icon.ifBlank { "◆" }} ${h.name}",
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            groups.find { it.id == h.groupId }?.name ?: h.groupId,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        TextButton(
                            onClick = { onAddExtension(type, null) },
                            enabled = canAdd
                        ) {
                            Text(
                                if (active.isEmpty()) "Add to planner" else "Add another",
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Text(
                "Tools",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp
            )
            Text(
                "Not day-timeline blocks — capture inbox and optional LAN dashboard.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            val key = "tool:capture"
            val expanded = expandedKey == key
            BlockAccordion(
                title = "✏️ Quick Capture",
                subtitle = if (expanded) "Inbox & journal tags" else "Inbox tags · journal defaults",
                enabled = true,
                expanded = expanded,
                canEnable = true,
                showEnable = false,
                onToggleExpand = { expandedKey = if (expanded) null else key },
                onEnabledChange = {}
            ) {
                CapturePrefsCard(
                    prefs = appData.capturePrefs,
                    onUpdate = onUpdateCapturePrefs
                )
            }
        }

        item {
            val key = "tool:web"
            val expanded = expandedKey == key
            BlockAccordion(
                title = "🌐 Local web UI",
                subtitle = if (web.enabled) "Server on · port ${web.port}" else "LAN dashboard · off",
                enabled = web.enabled,
                expanded = expanded,
                canEnable = true,
                onToggleExpand = { expandedKey = if (expanded) null else key },
                onEnabledChange = { on ->
                    onUpdateLocalWebPrefs(web.copy(enabled = on, autoStartedByWifi = false))
                    if (on) expandedKey = key
                }
            ) {
                LocalWebPrefsContent(
                    web = web,
                    onUpdate = onUpdateLocalWebPrefs
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showNewRoutineEditor) {
        RoutineEditorDialog(
            existing = null,
            onDismiss = { showNewRoutineEditor = false },
            onSave = {
                onSaveRoutine(it)
                showNewRoutineEditor = false
            }
        )
    }
    routineEditor?.let { rt ->
        RoutineEditorDialog(
            existing = rt,
            onDismiss = { routineEditor = null },
            onSave = {
                onSaveRoutine(it)
                routineEditor = null
            }
        )
    }
}

/** Permissions + quick grant actions for a special block. */
@Composable
private fun BlockPermissionPanel(type: com.steady.habittracker.data.ExtensionType) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val micLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { tick++ }
    val activityLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { tick++ }
    val locationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { tick++ }
    val notifLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { tick++ }

    val usageOk = remember(tick) {
        com.steady.habittracker.sensors.ScreenTimeReader.hasUsageAccess(context)
    }
    val micOk = remember(tick) {
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    val stepsOk = remember(tick) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACTIVITY_RECOGNITION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
    }
    val locOk = remember(tick) {
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    val notifOk = remember(tick) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
    }

    data class Need(
        val title: String,
        val ok: Boolean,
        val grant: (() -> Unit)?
    )

    val needs = when (type) {
        com.steady.habittracker.data.ExtensionType.SNORE_WATCH_ACTIVATE,
        com.steady.habittracker.data.ExtensionType.SNORE_WATCH_STOP -> listOf(
            Need("Microphone (overnight audio)", micOk) {
                micLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            }
        )
        com.steady.habittracker.data.ExtensionType.SENSOR_AUTO_READ -> listOf(
            Need("Usage access (screen time)", usageOk) {
                try {
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    )
                } catch (_: Exception) { }
                tick++
            },
            Need("Activity / steps", stepsOk) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    activityLauncher.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)
                }
            },
            Need("Location (optional GPS)", locOk) {
                locationLauncher.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
            },
            Need("Microphone (noise)", micOk) {
                micLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            }
        )
        com.steady.habittracker.data.ExtensionType.SCREEN_USAGE -> listOf(
            Need("Usage access (required for totals & apps)", usageOk) {
                try {
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    )
                } catch (_: Exception) { }
                tick++
            }
        )
        com.steady.habittracker.data.ExtensionType.ESM_CHECKIN -> listOf(
            Need("Notifications", notifOk) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        )
        com.steady.habittracker.data.ExtensionType.POMODORO -> emptyList()
        com.steady.habittracker.data.ExtensionType.WORKOUT_SESSION -> emptyList()
        com.steady.habittracker.data.ExtensionType.ORAL_HYGIENE -> emptyList()
        com.steady.habittracker.data.ExtensionType.GADGETBRIDGE_SYNC -> listOf(
            Need("Notifications (special wearable events)", notifOk) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        )
        else -> emptyList()
    }
    if (needs.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Permissions",
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )
            needs.forEach { n ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${if (n.ok) "✓" else "○"} ${n.title}",
                        fontSize = 11.sp,
                        color = if (n.ok) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!n.ok && n.grant != null) {
                        TextButton(onClick = n.grant) {
                            Text("Grant", fontSize = 11.sp)
                        }
                    }
                }
            }
            if (type == com.steady.habittracker.data.ExtensionType.SCREEN_USAGE ||
                type == com.steady.habittracker.data.ExtensionType.SENSOR_AUTO_READ
            ) {
                TextButton(onClick = { tick++ }) {
                    Text("Refresh status", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun ScreenUsageBlockPanel(appData: AppData) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val usageOk = remember(tick) {
        com.steady.habittracker.sensors.ScreenTimeReader.hasUsageAccess(context)
    }
    val minutes = remember(tick, usageOk) {
        if (usageOk) com.steady.habittracker.sensors.ScreenTimeReader.screenOnMinutes(context) else null
    }
    val top = remember(tick, usageOk) {
        if (usageOk) {
            com.steady.habittracker.sensors.ScreenTimeReader.topAppsMinutes(context, limit = 5)
        } else emptyList()
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Screen usage today",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp
            )
            if (!usageOk) {
                Text(
                    "Grant Usage access above, then tap Refresh. Without it Android will not report totals.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "Total · ${com.steady.habittracker.sensors.ScreenTimeReader.formatMinutes(minutes)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (top.isEmpty()) {
                    Text(
                        "No app breakdown yet (can take a few minutes of use after granting access).",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    top.forEach { (pkg, min) ->
                        val label = com.steady.habittracker.sensors.InstalledApps.labelFor(context, pkg)
                        Text(
                            "${label.take(22)} · ${min}m",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                TextButton(onClick = { tick++ }) {
                    Text("Refresh reading", fontSize = 12.sp)
                }
            }
            val limit = appData.habits
                .firstOrNull { !it.archived && it.extensionType == com.steady.habittracker.data.ExtensionType.SCREEN_USAGE }
                ?.extensionConfig?.dailyLimitMinutes
            if (limit != null) {
                Text("Soft limit on habit: ${limit}m · edit the habit square to change", fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EsmCheckInBlockPanel(
    prefs: NotificationPrefs,
    onUpdate: (NotificationPrefs) -> Unit
) {
    var customQ by remember {
        mutableStateOf(prefs.checkInQuestions.joinToString("\n"))
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Awareness & check-ins",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Enable random check-ins", fontSize = 13.sp)
                    Text(
                        "Quality ESM prompts · opens Write with Check-in tag",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = prefs.randomCheckInsEnabled,
                    onCheckedChange = { onUpdate(prefs.copy(randomCheckInsEnabled = it)) }
                )
            }
            Text("Schedule mode", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "group" to "By day group",
                    "random" to "Random"
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = prefs.checkInScheduleMode == mode,
                        onClick = { onUpdate(prefs.copy(checkInScheduleMode = mode)) },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }
            Text(
                "Group mode: a few in morning, ~every min interval at work, 2–3 in evening. Skips sleep.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Interval (minutes)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Min ${prefs.checkInMinIntervalMin} · Max ${prefs.checkInMaxIntervalMin}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Minimum spacing", fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(15, 30, 45, 60).forEach { m ->
                    FilterChip(
                        selected = prefs.checkInMinIntervalMin == m,
                        onClick = {
                            onUpdate(
                                prefs.copy(
                                    checkInMinIntervalMin = m,
                                    checkInMaxIntervalMin = prefs.checkInMaxIntervalMin.coerceAtLeast(m)
                                )
                            )
                        },
                        label = { Text("${m}m", fontSize = 11.sp) }
                    )
                }
            }
            Text("Maximum spacing", fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(60, 90, 120, 180).forEach { m ->
                    FilterChip(
                        selected = prefs.checkInMaxIntervalMin == m,
                        onClick = {
                            onUpdate(
                                prefs.copy(
                                    checkInMaxIntervalMin = m.coerceAtLeast(prefs.checkInMinIntervalMin)
                                )
                            )
                        },
                        label = { Text("${m}m", fontSize = 11.sp) }
                    )
                }
            }
            Text("Preset density (legacy)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("low", "medium", "high").forEach { f ->
                    FilterChip(
                        selected = prefs.randomCheckInFrequency == f,
                        onClick = { onUpdate(prefs.copy(randomCheckInFrequency = f)) },
                        label = { Text(f, fontSize = 11.sp) }
                    )
                }
            }
            Text("Questions (one per line)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Leave blank for proven defaults (${com.steady.habittracker.data.EsmDefaults.QUESTIONS.size} prompts).",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = customQ,
                onValueChange = { customQ = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 8,
                placeholder = {
                    Text(
                        com.steady.habittracker.data.EsmDefaults.QUESTIONS.take(3).joinToString("\n"),
                        fontSize = 11.sp
                    )
                }
            )
            TextButton(
                onClick = {
                    val list = customQ.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    onUpdate(prefs.copy(checkInQuestions = list))
                }
            ) {
                Text("Save questions", fontSize = 12.sp)
            }
            TextButton(
                onClick = {
                    customQ = ""
                    onUpdate(prefs.copy(checkInQuestions = emptyList()))
                }
            ) {
                Text("Reset to default questions", fontSize = 12.sp)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Daily motivational quotes", fontSize = 13.sp)
                    Text(
                        "At ${prefs.motivationalQuotesTime}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = prefs.motivationalQuotesEnabled,
                    onCheckedChange = { onUpdate(prefs.copy(motivationalQuotesEnabled = it)) }
                )
            }
        }
    }
}

@Composable
private fun WorkoutBlockPanel(
    appData: AppData,
    onLoadBlueprint: () -> Unit,
    onNewRoutine: () -> Unit,
    onEditRoutine: (com.steady.habittracker.data.ExerciseRoutine) -> Unit,
    onStart: (com.steady.habittracker.data.ExerciseRoutine) -> Unit
) {
    val routines = remember(appData.routines) {
        HabitDomain.getActiveRoutines(appData)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Workout routines",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp
            )
            Text(
                "Log sets, reps, weight & RPE per exercise. Seed longevity templates " +
                    "(calisthenics, gym compounds, Zone 2, stretch) or build your own.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onLoadBlueprint) {
                    Text("Load defaults", fontSize = 12.sp)
                }
                TextButton(onClick = onNewRoutine) {
                    Text("New routine", fontSize = 12.sp)
                }
            }
            Text(
                "Exercise library: ${com.steady.habittracker.data.ExerciseLibrary.ALL.size} movements " +
                    "(calisthenics · gym · longevity · stretch)",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (routines.isEmpty()) {
                Text(
                    "No routines yet — load defaults or create one.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                routines.take(12).forEach { rt ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onEditRoutine(rt) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(rt.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "${rt.exercises.size} exercises · ~${rt.estimatedDurationMin}m" +
                                    if (rt.tags.isNotEmpty()) " · ${rt.tags.take(2).joinToString()}" else "",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { onStart(rt) }) {
                            Text("Start", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * One expandable block/tool row: optional enable checkbox + exclusive expand.
 * Clicking the header opens this section and collapses any other.
 */
@Composable
private fun BlockAccordion(
    title: String,
    subtitle: String,
    enabled: Boolean,
    expanded: Boolean,
    canEnable: Boolean,
    showEnable: Boolean = true,
    onToggleExpand: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    expanded -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    enabled -> MaterialTheme.colorScheme.surface
                    else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                }
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showEnable) {
                    ThemedCheckbox(
                        checked = enabled,
                        onCheckedChange = { onEnabledChange(it) },
                        enabled = canEnable || enabled,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        subtitle,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    if (expanded) "▾" else "▸",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (expanded) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        }
    }
}

/** Local web server settings (shown under Blocks → Tools). */
@Composable
private fun LocalWebPrefsContent(
    web: com.steady.habittracker.data.LocalWebPrefs,
    onUpdate: (com.steady.habittracker.data.LocalWebPrefs) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Desktop dashboard for Today, Path, History, inbox, habits, focus, sleep & more. " +
                    "Use http:// (not https://) on the HTTP port. GrapheneOS: grant Network access for this app.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val currentSsid = remember {
                com.steady.habittracker.web.WifiWebMonitor.currentSsid(context)
            }
            if (currentSsid != null) {
                Text(
                    "Current Wi‑Fi: $currentSsid",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    "Current Wi‑Fi: unknown (grant Location / Nearby devices to read SSID)",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedTextField(
                value = web.pin,
                onValueChange = { onUpdate(web.copy(pin = it.take(12))) },
                label = { Text(if (web.autoStartOnTrustedWifi) "PIN (required, min 4)" else "Access PIN (recommended)") },
                singleLine = true,
                supportingText = {
                    Text(
                        if (web.pinIsSecure()) "PIN unlocks the web UI on your LAN"
                        else "Set 4+ characters — required for trusted-Wi‑Fi auto-start",
                        fontSize = 10.sp
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            Text("Manual auto turn-off", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(15 to "15m", 30 to "30m", 60 to "1h", 120 to "2h", 240 to "4h", 0 to "∞").forEach { (mins, label) ->
                    FilterChip(
                        selected = web.autoOffMinutes == mins,
                        onClick = { onUpdate(web.copy(autoOffMinutes = mins)) },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }
            Text("Trusted Wi‑Fi", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Auto-start on trusted Wi‑Fi", fontSize = 13.sp)
                    Text(
                        if (web.pinIsSecure()) "Uses longer auto-off below"
                        else "Set a PIN (4+) first",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = web.autoStartOnTrustedWifi && web.pinIsSecure(),
                    enabled = web.pinIsSecure() && web.trustedSsids.isNotEmpty(),
                    onCheckedChange = { onUpdate(web.copy(autoStartOnTrustedWifi = it)) }
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Stop when leaving trusted Wi‑Fi", fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = web.stopWhenLeavingTrustedWifi,
                    onCheckedChange = { onUpdate(web.copy(stopWhenLeavingTrustedWifi = it)) }
                )
            }
            Text("Trusted session length", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(60 to "1h", 240 to "4h", 480 to "8h", 720 to "12h", 0 to "∞").forEach { (mins, label) ->
                    FilterChip(
                        selected = web.trustedWifiAutoOffMinutes == mins,
                        onClick = { onUpdate(web.copy(trustedWifiAutoOffMinutes = mins)) },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }
            val ssidText = web.trustedSsids.joinToString(", ")
            OutlinedTextField(
                value = ssidText,
                onValueChange = { raw ->
                    val list = raw.split(',', '\n')
                        .map { it.trim().removePrefix("\"").removeSuffix("\"") }
                        .filter { it.isNotEmpty() }
                    onUpdate(web.copy(trustedSsids = list))
                },
                label = { Text("Trusted SSIDs (comma-separated)") },
                modifier = Modifier.fillMaxWidth()
            )
            if (currentSsid != null) {
                TextButton(
                    onClick = {
                        val next = (web.trustedSsids + currentSsid).map { it.trim() }
                            .filter { it.isNotEmpty() }.distinct()
                        onUpdate(web.copy(trustedSsids = next))
                    }
                ) {
                    Text("Add current Wi‑Fi “$currentSsid”", fontSize = 12.sp)
                }
            }
            if (web.enabled) {
                var tick by remember { mutableIntStateOf(0) }
                LaunchedEffect(web.enabled, web.port, web.httpsEnabled, web.autoOffMinutes) {
                    while (true) {
                        kotlinx.coroutines.delay(1000)
                        tick++
                    }
                }
                val running = remember(tick) {
                    com.steady.habittracker.web.LocalWebServer.isRunning()
                }
                val status = remember(tick) {
                    com.steady.habittracker.web.LocalWebServer.statusMessage
                }
                val err = remember(tick) {
                    com.steady.habittracker.web.LocalWebServer.lastError
                }
                val autoOffLeft = remember(tick) {
                    com.steady.habittracker.web.LocalWebServer.autoOffRemainingLabel()
                }
                val httpUrls = remember(tick, web.port) {
                    com.steady.habittracker.web.LocalWebServer.httpUrls().distinct()
                }
                val httpsUrls = remember(tick, web.port, web.httpsEnabled) {
                    com.steady.habittracker.web.LocalWebServer.httpsUrls().distinct()
                }
                Text(
                    when {
                        running -> "● Running"
                        err != null -> "○ Failed"
                        else -> "○ Starting…"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (running) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
                Text(status, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (autoOffLeft != null && running) {
                    Text(
                        "Auto-off in $autoOffLeft",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (web.autoStartedByWifi) {
                    Text("Started by trusted Wi‑Fi", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                }
                if (err != null && !running) {
                    Text("Error: $err", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }
                httpUrls.take(4).forEach { u ->
                    Text(u, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                }
                TextButton(
                    onClick = {
                        com.steady.habittracker.web.LocalWebService.restart(
                            context,
                            web.copy(enabled = true)
                        )
                        tick++
                    }
                ) {
                    Text("Restart server", fontSize = 12.sp)
                }
                OutlinedTextField(
                    value = web.port.toString(),
                    onValueChange = { v ->
                        v.toIntOrNull()?.let { p ->
                            if (p in 1024..65534) onUpdate(web.copy(port = p))
                        }
                    },
                    label = { Text("HTTP port (HTTPS = port+1)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("HTTPS (self-signed)", fontSize = 13.sp)
                        Text(
                            "TLS on port ${web.port + 1}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = web.httpsEnabled,
                        onCheckedChange = { onUpdate(web.copy(httpsEnabled = it)) }
                    )
                }
            }
        }
    }
}

@Immutable
private data class ManageGroupRowModel(
    val group: Group,
    val glyph: String,
    val subtitle: String
)

/** Wide bar above a group's habit squares. */
@Composable
private fun ManageGroupSectionBar(
    group: Group,
    glyph: String,
    subtitle: String,
    onSurface: Color,
    onVariant: Color,
    primary: Color,
    surface: Color,
    error: Color,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onAddHabit: () -> Unit,
    onAttachHabit: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = primary.copy(alpha = 0.14f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlyphIcon(
                glyph = glyph,
                size = 22.dp,
                tintForSimple = primary,
                modifier = Modifier.padding(end = 10.dp)
            )
            Column(Modifier.weight(1f)) {
                BasicText(
                    text = group.name,
                    style = TextStyle(
                        color = onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                BasicText(
                    text = subtitle,
                    style = TextStyle(color = onVariant, fontSize = 11.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(
                onClick = onAddHabit,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Add, null, tint = primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(2.dp))
                Text("New", color = primary, fontSize = 11.sp)
            }
            Text(
                "Link",
                color = onVariant,
                fontSize = 11.sp,
                modifier = Modifier
                    .clickable(onClick = onAttachHabit)
                    .padding(horizontal = 6.dp, vertical = 8.dp)
            )
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Edit group", tint = onVariant)
            }
            Text(
                "Archive",
                color = error,
                fontSize = 10.sp,
                modifier = Modifier
                    .clickable(onClick = onArchive)
                    .padding(4.dp)
            )
        }
    }
}

/** Square habit tile — matched to Today HabitSquare size/density. Tap = edit; long-press = actions. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ManageHabitSquare(
    habit: Habit,
    glyph: String,
    subtitle: String,
    onSurface: Color,
    onVariant: Color,
    primary: Color,
    surface: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    menuExpanded: Boolean,
    onDismissMenu: () -> Unit,
    onArchive: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemoveFromGroup: () -> Unit,
    canRemoveFromGroup: Boolean
) {
    val desc = habit.description.ifBlank { habit.why }.trim()
    Box {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            shape = RoundedCornerShape(14.dp),
            color = surface,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                GlyphIcon(
                    glyph = glyph,
                    size = 26.dp,
                    tintForSimple = primary
                )
                Spacer(Modifier.height(6.dp))
                BasicText(
                    text = habit.name,
                    style = TextStyle(
                        color = onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (desc.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    BasicText(
                        text = desc,
                        style = TextStyle(color = onVariant, fontSize = 9.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    BasicText(
                        text = subtitle,
                        style = TextStyle(color = onVariant, fontSize = 9.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = onDismissMenu
        ) {
            DropdownMenuItem(
                text = { Text("Edit") },
                onClick = {
                    onDismissMenu()
                    onClick()
                }
            )
            DropdownMenuItem(text = { Text("Move up") }, onClick = onMoveUp)
            DropdownMenuItem(text = { Text("Move down") }, onClick = onMoveDown)
            if (canRemoveFromGroup) {
                DropdownMenuItem(text = { Text("Remove from group") }, onClick = onRemoveFromGroup)
            }
            DropdownMenuItem(text = { Text("Archive") }, onClick = onArchive)
        }
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

/** Collapsible panel for Manage → Time (one section open at a time keeps the tab scannable). */
@Composable
private fun TimePanel(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
            colors = CardDefaults.cardColors(
                containerColor = if (expanded)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else
                    MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        subtitle,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    if (expanded) "▾" else "▸",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (expanded) {
            content()
        }
    }
}

@Composable
private fun GadgetbridgeBlockPanel(
    prefs: com.steady.habittracker.data.GadgetbridgePrefs,
    wearableDays: List<com.steady.habittracker.data.WearableDayMetrics>,
    onUpdate: (com.steady.habittracker.data.GadgetbridgePrefs) -> Unit,
    onSyncNow: () -> Unit,
    onPickFile: () -> Unit = {},
    onRelink: (String) -> Unit = {}
) {
    val today = wearableDays.maxByOrNull { it.date }
    val fileLabel = prefs.exportDisplayName.ifBlank {
        prefs.exportLocation.substringAfterLast('/').ifBlank { "No file linked" }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Gadgetbridge export",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp
            )
            Text(
                "Pick the SQLite export from Gadgetbridge (auto-export file, often named " +
                    "Gadgetbridge or Gadgetbridge.db). Steady validates the schema, remembers the path, " +
                    "and polls on your interval for steps / sleep / HR.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (prefs.schemaValidatedAt > 0L) "Linked · $fileLabel" else "Not linked · $fileLabel",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (prefs.schemaValidatedAt > 0L) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (prefs.exportLocation.startsWith("content://")) {
                Text(
                    prefs.exportLocation.take(64) + if (prefs.exportLocation.length > 64) "…" else "",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onPickFile) {
                    Text(
                        if (prefs.exportLocation.isBlank()) "Choose database…" else "Change file…",
                        fontSize = 12.sp
                    )
                }
                if (prefs.exportLocation.isNotBlank()) {
                    TextButton(onClick = { onRelink(prefs.exportLocation) }) {
                        Text("Re-validate", fontSize = 12.sp)
                    }
                }
            }
            Text("Check interval", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(15, 30, 60, 120, 180).forEach { m ->
                    FilterChip(
                        selected = prefs.pollIntervalMinutes == m,
                        onClick = {
                            onUpdate(prefs.copy(pollIntervalMinutes = m))
                        },
                        label = { Text(if (m < 60) "${m}m" else "${m / 60}h", fontSize = 11.sp) }
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Import steps", fontSize = 12.sp)
                Switch(
                    checked = prefs.importSteps,
                    onCheckedChange = { onUpdate(prefs.copy(importSteps = it)) }
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Import sleep", fontSize = 12.sp)
                Switch(
                    checked = prefs.importSleep,
                    onCheckedChange = { onUpdate(prefs.copy(importSleep = it)) }
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Import heart rate", fontSize = 12.sp)
                Switch(
                    checked = prefs.importHeartRate,
                    onCheckedChange = { onUpdate(prefs.copy(importHeartRate = it)) }
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("History frames", fontSize = 12.sp)
                    Text(
                        "Steps · sleep · HR charts in History",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = prefs.showHistoryFrames,
                    onCheckedChange = { onUpdate(prefs.copy(showHistoryFrames = it)) }
                )
            }
            Text("Special event alerts", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Notify on events", fontSize = 12.sp)
                Switch(
                    checked = prefs.notifyEvents,
                    onCheckedChange = { onUpdate(prefs.copy(notifyEvents = it)) }
                )
            }
            if (prefs.notifyEvents) {
                OutlinedTextField(
                    value = prefs.stepGoal.toString(),
                    onValueChange = { v ->
                        v.filter { it.isDigit() }.toIntOrNull()?.let {
                            onUpdate(prefs.copy(stepGoal = it.coerceIn(1000, 50000)))
                        }
                    },
                    label = { Text("Step goal") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = prefs.sleepMinHours.toString(),
                    onValueChange = { v ->
                        v.toFloatOrNull()?.let {
                            onUpdate(prefs.copy(sleepMinHours = it.coerceIn(3f, 12f)))
                        }
                    },
                    label = { Text("Min sleep hours (short alert)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = prefs.hrHighThreshold.toString(),
                    onValueChange = { v ->
                        v.filter { it.isDigit() }.toIntOrNull()?.let {
                            onUpdate(prefs.copy(hrHighThreshold = it.coerceIn(100, 220)))
                        }
                    },
                    label = { Text("High HR threshold (bpm)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Step goal alerts", fontSize = 11.sp)
                    Switch(
                        checked = prefs.notifyStepGoal,
                        onCheckedChange = { onUpdate(prefs.copy(notifyStepGoal = it)) }
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Personal best steps", fontSize = 11.sp)
                    Switch(
                        checked = prefs.notifyPersonalBest,
                        onCheckedChange = { onUpdate(prefs.copy(notifyPersonalBest = it)) }
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Short sleep", fontSize = 11.sp)
                    Switch(
                        checked = prefs.notifySleepShort,
                        onCheckedChange = { onUpdate(prefs.copy(notifySleepShort = it)) }
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("High HR", fontSize = 11.sp)
                    Switch(
                        checked = prefs.notifyHrHigh,
                        onCheckedChange = { onUpdate(prefs.copy(notifyHrHigh = it)) }
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Resting HR range", fontSize = 11.sp)
                    Switch(
                        checked = prefs.notifyRestingHr,
                        onCheckedChange = { onUpdate(prefs.copy(notifyRestingHr = it)) }
                    )
                }
            }
            val status = buildString {
                append(prefs.lastStatus.ifBlank { "Not synced yet" })
                if (prefs.lastError.isNotBlank()) append(" · ${prefs.lastError}")
            }
            Text(status, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (today != null) {
                Text(
                    "Latest ${today.date}: " +
                        listOfNotNull(
                            today.steps?.let { "$it steps" },
                            today.sleepMinutes?.let { "${it / 60}h ${it % 60}m sleep" },
                            today.avgHeartRate?.let { "HR avg $it" }
                        ).joinToString(" · ").ifBlank { "no metrics" },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            TextButton(onClick = onSyncNow) {
                Text("Sync now", fontSize = 12.sp)
            }
        }
    }
}


@Composable
private fun OralHygieneBlockPanel(
    prefs: com.steady.habittracker.data.OralHygienePrefs,
    activeHabits: List<Habit>,
    groups: List<Group>,
    onUpdate: (com.steady.habittracker.data.OralHygienePrefs) -> Unit
) {
    val groupNames = groups.associate { it.id to it.name }
    fun set(p: com.steady.habittracker.data.OralHygienePrefs) {
        onUpdate(p.copy(enabled = true))
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Oral care steps",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp
            )
            Text(
                "Each enabled step is a Today habit in morning and evening. " +
                    "Toggle options below — they apply immediately.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OralStepSwitch("🪥  Brushing", prefs.brush) { set(prefs.copy(brush = it)) }
            OralStepSwitch("🧵  Flossing", prefs.floss) { set(prefs.copy(floss = it)) }
            OralStepSwitch("👅  Tongue scraping", prefs.tongueScrape) {
                set(prefs.copy(tongueScrape = it))
            }
            OralStepSwitch("💦  Water floss / flush", prefs.waterFlush) {
                set(prefs.copy(waterFlush = it))
            }
            OralStepSwitch("🧴  Mouthwash", prefs.mouthwash) { set(prefs.copy(mouthwash = it)) }

            if (prefs.waterFlush) {
                OutlinedTextField(
                    value = prefs.waterFlushLabel,
                    onValueChange = { set(prefs.copy(waterFlushLabel = it.take(40))) },
                    label = { Text("Water step label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Text("Brush target", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1, 2, 3).forEach { m ->
                    FilterChip(
                        selected = prefs.brushMinutes == m,
                        onClick = { set(prefs.copy(brushMinutes = m)) },
                        label = { Text("${m} min", fontSize = 11.sp) }
                    )
                }
            }

            Text("Points per step", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(5, 10, 15, 20).forEach { pts ->
                    FilterChip(
                        selected = prefs.pointValue == pts,
                        onClick = { set(prefs.copy(pointValue = pts)) },
                        label = { Text("$pts", fontSize = 11.sp) }
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Essential (harder to skip)", fontSize = 12.sp)
                    Text(
                        "Hygiene foundations",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = prefs.essential,
                    onCheckedChange = { set(prefs.copy(essential = it)) }
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Stack order", fontSize = 12.sp)
                    Text(
                        "Brush → floss → tongue → …",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = prefs.stackOrder,
                    onCheckedChange = { set(prefs.copy(stackOrder = it)) }
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Morning + evening", fontSize = 12.sp)
                    Text(
                        "Required: each step on both routines",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = prefs.morningAndEvening,
                    onCheckedChange = { set(prefs.copy(morningAndEvening = it)) }
                )
            }

            if (activeHabits.isNotEmpty()) {
                Text(
                    "On planner (${activeHabits.size})",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                activeHabits.forEach { h ->
                    val places = buildList {
                        add(groupNames[h.groupId] ?: h.groupId)
                        h.additionalGroupIds.forEach { gid ->
                            add(groupNames[gid] ?: gid)
                        }
                    }.distinct().joinToString(" + ")
                    Text(
                        "${h.icon.ifBlank { "◆" }} ${h.name} · $places",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun OralStepSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
