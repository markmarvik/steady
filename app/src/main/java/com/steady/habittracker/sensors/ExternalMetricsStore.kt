package com.steady.habittracker.sensors

import android.content.Context
import android.content.SharedPreferences

/**
 * On-device cache for metrics pushed from Gadgetbridge, Tasker, or other apps
 * via [ExternalMetricsReceiver]. Keys are free-form (e.g. "steps", "hr_resting").
 *
 * Values are day-scoped (yyyy-MM-dd) so yesterday does not leak into today.
 */
object ExternalMetricsStore {
    private const val PREFS = "steady_external_metrics"
    private const val KEY_PREFIX = "m:"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun put(context: Context, date: String, key: String, value: Double) {
        val k = normalizeKey(key)
        if (k.isEmpty() || date.isBlank()) return
        prefs(context).edit()
            .putString("$KEY_PREFIX$date:$k", value.toString())
            .putLong("$KEY_PREFIX$date:$k:ts", System.currentTimeMillis())
            .apply()
    }

    fun get(context: Context, date: String, key: String): Double? {
        val k = normalizeKey(key)
        if (k.isEmpty()) return null
        val raw = prefs(context).getString("$KEY_PREFIX$date:$k", null) ?: return null
        return raw.toDoubleOrNull()
    }

    fun getSteps(context: Context, date: String): Double? =
        get(context, date, "steps")
            ?: get(context, date, "step_count")
            ?: get(context, date, "gadgetbridge_steps")

    fun normalizeKey(key: String): String =
        key.trim().lowercase().replace(' ', '_')
}
