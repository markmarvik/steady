package com.steady.habittracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steady.habittracker.data.AndroidHabitRepository
import com.steady.habittracker.data.AppData
import com.steady.habittracker.data.Group
import com.steady.habittracker.data.Habit
import com.steady.habittracker.data.HabitDomain
import com.steady.habittracker.data.HabitEntry
import com.steady.habittracker.data.HabitType
import com.steady.habittracker.data.Reminder
import com.steady.habittracker.data.Schedule
import com.steady.habittracker.data.SleepSettings
import com.steady.habittracker.data.Tag
import com.steady.habittracker.data.TagIds
import com.steady.habittracker.data.TimeBlock
import com.steady.habittracker.data.withActiveSchedule
import com.steady.habittracker.data.withAddedGroup
import com.steady.habittracker.data.withAddedHabit
import com.steady.habittracker.data.withAddedSchedule
import com.steady.habittracker.data.withAddedTag
import com.steady.habittracker.data.withArchivedGroup
import com.steady.habittracker.data.withArchivedHabit
import com.steady.habittracker.data.withArchivedTag
import com.steady.habittracker.data.withBackgroundMode
import com.steady.habittracker.data.withColorScheme
import com.steady.habittracker.data.withGroup
import com.steady.habittracker.data.withHabit
import com.steady.habittracker.data.withOnboarded
import com.steady.habittracker.data.withReminder
import com.steady.habittracker.data.withRemovedEntry
import com.steady.habittracker.data.withSleep
import com.steady.habittracker.data.withToggledReminder
import com.steady.habittracker.data.withUpdatedEntry
import com.steady.habittracker.data.withUpdatedSchedule
import com.steady.habittracker.data.withoutReminder
import com.steady.habittracker.data.withoutSchedule
import com.steady.habittracker.data.withUnarchivedGroup
import com.steady.habittracker.data.withUnarchivedHabit
import com.steady.habittracker.data.withAddedCapture
import com.steady.habittracker.data.withoutCapture
import com.steady.habittracker.data.withUpdatedCapture
import com.steady.habittracker.data.withRemindersMasterEnabled
import com.steady.habittracker.data.BlueprintRoutines
import com.steady.habittracker.data.ExerciseRoutine
import com.steady.habittracker.data.WorkoutSession
import com.steady.habittracker.data.withAddedRoutine
import com.steady.habittracker.data.withUpdatedRoutine
import com.steady.habittracker.data.withArchivedRoutine
import com.steady.habittracker.data.withLoggedWorkoutSession
import com.steady.habittracker.data.withBlueprintRoutinesIfMissing
import com.steady.habittracker.data.GoalStory
import com.steady.habittracker.data.PathAlignmentCheck
import com.steady.habittracker.data.withUpdatedGoal
import com.steady.habittracker.data.withArchivedGoal
import com.steady.habittracker.data.withGoalsReplacedFromWizard

import com.steady.habittracker.reminders.AlarmScheduler
import com.steady.habittracker.widget.WidgetRenderer
import android.app.Application
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class SteadyViewModel(
    private val repository: AndroidHabitRepository,
    application: Application? = null
) : ViewModel() {

    private val appContext = application?.applicationContext

    val appData: StateFlow<AppData> = repository.appDataFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppData()
        )

    // Today key (yyyy-MM-dd)
    val today: String get() = repository.getToday()

    val completionRate: StateFlow<Float> = appData
        .map { data -> repository.computeDayCompletion(data, today) }
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

    /** Per tag: (tagName, 7-day avg) — category completion for History. */
    val tagWeeklyRates: StateFlow<List<Triple<String, String, Float>>> = appData
        .map { data ->
            HabitDomain.getActiveTags(data).map { t ->
                Triple(t.id, t.name, repository.computeTag7DayAvg(data, t.id))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // --- Logging (core action) - now uses immutable helpers (#16) ---
    // date param supports backfill for manual metrics (issue #19)
    fun logEntry(habitId: String, value: Double, note: String = "", date: String = today) {
        viewModelScope.launch {
            val current = appData.value
            val entry = HabitEntry(
                value = value,
                note = note.trim(),
                loggedAt = System.currentTimeMillis()
            )
            val updated = current.withUpdatedEntry(date, habitId, entry)
            repository.saveData(updated)
            refreshWidget(updated)
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
            refreshWidget(updated)
        }
    }

    private fun refreshWidget(data: AppData) {
        val ctx = appContext ?: return
        WidgetRenderer.updateAll(ctx, data)
    }

    private fun rescheduleReminders(data: AppData) {
        val ctx = appContext ?: return
        AlarmScheduler.scheduleAll(ctx, data)
    }

    // --- Habit CRUD ---
    fun addHabit(
        name: String,
        groupId: String,
        type: HabitType = HabitType.CHECKBOX,
        canSkip: Boolean = true,
        isSupplement: Boolean = false,
        tags: List<String> = emptyList(),
        showPreset: com.steady.habittracker.data.ShowPreset = com.steady.habittracker.data.ShowPreset.DAILY,
        weekdays: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
        intervalDays: Int = 2,
        specificDates: List<String> = emptyList(),
        why: String = ""
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val current = appData.value
            val order = current.habits.filter { it.groupId == groupId }.size
            val todayStr = today
            val tagList = tags.toMutableList()
            if (isSupplement && TagIds.SUPPLEMENTS !in tagList) tagList.add(TagIds.SUPPLEMENTS)
            val newHabit = Habit(
                id = "h_${UUID.randomUUID().toString().take(8)}",
                name = name.trim(),
                why = why.trim(),
                groupId = groupId,
                type = type,
                order = order,
                canSkip = canSkip,
                isSupplement = isSupplement || TagIds.SUPPLEMENTS in tagList,
                tags = tagList.distinct(),
                showPreset = showPreset,
                weekdays = weekdays,
                intervalDays = intervalDays,
                anchorDate = if (showPreset == com.steady.habittracker.data.ShowPreset.EVERY_N_DAYS) todayStr else null,
                specificDates = specificDates
            )
            val updated = current.withAddedHabit(newHabit)
            repository.saveData(updated)
            refreshWidget(updated)
        }
    }

    fun addTag(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val current = appData.value
            if (current.tags.any { it.name.equals(name.trim(), ignoreCase = true) && !it.archived }) return@launch
            val tag = Tag(
                id = "tag_${UUID.randomUUID().toString().take(8)}",
                name = name.trim(),
                order = current.tags.size
            )
            repository.saveData(current.withAddedTag(tag))
        }
    }

    fun archiveTag(tagId: String) {
        viewModelScope.launch {
            val current = appData.value
            repository.saveData(current.withArchivedTag(tagId))
        }
    }

    fun updateSleepSettings(sleep: SleepSettings) {
        viewModelScope.launch {
            val current = appData.value
            repository.saveData(current.withSleep(sleep))
        }
    }

    /**
     * Apply sleep spine: ensure Morning / Bedtime / Sleep groups exist, rebuild 24h blocks
     * from bed/wake, activate schedule, and align reminder times to the new blocks.
     * [sleep] when non-null is saved first (avoids race with a separate updateSleepSettings call).
     */
    fun applySleepAnchoredSchedule(sleep: SleepSettings? = null) {
        viewModelScope.launch {
            var current = appData.value
            if (sleep != null) current = current.withSleep(sleep)
            current = HabitDomain.ensureSleepLinkedGroups(current)
            val s = current.sleep
            val morn = s.morningGroupId ?: return@launch
            val bed = s.bedtimeGroupId ?: return@launch
            val sleepG = s.sleepGroupId ?: return@launch
            val existing = HabitDomain.getActiveSchedule(current)?.timeBlocks ?: emptyList()
            val blocks = HabitDomain.buildSleepAnchoredBlocks(s, morn, bed, sleepG, existing)
            val scheduleId = current.activeScheduleId ?: "s_sleep"
            val schedule = current.schedules.find { it.id == scheduleId }?.copy(
                name = current.schedules.find { it.id == scheduleId }?.name ?: "Sleep-centered",
                timeBlocks = blocks,
                enabled = true
            ) ?: Schedule(id = scheduleId, name = "Sleep-centered", timeBlocks = blocks)
            current = if (current.schedules.any { it.id == schedule.id }) {
                current.withUpdatedSchedule(schedule)
            } else {
                current.withAddedSchedule(schedule)
            }
            current = current.withActiveSchedule(schedule.id).withSleep(s)
            current = HabitDomain.withRemindersAlignedToSchedule(current)
            repository.saveData(current)
            refreshWidget(current)
            rescheduleReminders(current)
        }
    }

    /** Re-derive all reminder times from the active schedule / sleep spine. */
    fun alignRemindersToSchedule() {
        viewModelScope.launch {
            val current = HabitDomain.withRemindersAlignedToSchedule(appData.value)
            repository.saveData(current)
            rescheduleReminders(current)
        }
    }

    fun reorderHabit(habitId: String, direction: Int) {
        // direction: -1 up, +1 down within group
        viewModelScope.launch {
            val current = appData.value
            val habit = current.habits.find { it.id == habitId } ?: return@launch
            val siblings = current.habits.filter { it.groupId == habit.groupId && !it.archived }.sortedBy { it.order }
            val idx = siblings.indexOfFirst { it.id == habitId }
            val swapIdx = idx + direction
            if (idx < 0 || swapIdx !in siblings.indices) return@launch
            val a = siblings[idx]
            val b = siblings[swapIdx]
            val newHabits = current.habits.map {
                when (it.id) {
                    a.id -> it.copy(order = b.order)
                    b.id -> it.copy(order = a.order)
                    else -> it
                }
            }
            val updated = current.copy(habits = newHabits)
            repository.saveData(updated)
            refreshWidget(updated)
        }
    }

    fun moveHabitToGroup(habitId: String, newGroupId: String) {
        viewModelScope.launch {
            val current = appData.value
            val newOrder = current.habits.count { it.groupId == newGroupId && !it.archived }
            val newHabits = com.steady.habittracker.data.moveHabit(current.habits, habitId, newGroupId, newOrder)
            val updated = current.copy(habits = newHabits)
            repository.saveData(updated)
            refreshWidget(updated)
        }
    }

    fun updateHabit(updated: Habit) {
        viewModelScope.launch {
            val current = appData.value
            val existing = current.habits.find { it.id == updated.id }
            var newData = if (existing != null && existing.groupId != updated.groupId) {
                val newOrder = current.habits.count { it.groupId == updated.groupId && !it.archived && it.id != updated.id }
                val habits = com.steady.habittracker.data.moveHabit(current.habits, updated.id, updated.groupId, newOrder)
                    .map { if (it.id == updated.id) updated.copy(groupId = updated.groupId, order = it.order) else it }
                current.copy(habits = habits)
            } else {
                current.withHabit(updated)
            }
            repository.saveData(newData)
            refreshWidget(newData)
        }
    }

    /** Create ad-hoc metric habit for sporadic logging (e.g. body weight). Creates "Metrics" group if needed. */
    fun addMetricHabit(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            var current = appData.value
            val metricsGroup = current.groups.firstOrNull { it.name.equals("Metrics", ignoreCase = true) || it.name.equals("Health Metrics", ignoreCase = true) }
            val gid = metricsGroup?.id ?: run {
                val newG = com.steady.habittracker.data.Group(
                    id = "g_metrics",
                    name = "Metrics",
                    timeHint = "ANY",
                    order = (current.groups.maxOfOrNull { it.order } ?: 0) + 1
                )
                current = current.withAddedGroup(newG)
                "g_metrics"
            }
            val newH = Habit(
                id = "h_${UUID.randomUUID().toString().take(8)}",
                name = name.trim(),
                why = "Manual / sporadic metric (logged ad-hoc)",
                groupId = gid,
                type = HabitType.COUNTER,
                target = null,
                unit = "",
                order = current.habits.filter { it.groupId == gid }.size
            )
            current = current.withAddedHabit(newH)
            repository.saveData(current)
        }
    }

    fun deleteHabit(habitId: String) {
        // Archive (soft delete) to preserve history - use immutable helper
        viewModelScope.launch {
            val current = appData.value
            val newData = current.withArchivedHabit(habitId)
            repository.saveData(newData)
            refreshWidget(newData)
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
            refreshWidget(updated)
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
            val updated = current.withReminder(reminder)
            repository.saveData(updated)
            rescheduleReminders(updated)
        }
    }

    fun deleteReminder(id: String) {
        viewModelScope.launch {
            val current = appData.value
            val updated = current.withoutReminder(id)
            repository.saveData(updated)
            rescheduleReminders(updated)
        }
    }

    fun toggleReminder(id: String) {
        viewModelScope.launch {
            val current = appData.value
            val updated = current.withToggledReminder(id)
            repository.saveData(updated)
            rescheduleReminders(updated)
        }
    }

    fun setRemindersMasterEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = appData.value
            if (current.remindersMasterEnabled == enabled) return@launch
            val updated = current.withRemindersMasterEnabled(enabled)
            repository.saveData(updated)
            rescheduleReminders(updated)
        }
    }

    /** For skip prompt logic */
    fun shouldShowSkipPrompt(habitId: String): Boolean = getRecentSkipCount(habitId) >= 3

    /** Refresh home-screen widgets from current in-memory data. */
    fun notifyDataChangedForWidget() {
        refreshWidget(appData.value)
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
                val updated = current.withColorScheme(scheme)
                repository.saveData(updated)
                refreshWidget(updated)
            }
        }
    }

    fun setBackgroundMode(mode: String) {
        viewModelScope.launch {
            val current = appData.value
            if (current.backgroundMode != mode) {
                val updated = current.withBackgroundMode(mode)
                repository.saveData(updated)
                refreshWidget(updated)
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

    fun updateScheduleBlocks(scheduleId: String, blocks: List<TimeBlock>) {
        viewModelScope.launch {
            val current = appData.value
            val sched = current.schedules.find { it.id == scheduleId } ?: return@launch
            val updated = sched.copy(timeBlocks = blocks)
            var next = current.withUpdatedSchedule(updated)
            next = HabitDomain.withRemindersAlignedToSchedule(next)
            repository.saveData(next)
            refreshWidget(next)
            rescheduleReminders(next)
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

    fun deleteCapture(id: String) {
        viewModelScope.launch {
            val current = appData.value
            repository.saveData(current.withoutCapture(id))
        }
    }

    // --- Exercise routines (#20–#22) ---
    fun saveRoutine(routine: ExerciseRoutine) {
        viewModelScope.launch {
            val current = appData.value
            val updated = if (current.routines.any { it.id == routine.id }) {
                current.withUpdatedRoutine(routine)
            } else {
                current.withAddedRoutine(routine)
            }
            repository.saveData(updated)
        }
    }

    fun archiveRoutine(routineId: String) {
        viewModelScope.launch {
            repository.saveData(appData.value.withArchivedRoutine(routineId))
        }
    }

    fun loadBlueprintRoutines() {
        viewModelScope.launch {
            val current = appData.value
            repository.saveData(current.withBlueprintRoutinesIfMissing(BlueprintRoutines.templates()))
        }
    }

    /**
     * Persist a finished (or partial) workout session.
     * Optionally upserts a linked habit entry for streaks / daily progress.
     */
    fun finishWorkoutSession(session: WorkoutSession) {
        viewModelScope.launch {
            var current = appData.value
            val finished = session.copy(
                completed = true,
                completedAt = session.completedAt ?: System.currentTimeMillis()
            )
            current = current.withLoggedWorkoutSession(finished)
            val routine = current.routines.find { it.id == finished.routineId }
            val linkedId = routine?.linkedHabitId
            if (linkedId != null && current.habits.any { it.id == linkedId && !it.archived }) {
                val duration = finished.totalDurationMin?.toDouble()
                    ?: ((finished.completedAt!! - finished.startedAt) / 60_000.0).coerceAtLeast(1.0)
                val habit = current.habits.find { it.id == linkedId }
                val value = when (habit?.type) {
                    HabitType.DURATION_MIN -> duration
                    HabitType.COUNTER -> HabitDomain.sessionSetCount(finished).toDouble().coerceAtLeast(1.0)
                    else -> 1.0
                }
                current = current.withUpdatedEntry(
                    finished.date,
                    linkedId,
                    HabitEntry(value = value, note = "Workout: ${routine.name}", loggedAt = finished.completedAt!!)
                )
            }
            repository.saveData(current)
            refreshWidget(current)
        }
    }

    fun saveInProgressWorkoutSession(session: WorkoutSession) {
        viewModelScope.launch {
            repository.saveData(appData.value.withLoggedWorkoutSession(session.copy(completed = false)))
        }
    }

    // --- Dreamline / Path (#25, #26) ---
    fun applyDreamlineGoals(newGoals: List<GoalStory>, replaceExistingDreamline: Boolean = true) {
        if (newGoals.isEmpty()) return
        viewModelScope.launch {
            val current = appData.value
            repository.saveData(
                current.withGoalsReplacedFromWizard(newGoals, replaceDreamline = replaceExistingDreamline)
            )
        }
    }

    fun updateGoal(goal: GoalStory) {
        viewModelScope.launch {
            repository.saveData(appData.value.withUpdatedGoal(goal.copy(updatedAt = System.currentTimeMillis())))
        }
    }

    fun archiveGoal(goalId: String) {
        viewModelScope.launch {
            repository.saveData(appData.value.withArchivedGoal(goalId))
        }
    }

    fun savePathAlignment(check: PathAlignmentCheck) {
        viewModelScope.launch {
            val current = appData.value
            // One primary check-in per day (still keeps prior days)
            val pruned = current.pathChecks.filter { it.date != check.date }
            repository.saveData(current.copy(pathChecks = pruned + check))
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
            newData = HabitDomain.withRemindersAlignedToSchedule(newData)
            repository.saveData(newData)
            refreshWidget(newData)
            rescheduleReminders(newData)
        }
    }
}
