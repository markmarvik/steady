package com.steady.habittracker.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.steady.habittracker.MainActivity
import com.steady.habittracker.data.AndroidHabitRepository
import com.steady.habittracker.data.HabitDomain
import com.steady.habittracker.data.HabitEntry
import com.steady.habittracker.data.HabitType
import com.steady.habittracker.data.withUpdatedEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Widget action receiver. Toggles / completes habits and refreshes widgets in-process
 * (no second APPWIDGET_UPDATE broadcast) for snappy disappear UX.
 */
class ToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Accept both explicit action and fill-in extras (some launchers strip action)
        val action = intent.action
            ?.takeIf { it == ACTION_TOGGLE || it == ACTION_OPEN_APP }
            ?: intent.getStringExtra(EXTRA_ACTION)
            ?: ACTION_TOGGLE

        if (action == ACTION_OPEN_APP) {
            val i = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(i)
            return
        }

        val habitId = intent.getStringExtra(EXTRA_HABIT_ID) ?: return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = AndroidHabitRepository(appContext)
                val data = repo.appDataFlow.first()
                val habit = data.habits.find { it.id == habitId }
                val today = HabitDomain.getToday()
                val currentEntry = data.entries[today]?.get(habitId)
                val alreadyDone =
                    (currentEntry?.value ?: 0.0) >= 0.5 && currentEntry?.skipped != true

                // Non-checkbox already complete: nothing to do (should not appear in list)
                if (alreadyDone && habit?.type != HabitType.CHECKBOX) {
                    return@launch
                }

                val newVal = when {
                    alreadyDone -> 0.0 // checkbox untoggle
                    habit?.type == HabitType.CHECKBOX -> 1.0
                    habit?.target != null && (habit.target ?: 0.0) > 0 -> habit.target!!
                    else -> 1.0
                }
                val entry = HabitEntry(
                    value = newVal,
                    note = currentEntry?.note ?: "",
                    loggedAt = System.currentTimeMillis()
                )
                val updatedData = data.withUpdatedEntry(today, habitId, entry)

                // Persist first so a concurrent APPWIDGET_UPDATE cannot reload stale data
                repo.saveData(updatedData)

                // Then re-render from the same in-memory snapshot (main thread for AppWidgetManager)
                withContext(Dispatchers.Main) {
                    WidgetRenderer.updateAll(appContext, updatedData)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.steady.habittracker.widget.ACTION_TOGGLE"
        const val ACTION_OPEN_APP = "com.steady.habittracker.widget.ACTION_OPEN_APP"
        const val EXTRA_HABIT_ID = "habitId"
        const val EXTRA_ACTION = "action"
    }
}
