package com.steady.habittracker.data

import java.time.LocalDate
import java.time.LocalTime

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

    /** Timeline groups this habit should appear under (primary + additional). */
    fun membershipGroupIds(habit: Habit): List<String> =
        (listOf(habit.groupId) + habit.additionalGroupIds).distinct()

    /** Whether habit belongs to [groupId] via primary or additional membership. */
    fun belongsToGroup(habit: Habit, groupId: String): Boolean =
        habit.groupId == groupId || groupId in habit.additionalGroupIds

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
     * Only sections with pending habits are returned (action-oriented Today).
     */
    fun timelineSectionsForToday(
        data: AppData,
        date: LocalDate = LocalDate.now(),
        now: LocalTime = LocalTime.now()
    ): List<TimelineSection> {
        val dateStr = date.toString()
        val entries = data.entries[dateStr] ?: emptyMap()
        val duePending = habitsDueOn(data, date).filter { isPendingEntry(entries[it.id]) }
        if (duePending.isEmpty()) return emptyList()

        // Multi-group expansion (#24): habit appears under every membership group
        val byGroup = mutableMapOf<String, MutableList<Habit>>()
        duePending.forEach { h ->
            membershipGroupIds(h).forEach { gid ->
                byGroup.getOrPut(gid) { mutableListOf() }.add(h)
            }
        }
        val orderedIds = orderedGroupIdsForDay(data, now)
        val currentId = resolveCurrentGroup(data, now)?.id
        val currentIndex = orderedIds.indexOf(currentId).takeIf { it >= 0 }

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
                    habits = sortByStack(habits, data.habits),
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
                    habits = sortByStack(habits, data.habits),
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
        val sections = timelineSectionsForToday(data, date, now)
        val ordered = orderedGroupIdsForDay(data, now)
        val current = resolveCurrentGroup(data, now)
        val currentId = current?.id
        val idx = ordered.indexOf(currentId).takeIf { it >= 0 } ?: -1
        val nextPending = sections.firstOrNull { s ->
            val si = ordered.indexOf(s.group.id)
            !s.isNow && (idx < 0 || si > idx)
        } ?: sections.firstOrNull { !it.isNow && it.isFuture }
        val nowHasPending = sections.any { it.isNow }
        return DayProgressionCue(
            nowGroupName = current?.name,
            nowHasPending = nowHasPending,
            nextGroupName = nextPending?.group?.name
        )
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
            groups.add(Group(preferredId, name, hint, order = order))
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

    /** Simple theme color resolver (used by app + widget). Supports AMOLED (OLED), dark, and light modes.
     * Foreground/accent is chosen separately via colorScheme.
     */
    fun resolveThemeColors(data: AppData): ThemeColors {
        val accent = when (data.colorScheme) {
            "blue" -> 0xFF3B82F6.toInt()
            "orange" -> 0xFFF97316.toInt()
            "purple" -> 0xFF8B5CF6.toInt()
            "slate" -> 0xFF64748B.toInt()
            "teal" -> 0xFF14B8A6.toInt()
            "red" -> 0xFFEF4444.toInt()
            else -> 0xFF22C55E.toInt() // green
        }
        val mode = data.backgroundMode
        val isAmoled = mode == "amoled"
        val isLight = mode == "light"

        val bg = when {
            isAmoled -> 0xFF000000.toInt()
            isLight -> 0xFFF8FAFC.toInt()
            else -> 0xFF0F172A.toInt()
        }
        val surface = when {
            isAmoled -> 0xFF0F0F0F.toInt()
            isLight -> 0xFFFFFFFF.toInt()
            else -> 0xFF1E2937.toInt()
        }
        val widgetRowBg = when {
            isAmoled -> 0xFF1A1A1A.toInt()
            isLight -> 0xFFE2E8F0.toInt()
            else -> 0xFF1E3A5F.toInt()
        }
        return ThemeColors(bg, surface, accent, widgetRowBg)
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

    /** Pure function to reorder/move a habit between or within groups. Returns new immutable list. */
    fun moveHabit(currentHabits: List<Habit>, habitId: String, newGroupId: String, newOrder: Int): List<Habit> {
        val habit = currentHabits.find { it.id == habitId } ?: return currentHabits
        val without = currentHabits.filter { it.id != habitId }
        val updated = habit.copy(groupId = newGroupId, order = newOrder)
        val newList = (without + updated).sortedWith(
            compareBy<Habit> { if (it.groupId == newGroupId) 0 else 1 }.thenBy { it.order }
        )
        // Reindex orders per group
        return newList.groupBy { it.groupId }.flatMap { (_, list) ->
            list.sortedBy { it.order }.mapIndexed { i, h -> h.copy(order = i) }
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
    now: LocalTime = LocalTime.now()
) = HabitDomain.timelineSectionsForToday(data, date, now)
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
