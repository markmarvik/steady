package com.steady.habittracker.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.steady.habittracker.data.AppData
import com.steady.habittracker.data.CaptureItem
import com.steady.habittracker.data.CaptureTags
import com.steady.habittracker.data.inboxCaptures
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Full-screen Write workspace for power users: compose notes/ideas, manage open inbox,
 * jump to Journal. Replaces the old Capture popup.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WriteScreen(
    appData: AppData,
    onBack: () -> Unit,
    onSave: (title: String, note: String, tags: List<String>) -> Unit,
    onUpdate: (id: String, title: String, note: String, tags: List<String>) -> Unit = { _, _, _, _ -> },
    onProcess: (id: String) -> Unit = {},
    onDelete: (id: String) -> Unit = {},
    onOpenJournal: () -> Unit = {},
    /** Prefill when editing or opening from check-in deep-link. */
    editItem: CaptureItem? = null,
    presetTags: List<String>? = null,
    forceTags: List<String> = emptyList(),
    screenTitle: String? = null
) {
    val prefs = appData.capturePrefs
    val accent = MaterialTheme.colorScheme.primary
    val dateFmt = remember { SimpleDateFormat("MMM d · HH:mm", Locale.getDefault()) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    val visibleTags = remember(prefs, presetTags, forceTags, editItem) {
        val base = prefs.visibleTags()
        val extra = (presetTags.orEmpty() + forceTags + (editItem?.tags.orEmpty()))
            .filter { it.isNotBlank() }
        (base + extra).distinct()
    }

    var title by remember { mutableStateOf(editItem?.title.orEmpty()) }
    var note by remember { mutableStateOf(editItem?.note.orEmpty()) }
    var energy by remember { mutableIntStateOf(0) }
    var inboxFilter by remember { mutableStateOf<String?>(null) }
    /** Local edit target when user picks an open item without leaving the page. */
    var localEdit by remember { mutableStateOf<CaptureItem?>(null) }
    val activeEdit = editItem ?: localEdit

    var selectedTags by remember {
        val forced = forceTags.filter { it.isNotBlank() }.toSet()
        val fromEdit = editItem?.tags?.toSet().orEmpty()
        val fromPreset = presetTags.orEmpty().filter { it.isNotBlank() }.toSet()
        mutableStateOf(
            when {
                fromEdit.isNotEmpty() -> fromEdit + forced
                fromPreset.isNotEmpty() || forced.isNotEmpty() -> fromPreset + forced
                else -> prefs.defaultTags.filter { it in visibleTags }.ifEmpty {
                    visibleTags.take(1)
                }.toSet()
            }
        )
    }

    LaunchedEffect(editItem?.id) {
        if (editItem != null) {
            localEdit = null
            title = editItem.title
            note = editItem.note
            selectedTags = editItem.tags.toSet()
        }
    }

    val inbox = remember(appData.captures, appData.capturePrefs, inboxFilter) {
        val all = appData.inboxCaptures()
        if (inboxFilter == null) all
        else all.filter { inboxFilter in it.tags }
    }
    val inboxTagOptions = remember(appData.captures, appData.capturePrefs) {
        appData.inboxCaptures().flatMap { it.tags }.distinct().sorted()
    }

    val isEditing = activeEdit != null
    val headerTitle = screenTitle
        ?: if (isEditing) "Edit note" else "Write"

    LaunchedEffect(Unit) {
        delay(80)
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {
        }
    }

    fun clearComposer() {
        title = ""
        note = ""
        energy = 0
        localEdit = null
        selectedTags = prefs.defaultTags.filter { it in visibleTags }.ifEmpty {
            visibleTags.take(1)
        }.toSet()
    }

    fun submit(andKeepOpen: Boolean) {
        val t = title.trim()
        if (t.isBlank()) return
        val tags = selectedTags.toMutableList()
        forceTags.forEach { ft ->
            if (ft.isNotBlank() && ft !in tags) tags.add(ft)
        }
        var finalNote = note.trim()
        if (prefs.showEnergyScale && energy in 1..5) {
            if (CaptureTags.ENERGY !in tags) tags.add(CaptureTags.ENERGY)
            finalNote = listOfNotNull(
                finalNote.takeIf { it.isNotBlank() },
                "energy=$energy"
            ).joinToString(" · ")
        }
        val edit = activeEdit
        if (edit != null) {
            onUpdate(edit.id, t, finalNote, tags)
            if (editItem != null) {
                keyboard?.hide()
                onBack()
            } else {
                clearComposer()
            }
        } else {
            onSave(t, finalNote, tags)
            if (andKeepOpen) {
                clearComposer()
            } else {
                keyboard?.hide()
                onBack()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp)
    ) {
        // Top bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← Back") }
            Column(Modifier.weight(1f)) {
                Text(
                    headerTitle,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Ideas · Todo · Reminders → open list · Journal tags archive automatically",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onOpenJournal) {
                Text("Journal", color = accent, fontWeight = FontWeight.SemiBold)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            placeholder = {
                                Text(
                                    prefs.placeholderTitle.ifBlank { "What's on your mind?" },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = fieldColors(),
                            keyboardOptions = KeyboardOptions(
                                imeAction = if (prefs.showNoteField) ImeAction.Next else ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { submit(true) })
                        )
                        if (prefs.showNoteField) {
                            OutlinedTextField(
                                value = note,
                                onValueChange = { note = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp),
                                placeholder = {
                                    Text(
                                        prefs.placeholderNote.ifBlank { "Details, context, freewrite…" },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = fieldColors()
                            )
                        }
                        if (visibleTags.isNotEmpty()) {
                            Text(
                                "Tags",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                visibleTags.forEach { tag ->
                                    val selected = tag in selectedTags
                                    CaptureTagChip(
                                        label = tag,
                                        glyph = CaptureTags.glyph(tag),
                                        selected = selected,
                                        onClick = {
                                            selectedTags = when {
                                                prefs.multiTag ->
                                                    if (selected) selectedTags - tag else selectedTags + tag
                                                selected -> emptySet()
                                                else -> setOf(tag)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        if (prefs.showEnergyScale) {
                            Text(
                                "Energy",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                (1..5).forEach { n ->
                                    val selected = energy == n
                                    val bg by animateColorAsState(
                                        if (selected) accent
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                        label = "e$n"
                                    )
                                    val fg by animateColorAsState(
                                        if (selected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        label = "ef$n"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(bg)
                                            .clickable { energy = if (energy == n) 0 else n },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("$n", color = fg, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!isEditing) {
                                OutlinedButton(
                                    onClick = { submit(andKeepOpen = true) },
                                    enabled = title.isNotBlank(),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Save & write more")
                                }
                            }
                            Button(
                                onClick = { submit(andKeepOpen = false) },
                                enabled = title.isNotBlank(),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accent,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text(
                                    if (isEditing) "Save changes"
                                    else prefs.saveLabel.ifBlank { "Save" },
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Always show open inbox unless opened purely as journal edit-from-elsewhere
            if (editItem == null) {
                item {
                    if (localEdit != null) {
                        TextButton(onClick = { clearComposer() }) {
                            Text("Cancel edit · new note", fontSize = 12.sp, color = accent)
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Open · ${inbox.size}",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = accent
                        )
                        Text(
                            "Done clears from this list · stays in Journal",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (inboxTagOptions.isNotEmpty()) {
                    item {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilterChip(
                                selected = inboxFilter == null,
                                onClick = { inboxFilter = null },
                                label = { Text("All", fontSize = 11.sp) }
                            )
                            inboxTagOptions.forEach { tag ->
                                FilterChip(
                                    selected = inboxFilter == tag,
                                    onClick = {
                                        inboxFilter = if (inboxFilter == tag) null else tag
                                    },
                                    label = {
                                        Text(
                                            "${CaptureTags.glyph(tag)} $tag",
                                            fontSize = 11.sp
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
                if (inbox.isEmpty()) {
                    item {
                        Text(
                            "Nothing open. New Ideas / Todos / Reminders land here.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    items(inbox, key = { it.id }) { cap ->
                        InboxRow(
                            cap = cap,
                            dateLabel = dateFmt.format(Date(cap.createdAt)),
                            onDone = { onProcess(cap.id) },
                            onDelete = { onDelete(cap.id) },
                            onLoadIntoComposer = {
                                localEdit = cap
                                title = cap.title
                                note = cap.note
                                selectedTags = cap.tags.toSet()
                            }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
)

@Composable
private fun InboxRow(
    cap: CaptureItem,
    dateLabel: String,
    onDone: () -> Unit,
    onDelete: () -> Unit,
    onLoadIntoComposer: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onLoadIntoComposer),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                cap.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (cap.note.isNotBlank()) {
                Text(
                    if (cap.note.length > 140) cap.note.take(140).trimEnd() + "…" else cap.note,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
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
                },
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 6.dp)
            ) {
                TextButton(onClick = onDone) {
                    Text("Done", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick = onLoadIntoComposer) {
                    Text("Edit in composer", fontSize = 12.sp)
                }
                TextButton(onClick = onDelete) {
                    Text("Delete", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
