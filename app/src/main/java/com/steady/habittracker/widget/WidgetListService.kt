package com.steady.habittracker.widget

import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.steady.habittracker.R

/**
 * Provides scrollable widget rows (section headers + habits) from the cache
 * written by [WidgetRenderer].
 */
class WidgetListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return WidgetListFactory(applicationContext)
    }
}

private class WidgetListFactory(
    private val context: android.content.Context
) : RemoteViewsService.RemoteViewsFactory {

    private var rows: List<WidgetRow> = emptyList()

    override fun onCreate() {
        rows = WidgetRenderer.readCachedRows(context)
    }

    override fun onDataSetChanged() {
        rows = WidgetRenderer.readCachedRows(context)
    }

    override fun onDestroy() {
        rows = emptyList()
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position !in rows.indices) {
            return RemoteViews(context.packageName, R.layout.widget_habit_row)
        }
        val row = rows[position]
        return when (row.kind) {
            WidgetRowKind.SECTION -> {
                val rv = RemoteViews(context.packageName, R.layout.widget_section_header)
                rv.setTextViewText(R.id.section_title, row.title)
                // Current block (●) uses full accent; earlier/later slightly softer via same accent
                rv.setTextColor(R.id.section_title, WidgetRenderer.readAccent(context))
                // Headers are not clickable
                rv.setOnClickFillInIntent(R.id.section_title, Intent())
                rv
            }
            WidgetRowKind.HABIT -> {
                val rv = RemoteViews(context.packageName, R.layout.widget_habit_row)
                val textColor = WidgetRenderer.readTextColor(context)
                val rowBg = WidgetRenderer.readRowBg(context)
                // Always complete from widget (no accidental app open). Checkboxes toggle;
                // counters/durations/etc. log a simple done value.
                rv.setTextViewText(R.id.habit_row_check, if (row.isCheckbox) "☐" else "✓")
                rv.setTextColor(R.id.habit_row_check, textColor)
                rv.setTextViewText(R.id.habit_row_text, row.title)
                rv.setTextColor(R.id.habit_row_text, textColor)
                rv.setInt(R.id.habit_row_root, "setBackgroundColor", rowBg)

                val fillIn = Intent().apply {
                    putExtra("habitId", row.habitId)
                    putExtra("action", "COMPLETE")
                }
                // Entire row is one big hit target
                rv.setOnClickFillInIntent(R.id.habit_row_root, fillIn)
                rv
            }
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 2

    override fun getItemId(position: Int): Long =
        rows.getOrNull(position)?.habitId?.hashCode()?.toLong()
            ?: rows.getOrNull(position)?.title?.hashCode()?.toLong()
            ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}
