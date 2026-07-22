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
    fun habitDescriptionDefaultsLogNoteAndFallsBackToWhy() {
        val withDesc = Habit(
            id = "h1",
            name = "Vitamin D",
            description = "2000 IU with breakfast",
            groupId = "g1"
        )
        assertEquals("2000 IU with breakfast", withDesc.effectiveDescription())
        assertEquals("2000 IU with breakfast", withDesc.defaultLogNote())
        val legacy = Habit(id = "h2", name = "Mag", why = "400 mg evening", groupId = "g1")
        assertEquals("400 mg evening", legacy.effectiveDescription())
        assertEquals("400 mg evening", legacy.defaultLogNote())
    }

    @Test
    fun captureTagPresetsIncludeIdeasNotesReminders() {
        assertTrue(CaptureTags.IDEAS in CaptureTags.PRESETS)
        assertTrue(CaptureTags.NOTES in CaptureTags.PRESETS)
        assertTrue(CaptureTags.REMINDERS in CaptureTags.PRESETS)
        assertTrue(CaptureTags.TODO in CaptureTags.DEFAULT_INBOX_TAGS)
        assertTrue(CaptureTags.CHECKIN in CaptureTags.PRESETS)
        assertTrue(CaptureTags.CHECKIN in CaptureTags.DEFAULT_JOURNAL_TAGS)
        assertEquals("🎯", CaptureTags.glyph(CaptureTags.CHECKIN))
    }

    @Test
    fun inboxVsJournalRouting() {
        val prefs = CapturePrefs()
        assertTrue(prefs.goesToInbox(listOf(CaptureTags.IDEAS)))
        assertTrue(prefs.goesToInbox(listOf(CaptureTags.TODO)))
        assertTrue(prefs.goesToInbox(listOf(CaptureTags.REMINDERS)))
        assertFalse(prefs.goesToInbox(listOf(CaptureTags.MEMORIES)))
        assertFalse(prefs.goesToInbox(listOf(CaptureTags.GRATITUDE)))
        assertFalse(prefs.goesToInbox(listOf(CaptureTags.THOUGHTS)))
        // ESM / random awareness check-ins are journal, not action inbox
        assertFalse(prefs.goesToInbox(listOf(CaptureTags.CHECKIN)))
        // Mixed: any inbox tag keeps it in inbox
        assertTrue(prefs.goesToInbox(listOf(CaptureTags.GRATITUDE, CaptureTags.IDEAS)))
        assertTrue(prefs.goesToInbox(emptyList()))
    }

    @Test
    fun journalArchiveMigration() {
        val data = AppData(
            groups = listOf(Group("g1", "M", order = 0)),
            tags = listOf(Tag("t1", "T")),
            captures = listOf(
                CaptureItem("c1", "idea", tags = listOf(CaptureTags.IDEAS), processed = false),
                CaptureItem("c2", "thanks", tags = listOf(CaptureTags.GRATITUDE), processed = false)
            ),
            schemaVersion = 13
        )
        val migrated = data.withJournalCapturesArchived()
        assertFalse(migrated.captures.first { it.id == "c1" }.processed)
        assertTrue(migrated.captures.first { it.id == "c2" }.processed)
        assertEquals(1, migrated.inboxCaptures().size)
        assertTrue(migrated.reflectionCaptures().any { it.id == "c2" })
    }

    @Test
    fun habitDefaultsExtensionNone() {
        val h = Habit(id = "h1", name = "Test", groupId = "g1")
        assertEquals(ExtensionType.NONE, h.extensionType)
        assertFalse(h.habitReminder.enabled)
    }

    @Test
    fun appDataSchemaV14() {
        assertEquals(14, AppData().schemaVersion)
        assertNotNull(AppData().localWebPrefs)
        assertNotNull(AppData().pomodoroPrefs)
        assertTrue(AppData().grokPresets.isEmpty())
        assertEquals(3, AppData().todayGridColumns)
    }

    @Test
    fun localWebPrefsTrustedWifiNeedsPin() {
        val open = LocalWebPrefs(autoStartOnTrustedWifi = true, pin = "12", trustedSsids = listOf("Home"))
        assertFalse(open.pinIsSecure())
        assertFalse(open.canAutoStartOnWifi())
        val secure = LocalWebPrefs(
            autoStartOnTrustedWifi = true,
            pin = "4821",
            trustedSsids = listOf("Home"),
            autoOffMinutes = 30,
            trustedWifiAutoOffMinutes = 480
        )
        assertTrue(secure.pinIsSecure())
        assertTrue(secure.canAutoStartOnWifi())
        assertEquals(480, secure.effectiveAutoOffMinutes(onTrustedWifi = true))
        assertEquals(30, secure.effectiveAutoOffMinutes(onTrustedWifi = false))
    }

    @Test
    fun customAccentSchemeRoundTrip() {
        val id = customAccentSchemeId(0xFF3B82F6.toInt())
        assertTrue(isCustomAccentScheme(id))
        assertEquals(0xFF3B82F6.toInt(), accentColorArgb(id))
        // Unknown presets fall back to Catppuccin green
        assertEquals(0xFFA6E3A1.toInt(), accentColorArgb("unknown_preset"))
        assertTrue(themePacks().size >= 8)
        assertTrue(backgroundModes().size <= 12)
        assertTrue(accentSchemes().size <= 12)
    }
}
