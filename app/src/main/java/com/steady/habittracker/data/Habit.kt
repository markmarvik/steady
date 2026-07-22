package com.steady.habittracker.data

import kotlinx.serialization.Serializable

/**
 * Data model v13.
 * - Groups = time-of-day slots on the 24h timeline (when you do it).
 * - Tags = category identity for History (what it is: Supplements, Movement…).
 * - SleepSettings anchors Morning + Bedtime routines to bed/wake times.
 * - Habit.additionalGroupIds: same habit can appear in multiple timeline groups (#24).
 * - Exercise routines + workout sessions for structured training (#20–#22).
 * - Habit.icon / Group.icon: optional emoji for lists, Today, widget (#29).
 * - ScoreState / NotificationPrefs: Steady Momentum + smart gentle reminders (v10).
 * - AutoSource / AutoLogMode / AutoSuggestion: on-device sensor & external auto-logging (v11).
 * - SleepAudio: OGG night recording, loud/snore events, multi-day retention (v12).
 * - ExtensionType / ExtensionConfig / habit reminders / sensor snapshots / local web + pomodoro (v13, #33–#38).
 * - GrokPreset + todayGridColumns (v14): save Chat with Grok command presets; Today square density.
 * - archived + canSkip, parentId, skip/loggedAt, themes, schedules (v5).
 * All @Serializable for DataStore JSON (portable).
 */

/**
 * Saved Chat with Grok configuration — tools, note tags, time scope, and the question prompt.
 * Applied when loading a preset; message body is rebuilt from live data each time.
 */
@Serializable
data class GrokPreset(
    val id: String,
    val name: String,
    val userPrompt: String = "",
    val includeOverview: Boolean = true,
    val includeMomentum: Boolean = true,
    val includeTagAverages: Boolean = true,
    val includeHabitDetails: Boolean = false,
    val includeSleep: Boolean = false,
    val includeWorkouts: Boolean = false,
    val includePathGoals: Boolean = false,
    val includeRecentLogs: Boolean = true,
    val includeScreenUsage: Boolean = false,
    /** Capture tag names (Ideas, Notes, …). */
    val captureTags: List<String> = listOf("Ideas", "Notes"),
    /** CaptureTimeScope name: TODAY, LAST_3, LAST_7, LAST_14, LAST_30, ALL. */
    val captureScope: String = "LAST_7",
    /** Optional sticky habit ids for habit-details section. */
    val habitIds: List<String> = emptyList(),
    val createdAt: Long = 0L
)

/**
 * Supported habit logging types. CHECKBOX is the simple done/not-done.
 * Others support richer entry values + notes (inspired by user's TrackAndGraph usage).
 */
@Serializable
enum class HabitType {
    CHECKBOX,      // Binary toggle (value 1.0 = done)
    COUNTER,       // e.g. reps, glasses, mg dose (value = amount)
    DURATION_MIN,  // e.g. breathing, NSDR, deep work minutes
    SCALE_1_5,     // Mood, energy, soreness (1 low - 5 high)
    NOTE           // Pure journal/reflection (value ignored, note required)
}

/**
 * On-device or external source that can propose/fill a habit log.
 * All processing stays local; each source is opt-in per habit + OS permission.
 */
@Serializable
enum class AutoSource {
    /** Manual only (default). */
    NONE,
    /** Daily screen-on minutes via UsageStats (needs usage access). */
    SCREEN_MINUTES,
    /** Screen minutes after wind-down start (phone-free evening). */
    SCREEN_AFTER_WINDDOWN,
    /** Average ambient lux during wind-down window (light sensor). */
    LIGHT_BEDTIME_AVG,
    /** Checkbox: dark enough in wind-down (avg lux under threshold). */
    LIGHT_DARK_CHECK,
    /** Approximate ambient noise during wind-down (mic samples, opt-in). */
    NOISE_EVENING_DB,
    /** Steps from phone hardware counter (optional; Gadgetbridge preferred later). */
    PHONE_STEPS,
    /**
     * Steps/metrics from Gadgetbridge or automation apps via
     * [com.steady.habittracker.sensors.ExternalMetricsStore] / broadcast.
     */
    GADGETBRIDGE_STEPS,
    /** Generic external metric key stored in ExternalMetricsStore (unit-agnostic). */
    EXTERNAL_METRIC
}

/** How sensor/external values are applied when a source is linked. */
@Serializable
enum class AutoLogMode {
    /** Show a suggestion on Today; user accepts or dismisses. */
    SUGGEST,
    /** Write the entry automatically (note marks source); user can still edit. */
    AUTO_APPLY
}

/**
 * Special habit block / extension kind (#33).
 * Standard habits stay [NONE]; special ones get custom Today/Widget UI and side-effects on log.
 */
@Serializable
enum class ExtensionType {
    NONE,
    /** Evening: start overnight snore / sleep-audio capture. */
    SNORE_WATCH_ACTIVATE,
    /** Morning: stop capture and review night. */
    SNORE_WATCH_STOP,
    /** Multi-sensor snapshot (GPS, steps, light, noise, screen) (#34). */
    SENSOR_AUTO_READ,
    /** Daily / per-app screen usage tracker (#35). */
    SCREEN_USAGE,
    /** Opens structured workout session logger. */
    WORKOUT_SESSION,
    /** ESM-style awareness check-in window (#36). */
    ESM_CHECKIN,
    /** Focus / Pomodoro timer block (#38). */
    POMODORO,
    /**
     * Poll Gadgetbridge auto-export DB (hourly / configurable) and unify
     * steps, sleep, and heart-rate into [WearableDayMetrics].
     */
    GADGETBRIDGE_SYNC,
    /**
     * Universal oral hygiene steps (brush, floss, tongue scrape, water flush, …).
     * Placed in morning + evening routines when the block is enabled.
     */
    ORAL_HYGIENE
}

/** Notification / habit reminder intensity (#30). */
@Serializable
enum class ReminderStrength {
    GENTLE,
    NORMAL,
    STRONG
}

/** Sensors a SENSOR_AUTO_READ block may capture (#34). */
@Serializable
enum class SensorKind {
    GPS,
    STEPS,
    LIGHT,
    NOISE,
    SCREEN,
    EXTERNAL
}

/**
 * Per-habit extension configuration (serializable, small).
 * Unused fields are ignored for [ExtensionType.NONE].
 */
@Serializable
data class ExtensionConfig(
    /** Sensors to sample for [ExtensionType.SENSOR_AUTO_READ]. */
    val sensors: List<SensorKind> = emptyList(),
    /** Package names for [ExtensionType.SCREEN_USAGE] per-app breakdown (empty = top apps). */
    val packages: List<String> = emptyList(),
    /** When set, this block auto-triggers after the parent habit is logged complete. */
    val chainAfterHabitId: String? = null,
    /** Optional exact HH:mm for time-based prominence (reminders still optional). */
    val triggerTime: String? = null,
    /** Auto-apply readings without confirm dialog. */
    val autoApply: Boolean = false,
    /** Include per-app breakdown for screen usage. */
    val includeAppBreakdown: Boolean = true,
    /** Soft daily screen limit (minutes) for color coding. */
    val dailyLimitMinutes: Int? = null,
    /** Pomodoro work minutes. */
    val pomodoroWorkMin: Int = 25,
    /** Pomodoro break minutes. */
    val pomodoroBreakMin: Int = 5,
    /** Optional export path override for [ExtensionType.GADGETBRIDGE_SYNC] (else prefs). */
    val gadgetbridgePathHint: String = "",
    /**
     * Oral hygiene step key for [ExtensionType.ORAL_HYGIENE]:
     * brush | floss | tongue | water | mouthwash
     */
    val oralStepKey: String = ""
)

/**
 * Universal oral hygiene block settings (Manage → Blocks).
 * Steps become habits in **morning and evening** groups when enabled.
 */
@Serializable
data class OralHygienePrefs(
    val enabled: Boolean = false,
    val brush: Boolean = true,
    val floss: Boolean = true,
    val tongueScrape: Boolean = true,
    /** Water flosser / water rinse flush. */
    val waterFlush: Boolean = false,
    val mouthwash: Boolean = false,
    /** Essential = harder to skip (canSkip false). */
    val essential: Boolean = true,
    /** Suggested brush duration shown on the habit (minutes). */
    val brushMinutes: Int = 2,
    /** Momentum points per completed step. */
    val pointValue: Int = 10,
    /** Chain steps (brush → floss → tongue → …) with afterHabitId. */
    val stackOrder: Boolean = true,
    /** Always place enabled steps in morning + evening (default true). */
    val morningAndEvening: Boolean = true,
    /** Custom label for water step (default “Water floss / rinse”). */
    val waterFlushLabel: String = "Water floss / rinse"
) {
    fun activeStepKeys(): List<String> = buildList {
        if (brush) add(OralHygieneSteps.BRUSH)
        if (floss) add(OralHygieneSteps.FLOSS)
        if (tongueScrape) add(OralHygieneSteps.TONGUE)
        if (waterFlush) add(OralHygieneSteps.WATER)
        if (mouthwash) add(OralHygieneSteps.MOUTHWASH)
    }
}

/** Stable keys for oral hygiene habits. */
object OralHygieneSteps {
    const val BRUSH = "brush"
    const val FLOSS = "floss"
    const val TONGUE = "tongue"
    const val WATER = "water"
    const val MOUTHWASH = "mouthwash"

    fun stableHabitId(stepKey: String): String = "oral_$stepKey"
}

/**
 * Unified daily wearable metrics (Gadgetbridge and future sources).
 * One row per local calendar day; importer merges without duplicates.
 */
@Serializable
data class WearableDayMetrics(
    val date: String,
    val steps: Int? = null,
    val sleepMinutes: Int? = null,
    val avgHeartRate: Int? = null,
    val minHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    /** Approx resting HR: lowest sustained overnight / early-morning average. */
    val restingHeartRate: Int? = null,
    val activeMinutes: Int? = null,
    val source: String = "gadgetbridge",
    val deviceIds: List<Int> = emptyList(),
    val sampleCount: Int = 0,
    val updatedAt: Long = 0L,
    /** Highest sample TIMESTAMP (unix seconds) contributing to this day. */
    val maxSampleTs: Long = 0L
)

/**
 * Gadgetbridge auto-export poll + special-event notification settings.
 * Stored at app level so the background worker does not need a habit id.
 */
@Serializable
data class GadgetbridgePrefs(
    val enabled: Boolean = false,
    /**
     * Absolute filesystem path or content:// URI to the Gadgetbridge export
     * SQLite file (often `Gadgetbridge` / `Gadgetbridge.db`).
     */
    val exportLocation: String = "",
    /** Friendly file name shown in UI (from document picker). */
    val exportDisplayName: String = "",
    /** Last successful schema validation timestamp (0 = never). */
    val schemaValidatedAt: Long = 0L,
    /** How often to check for a newer export (minutes). Default hourly. */
    val pollIntervalMinutes: Int = 60,
    val importSteps: Boolean = true,
    val importSleep: Boolean = true,
    val importHeartRate: Boolean = true,
    /** Full re-aggregate window on each successful file change (days). */
    val lookbackDays: Int = 14,
    /** When true (and block on), History shows steps / sleep / HR frames. */
    val showHistoryFrames: Boolean = true,
    val notifyEvents: Boolean = true,
    val stepGoal: Int = 8000,
    val sleepMinHours: Float = 6f,
    val sleepMaxHours: Float = 10f,
    val hrHighThreshold: Int = 140,
    val restingHrHigh: Int = 80,
    val restingHrLow: Int = 40,
    val notifyStepGoal: Boolean = true,
    val notifySleepShort: Boolean = true,
    val notifySleepLong: Boolean = false,
    val notifyHrHigh: Boolean = true,
    val notifyRestingHr: Boolean = true,
    val notifyPersonalBest: Boolean = true,
    val lastSyncAt: Long = 0L,
    val lastFileMtime: Long = 0L,
    val lastFileSize: Long = 0L,
    val lastMaxSampleTs: Long = 0L,
    val lastStatus: String = "",
    val lastError: String = "",
    /** Days with step-goal notifications already fired (yyyy-MM-dd). */
    val notifiedStepGoalDates: List<String> = emptyList(),
    val lastNotifiedPersonalBest: Int = 0
) {
    fun effectivePollMinutes(): Int = pollIntervalMinutes.coerceIn(15, 360)
    fun effectiveLookbackDays(): Int = lookbackDays.coerceIn(1, 90)
}

/** Per-habit reminder settings when configuring a habit (#30). */
@Serializable
data class HabitReminderPrefs(
    val enabled: Boolean = false,
    /** HH:mm local. */
    val time: String = "09:00",
    val strength: ReminderStrength = ReminderStrength.GENTLE,
    /** Nudge if still pending later in the day. */
    val remindOnMissed: Boolean = false
)

/** One multi-sensor capture linked to a habit log (#34). */
@Serializable
data class SensorSnapshot(
    val id: String,
    val habitId: String,
    val date: String,
    val loggedAt: Long = System.currentTimeMillis(),
    /** Human-readable metric map (e.g. "gps" → "37.77,-122.42 ±12m"). */
    val readings: Map<String, String> = emptyMap(),
    val note: String = ""
)

/** Local LAN web server prefs (#38). */
@Serializable
data class LocalWebPrefs(
    val enabled: Boolean = false,
    /** HTTP port (binds 0.0.0.0). HTTPS uses port+1 when [httpsEnabled]. */
    val port: Int = 8787,
    /**
     * Access PIN for the LAN UI / API.
     * Required (min 4 chars) when [autoStartOnTrustedWifi] is on.
     * Empty = open on LAN (manual mode only).
     */
    val pin: String = "",
    /**
     * Self-signed TLS on port+1 (default on). Browsers will warn once —
     * use “Advanced → proceed” or stick to http:// on the HTTP port.
     */
    val httpsEnabled: Boolean = true,
    /**
     * Auto turn-off after this many minutes when started manually
     * (0 = leave on until toggled off). Default 60.
     */
    val autoOffMinutes: Int = 60,
    /** Epoch ms when the server should auto-disable; 0 = no deadline. */
    val autoOffAtEpochMs: Long = 0L,
    /**
     * Wi‑Fi SSIDs that are trusted for auto-start (exact match, no quotes).
     * Requires location / nearby-Wi‑Fi permission to read the current SSID.
     */
    val trustedSsids: List<String> = emptyList(),
    /**
     * When on a [trustedSsids] network, auto-enable the server (needs a secure PIN).
     */
    val autoStartOnTrustedWifi: Boolean = false,
    /**
     * Auto-off minutes while on a trusted SSID (0 = stay on while connected).
     * Default 8 hours — longer than manual sessions.
     */
    val trustedWifiAutoOffMinutes: Int = 480,
    /**
     * When leaving a trusted network, stop the server if it was auto-started.
     */
    val stopWhenLeavingTrustedWifi: Boolean = true,
    /** True when the current session was started by trusted-Wi‑Fi auto-start. */
    val autoStartedByWifi: Boolean = false
) {
    fun pinIsSecure(): Boolean = pin.trim().length >= 4

    fun effectiveAutoOffMinutes(onTrustedWifi: Boolean): Int {
        return if (onTrustedWifi && (autoStartOnTrustedWifi || trustedSsids.isNotEmpty())) {
            trustedWifiAutoOffMinutes
        } else {
            autoOffMinutes
        }
    }

    fun canAutoStartOnWifi(): Boolean =
        autoStartOnTrustedWifi && pinIsSecure() && trustedSsids.any { it.isNotBlank() }
}

/** Global Pomodoro defaults (#38). */
@Serializable
data class PomodoroPrefs(
    val workMin: Int = 25,
    val breakMin: Int = 5,
    val linkedHabitId: String? = null,
    /** Running session start epoch ms; 0 = idle. */
    val sessionStartedAt: Long = 0L,
    val sessionIsBreak: Boolean = false
)

/** Preset tags for quick capture / ESM (#30, #36). */
object CaptureTags {
    const val IDEAS = "Ideas"
    const val TODO = "Todo"
    const val NOTES = "Notes"
    const val REMINDERS = "Reminders"
    const val MEMORIES = "Memories"
    const val THOUGHTS = "Thoughts"
    const val GRATITUDE = "Gratitude"
    const val DISTRACTIONS = "Distractions"
    const val ENERGY = "Energy"
    /** Random ESM / awareness check-in (#36) — journal, not inbox. */
    const val CHECKIN = "Check-in"

    val PRESETS: List<String> = listOf(
        IDEAS, TODO, REMINDERS, NOTES, MEMORIES, THOUGHTS, GRATITUDE, DISTRACTIONS, ENERGY, CHECKIN
    )

    /**
     * Action inbox — items that need a later check.
     * Journal tags (memories, thoughts, gratitude, …) skip the inbox and go to archive.
     */
    val DEFAULT_INBOX_TAGS: List<String> = listOf(IDEAS, TODO, REMINDERS)

    /** Reflection / log-only tags (never open inbox by themselves). */
    val DEFAULT_JOURNAL_TAGS: List<String> = listOf(
        NOTES, MEMORIES, THOUGHTS, GRATITUDE, DISTRACTIONS, ENERGY, CHECKIN
    )

    /** Short glyph for polished capture chips. */
    fun glyph(tag: String): String = when (tag) {
        IDEAS -> "💡"
        TODO -> "✅"
        NOTES -> "📝"
        REMINDERS -> "⏰"
        MEMORIES -> "✨"
        THOUGHTS -> "💭"
        GRATITUDE -> "🙏"
        DISTRACTIONS -> "🚫"
        ENERGY -> "⚡"
        CHECKIN -> "🎯"
        else -> "·"
    }

    fun isDefaultInboxTag(tag: String): Boolean =
        tag in DEFAULT_INBOX_TAGS
}

/**
 * Quick Capture configuration (Manage → Blocks / Capture).
 * Controls dialog tags, defaults, inbox vs journal routing, and energy scale.
 */
@Serializable
data class CapturePrefs(
    /** Tags shown in the capture dialog (order preserved). Empty → all presets. */
    val enabledTags: List<String> = CaptureTags.PRESETS,
    /** Pre-selected when the dialog opens. */
    val defaultTags: List<String> = listOf(CaptureTags.IDEAS),
    /** User-defined extra tags (merged into the chip list). */
    val customTags: List<String> = emptyList(),
    /**
     * Tags that land in the action Inbox (need follow-up).
     * Everything else is auto-archived to Journal.
     */
    val inboxTags: List<String> = CaptureTags.DEFAULT_INBOX_TAGS,
    val showNoteField: Boolean = true,
    /** Allow selecting more than one tag. */
    val multiTag: Boolean = true,
    /** Optional 1–5 energy/mood scale in the dialog. */
    val showEnergyScale: Boolean = false,
    val placeholderTitle: String = "What's on your mind?",
    val placeholderNote: String = "Optional details…",
    /** Confirm button label. */
    val saveLabel: String = "Save",
    /**
     * Soft-deleted journal/inbox items stay in trash this many days before permanent purge.
     * Default 30. Range 1–365 in UI.
     */
    val trashRetainDays: Int = 30,
    /** When true, Journal shows a Trash filter chip. */
    val showTrashInJournal: Boolean = true
) {
    fun visibleTags(): List<String> {
        val base = if (enabledTags.isEmpty()) CaptureTags.PRESETS else enabledTags
        val custom = customTags.map { it.trim() }.filter { it.isNotEmpty() }
        return (base + custom).distinct()
    }

    fun resolvedInboxTags(): Set<String> =
        (if (inboxTags.isEmpty()) CaptureTags.DEFAULT_INBOX_TAGS else inboxTags)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    /**
     * True if this capture should appear in the action Inbox.
     * Rule: at least one tag is an inbox tag (or no tags → treat as Ideas/inbox).
     */
    fun goesToInbox(tags: List<String>): Boolean {
        val inbox = resolvedInboxTags()
        val cleaned = tags.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) return true
        return cleaned.any { it in inbox }
    }
}

/** Pending (open) inbox captures — Ideas / Todo / Reminders etc. (excludes trash). */
fun AppData.inboxCaptures(): List<CaptureItem> {
    val prefs = capturePrefs
    return captures
        .filter { !it.isTrashed && !it.processed && prefs.goesToInbox(it.tags) }
        .sortedByDescending { it.createdAt }
}

/** Open Todo-tagged captures for Today Work section. */
fun AppData.openTodoCaptures(): List<CaptureItem> {
    return inboxCaptures().filter { cap ->
        cap.tags.any { it.equals(CaptureTags.TODO, ignoreCase = true) }
    }
}

/**
 * Journal / archive — reflections and completed inbox items (excludes trash).
 * Includes auto-archived non-inbox tags (memories, gratitude, …) and manually done inbox items.
 */
fun AppData.journalCaptures(): List<CaptureItem> {
    val prefs = capturePrefs
    return captures
        .filter { cap ->
            if (cap.isTrashed) return@filter false
            // Auto-archived journal entries (never were open inbox)
            (!prefs.goesToInbox(cap.tags)) ||
                // Or closed inbox items
                (cap.processed && prefs.goesToInbox(cap.tags))
        }
        .sortedByDescending { it.createdAt }
}

/** Pure journal (non-inbox tags only) — for filters. */
fun AppData.reflectionCaptures(): List<CaptureItem> {
    val prefs = capturePrefs
    return captures
        .filter { !it.isTrashed && !prefs.goesToInbox(it.tags) }
        .sortedByDescending { it.createdAt }
}

/** Soft-deleted captures still within retention window. */
fun AppData.trashedCaptures(): List<CaptureItem> =
    captures.filter { it.isTrashed }.sortedByDescending { it.deletedAt ?: 0L }

/** Move capture to trash (soft delete). */
fun AppData.withCaptureTrashed(id: String, atMs: Long = System.currentTimeMillis()): AppData =
    copy(
        captures = captures.map {
            if (it.id == id) it.copy(deletedAt = atMs) else it
        }
    )

/** Restore a capture from trash. */
fun AppData.withCaptureRestored(id: String): AppData =
    copy(
        captures = captures.map {
            if (it.id == id) it.copy(deletedAt = null) else it
        }
    )

/** Permanently remove one capture. */
fun AppData.withoutCapturePermanent(id: String): AppData =
    copy(captures = captures.filter { it.id != id })

/**
 * Drop trash older than [CapturePrefs.trashRetainDays].
 * Call on load / save / journal open so retention is enforced.
 */
fun AppData.withPurgedExpiredTrash(nowMs: Long = System.currentTimeMillis()): AppData {
    val days = capturePrefs.trashRetainDays.coerceIn(1, 365)
    val cutoff = nowMs - days * 24L * 60L * 60L * 1000L
    val kept = captures.filter { cap ->
        val del = cap.deletedAt ?: return@filter true
        del >= cutoff
    }
    return if (kept.size == captures.size) this else copy(captures = kept)
}

/** Pending or last sensor proposal for a habit on a date. */
@Serializable
data class AutoSuggestion(
    val habitId: String,
    val date: String,
    val value: Double,
    val note: String = "",
    val source: AutoSource = AutoSource.NONE,
    val observedAt: Long = 0L,
    /** When true, already applied or dismissed — hidden from banner. */
    val resolved: Boolean = false
)

/**
 * When a habit appears on Today / widget (catalog stays in Manage always).
 * Defaults to DAILY so existing items keep current behavior.
 */
@Serializable
enum class ShowPreset {
    DAILY,           // every day
    WEEKDAYS,        // Mon–Fri
    WEEKENDS,        // Sat–Sun
    CUSTOM_DAYS,     // uses [Habit.weekdays]
    EVERY_N_DAYS,    // every intervalDays from anchorDate
    SPECIFIC_DATES   // only dates in specificDates
}

/**
 * Timeline group: when on the 24h day (Morning, Focus, Bedtime, Sleep…).
 * Not a category — use [Tag] for "Supplements", "Movement", etc.
 */
@Serializable
data class Group(
    val id: String,
    val name: String,
    val timeHint: String = "ANY", // MORNING, EVENING, WORK, REVIEW, SLEEP, BEDTIME, ANY
    val order: Int = 0,
    val parentId: String? = null,   // for subgroups e.g. under a workout plan
    val archived: Boolean = false,
    /** Optional emoji / short icon for lists and Today section headers (#29). Empty = letter fallback. */
    val icon: String = ""
)

/**
 * Category tag for history and analytics. Survives moving a habit across timeline groups.
 * Example: Omega-3 lives in Morning group but keeps tag "Supplements".
 */
@Serializable
data class Tag(
    val id: String,
    val name: String,
    val color: Int? = null,
    val order: Int = 0,
    val archived: Boolean = false
)

/**
 * Sleep is the spine of the day (Blueprint-style).
 * Morning routine starts at wake; bedtime/wind-down ends at bed; Sleep fills overnight.
 */
@Serializable
data class SleepSettings(
    val bedTime: String = "23:00",      // HH:mm
    val wakeTime: String = "07:00",     // HH:mm
    /** Minutes of wind-down before bed (Bedtime group block). */
    val windDownMinutes: Int = 60,
    /** Minutes of morning routine after wake. */
    val morningMinutes: Int = 90,
    val morningGroupId: String? = null,
    val bedtimeGroupId: String? = null,
    val sleepGroupId: String? = null
)

/**
 * One logged entry for a habit on a given day.
 * For CHECKBOX: value=1.0 means completed.
 */
@Serializable
data class HabitEntry(
    val value: Double = 1.0,
    val note: String = "",
    val loggedAt: Long = 0L,
    val skipped: Boolean = false   // set for Skip button (value often 0)
)

/** Basic capture item for quick ideas/todos (#8, #9). Stored alongside for MVP. */
@Serializable
data class CaptureItem(
    val id: String,
    val title: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val tags: List<String> = emptyList(),
    /**
     * Inbox items: false until the user marks done.
     * Journal / archive tags (memories, gratitude, …) are saved with processed=true
     * so they never enter the action inbox.
     */
    val processed: Boolean = false,
    val linkedHabitId: String? = null,
    /**
     * Soft-delete timestamp. Null = active. Non-null = in trash until purge after
     * [CapturePrefs.trashRetainDays].
     */
    val deletedAt: Long? = null
) {
    val isTrashed: Boolean get() = deletedAt != null
}

/**
 * Configurable reminder (per group or global "missed review").
 * days: 1=Mon ... 7=Sun to match java.time.DayOfWeek.
 */
@Serializable
data class Reminder(
    val id: String,
    val groupId: String?, // null = daily review / global
    val time: String,     // "09:00" 24h format
    val days: Set<Int>,   // 1..7
    val enabled: Boolean = true
)

/**
 * One time block in a Schedule (used by the 24-hour circle).
 * color: optional ARGB int for custom segment color (null = use theme accent). Persisted in JSON/export.
 */
@Serializable
data class TimeBlock(
    val start: String,   // "HH:mm" 24h format
    val end: String,
    val groupId: String,
    val color: Int? = null
)

/**
 * Named schedule that can assign groups to times of day.
 * Used to drive the widget's "current group" and (future) notifications.
 * Can be active only on certain weekdays or specific dates.
 */
@Serializable
data class Schedule(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val weekdays: Set<Int> = setOf(1,2,3,4,5,6,7),  // 1=Mon ... 7=Sun
    val specificDates: List<String> = emptyList(),   // yyyy-MM-dd ; if non-empty these take precedence
    val timeBlocks: List<TimeBlock> = emptyList()
)

@Serializable
data class Habit(
    val id: String,
    val name: String,
    val why: String = "", // legacy; prefer [description]
    /**
     * Optional detail under the name (dosage, form, context).
     * Also used as the default log note when completing/logging this habit
     * (e.g. supplements: "2000 IU with breakfast").
     */
    val description: String = "",
    val groupId: String,
    val type: HabitType = HabitType.CHECKBOX,
    val target: Double? = null,
    val unit: String = "",
    val order: Int = 0,
    val canSkip: Boolean = true,   // false for hygiene/essentials (no skip allowed)
    val archived: Boolean = false,
    /** @deprecated Prefer [tags] with a Supplements tag; kept for back-compat / quick UI. */
    val isSupplement: Boolean = false,
    /** Category tag ids (what it is). Independent of [groupId] (when you do it). */
    val tags: List<String> = emptyList(),
    /** When this habit appears on Today (Manage catalog always keeps it). */
    val showPreset: ShowPreset = ShowPreset.DAILY,
    /** Used by CUSTOM_DAYS (1=Mon … 7=Sun). */
    val weekdays: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    /** Used by EVERY_N_DAYS (e.g. 2 = every other day). */
    val intervalDays: Int = 2,
    /** Anchor for EVERY_N_DAYS (yyyy-MM-dd). Null → treat as epoch / always interval from date 0. */
    val anchorDate: String? = null,
    /** Used by SPECIFIC_DATES (yyyy-MM-dd). */
    val specificDates: List<String> = emptyList(),
    /** Soft stack: previous habit in sequence (Atomic Habits style). Null = standalone / root. */
    val afterHabitId: String? = null,
    /**
     * Extra timeline groups for multi-group membership (#24).
     * Together with [groupId] this is a flat set: habit appears once per group on Today,
     * may join any number of groups, and still has one HabitEntry per day.
     * [groupId] is the first/canonical member of that set (storage shape only).
     */
    val additionalGroupIds: List<String> = emptyList(),
    /** Optional emoji / short icon shown next to the name (#29). Empty = first-letter fallback. */
    val icon: String = "",
    /** Sensor / external auto-log source (v11). */
    val autoSource: AutoSource = AutoSource.NONE,
    /** Suggest vs silent auto-apply when [autoSource] is set. */
    val autoMode: AutoLogMode = AutoLogMode.SUGGEST,
    /**
     * Threshold / target for boolean-style sources (e.g. lux max for dark check,
     * max evening screen minutes for "phone-free"). Also used as EXTERNAL_METRIC key
     * when [autoMetricKey] is blank and unit carries meaning — prefer [autoMetricKey].
     */
    val autoThreshold: Double? = null,
    /** Key for [AutoSource.EXTERNAL_METRIC] / Gadgetbridge field name (e.g. "steps"). */
    val autoMetricKey: String = "",
    /** Special block / extension type (#33). Default [ExtensionType.NONE] for normal habits. */
    val extensionType: ExtensionType = ExtensionType.NONE,
    /** Config for [extensionType] (sensors, packages, pomodoro, chaining). */
    val extensionConfig: ExtensionConfig = ExtensionConfig(),
    /** Optional per-habit reminder (#30). */
    val habitReminder: HabitReminderPrefs = HabitReminderPrefs(),
    /**
     * Steady Momentum base points when this habit is completed (importance).
     * Default matches [HabitDomain.BASE_POINTS] (10). Range typically 1–50.
     */
    val pointValue: Int = 10
) {
    /** Clamped importance weight used by scoring. */
    fun effectivePointValue(): Int = pointValue.coerceIn(1, 50)
}

// --- Exercise routines & workout sessions (#20–#22) ---

@Serializable
data class ExerciseDef(
    val id: String,
    val name: String,
    val sets: Int = 3,
    val reps: String = "8-12",
    val restSec: Int = 60,
    val notes: String = "",
    val muscleGroup: String = "",
    val order: Int = 0
)

@Serializable
data class ExerciseRoutine(
    val id: String,
    val name: String,
    val description: String = "",
    val exercises: List<ExerciseDef> = emptyList(),
    val estimatedDurationMin: Int = 45,
    val tags: List<String> = emptyList(),
    val showPreset: ShowPreset = ShowPreset.DAILY,
    val weekdays: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    val groupId: String? = null,
    val archived: Boolean = false,
    val order: Int = 0,
    /** Optional habit auto-logged on session complete (streaks / progress ring). */
    val linkedHabitId: String? = null
)

@Serializable
data class SetLog(
    val setNumber: Int,
    val actualReps: Int? = null,
    val weightKg: Double? = null,
    val rpe: Int? = null,
    val note: String = ""
)

@Serializable
data class WorkoutSession(
    val id: String,
    val routineId: String,
    val date: String,
    val startedAt: Long,
    val completedAt: Long? = null,
    val performedExercises: Map<String, List<SetLog>> = emptyMap(),
    val totalDurationMin: Int? = null,
    val overallNote: String = "",
    val completed: Boolean = false
)

// --- Dreamline Goal Stories (#25) + Path orientation (#26) ---

@Serializable
enum class DreamHorizon {
    SIX_MONTHS,
    TWELVE_MONTHS
}

@Serializable
enum class DreamCategory {
    HAVING,
    BEING,
    DOING
}

@Serializable
data class GoalStep(
    val id: String,
    val title: String,
    val done: Boolean = false,
    val order: Int = 0
)

/**
 * Continuous goal produced by the Dreamline wizard (or created manually).
 * Tagged for Path tab filtering (dreamline, 6-month/12-month, being/doing/having).
 */
@Serializable
data class GoalStory(
    val id: String,
    val title: String,
    val description: String = "",
    val category: DreamCategory = DreamCategory.DOING,
    val horizon: DreamHorizon = DreamHorizon.SIX_MONTHS,
    val tags: List<String> = listOf("dreamline"),
    val progress: Float = 0f,
    val confidence: Float = 0.5f,
    /** Immediate ≤5-minute first action from the wizard. */
    val firstStepNow: String = "",
    val steps: List<GoalStep> = emptyList(),
    val startDate: String = "",
    val endDate: String = "",
    val archived: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    /** Mindset notes / check-in reflections attached on Path tab. */
    val notes: List<String> = emptyList()
)

/** Daily/on-demand “Am I on path?” alignment check (#26). */
@Serializable
data class PathAlignmentCheck(
    val id: String,
    val date: String,
    val visionAlignment: Int = 3,
    val energyTowardDreams: Int = 3,
    val identityCongruence: Int = 3,
    val note: String = "",
    val loggedAt: Long = 0L
)

/**
 * Compact per-day Momentum score (last ~60 days kept for History charts).
 * Points for a day are always re-derivable from [AppData.entries] via [HabitDomain.computeDayPoints].
 */
@Serializable
data class DayScore(
    val date: String,
    val points: Int,
    val completion: Float = 0f
)

/**
 * Steady Momentum lifetime ledger. Today's live points are computed from entries;
 * [history] holds finalized past days; [lifetimePoints] is the sum of all finalized days
 * (including those aged out of the rolling window).
 */
@Serializable
data class ScoreState(
    val lifetimePoints: Int = 0,
    val history: List<DayScore> = emptyList(),
    /** Last date whose score was finalized into history (yyyy-MM-dd). Empty = never. */
    val lastFinalizedDate: String = ""
)

/**
 * Smart-but-gentle notification preferences (local only).
 * [firesDate]/[firesCount] enforce the daily rate limit.
 */
@Serializable
data class NotificationPrefs(
    val quietStart: String = "22:30",
    val quietEnd: String = "07:00",
    val maxPerDay: Int = 4,
    val adaptiveTiming: Boolean = true,
    val streakRiskNudge: Boolean = true,
    val celebrateFullClear: Boolean = true,
    /** Date (yyyy-MM-dd) the fire counter applies to. */
    val firesDate: String = "",
    val firesCount: Int = 0,
    /** Daily motivational consistency quotes (#32). */
    val motivationalQuotesEnabled: Boolean = false,
    /** HH:mm for the daily quote notification. */
    val motivationalQuotesTime: String = "08:00",
    /** ESM-style random awareness check-ins (#36). */
    val randomCheckInsEnabled: Boolean = false,
    /** low | medium | high — legacy spacing preset (used when min/max not custom). */
    val randomCheckInFrequency: String = "medium",
    /**
     * Minimum minutes between awareness check-ins.
     * Default 30 — work blocks fire about twice an hour when group-aware.
     */
    val checkInMinIntervalMin: Int = 30,
    /** Maximum minutes between check-ins (caps random spacing). */
    val checkInMaxIntervalMin: Int = 120,
    /**
     * random = uniform spacing in [min,max];
     * group = denser during WORK, a few in MORNING/BEDTIME windows.
     */
    val checkInScheduleMode: String = "group",
    /**
     * Quality ESM prompts (research-style). One is picked per fire.
     * Empty list → [EsmDefaults.QUESTIONS].
     */
    val checkInQuestions: List<String> = emptyList(),
    /** Remind about still-pending habits later in the day (#30). */
    val missedHabitReminders: Boolean = false,
    /** Prefer explicit dismiss/snooze over auto-cancel (#30). */
    val requireExplicitDismiss: Boolean = false,
    /** Default reminder strength for group / habit nudges (#30). */
    val reminderStrength: ReminderStrength = ReminderStrength.GENTLE,
    /** Last random check-in fire epoch ms (spacing). */
    val lastRandomCheckInAt: Long = 0L
)

/** Default experience-sampling style prompts for awareness check-ins. */
object EsmDefaults {
    val QUESTIONS: List<String> = listOf(
        "What are you doing right now?",
        "How energized do you feel (1–5)?",
        "How calm or stressed do you feel right now?",
        "Are you present, or is your mind elsewhere?",
        "Who are you with (or are you alone)?",
        "How aligned is this with what matters to you today?",
        "What emotion is strongest in this moment?",
        "Would you rather be doing something else?",
        "How is your body feeling (tension, ease, energy)?",
        "What will you do in the next 15 minutes?"
    )

    fun questionsFor(prefs: NotificationPrefs): List<String> {
        val custom = prefs.checkInQuestions.map { it.trim() }.filter { it.isNotEmpty() }
        return custom.ifEmpty { QUESTIONS }
    }
}

/** Classification for overnight loud events (heuristic, not medical diagnosis). */
@Serializable
enum class SleepEventKind {
    LOUD,
    /** Periodic loud bursts suggestive of snoring. */
    POSSIBLE_SNORE,
    TALK_OR_TV,
    OTHER
}

/** One loud episode within a night session (offset into a segment file for playback). */
@Serializable
data class SleepAudioEvent(
    val id: String,
    val kind: SleepEventKind = SleepEventKind.LOUD,
    /** Epoch millis when the event started. */
    val startAt: Long,
    val durationMs: Int,
    /** Peak amplitude 0–32767 from MediaRecorder. */
    val peakAmplitude: Int = 0,
    /** Relative 0–100 loudness score. */
    val loudness: Int = 0,
    /** Segment file name under the night folder (not absolute path). */
    val segmentFile: String = "",
    /** Seek offset within [segmentFile] for playback (ms). */
    val offsetInSegmentMs: Int = 0
)

/** One overnight OGG recording session (metadata in DataStore; audio on disk). */
@Serializable
data class SleepNightSession(
    val id: String,
    /** Calendar date of the morning this night ends (wake day), yyyy-MM-dd. */
    val wakeDate: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val events: List<SleepAudioEvent> = emptyList(),
    val segmentFiles: List<String> = emptyList(),
    val eventCount: Int = 0,
    val snoreLikeCount: Int = 0,
    val loudMinutes: Float = 0f,
    /** 0–100 rough quietness score (higher = quieter night). */
    val quietScore: Int = 0,
    val codec: String = "ogg/opus",
    val note: String = "",
    val completed: Boolean = false
)

/** Preferences for snoring / sleep-audio capture. */
@Serializable
data class SleepAudioPrefs(
    /** Master enable for scheduled night recording. */
    val enabled: Boolean = false,
    /** Keep audio + sessions for this many wake-days. */
    val retainDays: Int = 3,
    /** Segment length minutes (OGG chunks). */
    val segmentMinutes: Int = 15,
    /** Amplitude threshold 0–32767 (default ~moderate room noise). */
    val loudThreshold: Int = 4000,
    /** Min continuous loud ms to count as an event. */
    val minEventMs: Int = 600,
    /** Max single event ms before splitting / classifying as sustained. */
    val maxEventMs: Int = 12000,
    /** Auto-start at bedTime from [SleepSettings]. */
    val scheduleWithSleep: Boolean = true,
    /**
     * Only run overnight capture while the device is charging / plugged in.
     * Start is skipped off-charger; active sessions stop if unplugged.
     */
    val requireCharging: Boolean = true,
    /** Optional habit id to auto-fill SCALE_1_5 sleep quality from quietScore. */
    val linkedHabitId: String? = null
)

@Serializable
data class AppData(
    val groups: List<Group> = emptyList(),
    val habits: List<Habit> = emptyList(),
    // date (yyyy-MM-dd) -> habitId -> entry
    val entries: Map<String, Map<String, HabitEntry>> = emptyMap(),
    val reminders: List<Reminder> = emptyList(),
    val schemaVersion: Int = 15,
    val onboarded: Boolean = false,
    val colorScheme: String = "default",   // accent id from accentSchemes() (green, rose, blush, …)
    val backgroundMode: String = "dark",   // id from backgroundModes() (dark, amoled, light, forest, …)
    val schedules: List<Schedule> = emptyList(),
    val activeScheduleId: String? = null,
    val captures: List<CaptureItem> = emptyList(),  // #8 quick capture inbox support
    /** Quick Capture dialog configuration (Manage). */
    val capturePrefs: CapturePrefs = CapturePrefs(),
    /** Master switch for all habit reminders (Settings). When false, no alarms are scheduled. */
    val remindersMasterEnabled: Boolean = true,
    /** Category tags for History (Supplements, Movement, …). */
    val tags: List<Tag> = emptyList(),
    /** Bed/wake spine that anchors Morning + Bedtime groups on the 24h timeline. */
    val sleep: SleepSettings = SleepSettings(),
    /** Structured exercise routines catalog (#21). */
    val routines: List<ExerciseRoutine> = emptyList(),
    /** Completed / in-progress workout sessions (newest-friendly flat list). */
    val workoutSessions: List<WorkoutSession> = emptyList(),
    /** Dreamline-derived continuous goals for Path tab (#25, #26). */
    val goals: List<GoalStory> = emptyList(),
    /** Alignment check-ins for Path tab. */
    val pathChecks: List<PathAlignmentCheck> = emptyList(),
    /** Steady Momentum scoring ledger (v10). */
    val score: ScoreState = ScoreState(),
    /** Smart reminder prefs + daily fire counter (v10). */
    val notificationPrefs: NotificationPrefs = NotificationPrefs(),
    /** Pending sensor / external auto-log suggestions (v11). */
    val autoSuggestions: List<AutoSuggestion> = emptyList(),
    /**
     * Master switch for background sensor sampling (light/noise workers).
     * Per-habit sources still required.
     */
    val autoLogMasterEnabled: Boolean = true,
    /** Overnight snore / loud-noise audio capture prefs (v12). */
    val sleepAudioPrefs: SleepAudioPrefs = SleepAudioPrefs(),
    /** Recent sleep-audio nights (audio files live under app filesDir). */
    val sleepNights: List<SleepNightSession> = emptyList(),
    /** Multi-sensor snapshots from SENSOR_AUTO_READ blocks (#34). */
    val sensorSnapshots: List<SensorSnapshot> = emptyList(),
    /** Local LAN web UI (#38). */
    val localWebPrefs: LocalWebPrefs = LocalWebPrefs(),
    /** Pomodoro defaults / active session (#38). */
    val pomodoroPrefs: PomodoroPrefs = PomodoroPrefs(),
    /** Saved Chat with Grok command presets (v14). */
    val grokPresets: List<GrokPreset> = emptyList(),
    /** Last applied Grok preset id (for quick reload). */
    val lastGrokPresetId: String? = null,
    /**
     * Today habit square columns (2–4). More columns = denser grid.
     * Matches Manage-style squares with user density control.
     */
    val todayGridColumns: Int = 3,
    /**
     * Hour (0–23) when the planner day rolls over.
     * Default 4 = “Steady day” runs from 04:00 → next 04:00 (not midnight).
     */
    val dayStartHour: Int = 4,
    /** Gadgetbridge export poll + event thresholds (v15). */
    val gadgetbridgePrefs: GadgetbridgePrefs = GadgetbridgePrefs(),
    /**
     * Unified daily wearable metrics (steps / sleep / HR).
     * Newest-friendly; cap applied on merge.
     */
    val wearableDays: List<WearableDayMetrics> = emptyList(),
    /** Oral hygiene block config (v15+). */
    val oralHygienePrefs: OralHygienePrefs = OralHygienePrefs()
)

/**
 * Runtime theme colors resolved from settings (not persisted).
 * Background from [backgroundModes]; accent from [accentSchemes].
 */
data class ThemeColors(
    val background: Int,   // ARGB
    val surface: Int,
    val accent: Int,
    val widgetRowBg: Int
)

/**
 * Immutable update helpers for AppData.
 * Addresses mutable map mutations (see issues #16, #14).
 * All return new AppData instances; never mutate in place.
 * These keep business logic clean and easy to test / reason about.
 */
fun AppData.withUpdatedEntry(date: String, habitId: String, entry: HabitEntry): AppData {
    val dayMap = (entries[date] ?: emptyMap()).toMutableMap() // local temp only for building new
    dayMap[habitId] = entry
    val newEntries = entries.toMutableMap()
    newEntries[date] = dayMap
    return copy(entries = newEntries)
}

fun AppData.withRemovedEntry(date: String, habitId: String): AppData {
    val dayMap = entries[date]?.toMutableMap() ?: return this
    dayMap.remove(habitId)
    val newEntries = entries.toMutableMap()
    if (dayMap.isEmpty()) {
        newEntries.remove(date)
    } else {
        newEntries[date] = dayMap
    }
    return copy(entries = newEntries)
}

fun AppData.withArchivedHabit(habitId: String): AppData {
    val newHabits = habits.map { if (it.id == habitId) it.copy(archived = true) else it }
    return copy(habits = newHabits)
}

fun AppData.withUnarchivedHabit(habitId: String): AppData {
    val newHabits = habits.map { if (it.id == habitId) it.copy(archived = false) else it }
    return copy(habits = newHabits)
}

/**
 * Hard-delete a habit and scrub references. Prefer only for [Habit.archived] items
 * so active catalog mistakes still use archive first.
 * Removes: habit row, daily entries, auto-suggestions, stack/chain links, routine links.
 */
fun AppData.withPermanentlyDeletedHabit(habitId: String): AppData {
    if (habits.none { it.id == habitId }) return this
    val newHabits = habits
        .filter { it.id != habitId }
        .map { h ->
            var next = h
            if (next.afterHabitId == habitId) next = next.copy(afterHabitId = null)
            if (next.extensionConfig.chainAfterHabitId == habitId) {
                next = next.copy(
                    extensionConfig = next.extensionConfig.copy(chainAfterHabitId = null)
                )
            }
            next
        }
    val newEntries = entries.mapValues { (_, day) ->
        day - habitId
    }.filterValues { it.isNotEmpty() }
    val newSuggestions = autoSuggestions.filter { it.habitId != habitId }
    val newRoutines = routines.map { r ->
        if (r.linkedHabitId == habitId) r.copy(linkedHabitId = null) else r
    }
    return copy(
        habits = newHabits,
        entries = newEntries,
        autoSuggestions = newSuggestions,
        routines = newRoutines
    )
}

/** Permanently remove every archived habit (and their history). Active habits unchanged. */
fun AppData.withPermanentlyDeletedArchivedHabits(): AppData {
    val ids = habits.filter { it.archived }.map { it.id }
    return ids.fold(this) { acc, id -> acc.withPermanentlyDeletedHabit(id) }
}

fun AppData.withArchivedGroup(groupId: String): AppData {
    val newGroups = groups.map { if (it.id == groupId) it.copy(archived = true) else it }
    val newHabits = habits.map { if (it.groupId == groupId) it.copy(archived = true) else it }
    return copy(groups = newGroups, habits = newHabits)
}

fun AppData.withUnarchivedGroup(groupId: String): AppData {
    val newGroups = groups.map { if (it.id == groupId) it.copy(archived = false) else it }
    return copy(groups = newGroups)
}

fun AppData.withHabit(updated: Habit): AppData {
    val newHabits = habits.map { if (it.id == updated.id) updated else it }
    return copy(habits = newHabits)
}

fun AppData.withAddedHabit(newHabit: Habit): AppData {
    return copy(habits = habits + newHabit)
}

fun AppData.withGroup(updated: Group): AppData {
    val newGroups = groups.map { if (it.id == updated.id) updated else it }
    return copy(groups = newGroups)
}

fun AppData.withAddedGroup(newGroup: Group): AppData {
    return copy(groups = groups + newGroup)
}

fun AppData.withReminder(updated: Reminder): AppData {
    val others = reminders.filter { it.id != updated.id }
    return copy(reminders = others + updated)
}

fun AppData.withoutReminder(id: String): AppData {
    return copy(reminders = reminders.filter { it.id != id })
}

fun AppData.withToggledReminder(id: String): AppData {
    val updated = reminders.map { if (it.id == id) it.copy(enabled = !it.enabled) else it }
    return copy(reminders = updated)
}

fun AppData.withColorScheme(scheme: String): AppData = copy(colorScheme = scheme)
fun AppData.withBackgroundMode(mode: String): AppData = copy(backgroundMode = mode)
fun AppData.withOnboarded(): AppData = copy(onboarded = true)
fun AppData.withRemindersMasterEnabled(enabled: Boolean): AppData = copy(remindersMasterEnabled = enabled)
fun AppData.withSleep(sleep: SleepSettings): AppData = copy(sleep = sleep)
fun AppData.withAddedTag(tag: Tag): AppData = copy(tags = tags + tag)
fun AppData.withTag(updated: Tag): AppData =
    copy(tags = tags.map { if (it.id == updated.id) updated else it })
fun AppData.withArchivedTag(tagId: String): AppData =
    copy(
        tags = tags.map { if (it.id == tagId) it.copy(archived = true) else it },
        habits = habits.map { h ->
            if (tagId in h.tags) h.copy(tags = h.tags.filter { it != tagId }) else h
        }
    )

// Schedule helpers (mirrored for convenience; repo delegates still work)
fun AppData.withAddedSchedule(schedule: Schedule): AppData = copy(schedules = schedules + schedule)
fun AppData.withUpdatedSchedule(schedule: Schedule): AppData =
    copy(schedules = schedules.map { if (it.id == schedule.id) schedule else it })
fun AppData.withoutSchedule(scheduleId: String): AppData {
    val newSchedules = schedules.filter { it.id != scheduleId }
    val newActive = if (activeScheduleId == scheduleId) null else activeScheduleId
    return copy(schedules = newSchedules, activeScheduleId = newActive)
}
fun AppData.withActiveSchedule(scheduleId: String?): AppData = copy(activeScheduleId = scheduleId)

// Capture helpers (#8, #9, #11)
fun AppData.withAddedCapture(capture: CaptureItem): AppData = copy(captures = captures + capture)
fun AppData.withUpdatedCapture(updated: CaptureItem): AppData =
    copy(captures = captures.map { if (it.id == updated.id) updated else it })
/** @deprecated Prefer [withCaptureTrashed] for user deletes; this permanently removes. */
fun AppData.withoutCapture(id: String): AppData = withoutCapturePermanent(id)
fun AppData.withCapturePrefs(prefs: CapturePrefs): AppData = copy(capturePrefs = prefs)
fun AppData.withGadgetbridgePrefs(prefs: GadgetbridgePrefs): AppData = copy(gadgetbridgePrefs = prefs)
fun AppData.withOralHygienePrefs(prefs: OralHygienePrefs): AppData = copy(oralHygienePrefs = prefs)
fun AppData.withDayStartHour(hour: Int): AppData = copy(dayStartHour = hour.coerceIn(0, 23))

/**
 * Merge wearable day rows by date (no duplicates). Prefer richer / newer rows.
 * Caps list size for backup size.
 */
fun AppData.withMergedWearableDays(
    incoming: List<WearableDayMetrics>,
    maxDays: Int = 400
): AppData {
    if (incoming.isEmpty()) return this
    val byDate = wearableDays.associateBy { it.date }.toMutableMap()
    for (row in incoming) {
        val prev = byDate[row.date]
        byDate[row.date] = if (prev == null) row else mergeWearableDay(prev, row)
    }
    val merged = byDate.values.sortedByDescending { it.date }.take(maxDays)
    return copy(wearableDays = merged)
}

fun mergeWearableDay(a: WearableDayMetrics, b: WearableDayMetrics): WearableDayMetrics {
    // Prefer the row with newer maxSampleTs / updatedAt; fill nulls from the other.
    val preferB = b.maxSampleTs > a.maxSampleTs ||
        (b.maxSampleTs == a.maxSampleTs && b.updatedAt >= a.updatedAt)
    val primary = if (preferB) b else a
    val secondary = if (preferB) a else b
    return primary.copy(
        steps = primary.steps ?: secondary.steps,
        sleepMinutes = primary.sleepMinutes ?: secondary.sleepMinutes,
        avgHeartRate = primary.avgHeartRate ?: secondary.avgHeartRate,
        minHeartRate = when {
            primary.minHeartRate != null && secondary.minHeartRate != null ->
                minOf(primary.minHeartRate, secondary.minHeartRate)
            else -> primary.minHeartRate ?: secondary.minHeartRate
        },
        maxHeartRate = when {
            primary.maxHeartRate != null && secondary.maxHeartRate != null ->
                maxOf(primary.maxHeartRate, secondary.maxHeartRate)
            else -> primary.maxHeartRate ?: secondary.maxHeartRate
        },
        restingHeartRate = primary.restingHeartRate ?: secondary.restingHeartRate,
        activeMinutes = primary.activeMinutes ?: secondary.activeMinutes,
        deviceIds = (primary.deviceIds + secondary.deviceIds).distinct().sorted(),
        sampleCount = maxOf(primary.sampleCount, secondary.sampleCount),
        maxSampleTs = maxOf(primary.maxSampleTs, secondary.maxSampleTs),
        updatedAt = maxOf(primary.updatedAt, secondary.updatedAt)
    )
}

fun AppData.wearableFor(date: String): WearableDayMetrics? =
    wearableDays.firstOrNull { it.date == date }

fun AppData.hasGadgetbridgeBlock(): Boolean =
    gadgetbridgePrefs.enabled ||
        habits.any { !it.archived && it.extensionType == ExtensionType.GADGETBRIDGE_SYNC }

fun AppData.withGrokPresets(presets: List<GrokPreset>, lastId: String? = lastGrokPresetId): AppData =
    copy(grokPresets = presets, lastGrokPresetId = lastId)

fun AppData.withUpsertedGrokPreset(preset: GrokPreset): AppData {
    val others = grokPresets.filterNot { it.id == preset.id }
    return copy(
        grokPresets = (listOf(preset) + others).take(40),
        lastGrokPresetId = preset.id
    )
}

fun AppData.withoutGrokPreset(id: String): AppData =
    copy(
        grokPresets = grokPresets.filterNot { it.id == id },
        lastGrokPresetId = if (lastGrokPresetId == id) null else lastGrokPresetId
    )

fun AppData.withTodayGridColumns(columns: Int): AppData =
    copy(todayGridColumns = columns.coerceIn(2, 4))

/**
 * Auto-archive open captures that are not inbox-worthy (memories, gratitude, …)
 * so they leave the action Inbox after a prefs/schema update.
 */
fun AppData.withJournalCapturesArchived(): AppData {
    val prefs = capturePrefs
    var changed = false
    val next = captures.map { cap ->
        if (!cap.processed && cap.tags.isNotEmpty() && !prefs.goesToInbox(cap.tags)) {
            changed = true
            cap.copy(processed = true)
        } else {
            cap
        }
    }
    return if (changed) copy(captures = next) else this
}

// Exercise routine helpers (#21)
fun AppData.withAddedRoutine(routine: ExerciseRoutine): AppData = copy(routines = routines + routine)
fun AppData.withUpdatedRoutine(routine: ExerciseRoutine): AppData =
    copy(routines = routines.map { if (it.id == routine.id) routine else it })
fun AppData.withArchivedRoutine(routineId: String): AppData =
    copy(routines = routines.map { if (it.id == routineId) it.copy(archived = true) else it })
fun AppData.withUnarchivedRoutine(routineId: String): AppData =
    copy(routines = routines.map { if (it.id == routineId) it.copy(archived = false) else it })
fun AppData.withoutRoutine(routineId: String): AppData =
    copy(routines = routines.filter { it.id != routineId })
fun AppData.withLoggedWorkoutSession(session: WorkoutSession): AppData {
    val others = workoutSessions.filter { it.id != session.id }
    return copy(workoutSessions = others + session)
}
fun AppData.withUpdatedWorkoutSession(session: WorkoutSession): AppData =
    copy(workoutSessions = workoutSessions.map { if (it.id == session.id) session else it })
fun AppData.withoutWorkoutSession(sessionId: String): AppData =
    copy(workoutSessions = workoutSessions.filter { it.id != sessionId })
/** Merge Blueprint template routines that are not already present (by id). */
fun AppData.withBlueprintRoutinesIfMissing(templates: List<ExerciseRoutine>): AppData {
    val existingIds = routines.map { it.id }.toSet()
    val toAdd = templates.filter { it.id !in existingIds }
    return if (toAdd.isEmpty()) this else copy(routines = routines + toAdd)
}

// Goal Story / Path helpers (#25, #26)
fun AppData.withAddedGoal(goal: GoalStory): AppData = copy(goals = goals + goal)
fun AppData.withUpdatedGoal(goal: GoalStory): AppData =
    copy(goals = goals.map { if (it.id == goal.id) goal else it })
fun AppData.withArchivedGoal(goalId: String): AppData =
    copy(goals = goals.map { if (it.id == goalId) it.copy(archived = true, updatedAt = System.currentTimeMillis()) else it })
fun AppData.withGoalsReplacedFromWizard(newGoals: List<GoalStory>, replaceDreamline: Boolean = true): AppData {
    val kept = if (replaceDreamline) {
        goals.filter { g -> !g.archived && "dreamline" !in g.tags.map { it.lowercase() } }
    } else {
        goals
    }
    return copy(goals = kept + newGoals)
}
fun AppData.withAddedPathCheck(check: PathAlignmentCheck): AppData = copy(pathChecks = pathChecks + check)
fun AppData.withPathChecks(checks: List<PathAlignmentCheck>): AppData = copy(pathChecks = checks)

fun AppData.withScore(score: ScoreState): AppData = copy(score = score)
fun AppData.withNotificationPrefs(prefs: NotificationPrefs): AppData = copy(notificationPrefs = prefs)
fun AppData.withAutoSuggestions(list: List<AutoSuggestion>): AppData = copy(autoSuggestions = list)
fun AppData.withAutoLogMasterEnabled(enabled: Boolean): AppData = copy(autoLogMasterEnabled = enabled)
fun AppData.withSleepAudioPrefs(prefs: SleepAudioPrefs): AppData = copy(sleepAudioPrefs = prefs)
fun AppData.withSleepNights(nights: List<SleepNightSession>): AppData = copy(sleepNights = nights)
fun AppData.withUpsertedSleepNight(night: SleepNightSession): AppData {
    val others = sleepNights.filter { it.id != night.id }
    return copy(sleepNights = (listOf(night) + others).sortedByDescending { it.startedAt })
}
fun AppData.withSensorSnapshots(list: List<SensorSnapshot>): AppData = copy(sensorSnapshots = list)
fun AppData.withAddedSensorSnapshot(snap: SensorSnapshot): AppData =
    copy(sensorSnapshots = (listOf(snap) + sensorSnapshots).take(200))
fun AppData.withLocalWebPrefs(prefs: LocalWebPrefs): AppData = copy(localWebPrefs = prefs)
fun AppData.withPomodoroPrefs(prefs: PomodoroPrefs): AppData = copy(pomodoroPrefs = prefs)

fun AppData.withUpsertedAutoSuggestion(suggestion: AutoSuggestion): AppData {
    val others = autoSuggestions.filterNot {
        it.habitId == suggestion.habitId && it.date == suggestion.date && !it.resolved
    }
    return copy(autoSuggestions = others + suggestion)
}

fun AppData.withResolvedAutoSuggestion(habitId: String, date: String): AppData =
    copy(
        autoSuggestions = autoSuggestions.map {
            if (it.habitId == habitId && it.date == date && !it.resolved) it.copy(resolved = true) else it
        }
    )

/** Stable tag constants for Dreamline / Path filtering. */
object GoalTags {
    const val DREAMLINE = "dreamline"
    const val HORIZON_6 = "6-month"
    const val HORIZON_12 = "12-month"
    fun forHorizon(h: DreamHorizon) = when (h) {
        DreamHorizon.SIX_MONTHS -> HORIZON_6
        DreamHorizon.TWELVE_MONTHS -> HORIZON_12
    }
    fun forCategory(c: DreamCategory) = c.name.lowercase()
}
