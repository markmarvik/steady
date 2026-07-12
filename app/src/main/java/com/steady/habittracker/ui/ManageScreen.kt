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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.steady.habittracker.data.AppData
import com.steady.habittracker.data.Group
import com.steady.habittracker.data.Habit
import com.steady.habittracker.data.HabitDomain
import com.steady.habittracker.data.HabitType
import com.steady.habittracker.data.Reminder
import com.steady.habittracker.data.Schedule
import com.steady.habittracker.data.ShowPreset
import com.steady.habittracker.data.SleepSettings
import com.steady.habittracker.data.Tag
import com.steady.habittracker.data.TagIds
import com.steady.habittracker.data.TimeBlock
import java.time.LocalDate

/**
 * Manage = three focused areas:
 * - Habits: create / edit / archive habits (+ tags, exercise routines)
 * - Groups: timeline groups + membership (attach existing habits, order, move)
 * - Planner: sleep/schedule, reminders, backup
 */
@Composable
fun ManageScreen(
    appData: AppData,
    onAddGroup: (String, String, String?) -> Unit,
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
        additionalGroupIds: List<String>
    ) -> Unit,
    onDeleteHabit: (String) -> Unit,  // now archives
    onSetReminder: (Reminder) -> Unit,
    onToggleReminder: (String) -> Unit,
    onSetRemindersMasterEnabled: (Boolean) -> Unit = {},
    onAlignRemindersToSchedule: () -> Unit = {},
    onArchiveGroup: (String) -> Unit = {},
    onExportCsv: () -> Unit = {},
    onImportCsv: () -> Unit = {},
    onUpdateHabit: (Habit) -> Unit = {},
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
    // 0 Habits · 1 Groups · 2 Planner
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

    val activeGroups = appData.groups.filter { !it.archived }.sortedBy { it.order }
    val archivedGroups = appData.groups.filter { it.archived }.sortedBy { it.order }
    val archivedHabits = appData.habits.filter { it.archived }
    val groupOrder = remember(activeGroups) { activeGroups.mapIndexed { i, g -> g.id to i }.toMap() }

    // Leaving Groups clears drill-down so Habits always feels top-level
    LaunchedEffect(manageTab) {
        if (manageTab != 1) selectedGroupId = null
    }

    // Sub-tabs sit directly under the main app tabs (no extra "Manage" title)
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                .padding(4.dp)
        ) {
            TabButton("Habits", manageTab == 0, modifier = Modifier.weight(1f)) { manageTab = 0 }
            TabButton("Groups", manageTab == 1, modifier = Modifier.weight(1f)) { manageTab = 1 }
            TabButton("Planner", manageTab == 2, modifier = Modifier.weight(1f)) { manageTab = 2 }
        }

        Spacer(Modifier.height(8.dp))

        when (manageTab) {
            0 -> {
                // ——— Habits: flat catalog ———
                val activeHabits = appData.habits
                    .filter { !it.archived }
                    .sortedWith(
                        compareBy<Habit> { groupOrder[it.groupId] ?: Int.MAX_VALUE }
                            .thenBy { it.order }
                            .thenBy { it.name.lowercase() }
                    )
                val q = habitSearch.trim()
                val filteredHabits = if (q.isEmpty()) activeHabits else {
                    activeHabits.filter { h ->
                        h.name.contains(q, ignoreCase = true) ||
                            activeGroups.find { it.id == h.groupId }?.name?.contains(q, ignoreCase = true) == true
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${activeHabits.size} habits",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                            TextButton(
                                onClick = { showCreateHabit = true },
                                enabled = activeGroups.isNotEmpty()
                            ) {
                                Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(2.dp))
                                Text("Habit", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                            }
                        }
                    }
                    item {
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
                    }
                    if (activeGroups.isEmpty()) {
                        item {
                            Text(
                                "Create a timeline group first (Groups tab), then add habits.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                    } else if (filteredHabits.isEmpty()) {
                        item {
                            Text(
                                if (q.isEmpty()) "No habits yet. Tap + Habit to create one."
                                else "No matches for “$q”.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                    }
                    items(filteredHabits, key = { it.id }) { h ->
                        val groupNames = HabitDomain.membershipGroupIds(h).mapNotNull { gid ->
                            activeGroups.find { it.id == gid }?.name
                        }
                        val tagLabels = HabitDomain.tagNamesForHabit(appData, h)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showEditHabit = h },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        h.name,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        listOfNotNull(
                                            groupNames.joinToString(", ").ifBlank { null },
                                            HabitDomain.showRuleLabel(h),
                                            if (!h.canSkip) "essential" else null,
                                            tagLabels.take(2).joinToString(", ").ifBlank { null }
                                        ).joinToString(" · "),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                                IconButton(onClick = { showEditHabit = h }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = { confirmArchiveHabitId = h.id }) {
                                    Text("Archive", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    if (archivedHabits.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Archived habits",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        items(archivedHabits, key = { "arch_${it.id}" }) { h ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "• ${h.name}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                    fontSize = 13.sp
                                )
                                TextButton(onClick = { onUnarchiveHabit(h.id) }) {
                                    Text("Restore", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(8.dp))
                        TagManagerCard(
                            tags = HabitDomain.getActiveTags(appData),
                            habits = appData.habits.filter { !it.archived },
                            onAddTag = onAddTag
                        )
                    }

                    item {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Exercise routines",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Structured workouts · Start from Today or here",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }
                            Row {
                                TextButton(onClick = onLoadBlueprintRoutines) {
                                    Text("Templates", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                                }
                                TextButton(onClick = { showNewRoutine = true }) {
                                    Text("+ New", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    val activeRoutines = HabitDomain.getActiveRoutines(appData)
                    if (activeRoutines.isEmpty()) {
                        item {
                            Text(
                                "No routines yet. Load Blueprint templates or create one.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        items(activeRoutines, key = { it.id }) { rt ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        rt.name,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        listOfNotNull(
                                            "${rt.exercises.size} exercises",
                                            "~${rt.estimatedDurationMin} min",
                                            HabitDomain.showRuleLabel(
                                                Habit(
                                                    id = rt.id, name = rt.name, groupId = "",
                                                    showPreset = rt.showPreset, weekdays = rt.weekdays
                                                )
                                            ),
                                            rt.tags.take(2).joinToString(", ").ifBlank { null }
                                        ).joinToString(" · "),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(onClick = { onStartRoutine(rt) }) {
                                            Text("Start", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                                        }
                                        TextButton(onClick = { showRoutineEditor = rt }) {
                                            Text("Edit", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                        }
                                        TextButton(onClick = { onArchiveRoutine(rt.id) }) {
                                            Text("Archive", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }

            1 -> {
                // ——— Groups: list or drill-down ———
                if (selectedGroupId == null) {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            ManageSectionHeader(
                                title = "Timeline groups",
                                subtitle = "When on the day · attach habits from the Habits catalog",
                                action = {
                                    TextButton(onClick = { showAddGroup = true }) {
                                        Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(2.dp))
                                        Text("Group", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                                    }
                                }
                            )
                        }
                        items(activeGroups, key = { it.id }) { group ->
                            val subCount = appData.groups.count { it.parentId == group.id && !it.archived }
                            val count = HabitDomain.getActiveHabitsForGroup(appData, group.id).size
                            val linked = when (group.id) {
                                appData.sleep.morningGroupId -> " · wake"
                                appData.sleep.bedtimeGroupId -> " · bed"
                                appData.sleep.sleepGroupId -> " · sleep"
                                else -> ""
                            }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedGroupId = group.id },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            group.name,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "$count items · ${group.timeHint}$linked" +
                                                if (subCount > 0) " · $subCount subgroups" else "",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (archivedGroups.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Archived groups",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            items(archivedGroups, key = { "ag_${it.id}" }) { g ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("📁 ${g.name}", color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                        TextButton(onClick = { onUnarchiveGroup(g.id) }) {
                                            Text("Restore", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(12.dp)) }
                    }
                } else {
                    val group = activeGroups.find { it.id == selectedGroupId } ?: run {
                        selectedGroupId = null
                        return@Column
                    }
                    val habitsInGroup = HabitDomain.getActiveHabitsForGroup(appData, group.id)
                    val subs = appData.groups.filter { it.parentId == group.id && !it.archived }

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
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                group.name,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        TextButton(onClick = { confirmArchiveGroupId = group.id }) {
                            Text("Archive", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                        }
                    }

                    if (subs.isNotEmpty()) {
                        Text("Subgroups / Plans", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        subs.forEach { sub ->
                            Text("  • ${sub.name}", color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(4.dp))
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Habits here appear on Today in this block. Same habit can be in many groups — once each. Create new habits on the Habits tab.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    TextButton(onClick = { attachHabitToGroupId = group.id }) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(2.dp))
                        Text("Add habit", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    }

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(habitsInGroup, key = { it.id }) { h ->
                            val memberCount = HabitDomain.membershipGroupIds(h).size
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            h.name,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        val tagLabels = HabitDomain.tagNamesForHabit(appData, h)
                                        Text(
                                            listOfNotNull(
                                                HabitDomain.showRuleLabel(h),
                                                if (memberCount > 1) "$memberCount groups" else null,
                                                tagLabels.take(2).joinToString(", ").ifBlank { null }
                                            ).joinToString(" · "),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    }
                                    TextButton(onClick = { onReorderHabit(h.id, -1, group.id) }) {
                                        Text("↑", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                                    }
                                    TextButton(onClick = { onReorderHabit(h.id, 1, group.id) }) {
                                        Text("↓", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                                    }
                                    TextButton(
                                        onClick = { onRemoveHabitFromGroup(h.id, group.id) },
                                        enabled = memberCount > 1
                                    ) {
                                        Text(
                                            "Remove",
                                            color = if (memberCount > 1) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                        if (habitsInGroup.isEmpty()) {
                            item {
                                Text(
                                    "No habits in this group yet. Create habits on the Habits tab, then add them here.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                        }
                    }
                }
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
                            onAlignToSchedule = onAlignRemindersToSchedule
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
                                Text("Import CSV")
                            }
                        }
                        Text(
                            "Exports full backup (JSON) + structure even with no entries.",
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
            onAdd = { n, h, p -> onAddGroup(n, h, p); showAddGroup = false }
        )
    }
    if (showCreateHabit) {
        AddHabitWithTypeDialogLocal(
            initialGroupId = activeGroups.firstOrNull()?.id.orEmpty(),
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
                                    if (inGroups.isBlank()) h.name else "${h.name}  ·  $inGroups",
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
private fun AddGroupDialogWithParent(onDismiss: () -> Unit, onAdd: (name: String, hint: String, parent: String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("ANY") }
    var parent by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text("New Group / Plan") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = hint, onValueChange = { hint = it }, label = { Text("Time hint") }, modifier = Modifier.fillMaxWidth())
                Text("Parent (leave empty or set for Workout subgroup)", fontSize = 11.sp)
                // For simplicity a text field for parent id; real UI would dropdown
                OutlinedTextField(value = parent ?: "", onValueChange = { parent = it.ifBlank { null } }, label = { Text("Parent ID (e.g. g_workout)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onAdd(name, hint, parent) }) { Text("Add") } },
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
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(1 to "M", 2 to "T", 3 to "W", 4 to "T", 5 to "F", 6 to "S", 7 to "S").forEach { (d, lab) ->
                    ThemedFilterChip(
                        selected = d in weekdays,
                        onClick = {
                            onWeekdays(if (d in weekdays) weekdays - d else weekdays + d)
                        },
                        label = { Text(lab) }
                    )
                }
            }
        }
        ShowPreset.EVERY_N_DAYS -> {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(2, 3, 7).forEach { n ->
                    ThemedFilterChip(
                        selected = intervalDays == n,
                        onClick = { onInterval(n); onPreset(ShowPreset.EVERY_N_DAYS) },
                        label = { Text("Every ${n}d", fontSize = 11.sp) }
                    )
                }
            }
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
                            specificDates = specificDates
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
        additionalGroupIds: List<String>
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
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
                selectedGroupIds.drop(1)
            )
            name = ""
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
