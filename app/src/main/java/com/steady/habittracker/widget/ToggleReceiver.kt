package com.steady.habittracker.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.steady.habittracker.MainActivity
import com.steady.habittracker.data.AndroidHabitRepository
import com.steady.habittracker.data.HabitDomain
import com.steady.habittracker.data.HabitEntry
import com.steady.habittracker.data.withUpdatedEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Widget action receiver. Toggles checkbox habits and refreshes widgets in-process
 * (no second APPWIDGET_UPDATE broadcast) for snappy disappear UX.
 */
class ToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val habitId = intent.getStringExtra("habitId") ?: return
        val action = intent.getStringExtra("action") ?: "TOGGLE"

        if (action != "TOGGLE") {
            val i = Intent(context, MainActivity::class.java).apply {
                putExtra("log_habit", habitId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(i)
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = AndroidHabitRepository(appContext)
                val data = repo.appDataFlow.first()
                val today = HabitDomain.getToday()
                val currentEntry = data.entries[today]?.get(habitId)
                val newVal = if ((currentEntry?.value ?: 0.0) >= 0.5) 0.0 else 1.0
                val entry = HabitEntry(
                    value = newVal,
                    note = currentEntry?.note ?: "",
                    loggedAt = System.currentTimeMillis()
                )
                val updatedData = data.withUpdatedEntry(today, habitId, entry)

                // Optimistic: re-render from in-memory updated data so the row vanishes ASAP
                WidgetRenderer.updateAll(appContext, updatedData)

                repo.saveData(updatedData)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
