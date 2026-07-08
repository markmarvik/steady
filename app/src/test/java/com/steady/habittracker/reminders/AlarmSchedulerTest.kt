package com.steady.habittracker.reminders

import com.steady.habittracker.data.Reminder
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Calendar

class AlarmSchedulerTest {

    @Test
    fun `computeNext skips past times on same day`() {
        // Wednesday 2024-06-05 10:00 local
        val cal = Calendar.getInstance().apply {
            set(2024, Calendar.JUNE, 5, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = cal.timeInMillis
        // Reminder at 09:00 every day → should be Thursday 09:00
        val r = Reminder("r1", null, "09:00", setOf(1, 2, 3, 4, 5, 6, 7), enabled = true)
        val next = AlarmScheduler.computeNextTriggerMillis(r, now)
        val nextCal = Calendar.getInstance().apply { timeInMillis = next }
        assertTrue(next > now)
        assertTrue(nextCal.get(Calendar.HOUR_OF_DAY) == 9)
        assertTrue(nextCal.get(Calendar.MINUTE) == 0)
        // June 6 2024 is Thursday
        assertTrue(nextCal.get(Calendar.DAY_OF_MONTH) == 6)
    }

    @Test
    fun `computeNext respects weekday filter`() {
        // Wednesday 2024-06-05 08:00
        val cal = Calendar.getInstance().apply {
            set(2024, Calendar.JUNE, 5, 8, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = cal.timeInMillis
        // Only Friday (5) at 09:00
        val r = Reminder("r1", "g1", "09:00", setOf(5), enabled = true)
        val next = AlarmScheduler.computeNextTriggerMillis(r, now)
        val nextCal = Calendar.getInstance().apply { timeInMillis = next }
        // Friday June 7
        assertTrue(nextCal.get(Calendar.DAY_OF_MONTH) == 7)
        val isoDow = when (nextCal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.FRIDAY -> 5
            else -> -1
        }
        assertTrue(isoDow == 5)
    }

    @Test
    fun `computeNext same day future time`() {
        val cal = Calendar.getInstance().apply {
            set(2024, Calendar.JUNE, 5, 7, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = cal.timeInMillis
        val r = Reminder("r1", null, "08:30", setOf(1, 2, 3, 4, 5, 6, 7), enabled = true)
        val next = AlarmScheduler.computeNextTriggerMillis(r, now)
        val nextCal = Calendar.getInstance().apply { timeInMillis = next }
        assertTrue(nextCal.get(Calendar.DAY_OF_MONTH) == 5)
        assertTrue(nextCal.get(Calendar.HOUR_OF_DAY) == 8)
        assertTrue(nextCal.get(Calendar.MINUTE) == 30)
    }
}
