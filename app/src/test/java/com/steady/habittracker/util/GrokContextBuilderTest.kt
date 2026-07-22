package com.steady.habittracker.util

import com.steady.habittracker.data.AppData
import com.steady.habittracker.data.CaptureItem
import com.steady.habittracker.data.CaptureTags
import com.steady.habittracker.data.Group
import com.steady.habittracker.data.Habit
import com.steady.habittracker.data.HabitEntry
import com.steady.habittracker.data.HabitType
import com.steady.habittracker.data.Tag
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GrokContextBuilderTest {

    private fun sampleData(): AppData {
        val g = Group(id = "g1", name = "Morning", order = 0)
        val h1 = Habit(id = "h1", name = "Sunlight", groupId = "g1", type = HabitType.CHECKBOX, tags = listOf("movement"))
        val h2 = Habit(id = "h2", name = "Journal", groupId = "g1", type = HabitType.NOTE)
        val now = System.currentTimeMillis()
        val cap = CaptureItem(
            id = "c1",
            title = "Ship Ask Grok",
            note = "from history",
            tags = listOf(CaptureTags.IDEAS),
            createdAt = now
        )
        val old = CaptureItem(
            id = "c_old",
            title = "Old idea",
            tags = listOf(CaptureTags.IDEAS),
            createdAt = now - 20L * 24 * 60 * 60 * 1000
        )
        val today = java.time.LocalDate.now().toString()
        return AppData(
            groups = listOf(g),
            habits = listOf(h1, h2),
            entries = mapOf(
                today to mapOf(
                    "h1" to HabitEntry(value = 1.0),
                    "h2" to HabitEntry(value = 1.0, note = "good day")
                )
            ),
            captures = listOf(cap, old),
            tags = listOf(Tag(id = "movement", name = "Movement", order = 0)),
            onboarded = true
        )
    }

    @Test
    fun buildIncludesOverviewAndQuestion() {
        val data = sampleData()
        val msg = GrokContextBuilder.build(
            data,
            GrokShareSelection(
                includeOverview = true,
                includeMomentum = false,
                includeTagAverages = false,
                includeHabitDetails = false,
                includeRecentLogs = false,
                userPrompt = "What should I prioritize?"
            )
        )
        assertTrue(msg.contains("## My question"))
        assertTrue(msg.contains("What should I prioritize?"))
        assertTrue(msg.contains("## Overview"))
        assertTrue(msg.contains("Streak:"))
        assertTrue(msg.contains("Steady"))
    }

    @Test
    fun multiSelectTagsAndTimeScopeFilterCaptures() {
        val data = sampleData()
        val recent = GrokContextBuilder.selectableCaptures(
            data,
            tagsAnyOf = setOf(CaptureTags.IDEAS),
            scope = CaptureTimeScope.LAST_7
        )
        assertTrue(recent.any { it.id == "c1" })
        assertFalse(recent.any { it.id == "c_old" })

        val all = GrokContextBuilder.selectableCaptures(
            data,
            tagsAnyOf = setOf(CaptureTags.IDEAS),
            scope = CaptureTimeScope.ALL
        )
        assertTrue(all.any { it.id == "c_old" })
    }

    @Test
    fun resolveCapturesByTagsWhenNoIds() {
        val data = sampleData()
        val list = GrokContextBuilder.resolveCaptures(
            data,
            GrokShareSelection(
                captureTags = setOf(CaptureTags.IDEAS),
                captureScope = CaptureTimeScope.LAST_7,
                includeRecentLogs = false
            )
        )
        assertTrue(list.any { it.id == "c1" })
        assertFalse(list.any { it.id == "c_old" })
    }

    @Test
    fun buildIncludesSelectedCapturesAndHabits() {
        val data = sampleData()
        val msg = GrokContextBuilder.build(
            data,
            GrokShareSelection(
                includeOverview = false,
                includeMomentum = false,
                includeTagAverages = false,
                includeHabitDetails = true,
                includeRecentLogs = false,
                captureIds = setOf("c1"),
                habitIds = setOf("h1"),
                userPrompt = "Help"
            )
        )
        assertTrue(msg.contains("## Notes & ideas"))
        assertTrue(msg.contains("Ship Ask Grok"))
        assertTrue(msg.contains("## Selected habits"))
        assertTrue(msg.contains("Sunlight"))
        assertFalse(msg.contains("Journal"))
    }

    @Test
    fun formatCaptureReadable() {
        val item = CaptureItem(
            id = "x",
            title = "Title",
            note = "Body",
            tags = listOf(CaptureTags.NOTES),
            createdAt = 0L
        )
        val line = GrokContextBuilder.formatCapture(item)
        assertTrue(line.contains("Title"))
        assertTrue(line.contains("Body"))
        assertTrue(line.contains(CaptureTags.NOTES))
    }

    @Test
    fun habitSquareMetricsSortedByScore() {
        val metrics = GrokContextBuilder.habitSquareMetrics(sampleData())
        assertTrue(metrics.isNotEmpty())
        assertTrue(metrics.zipWithNext().all { (a, b) -> a.score >= b.score })
    }
}
