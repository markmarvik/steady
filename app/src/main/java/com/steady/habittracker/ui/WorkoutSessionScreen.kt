package com.steady.habittracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.steady.habittracker.data.ExerciseDef
import com.steady.habittracker.data.ExerciseRoutine
import com.steady.habittracker.data.SetLog
import com.steady.habittracker.data.WorkoutSession
import kotlinx.coroutines.delay
import java.util.UUID

/**
 * Interactive workout session logger (#22).
 * Logs sets per exercise, optional rest timer, finish saves session.
 */
@Composable
fun WorkoutSessionScreen(
    routine: ExerciseRoutine,
    existing: WorkoutSession? = null,
    onFinish: (WorkoutSession) -> Unit,
    onDiscard: () -> Unit
) {
    val startedAt = existing?.startedAt ?: remember { System.currentTimeMillis() }
    val sessionId = existing?.id ?: remember { "ws_${UUID.randomUUID().toString().take(8)}" }
    val date = existing?.date ?: remember {
        java.time.LocalDate.now().toString()
    }

    var exerciseIndex by remember { mutableIntStateOf(0) }
    var performed by remember {
        mutableStateOf(existing?.performedExercises ?: emptyMap())
    }
    var overallNote by remember { mutableStateOf(existing?.overallNote ?: "") }
    var restLeft by remember { mutableIntStateOf(0) }
    var repsInput by remember { mutableStateOf("") }
    var weightInput by remember { mutableStateOf("") }
    var rpeInput by remember { mutableStateOf("") }

    val exercises = routine.exercises.sortedBy { it.order }
    val current: ExerciseDef? = exercises.getOrNull(exerciseIndex)
    val doneExercises = exercises.count { (performed[it.id]?.size ?: 0) > 0 }
    val progress = if (exercises.isEmpty()) 0f else doneExercises.toFloat() / exercises.size

    LaunchedEffect(restLeft) {
        if (restLeft <= 0) return@LaunchedEffect
        while (restLeft > 0) {
            delay(1000)
            restLeft--
        }
    }

    fun addSet(ex: ExerciseDef) {
        val sets = (performed[ex.id] ?: emptyList()).toMutableList()
        val nextNum = (sets.maxOfOrNull { it.setNumber } ?: 0) + 1
        sets.add(
            SetLog(
                setNumber = nextNum,
                actualReps = repsInput.toIntOrNull(),
                weightKg = weightInput.toDoubleOrNull(),
                rpe = rpeInput.toIntOrNull()?.coerceIn(1, 10),
                note = ""
            )
        )
        performed = performed + (ex.id to sets)
        repsInput = ""
        if (ex.restSec > 0) restLeft = ex.restSec
    }

    fun buildSession(completed: Boolean): WorkoutSession {
        val now = System.currentTimeMillis()
        val mins = ((now - startedAt) / 60_000L).toInt().coerceAtLeast(0)
        return WorkoutSession(
            id = sessionId,
            routineId = routine.id,
            date = date,
            startedAt = startedAt,
            completedAt = if (completed) now else null,
            performedExercises = performed,
            totalDurationMin = mins,
            overallNote = overallNote,
            completed = completed
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(routine.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Exercise ${exerciseIndex + 1}/${exercises.size.coerceAtLeast(1)} · ${doneExercises} with sets",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onDiscard) {
                Text("Exit", color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        if (restLeft > 0) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Rest: ${restLeft}s", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Row {
                        TextButton(onClick = { restLeft += 30 }) { Text("+30s") }
                        TextButton(onClick = { restLeft = 0 }) { Text("Skip") }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            if (current == null) {
                Text("No exercises in this routine.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(current.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "Target ${current.sets}×${current.reps}" +
                                (if (current.muscleGroup.isNotBlank()) " · ${current.muscleGroup}" else "") +
                                (if (current.restSec > 0) " · rest ${current.restSec}s" else ""),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (current.notes.isNotBlank()) {
                            Text(current.notes, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        val sets = performed[current.id].orEmpty()
                        if (sets.isEmpty()) {
                            Text("No sets yet — log what you did.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            sets.forEach { s ->
                                Text(
                                    "Set ${s.setNumber}: ${s.actualReps ?: "—"} reps" +
                                        (s.weightKg?.let { " @ ${it}kg" } ?: "") +
                                        (s.rpe?.let { " RPE $it" } ?: ""),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = repsInput,
                                onValueChange = { repsInput = it.filter { c -> c.isDigit() }.take(3) },
                                label = { Text("Reps") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = weightInput,
                                onValueChange = { weightInput = it.filter { c -> c.isDigit() || c == '.' }.take(6) },
                                label = { Text("kg") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = rpeInput,
                                onValueChange = { rpeInput = it.filter { c -> c.isDigit() }.take(2) },
                                label = { Text("RPE") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { addSet(current) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Add set")
                        }
                        if (sets.isNotEmpty()) {
                            TextButton(onClick = {
                                val trimmed = sets.dropLast(1)
                                performed = if (trimmed.isEmpty()) performed - current.id
                                else performed + (current.id to trimmed)
                            }) {
                                Text("Undo last set", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = overallNote,
                onValueChange = { overallNote = it },
                label = { Text("Session note (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = { if (exerciseIndex > 0) exerciseIndex-- },
                enabled = exerciseIndex > 0,
                modifier = Modifier.weight(1f)
            ) { Text("Previous") }
            TextButton(
                onClick = { if (exerciseIndex < exercises.lastIndex) exerciseIndex++ },
                enabled = exerciseIndex < exercises.lastIndex,
                modifier = Modifier.weight(1f)
            ) { Text("Next") }
        }
        Button(
            onClick = { onFinish(buildSession(completed = true)) },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Finish workout")
        }
        Text(
            "Partial sessions are fine — log what you completed.",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp).align(Alignment.CenterHorizontally)
        )
    }
}
