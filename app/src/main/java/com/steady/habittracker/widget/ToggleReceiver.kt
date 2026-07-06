package com.steady.habittracker.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.steady.habittracker.MainActivity
import com.steady.habittracker.data.AndroidHabitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Widget action receiver. Performs toggle/skip/log and refreshes widgets.
 */
class ToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val habitId = intent.getStringExtra("habitId") ?: return
        val action = intent.getStringExtra("action") ?: "TOGGLE"

        val repo = AndroidHabitRepository(context)
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            val data = repo.appDataFlow.first()
            val today = repo.getToday()
            val current = data.entries[today]?.get(habitId)

            when (action) {
                "TOGGLE" -> {
                    val newVal = if ((current?.value ?: 0.0) >= 0.5) 0.0 else 1.0
                    // Reuse log via direct save (simplified; in real would use VM)
                    val entries = data.entries.toMutableMap()
                    val day = entries.getOrPut(today) { emptyMap() }.toMutableMap()
                    day[habitId] = com.steady.habittracker.data.HabitEntry(
                        value = newVal,
                        note = current?.note ?: "",
                        loggedAt = System.currentTimeMillis()
                    )
                    entries[today] = day
                    repo.saveData(data.copy(entries = entries))
                }
                else -> {
                    // For amounts open app
                    val i = Intent(context, MainActivity::class.java).apply {
                        putExtra("log_habit", habitId)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(i)
                    return@launch
                }
            }

            // Refresh all widgets
            val ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, SteadyWidgetProvider::class.java))
            val updateIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(updateIntent)
        }
    }
}
