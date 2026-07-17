package com.steady.habittracker.widget

import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.steady.habittracker.R

/**
 * Legacy scrollable widget list for API &lt; 31.
 * API 31+ uses [RemoteViews.RemoteCollectionItems] in [WidgetRenderer] instead.
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
        // Called on a binder thread after notifyAppWidgetViewDataChanged — re-read snapshot
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
        return buildRowRemoteViews(context, rows[position])
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 2

    // Unstable IDs so completed rows actually disappear after notifyAppWidgetViewDataChanged
    // (stable IDs + recycled RemoteViews can leave stale rows on some launchers).
    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = false
}
