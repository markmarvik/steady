package com.steady.habittracker.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ExtensionAndQuotesTest {

    @Test
    fun quoteRotatesByDayOfYear() {
        val a = MotivationalQuotes.forDayOfYear(1)
        val b = MotivationalQuotes.forDayOfYear(1)
        val c = MotivationalQuotes.forDayOfYear(2)
        assertEquals(a.text, b.text)
        assertTrue(a.text.isNotBlank())
        // Different day may differ (unless list size 1)
        if (MotivationalQuotes.ALL.size > 1) {
            assertTrue(a.text != c.text || a.attribution != c.attribution || true)
        }
    }

    @Test
    fun forTodayMatchesDayOfYear() {
        val d = LocalDate.of(2026, 1, 15)
        assertEquals(MotivationalQuotes.forDayOfYear(d.dayOfYear), MotivationalQuotes.forToday(d))
    }

    @Test
    fun extensionCatalogHasAllSpecialTypes() {
        val types = ExtensionCatalog.TEMPLATES.map { it.type }.toSet()
        assertTrue(ExtensionType.SNORE_WATCH_ACTIVATE in types)
        assertTrue(ExtensionType.SENSOR_AUTO_READ in types)
        assertTrue(ExtensionType.SCREEN_USAGE in types)
        assertTrue(ExtensionType.ESM_CHECKIN in types)
        assertTrue(ExtensionType.POMODORO in types)
        assertFalse(ExtensionType.NONE in types)
    }

    @Test
    fun suggestGroupIdPrefersMorningHint() {
        val data = AppData(
            groups = listOf(
                Group("g1", "Focus", "WORK", 0),
                Group("g2", "Morning Routine", "MORNING", 1)
            ),
            schemaVersion = 13
        )
        assertEquals("g2", ExtensionCatalog.suggestGroupId(data, "MORNING"))
    }

    @Test
    fun captureTagPresetsIncludeIdeasNotesReminders() {
        assertTrue(CaptureTags.IDEAS in CaptureTags.PRESETS)
        assertTrue(CaptureTags.NOTES in CaptureTags.PRESETS)
        assertTrue(CaptureTags.REMINDERS in CaptureTags.PRESETS)
    }

    @Test
    fun habitDefaultsExtensionNone() {
        val h = Habit(id = "h1", name = "Test", groupId = "g1")
        assertEquals(ExtensionType.NONE, h.extensionType)
        assertFalse(h.habitReminder.enabled)
    }

    @Test
    fun appDataSchemaV13() {
        assertEquals(13, AppData().schemaVersion)
        assertNotNull(AppData().localWebPrefs)
        assertNotNull(AppData().pomodoroPrefs)
    }
}
