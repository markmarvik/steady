package com.steady.habittracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.steady.habittracker.MainActivity
import com.steady.habittracker.R
import com.steady.habittracker.data.AndroidHabitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SteadyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val repo = AndroidHabitRepository(context)
        CoroutineScope(Dispatchers.IO).launch {
            val data = repo.appDataFlow.first()
            val period = repo.getCurrentPeriodHint()
            val groups = repo.groupHabits(data)
            val active = groups.firstOrNull { it.first.timeHint == period } ?: groups.firstOrNull() ?: return@launch
            val groupName = active.first.name

            // Determine prev/next for missed/upcoming
            val timeHints = listOf("MORNING", "WORK", "EVENING", "REVIEW")
            val currentIdx = timeHints.indexOf(period).let { if (it < 0) 0 else it }
            val prevHint = timeHints[if (currentIdx > 0) currentIdx - 1 else timeHints.lastIndex]
            val nextHint = timeHints[if (currentIdx < timeHints.lastIndex) currentIdx + 1 else 0]

            val prevGroup = groups.firstOrNull { it.first.timeHint == prevHint }
            val nextGroup = groups.firstOrNull { it.first.timeHint == nextHint }

            // Entries for today
            val today = repo.getToday()
            val entries = data.entries[today] ?: emptyMap()

            // Only pending (not done) for current - clicking hides them by re-render
            val currentPending = active.second.filter { h ->
                val e = entries[h.id]
                (e?.value ?: 0.0) < 0.5 && e?.skipped != true
            }

            val missed = prevGroup?.second?.filter { h ->
                val e = entries[h.id]
                e == null || (e.value < 0.5 && e.skipped != true)
            } ?: emptyList()

            val upcoming = nextGroup?.second?.take(3) ?: emptyList()

            // Title
            val complete = currentPending.isEmpty() && active.second.isNotEmpty()
            val title = if (complete) "✓ $groupName Complete" else "Steady • $groupName (${currentPending.size} left)"

            appWidgetIds.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.steady_widget)
                views.setTextViewText(R.id.widget_title, title)

                // --- Current pending (large tappable, 2x size, buttons) ---
                val currentRows = listOf(R.id.current_row_0, R.id.current_row_1, R.id.current_row_2)
                currentRows.forEachIndexed { idx, rowId ->
                    if (idx < currentPending.size) {
                        val h = currentPending[idx]
                        views.setTextViewText(rowId, "☐ ${h.name}")
                        views.setViewVisibility(rowId, android.view.View.VISIBLE)

                        val toggleIntent = Intent(context, ToggleReceiver::class.java).apply {
                            putExtra("habitId", h.id)
                            putExtra("action", if (h.type.name == "CHECKBOX") "TOGGLE" else "OPEN_LOG")
                        }
                        val pi = PendingIntent.getBroadcast(
                            context, (h.id + idx).hashCode(), toggleIntent,
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        )
                        views.setOnClickPendingIntent(rowId, pi)
                    } else {
                        views.setViewVisibility(rowId, android.view.View.GONE)
                    }
                }

                // --- Missed from previous ---
                views.setTextViewText(R.id.missed_header, if (missed.isNotEmpty()) "Missed from previous:" else "")
                views.setViewVisibility(R.id.missed_header, if (missed.isNotEmpty()) android.view.View.VISIBLE else android.view.View.GONE)

                if (missed.isNotEmpty()) {
                    val m = missed.first()
                    views.setTextViewText(R.id.missed_row_0, "• ${m.name}")
                    views.setViewVisibility(R.id.missed_row_0, android.view.View.VISIBLE)
                    // Optional: clicking missed opens app (no direct toggle to avoid complexity)
                    val openIntent = Intent(context, MainActivity::class.java)
                    val openPi = PendingIntent.getActivity(context, 1000, openIntent, PendingIntent.FLAG_IMMUTABLE)
                    views.setOnClickPendingIntent(R.id.missed_row_0, openPi)
                } else {
                    views.setViewVisibility(R.id.missed_row_0, android.view.View.GONE)
                }

                // --- Upcoming group preview ---
                views.setTextViewText(R.id.upcoming_header, if (upcoming.isNotEmpty()) "Upcoming:" else "")
                views.setViewVisibility(R.id.upcoming_header, if (upcoming.isNotEmpty()) android.view.View.VISIBLE else android.view.View.GONE)

                if (upcoming.isNotEmpty()) {
                    val u = upcoming.first()
                    views.setTextViewText(R.id.upcoming_row_0, "→ ${u.name}")
                    views.setViewVisibility(R.id.upcoming_row_0, android.view.View.VISIBLE)
                    val openIntent = Intent(context, MainActivity::class.java)
                    val openPi = PendingIntent.getActivity(context, 1001, openIntent, PendingIntent.FLAG_IMMUTABLE)
                    views.setOnClickPendingIntent(R.id.upcoming_row_0, openPi)
                } else {
                    views.setViewVisibility(R.id.upcoming_row_0, android.view.View.GONE)
                }

                // Open app button
                val open = Intent(context, MainActivity::class.java)
                val openPi = PendingIntent.getActivity(context, 999, open, PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(R.id.open_btn, openPi)

                appWidgetManager.updateAppWidget(id, views)
            }
        }
    }
}
