package com.steady.habittracker.widget

import com.steady.habittracker.data.AppData
import com.steady.habittracker.data.ExtensionCatalog
import com.steady.habittracker.data.ExtensionType
import com.steady.habittracker.data.HabitDomain
import com.steady.habittracker.data.HabitType
import com.steady.habittracker.data.OralHygieneSteps
import com.steady.habittracker.data.displayLabel
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime

@Serializable
enum class WidgetRowKind {
    SECTION,
    HABIT
}

@Serializable
data class WidgetRow(
    val kind: WidgetRowKind,
    val title: String = "",
    val habitId: String? = null,
    val isCheckbox: Boolean = true,
    /** HabitType name for pending icon (CHECKBOX, COUNTER, …). Empty for sections. */
    val habitType: String = "",
    val isCurrentGroup: Boolean = false,
    /** ExtensionType name when not NONE (#33 widget surface). */
    val extensionType: String = "",
    /** Compact status for special blocks (e.g. snore armed, screen so far). */
    val extensionHint: String = ""
)

/**
 * Pure visual day progression for the widget (same order as Today):
 * top = earlier, bottom = later. Current block labeled "● Name" — no clock times,
 * and current group is **not** moved to the top.
 */
fun buildWidgetRows(data: AppData, now: LocalTime = LocalTime.now()): List<WidgetRow> {
    val day = HabitDomain.logicalTodayDate(data)
    val sections = HabitDomain.timelineSectionsForToday(data, day, now)
    if (sections.isEmpty()) return emptyList()

    val rows = mutableListOf<WidgetRow>()
    for (section in sections) {
        if (rows.size >= 40) break
        val header = if (section.isNow) {
            "● ${section.group.displayLabel()}"
        } else {
            section.group.displayLabel()
        }
        rows.add(
            WidgetRow(
                kind = WidgetRowKind.SECTION,
                title = header,
                isCurrentGroup = section.isNow
            )
        )
        for (h in section.habits) {
            if (rows.size >= 40) break
            val stack = if (h.afterHabitId != null) "↳ " else ""
            val ext = h.extensionType
            val extLabel = if (ext != ExtensionType.NONE) {
                " · ${ExtensionCatalog.label(ext)}"
            } else ""
            // statusLine needs Context for screen time; keep offline-safe hints here
            val hint = when (ext) {
                ExtensionType.NONE -> ""
                ExtensionType.SNORE_WATCH_ACTIVATE ->
                    if (data.sleepAudioPrefs.enabled) "armed" else "start"
                ExtensionType.SNORE_WATCH_STOP ->
                    data.sleepNights.firstOrNull()?.let { "n:${it.eventCount}" } ?: "review"
                ExtensionType.SENSOR_AUTO_READ -> "sensors"
                ExtensionType.SCREEN_USAGE -> "screen"
                ExtensionType.WORKOUT_SESSION -> "workout"
                ExtensionType.ESM_CHECKIN -> "check-in"
                ExtensionType.POMODORO -> "${h.extensionConfig.pomodoroWorkMin}m"
                ExtensionType.GADGETBRIDGE_SYNC ->
                    data.wearableDays.firstOrNull()?.steps?.let { "${it} steps" } ?: "sync"
                ExtensionType.ORAL_HYGIENE -> when (h.extensionConfig.oralStepKey) {
                    OralHygieneSteps.BRUSH -> "brush"
                    OralHygieneSteps.FLOSS -> "floss"
                    OralHygieneSteps.TONGUE -> "tongue"
                    OralHygieneSteps.WATER -> "water"
                    OralHygieneSteps.MOUTHWASH -> "rinse"
                    else -> "oral"
                }
            }
            rows.add(
                WidgetRow(
                    kind = WidgetRowKind.HABIT,
                    title = stack + h.displayLabel() + extLabel,
                    habitId = h.id,
                    isCheckbox = h.type == HabitType.CHECKBOX || ext != ExtensionType.NONE,
                    habitType = h.type.name,
                    isCurrentGroup = section.isNow,
                    extensionType = if (ext != ExtensionType.NONE) ext.name else "",
                    extensionHint = hint
                )
            )
        }
        // Active extension status pill at end of current section (#33)
        if (section.isNow) {
            val activeSnore = data.habits.any {
                !it.archived && it.extensionType == ExtensionType.SNORE_WATCH_ACTIVATE &&
                    data.sleepAudioPrefs.enabled
            }
            if (activeSnore && rows.size < 40) {
                rows.add(
                    WidgetRow(
                        kind = WidgetRowKind.SECTION,
                        title = "● Snore Watch active",
                        isCurrentGroup = true,
                        extensionType = ExtensionType.SNORE_WATCH_ACTIVATE.name,
                        extensionHint = "recording"
                    )
                )
            }
        }
    }
    return rows
}

/** Pending-only icons — never show ✓ here (completed rows are filtered out). */
fun pendingRowIcon(habitType: String, isCheckbox: Boolean): String {
    if (isCheckbox || habitType == HabitType.CHECKBOX.name) return "☐"
    return when (habitType) {
        HabitType.COUNTER.name -> "#"
        HabitType.DURATION_MIN.name -> "m"
        HabitType.SCALE_1_5.name -> "±"
        HabitType.NOTE.name -> "✎"
        else -> "○"
    }
}

fun pendingCountToday(data: AppData): Int {
    val day = HabitDomain.logicalTodayDate(data)
    val entries = data.entries[day.toString()] ?: emptyMap()
    return HabitDomain.habitsDueOn(data, day).count { h ->
        HabitDomain.isPendingEntry(entries[h.id])
    }
}

fun widgetTitle(data: AppData): String {
    val left = pendingCountToday(data)
    val current = HabitDomain.resolveCurrentGroup(data)?.name
    return when {
        left == 0 -> "✓ Steady · All done"
        current != null -> "Steady · $left left · $current"
        else -> "Steady · $left left"
    }
}
