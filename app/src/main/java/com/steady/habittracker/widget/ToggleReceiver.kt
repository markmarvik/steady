package com.steady.habittracker.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.steady.habittracker.MainActivity
import com.steady.habittracker.data.AndroidHabitRepository
import com.steady.habittracker.data.HabitEntry
import com.steady.habittracker.data.withUpdatedEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Widget action receiver. Performs toggle/log and refreshes widgets.
 * Uses immutable helpers (#16) and same entry logic as VM to reduce bugs (#7).
 *
 * Improvements for #7:
 * - Uses goAsync() so the BroadcastReceiver lives until coroutine completes (prevents races / ANR on slow IO).
 * - No direct mutable map mutation.
 * - After change, triggers targeted widget refresh.
 * - Still short lived scope per receive (acceptable for broadcast receivers); app-level scope possible in future.
 */
class ToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val habitId = intent.getStringExtra("habitId") ?: return
        val action = intent.getStringExtra("action") ?: "TOGGLE"

        val pendingResult = goAsync()
        val repo = AndroidHabitRepository(context)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val data = repo.appDataFlow.first()
                val today = repo.getToday()
                val currentEntry = data.entries[today]?.get(habitId)

                when (action) {
                    "TOGGLE" -> {
                        val newVal = if ((currentEntry?.value ?: 0.0) >= 0.5) 0.0 else 1.0
                        val entry = HabitEntry(
                            value = newVal,
                            note = currentEntry?.note ?: "",
                            loggedAt = System.currentTimeMillis()
                        )
                        // Immutable update!
                        val updatedData = data.withUpdatedEntry(today, habitId, entry)
                        repo.saveData(updatedData)
                    }
                    else -> {
                        // For amounts / non-checkbox open app for full log dialog
                        val i = Intent(context, MainActivity::class.java).apply {
                            putExtra("log_habit", habitId)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(i)
                        return@launch
                    }
                }

                // Refresh widgets using update broadcast (provider will re-render using current data)
                val ids = AppWidgetManager.getInstance(context)
                    .getAppWidgetIds(ComponentName(context, SteadyWidgetProvider::class.java))
                if (ids.isNotEmpty()) {
                    val updateIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    }
                    context.sendBroadcast(updateIntent)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
