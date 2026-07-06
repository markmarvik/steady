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

    // --- Logging (core action) ---
    fun logEntry(habitId: String, value: Double, note: String = "") {
        viewModelScope.launch {
            val current = appData.value
            val entries = current.entries.toMutableMap()
            val dayMap = entries.getOrPut(today) { emptyMap() }.toMutableMap()
            dayMap[habitId] = HabitEntry(
                value = value,
                note = note.trim(),
                loggedAt = System.currentTimeMillis()
            )
            entries[today] = dayMap
            repository.saveData(current.copy(entries = entries))
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
            val entries = current.entries.toMutableMap()
            val dayMap = entries[today]?.toMutableMap() ?: return@launch
            dayMap.remove(habitId)
            if (dayMap.isEmpty()) entries.remove(today) else entries[today] = dayMap
            repository.saveData(current.copy(entries = entries))
        }
    }

    // --- Habit CRUD ---
    fun addHabit(name: String, why: String, groupId: String, type: HabitType = HabitType.CHECKBOX, canSkip: Boolean = true) {
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
                canSkip = canSkip
            )
            repository.saveData(current.copy(habits = current.habits + newHabit))
        }
    }

    fun updateHabit(updated: Habit) {
        viewModelScope.launch {
            val current = appData.value
            val newHabits = current.habits.map { if (it.id == updated.id) updated else it }
            repository.saveData(current.copy(habits = newHabits))
        }
    }

    fun deleteHabit(habitId: String) {
        // Archive (soft delete) to preserve history
        viewModelScope.launch {
            val current = appData.value
            val newData = repository.archiveHabit(current, habitId)  // from repo helper
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
            val entries = current.entries.toMutableMap()
            val dayMap = entries.getOrPut(today) { emptyMap() }.toMutableMap()
            dayMap[habitId] = entry
            entries[today] = dayMap
            repository.saveData(current.copy(entries = entries))
        }
    }

    /** Recent skip count for prompt logic (delegates to repo) */
    fun getRecentSkipCount(habitId: String): Int = repository.countRecentSkips(appData.value, habitId)

    fun moveHabit(habitId: String, newGroupId: String, newOrder: Int) {
        viewModelScope.launch {
            val current = appData.value
            val habit = current.habits.find { it.id == habitId } ?: return@launch
            val without = current.habits.filter { it.id != habitId }
            val updated = habit.copy(groupId = newGroupId, order = newOrder)
            val newList = (without + updated).sortedWith(compareBy<Habit> { if (it.groupId == newGroupId) 0 else 1 }.thenBy { it.order })
            // reindex orders per group
            val reindexed = newList.groupBy { it.groupId }.flatMap { (gid, list) ->
                list.sortedBy { it.order }.mapIndexed { i, h -> h.copy(order = i) }
            }
            repository.saveData(current.copy(habits = reindexed))
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
            repository.saveData(current.copy(groups = current.groups + newG))
        }
    }

    fun updateGroup(group: Group) {
        viewModelScope.launch {
            val current = appData.value
            repository.saveData(current.copy(groups = current.groups.map { if (it.id == group.id) group else it }))
        }
    }

    fun deleteGroup(groupId: String) {
        viewModelScope.launch {
            val current = appData.value
            val newData = repository.archiveGroup(current, groupId)
            repository.saveData(newData)
        }
    }

    // --- Reminders ---
    fun setReminder(reminder: Reminder) {
        viewModelScope.launch {
            val current = appData.value
            val others = current.reminders.filter { it.id != reminder.id }
            repository.saveData(current.copy(reminders = others + reminder))
        }
    }

    fun deleteReminder(id: String) {
        viewModelScope.launch {
            val current = appData.value
            repository.saveData(current.copy(reminders = current.reminders.filter { it.id != id }))
        }
    }

    fun toggleReminder(id: String) {
        viewModelScope.launch {
            val current = appData.value
            val updated = current.reminders.map {
                if (it.id == id) it.copy(enabled = !it.enabled) else it
            }
            repository.saveData(current.copy(reminders = updated))
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
                repository.saveData(current.copy(onboarded = true))
            }
        }
    }

    fun setColorScheme(scheme: String) {
        viewModelScope.launch {
            val current = appData.value
            if (current.colorScheme != scheme) {
                repository.saveData(current.copy(colorScheme = scheme))
            }
        }
    }
}
