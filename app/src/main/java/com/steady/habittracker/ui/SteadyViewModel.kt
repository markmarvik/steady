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
import com.steady.habittracker.data.withPermanentlyDeletedArchivedHabits
import com.steady.habittracker.data.withPermanentlyDeletedHabit
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
import com.steady.habittracker.data.withCaptureTrashed
import com.steady.habittracker.data.withCaptureRestored
import com.steady.habittracker.data.withoutCapturePermanent
import com.steady.habittracker.data.withPurgedExpiredTrash
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
import com.steady.habittracker.data.NotificationPrefs
import com.steady.habittracker.data.withNotificationPrefs
import com.steady.habittracker.data.withAutoLogMasterEnabled
import com.steady.habittracker.data.AutoSuggestion
import com.steady.habittracker.reminders.AlarmScheduler
import com.steady.habittracker.data.SleepAudioPrefs
import com.steady.habittracker.data.withSleepAudioPrefs
import com.steady.habittracker.data.withLocalWebPrefs
import com.steady.habittracker.data.withPomodoroPrefs
import com.steady.habittracker.data.CapturePrefs
import com.steady.habittracker.data.GrokPreset
import com.steady.habittracker.data.defaultLogNote
import com.steady.habittracker.data.withCapturePrefs
import com.steady.habittracker.data.withGadgetbridgePrefs
import com.steady.habittracker.data.withDayStartHour
import com.steady.habittracker.data.withTodayGridColumns
import com.steady.habittracker.data.withUpsertedGrokPreset
import com.steady.habittracker.data.withoutGrokPreset
import com.steady.habittracker.data.withGrokPresets
import com.steady.habittracker.data.ExtensionCatalog
import com.steady.habittracker.data.ExtensionType
import com.steady.habittracker.data.GadgetbridgePrefs
import com.steady.habittracker.data.OralHygieneBlock
import com.steady.habittracker.data.OralHygienePrefs
import com.steady.habittracker.reminders.NotificationHelper
import com.steady.habittracker.sensors.AutoLogEngine
import com.steady.habittracker.sensors.AutoLogWorker
import com.steady.habittracker.sensors.gadgetbridge.GadgetbridgeImporter
import com.steady.habittracker.sensors.gadgetbridge.GadgetbridgeWorker
import com.steady.habittracker.sleepaudio.ChargingStatus
import com.steady.habittracker.sleepaudio.SleepAudioScheduler
import com.steady.habittracker.sleepaudio.SleepAudioService
import com.steady.habittracker.sleepaudio.SleepAudioStorage
import com.steady.habittracker.widget.WidgetRenderer
import android.app.Application
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class SteadyViewModel(
    private val repository: AndroidHabitRepository,
    application: Application? = null
) : ViewModel() {

    private val appContext = application?.applicationContext

    /**
     * Cold-start session: [ready] is false until DataStore emits once.
     * Collect this as a single state so UI never sees ready=true with the default
     * onboarded=false placeholder (welcome flash on launch).
     */
    data class LoadSession(val ready: Boolean = false, val data: AppData = AppData())

    private val _loadSession = MutableStateFlow(LoadSession())
    val loadSession: StateFlow<LoadSession> = _loadSession.asStateFlow()

    /** True once prefs have been read at least once. */
    val dataReady: StateFlow<Boolean> = _loadSession
        .map { it.ready }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val appData: StateFlow<AppData> = _loadSession
        .map { it.data }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppData())

    init {
        // Start reading prefs immediately (before first compose frame when possible).
        viewModelScope.launch {
            repository.appDataFlow.collect { data ->
                _loadSession.value = LoadSession(ready = true, data = data)
            }
        }
    }

    /** Last extension log summary for snackbar / UI (#33). */
    val lastExtensionSummary = MutableStateFlow("")
    val pendingOpenCapture = MutableStateFlow(false)
    /** Tags to pre-select when [pendingOpenCapture] fires (e.g. Check-in). */
    val pendingCapturePresetTags = MutableStateFlow<List<String>>(emptyList())
    val pendingOpenWorkout = MutableStateFlow(false)
    val pendingOpenSleepReview = MutableStateFlow(false)

    fun consumeExtensionUiHints() {
        pendingOpenCapture.value = false
        pendingCapturePresetTags.value = emptyList()
        pendingOpenWorkout.value = false
        pendingOpenSleepReview.value = false
        lastExtensionSummary.value = ""
    }

    // Logical Steady day (respects dayStartHour, default 4am)
    val today: String get() = HabitDomain.logicalToday(appData.value)

    val completionRate: StateFlow<Float> = appData
        .map { data -> repository.computeDayCompletion(data, HabitDomain.logicalToday(data)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f)

    val todayEntries: StateFlow<Map<String, HabitEntry>> = appData
        .map { data -> data.entries[HabitDomain.logicalToday(data)] ?: emptyMap() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val streak: StateFlow<Int> = appData
        .map { repository.computeStreak(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Live Steady points for today (derived from entries). */
    val todayPoints: StateFlow<Int> = appData
        .map { HabitDomain.computeDayPoints(it, HabitDomain.logicalToday(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val momentumLevel: StateFlow<Int> = appData
        .map {
            HabitDomain.computeLevel(
                HabitDomain.effectiveLifetimePoints(it, HabitDomain.logicalToday(it))
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1)

    val lifetimePoints: StateFlow<Int> = appData
        .map { HabitDomain.effectiveLifetimePoints(it, HabitDomain.logicalToday(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val pointsToNextLevel: StateFlow<Int> = appData
        .map {
            HabitDomain.pointsToNextLevel(
                HabitDomain.effectiveLifetimePoints(it, HabitDomain.logicalToday(it))
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitDomain.POINTS_PER_LEVEL)

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
            val habit = current.habits.find { it.id == habitId }
            val entry = HabitEntry(
                value = value,
                note = note.trim(),
                loggedAt = System.currentTimeMillis()
            )
            var updated = current.withUpdatedEntry(date, habitId, entry)
            // Special habit block side-effects + chainAfter children (#33+)
            if (habit != null && value >= 0.5) {
                val ctx = appContext
                if (ctx != null) {
                    val result = com.steady.habittracker.extensions.ExtensionManager.onHabitLogged(
                        ctx, updated, habit, entry, date
                    )
                    updated = result.data
                    if (result.summaryNote.isNotBlank()) lastExtensionSummary.value = result.summaryNote
                    if (result.openCapture) {
                        pendingCapturePresetTags.value =
                            result.capturePresetTags.ifEmpty {
                                listOf(com.steady.habittracker.data.CaptureTags.CHECKIN)
                            }
                        pendingOpenCapture.value = true
                    }
                    if (result.openWorkout) pendingOpenWorkout.value = true
                    if (result.openSleepReview) pendingOpenSleepReview.value = true
                }
            }
            repository.saveData(updated)
            refreshWidget(updated)
            if (habit?.habitReminder?.enabled == true) {
                rescheduleReminders(updated)
            }
        }
    }

    fun toggleCheckbox(habitId: String) {
        val current = appData.value
        val habit = current.habits.find { it.id == habitId }
        val currentEntry = current.entries[today]?.get(habitId)
        val newVal = if ((currentEntry?.value ?: 0.0) >= 0.5) 0.0 else 1.0
        val note = when {
            newVal < 0.5 -> currentEntry?.note.orEmpty()
            !currentEntry?.note.isNullOrBlank() -> currentEntry!!.note
            else -> habit?.defaultLogNote().orEmpty()
        }
        logEntry(habitId, newVal, note)
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
        // Keep home-screen widget in lockstep with in-app toggles / edits (#28)
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
        why: String = "",
        description: String = "",
        additionalGroupIds: List<String> = emptyList(),
        icon: String = "",
        extensionType: com.steady.habittracker.data.ExtensionType = com.steady.habittracker.data.ExtensionType.NONE,
        extensionConfig: com.steady.habittracker.data.ExtensionConfig = com.steady.habittracker.data.ExtensionConfig(),
        habitReminder: com.steady.habittracker.data.HabitReminderPrefs = com.steady.habittracker.data.HabitReminderPrefs()
    ) {
        if (name.isBlank() || groupId.isBlank()) return
        viewModelScope.launch {
            val current = appData.value
            val order = current.habits.filter { it.groupId == groupId }.size
            val todayStr = today
            val tagList = tags.toMutableList()
            if (isSupplement && TagIds.SUPPLEMENTS !in tagList) tagList.add(TagIds.SUPPLEMENTS)
            val desc = description.trim().ifBlank { why.trim() }
            val newHabit = Habit(
                id = "h_${UUID.randomUUID().toString().take(8)}",
                name = name.trim(),
                why = why.trim(),
                description = desc,
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
                specificDates = specificDates,
                additionalGroupIds = additionalGroupIds.filter { it != groupId && it.isNotBlank() }.distinct(),
                icon = icon.trim(),
                extensionType = extensionType,
                extensionConfig = extensionConfig,
                habitReminder = habitReminder
            )
            val updated = current.withAddedHabit(newHabit)
            repository.saveData(updated)
            refreshWidget(updated)
            if (habitReminder.enabled) rescheduleReminders(updated)
        }
    }

    /** Create a special block from [ExtensionCatalog] template into a group (#33, #37). */
    fun addExtensionBlock(
        type: com.steady.habittracker.data.ExtensionType,
        groupId: String? = null,
        nameOverride: String? = null
    ) {
        val template = com.steady.habittracker.data.ExtensionCatalog.templateFor(type) ?: return
        val gid = groupId
            ?: com.steady.habittracker.data.ExtensionCatalog.suggestGroupId(appData.value, template.suggestTimeHint)
            ?: return
        addHabit(
            name = nameOverride ?: template.defaultName,
            groupId = gid,
            type = HabitType.CHECKBOX,
            icon = template.defaultIcon,
            extensionType = type,
            extensionConfig = template.defaultConfig
        )
    }

    fun updateLocalWebPrefs(prefs: com.steady.habittracker.data.LocalWebPrefs) {
        viewModelScope.launch {
            val current = appData.value
            val prev = current.localWebPrefs
            // Trusted Wi‑Fi auto-start requires a secure PIN (min 4 chars)
            var next = prefs
            if (next.autoStartOnTrustedWifi && !next.pinIsSecure()) {
                next = next.copy(autoStartOnTrustedWifi = false)
            }
            next = next.copy(
                trustedSsids = next.trustedSsids.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            )
            val ctx = appContext
            val onTrusted = ctx != null &&
                com.steady.habittracker.web.WifiWebMonitor.isOnTrustedWifi(ctx, next)
            val mins = next.effectiveAutoOffMinutes(onTrusted)
            // Stamp / clear auto-off deadline when enabling or changing duration
            val stamped = when {
                !next.enabled -> next.copy(autoOffAtEpochMs = 0L, autoStartedByWifi = false)
                next.enabled && (
                    !prev.enabled ||
                        next.autoOffMinutes != prev.autoOffMinutes ||
                        next.trustedWifiAutoOffMinutes != prev.trustedWifiAutoOffMinutes ||
                        next.autoOffAtEpochMs <= 0L
                    ) -> {
                    val deadline = if (mins > 0) {
                        System.currentTimeMillis() + mins * 60_000L
                    } else {
                        0L
                    }
                    next.copy(autoOffAtEpochMs = deadline)
                }
                else -> next
            }
            val updated = current.withLocalWebPrefs(stamped)
            repository.saveData(updated)
            if (ctx != null) {
                com.steady.habittracker.web.LocalWebServer.setEnabled(ctx, updated)
                com.steady.habittracker.web.WifiWebMonitor.start(ctx)
            }
        }
    }

    fun updatePomodoroPrefs(prefs: com.steady.habittracker.data.PomodoroPrefs) {
        viewModelScope.launch {
            val current = appData.value
            repository.saveData(current.withPomodoroPrefs(prefs))
        }
    }

    fun updateCapturePrefs(prefs: CapturePrefs) {
        viewModelScope.launch {
            val current = appData.value
            repository.saveData(current.withCapturePrefs(prefs))
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

    /**
     * Reorder within a timeline group list. Pass [withinGroupId] so multi-group
     * habits reorder among peers in that group (not only primary-group siblings).
     */
    fun reorderHabit(habitId: String, direction: Int, withinGroupId: String? = null) {
        viewModelScope.launch {
            val current = appData.value
            val habit = current.habits.find { it.id == habitId } ?: return@launch
            val groupId = withinGroupId ?: habit.groupId
            val newHabits = HabitDomain.reorderWithinGroup(current.habits, habitId, groupId, direction)
            if (newHabits === current.habits) return@launch
            val updated = current.copy(habits = newHabits)
            repository.saveData(updated)
            refreshWidget(updated)
        }
    }

    fun moveHabitToGroup(habitId: String, newGroupId: String) {
        viewModelScope.launch {
            val current = appData.value
            val newOrder = current.habits.count {
                !it.archived && HabitDomain.belongsToGroup(it, newGroupId)
            }
            val newHabits = com.steady.habittracker.data.moveHabit(current.habits, habitId, newGroupId, newOrder)
            val updated = current.copy(habits = newHabits)
            repository.saveData(updated)
            refreshWidget(updated)
        }
    }

    /** Attach existing habit to a group (no-op if already a member). */
    fun addHabitToGroup(habitId: String, groupId: String) {
        viewModelScope.launch {
            val current = appData.value
            val habit = current.habits.find { it.id == habitId } ?: return@launch
            val next = HabitDomain.addToGroup(habit, groupId)
            if (next == habit) return@launch
            val updated = current.withHabit(next)
            repository.saveData(updated)
            refreshWidget(updated)
        }
    }

    /** Detach habit from group; refuses if it would leave zero groups. */
    fun removeHabitFromGroup(habitId: String, groupId: String) {
        viewModelScope.launch {
            val current = appData.value
            val habit = current.habits.find { it.id == habitId } ?: return@launch
            val next = HabitDomain.removeFromGroup(habit, groupId) ?: return@launch
            val updated = current.withHabit(next)
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
            if (updated.habitReminder.enabled || existing?.habitReminder?.enabled == true) {
                rescheduleReminders(newData)
            }
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

    /**
     * Hard-delete an archived habit (and its log history) to clean the data store.
     * No-ops if the habit is still active — archive first.
     */
    fun permanentlyDeleteHabit(habitId: String) {
        viewModelScope.launch {
            val current = appData.value
            val habit = current.habits.find { it.id == habitId } ?: return@launch
            if (!habit.archived) return@launch
            val newData = current.withPermanentlyDeletedHabit(habitId)
            repository.saveData(newData)
            refreshWidget(newData)
            if (habit.habitReminder.enabled) rescheduleReminders(newData)
        }
    }

    /** Hard-delete every archived habit (history included). */
    fun permanentlyDeleteAllArchivedHabits() {
        viewModelScope.launch {
            val current = appData.value
            if (current.habits.none { it.archived }) return@launch
            val newData = current.withPermanentlyDeletedArchivedHabits()
            repository.saveData(newData)
            refreshWidget(newData)
            rescheduleReminders(newData)
        }
    }

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
    fun addGroup(name: String, timeHint: String = "ANY", parentId: String? = null, icon: String = "") {
        if (name.isBlank()) return
        viewModelScope.launch {
            val current = appData.value
            val newG = Group(
                id = "g_${UUID.randomUUID().toString().take(8)}",
                name = name.trim(),
                timeHint = timeHint,
                order = current.groups.size,
                parentId = parentId,
                icon = icon.trim()
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

    fun updateNotificationPrefs(prefs: NotificationPrefs) {
        viewModelScope.launch {
            val current = appData.value
            val updated = current.withNotificationPrefs(prefs)
            repository.saveData(updated)
            rescheduleReminders(updated)
        }
    }

    /** Bank past days into Momentum history when the calendar day rolls. */
    fun ensureScoreFinalized() {
        viewModelScope.launch {
            val current = appData.value
            val finalized = HabitDomain.withFinalizedScoreHistory(current, today)
            if (finalized.score != current.score) {
                repository.saveData(finalized)
            }
        }
    }

    /**
     * Replace all app data with a full JSON backup (Export Backup format).
     * Restores Momentum score, notification prefs, habits, entries, etc.
     * @return null on success, error message on failure
     */
    fun importBackupJson(jsonString: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                // Full AppData restore (all settings, prefs, trash, extensions, routines, …)
                val restored = repository.decodeBackupJson(jsonString).withPurgedExpiredTrash()
                repository.saveData(restored)
                rescheduleReminders(restored)
                refreshWidget(restored)
                val ctx = appContext
                if (ctx != null) {
                    com.steady.habittracker.web.LocalWebServer.setEnabled(ctx, restored)
                    if (restored.autoLogMasterEnabled) {
                        AutoLogWorker.enqueue(ctx)
                    }
                    SleepAudioScheduler.reschedule(ctx, restored)
                    com.steady.habittracker.sensors.gadgetbridge.GadgetbridgeWorker
                        .syncSchedule(ctx, restored)
                }
                onResult(null)
            } catch (e: Exception) {
                onResult(e.message ?: "Invalid backup file")
            }
        }
    }

    fun setAutoLogMasterEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = appData.value
            val updated = current.withAutoLogMasterEnabled(enabled)
            repository.saveData(updated)
            val ctx = appContext
            if (ctx != null) {
                if (enabled) AutoLogWorker.enqueue(ctx) else AutoLogWorker.cancel(ctx)
            }
        }
    }

    /** Run sensor / external auto-log once (Today refresh or Planner “Sync now”). */
    fun runAutoLogNow(onDone: ((applied: Int, suggested: Int) -> Unit)? = null) {
        viewModelScope.launch {
            val ctx = appContext ?: return@launch
            val current = appData.value
            val result = AutoLogEngine.run(ctx, current, today)
            if (result.data != current) {
                repository.saveData(result.data)
                refreshWidget(result.data)
            }
            onDone?.invoke(result.appliedCount, result.suggestedCount)
        }
    }

    fun acceptAutoSuggestion(suggestion: AutoSuggestion) {
        viewModelScope.launch {
            val updated = AutoLogEngine.acceptSuggestion(appData.value, suggestion)
            repository.saveData(updated)
            refreshWidget(updated)
        }
    }

    fun dismissAutoSuggestion(suggestion: AutoSuggestion) {
        viewModelScope.launch {
            val updated = AutoLogEngine.dismissSuggestion(appData.value, suggestion)
            repository.saveData(updated)
        }
    }

    fun ensureAutoLogScheduled() {
        val ctx = appContext ?: return
        if (appData.value.autoLogMasterEnabled) {
            AutoLogWorker.enqueue(ctx)
        }
        SleepAudioScheduler.reschedule(ctx, appData.value)
        GadgetbridgeWorker.syncSchedule(ctx, appData.value)
    }

    fun updateOralHygienePrefs(prefs: OralHygienePrefs) {
        viewModelScope.launch {
            val applied = OralHygieneBlock.apply(appData.value, prefs)
            repository.saveData(applied)
            refreshWidget(applied)
        }
    }

    fun enableOralHygieneBlock() {
        viewModelScope.launch {
            val current = appData.value
            var p = current.oralHygienePrefs.copy(enabled = true)
            // First enable: brush + floss + tongue by default
            if (!p.brush && !p.floss && !p.tongueScrape && !p.waterFlush && !p.mouthwash) {
                p = p.copy(brush = true, floss = true, tongueScrape = true)
            }
            val applied = OralHygieneBlock.apply(current, p)
            repository.saveData(applied)
            refreshWidget(applied)
        }
    }

    fun disableOralHygieneBlock() {
        viewModelScope.launch {
            val current = appData.value
            val applied = OralHygieneBlock.apply(
                current,
                current.oralHygienePrefs.copy(enabled = false)
            )
            repository.saveData(applied)
            refreshWidget(applied)
        }
    }

    fun updateGadgetbridgePrefs(prefs: GadgetbridgePrefs) {
        viewModelScope.launch {
            val current = appData.value
            val merged = current.gadgetbridgePrefs.mergePreservingPaths(prefs)
            val updated = current.withGadgetbridgePrefs(merged)
            repository.saveData(updated)
            appContext?.let { GadgetbridgeWorker.syncSchedule(it, updated) }
        }
    }

    /**
     * Enable Gadgetbridge from a picked file/URI in one atomic save (habit + prefs).
     * Validates SQLite + Gadgetbridge schema; refuses to stay on if invalid.
     */
    fun enableGadgetbridgeFromLocation(
        location: String,
        onResult: (error: String?) -> Unit = {}
    ) {
        viewModelScope.launch {
            val ctx = appContext
            if (ctx == null) {
                onResult("No context")
                return@launch
            }
            val loc = location.trim()
            if (loc.isBlank()) {
                onResult("Choose a Gadgetbridge export file")
                return@launch
            }
            GadgetbridgeImporter.takePersistableReadPermission(ctx, loc)
            val validation = GadgetbridgeImporter.validateLocation(ctx, loc)
            if (!validation.ok) {
                val current = appData.value
                val failed = current.gadgetbridgePrefs.copy(
                    enabled = false,
                    exportLocation = loc,
                    exportDisplayName = GadgetbridgeImporter.displayNameForUri(ctx, loc),
                    lastError = validation.message,
                    lastStatus = "Validation failed",
                    schemaValidatedAt = 0L
                )
                repository.saveData(current.withGadgetbridgePrefs(failed))
                onResult(validation.message)
                return@launch
            }

            val display = GadgetbridgeImporter.displayNameForUri(ctx, loc)
            var data = appData.value.withGadgetbridgePrefs(
                appData.value.gadgetbridgePrefs.copy(
                    enabled = true,
                    showHistoryFrames = true,
                    exportLocation = loc,
                    exportDisplayName = display,
                    schemaValidatedAt = System.currentTimeMillis(),
                    lastError = "",
                    lastStatus = validation.message
                )
            )
            data = ensureGadgetbridgeHabit(data)

            repository.saveData(data)
            refreshWidget(data)
            GadgetbridgeWorker.syncSchedule(ctx, data)
            val imported = GadgetbridgeImporter.importIfNeeded(ctx, data, force = true)
            if (imported.data != data) {
                repository.saveData(imported.data)
                refreshWidget(imported.data)
            }
            onResult(null)
        }
    }

    /** Turn off Gadgetbridge prefs and archive block habits in one write. */
    fun disableGadgetbridgeBlock() {
        viewModelScope.launch {
            val data = appData.value
            val archived = data.habits.map { h ->
                if (!h.archived && h.extensionType == ExtensionType.GADGETBRIDGE_SYNC) {
                    h.copy(archived = true)
                } else {
                    h
                }
            }
            val next = data.copy(
                habits = archived,
                gadgetbridgePrefs = data.gadgetbridgePrefs.copy(
                    enabled = false,
                    lastStatus = "Disabled"
                )
            )
            repository.saveData(next)
            refreshWidget(next)
            appContext?.let { GadgetbridgeWorker.cancel(it) }
        }
    }

    fun runGadgetbridgeSyncNow(onDone: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            val ctx = appContext ?: return@launch
            val current = appData.value
            val loc = current.gadgetbridgePrefs.exportLocation
            if (loc.isBlank()) {
                onDone?.invoke("Choose a Gadgetbridge export file first")
                return@launch
            }
            val validation = GadgetbridgeImporter.validateLocation(ctx, loc)
            if (!validation.ok) {
                repository.saveData(
                    current.withGadgetbridgePrefs(
                        current.gadgetbridgePrefs.copy(
                            lastError = validation.message,
                            lastStatus = "Validation failed"
                        )
                    )
                )
                onDone?.invoke(validation.message)
                return@launch
            }
            val base = current.withGadgetbridgePrefs(
                current.gadgetbridgePrefs.copy(
                    enabled = true,
                    showHistoryFrames = true,
                    schemaValidatedAt = System.currentTimeMillis(),
                    lastError = "",
                    lastStatus = validation.message
                )
            )
            val result = GadgetbridgeImporter.importIfNeeded(ctx, base, force = true)
            if (result.data != current) {
                repository.saveData(result.data)
                refreshWidget(result.data)
            }
            if (result.events.isNotEmpty() && result.data.gadgetbridgePrefs.notifyEvents) {
                val top = result.events.take(4)
                val title = if (result.events.size == 1) {
                    top.first().title
                } else {
                    "Wearable updates (${result.events.size})"
                }
                NotificationHelper.showReminder(
                    context = ctx,
                    title = title,
                    text = top.joinToString("\n") { "• ${it.body}" },
                    notificationId = 8840
                )
            }
            GadgetbridgeWorker.syncSchedule(ctx, result.data)
            onDone?.invoke(result.message.ifBlank { validation.message })
        }
    }

    /** Ensure a Wearable Sync habit exists for the Today blocks strip (single write helper). */
    private fun ensureGadgetbridgeHabit(data: AppData): AppData {
        if (data.habits.any { !it.archived && it.extensionType == ExtensionType.GADGETBRIDGE_SYNC }) {
            return data
        }
        val template = ExtensionCatalog.templateFor(ExtensionType.GADGETBRIDGE_SYNC) ?: return data
        val gid = ExtensionCatalog.suggestGroupId(data, template.suggestTimeHint) ?: return data
        val order = data.habits.count { it.groupId == gid }
        return data.withAddedHabit(
            Habit(
                id = "h_${UUID.randomUUID().toString().take(8)}",
                name = template.defaultName,
                groupId = gid,
                type = HabitType.CHECKBOX,
                order = order,
                icon = template.defaultIcon,
                extensionType = ExtensionType.GADGETBRIDGE_SYNC,
                extensionConfig = template.defaultConfig
            )
        )
    }

    fun updateSleepAudioPrefs(prefs: SleepAudioPrefs) {
        viewModelScope.launch {
            val current = appData.value
            var updated = current.withSleepAudioPrefs(prefs)
            val ctx = appContext
            if (ctx != null) {
                updated = updated.copy(
                    sleepNights = SleepAudioStorage.prune(ctx, updated.sleepNights, prefs)
                )
            }
            repository.saveData(updated)
            if (ctx != null) SleepAudioScheduler.reschedule(ctx, updated)
        }
    }

    /**
     * @return null if started (or start requested), else a short reason (e.g. not charging).
     */
    fun startSleepAudioRecording(): String? {
        val ctx = appContext ?: return "No context"
        val prefs = appData.value.sleepAudioPrefs
        if (prefs.requireCharging && !ChargingStatus.isCharging(ctx)) {
            return "Plug in the phone — charging is required for sleep audio"
        }
        SleepAudioService.start(ctx)
        return null
    }

    fun stopSleepAudioRecording() {
        val ctx = appContext ?: return
        SleepAudioService.stop(ctx)
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

    /**
     * Finish first-run onboarding: apply daily schedule, commit drafted habits, accent, mark onboarded.
     * Clean slate is allowed — empty [drafts] still completes onboarding.
     */
    fun completeOnboardingWithHabits(
        drafts: List<HabitDraft>,
        colorScheme: String = "default",
        schedule: ScheduleDraft = ScheduleDraft(),
        backgroundMode: String = "dark"
    ) {
        viewModelScope.launch {
            var current = appData.value

            // —— Schedule: sleep spine + work (+ optional exercise) middle blocks ——
            val wake = normalizeOnboardingTime(schedule.wakeTime)
            val bed = normalizeOnboardingTime(schedule.bedTime)
            val workStart = normalizeOnboardingTime(schedule.workStart)
            val workEnd = normalizeOnboardingTime(schedule.workEnd)
            val exStart = normalizeOnboardingTime(schedule.exerciseStart)
            val exEnd = normalizeOnboardingTime(schedule.exerciseEndTime())

            current = HabitDomain.ensureSleepLinkedGroups(
                current.withSleep(
                    current.sleep.copy(
                        wakeTime = wake,
                        bedTime = bed,
                        morningMinutes = schedule.morningMinutes.coerceIn(30, 180),
                        windDownMinutes = schedule.windDownMinutes.coerceIn(15, 180)
                    )
                )
            )

            // Ensure Focus / Work group
            var workGroupId = current.groups.firstOrNull {
                !it.archived && (
                    it.timeHint == "WORK" ||
                        it.name.contains("focus", true) ||
                        it.name.contains("work", true)
                    ) &&
                    !it.name.contains("exercise", true)
            }?.id
            if (workGroupId == null) {
                val g = Group("g_focus", "Focus & Work", "WORK", order = 1, icon = "🎯")
                current = current.withAddedGroup(g)
                workGroupId = g.id
            }

            // Optional Exercise group
            var exerciseGroupId: String? = null
            if (schedule.includeExercise) {
                exerciseGroupId = current.groups.firstOrNull {
                    !it.archived && (
                        it.id == "g_exercise" ||
                            it.name.contains("exercise", true) ||
                            it.name.contains("movement", true) ||
                            it.name.contains("workout", true)
                        )
                }?.id
                if (exerciseGroupId == null) {
                    val g = Group("g_exercise", "Exercise", "WORK", order = 2, icon = "💪")
                    current = current.withAddedGroup(g)
                    exerciseGroupId = g.id
                }
            }

            val s = current.sleep
            val morn = s.morningGroupId ?: return@launch
            val bedG = s.bedtimeGroupId ?: return@launch
            val sleepG = s.sleepGroupId ?: return@launch

            val middle = buildList {
                add(
                    TimeBlock(
                        start = workStart,
                        end = workEnd,
                        groupId = workGroupId!!,
                        color = 0xFF3B82F6.toInt()
                    )
                )
                if (schedule.includeExercise && exerciseGroupId != null) {
                    add(
                        TimeBlock(
                            start = exStart,
                            end = exEnd,
                            groupId = exerciseGroupId,
                            color = 0xFF22C55E.toInt()
                        )
                    )
                }
            }
            val blocks = HabitDomain.buildSleepAnchoredBlocks(s, morn, bedG, sleepG, middle)
            val scheduleId = current.activeScheduleId ?: "s_sleep"
            val sched = current.schedules.find { it.id == scheduleId }?.copy(
                name = "My day",
                timeBlocks = blocks,
                enabled = true
            ) ?: Schedule(id = scheduleId, name = "My day", timeBlocks = blocks)
            current = if (current.schedules.any { it.id == sched.id }) {
                current.withUpdatedSchedule(sched)
            } else {
                current.withAddedSchedule(sched)
            }
            current = current.withActiveSchedule(sched.id)
            current = HabitDomain.withRemindersAlignedToSchedule(current)

            // —— Habits ——
            drafts.forEach { d ->
                if (d.name.isBlank()) return@forEach
                var groupId = d.groupId
                // Resolve placeholder exercise id if group was just created
                if (groupId == "g_exercise" || groupId.isBlank()) {
                    groupId = when {
                        groupId == "g_exercise" && exerciseGroupId != null -> exerciseGroupId
                        groupId.isBlank() -> current.groups.firstOrNull { !it.archived }?.id
                        else -> groupId
                    } ?: return@forEach
                }
                // If exercise was disabled but habit still points at missing group, fall back to work
                if (current.groups.none { it.id == groupId && !it.archived }) {
                    groupId = workGroupId ?: current.groups.firstOrNull { !it.archived }?.id
                        ?: return@forEach
                }
                val order = current.habits.count { it.groupId == groupId && !it.archived }
                val tagList = d.tags.toMutableList()
                if (d.isSupplement && TagIds.SUPPLEMENTS !in tagList) tagList.add(TagIds.SUPPLEMENTS)
                val habit = Habit(
                    id = "h_${UUID.randomUUID().toString().take(8)}",
                    name = d.name.trim(),
                    why = d.why.trim(),
                    groupId = groupId,
                    type = d.type,
                    order = order,
                    canSkip = d.canSkip,
                    isSupplement = d.isSupplement || TagIds.SUPPLEMENTS in tagList,
                    tags = tagList.distinct(),
                    target = d.target,
                    unit = d.unit,
                    icon = d.icon.trim()
                )
                current = current.withAddedHabit(habit)
            }

            if (colorScheme.isNotBlank()) {
                current = current.withColorScheme(colorScheme)
            }
            if (backgroundMode.isNotBlank()) {
                current = current.withBackgroundMode(backgroundMode)
            }
            if (!current.onboarded) current = current.withOnboarded()
            repository.saveData(current)
            refreshWidget(current)
            rescheduleReminders(current)
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

    fun setDayStartHour(hour: Int) {
        viewModelScope.launch {
            val current = appData.value
            repository.saveData(current.withDayStartHour(hour))
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
            val cleanedTags = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            // Inbox only for Ideas / Todo / Reminders (configurable).
            // Memories, Thoughts, Gratitude, etc. are auto-archived to Journal.
            val toInbox = current.capturePrefs.goesToInbox(cleanedTags)
            val cap = com.steady.habittracker.data.CaptureItem(
                id = "c_${UUID.randomUUID().toString().take(8)}",
                title = title.trim(),
                note = note.trim(),
                tags = cleanedTags.ifEmpty {
                    if (toInbox) listOf(com.steady.habittracker.data.CaptureTags.IDEAS) else cleanedTags
                },
                processed = !toInbox
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

    /** Move a closed inbox item back into the open Inbox. */
    fun reopenCaptureToInbox(id: String) {
        viewModelScope.launch {
            val current = appData.value
            val existing = current.captures.find { it.id == id } ?: return@launch
            if (!current.capturePrefs.goesToInbox(existing.tags)) return@launch
            repository.saveData(
                current.withUpdatedCapture(existing.copy(processed = false))
            )
        }
    }

    /** Soft-delete → trash (kept [CapturePrefs.trashRetainDays]). */
    fun deleteCapture(id: String) {
        viewModelScope.launch {
            val current = appData.value.withPurgedExpiredTrash()
            repository.saveData(current.withCaptureTrashed(id))
        }
    }

    fun restoreCapture(id: String) {
        viewModelScope.launch {
            val current = appData.value
            repository.saveData(current.withCaptureRestored(id))
        }
    }

    /** Permanent delete from trash. */
    fun permanentlyDeleteCapture(id: String) {
        viewModelScope.launch {
            val current = appData.value
            repository.saveData(current.withoutCapturePermanent(id))
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            val current = appData.value
            repository.saveData(
                current.copy(captures = current.captures.filter { !it.isTrashed })
            )
        }
    }

    fun updateCapturePrefsAndPurge(prefs: CapturePrefs) {
        viewModelScope.launch {
            val current = appData.value.withCapturePrefs(prefs).withPurgedExpiredTrash()
            repository.saveData(current)
        }
    }

    fun saveGrokPreset(preset: GrokPreset) {
        viewModelScope.launch {
            val current = appData.value
            repository.saveData(current.withUpsertedGrokPreset(preset))
        }
    }

    fun deleteGrokPreset(id: String) {
        viewModelScope.launch {
            val current = appData.value
            repository.saveData(current.withoutGrokPreset(id))
        }
    }

    fun setLastGrokPresetId(id: String?) {
        viewModelScope.launch {
            val current = appData.value
            repository.saveData(current.withGrokPresets(current.grokPresets, lastId = id))
        }
    }

    fun setTodayGridColumns(columns: Int) {
        viewModelScope.launch {
            val current = appData.value
            repository.saveData(current.withTodayGridColumns(columns))
        }
    }

    /** Edit an existing capture in place (title / note / tags). */
    fun updateCapture(id: String, title: String, note: String = "", tags: List<String>? = null) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val current = appData.value
            val existing = current.captures.find { it.id == id } ?: return@launch
            val cleanedTags = (tags ?: existing.tags).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            val toInbox = current.capturePrefs.goesToInbox(cleanedTags)
            val updated = existing.copy(
                title = title.trim(),
                note = note.trim(),
                tags = cleanedTags.ifEmpty { existing.tags },
                // Keep processed state unless tags move it between inbox/journal
                processed = if (toInbox) existing.processed else true
            )
            repository.saveData(current.withUpdatedCapture(updated))
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
