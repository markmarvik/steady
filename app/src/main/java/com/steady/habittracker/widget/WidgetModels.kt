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
 * Build ordered widget rows: pending *due* habits for today, grouped.
 * Current time-of-day group is listed first and labeled "Now".
 * Safety cap of 40 rows total (headers + habits).
 */
fun buildWidgetRows(data: AppData, now: LocalTime = LocalTime.now()): List<WidgetRow> {
    val currentGroup = HabitDomain.resolveCurrentGroup(data, now)
    val pendingByGroup = HabitDomain.pendingGroupedForDate(data, LocalDate.now())

    if (pendingByGroup.isEmpty()) return emptyList()

    val ordered = buildList {
        val current = pendingByGroup.firstOrNull { it.first.id == currentGroup?.id }
        if (current != null) add(current)
        pendingByGroup.filter { it.first.id != currentGroup?.id }.forEach { add(it) }
    }

    val rows = mutableListOf<WidgetRow>()
    for ((group, habits) in ordered) {
        if (rows.size >= 40) break
        val isNow = group.id == currentGroup?.id
        val header = if (isNow) "Now · ${group.name}" else group.name
        rows.add(WidgetRow(kind = WidgetRowKind.SECTION, title = header, isCurrentGroup = isNow))
        for (h in habits) {
            if (rows.size >= 40) break
            val prefix = if (h.afterHabitId != null) "↳ " else ""
            rows.add(
                WidgetRow(
                    kind = WidgetRowKind.HABIT,
                    title = prefix + h.name,
                    habitId = h.id,
                    isCheckbox = h.type == HabitType.CHECKBOX,
                    isCurrentGroup = isNow
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
