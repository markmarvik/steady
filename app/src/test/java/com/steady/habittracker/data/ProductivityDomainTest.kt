package com.steady.habittracker.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProductivityDomainTest {

    private fun base(): AppData = AppData(
        groups = listOf(
            Group("g_work", "Focus", "WORK", 0),
            Group("g_morn", "Morning", "MORNING", 1)
        ),
        dayStartHour = 0
    )

    @Test
    fun addMitCapsAtThreeOpen() {
        var data = base()
        data = ProductivityDomain.addMit(data, "A", "2026-07-23")
        data = ProductivityDomain.addMit(data, "B", "2026-07-23")
        data = ProductivityDomain.addMit(data, "C", "2026-07-23")
        data = ProductivityDomain.addMit(data, "D", "2026-07-23")
        assertEquals(3, ProductivityDomain.openMitsForDate(data, "2026-07-23").size)
        assertFalse(ProductivityDomain.canAddMit(data, "2026-07-23"))
    }

    @Test
    fun completeMitMarksDoneAndProcessesCapture() {
        val cap = CaptureItem("c1", "Ship PR", tags = listOf(CaptureTags.TODO), processed = false)
        var data = base().copy(captures = listOf(cap))
        data = ProductivityDomain.addMit(data, "Ship PR", "2026-07-23", captureId = "c1")
        val mitId = ProductivityDomain.openMitsForDate(data, "2026-07-23").first().id
        data = ProductivityDomain.completeMit(data, mitId, nowMs = 1000L)
        assertTrue(ProductivityDomain.openMitsForDate(data, "2026-07-23").isEmpty())
        assertEquals(1, ProductivityDomain.doneMitsForDate(data, "2026-07-23").size)
        assertTrue(data.captures.first { it.id == "c1" }.processed)
    }

    @Test
    fun carryUnfinishedToNextDayOnce() {
        var data = base()
        data = ProductivityDomain.addMit(data, "Carry me", "2026-07-22")
        data = ProductivityDomain.withCarriedMits(data, "2026-07-23")
        val open = ProductivityDomain.openMitsForDate(data, "2026-07-23")
        assertEquals(1, open.size)
        assertEquals("Carry me", open.first().title)
        assertEquals("2026-07-22", open.first().carriedFrom)
        // Idempotent
        val again = ProductivityDomain.withCarriedMits(data, "2026-07-23")
        assertEquals(data.mits.size, again.mits.size)
    }

    @Test
    fun deepWorkSessionStartFinish() {
        var data = base()
        data = ProductivityDomain.ensureDeepWorkHabit(data)
        assertTrue(data.habits.any { it.extensionType == ExtensionType.DEEP_WORK })
        data = ProductivityDomain.startDeepWorkSession(
            data,
            habitId = "h_deep_work",
            intent = "Ship hybrid",
            plannedMinutes = 90,
            nowMs = 1_000_000L
        )
        assertTrue(data.deepWorkPrefs.isSessionActive())
        assertEquals("90m left · Ship hybrid".let { /* status contains left */ true }, true)
        assertTrue(ProductivityDomain.deepWorkStatusLine(data, 1_000_000L).contains("left"))

        val (after, finish) = ProductivityDomain.finishDeepWorkSession(
            data,
            nowMs = 1_000_000L + 45 * 60_000L
        )
        assertFalse(after.deepWorkPrefs.isSessionActive())
        assertEquals(45, finish!!.minutes)
        assertEquals("Ship hybrid", finish.intent)
        assertEquals(45, after.deepWorkPrefs.lastCompletedMinutes)
    }

    @Test
    fun deepWorkLivesOnTimeline() {
        val h = Habit("h", "Deep", groupId = "g", extensionType = ExtensionType.DEEP_WORK)
        assertTrue(ExtensionCatalog.livesOnDayTimeline(h))
        assertTrue(ExtensionCatalog.TEMPLATES.any { it.type == ExtensionType.DEEP_WORK })
    }
}
