package com.steady.habittracker.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.steady.habittracker.data.Reminder
import java.util.*

object AlarmScheduler {
    fun scheduleAll(context: Context, reminders: List<Reminder>) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        reminders.filter { it.enabled }.forEach { r ->
            scheduleOne(context, am, r)
        }
    }

    fun cancelAll(context: Context, reminders: List<Reminder>) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        reminders.forEach { r ->
            val pi = makePending(context, r.id)
            am.cancel(pi)
        }
    }

    private fun scheduleOne(context: Context, am: AlarmManager, r: Reminder) {
        val pi = makePending(context, r.id, r.groupId, r.groupId ?: "Daily")
        val triggerAt = computeNext(r)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            // App should guide user; fall back to inexact
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun makePending(context: Context, id: String, groupId: String? = null, name: String = ""): PendingIntent {
        val i = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("reminderId", id)
            putExtra("groupId", groupId)
            putExtra("name", name)
        }
        return PendingIntent.getBroadcast(
            context, id.hashCode(), i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun computeNext(r: Reminder): Long {
        val cal = Calendar.getInstance()
        val parts = r.time.split(":")
        cal.set(Calendar.HOUR_OF_DAY, parts.getOrNull(0)?.toIntOrNull() ?: 8)
        cal.set(Calendar.MINUTE, parts.getOrNull(1)?.toIntOrNull() ?: 30)
        cal.set(Calendar.SECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        // naive weekday filter (real impl would loop to next matching day)
        return cal.timeInMillis
    }
}
