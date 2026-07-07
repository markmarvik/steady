package com.steady.habittracker.data

import java.time.LocalDate
import java.time.LocalTime

/**
 * Domain / pure computation helpers extracted from Repository.
 * Addresses #14 (move business logic out of data layer).
 *
 * These are stateless, easy to unit test (see #15), and have no Android / IO side effects.
 * Repository can delegate or UI can use directly for derived state.
 */
object HabitDomain {

    /** Returns sorted groups + habits in each for Today UI (non-archived only). */
    fun groupHabits(data: AppData): List<Pair<Group, List<Habit>>> {
        val sortedGroups = data.groups.filter { !it.archived }.sortedBy { it.order }
        return sortedGroups.map { g ->
            g to data.habits.filter { it.groupId == g.id && !it.archived }.sortedBy { it.order }
        }
    }

    /** Compute current streak (consecutive days back from today with reasonable completion). */
    fun computeStreak(data: AppData): Int {
        if (data.habits.isEmpty()) return 0
        val todayStr = getToday()
        var streak = 0
        var checkDate = LocalDate.parse(todayStr)
        val totalHabits = data.habits.size

        while (true) {
            val key = checkDate.toString()
            val dayEntries = data.entries[key] ?: emptyMap()
            val completedCount = dayEntries.count { (_, e) -> e.value >= 0.5 }
            val rate = if (totalHabits > 0) completedCount.toFloat() / totalHabits else 0f
            if (rate >= 0.6f || completedCount >= 3) { // lenient for mixed types
                streak++
                checkDate = checkDate.minusDays(1)
                if (streak > 365) break
            } else break
        }
        return streak
    }

    /** Overall completion rate (0f..1f) for a specific date key (yyyy-MM-dd). */
    fun computeDayCompletion(data: AppData, dateStr: String): Float {
        val dayMap = data.entries[dateStr] ?: emptyMap()
        val done = dayMap.count { (_, e) -> e.value >= 0.5 }
        val total = data.habits.size.coerceAtLeast(1)
        return done.toFloat() / total
    }

    /** Last N days of overall rates, oldest first. Returns list of (date, rate). */
    fun computeLastNDays(data: AppData, n: Int = 7): List<Pair<String, Float>> {
        val result = mutableListOf<Pair<String, Float>>()
        var d = LocalDate.now()
        repeat(n) {
            val key = d.toString()
            result.add(key to computeDayCompletion(data, key))
            d = d.minusDays(1)
        }
        return result.reversed()
    }

    /** 7-day average completion for a specific group (habits belonging to it). */
    fun computeGroup7DayAvg(data: AppData, groupId: String): Float {
        val groupHabits = data.habits.filter { it.groupId == groupId && !it.archived }
        if (groupHabits.isEmpty()) return 0f
        var sum = 0f
        var count = 0
        var d = LocalDate.now()
        repeat(7) {
            val key = d.toString()
            val dayMap = data.entries[key] ?: emptyMap()
            val done = groupHabits.count { h -> (dayMap[h.id]?.value ?: 0.0) >= 0.5 }
            sum += done.toFloat() / groupHabits.size
            count++
            d = d.minusDays(1)
        }
        return if (count > 0) sum / count else 0f
    }

    /** Simple time-of-day group hint for widget / Today highlight. */
    fun getCurrentPeriodHint(): String {
        val hour = LocalTime.now().hour
        return when (hour) {
            in 5..11 -> "MORNING"
            in 12..17 -> "WORK"
            else -> "EVENING"
        }
    }

    /** Simple theme color resolver (used by app + widget). Supports AMOLED (OLED), dark, and light modes.
     * Foreground/accent is chosen separately via colorScheme.
     */
    fun resolveThemeColors(data: AppData): ThemeColors {
        val accent = when (data.colorScheme) {
            "blue" -> 0xFF3B82F6.toInt()
            "orange" -> 0xFFF97316.toInt()
            "purple" -> 0xFF8B5CF6.toInt()
            "slate" -> 0xFF64748B.toInt()
            "teal" -> 0xFF14B8A6.toInt()
            "red" -> 0xFFEF4444.toInt()
            else -> 0xFF22C55E.toInt() // green
        }
        val mode = data.backgroundMode
        val isAmoled = mode == "amoled"
        val isLight = mode == "light"

        val bg = when {
            isAmoled -> 0xFF000000.toInt()
            isLight -> 0xFFF8FAFC.toInt()
            else -> 0xFF0F172A.toInt()
        }
        val surface = when {
            isAmoled -> 0xFF0F0F0F.toInt()
            isLight -> 0xFFFFFFFF.toInt()
            else -> 0xFF1E2937.toInt()
        }
        val widgetRowBg = when {
            isAmoled -> 0xFF1A1A1A.toInt()
            isLight -> 0xFFE2E8F0.toInt()
            else -> 0xFF1E3A5F.toInt()
        }
        return ThemeColors(bg, surface, accent, widgetRowBg)
    }

    // --- Active + hierarchy helpers ---

    /** Active (non-archived) groups, top-level first. */
    fun getActiveGroups(data: AppData): List<Group> =
        data.groups.filter { !it.archived }.sortedBy { it.order }

    /** Habits for a group (active only). Supports parent for subgroups. */
    fun getActiveHabitsForGroup(data: AppData, groupId: String): List<Habit> =
        data.habits.filter { it.groupId == groupId && !it.archived }.sortedBy { it.order }

    /** Return nested view for a parent (e.g. Workouts and its plan subgroups). */
    fun getSubGroups(data: AppData, parentId: String): List<Group> =
        data.groups.filter { it.parentId == parentId && !it.archived }.sortedBy { it.order }

    /** Count recent skips for a habit (distinct days in window). */
    fun countRecentSkips(data: AppData, habitId: String, daysBack: Int = 7): Int {
        val today = LocalDate.now()
        return (0 until daysBack).count { offset ->
            val d = today.minusDays(offset.toLong()).toString()
            data.entries[d]?.get(habitId)?.skipped == true
        }
    }

    /** Convenience today string (yyyy-MM-dd). */
    fun getToday(): String = LocalDate.now().toString()

    /** Resolve current group from active schedule or legacy hint.
     * Supports overnight blocks (e.g. Sleep 23:00-07:00). */
    fun resolveCurrentGroup(data: AppData, now: LocalTime = LocalTime.now()): Group? {
        val schedule = getActiveSchedule(data)
        if (schedule != null && isScheduleApplicableToday(data, schedule) && schedule.timeBlocks.isNotEmpty()) {
            val minutesNow = now.hour * 60 + now.minute
            for (block in schedule.timeBlocks) {
                val startMin = parseTimeToMinutes(block.start)
                val endMin = parseTimeToMinutes(block.end)
                val inBlock = if (startMin < endMin) {
                    minutesNow in startMin until endMin
                } else {
                    // overnight block crosses midnight
                    minutesNow >= startMin || minutesNow < endMin
                }
                if (inBlock) {
                    return data.groups.find { it.id == block.groupId && !it.archived }
                }
            }
            // If past last block, pick first (or next day's)
            return data.groups.find { it.id == schedule.timeBlocks.first().groupId && !it.archived }
        }

        // Fallback to legacy timeHint
        val hint = getCurrentPeriodHint()
        return data.groups.filter { !it.archived }.firstOrNull { it.timeHint == hint }
            ?: data.groups.filter { !it.archived }.firstOrNull()
    }

    // Schedule helpers (moved here too for domain purity)
    fun getActiveSchedule(data: AppData): Schedule? =
        data.activeScheduleId?.let { id -> data.schedules.find { it.id == id } }

    fun isScheduleApplicableToday(data: AppData, schedule: Schedule): Boolean {
        val today = LocalDate.now().toString()
        if (schedule.specificDates.isNotEmpty()) {
            return today in schedule.specificDates
        }
        val dow = LocalDate.now().dayOfWeek.value  // 1=Mon ... 7=Sun in java.time
        return dow in schedule.weekdays
    }

    private fun parseTimeToMinutes(hhmm: String): Int {
        val parts = hhmm.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return h * 60 + m
    }

    /** Pure function to reorder/move a habit between or within groups. Returns new immutable list. */
    fun moveHabit(currentHabits: List<Habit>, habitId: String, newGroupId: String, newOrder: Int): List<Habit> {
        val habit = currentHabits.find { it.id == habitId } ?: return currentHabits
        val without = currentHabits.filter { it.id != habitId }
        val updated = habit.copy(groupId = newGroupId, order = newOrder)
        val newList = (without + updated).sortedWith(
            compareBy<Habit> { if (it.groupId == newGroupId) 0 else 1 }.thenBy { it.order }
        )
        // Reindex orders per group
        return newList.groupBy { it.groupId }.flatMap { (_, list) ->
            list.sortedBy { it.order }.mapIndexed { i, h -> h.copy(order = i) }
        }
    }
}

// Backwards-compatible top level functions delegating to object (so existing call sites continue to work with minimal changes)
fun groupHabits(data: AppData): List<Pair<Group, List<Habit>>> = HabitDomain.groupHabits(data)
fun computeStreak(data: AppData): Int = HabitDomain.computeStreak(data)
fun computeDayCompletion(data: AppData, dateStr: String): Float = HabitDomain.computeDayCompletion(data, dateStr)
fun computeLastNDays(data: AppData, n: Int): List<Pair<String, Float>> = HabitDomain.computeLastNDays(data, n)
fun computeGroup7DayAvg(data: AppData, groupId: String): Float = HabitDomain.computeGroup7DayAvg(data, groupId)
fun getCurrentPeriodHint(): String = HabitDomain.getCurrentPeriodHint()
fun resolveThemeColors(data: AppData) = HabitDomain.resolveThemeColors(data)
fun getActiveGroups(data: AppData): List<Group> = HabitDomain.getActiveGroups(data)
fun getActiveHabitsForGroup(data: AppData, groupId: String): List<Habit> = HabitDomain.getActiveHabitsForGroup(data, groupId)
fun getSubGroups(data: AppData, parentId: String): List<Group> = HabitDomain.getSubGroups(data, parentId)
fun countRecentSkips(data: AppData, habitId: String, daysBack: Int = 7): Int = HabitDomain.countRecentSkips(data, habitId, daysBack)
fun getToday(): String = HabitDomain.getToday()
fun resolveCurrentGroup(data: AppData, now: LocalTime = LocalTime.now()): Group? = HabitDomain.resolveCurrentGroup(data, now)
fun moveHabit(currentHabits: List<Habit>, habitId: String, newGroupId: String, newOrder: Int): List<Habit> =
    HabitDomain.moveHabit(currentHabits, habitId, newGroupId, newOrder)

// Note: repo still exposes getToday() and resolve* for convenience + ThemeColors type lives in repo temporarily.
