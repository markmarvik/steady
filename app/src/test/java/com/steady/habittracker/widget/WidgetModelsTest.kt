package com.steady.habittracker.widget

import com.steady.habittracker.data.AppData
import com.steady.habittracker.data.Group
import com.steady.habittracker.data.Habit
import com.steady.habittracker.data.HabitEntry
import com.steady.habittracker.data.HabitType
import com.steady.habittracker.data.HabitDomain
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
