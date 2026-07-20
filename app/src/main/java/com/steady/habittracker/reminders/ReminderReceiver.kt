package com.steady.habittracker.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.steady.habittracker.data.AndroidHabitRepository
import com.steady.habittracker.data.HabitDomain
import com.steady.habittracker.data.MotivationalQuotes
import com.steady.habittracker.data.withNotificationPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra("reminderId")
        val groupId = intent.getStringExtra("groupId")
        val name = intent.getStringExtra("name") ?: "Steady"
        val kind = intent.getStringExtra("kind") ?: "group"

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = AndroidHabitRepository(appContext)
                var data = repo.appDataFlow.first()
                val today = HabitDomain.getToday()
                val strength = data.notificationPrefs.reminderStrength

                when (kind) {
                    "quote" -> {
                        if (data.remindersMasterEnabled && data.notificationPrefs.motivationalQuotesEnabled) {
                            val q = MotivationalQuotes.forToday(LocalDate.parse(today))
                            NotificationHelper.showReminder(
                                appContext,
                                "Steady · Consistency",
                                "\"${q.text}\" — ${q.attribution}",
                                groupId = null,
                                notificationId = SpecialAlarmIds.MOTIVATIONAL_QUOTE.hashCode(),
                                strength = strength,
                                openCapture = false
                            )
                            data = HabitDomain.withRecordedNotificationFire(data, today)
                            repo.saveData(data)
                        }
                    }
                    "checkin" -> {
                        if (data.remindersMasterEnabled && data.notificationPrefs.randomCheckInsEnabled) {
                            val prompts = listOf(
                                "What are you working on right now?",
                                "Quick check-in: Energy level? Any distractions?",
                                "Productivity nudge: Are you on path?",
                                "Pause — capture one thought or idea.",
                                "Awareness: still aligned with today's priorities?"
                            )
                            NotificationHelper.showReminder(
                                appContext,
                                "Steady · Check-in",
                                prompts.random(),
                                groupId = null,
                                notificationId = SpecialAlarmIds.RANDOM_CHECKIN.hashCode(),
                                strength = strength,
                                openCapture = true
                            )
                            data = data.withNotificationPrefs(
                                data.notificationPrefs.copy(lastRandomCheckInAt = System.currentTimeMillis())
                            )
                            data = HabitDomain.withRecordedNotificationFire(data, today)
                            repo.saveData(data)
                        }
                    }
                    "missed" -> {
                        if (data.remindersMasterEnabled && data.notificationPrefs.missedHabitReminders) {
                            val pending = HabitDomain.habitsDueOn(data, LocalDate.parse(today))
                                .count { h -> HabitDomain.isPendingEntry(data.entries[today]?.get(h.id)) }
                            if (pending > 0) {
                                NotificationHelper.showReminder(
                                    appContext,
                                    "Steady · Still open",
                                    "$pending habit(s) still pending today — a small finish counts.",
                                    groupId = null,
                                    notificationId = SpecialAlarmIds.MISSED_HABITS.hashCode(),
                                    strength = strength
                                )
                                data = HabitDomain.withRecordedNotificationFire(data, today)
                                repo.saveData(data)
                            }
                        }
                    }
                    "habit" -> {
                        val habitId = groupId
                        val habit = data.habits.firstOrNull { it.id == habitId }
                        if (habit != null && habit.habitReminder.enabled && data.remindersMasterEnabled) {
                            val pending = HabitDomain.isPendingEntry(data.entries[today]?.get(habit.id))
                            if (pending) {
                                NotificationHelper.showReminder(
                                    appContext,
                                    habit.name,
                                    "Time for ${habit.name}",
                                    groupId = habit.groupId,
                                    notificationId = SpecialAlarmIds.habitReminderId(habit.id).hashCode(),
                                    strength = habit.habitReminder.strength
                                )
                                data = HabitDomain.withRecordedNotificationFire(data, today)
                                repo.saveData(data)
                            }
                        }
                    }
                    else -> {
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
                                notifId,
                                strength = strength,
                                ongoing = data.notificationPrefs.requireExplicitDismiss
                            )
                            data = HabitDomain.withRecordedNotificationFire(data, today)
                            repo.saveData(data)
                        }
                    }
                }

                if (data.remindersMasterEnabled) {
                    when (kind) {
                        "quote", "checkin", "missed", "habit" ->
                            AlarmScheduler.scheduleAll(appContext, data)
                        else -> {
                            val reminder = data.reminders.firstOrNull { it.id == reminderId }
                                ?: data.reminders.firstOrNull { it.groupId == groupId }
                            if (reminder != null && reminder.enabled) {
                                AlarmScheduler.scheduleOne(appContext, reminder, data)
                            }
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
