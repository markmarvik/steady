package com.steady.habittracker.data

import kotlinx.serialization.Serializable

/**
 * Data model v5.
 * - archived + canSkip on Habit/Group (archive instead of delete; hygiene non-skippable)
 * - parentId on Group (for Workouts subgroups / plans)
 * - skipped flag + loggedAt precision on entries (exact time + skip tracking)
 * - schemaVersion for migration.
 * - onboarded + colorScheme + backgroundMode for theming (supports AMOLED black).
 * - schedules + activeScheduleId for advanced 24h time scheduling (multiple schedules,
 *   weekday/date rules, presets, 24-hour circle editor for widget + notifications).
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
 * Group for time-of-day / routine organization (Morning Routine, Evening, Work, Mindset...).
 */
@Serializable
data class Group(
    val id: String,
    val name: String,
    val timeHint: String = "ANY", // MORNING, EVENING, WORK, REVIEW, ANY
    val order: Int = 0,
    val parentId: String? = null,   // for subgroups e.g. under "Workouts"
    val archived: Boolean = false
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
 */
@Serializable
data class TimeBlock(
    val start: String,   // "HH:mm" 24h format
    val end: String,
    val groupId: String
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
    val why: String = "",
    val groupId: String,
    val type: HabitType = HabitType.CHECKBOX,
    val target: Double? = null,
    val unit: String = "",
    val order: Int = 0,
    val canSkip: Boolean = true,   // false for hygiene/essentials (no skip allowed)
    val archived: Boolean = false,
    val isSupplement: Boolean = false   // #3: tag for supplement visibility + scheduling across morning/evening
)

@Serializable
data class AppData(
    val groups: List<Group> = emptyList(),
    val habits: List<Habit> = emptyList(),
    // date (yyyy-MM-dd) -> habitId -> entry
    val entries: Map<String, Map<String, HabitEntry>> = emptyMap(),
    val reminders: List<Reminder> = emptyList(),
    val schemaVersion: Int = 5,
    val onboarded: Boolean = false,
    val colorScheme: String = "default",   // accent: "default" | "blue" | "orange" | "purple" | "slate"
    val backgroundMode: String = "dark",   // "dark" | "amoled" | "light"  (OLED pure black supported)
    val schedules: List<Schedule> = emptyList(),
    val activeScheduleId: String? = null,
    val captures: List<CaptureItem> = emptyList()  // #8 quick capture inbox support
)

/**
 * Runtime theme colors resolved from settings (not persisted).
 * Supports background (dark / amoled / light) + accent (foreground highlight) selection.
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
