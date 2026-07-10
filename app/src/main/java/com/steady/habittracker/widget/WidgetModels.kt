package com.steady.habittracker.widget

import com.steady.habittracker.data.AppData
import com.steady.habittracker.data.HabitDomain
import com.steady.habittracker.data.HabitType
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
    val isCurrentGroup: Boolean = false
)

/**
 * Pure visual day progression for the widget (same order as Today):
 * top = earlier, bottom = later. Current block labeled "● Name" — no clock times,
 * and current group is **not** moved to the top.
 */
fun buildWidgetRows(data: AppData, now: LocalTime = LocalTime.now()): List<WidgetRow> {
    val sections = HabitDomain.timelineSectionsForToday(data, LocalDate.now(), now)
    if (sections.isEmpty()) return emptyList()

    val rows = mutableListOf<WidgetRow>()
    for (section in sections) {
        if (rows.size >= 40) break
        val header = if (section.isNow) {
            "● ${section.group.name}"
        } else {
            section.group.name
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
            val prefix = if (h.afterHabitId != null) "↳ " else ""
            rows.add(
                WidgetRow(
                    kind = WidgetRowKind.HABIT,
                    title = prefix + h.name,
                    habitId = h.id,
                    isCheckbox = h.type == HabitType.CHECKBOX,
                    isCurrentGroup = section.isNow
                )
            )
        }
    }
    return rows
}

fun pendingCountToday(data: AppData): Int {
    val today = LocalDate.now()
    val entries = data.entries[HabitDomain.getToday()] ?: emptyMap()
    return HabitDomain.habitsDueOn(data, today).count { h ->
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
