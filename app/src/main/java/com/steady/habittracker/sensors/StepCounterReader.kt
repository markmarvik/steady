package com.steady.habittracker.sensors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Phone hardware step counter (TYPE_STEP_COUNTER is cumulative since boot).
 * Day totals require anchoring; we store boot-day baseline in prefs.
 * Prefer Gadgetbridge / EXTERNAL for wearable steps.
 */
object StepCounterReader {
    private const val PREFS = "steady_step_counter"
    private const val KEY_BASELINE = "baseline"
    private const val KEY_BASELINE_DATE = "baseline_date"
    private const val KEY_LAST_RAW = "last_raw"

    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isAvailable(context: Context): Boolean {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        return sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
    }

    /**
     * Today's steps from hardware counter, or null if unavailable.
     */
    suspend fun todaySteps(context: Context, today: String): Int? {
        if (!hasPermission(context) || !isAvailable(context)) return null
        val raw = readRawSteps(context) ?: return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val baselineDate = prefs.getString(KEY_BASELINE_DATE, null)
        var baseline = prefs.getFloat(KEY_BASELINE, -1f)
        val lastRaw = prefs.getFloat(KEY_LAST_RAW, -1f)

        // Reboot detection: raw dropped
        if (lastRaw >= 0 && raw < lastRaw - 10f) {
            baseline = raw
            prefs.edit()
                .putFloat(KEY_BASELINE, baseline)
                .putString(KEY_BASELINE_DATE, today)
                .putFloat(KEY_LAST_RAW, raw)
                .apply()
            return 0
        }

        if (baselineDate != today || baseline < 0f) {
            // New day: baseline = current raw so today's count starts near 0
            // Keep partial day if we had last_raw from same boot yesterday
            val start = if (baselineDate != null && baselineDate != today && lastRaw >= 0) {
                // Day rollover without reboot: steps today = raw - last_raw_at_midnight approx
                // We only sample periodically; use raw as new baseline for clean day
                raw
            } else {
                raw
            }
            prefs.edit()
                .putFloat(KEY_BASELINE, start)
                .putString(KEY_BASELINE_DATE, today)
                .putFloat(KEY_LAST_RAW, raw)
                .apply()
            return 0
        }

        prefs.edit().putFloat(KEY_LAST_RAW, raw).apply()
        return (raw - baseline).toInt().coerceAtLeast(0)
    }

    private suspend fun readRawSteps(context: Context): Float? {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null
        return suspendCancellableCoroutine { cont ->
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    val v = event?.values?.firstOrNull()
                    sm.unregisterListener(this)
                    if (cont.isActive) cont.resume(v)
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val timeout = Runnable {
                sm.unregisterListener(listener)
                if (cont.isActive) cont.resume(null)
            }
            handler.postDelayed(timeout, 2500L)
            cont.invokeOnCancellation {
                handler.removeCallbacks(timeout)
                sm.unregisterListener(listener)
            }
        }
    }
}
