package com.steady.habittracker.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OralHygieneBlockTest {

    private fun base(): AppData = AppData(
        groups = listOf(
            Group("g_morn", "Morning Routine", "MORNING", 0),
            Group("g_even", "Evening Routine", "BEDTIME", 1)
        ),
        tags = listOf(Tag(TagIds.HYGIENE, "Hygiene", order = 0)),
        schemaVersion = 15
    )

    @Test
    fun enableCreatesMorningAndEveningMembership() {
        val prefs = OralHygienePrefs(
            enabled = true,
            brush = true,
            floss = true,
            tongueScrape = false,
            waterFlush = false,
            mouthwash = false,
            morningAndEvening = true
        )
        val out = OralHygieneBlock.apply(base(), prefs)
        val oral = out.habits.filter { !it.archived && it.extensionType == ExtensionType.ORAL_HYGIENE }
        assertEquals(2, oral.size)
        oral.forEach { h ->
            assertEquals("g_morn", h.groupId)
            assertTrue("g_even" in h.additionalGroupIds)
            assertFalse(h.canSkip) // essential default
            assertEquals(TagIds.HYGIENE, h.tags.first())
        }
        assertTrue(oral.any { it.extensionConfig.oralStepKey == OralHygieneSteps.BRUSH })
        assertTrue(oral.any { it.extensionConfig.oralStepKey == OralHygieneSteps.FLOSS })
    }

    @Test
    fun stackOrderChainsAfterHabitId() {
        val prefs = OralHygienePrefs(
            enabled = true,
            brush = true,
            floss = true,
            tongueScrape = true,
            stackOrder = true
        )
        val out = OralHygieneBlock.apply(base(), prefs)
        val brush = out.habits.first { it.id == OralHygieneSteps.stableHabitId(OralHygieneSteps.BRUSH) }
        val floss = out.habits.first { it.id == OralHygieneSteps.stableHabitId(OralHygieneSteps.FLOSS) }
        val tongue = out.habits.first { it.id == OralHygieneSteps.stableHabitId(OralHygieneSteps.TONGUE) }
        assertEquals(null, brush.afterHabitId)
        assertEquals(brush.id, floss.afterHabitId)
        assertEquals(floss.id, tongue.afterHabitId)
    }

    @Test
    fun disableArchivesOralHabits() {
        val enabled = OralHygieneBlock.apply(
            base(),
            OralHygienePrefs(enabled = true, brush = true, floss = true)
        )
        val disabled = OralHygieneBlock.apply(
            enabled,
            enabled.oralHygienePrefs.copy(enabled = false)
        )
        assertTrue(
            disabled.habits
                .filter { it.extensionType == ExtensionType.ORAL_HYGIENE }
                .all { it.archived }
        )
        assertFalse(OralHygieneBlock.isEnabled(disabled))
    }

    @Test
    fun createsEveningGroupWhenMissing() {
        val data = AppData(
            groups = listOf(Group("g_morn", "Morning", "MORNING", 0)),
            tags = emptyList(),
            schemaVersion = 15
        )
        val out = OralHygieneBlock.apply(
            data,
            OralHygienePrefs(enabled = true, brush = true, floss = false, tongueScrape = false)
        )
        assertTrue(out.groups.any { it.timeHint == "BEDTIME" || it.id == "g_oral_even" })
        val brush = out.habits.first { it.extensionConfig.oralStepKey == OralHygieneSteps.BRUSH }
        assertTrue(brush.additionalGroupIds.isNotEmpty())
    }

    @Test
    fun catalogIncludesOralHygiene() {
        assertTrue(
            ExtensionCatalog.TEMPLATES.any { it.type == ExtensionType.ORAL_HYGIENE }
        )
    }
}
