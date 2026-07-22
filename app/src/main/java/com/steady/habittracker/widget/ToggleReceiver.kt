package com.steady.habittracker.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.steady.habittracker.MainActivity
import com.steady.habittracker.data.AndroidHabitRepository
import com.steady.habittracker.data.HabitDomain
import com.steady.habittracker.data.HabitEntry
import com.steady.habittracker.data.HabitType
import com.steady.habittracker.data.entryFor
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
            ?.takeIf {
                it == ACTION_TOGGLE || it == ACTION_OPEN_APP || it == ACTION_OPEN_LOG ||
                    it == ACTION_OPEN_CAPTURE || it == ACTION_OPEN_METRIC_LOG
            }
            ?: intent.getStringExtra(EXTRA_ACTION)
            ?: ACTION_TOGGLE

        when (action) {
            ACTION_OPEN_APP -> {
                openApp(context)
                return
            }
            ACTION_OPEN_CAPTURE -> {
                openApp(context, "open_capture" to "1")
                return
            }
            ACTION_OPEN_METRIC_LOG -> {
                openApp(context, "open_log" to "1")
                return
            }
            ACTION_OPEN_LOG -> {
                val habitId = intent.getStringExtra(EXTRA_HABIT_ID)
                if (habitId.isNullOrBlank()) {
                    openApp(context)
                } else {
                    openApp(context, "log_habit" to habitId)
                }
                return
            }
        }

        val habitId = intent.getStringExtra(EXTRA_HABIT_ID)
        if (habitId.isNullOrBlank()) {
            Log.w(TAG, "Toggle missing habitId")
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = AndroidHabitRepository(appContext)
                val data = repo.appDataFlow.first()
                val habit = data.habits.find { it.id == habitId }
                if (habit == null) {
                    Log.w(TAG, "Habit not found: $habitId")
                    return@launch
                }

                // Notes need text — open app log dialog instead of silent complete
                if (habit.type == HabitType.NOTE) {
                    withContext(Dispatchers.Main) {
                        openApp(appContext, "log_habit" to habitId)
                    }
                    return@launch
                }

                val today = HabitDomain.logicalToday(data)
                val groupId = intent.getStringExtra(EXTRA_GROUP_ID)?.takeIf { it.isNotBlank() }
                val currentEntry = data.entryFor(today, habit, groupId)
                val alreadyDone =
                    (currentEntry?.value ?: 0.0) >= 0.5 && currentEntry?.skipped != true

                // Non-checkbox already complete: nothing to do (should not appear in list)
                if (alreadyDone && habit.type != HabitType.CHECKBOX) {
                    return@launch
                }

                val newVal = when {
                    alreadyDone -> 0.0 // checkbox untoggle
                    habit.type == HabitType.CHECKBOX -> 1.0
                    habit.target != null && (habit.target ?: 0.0) > 0 -> habit.target!!
                    habit.type == HabitType.SCALE_1_5 -> 3.0 // neutral mid-scale one-tap
                    else -> 1.0
                }
                val entry = HabitEntry(
                    value = newVal,
                    note = currentEntry?.note ?: "",
                    loggedAt = System.currentTimeMillis()
                )
                val updatedData = data.withUpdatedEntry(today, habitId, entry, groupId)

                // Persist first so a concurrent APPWIDGET_UPDATE cannot reload stale data
                repo.saveData(updatedData)

                // Then re-render from the same in-memory snapshot (main thread for AppWidgetManager)
                withContext(Dispatchers.Main) {
                    WidgetRenderer.updateAll(appContext, updatedData)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Toggle failed for $habitId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "SteadyWidget"

        const val ACTION_TOGGLE = "com.steady.habittracker.widget.ACTION_TOGGLE"
        const val ACTION_OPEN_APP = "com.steady.habittracker.widget.ACTION_OPEN_APP"
        const val ACTION_OPEN_LOG = "com.steady.habittracker.widget.ACTION_OPEN_LOG"
        const val ACTION_OPEN_CAPTURE = "com.steady.habittracker.widget.ACTION_OPEN_CAPTURE"
        const val ACTION_OPEN_METRIC_LOG = "com.steady.habittracker.widget.ACTION_OPEN_METRIC_LOG"
        const val EXTRA_HABIT_ID = "habitId"
        const val EXTRA_GROUP_ID = "groupId"
        const val EXTRA_ACTION = "action"

        fun openApp(context: Context, vararg extras: Pair<String, String>) {
            val i = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                extras.forEach { (k, v) -> putExtra(k, v) }
            }
            context.startActivity(i)
        }
    }
}
