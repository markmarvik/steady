package com.steady.habittracker.sensors

import android.content.Context
import com.steady.habittracker.data.AppData
import com.steady.habittracker.data.AutoSource
import com.steady.habittracker.data.AutoSuggestion
import com.steady.habittracker.data.Habit
import com.steady.habittracker.data.HabitDomain
import com.steady.habittracker.data.HabitEntry
import com.steady.habittracker.data.withResolvedAutoSuggestion
import com.steady.habittracker.data.withUpdatedEntry
import com.steady.habittracker.data.withUpsertedAutoSuggestion
import java.time.LocalDate
import java.time.LocalTime

/**
 * Collects sensor/external readings and merges suggestions or auto-applied entries into [AppData].
 */
object AutoLogEngine {

    data class Result(
        val data: AppData,
        val appliedCount: Int,
        val suggestedCount: Int,
        val messages: List<String> = emptyList()
    )

    suspend fun run(context: Context, data: AppData, today: String = HabitDomain.getToday()): Result {
        if (!data.autoLogMasterEnabled) {
            return Result(data, 0, 0, listOf("Auto-log master switch is off"))
        }
        val linked = data.habits.filter { !it.archived && it.autoSource != AutoSource.NONE }
        if (linked.isEmpty()) return Result(data, 0, 0)

        val date = try {
            LocalDate.parse(today)
        } catch (_: Exception) {
            LocalDate.now()
        }

        // Shared samples (take once per run)
        val screenMin = ScreenTimeReader.screenOnMinutes(context, date)
        val windStart = windDownStart(data)
        val eveningScreen = ScreenTimeReader.screenOnMinutesAfter(context, date, windStart)
        val lux = if (linked.any {
                it.autoSource == AutoSource.LIGHT_BEDTIME_AVG ||
                    it.autoSource == AutoSource.LIGHT_DARK_CHECK
            }
        ) {
            LightSampler.sampleAverageLux(context)?.toDouble()
        } else null
        val noise = if (linked.any { it.autoSource == AutoSource.NOISE_EVENING_DB }) {
            NoiseSampler.sampleApproxDb(context)
        } else null
        val phoneSteps = if (linked.any { it.autoSource == AutoSource.PHONE_STEPS }) {
            StepCounterReader.todaySteps(context, today)?.toDouble()
        } else null

        var next = data
        var applied = 0
        var suggested = 0
        val msgs = mutableListOf<String>()

        for (habit in linked) {
            val reading = readingFor(context, habit, today, screenMin, eveningScreen, lux, noise, phoneSteps)
                ?: continue
            val suggestion = AutoLogMapper.toSuggestion(habit, today, reading) ?: continue

            if (AutoLogMapper.shouldAutoApply(habit)) {
                // Don't overwrite a manual non-auto entry unless empty
                val existing = next.entries[today]?.get(habit.id)
                if (existing != null && existing.note.isNotBlank() && !existing.note.startsWith("auto ·")) {
                    // Keep manual; still store suggestion as resolved skip
                    next = next.withUpsertedAutoSuggestion(suggestion.copy(resolved = true))
                    msgs.add("${habit.name}: skipped (manual log present)")
                    continue
                }
                val entry = HabitEntry(
                    value = suggestion.value,
                    note = suggestion.note,
                    loggedAt = suggestion.observedAt,
                    skipped = false
                )
                next = next.withUpdatedEntry(today, habit.id, entry)
                    .withUpsertedAutoSuggestion(suggestion.copy(resolved = true))
                applied++
            } else {
                next = next.withUpsertedAutoSuggestion(suggestion)
                suggested++
            }
        }
        return Result(next, applied, suggested, msgs)
    }

    private fun readingFor(
        context: Context,
        habit: Habit,
        today: String,
        screenMin: Long?,
        eveningScreen: Long?,
        lux: Double?,
        noise: Double?,
        phoneSteps: Double?
    ): AutoLogMapper.Reading? {
        return when (habit.autoSource) {
            AutoSource.NONE -> null
            AutoSource.SCREEN_MINUTES ->
                screenMin?.toDouble()?.let { AutoLogMapper.Reading(AutoSource.SCREEN_MINUTES, it) }
            AutoSource.SCREEN_AFTER_WINDDOWN ->
                eveningScreen?.toDouble()?.let {
                    AutoLogMapper.Reading(AutoSource.SCREEN_AFTER_WINDDOWN, it)
                }
            AutoSource.LIGHT_BEDTIME_AVG ->
                lux?.let { AutoLogMapper.Reading(AutoSource.LIGHT_BEDTIME_AVG, it) }
            AutoSource.LIGHT_DARK_CHECK ->
                lux?.let { AutoLogMapper.Reading(AutoSource.LIGHT_DARK_CHECK, it) }
            AutoSource.NOISE_EVENING_DB ->
                noise?.let { AutoLogMapper.Reading(AutoSource.NOISE_EVENING_DB, it) }
            AutoSource.PHONE_STEPS ->
                phoneSteps?.let { AutoLogMapper.Reading(AutoSource.PHONE_STEPS, it) }
            AutoSource.GADGETBRIDGE_STEPS -> {
                val steps = ExternalMetricsStore.getSteps(context, today) ?: return null
                AutoLogMapper.Reading(AutoSource.GADGETBRIDGE_STEPS, steps)
            }
            AutoSource.EXTERNAL_METRIC -> {
                val key = habit.autoMetricKey.ifBlank { habit.unit.ifBlank { "value" } }
                val v = ExternalMetricsStore.get(context, today, key) ?: return null
                AutoLogMapper.Reading(AutoSource.EXTERNAL_METRIC, v, key)
            }
        }
    }

    fun windDownStart(data: AppData): LocalTime {
        val bed = HabitDomain.parseTimeToMinutes(data.sleep.bedTime)
        val wind = data.sleep.windDownMinutes.coerceIn(15, 180)
        val startMin = bed - wind
        val norm = ((startMin % (24 * 60)) + (24 * 60)) % (24 * 60)
        return LocalTime.of(norm / 60, norm % 60)
    }

    fun pendingSuggestions(data: AppData, today: String = HabitDomain.getToday()): List<AutoSuggestion> =
        data.autoSuggestions.filter { it.date == today && !it.resolved }

    fun acceptSuggestion(data: AppData, suggestion: AutoSuggestion): AppData {
        val entry = HabitEntry(
            value = suggestion.value,
            note = suggestion.note,
            loggedAt = System.currentTimeMillis(),
            skipped = false
        )
        return data.withUpdatedEntry(suggestion.date, suggestion.habitId, entry)
            .withResolvedAutoSuggestion(suggestion.habitId, suggestion.date)
    }

    fun dismissSuggestion(data: AppData, suggestion: AutoSuggestion): AppData =
        data.withResolvedAutoSuggestion(suggestion.habitId, suggestion.date)
}
