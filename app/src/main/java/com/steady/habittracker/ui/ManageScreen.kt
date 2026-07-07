package com.steady.habittracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
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
    onUpdateScheduleBlocks: (scheduleId: String, blocks: List<TimeBlock>) -> Unit = { _, _ -> },
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

    // Hoisted 24h schedule editor state (must be at composable root, not inside Lazy item)
    var isEditingSchedule by remember { mutableStateOf(false) }
    var editBlocks by remember(activeScheduleId) { mutableStateOf( (schedules.firstOrNull { it.id == activeScheduleId }?.timeBlocks ?: emptyList()) ) }

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
                    Text("Daily Scheduling — 24h Timeline + Sleep", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    val activeSched = schedules.firstOrNull { it.id == activeScheduleId }
                    if (activeSched != null) {
                        Text("Active: ${activeSched.name} (${activeSched.timeBlocks.size} blocks)", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                    } else {
                        Text("Using legacy time hints (no advanced schedule active)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }

                    // Quick presets row
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Button(onClick = {
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
                            Text("Standard Day", fontSize = 11.sp)
                        }
                        Button(onClick = {
                            val sleepG = activeGroups.firstOrNull { it.name.contains("sleep", ignoreCase = true) }?.id
                                ?: activeGroups.firstOrNull { it.timeHint == "EVENING" }?.id ?: activeGroups.getOrNull(0)?.id
                            val morn = activeGroups.firstOrNull { it.timeHint == "MORNING" }?.id ?: activeGroups.getOrNull(1)?.id
                            val blocks = mutableListOf<TimeBlock>()
                            if (sleepG != null) blocks += TimeBlock("23:00", "07:00", sleepG)
                            if (morn != null) blocks += TimeBlock("07:00", "12:00", morn)
                            if (blocks.isNotEmpty()) {
                                onApplySchedulePreset("Sleep + Morning", blocks)
                            }
                        }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                            Text("Sleep+Morning", fontSize = 11.sp)
                        }
                        OutlinedButton(onClick = { onSetActiveSchedule(null) }) {
                            Text("Legacy", fontSize = 11.sp)
                        }
                    }

                    val allActiveGroups = activeGroups

                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            if (!isEditingSchedule) {
                                editBlocks = activeSched?.timeBlocks ?: emptyList()
                            }
                            isEditingSchedule = !isEditingSchedule
                        }) {
                            Text(if (isEditingSchedule) "Close Editor" else "Edit 24h Timeline", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                        }
                        if (activeSched != null && !isEditingSchedule) {
                            OutlinedButton(onClick = { onSetActiveSchedule(null) }, modifier = Modifier.height(28.dp)) { Text("Deactivate", fontSize = 10.sp) }
                        }
                    }

                    if (isEditingSchedule) {
                        // Visual 24h timeline (Canvas)
                        val timelineHeight = 48.dp
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(Modifier.padding(8.dp)) {
                                Text("24h Timeline", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                Spacer(Modifier.height(4.dp))
                                val accent = MaterialTheme.colorScheme.primary
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(timelineHeight)
                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                                ) {
                                    val w = size.width
                                    val h = size.height
                                    for (hIdx in 0..24 step 4) {
                                        val x = (hIdx / 24f) * w
                                        drawLine(color = Color.Gray.copy(alpha = 0.5f), start = Offset(x, 0f), end = Offset(x, h), strokeWidth = 1f)
                                    }
                                    editBlocks.forEachIndexed { idx, blk ->
                                        val sMin = parseHhMmToMin(blk.start) ?: 0
                                        val eMin = parseHhMmToMin(blk.end) ?: (24*60)
                                        val startFrac = sMin / (24f * 60)
                                        var endFrac = eMin / (24f * 60)
                                        if (eMin <= sMin) endFrac = 1f
                                        val bw = ((endFrac - startFrac).coerceAtLeast(0.02f)) * w
                                        val bx = startFrac * w
                                        drawRoundRect(
                                            color = accent.copy(alpha = 0.7f),
                                            topLeft = Offset(bx, 4f),
                                            size = Size(bw, h - 8f),
                                            cornerRadius = CornerRadius(3.dp.toPx())
                                        )
                                    }
                                }
                                Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("00", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 8.sp)
                                    Text("06", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 8.sp)
                                    Text("12", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 8.sp)
                                    Text("18", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 8.sp)
                                    Text("24", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 8.sp)
                                }
                            }
                        }

                        Text("Time Blocks (HH:mm; overnight ok)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                        // Use Column + regular for (body stays in @Composable context)
                        Column {
                            for (index in editBlocks.indices) {
                                val block = editBlocks[index]
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(value = block.start, onValueChange = { nv -> if (nv.length <= 5) { val m = editBlocks.toMutableList(); m[index] = block.copy(start = nv); editBlocks = m } }, label = { Text("Start") }, modifier = Modifier.width(78.dp), singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp))
                                    Spacer(Modifier.width(3.dp))
                                    OutlinedTextField(value = block.end, onValueChange = { nv -> if (nv.length <= 5) { val m = editBlocks.toMutableList(); m[index] = block.copy(end = nv); editBlocks = m } }, label = { Text("End") }, modifier = Modifier.width(78.dp), singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp))
                                    Spacer(Modifier.width(4.dp))
                                    val grp = allActiveGroups.find { it.id == block.groupId }
                                    Box(modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp)).clickable {
                                        val cur = allActiveGroups.indexOfFirst { it.id == block.groupId }.coerceAtLeast(0)
                                        val nextG = allActiveGroups.getOrNull((cur + 1) % allActiveGroups.size)
                                        if (nextG != null) { val m = editBlocks.toMutableList(); m[index] = block.copy(groupId = nextG.id); editBlocks = m }
                                    }.padding(6.dp)) {
                                        Text(grp?.name ?: "?", color = MaterialTheme.colorScheme.onSurface, fontSize = 10.sp, maxLines = 1)
                                    }
                                    IconButton(onClick = { val m = editBlocks.toMutableList(); m.removeAt(index); editBlocks = m }, modifier = Modifier.size(26.dp)) {
                                        Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                            OutlinedButton(onClick = {
                                val fg = allActiveGroups.firstOrNull()?.id ?: ""
                                editBlocks = editBlocks + TimeBlock("09:00", "10:00", fg)
                            }, modifier = Modifier.height(30.dp)) { Text("+ Block", fontSize = 10.sp) }
                            Button(onClick = {
                                var sId = allActiveGroups.firstOrNull { it.name.equals("Sleep", ignoreCase = true) }?.id
                                if (sId == null) {
                                    onAddGroup("Sleep", "SLEEP", null)
                                    sId = "g_sleep"
                                }
                                editBlocks = editBlocks + TimeBlock("23:00", "07:00", sId)
                            }, modifier = Modifier.height(30.dp)) { Text("+ Sleep 23-7", fontSize = 10.sp) }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { isEditingSchedule = false }) { Text("Cancel", fontSize = 10.sp) }
                            Button(onClick = {
                                val cleaned = editBlocks.filter { it.start.contains(":") && it.end.contains(":") && it.groupId.isNotBlank() }
                                    .map { it.copy(start = normalizeTime(it.start), end = normalizeTime(it.end)) }
                                if (activeSched != null) {
                                    onUpdateScheduleBlocks(activeSched.id, cleaned)
                                } else {
                                    onApplySchedulePreset("Custom", cleaned)
                                }
                                isEditingSchedule = false
                            }, modifier = Modifier.height(30.dp)) { Text("Apply", fontSize = 10.sp) }
                        }
                    } else if (activeSched != null && activeSched.timeBlocks.isNotEmpty()) {
                        Row(Modifier.padding(top = 2.dp)) {
                            val bs = activeSched.timeBlocks.take(3)
                            if (bs.isNotEmpty()) { val b = bs[0]; val gn = allActiveGroups.find { it.id == b.groupId }?.name?.take(6) ?: "?"; Text("${b.start}-${b.end} ${gn}  ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp) }
                            if (bs.size > 1) { val b = bs[1]; val gn = allActiveGroups.find { it.id == b.groupId }?.name?.take(6) ?: "?"; Text("${b.start}-${b.end} ${gn}  ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp) }
                            if (bs.size > 2) { val b = bs[2]; val gn = allActiveGroups.find { it.id == b.groupId }?.name?.take(6) ?: "?"; Text("${b.start}-${b.end} ${gn}  ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp) }
                        }
                    }
                    Text("Editor updates schedule. Sleep group + overnight supported for resolveCurrentGroup/widget.", color = Color(0xFF475569), fontSize = 9.sp)
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
