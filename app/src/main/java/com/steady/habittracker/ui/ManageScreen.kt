package com.steady.habittracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.steady.habittracker.data.AppData
import com.steady.habittracker.data.Group
import com.steady.habittracker.data.Habit
import com.steady.habittracker.data.HabitType
import com.steady.habittracker.data.Reminder

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
    onAddHabit: (name: String, why: String, groupId: String, type: HabitType) -> Unit,
    onDeleteHabit: (String) -> Unit,  // now archives
    onSetReminder: (Reminder) -> Unit,
    onToggleReminder: (String) -> Unit,
    onArchiveGroup: (String) -> Unit = {},
    onExportCsv: () -> Unit = {},
    onImportCsv: () -> Unit = {},
    onUpdateHabit: (Habit) -> Unit = {},
    onUnarchiveGroup: (String) -> Unit = {},
    onUnarchiveHabit: (String) -> Unit = {}
) {
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    var showAddGroup by remember { mutableStateOf(false) }
    var showAddHabitFor by remember { mutableStateOf<String?>(null) }
    var showReminderFor by remember { mutableStateOf<Group?>(null) }
    var showEditHabit by remember { mutableStateOf<Habit?>(null) }

    val activeGroups = appData.groups.filter { !it.archived }.sortedBy { it.order }
    val archivedGroups = appData.groups.filter { it.archived }.sortedBy { it.order }
    val archivedHabits = appData.habits.filter { it.archived }

    Column {
        if (selectedGroupId == null) {
            // Group list (drill down entry)
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

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(activeGroups) { group ->
                    val subCount = appData.groups.count { it.parentId == group.id && !it.archived }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedGroupId = group.id },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2937)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(group.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                if (subCount > 0) Text("$subCount subgroups", color = Color(0xFF64748B), fontSize = 11.sp)
                            }
                            Icon(Icons.Default.Edit, null, tint = Color(0xFF94A3B8))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            // Backup
            Text("Backup", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onExportCsv, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF166534))) {
                    Text("Export Backup")
                }
                OutlinedButton(onClick = onImportCsv) {
                    Text("Import CSV")
                }
            }
            Text("Exports full backup (JSON) + structure even with no entries. CSV helpers also available.", color = Color(0xFF475569), fontSize = 10.sp)

            // Archived items + restore (visible even if empty)
            if (archivedGroups.isNotEmpty() || archivedHabits.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text("Archived (tap restore to bring back)", color = Color(0xFFF87171), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                archivedGroups.forEach { g ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("📁 ${g.name}", color = Color(0xFFCBD5E1), modifier = Modifier.weight(1f))
                            TextButton(onClick = { onUnarchiveGroup(g.id) }) {
                                Text("Restore", color = Color(0xFF4ADE80), fontSize = 12.sp)
                            }
                        }
                    }
                }
                archivedHabits.take(6).forEach { h ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("• ${h.name}", color = Color(0xFF94A3B8), modifier = Modifier.weight(1f), fontSize = 13.sp)
                        TextButton(onClick = { onUnarchiveHabit(h.id) }) {
                            Text("Restore", color = Color(0xFF4ADE80), fontSize = 11.sp)
                        }
                    }
                }
            }

        } else {
            // Drill-down detail for selected group
            val group = activeGroups.find { it.id == selectedGroupId } ?: return@Column
            val habitsInGroup = appData.habits.filter { it.groupId == group.id && !it.archived }.sortedBy { it.order }
            val subs = appData.groups.filter { it.parentId == group.id && !it.archived }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selectedGroupId = null }) { Icon(Icons.Default.ArrowBack, "Back") }
                Text(group.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { onArchiveGroup(group.id); selectedGroupId = null }) {
                    Text("Archive Group", color = Color(0xFFF87171), fontSize = 11.sp)
                }
            }

            if (subs.isNotEmpty()) {
                Text("Subgroups / Plans", color = Color(0xFF94A3B8), fontSize = 12.sp)
                subs.forEach { sub ->
                    Text("  • ${sub.name}", color = Color(0xFFCBD5E1), modifier = Modifier.padding(4.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { showAddHabitFor = group.id }) { Text("+ Add Habit / Exercise") }

            LazyColumn {
                items(habitsInGroup) { h ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { showEditHabit = h },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("• ${h.name} (${h.type.name})", color = Color(0xFFCBD5E1), modifier = Modifier.weight(1f))
                        if (!h.canSkip) Text(" essential", color = Color(0xFFFCA5A5), fontSize = 10.sp)
                        IconButton(onClick = { showEditHabit = h }) {
                            Icon(Icons.Default.Edit, null, tint = Color(0xFF64748B))
                        }
                    }
                }
            }

            // Reminder quick
            val rem = appData.reminders.firstOrNull { it.groupId == group.id }
            Row {
                IconButton(onClick = { showReminderFor = group }) {
                    Icon(Icons.Default.Notifications, null, tint = if (rem?.enabled == true) Color(0xFF22C55E) else Color.Gray)
                }
                if (rem != null) Text("${rem.time} (${rem.days.size}d)", color = Color(0xFF4ADE80), fontSize = 12.sp)
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
            onArchive = { onDeleteHabit(h.id); showEditHabit = null }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${habit.name}") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                OutlinedTextField(value = why, onValueChange = { why = it }, label = { Text("Why") })
                Row {
                    Checkbox(checked = !canSkip, onCheckedChange = { canSkip = !it })
                    Text("Essential (prevent skip, e.g. hygiene)")
                }
                Spacer(Modifier.height(8.dp))
                Text("Default value (prefills log, e.g. 300 for supplements or 10 for duration)", fontSize = 10.sp, color = Color(0xFF94A3B8))
                OutlinedTextField(value = defaultValue, onValueChange = { defaultValue = it }, label = { Text("Default value") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("Unit (e.g. mg, min, reps)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val target = defaultValue.toDoubleOrNull()
                onSave(habit.copy(name = name, why = why, canSkip = canSkip, target = target, unit = unit))
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
    onAdd: (name: String, why: String, groupId: String, type: HabitType) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var why by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(HabitType.CHECKBOX) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Habit / Exercise") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = why, onValueChange = { why = it }, label = { Text("Why / notes") }, modifier = Modifier.fillMaxWidth())
                Text("Type", fontSize = 11.sp)
                Row { HabitType.values().forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.name.take(6)) }) } }
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onAdd(name, why, groupId, type) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
