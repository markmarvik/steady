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
 *
 * Note: [UsageStatsManager.queryUsageStats] often returns empty buckets on modern Android
 * when the query window is “today only”. We prefer event-stream aggregation and fall back
 * to broader INTERVAL_BEST stats when needed.
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
     * Returns 0 when access is granted but nothing was recorded yet.
     */
    fun screenOnMillis(context: Context, date: LocalDate = LocalDate.now()): Long? {
        if (!hasUsageAccess(context)) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val (start, end) = dayBoundsMs(date)
        return try {
            val fromEvents = sumScreenOnEvents(usm, start, end)
            if (fromEvents > 0L) return fromEvents
            // Fallback: aggregate package foreground times (works when events are sparse)
            val fromStats = sumForegroundFromStats(usm, start, end)
            fromStats ?: fromEvents
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
            val fromEvents = sumScreenOnEvents(usm, start, end)
            if (fromEvents > 0L) return fromEvents / 60_000L
            val fromStats = sumForegroundFromStats(usm, start, end) ?: 0L
            fromStats / 60_000L
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
        val (start, end) = dayBoundsMs(date)
        val filter = packages.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        return try {
            // Events are more reliable than queryUsageStats for partial-day “today”
            val byPkg = foregroundByPackageEvents(usm, start, end)
            val eventList = byPkg
                .asSequence()
                .filter { filter.isEmpty() || it.key in filter }
                .map { it.key to (it.value / 60_000L) }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .take(limit.coerceAtLeast(1))
                .toList()
            if (eventList.isNotEmpty()) return eventList

            // Fallback: INTERVAL_BEST / DAILY stats over the window
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, end)
                ?: usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
                ?: return emptyList()
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

    /** Human label for UI previews. */
    fun formatMinutes(min: Long?): String {
        if (min == null) return "usage access needed"
        val h = min / 60
        val m = min % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    private fun dayBoundsMs(date: LocalDate): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            .coerceAtMost(System.currentTimeMillis())
        return start to end
    }

    private fun sumScreenOnEvents(usm: UsageStatsManager, start: Long, end: Long): Long {
        if (end <= start) return 0L
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
                UsageEvents.Event.MOVE_TO_BACKGROUND,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
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

    private fun foregroundByPackageEvents(
        usm: UsageStatsManager,
        start: Long,
        end: Long
    ): Map<String, Long> {
        if (end <= start) return emptyMap()
        val events = usm.queryEvents(start, end)
        val event = UsageEvents.Event()
        val totals = HashMap<String, Long>()
        var activePkg: String? = null
        var lastResume = -1L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED,
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    // Close previous session if any
                    if (activePkg != null && lastResume >= 0) {
                        val t = event.timeStamp.coerceAtMost(end)
                        if (t > lastResume) {
                            totals[activePkg!!] = (totals[activePkg!!] ?: 0L) + (t - lastResume)
                        }
                    }
                    activePkg = pkg
                    lastResume = event.timeStamp.coerceAtLeast(start)
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.MOVE_TO_BACKGROUND,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    if (activePkg != null && lastResume >= 0 &&
                        (activePkg == pkg || event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND)
                    ) {
                        val t = event.timeStamp.coerceAtMost(end)
                        if (t > lastResume) {
                            totals[activePkg!!] = (totals[activePkg!!] ?: 0L) + (t - lastResume)
                        }
                        if (activePkg == pkg) {
                            activePkg = null
                            lastResume = -1L
                        }
                    }
                }
            }
        }
        if (activePkg != null && lastResume >= 0 && end > lastResume) {
            totals[activePkg!!] = (totals[activePkg!!] ?: 0L) + (end - lastResume)
        }
        return totals
    }

    /** Sum totalTimeInForeground across packages for [start,end]. */
    private fun sumForegroundFromStats(usm: UsageStatsManager, start: Long, end: Long): Long? {
        if (end <= start) return 0L
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, end)
            ?: usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            ?: return null
        // queryUsageStats may return multi-day buckets — clamp with first/last time when available
        var total = 0L
        for (s in stats) {
            val t = s.totalTimeInForeground
            if (t > 0) total += t
        }
        return total.coerceAtLeast(0L)
    }
}
