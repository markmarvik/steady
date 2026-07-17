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
                var data = repo.appDataFlow.first()
                val today = HabitDomain.getToday()

                val decision = HabitDomain.decideReminder(
                    data = data,
                    groupId = groupId,
                    groupName = name,
                    today = today
                )

                if (decision.show) {
                    val notifId = (reminderId ?: groupId ?: "steady").hashCode()
                    NotificationHelper.showReminder(
                        appContext,
                        decision.title,
                        decision.body,
                        groupId,
                        notifId
                    )
                    data = HabitDomain.withRecordedNotificationFire(data, today)
                    repo.saveData(data)
                }

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
