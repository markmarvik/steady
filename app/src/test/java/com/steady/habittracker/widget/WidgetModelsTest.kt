package com.steady.habittracker.widget

import com.steady.habittracker.data.AppData
import com.steady.habittracker.data.Group
import com.steady.habittracker.data.Habit
import com.steady.habittracker.data.HabitEntry
import com.steady.habittracker.data.HabitType
import com.steady.habittracker.data.HabitDomain
import com.steady.habittracker.data.Schedule
import com.steady.habittracker.data.TimeBlock
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalTime

class WidgetModelsTest {

    private fun data(): AppData {
        val groups = listOf(
            Group("g1", "Morning", "MORNING", 0),
            Group("g2", "Work", "WORK", 1),
            Group("g3", "Evening", "EVENING", 2)
        )
        val habits = listOf(
            Habit("h1", "Sunlight", groupId = "g1", type = HabitType.CHECKBOX, order = 0),
            Habit("h2", "Breathing", groupId = "g1", type = HabitType.DURATION_MIN, order = 1),
            Habit("h3", "Focus", groupId = "g2", type = HabitType.CHECKBOX, order = 0),
            Habit("h4", "Protein", groupId = "g2", type = HabitType.CHECKBOX, order = 1),
            Habit("h5", "Wind down", groupId = "g3", type = HabitType.CHECKBOX, order = 0)
        )
        return AppData(groups = groups, habits = habits, schemaVersion = 5)
    }

    @Test
    fun `buildWidgetRows lists all pending across groups`() {
        val rows = buildWidgetRows(data(), LocalTime.of(9, 0))
        val habitRows = rows.filter { it.kind == WidgetRowKind.HABIT }
        assertEquals(5, habitRows.size)
        assertTrue(rows.any { it.kind == WidgetRowKind.SECTION && it.title.contains("Morning") })
    }

    @Test
    fun `buildWidgetRows keeps chronological order not now-first`() {
        val groups = listOf(
            Group("g_morn", "Morning", "MORNING", 0),
            Group("g_work", "Work", "WORK", 1),
            Group("g_even", "Evening", "EVENING", 2)
        )
        val schedule = Schedule(
            "s1", "Day",
            timeBlocks = listOf(
                TimeBlock("07:00", "09:00", "g_morn"),
                TimeBlock("09:00", "18:00", "g_work"),
                TimeBlock("18:00", "23:00", "g_even")
            )
        )
        val d = AppData(
            groups = groups,
            habits = listOf(
                Habit("h1", "A", groupId = "g_morn"),
                Habit("h2", "B", groupId = "g_work"),
                Habit("h3", "C", groupId = "g_even")
            ),
            schedules = listOf(schedule),
            activeScheduleId = "s1"
        )
        // Afternoon: Work is Now, but Morning section still first
        val rows = buildWidgetRows(d, LocalTime.of(14, 0))
        val sections = rows.filter { it.kind == WidgetRowKind.SECTION }
        assertEquals("Morning", sections[0].title)
        assertTrue(sections[1].title.startsWith("●"))
        assertTrue(sections[1].title.contains("Work"))
        assertEquals("Evening", sections[2].title)
    }

    @Test
    fun `buildWidgetRows hides completed`() {
        val today = HabitDomain.getToday()
        val d = data().copy(
            entries = mapOf(
                today to mapOf(
                    "h1" to HabitEntry(value = 1.0),
                    "h3" to HabitEntry(value = 1.0)
                )
            )
        )
        val habitRows = buildWidgetRows(d, LocalTime.of(9, 0)).filter { it.kind == WidgetRowKind.HABIT }
        assertEquals(3, habitRows.size)
        assertTrue(habitRows.none { it.habitId == "h1" })
    }

    @Test
    fun `pendingRowIcon never shows check for pending items`() {
        assertEquals("☐", pendingRowIcon(HabitType.CHECKBOX.name, true))
        assertEquals("#", pendingRowIcon(HabitType.COUNTER.name, false))
        assertEquals("m", pendingRowIcon(HabitType.DURATION_MIN.name, false))
        assertEquals("±", pendingRowIcon(HabitType.SCALE_1_5.name, false))
        assertEquals("✎", pendingRowIcon(HabitType.NOTE.name, false))
        assertTrue(pendingRowIcon(HabitType.COUNTER.name, false) != "✓")
    }

    @Test
    fun `pendingCountToday and title`() {
        val d = data()
        assertEquals(5, pendingCountToday(d))
        assertTrue(widgetTitle(d).contains("5 left"))
        val today = HabitDomain.getToday()
        val done = d.copy(
            entries = mapOf(
                today to d.habits.associate { it.id to HabitEntry(value = 1.0) }
            )
        )
        assertEquals(0, pendingCountToday(done))
        assertTrue(widgetTitle(done).contains("All done"))
    }
}
