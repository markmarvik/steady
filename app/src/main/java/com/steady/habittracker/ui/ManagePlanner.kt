package com.steady.habittracker.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.steady.habittracker.data.AppData
import com.steady.habittracker.data.AutoSource
import com.steady.habittracker.data.Group
import com.steady.habittracker.data.HabitDomain
import com.steady.habittracker.data.NotificationPrefs
import com.steady.habittracker.data.Reminder
import com.steady.habittracker.data.Schedule
import com.steady.habittracker.data.SleepSettings
import com.steady.habittracker.data.TimeBlock
import com.steady.habittracker.data.SleepAudioPrefs
import com.steady.habittracker.sensors.LightSampler
import com.steady.habittracker.sensors.NoiseSampler
import com.steady.habittracker.sensors.ScreenTimeReader
import com.steady.habittracker.sensors.StepCounterReader

@Composable
fun ManageSectionHeader(
    title: String,
    subtitle: String,
    action: @Composable (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        }
        action?.invoke()
    }
}

/** Sleep spine + 24h timeline in one Daily Planner card. */
@Composable
fun DailyPlannerCard(
    appData: AppData,
    groups: List<Group>,
    schedules: List<Schedule>,
    activeScheduleId: String?,
    isEditingSchedule: Boolean,
    editBlocks: List<TimeBlock>,
    onEditBlocksChange: (List<TimeBlock>) -> Unit,
    onToggleEditing: () -> Unit,
    onCloseEditing: () -> Unit,
    onApplySleep: (SleepSettings) -> Unit,
    onSetActiveSchedule: (String?) -> Unit,
    onUpdateScheduleBlocks: (scheduleId: String, blocks: List<TimeBlock>) -> Unit,
    onApplySchedulePreset: (name: String, blocks: List<TimeBlock>) -> Unit
) {
    val sleep = appData.sleep
    var bed by remember(sleep.bedTime) { mutableStateOf(sleep.bedTime) }
    var wake by remember(sleep.wakeTime) { mutableStateOf(sleep.wakeTime) }
    var wind by remember(sleep.windDownMinutes) { mutableIntStateOf(sleep.windDownMinutes) }
    var mornMin by remember(sleep.morningMinutes) { mutableIntStateOf(sleep.morningMinutes) }
    val activeSched = schedules.firstOrNull { it.id == activeScheduleId }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Daily Planner", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Sleep anchors the day · timeline places groups on the clock",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )

            Text("Sleep", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = wake,
                    onValueChange = { if (it.length <= 5) wake = it },
                    label = { Text("Wake") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 13.sp)
                )
                OutlinedTextField(
                    value = bed,
                    onValueChange = { if (it.length <= 5) bed = it },
                    label = { Text("Bed") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 13.sp)
                )
            }
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
                fontSize = 10.sp
            )
            Button(
                onClick = {
                    onApplySleep(
                        sleep.copy(
                            bedTime = normalizePlannerTime(bed),
                            wakeTime = normalizePlannerTime(wake),
                            windDownMinutes = wind,
                            morningMinutes = mornMin
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Apply sleep-centered day", fontSize = 12.sp)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text("24h Timeline", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            if (activeSched != null) {
                Text(
                    "Active: ${activeSched.name} · ${activeSched.timeBlocks.size} blocks",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp
                )
            } else {
                Text(
                    "No schedule active · using time hints",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onToggleEditing) {
                    Text(
                        if (isEditingSchedule) "Close editor" else "Edit timeline",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp
                    )
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
                TimelineEditorBody(
                    editBlocks = editBlocks,
                    onEditBlocksChange = onEditBlocksChange,
                    groups = groups,
                    sleep = sleep,
                    onApplySleep = { onApplySleep(sleep) },
                    onCancel = onCloseEditing,
                    onApply = {
                        val cleaned = editBlocks
                            .filter { it.start.contains(":") && it.end.contains(":") && it.groupId.isNotBlank() }
                            .map {
                                it.copy(
                                    start = normalizePlannerTime(it.start),
                                    end = normalizePlannerTime(it.end)
                                )
                            }
                        if (activeSched != null) {
                            onUpdateScheduleBlocks(activeSched.id, cleaned)
                        } else {
                            onApplySchedulePreset("Custom", cleaned)
                        }
                        onCloseEditing()
                    }
                )
            } else if (activeSched != null && activeSched.timeBlocks.isNotEmpty()) {
                TimelinePreviewBar(blocks = activeSched.timeBlocks)
                val preview = activeSched.timeBlocks.take(4).joinToString(" · ") { b ->
                    val gn = groups.find { it.id == b.groupId }?.name?.take(8) ?: "?"
                    "${b.start} $gn"
                }
                Text(preview, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun TimelinePreviewBar(blocks: List<TimeBlock>) {
    val accent = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
    ) {
        val w = size.width
        val h = size.height
        blocks.forEach { blk ->
            drawTimeBlock(blk, w, h, accent)
        }
    }
}

@Composable
private fun TimelineEditorBody(
    editBlocks: List<TimeBlock>,
    onEditBlocksChange: (List<TimeBlock>) -> Unit,
    groups: List<Group>,
    sleep: SleepSettings,
    onApplySleep: () -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val colorPalette = listOf(
        0xFF22C55E.toInt(), 0xFF3B82F6.toInt(), 0xFFF97316.toInt(), 0xFF8B5CF6.toInt(),
        0xFF14B8A6.toInt(), 0xFFEF4444.toInt(), 0xFFFBBF24.toInt(), 0xFFEC4899.toInt()
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.padding(8.dp)) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
            ) {
                val w = size.width
                val h = size.height
                for (hIdx in 0..24 step 4) {
                    val x = (hIdx / 24f) * w
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.5f),
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = 1f
                    )
                }
                editBlocks.forEach { blk -> drawTimeBlock(blk, w, h, accent) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("00", "06", "12", "18", "24").forEach {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 8.sp)
                }
            }
        }
    }

    Text("Time blocks (HH:mm; overnight ok)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
    Column {
        for (index in editBlocks.indices) {
            val block = editBlocks[index]
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = block.start,
                    onValueChange = { nv ->
                        if (nv.length <= 5) {
                            val m = editBlocks.toMutableList()
                            m[index] = block.copy(start = nv)
                            onEditBlocksChange(m)
                        }
                    },
                    label = { Text("Start") },
                    modifier = Modifier.width(78.dp),
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 11.sp)
                )
                Spacer(Modifier.width(3.dp))
                OutlinedTextField(
                    value = block.end,
                    onValueChange = { nv ->
                        if (nv.length <= 5) {
                            val m = editBlocks.toMutableList()
                            m[index] = block.copy(end = nv)
                            onEditBlocksChange(m)
                        }
                    },
                    label = { Text("End") },
                    modifier = Modifier.width(78.dp),
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 11.sp)
                )
                Spacer(Modifier.width(4.dp))
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
                            onEditBlocksChange(m)
                        }
                )
                Spacer(Modifier.width(4.dp))
                val grp = groups.find { it.id == block.groupId }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                        .clickable {
                            if (groups.isEmpty()) return@clickable
                            val cur = groups.indexOfFirst { it.id == block.groupId }.coerceAtLeast(0)
                            val nextG = groups[(cur + 1) % groups.size]
                            val m = editBlocks.toMutableList()
                            m[index] = block.copy(groupId = nextG.id)
                            onEditBlocksChange(m)
                        }
                        .padding(6.dp)
                ) {
                    Text(grp?.name ?: "?", color = MaterialTheme.colorScheme.onSurface, fontSize = 10.sp, maxLines = 1)
                }
                IconButton(
                    onClick = {
                        val m = editBlocks.toMutableList()
                        m.removeAt(index)
                        onEditBlocksChange(m)
                    },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                }
            }
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
    ) {
        OutlinedButton(
            onClick = {
                val fg = groups.firstOrNull()?.id ?: ""
                onEditBlocksChange(editBlocks + TimeBlock("09:00", "10:00", fg))
            },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier.heightIn(min = 40.dp)
        ) { Text("+ Block", fontSize = 12.sp) }
        Button(
            onClick = {
                val sId = sleep.sleepGroupId
                    ?: groups.firstOrNull { it.timeHint == "SLEEP" || it.name.equals("Sleep", true) }?.id
                if (sId != null) {
                    onEditBlocksChange(
                        editBlocks + TimeBlock(sleep.bedTime, sleep.wakeTime, sId, color = 0xFF64748B.toInt())
                    )
                } else {
                    onApplySleep()
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
        TextButton(onClick = onCancel, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
            Text("Cancel", fontSize = 12.sp)
        }
        Button(
            onClick = onApply,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            modifier = Modifier.heightIn(min = 40.dp)
        ) { Text("Apply", fontSize = 12.sp) }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTimeBlock(
    blk: TimeBlock,
    w: Float,
    h: Float,
    accent: Color
) {
    val sMin = parsePlannerHhMm(blk.start) ?: 0
    val eMin = parsePlannerHhMm(blk.end) ?: (24 * 60)
    val startFrac = (sMin / (24f * 60)).coerceIn(0f, 1f)
    val endFrac = (eMin / (24f * 60)).coerceIn(0f, 1f)
    val blockColor = blk.color?.let { Color(it) } ?: accent
    if (sMin < eMin) {
        val bw = ((endFrac - startFrac).coerceAtLeast(0.02f)) * w
        drawRoundRect(
            color = blockColor.copy(alpha = 0.75f),
            topLeft = Offset(startFrac * w, 4f),
            size = Size(bw, h - 8f),
            cornerRadius = CornerRadius(3f)
        )
    } else {
        val tailW = (1f - startFrac).coerceAtLeast(0.01f) * w
        drawRoundRect(
            color = blockColor.copy(alpha = 0.75f),
            topLeft = Offset(startFrac * w, 4f),
            size = Size(tailW, h - 8f),
            cornerRadius = CornerRadius(3f)
        )
        val headW = endFrac.coerceAtLeast(0.01f) * w
        drawRoundRect(
            color = blockColor.copy(alpha = 0.75f),
            topLeft = Offset(0f, 4f),
            size = Size(headW, h - 8f),
            cornerRadius = CornerRadius(3f)
        )
    }
}

@Composable
fun RemindersCard(
    appData: AppData,
    onToggleMaster: (Boolean) -> Unit,
    onToggleReminder: (String) -> Unit,
    onEditReminder: (Reminder) -> Unit,
    onAlignToSchedule: () -> Unit,
    onUpdateNotificationPrefs: (NotificationPrefs) -> Unit = {}
) {
    val context = LocalContext.current
    var permissionTick by remember { mutableIntStateOf(0) }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permissionTick++ }

    val notifOk = remember(permissionTick) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true
    }
    val exactOk = remember(permissionTick) {
        val am = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am?.canScheduleExactAlarms() == true
        } else true
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Reminders", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Times follow your Daily Planner schedule",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Habit reminders", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                    Text("Master switch for all notifications", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                }
                Switch(
                    checked = appData.remindersMasterEnabled,
                    onCheckedChange = onToggleMaster
                )
            }

            appData.reminders.sortedBy { it.time }.forEach { rem ->
                val gName = rem.groupId?.let { gid -> appData.groups.find { it.id == gid }?.name } ?: "Daily Review"
                val suggested = if (rem.groupId != null) {
                    HabitDomain.suggestedReminderTimeForGroup(appData, rem.groupId)
                } else {
                    HabitDomain.suggestedDailyReviewTime(appData)
                }
                val aligned = suggested != null && suggested == rem.time
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onEditReminder(rem) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("$gName · ${rem.time}", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                        Text(
                            buildString {
                                append("${rem.days.size} day(s)/week")
                                if (aligned) append(" · from schedule")
                                else if (suggested != null && suggested != rem.time) append(" · schedule suggests $suggested")
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = rem.enabled && appData.remindersMasterEnabled,
                        enabled = appData.remindersMasterEnabled,
                        onCheckedChange = { onToggleReminder(rem.id) }
                    )
                }
            }

            TextButton(onClick = onAlignToSchedule) {
                Text("Align times to schedule", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            Text("Smart & gentle", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Adaptive timing, quiet hours, and a daily cap keep nudges useful — not noisy.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )

            val prefs = appData.notificationPrefs
            SmartPrefSwitch(
                title = "Adaptive timing",
                subtitle = "Nudge toward when you usually log (±45 min)",
                checked = prefs.adaptiveTiming,
                onCheckedChange = { onUpdateNotificationPrefs(prefs.copy(adaptiveTiming = it)) }
            )
            SmartPrefSwitch(
                title = "Streak-risk nudge",
                subtitle = "Evening review if a multi-day streak is at risk",
                checked = prefs.streakRiskNudge,
                onCheckedChange = { onUpdateNotificationPrefs(prefs.copy(streakRiskNudge = it)) }
            )
            SmartPrefSwitch(
                title = "Celebrate full clear",
                subtitle = "One calm note when the day is complete",
                checked = prefs.celebrateFullClear,
                onCheckedChange = { onUpdateNotificationPrefs(prefs.copy(celebrateFullClear = it)) }
            )

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Quiet hours", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                    Text(
                        "${prefs.quietStart} – ${prefs.quietEnd}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("22:30" to "07:00", "23:00" to "06:30", "21:00" to "08:00").forEach { (s, e) ->
                        val selected = prefs.quietStart == s && prefs.quietEnd == e
                        TextButton(
                            onClick = { onUpdateNotificationPrefs(prefs.copy(quietStart = s, quietEnd = e)) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(
                                "$s",
                                fontSize = 10.sp,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Max notifications / day", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                    Text("Currently ${prefs.maxPerDay}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(2, 3, 4, 5).forEach { n ->
                        val selected = prefs.maxPerDay == n
                        TextButton(onClick = { onUpdateNotificationPrefs(prefs.copy(maxPerDay = n)) }) {
                            Text(
                                "$n",
                                fontSize = 12.sp,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (!notifOk) {
                TextButton(onClick = {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text("Allow notifications", fontSize = 12.sp) }
            }
            if (!exactOk) {
                TextButton(onClick = {
                    try {
                        val i = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        i.data = Uri.parse("package:${context.packageName}")
                        context.startActivity(i)
                    } catch (_: Exception) {
                        try {
                            val i2 = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            i2.data = Uri.parse("package:${context.packageName}")
                            context.startActivity(i2)
                        } catch (_: Exception) {}
                    }
                }) { Text("Allow exact alarms", fontSize = 12.sp) }
            }
            if (notifOk && exactOk) {
                Text("Notifications & exact alarms: OK", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun SmartPrefSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SleepAudioCard(
    appData: AppData,
    onUpdatePrefs: (SleepAudioPrefs) -> Unit,
    onStartNow: () -> Unit,
    onStopNow: () -> Unit
) {
    val context = LocalContext.current
    var permissionTick by remember { mutableIntStateOf(0) }
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permissionTick++ }
    val micOk = remember(permissionTick) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }
    val prefs = appData.sleepAudioPrefs
    val nights = appData.sleepNights.take(5)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Sleep audio / snore watch",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "High-efficiency OGG/Opus overnight capture (AMR fallback). " +
                    "Detects loud events and possible snoring cadence. Keeps last ${prefs.retainDays} days on device.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Enable night recording", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        if (prefs.scheduleWithSleep)
                            "Auto ${appData.sleep.bedTime} → ${appData.sleep.wakeTime}"
                        else "Manual start only",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = prefs.enabled,
                    onCheckedChange = { onUpdatePrefs(prefs.copy(enabled = it)) }
                )
            }
            SmartPrefSwitch(
                title = "Schedule with bed / wake",
                subtitle = "Uses sleep spine times",
                checked = prefs.scheduleWithSleep,
                onCheckedChange = { onUpdatePrefs(prefs.copy(scheduleWithSleep = it)) }
            )
            SmartPrefSwitch(
                title = "Only while charging",
                subtitle = "Skip start if unplugged; stop if you unplug mid-night",
                checked = prefs.requireCharging,
                onCheckedChange = { onUpdatePrefs(prefs.copy(requireCharging = it)) }
            )
            if (!micOk) {
                TextButton(onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                    Text("Allow microphone", fontSize = 12.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onStartNow) {
                    Text("Start now", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                }
                TextButton(onClick = onStopNow) {
                    Text("Stop", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            Text("Retain days", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(2, 3, 5, 7).forEach { d ->
                    val sel = prefs.retainDays == d
                    TextButton(onClick = { onUpdatePrefs(prefs.copy(retainDays = d)) }) {
                        Text(
                            "$d",
                            fontSize = 12.sp,
                            color = if (sel) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Text("Loudness sensitivity", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    2000 to "High",
                    4000 to "Med",
                    8000 to "Low"
                ).forEach { (th, label) ->
                    val sel = prefs.loudThreshold == th
                    TextButton(onClick = { onUpdatePrefs(prefs.copy(loudThreshold = th)) }) {
                        Text(
                            label,
                            fontSize = 11.sp,
                            color = if (sel) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (nights.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    "Recent nights · open History for playback",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                nights.forEach { n ->
                    Text(
                        "${n.wakeDate}: quiet ${n.quietScore} · ${n.eventCount} events · ${n.snoreLikeCount} snore-like",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Text(
                "Not a medical device. Snore labels are energy/cadence heuristics for personal tracking only.",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AutoLogCard(
    appData: AppData,
    onToggleMaster: (Boolean) -> Unit,
    onSyncNow: () -> Unit
) {
    val context = LocalContext.current
    var permissionTick by remember { mutableIntStateOf(0) }
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permissionTick++ }
    val activityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permissionTick++ }

    val usageOk = remember(permissionTick) { ScreenTimeReader.hasUsageAccess(context) }
    val lightOk = remember { LightSampler.isAvailable(context) }
    val micOk = remember(permissionTick) { NoiseSampler.hasMicPermission(context) }
    val stepsOk = remember(permissionTick) { StepCounterReader.hasPermission(context) }
    val linked = remember(appData.habits) {
        appData.habits.count { !it.archived && it.autoSource != AutoSource.NONE }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Auto-log (sensors)", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Screen use, bedtime light, ambient noise, phone steps, and Gadgetbridge/external broadcasts. All on-device.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Background sampling", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                    Text("$linked habit(s) linked · every ~6h + on open", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                }
                Switch(checked = appData.autoLogMasterEnabled, onCheckedChange = onToggleMaster)
            }
            TextButton(onClick = onSyncNow) {
                Text("Sync sensors now", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text("Permissions", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            PermissionLine("Usage access (screen time)", usageOk) {
                try {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                } catch (_: Exception) {
                }
                permissionTick++
            }
            PermissionLine("Light sensor", lightOk, actionLabel = null) {}
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                PermissionLine("Activity (phone steps)", stepsOk) {
                    activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                }
            }
            PermissionLine("Microphone (noise)", micOk) {
                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            Text(
                "Link a source on each habit (Manage → edit habit → Auto-log). " +
                    "Gadgetbridge: broadcast com.steady.habittracker.ACTION_EXTERNAL_METRIC with key=steps, value=N.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun PermissionLine(
    title: String,
    ok: Boolean,
    actionLabel: String? = "Grant",
    onGrant: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "$title · ${if (ok) "OK" else "needed"}",
            fontSize = 11.sp,
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!ok && actionLabel != null) {
            TextButton(onClick = onGrant) { Text(actionLabel, fontSize = 11.sp) }
        }
    }
}

private fun parsePlannerHhMm(hhmm: String): Int? {
    val parts = hhmm.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val m = parts.getOrNull(1)?.toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

private fun normalizePlannerTime(hhmm: String): String {
    val p = hhmm.split(":")
    val h = (p.getOrNull(0)?.toIntOrNull() ?: 0).coerceIn(0, 23)
    val m = (p.getOrNull(1)?.toIntOrNull() ?: 0).coerceIn(0, 59)
    return "%02d:%02d".format(h, m)
}
