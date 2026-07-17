package com.steady.habittracker.sleepaudio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.steady.habittracker.MainActivity
import com.steady.habittracker.data.AndroidHabitRepository
import com.steady.habittracker.data.HabitDomain
import com.steady.habittracker.data.HabitEntry
import com.steady.habittracker.data.HabitType
import com.steady.habittracker.data.SleepAudioEvent
import com.steady.habittracker.data.SleepNightSession
import com.steady.habittracker.data.withUpdatedEntry
import com.steady.habittracker.data.withUpsertedSleepNight
import com.steady.habittracker.widget.WidgetRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground overnight recorder: OGG/Opus (or 3GP AMR fallback) segments + amplitude-based
 * loud/snore event detection via [MediaRecorder.getMaxAmplitude].
 */
class SleepAudioService : Service() {

    private val running = AtomicBoolean(false)
    private var wakeLock: PowerManager.WakeLock? = null
    private var recorder: MediaRecorder? = null
    private var sessionId: String = ""
    private var segmentIndex = 0
    private var segmentStartAt = 0L
    private var currentSegmentName = ""
    private var codecLabel = "ogg/opus"
    private var fileExt = "ogg"
    private val samples = mutableListOf<SleepAudioAnalytics.AmpSample>()
    private val events = mutableListOf<SleepAudioEvent>()
    private val segmentFiles = mutableListOf<String>()
    private var sessionStartedAt = 0L
    private var loudThreshold = 4000
    private var minEventMs = 600
    private var maxEventMs = 12000
    private var segmentMinutes = 15
    private var requireCharging = true
    private var powerReceiverRegistered = false
    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!running.get() || !requireCharging) return
            when (intent?.action) {
                Intent.ACTION_POWER_DISCONNECTED -> {
                    updateNotification("Stopped — unplugged (charging required)")
                    stopRecordingAndPersist()
                    stopSelf()
                }
            }
        }
    }
    private val handler = Handler(Looper.getMainLooper())
    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!running.get()) return
            try {
                val amp = recorder?.maxAmplitude ?: 0
                samples.add(SleepAudioAnalytics.AmpSample(System.currentTimeMillis(), amp))
                // Cap memory: keep ~2 minutes of samples at 4 Hz
                if (samples.size > 500) {
                    samples.subList(0, samples.size - 400).clear()
                }
            } catch (_: Exception) {
            }
            handler.postDelayed(this, POLL_MS)
        }
    }
    private val rotateRunnable = object : Runnable {
        override fun run() {
            if (!running.get()) return
            rotateSegment()
            handler.postDelayed(this, segmentMinutes * 60_000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRecordingAndPersist()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                if (running.get()) return START_STICKY
                beginStartSequence()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterPowerReceiver()
        if (running.get()) stopRecordingAndPersist()
        super.onDestroy()
    }

    private fun beginStartSequence() {
        // Must call startForeground promptly when started as FGS — show waiting notif first
        ensureChannel()
        val bootNotif = buildNotification("Starting sleep audio…")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIF_ID,
                bootNotif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIF_ID, bootNotif)
        }

        CoroutineScope(Dispatchers.IO).launch {
            var blockedReason: String? = null
            try {
                val repo = AndroidHabitRepository(applicationContext)
                val data = repo.appDataFlow.first()
                val prefs = data.sleepAudioPrefs
                loudThreshold = prefs.loudThreshold.coerceIn(500, 20000)
                minEventMs = prefs.minEventMs.coerceIn(200, 5000)
                maxEventMs = prefs.maxEventMs.coerceIn(2000, 60000)
                segmentMinutes = prefs.segmentMinutes.coerceIn(5, 30)
                requireCharging = prefs.requireCharging
                if (requireCharging && !ChargingStatus.isCharging(applicationContext)) {
                    blockedReason = "Plug in to record (charging required)"
                }
            } catch (_: Exception) {
            }
            handler.post {
                if (blockedReason != null) {
                    updateNotification(blockedReason)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@post
                }
                startRecording()
            }
        }
    }

    private fun startRecording() {
        running.set(true)
        sessionId = "night_${UUID.randomUUID().toString().take(10)}"
        sessionStartedAt = System.currentTimeMillis()
        segmentIndex = 0
        events.clear()
        segmentFiles.clear()
        samples.clear()

        updateNotification("Recording sleep audio…")
        acquireWakeLock()
        registerPowerReceiver()
        openSegment()
        handler.post(pollRunnable)
        handler.postDelayed(rotateRunnable, segmentMinutes * 60_000L)
        persistPartialSession(completed = false)
    }

    private fun registerPowerReceiver() {
        if (powerReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_POWER_CONNECTED)
        }
        registerReceiver(powerReceiver, filter)
        powerReceiverRegistered = true
    }

    private fun unregisterPowerReceiver() {
        if (!powerReceiverRegistered) return
        try {
            unregisterReceiver(powerReceiver)
        } catch (_: Exception) {
        }
        powerReceiverRegistered = false
    }

    private fun openSegment() {
        flushSegmentEvents()
        stopRecorderQuietly()
        val file = SleepAudioStorage.segmentFile(this, sessionId, segmentIndex, fileExt)
        currentSegmentName = file.name
        segmentStartAt = System.currentTimeMillis()
        samples.clear()

        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    mr.setOutputFormat(MediaRecorder.OutputFormat.OGG)
                    mr.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                    mr.setAudioEncodingBitRate(16_000)
                    mr.setAudioSamplingRate(16_000)
                    codecLabel = "ogg/opus"
                    fileExt = "ogg"
                } catch (_: Exception) {
                    configureFallback(mr)
                }
            } else {
                configureFallback(mr)
            }
            // Re-resolve file if extension changed
            val out = SleepAudioStorage.segmentFile(this, sessionId, segmentIndex, fileExt)
            currentSegmentName = out.name
            mr.setOutputFile(out.absolutePath)
            mr.prepare()
            mr.start()
            recorder = mr
            if (currentSegmentName !in segmentFiles) segmentFiles.add(currentSegmentName)
            segmentIndex++
        } catch (e: Exception) {
            try {
                mr.release()
            } catch (_: Exception) {
            }
            recorder = null
            updateNotification("Sleep audio failed: ${e.message?.take(40)}")
        }
    }

    private fun configureFallback(mr: MediaRecorder) {
        mr.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
        mr.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
        codecLabel = "3gp/amr"
        fileExt = "3gp"
    }

    private fun rotateSegment() {
        openSegment()
        persistPartialSession(completed = false)
        updateNotification("Recording… ${events.size} events")
    }

    private fun flushSegmentEvents() {
        if (samples.isEmpty() || currentSegmentName.isBlank()) return
        val detected = SleepAudioAnalytics.detectEvents(
            samples = samples.toList(),
            threshold = loudThreshold,
            minEventMs = minEventMs,
            maxEventMs = maxEventMs,
            segmentFile = currentSegmentName,
            segmentStartAt = segmentStartAt
        )
        events.addAll(detected)
        samples.clear()
    }

    private fun stopRecordingAndPersist() {
        if (!running.getAndSet(false)) return
        handler.removeCallbacks(pollRunnable)
        handler.removeCallbacks(rotateRunnable)
        unregisterPowerReceiver()
        flushSegmentEvents()
        stopRecorderQuietly()
        releaseWakeLock()
        val ended = System.currentTimeMillis()
        var night = SleepNightSession(
            id = sessionId,
            wakeDate = LocalDate.ofInstant(Instant.ofEpochMilli(ended), ZoneId.systemDefault()).toString(),
            startedAt = sessionStartedAt,
            endedAt = ended,
            events = events.toList(),
            segmentFiles = segmentFiles.toList(),
            codec = codecLabel,
            completed = true
        )
        night = SleepAudioAnalytics.computeNightStats(night)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = AndroidHabitRepository(applicationContext)
                var data = repo.appDataFlow.first()
                data = data.withUpsertedSleepNight(night)
                data = data.copy(
                    sleepNights = SleepAudioStorage.prune(
                        applicationContext,
                        data.sleepNights,
                        data.sleepAudioPrefs
                    )
                )
                // Optional linked habit quality scale
                val hid = data.sleepAudioPrefs.linkedHabitId
                if (hid != null) {
                    val habit = data.habits.find { it.id == hid }
                    if (habit != null && (habit.type == HabitType.SCALE_1_5 || habit.type == HabitType.CHECKBOX)) {
                        val today = HabitDomain.getToday()
                        val value = if (habit.type == HabitType.SCALE_1_5) {
                            SleepAudioAnalytics.quietScoreToScale(night.quietScore)
                        } else {
                            if (night.quietScore >= 60) 1.0 else 0.0
                        }
                        data = data.withUpdatedEntry(
                            today,
                            hid,
                            HabitEntry(
                                value = value,
                                note = "auto · sleep audio · quiet ${night.quietScore} · ${night.snoreLikeCount} snore-like",
                                loggedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
                repo.saveData(data)
                WidgetRenderer.updateAll(applicationContext, data)
            } catch (_: Exception) {
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun persistPartialSession(completed: Boolean) {
        val night = SleepAudioAnalytics.computeNightStats(
            SleepNightSession(
                id = sessionId,
                wakeDate = LocalDate.now().toString(),
                startedAt = sessionStartedAt,
                endedAt = if (completed) System.currentTimeMillis() else null,
                events = events.toList(),
                segmentFiles = segmentFiles.toList(),
                codec = codecLabel,
                completed = completed
            )
        )
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = AndroidHabitRepository(applicationContext)
                val data = repo.appDataFlow.first().withUpsertedSleepNight(night)
                repo.saveData(data)
            } catch (_: Exception) {
            }
        }
    }

    private fun stopRecorderQuietly() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "steady:sleep_audio").apply {
            setReferenceCounted(false)
            acquire(12 * 60 * 60 * 1000L) // max 12h
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Sleep audio",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Overnight snore / noise recording"
            }
            mgr.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                putExtra("open_sleep_audio", true)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, SleepAudioService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Steady sleep audio")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(0, "Stop", stop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    companion object {
        const val ACTION_START = "com.steady.habittracker.sleepaudio.START"
        const val ACTION_STOP = "com.steady.habittracker.sleepaudio.STOP"
        private const val CHANNEL_ID = "steady_sleep_audio"
        private const val NOTIF_ID = 7721
        private const val POLL_MS = 250L

        fun start(context: Context) {
            val i = Intent(context, SleepAudioService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, SleepAudioService::class.java).setAction(ACTION_STOP)
            )
        }

        fun isRecordingHint(context: Context): Boolean {
            // Best-effort: check notification channel / process — use shared pref flag from service
            return context.getSharedPreferences("sleep_audio_svc", MODE_PRIVATE)
                .getBoolean("recording", false)
        }
    }
}
