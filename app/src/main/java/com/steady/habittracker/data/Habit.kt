package com.steady.habittracker.data

import kotlinx.serialization.Serializable

/**
 * Data model v4.
 * - archived + canSkip on Habit/Group (archive instead of delete; hygiene non-skippable)
 * - parentId on Group (for Workouts subgroups / plans)
 * - skipped flag + loggedAt precision on entries (exact time + skip tracking)
 * - schemaVersion for migration.
 * - onboarded + colorScheme for first-run UX and app theming.
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
    val archived: Boolean = false
)

@Serializable
data class AppData(
    val groups: List<Group> = emptyList(),
    val habits: List<Habit> = emptyList(),
    // date (yyyy-MM-dd) -> habitId -> entry
    val entries: Map<String, Map<String, HabitEntry>> = emptyMap(),
    val reminders: List<Reminder> = emptyList(),
    val schemaVersion: Int = 4,
    val onboarded: Boolean = false,
    val colorScheme: String = "default"   // "default" | "blue" | "orange" | "purple" | "slate"
)