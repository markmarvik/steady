package com.steady.habittracker.extensions

import android.content.Context
import com.steady.habittracker.data.AppData
import com.steady.habittracker.data.CaptureTags
import com.steady.habittracker.data.ExtensionType
import com.steady.habittracker.data.Habit
import com.steady.habittracker.data.HabitEntry
import com.steady.habittracker.data.OralHygieneSteps
import com.steady.habittracker.data.SensorKind
import com.steady.habittracker.data.SensorSnapshot
import com.steady.habittracker.data.SleepPhoneSlots
import com.steady.habittracker.data.withAddedSensorSnapshot
import com.steady.habittracker.data.withSleepAudioPrefs
import com.steady.habittracker.data.withUpdatedEntry
import com.steady.habittracker.sensors.InstalledApps
import com.steady.habittracker.sensors.LocationReader
import com.steady.habittracker.sensors.ScreenTimeReader
import com.steady.habittracker.sensors.StepCounterReader
import com.steady.habittracker.sleepaudio.SleepAudioScheduler
import com.steady.habittracker.sleepaudio.SleepAudioService
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * Side-effects when special habit blocks are logged (#33–#35, #38).
 * Pure coordination; sensor readers stay in sensors/.
 */
object ExtensionManager {

    data class LogResult(
        val data: AppData,
        val summaryNote: String = "",
        val openWorkout: Boolean = false,
        val openCapture: Boolean = false,
        /** Tags pre-selected when opening capture (e.g. Check-in for ESM). */
        val capturePresetTags: List<String> = emptyList(),
        val openSleepReview: Boolean = false
    )

    /**
     * After a successful log (value >= 0.5, not skipped), run extension side-effects.
     * Returns possibly updated [AppData] and UI hints.
     */
    fun onHabitLogged(
        context: Context,
        data: AppData,
        habit: Habit,
        entry: HabitEntry,
        date: String = LocalDate.now().toString(),
        /** Timeline group for multi-group habits (AM/PM independent). */
        groupId: String? = null
    ): LogResult {
        if (entry.skipped || entry.value < 0.5) return LogResult(data)
        return when (habit.extensionType) {
            ExtensionType.NONE -> LogResult(data)
            ExtensionType.SNORE_WATCH_ACTIVATE -> handleSnoreActivate(context, data, entry)
            ExtensionType.SNORE_WATCH_STOP -> handleSnoreStop(context, data, entry)
            ExtensionType.SENSOR_AUTO_READ -> handleSensorRead(context, data, habit, entry, date)
            ExtensionType.SCREEN_USAGE -> handleScreenUsage(context, data, habit, entry, date)
            ExtensionType.WORKOUT_SESSION -> LogResult(data, openWorkout = true)
            ExtensionType.ESM_CHECKIN -> LogResult(
                data,
                openCapture = true,
                capturePresetTags = listOf(CaptureTags.CHECKIN)
            )
            ExtensionType.POMODORO -> handlePomodoro(data, habit, entry, date)
            ExtensionType.GADGETBRIDGE_SYNC -> LogResult(data)
            ExtensionType.ORAL_HYGIENE -> handleOralHygiene(data, habit, entry, date, groupId)
            ExtensionType.SLEEP_PHONE -> handleSleepPhone(context, data, habit, entry, date)
        }.let { result ->
            // Chain: trigger child SENSOR_AUTO_READ / others with chainAfterHabitId
            val chained = triggerChained(context, result.data, habit.id, date)
            result.copy(data = chained)
        }
    }

    private fun handleSnoreActivate(context: Context, data: AppData, entry: HabitEntry): LogResult {
        val enabled = data.withSleepAudioPrefs(data.sleepAudioPrefs.copy(enabled = true))
        try {
            SleepAudioService.start(context)
        } catch (_: Exception) { }
        try {
            SleepAudioScheduler.reschedule(context, enabled)
        } catch (_: Exception) { }
        val note = listOfNotNull(entry.note.takeIf { it.isNotBlank() }, "Snore Watch started").joinToString(" · ")
        return LogResult(enabled, summaryNote = note)
    }

    private fun handleSnoreStop(context: Context, data: AppData, entry: HabitEntry): LogResult {
        try {
            SleepAudioService.stop(context)
        } catch (_: Exception) { }
        val last = data.sleepNights.firstOrNull()
        val summary = if (last != null) {
            "Night: ${last.eventCount} events, ${last.snoreLikeCount} snore-like, quiet ${last.quietScore}"
        } else {
            "Snore Watch stopped"
        }
        val note = listOfNotNull(entry.note.takeIf { it.isNotBlank() }, summary).joinToString(" · ")
        return LogResult(data, summaryNote = note, openSleepReview = true)
    }

    private fun handleSensorRead(
        context: Context,
        data: AppData,
        habit: Habit,
        entry: HabitEntry,
        date: String
    ): LogResult {
        val kinds = habit.extensionConfig.sensors.ifEmpty {
            listOf(SensorKind.STEPS, SensorKind.SCREEN, SensorKind.LIGHT)
        }
        val readings = linkedMapOf<String, String>()
        for (kind in kinds) {
            when (kind) {
                SensorKind.GPS -> {
                    val fix = LocationReader.lastKnown(context)
                    if (fix != null) {
                        readings["gps"] = LocationReader.format(fix)
                    } else {
                        readings["gps"] = "unavailable"
                    }
                }
                SensorKind.STEPS -> {
                    readings["steps"] = StepCounterReader.cachedTodayHint(context)
                        ?: if (StepCounterReader.isAvailable(context)) "sample pending"
                        else "unavailable"
                }
                SensorKind.LIGHT -> {
                    readings["light"] = "see auto-log / sample on device"
                }
                SensorKind.NOISE -> {
                    readings["noise"] = "see auto-log / sample on device"
                }
                SensorKind.SCREEN -> {
                    val min = ScreenTimeReader.screenOnMinutes(context)
                    readings["screen_min"] = min?.toString() ?: "no usage access"
                }
                SensorKind.EXTERNAL -> {
                    readings["external"] = "see ExternalMetricsStore"
                }
            }
        }
        val snap = SensorSnapshot(
            id = "ss_${UUID.randomUUID().toString().take(8)}",
            habitId = habit.id,
            date = date,
            loggedAt = entry.loggedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
            readings = readings,
            note = entry.note
        )
        val summary = readings.entries.joinToString(" · ") { "${it.key}=${it.value}" }
        val note = listOfNotNull(entry.note.takeIf { it.isNotBlank() }, summary).joinToString(" · ")
        var next = data.withAddedSensorSnapshot(snap)
        next = next.withUpdatedEntry(
            date, habit.id,
            entry.copy(note = note.take(500))
        )
        return LogResult(next, summaryNote = summary)
    }

    private fun handleScreenUsage(
        context: Context,
        data: AppData,
        habit: Habit,
        entry: HabitEntry,
        date: String
    ): LogResult {
        val totalMin = ScreenTimeReader.screenOnMinutes(context) ?: -1L
        val parts = mutableListOf<String>()
        if (totalMin >= 0) {
            val h = totalMin / 60
            val m = totalMin % 60
            parts += "Screen today: ${h}h ${m}m"
            habit.extensionConfig.dailyLimitMinutes?.let { lim ->
                parts += if (totalMin <= lim) "under ${lim}m limit" else "over ${lim}m limit"
            }
        } else {
            parts += "Usage access not granted"
        }
        if (habit.extensionConfig.includeAppBreakdown) {
            val pkgs = habit.extensionConfig.packages
            val top = ScreenTimeReader.topAppsMinutes(
                context,
                limit = if (pkgs.isEmpty()) 5 else pkgs.size.coerceAtMost(12),
                packages = pkgs
            )
            if (top.isNotEmpty()) {
                parts += (if (pkgs.isEmpty()) "Top: " else "Tracked: ") +
                    top.joinToString(", ") { (pkg, min) ->
                        "${InstalledApps.labelFor(context, pkg).take(16)}=${min}m"
                    }
            } else if (pkgs.isNotEmpty()) {
                parts += "No usage for selected apps today"
            }
        }
        val summary = parts.joinToString(" · ")
        val note = listOfNotNull(entry.note.takeIf { it.isNotBlank() }, summary).joinToString(" · ")
        val readings = buildMap {
            if (totalMin >= 0) put("screen_min", totalMin.toString())
            put("summary", summary)
        }
        val snap = SensorSnapshot(
            id = "ss_${UUID.randomUUID().toString().take(8)}",
            habitId = habit.id,
            date = date,
            loggedAt = entry.loggedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
            readings = readings,
            note = entry.note
        )
        var next = data.withAddedSensorSnapshot(snap)
        next = next.withUpdatedEntry(date, habit.id, entry.copy(note = note.take(500)))
        return LogResult(next, summaryNote = summary)
    }

    private fun handlePomodoro(
        data: AppData,
        habit: Habit,
        entry: HabitEntry,
        date: String
    ): LogResult {
        val work = habit.extensionConfig.pomodoroWorkMin.coerceIn(5, 120)
        val note = listOfNotNull(
            entry.note.takeIf { it.isNotBlank() },
            "Focus block ${work}m"
        ).joinToString(" · ")
        val next = data.withUpdatedEntry(date, habit.id, entry.copy(note = note.take(500)))
        return LogResult(next, summaryNote = "Pomodoro ${work}m logged")
    }

    private fun triggerChained(
        context: Context,
        data: AppData,
        completedHabitId: String,
        date: String
    ): AppData {
        var current = data
        val children = current.habits.filter {
            !it.archived &&
                it.extensionConfig.chainAfterHabitId == completedHabitId &&
                it.extensionType != ExtensionType.NONE
        }
        for (child in children) {
            val already = current.entries[date]?.get(child.id)
            if (already != null && already.value >= 0.5) continue
            val entry = HabitEntry(value = 1.0, note = "chained after $completedHabitId", loggedAt = System.currentTimeMillis())
            current = current.withUpdatedEntry(date, child.id, entry)
            val result = onHabitLogged(context, current, child, entry, date)
            current = result.data
        }
        return current
    }

    fun statusLine(habit: Habit, data: AppData, context: Context? = null): String? {
        return when (habit.extensionType) {
            ExtensionType.NONE -> null
            ExtensionType.SNORE_WATCH_ACTIVATE ->
                if (data.sleepAudioPrefs.enabled) "Snore Watch armed" else "Tap to start overnight capture"
            ExtensionType.SNORE_WATCH_STOP ->
                data.sleepNights.firstOrNull()?.let {
                    "Last night: ${it.eventCount} events · quiet ${it.quietScore}"
                } ?: "Stop & review night"
            ExtensionType.SENSOR_AUTO_READ -> {
                val last = data.sensorSnapshots.firstOrNull { it.habitId == habit.id }
                last?.let { "Last: ${it.readings.entries.take(2).joinToString { e -> e.key }}" }
                    ?: "Capture sensors"
            }
            ExtensionType.SCREEN_USAGE -> {
                if (context != null) {
                    val min = ScreenTimeReader.screenOnMinutes(context)
                    if (min != null) "Screen so far: ${ScreenTimeReader.formatMinutes(min)}"
                    else "Grant usage access"
                } else {
                    "Screen usage block"
                }
            }
            ExtensionType.WORKOUT_SESSION -> "Start workout session"
            ExtensionType.ESM_CHECKIN -> "What are you doing right now?"
            ExtensionType.POMODORO -> {
                val w = habit.extensionConfig.pomodoroWorkMin
                "${w}m focus block"
            }
            ExtensionType.GADGETBRIDGE_SYNC -> {
                val p = data.gadgetbridgePrefs
                when {
                    !p.enabled -> "Enable in Blocks"
                    p.lastStatus.isNotBlank() -> p.lastStatus
                    else -> "Polling Gadgetbridge export"
                }
            }
            ExtensionType.ORAL_HYGIENE -> oralHygieneStatus(habit, data)
            ExtensionType.SLEEP_PHONE -> sleepPhoneStatus(habit, data, context)
        }
    }

    private fun handleOralHygiene(
        data: AppData,
        habit: Habit,
        entry: HabitEntry,
        date: String,
        groupId: String?
    ): LogResult {
        val step = habit.extensionConfig.oralStepKey.ifBlank { "care" }
        val prefs = data.oralHygienePrefs
        val noteExtra = when (step) {
            OralHygieneSteps.BRUSH -> "Brushed ${prefs.brushMinutes.coerceIn(1, 5)} min"
            OralHygieneSteps.FLOSS -> "Flossed"
            OralHygieneSteps.TONGUE -> "Tongue scraped"
            OralHygieneSteps.WATER -> prefs.waterFlushLabel.ifBlank { "Water rinse" }
            OralHygieneSteps.MOUTHWASH -> "Mouthwash"
            else -> "Oral care"
        }
        val note = listOfNotNull(entry.note.takeIf { it.isNotBlank() }, noteExtra)
            .joinToString(" · ")
        val next = data.withUpdatedEntry(
            date,
            habit.id,
            entry.copy(note = note.take(500)),
            groupId
        )
        return LogResult(next, summaryNote = noteExtra)
    }

    private fun oralHygieneStatus(habit: Habit, data: AppData): String {
        val step = habit.extensionConfig.oralStepKey
        val prefs = data.oralHygienePrefs
        return when (step) {
            OralHygieneSteps.BRUSH -> "${prefs.brushMinutes.coerceIn(1, 5)} min · AM & PM"
            OralHygieneSteps.FLOSS -> "Floss · AM & PM"
            OralHygieneSteps.TONGUE -> "Tongue · AM & PM"
            OralHygieneSteps.WATER -> "${prefs.waterFlushLabel.ifBlank { "Water" }} · AM & PM"
            OralHygieneSteps.MOUTHWASH -> "Mouthwash · AM & PM"
            else -> "Oral hygiene · AM & PM"
        }
    }

    private fun handleSleepPhone(
        context: Context,
        data: AppData,
        habit: Habit,
        entry: HabitEntry,
        date: String
    ): LogResult {
        val prefs = data.sleepPhonePrefs
        val slot = habit.extensionConfig.sleepPhoneSlot
        val screenNote = if (prefs.attachScreenMinutes) {
            sleepPhoneScreenNote(context, prefs, slot, date)
        } else {
            null
        }
        val label = when (slot) {
            SleepPhoneSlots.MORNING -> "Morning phone delay"
            SleepPhoneSlots.EVENING -> "Evening phone parked"
            else -> "Phone guard"
        }
        val note = listOfNotNull(
            entry.note.takeIf { it.isNotBlank() },
            label,
            screenNote
        ).joinToString(" · ")
        val next = data.withUpdatedEntry(date, habit.id, entry.copy(note = note.take(500)))
        return LogResult(next, summaryNote = listOfNotNull(label, screenNote).joinToString(" · "))
    }

    private fun sleepPhoneStatus(
        habit: Habit,
        data: AppData,
        context: Context?
    ): String {
        val prefs = data.sleepPhonePrefs
        val slot = habit.extensionConfig.sleepPhoneSlot
        val base = when (slot) {
            SleepPhoneSlots.MORNING -> "Delay first use · until ${prefs.morningTrackUntilHour}:00"
            SleepPhoneSlots.EVENING -> "Park phone · after ${prefs.eveningTrackFromHour}:00"
            else -> "Phone guard"
        }
        if (context == null || !prefs.attachScreenMinutes) return base
        val mins = sleepPhoneScreenMinutes(context, prefs, slot)
        return if (mins != null) {
            "$base · ${ScreenTimeReader.formatMinutes(mins)} so far"
        } else {
            base
        }
    }

    private fun sleepPhoneScreenNote(
        context: Context,
        prefs: com.steady.habittracker.data.SleepPhonePrefs,
        slot: String,
        date: String
    ): String? {
        val mins = sleepPhoneScreenMinutes(context, prefs, slot, date) ?: return null
        return "screen ${ScreenTimeReader.formatMinutes(mins)}"
    }

    private fun sleepPhoneScreenMinutes(
        context: Context,
        prefs: com.steady.habittracker.data.SleepPhonePrefs,
        slot: String,
        dateStr: String? = null
    ): Long? {
        val date = try {
            if (dateStr != null) LocalDate.parse(dateStr) else LocalDate.now()
        } catch (_: Exception) {
            LocalDate.now()
        }
        return when (slot) {
            SleepPhoneSlots.EVENING -> {
                val hour = prefs.eveningTrackFromHour.coerceIn(0, 23)
                ScreenTimeReader.screenOnMinutesAfter(context, date, LocalTime.of(hour, 0))
            }
            SleepPhoneSlots.MORNING -> {
                val endHour = prefs.morningTrackUntilHour.coerceIn(1, 23)
                val full = ScreenTimeReader.screenOnMinutes(context, date) ?: return null
                val after = ScreenTimeReader.screenOnMinutesAfter(
                    context,
                    date,
                    LocalTime.of(endHour, 0)
                ) ?: 0L
                (full - after).coerceAtLeast(0L)
            }
            else -> null
        }
    }
}
