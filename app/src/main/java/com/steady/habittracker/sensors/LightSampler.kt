package com.steady.habittracker.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * One-shot ambient light sample (lux). No special permission.
 */
object LightSampler {

    fun isAvailable(context: Context): Boolean {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        return sm.getDefaultSensor(Sensor.TYPE_LIGHT) != null
    }

    /**
     * Average lux over a short window (~1.2s). Returns null if no sensor or timeout.
     */
    suspend fun sampleAverageLux(context: Context, durationMs: Long = 1200L): Float? {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_LIGHT) ?: return null
        return suspendCancellableCoroutine { cont ->
            val readings = mutableListOf<Float>()
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    val v = event?.values?.firstOrNull() ?: return
                    if (v >= 0f) readings.add(v)
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val finish = Runnable {
                sm.unregisterListener(listener)
                if (cont.isActive) {
                    if (readings.isEmpty()) cont.resume(null)
                    else cont.resume(readings.average().toFloat())
                }
            }
            handler.postDelayed(finish, durationMs)
            cont.invokeOnCancellation {
                handler.removeCallbacks(finish)
                sm.unregisterListener(listener)
            }
        }
    }

    fun formatLux(lux: Float): String =
        if (lux >= 100f) "${lux.roundToInt()} lux" else String.format("%.1f lux", lux)
}
