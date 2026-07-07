package com.steady.habittracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.steady.habittracker.data.Habit
import com.steady.habittracker.data.HabitType
import java.time.LocalDate

@Composable
fun LogEntryDialog(
    habit: Habit,
    onDismiss: () -> Unit,
    onLog: (value: Double, note: String, date: String) -> Unit
) {
    var valueText by remember { mutableStateOf(if (habit.target != null) habit.target.toString() else "1") }
    var note by remember { mutableStateOf("") }
    var selectedScale by remember { mutableStateOf(3) }
    var dateStr by remember { mutableStateOf(LocalDate.now().toString()) }

    val noteFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Auto open keyboard for text input (esp. gratitude/NOTE)
    LaunchedEffect(habit.id) {
        if (habit.type == HabitType.NOTE) {
            noteFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text("Log: ${habit.name}") },
        text = {
            Column {
                when (habit.type) {
                    HabitType.CHECKBOX -> {
                        Text("Mark as complete? Value will be 1.")
                    }
                    HabitType.COUNTER, HabitType.DURATION_MIN -> {
                        val isDur = habit.type == HabitType.DURATION_MIN
                        OutlinedTextField(
                            value = valueText,
                            onValueChange = { valueText = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text(if (isDur) "Duration in minutes (default ${habit.target ?: ""} ${habit.unit})" else "Value (${habit.unit.ifBlank { "amount" }})") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (isDur) Text("Tip: use decimal for partial mins, or set default in Manage", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HabitType.SCALE_1_5 -> {
                        Text("Rate 1-5")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (1..5).forEach { s ->
                                FilterChip(
                                    selected = selectedScale == s,
                                    onClick = { selectedScale = s },
                                    label = { Text(s.toString()) }
                                )
                            }
                        }
                    }
                    HabitType.NOTE -> {
                        // value ignored for pure text; focus below
                        Text("Enter your note / gratitude / reflection below", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(8.dp))
                val noteLabel = if (habit.type == HabitType.NOTE) "Journal / Gratitude / Reflection (saved with exact time)" else "Note (optional)"
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(noteLabel) },
                    modifier = Modifier.fillMaxWidth()
                        .then(if (habit.type == HabitType.NOTE) Modifier.focusRequester(noteFocusRequester) else Modifier),
                    minLines = if (habit.type == HabitType.NOTE) 5 else 2
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = dateStr,
                    onValueChange = { dateStr = it },
                    label = { Text("Date (yyyy-MM-dd) for backfill") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = when (habit.type) {
                    HabitType.SCALE_1_5 -> selectedScale.toDouble()
                    HabitType.CHECKBOX -> 1.0
                    else -> valueText.toDoubleOrNull() ?: 1.0
                }
                val d = try { LocalDate.parse(dateStr).toString() } catch (_: Exception) { LocalDate.now().toString() }
                onLog(v, note, d)
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
