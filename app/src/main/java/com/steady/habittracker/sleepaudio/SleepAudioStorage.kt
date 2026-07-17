package com.steady.habittracker.sleepaudio

import android.content.Context
import com.steady.habittracker.data.SleepAudioPrefs
import com.steady.habittracker.data.SleepNightSession
import java.io.File
import java.time.LocalDate

/**
 * On-disk layout:
 * filesDir/sleep_audio/{sessionId}/seg_000.ogg …
 */
object SleepAudioStorage {
    private const val ROOT = "sleep_audio"

    fun rootDir(context: Context): File =
        File(context.filesDir, ROOT).also { it.mkdirs() }

    fun sessionDir(context: Context, sessionId: String): File =
        File(rootDir(context), sessionId).also { it.mkdirs() }

    fun segmentFile(context: Context, sessionId: String, index: Int, ext: String = "ogg"): File =
        File(sessionDir(context, sessionId), "seg_%03d.%s".format(index, ext))

    fun resolveSegment(context: Context, sessionId: String, fileName: String): File =
        File(sessionDir(context, sessionId), fileName)

    /**
     * Drop nights older than [prefs.retainDays] (by wakeDate) and delete their folders.
     * Returns pruned session list.
     */
    fun prune(context: Context, nights: List<SleepNightSession>, prefs: SleepAudioPrefs): List<SleepNightSession> {
        val keep = prefs.retainDays.coerceIn(1, 14)
        val cutoff = LocalDate.now().minusDays(keep.toLong())
        val kept = mutableListOf<SleepNightSession>()
        val keepIds = mutableSetOf<String>()
        for (n in nights.sortedByDescending { it.startedAt }) {
            val wake = try {
                LocalDate.parse(n.wakeDate)
            } catch (_: Exception) {
                null
            }
            if (wake == null || !wake.isBefore(cutoff)) {
                kept.add(n)
                keepIds.add(n.id)
            }
        }
        // Delete orphan dirs
        rootDir(context).listFiles()?.forEach { dir ->
            if (dir.isDirectory && dir.name !in keepIds) {
                dir.deleteRecursively()
            }
        }
        return kept
    }

    fun estimateSessionBytes(context: Context, sessionId: String): Long {
        val dir = sessionDir(context, sessionId)
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
