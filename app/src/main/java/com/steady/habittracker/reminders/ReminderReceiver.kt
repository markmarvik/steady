package com.steady.habittracker.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.steady.habittracker.data.AndroidHabitRepository
import com.steady.habittracker.data.HabitDomain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra("reminderId")
        val groupId = intent.getStringExtra("groupId")
        val name = intent.getStringExtra("name") ?: "Steady"

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = AndroidHabitRepository(appContext)
                val data = repo.appDataFlow.first()
                val today = HabitDomain.getToday()
                val entries = data.entries[today] ?: emptyMap()

                val todayDate = try {
                    java.time.LocalDate.parse(today)
                } catch (_: Exception) {
                    java.time.LocalDate.now()
                }
                val due = HabitDomain.habitsDueOn(data, todayDate)
                val pendingNames = due
                    .filter { groupId == null || it.groupId == groupId }
                    .filter { h ->
                        val e = entries[h.id]
                        (e?.value ?: 0.0) < 0.5 && e?.skipped != true
                    }
                    .map { it.name }

                val body = when {
                    pendingNames.isEmpty() -> "You're caught up — open Steady to review."
                    pendingNames.size <= 3 -> pendingNames.joinToString(" · ")
                    else -> pendingNames.take(3).joinToString(" · ") + " +${pendingNames.size - 3} more"
                }

                val title = if (name.isNotBlank() && name != "Daily") "$name time" else "Steady reminder"
                val notifId = (reminderId ?: groupId ?: "steady").hashCode()

                NotificationHelper.showReminder(
                    appContext,
                    title,
                    body,
                    groupId,
                    notifId
                )

                // Reschedule next occurrence (alarms are one-shot)
                if (data.remindersMasterEnabled) {
                    val reminder = data.reminders.firstOrNull { it.id == reminderId }
                        ?: data.reminders.firstOrNull { it.groupId == groupId }
                    if (reminder != null && reminder.enabled) {
                        AlarmScheduler.scheduleOne(appContext, reminder, data)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
