package com.steady.habittracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.steady.habittracker.data.AppData
import com.steady.habittracker.data.CaptureItem
import com.steady.habittracker.data.CapturePrefs
import com.steady.habittracker.data.CaptureTags
import com.steady.habittracker.data.journalCaptures
import com.steady.habittracker.data.reflectionCaptures
import com.steady.habittracker.data.trashedCaptures
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private enum class JournalPageFilter {
    ALL,
    REFLECTIONS,
    DONE_INBOX,
    OPEN_INBOX,
    TRASH
}

/**
 * Full-screen Journal: search, filter, edit, soft-delete to trash (30 days default), restore.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JournalScreen(
    appData: AppData,
    onBack: () -> Unit,
    onDelete: (String) -> Unit = {},
    onRestore: (String) -> Unit = {},
    onPermanentlyDelete: (String) -> Unit = {},
    onEmptyTrash: () -> Unit = {},
    onUpdateCapturePrefs: (CapturePrefs) -> Unit = {},
    onReopenToInbox: (String) -> Unit = {},
    onEdit: (CaptureItem) -> Unit = {},
    onOpenWrite: () -> Unit = {}
) {
    val accent = MaterialTheme.colorScheme.primary
    var filter by remember { mutableStateOf(JournalPageFilter.ALL) }
    var tagFilter by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var expandedIds by remember { mutableStateOf(setOf<String>()) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    var pendingPurgeId by remember { mutableStateOf<String?>(null) }
    var confirmEmptyTrash by remember { mutableStateOf(false) }
    var showTrashSettings by remember { mutableStateOf(false) }

    val prefs = appData.capturePrefs
    val retainDays = prefs.trashRetainDays.coerceIn(1, 365)

    val allJournal = remember(appData.captures, appData.capturePrefs) {
        appData.journalCaptures()
    }
    val reflections = remember(appData.captures, appData.capturePrefs) {
        appData.reflectionCaptures()
    }
    val doneInbox = remember(appData.captures, appData.capturePrefs) {
        val inbox = appData.capturePrefs.resolvedInboxTags()
        appData.captures
            .filter { !it.isTrashed && it.processed && it.tags.any { t -> t in inbox } }
            .sortedByDescending { it.createdAt }
    }
    val openInbox = remember(appData.captures, appData.capturePrefs) {
        appData.captures
            .filter { !it.isTrashed && !it.processed && appData.capturePrefs.goesToInbox(it.tags) }
            .sortedByDescending { it.createdAt }
    }
    val trash = remember(appData.captures) { appData.trashedCaptures() }

    val source = when (filter) {
        JournalPageFilter.ALL -> allJournal
        JournalPageFilter.REFLECTIONS -> reflections
        JournalPageFilter.DONE_INBOX -> doneInbox
        JournalPageFilter.OPEN_INBOX -> openInbox
        JournalPageFilter.TRASH -> trash
    }

    val list = remember(source, tagFilter, query) {
        source
            .asSequence()
            .filter { tagFilter == null || tagFilter in it.tags }
            .filter { cap ->
                val q = query.trim()
                if (q.isEmpty()) true
                else cap.title.contains(q, ignoreCase = true) ||
                    cap.note.contains(q, ignoreCase = true) ||
                    cap.tags.any { it.contains(q, ignoreCase = true) }
            }
            .toList()
    }

    val tagOptions = remember(allJournal, openInbox) {
        (allJournal + openInbox).flatMap { it.tags }.distinct().sorted()
    }

    val dateFmt = remember { SimpleDateFormat("EEE, MMM d · HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← Back") }
            Column(Modifier.weight(1f)) {
                Text(
                    "Journal",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    when (filter) {
                        JournalPageFilter.TRASH ->
                            "${list.size} in trash · auto-purge after $retainDays days"
                        else ->
                            "${list.size} shown · ${allJournal.size} archived · ${openInbox.size} open" +
                                if (trash.isNotEmpty()) " · ${trash.size} trash" else ""
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onOpenWrite) {
                Text("✎ Write", color = accent, fontWeight = FontWeight.SemiBold)
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            placeholder = { Text("Search title, body, tags…") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            FilterChip(
                selected = filter == JournalPageFilter.ALL,
                onClick = { filter = JournalPageFilter.ALL },
                label = { Text("Archive · ${allJournal.size}", fontSize = 11.sp) }
            )
            FilterChip(
                selected = filter == JournalPageFilter.REFLECTIONS,
                onClick = { filter = JournalPageFilter.REFLECTIONS },
                label = { Text("Reflections · ${reflections.size}", fontSize = 11.sp) }
            )
            FilterChip(
                selected = filter == JournalPageFilter.DONE_INBOX,
                onClick = { filter = JournalPageFilter.DONE_INBOX },
                label = { Text("Done · ${doneInbox.size}", fontSize = 11.sp) }
            )
            FilterChip(
                selected = filter == JournalPageFilter.OPEN_INBOX,
                onClick = { filter = JournalPageFilter.OPEN_INBOX },
                label = { Text("Open · ${openInbox.size}", fontSize = 11.sp) }
            )
            if (prefs.showTrashInJournal || trash.isNotEmpty()) {
                FilterChip(
                    selected = filter == JournalPageFilter.TRASH,
                    onClick = { filter = JournalPageFilter.TRASH },
                    label = { Text("🗑 Trash · ${trash.size}", fontSize = 11.sp) }
                )
            }
        }

        if (filter == JournalPageFilter.TRASH) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { showTrashSettings = true }) {
                    Text("Trash settings", fontSize = 12.sp, color = accent)
                }
                if (trash.isNotEmpty()) {
                    TextButton(onClick = { confirmEmptyTrash = true }) {
                        Text("Empty trash", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        if (tagOptions.isNotEmpty() && filter != JournalPageFilter.TRASH) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                FilterChip(
                    selected = tagFilter == null,
                    onClick = { tagFilter = null },
                    label = { Text("Any tag", fontSize = 10.sp) }
                )
                tagOptions.forEach { tag ->
                    FilterChip(
                        selected = tagFilter == tag,
                        onClick = {
                            tagFilter = if (tagFilter == tag) null else tag
                        },
                        label = {
                            Text("${CaptureTags.glyph(tag)} $tag", fontSize = 10.sp)
                        }
                    )
                }
            }
        }

        if (list.isEmpty()) {
            Text(
                when {
                    query.isNotBlank() -> "No matches for “$query”."
                    filter == JournalPageFilter.OPEN_INBOX ->
                        "Nothing open. Use Write for Ideas / Todo / Reminders."
                    filter == JournalPageFilter.TRASH ->
                        "Trash is empty. Deleted entries stay here for $retainDays days."
                    else ->
                        "Journal is empty. Memories, Thoughts, Gratitude, and closed inbox items land here."
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(list, key = { it.id }) { cap ->
                    val expanded = cap.id in expandedIds
                    val daysLeft = cap.deletedAt?.let { del ->
                        val age = System.currentTimeMillis() - del
                        val left = retainDays - TimeUnit.MILLISECONDS.toDays(age).toInt()
                        left.coerceAtLeast(0)
                    }
                    JournalEntryCard(
                        cap = cap,
                        dateLabel = dateFmt.format(Date(cap.createdAt)),
                        expanded = expanded,
                        isOpen = !cap.processed && appData.capturePrefs.goesToInbox(cap.tags) && !cap.isTrashed,
                        isTrash = filter == JournalPageFilter.TRASH,
                        trashDaysLeft = daysLeft,
                        onToggleExpand = {
                            expandedIds = if (expanded) expandedIds - cap.id else expandedIds + cap.id
                        },
                        onEdit = { onEdit(cap) },
                        onDelete = { pendingDeleteId = cap.id },
                        onRestore = { onRestore(cap.id) },
                        onPermanentDelete = { pendingPurgeId = cap.id },
                        onReopen = if (cap.processed && !cap.isTrashed &&
                            appData.capturePrefs.goesToInbox(cap.tags)
                        ) {
                            { onReopenToInbox(cap.id) }
                        } else null
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    // Confirm move to trash
    pendingDeleteId?.let { id ->
        val cap = appData.captures.find { it.id == id }
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Move to trash?") },
            text = {
                Text(
                    "“${cap?.title?.ifBlank { "(untitled)" } ?: "Entry"}” will stay in trash for " +
                        "$retainDays days, then be permanently removed. You can restore it anytime from Trash."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(id)
                        pendingDeleteId = null
                    }
                ) {
                    Text("Move to trash", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("Cancel") }
            }
        )
    }

    // Confirm permanent delete
    pendingPurgeId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingPurgeId = null },
            title = { Text("Delete forever?") },
            text = { Text("This cannot be undone. The entry will be removed immediately.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onPermanentlyDelete(id)
                        pendingPurgeId = null
                    }
                ) {
                    Text("Delete forever", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPurgeId = null }) { Text("Cancel") }
            }
        )
    }

    if (confirmEmptyTrash) {
        AlertDialog(
            onDismissRequest = { confirmEmptyTrash = false },
            title = { Text("Empty trash?") },
            text = { Text("Permanently delete all ${trash.size} item(s) in trash now?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEmptyTrash()
                        confirmEmptyTrash = false
                    }
                ) {
                    Text("Empty trash", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmptyTrash = false }) { Text("Cancel") }
            }
        )
    }

    if (showTrashSettings) {
        AlertDialog(
            onDismissRequest = { showTrashSettings = false },
            title = { Text("Trash settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Soft-deleted journal and inbox items are kept this long, then purged automatically.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("Retain for (days)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(7, 14, 30, 60, 90).forEach { d ->
                            FilterChip(
                                selected = retainDays == d,
                                onClick = {
                                    onUpdateCapturePrefs(prefs.copy(trashRetainDays = d))
                                },
                                label = { Text("$d", fontSize = 12.sp) }
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Show Trash tab", fontSize = 13.sp)
                        androidx.compose.material3.Switch(
                            checked = prefs.showTrashInJournal,
                            onCheckedChange = {
                                onUpdateCapturePrefs(prefs.copy(showTrashInJournal = it))
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTrashSettings = false }) { Text("Done") }
            }
        )
    }
}

@Composable
private fun JournalEntryCard(
    cap: CaptureItem,
    dateLabel: String,
    expanded: Boolean,
    isOpen: Boolean,
    isTrash: Boolean,
    trashDaysLeft: Int?,
    onToggleExpand: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit,
    onReopen: (() -> Unit)?
) {
    val long = cap.note.length > 160
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isTrash -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                isOpen -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    cap.title.ifBlank { "(untitled)" },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = if (expanded) 6 else 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (isOpen) {
                    Text(
                        "OPEN",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                if (isTrash && trashDaysLeft != null) {
                    Text(
                        "${trashDaysLeft}d left",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            if (cap.note.isNotBlank()) {
                Text(
                    text = when {
                        expanded || !long -> cap.note
                        else -> cap.note.take(160).trimEnd() + "…"
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis
                )
                if (long) {
                    Text(
                        if (expanded) "Show less" else "Show more",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable(onClick = onToggleExpand)
                            .padding(top = 4.dp)
                    )
                }
            }
            Text(
                buildString {
                    append(dateLabel)
                    if (cap.tags.isNotEmpty()) {
                        append(" · ")
                        append(cap.tags.joinToString(" · ") { "${CaptureTags.glyph(it)} $it" })
                    }
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                if (isTrash) {
                    TextButton(onClick = onRestore) {
                        Text("Restore", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(onClick = onPermanentDelete) {
                        Text("Delete forever", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    TextButton(onClick = onEdit) {
                        Text("Edit", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    if (onReopen != null) {
                        TextButton(onClick = onReopen) {
                            Text("Reopen", fontSize = 12.sp)
                        }
                    }
                    TextButton(onClick = onDelete) {
                        Text("Delete", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
