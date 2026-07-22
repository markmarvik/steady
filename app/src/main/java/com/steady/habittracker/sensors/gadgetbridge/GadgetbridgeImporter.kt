package com.steady.habittracker.sensors.gadgetbridge

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
import com.steady.habittracker.data.AppData
import com.steady.habittracker.data.GadgetbridgePrefs
import com.steady.habittracker.data.WearableDayMetrics
import com.steady.habittracker.data.withGadgetbridgePrefs
import com.steady.habittracker.data.withMergedWearableDays
import com.steady.habittracker.sensors.ExternalMetricsStore
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Efficient Gadgetbridge SQLite export → [WearableDayMetrics] importer.
 *
 * - Skip unchanged export files (mtime + size).
 * - Re-aggregate only a lookback window so multi-gadget tables stay fast.
 * - Deduplicate by calendar date when merging into [AppData.wearableDays].
 * - Universal across device tables (`%_ACTIVITY_SAMPLE`).
 */
object GadgetbridgeImporter {
    private const val TAG = "GadgetbridgeImporter"
    private const val CACHE_NAME = "gadgetbridge_import.db"

    data class ImportResult(
        val data: AppData,
        val daysUpdated: Int = 0,
        val skippedUnchanged: Boolean = false,
        val events: List<WearableEvent> = emptyList(),
        val message: String = ""
    )

    data class WearableEvent(
        val kind: Kind,
        val date: String,
        val title: String,
        val body: String
    ) {
        enum class Kind {
            STEP_GOAL, SLEEP_SHORT, SLEEP_LONG, HR_HIGH, RESTING_HR, PERSONAL_BEST
        }
    }

    private class DayAcc {
        var steps: Long = 0
        var hrSum: Long = 0
        var hrCount: Int = 0
        var hrMin: Int = Int.MAX_VALUE
        var hrMax: Int = 0
        var sleepMin: Int = 0
        var activeMin: Int = 0
        var samples: Int = 0
        var maxTs: Long = 0
        val devices: MutableSet<Int> = mutableSetOf()
        var restHrSum: Long = 0
        var restHrCount: Int = 0
        var summarySleepMin: Int = 0
    }

    fun importIfNeeded(context: Context, data: AppData, force: Boolean = false): ImportResult {
        val prefs = data.gadgetbridgePrefs
        if (!prefs.enabled && !force) {
            return ImportResult(data, message = "Gadgetbridge sync disabled")
        }
        val location = prefs.exportLocation.trim().ifBlank {
            defaultExportCandidates().firstOrNull {
                val f = File(it)
                f.isFile || f.isDirectory
            }.orEmpty()
        }
        if (location.isBlank()) {
            val next = prefs.copy(
                lastError = "Set export location (Gadgetbridge auto-export path)",
                lastStatus = "No export path"
            )
            return ImportResult(data.withGadgetbridgePrefs(next), message = next.lastError)
        }

        val resolved = resolveExportFile(context, location)
        if (resolved == null) {
            val next = prefs.copy(
                lastError = "Export not found: $location",
                lastStatus = "Missing file",
                lastSyncAt = System.currentTimeMillis()
            )
            return ImportResult(data.withGadgetbridgePrefs(next), message = next.lastError)
        }

        val mtime = resolved.lastModified()
        val size = resolved.length()
        if (!force &&
            mtime > 0L &&
            mtime == prefs.lastFileMtime &&
            size == prefs.lastFileSize &&
            prefs.lastSyncAt > 0L
        ) {
            val next = prefs.copy(
                lastStatus = "Up to date",
                lastError = "",
                lastSyncAt = System.currentTimeMillis()
            )
            return ImportResult(
                data.withGadgetbridgePrefs(next),
                skippedUnchanged = true,
                message = "Export unchanged"
            )
        }

        val cache = File(context.cacheDir, CACHE_NAME)
        try {
            resolved.inputStream().use { input ->
                FileOutputStream(cache).use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "copy failed", e)
            val next = prefs.copy(
                lastError = "Cannot read export: ${e.message}",
                lastStatus = "Read error",
                lastSyncAt = System.currentTimeMillis()
            )
            return ImportResult(data.withGadgetbridgePrefs(next), message = next.lastError)
        }

        val lookback = prefs.effectiveLookbackDays()
        val zone = ZoneId.systemDefault()
        val minDate = LocalDate.now(zone).minusDays(lookback.toLong())
        val minTs = minDate.atStartOfDay(zone).toEpochSecond()

        val days = try {
            aggregateFromDb(
                dbPath = cache.absolutePath,
                minTs = minTs,
                importSteps = prefs.importSteps,
                importSleep = prefs.importSleep,
                importHeartRate = prefs.importHeartRate,
                zone = zone
            )
        } catch (e: Exception) {
            Log.w(TAG, "aggregate failed", e)
            val next = prefs.copy(
                lastError = "Parse failed: ${e.message}",
                lastStatus = "DB error",
                lastSyncAt = System.currentTimeMillis()
            )
            return ImportResult(data.withGadgetbridgePrefs(next), message = next.lastError)
        }

        if (days.isEmpty()) {
            val next = prefs.copy(
                lastFileMtime = mtime,
                lastFileSize = size,
                lastSyncAt = System.currentTimeMillis(),
                lastStatus = "No samples in window",
                lastError = ""
            )
            return ImportResult(data.withGadgetbridgePrefs(next), message = next.lastStatus)
        }

        val merged = data.withMergedWearableDays(days)
        val maxTs = days.maxOf { it.maxSampleTs }
        val events = if (prefs.notifyEvents) {
            detectEvents(data.wearableDays, days, prefs)
        } else {
            emptyList()
        }

        var nextPrefs = prefs.copy(
            lastFileMtime = mtime,
            lastFileSize = size,
            lastMaxSampleTs = maxOf(prefs.lastMaxSampleTs, maxTs),
            lastSyncAt = System.currentTimeMillis(),
            lastStatus = "Synced ${days.size} day(s)",
            lastError = ""
        )
        val goalDates = nextPrefs.notifiedStepGoalDates.toMutableList()
        var pb = nextPrefs.lastNotifiedPersonalBest
        for (ev in events) {
            when (ev.kind) {
                WearableEvent.Kind.STEP_GOAL -> {
                    if (ev.date !in goalDates) goalDates.add(ev.date)
                }
                WearableEvent.Kind.PERSONAL_BEST -> {
                    days.find { it.date == ev.date }?.steps?.let { pb = maxOf(pb, it) }
                }
                else -> Unit
            }
        }
        nextPrefs = nextPrefs.copy(
            notifiedStepGoalDates = goalDates.takeLast(60),
            lastNotifiedPersonalBest = pb
        )

        val today = LocalDate.now(zone).toString()
        days.find { it.date == today }?.steps?.let { steps ->
            ExternalMetricsStore.put(context, today, "steps", steps.toDouble())
            ExternalMetricsStore.put(context, today, "gadgetbridge_steps", steps.toDouble())
        }
        days.find { it.date == today }?.avgHeartRate?.let { hr ->
            ExternalMetricsStore.put(context, today, "hr_avg", hr.toDouble())
        }
        days.find { it.date == today }?.sleepMinutes?.let { sm ->
            ExternalMetricsStore.put(context, today, "sleep_minutes", sm.toDouble())
        }

        return ImportResult(
            data = merged.withGadgetbridgePrefs(nextPrefs),
            daysUpdated = days.size,
            events = events,
            message = nextPrefs.lastStatus
        )
    }

    fun defaultExportCandidates(): List<String> = listOf(
        "/storage/emulated/0/Download/Gadgetbridge",
        "/storage/emulated/0/Download/Gadgetbridge.db",
        "/storage/emulated/0/Download/gadgetbridge",
        "/sdcard/Download/Gadgetbridge",
        "/storage/emulated/0/Android/data/nodomain.freeyourgadget.gadgetbridge/files/Gadgetbridge"
    )

    data class ValidationResult(
        val ok: Boolean,
        val message: String,
        /** ACTIVITY_SAMPLE-like table names found. */
        val activityTables: List<String> = emptyList(),
        val hasDeviceTable: Boolean = false,
        val fileBytes: Long = 0L
    )

    /**
     * Resolve [location], open as SQLite, and confirm Gadgetbridge-like schema
     * (DEVICE and/or *ACTIVITY_SAMPLE* with STEPS/HEART_RATE/TIMESTAMP).
     */
    fun validateLocation(context: Context, location: String): ValidationResult {
        val loc = location.trim()
        if (loc.isBlank()) {
            return ValidationResult(false, "No file selected")
        }
        val resolved = resolveExportFile(context, loc)
            ?: return ValidationResult(false, "Cannot open file (missing permission or path)")
        if (resolved.length() < 100) {
            return ValidationResult(false, "File too small to be a Gadgetbridge database")
        }
        // SQLite header: "SQLite format 3\u0000"
        try {
            resolved.inputStream().use { input ->
                val header = ByteArray(16)
                val n = input.read(header)
                if (n < 16 || !header.decodeToString().startsWith("SQLite format 3")) {
                    return ValidationResult(
                        false,
                        "Not a SQLite database. Pick Gadgetbridge’s export (.db / Gadgetbridge file)."
                    )
                }
            }
        } catch (e: Exception) {
            return ValidationResult(false, "Cannot read file: ${e.message}")
        }
        return try {
            validateDbFile(resolved.absolutePath, resolved.length())
        } catch (e: Exception) {
            ValidationResult(false, "Invalid SQLite: ${e.message}")
        }
    }

    fun validateDbFile(dbPath: String, fileBytes: Long = File(dbPath).length()): ValidationResult {
        val db = SQLiteDatabase.openDatabase(
            dbPath,
            null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        )
        try {
            val allTables = mutableListOf<String>()
            db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    val n = c.getString(0) ?: continue
                    allTables.add(n)
                }
            }
            if (allTables.isEmpty()) {
                return ValidationResult(false, "Empty database (no tables)", fileBytes = fileBytes)
            }
            val hasDevice = allTables.any { it.equals("DEVICE", ignoreCase = true) }
            val activityTables = allTables.filter {
                val u = it.uppercase()
                u.contains("ACTIVITY_SAMPLE") || u == "ACTIVITY_SAMPLE"
            }
            val sampleLike = if (activityTables.isEmpty()) {
                allTables.filter { name ->
                    val u = name.uppercase()
                    (u.endsWith("_SAMPLE") || u.contains("ACTIVITY")) &&
                        run {
                            val cols = columnNames(db, name)
                            "TIMESTAMP" in cols && ("STEPS" in cols || "HEART_RATE" in cols)
                        }
                }
            } else {
                activityTables
            }

            if (sampleLike.isEmpty() && !hasDevice) {
                return ValidationResult(
                    false,
                    "Not a Gadgetbridge export — missing DEVICE / ACTIVITY_SAMPLE tables " +
                        "(found: ${allTables.take(6).joinToString()})",
                    fileBytes = fileBytes
                )
            }
            if (sampleLike.isEmpty() && hasDevice) {
                return ValidationResult(
                    false,
                    "Gadgetbridge DEVICE table found, but no activity sample tables with steps/HR yet. " +
                        "Sync your gadget in Gadgetbridge, then re-export.",
                    hasDeviceTable = true,
                    fileBytes = fileBytes
                )
            }
            val usable = sampleLike.filter { name ->
                val cols = columnNames(db, name)
                "TIMESTAMP" in cols && ("STEPS" in cols || "HEART_RATE" in cols)
            }
            if (usable.isEmpty()) {
                return ValidationResult(
                    false,
                    "Activity tables found but missing TIMESTAMP/STEPS/HEART_RATE columns",
                    activityTables = sampleLike,
                    hasDeviceTable = hasDevice,
                    fileBytes = fileBytes
                )
            }
            val summary = buildString {
                append("Valid Gadgetbridge DB · ")
                append(usable.size)
                append(" activity table(s)")
                if (hasDevice) append(" · DEVICE")
                append(" · ")
                append(fileBytes / 1024)
                append(" KB")
            }
            return ValidationResult(
                ok = true,
                message = summary,
                activityTables = usable,
                hasDeviceTable = hasDevice,
                fileBytes = fileBytes
            )
        } finally {
            db.close()
        }
    }

    /** Persistable read access for a content:// export URI. */
    fun takePersistableReadPermission(context: Context, location: String) {
        if (!location.startsWith("content://", ignoreCase = true)) return
        try {
            val uri = Uri.parse(location)
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            Log.w(TAG, "takePersistableUriPermission failed", e)
        }
    }

    fun displayNameForUri(context: Context, location: String): String {
        if (!location.startsWith("content://", ignoreCase = true)) {
            return File(location).name.ifBlank { location }
        }
        return try {
            val uri = Uri.parse(location)
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
            } ?: uri.lastPathSegment ?: "Gadgetbridge export"
        } catch (_: Exception) {
            "Gadgetbridge export"
        }
    }

    fun resolveExportFile(context: Context, location: String): File? {
        return try {
            when {
                location.startsWith("content://", ignoreCase = true) -> {
                    val uri = Uri.parse(location)
                    val tmp = File(context.cacheDir, "gb_uri_src.bin")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tmp).use { out -> input.copyTo(out) }
                    } ?: return null
                    if (tmp.length() > 0) tmp else null
                }
                else -> {
                    val f = File(location)
                    when {
                        f.isFile -> f
                        f.isDirectory -> {
                            f.listFiles()
                                ?.filter { it.isFile && it.length() > 0 }
                                ?.firstOrNull {
                                    val n = it.name.lowercase()
                                    n == "gadgetbridge" ||
                                        n == "gadgetbridge.db" ||
                                        n.endsWith(".db") ||
                                        n.contains("gadgetbridge")
                                }
                                ?: f.listFiles()?.filter { it.isFile }?.maxByOrNull { it.lastModified() }
                        }
                        else -> null
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "resolve failed", e)
            null
        }
    }

    fun aggregateFromDb(
        dbPath: String,
        minTs: Long,
        importSteps: Boolean,
        importSleep: Boolean,
        importHeartRate: Boolean,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<WearableDayMetrics> {
        val db = SQLiteDatabase.openDatabase(
            dbPath,
            null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        )
        try {
            val tables = activitySampleTables(db)
            if (tables.isEmpty()) return emptyList()

            val byDate = LinkedHashMap<String, DayAcc>()

            for (table in tables) {
                val cols = columnNames(db, table)
                val hasSteps = "STEPS" in cols
                val hasHr = "HEART_RATE" in cols
                val hasKind = "RAW_KIND" in cols
                val hasIntensity = "RAW_INTENSITY" in cols
                val hasDevice = "DEVICE_ID" in cols
                if (!hasSteps && !hasHr) continue

                val select = buildString {
                    append("SELECT TIMESTAMP")
                    if (hasSteps) append(", STEPS")
                    if (hasHr) append(", HEART_RATE")
                    if (hasKind) append(", RAW_KIND")
                    if (hasIntensity) append(", RAW_INTENSITY")
                    if (hasDevice) append(", DEVICE_ID")
                    append(" FROM ").append(quoteIdent(table))
                    append(" WHERE TIMESTAMP >= ?")
                }
                db.rawQuery(select, arrayOf(minTs.toString())).use { c ->
                    val iTs = c.getColumnIndex("TIMESTAMP")
                    val iSteps = c.getColumnIndex("STEPS")
                    val iHr = c.getColumnIndex("HEART_RATE")
                    val iKind = c.getColumnIndex("RAW_KIND")
                    val iInt = c.getColumnIndex("RAW_INTENSITY")
                    val iDev = c.getColumnIndex("DEVICE_ID")
                    while (c.moveToNext()) {
                        val ts = c.getLong(iTs)
                        if (ts < minTs) continue
                        val date = Instant.ofEpochSecond(ts).atZone(zone).toLocalDate().toString()
                        val acc = byDate.getOrPut(date) { DayAcc() }
                        acc.samples++
                        if (ts > acc.maxTs) acc.maxTs = ts
                        if (iDev >= 0 && !c.isNull(iDev)) acc.devices.add(c.getInt(iDev))

                        var stepsVal = 0
                        if (importSteps && iSteps >= 0 && !c.isNull(iSteps)) {
                            stepsVal = c.getInt(iSteps)
                            if (stepsVal > 0) {
                                acc.steps += stepsVal
                                acc.activeMin++
                            }
                        }

                        var hr = -1
                        if (importHeartRate && iHr >= 0 && !c.isNull(iHr)) {
                            hr = c.getInt(iHr)
                            if (hr in 30..230) {
                                acc.hrSum += hr
                                acc.hrCount++
                                if (hr < acc.hrMin) acc.hrMin = hr
                                if (hr > acc.hrMax) acc.hrMax = hr
                                val hour = Instant.ofEpochSecond(ts).atZone(zone).hour
                                if (hour in 0..5) {
                                    acc.restHrSum += hr
                                    acc.restHrCount++
                                }
                            }
                        }

                        if (importSleep) {
                            val kind = if (iKind >= 0 && !c.isNull(iKind)) c.getInt(iKind) else null
                            val intensity = if (iInt >= 0 && !c.isNull(iInt)) c.getInt(iInt) else null
                            if (isSleepSample(kind, intensity, hr, stepsVal == 0)) {
                                acc.sleepMin++
                            }
                        }
                    }
                }
            }

            if (importSleep && tableExists(db, "BASE_ACTIVITY_SUMMARY")) {
                applySummarySleep(db, byDate, minTs, zone)
            }

            val now = System.currentTimeMillis()
            return byDate.map { (date, acc) ->
                val sleep = when {
                    acc.summarySleepMin > 0 -> acc.summarySleepMin
                    acc.sleepMin > 0 -> acc.sleepMin
                    else -> 0
                }
                WearableDayMetrics(
                    date = date,
                    steps = if (importSteps && acc.steps > 0) acc.steps.toInt().coerceAtMost(200_000) else null,
                    sleepMinutes = if (importSleep && sleep > 0) sleep.coerceAtMost(24 * 60) else null,
                    avgHeartRate = if (importHeartRate && acc.hrCount > 0) {
                        (acc.hrSum.toDouble() / acc.hrCount).roundToInt()
                    } else null,
                    minHeartRate = if (importHeartRate && acc.hrMin != Int.MAX_VALUE) acc.hrMin else null,
                    maxHeartRate = if (importHeartRate && acc.hrMax > 0) acc.hrMax else null,
                    restingHeartRate = if (importHeartRate && acc.restHrCount >= 5) {
                        (acc.restHrSum.toDouble() / acc.restHrCount).roundToInt()
                    } else null,
                    activeMinutes = if (importSteps && acc.activeMin > 0) acc.activeMin else null,
                    source = "gadgetbridge",
                    deviceIds = acc.devices.sorted(),
                    sampleCount = acc.samples,
                    updatedAt = now,
                    maxSampleTs = acc.maxTs
                )
            }.sortedBy { it.date }
        } finally {
            db.close()
        }
    }

    private fun activitySampleTables(db: SQLiteDatabase): List<String> {
        val out = mutableListOf<String>()
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND " +
                "(name LIKE '%ACTIVITY_SAMPLE%' OR name = 'ACTIVITY_SAMPLE')",
            null
        ).use { c ->
            while (c.moveToNext()) {
                val n = c.getString(0) ?: continue
                if (n.startsWith("sqlite_", true) || n.startsWith("android_", true)) continue
                out.add(n)
            }
        }
        return out
    }

    private fun tableExists(db: SQLiteDatabase, name: String): Boolean {
        db.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1",
            arrayOf(name)
        ).use { return it.moveToFirst() }
    }

    private fun columnNames(db: SQLiteDatabase, table: String): Set<String> {
        val cols = mutableSetOf<String>()
        db.rawQuery("PRAGMA table_info(${quoteIdent(table)})", null).use { c ->
            val iName = c.getColumnIndex("name")
            while (c.moveToNext()) {
                if (iName >= 0) cols.add(c.getString(iName).uppercase())
            }
        }
        return cols
    }

    private fun quoteIdent(name: String): String =
        "\"" + name.replace("\"", "\"\"") + "\""

    /**
     * Universal-ish sleep heuristic across gadgets.
     */
    fun isSleepSample(
        rawKind: Int?,
        rawIntensity: Int?,
        heartRate: Int,
        zeroSteps: Boolean
    ): Boolean {
        if (rawKind != null) {
            when (rawKind) {
                in listOf(2, 4, 5, 8, 10, 11, 12) -> return true
            }
            if (rawKind in 100..130) return true
        }
        if (rawIntensity != null && rawIntensity in 1..20 && zeroSteps && heartRate in 35..95) {
            return true
        }
        return false
    }

    private fun applySummarySleep(
        db: SQLiteDatabase,
        byDate: LinkedHashMap<String, DayAcc>,
        minTs: Long,
        zone: ZoneId
    ) {
        val cols = columnNames(db, "BASE_ACTIVITY_SUMMARY")
        if ("TIMESTAMP" !in cols) return
        val hasName = "NAME" in cols
        val hasKind = "ACTIVITY_KIND" in cols
        if (!hasName && !hasKind) return
        val endCol = when {
            "END_TIMESTAMP" in cols -> "END_TIMESTAMP"
            "TIMESTAMP_TO" in cols -> "TIMESTAMP_TO"
            else -> null
        }
        val sql = buildString {
            append("SELECT TIMESTAMP")
            if (hasName) append(", NAME")
            if (hasKind) append(", ACTIVITY_KIND")
            if (endCol != null) append(", ").append(endCol)
            append(" FROM BASE_ACTIVITY_SUMMARY WHERE TIMESTAMP >= ?")
        }
        try {
            db.rawQuery(sql, arrayOf(minTs.toString())).use { c ->
                val iTs = c.getColumnIndex("TIMESTAMP")
                val iName = c.getColumnIndex("NAME")
                val iKind = c.getColumnIndex("ACTIVITY_KIND")
                val iEnd = endCol?.let { c.getColumnIndex(it) } ?: -1
                while (c.moveToNext()) {
                    val name = if (iName >= 0 && !c.isNull(iName)) c.getString(iName).orEmpty() else ""
                    val kind = if (iKind >= 0 && !c.isNull(iKind)) c.getInt(iKind) else -1
                    val isSleep = name.contains("sleep", ignoreCase = true) || kind in listOf(2, 4, 8)
                    if (!isSleep) continue
                    val start = c.getLong(iTs)
                    val end = if (iEnd >= 0 && !c.isNull(iEnd)) c.getLong(iEnd) else start + 3600
                    val minutes = ((end - start).coerceAtLeast(0) / 60).toInt().coerceIn(0, 24 * 60)
                    if (minutes <= 0) continue
                    val date = Instant.ofEpochSecond(end).atZone(zone).toLocalDate().toString()
                    val acc = byDate.getOrPut(date) { DayAcc() }
                    if (minutes > acc.summarySleepMin) acc.summarySleepMin = minutes
                }
            }
        } catch (_: Exception) {
            // Optional table shape varies
        }
    }

    fun detectEvents(
        previous: List<WearableDayMetrics>,
        incoming: List<WearableDayMetrics>,
        prefs: GadgetbridgePrefs
    ): List<WearableEvent> {
        val events = mutableListOf<WearableEvent>()
        val prevByDate = previous.associateBy { it.date }
        val historicalBest = previous.mapNotNull { it.steps }.maxOrNull()
            ?: prefs.lastNotifiedPersonalBest
        val notifiedGoals = prefs.notifiedStepGoalDates.toSet()

        for (day in incoming) {
            val steps = day.steps
            if (prefs.notifyStepGoal && steps != null && steps >= prefs.stepGoal && day.date !in notifiedGoals) {
                val prev = prevByDate[day.date]?.steps ?: 0
                if (prev < prefs.stepGoal) {
                    events += WearableEvent(
                        WearableEvent.Kind.STEP_GOAL,
                        day.date,
                        "Step goal reached",
                        "${day.date}: $steps steps (goal ${prefs.stepGoal})"
                    )
                }
            }
            if (prefs.notifyPersonalBest && steps != null && steps > 0) {
                val bar = maxOf(historicalBest, prefs.lastNotifiedPersonalBest)
                if (steps > bar) {
                    events += WearableEvent(
                        WearableEvent.Kind.PERSONAL_BEST,
                        day.date,
                        "Steps personal best",
                        "${day.date}: $steps steps (previous best $bar)"
                    )
                }
            }
            val sleepM = day.sleepMinutes
            if (sleepM != null) {
                val hours = sleepM / 60f
                if (prefs.notifySleepShort && hours < prefs.sleepMinHours) {
                    val prevM = prevByDate[day.date]?.sleepMinutes
                    if (prevM == null || prevM / 60f >= prefs.sleepMinHours) {
                        events += WearableEvent(
                            WearableEvent.Kind.SLEEP_SHORT,
                            day.date,
                            "Short sleep",
                            "${day.date}: ${"%.1f".format(hours)}h (min ${prefs.sleepMinHours}h)"
                        )
                    }
                }
                if (prefs.notifySleepLong && hours > prefs.sleepMaxHours) {
                    events += WearableEvent(
                        WearableEvent.Kind.SLEEP_LONG,
                        day.date,
                        "Long sleep",
                        "${day.date}: ${"%.1f".format(hours)}h (max ${prefs.sleepMaxHours}h)"
                    )
                }
            }
            val maxHr = day.maxHeartRate
            if (prefs.notifyHrHigh && maxHr != null && maxHr >= prefs.hrHighThreshold) {
                val prevMax = prevByDate[day.date]?.maxHeartRate ?: 0
                if (prevMax < prefs.hrHighThreshold) {
                    events += WearableEvent(
                        WearableEvent.Kind.HR_HIGH,
                        day.date,
                        "High heart rate",
                        "${day.date}: max $maxHr bpm (threshold ${prefs.hrHighThreshold})"
                    )
                }
            }
            val rest = day.restingHeartRate
            if (prefs.notifyRestingHr && rest != null) {
                when {
                    rest >= prefs.restingHrHigh -> events += WearableEvent(
                        WearableEvent.Kind.RESTING_HR,
                        day.date,
                        "Elevated resting HR",
                        "${day.date}: resting ~$rest bpm (high ≥${prefs.restingHrHigh})"
                    )
                    rest in 1 until prefs.restingHrLow -> events += WearableEvent(
                        WearableEvent.Kind.RESTING_HR,
                        day.date,
                        "Low resting HR",
                        "${day.date}: resting ~$rest bpm (low <${prefs.restingHrLow})"
                    )
                }
            }
        }
        return events.distinctBy { "${it.kind}:${it.date}:${it.title}" }
    }
}
