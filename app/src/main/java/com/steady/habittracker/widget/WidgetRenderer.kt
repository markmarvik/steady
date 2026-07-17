package com.steady.habittracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
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
        val textColor = if (com.steady.habittracker.data.backgroundModeOption(appData.backgroundMode).isLight) {
            0xFF0F172A.toInt()
        } else {
            0xFFE2E8F0.toInt()
        }
        val onAccent = buttonLabelColor(theme.accent)

        // commit() (not apply): legacy RemoteViewsFactory may re-read immediately after notify
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ROWS, json.encodeToString(rows))
            .putInt(KEY_ROW_BG, theme.widgetRowBg)
            .putInt(KEY_ACCENT, theme.accent)
            .putInt(KEY_TEXT, textColor)
            .commit()

        val title = widgetTitle(appData)
        val empty = rows.isEmpty()
        val useCollectionItems = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.steady_widget)
            views.setInt(R.id.widget_root, "setBackgroundColor", theme.background)
            views.setTextViewText(R.id.widget_title, title)
            views.setTextColor(R.id.widget_title, theme.accent)

            views.setViewVisibility(R.id.widget_list, if (empty) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.widget_empty, if (empty) View.VISIBLE else View.GONE)
            views.setTextColor(R.id.widget_empty, theme.accent)

            if (!empty) {
                if (useCollectionItems) {
                    // API 31+: embed list items in the RemoteViews update (no RemoteViewsService /
                    // notifyAppWidgetViewDataChanged — those Intent APIs are deprecated).
                    val builder = RemoteViews.RemoteCollectionItems.Builder()
                        .setHasStableIds(false)
                        .setViewTypeCount(2)
                    rows.forEachIndexed { index, row ->
                        builder.addItem(index.toLong(), buildRowRemoteViews(context, row))
                    }
                    views.setRemoteAdapter(R.id.widget_list, builder.build())
                } else {
                    val serviceIntent = Intent(context, WidgetListService::class.java).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        this.data = android.net.Uri.parse("steady://widget/$id")
                    }
                    @Suppress("DEPRECATION")
                    views.setRemoteAdapter(R.id.widget_list, serviceIntent)
                }
                views.setEmptyView(R.id.widget_list, R.id.widget_empty)

                // Template for habit row clicks → ToggleReceiver (must be MUTABLE for fill-in)
                val template = Intent(context, ToggleReceiver::class.java).apply {
                    this.action = ToggleReceiver.ACTION_TOGGLE
                    this.data = android.net.Uri.parse("steady://widget/template/$id")
                }
                val templatePi = PendingIntent.getBroadcast(
                    context,
                    2000 + id,
                    template,
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                views.setPendingIntentTemplate(R.id.widget_list, templatePi)
            }

            // Footer buttons: accent from app theme (same colorScheme as in-app primary)
            styleFooterButton(views, R.id.capture_btn, theme.accent, onAccent)
            styleFooterButton(views, R.id.log_btn, theme.accent, onAccent)
            styleFooterButton(views, R.id.open_btn, theme.accent, onAccent)

            views.setOnClickPendingIntent(
                R.id.capture_btn,
                activityPi(context, 901 + id * 10, "open_capture" to "1")
            )
            views.setOnClickPendingIntent(
                R.id.log_btn,
                activityPi(context, 902 + id * 10, "open_log" to "1")
            )
            views.setOnClickPendingIntent(
                R.id.open_btn,
                activityPi(context, 903 + id * 10)
            )

            appWidgetManager.updateAppWidget(id, views)

            // Legacy path only: force RemoteViewsFactory to re-read SharedPreferences snapshot
            if (!empty && !useCollectionItems) {
                @Suppress("DEPRECATION")
                appWidgetManager.notifyAppWidgetViewDataChanged(id, R.id.widget_list)
            }
        }
    }

    private fun styleFooterButton(views: RemoteViews, viewId: Int, accent: Int, onAccent: Int) {
        views.setInt(viewId, "setBackgroundColor", accent)
        views.setTextColor(viewId, onAccent)
    }

    /** Black text on bright accents, white on darker ones — matches in-app onPrimary. */
    private fun buttonLabelColor(accent: Int): Int {
        val r = Color.red(accent) / 255.0
        val g = Color.green(accent) / 255.0
        val b = Color.blue(accent) / 255.0
        // Relative luminance (sRGB approx)
        val lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
        return if (lum > 0.55) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }

    private fun activityPi(
        context: Context,
        requestCode: Int,
        vararg extras: Pair<String, String>
    ): PendingIntent {
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            extras.forEach { (k, v) -> putExtra(k, v) }
            // Unique data so PendingIntents with different extras are not collapsed
            if (extras.isNotEmpty()) {
                data = android.net.Uri.parse(
                    "steady://app/" + extras.joinToString("&") { "${it.first}=${it.second}" }
                )
            } else {
                data = android.net.Uri.parse("steady://app/open")
            }
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
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
