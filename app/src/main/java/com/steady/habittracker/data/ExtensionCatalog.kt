package com.steady.habittracker.data

/**
 * Catalog of special habit blocks / extensions for Manage → Blocks (#33, #37).
 * Pure data + helpers — no Android dependencies.
 */
object ExtensionCatalog {

    data class Template(
        val type: ExtensionType,
        val title: String,
        val description: String,
        val defaultName: String,
        val defaultIcon: String,
        val category: String,
        val defaultConfig: ExtensionConfig = ExtensionConfig(),
        /** Suggested planner group timeHint: MORNING, BEDTIME, WORK, ANY… */
        val suggestTimeHint: String = "ANY"
    )

    val TEMPLATES: List<Template> = listOf(
        Template(
            type = ExtensionType.SNORE_WATCH_ACTIVATE,
            title = "Snore Watch · Activate",
            description = "Start overnight sleep-audio capture (bedtime routine).",
            defaultName = "Activate Snoring Recording",
            defaultIcon = "🎙️",
            category = "Sleep",
            suggestTimeHint = "BEDTIME"
        ),
        Template(
            type = ExtensionType.SNORE_WATCH_STOP,
            title = "Snore Watch · Stop & Review",
            description = "Stop overnight capture and review the night (morning).",
            defaultName = "Stop Snoring Recording & Review",
            defaultIcon = "🌙",
            category = "Sleep",
            suggestTimeHint = "MORNING"
        ),
        Template(
            type = ExtensionType.SENSOR_AUTO_READ,
            title = "Sensor Auto-Read",
            description = "Capture GPS, steps, light, noise, and/or screen in one block.",
            defaultName = "Sensor Snapshot",
            defaultIcon = "📡",
            category = "Sensors",
            defaultConfig = ExtensionConfig(
                sensors = listOf(SensorKind.GPS, SensorKind.STEPS, SensorKind.LIGHT, SensorKind.SCREEN)
            ),
            suggestTimeHint = "MORNING"
        ),
        Template(
            type = ExtensionType.SCREEN_USAGE,
            title = "Screen Usage Tracker",
            description = "Daily screen total + optional per-app breakdown.",
            defaultName = "Screen Usage Audit",
            defaultIcon = "📱",
            category = "Digital wellness",
            defaultConfig = ExtensionConfig(includeAppBreakdown = true, dailyLimitMinutes = 180),
            suggestTimeHint = "BEDTIME"
        ),
        Template(
            type = ExtensionType.WORKOUT_SESSION,
            title = "Workout Session",
            description = "Opens structured workout logging for exercise routines.",
            defaultName = "Workout Session",
            defaultIcon = "💪",
            category = "Movement",
            suggestTimeHint = "WORK"
        ),
        Template(
            type = ExtensionType.ESM_CHECKIN,
            title = "Awareness Check-in",
            description = "ESM-style prompt: what are you doing right now?",
            defaultName = "Awareness Check-in",
            defaultIcon = "🪞",
            category = "Awareness",
            suggestTimeHint = "ANY"
        ),
        Template(
            type = ExtensionType.POMODORO,
            title = "Pomodoro / Focus Timer",
            description = "Focus block with work/break minutes; pairs with local web timer.",
            defaultName = "Focus Session",
            defaultIcon = "🍅",
            category = "Focus",
            defaultConfig = ExtensionConfig(pomodoroWorkMin = 25, pomodoroBreakMin = 5),
            suggestTimeHint = "WORK"
        ),
        Template(
            type = ExtensionType.GADGETBRIDGE_SYNC,
            title = "Gadgetbridge Wearables",
            description = "Poll Gadgetbridge auto-export for steps, sleep & heart rate from any " +
                "supported gadget into one standard History system.",
            defaultName = "Wearable Sync",
            defaultIcon = "⌚",
            category = "Wearables",
            suggestTimeHint = "MORNING"
        ),
        Template(
            type = ExtensionType.ORAL_HYGIENE,
            title = "Oral Hygiene",
            description = "Universal dental care — brush, floss, tongue scrape, water flush & more. " +
                "Always placed in morning and evening routines.",
            defaultName = "Oral Hygiene",
            defaultIcon = "🪥",
            category = "Hygiene",
            suggestTimeHint = "MORNING"
        )
    )

    fun templateFor(type: ExtensionType): Template? =
        TEMPLATES.find { it.type == type }

    fun label(type: ExtensionType): String = when (type) {
        ExtensionType.NONE -> "Standard"
        ExtensionType.SNORE_WATCH_ACTIVATE -> "Snore · Start"
        ExtensionType.SNORE_WATCH_STOP -> "Snore · Stop"
        ExtensionType.SENSOR_AUTO_READ -> "Sensor Read"
        ExtensionType.SCREEN_USAGE -> "Screen Usage"
        ExtensionType.WORKOUT_SESSION -> "Workout"
        ExtensionType.ESM_CHECKIN -> "Check-in"
        ExtensionType.POMODORO -> "Pomodoro"
        ExtensionType.GADGETBRIDGE_SYNC -> "Gadgetbridge"
        ExtensionType.ORAL_HYGIENE -> "Oral hygiene"
    }

    fun isSpecial(habit: Habit): Boolean = habit.extensionType != ExtensionType.NONE

    /**
     * Habits that stay on the day timeline (Morning / Work / …).
     * Oral hygiene steps are timeline habits; tool-style blocks are not.
     */
    fun livesOnDayTimeline(habit: Habit): Boolean = when (habit.extensionType) {
        ExtensionType.NONE -> true
        ExtensionType.ORAL_HYGIENE -> true
        // Everything else is an “enabled block” strip under Today’s habits
        else -> false
    }

    /** Manage → Blocks tools shown under Today habits, not in group grids. */
    fun isBlockStripHabit(habit: Habit): Boolean =
        !habit.archived && isSpecial(habit) && !livesOnDayTimeline(habit)

    fun activeExtensionHabits(data: AppData): List<Habit> =
        data.habits.filter { !it.archived && it.extensionType != ExtensionType.NONE }

    /** One representative habit per extension type for the Today blocks strip. */
    fun enabledBlockHabitsForToday(data: AppData): List<Habit> {
        return activeExtensionHabits(data)
            .filter { isBlockStripHabit(it) }
            .groupBy { it.extensionType }
            .values
            .map { group -> group.minByOrNull { it.order } ?: group.first() }
            .sortedBy { label(it.extensionType) }
    }

    fun habitsOfType(data: AppData, type: ExtensionType): List<Habit> =
        data.habits.filter { !it.archived && it.extensionType == type }

    /** Suggest a group id by timeHint, else first active group. */
    fun suggestGroupId(data: AppData, timeHint: String): String? {
        val active = data.groups.filter { !it.archived }.sortedBy { it.order }
        if (active.isEmpty()) return null
        val hint = timeHint.uppercase()
        return active.firstOrNull { it.timeHint.uppercase() == hint }?.id
            ?: active.firstOrNull {
                when (hint) {
                    "MORNING" -> it.name.contains("morning", true) || it.name.contains("wake", true)
                    "BEDTIME" -> it.name.contains("bed", true) || it.name.contains("wind", true) || it.name.contains("evening", true)
                    "WORK" -> it.name.contains("focus", true) || it.name.contains("work", true)
                    else -> false
                }
            }?.id
            ?: active.first().id
    }
}
