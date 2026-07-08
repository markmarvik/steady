package com.steady.habittracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
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
    var valueText by remember {
        mutableStateOf(
            if (habit.target != null) {
                if (habit.target == habit.target.toLong().toDouble()) habit.target.toLong().toString()
                else habit.target.toString()
            } else "1"
        )
    }
    var note by remember { mutableStateOf("") }
    var selectedScale by remember { mutableStateOf((habit.target ?: 3.0).toInt().coerceIn(1, 5)) }
    var dateStr by remember { mutableStateOf(LocalDate.now().toString()) }
    var showDate by remember { mutableStateOf(false) }

    val noteFocusRequester = remember { FocusRequester() }
    val valueFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun commit() {
        val v = when (habit.type) {
            HabitType.SCALE_1_5 -> selectedScale.toDouble()
            HabitType.CHECKBOX -> 1.0
            HabitType.NOTE -> 1.0
            else -> valueText.toDoubleOrNull() ?: habit.target ?: 1.0
        }
        val d = try {
            LocalDate.parse(dateStr).toString()
        } catch (_: Exception) {
            LocalDate.now().toString()
        }
        onLog(v, note, d)
    }

    LaunchedEffect(habit.id) {
        when (habit.type) {
            HabitType.NOTE -> {
                noteFocusRequester.requestFocus()
                keyboardController?.show()
            }
            HabitType.COUNTER, HabitType.DURATION_MIN -> {
                valueFocusRequester.requestFocus()
                keyboardController?.show()
            }
            else -> {}
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
                        Text("Mark as complete?", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                    HabitType.COUNTER, HabitType.DURATION_MIN -> {
                        val isDur = habit.type == HabitType.DURATION_MIN
                        OutlinedTextField(
                            value = valueText,
                            onValueChange = { valueText = it.filter { c -> c.isDigit() || c == '.' } },
                            label = {
                                Text(
                                    if (isDur) "Minutes${if (habit.target != null) " (default ${habit.target})" else ""}"
                                    else "Amount${if (habit.unit.isNotBlank()) " (${habit.unit})" else ""}"
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { commit() }),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().focusRequester(valueFocusRequester)
                        )
                    }
                    HabitType.SCALE_1_5 -> {
                        Text("Rate 1–5", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Text(
                            "Note / gratitude / reflection",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                if (habit.type == HabitType.NOTE || habit.type != HabitType.CHECKBOX) {
                    val noteLabel = if (habit.type == HabitType.NOTE) {
                        "Journal entry"
                    } else {
                        "Note (optional)"
                    }
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text(noteLabel) },
                        modifier = Modifier.fillMaxWidth()
                            .then(if (habit.type == HabitType.NOTE) Modifier.focusRequester(noteFocusRequester) else Modifier),
                        minLines = if (habit.type == HabitType.NOTE) 4 else 1,
                        keyboardOptions = KeyboardOptions(imeAction = if (habit.type == HabitType.NOTE) ImeAction.Default else ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { commit() })
                    )
                }

                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { showDate = !showDate }) {
                    Text(
                        if (showDate) "Hide date" else "Change date (backfill)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (showDate) {
                    OutlinedTextField(
                        value = dateStr,
                        onValueChange = { dateStr = it },
                        label = { Text("Date (yyyy-MM-dd)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { commit() }) {
                Text(if (habit.type == HabitType.CHECKBOX) "Complete" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
