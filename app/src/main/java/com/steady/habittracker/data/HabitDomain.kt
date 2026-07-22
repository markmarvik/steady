package com.steady.habittracker.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Domain / pure computation helpers extracted from Repository.
 * Addresses #14 (move business logic out of data layer).
 *
 * These are stateless, easy to unit test (see #15), and have no Android / IO side effects.
 * Repository can delegate or UI can use directly for derived state.
 */
object HabitDomain {

    /** Returns sorted groups + habits in each for Manage catalog (non-archived only).
     * Primary [Habit.groupId] only — additional groups are display-only on Today (#24). */
    fun groupHabits(data: AppData): List<Pair<Group, List<Habit>>> {
        val sortedGroups = data.groups.filter { !it.archived }.sortedBy { it.order }
        return sortedGroups.map { g ->
            g to data.habits.filter { it.groupId == g.id && !it.archived }.sortedBy { it.order }
        }
    }

    /**
     * Timeline groups this habit belongs to (distinct, stable order).
     * Storage still uses [Habit.groupId] + [Habit.additionalGroupIds]; UI treats membership as a flat set.
     */
    fun membershipGroupIds(habit: Habit): List<String> =
        (listOf(habit.groupId) + habit.additionalGroupIds)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    /** Whether habit belongs to [groupId] (once per group — no duplicates). */
    fun belongsToGroup(habit: Habit, groupId: String): Boolean =
        groupId in membershipGroupIds(habit)

    /**
     * Rewrite membership from an ordered group id list (first becomes [Habit.groupId]).
     * Empty input returns the habit unchanged (always keep ≥1 group).
     */
    fun withMembership(habit: Habit, groupIds: List<String>): Habit {
        val ids = groupIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (ids.isEmpty()) return habit
        return habit.copy(
            groupId = ids.first(),
            additionalGroupIds = ids.drop(1)
        )
    }

    /** Add habit to [groupId] if not already a member (no-op if already in). */
    fun addToGroup(habit: Habit, groupId: String): Habit {
        val gid = groupId.trim()
        if (gid.isEmpty() || belongsToGroup(habit, gid)) return habit
        return withMembership(habit, membershipGroupIds(habit) + gid)
    }

    /**
     * Remove habit from [groupId]. Returns null if that would leave zero groups
     * (caller should refuse or archive instead).
     */
    fun removeFromGroup(habit: Habit, groupId: String): Habit? {
        val ids = membershipGroupIds(habit).filter { it != groupId }
        if (ids.isEmpty()) return null
        return withMembership(habit, ids)
    }

    // --- Show rules (when habit appears on Today) ---

    /** Whether [habit] should appear on the given calendar [date] based on showPreset. */
    fun isDueOn(habit: Habit, date: LocalDate): Boolean {
        if (habit.archived) return false
        val dow = date.dayOfWeek.value // 1=Mon … 7=Sun
        return when (habit.showPreset) {
            ShowPreset.DAILY -> true
            ShowPreset.WEEKDAYS -> dow in 1..5
            ShowPreset.WEEKENDS -> dow in 6..7
            ShowPreset.CUSTOM_DAYS -> {
                val days = if (habit.weekdays.isEmpty()) (1..7).toSet() else habit.weekdays
                dow in days
            }
            ShowPreset.EVERY_N_DAYS -> {
                val n = habit.intervalDays.coerceAtLeast(1)
                val anchor = try {
                    LocalDate.parse(habit.anchorDate ?: "1970-01-01")
                } catch (_: Exception) {
                    LocalDate.of(1970, 1, 1)
                }
                val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(anchor, date)
                if (daysBetween < 0) false else daysBetween % n == 0L
            }
            ShowPreset.SPECIFIC_DATES -> date.toString() in habit.specificDates
        }
    }

    fun isDueOn(habit: Habit, dateStr: String): Boolean =
        try {
            isDueOn(habit, LocalDate.parse(dateStr))
        } catch (_: Exception) {
            false
        }

    /** Active non-archived habits due on [date]. */
    fun habitsDueOn(data: AppData, date: LocalDate = LocalDate.now()): List<Habit> =
        data.habits.filter { !it.archived && isDueOn(it, date) }

    fun isPendingEntry(entry: HabitEntry?): Boolean =
        (entry?.value ?: 0.0) < 0.5 && entry?.skipped != true

    /**
     * Pending (due + not done/skipped) habits for a date, sorted by group → stack → order.
     * Returns list of (group, ordered habits) in **catalog** order.
     * Prefer [timelineSectionsForToday] for day progression UI.
     */
    fun pendingGroupedForDate(
        data: AppData,
        date: LocalDate = LocalDate.now()
    ): List<Pair<Group, List<Habit>>> {
        val dateStr = date.toString()
        val entries = data.entries[dateStr] ?: emptyMap()
        val due = habitsDueOn(data, date).filter { isPendingEntry(entries[it.id]) }
        // Expand multi-group membership so one habit can appear in several sections (#24)
        val byGroup = mutableMapOf<String, MutableList<Habit>>()
        due.forEach { h ->
            membershipGroupIds(h).forEach { gid ->
                byGroup.getOrPut(gid) { mutableListOf() }.add(h)
            }
        }
        return getActiveGroups(data).mapNotNull { g ->
            val inGroup = byGroup[g.id] ?: return@mapNotNull null
            if (inGroup.isEmpty()) null else g to sortByStack(inGroup, data.habits)
        }
    }

    /**
     * Pure visual day progression: sections top→bottom in day order (Morning → … → Sleep).
     * No clock labels — only names + isNow / isPast for a soft “you are here” cue.
     * By default only pending habits (action-oriented Today).
     * [includeCompleted] also shows finished items (grayed in UI) so streaks stay visible.
     */
    fun timelineSectionsForToday(
        data: AppData,
        date: LocalDate = LocalDate.now(),
        now: LocalTime = LocalTime.now(),
        includeCompleted: Boolean = false
    ): List<TimelineSection> {
        val dateStr = date.toString()
        val entries = data.entries[dateStr] ?: emptyMap()
        val due = habitsDueOn(data, date)
        val shown = if (includeCompleted) {
            // Pending + completed; keep skipped out of the grid (they already left the day).
            due.filter { h ->
                val e = entries[h.id]
                isPendingEntry(e) || ((e?.value ?: 0.0) >= 0.5 && e?.skipped != true)
            }
        } else {
            due.filter { isPendingEntry(entries[it.id]) }
        }
        if (shown.isEmpty()) return emptyList()

        // Multi-group expansion (#24): habit appears under every membership group
        val byGroup = mutableMapOf<String, MutableList<Habit>>()
        shown.forEach { h ->
            membershipGroupIds(h).forEach { gid ->
                byGroup.getOrPut(gid) { mutableListOf() }.add(h)
            }
        }
        val orderedIds = orderedGroupIdsForDay(data, now)
        val currentId = resolveCurrentGroup(data, now)?.id
        val currentIndex = orderedIds.indexOf(currentId).takeIf { it >= 0 }

        fun orderForSection(habits: List<Habit>): List<Habit> {
            val stacked = sortByStack(habits, data.habits)
            if (!includeCompleted) return stacked
            val pending = stacked.filter { isPendingEntry(entries[it.id]) }
            val done = stacked.filter { !isPendingEntry(entries[it.id]) }
            return pending + done
        }

        val sections = mutableListOf<TimelineSection>()
        orderedIds.forEachIndexed { index, gid ->
            val habits = byGroup[gid] ?: return@forEachIndexed
            if (habits.isEmpty()) return@forEachIndexed
            val group = data.groups.find { it.id == gid && !it.archived } ?: return@forEachIndexed
            val isNow = gid == currentId
            val isPast = currentIndex != null && index < currentIndex
            val isFuture = currentIndex != null && index > currentIndex
            sections.add(
                TimelineSection(
                    group = group,
                    habits = orderForSection(habits),
                    isNow = isNow,
                    isPast = isPast && !isNow,
                    isFuture = isFuture && !isNow
                )
            )
        }
        // Pending in groups missing from spine (shouldn't happen often)
        val seen = sections.map { it.group.id }.toSet()
        byGroup.forEach { (gid, habits) ->
            if (gid in seen || habits.isEmpty()) return@forEach
            val group = data.groups.find { it.id == gid && !it.archived } ?: return@forEach
            sections.add(
                TimelineSection(
                    group = group,
                    habits = orderForSection(habits),
                    isNow = gid == currentId,
                    isPast = false,
                    isFuture = true
                )
            )
        }
        return sections
    }

    /**
     * Day spine group ids: schedule blocks in visual day order, then unscheduled groups.
     * Overnight blocks (start > end) sort by start time (typically late evening → bottom).
     */
    fun orderedGroupIdsForDay(data: AppData, now: LocalTime = LocalTime.now()): List<String> {
        val schedule = getActiveSchedule(data)
        val useSchedule = schedule != null &&
            isScheduleApplicableToday(data, schedule) &&
            schedule.timeBlocks.isNotEmpty()

        val fromSchedule = if (useSchedule) {
            schedule!!.timeBlocks
                .sortedWith(
                    compareBy<TimeBlock> { blockSortKey(it) }
                        .thenBy { parseTimeToMinutes(it.start) }
                )
                .map { it.groupId }
                .distinct()
        } else {
            // Fallback: timeHint progression, then catalog order
            val hintRank = mapOf(
                "MORNING" to 0,
                "WORK" to 1,
                "REVIEW" to 2,
                "EVENING" to 3,
                "BEDTIME" to 3,
                "SLEEP" to 4,
                "ANY" to 5
            )
            getActiveGroups(data)
                .sortedWith(
                    compareBy<Group> { hintRank[it.timeHint.uppercase()] ?: 5 }
                        .thenBy { it.order }
                )
                .map { it.id }
        }

        val scheduledSet = fromSchedule.toSet()
        val extras = getActiveGroups(data)
            .filter { it.id !in scheduledSet }
            .sortedBy { it.order }
            .map { it.id }
        return fromSchedule + extras
    }

    /** Sort key so the day reads top→bottom; overnight sleep sits near the end. */
    private fun blockSortKey(block: TimeBlock): Int {
        val start = parseTimeToMinutes(block.start)
        val end = parseTimeToMinutes(block.end)
        // Overnight block: treat as late-day (start near end of day)
        return if (start > end) start else start
    }

    /** Short spine line: current group name + next group with pending work (names only). */
    fun dayProgressionCue(
        data: AppData,
        date: LocalDate = LocalDate.now(),
        now: LocalTime = LocalTime.now()
    ): DayProgressionCue {
        // Single timeline pass — avoid calling timelineSectionsForToday twice from UI.
        val sections = timelineSectionsForToday(data, date, now)
        return dayProgressionCueFromSections(sections)
    }

    /** Derive day-cue from already-built sections (cheap; use on Today list path). */
    fun dayProgressionCueFromSections(sections: List<TimelineSection>): DayProgressionCue {
        val nowSection = sections.firstOrNull { it.isNow }
        val nextPending = sections.firstOrNull { !it.isNow && it.isFuture }
            ?: sections.firstOrNull { !it.isNow }
        return DayProgressionCue(
            nowGroupName = nowSection?.group?.let { DisplayIcon.label(it.icon, it.name) },
            nowHasPending = nowSection != null && nowSection.habits.isNotEmpty(),
            nextGroupName = nextPending?.group?.let { DisplayIcon.label(it.icon, it.name) }
        )
    }

    /** Pre-resolve tag display names for all habits (O(tags + habits) once per frame of data). */
    fun tagLabelByHabitId(data: AppData): Map<String, String> {
        val byId = data.tags.associateBy { it.id }
        val out = HashMap<String, String>(data.habits.size)
        for (h in data.habits) {
            if (h.archived) continue
            val names = h.tags.mapNotNull { byId[it]?.name }.toMutableList()
            if (h.isSupplement && names.none { it.equals("Supplements", true) }) {
                names.add("Supplements")
            }
            if (names.isNotEmpty()) out[h.id] = names.joinToString(" · ")
        }
        return out
    }

    /**
     * Soft-stack sort: roots by order, then followers after their predecessor.
     * Broken links fall back to flat order.
     */
    fun sortByStack(habits: List<Habit>, allHabits: List<Habit> = habits): List<Habit> {
        if (habits.isEmpty()) return emptyList()
        val byId = habits.associateBy { it.id }
        val remaining = habits.toMutableList()
        val result = mutableListOf<Habit>()

        fun chainFrom(root: Habit) {
            var current: Habit? = root
            val seen = mutableSetOf<String>()
            while (current != null && current.id !in seen) {
                seen.add(current.id)
                if (current.id in byId) {
                    remaining.removeAll { it.id == current!!.id }
                    result.add(current)
                }
                val next = remaining.firstOrNull { it.afterHabitId == current!!.id }
                    ?: habits.firstOrNull { it.afterHabitId == current!!.id && it.id !in seen && it !in result }
                current = next
            }
        }

        // Roots: afterHabitId null or points outside this set
        val roots = habits.filter { h ->
            h.afterHabitId == null || h.afterHabitId !in byId
        }.sortedBy { it.order }

        roots.forEach { chainFrom(it) }
        // Any leftovers (cycles / orphans)
        remaining.sortedBy { it.order }.forEach { if (it !in result) result.add(it) }
        return result
    }

    /** Short label for Manage / chips: "Daily", "Weekdays", "Every 2d", … */
    fun showRuleLabel(habit: Habit): String = when (habit.showPreset) {
        ShowPreset.DAILY -> "Daily"
        ShowPreset.WEEKDAYS -> "Weekdays"
        ShowPreset.WEEKENDS -> "Weekends"
        ShowPreset.CUSTOM_DAYS -> {
            val names = listOf("", "M", "T", "W", "T", "F", "S", "S")
            habit.weekdays.sorted().joinToString("") { names.getOrElse(it) { "?" } }
                .ifBlank { "Custom" }
        }
        ShowPreset.EVERY_N_DAYS -> "Every ${habit.intervalDays.coerceAtLeast(1)}d"
        ShowPreset.SPECIFIC_DATES ->
            if (habit.specificDates.isEmpty()) "No dates"
            else "${habit.specificDates.size} date(s)"
    }

    /** Compute current streak using due-aware completion. */
    fun computeStreak(data: AppData): Int {
        if (data.habits.none { !it.archived }) return 0
        var streak = 0
        var checkDate = LocalDate.parse(getToday())

        while (true) {
            val due = habitsDueOn(data, checkDate)
            val key = checkDate.toString()
            val dayEntries = data.entries[key] ?: emptyMap()
            val completedCount = due.count { h -> (dayEntries[h.id]?.value ?: 0.0) >= 0.5 }
            val ok = when {
                due.isEmpty() -> true // nothing due — skip day without breaking
                completedCount == due.size -> true
                completedCount >= 3 -> true
                completedCount.toFloat() / due.size >= 0.6f -> true
                else -> false
            }
            if (!ok) break
            if (due.isNotEmpty()) streak++ // only count days that had something to do
            checkDate = checkDate.minusDays(1)
            if (streak > 365) break
        }
        return streak
    }

    /**
     * Consecutive due-days this habit was completed (value ≥ 0.5, not skipped).
     * Days the habit was not due are skipped without breaking the chain.
     * If today is still pending, the streak starts from yesterday (streak not lost mid-day).
     */
    fun computeHabitStreak(data: AppData, habitId: String): Int {
        val habit = data.habits.find { it.id == habitId && !it.archived } ?: return 0
        var streak = 0
        var checkDate = LocalDate.parse(getToday())
        var started = false
        repeat(400) {
            val dueToday = isDueOn(habit, checkDate)
            val key = checkDate.toString()
            val entry = data.entries[key]?.get(habitId)
            val done = entry != null && !entry.skipped && entry.value >= 0.5
            if (dueToday) {
                if (done) {
                    streak++
                    started = true
                } else {
                    // Today still open: don't break yet — look at prior days.
                    if (started || checkDate != LocalDate.parse(getToday())) {
                        return streak
                    }
                }
            }
            checkDate = checkDate.minusDays(1)
        }
        return streak
    }

    /** Flame glyph that “grows” with streak length (UI companion to [computeHabitStreak]). */
    fun streakFlameEmoji(streak: Int): String = when {
        streak <= 0 -> ""
        streak < 3 -> "🕯️"
        streak < 7 -> "🔥"
        streak < 14 -> "🔥"
        streak < 30 -> "🔥"
        else -> "🔥"
    }

    /** Completion among habits *due* that day (not full catalog). */
    fun computeDayCompletion(data: AppData, dateStr: String): Float {
        val date = try {
            LocalDate.parse(dateStr)
        } catch (_: Exception) {
            return 0f
        }
        val due = habitsDueOn(data, date)
        if (due.isEmpty()) return 1f // nothing due = fully "done"
        val dayMap = data.entries[dateStr] ?: emptyMap()
        val done = due.count { h -> (dayMap[h.id]?.value ?: 0.0) >= 0.5 }
        return done.toFloat() / due.size
    }

    /** Last N days of overall rates, oldest first. Returns list of (date, rate). */
    fun computeLastNDays(data: AppData, n: Int = 7): List<Pair<String, Float>> {
        val result = mutableListOf<Pair<String, Float>>()
        var d = LocalDate.now()
        repeat(n) {
            val key = d.toString()
            result.add(key to computeDayCompletion(data, key))
            d = d.minusDays(1)
        }
        return result.reversed()
    }

    /** Log counts per day for last N days (oldest first). Done (value≥0.5) + total entries. */
    fun computeLastNDaysLogCounts(data: AppData, n: Int = 30): List<Triple<String, Int, Int>> {
        val result = mutableListOf<Triple<String, Int, Int>>()
        var d = LocalDate.now()
        repeat(n) {
            val key = d.toString()
            val dayMap = data.entries[key] ?: emptyMap()
            val done = dayMap.count { (_, e) -> e.value >= 0.5 && !e.skipped }
            result.add(0, Triple(key, done, dayMap.size))
            d = d.minusDays(1)
        }
        return result
    }

    /**
     * Calendar heatmap cells for [weeks] weeks ending today (GitHub/Anki style).
     * Each cell: date string, completion rate 0..1 (null = nothing due / no data day with 0 entries).
     * Rows are weekdays Mon=0…Sun=6; columns are weeks oldest→newest.
     */
    fun computeHeatmap(data: AppData, weeks: Int = 16): List<List<Pair<String, Float?>>> {
        val today = LocalDate.now()
        // Align end to today; start so we fill full weeks (Mon start)
        val days = weeks * 7
        val start = today.minusDays((days - 1).toLong())
        // Pad start back to Monday
        val pad = (start.dayOfWeek.value - 1).toLong() // Mon=1 → 0
        val gridStart = start.minusDays(pad)
        val columns = mutableListOf<List<Pair<String, Float?>>>()
        var colStart = gridStart
        while (!colStart.isAfter(today)) {
            val week = (0..6).map { offset ->
                val d = colStart.plusDays(offset.toLong())
                val key = d.toString()
                if (d.isAfter(today)) {
                    key to null
                } else {
                    val due = habitsDueOn(data, d)
                    val dayMap = data.entries[key] ?: emptyMap()
                    when {
                        due.isEmpty() && dayMap.isEmpty() -> key to null
                        due.isEmpty() -> key to 1f
                        else -> {
                            val done = due.count { h -> (dayMap[h.id]?.value ?: 0.0) >= 0.5 }
                            key to (done.toFloat() / due.size)
                        }
                    }
                }
            }
            columns.add(week)
            colStart = colStart.plusWeeks(1)
        }
        return columns
    }

    /** Hour-of-day histogram (0..23) of log timestamps across all entries. */
    fun computeHourlyLogCounts(data: AppData): IntArray {
        val hours = IntArray(24)
        data.entries.values.forEach { day ->
            day.values.forEach { e ->
                if (e.loggedAt > 0) {
                    val h = java.time.Instant.ofEpochMilli(e.loggedAt)
                        .atZone(java.time.ZoneId.systemDefault())
                        .hour
                    hours[h]++
                }
            }
        }
        return hours
    }

    /** Total done logs (value≥0.5, not skipped) across history. */
    fun totalCompletedLogs(data: AppData): Int =
        data.entries.values.sumOf { day -> day.values.count { it.value >= 0.5 && !it.skipped } }

    /** Days with at least one done log. */
    fun daysWithActivity(data: AppData): Int =
        data.entries.count { (_, map) -> map.values.any { it.value >= 0.5 && !it.skipped } }

    /** 7-day average completion for a group (only habits due that day count). */
    fun computeGroup7DayAvg(data: AppData, groupId: String): Float {
        var sum = 0f
        var count = 0
        var d = LocalDate.now()
        repeat(7) {
            val due = habitsDueOn(data, d).filter { it.groupId == groupId }
            if (due.isNotEmpty()) {
                val key = d.toString()
                val dayMap = data.entries[key] ?: emptyMap()
                val done = due.count { h -> (dayMap[h.id]?.value ?: 0.0) >= 0.5 }
                sum += done.toFloat() / due.size
                count++
            }
            d = d.minusDays(1)
        }
        return if (count > 0) sum / count else 0f
    }

    /** Active (non-archived) tags, sorted. */
    fun getActiveTags(data: AppData): List<Tag> =
        data.tags.filter { !it.archived }.sortedBy { it.order }

    /** Habits carrying a given tag (active). */
    fun habitsWithTag(data: AppData, tagId: String): List<Habit> =
        data.habits.filter { !it.archived && (tagId in it.tags || (tagId == TagIds.SUPPLEMENTS && it.isSupplement)) }

    /**
     * 7-day average completion for a tag — independent of which timeline group the habit sits in.
     * So morning Omega-3 still counts toward Supplements even if group = Morning.
     */
    fun computeTag7DayAvg(data: AppData, tagId: String): Float {
        var sum = 0f
        var count = 0
        var d = LocalDate.now()
        repeat(7) {
            val due = habitsDueOn(data, d).filter { h ->
                tagId in h.tags || (tagId == TagIds.SUPPLEMENTS && h.isSupplement)
            }
            if (due.isNotEmpty()) {
                val key = d.toString()
                val dayMap = data.entries[key] ?: emptyMap()
                val done = due.count { h -> (dayMap[h.id]?.value ?: 0.0) >= 0.5 }
                sum += done.toFloat() / due.size
                count++
            }
            d = d.minusDays(1)
        }
        return if (count > 0) sum / count else 0f
    }

    /** Today’s completion rate for a tag (due items only). */
    fun computeTagDayCompletion(data: AppData, tagId: String, date: LocalDate = LocalDate.now()): Float {
        val due = habitsDueOn(data, date).filter { h ->
            tagId in h.tags || (tagId == TagIds.SUPPLEMENTS && h.isSupplement)
        }
        if (due.isEmpty()) return 1f
        val dayMap = data.entries[date.toString()] ?: emptyMap()
        val done = due.count { h -> (dayMap[h.id]?.value ?: 0.0) >= 0.5 }
        return done.toFloat() / due.size
    }

    fun tagNamesForHabit(data: AppData, habit: Habit): List<String> {
        val byId = data.tags.associateBy { it.id }
        val names = habit.tags.mapNotNull { byId[it]?.name }
        return if (habit.isSupplement && names.none { it.equals("Supplements", true) }) {
            names + "Supplements"
        } else names
    }

    // --- Sleep-centered 24h timeline ---

    /**
     * Build time blocks from sleep settings + group ids.
     * Order on the day: Morning (at wake) → optional middle blocks kept → Bedtime → Sleep overnight.
     * [existingMiddle] are non-sleep/morning/bedtime blocks to preserve (Focus, etc.).
     */
    fun buildSleepAnchoredBlocks(
        sleep: SleepSettings,
        morningGroupId: String,
        bedtimeGroupId: String,
        sleepGroupId: String,
        existingMiddle: List<TimeBlock> = emptyList()
    ): List<TimeBlock> {
        val wake = parseTimeToMinutes(sleep.wakeTime).coerceIn(0, 24 * 60 - 1)
        val bed = parseTimeToMinutes(sleep.bedTime).coerceIn(0, 24 * 60 - 1)
        val morningEnd = (wake + sleep.morningMinutes.coerceIn(15, 180)).coerceAtMost(
            if (bed > wake) bed else 24 * 60
        )
        val windStart = run {
            val raw = bed - sleep.windDownMinutes.coerceIn(15, 180)
            if (raw < 0) raw + 24 * 60 else raw
        }

        val morning = TimeBlock(
            start = minutesToHhMm(wake),
            end = minutesToHhMm(morningEnd),
            groupId = morningGroupId,
            color = 0xFFFBBF24.toInt() // amber
        )
        val bedtime = TimeBlock(
            start = minutesToHhMm(windStart),
            end = minutesToHhMm(bed),
            groupId = bedtimeGroupId,
            color = 0xFF8B5CF6.toInt() // purple
        )
        val sleepBlock = TimeBlock(
            start = minutesToHhMm(bed),
            end = minutesToHhMm(wake),
            groupId = sleepGroupId,
            color = 0xFF64748B.toInt() // slate
        )

        // Keep middle blocks that don't overlap sleep anchors (simple: not those group ids)
        val reserved = setOf(morningGroupId, bedtimeGroupId, sleepGroupId)
        val middle = existingMiddle.filter { it.groupId !in reserved }

        return listOf(morning) + middle + listOf(bedtime, sleepBlock)
    }

    /**
     * Ensure Sleep / Morning / Bedtime groups exist and sleep settings point at them.
     * Returns updated AppData (groups + sleep only).
     */
    fun ensureSleepLinkedGroups(data: AppData): AppData {
        var groups = data.groups.toMutableList()
        fun findOrAdd(preferredId: String, name: String, hint: String, order: Int): String {
            val existing = groups.firstOrNull {
                !it.archived && (
                    it.id == preferredId ||
                        it.timeHint == hint ||
                        it.name.equals(name, ignoreCase = true) ||
                        (hint == "MORNING" && it.name.contains("morning", true)) ||
                        (hint == "BEDTIME" && (it.name.contains("bed", true) || it.name.contains("wind", true) || it.name.contains("evening", true))) ||
                        (hint == "SLEEP" && it.name.equals("sleep", true))
                    )
            }
            if (existing != null) return existing.id
            val icon = when (hint) {
                "MORNING" -> "🌅"
                "BEDTIME", "EVENING" -> "🌙"
                "SLEEP" -> "💤"
                "WORK" -> "🎯"
                else -> ""
            }
            groups.add(Group(preferredId, name, hint, order = order, icon = icon))
            return preferredId
        }
        val mornId = data.sleep.morningGroupId
            ?: findOrAdd("g_morn", "Morning Routine", "MORNING", 0)
        val bedId = data.sleep.bedtimeGroupId
            ?: findOrAdd("g_even", "Bedtime / Wind Down", "BEDTIME", 2)
        // Prefer BEDTIME hint; also accept existing Evening group
        val bedResolved = groups.firstOrNull { it.id == bedId }?.id
            ?: findOrAdd("g_even", "Bedtime / Wind Down", "EVENING", 2)
        val sleepId = data.sleep.sleepGroupId
            ?: findOrAdd("g_sleep", "Sleep", "SLEEP", 10)

        // Ensure sleep group name/hint if we created/found it
        groups = groups.map {
            when (it.id) {
                sleepId -> it.copy(timeHint = "SLEEP", name = if (it.name.isBlank()) "Sleep" else it.name)
                else -> it
            }
        }.toMutableList()
        if (groups.none { it.id == sleepId }) {
            groups.add(Group(sleepId, "Sleep", "SLEEP", order = 10))
        }

        val sleep = data.sleep.copy(
            morningGroupId = mornId,
            bedtimeGroupId = bedResolved,
            sleepGroupId = sleepId
        )
        return data.copy(groups = groups.sortedBy { it.order }, sleep = sleep)
    }

    fun minutesToHhMm(totalMin: Int): String {
        val m = ((totalMin % (24 * 60)) + (24 * 60)) % (24 * 60)
        val h = m / 60
        val min = m % 60
        return "%02d:%02d".format(h, min)
    }

    /** Simple time-of-day group hint for widget / Today highlight. */
    fun getCurrentPeriodHint(): String {
        val hour = LocalTime.now().hour
        return when (hour) {
            in 5..11 -> "MORNING"
            in 12..17 -> "WORK"
            else -> "EVENING"
        }
    }

    /**
     * Theme color resolver (app + widget). Background from [backgroundModes];
     * accent from [accentSchemes] / [AppData.colorScheme].
     */
    fun resolveThemeColors(data: AppData): ThemeColors {
        val accent = accentColorArgb(data.colorScheme)
        val bg = backgroundModeOption(data.backgroundMode)
        return ThemeColors(
            background = bg.backgroundArgb,
            surface = bg.surfaceArgb,
            accent = accent,
            widgetRowBg = bg.widgetRowBgArgb
        )
    }

    // --- Active + hierarchy helpers ---

    /** Active (non-archived) groups, top-level first. */
    fun getActiveGroups(data: AppData): List<Group> =
        data.groups.filter { !it.archived }.sortedBy { it.order }

    /** Habits for a group (active only), including additionalGroupIds membership (#24). */
    fun getActiveHabitsForGroup(data: AppData, groupId: String): List<Habit> =
        data.habits.filter { !it.archived && belongsToGroup(it, groupId) }.sortedBy { it.order }

    // --- Exercise routines (#21) ---

    /** Active (non-archived) routines, catalog order. */
    fun getActiveRoutines(data: AppData): List<ExerciseRoutine> =
        data.routines.filter { !it.archived }.sortedBy { it.order }

    /** Whether a routine should appear on [date] (same show presets as habits). */
    fun isRoutineDueOn(routine: ExerciseRoutine, date: LocalDate): Boolean {
        if (routine.archived) return false
        val asHabit = Habit(
            id = routine.id,
            name = routine.name,
            groupId = routine.groupId ?: "",
            showPreset = routine.showPreset,
            weekdays = routine.weekdays
        )
        return isDueOn(asHabit, date)
    }

    fun routinesDueOn(data: AppData, date: LocalDate = LocalDate.now()): List<ExerciseRoutine> =
        getActiveRoutines(data).filter { isRoutineDueOn(it, date) }

    /** True if a completed session exists for routine on that date. */
    fun isRoutineCompletedOn(data: AppData, routineId: String, date: String): Boolean =
        data.workoutSessions.any { it.routineId == routineId && it.date == date && it.completed }

    /** Recent sessions newest first. */
    fun recentWorkoutSessions(data: AppData, limit: Int = 30): List<WorkoutSession> =
        data.workoutSessions.sortedByDescending { it.startedAt }.take(limit)

    /** Days in last [daysBack] with ≥1 completed workout. */
    fun workoutDaysInWindow(data: AppData, daysBack: Int = 7): Int {
        val today = LocalDate.now()
        val dates = data.workoutSessions
            .filter { it.completed }
            .map { it.date }
            .toSet()
        return (0 until daysBack).count { offset ->
            today.minusDays(offset.toLong()).toString() in dates
        }
    }

    /** Total sets logged in a session. */
    fun sessionSetCount(session: WorkoutSession): Int =
        session.performedExercises.values.sumOf { it.size }

    /** Total exercises with at least one set. */
    fun sessionExerciseCount(session: WorkoutSession): Int =
        session.performedExercises.count { it.value.isNotEmpty() }

    // --- Dreamline / Path (#25, #26) ---

    fun getActiveGoals(data: AppData): List<GoalStory> =
        data.goals.filter { !it.archived }.sortedByDescending { it.updatedAt }

    fun dreamlineGoals(data: AppData): List<GoalStory> =
        getActiveGoals(data).filter { g ->
            g.tags.any { it.equals(GoalTags.DREAMLINE, ignoreCase = true) } ||
                g.tags.any { it.equals("dreamline", ignoreCase = true) }
        }

    fun goalsByHorizon(data: AppData, horizon: DreamHorizon): List<GoalStory> =
        dreamlineGoals(data).filter { it.horizon == horizon }

    fun goalsByCategory(data: AppData, category: DreamCategory): List<GoalStory> =
        dreamlineGoals(data).filter { it.category == category }

    fun latestPathCheck(data: AppData): PathAlignmentCheck? =
        data.pathChecks.maxByOrNull { it.loggedAt }

    fun pathCheckToday(data: AppData, date: String = getToday()): PathAlignmentCheck? =
        data.pathChecks.filter { it.date == date }.maxByOrNull { it.loggedAt }

    /** Average of the three alignment sliders (1–5 → 0–1). */
    fun pathCheckScore(check: PathAlignmentCheck): Float {
        val avg = (check.visionAlignment + check.energyTowardDreams + check.identityCongruence) / 3f
        return ((avg - 1f) / 4f).coerceIn(0f, 1f)
    }

    fun averageProgress(goals: List<GoalStory>): Float {
        if (goals.isEmpty()) return 0f
        return goals.map { it.progress.coerceIn(0f, 1f) }.average().toFloat()
    }

    /**
     * Mindset prompts derived from Being goals (identity-linked).
     * Returns ready-to-show strings for the Path tab.
     */
    fun mindsetPrompts(data: AppData, limit: Int = 4): List<String> {
        val being = goalsByCategory(data, DreamCategory.BEING)
        if (being.isEmpty()) {
            return listOf(
                "What would the future you who already lives your dream do today?",
                "If stuck, consider the opposite of what you hate or fear.",
                "Name one identity you want to grow into this week."
            )
        }
        return being.take(limit).map { g ->
            val core = g.title.trim().ifBlank { "your vision" }
            "What would the version of you who is already “$core” do today?"
        }
    }

    /** First-step actions still open (not marked done on parent). */
    fun openFirstSteps(data: AppData): List<Pair<GoalStory, String>> =
        dreamlineGoals(data)
            .filter { it.firstStepNow.isNotBlank() }
            .map { it to it.firstStepNow }

    fun openSteps(data: AppData): List<Pair<GoalStory, GoalStep>> =
        dreamlineGoals(data).flatMap { g ->
            g.steps.filter { !it.done }.map { g to it }
        }

    /**
     * Build GoalStories from wizard draft lines.
     * [dreams]: list of (horizon, category, text)
     * [stepsByKey]: map "horizon|category|text" -> list of step titles
     * [firstStepsByKey]: map key -> first step now
     */
    fun buildGoalsFromDreamline(
        dreams: List<Triple<DreamHorizon, DreamCategory, String>>,
        stepsByKey: Map<String, List<String>> = emptyMap(),
        firstStepsByKey: Map<String, String> = emptyMap(),
        nowMs: Long = System.currentTimeMillis()
    ): List<GoalStory> {
        val today = java.time.LocalDate.now()
        return dreams.mapIndexedNotNull { index, (horizon, category, raw) ->
            val text = raw.trim()
            if (text.isBlank()) return@mapIndexedNotNull null
            val key = dreamKey(horizon, category, text)
            val end = when (horizon) {
                DreamHorizon.SIX_MONTHS -> today.plusMonths(6)
                DreamHorizon.TWELVE_MONTHS -> today.plusMonths(12)
            }
            val stepTitles = stepsByKey[key].orEmpty().map { it.trim() }.filter { it.isNotEmpty() }.take(3)
            GoalStory(
                id = "goal_${java.util.UUID.randomUUID().toString().take(8)}",
                title = text.take(120),
                description = when (category) {
                    DreamCategory.HAVING -> "Dreamline · Having (${horizonLabel(horizon)})"
                    DreamCategory.BEING -> "Dreamline · Being / identity (${horizonLabel(horizon)})"
                    DreamCategory.DOING -> "Dreamline · Doing (${horizonLabel(horizon)})"
                },
                category = category,
                horizon = horizon,
                tags = listOf(
                    GoalTags.DREAMLINE,
                    GoalTags.forHorizon(horizon),
                    GoalTags.forCategory(category)
                ),
                progress = 0f,
                confidence = 0.5f,
                firstStepNow = firstStepsByKey[key]?.trim().orEmpty(),
                steps = stepTitles.mapIndexed { i, t ->
                    GoalStep(id = "gs_${i}_$index", title = t, order = i)
                },
                startDate = today.toString(),
                endDate = end.toString(),
                createdAt = nowMs,
                updatedAt = nowMs
            )
        }
    }

    fun dreamKey(horizon: DreamHorizon, category: DreamCategory, text: String): String =
        "${horizon.name}|${category.name}|${text.trim().lowercase()}"

    fun horizonLabel(horizon: DreamHorizon): String = when (horizon) {
        DreamHorizon.SIX_MONTHS -> "6 months"
        DreamHorizon.TWELVE_MONTHS -> "12 months"
    }

    fun categoryLabel(category: DreamCategory): String = when (category) {
        DreamCategory.HAVING -> "Having"
        DreamCategory.BEING -> "Being"
        DreamCategory.DOING -> "Doing"
    }

    /** Example dream prompts when the user is stuck (Dreamline helpers). */
    fun dreamlineStuckPrompts(): List<String> = listOf(
        "If stuck, consider the opposite of what you hate or fear.",
        "A place you want to visit or live.",
        "A memory of a lifetime you want to create.",
        "A daily or weekly habit that would change everything.",
        "A skill you want to learn or master.",
        "Who do you want to become — not just what you want to do?"
    )

    /** Return nested view for a parent (e.g. Workouts and its plan subgroups). */
    fun getSubGroups(data: AppData, parentId: String): List<Group> =
        data.groups.filter { it.parentId == parentId && !it.archived }.sortedBy { it.order }

    /** Count recent skips for a habit (distinct days in window). */
    fun countRecentSkips(data: AppData, habitId: String, daysBack: Int = 7): Int {
        val today = LocalDate.now()
        return (0 until daysBack).count { offset ->
            val d = today.minusDays(offset.toLong()).toString()
            data.entries[d]?.get(habitId)?.skipped == true
        }
    }

    /** Convenience today string (yyyy-MM-dd). */
    fun getToday(): String = LocalDate.now().toString()

    /** Resolve current group from active schedule or legacy hint.
     * Supports overnight blocks (e.g. Sleep 23:00-07:00). */
    fun resolveCurrentGroup(data: AppData, now: LocalTime = LocalTime.now()): Group? {
        val schedule = getActiveSchedule(data)
        if (schedule != null && isScheduleApplicableToday(data, schedule) && schedule.timeBlocks.isNotEmpty()) {
            val minutesNow = now.hour * 60 + now.minute
            for (block in schedule.timeBlocks) {
                val startMin = parseTimeToMinutes(block.start)
                val endMin = parseTimeToMinutes(block.end)
                val inBlock = if (startMin < endMin) {
                    minutesNow in startMin until endMin
                } else {
                    // overnight block crosses midnight
                    minutesNow >= startMin || minutesNow < endMin
                }
                if (inBlock) {
                    return data.groups.find { it.id == block.groupId && !it.archived }
                }
            }
            // If past last block, pick first (or next day's)
            return data.groups.find { it.id == schedule.timeBlocks.first().groupId && !it.archived }
        }

        // Fallback to legacy timeHint
        val hint = getCurrentPeriodHint()
        return data.groups.filter { !it.archived }.firstOrNull { it.timeHint == hint }
            ?: data.groups.filter { !it.archived }.firstOrNull()
    }

    // Schedule helpers (moved here too for domain purity)
    fun getActiveSchedule(data: AppData): Schedule? =
        data.activeScheduleId?.let { id -> data.schedules.find { it.id == id } }

    fun isScheduleApplicableToday(data: AppData, schedule: Schedule): Boolean {
        val today = LocalDate.now().toString()
        if (schedule.specificDates.isNotEmpty()) {
            return today in schedule.specificDates
        }
        val dow = LocalDate.now().dayOfWeek.value  // 1=Mon ... 7=Sun in java.time
        return dow in schedule.weekdays
    }

    fun parseTimeToMinutes(hhmm: String): Int {
        val parts = hhmm.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return h * 60 + m
    }

    /**
     * Suggested HH:mm for a group-linked reminder from the active schedule (block start),
     * falling back to sleep spine (wake / wind-down start / bed).
     */
    fun suggestedReminderTimeForGroup(data: AppData, groupId: String): String? {
        val schedule = getActiveSchedule(data)
        schedule?.timeBlocks?.firstOrNull { it.groupId == groupId }?.let { return it.start }

        val group = data.groups.find { it.id == groupId }
        val sleep = data.sleep
        val hint = group?.timeHint?.uppercase().orEmpty()
        return when {
            groupId == sleep.morningGroupId || hint == "MORNING" -> sleep.wakeTime
            groupId == sleep.bedtimeGroupId || hint == "BEDTIME" || hint == "EVENING" -> {
                val bed = parseTimeToMinutes(sleep.bedTime)
                val wind = sleep.windDownMinutes.coerceIn(15, 180)
                val raw = bed - wind
                minutesToHhMm(if (raw < 0) raw + 24 * 60 else raw)
            }
            groupId == sleep.sleepGroupId || hint == "SLEEP" -> sleep.bedTime
            else -> null
        }
    }

    /**
     * Global daily-review time: end of bedtime block if present, else bed time, else 21:45.
     */
    fun suggestedDailyReviewTime(data: AppData): String {
        val sleep = data.sleep
        val bedId = sleep.bedtimeGroupId
        val schedule = getActiveSchedule(data)
        if (bedId != null) {
            schedule?.timeBlocks?.firstOrNull { it.groupId == bedId }?.let { return it.end }
        }
        val bed = sleep.bedTime.trim()
        return if (bed.contains(":")) bed else "21:45"
    }

    /**
     * Recompute each reminder's [Reminder.time] from the active schedule / sleep spine.
     * Preserves id, groupId, days, and enabled.
     */
    fun alignRemindersToSchedule(data: AppData): List<Reminder> {
        return data.reminders.map { r ->
            val suggested = if (r.groupId == null) {
                suggestedDailyReviewTime(data)
            } else {
                suggestedReminderTimeForGroup(data, r.groupId) ?: r.time
            }
            if (suggested == r.time) r else r.copy(time = suggested)
        }
    }

    fun withRemindersAlignedToSchedule(data: AppData): AppData =
        data.copy(reminders = alignRemindersToSchedule(data))

    /**
     * Set primary group to [newGroupId] while keeping other memberships.
     * If already a member, that id becomes primary; if not, it is added as primary.
     */
    fun moveHabit(currentHabits: List<Habit>, habitId: String, newGroupId: String, newOrder: Int): List<Habit> {
        val habit = currentHabits.find { it.id == habitId } ?: return currentHabits
        val others = membershipGroupIds(habit).filter { it != newGroupId }
        val updated = withMembership(habit, listOf(newGroupId) + others).copy(order = newOrder)
        val without = currentHabits.filter { it.id != habitId }
        val newList = without + updated
        // Reindex orders among habits that share the new primary group
        return newList.groupBy { it.groupId }.flatMap { (_, list) ->
            list.sortedBy { it.order }.mapIndexed { i, h -> h.copy(order = i) }
        }
    }

    /**
     * Swap display order among habits that belong to [withinGroupId]
     * (primary or additional membership — multi-group habits behave the same).
     */
    fun reorderWithinGroup(
        currentHabits: List<Habit>,
        habitId: String,
        withinGroupId: String,
        direction: Int
    ): List<Habit> {
        val siblings = currentHabits
            .filter { !it.archived && belongsToGroup(it, withinGroupId) }
            .sortedBy { it.order }
        val idx = siblings.indexOfFirst { it.id == habitId }
        val swapIdx = idx + direction
        if (idx < 0 || swapIdx !in siblings.indices) return currentHabits
        val a = siblings[idx]
        val b = siblings[swapIdx]
        return currentHabits.map {
            when (it.id) {
                a.id -> it.copy(order = b.order)
                b.id -> it.copy(order = a.order)
                else -> it
            }
        }
    }

    // --- Steady Momentum scoring (v10) ---

    const val BASE_POINTS = 10
    const val TARGET_BONUS = 5
    const val QUALITY_BONUS = 3
    const val FULL_CLEAR_BONUS = 25
    const val SOLID_DAY_BONUS = 10
    const val PATH_CHECK_BONUS = 15
    const val DAILY_POINTS_CAP = 500
    const val POINTS_PER_LEVEL = 500
    const val SCORE_HISTORY_MAX = 60
    /** Adaptive reminder may shift ± this many minutes from scheduled time. */
    const val ADAPTIVE_CLAMP_MINUTES = 45
    /** Minimum historical group logs before adaptive timing engages. */
    const val ADAPTIVE_MIN_SAMPLES = 5

    data class DayPointsBreakdown(
        val habitPoints: Int,
        val targetBonuses: Int,
        val qualityBonuses: Int,
        val fullClearBonus: Int,
        val solidDayBonus: Int,
        val pathCheckBonus: Int,
        val rawTotal: Int,
        val total: Int
    ) {
        val remainingTowardCap: Int get() = (DAILY_POINTS_CAP - total).coerceAtLeast(0)
    }

    fun isEntryCompleted(entry: HabitEntry?): Boolean =
        entry != null && !entry.skipped && entry.value >= 0.5

    /** Same thresholds as [computeStreak] for a single day. */
    fun isSolidDay(data: AppData, date: LocalDate): Boolean {
        val due = habitsDueOn(data, date)
        if (due.isEmpty()) return true
        val key = date.toString()
        val dayEntries = data.entries[key] ?: emptyMap()
        val completedCount = due.count { h -> isEntryCompleted(dayEntries[h.id]) }
        return when {
            completedCount == due.size -> true
            completedCount >= 3 -> true
            completedCount.toFloat() / due.size >= 0.6f -> true
            else -> false
        }
    }

    /** All due habits completed (not skipped). Empty due day counts as clear. */
    fun isFullClear(data: AppData, date: LocalDate): Boolean {
        val due = habitsDueOn(data, date)
        if (due.isEmpty()) return true
        val dayEntries = data.entries[date.toString()] ?: emptyMap()
        return due.all { h -> isEntryCompleted(dayEntries[h.id]) }
    }

    fun hasPathCheckOn(data: AppData, dateStr: String): Boolean =
        data.pathChecks.any { it.date == dateStr }

    fun computeDayPointsBreakdown(data: AppData, dateStr: String): DayPointsBreakdown {
        val date = try {
            LocalDate.parse(dateStr)
        } catch (_: Exception) {
            return DayPointsBreakdown(0, 0, 0, 0, 0, 0, 0, 0)
        }
        val due = habitsDueOn(data, date)
        val dayMap = data.entries[dateStr] ?: emptyMap()
        var habitPoints = 0
        var targetBonuses = 0
        var qualityBonuses = 0
        for (h in due) {
            val e = dayMap[h.id]
            if (!isEntryCompleted(e)) continue
            habitPoints += BASE_POINTS
            val value = e!!.value
            when (h.type) {
                HabitType.COUNTER, HabitType.DURATION_MIN -> {
                    val target = h.target
                    if (target != null && target > 0 && value >= target) {
                        targetBonuses += TARGET_BONUS
                    }
                }
                HabitType.SCALE_1_5 -> {
                    if (value >= 4.0) qualityBonuses += QUALITY_BONUS
                }
                else -> Unit
            }
        }
        val fullClear = if (due.isNotEmpty() && isFullClear(data, date)) FULL_CLEAR_BONUS else 0
        val solid = if (due.isNotEmpty() && isSolidDay(data, date)) SOLID_DAY_BONUS else 0
        val path = if (hasPathCheckOn(data, dateStr)) PATH_CHECK_BONUS else 0
        val raw = habitPoints + targetBonuses + qualityBonuses + fullClear + solid + path
        val total = raw.coerceAtMost(DAILY_POINTS_CAP)
        return DayPointsBreakdown(
            habitPoints = habitPoints,
            targetBonuses = targetBonuses,
            qualityBonuses = qualityBonuses,
            fullClearBonus = fullClear,
            solidDayBonus = solid,
            pathCheckBonus = path,
            rawTotal = raw,
            total = total
        )
    }

    fun computeDayPoints(data: AppData, dateStr: String): Int =
        computeDayPointsBreakdown(data, dateStr).total

    /**
     * Max points still available today if remaining due habits are completed
     * (includes day bonuses not yet earned). Rough upper bound for nudge copy.
     */
    fun pointsRemainingToday(data: AppData, dateStr: String = getToday()): Int {
        val date = try {
            LocalDate.parse(dateStr)
        } catch (_: Exception) {
            return 0
        }
        val current = computeDayPoints(data, dateStr)
        val due = habitsDueOn(data, date)
        val dayMap = data.entries[dateStr] ?: emptyMap()
        var ifDone = 0
        for (h in due) {
            val e = dayMap[h.id]
            if (isEntryCompleted(e)) {
                ifDone += BASE_POINTS
                val value = e!!.value
                when (h.type) {
                    HabitType.COUNTER, HabitType.DURATION_MIN -> {
                        val target = h.target
                        if (target != null && target > 0 && value >= target) ifDone += TARGET_BONUS
                    }
                    HabitType.SCALE_1_5 -> if (value >= 4.0) ifDone += QUALITY_BONUS
                    else -> Unit
                }
            } else if (e?.skipped != true) {
                ifDone += BASE_POINTS
                when (h.type) {
                    HabitType.COUNTER, HabitType.DURATION_MIN -> {
                        if (h.target != null && h.target > 0) ifDone += TARGET_BONUS
                    }
                    HabitType.SCALE_1_5 -> ifDone += QUALITY_BONUS
                    else -> Unit
                }
            }
        }
        if (due.isNotEmpty()) {
            ifDone += FULL_CLEAR_BONUS + SOLID_DAY_BONUS
        }
        // Path check may still be logged today
        ifDone += PATH_CHECK_BONUS
        val potential = ifDone.coerceAtMost(DAILY_POINTS_CAP)
        return (potential - current).coerceAtLeast(0)
    }

    fun computeLevel(lifetimePoints: Int): Int =
        1 + (lifetimePoints.coerceAtLeast(0) / POINTS_PER_LEVEL)

    fun pointsIntoCurrentLevel(lifetimePoints: Int): Int =
        lifetimePoints.coerceAtLeast(0) % POINTS_PER_LEVEL

    fun pointsToNextLevel(lifetimePoints: Int): Int =
        POINTS_PER_LEVEL - pointsIntoCurrentLevel(lifetimePoints)

    /** Soft titles every 5 levels (calm, not gamey). */
    fun levelTitle(level: Int): String = when {
        level >= 20 -> "Unshakable"
        level >= 15 -> "Committed"
        level >= 10 -> "Consistent"
        level >= 5 -> "Anchored"
        else -> "Steady"
    }

    /**
     * Effective lifetime for display = finalized lifetime + today's live points
     * when today is not yet in history.
     */
    fun effectiveLifetimePoints(data: AppData, today: String = getToday()): Int {
        val todayPts = computeDayPoints(data, today)
        val inHistory = data.score.history.any { it.date == today }
        return if (inHistory) data.score.lifetimePoints else data.score.lifetimePoints + todayPts
    }

    fun bestDayScore(data: AppData): DayScore? {
        val fromHistory = data.score.history.maxByOrNull { it.points }
        val today = getToday()
        val todayPts = computeDayPoints(data, today)
        val todayScore = DayScore(today, todayPts, computeDayCompletion(data, today))
        return listOfNotNull(fromHistory, todayScore).maxByOrNull { it.points }
            ?.takeIf { it.points > 0 }
    }

    /**
     * Rebuild rolling score history from entries (last [SCORE_HISTORY_MAX] days before today).
     * Used after CSV entry import when the ledger may be stale.
     * Lifetime = max(previous lifetime, sum of rebuilt window) so we never erase older prestige
     * if the window alone is smaller.
     */
    fun rebuildScoreFromEntries(data: AppData, today: String = getToday()): AppData {
        val todayDate = try {
            LocalDate.parse(today)
        } catch (_: Exception) {
            return data
        }
        val history = mutableListOf<DayScore>()
        var windowSum = 0
        var d = todayDate.minusDays(1)
        repeat(SCORE_HISTORY_MAX) {
            val key = d.toString()
            val due = habitsDueOn(data, d)
            val pts = computeDayPoints(data, key)
            val completion = computeDayCompletion(data, key)
            if (due.isNotEmpty() || pts > 0 || data.entries.containsKey(key)) {
                history.add(0, DayScore(key, pts, completion))
                windowSum += pts
            }
            d = d.minusDays(1)
        }
        val finalizedDate = todayDate.minusDays(1).toString()
        val lifetime = maxOf(data.score.lifetimePoints, windowSum)
        return data.copy(
            score = ScoreState(
                lifetimePoints = lifetime,
                history = history,
                lastFinalizedDate = if (history.isEmpty()) data.score.lastFinalizedDate else finalizedDate
            )
        )
    }

    /**
     * Finalize past days into [ScoreState.history] up to (but not including) [today].
     * Idempotent for dates already present. Caps history at [SCORE_HISTORY_MAX].
     */
    fun withFinalizedScoreHistory(data: AppData, today: String = getToday()): AppData {
        val todayDate = try {
            LocalDate.parse(today)
        } catch (_: Exception) {
            return data
        }
        val lastFinal = data.score.lastFinalizedDate
        val startDate = if (lastFinal.isBlank()) {
            // Backfill up to 60 days of existing entry activity without huge first-run cost
            val earliest = data.entries.keys.mapNotNull {
                try { LocalDate.parse(it) } catch (_: Exception) { null }
            }.minOrNull()
            val floor = todayDate.minusDays((SCORE_HISTORY_MAX - 1).toLong())
            when {
                earliest == null -> todayDate // nothing to finalize
                earliest.isBefore(floor) -> floor
                else -> earliest
            }
        } else {
            try {
                LocalDate.parse(lastFinal).plusDays(1)
            } catch (_: Exception) {
                todayDate
            }
        }

        if (!startDate.isBefore(todayDate)) {
            // Still ensure schema fields present; maybe just refresh today out of history if stuck
            return data
        }

        var lifetime = data.score.lifetimePoints
        val history = data.score.history.toMutableList()
        val existingDates = history.map { it.date }.toMutableSet()
        var d = startDate
        var lastDone = data.score.lastFinalizedDate
        while (d.isBefore(todayDate)) {
            val key = d.toString()
            if (key !in existingDates) {
                val pts = computeDayPoints(data, key)
                val completion = computeDayCompletion(data, key)
                // Only bank days that had something due or activity
                val due = habitsDueOn(data, d)
                if (due.isNotEmpty() || pts > 0) {
                    history.add(DayScore(key, pts, completion))
                    existingDates.add(key)
                    lifetime += pts
                }
            }
            lastDone = key
            d = d.plusDays(1)
        }
        // Cap rolling window
        val trimmed = if (history.size > SCORE_HISTORY_MAX) {
            history.sortedBy { it.date }.takeLast(SCORE_HISTORY_MAX)
        } else {
            history.sortedBy { it.date }
        }
        return data.copy(
            score = ScoreState(
                lifetimePoints = lifetime,
                history = trimmed,
                lastFinalizedDate = lastDone
            )
        )
    }

    fun lastNDayPoints(data: AppData, n: Int = 30): List<Pair<String, Int>> {
        val result = mutableListOf<Pair<String, Int>>()
        var d = LocalDate.now()
        repeat(n) {
            val key = d.toString()
            val fromHist = data.score.history.find { it.date == key }?.points
            val pts = fromHist ?: computeDayPoints(data, key)
            result.add(key to pts)
            d = d.minusDays(1)
        }
        return result.reversed()
    }

    // --- Smart gentle notifications (v10) ---

    fun isInQuietHours(prefs: NotificationPrefs, minutesNow: Int): Boolean {
        val start = parseTimeToMinutes(prefs.quietStart)
        val end = parseTimeToMinutes(prefs.quietEnd)
        return if (start == end) {
            false
        } else if (start < end) {
            minutesNow in start until end
        } else {
            // overnight window e.g. 22:30–07:00
            minutesNow >= start || minutesNow < end
        }
    }

    /**
     * If [triggerMillis] falls in quiet hours, push to quiet end on that local day
     * (or next calendar day if quiet end is after midnight relative to start).
     */
    fun pushPastQuietHours(
        triggerMillis: Long,
        prefs: NotificationPrefs,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long {
        val zdt = Instant.ofEpochMilli(triggerMillis).atZone(zone)
        val minutesNow = zdt.hour * 60 + zdt.minute
        if (!isInQuietHours(prefs, minutesNow)) return triggerMillis
        val endMin = parseTimeToMinutes(prefs.quietEnd)
        val startMin = parseTimeToMinutes(prefs.quietStart)
        var target = zdt.withHour(endMin / 60).withMinute(endMin % 60).withSecond(0).withNano(0)
        // Overnight quiet: if we're after start (evening), quiet ends next morning
        if (startMin > endMin && minutesNow >= startMin) {
            target = target.plusDays(1)
        }
        // If still not after trigger (same-minute edge), add a minute
        if (!target.toInstant().isAfter(Instant.ofEpochMilli(triggerMillis))) {
            target = target.plusMinutes(1)
        }
        return target.toInstant().toEpochMilli()
    }

    /**
     * Group-aware ESM spacing:
     * - MORNING: ~2 check-ins in the morning window → ~45–60 min
     * - WORK: every ~[minInterval] minutes (default 30)
     * - BEDTIME / evening: 2–3 total → ~60–90 min
     * - SLEEP / quiet-ish: max interval
     */
    fun suggestedCheckInSpacingMinutes(
        data: AppData,
        minI: Int,
        maxI: Int,
        base: Int,
        now: LocalTime = LocalTime.now()
    ): Int {
        val g = resolveCurrentGroup(data, now)
        val hint = g?.timeHint?.uppercase().orEmpty()
        val name = g?.name?.lowercase().orEmpty()
        val isMorning = hint == "MORNING" || name.contains("morn") || name.contains("wake")
        val isWork = hint == "WORK" || name.contains("work") || name.contains("focus")
        val isEvening = hint == "BEDTIME" || name.contains("even") || name.contains("bed") || name.contains("wind")
        val isSleep = hint == "SLEEP" || name.contains("sleep")
        val raw = when {
            isWork -> minI
            isMorning -> ((minI + maxI) / 2).coerceAtLeast(40)
            isEvening -> (minI * 2).coerceIn(minI, maxI)
            isSleep -> maxI
            else -> base
        }
        return raw.coerceIn(minI, maxI)
    }

    /**
     * If [triggerMillis] falls outside typical check-in windows (sleep / deep night),
     * nudge to next morning / work window start when possible.
     */
    fun alignCheckInToGroupWindow(
        triggerMillis: Long,
        data: AppData,
        prefs: NotificationPrefs,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long {
        val zdt = Instant.ofEpochMilli(triggerMillis).atZone(zone)
        val t = zdt.toLocalTime()
        val g = resolveCurrentGroup(data, t)
        val hint = g?.timeHint?.uppercase().orEmpty()
        val name = g?.name?.lowercase().orEmpty()
        val isSleep = hint == "SLEEP" || name.contains("sleep")
        if (!isSleep && !isInQuietHours(prefs, t.hour * 60 + t.minute)) {
            return triggerMillis
        }
        // Next wake / morning: use sleep.wakeTime when set
        val wake = parseTimeToMinutes(data.sleep.wakeTime)
        var target = zdt.withHour(wake / 60).withMinute(wake % 60).withSecond(0).withNano(0)
        if (!target.toInstant().isAfter(Instant.ofEpochMilli(triggerMillis))) {
            target = target.plusDays(1)
        }
        // Slight jitter into morning (0–40 min)
        target = target.plusMinutes((0..40).random().toLong())
        return pushPastQuietHours(target.toInstant().toEpochMilli(), prefs, zone)
    }

    /**
     * Median log minute-of-day for habits belonging to [groupId], from [HabitEntry.loggedAt].
     * Returns null if fewer than [ADAPTIVE_MIN_SAMPLES] samples.
     */
    fun medianLogMinuteForGroup(data: AppData, groupId: String): Int? {
        val minutes = mutableListOf<Int>()
        for ((_, dayMap) in data.entries) {
            for ((habitId, entry) in dayMap) {
                if (entry.loggedAt <= 0L || entry.skipped) continue
                val habit = data.habits.find { it.id == habitId } ?: continue
                if (!belongsToGroup(habit, groupId)) continue
                val zdt = Instant.ofEpochMilli(entry.loggedAt).atZone(ZoneId.systemDefault())
                minutes.add(zdt.hour * 60 + zdt.minute)
            }
        }
        if (minutes.size < ADAPTIVE_MIN_SAMPLES) return null
        val sorted = minutes.sorted()
        return sorted[sorted.size / 2]
    }

    /**
     * Effective HH:mm for scheduling: base reminder time, optionally nudged toward
     * median log time within [ADAPTIVE_CLAMP_MINUTES].
     */
    fun resolveEffectiveReminderTime(r: Reminder, data: AppData): String {
        if (!data.notificationPrefs.adaptiveTiming || r.groupId == null) return r.time
        val scheduled = parseTimeToMinutes(r.time)
        val median = medianLogMinuteForGroup(data, r.groupId) ?: return r.time
        val delta = (median - scheduled).coerceIn(-ADAPTIVE_CLAMP_MINUTES, ADAPTIVE_CLAMP_MINUTES)
        return minutesToHhMm(scheduled + delta)
    }

    /** Pending due habits for a reminder (null groupId = all groups). Uses full membership. */
    fun pendingHabitsForReminder(
        data: AppData,
        groupId: String?,
        date: LocalDate = LocalDate.now()
    ): List<Habit> {
        val key = date.toString()
        val entries = data.entries[key] ?: emptyMap()
        return habitsDueOn(data, date)
            .filter { h -> groupId == null || belongsToGroup(h, groupId) }
            .filter { h ->
                val e = entries[h.id]
                !isEntryCompleted(e) && e?.skipped != true
            }
    }

    fun firesToday(prefs: NotificationPrefs, today: String = getToday()): Int =
        if (prefs.firesDate == today) prefs.firesCount else 0

    fun withRecordedNotificationFire(data: AppData, today: String = getToday()): AppData {
        val prefs = data.notificationPrefs
        val count = if (prefs.firesDate == today) prefs.firesCount + 1 else 1
        return data.copy(
            notificationPrefs = prefs.copy(firesDate = today, firesCount = count)
        )
    }

    data class ReminderDecision(
        val show: Boolean,
        val title: String,
        val body: String,
        val celebrate: Boolean = false
    )

    /**
     * Decide whether to show a notification and what copy to use.
     * Rate limit, empty-pending skip, streak-risk (daily review only), full-clear celebrate.
     */
    fun decideReminder(
        data: AppData,
        groupId: String?,
        groupName: String,
        today: String = getToday()
    ): ReminderDecision {
        val prefs = data.notificationPrefs
        val fires = firesToday(prefs, today)
        if (fires >= prefs.maxPerDay.coerceAtLeast(1)) {
            return ReminderDecision(false, "", "")
        }
        val date = try {
            LocalDate.parse(today)
        } catch (_: Exception) {
            LocalDate.now()
        }
        val pending = pendingHabitsForReminder(data, groupId, date)
        val streak = computeStreak(data)
        val solid = isSolidDay(data, date)
        val remainingPts = pointsRemainingToday(data, today)

        // Daily review (null group): streak-risk or pending summary
        if (groupId == null) {
            if (pending.isEmpty()) {
                if (prefs.celebrateFullClear && isFullClear(data, date) && solid) {
                    val pts = computeDayPoints(data, today)
                    return ReminderDecision(
                        show = true,
                        title = "Solid day",
                        body = "You're clear. +$pts Steady points today.",
                        celebrate = true
                    )
                }
                return ReminderDecision(false, "", "")
            }
            if (prefs.streakRiskNudge && streak >= 3 && !solid) {
                val need = pending.size
                return ReminderDecision(
                    show = true,
                    title = "Protect your $streak-day streak",
                    body = buildPendingBody(pending, remainingPts, streakRisk = true, need = need)
                )
            }
            return ReminderDecision(
                show = true,
                title = "Daily review",
                body = buildPendingBody(pending, remainingPts)
            )
        }

        // Group reminder
        if (pending.isEmpty()) {
            return ReminderDecision(false, "", "")
        }
        val title = if (groupName.isNotBlank() && groupName != "Daily") "$groupName time" else "Steady reminder"
        return ReminderDecision(
            show = true,
            title = title,
            body = buildPendingBody(pending, remainingPts)
        )
    }

    private fun buildPendingBody(
        pending: List<Habit>,
        remainingPts: Int,
        streakRisk: Boolean = false,
        need: Int = pending.size
    ): String {
        val names = when {
            pending.isEmpty() -> ""
            pending.size <= 3 -> pending.joinToString(" · ") { it.name }
            else -> pending.take(3).joinToString(" · ") { it.name } + " +${pending.size - 3} more"
        }
        return buildString {
            if (streakRisk) {
                append("$need habit${if (need == 1) "" else "s"} would keep it")
                if (names.isNotEmpty()) append(" · $names")
            } else {
                append(names)
            }
            if (remainingPts > 0) {
                if (isNotEmpty()) append(" · ")
                append("~$remainingPts pts still open")
            }
        }
    }
}

// Backwards-compatible top level functions delegating to object (so existing call sites continue to work with minimal changes)
fun groupHabits(data: AppData): List<Pair<Group, List<Habit>>> = HabitDomain.groupHabits(data)
fun computeStreak(data: AppData): Int = HabitDomain.computeStreak(data)
fun computeDayCompletion(data: AppData, dateStr: String): Float = HabitDomain.computeDayCompletion(data, dateStr)
fun computeLastNDays(data: AppData, n: Int): List<Pair<String, Float>> = HabitDomain.computeLastNDays(data, n)
fun computeGroup7DayAvg(data: AppData, groupId: String): Float = HabitDomain.computeGroup7DayAvg(data, groupId)
fun computeTag7DayAvg(data: AppData, tagId: String): Float = HabitDomain.computeTag7DayAvg(data, tagId)
fun getActiveTags(data: AppData): List<Tag> = HabitDomain.getActiveTags(data)
fun getCurrentPeriodHint(): String = HabitDomain.getCurrentPeriodHint()
fun resolveThemeColors(data: AppData) = HabitDomain.resolveThemeColors(data)
fun getActiveGroups(data: AppData): List<Group> = HabitDomain.getActiveGroups(data)
fun getActiveHabitsForGroup(data: AppData, groupId: String): List<Habit> = HabitDomain.getActiveHabitsForGroup(data, groupId)
fun getSubGroups(data: AppData, parentId: String): List<Group> = HabitDomain.getSubGroups(data, parentId)
fun countRecentSkips(data: AppData, habitId: String, daysBack: Int = 7): Int = HabitDomain.countRecentSkips(data, habitId, daysBack)
fun getToday(): String = HabitDomain.getToday()
fun resolveCurrentGroup(data: AppData, now: LocalTime = LocalTime.now()): Group? = HabitDomain.resolveCurrentGroup(data, now)
fun moveHabit(currentHabits: List<Habit>, habitId: String, newGroupId: String, newOrder: Int): List<Habit> =
    HabitDomain.moveHabit(currentHabits, habitId, newGroupId, newOrder)
fun isDueOn(habit: Habit, date: LocalDate): Boolean = HabitDomain.isDueOn(habit, date)
fun habitsDueOn(data: AppData, date: LocalDate = LocalDate.now()): List<Habit> = HabitDomain.habitsDueOn(data, date)
fun pendingGroupedForDate(data: AppData, date: LocalDate = LocalDate.now()) = HabitDomain.pendingGroupedForDate(data, date)
fun timelineSectionsForToday(
    data: AppData,
    date: LocalDate = LocalDate.now(),
    now: LocalTime = LocalTime.now(),
    includeCompleted: Boolean = false
) = HabitDomain.timelineSectionsForToday(data, date, now, includeCompleted)

fun computeHabitStreak(data: AppData, habitId: String): Int =
    HabitDomain.computeHabitStreak(data, habitId)
fun showRuleLabel(habit: Habit): String = HabitDomain.showRuleLabel(habit)

/** One spine section on Today / widget (pure visual day order). */
data class TimelineSection(
    val group: Group,
    val habits: List<Habit>,
    val isNow: Boolean,
    val isPast: Boolean,
    val isFuture: Boolean
)

/** Tiny “where am I / what’s next” cue without clock numbers. */
data class DayProgressionCue(
    val nowGroupName: String?,
    val nowHasPending: Boolean,
    val nextGroupName: String?
)

/** Stable default tag ids (seeded + migration). */
object TagIds {
    const val SUPPLEMENTS = "tag_supp"
    const val MOVEMENT = "tag_move"
    const val MINDSET = "tag_mind"
    const val NUTRITION = "tag_nutri"
    const val HYGIENE = "tag_hygiene"
    const val SLEEP = "tag_sleep"
}

fun defaultTags(): List<Tag> = listOf(
    Tag(TagIds.SUPPLEMENTS, "Supplements", color = 0xFF14B8A6.toInt(), order = 0),
    Tag(TagIds.MOVEMENT, "Movement", color = 0xFF3B82F6.toInt(), order = 1),
    Tag(TagIds.MINDSET, "Mindset", color = 0xFF8B5CF6.toInt(), order = 2),
    Tag(TagIds.NUTRITION, "Nutrition", color = 0xFFF97316.toInt(), order = 3),
    Tag(TagIds.HYGIENE, "Hygiene", color = 0xFF22C55E.toInt(), order = 4),
    Tag(TagIds.SLEEP, "Sleep", color = 0xFF64748B.toInt(), order = 5)
)

/** Accent palette option for settings + first-run theme picker. */
data class AccentSchemeOption(
    val id: String,
    val label: String,
    /** ARGB int (e.g. 0xFF22C55E.toInt()). */
    val colorArgb: Int,
    /** Grouping for UI sections: classic | feminine | bold. */
    val family: String = "classic"
)

/**
 * Background palette option for settings + first-run theme picker.
 * [isLight] drives text contrast and Material light vs dark color schemes.
 */
data class BackgroundModeOption(
    val id: String,
    val label: String,
    /** dark | tinted | light */
    val family: String,
    val backgroundArgb: Int,
    val surfaceArgb: Int,
    val surfaceVariantArgb: Int,
    val widgetRowBgArgb: Int,
    val isLight: Boolean
)

/**
 * Compact Linux-rice-inspired background catalog (curated; legacy ids alias in [backgroundModeOption]).
 */
fun backgroundModes(): List<BackgroundModeOption> = listOf(
    // Keep stable default id "dark"
    BackgroundModeOption(
        "dark", "Nord", "rice",
        0xFF2E3440.toInt(), 0xFF3B4252.toInt(), 0xFF434C5E.toInt(), 0xFF3B4252.toInt(), false
    ),
    BackgroundModeOption(
        "catppuccin", "Catppuccin", "rice",
        0xFF1E1E2E.toInt(), 0xFF313244.toInt(), 0xFF45475A.toInt(), 0xFF313244.toInt(), false
    ),
    BackgroundModeOption(
        "tokyonight", "Tokyo Night", "rice",
        0xFF1A1B26.toInt(), 0xFF24283B.toInt(), 0xFF414868.toInt(), 0xFF24283B.toInt(), false
    ),
    BackgroundModeOption(
        "gruvbox", "Gruvbox", "rice",
        0xFF282828.toInt(), 0xFF3C3836.toInt(), 0xFF504945.toInt(), 0xFF3C3836.toInt(), false
    ),
    BackgroundModeOption(
        "dracula", "Dracula", "rice",
        0xFF282A36.toInt(), 0xFF44475A.toInt(), 0xFF6272A4.toInt(), 0xFF44475A.toInt(), false
    ),
    BackgroundModeOption(
        "rosepine", "Rosé Pine", "rice",
        0xFF191724.toInt(), 0xFF1F1D2E.toInt(), 0xFF26233A.toInt(), 0xFF1F1D2E.toInt(), false
    ),
    BackgroundModeOption(
        "everforest", "Everforest", "rice",
        0xFF2D353B.toInt(), 0xFF343F44.toInt(), 0xFF3D484D.toInt(), 0xFF343F44.toInt(), false
    ),
    BackgroundModeOption(
        "onedark", "One Dark", "rice",
        0xFF282C34.toInt(), 0xFF21252B.toInt(), 0xFF3E4451.toInt(), 0xFF2C313A.toInt(), false
    ),
    BackgroundModeOption(
        "amoled", "OLED", "rice",
        0xFF000000.toInt(), 0xFF0A0A0A.toInt(), 0xFF1A1A1A.toInt(), 0xFF121212.toInt(), false
    ),
    BackgroundModeOption(
        "light", "Latte", "light",
        0xFFEFF1F5.toInt(), 0xFFFFFFFF.toInt(), 0xFFCCD0DA.toInt(), 0xFFE6E9EF.toInt(), true
    )
)

/** Dual swatch packs: one tap sets background + matching accent (Settings compact view). */
data class ThemePack(
    val id: String,
    val label: String,
    val backgroundId: String,
    val accentId: String,
    val blurb: String = ""
)

fun themePacks(): List<ThemePack> = listOf(
    ThemePack("pack_nord", "Nord", "dark", "nord", "Arctic frost"),
    ThemePack("pack_catppuccin", "Catppuccin", "catppuccin", "mauve", "Mocha mauve"),
    ThemePack("pack_tokyo", "Tokyo Night", "tokyonight", "tokyonight", "Neon city"),
    ThemePack("pack_gruvbox", "Gruvbox", "gruvbox", "gruvbox", "Warm retro"),
    ThemePack("pack_dracula", "Dracula", "dracula", "dracula", "Vampire purple"),
    ThemePack("pack_rosepine", "Rosé Pine", "rosepine", "rosepine", "Soft rose"),
    ThemePack("pack_everforest", "Everforest", "everforest", "everforest", "Calm greens"),
    ThemePack("pack_onedark", "One Dark", "onedark", "onedark", "Atom classic"),
    ThemePack("pack_oled", "OLED + Green", "amoled", "default", "True black"),
    ThemePack("pack_latte", "Catppuccin Latte", "light", "teal", "Soft light")
)

/** Resolve stored [AppData.backgroundMode] id → palette. Unknown / legacy ids alias to rice sets. */
fun backgroundModeOption(modeId: String): BackgroundModeOption {
    val id = when (modeId) {
        "charcoal", "graphite" -> "onedark"
        "midnight", "navy", "ocean" -> "tokyonight"
        "forest" -> "everforest"
        "plum", "dusk" -> "rosepine"
        "wine", "ember", "mocha" -> "gruvbox"
        "aurora" -> "catppuccin"
        "paper", "cream", "mist", "sage_bg", "blush_bg", "lavender_bg", "sky_bg" -> "light"
        else -> modeId
    }
    return backgroundModes().firstOrNull { it.id == id }
        ?: backgroundModes().first { it.id == "dark" }
}

/**
 * Compact rice-inspired accent catalog (~10 presets). Legacy ids alias in [accentColorArgb].
 */
fun accentSchemes(): List<AccentSchemeOption> = listOf(
    AccentSchemeOption("default", "Green", 0xFFA6E3A1.toInt(), "rice"),       // catppuccin green
    AccentSchemeOption("nord", "Nord frost", 0xFF88C0D0.toInt(), "rice"),
    AccentSchemeOption("tokyonight", "Tokyo blue", 0xFF7AA2F7.toInt(), "rice"),
    AccentSchemeOption("gruvbox", "Gruvbox", 0xFFFE8019.toInt(), "rice"),
    AccentSchemeOption("dracula", "Dracula", 0xFFBD93F9.toInt(), "rice"),
    AccentSchemeOption("mauve", "Mauve", 0xFFCBA6F7.toInt(), "rice"),          // catppuccin
    AccentSchemeOption("rosepine", "Rosé", 0xFFEBBCBA.toInt(), "rice"),
    AccentSchemeOption("everforest", "Everforest", 0xFFA7C080.toInt(), "rice"),
    AccentSchemeOption("onedark", "One Dark", 0xFF61AFEF.toInt(), "rice"),
    AccentSchemeOption("teal", "Teal", 0xFF94E2D5.toInt(), "rice")             // catppuccin teal
)

/**
 * Resolve stored [AppData.colorScheme] id → ARGB accent.
 * Supports catalog ids and custom accents: `custom:#RRGGBB` or `custom_RRGGBB` (#30 color picker).
 * Unknown ids fall back to green.
 */
fun accentColorArgb(schemeId: String): Int {
    val raw = schemeId.trim()
    if (raw.startsWith("custom:", ignoreCase = true)) {
        return parseCustomAccentHex(raw.removePrefix("custom:").removePrefix("CUSTOM:"))
            ?: 0xFFA6E3A1.toInt()
    }
    if (raw.startsWith("custom_", ignoreCase = true)) {
        return parseCustomAccentHex(raw.removePrefix("custom_").removePrefix("CUSTOM_"))
            ?: 0xFFA6E3A1.toInt()
    }
    val id = when (raw) {
        "blue", "sky", "indigo", "cyan" -> "tokyonight"
        "purple", "violet", "lilac", "lavender" -> "mauve"
        "orange", "amber", "peach" -> "gruvbox"
        "red", "rose", "berry", "coral", "pink", "fuchsia" -> "rosepine"
        "slate" -> "nord"
        "lime", "emerald" -> "everforest"
        "blush", "champagne", "mauve_old" -> "rosepine"
        else -> raw
    }
    return accentSchemes().firstOrNull { it.id == id }?.colorArgb ?: 0xFFA6E3A1.toInt()
}

/** Build a stable custom scheme id from an ARGB color (always stores RGB hex). */
fun customAccentSchemeId(argb: Int): String {
    val rgb = argb and 0x00FFFFFF
    return "custom:#%06X".format(rgb)
}

fun isCustomAccentScheme(schemeId: String): Boolean {
    val id = schemeId.trim()
    return id.startsWith("custom:", ignoreCase = true) || id.startsWith("custom_", ignoreCase = true)
}

private fun parseCustomAccentHex(raw: String): Int? {
    val hex = raw.trim().removePrefix("#")
    if (hex.length != 6 && hex.length != 8) return null
    return try {
        val v = hex.toLong(16)
        if (hex.length == 6) (0xFF000000L or v).toInt() else v.toInt()
    } catch (_: Exception) {
        null
    }
}
