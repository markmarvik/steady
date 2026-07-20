package com.steady.habittracker.sensors

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Daily screen-on time from UsageStats (requires PACKAGE_USAGE_STATS / usage access).
 */
object ScreenTimeReader {

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Total screen-interactive milliseconds for [date] in the default zone.
     * Returns null if usage access is missing or query fails.
     */
    fun screenOnMillis(context: Context, date: LocalDate = LocalDate.now()): Long? {
        if (!hasUsageAccess(context)) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            .coerceAtMost(System.currentTimeMillis())
        return try {
            sumScreenOn(usm, start, end)
        } catch (_: Exception) {
            null
        }
    }

    /** Screen-on minutes after [afterTime] local on [date] (e.g. wind-down start). */
    fun screenOnMinutesAfter(
        context: Context,
        date: LocalDate,
        afterTime: LocalTime
    ): Long? {
        if (!hasUsageAccess(context)) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val zone = ZoneId.systemDefault()
        val start = date.atTime(afterTime).atZone(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            .coerceAtMost(System.currentTimeMillis())
        if (end <= start) return 0L
        return try {
            sumScreenOn(usm, start, end) / 60_000L
        } catch (_: Exception) {
            null
        }
    }

    fun screenOnMinutes(context: Context, date: LocalDate = LocalDate.now()): Long? =
        screenOnMillis(context, date)?.div(60_000L)

    /**
     * Top apps by foreground time for [date] (#35).
     * Returns package → minutes, sorted descending, up to [limit].
     * If [packages] is non-empty, only those packages are included (then sorted).
     */
    fun topAppsMinutes(
        context: Context,
        date: LocalDate = LocalDate.now(),
        limit: Int = 8,
        packages: List<String> = emptyList()
    ): List<Pair<String, Long>> {
        if (!hasUsageAccess(context)) return emptyList()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            .coerceAtMost(System.currentTimeMillis())
        return try {
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
                ?: return emptyList()
            val filter = packages.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            stats
                .asSequence()
                .filter { it.totalTimeInForeground > 0 }
                .filter { filter.isEmpty() || it.packageName in filter }
                .map { it.packageName to (it.totalTimeInForeground / 60_000L) }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .take(limit.coerceAtLeast(1))
                .toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun sumScreenOn(usm: UsageStatsManager, start: Long, end: Long): Long {
        val events = usm.queryEvents(start, end)
        val event = UsageEvents.Event()
        var total = 0L
        var lastResume = -1L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED,
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    lastResume = event.timeStamp.coerceAtLeast(start)
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    if (lastResume >= 0) {
                        val t = event.timeStamp.coerceAtMost(end)
                        if (t > lastResume) total += t - lastResume
                        lastResume = -1L
                    }
                }
            }
        }
        if (lastResume >= 0 && end > lastResume) {
            total += end - lastResume
        }
        return total.coerceAtLeast(0L)
    }
}
