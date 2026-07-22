package com.steady.habittracker.web

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.steady.habittracker.MainActivity
import com.steady.habittracker.data.AndroidHabitRepository
import com.steady.habittracker.data.LocalWebPrefs
import com.steady.habittracker.data.withLocalWebPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Foreground service that keeps the LAN web server alive while enabled.
 * Prefs are passed via Intent extras so we never race DataStore (enable →
 * start service before save completes used to immediately self-stop).
 */
class LocalWebService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var autoOffRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                cancelAutoOff()
                LocalWebServer.stop()
                persistDisabled()
                stopForegroundSafe()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_AUTO_OFF -> {
                Log.i(TAG, "Auto-off timer fired")
                cancelAutoOff()
                LocalWebServer.stop()
                persistDisabled()
                stopForegroundSafe()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                // Promote to FGS immediately (Android 8+ timeout if delayed)
                if (!enterForeground("Starting…")) {
                    LocalWebServer.markFailed("Could not start foreground service (notification?)")
                    stopSelf()
                    return START_NOT_STICKY
                }

                val prefs = resolvePrefs(intent)
                if (!prefs.enabled) {
                    Log.w(TAG, "Start requested but prefs.enabled=false — stopping")
                    LocalWebServer.stop()
                    stopForegroundSafe()
                    stopSelf()
                    return START_NOT_STICKY
                }

                scope.launch {
                    val ok = try {
                        LocalWebServer.start(applicationContext, prefs)
                    } catch (e: Exception) {
                        Log.e(TAG, "start threw", e)
                        LocalWebServer.markFailed(e.message ?: e.javaClass.simpleName)
                        false
                    }

                    val text = if (ok) {
                        val urls = LocalWebServer.httpUrls()
                        val base = urls.firstOrNull() ?: LocalWebServer.statusMessage
                        val remain = LocalWebServer.autoOffRemainingLabel()
                        if (remain != null) "$base · auto-off $remain" else base
                    } else {
                        "Failed: ${LocalWebServer.lastError ?: "unknown"}"
                    }
                    Log.i(TAG, "Server start ok=$ok · ${LocalWebServer.statusMessage}")
                    updateNotification(text)

                    if (ok) {
                        scheduleAutoOff(prefs)
                    } else {
                        // Keep error visible briefly, then tear down FGS
                        mainHandler.postDelayed({
                            stopForegroundSafe()
                            stopSelf()
                        }, 4_000L)
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        cancelAutoOff()
        LocalWebServer.stop()
        super.onDestroy()
    }

    /**
     * Prefer Intent extras (authoritative for this start). Fall back to
     * in-memory prefsRef, then DataStore, then a safe enabled default.
     */
    private fun resolvePrefs(intent: Intent?): LocalWebPrefs {
        if (intent != null && intent.hasExtra(EXTRA_ENABLED)) {
            // Merge with stored prefs for list fields not always in extras
            val stored = try {
                runBlocking {
                    AndroidHabitRepository(applicationContext).appDataFlow.first().localWebPrefs
                }
            } catch (_: Exception) {
                LocalWebPrefs()
            }
            return stored.copy(
                enabled = intent.getBooleanExtra(EXTRA_ENABLED, true),
                port = intent.getIntExtra(EXTRA_PORT, stored.port).coerceIn(1024, 65534),
                pin = intent.getStringExtra(EXTRA_PIN) ?: stored.pin,
                httpsEnabled = intent.getBooleanExtra(EXTRA_HTTPS, stored.httpsEnabled),
                autoOffMinutes = intent.getIntExtra(EXTRA_AUTO_OFF_MIN, stored.autoOffMinutes)
                    .coerceIn(0, 24 * 60),
                autoOffAtEpochMs = intent.getLongExtra(EXTRA_AUTO_OFF_AT, stored.autoOffAtEpochMs),
                autoStartedByWifi = intent.getBooleanExtra(EXTRA_AUTO_WIFI, stored.autoStartedByWifi)
            )
        }
        LocalWebServer.currentPrefs()?.let { if (it.enabled) return it }
        return try {
            runBlocking {
                AndroidHabitRepository(applicationContext).appDataFlow.first().localWebPrefs
            }
        } catch (_: Exception) {
            LocalWebPrefs(enabled = true)
        }
    }

    private fun scheduleAutoOff(prefs: LocalWebPrefs) {
        cancelAutoOff()
        val onTrusted = WifiWebMonitor.isOnTrustedWifi(this, prefs)
        val defaultMins = prefs.effectiveAutoOffMinutes(onTrusted)
        val deadline = when {
            prefs.autoOffAtEpochMs > 0L -> prefs.autoOffAtEpochMs
            defaultMins > 0 -> System.currentTimeMillis() + defaultMins * 60_000L
            else -> 0L
        }
        if (deadline <= 0L) {
            LocalWebServer.setAutoOffDeadline(0L)
            return
        }
        val delay = (deadline - System.currentTimeMillis()).coerceAtLeast(500L)
        LocalWebServer.setAutoOffDeadline(deadline)
        val r = Runnable {
            val i = Intent(this, LocalWebService::class.java).setAction(ACTION_AUTO_OFF)
            try {
                startService(i)
            } catch (e: Exception) {
                Log.e(TAG, "auto-off start failed", e)
                LocalWebServer.stop()
                persistDisabled()
                stopForegroundSafe()
                stopSelf()
            }
        }
        autoOffRunnable = r
        mainHandler.postDelayed(r, delay)
        Log.i(TAG, "Auto-off in ${delay / 1000}s (deadline=$deadline)")
        // Refresh notification countdown every 30s
        mainHandler.post(object : Runnable {
            override fun run() {
                if (!LocalWebServer.isRunning()) return
                val remain = LocalWebServer.autoOffRemainingLabel()
                val base = LocalWebServer.httpUrls().firstOrNull()
                    ?: LocalWebServer.statusMessage
                val text = if (remain != null) "$base · auto-off $remain" else base
                updateNotification(text)
                if (remain != null) {
                    mainHandler.postDelayed(this, 30_000L)
                }
            }
        })
    }

    private fun cancelAutoOff() {
        autoOffRunnable?.let { mainHandler.removeCallbacks(it) }
        autoOffRunnable = null
        LocalWebServer.setAutoOffDeadline(0L)
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun persistDisabled() {
        scope.launch {
            try {
                val repo = AndroidHabitRepository(applicationContext)
                val data = repo.appDataFlow.first()
                val next = data.localWebPrefs.copy(
                    enabled = false,
                    autoOffAtEpochMs = 0L,
                    autoStartedByWifi = false
                )
                repo.saveData(data.withLocalWebPrefs(next))
            } catch (e: Exception) {
                Log.e(TAG, "persistDisabled failed", e)
            }
        }
    }

    private fun enterForeground(content: String): Boolean {
        return try {
            ensureChannel()
            val notif = buildNotification(content)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIF_ID,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIF_ID, notif)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            try {
                // Fallback without type (older / restricted devices)
                startForeground(NOTIF_ID, buildNotification(content))
                true
            } catch (e2: Exception) {
                Log.e(TAG, "startForeground fallback failed", e2)
                false
            }
        }
    }

    private fun stopForegroundSafe() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) { }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Local web server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "LAN dashboard for Steady (desktop access)"
            }
            mgr?.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(content: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, LocalWebService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentTitle("Steady web server")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(0, "Stop", stop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(content: String) {
        val mgr = getSystemService(NotificationManager::class.java)
        mgr?.notify(NOTIF_ID, buildNotification(content))
    }

    companion object {
        private const val TAG = "SteadyWebService"
        private const val CHANNEL_ID = "steady_local_web"
        private const val NOTIF_ID = 8842
        const val ACTION_STOP = "com.steady.habittracker.web.STOP"
        const val ACTION_START = "com.steady.habittracker.web.START"
        const val ACTION_AUTO_OFF = "com.steady.habittracker.web.AUTO_OFF"

        const val EXTRA_ENABLED = "enabled"
        const val EXTRA_PORT = "port"
        const val EXTRA_PIN = "pin"
        const val EXTRA_HTTPS = "https"
        const val EXTRA_AUTO_OFF_MIN = "auto_off_min"
        const val EXTRA_AUTO_OFF_AT = "auto_off_at"
        const val EXTRA_AUTO_WIFI = "auto_wifi"

        fun start(context: Context, prefs: LocalWebPrefs = LocalWebPrefs(enabled = true)) {
            val app = context.applicationContext
            val onTrusted = WifiWebMonitor.isOnTrustedWifi(app, prefs)
            val mins = prefs.effectiveAutoOffMinutes(onTrusted)
            val withDeadline = if (prefs.enabled && mins > 0 && prefs.autoOffAtEpochMs <= 0L) {
                prefs.copy(autoOffAtEpochMs = System.currentTimeMillis() + mins * 60_000L)
            } else {
                prefs
            }
            LocalWebServer.rememberPrefs(withDeadline)
            val i = Intent(app, LocalWebService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_ENABLED, withDeadline.enabled)
                .putExtra(EXTRA_PORT, withDeadline.port)
                .putExtra(EXTRA_PIN, withDeadline.pin)
                .putExtra(EXTRA_HTTPS, withDeadline.httpsEnabled)
                .putExtra(EXTRA_AUTO_OFF_MIN, withDeadline.autoOffMinutes)
                .putExtra(EXTRA_AUTO_OFF_AT, withDeadline.autoOffAtEpochMs)
                .putExtra(EXTRA_AUTO_WIFI, withDeadline.autoStartedByWifi)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    app.startForegroundService(i)
                } else {
                    app.startService(i)
                }
            } catch (e: Exception) {
                Log.e(TAG, "startForegroundService failed", e)
                LocalWebServer.markFailed(e.message ?: "startForegroundService failed")
            }
        }

        fun stop(context: Context) {
            val app = context.applicationContext
            val i = Intent(app, LocalWebService::class.java).setAction(ACTION_STOP)
            try {
                app.startService(i)
            } catch (_: Exception) {
                try {
                    app.stopService(Intent(app, LocalWebService::class.java))
                } catch (_: Exception) { }
                LocalWebServer.stop()
            }
        }

        /** Stop then start with a short delay so the port is released. */
        fun restart(context: Context, prefs: LocalWebPrefs) {
            val app = context.applicationContext
            LocalWebServer.stop()
            stop(app)
            Handler(Looper.getMainLooper()).postDelayed({
                start(
                    app,
                    prefs.copy(
                        enabled = true,
                        autoOffAtEpochMs = if (prefs.autoOffMinutes > 0) {
                            System.currentTimeMillis() + prefs.autoOffMinutes * 60_000L
                        } else {
                            0L
                        }
                    )
                )
            }, 400L)
        }
    }
}
