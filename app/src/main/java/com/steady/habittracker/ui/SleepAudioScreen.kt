package com.steady.habittracker.ui

import android.media.MediaPlayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.steady.habittracker.data.SleepAudioEvent
import com.steady.habittracker.data.SleepNightSession
import com.steady.habittracker.sleepaudio.SleepAudioAnalytics
import com.steady.habittracker.sleepaudio.SleepAudioStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SleepNightDetailScreen(
    night: SleepNightSession,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playingId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                player?.release()
            } catch (_: Exception) {
            }
        }
    }

    fun stopPlayback() {
        try {
            player?.stop()
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        playingId = null
    }

    fun playEvent(ev: SleepAudioEvent) {
        stopPlayback()
        val file = SleepAudioStorage.resolveSegment(context, night.id, ev.segmentFile)
        if (!file.exists()) {
            error = "Audio file missing (pruned or failed write)"
            return
        }
        try {
            val mp = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                val seek = ev.offsetInSegmentMs.coerceIn(0, (duration - 500).coerceAtLeast(0))
                seekTo(seek)
                // Play event window + 1s pad
                val padEnd = (ev.durationMs + 1500).coerceAtLeast(1500)
                setOnCompletionListener {
                    stopPlayback()
                }
                start()
                // Auto-stop after event window
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (playingId == ev.id) stopPlayback()
                }, padEnd.toLong())
            }
            player = mp
            playingId = ev.id
            error = null
        } catch (e: Exception) {
            error = e.message ?: "Playback failed"
        }
    }

    fun playSegment(fileName: String) {
        stopPlayback()
        val file = SleepAudioStorage.resolveSegment(context, night.id, fileName)
        if (!file.exists()) {
            error = "Segment missing"
            return
        }
        try {
            val mp = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener { stopPlayback() }
                start()
            }
            player = mp
            playingId = fileName
            error = null
        } catch (e: Exception) {
            error = e.message ?: "Playback failed"
        }
    }

    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val events = remember(night) { night.events.sortedBy { it.startAt } }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = {
                stopPlayback()
                onBack()
            }) { Text("← Back", color = accent) }
            if (playingId != null) {
                TextButton(onClick = { stopPlayback() }) { Text("Stop", color = accent) }
            }
        }
        Text(
            "Sleep audio · ${night.wakeDate}",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            buildString {
                append("Quiet score ${night.quietScore}/100")
                append(" · ${night.eventCount} events")
                append(" · ${night.snoreLikeCount} snore-like")
                append(" · ${"%.1f".format(night.loudMinutes)} loud min")
                append(" · ${night.codec}")
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("Events", fontWeight = FontWeight.SemiBold, color = accent, fontSize = 14.sp)
                Text(
                    "Tap to play from that moment in the OGG segment (heuristic snore labels).",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (events.isEmpty()) {
                item {
                    Text(
                        "No loud events detected this night.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }
            items(events, key = { it.id }) { ev ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { playEvent(ev) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (playingId == ev.id)
                            accent.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(
                            SleepAudioAnalytics.kindLabel(ev.kind),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "${timeFmt.format(Date(ev.startAt))} · ${ev.durationMs} ms · loudness ${ev.loudness}% · ${ev.segmentFile}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (playingId == ev.id) "Playing…" else "Tap to play",
                            fontSize = 11.sp,
                            color = accent
                        )
                    }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text("Segments", fontWeight = FontWeight.SemiBold, color = accent, fontSize = 14.sp)
            }
            items(night.segmentFiles) { name ->
                Text(
                    name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { playSegment(name) }
                        .padding(vertical = 6.dp),
                    fontSize = 12.sp,
                    color = if (playingId == name) accent else MaterialTheme.colorScheme.onSurface
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
