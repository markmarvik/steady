package com.steady.habittracker.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.steady.habittracker.R
import com.steady.habittracker.data.HabitType

/**
 * Shared row RemoteViews for the home-screen list.
 * Used by [RemoteViews.RemoteCollectionItems] (API 31+) and the legacy [WidgetListService].
 */
fun buildRowRemoteViews(context: Context, row: WidgetRow): RemoteViews {
    return when (row.kind) {
        WidgetRowKind.SECTION -> {
            val rv = RemoteViews(context.packageName, R.layout.widget_section_header)
            rv.setTextViewText(R.id.section_title, row.title)
            rv.setTextColor(R.id.section_title, WidgetRenderer.readAccent(context))
            // Headers are not clickable (empty fill-in)
            rv.setOnClickFillInIntent(R.id.section_title, Intent())
            rv
        }
        WidgetRowKind.HABIT -> {
            val rv = RemoteViews(context.packageName, R.layout.widget_habit_row)
            val textColor = WidgetRenderer.readTextColor(context)
            val rowBg = WidgetRenderer.readRowBg(context)
            val habitId = row.habitId.orEmpty()
            val groupId = row.groupId.orEmpty()
            val isNote = row.habitType == HabitType.NOTE.name

            // Pending list only — never show filled checkmarks here
            val checkIcon = when {
                row.extensionType.isNotBlank() && row.extensionHint.isNotBlank() -> "◆"
                else -> pendingRowIcon(row.habitType, row.isCheckbox)
            }
            rv.setTextViewText(R.id.habit_row_check, checkIcon)
            rv.setTextColor(R.id.habit_row_check, textColor)
            val label = if (row.extensionHint.isNotBlank()) {
                "${row.title} · ${row.extensionHint}"
            } else {
                row.title
            }
            rv.setTextViewText(R.id.habit_row_text, label)
            rv.setTextColor(R.id.habit_row_text, textColor)
            rv.setInt(R.id.habit_row_root, "setBackgroundColor", rowBg)

            // NOTE opens the in-app log dialog; everything else one-taps complete/toggle
            val fillIn = Intent().apply {
                action = if (isNote) ToggleReceiver.ACTION_OPEN_LOG else ToggleReceiver.ACTION_TOGGLE
                putExtra(ToggleReceiver.EXTRA_HABIT_ID, habitId)
                if (groupId.isNotBlank()) {
                    putExtra(ToggleReceiver.EXTRA_GROUP_ID, groupId)
                }
                putExtra(
                    ToggleReceiver.EXTRA_ACTION,
                    if (isNote) ToggleReceiver.ACTION_OPEN_LOG else ToggleReceiver.ACTION_TOGGLE
                )
                // Unique data so each row's PendingIntent fill-in is distinct across launchers
                data = Uri.parse("steady://widget/habit/$habitId/$groupId")
            }
            // Entire row is one big hit target (root + children for OEM quirks)
            rv.setOnClickFillInIntent(R.id.habit_row_root, fillIn)
            rv.setOnClickFillInIntent(R.id.habit_row_check, fillIn)
            rv.setOnClickFillInIntent(R.id.habit_row_text, fillIn)
            rv
        }
    }
}
