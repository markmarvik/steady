package com.steady.habittracker.sensors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Short ambient noise estimate via mic (opt-in RECORD_AUDIO).
 * Not calibrated dB SPL — relative “approx dB” for habit tracking only.
 */
object NoiseSampler {

    fun hasMicPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun isMicPresent(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)

    /**
     * Sample ~[durationMs] of mono 16-bit audio and return approximate dBFS-based level (0–100 scale).
     */
    suspend fun sampleApproxDb(context: Context, durationMs: Int = 1500): Double? =
        withContext(Dispatchers.IO) {
            if (!hasMicPermission(context) || !isMicPresent(context)) return@withContext null
            val sampleRate = 44100
            val channel = AudioFormat.CHANNEL_IN_MONO
            val encoding = AudioFormat.ENCODING_PCM_16BIT
            val minBuf = AudioRecord.getMinBufferSize(sampleRate, channel, encoding)
            if (minBuf <= 0) return@withContext null
            val recorder = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channel,
                    encoding,
                    minBuf * 2
                )
            } catch (_: Exception) {
                return@withContext null
            }
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                return@withContext null
            }
            try {
                val buf = ShortArray(minBuf)
                var sumSq = 0.0
                var count = 0
                recorder.startRecording()
                val deadline = System.currentTimeMillis() + durationMs
                while (System.currentTimeMillis() < deadline) {
                    val n = recorder.read(buf, 0, buf.size)
                    if (n > 0) {
                        for (i in 0 until n) {
                            val s = buf[i].toDouble()
                            sumSq += s * s
                            count++
                        }
                    }
                }
                recorder.stop()
                if (count == 0) return@withContext null
                val rms = sqrt(sumSq / count)
                // Map RMS of 16-bit audio to a rough 0–100 "approx dB" display scale
                val db = if (rms < 1.0) 0.0 else 20.0 * log10(rms)
                // Typical speech ~60–80 on this uncalibrated scale; clamp for habit use
                db.coerceIn(0.0, 100.0)
            } catch (_: Exception) {
                null
            } finally {
                try {
                    recorder.release()
                } catch (_: Exception) {
                }
            }
        }
}
