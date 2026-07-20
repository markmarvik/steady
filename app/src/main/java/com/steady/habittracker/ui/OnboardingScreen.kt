package com.steady.habittracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.steady.habittracker.data.Group
import com.steady.habittracker.data.HabitType
import com.steady.habittracker.data.SleepSettings
import com.steady.habittracker.data.Tag
import com.steady.habittracker.data.TagIds
import com.steady.habittracker.data.accentColorArgb
import com.steady.habittracker.data.accentSchemes
import com.steady.habittracker.data.backgroundModeOption
import com.steady.habittracker.data.backgroundModes

/** Draft habit built during first-run onboarding (committed on finish). */
data class HabitDraft(
    val name: String,
    val why: String = "",
    val groupId: String,
    val type: HabitType = HabitType.CHECKBOX,
    val tags: List<String> = emptyList(),
    val icon: String = "",
    val target: Double? = null,
    val unit: String = "",
    val canSkip: Boolean = true,
    val isSupplement: Boolean = false
)

/**
 * First daily schedule draft: sleep spine + work + optional exercise block.
 * Exercise uses start time + duration (end is derived on save).
 * Applied when onboarding completes.
 */
data class ScheduleDraft(
    val wakeTime: String = "07:00",
    val bedTime: String = "23:00",
    val morningMinutes: Int = 90,
    val windDownMinutes: Int = 60,
    val workStart: String = "09:00",
    val workEnd: String = "17:00",
    val includeExercise: Boolean = true,
    val exerciseStart: String = "17:30",
    val exerciseDurationMinutes: Int = 60
) {
    /** End of exercise block from start + duration (wraps at midnight). */
    fun exerciseEndTime(): String =
        addMinutesToOnboardingTime(exerciseStart, exerciseDurationMinutes.coerceIn(15, 180))

    companion object {
        fun fromSleep(sleep: SleepSettings): ScheduleDraft = ScheduleDraft(
            wakeTime = sleep.wakeTime.ifBlank { "07:00" },
            bedTime = sleep.bedTime.ifBlank { "23:00" },
            morningMinutes = sleep.morningMinutes.coerceIn(30, 180),
            windDownMinutes = sleep.windDownMinutes.coerceIn(15, 180)
        )
    }
}

/** Optional suggestions — never auto-seeded; user must tap to add. */
private data class HabitSuggestion(
    val name: String,
    val why: String,
    /** MORNING | WORK | BEDTIME | EXERCISE */
    val groupHint: String,
    val type: HabitType = HabitType.CHECKBOX,
    val tags: List<String> = emptyList(),
    val icon: String = "",
    val target: Double? = null,
    val unit: String = "",
    val canSkip: Boolean = true,
    val isSupplement: Boolean = false,
    /** UI section label */
    val section: String = "Morning"
)

private val SUGGESTIONS = listOf(
    // —— Morning routine ——
    HabitSuggestion("Morning sunlight", "Aligns circadian rhythm.", "MORNING", tags = listOf(TagIds.SLEEP), icon = "☀️", section = "Morning"),
    HabitSuggestion("Box breathing", "Calms nervous system.", "MORNING", HabitType.DURATION_MIN, listOf(TagIds.MINDSET), "🧘", 6.0, "min", section = "Morning"),
    HabitSuggestion("Meditate", "Start calm and present.", "MORNING", HabitType.DURATION_MIN, listOf(TagIds.MINDSET), "🪷", 10.0, "min", section = "Morning"),
    HabitSuggestion("Brush + floss", "Dental foundation.", "MORNING", tags = listOf(TagIds.HYGIENE), icon = "🪥", canSkip = false, section = "Morning"),
    HabitSuggestion("Morning supplements", "Foundational nutrition.", "MORNING", HabitType.COUNTER, listOf(TagIds.SUPPLEMENTS), "💊", 1.0, isSupplement = true, section = "Morning"),
    HabitSuggestion("Make the bed", "Small win, tidy space.", "MORNING", tags = listOf(TagIds.HYGIENE), icon = "🛏️", section = "Morning"),
    HabitSuggestion("Morning hydrate", "Glass of water on wake.", "MORNING", HabitType.COUNTER, listOf(TagIds.NUTRITION), "💧", 1.0, unit = "glass", section = "Morning"),
    HabitSuggestion("Journal / morning pages", "Clear the mind.", "MORNING", HabitType.NOTE, listOf(TagIds.MINDSET), "📓", section = "Morning"),
    HabitSuggestion("Cold shower / shower", "Wake-up and hygiene.", "MORNING", tags = listOf(TagIds.HYGIENE), icon = "🚿", section = "Morning"),
    HabitSuggestion("Plan the day", "3 priorities written down.", "MORNING", HabitType.NOTE, listOf(TagIds.MINDSET), "📋", section = "Morning"),
    HabitSuggestion("Energy check-in", "How do you feel today?", "MORNING", HabitType.SCALE_1_5, listOf(TagIds.MINDSET), "⚡", section = "Morning"),
    HabitSuggestion("Sleep quality", "How was last night?", "MORNING", HabitType.SCALE_1_5, listOf(TagIds.SLEEP), "😴", section = "Morning"),

    // —— Work / focus ——
    HabitSuggestion("Deep focus block", "Undistracted work.", "WORK", HabitType.DURATION_MIN, listOf(TagIds.MINDSET), "🧠", 60.0, "min", section = "Work & focus"),
    HabitSuggestion("Protein first", "Muscle, satiety, metabolism.", "WORK", tags = listOf(TagIds.NUTRITION), icon = "🥩", section = "Work & focus"),
    HabitSuggestion("Lunch walk", "Midday movement reset.", "WORK", tags = listOf(TagIds.MOVEMENT), icon = "🚶", section = "Work & focus"),
    HabitSuggestion("Stand / stretch break", "Undo sitting.", "WORK", tags = listOf(TagIds.MOVEMENT), icon = "🧍", section = "Work & focus"),
    HabitSuggestion("Inbox zero / email batch", "Protect deep work.", "WORK", tags = listOf(TagIds.MINDSET), icon = "📬", section = "Work & focus"),
    HabitSuggestion("No social media block", "Protect attention.", "WORK", tags = listOf(TagIds.MINDSET), icon = "📵", section = "Work & focus"),
    HabitSuggestion("Healthy lunch", "Real food, not skip.", "WORK", tags = listOf(TagIds.NUTRITION), icon = "🥗", section = "Work & focus"),

    // —— Exercise / movement ——
    HabitSuggestion("Move your body", "20+ min walk or exercise.", "EXERCISE", tags = listOf(TagIds.MOVEMENT), icon = "🚶", section = "Exercise"),
    HabitSuggestion("Strength training", "Weights, bodyweight, or gym.", "EXERCISE", tags = listOf(TagIds.MOVEMENT), icon = "🏋️", section = "Exercise"),
    HabitSuggestion("Pull-ups / dips / squats", "Strength (reps).", "EXERCISE", HabitType.COUNTER, listOf(TagIds.MOVEMENT), "💪", 5.0, "reps", section = "Exercise"),
    HabitSuggestion("Cardio / run", "Heart health.", "EXERCISE", HabitType.DURATION_MIN, listOf(TagIds.MOVEMENT), "🏃", 30.0, "min", section = "Exercise"),
    HabitSuggestion("Mobility / stretch", "Joints and recovery.", "EXERCISE", HabitType.DURATION_MIN, listOf(TagIds.MOVEMENT), "🤸", 10.0, "min", section = "Exercise"),
    HabitSuggestion("Steps goal", "Daily walking volume.", "EXERCISE", HabitType.COUNTER, listOf(TagIds.MOVEMENT), "👟", 8000.0, "steps", section = "Exercise"),
    HabitSuggestion("Yoga / pilates", "Strength + flexibility.", "EXERCISE", HabitType.DURATION_MIN, listOf(TagIds.MOVEMENT), "🧘", 20.0, "min", section = "Exercise"),

    // —— Evening / wind-down (schedule already has a wind-down block — no generic "Wind down" habit) ——
    HabitSuggestion("Stay hydrated", "Energy and recovery.", "BEDTIME", HabitType.COUNTER, listOf(TagIds.NUTRITION), "💧", 2.5, "L", section = "Evening"),
    HabitSuggestion("Magnesium", "Evening mineral for calm sleep.", "BEDTIME", tags = listOf(TagIds.SUPPLEMENTS, TagIds.SLEEP), icon = "💊", isSupplement = true, section = "Evening"),
    HabitSuggestion("Chamomile tea", "Warm herbal wind-down.", "BEDTIME", tags = listOf(TagIds.SLEEP, TagIds.NUTRITION), icon = "🍵", section = "Evening"),
    HabitSuggestion("NSDR / 4-7-8 breathing", "Non-sleep deep rest.", "BEDTIME", HabitType.DURATION_MIN, listOf(TagIds.SLEEP, TagIds.MINDSET), "🧘", 10.0, "min", section = "Evening"),
    HabitSuggestion("Gratitude / 3 things", "Evening review.", "BEDTIME", HabitType.NOTE, listOf(TagIds.MINDSET), "🙏", section = "Evening"),
    HabitSuggestion("No screens wind-down", "Protect melatonin.", "BEDTIME", tags = listOf(TagIds.SLEEP), icon = "📵", section = "Evening"),
    HabitSuggestion("Read before bed", "Calm input, not scroll.", "BEDTIME", HabitType.DURATION_MIN, listOf(TagIds.MINDSET), "📖", 20.0, "min", section = "Evening"),
    HabitSuggestion("Plan tomorrow", "Unload open loops.", "BEDTIME", HabitType.NOTE, listOf(TagIds.MINDSET), "📝", section = "Evening"),
    HabitSuggestion("Evening hygiene", "Brush + floss at night.", "BEDTIME", tags = listOf(TagIds.HYGIENE), icon = "🪥", canSkip = false, section = "Evening"),
    HabitSuggestion("Evening supplements", "Night stack.", "BEDTIME", HabitType.COUNTER, listOf(TagIds.SUPPLEMENTS), "💊", 1.0, isSupplement = true, section = "Evening")
)

private val SECTION_ORDER = listOf("Morning", "Work & focus", "Exercise", "Evening")

/**
 * First-run onboarding:
 * welcome → daily schedule → habit builder → review → theme (bg + accent) → done.
 */
@Composable
fun OnboardingScreen(
    groups: List<Group>,
    tags: List<Tag>,
    initialSleep: SleepSettings = SleepSettings(),
    onComplete: (
        drafts: List<HabitDraft>,
        colorScheme: String,
        schedule: ScheduleDraft,
        backgroundMode: String
    ) -> Unit
) {
    val title = Color(0xFFF1F5F9)
    val body = Color(0xFFE2E8F0)
    val muted = Color(0xFF94A3B8)
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface

    // 0 welcome, 1 schedule, 2 builder, 3 review, 4 theme
    var step by remember { mutableIntStateOf(0) }
    var drafts by remember { mutableStateOf(listOf<HabitDraft>()) }
    var colorScheme by remember { mutableStateOf("default") }
    var backgroundMode by remember { mutableStateOf("dark") }
    var schedule by remember { mutableStateOf(ScheduleDraft.fromSleep(initialSleep)) }
    var showCustomForm by remember { mutableStateOf(false) }

    val accent = Color(accentColorArgb(colorScheme))
    val totalSteps = 5

    fun groupForHint(hint: String): Group? {
        val active = groups.filter { !it.archived }
        return when (hint) {
            "MORNING" -> active.firstOrNull { it.timeHint == "MORNING" } ?: active.firstOrNull()
            "WORK" -> active.firstOrNull { it.timeHint == "WORK" && !it.name.contains("exercise", true) }
                ?: active.firstOrNull { it.name.contains("focus", true) || it.name.contains("work", true) }
                ?: active.getOrNull(1)
            "EXERCISE" -> active.firstOrNull {
                it.name.contains("exercise", true) || it.name.contains("movement", true) ||
                    it.name.contains("workout", true)
            } ?: active.firstOrNull { it.timeHint == "WORK" } ?: active.getOrNull(1)
            "BEDTIME" -> active.firstOrNull { it.timeHint == "BEDTIME" || it.timeHint == "EVENING" }
                ?: active.firstOrNull { it.name.contains("bed", true) || it.name.contains("wind", true) }
                ?: active.getOrNull(2)
            else -> active.firstOrNull()
        }
    }

    /** Effective groups for mapping: includes a virtual exercise group id when enabled. */
    fun resolvedGroupId(hint: String): String? {
        if (hint == "EXERCISE" && schedule.includeExercise) {
            // Prefer existing exercise group; otherwise use placeholder id applied on save
            return groupForHint("EXERCISE")?.id ?: "g_exercise"
        }
        return groupForHint(hint)?.id
    }

    fun groupName(id: String): String {
        if (id == "g_exercise") return "Exercise"
        return groups.find { it.id == id }?.name ?: id
    }

    fun addSuggestion(s: HabitSuggestion) {
        val gid = resolvedGroupId(s.groupHint) ?: return
        if (drafts.any { it.name.equals(s.name, ignoreCase = true) }) return
        drafts = drafts + HabitDraft(
            name = s.name,
            why = s.why,
            groupId = gid,
            type = s.type,
            tags = s.tags,
            icon = s.icon,
            target = s.target,
            unit = s.unit,
            canSkip = s.canSkip,
            isSupplement = s.isSupplement
        )
    }

    fun addCustom(draft: HabitDraft) {
        if (draft.name.isBlank()) return
        drafts = drafts + draft
        showCustomForm = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        LinearProgressIndicator(
            progress = { (step + 1) / totalSteps.toFloat() },
            modifier = Modifier.fillMaxWidth(),
            color = accent,
            trackColor = muted.copy(alpha = 0.3f)
        )
        Spacer(Modifier.height(16.dp))

        when (step) {
            0 -> WelcomeStep(
                title = title,
                body = body,
                muted = muted,
                accent = accent,
                onNext = { step = 1 }
            )
            1 -> ScheduleStep(
                title = title,
                body = body,
                muted = muted,
                accent = accent,
                surface = surface,
                schedule = schedule,
                onScheduleChange = { schedule = it },
                onBack = { step = 0 },
                onNext = { step = 2 }
            )
            2 -> BuilderStep(
                title = title,
                body = body,
                muted = muted,
                accent = accent,
                surface = surface,
                onSurface = onSurface,
                drafts = drafts,
                groups = groups,
                tags = tags,
                includeExercise = schedule.includeExercise,
                showCustomForm = showCustomForm,
                onShowCustom = { showCustomForm = true },
                onDismissCustom = { showCustomForm = false },
                onAddCustom = ::addCustom,
                onAddSuggestion = ::addSuggestion,
                onRemoveDraft = { i -> drafts = drafts.toMutableList().also { it.removeAt(i) } },
                groupName = ::groupName,
                onBack = { step = 1 },
                onNext = { step = 3 }
            )
            3 -> ReviewStep(
                title = title,
                body = body,
                muted = muted,
                accent = accent,
                surface = surface,
                onSurface = onSurface,
                drafts = drafts,
                schedule = schedule,
                groupName = ::groupName,
                onRemoveDraft = { i -> drafts = drafts.toMutableList().also { it.removeAt(i) } },
                onBack = { step = 2 },
                onNext = { step = 4 }
            )
            else -> ThemeStep(
                title = title,
                body = body,
                muted = muted,
                accent = accent,
                surface = surface,
                selectedScheme = colorScheme,
                onSelectScheme = { colorScheme = it },
                selectedBackground = backgroundMode,
                onSelectBackground = { backgroundMode = it },
                onBack = { step = 3 },
                onFinish = { onComplete(drafts, colorScheme, schedule, backgroundMode) }
            )
        }
    }
}

@Composable
private fun WelcomeStep(
    title: Color,
    body: Color,
    muted: Color,
    accent: Color,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            "Welcome to Steady",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = title,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Evidence-based habits. Simple daily tracking.",
            fontSize = 14.sp,
            color = muted,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))

        val features = listOf(
            "😴 Sleep first — set wake & bed; Morning and Bedtime anchor to them.",
            "📅 Shape your day — work hours, morning routine, evening wind-down, exercise.",
            "🏷 Tags = what it is (Supplements, Movement…) for History.",
            "✅ Tap to log. Checkboxes, counters, minutes, scales, notes.",
            "📈 Streak + tag completion trends.",
            "🧩 Widget shows today’s list; NOW highlights the current block."
        )
        features.forEach { f ->
            Text(
                f,
                color = body,
                fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "You start with a clean slate. First, set up your day — then pick habits that fit it.",
            color = muted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onNext,
            colors = ButtonDefaults.buttonColors(containerColor = accent),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Set up my day", color = Color(0xFF0F172A), fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Tip: You can change schedule and habits anytime in Manage.",
            color = muted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ScheduleStep(
    title: Color,
    body: Color,
    muted: Color,
    accent: Color,
    surface: Color,
    schedule: ScheduleDraft,
    onScheduleChange: (ScheduleDraft) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Text(
            "Your daily schedule",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = title
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Sleep anchors the day. Work, morning, evening, and exercise fill the rest.",
            fontSize = 13.sp,
            color = muted
        )
        Spacer(Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ScheduleSectionCard(title = "Sleep", surface = surface, accent = accent) {
                Text("Wake & bed times drive Morning, Wind-down, and Sleep blocks.", fontSize = 11.sp, color = muted)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimePickerField(
                        label = "Wake",
                        time = schedule.wakeTime,
                        accent = accent,
                        muted = muted,
                        modifier = Modifier.weight(1f),
                        onTimeChange = { onScheduleChange(schedule.copy(wakeTime = it)) }
                    )
                    TimePickerField(
                        label = "Bed",
                        time = schedule.bedTime,
                        accent = accent,
                        muted = muted,
                        modifier = Modifier.weight(1f),
                        onTimeChange = { onScheduleChange(schedule.copy(bedTime = it)) }
                    )
                }
            }

            ScheduleSectionCard(title = "Morning routine", surface = surface, accent = accent) {
                Text(
                    "Block length after wake (sunlight, hygiene, supplements…).",
                    fontSize = 11.sp,
                    color = muted
                )
                Spacer(Modifier.height(6.dp))
                MinutesStepper(
                    label = "Morning",
                    minutes = schedule.morningMinutes,
                    min = 30,
                    max = 180,
                    step = 15,
                    muted = muted,
                    accent = accent,
                    onChange = { onScheduleChange(schedule.copy(morningMinutes = it)) }
                )
            }

            ScheduleSectionCard(title = "Work / focus", surface = surface, accent = accent) {
                Text("Deep work and daytime habits land here.", fontSize = 11.sp, color = muted)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimePickerField(
                        label = "Start",
                        time = schedule.workStart,
                        accent = accent,
                        muted = muted,
                        modifier = Modifier.weight(1f),
                        onTimeChange = { onScheduleChange(schedule.copy(workStart = it)) }
                    )
                    TimePickerField(
                        label = "End",
                        time = schedule.workEnd,
                        accent = accent,
                        muted = muted,
                        modifier = Modifier.weight(1f),
                        onTimeChange = { onScheduleChange(schedule.copy(workEnd = it)) }
                    )
                }
            }

            ScheduleSectionCard(title = "Exercise", surface = surface, accent = accent) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Dedicated exercise block", fontSize = 13.sp, color = body, fontWeight = FontWeight.Medium)
                        Text("Start time + how long you move.", fontSize = 11.sp, color = muted)
                    }
                    Switch(
                        checked = schedule.includeExercise,
                        onCheckedChange = { onScheduleChange(schedule.copy(includeExercise = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = accent
                        )
                    )
                }
                if (schedule.includeExercise) {
                    Spacer(Modifier.height(8.dp))
                    TimePickerField(
                        label = "Start",
                        time = schedule.exerciseStart,
                        accent = accent,
                        muted = muted,
                        modifier = Modifier.fillMaxWidth(),
                        onTimeChange = { onScheduleChange(schedule.copy(exerciseStart = it)) }
                    )
                    Spacer(Modifier.height(8.dp))
                    MinutesStepper(
                        label = "Duration",
                        minutes = schedule.exerciseDurationMinutes,
                        min = 15,
                        max = 180,
                        step = 15,
                        muted = muted,
                        accent = accent,
                        onChange = { onScheduleChange(schedule.copy(exerciseDurationMinutes = it)) }
                    )
                    Text(
                        "Ends ~${normalizeOnboardingTime(schedule.exerciseEndTime())}",
                        fontSize = 11.sp,
                        color = muted
                    )
                }
            }

            ScheduleSectionCard(title = "Evening / wind-down", surface = surface, accent = accent) {
                Text(
                    "Minutes before bed for wind-down habits (screens off, read, gratitude…).",
                    fontSize = 11.sp,
                    color = muted
                )
                Spacer(Modifier.height(6.dp))
                MinutesStepper(
                    label = "Wind-down",
                    minutes = schedule.windDownMinutes,
                    min = 15,
                    max = 180,
                    step = 15,
                    muted = muted,
                    accent = accent,
                    onChange = { onScheduleChange(schedule.copy(windDownMinutes = it)) }
                )
            }

            // Day summary
            Text("Day snapshot", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = body)
            Text(
                buildString {
                    append("🌅 Wake ${normalizeOnboardingTime(schedule.wakeTime)}")
                    append(" · morning ${schedule.morningMinutes}m\n")
                    append("🎯 Work ${normalizeOnboardingTime(schedule.workStart)}–${normalizeOnboardingTime(schedule.workEnd)}\n")
                    if (schedule.includeExercise) {
                        append(
                            "💪 Exercise ${normalizeOnboardingTime(schedule.exerciseStart)} " +
                                "for ${schedule.exerciseDurationMinutes}m " +
                                "(→ ${normalizeOnboardingTime(schedule.exerciseEndTime())})\n"
                        )
                    }
                    append("🌙 Wind-down ${schedule.windDownMinutes}m before bed ${normalizeOnboardingTime(schedule.bedTime)}")
                },
                fontSize = 12.sp,
                color = muted
            )
            Spacer(Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack) { Text("Back", color = muted) }
            Button(
                onClick = onNext,
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) {
                Text("Next: habits", color = Color(0xFF0F172A), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ScheduleSectionCard(
    title: String,
    surface: Color,
    accent: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(surface, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = accent)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun MinutesStepper(
    label: String,
    minutes: Int,
    min: Int,
    max: Int,
    step: Int,
    muted: Color,
    accent: Color,
    onChange: (Int) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("$label ${minutes}m", fontSize = 13.sp, color = muted, modifier = Modifier.weight(1f))
        OutlinedButton(
            onClick = { onChange((minutes - step).coerceAtLeast(min)) },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) { Text("−") }
        OutlinedButton(
            onClick = { onChange((minutes + step).coerceAtMost(max)) },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)
        ) { Text("+") }
    }
}

/**
 * Tappable time field that opens Material3 TimePicker (clock + optional text input).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerField(
    label: String,
    time: String,
    accent: Color,
    muted: Color,
    modifier: Modifier = Modifier,
    onTimeChange: (String) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val normalized = normalizeOnboardingTime(time)

    Column(modifier = modifier) {
        Text(label, fontSize = 11.sp, color = muted, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Surface(
            onClick = { showPicker = true },
            shape = RoundedCornerShape(10.dp),
            color = accent.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    normalized,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    letterSpacing = 0.5.sp
                )
                Text("Edit", fontSize = 12.sp, color = muted, fontWeight = FontWeight.Medium)
            }
        }
    }

    if (showPicker) {
        OnboardingTimePickerDialog(
            initialTime = normalized,
            accent = accent,
            onDismiss = { showPicker = false },
            onConfirm = { hhmm ->
                onTimeChange(hhmm)
                showPicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingTimePickerDialog(
    initialTime: String,
    accent: Color,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val parts = normalizeOnboardingTime(initialTime).split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val state = rememberTimePickerState(
        initialHour = hour,
        initialMinute = minute,
        is24Hour = true
    )
    var useInput by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (useInput) "Enter time" else "Select time",
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(
                    onClick = { useInput = !useInput },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(
                        if (useInput) "Clock" else "Keyboard",
                        color = accent,
                        fontSize = 13.sp
                    )
                }
            }
        },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (useInput) {
                    TimeInput(state = state)
                } else {
                    TimePicker(state = state)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm("%02d:%02d".format(state.hour, state.minute))
                }
            ) {
                Text("OK", color = accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun BuilderStep(
    title: Color,
    body: Color,
    muted: Color,
    accent: Color,
    surface: Color,
    onSurface: Color,
    drafts: List<HabitDraft>,
    groups: List<Group>,
    tags: List<Tag>,
    includeExercise: Boolean,
    showCustomForm: Boolean,
    onShowCustom: () -> Unit,
    onDismissCustom: () -> Unit,
    onAddCustom: (HabitDraft) -> Unit,
    onAddSuggestion: (HabitSuggestion) -> Unit,
    onRemoveDraft: (Int) -> Unit,
    groupName: (String) -> String,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val visibleSuggestions = remember(includeExercise) {
        if (includeExercise) SUGGESTIONS
        else SUGGESTIONS.map { s ->
            if (s.groupHint == "EXERCISE") s.copy(groupHint = "WORK") else s
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Build your habits",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = title
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Pick for morning, work, exercise, and evening — or create your own.",
            fontSize = 13.sp,
            color = muted
        )
        Spacer(Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            if (drafts.isNotEmpty()) {
                Text(
                    "Your list (${drafts.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accent
                )
                Spacer(Modifier.height(6.dp))
                drafts.forEachIndexed { i, d ->
                    DraftChipRow(
                        draft = d,
                        groupLabel = groupName(d.groupId),
                        surface = surface,
                        onSurface = onSurface,
                        muted = muted,
                        onRemove = { onRemoveDraft(i) }
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(12.dp))
            }

            SECTION_ORDER.forEach { section ->
                val items = visibleSuggestions.filter { it.section == section }
                if (items.isEmpty()) return@forEach
                Text(
                    section,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = body
                )
                Spacer(Modifier.height(6.dp))
                items.forEach { s ->
                    val already = drafts.any { it.name.equals(s.name, ignoreCase = true) }
                    SuggestionCard(
                        suggestion = s,
                        alreadyAdded = already,
                        accent = accent,
                        surface = surface,
                        onSurface = onSurface,
                        muted = muted,
                        onClick = { if (!already) onAddSuggestion(s) }
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(10.dp))
            }

            OutlinedButton(
                onClick = onShowCustom,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Create custom habit")
            }
            Spacer(Modifier.height(16.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack) { Text("Back", color = muted) }
            Button(
                onClick = onNext,
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) {
                Text(
                    if (drafts.isEmpty()) "Continue" else "Review (${drafts.size})",
                    color = Color(0xFF0F172A),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    if (showCustomForm) {
        val formGroups = groups.filter { !it.archived }.toMutableList()
        if (includeExercise && formGroups.none {
                it.id == "g_exercise" || it.name.contains("exercise", true)
            }
        ) {
            formGroups.add(Group("g_exercise", "Exercise", "WORK", order = 2, icon = "💪"))
        }
        CustomHabitSheet(
            groups = formGroups,
            tags = tags.filter { !it.archived },
            accent = accent,
            onDismiss = onDismissCustom,
            onAdd = onAddCustom
        )
    }
}

@Composable
private fun ReviewStep(
    title: Color,
    body: Color,
    muted: Color,
    accent: Color,
    surface: Color,
    onSurface: Color,
    drafts: List<HabitDraft>,
    schedule: ScheduleDraft,
    groupName: (String) -> String,
    onRemoveDraft: (Int) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Text(
            if (drafts.isEmpty()) "Ready when you are" else "Looking good",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = title
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Confirm your day and habits. Next: pick an accent color.",
            fontSize = 13.sp,
            color = muted
        )
        Spacer(Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(surface, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text("Schedule", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = accent)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Wake ${normalizeOnboardingTime(schedule.wakeTime)} · Bed ${normalizeOnboardingTime(schedule.bedTime)}\n" +
                        "Morning ${schedule.morningMinutes}m · Work ${normalizeOnboardingTime(schedule.workStart)}–${normalizeOnboardingTime(schedule.workEnd)}" +
                        if (schedule.includeExercise) {
                            "\nExercise ${normalizeOnboardingTime(schedule.exerciseStart)} " +
                                "for ${schedule.exerciseDurationMinutes}m"
                        } else {
                            ""
                        } +
                        "\nWind-down ${schedule.windDownMinutes}m",
                    fontSize = 12.sp,
                    color = muted
                )
            }
            Spacer(Modifier.height(12.dp))

            if (drafts.isEmpty()) {
                Text(
                    "No habits yet — you can add them later in Manage.",
                    color = body,
                    fontSize = 14.sp
                )
            } else {
                Text(
                    "Habits (${drafts.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = body
                )
                Spacer(Modifier.height(6.dp))
                drafts.forEachIndexed { i, d ->
                    DraftChipRow(
                        draft = d,
                        groupLabel = groupName(d.groupId),
                        surface = surface,
                        onSurface = onSurface,
                        muted = muted,
                        onRemove = { onRemoveDraft(i) }
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack) { Text("Back", color = muted) }
            Button(
                onClick = onNext,
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) {
                Text("Choose colors", color = Color(0xFF0F172A), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ThemeStep(
    title: Color,
    body: Color,
    muted: Color,
    accent: Color,
    surface: Color,
    selectedScheme: String,
    onSelectScheme: (String) -> Unit,
    selectedBackground: String,
    onSelectBackground: (String) -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit
) {
    val schemes = remember { accentSchemes() }
    val backgrounds = remember { backgroundModes() }
    val packs = remember { com.steady.habittracker.data.themePacks() }
    val bgOpt = backgroundModeOption(selectedBackground)
    val previewBg = Color(bgOpt.backgroundArgb)
    val previewSurface = Color(bgOpt.surfaceArgb)
    val previewOn = if (bgOpt.isLight) Color(0xFF0F172A) else Color(0xFFE2E8F0)
    val previewMuted = if (bgOpt.isLight) Color(0xFF64748B) else Color(0xFF94A3B8)

    Column(Modifier.fillMaxSize()) {
        Text(
            "Your look",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = title
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Linux rice-inspired packs. Change anytime in Settings.",
            fontSize = 13.sp,
            color = muted
        )
        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // Live preview of selected bg + accent
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(previewBg)
                    .border(BorderStroke(1.dp, muted.copy(alpha = 0.35f)), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Text("Preview", color = previewMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(previewSurface, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            backgrounds.firstOrNull { it.id == selectedBackground }?.label ?: "Nord",
                            color = previewOn,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            schemes.firstOrNull { it.id == selectedScheme }?.label ?: "Green",
                            color = accent,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("Theme packs", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = body)
            Spacer(Modifier.height(8.dp))

            packs.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { pack ->
                        val pBg = backgroundModeOption(pack.backgroundId)
                        val pAccent = Color(com.steady.habittracker.data.accentColorArgb(pack.accentId))
                        val isSel = selectedBackground == pack.backgroundId && selectedScheme == pack.accentId
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(pBg.surfaceArgb))
                                .border(
                                    if (isSel) BorderStroke(2.dp, pAccent)
                                    else BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    onSelectBackground(pack.backgroundId)
                                    onSelectScheme(pack.accentId)
                                }
                                .padding(10.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(24.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(pBg.backgroundArgb))
                                )
                                Box(
                                    Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(pAccent)
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                pack.label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (pBg.isLight) Color(0xFF0F172A) else Color(0xFFE2E8F0)
                            )
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(8.dp))
            Text("Accent only", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = body)
            Spacer(Modifier.height(8.dp))

            schemes.chunked(5).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { opt ->
                        val col = Color(opt.colorArgb)
                        val isSel = selectedScheme == opt.id
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(col)
                                .border(
                                    if (isSel) BorderStroke(2.dp, Color.White)
                                    else BorderStroke(0.dp, Color.Transparent),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onSelectScheme(opt.id) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSel) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "${opt.label} selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    repeat(5 - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack) { Text("Back", color = muted) }
            Button(
                onClick = onFinish,
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) {
                Text(
                    "Start tracking",
                    color = Color(0xFF0F172A),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun DraftChipRow(
    draft: HabitDraft,
    groupLabel: String,
    surface: Color,
    onSurface: Color,
    muted: Color,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(surface, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (draft.icon.isNotBlank()) draft.icon else "✓",
            fontSize = 18.sp,
            modifier = Modifier.padding(end = 8.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(draft.name, color = onSurface, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(
                "$groupLabel · ${draft.type.name.lowercase().replace('_', ' ')}",
                color = muted,
                fontSize = 11.sp
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Remove", tint = muted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SuggestionCard(
    suggestion: HabitSuggestion,
    alreadyAdded: Boolean,
    accent: Color,
    surface: Color,
    onSurface: Color,
    muted: Color,
    onClick: () -> Unit
) {
    val border = if (alreadyAdded) accent.copy(alpha = 0.5f) else muted.copy(alpha = 0.25f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(surface, RoundedCornerShape(10.dp))
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable(enabled = !alreadyAdded, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (suggestion.icon.isNotBlank()) suggestion.icon else "•",
            fontSize = 18.sp,
            modifier = Modifier.padding(end = 10.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(
                suggestion.name,
                color = if (alreadyAdded) muted else onSurface,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
            Text(suggestion.why, color = muted, fontSize = 11.sp)
        }
        Text(
            if (alreadyAdded) "Added" else "+ Add",
            color = if (alreadyAdded) muted else accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CustomHabitSheet(
    groups: List<Group>,
    tags: List<Tag>,
    accent: Color,
    onDismiss: () -> Unit,
    onAdd: (HabitDraft) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var why by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(HabitType.CHECKBOX) }
    var groupId by remember { mutableStateOf(groups.firstOrNull()?.id.orEmpty()) }
    var selectedTags by remember { mutableStateOf(setOf<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text("Custom habit") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = why,
                    onValueChange = { why = it },
                    label = { Text("Why it matters (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Spacer(Modifier.height(8.dp))
                EmojiIconPicker(selected = icon, onSelect = { icon = it })
                Spacer(Modifier.height(8.dp))
                Text("Type", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        HabitType.CHECKBOX to "Check",
                        HabitType.COUNTER to "Count",
                        HabitType.DURATION_MIN to "Mins",
                        HabitType.SCALE_1_5 to "Scale",
                        HabitType.NOTE to "Note"
                    ).forEach { (t, label) ->
                        ThemedFilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("When (timeline group)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                groups.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { g ->
                            ThemedFilterChip(
                                selected = groupId == g.id,
                                onClick = { groupId = g.id },
                                label = {
                                    Text(
                                        if (g.icon.isNotBlank()) "${g.icon} ${g.name}" else g.name,
                                        fontSize = 11.sp
                                    )
                                }
                            )
                        }
                    }
                }
                if (tags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Tags (optional)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    tags.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { t ->
                                ThemedFilterChip(
                                    selected = t.id in selectedTags,
                                    onClick = {
                                        selectedTags =
                                            if (t.id in selectedTags) selectedTags - t.id
                                            else selectedTags + t.id
                                    },
                                    label = { Text(t.name, fontSize = 10.sp) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && groupId.isNotBlank()) {
                        onAdd(
                            HabitDraft(
                                name = name.trim(),
                                why = why.trim(),
                                groupId = groupId,
                                type = type,
                                tags = selectedTags.toList(),
                                icon = icon.trim(),
                                isSupplement = TagIds.SUPPLEMENTS in selectedTags
                            )
                        )
                    }
                },
                enabled = name.isNotBlank() && groupId.isNotBlank()
            ) {
                Text("Add to list", color = accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

internal fun normalizeOnboardingTime(hhmm: String): String {
    val p = hhmm.trim().split(":")
    val h = (p.getOrNull(0)?.toIntOrNull() ?: 0).coerceIn(0, 23)
    val m = (p.getOrNull(1)?.toIntOrNull() ?: 0).coerceIn(0, 59)
    return "%02d:%02d".format(h, m)
}

/** Add [minutes] to an HH:mm string, wrapping at 24h. */
internal fun addMinutesToOnboardingTime(hhmm: String, minutes: Int): String {
    val p = normalizeOnboardingTime(hhmm).split(":")
    val base = (p[0].toInt() * 60 + p[1].toInt() + minutes).mod(24 * 60)
    return "%02d:%02d".format(base / 60, base % 60)
}
