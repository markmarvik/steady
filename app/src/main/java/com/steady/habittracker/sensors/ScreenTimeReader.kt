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
 * Daily screen-on and per-app foreground time from UsageStats.
 *
 * Screen-on prefers [UsageEvents.Event.SCREEN_INTERACTIVE] /
 * [UsageEvents.Event.SCREEN_NON_INTERACTIVE] (true wall-clock screen time).
 * Totals are always clamped to the query window so we never report >24h/day.
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
     * Total screen-on milliseconds for [date] (midnight–midnight local, capped at now).
     * Null if usage access missing; 0 if no activity.
     */
    fun screenOnMillis(context: Context, date: LocalDate = LocalDate.now()): Long? {
        if (!hasUsageAccess(context)) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val (start, end) = dayBoundsMs(date)
        val window = (end - start).coerceAtLeast(0L)
        return try {
            val fromScreen = sumScreenInteractiveEvents(usm, start, end)
            if (fromScreen > 0L) return fromScreen.coerceAtMost(window)

            // Fallback: single-stream activity resume/pause (still wall-clock, not sum of apps)
            val fromActivity = sumActivityScreenEvents(usm, start, end)
            if (fromActivity > 0L) return fromActivity.coerceAtMost(window)

            // Last resort: stats — clamp hard (INTERVAL_BEST often overcounts)
            val fromStats = sumForegroundFromStatsClamped(usm, start, end, window)
            fromStats
        } catch (_: Exception) {
            null
        }
    }

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
        val window = end - start
        return try {
            val fromScreen = sumScreenInteractiveEvents(usm, start, end)
            if (fromScreen > 0L) return (fromScreen.coerceAtMost(window)) / 60_000L
            val fromActivity = sumActivityScreenEvents(usm, start, end)
            (fromActivity.coerceAtMost(window)) / 60_000L
        } catch (_: Exception) {
            null
        }
    }

    fun screenOnMinutes(context: Context, date: LocalDate = LocalDate.now()): Long? =
        screenOnMillis(context, date)?.div(60_000L)

    /**
     * Top apps by **foreground** time for [date] (not “screen on”).
     * Values are clamped per package and overall to the day window.
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
        val window = (end - start).coerceAtLeast(1L)
        val filter = packages.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        return try {
            val byPkg = foregroundByPackageEvents(usm, start, end)
            var list = byPkg
                .asSequence()
                .filter { filter.isEmpty() || it.key in filter }
                .map { it.key to (it.value.coerceAtMost(window) / 60_000L) }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .take(limit.coerceAtLeast(1))
                .toList()
            if (list.isNotEmpty()) return list

            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
                ?: usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, end)
                ?: return emptyList()
            list = stats
                .asSequence()
                .filter { it.totalTimeInForeground > 0 }
                .filter { filter.isEmpty() || it.packageName in filter }
                // Only count buckets that overlap this day
                .filter { s ->
                    val last = s.lastTimeUsed
                    last == 0L || (last in start until end) ||
                        (s.firstTimeStamp in start until end)
                }
                .map {
                    it.packageName to
                        (it.totalTimeInForeground.coerceAtMost(window) / 60_000L)
                }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .take(limit.coerceAtLeast(1))
                .toList()
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun formatMinutes(min: Long?): String {
        if (min == null) return "usage access needed"
        val capped = min.coerceAtMost(24 * 60L)
        val h = capped / 60
        val m = capped % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    private fun dayBoundsMs(date: LocalDate): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            .coerceAtMost(System.currentTimeMillis())
        return start to end
    }

    /**
     * True wall-clock screen-on via SCREEN_INTERACTIVE / SCREEN_NON_INTERACTIVE (API 29+).
     * Constants: INTERACTIVE=15, NON_INTERACTIVE=16.
     */
    private fun sumScreenInteractiveEvents(usm: UsageStatsManager, start: Long, end: Long): Long {
        if (end <= start) return 0L
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0L
        val events = usm.queryEvents(start, end)
        val event = UsageEvents.Event()
        var total = 0L
        var interactiveSince = -1L
        // If we never see NON_INTERACTIVE first, assume screen already on at [start]
        // only after first INTERACTIVE in window — safer than assuming always on.
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> {
                    if (interactiveSince < 0) {
                        interactiveSince = event.timeStamp.coerceAtLeast(start)
                    }
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    if (interactiveSince >= 0) {
                        val t = event.timeStamp.coerceAtMost(end)
                        if (t > interactiveSince) total += t - interactiveSince
                        interactiveSince = -1L
                    }
                }
            }
        }
        if (interactiveSince >= 0 && end > interactiveSince) {
            total += end - interactiveSince
        }
        return total.coerceAtLeast(0L)
    }

    /**
     * Fallback wall-clock estimate from a single activity stream
     * (not summing packages — that double-counts multi-app use).
     */
    private fun sumActivityScreenEvents(usm: UsageStatsManager, start: Long, end: Long): Long {
        if (end <= start) return 0L
        val events = usm.queryEvents(start, end)
        val event = UsageEvents.Event()
        var total = 0L
        var depth = 0
        var openSince = -1L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED,
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    if (depth == 0) {
                        openSince = event.timeStamp.coerceAtLeast(start)
                    }
                    depth++
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.MOVE_TO_BACKGROUND,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    if (depth > 0) {
                        depth--
                        if (depth == 0 && openSince >= 0) {
                            val t = event.timeStamp.coerceAtMost(end)
                            if (t > openSince) total += t - openSince
                            openSince = -1L
                        }
                    }
                }
            }
        }
        if (depth > 0 && openSince >= 0 && end > openSince) {
            total += end - openSince
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
        // Per-package depth so multi-activity apps don't break
        val depth = HashMap<String, Int>()
        val openSince = HashMap<String, Long>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED,
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    val d = (depth[pkg] ?: 0)
                    if (d == 0) openSince[pkg] = event.timeStamp.coerceAtLeast(start)
                    depth[pkg] = d + 1
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.MOVE_TO_BACKGROUND,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val d = depth[pkg] ?: 0
                    if (d <= 0) continue
                    val nd = d - 1
                    depth[pkg] = nd
                    if (nd == 0) {
                        val since = openSince.remove(pkg) ?: continue
                        val t = event.timeStamp.coerceAtMost(end)
                        if (t > since) {
                            totals[pkg] = (totals[pkg] ?: 0L) + (t - since)
                        }
                    }
                }
            }
        }
        for ((pkg, since) in openSince) {
            if (end > since) {
                totals[pkg] = (totals[pkg] ?: 0L) + (end - since)
            }
        }
        return totals
    }

    private fun sumForegroundFromStatsClamped(
        usm: UsageStatsManager,
        start: Long,
        end: Long,
        windowMs: Long
    ): Long {
        if (end <= start) return 0L
        // Prefer DAILY interval for single-day queries
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            ?: usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, end)
            ?: return 0L
        var total = 0L
        for (s in stats) {
            val t = s.totalTimeInForeground
            if (t > 0) total += t
        }
        // Sum of package FG times can exceed wall clock (overlapping apps / multi-day buckets)
        return total.coerceIn(0L, windowMs)
    }
}
