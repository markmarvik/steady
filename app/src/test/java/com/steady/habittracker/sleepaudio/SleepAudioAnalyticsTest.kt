package com.steady.habittracker.sleepaudio

import com.steady.habittracker.data.SleepEventKind
import com.steady.habittracker.data.SleepNightSession
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SleepAudioAnalyticsTest {

    @Test
    fun `detectEvents creates event above threshold`() {
        val t0 = 1_000_000L
        val samples = (0..20).map { i ->
            val amp = if (i in 5..12) 8000 else 100
            SleepAudioAnalytics.AmpSample(t0 + i * 250L, amp)
        }
        val events = SleepAudioAnalytics.detectEvents(
            samples = samples,
            threshold = 4000,
            minEventMs = 500,
            maxEventMs = 12000,
            segmentFile = "seg_000.ogg",
            segmentStartAt = t0
        )
        assertTrue(events.isNotEmpty())
        assertEquals("seg_000.ogg", events.first().segmentFile)
        assertTrue(events.first().loudness > 0)
    }

    @Test
    fun `snore-like cadence classified`() {
        val t0 = 2_000_000L
        // Periodic 1s bursts every 4s
        val events = (0..5).map { i ->
            com.steady.habittracker.data.SleepAudioEvent(
                id = "e$i",
                kind = SleepEventKind.LOUD,
                startAt = t0 + i * 4000L,
                durationMs = 900,
                peakAmplitude = 9000,
                loudness = 40,
                segmentFile = "seg_000.ogg",
                offsetInSegmentMs = i * 4000
            )
        }
        val classified = SleepAudioAnalytics.classifySnoreLike(events)
        assertTrue(classified.any { it.kind == SleepEventKind.POSSIBLE_SNORE })
    }

    @Test
    fun `quiet score drops with many events`() {
        val base = SleepNightSession(
            id = "n1",
            wakeDate = "2024-06-01",
            startedAt = 0L,
            endedAt = 8 * 3600_000L,
            events = emptyList()
        )
        val quiet = SleepAudioAnalytics.computeNightStats(base)
        assertEquals(100, quiet.quietScore)

        val noisyEvents = (0..40).map { i ->
            com.steady.habittracker.data.SleepAudioEvent(
                id = "x$i",
                kind = if (i % 2 == 0) SleepEventKind.POSSIBLE_SNORE else SleepEventKind.LOUD,
                startAt = i * 60_000L,
                durationMs = 30_000,
                peakAmplitude = 10000,
                loudness = 50
            )
        }
        val noisy = SleepAudioAnalytics.computeNightStats(base.copy(events = noisyEvents))
        assertTrue(noisy.quietScore < quiet.quietScore)
        assertTrue(noisy.snoreLikeCount > 0)
    }

    @Test
    fun `quietScoreToScale maps bands`() {
        assertEquals(5.0, SleepAudioAnalytics.quietScoreToScale(90))
        assertEquals(1.0, SleepAudioAnalytics.quietScoreToScale(10))
    }
}
