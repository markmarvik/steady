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
            schemaVersion = 5,
            // Calendar midnight in tests — avoid flaky 4am logical-day rollover
            dayStartHour = 0
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
    fun `computeHabitStreak counts consecutive due completions`() {
        val today = LocalDate.now()
        val entries = (0..2).associate { i ->
            val d = today.minusDays(i.toLong()).toString()
            d to mapOf("h1" to HabitEntry(value = 1.0))
        }
        val data = sampleDataWithEntries(entries)
        assertEquals(3, HabitDomain.computeHabitStreak(data, "h1"))
        assertEquals(0, HabitDomain.computeHabitStreak(data, "h2"))
    }

    @Test
    fun `computeHabitStreak ignores open today and keeps prior chain`() {
        val today = LocalDate.now()
        val entries = mapOf(
            today.minusDays(1).toString() to mapOf("h1" to HabitEntry(value = 1.0)),
            today.minusDays(2).toString() to mapOf("h1" to HabitEntry(value = 1.0))
            // today intentionally missing
        )
        val data = sampleDataWithEntries(entries)
        assertEquals(2, HabitDomain.computeHabitStreak(data, "h1"))
    }

    @Test
    fun `timelineSectionsForToday includeCompleted shows finished habits`() {
        val today = LocalDate.now()
        val data = sampleDataWithEntries(
            mapOf(today.toString() to mapOf("h1" to HabitEntry(value = 1.0)))
        )
        val pendingOnly = HabitDomain.timelineSectionsForToday(data, today, LocalTime.of(10, 0))
        val pendingIds = pendingOnly.flatMap { it.habits }.map { it.id }.toSet()
        assertFalse(pendingIds.contains("h1"))

        val withDone = HabitDomain.timelineSectionsForToday(
            data, today, LocalTime.of(10, 0), includeCompleted = true
        )
        val ids = withDone.flatMap { it.habits }.map { it.id }.toSet()
        assertTrue(ids.contains("h1"))
        assertTrue(ids.contains("h2"))
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
    fun `withPermanentlyDeletedHabit removes habit entries and links`() {
        val today = LocalDate.now().toString()
        val data = AppData(
            groups = listOf(Group("g1", "G")),
            habits = listOf(
                Habit("h1", "Keep", groupId = "g1"),
                Habit("h2", "Gone", groupId = "g1", archived = true, afterHabitId = "h1"),
                Habit("h3", "Stacked", groupId = "g1", afterHabitId = "h2")
            ),
            entries = mapOf(
                today to mapOf(
                    "h1" to HabitEntry(1.0),
                    "h2" to HabitEntry(1.0, note = "dose")
                )
            ),
            autoSuggestions = listOf(
                AutoSuggestion(habitId = "h2", date = today, value = 1.0)
            ),
            schemaVersion = 13
        )
        val gone = data.withPermanentlyDeletedHabit("h2")
        assertTrue(gone.habits.none { it.id == "h2" })
        assertTrue(gone.habits.any { it.id == "h1" })
        assertEquals(null, gone.habits.find { it.id == "h3" }?.afterHabitId)
        assertFalse(gone.entries[today]?.containsKey("h2") == true)
        assertTrue(gone.entries[today]?.containsKey("h1") == true)
        assertTrue(gone.autoSuggestions.none { it.habitId == "h2" })
        // bulk: only archived
        val multi = data.copy(
            habits = data.habits.map {
                if (it.id == "h1") it.copy(archived = true) else it
            }
        ).withPermanentlyDeletedArchivedHabits()
        assertTrue(multi.habits.none { it.archived })
        assertTrue(multi.habits.any { it.id == "h3" && !it.archived })
        assertTrue(multi.habits.none { it.id == "h1" || it.id == "h2" })
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
        val data = AppData(groups = groups, habits = listOf(nac), dayStartHour = 0)
        val sections = HabitDomain.timelineSectionsForToday(data, LocalDate.now(), LocalTime.of(8, 0))
        val groupIds = sections.map { it.group.id }
        assertTrue(groupIds.contains("g_morn"))
        assertTrue(groupIds.contains("g_even"))
        assertEquals(1, sections.find { it.group.id == "g_morn" }?.habits?.size)
        assertEquals(1, sections.find { it.group.id == "g_even" }?.habits?.size)
        // Completing morning only — evening stays pending
        val mornDone = data.withUpdatedEntry(
            LocalDate.now().toString(), "h_nac", HabitEntry(value = 1.0), groupId = "g_morn"
        )
        val afterMorn = HabitDomain.timelineSectionsForToday(
            mornDone, LocalDate.now(), LocalTime.of(8, 0)
        )
        assertTrue(afterMorn.none { sec ->
            sec.group.id == "g_morn" && sec.habits.any { it.id == "h_nac" }
        })
        assertTrue(afterMorn.any { sec ->
            sec.group.id == "g_even" && sec.habits.any { it.id == "h_nac" }
        })
        // Completing evening clears the other slot
        val both = mornDone.withUpdatedEntry(
            LocalDate.now().toString(), "h_nac", HabitEntry(value = 1.0), groupId = "g_even"
        )
        val afterBoth = HabitDomain.timelineSectionsForToday(
            both, LocalDate.now(), LocalTime.of(8, 0)
        )
        assertTrue(afterBoth.none { sec -> sec.habits.any { it.id == "h_nac" } })
    }

    @Test
    fun `multi-group entry keys are independent`() {
        val h = Habit("h1", "X", groupId = "g1", additionalGroupIds = listOf("g2"))
        val data = AppData(habits = listOf(h), dayStartHour = 0)
        val d = LocalDate.now().toString()
        val am = data.withUpdatedEntry(d, "h1", HabitEntry(value = 1.0), "g1")
        assertTrue(HabitDomain.isEntryCompleted(am.entryFor(d, h, "g1")))
        assertFalse(HabitDomain.isEntryCompleted(am.entryFor(d, h, "g2")))
        assertEquals("h1@g1", HabitEntryKeys.key(h, "g1"))
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

    @Test
    fun `getToday respects dayStartHour before rollover`() {
        val at3am = java.time.LocalDateTime.of(2026, 7, 23, 3, 30)
        assertEquals("2026-07-22", HabitDomain.getToday(4, at3am))
        val at5am = java.time.LocalDateTime.of(2026, 7, 23, 5, 0)
        assertEquals("2026-07-23", HabitDomain.getToday(4, at5am))
        // Midnight rollover when hour is 0
        assertEquals("2026-07-23", HabitDomain.getToday(0, at3am))
    }

    // --- Steady Momentum scoring (v10) ---

    @Test
    fun `computeDayPoints awards base per completed habit`() {
        val today = LocalDate.now().toString()
        val data = sampleDataWithEntries(
            mapOf(today to mapOf("h1" to HabitEntry(value = 1.0), "h2" to HabitEntry(value = 1.0)))
        )
        val pts = HabitDomain.computeDayPoints(data, today)
        // 2 * 10 base + solid day bonus (2/3 not solid? 2/3 ≈ 0.66 ≥ 0.6 → solid +10)
        // not full clear → no +25
        assertEquals(20 + HabitDomain.SOLID_DAY_BONUS, pts)
    }

    @Test
    fun `computeDayPoints uses habit pointValue importance`() {
        val today = LocalDate.now().toString()
        val base = sampleDataWithEntries(
            mapOf(today to mapOf("h1" to HabitEntry(value = 1.0)))
        )
        val habits = base.habits.map {
            when (it.id) {
                "h1" -> it.copy(pointValue = 25)
                else -> it
            }
        }
        val data = base.copy(habits = habits)
        val b = HabitDomain.computeDayPointsBreakdown(data, today)
        assertEquals(25, b.habitPoints)
    }

    @Test
    fun `screen overage penalty deducts points`() {
        val today = LocalDate.now().toString()
        val screenHabit = Habit(
            id = "h_screen",
            name = "Screen",
            groupId = "g1",
            extensionType = ExtensionType.SCREEN_USAGE,
            extensionConfig = ExtensionConfig(dailyLimitMinutes = 120)
        )
        val base = sampleDataWithEntries(
            mapOf(today to mapOf("h1" to HabitEntry(value = 1.0), "h2" to HabitEntry(value = 1.0)))
        )
        val data = base.copy(
            dayStartHour = 0,
            habits = base.habits + screenHabit,
            sensorSnapshots = listOf(
                SensorSnapshot(
                    id = "ss1",
                    habitId = "h_screen",
                    date = today,
                    readings = mapOf("screen_min" to "180") // 60m over → 4 chunks * 2 = 8
                )
            )
        )
        assertEquals(8, HabitDomain.screenOveragePenalty(data, today))
        val b = HabitDomain.computeDayPointsBreakdown(data, today)
        assertEquals(8, b.screenPenalty)
        // Screen is a strip tool (not due). h1+h2 of 3 due → habit 20 + solid 10 − pen 8
        assertEquals(20 + HabitDomain.SOLID_DAY_BONUS - 8, b.total)
        assertFalse(ExtensionCatalog.livesOnDayTimeline(screenHabit))
    }

    @Test
    fun `computeDayPoints full clear and target bonus`() {
        val today = LocalDate.now().toString()
        val data = sampleDataWithEntries(
            mapOf(
                today to mapOf(
                    "h1" to HabitEntry(value = 1.0),
                    "h2" to HabitEntry(value = 1.0),
                    "h3" to HabitEntry(value = 60.0) // meets target 60
                )
            )
        )
        val b = HabitDomain.computeDayPointsBreakdown(data, today)
        assertEquals(30, b.habitPoints)
        assertEquals(HabitDomain.TARGET_BONUS, b.targetBonuses)
        assertEquals(HabitDomain.FULL_CLEAR_BONUS, b.fullClearBonus)
        assertEquals(HabitDomain.SOLID_DAY_BONUS, b.solidDayBonus)
        assertEquals(30 + 5 + 25 + 10, b.total)
    }

    @Test
    fun `skipped habits award zero points`() {
        val today = LocalDate.now().toString()
        val data = sampleDataWithEntries(
            mapOf(today to mapOf("h1" to HabitEntry(value = 0.0, skipped = true)))
        )
        val b = HabitDomain.computeDayPointsBreakdown(data, today)
        assertEquals(0, b.habitPoints)
    }

    @Test
    fun `level curve and titles`() {
        assertEquals(1, HabitDomain.computeLevel(0))
        assertEquals(1, HabitDomain.computeLevel(499))
        assertEquals(2, HabitDomain.computeLevel(500))
        assertEquals(100, HabitDomain.pointsToNextLevel(400))
        assertEquals("Steady", HabitDomain.levelTitle(1))
        assertEquals("Anchored", HabitDomain.levelTitle(5))
        assertEquals("Unshakable", HabitDomain.levelTitle(20))
    }

    @Test
    fun `withFinalizedScoreHistory banks yesterday once`() {
        val yesterday = LocalDate.now().minusDays(1).toString()
        val today = LocalDate.now().toString()
        val data = sampleDataWithEntries(
            mapOf(
                yesterday to mapOf(
                    "h1" to HabitEntry(value = 1.0),
                    "h2" to HabitEntry(value = 1.0),
                    "h3" to HabitEntry(value = 1.0)
                )
            )
        )
        val finalized = HabitDomain.withFinalizedScoreHistory(data, today)
        assertTrue(finalized.score.history.any { it.date == yesterday })
        assertTrue(finalized.score.lifetimePoints > 0)
        val again = HabitDomain.withFinalizedScoreHistory(finalized, today)
        assertEquals(finalized.score.lifetimePoints, again.score.lifetimePoints)
        assertEquals(finalized.score.history.size, again.score.history.size)
    }

    // --- Smart notifications ---

    @Test
    fun `quiet hours overnight window`() {
        val prefs = NotificationPrefs(quietStart = "22:30", quietEnd = "07:00")
        assertTrue(HabitDomain.isInQuietHours(prefs, HabitDomain.parseTimeToMinutes("23:00")))
        assertTrue(HabitDomain.isInQuietHours(prefs, HabitDomain.parseTimeToMinutes("02:00")))
        assertFalse(HabitDomain.isInQuietHours(prefs, HabitDomain.parseTimeToMinutes("08:00")))
        assertFalse(HabitDomain.isInQuietHours(prefs, HabitDomain.parseTimeToMinutes("12:00")))
    }

    @Test
    fun `pendingHabitsForReminder uses multi-group membership`() {
        val today = LocalDate.now()
        val h = Habit("h1", "NAC", groupId = "g1", additionalGroupIds = listOf("g2"))
        val data = AppData(
            groups = listOf(Group("g1", "M"), Group("g2", "E")),
            habits = listOf(h)
        )
        val forG2 = HabitDomain.pendingHabitsForReminder(data, "g2", today)
        assertEquals(1, forG2.size)
        assertEquals("h1", forG2[0].id)
    }

    @Test
    fun `decideReminder skips empty group and rate limits`() {
        val today = LocalDate.now().toString()
        val data = sampleDataWithEntries(
            mapOf(
                today to mapOf(
                    "h1" to HabitEntry(value = 1.0),
                    "h2" to HabitEntry(value = 1.0),
                    "h3" to HabitEntry(value = 1.0)
                )
            )
        )
        val empty = HabitDomain.decideReminder(data, "g1", "Morning", today)
        assertFalse(empty.show)

        val limited = data.copy(
            notificationPrefs = NotificationPrefs(maxPerDay = 2, firesDate = today, firesCount = 2)
        )
        // force pending by clearing entries
        val withPending = limited.copy(entries = emptyMap())
        val blocked = HabitDomain.decideReminder(withPending, "g1", "Morning", today)
        assertFalse(blocked.show)
    }

    @Test
    fun `decideReminder includes points remaining copy`() {
        val today = LocalDate.now().toString()
        val data = sampleDataWithEntries()
        val d = HabitDomain.decideReminder(data, "g1", "Morning", today)
        assertTrue(d.show)
        assertTrue(d.body.contains("pts") || d.body.contains("Sunlight") || d.body.contains("Protein"))
    }

    @Test
    fun `score and notificationPrefs survive JSON round trip`() {
        val today = LocalDate.now().toString()
        val original = sampleDataWithEntries(
            mapOf(today to mapOf("h1" to HabitEntry(value = 1.0), "h2" to HabitEntry(value = 1.0)))
        ).copy(
            schemaVersion = 10,
            score = ScoreState(
                lifetimePoints = 1200,
                history = listOf(DayScore("2024-01-01", 80, 1f)),
                lastFinalizedDate = "2024-01-01"
            ),
            notificationPrefs = NotificationPrefs(
                quietStart = "23:00",
                quietEnd = "06:30",
                maxPerDay = 3,
                adaptiveTiming = false,
                streakRiskNudge = true
            )
        )
        val json = kotlinx.serialization.json.Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
        val encoded = json.encodeToString(AppData.serializer(), original)
        assertTrue(encoded.contains("\"score\""))
        assertTrue(encoded.contains("\"notificationPrefs\""))
        assertTrue(encoded.contains("lifetimePoints"))
        val decoded = json.decodeFromString(AppData.serializer(), encoded)
        assertEquals(1200, decoded.score.lifetimePoints)
        assertEquals(1, decoded.score.history.size)
        assertEquals("23:00", decoded.notificationPrefs.quietStart)
        assertEquals(3, decoded.notificationPrefs.maxPerDay)
        assertFalse(decoded.notificationPrefs.adaptiveTiming)
    }

    @Test
    fun `legacy JSON without score key defaults cleanly`() {
        val minimal = """
            {"groups":[],"habits":[],"entries":{},"reminders":[],"schemaVersion":8}
        """.trimIndent()
        val json = kotlinx.serialization.json.Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
        val decoded = json.decodeFromString(AppData.serializer(), minimal)
        assertEquals(0, decoded.score.lifetimePoints)
        assertTrue(decoded.score.history.isEmpty())
        assertEquals(4, decoded.notificationPrefs.maxPerDay)
    }

    @Test
    fun `rebuildScoreFromEntries banks past day from logs`() {
        val yesterday = LocalDate.now().minusDays(1).toString()
        val data = sampleDataWithEntries(
            mapOf(
                yesterday to mapOf(
                    "h1" to HabitEntry(value = 1.0),
                    "h2" to HabitEntry(value = 1.0),
                    "h3" to HabitEntry(value = 1.0)
                )
            )
        )
        val rebuilt = HabitDomain.rebuildScoreFromEntries(data)
        assertTrue(rebuilt.score.history.any { it.date == yesterday && it.points > 0 })
        assertTrue(rebuilt.score.lifetimePoints > 0)
    }

    @Test
    fun `resolveEffectiveReminderTime clamps adaptive shift`() {
        val zone = java.time.ZoneId.systemDefault()
        // Build 5 logs at 10:00 for g1 habits
        val entries = mutableMapOf<String, Map<String, HabitEntry>>()
        repeat(6) { i ->
            val day = LocalDate.now().minusDays(i.toLong()).toString()
            val at = LocalDate.now().minusDays(i.toLong())
                .atTime(10, 0)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
            entries[day] = mapOf("h1" to HabitEntry(value = 1.0, loggedAt = at))
        }
        val data = sampleDataWithEntries(entries).copy(
            notificationPrefs = NotificationPrefs(adaptiveTiming = true)
        )
        val r = Reminder("r1", "g1", "08:00", setOf(1, 2, 3, 4, 5, 6, 7), enabled = true)
        val effective = HabitDomain.resolveEffectiveReminderTime(r, data)
        // 08:00 + 45 clamp toward 10:00 → 08:45
        assertEquals("08:45", effective)
    }
}
