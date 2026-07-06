package com.steady.habittracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.steady.habittracker.data.Group
import com.steady.habittracker.data.Reminder

@Composable
fun ReminderDialog(
    group: Group?,
    existing: Reminder?,
    onDismiss: () -> Unit,
    onSave: (Reminder) -> Unit
) {
    var time by remember { mutableStateOf(existing?.time ?: "08:30") }
    val days = remember { mutableStateListOf<Int>().apply { addAll(existing?.days ?: setOf(1,2,3,4,5,6,7)) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reminder for ${group?.name ?: "Daily Review"}") },
        text = {
            Column {
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { Text("Time (HH:mm)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text("Days", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(1 to "M", 2 to "T", 3 to "W", 4 to "T", 5 to "F", 6 to "S", 7 to "S").forEach { (d, label) ->
                        FilterChip(
                            selected = d in days,
                            onClick = {
                                if (d in days) days.remove(d) else days.add(d)
                            },
                            label = { Text(label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val r = Reminder(
                    id = existing?.id ?: "rem_${System.currentTimeMillis()}",
                    groupId = group?.id,
                    time = time,
                    days = days.toSet(),
                    enabled = true
                )
                onSave(r)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
