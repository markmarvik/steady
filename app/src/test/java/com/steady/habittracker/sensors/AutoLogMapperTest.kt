package com.steady.habittracker.sensors

import com.steady.habittracker.data.AutoLogMode
import com.steady.habittracker.data.AutoSource
import com.steady.habittracker.data.Habit
import com.steady.habittracker.data.HabitType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AutoLogMapperTest {

    @Test
    fun `evening screen checkbox passes under threshold`() {
        val h = Habit(
            id = "h1",
            name = "Phone-free evening",
            groupId = "g1",
            type = HabitType.CHECKBOX,
            autoSource = AutoSource.SCREEN_AFTER_WINDDOWN,
            autoMode = AutoLogMode.SUGGEST,
            autoThreshold = 20.0
        )
        val pass = AutoLogMapper.mapToHabitValue(
            h,
            AutoLogMapper.Reading(AutoSource.SCREEN_AFTER_WINDDOWN, 10.0)
        )!!
        assertEquals(1.0, pass.value)
        val fail = AutoLogMapper.mapToHabitValue(
            h,
            AutoLogMapper.Reading(AutoSource.SCREEN_AFTER_WINDDOWN, 45.0)
        )!!
        assertEquals(0.0, fail.value)
    }

    @Test
    fun `light maps to dark scale`() {
        val h = Habit(
            id = "h1",
            name = "Dark",
            groupId = "g1",
            type = HabitType.SCALE_1_5,
            autoSource = AutoSource.LIGHT_BEDTIME_AVG
        )
        val dark = AutoLogMapper.mapToHabitValue(
            h,
            AutoLogMapper.Reading(AutoSource.LIGHT_BEDTIME_AVG, 3.0)
        )!!
        assertEquals(5.0, dark.value)
        val bright = AutoLogMapper.mapToHabitValue(
            h,
            AutoLogMapper.Reading(AutoSource.LIGHT_BEDTIME_AVG, 200.0)
        )!!
        assertEquals(1.0, bright.value)
    }

    @Test
    fun `gadgetbridge steps fills counter`() {
        val h = Habit(
            id = "h1",
            name = "Steps",
            groupId = "g1",
            type = HabitType.COUNTER,
            target = 8000.0,
            unit = "steps",
            autoSource = AutoSource.GADGETBRIDGE_STEPS,
            autoMode = AutoLogMode.AUTO_APPLY,
            autoMetricKey = "steps"
        )
        val m = AutoLogMapper.mapToHabitValue(
            h,
            AutoLogMapper.Reading(AutoSource.GADGETBRIDGE_STEPS, 8420.0)
        )!!
        assertEquals(8420.0, m.value)
        assertTrue(m.note.contains("auto"))
    }

    @Test
    fun `none source maps to null`() {
        val h = Habit("h1", "X", groupId = "g1")
        assertNull(
            AutoLogMapper.mapToHabitValue(
                h,
                AutoLogMapper.Reading(AutoSource.SCREEN_MINUTES, 30.0)
            )
        )
    }
}
