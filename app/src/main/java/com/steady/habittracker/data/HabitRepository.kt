package com.steady.habittracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "steady_prefs")

/**
 * Portable repository contract.
 * Android impl below. Future iOS/KMP can provide another impl using the same interface + models.
 */
interface HabitRepository {
    val appDataFlow: Flow<AppData>
    suspend fun saveData(data: AppData)
}

/**
 * Android implementation using DataStore + JSON.
 * All platform-specific code lives here.
 */
class AndroidHabitRepository(private val context: Context) : HabitRepository {

    private val json = Json { 
        prettyPrint = true 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }
    
    private val DATA_KEY = stringPreferencesKey("app_data")

    // Simple in-memory cache to avoid re-parsing the full (potentially large) JSON blob on every flow emission.
    // Addresses part of #18 perf concern for growing history. Decode only on actual content change.
    private var lastJson: String? = null
    private var cachedParsed: AppData? = null

    override val appDataFlow: Flow<AppData> = context.dataStore.data
        .map { preferences ->
            val jsonString = preferences[DATA_KEY] ?: ""
            val data = if (jsonString.isBlank()) {
                getDefaultData()
            } else if (jsonString == lastJson && cachedParsed != null) {
                cachedParsed!!
            } else {
                try {
                    val parsed = json.decodeFromString<AppData>(jsonString)
                    val migrated = migrateIfNeeded(parsed)
                    lastJson = jsonString
                    cachedParsed = migrated
                    migrated
                } catch (e: Exception) {
                    getDefaultData()
                }
            }
            // Ensure at least defaults if empty
            if (data.habits.isEmpty()) getDefaultData() else data
        }

    override suspend fun saveData(data: AppData) {
        // Update cache immediately so subsequent flow emissions / reads are fast (perf #18)
        val encoded = json.encodeToString(data)
        lastJson = encoded
        cachedParsed = data
        context.dataStore.edit { preferences ->
            preferences[DATA_KEY] = encoded
        }
    }

    /**
     * Migrate older schemas to v5.
     * Adds schedules + activeScheduleId for advanced time scheduling (24h circle, multiple schedules,
     * weekday + specific-date rules, shift presets).
     */
    private fun migrateIfNeeded(data: AppData): AppData {
        if (data.schemaVersion >= 5 && data.groups.isNotEmpty() && data.habits.isNotEmpty()) {
            return data
        }
        // Start from current or fresh
        var d = if (data.groups.isNotEmpty() && data.habits.isNotEmpty()) data else getDefaultData()

        // Upgrade fields
        val upgradedGroups = d.groups.map { g ->
            if (g.archived || g.parentId != null) g else g.copy(archived = false, parentId = null)
        }
        val upgradedHabits = d.habits.map { h ->
            val isHygiene = h.name.contains("teeth", true) || h.name.contains("floss", true) ||
                            h.name.contains("brush", true) || h.name.contains("tongue", true) ||
                            h.name.contains("hygiene", true) || h.name.contains("shower", true)
            h.copy(
                archived = h.archived,
                canSkip = if (isHygiene) false else h.canSkip,
                isSupplement = h.isSupplement
            )
        }
        val upgradedEntries = d.entries.mapValues { (_, m) ->
            m.mapValues { (_, e) -> e.copy(skipped = e.skipped) }
        }

        return d.copy(
            groups = upgradedGroups,
            habits = upgradedHabits,
            entries = upgradedEntries,
            schemaVersion = 5,
            onboarded = if (d.onboarded) d.onboarded else true,
            colorScheme = if (d.colorScheme.isNotBlank()) d.colorScheme else "default",
            backgroundMode = if (d.backgroundMode.isNotBlank()) d.backgroundMode else "dark",
            schedules = d.schedules.ifEmpty { emptyList() },
            activeScheduleId = d.activeScheduleId
        )
    }

    private fun defaultReminders(groups: List<Group>): List<Reminder> = listOf(
        Reminder(
            id = "r_morn",
            groupId = groups.firstOrNull { it.timeHint == "MORNING" }?.id,
            time = "08:30",
            days = setOf(1,2,3,4,5,6,7),
            enabled = true
        ),
        Reminder(
            id = "r_even",
            groupId = groups.firstOrNull { it.timeHint == "EVENING" }?.id,
            time = "21:00",
            days = setOf(1,2,3,4,5,6,7),
            enabled = true
        ),
        Reminder(
            id = "r_review",
            groupId = null,
            time = "21:45",
            days = setOf(1,2,3,4,5,6,7),
            enabled = true
        )
    )

    /**
     * Rich seeded data matching original 7 + real patterns from user's TrackAndGraph (groups, varied types).
     */
    private fun getDefaultData(): AppData {
        val groups = listOf(
            Group("g_morn", "Morning Routine", "MORNING", 0),
            Group("g_focus", "Focus & Work", "WORK", 1),
            Group("g_even", "Evening / Wind Down", "EVENING", 2),
            Group("g_mind", "Mindset & Review", "REVIEW", 3),
            Group("g_supp", "Supplements", "ANY", 4),
            Group("g_workout", "Workouts", "ANY", 5)
            // Subgroups (e.g. plans) can be added with parentId = "g_workout"
        )

        val habits = listOf(
            // Morning (hygiene non-skippable)
            Habit("h_light", "Morning Sunlight / Light exposure", "Aligns circadian rhythm, boosts mood/energy.", "g_morn", HabitType.CHECKBOX, order = 0),
            Habit("h_breathe", "Box Breathing (6 min)", "Calms nervous system before day starts.", "g_morn", HabitType.DURATION_MIN, target = 6.0, unit = "min", order = 1),
            Habit("h_teeth", "Brush + Floss + Tongue scrape", "Dental + overall health foundation.", "g_morn", HabitType.CHECKBOX, order = 2, canSkip = false),
            Habit("h_supp_am", "Morning Supplements (Omega-3, Multi)", "Foundational nutrition.", "g_supp", HabitType.COUNTER, target = 1.0, unit = "", order = 3, isSupplement = true),

            // Focus/Work
            Habit("h_move", "Move Your Body", "20+ min walk or exercise for brain and longevity.", "g_focus", HabitType.CHECKBOX, order = 0),
            Habit("h_protein", "Protein First", "Prioritize protein for muscle, satiety and metabolism.", "g_focus", HabitType.CHECKBOX, order = 1),
            Habit("h_focus", "Deep Focus Block", "Undistracted meaningful work or learning session.", "g_focus", HabitType.DURATION_MIN, target = 60.0, unit = "min", order = 2),
            Habit("h_pull", "Pull-ups / Dips / Squats", "Strength training tracker (reps).", "g_workout", HabitType.COUNTER, target = 5.0, unit = "reps", order = 3),

            // Evening
            Habit("h_wind", "Wind Down Routine", "Protects sleep quality with consistent bedtime.", "g_even", HabitType.CHECKBOX, order = 0),
            Habit("h_hydrate", "Stay Hydrated", "Better energy, focus and recovery.", "g_even", HabitType.COUNTER, target = 2.5, unit = "L", order = 1),
            Habit("h_mg", "Magnesium + Chamomile", "Nervous system + sleep prep.", "g_even", HabitType.CHECKBOX, order = 2, isSupplement = true),
            Habit("h_nSDR", "NSDR / 4-7-8 Breathing", "Non-sleep deep rest for recovery.", "g_even", HabitType.DURATION_MIN, target = 10.0, unit = "min", order = 3),

            // Mindset (journaling)
            Habit("h_grat", "Gratitude / 3 things", "Note things you're grateful for.", "g_mind", HabitType.NOTE, order = 0),
            Habit("h_wins", "Wins today (1-3)", "Celebrate specific progress.", "g_mind", HabitType.NOTE, order = 1),
            Habit("h_reflect", "Hard is Good / Ownership", "Did I lean in? Took responsibility?", "g_mind", HabitType.SCALE_1_5, target = 4.0, unit = "", order = 2),

            // Workout example exercise (user can create more + subgroups)
            Habit("h_bench", "Bench Press", "Workout plan exercise. Log weight x reps.", "g_workout", HabitType.COUNTER, target = 60.0, unit = "kg x reps", order = 4)
        )

        val reminders = defaultReminders(groups)

        return AppData(
            groups = groups,
            habits = habits,
            entries = emptyMap(),
            reminders = reminders,
            schemaVersion = 5,
            onboarded = false,
            colorScheme = "default",
            backgroundMode = "dark",
            schedules = emptyList(),
            activeScheduleId = null
        )
    }

    // --- Convenience helpers used by UI / schedulers (not part of portable interface) ---

    fun getToday(): String = HabitDomain.getToday()

    /** Returns sorted groups + habits in each for Today UI (non-archived only). Delegates to domain. */
    fun groupHabits(data: AppData): List<Pair<Group, List<Habit>>> = HabitDomain.groupHabits(data)

    /** Compute current streak (consecutive days back from today with reasonable completion). */
    fun computeStreak(data: AppData): Int = HabitDomain.computeStreak(data)

    // --- Weekly / completion rate helpers for progress UI (delegated) ---

    /** Overall completion rate (0f..1f) for a specific date key (yyyy-MM-dd). */
    fun computeDayCompletion(data: AppData, dateStr: String): Float = HabitDomain.computeDayCompletion(data, dateStr)

    /** Last N days of overall rates, oldest first. Returns list of (date, rate). */
    fun computeLastNDays(data: AppData, n: Int = 7): List<Pair<String, Float>> = HabitDomain.computeLastNDays(data, n)

    /** 7-day average completion for a specific group (habits belonging to it). */
    fun computeGroup7DayAvg(data: AppData, groupId: String): Float = HabitDomain.computeGroup7DayAvg(data, groupId)

    /** Simple time-of-day group hint for widget / Today highlight. */
    fun getCurrentPeriodHint(): String = HabitDomain.getCurrentPeriodHint()

    /** Simple theme color resolver (used by app + widget). Supports AMOLED. Delegates to domain. */
    fun resolveThemeColors(data: AppData): ThemeColors = HabitDomain.resolveThemeColors(data)

    // --- Active + hierarchy + archive helpers (v3) (delegated where pure) ---

    /** Active (non-archived) groups, top-level first. */
    fun getActiveGroups(data: AppData): List<Group> = HabitDomain.getActiveGroups(data)

    /** Habits for a group (active only). Supports parent for subgroups. */
    fun getActiveHabitsForGroup(data: AppData, groupId: String): List<Habit> = HabitDomain.getActiveHabitsForGroup(data, groupId)

    /** Return nested view for a parent (e.g. Workouts and its plan subgroups). */
    fun getSubGroups(data: AppData, parentId: String): List<Group> = HabitDomain.getSubGroups(data, parentId)

    /** Archive instead of delete. Call save after. Now delegate to immutable helpers. */
    fun archiveHabit(current: AppData, habitId: String): AppData = current.withArchivedHabit(habitId)

    fun archiveGroup(current: AppData, groupId: String): AppData = current.withArchivedGroup(groupId)

    /** Unarchive (restore). Does not touch entries. */
    fun unarchiveHabit(current: AppData, habitId: String): AppData = current.withUnarchivedHabit(habitId)

    fun unarchiveGroup(current: AppData, groupId: String): AppData = current.withUnarchivedGroup(groupId)

    // --- Skip tracking ---

    /** Count recent skips for a habit (distinct days in window). */
    fun countRecentSkips(data: AppData, habitId: String, daysBack: Int = 7): Int = HabitDomain.countRecentSkips(data, habitId, daysBack)

    // --- CSV backup (human + AI friendly) ---
    // Exports 3 simple CSVs (or caller can combine). Uses names + ISO times for readability.

    fun exportGroupsCsv(data: AppData): String {
        val header = "id,name,parentId,timeHint,archived,order\n"
        val rows = data.groups.joinToString("\n") { g ->
            listOf(g.id, g.name, g.parentId ?: "", g.timeHint, g.archived, g.order).joinToString(",")
        }
        return header + rows
    }

    fun exportHabitsCsv(data: AppData): String {
        val header = "id,name,groupId,type,target,unit,canSkip,archived,why,isSupplement\n"
        val rows = data.habits.joinToString("\n") { h ->
            val why = h.why.replace(",", " ").replace("\n", " ")
            listOf(h.id, h.name, h.groupId, h.type.name, h.target ?: "", h.unit, h.canSkip, h.archived, why, h.isSupplement).joinToString(",")
        }
        return header + rows
    }

    fun exportEntriesCsv(data: AppData): String {
        val header = "date,logged_at_iso,habit_id,habit_name,group_name,value,note,skipped\n"
        val sb = StringBuilder(header)
        data.entries.forEach { (date, map) ->
            map.forEach { (hid, e) ->
                val h = data.habits.find { it.id == hid }
                val gName = data.groups.find { it.id == h?.groupId }?.name ?: ""
                val name = h?.name ?: hid
                val note = e.note.replace(",", " ").replace("\n", " ")
                val iso = java.time.Instant.ofEpochMilli(e.loggedAt).toString()
                sb.append("$date,$iso,$hid,$name,$gName,${e.value},$note,${e.skipped}\n")
            }
        }
        return sb.toString()
    }

    /**
     * Simple import (merge). Matches by id then name. Returns merged AppData.
     * For production would be more robust; this is human/AI CSV friendly.
     */
    fun importFromCsvs(current: AppData, groupsCsv: String?, habitsCsv: String?, entriesCsv: String?): AppData {
        // Very lightweight parser (assumes well-formed, no complex escaping for MVP)
        var result = current

        // Groups (basic)
        groupsCsv?.lines()?.drop(1)?.filter { it.isNotBlank() }?.forEach { line ->
            val p = line.split(",")
            if (p.size >= 2) {
                val id = p[0]
                val existing = result.groups.find { it.id == id }
                if (existing == null) {
                    val ng = Group(id, p[1], p.getOrNull(3) ?: "ANY", p.getOrNull(5)?.toIntOrNull() ?: 0, p.getOrNull(2)?.takeIf { it.isNotBlank() })
                    result = result.withAddedGroup(ng)
                }
            }
        }

        // Habits (basic)
        habitsCsv?.lines()?.drop(1)?.filter { it.isNotBlank() }?.forEach { line ->
            val p = line.split(",")
            if (p.size >= 3) {
                val id = p[0]
                val g = p[2]
                if (result.habits.none { it.id == id }) {
                    val nh = Habit(
                        id = id, name = p[1], groupId = g,
                        type = runCatching { HabitType.valueOf(p.getOrNull(3) ?: "CHECKBOX") }.getOrDefault(HabitType.CHECKBOX),
                        target = p.getOrNull(4)?.toDoubleOrNull(),
                        unit = p.getOrNull(5) ?: "",
                        canSkip = p.getOrNull(6)?.toBooleanStrictOrNull() ?: true,
                        isSupplement = p.getOrNull(8)?.toBooleanStrictOrNull() ?: false
                    )
                    result = result.withAddedHabit(nh)
                }
            }
        }

        // Entries: merge by date + habitId. Support hand-edited past data.
        entriesCsv?.lines()?.drop(1)?.filter { it.isNotBlank() }?.forEach { line ->
            try {
                // Naive but tolerant CSV split (notes may contain commas - user should quote or escape when editing)
                val parts = line.split(",", limit = 8)
                if (parts.size >= 6) {
                    val date = parts[0]
                    val hid = parts[2]
                    val value = parts.getOrNull(5)?.toDoubleOrNull() ?: 1.0
                    val note = parts.getOrNull(6) ?: ""
                    val skipped = parts.getOrNull(7)?.toBooleanStrictOrNull() ?: false
                    val loggedAt = try {
                        java.time.Instant.parse(parts.getOrNull(1)).toEpochMilli()
                    } catch (_: Exception) { System.currentTimeMillis() }

                    val entry = HabitEntry(
                        value = value,
                        note = note,
                        loggedAt = loggedAt,
                        skipped = skipped
                    )
                    result = result.withUpdatedEntry(date, hid, entry)
                }
            } catch (ex: Exception) {
                // Skip bad row, caller should log
            }
        }

        return result
    }

    // --- Schedule helpers (v5) for 24h circle, multiple schedules, weekday/date rules, presets (delegated) ---

    fun getActiveSchedule(data: AppData): Schedule? = HabitDomain.getActiveSchedule(data)

    /** Returns true if the schedule's rules make it applicable today. */
    fun isScheduleApplicableToday(data: AppData, schedule: Schedule): Boolean =
        HabitDomain.isScheduleApplicableToday(data, schedule)

    /** Resolve the group that should be "current" right now based on active schedule + time.
     * Falls back to old timeHint + static period logic if no usable schedule.
     */
    fun resolveCurrentGroup(data: AppData, now: java.time.LocalTime = java.time.LocalTime.now()): Group? =
        HabitDomain.resolveCurrentGroup(data, now)

    fun addSchedule(current: AppData, schedule: Schedule): AppData = current.withAddedSchedule(schedule)

    fun updateSchedule(current: AppData, schedule: Schedule): AppData = current.withUpdatedSchedule(schedule)

    fun deleteSchedule(current: AppData, scheduleId: String): AppData = current.withoutSchedule(scheduleId)

    fun setActiveSchedule(current: AppData, scheduleId: String?): AppData = current.withActiveSchedule(scheduleId)

    /** Pure reorder/move delegated (#14). */
    fun moveHabit(currentHabits: List<Habit>, habitId: String, newGroupId: String, newOrder: Int): List<Habit> =
        HabitDomain.moveHabit(currentHabits, habitId, newGroupId, newOrder)

    // Capture support (#8-12)
    fun addCapture(current: AppData, capture: CaptureItem): AppData = current.withAddedCapture(capture)
    fun updateCapture(current: AppData, capture: CaptureItem): AppData = current.withUpdatedCapture(capture)
    fun deleteCapture(current: AppData, id: String): AppData = current.withoutCapture(id)
}

// Convenience typealias for existing call sites during transition (can be removed later)
typealias HabitRepositoryImpl = AndroidHabitRepository
