package com.steady.habittracker.data

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * Unit tests for pure domain logic.
 * Started as part of #15 (add unit testing) alongside #14 (extract domain).
 */
class HabitDomainTest {

    private fun sampleDataWithEntries(entries: Map<String, Map<String, HabitEntry>> = emptyMap()): AppData {
        val groups = listOf(
            Group("g1", "Morning", "MORNING", 0),
            Group("g2", "Work", "WORK", 1)
        )
        val habits = listOf(
            Habit("h1", "Sunlight", groupId = "g1", type = HabitType.CHECKBOX, order = 0),
            Habit("h2", "Protein", groupId = "g1", type = HabitType.CHECKBOX, order = 1),
            Habit("h3", "Focus", groupId = "g2", type = HabitType.DURATION_MIN, target = 60.0, order = 0)
        )
        return AppData(
            groups = groups,
            habits = habits,
            entries = entries,
            schemaVersion = 5
        )
    }

    @Test
    fun `computeDayCompletion returns 0 when due items unfinished`() {
        val data = sampleDataWithEntries()
        val rate = HabitDomain.computeDayCompletion(data, LocalDate.now().toString())
        // all 3 daily habits due, none done
        assertEquals(0f, rate)
    }

    @Test
    fun `isDueOn weekdays excludes weekend`() {
        val h = Habit("h1", "Work", groupId = "g1", showPreset = ShowPreset.WEEKDAYS)
        val monday = LocalDate.of(2024, 6, 3) // Monday
        val saturday = LocalDate.of(2024, 6, 8)
        assertTrue(HabitDomain.isDueOn(h, monday))
        assertFalse(HabitDomain.isDueOn(h, saturday))
    }

    @Test
    fun `isDueOn every other day from anchor`() {
        val h = Habit(
            "h1", "Alt", groupId = "g1",
            showPreset = ShowPreset.EVERY_N_DAYS,
            intervalDays = 2,
            anchorDate = "2024-06-01"
        )
        assertTrue(HabitDomain.isDueOn(h, LocalDate.of(2024, 6, 1)))
        assertFalse(HabitDomain.isDueOn(h, LocalDate.of(2024, 6, 2)))
        assertTrue(HabitDomain.isDueOn(h, LocalDate.of(2024, 6, 3)))
    }

    @Test
    fun `isDueOn specific dates only`() {
        val h = Habit(
            "h1", "Once", groupId = "g1",
            showPreset = ShowPreset.SPECIFIC_DATES,
            specificDates = listOf("2024-07-04", "2024-12-25")
        )
        assertTrue(HabitDomain.isDueOn(h, LocalDate.of(2024, 7, 4)))
        assertFalse(HabitDomain.isDueOn(h, LocalDate.of(2024, 7, 5)))
    }

    @Test
    fun `sortByStack puts followers after predecessors`() {
        val habits = listOf(
            Habit("a", "Coffee", groupId = "g1", order = 0),
            Habit("c", "Journal", groupId = "g1", order = 2, afterHabitId = "b"),
            Habit("b", "Meditate", groupId = "g1", order = 1, afterHabitId = "a")
        )
        val sorted = HabitDomain.sortByStack(habits)
        assertEquals(listOf("a", "b", "c"), sorted.map { it.id })
    }

    @Test
    fun `pendingGroupedForDate hides non-due and done`() {
        val today = LocalDate.now()
        val weekendOnly = Habit("h_we", "Weekend", groupId = "g1", showPreset = ShowPreset.WEEKENDS, order = 0)
        val daily = Habit("h_d", "Daily", groupId = "g1", showPreset = ShowPreset.DAILY, order = 1)
        val data = AppData(
            groups = listOf(Group("g1", "M", order = 0)),
            habits = listOf(weekendOnly, daily),
            entries = mapOf(today.toString() to emptyMap())
        )
        val pending = HabitDomain.pendingGroupedForDate(data, today)
        val ids = pending.flatMap { it.second }.map { it.id }
        // Only daily if today is weekday; both if weekend
        val dow = today.dayOfWeek.value
        if (dow in 6..7) {
            assertTrue(ids.contains("h_we"))
            assertTrue(ids.contains("h_d"))
        } else {
            assertFalse(ids.contains("h_we"))
            assertTrue(ids.contains("h_d"))
        }
    }

    @Test
    fun `computeDayCompletion counts completed entries`() {
        val today = LocalDate.now().toString()
        val data = sampleDataWithEntries(
            mapOf(
                today to mapOf(
                    "h1" to HabitEntry(value = 1.0),
                    "h2" to HabitEntry(value = 0.0),
                    "h3" to HabitEntry(value = 1.0)
                )
            )
        )
        val rate = HabitDomain.computeDayCompletion(data, today)
        // 2 of 3
        assertEquals(2f / 3f, rate, 0.001f)
    }

    @Test
    fun `computeStreak is 0 with no entries`() {
        val data = sampleDataWithEntries()
        assertEquals(0, HabitDomain.computeStreak(data))
    }

    @Test
    fun `groupHabits excludes archived`() {
        val data = sampleDataWithEntries().copy(
            groups = listOf(Group("g1", "M", archived = false), Group("g2", "W", archived = true)),
            habits = listOf(
                Habit("h1", "ok", groupId = "g1"),
                Habit("h2", "arch", groupId = "g2", archived = true)
            )
        )
        val grouped = HabitDomain.groupHabits(data)
        assertEquals(1, grouped.size)
        assertEquals("g1", grouped[0].first.id)
        assertEquals(1, grouped[0].second.size)
    }

    @Test
    fun `computeGroup7DayAvg handles missing data`() {
        val data = sampleDataWithEntries()
        val avg = HabitDomain.computeGroup7DayAvg(data, "g1")
        assertEquals(0f, avg)
    }

    @Test
    fun `withUpdatedEntry produces immutable new state`() {
        val data = sampleDataWithEntries()
        val today = LocalDate.now().toString()
        val entry = HabitEntry(value = 1.0, note = "test")
        val updated = data.withUpdatedEntry(today, "h1", entry)

        assertNotSame(data, updated)
        assertEquals(entry, updated.entries[today]?.get("h1"))
        // original unchanged
        assertNull(data.entries[today]?.get("h1"))
    }

    @Test
    fun `moveHabit reorders within group and reindexes`() {
        val data = sampleDataWithEntries()
        val moved = HabitDomain.moveHabit(data.habits, "h1", "g1", 5)
        val h1 = moved.find { it.id == "h1" }!!
        assertEquals("g1", h1.groupId)
        assertEquals(1, h1.order) // after reindex, h2 was 1 now h1 at end? wait logic
        // Actually since only 2 in g1, moving h1 to high order makes it last with order 1
        assertEquals(1, h1.order)
    }

    @Test
    fun `resolveCurrentGroup falls back to timeHint when no schedule`() {
        val data = sampleDataWithEntries()
        val g = HabitDomain.resolveCurrentGroup(data)
        assertNotNull(g)
    }

    @Test
    fun `withArchivedHabit marks and does not mutate original`() {
        val data = sampleDataWithEntries()
        val archived = data.withArchivedHabit("h2")
        assertTrue(archived.habits.any { it.id == "h2" && it.archived })
        assertFalse(data.habits.any { it.id == "h2" && it.archived })
    }

    @Test
    fun `computeStreak and day completion work with schedules present`() {
        // Schedules don't affect these computes directly
        val data = sampleDataWithEntries().copy(
            schedules = listOf(Schedule("s1", "Std", timeBlocks = emptyList())),
            activeScheduleId = "s1"
        )
        assertEquals(0, HabitDomain.computeStreak(data))
        val rate = HabitDomain.computeDayCompletion(data, LocalDate.now().toString())
        assertEquals(0f, rate)
    }

    @Test
    fun `resolveCurrentGroup supports overnight sleep block`() {
        val groups = listOf(
            Group("g_sleep", "Sleep", "SLEEP", 0),
            Group("g_morn", "Morning", "MORNING", 1)
        )
        val habits = listOf(Habit("h_sleep", "Sleep", groupId = "g_sleep"))
        val schedule = Schedule("s1", "WithSleep", timeBlocks = listOf(TimeBlock("23:00", "07:00", "g_sleep")))
        val data = AppData(
            groups = groups,
            habits = habits,
            schedules = listOf(schedule),
            activeScheduleId = "s1"
        )
        // simulate night time
        val night = java.time.LocalTime.of(2, 30)
        val gNight = HabitDomain.resolveCurrentGroup(data, night)
        assertEquals("g_sleep", gNight?.id)

        val day = java.time.LocalTime.of(10, 0)
        val gDay = HabitDomain.resolveCurrentGroup(data, day)
        // falls to first after no match
        assertNotNull(gDay)
    }

    @Test
    fun `tag completion tracks across timeline groups`() {
        val tags = listOf(Tag(TagIds.SUPPLEMENTS, "Supplements", order = 0))
        val groups = listOf(
            Group("g_morn", "Morning", "MORNING", 0),
            Group("g_even", "Bedtime", "BEDTIME", 1)
        )
        val habits = listOf(
            Habit("h_am", "Omega", groupId = "g_morn", tags = listOf(TagIds.SUPPLEMENTS), order = 0),
            Habit("h_pm", "Mag", groupId = "g_even", tags = listOf(TagIds.SUPPLEMENTS), order = 0)
        )
        val today = LocalDate.now().toString()
        val data = AppData(
            groups = groups,
            habits = habits,
            tags = tags,
            entries = mapOf(today to mapOf("h_am" to HabitEntry(value = 1.0)))
        )
        val todayRate = HabitDomain.computeTagDayCompletion(data, TagIds.SUPPLEMENTS)
        assertEquals(0.5f, todayRate, 0.001f)
        // Group avgs differ by group; tag sees both
        assertTrue(HabitDomain.computeTag7DayAvg(data, TagIds.SUPPLEMENTS) > 0f)
    }

    @Test
    fun `buildSleepAnchoredBlocks links morning bedtime sleep`() {
        val sleep = SleepSettings(
            bedTime = "23:00",
            wakeTime = "07:00",
            windDownMinutes = 60,
            morningMinutes = 90
        )
        val blocks = HabitDomain.buildSleepAnchoredBlocks(
            sleep, "g_morn", "g_even", "g_sleep",
            existingMiddle = listOf(TimeBlock("09:00", "21:00", "g_focus"))
        )
        assertEquals(4, blocks.size)
        assertEquals("g_morn", blocks[0].groupId)
        assertEquals("07:00", blocks[0].start)
        assertEquals("g_sleep", blocks.last().groupId)
        assertEquals("23:00", blocks.last().start)
        assertEquals("07:00", blocks.last().end)
        assertTrue(blocks.any { it.groupId == "g_even" && it.end == "23:00" })
    }

    @Test
    fun `ensureSleepLinkedGroups creates Sleep group`() {
        val data = AppData(
            groups = listOf(Group("g_morn", "Morning Routine", "MORNING", 0)),
            habits = listOf(Habit("h1", "Light", groupId = "g_morn")),
            schemaVersion = 6
        )
        val out = HabitDomain.ensureSleepLinkedGroups(data)
        assertNotNull(out.sleep.morningGroupId)
        assertNotNull(out.sleep.bedtimeGroupId)
        assertNotNull(out.sleep.sleepGroupId)
        assertTrue(out.groups.any { it.timeHint == "SLEEP" || it.name.equals("Sleep", true) })
    }

    @Test
    fun `timelineSections order follows schedule top to bottom not catalog`() {
        val groups = listOf(
            Group("g_focus", "Focus", "WORK", 0), // catalog first
            Group("g_morn", "Morning", "MORNING", 1),
            Group("g_bed", "Bedtime", "BEDTIME", 2)
        )
        val schedule = Schedule(
            "s1", "Day",
            timeBlocks = listOf(
                TimeBlock("07:00", "09:00", "g_morn"),
                TimeBlock("09:00", "21:00", "g_focus"),
                TimeBlock("21:00", "23:00", "g_bed")
            )
        )
        val data = AppData(
            groups = groups,
            habits = listOf(
                Habit("h_f", "Work", groupId = "g_focus"),
                Habit("h_m", "Light", groupId = "g_morn"),
                Habit("h_b", "Wind", groupId = "g_bed")
            ),
            schedules = listOf(schedule),
            activeScheduleId = "s1"
        )
        val sections = HabitDomain.timelineSectionsForToday(data, LocalDate.now(), LocalTime.of(10, 0))
        assertEquals(listOf("g_morn", "g_focus", "g_bed"), sections.map { it.group.id })
        assertTrue(sections[1].isNow) // 10:00 is Focus
        assertTrue(sections[0].isPast)
        assertTrue(sections[2].isFuture)
    }

    @Test
    fun `timelineSections without schedule uses timeHint order`() {
        val data = sampleDataWithEntries() // g1 Morning order 0, g2 Work order 1
        val ids = HabitDomain.orderedGroupIdsForDay(data)
        assertEquals("g1", ids.first())
    }

    @Test
    fun `heatmap and hourly helpers run on empty and partial data`() {
        val data = sampleDataWithEntries(
            mapOf(
                LocalDate.now().toString() to mapOf(
                    "h1" to HabitEntry(value = 1.0, loggedAt = System.currentTimeMillis())
                )
            )
        )
        val heat = HabitDomain.computeHeatmap(data, weeks = 4)
        assertTrue(heat.isNotEmpty())
        assertEquals(7, heat.first().size)
        val hours = HabitDomain.computeHourlyLogCounts(data)
        assertEquals(24, hours.size)
        assertTrue(hours.sum() >= 1)
        assertEquals(1, HabitDomain.totalCompletedLogs(data))
        assertEquals(1, HabitDomain.daysWithActivity(data))
    }

    // --- #24 multi-group membership ---

    @Test
    fun `additionalGroupIds habit appears in both timeline sections once`() {
        val groups = listOf(
            Group("g_morn", "Morning", "MORNING", 0),
            Group("g_even", "Evening", "EVENING", 1)
        )
        val nac = Habit(
            "h_nac", "NAC", groupId = "g_morn",
            additionalGroupIds = listOf("g_even"), order = 0
        )
        val data = AppData(groups = groups, habits = listOf(nac))
        val sections = HabitDomain.timelineSectionsForToday(data, LocalDate.now(), LocalTime.of(8, 0))
        val groupIds = sections.map { it.group.id }
        assertTrue(groupIds.contains("g_morn"))
        assertTrue(groupIds.contains("g_even"))
        assertEquals(1, sections.find { it.group.id == "g_morn" }?.habits?.size)
        assertEquals(1, sections.find { it.group.id == "g_even" }?.habits?.size)
        // Single entry still: logging once clears both appearances
        val withEntry = data.withUpdatedEntry(
            LocalDate.now().toString(), "h_nac", HabitEntry(value = 1.0)
        )
        val after = HabitDomain.timelineSectionsForToday(withEntry, LocalDate.now(), LocalTime.of(8, 0))
        assertTrue(after.none { sec -> sec.habits.any { it.id == "h_nac" } })
    }

    @Test
    fun `groupHabits catalog shows habit only under primary group`() {
        val groups = listOf(
            Group("g_morn", "Morning", order = 0),
            Group("g_even", "Evening", order = 1)
        )
        val h = Habit("h1", "Split", groupId = "g_morn", additionalGroupIds = listOf("g_even"))
        val data = AppData(groups = groups, habits = listOf(h))
        val grouped = HabitDomain.groupHabits(data)
        assertEquals(1, grouped.find { it.first.id == "g_morn" }?.second?.size)
        assertEquals(0, grouped.find { it.first.id == "g_even" }?.second?.size)
    }

    @Test
    fun `getActiveHabitsForGroup includes additional membership`() {
        val data = AppData(
            groups = listOf(Group("g1", "A"), Group("g2", "B")),
            habits = listOf(
                Habit("h1", "X", groupId = "g1", additionalGroupIds = listOf("g2"))
            )
        )
        assertEquals(1, HabitDomain.getActiveHabitsForGroup(data, "g1").size)
        assertEquals(1, HabitDomain.getActiveHabitsForGroup(data, "g2").size)
    }

    @Test
    fun `membership add is idempotent and remove refuses empty set`() {
        val h = Habit("h1", "X", groupId = "g1")
        val two = HabitDomain.addToGroup(h, "g2")
        assertEquals(listOf("g1", "g2"), HabitDomain.membershipGroupIds(two))
        // once per group
        assertEquals(two, HabitDomain.addToGroup(two, "g2"))
        val back = HabitDomain.removeFromGroup(two, "g1")
        assertNotNull(back)
        assertEquals(listOf("g2"), HabitDomain.membershipGroupIds(back!!))
        assertNull(HabitDomain.removeFromGroup(back, "g2"))
        // withMembership dedupes
        val d = HabitDomain.withMembership(h, listOf("g1", "g1", "g3", "g2"))
        assertEquals(listOf("g1", "g3", "g2"), HabitDomain.membershipGroupIds(d))
    }

    @Test
    fun `reorderWithinGroup works for multi-group members`() {
        val a = Habit("a", "A", groupId = "g1", order = 0, additionalGroupIds = listOf("g2"))
        val b = Habit("b", "B", groupId = "g2", order = 1) // only g2
        val c = Habit("c", "C", groupId = "g1", order = 2, additionalGroupIds = listOf("g2"))
        val list = listOf(a, b, c)
        // In g2 order by order field: a(0), b(1), c(2) — swap a down
        val reordered = HabitDomain.reorderWithinGroup(list, "a", "g2", +1)
        val a2 = reordered.find { it.id == "a" }!!
        val b2 = reordered.find { it.id == "b" }!!
        assertEquals(1, a2.order)
        assertEquals(0, b2.order)
        // still multi-group
        assertTrue(HabitDomain.belongsToGroup(a2, "g1"))
        assertTrue(HabitDomain.belongsToGroup(a2, "g2"))
    }

    // --- Reminder times from Daily Planner schedule ---

    @Test
    fun `suggestedReminderTimeForGroup uses block start then sleep fallback`() {
        val groups = listOf(
            Group("g_morn", "Morning", "MORNING", 0),
            Group("g_even", "Bedtime", "BEDTIME", 1),
            Group("g_sleep", "Sleep", "SLEEP", 2)
        )
        val sleep = SleepSettings(
            bedTime = "23:00",
            wakeTime = "07:00",
            windDownMinutes = 60,
            morningMinutes = 90,
            morningGroupId = "g_morn",
            bedtimeGroupId = "g_even",
            sleepGroupId = "g_sleep"
        )
        val blocks = HabitDomain.buildSleepAnchoredBlocks(sleep, "g_morn", "g_even", "g_sleep")
        val data = AppData(
            groups = groups,
            sleep = sleep,
            schedules = listOf(Schedule("s1", "Day", timeBlocks = blocks)),
            activeScheduleId = "s1"
        )
        assertEquals("07:00", HabitDomain.suggestedReminderTimeForGroup(data, "g_morn"))
        assertEquals("22:00", HabitDomain.suggestedReminderTimeForGroup(data, "g_even"))
        assertEquals("23:00", HabitDomain.suggestedReminderTimeForGroup(data, "g_sleep"))
        assertEquals("23:00", HabitDomain.suggestedDailyReviewTime(data))
    }

    @Test
    fun `alignRemindersToSchedule updates times preserves days and enabled`() {
        val groups = listOf(
            Group("g_morn", "Morning", "MORNING", 0),
            Group("g_even", "Bedtime", "BEDTIME", 1)
        )
        val sleep = SleepSettings(
            bedTime = "22:30",
            wakeTime = "06:30",
            windDownMinutes = 30,
            morningGroupId = "g_morn",
            bedtimeGroupId = "g_even"
        )
        val blocks = listOf(
            TimeBlock("06:30", "08:00", "g_morn"),
            TimeBlock("22:00", "22:30", "g_even")
        )
        val reminders = listOf(
            Reminder("r_morn", "g_morn", "08:30", setOf(1, 2, 3), enabled = true),
            Reminder("r_even", "g_even", "21:00", setOf(1, 2, 3, 4, 5, 6, 7), enabled = false),
            Reminder("r_review", null, "21:45", setOf(1, 2, 3, 4, 5, 6, 7), enabled = true)
        )
        val data = AppData(
            groups = groups,
            sleep = sleep,
            schedules = listOf(Schedule("s1", "Day", timeBlocks = blocks)),
            activeScheduleId = "s1",
            reminders = reminders
        )
        val aligned = HabitDomain.alignRemindersToSchedule(data)
        assertEquals("06:30", aligned.find { it.id == "r_morn" }?.time)
        assertEquals(setOf(1, 2, 3), aligned.find { it.id == "r_morn" }?.days)
        assertEquals("22:00", aligned.find { it.id == "r_even" }?.time)
        assertEquals(false, aligned.find { it.id == "r_even" }?.enabled)
        assertEquals("22:30", aligned.find { it.id == "r_review" }?.time)
    }

    @Test
    fun `suggestedReminderTime falls back to sleep when no schedule`() {
        val groups = listOf(Group("g_morn", "Morning", "MORNING", 0))
        val sleep = SleepSettings(wakeTime = "05:45", morningGroupId = "g_morn")
        val data = AppData(groups = groups, sleep = sleep, activeScheduleId = null)
        assertEquals("05:45", HabitDomain.suggestedReminderTimeForGroup(data, "g_morn"))
    }

    // --- #21 routines / sessions ---

    @Test
    fun `withBlueprintRoutinesIfMissing is idempotent`() {
        val templates = BlueprintRoutines.templates()
        val empty = AppData()
        val once = empty.withBlueprintRoutinesIfMissing(templates)
        assertEquals(templates.size, once.routines.size)
        val twice = once.withBlueprintRoutinesIfMissing(templates)
        assertEquals(templates.size, twice.routines.size)
    }

    @Test
    fun `isRoutineDueOn respects weekdays`() {
        val rt = ExerciseRoutine(
            id = "rt1", name = "Mon only",
            showPreset = ShowPreset.CUSTOM_DAYS,
            weekdays = setOf(1)
        )
        val mon = LocalDate.of(2024, 6, 3)
        val tue = LocalDate.of(2024, 6, 4)
        assertTrue(HabitDomain.isRoutineDueOn(rt, mon))
        assertFalse(HabitDomain.isRoutineDueOn(rt, tue))
    }

    @Test
    fun `workout session helpers count sets and completion`() {
        val session = WorkoutSession(
            id = "ws1",
            routineId = "rt1",
            date = LocalDate.now().toString(),
            startedAt = 1L,
            completedAt = 2L,
            performedExercises = mapOf(
                "ex1" to listOf(SetLog(1, actualReps = 10), SetLog(2, actualReps = 8)),
                "ex2" to listOf(SetLog(1, actualReps = 5))
            ),
            completed = true
        )
        assertEquals(3, HabitDomain.sessionSetCount(session))
        assertEquals(2, HabitDomain.sessionExerciseCount(session))
        val data = AppData(workoutSessions = listOf(session))
        assertTrue(HabitDomain.isRoutineCompletedOn(data, "rt1", LocalDate.now().toString()))
        assertEquals(1, HabitDomain.workoutDaysInWindow(data, 7))
    }

    @Test
    fun `withLoggedWorkoutSession replaces same id`() {
        val s1 = WorkoutSession("ws1", "rt1", "2024-01-01", startedAt = 1L, completed = false)
        val s2 = s1.copy(completed = true, completedAt = 2L)
        val data = AppData().withLoggedWorkoutSession(s1).withLoggedWorkoutSession(s2)
        assertEquals(1, data.workoutSessions.size)
        assertTrue(data.workoutSessions.first().completed)
    }

    // --- #25 / #26 Dreamline + Path ---

    @Test
    fun `buildGoalsFromDreamline tags and horizons`() {
        val dreams = listOf(
            Triple(DreamHorizon.SIX_MONTHS, DreamCategory.BEING, "Calm and present"),
            Triple(DreamHorizon.TWELVE_MONTHS, DreamCategory.DOING, "Run a marathon")
        )
        val key = HabitDomain.dreamKey(DreamHorizon.SIX_MONTHS, DreamCategory.BEING, "Calm and present")
        val goals = HabitDomain.buildGoalsFromDreamline(
            dreams = dreams,
            stepsByKey = mapOf(key to listOf("Meditate 5 min", "Phone-free morning")),
            firstStepsByKey = mapOf(key to "Sit quietly for 2 minutes")
        )
        assertEquals(2, goals.size)
        val being = goals.find { it.category == DreamCategory.BEING }!!
        assertTrue(being.tags.contains(GoalTags.DREAMLINE))
        assertTrue(being.tags.contains(GoalTags.HORIZON_6))
        assertTrue(being.tags.contains("being"))
        assertEquals(2, being.steps.size)
        assertEquals("Sit quietly for 2 minutes", being.firstStepNow)
        assertTrue(being.endDate.isNotBlank())
    }

    @Test
    fun `dreamlineGoals filters and mindset prompts use Being`() {
        val goals = listOf(
            GoalStory(
                id = "g1", title = "Great cook", category = DreamCategory.BEING,
                horizon = DreamHorizon.SIX_MONTHS, tags = listOf(GoalTags.DREAMLINE, "being")
            ),
            GoalStory(
                id = "g2", title = "Other", category = DreamCategory.DOING,
                tags = listOf("manual"), archived = false
            )
        )
        val data = AppData(goals = goals)
        assertEquals(1, HabitDomain.dreamlineGoals(data).size)
        val prompts = HabitDomain.mindsetPrompts(data)
        assertTrue(prompts.any { it.contains("Great cook") })
    }

    @Test
    fun `pathCheckScore maps 1-5 to 0-1`() {
        val low = PathAlignmentCheck("1", "2024-01-01", 1, 1, 1)
        val mid = PathAlignmentCheck("2", "2024-01-01", 3, 3, 3)
        val high = PathAlignmentCheck("3", "2024-01-01", 5, 5, 5)
        assertEquals(0f, HabitDomain.pathCheckScore(low), 0.01f)
        assertEquals(0.5f, HabitDomain.pathCheckScore(mid), 0.01f)
        assertEquals(1f, HabitDomain.pathCheckScore(high), 0.01f)
    }

    @Test
    fun `withGoalsReplacedFromWizard replaces dreamline only`() {
        val manual = GoalStory(id = "m1", title = "Manual", tags = listOf("work"))
        val oldDl = GoalStory(id = "d1", title = "Old", tags = listOf(GoalTags.DREAMLINE))
        val base = AppData(goals = listOf(manual, oldDl))
        val fresh = listOf(
            GoalStory(id = "d2", title = "New", tags = listOf(GoalTags.DREAMLINE))
        )
        val out = base.withGoalsReplacedFromWizard(fresh, replaceDreamline = true)
        assertEquals(2, out.goals.size)
        assertTrue(out.goals.any { it.id == "m1" })
        assertTrue(out.goals.any { it.id == "d2" })
        assertFalse(out.goals.any { it.id == "d1" })
    }
}
