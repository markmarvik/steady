package com.steady.habittracker.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.steady.habittracker.data.AppData
import com.steady.habittracker.data.HabitDomain
import com.steady.habittracker.data.Reminder
import java.util.Calendar

object AlarmScheduler {

    fun scheduleAll(context: Context, data: AppData) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Cancel everything first so disabled / master-off is honored
        data.reminders.forEach { r ->
            am.cancel(makePending(context, r.id))
        }
        cancelSpecial(context, am, SpecialAlarmIds.MOTIVATIONAL_QUOTE)
        cancelSpecial(context, am, SpecialAlarmIds.RANDOM_CHECKIN)
        cancelSpecial(context, am, SpecialAlarmIds.MISSED_HABITS)
        data.habits.forEach { h ->
            cancelSpecial(context, am, SpecialAlarmIds.habitReminderId(h.id))
        }
        if (!data.remindersMasterEnabled) return
        data.reminders.filter { it.enabled }.forEach { r ->
            scheduleOne(context, am, r, data)
        }
        // Daily motivational quotes (#32) — independent of group reminders list
        if (data.notificationPrefs.motivationalQuotesEnabled) {
            scheduleSpecial(
                context, am, SpecialAlarmIds.MOTIVATIONAL_QUOTE,
                data.notificationPrefs.motivationalQuotesTime,
                kind = "quote",
                data = data
            )
        }
        // Random ESM check-ins (#36) — schedule next window
        if (data.notificationPrefs.randomCheckInsEnabled) {
            scheduleRandomCheckIn(context, am, data)
        }
        // Missed-habit evening pass (#30)
        if (data.notificationPrefs.missedHabitReminders) {
            scheduleSpecial(
                context, am, SpecialAlarmIds.MISSED_HABITS,
                "20:00",
                kind = "missed",
                data = data
            )
        }
        // Per-habit reminders (#30)
        data.habits.filter { !it.archived && it.habitReminder.enabled }.forEach { h ->
            scheduleSpecial(
                context, am, SpecialAlarmIds.habitReminderId(h.id),
                h.habitReminder.time,
                kind = "habit",
                extraId = h.id,
                name = h.name,
                data = data
            )
        }
    }

    private fun cancelSpecial(context: Context, am: AlarmManager, id: String) {
        am.cancel(makePending(context, id))
    }

    private fun scheduleSpecial(
        context: Context,
        am: AlarmManager,
        id: String,
        timeHhMm: String,
        kind: String,
        extraId: String? = null,
        name: String = "",
        data: AppData?
    ) {
        val synthetic = Reminder(
            id = id,
            groupId = null,
            time = timeHhMm,
            days = (1..7).toSet(),
            enabled = true
        )
        val pi = makePending(context, id, groupId = extraId, name = name, kind = kind)
        val triggerAt = computeNextTriggerMillis(synthetic, data = data)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun scheduleRandomCheckIn(context: Context, am: AlarmManager, data: AppData) {
        val prefs = data.notificationPrefs
        val spacingMin = when (prefs.randomCheckInFrequency.lowercase()) {
            "low" -> 120
            "high" -> 45
            else -> 75
        }
        val jitter = (0 until (spacingMin / 3).coerceAtLeast(5)).random()
        val last = prefs.lastRandomCheckInAt
        val now = System.currentTimeMillis()
        val earliest = if (last > 0) last + (spacingMin + jitter) * 60_000L else now + 30 * 60_000L
        var triggerAt = earliest.coerceAtLeast(now + 15 * 60_000L)
        // Push into active hours (avoid quiet)
        triggerAt = HabitDomain.pushPastQuietHours(triggerAt, prefs)
        val pi = makePending(context, SpecialAlarmIds.RANDOM_CHECKIN, kind = "checkin")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun scheduleAll(context: Context, reminders: List<Reminder>) {
        // Legacy entry points (boot without full AppData master flag) — assume master on
        scheduleAll(context, AppData(reminders = reminders, remindersMasterEnabled = true))
    }

    fun cancelAll(context: Context, reminders: List<Reminder>) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        reminders.forEach { r ->
            am.cancel(makePending(context, r.id))
        }
    }

    fun scheduleOne(context: Context, r: Reminder, data: AppData? = null) {
        if (data != null && !data.remindersMasterEnabled) return
        if (!r.enabled) return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        scheduleOne(context, am, r, data)
    }

    fun cancelOne(context: Context, reminderId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(makePending(context, reminderId))
    }

    private fun scheduleOne(context: Context, am: AlarmManager, r: Reminder, data: AppData?) {
        val groupName = resolveGroupName(r, data)
        val pi = makePending(context, r.id, r.groupId, groupName)
        val triggerAt = computeNextTriggerMillis(r, data = data)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun resolveGroupName(r: Reminder, data: AppData?): String {
        if (r.groupId == null) return "Daily Review"
        val name = data?.groups?.firstOrNull { it.id == r.groupId }?.name
        return name ?: "Habits"
    }

    private fun makePending(
        context: Context,
        id: String,
        groupId: String? = null,
        name: String = "",
        kind: String = "group"
    ): PendingIntent {
        val i = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("reminderId", id)
            putExtra("groupId", groupId)
            putExtra("name", name)
            putExtra("kind", kind)
        }
        return PendingIntent.getBroadcast(
            context, id.hashCode(), i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /**
     * Next fire time for [r], honoring HH:mm (optionally adaptive) and weekdays (1=Mon … 7=Sun).
     * When [data] is provided, applies smart timing + quiet hours.
     */
    fun computeNextTriggerMillis(
        r: Reminder,
        nowMillis: Long = System.currentTimeMillis(),
        data: AppData? = null
    ): Long {
        val effectiveTime = if (data != null) {
            HabitDomain.resolveEffectiveReminderTime(r, data)
        } else {
            r.time
        }
        val parts = effectiveTime.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 30
        val days = if (r.days.isEmpty()) (1..7).toSet() else r.days

        var triggerAt = 0L
        // Search up to 8 days ahead for next matching weekday + time strictly after now
        for (offset in 0..8) {
            val candidate = Calendar.getInstance()
            candidate.timeInMillis = nowMillis
            candidate.add(Calendar.DAY_OF_YEAR, offset)
            candidate.set(Calendar.HOUR_OF_DAY, hour)
            candidate.set(Calendar.MINUTE, minute)
            candidate.set(Calendar.SECOND, 0)
            candidate.set(Calendar.MILLISECOND, 0)

            // Calendar.DAY_OF_WEEK: Sun=1 … Sat=7 → convert to ISO 1=Mon … 7=Sun
            val isoDow = when (candidate.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> 1
                Calendar.TUESDAY -> 2
                Calendar.WEDNESDAY -> 3
                Calendar.THURSDAY -> 4
                Calendar.FRIDAY -> 5
                Calendar.SATURDAY -> 6
                else -> 7 // Sunday
            }
            if (isoDow !in days) continue
            if (candidate.timeInMillis > nowMillis) {
                triggerAt = candidate.timeInMillis
                break
            }
        }

        if (triggerAt == 0L) {
            // Fallback: tomorrow at time
            val cal = Calendar.getInstance()
            cal.timeInMillis = nowMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            triggerAt = cal.timeInMillis
        }

        if (data != null) {
            triggerAt = HabitDomain.pushPastQuietHours(triggerAt, data.notificationPrefs)
        }
        return triggerAt
    }
}
