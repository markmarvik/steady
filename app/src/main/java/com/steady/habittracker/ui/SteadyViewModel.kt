package com.steady.habittracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steady.habittracker.data.AndroidHabitRepository
import com.steady.habittracker.data.AppData
import com.steady.habittracker.data.Group
import com.steady.habittracker.data.Habit
import com.steady.habittracker.data.HabitEntry
import com.steady.habittracker.data.HabitType
import com.steady.habittracker.data.Reminder
import com.steady.habittracker.data.Schedule
import com.steady.habittracker.data.TimeBlock
import com.steady.habittracker.data.withActiveSchedule
import com.steady.habittracker.data.withAddedGroup
import com.steady.habittracker.data.withAddedHabit
import com.steady.habittracker.data.withAddedSchedule
import com.steady.habittracker.data.withArchivedGroup
import com.steady.habittracker.data.withArchivedHabit
import com.steady.habittracker.data.withBackgroundMode
import com.steady.habittracker.data.withColorScheme
import com.steady.habittracker.data.withGroup
import com.steady.habittracker.data.withHabit
import com.steady.habittracker.data.withOnboarded
import com.steady.habittracker.data.withReminder
import com.steady.habittracker.data.withRemovedEntry
import com.steady.habittracker.data.withToggledReminder
import com.steady.habittracker.data.withUpdatedEntry
import com.steady.habittracker.data.withUpdatedSchedule
import com.steady.habittracker.data.withoutReminder
import com.steady.habittracker.data.withoutSchedule
import com.steady.habittracker.data.withUnarchivedGroup
import com.steady.habittracker.data.withUnarchivedHabit
import com.steady.habittracker.data.withAddedCapture
import com.steady.habittracker.data.withUpdatedCapture
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class SteadyViewModel(
    private val repository: AndroidHabitRepository
) : ViewModel() {

    val appData: StateFlow<AppData> = repository.appDataFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppData()
        )

    // Today key (yyyy-MM-dd)
    val today: String get() = repository.getToday()

    val completionRate: StateFlow<Float> = appData
        .map { data ->
            val day = data.entries[today] ?: emptyMap()
            val done = day.count { (_, e) -> e.value >= 0.5 }
            if (data.habits.isNotEmpty()) done.toFloat() / data.habits.size else 0f
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f)

    val todayEntries: StateFlow<Map<String, HabitEntry>> = appData
        .map { data -> data.entries[today] ?: emptyMap() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val streak: StateFlow<Int> = appData
        .map { repository.computeStreak(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val currentPeriod: StateFlow<String> = appData
        .map { repository.getCurrentPeriodHint() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "ANY")

    val groupedHabits: StateFlow<List<Pair<Group, List<Habit>>>> = appData
        .map { repository.groupHabits(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Last 7 days overall completion rates (date to rate), for weekly tracker UI. */
    val weeklyRates: StateFlow<List<Pair<String, Float>>> = appData
        .map { repository.computeLastNDays(it, 7) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Per active group: (groupName, 7-day avg rate) for expanded group circles. */
    val groupWeeklyRates: StateFlow<List<Pair<String, Float>>> = appData
        .map { data ->
            repository.getActiveGroups(data).map { g ->
                g.name to repository.computeGroup7DayAvg(data, g.id)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // --- Logging (core action) - now uses immutable helpers (#16) ---
    fun logEntry(habitId: String, value: Double, note: String = "") {
        viewModelScope.launch {
            val current = appData.value
            val entry = HabitEntry(
                value = value,
                note = note.trim(),
                loggedAt = System.currentTimeMillis()
            )
            val updated = current.withUpdatedEntry(today, habitId, entry)
            repository.saveData(updated)
        }
    }

    fun toggleCheckbox(habitId: String) {
        val currentEntry = appData.value.entries[today]?.get(habitId)
        val newVal = if ((currentEntry?.value ?: 0.0) >= 0.5) 0.0 else 1.0
        logEntry(habitId, newVal, currentEntry?.note ?: "")
    }

    fun clearEntry(habitId: String) {
        viewModelScope.launch {
            val current = appData.value
            val updated = current.withRemovedEntry(today, habitId)
            repository.saveData(updated)
        }
    }

    // --- Habit CRUD ---
    fun addHabit(name: String, why: String, groupId: String, type: HabitType = HabitType.CHECKBOX, canSkip: Boolean = true, isSupplement: Boolean = false) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val current = appData.value
            val order = current.habits.filter { it.groupId == groupId }.size
            val newHabit = Habit(
                id = "h_${UUID.randomUUID().toString().take(8)}",
                name = name.trim(),
                why = why.trim(),
                groupId = groupId,
                type = type,
                order = order,
                canSkip = canSkip,
                isSupplement = isSupplement
            )
            repository.saveData(current.withAddedHabit(newHabit))
        }
    }

    fun updateHabit(updated: Habit) {
        viewModelScope.launch {
            val current = appData.value
            repository.saveData(current.withHabit(updated))
        }
    }

    fun deleteHabit(habitId: String) {
        // Archive (soft delete) to preserve history - use immutable helper
        viewModelScope.launch {
            val current = appData.value
            val newData = current.withArchivedHabit(habitId)
            repository.saveData(newData)
        }
    }

    fun archiveHabit(habitId: String) = deleteHabit(habitId) // alias for clarity

    fun skipHabit(habitId: String, reasonNote: String = "Skipped") {
        viewModelScope.launch {
            val current = appData.value
            val entry = HabitEntry(
                value = 0.0,
                note = reasonNote,
                loggedAt = System.currentTimeMillis(),
                skipped = true
            )
            val updated = current.withUpdatedEntry(today, habitId, entry)
            repository.saveData(updated)
        }
    }

    /** Recent skip count for prompt logic (delegates to repo) */
    fun getRecentSkipCount(habitId: String): Int = repository.countRecentSkips(appData.value, habitId)

    fun moveHabit(habitId: String, newGroupId: String, newOrder: Int) {
        viewModelScope.launch {
            val current = appData.value
            // Use pure domain function (#14) for the list transformation - fully immutable result
            val newHabits = com.steady.habittracker.data.moveHabit(current.habits, habitId, newGroupId, newOrder)
            repository.saveData(current.copy(habits = newHabits))
        }
    }

    // --- Group CRUD ---
    fun addGroup(name: String, timeHint: String = "ANY", parentId: String? = null) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val current = appData.value
            val newG = Group(
                id = "g_${UUID.randomUUID().toString().take(8)}",
                name = name.trim(),
                timeHint = timeHint,
                order = current.groups.size,
                parentId = parentId
            )
            repository.saveData(current.withAddedGroup(newG))
        }
    }

    fun updateGroup(group: Group) {
        viewModelScope.launch {
            val current = appData.value
            repository.saveData(current.withGroup(group))
        }
    }

    fun deleteGroup(groupId: String) {
        viewModelScope.launch {
            val current = appData.value
            val newData = current.withArchivedGroup(groupId)
            repository.saveData(newData)
        }
    }

    // --- Reminders ---
    fun setReminder(reminder: Reminder) {
        viewModelScope.launch {
            val current = appData.value
            repository.saveData(current.withReminder(reminder))
        }
    }

    fun deleteReminder(id: String) {
        viewModelScope.launch {
            val current = appData.value
            repository.saveData(current.withoutReminder(id))
        }
    }

    fun toggleReminder(id: String) {
        viewModelScope.launch {
            val current = appData.value
            repository.saveData(current.withToggledReminder(id))
        }
    }

    /** For skip prompt logic */
    fun shouldShowSkipPrompt(habitId: String): Boolean = getRecentSkipCount(habitId) >= 3

    /** Hook for external widget refresh after saves (called from host) */
    fun notifyDataChangedForWidget() {
        // VM owner (MainActivity) will listen or call AppWidgetManager
    }

    // --- Archive restore ---
    fun unarchiveHabit(habitId: String) {
        viewModelScope.launch {
            val current = appData.value
            val newData = repository.unarchiveHabit(current, habitId)
            repository.saveData(newData)
        }
    }

    fun unarchiveGroup(groupId: String) {
        viewModelScope.launch {
            val current = appData.value
            val newData = repository.unarchiveGroup(current, groupId)
            repository.saveData(newData)
        }
    }

    // --- Onboarding + Settings ---
    fun completeOnboarding() {
        viewModelScope.launch {
            val current = appData.value
            if (!current.onboarded) {
                repository.saveData(current.withOnboarded())
            }
        }
    }

    fun setColorScheme(scheme: String) {
        viewModelScope.launch {
            val current = appData.value
            if (current.colorScheme != scheme) {
                repository.saveData(current.withColorScheme(scheme))
            }
        }
    }

    fun setBackgroundMode(mode: String) {
        viewModelScope.launch {
            val current = appData.value
            if (current.backgroundMode != mode) {
                repository.saveData(current.withBackgroundMode(mode))
            }
        }
    }

    // --- Schedules (v5) ---
    fun addSchedule(schedule: Schedule) {
        viewModelScope.launch {
            val current = appData.value
            repository.saveData(current.withAddedSchedule(schedule))
        }
    }

    fun updateSchedule(schedule: Schedule) {
        viewModelScope.launch {
            val current = appData.value
            repository.saveData(current.withUpdatedSchedule(schedule))
        }
    }

    fun deleteSchedule(scheduleId: String) {
        viewModelScope.launch {
            val current = appData.value
            repository.saveData(current.withoutSchedule(scheduleId))
        }
    }

    fun setActiveSchedule(scheduleId: String?) {
        viewModelScope.launch {
            val current = appData.value
            repository.saveData(current.withActiveSchedule(scheduleId))
        }
    }

    // --- Captures (quick inbox #8, #10) ---
    fun addCapture(title: String, note: String = "", tags: List<String> = emptyList()) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val current = appData.value
            val cap = com.steady.habittracker.data.CaptureItem(
                id = "c_${UUID.randomUUID().toString().take(8)}",
                title = title.trim(),
                note = note.trim(),
                tags = tags
            )
            repository.saveData(current.withAddedCapture(cap))
        }
    }

    fun markCaptureProcessed(id: String, linkedHabitId: String? = null) {
        viewModelScope.launch {
            val current = appData.value
            val existing = current.captures.find { it.id == id } ?: return@launch
            val updated = existing.copy(processed = true, linkedHabitId = linkedHabitId)
            repository.saveData(current.withUpdatedCapture(updated))
        }
    }

    /** Convenience preset applier. Creates (or finds) a schedule with given blocks and activates it. */
    fun applySchedulePreset(name: String, blocks: List<TimeBlock>, weekdays: Set<Int> = setOf(1,2,3,4,5,6,7)) {
        viewModelScope.launch {
            val current = appData.value
            val existing = current.schedules.find { it.name == name }
            val schedule = if (existing != null) {
                existing.copy(timeBlocks = blocks, weekdays = weekdays, enabled = true)
            } else {
                Schedule(
                    id = "sch_${UUID.randomUUID().toString().take(8)}",
                    name = name,
                    enabled = true,
                    weekdays = weekdays,
                    timeBlocks = blocks
                )
            }
            var newData = if (existing != null) current.withUpdatedSchedule(schedule) else current.withAddedSchedule(schedule)
            newData = newData.withActiveSchedule(schedule.id)
            repository.saveData(newData)
        }
    }
}
