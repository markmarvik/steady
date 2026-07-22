package com.steady.habittracker.sensors.gadgetbridge

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.steady.habittracker.data.AndroidHabitRepository
import com.steady.habittracker.data.ExtensionType
import com.steady.habittracker.data.hasGadgetbridgeBlock
import com.steady.habittracker.reminders.NotificationHelper
import com.steady.habittracker.widget.WidgetRenderer
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Periodic poll of the Gadgetbridge auto-export database.
 * Interval comes from [com.steady.habittracker.data.GadgetbridgePrefs.pollIntervalMinutes]
 * (WorkManager minimum is 15 minutes).
 */
class GadgetbridgeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val repo = AndroidHabitRepository(applicationContext)
            val data = repo.appDataFlow.first()
            val prefs = data.gadgetbridgePrefs
            val active = prefs.enabled || data.hasGadgetbridgeBlock()
            if (!active) return Result.success()

            // Keep prefs.enabled in sync if only the habit block is on
            val base = if (!prefs.enabled && data.hasGadgetbridgeBlock()) {
                data.copy(gadgetbridgePrefs = prefs.copy(enabled = true, showHistoryFrames = true))
            } else data

            val result = GadgetbridgeImporter.importIfNeeded(applicationContext, base, force = false)
            if (result.data != data) {
                repo.saveData(result.data)
                WidgetRenderer.updateAll(applicationContext, result.data)
            }
            if (result.events.isNotEmpty() && result.data.gadgetbridgePrefs.notifyEvents) {
                notifyEvents(applicationContext, result.events)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "steady_gadgetbridge_sync"

        fun enqueue(context: Context, intervalMinutes: Int = 60) {
            val mins = intervalMinutes.coerceIn(15, 360).toLong()
            val req = PeriodicWorkRequestBuilder<GadgetbridgeWorker>(mins, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                req
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_NAME)
        }

        fun enqueueOneShot(context: Context) {
            val req = androidx.work.OneTimeWorkRequestBuilder<GadgetbridgeWorker>().build()
            WorkManager.getInstance(context.applicationContext).enqueue(req)
        }

        fun syncSchedule(context: Context, data: com.steady.habittracker.data.AppData) {
            val on = data.gadgetbridgePrefs.enabled ||
                data.habits.any { !it.archived && it.extensionType == ExtensionType.GADGETBRIDGE_SYNC }
            if (on) {
                enqueue(context, data.gadgetbridgePrefs.effectivePollMinutes())
            } else {
                cancel(context)
            }
        }

        private fun notifyEvents(context: Context, events: List<GadgetbridgeImporter.WearableEvent>) {
            // Collapse into one notification when many fire at once
            val top = events.take(4)
            val title = if (events.size == 1) top.first().title else "Wearable updates (${events.size})"
            val body = top.joinToString("\n") { "• ${it.body}" }
            NotificationHelper.showReminder(
                context = context,
                title = title,
                text = body,
                notificationId = 8840,
                strength = com.steady.habittracker.data.ReminderStrength.NORMAL
            )
        }
    }
}
