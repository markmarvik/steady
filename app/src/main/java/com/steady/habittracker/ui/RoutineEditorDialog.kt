package com.steady.habittracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.FilterChip
import com.steady.habittracker.data.ExerciseDef
import com.steady.habittracker.data.ExerciseLibrary
import com.steady.habittracker.data.ExerciseRoutine
import com.steady.habittracker.data.ShowPreset
import java.util.UUID

@Composable
fun RoutineEditorDialog(
    existing: ExerciseRoutine?,
    onDismiss: () -> Unit,
    onSave: (ExerciseRoutine) -> Unit,
    onArchive: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var duration by remember { mutableStateOf((existing?.estimatedDurationMin ?: 45).toString()) }
    var tagsText by remember { mutableStateOf(existing?.tags?.joinToString(", ") ?: "Strength") }
    var showPreset by remember { mutableStateOf(existing?.showPreset ?: ShowPreset.DAILY) }
    var weekdays by remember { mutableStateOf(existing?.weekdays ?: setOf(1, 2, 3, 4, 5, 6, 7)) }
    var catalogCategory by remember { mutableStateOf("Calisthenics") }
    var showCatalog by remember { mutableStateOf(false) }
    var exercises by remember {
        mutableStateOf(
            existing?.exercises?.sortedBy { it.order }?.toList()
                ?: listOf(
                    ExerciseDef(
                        id = "ex_${UUID.randomUUID().toString().take(6)}",
                        name = "Exercise 1",
                        sets = 3,
                        reps = "8-12",
                        order = 0
                    )
                )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text(if (existing == null) "New routine" else "Edit ${existing.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("Est. minutes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text("Tags (comma-separated)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                ShowWhenPicker(
                    preset = showPreset,
                    weekdays = weekdays,
                    intervalDays = 2,
                    specificDates = emptyList(),
                    onPreset = { showPreset = it },
                    onWeekdays = { weekdays = it },
                    onInterval = {},
                    onDates = {}
                )
                Spacer(Modifier.height(8.dp))
                Text("Exercises", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                exercises.forEachIndexed { idx, ex ->
                    ExerciseEditRow(
                        exercise = ex,
                        onChange = { updated ->
                            exercises = exercises.toMutableList().also { it[idx] = updated }
                        },
                        onRemove = {
                            if (exercises.size > 1) {
                                exercises = exercises.filterIndexed { i, _ -> i != idx }
                                    .mapIndexed { i, e -> e.copy(order = i) }
                            }
                        },
                        onMoveUp = {
                            if (idx > 0) {
                                val list = exercises.toMutableList()
                                val tmp = list[idx - 1]
                                list[idx - 1] = list[idx].copy(order = idx - 1)
                                list[idx] = tmp.copy(order = idx)
                                exercises = list
                            }
                        },
                        onMoveDown = {
                            if (idx < exercises.lastIndex) {
                                val list = exercises.toMutableList()
                                val tmp = list[idx + 1]
                                list[idx + 1] = list[idx].copy(order = idx + 1)
                                list[idx] = tmp.copy(order = idx)
                                exercises = list
                            }
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                }
                TextButton(onClick = {
                    exercises = exercises + ExerciseDef(
                        id = "ex_${UUID.randomUUID().toString().take(6)}",
                        name = "Exercise ${exercises.size + 1}",
                        sets = 3,
                        reps = "8-12",
                        order = exercises.size
                    )
                }) {
                    Text("+ Add blank exercise", color = MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick = { showCatalog = !showCatalog }) {
                    Text(
                        if (showCatalog) "Hide library" else "+ From library (calisthenics / gym / stretch)",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (showCatalog) {
                    Spacer(Modifier.height(4.dp))
                    Text("Library category", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("Calisthenics", "Gym", "Longevity", "Stretching", "Cardio").forEach { cat ->
                            FilterChip(
                                selected = catalogCategory == cat,
                                onClick = { catalogCategory = cat },
                                label = { Text(cat, fontSize = 10.sp) }
                            )
                        }
                    }
                    val items = ExerciseLibrary.byCategory()[catalogCategory].orEmpty()
                    items.forEach { item ->
                        TextButton(
                            onClick = {
                                exercises = exercises + ExerciseLibrary.toDef(item, exercises.size).copy(
                                    id = "ex_${UUID.randomUUID().toString().take(6)}"
                                )
                            }
                        ) {
                            Text(
                                "+ ${item.name} · ${item.reps} · track ${item.metric}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) return@TextButton
                val tagList = tagsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                onSave(
                    ExerciseRoutine(
                        id = existing?.id ?: "rt_${UUID.randomUUID().toString().take(8)}",
                        name = name.trim(),
                        description = description.trim(),
                        exercises = exercises.mapIndexed { i, e -> e.copy(order = i) },
                        estimatedDurationMin = duration.toIntOrNull() ?: 45,
                        tags = tagList,
                        showPreset = showPreset,
                        weekdays = if (weekdays.isEmpty()) setOf(1, 2, 3, 4, 5, 6, 7) else weekdays,
                        groupId = existing?.groupId,
                        archived = existing?.archived ?: false,
                        order = existing?.order ?: 0,
                        linkedHabitId = existing?.linkedHabitId
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onArchive != null && existing != null) {
                    TextButton(onClick = onArchive) {
                        Text("Archive", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun ExerciseEditRow(
    exercise: ExerciseDef,
    onChange: (ExerciseDef) -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    var name by remember(exercise.id) { mutableStateOf(exercise.name) }
    var sets by remember(exercise.id) { mutableStateOf(exercise.sets.toString()) }
    var reps by remember(exercise.id) { mutableStateOf(exercise.reps) }
    var rest by remember(exercise.id) { mutableStateOf(exercise.restSec.toString()) }
    var notes by remember(exercise.id) { mutableStateOf(exercise.notes) }
    var muscle by remember(exercise.id) { mutableStateOf(exercise.muscleGroup) }

    fun push() {
        onChange(
            exercise.copy(
                name = name.trim().ifBlank { exercise.name },
                sets = sets.toIntOrNull()?.coerceIn(1, 20) ?: exercise.sets,
                reps = reps.ifBlank { "8-12" },
                restSec = rest.toIntOrNull()?.coerceIn(0, 600) ?: exercise.restSec,
                notes = notes,
                muscleGroup = muscle
            )
        )
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; push() },
            label = { Text("Exercise name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(
                value = sets,
                onValueChange = { sets = it.filter { c -> c.isDigit() }.take(2); push() },
                label = { Text("Sets") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = reps,
                onValueChange = { reps = it; push() },
                label = { Text("Reps") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = rest,
                onValueChange = { rest = it.filter { c -> c.isDigit() }.take(3); push() },
                label = { Text("Rest s") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        OutlinedTextField(
            value = muscle,
            onValueChange = { muscle = it; push() },
            label = { Text("Muscle / focus") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it; push() },
            label = { Text("Cues / notes") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onMoveUp) { Text("↑", fontSize = 12.sp) }
            TextButton(onClick = onMoveDown) { Text("↓", fontSize = 12.sp) }
            TextButton(onClick = onRemove) {
                Text("Remove", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
            }
        }
    }
}
