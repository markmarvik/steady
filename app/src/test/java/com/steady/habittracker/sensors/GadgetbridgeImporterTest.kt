package com.steady.habittracker.sensors

import com.steady.habittracker.data.GadgetbridgePrefs
import com.steady.habittracker.data.WearableDayMetrics
import com.steady.habittracker.data.mergeWearableDay
import com.steady.habittracker.data.withMergedWearableDays
import com.steady.habittracker.data.AppData
import com.steady.habittracker.sensors.gadgetbridge.GadgetbridgeImporter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GadgetbridgeImporterTest {

    @Test
    fun sleepHeuristicRecognizesHuamiKinds() {
        assertTrue(GadgetbridgeImporter.isSleepSample(112, null, 60, true))
        assertTrue(GadgetbridgeImporter.isSleepSample(4, null, -1, true))
        assertFalse(GadgetbridgeImporter.isSleepSample(1, 80, 120, false))
        assertTrue(GadgetbridgeImporter.isSleepSample(null, 10, 55, true))
    }

    @Test
    fun mergeWearableDaysDedupesByDate() {
        val a = WearableDayMetrics(date = "2026-07-01", steps = 1000, maxSampleTs = 10, updatedAt = 1)
        val b = WearableDayMetrics(
            date = "2026-07-01",
            steps = 5000,
            avgHeartRate = 70,
            maxSampleTs = 20,
            updatedAt = 2
        )
        val merged = mergeWearableDay(a, b)
        assertEquals(5000, merged.steps)
        assertEquals(70, merged.avgHeartRate)
        assertEquals(20L, merged.maxSampleTs)

        val data = AppData(wearableDays = listOf(a)).withMergedWearableDays(listOf(b))
        assertEquals(1, data.wearableDays.size)
        assertEquals(5000, data.wearableDays.first().steps)
    }

    @Test
    fun detectEventsStepGoalAndPersonalBest() {
        val prefs = GadgetbridgePrefs(
            notifyEvents = true,
            notifyStepGoal = true,
            notifyPersonalBest = true,
            stepGoal = 8000,
            lastNotifiedPersonalBest = 9000
        )
        val prev = listOf(
            WearableDayMetrics(date = "2026-07-01", steps = 7000)
        )
        val incoming = listOf(
            WearableDayMetrics(date = "2026-07-01", steps = 8500),
            WearableDayMetrics(date = "2026-07-02", steps = 12000)
        )
        val events = GadgetbridgeImporter.detectEvents(prev, incoming, prefs)
        assertTrue(events.any { it.kind == GadgetbridgeImporter.WearableEvent.Kind.STEP_GOAL })
        assertTrue(events.any { it.kind == GadgetbridgeImporter.WearableEvent.Kind.PERSONAL_BEST })
    }

    @Test
    fun detectEventsShortSleep() {
        val prefs = GadgetbridgePrefs(
            notifyEvents = true,
            notifySleepShort = true,
            sleepMinHours = 6f
        )
        val events = GadgetbridgeImporter.detectEvents(
            previous = emptyList(),
            incoming = listOf(WearableDayMetrics(date = "2026-07-03", sleepMinutes = 4 * 60)),
            prefs = prefs
        )
        assertTrue(events.any { it.kind == GadgetbridgeImporter.WearableEvent.Kind.SLEEP_SHORT })
    }

    @Test
    fun extensionCatalogIncludesGadgetbridge() {
        assertTrue(
            com.steady.habittracker.data.ExtensionCatalog.TEMPLATES.any {
                it.type == com.steady.habittracker.data.ExtensionType.GADGETBRIDGE_SYNC
            }
        )
    }

    @Test
    fun prefsRememberDisplayNameAndValidationStamp() {
        val p = GadgetbridgePrefs(
            enabled = true,
            exportLocation = "content://com.android.providers.downloads.documents/document/42",
            exportDisplayName = "Gadgetbridge.db",
            schemaValidatedAt = 1_700_000_000_000L
        )
        assertTrue(p.enabled)
        assertTrue(p.exportDisplayName.endsWith(".db") || p.exportDisplayName.contains("Gadgetbridge"))
        assertTrue(p.schemaValidatedAt > 0L)
    }
}
