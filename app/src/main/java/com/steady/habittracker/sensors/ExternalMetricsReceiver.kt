package com.steady.habittracker.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.steady.habittracker.data.AndroidHabitRepository
import com.steady.habittracker.data.HabitDomain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Receives external metrics (Gadgetbridge automations, Tasker, etc.).
 *
 * Intent action: [ACTION]
 * Extras:
 * - `key` (String): metric name, default `steps`
 * - `value` (double/float/int/long/String)
 * - `date` (optional yyyy-MM-dd, default today)
 *
 * Example (adb):
 * ```
 * adb shell am broadcast -a com.steady.habittracker.ACTION_EXTERNAL_METRIC \
 *   --es key steps --ef value 8420
 * ```
 */
class ExternalMetricsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION && intent?.action != ACTION_LEGACY) return
        val key = intent.getStringExtra(EXTRA_KEY) ?: "steps"
        val value = readValue(intent) ?: return
        val date = intent.getStringExtra(EXTRA_DATE)?.takeIf { it.isNotBlank() }
            ?: HabitDomain.getToday()

        ExternalMetricsStore.put(context, date, key, value)

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = AndroidHabitRepository(appContext)
                val data = repo.appDataFlow.first()
                if (!data.autoLogMasterEnabled) return@launch
                val result = AutoLogEngine.run(appContext, data, date)
                if (result.data != data) {
                    repo.saveData(result.data)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun readValue(intent: Intent): Double? {
        if (intent.hasExtra(EXTRA_VALUE)) {
            // Prefer double; fall back through types Intent supports
            val d = intent.extras?.get(EXTRA_VALUE)
            return when (d) {
                is Double -> d
                is Float -> d.toDouble()
                is Int -> d.toDouble()
                is Long -> d.toDouble()
                is String -> d.toDoubleOrNull()
                is Number -> d.toDouble()
                else -> null
            }
        }
        return null
    }

    companion object {
        const val ACTION = "com.steady.habittracker.ACTION_EXTERNAL_METRIC"
        /** Alias for step-focused automations. */
        const val ACTION_LEGACY = "com.steady.habittracker.ACTION_EXTERNAL_STEPS"
        const val EXTRA_KEY = "key"
        const val EXTRA_VALUE = "value"
        const val EXTRA_DATE = "date"
    }
}
