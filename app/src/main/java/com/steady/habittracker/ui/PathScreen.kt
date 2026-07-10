package com.steady.habittracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.steady.habittracker.data.AppData
import com.steady.habittracker.data.DreamCategory
import com.steady.habittracker.data.DreamHorizon
import com.steady.habittracker.data.GoalStory
import com.steady.habittracker.data.HabitDomain
import com.steady.habittracker.data.PathAlignmentCheck
import java.util.UUID

/**
 * Path tab (#26): long-term orientation, alignment check, mindset anchors, first steps.
 * Surfaces Dreamline-tagged Goal Stories from the wizard (#25).
 */
@Composable
fun PathScreen(
    appData: AppData,
    onOpenWizard: () -> Unit,
    onUpdateGoal: (GoalStory) -> Unit,
    onSaveAlignment: (PathAlignmentCheck) -> Unit,
    onArchiveGoal: (String) -> Unit = {}
) {
    val goals = remember(appData.goals) { HabitDomain.dreamlineGoals(appData) }
    val six = remember(goals) { goals.filter { it.horizon == DreamHorizon.SIX_MONTHS } }
    val twelve = remember(goals) { goals.filter { it.horizon == DreamHorizon.TWELVE_MONTHS } }
    val being = remember(goals) { goals.filter { it.category == DreamCategory.BEING } }
    val prompts = remember(appData.goals) { HabitDomain.mindsetPrompts(appData) }
    val firstSteps = remember(appData.goals) { HabitDomain.openFirstSteps(appData) }
    val todayCheck = remember(appData.pathChecks) { HabitDomain.pathCheckToday(appData) }
    val avgProgress = remember(goals) { HabitDomain.averageProgress(goals) }

    var showAlignment by remember { mutableStateOf(false) }
    var expandedGoalId by remember { mutableStateOf<String?>(null) }
    var mindsetNote by remember { mutableStateOf("") }
    var noteGoalId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Path",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Stay oriented toward your long-term vision",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                Button(
                    onClick = onOpenWizard,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(if (goals.isEmpty()) "Start Dreamline" else "Re-run wizard", fontSize = 12.sp)
                }
            }
        }

        // Snapshot
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "Current path snapshot",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                    if (goals.isEmpty()) {
                        Text(
                            "No Dreamline goals yet. Run the wizard to define Having / Being / Doing dreams for 6 and 12 months.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    } else {
                        Text(
                            "${goals.size} active · avg progress ${(avgProgress * 100).toInt()}%",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                        )
                        LinearProgressIndicator(
                            progress = { avgProgress },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("6 months · ${six.size}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        six.take(4).forEach { g ->
                            Text(
                                "  ${HabitDomain.categoryLabel(g.category)}: ${g.title}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("12 months · ${twelve.size}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        twelve.take(4).forEach { g ->
                            Text(
                                "  ${HabitDomain.categoryLabel(g.category)}: ${g.title}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Alignment check
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Am I on path?",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        )
                        TextButton(onClick = { showAlignment = !showAlignment }) {
                            Text(
                                if (showAlignment) "Hide" else if (todayCheck != null) "Update" else "Check in",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp
                            )
                        }
                    }
                    if (todayCheck != null && !showAlignment) {
                        val score = (HabitDomain.pathCheckScore(todayCheck) * 100).toInt()
                        Text(
                            "Today: vision ${todayCheck.visionAlignment}/5 · energy ${todayCheck.energyTowardDreams}/5 · identity ${todayCheck.identityCongruence}/5 ($score%)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (todayCheck.note.isNotBlank()) {
                            Text("“${todayCheck.note}”", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (showAlignment) {
                        AlignmentCheckForm(
                            existing = todayCheck,
                            onSave = {
                                onSaveAlignment(it)
                                showAlignment = false
                            }
                        )
                    }
                }
            }
        }

        // Mindset anchors
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "Mindset anchors",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                    Text(
                        if (being.isNotEmpty()) "Tied to your Being goals" else "General identity prompts",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    prompts.forEach { p ->
                        Text(
                            "• $p",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (goals.isNotEmpty()) {
                        Text("Attach a short note to a goal", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            goals.take(4).forEach { g ->
                                ThemedFilterChip(
                                    selected = noteGoalId == g.id,
                                    onClick = { noteGoalId = if (noteGoalId == g.id) null else g.id },
                                    label = { Text(g.title.take(14), fontSize = 10.sp) }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = mindsetNote,
                            onValueChange = { mindsetNote = it },
                            label = { Text("Mindset note") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = false,
                            maxLines = 2
                        )
                        TextButton(
                            onClick = {
                                val gid = noteGoalId ?: goals.firstOrNull()?.id ?: return@TextButton
                                val g = goals.find { it.id == gid } ?: return@TextButton
                                if (mindsetNote.isBlank()) return@TextButton
                                val stamp = java.time.LocalDate.now().toString()
                                onUpdateGoal(
                                    g.copy(
                                        notes = g.notes + "$stamp: ${mindsetNote.trim()}",
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                                mindsetNote = ""
                            },
                            enabled = mindsetNote.isNotBlank() && goals.isNotEmpty()
                        ) {
                            Text("Save note", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Quick first steps
        if (firstSteps.isNotEmpty()) {
            item {
                Text(
                    "Take the first step now",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp
                )
            }
            items(firstSteps, key = { it.first.id }) { (goal, step) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(goal.title, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text(step, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "${HabitDomain.horizonLabel(goal.horizon)} · ${HabitDomain.categoryLabel(goal.category)}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Goal cards with progress / confidence
        if (goals.isNotEmpty()) {
            item {
                Text(
                    "All Dreamline goals",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(goals, key = { it.id }) { goal ->
                GoalStoryCard(
                    goal = goal,
                    expanded = expandedGoalId == goal.id,
                    onToggle = {
                        expandedGoalId = if (expandedGoalId == goal.id) null else goal.id
                    },
                    onUpdate = onUpdateGoal,
                    onArchive = { onArchiveGoal(goal.id) }
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun AlignmentCheckForm(
    existing: PathAlignmentCheck?,
    onSave: (PathAlignmentCheck) -> Unit
) {
    var vision by remember { mutableIntStateOf(existing?.visionAlignment ?: 3) }
    var energy by remember { mutableIntStateOf(existing?.energyTowardDreams ?: 3) }
    var identity by remember { mutableIntStateOf(existing?.identityCongruence ?: 3) }
    var note by remember { mutableStateOf(existing?.note ?: "") }

    @Composable
    fun scale(label: String, value: Int, onValue: (Int) -> Unit) {
        Text("$label: $value/5", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
        Slider(
            value = value.toFloat(),
            onValueChange = { onValue(it.toInt().coerceIn(1, 5)) },
            valueRange = 1f..5f,
            steps = 3,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }

    scale("Alignment with vision", vision) { vision = it }
    scale("Energy toward dreams", energy) { energy = it }
    scale("Identity congruence", identity) { identity = it }
    OutlinedTextField(
        value = note,
        onValueChange = { note = it },
        label = { Text("Optional note") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Button(
        onClick = {
            onSave(
                PathAlignmentCheck(
                    id = existing?.id ?: "pc_${UUID.randomUUID().toString().take(8)}",
                    date = java.time.LocalDate.now().toString(),
                    visionAlignment = vision,
                    energyTowardDreams = energy,
                    identityCongruence = identity,
                    note = note.trim(),
                    loggedAt = System.currentTimeMillis()
                )
            )
        },
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Save check-in")
    }
}

@Composable
private fun GoalStoryCard(
    goal: GoalStory,
    expanded: Boolean,
    onToggle: () -> Unit,
    onUpdate: (GoalStory) -> Unit,
    onArchive: () -> Unit
) {
    var progress by remember(goal.id, goal.progress) { mutableFloatStateOf(goal.progress.coerceIn(0f, 1f)) }
    var confidence by remember(goal.id, goal.confidence) { mutableFloatStateOf(goal.confidence.coerceIn(0f, 1f)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(goal.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "${HabitDomain.horizonLabel(goal.horizon)} · ${HabitDomain.categoryLabel(goal.category)}" +
                            (if (goal.endDate.isNotBlank()) " · by ${goal.endDate}" else ""),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onToggle) {
                    Text(if (expanded) "Less" else "Edit", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp).padding(top = 6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Text(
                "Progress ${(progress * 100).toInt()}% · Confidence ${(confidence * 100).toInt()}%",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (goal.firstStepNow.isNotBlank()) {
                Text("First step: ${goal.firstStepNow}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
            if (expanded) {
                Spacer(Modifier.height(6.dp))
                Text("Progress", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                Slider(
                    value = progress,
                    onValueChange = { progress = it },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
                Text("Confidence", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                Slider(
                    value = confidence,
                    onValueChange = { confidence = it },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
                goal.steps.forEach { step ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ThemedCheckbox(
                            checked = step.done,
                            onCheckedChange = { checked ->
                                val newSteps = goal.steps.map {
                                    if (it.id == step.id) it.copy(done = checked == true) else it
                                }
                                val doneFrac = if (newSteps.isEmpty()) progress
                                else newSteps.count { it.done }.toFloat() / newSteps.size
                                onUpdate(
                                    goal.copy(
                                        steps = newSteps,
                                        progress = doneFrac,
                                        confidence = confidence,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                            }
                        )
                        Text(step.title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                if (goal.notes.isNotEmpty()) {
                    Text("Notes", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    goal.notes.takeLast(5).forEach { n ->
                        Text("• $n", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row {
                    TextButton(onClick = {
                        onUpdate(
                            goal.copy(
                                progress = progress,
                                confidence = confidence,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }) {
                        Text("Save", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    }
                    TextButton(onClick = onArchive) {
                        Text("Archive", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
