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

    override val appDataFlow: Flow<AppData> = context.dataStore.data
        .map { preferences ->
            val jsonString = preferences[DATA_KEY] ?: ""
            val data = if (jsonString.isBlank()) {
                getDefaultData()
            } else {
                try {
                    val parsed = json.decodeFromString<AppData>(jsonString)
                    migrateIfNeeded(parsed)
                } catch (e: Exception) {
                    getDefaultData()
                }
            }
            // Ensure at least defaults if empty
            if (data.habits.isEmpty()) getDefaultData() else data
        }

    override suspend fun saveData(data: AppData) {
        context.dataStore.edit { preferences ->
            preferences[DATA_KEY] = json.encodeToString(data)
        }
    }

    /**
     * Migrate older schemas to v4.
     * Adds: archived, canSkip, parentId, skipped, onboarded, colorScheme.
     * Legacy users get onboarded=true to avoid re-showing welcome.
     */
    private fun migrateIfNeeded(data: AppData): AppData {
        if (data.schemaVersion >= 4 && data.groups.isNotEmpty() && data.habits.isNotEmpty()) {
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
                canSkip = if (isHygiene) false else h.canSkip
            )
        }
        val upgradedEntries = d.entries.mapValues { (_, m) ->
            m.mapValues { (_, e) -> e.copy(skipped = e.skipped) }
        }

        return d.copy(
            groups = upgradedGroups,
            habits = upgradedHabits,
            entries = upgradedEntries,
            schemaVersion = 4,
            onboarded = if (d.onboarded) d.onboarded else true, // legacy users treated as onboarded
            colorScheme = if (d.colorScheme.isNotBlank()) d.colorScheme else "default"
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
            Habit("h_supp_am", "Morning Supplements (Omega-3, Multi)", "Foundational nutrition.", "g_supp", HabitType.COUNTER, target = 1.0, unit = "", order = 3),

            // Focus/Work
            Habit("h_move", "Move Your Body", "20+ min walk or exercise for brain and longevity.", "g_focus", HabitType.CHECKBOX, order = 0),
            Habit("h_protein", "Protein First", "Prioritize protein for muscle, satiety and metabolism.", "g_focus", HabitType.CHECKBOX, order = 1),
            Habit("h_focus", "Deep Focus Block", "Undistracted meaningful work or learning session.", "g_focus", HabitType.DURATION_MIN, target = 60.0, unit = "min", order = 2),
            Habit("h_pull", "Pull-ups / Dips / Squats", "Strength training tracker (reps).", "g_workout", HabitType.COUNTER, target = 5.0, unit = "reps", order = 3),

            // Evening
            Habit("h_wind", "Wind Down Routine", "Protects sleep quality with consistent bedtime.", "g_even", HabitType.CHECKBOX, order = 0),
            Habit("h_hydrate", "Stay Hydrated", "Better energy, focus and recovery.", "g_even", HabitType.COUNTER, target = 2.5, unit = "L", order = 1),
            Habit("h_mg", "Magnesium + Chamomile", "Nervous system + sleep prep.", "g_even", HabitType.CHECKBOX, order = 2),
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
            schemaVersion = 4,
            onboarded = false,
            colorScheme = "default"
        )
    }

    // --- Convenience helpers used by UI / schedulers (not part of portable interface) ---

    fun getToday(): String {
        val today = java.time.LocalDate.now()
        return today.toString()
    }

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
        var checkDate = java.time.LocalDate.parse(todayStr)
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

    // --- Weekly / completion rate helpers for progress UI ---

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
        var d = java.time.LocalDate.now()
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
        var d = java.time.LocalDate.now()
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
        val hour = java.time.LocalTime.now().hour
        return when (hour) {
            in 5..11 -> "MORNING"
            in 12..17 -> "WORK"
            else -> "EVENING"
        }
    }

    // --- Active + hierarchy + archive helpers (v3) ---

    /** Active (non-archived) groups, top-level first. */
    fun getActiveGroups(data: AppData): List<Group> =
        data.groups.filter { !it.archived }.sortedBy { it.order }

    /** Habits for a group (active only). Supports parent for subgroups. */
    fun getActiveHabitsForGroup(data: AppData, groupId: String): List<Habit> =
        data.habits.filter { it.groupId == groupId && !it.archived }.sortedBy { it.order }

    /** Return nested view for a parent (e.g. Workouts and its plan subgroups). */
    fun getSubGroups(data: AppData, parentId: String): List<Group> =
        data.groups.filter { it.parentId == parentId && !it.archived }.sortedBy { it.order }

    /** Archive instead of delete. Call save after. */
    fun archiveHabit(current: AppData, habitId: String): AppData {
        val newHabits = current.habits.map { if (it.id == habitId) it.copy(archived = true) else it }
        return current.copy(habits = newHabits)
    }

    fun archiveGroup(current: AppData, groupId: String): AppData {
        val newGroups = current.groups.map { if (it.id == groupId) it.copy(archived = true) else it }
        val newHabits = current.habits.map { if (it.groupId == groupId) it.copy(archived = true) else it }
        return current.copy(groups = newGroups, habits = newHabits)
    }

    /** Unarchive (restore). Does not touch entries. */
    fun unarchiveHabit(current: AppData, habitId: String): AppData {
        val newHabits = current.habits.map { if (it.id == habitId) it.copy(archived = false) else it }
        return current.copy(habits = newHabits)
    }

    fun unarchiveGroup(current: AppData, groupId: String): AppData {
        val newGroups = current.groups.map { if (it.id == groupId) it.copy(archived = false) else it }
        // Do not auto-unarchive habits (user can restore individually or we can add cascade option)
        return current.copy(groups = newGroups)
    }

    // --- Skip tracking ---

    /** Count recent skips for a habit (distinct days in window). */
    fun countRecentSkips(data: AppData, habitId: String, daysBack: Int = 7): Int {
        val today = java.time.LocalDate.now()
        return (0 until daysBack).count { offset ->
            val d = today.minusDays(offset.toLong()).toString()
            data.entries[d]?.get(habitId)?.skipped == true
        }
    }

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
        val header = "id,name,groupId,type,target,unit,canSkip,archived,why\n"
        val rows = data.habits.joinToString("\n") { h ->
            val why = h.why.replace(",", " ").replace("\n", " ")
            listOf(h.id, h.name, h.groupId, h.type.name, h.target ?: "", h.unit, h.canSkip, h.archived, why).joinToString(",")
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
                    result = result.copy(groups = result.groups + Group(id, p[1], p.getOrNull(3) ?: "ANY", p.getOrNull(5)?.toIntOrNull() ?: 0, p.getOrNull(2)?.takeIf { it.isNotBlank() }))
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
                    result = result.copy(habits = result.habits + Habit(
                        id = id, name = p[1], groupId = g,
                        type = runCatching { HabitType.valueOf(p.getOrNull(3) ?: "CHECKBOX") }.getOrDefault(HabitType.CHECKBOX),
                        target = p.getOrNull(4)?.toDoubleOrNull(),
                        unit = p.getOrNull(5) ?: "",
                        canSkip = p.getOrNull(6)?.toBooleanStrictOrNull() ?: true
                    ))
                }
            }
        }

        // Entries are appended (historical). Simple append for now.
        // (Full merge would parse date/hid/value/note/skipped/loggedAt)
        // For this scope we keep entries as-is on import of habits/groups; advanced entry import can be added.

        return result
    }
}

// Convenience typealias for existing call sites during transition (can be removed later)
typealias HabitRepositoryImpl = AndroidHabitRepository
