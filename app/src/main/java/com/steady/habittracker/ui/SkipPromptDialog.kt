package com.steady.habittracker.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.steady.habittracker.data.Habit

@Composable
fun SkipPromptDialog(
    habit: Habit,
    skipCount: Int,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onLockIn: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Skipped ${habit.name} $skipCount+ times") },
        text = { Text("Modify, archive, or lock in (mark essential / no-skip)?") },
        confirmButton = { TextButton(onClick = onEdit) { Text("Edit") } },
        dismissButton = {
            Row {
                TextButton(onClick = onArchive) { Text("Archive") }
                TextButton(onClick = onLockIn) { Text("Lock in") }
                TextButton(onClick = onDismiss) { Text("Later") }
            }
        }
    )
}
