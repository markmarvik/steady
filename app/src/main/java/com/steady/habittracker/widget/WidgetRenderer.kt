package com.steady.habittracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.steady.habittracker.MainActivity
import com.steady.habittracker.R
import com.steady.habittracker.data.AndroidHabitRepository
import com.steady.habittracker.data.AppData
import com.steady.habittracker.data.HabitDomain
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Shared widget render + refresh. Provider, ToggleReceiver, and app saves all call here
 * so the home screen stays in sync without a second cold APPWIDGET_UPDATE hop when data
 * is already in memory.
 */
object WidgetRenderer {

    private const val PREFS = "steady_widget_cache"
    private const val KEY_ROWS = "rows_json"
    private const val KEY_ROW_BG = "row_bg"
    private const val KEY_ACCENT = "accent"
    private const val KEY_TEXT = "text_color"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun loadData(context: Context): AppData {
        return AndroidHabitRepository(context).appDataFlow.first()
    }

    fun updateAll(context: Context, appData: AppData) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, SteadyWidgetProvider::class.java))
        if (ids.isEmpty()) return
        render(context, mgr, ids, appData)
    }

    fun render(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        appData: AppData
    ) {
        val rows = buildWidgetRows(appData)
        val theme = HabitDomain.resolveThemeColors(appData)
        val textColor = if (appData.backgroundMode == "light") 0xFF0F172A.toInt() else 0xFFE2E8F0.toInt()

        // Snapshot for RemoteViewsFactory (collection adapter)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ROWS, json.encodeToString(rows))
            .putInt(KEY_ROW_BG, theme.widgetRowBg)
            .putInt(KEY_ACCENT, theme.accent)
            .putInt(KEY_TEXT, textColor)
            .apply()

        val title = widgetTitle(appData)
        val empty = rows.isEmpty()

        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.steady_widget)
            views.setInt(R.id.widget_root, "setBackgroundColor", theme.background)
            views.setTextViewText(R.id.widget_title, title)
            views.setTextColor(R.id.widget_title, theme.accent)

            views.setViewVisibility(R.id.widget_list, if (empty) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.widget_empty, if (empty) View.VISIBLE else View.GONE)
            views.setTextColor(R.id.widget_empty, theme.accent)

            if (!empty) {
                val serviceIntent = Intent(context, WidgetListService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    // Unique data URI so Android binds per-widget
                    this.data = android.net.Uri.parse("steady://widget/$id")
                }
                views.setRemoteAdapter(R.id.widget_list, serviceIntent)
                views.setEmptyView(R.id.widget_list, R.id.widget_empty)

                // Template for habit row clicks → ToggleReceiver
                val template = Intent(context, ToggleReceiver::class.java)
                val templatePi = PendingIntent.getBroadcast(
                    context,
                    2000 + id,
                    template,
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                views.setPendingIntentTemplate(R.id.widget_list, templatePi)
            }

            val open = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPi = PendingIntent.getActivity(
                context, 999, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.open_btn, openPi)
            views.setOnClickPendingIntent(R.id.widget_title, openPi)

            appWidgetManager.updateAppWidget(id, views)
            // Force list reload after snapshot write
            appWidgetManager.notifyAppWidgetViewDataChanged(id, R.id.widget_list)
        }
    }

    fun readCachedRows(context: Context): List<WidgetRow> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ROWS, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<WidgetRow>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun readRowBg(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_ROW_BG, 0xFF1E3A5F.toInt())

    fun readAccent(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_ACCENT, 0xFF22C55E.toInt())

    fun readTextColor(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_TEXT, 0xFFE2E8F0.toInt())
}
