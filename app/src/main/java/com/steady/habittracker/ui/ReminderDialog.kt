package com.steady.habittracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.steady.habittracker.data.AppData
import com.steady.habittracker.data.Group
import com.steady.habittracker.data.HabitDomain
import com.steady.habittracker.data.Reminder

@Composable
fun ReminderDialog(
    group: Group?,
    existing: Reminder?,
    onDismiss: () -> Unit,
    onSave: (Reminder) -> Unit,
    appData: AppData? = null
) {
    val suggested = remember(group?.id, appData) {
        when {
            appData == null -> null
            group != null -> HabitDomain.suggestedReminderTimeForGroup(appData, group.id)
            else -> HabitDomain.suggestedDailyReviewTime(appData)
        }
    }
    var time by remember {
        mutableStateOf(existing?.time ?: suggested ?: "08:30")
    }
    val days = remember {
        mutableStateListOf<Int>().apply { addAll(existing?.days ?: setOf(1, 2, 3, 4, 5, 6, 7)) }
    }
    var enabled by remember { mutableStateOf(existing?.enabled ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text("Reminder for ${group?.name ?: "Daily Review"}") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enabled", modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { Text("Time (HH:mm)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (suggested != null) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { time = suggested }) {
                        Text("Use schedule time ($suggested)", fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Days", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(1 to "M", 2 to "T", 3 to "W", 4 to "T", 5 to "F", 6 to "S", 7 to "S").forEach { (d, label) ->
                        ThemedFilterChip(
                            selected = d in days,
                            onClick = {
                                if (d in days) days.remove(d) else days.add(d)
                            },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "You'll get a notification with what's still left to do.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val normalized = normalizeReminderTime(time)
                val r = Reminder(
                    id = existing?.id ?: "rem_${System.currentTimeMillis()}",
                    groupId = group?.id,
                    time = normalized,
                    days = if (days.isEmpty()) setOf(1, 2, 3, 4, 5, 6, 7) else days.toSet(),
                    enabled = enabled
                )
                onSave(r)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun normalizeReminderTime(raw: String): String {
    val parts = raw.trim().split(":")
    val h = (parts.getOrNull(0)?.toIntOrNull() ?: 8).coerceIn(0, 23)
    val m = (parts.getOrNull(1)?.toIntOrNull() ?: 0).coerceIn(0, 59)
    return "%02d:%02d".format(h, m)
}
