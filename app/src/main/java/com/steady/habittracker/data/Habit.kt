package com.steady.habittracker.data

import kotlinx.serialization.Serializable

/**
 * Data model v12.
 * - Groups = time-of-day slots on the 24h timeline (when you do it).
 * - Tags = category identity for History (what it is: Supplements, Movement…).
 * - SleepSettings anchors Morning + Bedtime routines to bed/wake times.
 * - Habit.additionalGroupIds: same habit can appear in multiple timeline groups (#24).
 * - Exercise routines + workout sessions for structured training (#20–#22).
 * - Habit.icon / Group.icon: optional emoji for lists, Today, widget (#29).
 * - ScoreState / NotificationPrefs: Steady Momentum + smart gentle reminders (v10).
 * - AutoSource / AutoLogMode / AutoSuggestion: on-device sensor & external auto-logging (v11).
 * - SleepAudio: OGG night recording, loud/snore events, multi-day retention (v12).
 * - archived + canSkip, parentId, skip/loggedAt, themes, schedules (v5).
 * All @Serializable for DataStore JSON (portable).
 */

/**
 * Supported habit logging types. CHECKBOX is the simple done/not-done.
 * Others support richer entry values + notes (inspired by user's TrackAndGraph usage).
 */
@Serializable
enum class HabitType {
    CHECKBOX,      // Binary toggle (value 1.0 = done)
    COUNTER,       // e.g. reps, glasses, mg dose (value = amount)
    DURATION_MIN,  // e.g. breathing, NSDR, deep work minutes
    SCALE_1_5,     // Mood, energy, soreness (1 low - 5 high)
    NOTE           // Pure journal/reflection (value ignored, note required)
}

/**
 * On-device or external source that can propose/fill a habit log.
 * All processing stays local; each source is opt-in per habit + OS permission.
 */
@Serializable
enum class AutoSource {
    /** Manual only (default). */
    NONE,
    /** Daily screen-on minutes via UsageStats (needs usage access). */
    SCREEN_MINUTES,
    /** Screen minutes after wind-down start (phone-free evening). */
    SCREEN_AFTER_WINDDOWN,
    /** Average ambient lux during wind-down window (light sensor). */
    LIGHT_BEDTIME_AVG,
    /** Checkbox: dark enough in wind-down (avg lux under threshold). */
    LIGHT_DARK_CHECK,
    /** Approximate ambient noise during wind-down (mic samples, opt-in). */
    NOISE_EVENING_DB,
    /** Steps from phone hardware counter (optional; Gadgetbridge preferred later). */
    PHONE_STEPS,
    /**
     * Steps/metrics from Gadgetbridge or automation apps via
     * [com.steady.habittracker.sensors.ExternalMetricsStore] / broadcast.
     */
    GADGETBRIDGE_STEPS,
    /** Generic external metric key stored in ExternalMetricsStore (unit-agnostic). */
    EXTERNAL_METRIC
}

/** How sensor/external values are applied when a source is linked. */
@Serializable
enum class AutoLogMode {
    /** Show a suggestion on Today; user accepts or dismisses. */
    SUGGEST,
    /** Write the entry automatically (note marks source); user can still edit. */
    AUTO_APPLY
}

/** Pending or last sensor proposal for a habit on a date. */
@Serializable
data class AutoSuggestion(
    val habitId: String,
    val date: String,
    val value: Double,
    val note: String = "",
    val source: AutoSource = AutoSource.NONE,
    val observedAt: Long = 0L,
    /** When true, already applied or dismissed — hidden from banner. */
    val resolved: Boolean = false
)

/**
 * When a habit appears on Today / widget (catalog stays in Manage always).
 * Defaults to DAILY so existing items keep current behavior.
 */
@Serializable
enum class ShowPreset {
    DAILY,           // every day
    WEEKDAYS,        // Mon–Fri
    WEEKENDS,        // Sat–Sun
    CUSTOM_DAYS,     // uses [Habit.weekdays]
    EVERY_N_DAYS,    // every intervalDays from anchorDate
    SPECIFIC_DATES   // only dates in specificDates
}

/**
 * Timeline group: when on the 24h day (Morning, Focus, Bedtime, Sleep…).
 * Not a category — use [Tag] for "Supplements", "Movement", etc.
 */
@Serializable
data class Group(
    val id: String,
    val name: String,
    val timeHint: String = "ANY", // MORNING, EVENING, WORK, REVIEW, SLEEP, BEDTIME, ANY
    val order: Int = 0,
    val parentId: String? = null,   // for subgroups e.g. under a workout plan
    val archived: Boolean = false,
    /** Optional emoji / short icon for lists and Today section headers (#29). Empty = letter fallback. */
    val icon: String = ""
)

/**
 * Category tag for history and analytics. Survives moving a habit across timeline groups.
 * Example: Omega-3 lives in Morning group but keeps tag "Supplements".
 */
@Serializable
data class Tag(
    val id: String,
    val name: String,
    val color: Int? = null,
    val order: Int = 0,
    val archived: Boolean = false
)

/**
 * Sleep is the spine of the day (Blueprint-style).
 * Morning routine starts at wake; bedtime/wind-down ends at bed; Sleep fills overnight.
 */
@Serializable
data class SleepSettings(
    val bedTime: String = "23:00",      // HH:mm
    val wakeTime: String = "07:00",     // HH:mm
    /** Minutes of wind-down before bed (Bedtime group block). */
    val windDownMinutes: Int = 60,
    /** Minutes of morning routine after wake. */
    val morningMinutes: Int = 90,
    val morningGroupId: String? = null,
    val bedtimeGroupId: String? = null,
    val sleepGroupId: String? = null
)

/**
 * One logged entry for a habit on a given day.
 * For CHECKBOX: value=1.0 means completed.
 */
@Serializable
data class HabitEntry(
    val value: Double = 1.0,
    val note: String = "",
    val loggedAt: Long = 0L,
    val skipped: Boolean = false   // set for Skip button (value often 0)
)

/** Basic capture item for quick ideas/todos (#8, #9). Stored alongside for MVP. */
@Serializable
data class CaptureItem(
    val id: String,
    val title: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val tags: List<String> = emptyList(),
    val processed: Boolean = false,
    val linkedHabitId: String? = null
)

/**
 * Configurable reminder (per group or global "missed review").
 * days: 1=Mon ... 7=Sun to match java.time.DayOfWeek.
 */
@Serializable
data class Reminder(
    val id: String,
    val groupId: String?, // null = daily review / global
    val time: String,     // "09:00" 24h format
    val days: Set<Int>,   // 1..7
    val enabled: Boolean = true
)

/**
 * One time block in a Schedule (used by the 24-hour circle).
 * color: optional ARGB int for custom segment color (null = use theme accent). Persisted in JSON/export.
 */
@Serializable
data class TimeBlock(
    val start: String,   // "HH:mm" 24h format
    val end: String,
    val groupId: String,
    val color: Int? = null
)

/**
 * Named schedule that can assign groups to times of day.
 * Used to drive the widget's "current group" and (future) notifications.
 * Can be active only on certain weekdays or specific dates.
 */
@Serializable
data class Schedule(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val weekdays: Set<Int> = setOf(1,2,3,4,5,6,7),  // 1=Mon ... 7=Sun
    val specificDates: List<String> = emptyList(),   // yyyy-MM-dd ; if non-empty these take precedence
    val timeBlocks: List<TimeBlock> = emptyList()
)

@Serializable
data class Habit(
    val id: String,
    val name: String,
    val why: String = "", // kept for export/back-compat; not shown in UI
    val groupId: String,
    val type: HabitType = HabitType.CHECKBOX,
    val target: Double? = null,
    val unit: String = "",
    val order: Int = 0,
    val canSkip: Boolean = true,   // false for hygiene/essentials (no skip allowed)
    val archived: Boolean = false,
    /** @deprecated Prefer [tags] with a Supplements tag; kept for back-compat / quick UI. */
    val isSupplement: Boolean = false,
    /** Category tag ids (what it is). Independent of [groupId] (when you do it). */
    val tags: List<String> = emptyList(),
    /** When this habit appears on Today (Manage catalog always keeps it). */
    val showPreset: ShowPreset = ShowPreset.DAILY,
    /** Used by CUSTOM_DAYS (1=Mon … 7=Sun). */
    val weekdays: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    /** Used by EVERY_N_DAYS (e.g. 2 = every other day). */
    val intervalDays: Int = 2,
    /** Anchor for EVERY_N_DAYS (yyyy-MM-dd). Null → treat as epoch / always interval from date 0. */
    val anchorDate: String? = null,
    /** Used by SPECIFIC_DATES (yyyy-MM-dd). */
    val specificDates: List<String> = emptyList(),
    /** Soft stack: previous habit in sequence (Atomic Habits style). Null = standalone / root. */
    val afterHabitId: String? = null,
    /**
     * Extra timeline groups for multi-group membership (#24).
     * Together with [groupId] this is a flat set: habit appears once per group on Today,
     * may join any number of groups, and still has one HabitEntry per day.
     * [groupId] is the first/canonical member of that set (storage shape only).
     */
    val additionalGroupIds: List<String> = emptyList(),
    /** Optional emoji / short icon shown next to the name (#29). Empty = first-letter fallback. */
    val icon: String = "",
    /** Sensor / external auto-log source (v11). */
    val autoSource: AutoSource = AutoSource.NONE,
    /** Suggest vs silent auto-apply when [autoSource] is set. */
    val autoMode: AutoLogMode = AutoLogMode.SUGGEST,
    /**
     * Threshold / target for boolean-style sources (e.g. lux max for dark check,
     * max evening screen minutes for "phone-free"). Also used as EXTERNAL_METRIC key
     * when [autoMetricKey] is blank and unit carries meaning — prefer [autoMetricKey].
     */
    val autoThreshold: Double? = null,
    /** Key for [AutoSource.EXTERNAL_METRIC] / Gadgetbridge field name (e.g. "steps"). */
    val autoMetricKey: String = ""
)

// --- Exercise routines & workout sessions (#20–#22) ---

@Serializable
data class ExerciseDef(
    val id: String,
    val name: String,
    val sets: Int = 3,
    val reps: String = "8-12",
    val restSec: Int = 60,
    val notes: String = "",
    val muscleGroup: String = "",
    val order: Int = 0
)

@Serializable
data class ExerciseRoutine(
    val id: String,
    val name: String,
    val description: String = "",
    val exercises: List<ExerciseDef> = emptyList(),
    val estimatedDurationMin: Int = 45,
    val tags: List<String> = emptyList(),
    val showPreset: ShowPreset = ShowPreset.DAILY,
    val weekdays: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    val groupId: String? = null,
    val archived: Boolean = false,
    val order: Int = 0,
    /** Optional habit auto-logged on session complete (streaks / progress ring). */
    val linkedHabitId: String? = null
)

@Serializable
data class SetLog(
    val setNumber: Int,
    val actualReps: Int? = null,
    val weightKg: Double? = null,
    val rpe: Int? = null,
    val note: String = ""
)

@Serializable
data class WorkoutSession(
    val id: String,
    val routineId: String,
    val date: String,
    val startedAt: Long,
    val completedAt: Long? = null,
    val performedExercises: Map<String, List<SetLog>> = emptyMap(),
    val totalDurationMin: Int? = null,
    val overallNote: String = "",
    val completed: Boolean = false
)

// --- Dreamline Goal Stories (#25) + Path orientation (#26) ---

@Serializable
enum class DreamHorizon {
    SIX_MONTHS,
    TWELVE_MONTHS
}

@Serializable
enum class DreamCategory {
    HAVING,
    BEING,
    DOING
}

@Serializable
data class GoalStep(
    val id: String,
    val title: String,
    val done: Boolean = false,
    val order: Int = 0
)

/**
 * Continuous goal produced by the Dreamline wizard (or created manually).
 * Tagged for Path tab filtering (dreamline, 6-month/12-month, being/doing/having).
 */
@Serializable
data class GoalStory(
    val id: String,
    val title: String,
    val description: String = "",
    val category: DreamCategory = DreamCategory.DOING,
    val horizon: DreamHorizon = DreamHorizon.SIX_MONTHS,
    val tags: List<String> = listOf("dreamline"),
    val progress: Float = 0f,
    val confidence: Float = 0.5f,
    /** Immediate ≤5-minute first action from the wizard. */
    val firstStepNow: String = "",
    val steps: List<GoalStep> = emptyList(),
    val startDate: String = "",
    val endDate: String = "",
    val archived: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    /** Mindset notes / check-in reflections attached on Path tab. */
    val notes: List<String> = emptyList()
)

/** Daily/on-demand “Am I on path?” alignment check (#26). */
@Serializable
data class PathAlignmentCheck(
    val id: String,
    val date: String,
    val visionAlignment: Int = 3,
    val energyTowardDreams: Int = 3,
    val identityCongruence: Int = 3,
    val note: String = "",
    val loggedAt: Long = 0L
)

/**
 * Compact per-day Momentum score (last ~60 days kept for History charts).
 * Points for a day are always re-derivable from [AppData.entries] via [HabitDomain.computeDayPoints].
 */
@Serializable
data class DayScore(
    val date: String,
    val points: Int,
    val completion: Float = 0f
)

/**
 * Steady Momentum lifetime ledger. Today's live points are computed from entries;
 * [history] holds finalized past days; [lifetimePoints] is the sum of all finalized days
 * (including those aged out of the rolling window).
 */
@Serializable
data class ScoreState(
    val lifetimePoints: Int = 0,
    val history: List<DayScore> = emptyList(),
    /** Last date whose score was finalized into history (yyyy-MM-dd). Empty = never. */
    val lastFinalizedDate: String = ""
)

/**
 * Smart-but-gentle notification preferences (local only).
 * [firesDate]/[firesCount] enforce the daily rate limit.
 */
@Serializable
data class NotificationPrefs(
    val quietStart: String = "22:30",
    val quietEnd: String = "07:00",
    val maxPerDay: Int = 4,
    val adaptiveTiming: Boolean = true,
    val streakRiskNudge: Boolean = true,
    val celebrateFullClear: Boolean = true,
    /** Date (yyyy-MM-dd) the fire counter applies to. */
    val firesDate: String = "",
    val firesCount: Int = 0
)

/** Classification for overnight loud events (heuristic, not medical diagnosis). */
@Serializable
enum class SleepEventKind {
    LOUD,
    /** Periodic loud bursts suggestive of snoring. */
    POSSIBLE_SNORE,
    TALK_OR_TV,
    OTHER
}

/** One loud episode within a night session (offset into a segment file for playback). */
@Serializable
data class SleepAudioEvent(
    val id: String,
    val kind: SleepEventKind = SleepEventKind.LOUD,
    /** Epoch millis when the event started. */
    val startAt: Long,
    val durationMs: Int,
    /** Peak amplitude 0–32767 from MediaRecorder. */
    val peakAmplitude: Int = 0,
    /** Relative 0–100 loudness score. */
    val loudness: Int = 0,
    /** Segment file name under the night folder (not absolute path). */
    val segmentFile: String = "",
    /** Seek offset within [segmentFile] for playback (ms). */
    val offsetInSegmentMs: Int = 0
)

/** One overnight OGG recording session (metadata in DataStore; audio on disk). */
@Serializable
data class SleepNightSession(
    val id: String,
    /** Calendar date of the morning this night ends (wake day), yyyy-MM-dd. */
    val wakeDate: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val events: List<SleepAudioEvent> = emptyList(),
    val segmentFiles: List<String> = emptyList(),
    val eventCount: Int = 0,
    val snoreLikeCount: Int = 0,
    val loudMinutes: Float = 0f,
    /** 0–100 rough quietness score (higher = quieter night). */
    val quietScore: Int = 0,
    val codec: String = "ogg/opus",
    val note: String = "",
    val completed: Boolean = false
)

/** Preferences for snoring / sleep-audio capture. */
@Serializable
data class SleepAudioPrefs(
    /** Master enable for scheduled night recording. */
    val enabled: Boolean = false,
    /** Keep audio + sessions for this many wake-days. */
    val retainDays: Int = 3,
    /** Segment length minutes (OGG chunks). */
    val segmentMinutes: Int = 15,
    /** Amplitude threshold 0–32767 (default ~moderate room noise). */
    val loudThreshold: Int = 4000,
    /** Min continuous loud ms to count as an event. */
    val minEventMs: Int = 600,
    /** Max single event ms before splitting / classifying as sustained. */
    val maxEventMs: Int = 12000,
    /** Auto-start at bedTime from [SleepSettings]. */
    val scheduleWithSleep: Boolean = true,
    /**
     * Only run overnight capture while the device is charging / plugged in.
     * Start is skipped off-charger; active sessions stop if unplugged.
     */
    val requireCharging: Boolean = true,
    /** Optional habit id to auto-fill SCALE_1_5 sleep quality from quietScore. */
    val linkedHabitId: String? = null
)

@Serializable
data class AppData(
    val groups: List<Group> = emptyList(),
    val habits: List<Habit> = emptyList(),
    // date (yyyy-MM-dd) -> habitId -> entry
    val entries: Map<String, Map<String, HabitEntry>> = emptyMap(),
    val reminders: List<Reminder> = emptyList(),
    val schemaVersion: Int = 12,
    val onboarded: Boolean = false,
    val colorScheme: String = "default",   // accent id from accentSchemes() (green, rose, blush, …)
    val backgroundMode: String = "dark",   // id from backgroundModes() (dark, amoled, light, forest, …)
    val schedules: List<Schedule> = emptyList(),
    val activeScheduleId: String? = null,
    val captures: List<CaptureItem> = emptyList(),  // #8 quick capture inbox support
    /** Master switch for all habit reminders (Settings). When false, no alarms are scheduled. */
    val remindersMasterEnabled: Boolean = true,
    /** Category tags for History (Supplements, Movement, …). */
    val tags: List<Tag> = emptyList(),
    /** Bed/wake spine that anchors Morning + Bedtime groups on the 24h timeline. */
    val sleep: SleepSettings = SleepSettings(),
    /** Structured exercise routines catalog (#21). */
    val routines: List<ExerciseRoutine> = emptyList(),
    /** Completed / in-progress workout sessions (newest-friendly flat list). */
    val workoutSessions: List<WorkoutSession> = emptyList(),
    /** Dreamline-derived continuous goals for Path tab (#25, #26). */
    val goals: List<GoalStory> = emptyList(),
    /** Alignment check-ins for Path tab. */
    val pathChecks: List<PathAlignmentCheck> = emptyList(),
    /** Steady Momentum scoring ledger (v10). */
    val score: ScoreState = ScoreState(),
    /** Smart reminder prefs + daily fire counter (v10). */
    val notificationPrefs: NotificationPrefs = NotificationPrefs(),
    /** Pending sensor / external auto-log suggestions (v11). */
    val autoSuggestions: List<AutoSuggestion> = emptyList(),
    /**
     * Master switch for background sensor sampling (light/noise workers).
     * Per-habit sources still required.
     */
    val autoLogMasterEnabled: Boolean = true,
    /** Overnight snore / loud-noise audio capture prefs (v12). */
    val sleepAudioPrefs: SleepAudioPrefs = SleepAudioPrefs(),
    /** Recent sleep-audio nights (audio files live under app filesDir). */
    val sleepNights: List<SleepNightSession> = emptyList()
)

/**
 * Runtime theme colors resolved from settings (not persisted).
 * Background from [backgroundModes]; accent from [accentSchemes].
 */
data class ThemeColors(
    val background: Int,   // ARGB
    val surface: Int,
    val accent: Int,
    val widgetRowBg: Int
)

/**
 * Immutable update helpers for AppData.
 * Addresses mutable map mutations (see issues #16, #14).
 * All return new AppData instances; never mutate in place.
 * These keep business logic clean and easy to test / reason about.
 */
fun AppData.withUpdatedEntry(date: String, habitId: String, entry: HabitEntry): AppData {
    val dayMap = (entries[date] ?: emptyMap()).toMutableMap() // local temp only for building new
    dayMap[habitId] = entry
    val newEntries = entries.toMutableMap()
    newEntries[date] = dayMap
    return copy(entries = newEntries)
}

fun AppData.withRemovedEntry(date: String, habitId: String): AppData {
    val dayMap = entries[date]?.toMutableMap() ?: return this
    dayMap.remove(habitId)
    val newEntries = entries.toMutableMap()
    if (dayMap.isEmpty()) {
        newEntries.remove(date)
    } else {
        newEntries[date] = dayMap
    }
    return copy(entries = newEntries)
}

fun AppData.withArchivedHabit(habitId: String): AppData {
    val newHabits = habits.map { if (it.id == habitId) it.copy(archived = true) else it }
    return copy(habits = newHabits)
}

fun AppData.withUnarchivedHabit(habitId: String): AppData {
    val newHabits = habits.map { if (it.id == habitId) it.copy(archived = false) else it }
    return copy(habits = newHabits)
}

fun AppData.withArchivedGroup(groupId: String): AppData {
    val newGroups = groups.map { if (it.id == groupId) it.copy(archived = true) else it }
    val newHabits = habits.map { if (it.groupId == groupId) it.copy(archived = true) else it }
    return copy(groups = newGroups, habits = newHabits)
}

fun AppData.withUnarchivedGroup(groupId: String): AppData {
    val newGroups = groups.map { if (it.id == groupId) it.copy(archived = false) else it }
    return copy(groups = newGroups)
}

fun AppData.withHabit(updated: Habit): AppData {
    val newHabits = habits.map { if (it.id == updated.id) updated else it }
    return copy(habits = newHabits)
}

fun AppData.withAddedHabit(newHabit: Habit): AppData {
    return copy(habits = habits + newHabit)
}

fun AppData.withGroup(updated: Group): AppData {
    val newGroups = groups.map { if (it.id == updated.id) updated else it }
    return copy(groups = newGroups)
}

fun AppData.withAddedGroup(newGroup: Group): AppData {
    return copy(groups = groups + newGroup)
}

fun AppData.withReminder(updated: Reminder): AppData {
    val others = reminders.filter { it.id != updated.id }
    return copy(reminders = others + updated)
}

fun AppData.withoutReminder(id: String): AppData {
    return copy(reminders = reminders.filter { it.id != id })
}

fun AppData.withToggledReminder(id: String): AppData {
    val updated = reminders.map { if (it.id == id) it.copy(enabled = !it.enabled) else it }
    return copy(reminders = updated)
}

fun AppData.withColorScheme(scheme: String): AppData = copy(colorScheme = scheme)
fun AppData.withBackgroundMode(mode: String): AppData = copy(backgroundMode = mode)
fun AppData.withOnboarded(): AppData = copy(onboarded = true)
fun AppData.withRemindersMasterEnabled(enabled: Boolean): AppData = copy(remindersMasterEnabled = enabled)
fun AppData.withSleep(sleep: SleepSettings): AppData = copy(sleep = sleep)
fun AppData.withAddedTag(tag: Tag): AppData = copy(tags = tags + tag)
fun AppData.withTag(updated: Tag): AppData =
    copy(tags = tags.map { if (it.id == updated.id) updated else it })
fun AppData.withArchivedTag(tagId: String): AppData =
    copy(
        tags = tags.map { if (it.id == tagId) it.copy(archived = true) else it },
        habits = habits.map { h ->
            if (tagId in h.tags) h.copy(tags = h.tags.filter { it != tagId }) else h
        }
    )

// Schedule helpers (mirrored for convenience; repo delegates still work)
fun AppData.withAddedSchedule(schedule: Schedule): AppData = copy(schedules = schedules + schedule)
fun AppData.withUpdatedSchedule(schedule: Schedule): AppData =
    copy(schedules = schedules.map { if (it.id == schedule.id) schedule else it })
fun AppData.withoutSchedule(scheduleId: String): AppData {
    val newSchedules = schedules.filter { it.id != scheduleId }
    val newActive = if (activeScheduleId == scheduleId) null else activeScheduleId
    return copy(schedules = newSchedules, activeScheduleId = newActive)
}
fun AppData.withActiveSchedule(scheduleId: String?): AppData = copy(activeScheduleId = scheduleId)

// Capture helpers (#8, #9, #11)
fun AppData.withAddedCapture(capture: CaptureItem): AppData = copy(captures = captures + capture)
fun AppData.withUpdatedCapture(updated: CaptureItem): AppData =
    copy(captures = captures.map { if (it.id == updated.id) updated else it })
fun AppData.withoutCapture(id: String): AppData = copy(captures = captures.filter { it.id != id })

// Exercise routine helpers (#21)
fun AppData.withAddedRoutine(routine: ExerciseRoutine): AppData = copy(routines = routines + routine)
fun AppData.withUpdatedRoutine(routine: ExerciseRoutine): AppData =
    copy(routines = routines.map { if (it.id == routine.id) routine else it })
fun AppData.withArchivedRoutine(routineId: String): AppData =
    copy(routines = routines.map { if (it.id == routineId) it.copy(archived = true) else it })
fun AppData.withUnarchivedRoutine(routineId: String): AppData =
    copy(routines = routines.map { if (it.id == routineId) it.copy(archived = false) else it })
fun AppData.withoutRoutine(routineId: String): AppData =
    copy(routines = routines.filter { it.id != routineId })
fun AppData.withLoggedWorkoutSession(session: WorkoutSession): AppData {
    val others = workoutSessions.filter { it.id != session.id }
    return copy(workoutSessions = others + session)
}
fun AppData.withUpdatedWorkoutSession(session: WorkoutSession): AppData =
    copy(workoutSessions = workoutSessions.map { if (it.id == session.id) session else it })
fun AppData.withoutWorkoutSession(sessionId: String): AppData =
    copy(workoutSessions = workoutSessions.filter { it.id != sessionId })
/** Merge Blueprint template routines that are not already present (by id). */
fun AppData.withBlueprintRoutinesIfMissing(templates: List<ExerciseRoutine>): AppData {
    val existingIds = routines.map { it.id }.toSet()
    val toAdd = templates.filter { it.id !in existingIds }
    return if (toAdd.isEmpty()) this else copy(routines = routines + toAdd)
}

// Goal Story / Path helpers (#25, #26)
fun AppData.withAddedGoal(goal: GoalStory): AppData = copy(goals = goals + goal)
fun AppData.withUpdatedGoal(goal: GoalStory): AppData =
    copy(goals = goals.map { if (it.id == goal.id) goal else it })
fun AppData.withArchivedGoal(goalId: String): AppData =
    copy(goals = goals.map { if (it.id == goalId) it.copy(archived = true, updatedAt = System.currentTimeMillis()) else it })
fun AppData.withGoalsReplacedFromWizard(newGoals: List<GoalStory>, replaceDreamline: Boolean = true): AppData {
    val kept = if (replaceDreamline) {
        goals.filter { g -> !g.archived && "dreamline" !in g.tags.map { it.lowercase() } }
    } else {
        goals
    }
    return copy(goals = kept + newGoals)
}
fun AppData.withAddedPathCheck(check: PathAlignmentCheck): AppData = copy(pathChecks = pathChecks + check)
fun AppData.withPathChecks(checks: List<PathAlignmentCheck>): AppData = copy(pathChecks = checks)

fun AppData.withScore(score: ScoreState): AppData = copy(score = score)
fun AppData.withNotificationPrefs(prefs: NotificationPrefs): AppData = copy(notificationPrefs = prefs)
fun AppData.withAutoSuggestions(list: List<AutoSuggestion>): AppData = copy(autoSuggestions = list)
fun AppData.withAutoLogMasterEnabled(enabled: Boolean): AppData = copy(autoLogMasterEnabled = enabled)
fun AppData.withSleepAudioPrefs(prefs: SleepAudioPrefs): AppData = copy(sleepAudioPrefs = prefs)
fun AppData.withSleepNights(nights: List<SleepNightSession>): AppData = copy(sleepNights = nights)
fun AppData.withUpsertedSleepNight(night: SleepNightSession): AppData {
    val others = sleepNights.filter { it.id != night.id }
    return copy(sleepNights = (listOf(night) + others).sortedByDescending { it.startedAt })
}

fun AppData.withUpsertedAutoSuggestion(suggestion: AutoSuggestion): AppData {
    val others = autoSuggestions.filterNot {
        it.habitId == suggestion.habitId && it.date == suggestion.date && !it.resolved
    }
    return copy(autoSuggestions = others + suggestion)
}

fun AppData.withResolvedAutoSuggestion(habitId: String, date: String): AppData =
    copy(
        autoSuggestions = autoSuggestions.map {
            if (it.habitId == habitId && it.date == date && !it.resolved) it.copy(resolved = true) else it
        }
    )

/** Stable tag constants for Dreamline / Path filtering. */
object GoalTags {
    const val DREAMLINE = "dreamline"
    const val HORIZON_6 = "6-month"
    const val HORIZON_12 = "12-month"
    fun forHorizon(h: DreamHorizon) = when (h) {
        DreamHorizon.SIX_MONTHS -> HORIZON_6
        DreamHorizon.TWELVE_MONTHS -> HORIZON_12
    }
    fun forCategory(c: DreamCategory) = c.name.lowercase()
}
