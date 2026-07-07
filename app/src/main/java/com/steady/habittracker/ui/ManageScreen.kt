package com.steady.habittracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.steady.habittracker.data.AppData
import com.steady.habittracker.data.Group
import com.steady.habittracker.data.Habit
import com.steady.habittracker.data.HabitType
import com.steady.habittracker.data.Reminder
import com.steady.habittracker.data.Schedule
import com.steady.habittracker.data.TimeBlock

/**
 * Manage with drill-down:
 * - List of groups (click to open)
 * - Inside group: habits (edit via dialog), add, reminder, archive group
 * - No immediate delete buttons at list level
 * - Backup section for CSV
 */
@Composable
fun ManageScreen(
    appData: AppData,
    onAddGroup: (String, String, String?) -> Unit,
    onAddHabit: (name: String, why: String, groupId: String, type: HabitType, isSupplement: Boolean) -> Unit,
    onDeleteHabit: (String) -> Unit,  // now archives
    onSetReminder: (Reminder) -> Unit,
    onToggleReminder: (String) -> Unit,
    onArchiveGroup: (String) -> Unit = {},
    onExportCsv: () -> Unit = {},
    onImportCsv: () -> Unit = {},
    onUpdateHabit: (Habit) -> Unit = {},
    onUnarchiveGroup: (String) -> Unit = {},
    onUnarchiveHabit: (String) -> Unit = {},
    // Scheduling (daily group time assignment for widget + current group logic; key for reminders context)
    onApplySchedulePreset: (name: String, blocks: List<TimeBlock>) -> Unit = { _, _ -> },
    onSetActiveSchedule: (String?) -> Unit = {},
    schedules: List<Schedule> = emptyList(),
    activeScheduleId: String? = null
) {
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    var showAddGroup by remember { mutableStateOf(false) }
    var showAddHabitFor by remember { mutableStateOf<String?>(null) }
    var showReminderFor by remember { mutableStateOf<Group?>(null) }
    var showEditHabit by remember { mutableStateOf<Habit?>(null) }

    // Confirmation for archive (do not archive immediately; tap away / cancel = nothing)
    var confirmArchiveGroupId by remember { mutableStateOf<String?>(null) }
    var confirmArchiveHabitId by remember { mutableStateOf<String?>(null) }

    val activeGroups = appData.groups.filter { !it.archived }.sortedBy { it.order }
    val archivedGroups = appData.groups.filter { it.archived }.sortedBy { it.order }
    val archivedHabits = appData.habits.filter { it.archived }

    Column(modifier = Modifier.fillMaxSize()) {
        if (selectedGroupId == null) {
            // Group list header (fixed)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Groups (tap to edit)", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { showAddGroup = true }) {
                    Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Group", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                }
            }

            // Scrollable content: groups + scheduling + backup + archived
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(activeGroups) { group ->
                    val subCount = appData.groups.count { it.parentId == group.id && !it.archived }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedGroupId = group.id },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(group.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                if (subCount > 0) Text("$subCount subgroups", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            }
                            Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Daily group scheduling (advanced time blocks for current group/widget; key for reminders & 24h)
                item {
                    Spacer(Modifier.height(12.dp))
                    Text("Daily Scheduling (for widget + current period)", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    val activeSched = schedules.firstOrNull { it.id == activeScheduleId }
                    if (activeSched != null) {
                        Text("Active: ${activeSched.name}", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                    } else {
                        Text("Using legacy time hints (no advanced schedule active)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Button(onClick = {
                            // Build a simple standard day schedule using current groups if they exist
                            val morn = activeGroups.firstOrNull { it.timeHint == "MORNING" }?.id ?: activeGroups.getOrNull(0)?.id
                            val work = activeGroups.firstOrNull { it.timeHint == "WORK" }?.id ?: activeGroups.getOrNull(1)?.id
                            val even = activeGroups.firstOrNull { it.timeHint == "EVENING" }?.id ?: activeGroups.getOrNull(2)?.id
                            val blocks = mutableListOf<TimeBlock>()
                            if (morn != null) blocks += TimeBlock("05:00", "12:00", morn)
                            if (work != null) blocks += TimeBlock("12:00", "18:00", work)
                            if (even != null) blocks += TimeBlock("18:00", "23:00", even)
                            if (blocks.isNotEmpty()) {
                                onApplySchedulePreset("Standard Day", blocks)
                            }
                        }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                            Text("Apply Standard Day", fontSize = 11.sp)
                        }
                        OutlinedButton(onClick = { onSetActiveSchedule(null) }) {
                            Text("Use Legacy", fontSize = 11.sp)
                        }
                    }
                    Text("Advanced schedules control widget 'current group' and can influence reminders. Presets activate time-based group switching.", color = Color(0xFF475569), fontSize = 10.sp)
                    Spacer(Modifier.height(8.dp))
                }

                // Backup section inside the scrollable list
                item {
                    Spacer(Modifier.height(16.dp))
                    Text("Backup", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onExportCsv, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                            Text("Export Backup")
                        }
                        OutlinedButton(onClick = onImportCsv) {
                            Text("Import CSV")
                        }
                    }
                    Text("Exports full backup (JSON) + structure even with no entries. CSV helpers also available.", color = Color(0xFF475569), fontSize = 10.sp)
                }

                // Archived items + restore (inside scroll)
                if (archivedGroups.isNotEmpty() || archivedHabits.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(20.dp))
                        Text("Archived (tap restore to bring back)", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    items(archivedGroups) { g ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
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
                    items(archivedHabits.take(6)) { h ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("• ${h.name}", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), fontSize = 13.sp)
                            TextButton(onClick = { onUnarchiveHabit(h.id) }) {
                                Text("Restore", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

        } else {
            // Drill-down detail for selected group
            val group = activeGroups.find { it.id == selectedGroupId } ?: run {
                selectedGroupId = null
                return@Column
            }
            val habitsInGroup = appData.habits.filter { it.groupId == group.id && !it.archived }.sortedBy { it.order }
            val subs = appData.groups.filter { it.parentId == group.id && !it.archived }

            // Fixed header for drilldown
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selectedGroupId = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text(group.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { confirmArchiveGroupId = group.id }) {
                    Text("Archive Group", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }
            }

            if (subs.isNotEmpty()) {
                Text("Subgroups / Plans", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                subs.forEach { sub ->
                    Text("  • ${sub.name}", color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(4.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { showAddHabitFor = group.id }) { Text("+ Add Habit / Exercise") }

            // Scrollable habits list (takes remaining space)
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(habitsInGroup) { h ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { showEditHabit = h },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("• ${h.name} (${h.type.name})", color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        if (!h.canSkip) Text(" essential", color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
                        IconButton(onClick = { showEditHabit = h }) {
                            Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Reminder quick (pinned below scroll area)
            val rem = appData.reminders.firstOrNull { it.groupId == group.id }
            Row {
                IconButton(onClick = { showReminderFor = group }) {
                    Icon(Icons.Default.Notifications, null, tint = if (rem?.enabled == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (rem != null) Text("${rem.time} (${rem.days.size}d)", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
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
    showAddHabitFor?.let { gid ->
        AddHabitWithTypeDialogLocal(gid, { showAddHabitFor = null }, onAddHabit)
    }
    showReminderFor?.let { g ->
        val ex = appData.reminders.firstOrNull { it.groupId == g.id }
        ReminderDialog(g, ex, { showReminderFor = null }, onSetReminder)
    }
    showEditHabit?.let { h ->
        EditHabitDialog(
            habit = h,
            onDismiss = { showEditHabit = null },
            onSave = { updated ->
                onUpdateHabit(updated)
                showEditHabit = null
            },
            onArchive = { confirmArchiveHabitId = h.id; showEditHabit = null }
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
private fun EditHabitDialog(
    habit: Habit,
    onDismiss: () -> Unit,
    onSave: (Habit) -> Unit,
    onArchive: () -> Unit
) {
    var name by remember { mutableStateOf(habit.name) }
    var why by remember { mutableStateOf(habit.why) }
    var canSkip by remember { mutableStateOf(habit.canSkip) }
    var defaultValue by remember { mutableStateOf(habit.target?.toString() ?: "") }
    var unit by remember { mutableStateOf(habit.unit) }
    var isSupp by remember { mutableStateOf(habit.isSupplement) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text("Edit ${habit.name}") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                OutlinedTextField(value = why, onValueChange = { why = it }, label = { Text("Why") })
                Row {
                    Checkbox(checked = !canSkip, onCheckedChange = { canSkip = !it })
                    Text("Essential (prevent skip, e.g. hygiene)")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isSupp, onCheckedChange = { isSupp = it })
                    Text("Supplement tag (#3)", fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text("Default value (prefills log, e.g. 300 for supplements or 10 for duration)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = defaultValue, onValueChange = { defaultValue = it }, label = { Text("Default value") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("Unit (e.g. mg, min, reps)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val target = defaultValue.toDoubleOrNull()
                onSave(habit.copy(name = name, why = why, canSkip = canSkip, target = target, unit = unit, isSupplement = isSupp))
            }) { Text("Save") }
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
    groupId: String,
    onDismiss: () -> Unit,
    onAdd: (name: String, why: String, groupId: String, type: HabitType, isSupplement: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var why by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(HabitType.CHECKBOX) }
    var isSupp by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text("New Habit / Exercise") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = why, onValueChange = { why = it }, label = { Text("Why / notes") }, modifier = Modifier.fillMaxWidth())
                Text("Type", fontSize = 11.sp)
                Row { HabitType.values().forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.name.take(6)) }) } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isSupp, onCheckedChange = { isSupp = it })
                    Text("Supplement (tag for special visibility + quick add)", fontSize = 12.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onAdd(name, why, groupId, type, isSupp) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
