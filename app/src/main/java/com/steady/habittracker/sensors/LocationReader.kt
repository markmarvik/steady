package com.steady.habittracker.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * One-shot / last-known GPS for SENSOR_AUTO_READ blocks (#34).
 * Uses platform [LocationManager] (no Play Services dependency).
 * Privacy: local only; no continuous tracking.
 */
object LocationReader {

    data class Fix(
        val latitude: Double,
        val longitude: Double,
        val accuracyM: Float,
        val timeMs: Long,
        val provider: String
    )

    fun hasFineOrCoarse(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    fun lastKnown(context: Context): Fix? {
        if (!hasFineOrCoarse(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = buildList {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                lm.isProviderEnabled(LocationManager.FUSED_PROVIDER)
            ) {
                add(LocationManager.FUSED_PROVIDER)
            }
            add(LocationManager.PASSIVE_PROVIDER)
        }.distinct()
        var best: Location? = null
        for (p in providers) {
            try {
                val loc = lm.getLastKnownLocation(p) ?: continue
                if (best == null || loc.time > best.time) best = loc
            } catch (_: SecurityException) {
                return null
            } catch (_: Exception) {
                // ignore provider errors
            }
        }
        return best?.let {
            Fix(
                latitude = it.latitude,
                longitude = it.longitude,
                accuracyM = it.accuracy,
                timeMs = it.time,
                provider = it.provider ?: "unknown"
            )
        }
    }

    fun format(fix: Fix): String {
        val lat = String.format("%.5f", fix.latitude)
        val lon = String.format("%.5f", fix.longitude)
        val acc = if (fix.accuracyM > 0) " ±${fix.accuracyM.toInt()}m" else ""
        return "$lat,$lon$acc (${fix.provider})"
    }
}
