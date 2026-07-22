package com.steady.habittracker.web

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.steady.habittracker.data.AndroidHabitRepository
import com.steady.habittracker.data.LocalWebPrefs
import com.steady.habittracker.data.withLocalWebPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Watches Wi‑Fi SSID and auto-starts / stops the LAN web server for trusted networks.
 * Requires a secure PIN when auto-start is enabled. SSID visibility needs location
 * or nearby-Wi‑Fi permission (GrapheneOS: grant Network / Nearby devices for Steady).
 */
object WifiWebMonitor {
    private const val TAG = "SteadyWifiWeb"
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var appContext: Context? = null

    fun start(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (!started.compareAndSet(false, true)) {
            evaluate("refresh")
            return
        }
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            started.set(false)
            return
        }
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = evaluate("available")
            override fun onLost(network: Network) = evaluate("lost")
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
                evaluate("caps")
        }
        callback = cb
        try {
            val req = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            cm.registerNetworkCallback(req, cb)
        } catch (e: Exception) {
            Log.e(TAG, "registerNetworkCallback failed", e)
            // Fallback: still try once
        }
        evaluate("init")
    }

    fun stop(context: Context) {
        val app = context.applicationContext
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        callback?.let {
            try {
                cm?.unregisterNetworkCallback(it)
            } catch (_: Exception) {
            }
        }
        callback = null
        started.set(false)
    }

    fun currentSsid(context: Context): String? {
        return try {
            @Suppress("DEPRECATION")
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            val info = wm?.connectionInfo ?: return null
            @Suppress("DEPRECATION")
            val raw = info.ssid ?: return null
            val cleaned = raw.trim().removePrefix("\"").removeSuffix("\"")
            if (cleaned.isBlank() || cleaned == "<unknown ssid>" || cleaned == "0x") null else cleaned
        } catch (_: Exception) {
            null
        }
    }

    fun isOnTrustedWifi(context: Context, prefs: LocalWebPrefs): Boolean {
        val ssid = currentSsid(context) ?: return false
        val trusted = prefs.trustedSsids.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        return ssid in trusted
    }

    private fun evaluate(reason: String) {
        val ctx = appContext ?: return
        scope.launch {
            try {
                val repo = AndroidHabitRepository(ctx)
                val data = repo.appDataFlow.first()
                var prefs = data.localWebPrefs
                val ssid = currentSsid(ctx)
                LocalWebServer.currentSsid = ssid
                val onTrusted = ssid != null &&
                    prefs.trustedSsids.map { it.trim() }.filter { it.isNotEmpty() }.any { it == ssid }

                Log.i(TAG, "evaluate($reason) ssid=$ssid onTrusted=$onTrusted enabled=${prefs.enabled} auto=${prefs.autoStartOnTrustedWifi}")

                if (prefs.canAutoStartOnWifi() && onTrusted && !prefs.enabled) {
                    val mins = prefs.trustedWifiAutoOffMinutes
                    val deadline = if (mins > 0) System.currentTimeMillis() + mins * 60_000L else 0L
                    prefs = prefs.copy(
                        enabled = true,
                        autoStartedByWifi = true,
                        autoOffAtEpochMs = deadline
                    )
                    repo.saveData(data.withLocalWebPrefs(prefs))
                    LocalWebServer.setEnabled(ctx, data.withLocalWebPrefs(prefs))
                    Log.i(TAG, "Auto-started on trusted Wi‑Fi $ssid")
                    return@launch
                }

                if (prefs.enabled && prefs.autoStartedByWifi && prefs.stopWhenLeavingTrustedWifi && !onTrusted) {
                    // Left trusted network — stop if we auto-started
                    prefs = prefs.copy(
                        enabled = false,
                        autoStartedByWifi = false,
                        autoOffAtEpochMs = 0L
                    )
                    repo.saveData(data.withLocalWebPrefs(prefs))
                    LocalWebServer.setEnabled(ctx, data.withLocalWebPrefs(prefs))
                    Log.i(TAG, "Auto-stopped after leaving trusted Wi‑Fi")
                    return@launch
                }

                // If already running on trusted Wi‑Fi, optionally extend deadline style is handled at start
            } catch (e: Exception) {
                Log.e(TAG, "evaluate failed", e)
            }
        }
    }
}
