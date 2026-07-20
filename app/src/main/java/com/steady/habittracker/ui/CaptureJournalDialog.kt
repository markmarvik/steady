package com.steady.habittracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.steady.habittracker.data.AppData
import com.steady.habittracker.data.CaptureItem
import com.steady.habittracker.data.CaptureTags
import com.steady.habittracker.data.journalCaptures
import com.steady.habittracker.data.reflectionCaptures
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class JournalFilter {
    ALL,
    REFLECTIONS,
    DONE_INBOX
}

/**
 * Browse archived / journal captures (memories, gratitude, thoughts, …)
 * and completed inbox items — separate from the action Inbox on Today.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaptureJournalDialog(
    appData: AppData,
    onDismiss: () -> Unit,
    onDelete: (String) -> Unit = {},
    onReopenToInbox: (String) -> Unit = {}
) {
    var filter by remember { mutableStateOf(JournalFilter.ALL) }
    var tagFilter by remember { mutableStateOf<String?>(null) }

    val allJournal = remember(appData.captures, appData.capturePrefs) {
        appData.journalCaptures()
    }
    val reflections = remember(appData.captures, appData.capturePrefs) {
        appData.reflectionCaptures()
    }
    val doneInbox = remember(appData.captures, appData.capturePrefs) {
        val inbox = appData.capturePrefs.resolvedInboxTags()
        appData.captures
            .filter { it.processed && it.tags.any { t -> t in inbox } }
            .sortedByDescending { it.createdAt }
    }

    val source = when (filter) {
        JournalFilter.ALL -> allJournal
        JournalFilter.REFLECTIONS -> reflections
        JournalFilter.DONE_INBOX -> doneInbox
    }
    val list = if (tagFilter == null) source else source.filter { tagFilter in it.tags }

    val tagOptions = remember(allJournal) {
        allJournal.flatMap { it.tags }.distinct().sorted()
    }

    val dateFmt = remember { SimpleDateFormat("MMM d · HH:mm", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = {
            Column {
                Text("Journal & archive", fontWeight = FontWeight.SemiBold)
                Text(
                    "Reflections stay out of Inbox. Inbox is only Ideas / Todo / Reminders.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = filter == JournalFilter.ALL,
                        onClick = { filter = JournalFilter.ALL },
                        label = { Text("All · ${allJournal.size}", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = filter == JournalFilter.REFLECTIONS,
                        onClick = { filter = JournalFilter.REFLECTIONS },
                        label = { Text("Reflections · ${reflections.size}", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = filter == JournalFilter.DONE_INBOX,
                        onClick = { filter = JournalFilter.DONE_INBOX },
                        label = { Text("Done inbox · ${doneInbox.size}", fontSize = 11.sp) }
                    )
                }
                if (tagOptions.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = tagFilter == null,
                            onClick = { tagFilter = null },
                            label = { Text("Any tag", fontSize = 10.sp) }
                        )
                        tagOptions.take(12).forEach { tag ->
                            FilterChip(
                                selected = tagFilter == tag,
                                onClick = {
                                    tagFilter = if (tagFilter == tag) null else tag
                                },
                                label = {
                                    Text(
                                        "${CaptureTags.glyph(tag)} $tag",
                                        fontSize = 10.sp
                                    )
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (list.isEmpty()) {
                    Text(
                        "Nothing here yet. Capture a Memory, Thought, or Gratitude — it lands here, not in Inbox.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(list, key = { it.id }) { cap ->
                            JournalCaptureRow(
                                cap = cap,
                                dateLabel = dateFmt.format(Date(cap.createdAt)),
                                isInboxTag = appData.capturePrefs.goesToInbox(cap.tags),
                                onDelete = { onDelete(cap.id) },
                                onReopen = if (cap.processed && appData.capturePrefs.goesToInbox(cap.tags)) {
                                    { onReopenToInbox(cap.id) }
                                } else null
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun JournalCaptureRow(
    cap: CaptureItem,
    dateLabel: String,
    isInboxTag: Boolean,
    onDelete: () -> Unit,
    onReopen: (() -> Unit)?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                cap.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (cap.note.isNotBlank()) {
                Text(
                    cap.note,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Text(
                buildString {
                    append(dateLabel)
                    if (cap.tags.isNotEmpty()) {
                        append(" · ")
                        append(cap.tags.joinToString(" · ") { "${CaptureTags.glyph(it)} $it" })
                    }
                    append(if (isInboxTag) " · closed" else " · journal")
                },
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 6.dp)
            ) {
                if (onReopen != null) {
                    Text(
                        "Reopen inbox",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clickable(onClick = onReopen)
                            .padding(vertical = 4.dp)
                    )
                }
                Text(
                    "Delete",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clickable(onClick = onDelete)
                        .padding(vertical = 4.dp)
                )
            }
        }
    }
}
