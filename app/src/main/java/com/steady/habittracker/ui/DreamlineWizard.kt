package com.steady.habittracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.steady.habittracker.data.DreamCategory
import com.steady.habittracker.data.DreamHorizon
import com.steady.habittracker.data.GoalStory
import com.steady.habittracker.data.HabitDomain

/**
 * Guided Dreamline-style vision wizard (#25).
 * Having / Being / Doing across 6- and 12-month horizons → Goal Stories.
 */
@Composable
fun DreamlineWizard(
    onComplete: (List<GoalStory>) -> Unit,
    onCancel: () -> Unit
) {
    // Steps: 0 intro, 1 = 6mo, 2 = 12mo, 3 = convert steps + first actions, 4 review
    var step by remember { mutableIntStateOf(0) }
    val totalSteps = 5

    // horizon -> category -> list of dream texts (max 5 each)
    var sixMonth by remember {
        mutableStateOf(
            mapOf(
                DreamCategory.HAVING to listOf(""),
                DreamCategory.BEING to listOf(""),
                DreamCategory.DOING to listOf("")
            )
        )
    }
    var twelveMonth by remember {
        mutableStateOf(
            mapOf(
                DreamCategory.HAVING to listOf(""),
                DreamCategory.BEING to listOf(""),
                DreamCategory.DOING to listOf("")
            )
        )
    }
    // key dreamKey -> steps (up to 3) and firstStep
    var stepsMap by remember { mutableStateOf(mapOf<String, List<String>>()) }
    var firstStepMap by remember { mutableStateOf(mapOf<String, String>()) }

    fun collectDreams(map: Map<DreamCategory, List<String>>, horizon: DreamHorizon): List<Triple<DreamHorizon, DreamCategory, String>> =
        DreamCategory.entries.flatMap { cat ->
            map[cat].orEmpty().mapNotNull { t ->
                val s = t.trim()
                if (s.isEmpty()) null else Triple(horizon, cat, s)
            }
        }

    fun allDreams(): List<Triple<DreamHorizon, DreamCategory, String>> =
        collectDreams(sixMonth, DreamHorizon.SIX_MONTHS) +
            collectDreams(twelveMonth, DreamHorizon.TWELVE_MONTHS)

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "Dreamline wizard",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(onClick = onCancel) {
                Text("Cancel", color = MaterialTheme.colorScheme.error)
            }
        }
        LinearProgressIndicator(
            progress = { (step + 1).toFloat() / totalSteps },
            modifier = Modifier.fillMaxWidth().height(6.dp).padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text(
            "Step ${step + 1} of $totalSteps",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            when (step) {
                0 -> IntroStep()
                1 -> HorizonCaptureStep(
                    title = "6-month dreams",
                    subtitle = "No judgment. No “how” yet. Dream freely — Having, Being, Doing.",
                    values = sixMonth,
                    onChange = { sixMonth = it }
                )
                2 -> HorizonCaptureStep(
                    title = "12-month dreams",
                    subtitle = "Bigger horizon. Same three lenses. Be ambitious.",
                    values = twelveMonth,
                    onChange = { twelveMonth = it }
                )
                3 -> ConvertStep(
                    dreams = allDreams().filter { it.first == DreamHorizon.SIX_MONTHS },
                    stepsMap = stepsMap,
                    firstStepMap = firstStepMap,
                    onSteps = { k, v -> stepsMap = stepsMap + (k to v) },
                    onFirst = { k, v -> firstStepMap = firstStepMap + (k to v) }
                )
                4 -> ReviewStep(dreams = allDreams(), stepsMap = stepsMap, firstStepMap = firstStepMap)
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (step > 0) {
                TextButton(onClick = { step-- }, modifier = Modifier.weight(1f)) {
                    Text("Back")
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            Button(
                onClick = {
                    if (step < totalSteps - 1) {
                        step++
                    } else {
                        val goals = HabitDomain.buildGoalsFromDreamline(
                            dreams = allDreams(),
                            stepsByKey = stepsMap,
                            firstStepsByKey = firstStepMap
                        )
                        onComplete(goals)
                    }
                },
                enabled = when (step) {
                    1, 2 -> true // allow sparse dreams
                    4 -> allDreams().isNotEmpty()
                    else -> true
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f)
            ) {
                Text(if (step == totalSteps - 1) "Create goals" else "Next")
            }
        }
    }
}

@Composable
private fun IntroStep() {
    Text(
        "Reignite your vision",
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Inspired by Tim Ferriss’s Dreamlining. You’ll list unconstrained dreams across two timelines and three categories, then turn a few into continuous goals Steady can track.",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(Modifier.height(12.dp))
    Text("Categories", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    Text("• Having — possessions, experiences, resources", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("• Being — identity, character, who you become", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("• Doing — skills, adventures, concrete actions", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    Text("If you’re stuck", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    HabitDomain.dreamlineStuckPrompts().forEach { p ->
        Text("• $p", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
    }
    Spacer(Modifier.height(12.dp))
    Text(
        "Later you’ll pick first steps (≤5 minutes) and optional milestones for 6-month dreams. Path tab keeps you oriented.",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun HorizonCaptureStep(
    title: String,
    subtitle: String,
    values: Map<DreamCategory, List<String>>,
    onChange: (Map<DreamCategory, List<String>>) -> Unit
) {
    Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    DreamCategory.entries.forEach { cat ->
        val list = values[cat].orEmpty().ifEmpty { listOf("") }.toMutableList()
        Text(
            HabitDomain.categoryLabel(cat),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
        list.forEachIndexed { i, text ->
            OutlinedTextField(
                value = text,
                onValueChange = { v ->
                    val next = list.toMutableList()
                    next[i] = v
                    onChange(values + (cat to next))
                },
                label = { Text("Dream ${i + 1}") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                singleLine = false,
                maxLines = 2
            )
        }
        if (list.size < 5) {
            TextButton(onClick = {
                onChange(values + (cat to (list + "")))
            }) {
                Text("+ Add ${HabitDomain.categoryLabel(cat).lowercase()} dream", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ConvertStep(
    dreams: List<Triple<DreamHorizon, DreamCategory, String>>,
    stepsMap: Map<String, List<String>>,
    firstStepMap: Map<String, String>,
    onSteps: (String, List<String>) -> Unit,
    onFirst: (String, String) -> Unit
) {
    Text(
        "Make it actionable",
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Text(
        "For each 6-month dream: up to 3 concrete steps + one first step you can take in ≤5 minutes. Being goals work best as concrete Doing actions.",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))
    if (dreams.isEmpty()) {
        Text(
            "No 6-month dreams yet — go back and add a few, or skip ahead. 12-month dreams still become goals without steps.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    dreams.forEach { (horizon, cat, text) ->
        val key = HabitDomain.dreamKey(horizon, cat, text)
        val steps = stepsMap[key].orEmpty().let { if (it.isEmpty()) listOf("") else it }.toMutableList()
        Text(
            "${HabitDomain.categoryLabel(cat)} · $text",
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 10.dp)
        )
        if (cat == DreamCategory.BEING) {
            Text(
                "Tip: turn identity into action — e.g. “great cook” → “make dinner without help”.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        steps.take(3).forEachIndexed { i, s ->
            OutlinedTextField(
                value = s,
                onValueChange = { v ->
                    val next = steps.toMutableList()
                    while (next.size <= i) next.add("")
                    next[i] = v
                    onSteps(key, next)
                },
                label = { Text("Step ${i + 1}") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            )
        }
        if (steps.size < 3) {
            TextButton(onClick = { onSteps(key, steps + "") }) {
                Text("+ Step", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
        OutlinedTextField(
            value = firstStepMap[key].orEmpty(),
            onValueChange = { onFirst(key, it) },
            label = { Text("First step now (≤5 min)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ReviewStep(
    dreams: List<Triple<DreamHorizon, DreamCategory, String>>,
    stepsMap: Map<String, List<String>>,
    firstStepMap: Map<String, String>
) {
    Text(
        "Review & create",
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Text(
        "${dreams.size} dream(s) will become continuous Goal Stories (tagged dreamline). They appear on the Path tab with progress and mindset prompts.",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))
    if (dreams.isEmpty()) {
        Text("Nothing to create yet — add at least one dream.", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        return
    }
    dreams.forEach { (h, c, t) ->
        val key = HabitDomain.dreamKey(h, c, t)
        val steps = stepsMap[key].orEmpty().filter { it.isNotBlank() }
        val first = firstStepMap[key].orEmpty()
        Text(
            "• [${HabitDomain.horizonLabel(h)} · ${HabitDomain.categoryLabel(c)}] $t",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 6.dp)
        )
        if (steps.isNotEmpty()) {
            Text("  Steps: ${steps.joinToString(" · ")}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (first.isNotBlank()) {
            Text("  First now: $first", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}
