package com.steady.habittracker.util

import com.steady.habittracker.data.AppData
import com.steady.habittracker.data.CaptureItem
import com.steady.habittracker.data.CaptureTags
import com.steady.habittracker.data.DisplayIcon
import com.steady.habittracker.data.ExtensionType
import com.steady.habittracker.data.GrokPreset
import com.steady.habittracker.data.Habit
import com.steady.habittracker.data.HabitDomain
import com.steady.habittracker.data.HabitType
import com.steady.habittracker.data.SensorSnapshot
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/** How far back to pull notes / captures for Grok context. */
enum class CaptureTimeScope(val label: String, val days: Int?) {
    TODAY("Today", 1),
    LAST_3("Last 3 days", 3),
    LAST_7("Last 7 days", 7),
    LAST_14("Last 14 days", 14),
    LAST_30("Last 30 days", 30),
    ALL("All time", null)
}

/**
 * What the user can attach when building a "Chat with Grok" message from Steady data.
 */
data class GrokShareSelection(
    val includeOverview: Boolean = true,
    val includeMomentum: Boolean = true,
    val includeTagAverages: Boolean = true,
    val includeHabitDetails: Boolean = true,
    val includeSleep: Boolean = false,
    val includeWorkouts: Boolean = false,
    val includePathGoals: Boolean = false,
    val includeRecentLogs: Boolean = true,
    val includeScreenUsage: Boolean = false,
    /** Capture item ids to include (notes, ideas, journal, …). Empty + tags = auto by tags/scope. */
    val captureIds: Set<String> = emptySet(),
    /**
     * When non-empty and [captureIds] is empty, include all captures matching these tags
     * within [captureScope]. When both empty, no captures unless ids are set.
     */
    val captureTags: Set<String> = emptySet(),
    val captureScope: CaptureTimeScope = CaptureTimeScope.LAST_7,
    /** Habit ids for per-habit averages / recent values. */
    val habitIds: Set<String> = emptySet(),
    /** Opening question / instruction the user can edit. */
    val userPrompt: String = DEFAULT_PROMPT
) {
    companion object {
        const val DEFAULT_PROMPT =
            "Looking at my Steady data below, what patterns stand out and what " +
                "should I focus on next week to stay consistent?"

        fun fromPreset(preset: GrokPreset): GrokShareSelection {
            val scope = try {
                CaptureTimeScope.valueOf(preset.captureScope)
            } catch (_: Exception) {
                CaptureTimeScope.LAST_7
            }
            return GrokShareSelection(
                includeOverview = preset.includeOverview,
                includeMomentum = preset.includeMomentum,
                includeTagAverages = preset.includeTagAverages,
                includeHabitDetails = preset.includeHabitDetails,
                includeSleep = preset.includeSleep,
                includeWorkouts = preset.includeWorkouts,
                includePathGoals = preset.includePathGoals,
                includeRecentLogs = preset.includeRecentLogs,
                includeScreenUsage = preset.includeScreenUsage,
                captureTags = preset.captureTags.toSet(),
                captureScope = scope,
                habitIds = preset.habitIds.toSet(),
                userPrompt = preset.userPrompt.ifBlank { DEFAULT_PROMPT }
            )
        }
    }

    fun toPreset(name: String, id: String = "gp_${UUID.randomUUID().toString().take(8)}"): GrokPreset =
        GrokPreset(
            id = id,
            name = name.trim().ifBlank { "Preset" },
            userPrompt = userPrompt,
            includeOverview = includeOverview,
            includeMomentum = includeMomentum,
            includeTagAverages = includeTagAverages,
            includeHabitDetails = includeHabitDetails,
            includeSleep = includeSleep,
            includeWorkouts = includeWorkouts,
            includePathGoals = includePathGoals,
            includeRecentLogs = includeRecentLogs,
            includeScreenUsage = includeScreenUsage,
            captureTags = captureTags.toList(),
            captureScope = captureScope.name,
            habitIds = habitIds.toList(),
            createdAt = System.currentTimeMillis()
        )
}

/**
 * Builds a readable plain-text payload for the Grok app (or clipboard / share).
 * Pure: no Android Context — easy to unit-test.
 */
object GrokContextBuilder {

    private val dateFmt = SimpleDateFormat("MMM d", Locale.US)

    fun build(data: AppData, selection: GrokShareSelection): String {
        val parts = mutableListOf<String>()
        parts += "I'm using Steady, a local habit tracker. Please use the data below."
        parts += ""
        parts += "## My question"
        parts += selection.userPrompt.trim().ifBlank { GrokShareSelection.DEFAULT_PROMPT }
        parts += ""

        if (selection.includeOverview) {
            parts += "## Overview"
            parts += formatOverview(data)
            parts += ""
        }
        if (selection.includeMomentum) {
            parts += "## Momentum"
            parts += formatMomentum(data)
            parts += ""
        }
        if (selection.includeTagAverages) {
            val tags = formatTagAverages(data)
            if (tags.isNotEmpty()) {
                parts += "## Tag averages (7-day)"
                parts += tags
                parts += ""
            }
        }
        if (selection.includeHabitDetails && selection.habitIds.isNotEmpty()) {
            parts += "## Selected habits"
            parts += formatHabits(data, selection.habitIds)
            parts += ""
        }

        val captures = resolveCaptures(data, selection)
        if (captures.isNotEmpty()) {
            parts += "## Notes & ideas (${selection.captureScope.label})"
            parts += formatCapturesList(captures)
            parts += ""
        }

        if (selection.includePathGoals) {
            val path = formatPathGoals(data)
            if (path.isNotEmpty()) {
                parts += "## Path / Dreamline goals"
                parts += path
                parts += ""
            }
        }
        if (selection.includeSleep) {
            val sleep = formatSleep(data)
            if (sleep.isNotEmpty()) {
                parts += "## Sleep audio (recent nights)"
                parts += sleep
                parts += ""
            }
        }
        if (selection.includeWorkouts) {
            val workouts = formatWorkouts(data)
            if (workouts.isNotEmpty()) {
                parts += "## Recent workouts"
                parts += workouts
                parts += ""
            }
        }
        if (selection.includeScreenUsage) {
            val screen = formatScreenUsage(data)
            if (screen.isNotEmpty()) {
                parts += "## Screen usage"
                parts += screen
                parts += ""
            }
        }
        if (selection.includeRecentLogs) {
            val days = selection.captureScope.days?.coerceAtMost(14) ?: 7
            parts += "## Recent logs (last $days days)"
            parts += formatRecentLogs(data, days)
            parts += ""
        }

        parts += "---"
        parts += "Context generated by Steady · ${HabitDomain.getToday()}"
        return parts.joinToString("\n").trimEnd() + "\n"
    }

    /** Captures included by ids, or by multi-select tags + time scope. */
    fun resolveCaptures(data: AppData, selection: GrokShareSelection): List<CaptureItem> {
        if (selection.captureIds.isNotEmpty()) {
            return data.captures
                .filter { it.id in selection.captureIds }
                .sortedByDescending { it.createdAt }
        }
        if (selection.captureTags.isEmpty()) return emptyList()
        return selectableCaptures(
            data = data,
            tagFilter = null,
            tagsAnyOf = selection.captureTags,
            scope = selection.captureScope
        )
    }

    fun estimatePayloadChars(data: AppData, selection: GrokShareSelection): Int =
        build(data, selection).length

    fun formatOverview(data: AppData): String {
        val streak = HabitDomain.computeStreak(data)
        val daysActive = HabitDomain.daysWithActivity(data)
        val totalDone = HabitDomain.totalCompletedLogs(data)
        val last30 = HabitDomain.computeLastNDays(data, 30)
        val avg30 = if (last30.isEmpty()) 0f else last30.map { it.second }.average().toFloat()
        val last7 = HabitDomain.computeLastNDays(data, 7)
        val avg7 = if (last7.isEmpty()) 0f else last7.map { it.second }.average().toFloat()
        val today = HabitDomain.getToday()
        val todayRate = HabitDomain.computeDayCompletion(data, today)
        return buildString {
            appendLine("- Streak: $streak day${if (streak == 1) "" else "s"}")
            appendLine("- Today completion: ${pct(todayRate)}")
            appendLine("- 7-day avg completion: ${pct(avg7)}")
            appendLine("- 30-day avg completion: ${pct(avg30)}")
            appendLine("- Days with activity (all time): $daysActive")
            append("- Total completed logs: $totalDone")
        }
    }

    fun formatMomentum(data: AppData): String {
        val today = HabitDomain.getToday()
        val todayPts = HabitDomain.computeDayPoints(data, today)
        val lifetime = HabitDomain.effectiveLifetimePoints(data)
        val level = HabitDomain.computeLevel(lifetime)
        val title = HabitDomain.levelTitle(level)
        val best = HabitDomain.bestDayScore(data)
        val series = HabitDomain.lastNDayPoints(data, 14)
        val avgPts = if (series.isEmpty()) 0.0 else series.map { it.second }.average()
        return buildString {
            appendLine("- Level $level ($title) · lifetime $lifetime pts")
            appendLine("- Today: $todayPts pts")
            appendLine("- Best day: ${best?.points ?: 0} pts${best?.date?.let { " on $it" } ?: ""}")
            appendLine("- 14-day avg points: ${"%.1f".format(avgPts)}")
            if (series.isNotEmpty()) {
                append(
                    "- Recent points: " +
                        series.takeLast(7).joinToString(", ") { "${it.first.takeLast(5)}=${it.second}" }
                )
            }
        }.trimEnd()
    }

    fun formatTagAverages(data: AppData): String {
        val tags = HabitDomain.getActiveTags(data)
        if (tags.isEmpty()) return ""
        val lines = tags.mapNotNull { tag ->
            val dueToday = HabitDomain.habitsDueOn(data).count { h ->
                tag.id in h.tags ||
                    (tag.id == com.steady.habittracker.data.TagIds.SUPPLEMENTS && h.isSupplement)
            }
            val avg7 = HabitDomain.computeTag7DayAvg(data, tag.id)
            val todayRate = HabitDomain.computeTagDayCompletion(data, tag.id)
            if (dueToday == 0 && avg7 == 0f) return@mapNotNull null
            "- ${tag.name}: today ${pct(todayRate)} · 7d avg ${pct(avg7)}"
        }
        return lines.joinToString("\n")
    }

    fun formatHabits(data: AppData, habitIds: Set<String>): String {
        val habits = data.habits.filter { it.id in habitIds && !it.archived }
            .sortedBy { it.name.lowercase(Locale.getDefault()) }
        if (habits.isEmpty()) return "(none selected)"
        return habits.joinToString("\n") { h -> formatOneHabit(data, h) }
    }

    fun formatOneHabit(data: AppData, h: Habit): String {
        val avg7 = habitDueAvg(data, h, 7)
        val avg30 = habitDueAvg(data, h, 30)
        val label = DisplayIcon.label(h.icon, h.name)
        val type = h.type.name.lowercase(Locale.US).replace('_', ' ')
        val unit = h.unit.trim()
        val last = lastEntrySummary(data, h)
        val tagStr = HabitDomain.tagNamesForHabit(data, h).joinToString(", ")
        return buildString {
            append("- $label ($type")
            if (unit.isNotEmpty()) append(", $unit")
            append(")")
            if (tagStr.isNotEmpty()) append(" · tags: $tagStr")
            appendLine()
            append("  7d avg on due days: ${pct(avg7)} · 30d: ${pct(avg30)}")
            if (last != null) append(" · last: $last")
        }
    }

    fun formatCaptures(data: AppData, ids: Set<String>): String {
        val items = data.captures.filter { it.id in ids }.sortedByDescending { it.createdAt }
        if (items.isEmpty()) return "(none selected)"
        return formatCapturesList(items)
    }

    fun formatCapturesList(items: List<CaptureItem>): String {
        if (items.isEmpty()) return "(none selected)"
        // Group by primary tag for easier Grok parsing
        val grouped = items.groupBy { it.tags.firstOrNull() ?: "Note" }
        return grouped.entries.joinToString("\n") { (tag, list) ->
            buildString {
                appendLine("### $tag")
                list.forEach { appendLine(formatCapture(it)) }
            }.trimEnd()
        }
    }

    fun formatCapture(item: CaptureItem): String {
        val whenStr = dateFmt.format(Date(item.createdAt))
        val tags = if (item.tags.isEmpty()) "Note" else item.tags.joinToString(" · ")
        val title = item.title.trim().ifBlank { "(untitled)" }
        val note = item.note.trim()
        return if (note.isEmpty()) {
            "- [$tags] $title ($whenStr)"
        } else {
            "- [$tags] $title — $note ($whenStr)"
        }
    }

    fun formatPathGoals(data: AppData): String {
        val goals = data.goals.filter { !it.archived }
        if (goals.isEmpty()) return ""
        return goals.joinToString("\n") { g ->
            val conf = (g.confidence * 100).toInt()
            val prog = (g.progress * 100).toInt()
            val first = g.firstStepNow.trim().takeIf { it.isNotEmpty() }
            buildString {
                append("- ${g.title} (${g.category.name.lowercase()} · ${g.horizon.name.lowercase().replace('_', ' ')})")
                appendLine()
                append("  progress $prog% · confidence $conf%")
                if (first != null) append(" · next: $first")
                val note = g.notes.lastOrNull()?.trim()
                if (!note.isNullOrEmpty()) append(" · note: $note")
            }
        }
    }

    fun formatSleep(data: AppData): String {
        val nights = data.sleepNights.sortedByDescending { it.startedAt }.take(5)
        if (nights.isEmpty()) return ""
        return nights.joinToString("\n") { n ->
            "- Night → ${n.wakeDate}: quiet ${n.quietScore}/100 · ${n.eventCount} events · " +
                "${n.snoreLikeCount} snore-like · ${"%.1f".format(n.loudMinutes)} loud min" +
                if (n.completed) "" else " (in progress)"
        }
    }

    fun formatWorkouts(data: AppData): String {
        val sessions = HabitDomain.recentWorkoutSessions(data, 8)
        if (sessions.isEmpty()) return ""
        return sessions.joinToString("\n") { s ->
            val name = data.routines.find { it.id == s.routineId }?.name ?: s.routineId
            val sets = HabitDomain.sessionSetCount(s)
            val mins = s.totalDurationMin?.let { " · ${it} min" }.orEmpty()
            "- $name · ${s.date} · $sets set(s)$mins"
        }
    }

    fun formatScreenUsage(data: AppData): String {
        val snaps = screenUsageSnapshots(data)
        if (snaps.isEmpty()) return ""
        val byDate = snaps.groupBy { it.date }.toSortedMap(compareByDescending { it })
        return byDate.entries.take(14).joinToString("\n") { (date, list) ->
            val best = list.maxByOrNull { it.loggedAt } ?: list.first()
            val min = best.readings["screen_min"]?.toLongOrNull()
            val summary = best.readings["summary"] ?: best.note
            val time = if (min != null && min >= 0) {
                val h = min / 60
                val m = min % 60
                "${h}h ${m}m"
            } else {
                summary.ifBlank { "—" }
            }
            "- $date: $time" + if (summary.isNotBlank() && min != null) " · $summary" else ""
        }
    }

    fun screenUsageSnapshots(data: AppData): List<SensorSnapshot> {
        val screenHabitIds = data.habits
            .filter { it.extensionType == ExtensionType.SCREEN_USAGE && !it.archived }
            .map { it.id }
            .toSet()
        if (screenHabitIds.isEmpty()) {
            // Still surface any snapshots that look like screen usage
            return data.sensorSnapshots.filter {
                it.readings.containsKey("screen_min") ||
                    it.readings["summary"]?.contains("Screen", ignoreCase = true) == true
            }
        }
        return data.sensorSnapshots.filter { it.habitId in screenHabitIds || it.readings.containsKey("screen_min") }
    }

    fun hasScreenUsageBlock(data: AppData): Boolean =
        data.habits.any { !it.archived && it.extensionType == ExtensionType.SCREEN_USAGE } ||
            data.sensorSnapshots.any { it.readings.containsKey("screen_min") }

    /** Daily screen minutes for heatmap / History (date → minutes, -1 if unknown). */
    fun dailyScreenMinutes(data: AppData, days: Int = 28): List<Pair<String, Long?>> {
        val snaps = screenUsageSnapshots(data)
        val byDate = snaps.groupBy { it.date }.mapValues { (_, list) ->
            list.maxByOrNull { it.loggedAt }
                ?.readings?.get("screen_min")
                ?.toLongOrNull()
        }
        val out = mutableListOf<Pair<String, Long?>>()
        var d = LocalDate.now()
        repeat(days) {
            out.add(d.toString() to byDate[d.toString()])
            d = d.minusDays(1)
        }
        return out.reversed()
    }

    fun formatRecentLogs(data: AppData, days: Int = 7): String {
        val today = try {
            LocalDate.parse(HabitDomain.getToday())
        } catch (_: Exception) {
            LocalDate.now()
        }
        val lines = mutableListOf<String>()
        var d = today
        repeat(days.coerceAtLeast(1)) {
            val key = d.toString()
            val dayMap = data.entries[key].orEmpty()
            if (dayMap.isNotEmpty()) {
                lines += "$key:"
                dayMap.forEach { (habitId, entry) ->
                    val habit = data.habits.find { it.id == habitId }
                    val name = habit?.let { DisplayIcon.label(it.icon, it.name) } ?: habitId
                    val valueStr = when {
                        entry.skipped -> "skipped"
                        habit?.type == HabitType.CHECKBOX && entry.value >= 0.5 -> "done"
                        else -> {
                            val u = habit?.unit.orEmpty()
                            "${entry.value}${if (u.isNotBlank()) " $u" else ""}"
                        }
                    }
                    val note = entry.note.trim().takeIf { it.isNotEmpty() }?.let { " — \"$it\"" }.orEmpty()
                    lines += "  · $name: $valueStr$note"
                }
            }
            d = d.minusDays(1)
        }
        return if (lines.isEmpty()) "(no logs in the last $days days)" else lines.joinToString("\n")
    }

    /** Average completion on days the habit was due (0–1). */
    fun habitDueAvg(data: AppData, habit: Habit, days: Int): Float {
        var sum = 0f
        var count = 0
        var d = LocalDate.now()
        repeat(days) {
            val due = HabitDomain.habitsDueOn(data, d).any { it.id == habit.id }
            if (due) {
                val entry = data.entries[d.toString()]?.get(habit.id)
                val done = when {
                    entry == null || entry.skipped -> 0f
                    habit.type == HabitType.CHECKBOX -> if (entry.value >= 0.5) 1f else 0f
                    habit.type == HabitType.SCALE_1_5 -> (entry.value / 5.0).toFloat().coerceIn(0f, 1f)
                    else -> if (entry.value > 0) 1f else 0f
                }
                sum += done
                count++
            }
            d = d.minusDays(1)
        }
        return if (count > 0) sum / count else 0f
    }

    private fun lastEntrySummary(data: AppData, habit: Habit): String? {
        val dates = data.entries.keys.sortedDescending()
        for (date in dates) {
            val e = data.entries[date]?.get(habit.id) ?: continue
            val v = when {
                e.skipped -> "skipped"
                habit.type == HabitType.CHECKBOX && e.value >= 0.5 -> "done"
                else -> {
                    val u = habit.unit
                    "${e.value}${if (u.isNotBlank()) " $u" else ""}"
                }
            }
            return "$v on $date"
        }
        return null
    }

    private fun pct(rate: Float): String = "${(rate * 100).toInt().coerceIn(0, 100)}%"

    /**
     * Captures for pickers, newest first.
     * @param tagFilter single tag (legacy UI filter)
     * @param tagsAnyOf multi-select: match any of these tags
     * @param scope time window
     */
    fun selectableCaptures(
        data: AppData,
        tagFilter: String? = null,
        tagsAnyOf: Set<String> = emptySet(),
        scope: CaptureTimeScope = CaptureTimeScope.ALL
    ): List<CaptureItem> {
        val cutoff = scopeCutoffMs(scope)
        return data.captures
            .asSequence()
            .filter { cutoff == null || it.createdAt >= cutoff }
            .filter { cap ->
                when {
                    tagsAnyOf.isNotEmpty() ->
                        cap.tags.any { t -> tagsAnyOf.any { sel -> sel.equals(t, ignoreCase = true) } }
                    tagFilter != null ->
                        cap.tags.any { it.equals(tagFilter, ignoreCase = true) }
                    else -> true
                }
            }
            .sortedByDescending { it.createdAt }
            .toList()
    }

    fun scopeCutoffMs(scope: CaptureTimeScope, nowMs: Long = System.currentTimeMillis()): Long? {
        val days = scope.days ?: return null
        val zone = ZoneId.systemDefault()
        val startOfToday = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        return if (days <= 1) startOfToday
        else startOfToday - TimeUnit.DAYS.toMillis((days - 1).toLong())
    }

    fun selectableHabits(data: AppData): List<Habit> =
        data.habits.filter { !it.archived }.sortedBy { it.name.lowercase(Locale.getDefault()) }

    /** Tag chips for capture multi-select. */
    fun captureFilterTags(): List<String> = listOf(
        CaptureTags.IDEAS,
        CaptureTags.NOTES,
        CaptureTags.TODO,
        CaptureTags.THOUGHTS,
        CaptureTags.GRATITUDE,
        CaptureTags.MEMORIES,
        CaptureTags.CHECKIN,
        CaptureTags.REMINDERS,
        CaptureTags.ENERGY,
        CaptureTags.DISTRACTIONS
    )

    /** Per-habit 30d completion for History square sizing (0–1). */
    fun habitSquareMetrics(data: AppData): List<HabitSquareMetric> {
        return selectableHabits(data).map { h ->
            val avg30 = habitDueAvg(data, h, 30)
            val avg7 = habitDueAvg(data, h, 7)
            HabitSquareMetric(
                habit = h,
                avg30 = avg30,
                avg7 = avg7,
                score = avg30
            )
        }.sortedByDescending { it.score }
    }
}

data class HabitSquareMetric(
    val habit: Habit,
    val avg30: Float,
    val avg7: Float,
    val score: Float
)
