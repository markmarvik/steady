package com.steady.habittracker.data

import java.util.UUID

/**
 * Pure helpers for Deep Work sessions and daily MITs (Most Important Tasks).
 */
object ProductivityDomain {

    const val MAX_MITS_PER_DAY = 3
    /** Momentum points per completed MIT (added in day points breakdown). */
    const val MIT_POINT_VALUE = 5
    /** Cap retained MIT rows (done + open history). */
    const val MAX_MIT_HISTORY = 200

    fun mitsForDate(data: AppData, date: String): List<MitItem> =
        data.mits.filter { it.date == date }.sortedBy { it.order }

    fun openMitsForDate(data: AppData, date: String): List<MitItem> =
        mitsForDate(data, date).filter { !it.done }

    fun doneMitsForDate(data: AppData, date: String): List<MitItem> =
        mitsForDate(data, date).filter { it.done }

    fun canAddMit(data: AppData, date: String): Boolean =
        openMitsForDate(data, date).size < MAX_MITS_PER_DAY

    fun addMit(
        data: AppData,
        title: String,
        date: String,
        captureId: String? = null
    ): AppData {
        val clean = title.trim().take(120)
        if (clean.isEmpty()) return data
        if (!canAddMit(data, date)) return data
        val order = openMitsForDate(data, date).maxOfOrNull { it.order }?.plus(1) ?: 0
        val item = MitItem(
            id = "mit_${UUID.randomUUID().toString().take(8)}",
            title = clean,
            date = date,
            captureId = captureId,
            order = order
        )
        return data.withMits(data.mits + item)
    }

    fun completeMit(data: AppData, mitId: String, nowMs: Long = System.currentTimeMillis()): AppData {
        val next = data.mits.map { m ->
            if (m.id == mitId && !m.done) m.copy(done = true, completedAt = nowMs) else m
        }
        var out = data.withMits(next)
        // If linked to a todo capture, mark processed
        val mit = data.mits.find { it.id == mitId }
        val capId = mit?.captureId
        if (capId != null) {
            val cap = out.captures.find { it.id == capId }
            if (cap != null && !cap.processed) {
                out = out.withUpdatedCapture(cap.copy(processed = true))
            }
        }
        return out
    }

    fun demoteMit(data: AppData, mitId: String): AppData {
        val mit = data.mits.find { it.id == mitId } ?: return data
        var out = data.withMits(data.mits.filterNot { it.id == mitId })
        // Return to todo inbox if it was a free-form MIT (no capture) — create one
        if (mit.captureId == null && mit.title.isNotBlank() && !mit.done) {
            out = out.withAddedCapture(
                CaptureItem(
                    id = "c_${UUID.randomUUID().toString().take(8)}",
                    title = mit.title,
                    tags = listOf(CaptureTags.TODO),
                    processed = false
                )
            )
        }
        return out
    }

    fun removeMit(data: AppData, mitId: String): AppData =
        data.withMits(data.mits.filterNot { it.id == mitId })

    /**
     * Promote a Todo capture into today's MIT list (if room).
     */
    fun promoteCaptureToMit(data: AppData, captureId: String, date: String): AppData {
        if (!canAddMit(data, date)) return data
        val cap = data.captures.find { it.id == captureId } ?: return data
        if (cap.isTrashed) return data
        // Avoid duplicate promote of same capture open on this day
        if (openMitsForDate(data, date).any { it.captureId == captureId }) return data
        return addMit(data, cap.title.ifBlank { cap.note }.ifBlank { "Todo" }, date, captureId)
    }

    /**
     * If [today] has no MITs yet, carry up to 3 unfinished MITs from the most recent prior day.
     * Idempotent when today already has any MIT rows.
     */
    fun withCarriedMits(data: AppData, today: String): AppData {
        if (data.mits.any { it.date == today }) return data
        val priorDates = data.mits.map { it.date }.filter { it < today }.distinct().sorted()
        if (priorDates.isEmpty()) return data
        val fromDate = priorDates.last()
        val openPrior = openMitsForDate(data, fromDate).sortedBy { it.order }.take(MAX_MITS_PER_DAY)
        if (openPrior.isEmpty()) return data
        val carried = openPrior.mapIndexed { i, m ->
            MitItem(
                id = "mit_${UUID.randomUUID().toString().take(8)}",
                title = m.title,
                date = today,
                captureId = m.captureId,
                order = i,
                carriedFrom = fromDate
            )
        }
        return data.withMits(trimMitHistory(data.mits + carried))
    }

    fun trimMitHistory(mits: List<MitItem>): List<MitItem> {
        if (mits.size <= MAX_MIT_HISTORY) return mits
        // Keep all open; drop oldest done first
        val open = mits.filter { !it.done }
        val done = mits.filter { it.done }.sortedByDescending { it.completedAt }
        val room = (MAX_MIT_HISTORY - open.size).coerceAtLeast(0)
        return open + done.take(room)
    }

    // —— Deep Work ——

    fun startDeepWorkSession(
        data: AppData,
        habitId: String?,
        intent: String = "",
        plannedMinutes: Int? = null,
        nowMs: Long = System.currentTimeMillis()
    ): AppData {
        val p = data.deepWorkPrefs
        if (p.isSessionActive()) return data
        val mins = (plannedMinutes ?: p.effectiveDefaultMinutes()).coerceIn(15, 240)
        return data.withDeepWorkPrefs(
            p.copy(
                sessionStartedAt = nowMs,
                sessionPlannedMinutes = mins,
                sessionIntent = intent.trim().take(120),
                sessionHabitId = habitId
            )
        )
    }

    /**
     * Finish active session → clear prefs, return minutes worked + note intent.
     * Does not write the habit entry (caller logs).
     */
    fun finishDeepWorkSession(
        data: AppData,
        nowMs: Long = System.currentTimeMillis()
    ): Pair<AppData, DeepWorkFinish?> {
        val p = data.deepWorkPrefs
        if (!p.isSessionActive()) return data to null
        val mins = p.elapsedMinutes(nowMs).coerceAtLeast(1)
        val finish = DeepWorkFinish(
            minutes = mins,
            plannedMinutes = p.sessionPlannedMinutes,
            intent = p.sessionIntent,
            habitId = p.sessionHabitId
        )
        val next = data.withDeepWorkPrefs(
            p.clearSession().copy(
                lastCompletedAt = nowMs,
                lastCompletedMinutes = mins,
                lastIntent = p.sessionIntent
            )
        )
        return next to finish
    }

    fun cancelDeepWorkSession(data: AppData): AppData =
        data.withDeepWorkPrefs(data.deepWorkPrefs.clearSession())

    fun deepWorkStatusLine(data: AppData, nowMs: Long = System.currentTimeMillis()): String {
        val p = data.deepWorkPrefs
        return when {
            p.isSessionActive() -> {
                val left = p.remainingMinutes(nowMs)
                val intent = p.sessionIntent.takeIf { it.isNotBlank() }
                listOfNotNull("${left}m left", intent).joinToString(" · ")
            }
            p.lastCompletedMinutes > 0 -> {
                val intent = p.lastIntent.takeIf { it.isNotBlank() }
                listOfNotNull("Last ${p.lastCompletedMinutes}m", intent).joinToString(" · ")
            }
            else -> "Start deep work · ${p.effectiveDefaultMinutes()}m"
        }
    }

    /** Ensure a Deep Work habit exists in a WORK/Focus-ish group. */
    fun ensureDeepWorkHabit(data: AppData): AppData {
        if (data.habits.any { !it.archived && it.extensionType == ExtensionType.DEEP_WORK }) {
            return data
        }
        val template = ExtensionCatalog.templateFor(ExtensionType.DEEP_WORK) ?: return data
        val gid = ExtensionCatalog.suggestGroupId(data, template.suggestTimeHint) ?: return data
        val order = data.habits.count { it.groupId == gid }
        val habit = Habit(
            id = "h_deep_work",
            name = template.defaultName,
            description = "Intentional focus block",
            groupId = gid,
            type = HabitType.CHECKBOX,
            order = order,
            icon = template.defaultIcon,
            extensionType = ExtensionType.DEEP_WORK,
            extensionConfig = template.defaultConfig,
            pointValue = data.deepWorkPrefs.pointValue.coerceIn(5, 50),
            canSkip = true
        )
        return data.withAddedHabit(habit)
    }

    fun disableDeepWorkHabit(data: AppData): AppData {
        val archived = data.habits.map { h ->
            if (!h.archived && h.extensionType == ExtensionType.DEEP_WORK) h.copy(archived = true) else h
        }
        return data.copy(
            habits = archived,
            deepWorkPrefs = data.deepWorkPrefs.clearSession()
        )
    }
}

data class DeepWorkFinish(
    val minutes: Int,
    val plannedMinutes: Int,
    val intent: String,
    val habitId: String?
)
