package com.steady.habittracker.data

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

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
    fun `computeDayCompletion returns 0 for empty day`() {
        val data = sampleDataWithEntries()
        val rate = HabitDomain.computeDayCompletion(data, LocalDate.now().toString())
        assertEquals(0f, rate)
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
}
