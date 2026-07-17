package com.steady.habittracker.sensors

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.steady.habittracker.data.AndroidHabitRepository
import com.steady.habittracker.data.HabitDomain
import com.steady.habittracker.widget.WidgetRenderer
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Periodic sensor sync (screen / light / noise / steps / external cache).
 */
class AutoLogWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val repo = AndroidHabitRepository(applicationContext)
            val data = repo.appDataFlow.first()
            if (!data.autoLogMasterEnabled) return Result.success()
            val result = AutoLogEngine.run(applicationContext, data, HabitDomain.getToday())
            if (result.data != data) {
                repo.saveData(result.data)
                WidgetRenderer.updateAll(applicationContext, result.data)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "steady_auto_log"

        fun enqueue(context: Context) {
            val req = PeriodicWorkRequestBuilder<AutoLogWorker>(6, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                req
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_NAME)
        }

        fun enqueueOneShot(context: Context) {
            val req = androidx.work.OneTimeWorkRequestBuilder<AutoLogWorker>().build()
            WorkManager.getInstance(context.applicationContext).enqueue(req)
        }
    }
}
