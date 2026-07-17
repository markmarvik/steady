package com.steady.habittracker.sleepaudio

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.steady.habittracker.data.AndroidHabitRepository
import com.steady.habittracker.data.AppData
import com.steady.habittracker.data.HabitDomain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Schedules automatic start at bedTime and stop at wakeTime when sleep-audio is enabled.
 */
object SleepAudioScheduler {

    private const val REQ_START = 8801
    private const val REQ_STOP = 8802

    fun reschedule(context: Context, data: AppData) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(startPi(context))
        am.cancel(stopPi(context))
        if (!data.sleepAudioPrefs.enabled || !data.sleepAudioPrefs.scheduleWithSleep) return

        val bed = HabitDomain.parseTimeToMinutes(data.sleep.bedTime)
        val wake = HabitDomain.parseTimeToMinutes(data.sleep.wakeTime)
        scheduleAt(context, am, bed, startPi(context), start = true)
        scheduleAt(context, am, wake, stopPi(context), start = false)
    }

    private fun scheduleAt(
        context: Context,
        am: AlarmManager,
        minutesOfDay: Int,
        pi: PendingIntent,
        start: Boolean
    ) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, minutesOfDay / 60)
        cal.set(Calendar.MINUTE, minutesOfDay % 60)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        val trigger = cal.timeInMillis
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
        } catch (_: Exception) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }

    private fun startPi(context: Context): PendingIntent {
        val i = Intent(context, SleepAudioAlarmReceiver::class.java).setAction(ACTION_ALARM_START)
        return PendingIntent.getBroadcast(
            context, REQ_START, i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun stopPi(context: Context): PendingIntent {
        val i = Intent(context, SleepAudioAlarmReceiver::class.java).setAction(ACTION_ALARM_STOP)
        return PendingIntent.getBroadcast(
            context, REQ_STOP, i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    const val ACTION_ALARM_START = "com.steady.habittracker.sleepaudio.ALARM_START"
    const val ACTION_ALARM_STOP = "com.steady.habittracker.sleepaudio.ALARM_STOP"
}

class SleepAudioAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = AndroidHabitRepository(app)
                val data = repo.appDataFlow.first()
                when (intent?.action) {
                    SleepAudioScheduler.ACTION_ALARM_START -> {
                        if (data.sleepAudioPrefs.enabled) {
                            if (data.sleepAudioPrefs.requireCharging &&
                                !ChargingStatus.isCharging(app)
                            ) {
                                // Skip tonight until user plugs in; still chain next alarms
                            } else {
                                SleepAudioService.start(app)
                            }
                        }
                    }
                    SleepAudioScheduler.ACTION_ALARM_STOP -> {
                        SleepAudioService.stop(app)
                    }
                }
                // Chain next day's alarms
                SleepAudioScheduler.reschedule(app, data)
            } finally {
                pending.finish()
            }
        }
    }
}
