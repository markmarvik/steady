package com.steady.habittracker.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.steady.habittracker.data.AndroidHabitRepository
import com.steady.habittracker.sensors.AutoLogWorker
import com.steady.habittracker.sleepaudio.SleepAudioScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val pendingResult = goAsync()
            val appContext = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repo = AndroidHabitRepository(appContext)
                    val data = repo.appDataFlow.first()
                    AlarmScheduler.scheduleAll(appContext, data)
                    if (data.autoLogMasterEnabled) {
                        AutoLogWorker.enqueue(appContext)
                    }
                    SleepAudioScheduler.reschedule(appContext, data)
                    com.steady.habittracker.sensors.gadgetbridge.GadgetbridgeWorker
                        .syncSchedule(appContext, data)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
