package com.steady.habittracker.sleepaudio

import com.steady.habittracker.data.SleepAudioEvent
import com.steady.habittracker.data.SleepEventKind
import com.steady.habittracker.data.SleepNightSession
import java.util.UUID

/**
 * Pure heuristics for loud / possible-snore classification and night stats.
 * Not a medical snore detector — energy + periodicity only.
 */
object SleepAudioAnalytics {

    data class AmpSample(val atMs: Long, val amplitude: Int)

    /**
     * Fold amplitude stream into events.
     * [threshold] MediaRecorder scale 0–32767.
     */
    fun detectEvents(
        samples: List<AmpSample>,
        threshold: Int,
        minEventMs: Int,
        maxEventMs: Int,
        segmentFile: String,
        segmentStartAt: Long
    ): List<SleepAudioEvent> {
        if (samples.isEmpty()) return emptyList()
        val events = mutableListOf<SleepAudioEvent>()
        var inEvent = false
        var eventStart = 0L
        var peak = 0
        var lastAbove = 0L

        fun closeEvent(endMs: Long) {
            val dur = (endMs - eventStart).toInt().coerceAtLeast(0)
            if (dur < minEventMs) return
            val loudness = ((peak / 32767.0) * 100).toInt().coerceIn(0, 100)
            val offset = (eventStart - segmentStartAt).toInt().coerceAtLeast(0)
            events.add(
                SleepAudioEvent(
                    id = "se_${UUID.randomUUID().toString().take(8)}",
                    kind = SleepEventKind.LOUD, // refined later
                    startAt = eventStart,
                    durationMs = dur.coerceAtMost(maxEventMs),
                    peakAmplitude = peak,
                    loudness = loudness,
                    segmentFile = segmentFile,
                    offsetInSegmentMs = offset
                )
            )
        }

        for (s in samples) {
            if (s.amplitude >= threshold) {
                if (!inEvent) {
                    inEvent = true
                    eventStart = s.atMs
                    peak = s.amplitude
                } else {
                    peak = maxOf(peak, s.amplitude)
                    if (s.atMs - eventStart > maxEventMs) {
                        closeEvent(s.atMs)
                        eventStart = s.atMs
                        peak = s.amplitude
                    }
                }
                lastAbove = s.atMs
            } else if (inEvent) {
                // allow short dips under 250ms
                if (s.atMs - lastAbove > 250) {
                    closeEvent(lastAbove)
                    inEvent = false
                    peak = 0
                }
            }
        }
        if (inEvent) closeEvent(lastAbove)

        return classifySnoreLike(events)
    }

    /**
     * Mark events as POSSIBLE_SNORE when several medium-length bursts cluster
     * with 1.5–12s gaps (typical snore cadence). Long sustained → TALK_OR_TV.
     */
    fun classifySnoreLike(events: List<SleepAudioEvent>): List<SleepAudioEvent> {
        if (events.isEmpty()) return events
        val sorted = events.sortedBy { it.startAt }
        val kinds = MutableList(sorted.size) { SleepEventKind.LOUD }

        for (i in sorted.indices) {
            val e = sorted[i]
            if (e.durationMs >= 5000) {
                kinds[i] = SleepEventKind.TALK_OR_TV
                continue
            }
            // Look at neighbors within ±45s
            val window = sorted.filter {
                kotlin.math.abs(it.startAt - e.startAt) <= 45_000 &&
                    it.durationMs in 400..4000
            }
            if (window.size >= 3) {
                // Check gaps between consecutive in window
                val gaps = window.zipWithNext { a, b -> b.startAt - a.startAt }
                val snoreGaps = gaps.count { it in 1500..12_000 }
                if (snoreGaps >= 2) kinds[i] = SleepEventKind.POSSIBLE_SNORE
            }
        }
        return sorted.mapIndexed { i, e -> e.copy(kind = kinds[i]) }
    }

    fun computeNightStats(
        session: SleepNightSession,
        events: List<SleepAudioEvent> = session.events
    ): SleepNightSession {
        val snore = events.count { it.kind == SleepEventKind.POSSIBLE_SNORE }
        val loudMs = events.sumOf { it.durationMs.toLong() }
        val loudMinutes = loudMs / 60_000f
        val durationMs = ((session.endedAt ?: System.currentTimeMillis()) - session.startedAt)
            .coerceAtLeast(1L)
        val nightMinutes = durationMs / 60_000f
        // Quiet score: start 100, subtract for loud density + snore events
        val densityPenalty = ((loudMinutes / nightMinutes.coerceAtLeast(1f)) * 80f).coerceIn(0f, 70f)
        val snorePenalty = (snore * 1.5f).coerceIn(0f, 25f)
        val quiet = (100f - densityPenalty - snorePenalty).toInt().coerceIn(0, 100)
        return session.copy(
            events = events,
            eventCount = events.size,
            snoreLikeCount = snore,
            loudMinutes = loudMinutes,
            quietScore = quiet,
            completed = session.endedAt != null
        )
    }

    /** Map quiet score 0–100 → sleep quality scale 1–5. */
    fun quietScoreToScale(quietScore: Int): Double = when {
        quietScore >= 85 -> 5.0
        quietScore >= 70 -> 4.0
        quietScore >= 50 -> 3.0
        quietScore >= 30 -> 2.0
        else -> 1.0
    }

    fun kindLabel(kind: SleepEventKind): String = when (kind) {
        SleepEventKind.LOUD -> "Loud noise"
        SleepEventKind.POSSIBLE_SNORE -> "Possible snore"
        SleepEventKind.TALK_OR_TV -> "Sustained sound"
        SleepEventKind.OTHER -> "Other"
    }
}
