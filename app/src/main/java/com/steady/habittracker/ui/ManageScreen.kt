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
 * Manage = catalog workshop:
 * - Groups = when on the 24h timeline (Morning, Focus, Bedtime, Sleep)
 * - Tags = what it is for History (Supplements, Movement…)
 * - Sleep settings anchor Morning + Bedtime to bed/wake
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
        specificDates: List<String>
    ) -> Unit,
    onDeleteHabit: (String) -> Unit,  // now archives
    onSetReminder: (Reminder) -> Unit,
    onToggleReminder: (String) -> Unit,
    onArchiveGroup: (String) -> Unit = {},
    onExportCsv: () -> Unit = {},
    onImportCsv: () -> Unit = {},
    onUpdateHabit: (Habit) -> Unit = {},
    onUnarchiveGroup: (String) -> Unit = {},
    onUnarchiveHabit: (String) -> Unit = {},
    onReorderHabit: (habitId: String, direction: Int) -> Unit = { _, _ -> },
    onMoveHabitToGroup: (habitId: String, groupId: String) -> Unit = { _, _ -> },
    onApplySchedulePreset: (name: String, blocks: List<TimeBlock>) -> Unit = { _, _ -> },
    onSetActiveSchedule: (String?) -> Unit = {},
    onUpdateScheduleBlocks: (scheduleId: String, blocks: List<TimeBlock>) -> Unit = { _, _ -> },
    onAddTag: (String) -> Unit = {},
    onUpdateSleep: (SleepSettings) -> Unit = {},
    onApplySleepSchedule: () -> Unit = {},
    onSaveRoutine: (com.steady.habittracker.data.ExerciseRoutine) -> Unit = {},
    onArchiveRoutine: (String) -> Unit = {},
    onLoadBlueprintRoutines: () -> Unit = {},
    onStartRoutine: (com.steady.habittracker.data.ExerciseRoutine) -> Unit = {},
    schedules: List<Schedule> = emptyList(),
    activeScheduleId: String? = null
) {
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    var showAddGroup by remember { mutableStateOf(false) }
    var showAddHabitFor by remember { mutableStateOf<String?>(null) }
    var showReminderFor by remember { mutableStateOf<Group?>(null) }
    var showEditHabit by remember { mutableStateOf<Habit?>(null) }
    var moveHabitId by remember { mutableStateOf<String?>(null) }
    var stackHabitId by remember { mutableStateOf<String?>(null) }
    var showRoutineEditor by remember { mutableStateOf<com.steady.habittracker.data.ExerciseRoutine?>(null) }
    var showNewRoutine by remember { mutableStateOf(false) }

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
                Column(Modifier.weight(1f)) {
                    Text("Timeline groups", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("When you do it · Tags = what it is", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                }
                TextButton(onClick = { showAddGroup = true }) {
                    Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Group", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                }
            }

            // Scrollable content: groups + sleep + tags + scheduling + backup + archived
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // --- Sleep spine (Bryan Johnson priority) ---
                item {
                    SleepSettingsCard(
                        sleep = appData.sleep,
                        groups = activeGroups,
                        onUpdate = onUpdateSleep,
                        onApply = onApplySleepSchedule
                    )
                }

                items(activeGroups) { group ->
                    val subCount = appData.groups.count { it.parentId == group.id && !it.archived }
                    val count = appData.habits.count { it.groupId == group.id && !it.archived }
                    val linked = when (group.id) {
                        appData.sleep.morningGroupId -> " · linked to wake"
                        appData.sleep.bedtimeGroupId -> " · linked to bed"
                        appData.sleep.sleepGroupId -> " · overnight sleep"
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
                                Text(group.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "$count items · ${group.timeHint}$linked" + if (subCount > 0) " · $subCount subgroups" else "",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                            Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // --- Category tags ---
                item {
                    TagManagerCard(
                        tags = HabitDomain.getActiveTags(appData),
                        habits = appData.habits.filter { !it.archived },
                        onAddTag = onAddTag
                    )
                }

                // Daily group scheduling (advanced time blocks for current group/widget; key for reminders & 24h)
                item {
                    Spacer(Modifier.height(12.dp))
                    Text("24h Timeline (groups on the clock)", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    val activeSched = schedules.firstOrNull { it.id == activeScheduleId }
                    if (activeSched != null) {
                        Text("Active: ${activeSched.name} (${activeSched.timeBlocks.size} blocks)", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                    } else {
                        Text("Using legacy time hints (no advanced schedule active)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }

                    // Focus on custom schedules (no hardcoded presets per UX feedback)
                    Text("Create and edit your own 24-hour schedules below. Add blocks, assign groups (incl. Sleep), set custom colors.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)

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
                            OutlinedButton(
                                onClick = { onSetActiveSchedule(null) },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Deactivate", fontSize = 12.sp)
                            }
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
                                        val startFrac = (sMin / (24f * 60)).coerceIn(0f, 1f)
                                        val endFrac = (eMin / (24f * 60)).coerceIn(0f, 1f)
                                        val blockColor = blk.color?.let { Color(it) } ?: accent
                                        if (sMin < eMin) {
                                            // normal non-overnight
                                            val bw = ((endFrac - startFrac).coerceAtLeast(0.02f)) * w
                                            val bx = startFrac * w
                                            drawRoundRect(
                                                color = blockColor.copy(alpha = 0.75f),
                                                topLeft = Offset(bx, 4f),
                                                size = Size(bw, h - 8f),
                                                cornerRadius = CornerRadius(3.dp.toPx())
                                            )
                                        } else {
                                            // overnight: draw tail (start->24) + head (0->end)
                                            // tail
                                            val tailW = (1f - startFrac).coerceAtLeast(0.01f) * w
                                            drawRoundRect(
                                                color = blockColor.copy(alpha = 0.75f),
                                                topLeft = Offset(startFrac * w, 4f),
                                                size = Size(tailW, h - 8f),
                                                cornerRadius = CornerRadius(3.dp.toPx())
                                            )
                                            // head from midnight
                                            val headW = endFrac.coerceAtLeast(0.01f) * w
                                            drawRoundRect(
                                                color = blockColor.copy(alpha = 0.75f),
                                                topLeft = Offset(0f, 4f),
                                                size = Size(headW, h - 8f),
                                                cornerRadius = CornerRadius(3.dp.toPx())
                                            )
                                        }
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
                            // Color palette for custom segment colors (persisted)
                            val colorPalette = listOf(
                                0xFF22C55E.toInt(), // green
                                0xFF3B82F6.toInt(), // blue
                                0xFFF97316.toInt(), // orange
                                0xFF8B5CF6.toInt(), // purple
                                0xFF14B8A6.toInt(), // teal
                                0xFFEF4444.toInt(), // red
                                0xFFFBBF24.toInt(), // amber
                                0xFFEC4899.toInt(), // pink
                            )
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
                                    // Per-block color swatch (click to cycle)
                                    val currentColor = block.color ?: MaterialTheme.colorScheme.primary.value.toInt()
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .background(Color(currentColor), CircleShape)
                                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                            .clickable {
                                                val curIdx = colorPalette.indexOfFirst { it == (block.color ?: -1) }.coerceAtLeast(-1)
                                                val nextColor = colorPalette[(curIdx + 1) % colorPalette.size]
                                                val m = editBlocks.toMutableList()
                                                m[index] = block.copy(color = nextColor)
                                                editBlocks = m
                                            }
                                    )
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

                        // Wrap so labels are not clipped; allow horizontal scroll on small screens
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 4.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val fg = allActiveGroups.firstOrNull()?.id ?: ""
                                    editBlocks = editBlocks + TimeBlock("09:00", "10:00", fg)
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.heightIn(min = 40.dp)
                            ) { Text("+ Block", fontSize = 12.sp) }
                            Button(
                                onClick = {
                                    val sId = appData.sleep.sleepGroupId
                                        ?: allActiveGroups.firstOrNull { it.timeHint == "SLEEP" || it.name.equals("Sleep", true) }?.id
                                    if (sId != null) {
                                        val bed = appData.sleep.bedTime
                                        val wake = appData.sleep.wakeTime
                                        editBlocks = editBlocks + TimeBlock(bed, wake, sId, color = 0xFF64748B.toInt())
                                    } else {
                                        onApplySleepSchedule()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.heightIn(min = 40.dp)
                            ) { Text("+ Sleep", fontSize = 12.sp) }
                        }
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                        ) {
                            TextButton(
                                onClick = { isEditingSchedule = false },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) { Text("Cancel", fontSize = 12.sp) }
                            Button(
                                onClick = {
                                    val cleaned = editBlocks.filter { it.start.contains(":") && it.end.contains(":") && it.groupId.isNotBlank() }
                                        .map { it.copy(start = normalizeTime(it.start), end = normalizeTime(it.end)) }
                                    if (activeSched != null) {
                                        onUpdateScheduleBlocks(activeSched.id, cleaned)
                                    } else {
                                        onApplySchedulePreset("Custom", cleaned)
                                    }
                                    isEditingSchedule = false
                                },
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                modifier = Modifier.heightIn(min = 40.dp)
                            ) { Text("Apply", fontSize = 12.sp) }
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

                // Exercise routines (#20–#22)
                item {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Exercise routines", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Structured workouts · Start from Today or here", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
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
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                } else {
                    items(activeRoutines, key = { it.id }) { rt ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(rt.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(
                                    listOfNotNull(
                                        "${rt.exercises.size} exercises",
                                        "~${rt.estimatedDurationMin} min",
                                        HabitDomain.showRuleLabel(
                                            com.steady.habittracker.data.Habit(
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

            // Fixed header — arrow + title both go back (not only the icon)
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
                "Timeline group = when. Tags = History category (move freely without losing Supplements etc.).",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            TextButton(onClick = { showAddHabitFor = group.id }) { Text("+ Add item") }

            val nameById = appData.habits.associate { it.id to it.name }
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(habitsInGroup, key = { it.id }) { h ->
                    val afterLabel = h.afterHabitId?.let { nameById[it] }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { showEditHabit = h }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(start = if (afterLabel != null) 12.dp else 0.dp)) {
                                Text(
                                    (if (afterLabel != null) "↳ " else "• ") + h.name,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                val tagLabels = HabitDomain.tagNamesForHabit(appData, h)
                                Text(
                                    listOfNotNull(
                                        HabitDomain.showRuleLabel(h),
                                        if (!h.canSkip) "essential" else null,
                                        tagLabels.take(3).joinToString(", ").ifBlank { null }
                                    ).joinToString(" · "),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                                if (h.additionalGroupIds.isNotEmpty()) {
                                    val alsoNames = h.additionalGroupIds.mapNotNull { aid ->
                                        appData.groups.find { it.id == aid && !it.archived }?.name
                                    }
                                    if (alsoNames.isNotEmpty()) {
                                        Text(
                                            "Also in: ${alsoNames.joinToString(", ")}",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                                if (afterLabel != null) {
                                    Text("after $afterLabel", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                                }
                            }
                            TextButton(onClick = { onReorderHabit(h.id, -1) }) {
                                Text("↑", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                            }
                            TextButton(onClick = { onReorderHabit(h.id, 1) }) {
                                Text("↓", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                            }
                            IconButton(onClick = { showEditHabit = h }) {
                                Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { moveHabitId = h.id }) {
                                Text("Move", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                            }
                            TextButton(onClick = { stackHabitId = h.id }) {
                                Text(if (h.afterHabitId != null) "Restack" else "Stack after…", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                            }
                            if (h.afterHabitId != null) {
                                TextButton(onClick = {
                                    onUpdateHabit(h.copy(afterHabitId = null))
                                }) {
                                    Text("Unstack", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

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
        AddHabitWithTypeDialogLocal(
            groupId = gid,
            tags = HabitDomain.getActiveTags(appData),
            onDismiss = { showAddHabitFor = null },
            onAdd = onAddHabit
        )
    }
    showReminderFor?.let { g ->
        val ex = appData.reminders.firstOrNull { it.groupId == g.id }
        ReminderDialog(g, ex, { showReminderFor = null }, onSetReminder)
    }
    showEditHabit?.let { h ->
        EditHabitDialog(
            habit = h,
            allHabits = appData.habits.filter { !it.archived },
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
    moveHabitId?.let { hid ->
        AlertDialog(
            onDismissRequest = { moveHabitId = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Move to timeline group") },
            text = {
                Column {
                    Text(
                        "Tags (Supplements, etc.) stay on the item for History.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    activeGroups.forEach { g ->
                        TextButton(onClick = {
                            onMoveHabitToGroup(hid, g.id)
                            moveHabitId = null
                        }) {
                            Text(g.name, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { moveHabitId = null }) { Text("Cancel") }
            }
        )
    }
    stackHabitId?.let { hid ->
        val candidates = appData.habits.filter { !it.archived && it.id != hid }
        AlertDialog(
            onDismissRequest = { stackHabitId = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Stack after…") },
            text = {
                Column {
                    Text("This item will sit under the chosen one on Today.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    candidates.forEach { c ->
                        TextButton(onClick = {
                            val h = appData.habits.find { it.id == hid }
                            if (h != null) onUpdateHabit(h.copy(afterHabitId = c.id))
                            stackHabitId = null
                        }) {
                            Text("${c.name} (${appData.groups.find { it.id == c.groupId }?.name ?: "?"})", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { stackHabitId = null }) { Text("Cancel") }
            }
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

@Composable
private fun EditHabitDialog(
    habit: Habit,
    allHabits: List<Habit>,
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
    var groupId by remember { mutableStateOf(habit.groupId) }
    var additionalGroupIds by remember {
        mutableStateOf(habit.additionalGroupIds.filter { it != habit.groupId }.toSet())
    }
    var showPreset by remember { mutableStateOf(habit.showPreset) }
    var weekdays by remember { mutableStateOf(habit.weekdays) }
    var intervalDays by remember { mutableIntStateOf(habit.intervalDays.coerceAtLeast(1)) }
    var specificDates by remember { mutableStateOf(habit.specificDates) }
    var afterId by remember { mutableStateOf(habit.afterHabitId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text("Edit ${habit.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text("Type", fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    HabitType.entries.forEach { t ->
                        ThemedFilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.name.take(6), fontSize = 10.sp) })
                    }
                }
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
                Text("Primary timeline group (when)", fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    groups.forEach { g ->
                        ThemedFilterChip(
                            selected = groupId == g.id,
                            onClick = {
                                groupId = g.id
                                additionalGroupIds = additionalGroupIds - g.id
                            },
                            label = { Text(g.name.take(10), fontSize = 10.sp) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Also appear in (e.g. split-dose AM + PM)", fontSize = 11.sp)
                Text(
                    "Same habit, one log per day — shows in multiple sections on Today.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    groups.filter { it.id != groupId }.forEach { g ->
                        ThemedFilterChip(
                            selected = g.id in additionalGroupIds,
                            onClick = {
                                additionalGroupIds =
                                    if (g.id in additionalGroupIds) additionalGroupIds - g.id
                                    else additionalGroupIds + g.id
                            },
                            label = { Text(g.name.take(10), fontSize = 10.sp) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Tags (what — for History)", fontSize = 11.sp)
                TagChipRow(tags = tags, selected = selectedTags) { id ->
                    selectedTags = if (id in selectedTags) selectedTags - id else selectedTags + id
                }
                Spacer(Modifier.height(6.dp))
                Text("Stack after", fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ThemedFilterChip(selected = afterId == null, onClick = { afterId = null }, label = { Text("None", fontSize = 10.sp) })
                    allHabits.filter { it.id != habit.id }.take(8).forEach { o ->
                        ThemedFilterChip(
                            selected = afterId == o.id,
                            onClick = { afterId = o.id },
                            label = { Text(o.name.take(12), fontSize = 10.sp) }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ThemedCheckbox(checked = !canSkip, onCheckedChange = { canSkip = !it })
                    Text("Essential (harder to skip)", fontSize = 12.sp)
                }
                if (type != HabitType.CHECKBOX && type != HabitType.NOTE) {
                    OutlinedTextField(value = defaultValue, onValueChange = { defaultValue = it }, label = { Text("Default value") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("Unit") }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val target = defaultValue.toDoubleOrNull()
                val anchor = if (showPreset == ShowPreset.EVERY_N_DAYS) {
                    habit.anchorDate ?: LocalDate.now().toString()
                } else habit.anchorDate
                val tagList = selectedTags.toList()
                onSave(
                    habit.copy(
                        name = name.trim(),
                        type = type,
                        canSkip = canSkip,
                        target = target,
                        unit = unit,
                        isSupplement = TagIds.SUPPLEMENTS in tagList,
                        tags = tagList,
                        groupId = groupId,
                        additionalGroupIds = additionalGroupIds.filter { it != groupId }.toList(),
                        showPreset = showPreset,
                        weekdays = if (weekdays.isEmpty()) setOf(1, 2, 3, 4, 5, 6, 7) else weekdays,
                        intervalDays = intervalDays,
                        anchorDate = anchor,
                        specificDates = specificDates,
                        afterHabitId = afterId
                    )
                )
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
        specificDates: List<String>
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(HabitType.CHECKBOX) }
    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    var showPreset by remember { mutableStateOf(ShowPreset.DAILY) }
    var weekdays by remember { mutableStateOf(setOf(1, 2, 3, 4, 5, 6, 7)) }
    var intervalDays by remember { mutableIntStateOf(2) }
    var specificDates by remember { mutableStateOf(listOf<String>()) }
    var addedCount by remember { mutableIntStateOf(0) }

    fun submit(close: Boolean) {
        if (name.isNotBlank()) {
            val list = selectedTags.toList()
            onAdd(
                name.trim(), groupId, type, TagIds.SUPPLEMENTS in list, list,
                showPreset, weekdays, intervalDays, specificDates
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
            Text(if (addedCount > 0) "New item (+$addedCount)" else "New item")
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
                Text("Type", fontSize = 11.sp)
                Row {
                    HabitType.entries.forEach { t ->
                        ThemedFilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.name.take(6), fontSize = 10.sp) })
                    }
                }
                Spacer(Modifier.height(6.dp))
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
                Spacer(Modifier.height(6.dp))
                Text("Tags (History category)", fontSize = 11.sp)
                TagChipRow(tags = tags, selected = selectedTags) { id ->
                    selectedTags = if (id in selectedTags) selectedTags - id else selectedTags + id
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { submit(false) }) { Text("Add another") }
                TextButton(onClick = { submit(true) }) { Text(if (name.isNotBlank() || addedCount == 0) "Add & close" else "Done") }
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
private fun SleepSettingsCard(
    sleep: SleepSettings,
    groups: List<Group>,
    onUpdate: (SleepSettings) -> Unit,
    onApply: () -> Unit
) {
    var bed by remember(sleep.bedTime) { mutableStateOf(sleep.bedTime) }
    var wake by remember(sleep.wakeTime) { mutableStateOf(sleep.wakeTime) }
    var wind by remember(sleep.windDownMinutes) { mutableIntStateOf(sleep.windDownMinutes) }
    var mornMin by remember(sleep.morningMinutes) { mutableIntStateOf(sleep.morningMinutes) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Sleep (day spine)", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Morning starts at wake · Bedtime ends at bed · Sleep fills overnight. Protect sleep first.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = wake,
                    onValueChange = { if (it.length <= 5) wake = it },
                    label = { Text("Wake") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                )
                OutlinedTextField(
                    value = bed,
                    onValueChange = { if (it.length <= 5) bed = it },
                    label = { Text("Bed") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Morning ${mornMin}m", fontSize = 11.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { mornMin = (mornMin - 15).coerceAtLeast(30) }) { Text("−", fontSize = 12.sp) }
                TextButton(onClick = { mornMin = (mornMin + 15).coerceAtMost(180) }) { Text("+", fontSize = 12.sp) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Wind-down ${wind}m", fontSize = 11.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { wind = (wind - 15).coerceAtLeast(15) }) { Text("−", fontSize = 12.sp) }
                TextButton(onClick = { wind = (wind + 15).coerceAtMost(180) }) { Text("+", fontSize = 12.sp) }
            }
            val mornName = groups.find { it.id == sleep.morningGroupId }?.name ?: "Morning"
            val bedName = groups.find { it.id == sleep.bedtimeGroupId }?.name ?: "Bedtime"
            Text(
                "Linked: $mornName @ wake · $bedName before bed · Sleep overnight",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    onUpdate(
                        sleep.copy(
                            bedTime = normalizeTime(bed),
                            wakeTime = normalizeTime(wake),
                            windDownMinutes = wind,
                            morningMinutes = mornMin
                        )
                    )
                    onApply()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Apply sleep-centered day", fontSize = 12.sp)
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
