package com.steady.habittracker.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SleepPhoneBlockTest {

    @Test
    fun enableCreatesMorningAndEveningHabits() {
        val data = AppData(
            groups = listOf(
                Group("g_morn", "Morning", "MORNING", 0),
                Group("g_even", "Evening", "BEDTIME", 1)
            ),
            dayStartHour = 0
        )
        val out = SleepPhoneBlock.apply(
            data,
            SleepPhonePrefs(enabled = true, morningEnabled = true, eveningEnabled = true)
        )
        val phones = out.habits.filter {
            !it.archived && it.extensionType == ExtensionType.SLEEP_PHONE
        }
        assertEquals(2, phones.size)
        assertTrue(phones.any { it.extensionConfig.sleepPhoneSlot == SleepPhoneSlots.MORNING })
        assertTrue(phones.any { it.extensionConfig.sleepPhoneSlot == SleepPhoneSlots.EVENING })
        // Separate habit ids — not multi-group
        phones.forEach { assertTrue(it.additionalGroupIds.isEmpty()) }
        assertTrue(ExtensionCatalog.livesOnDayTimeline(phones.first()))
    }

    @Test
    fun disableArchivesPhoneHabits() {
        val base = SleepPhoneBlock.apply(
            AppData(
                groups = listOf(
                    Group("g1", "Morning", "MORNING", 0),
                    Group("g2", "Bed", "BEDTIME", 1)
                )
            ),
            SleepPhonePrefs(enabled = true)
        )
        val off = SleepPhoneBlock.apply(base, base.sleepPhonePrefs.copy(enabled = false))
        assertFalse(
            off.habits.any { !it.archived && it.extensionType == ExtensionType.SLEEP_PHONE }
        )
    }
}
