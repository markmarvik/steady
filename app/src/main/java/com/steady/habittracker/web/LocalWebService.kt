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
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.steady.habittracker.MainActivity
import com.steady.habittracker.data.AndroidHabitRepository
import com.steady.habittracker.data.LocalWebPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Foreground service that keeps the LAN web server alive while enabled.
 * Without this, Android often kills or never reliably binds the socket
 * when the UI thread only fires a daemon thread.
 */
class LocalWebService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                LocalWebServer.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                ensureChannel()
                val notif = buildNotification("Starting…")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIF_ID,
                        notif,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIF_ID, notif)
                }
                scope.launch {
                    val prefs = loadPrefs()
                    if (!prefs.enabled) {
                        LocalWebServer.stop()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        return@launch
                    }
                    val ok = LocalWebServer.start(applicationContext, prefs)
                    val text = if (ok) {
                        LocalWebServer.httpUrls().firstOrNull() ?: LocalWebServer.statusMessage
                    } else {
                        "Failed: ${LocalWebServer.lastError ?: "unknown"}"
                    }
                    Log.i(TAG, "Server start ok=$ok · ${LocalWebServer.statusMessage}")
                    updateNotification(text)
                    if (!ok) {
                        // Keep notification briefly so user sees the error, then stop
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        LocalWebServer.stop()
        super.onDestroy()
    }

    private fun loadPrefs(): LocalWebPrefs {
        return try {
            runBlocking {
                AndroidHabitRepository(applicationContext).appDataFlow.first().localWebPrefs
            }
        } catch (_: Exception) {
            LocalWebPrefs(enabled = true)
        }
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

        fun start(context: Context) {
            val i = Intent(context, LocalWebService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            val i = Intent(context, LocalWebService::class.java).setAction(ACTION_STOP)
            try {
                context.startService(i)
            } catch (_: Exception) {
                context.stopService(Intent(context, LocalWebService::class.java))
            }
        }
    }
}
