package com.steady.habittracker.ui

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.steady.habittracker.data.AppData
import com.steady.habittracker.data.CaptureTags
import com.steady.habittracker.data.DisplayIcon
import com.steady.habittracker.data.GrokPreset
import com.steady.habittracker.util.CaptureTimeScope
import com.steady.habittracker.util.GrokContextBuilder
import com.steady.habittracker.util.GrokLauncher
import com.steady.habittracker.util.GrokShareSelection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Chat with Grok composer — optimised for smooth scrolling:
 * - message body only built when preview is open or on send/copy
 * - habit/capture rows are lightweight (no Card/Checkbox trees)
 * - expensive habit averages precomputed once
 * - long lists only appear when the user opts in
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskGrokScreen(
    appData: AppData,
    onBack: () -> Unit,
    onSaveGrokReply: (title: String, note: String, tags: List<String>) -> Unit = { _, _, _ -> },
    onSaveGrokPreset: (GrokPreset) -> Unit = {},
    onDeleteGrokPreset: (String) -> Unit = {},
    onMarkLastGrokPreset: (String?) -> Unit = {}
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val accent = scheme.primary
    val onSurface = scheme.onSurface
    val onVariant = scheme.onSurfaceVariant
    val surface = scheme.surface
    val primaryContainer = scheme.primaryContainer
    val dateFmt = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val listState = rememberLazyListState()

    val initialPreset = remember(appData.lastGrokPresetId, appData.grokPresets.size) {
        appData.grokPresets.find { it.id == appData.lastGrokPresetId }
            ?: appData.grokPresets.firstOrNull()
    }
    val initialSel = remember(initialPreset?.id) {
        initialPreset?.let { GrokShareSelection.fromPreset(it) }
    }

    var includeOverview by remember { mutableStateOf(initialSel?.includeOverview ?: true) }
    var includeMomentum by remember { mutableStateOf(initialSel?.includeMomentum ?: true) }
    var includeTagAverages by remember { mutableStateOf(initialSel?.includeTagAverages ?: true) }
    var includeHabitDetails by remember { mutableStateOf(initialSel?.includeHabitDetails ?: false) }
    var includeSleep by remember {
        mutableStateOf(initialSel?.includeSleep ?: appData.sleepNights.isNotEmpty())
    }
    var includeWorkouts by remember {
        mutableStateOf(initialSel?.includeWorkouts ?: appData.workoutSessions.isNotEmpty())
    }
    var includePathGoals by remember {
        mutableStateOf(initialSel?.includePathGoals ?: appData.goals.any { !it.archived })
    }
    var includeRecentLogs by remember { mutableStateOf(initialSel?.includeRecentLogs ?: true) }
    var includeScreenUsage by remember {
        mutableStateOf(
            initialSel?.includeScreenUsage
                ?: GrokContextBuilder.hasScreenUsageBlock(appData)
        )
    }

    var userPrompt by remember {
        mutableStateOf(initialSel?.userPrompt ?: GrokShareSelection.DEFAULT_PROMPT)
    }
    var selectedCaptures by remember { mutableStateOf(setOf<String>()) }
    var selectedHabits by remember { mutableStateOf(initialSel?.habitIds ?: emptySet()) }
    var selectedNoteTags by remember {
        mutableStateOf(
            initialSel?.captureTags?.takeIf { it.isNotEmpty() }
                ?: setOf(CaptureTags.IDEAS, CaptureTags.NOTES)
        )
    }
    var captureScope by remember {
        mutableStateOf(initialSel?.captureScope ?: CaptureTimeScope.LAST_7)
    }
    var pickIndividual by remember { mutableStateOf(false) }
    var messageOverride by remember { mutableStateOf<String?>(null) }
    // Preview off by default — huge TextField was the main scroll jank source
    var showPreview by remember { mutableStateOf(false) }
    var showPasteReply by remember { mutableStateOf(false) }
    var pasteBody by remember { mutableStateOf("") }
    var pasteTitle by remember { mutableStateOf("Grok reply") }
    var showSavePreset by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }
    var activePresetId by remember { mutableStateOf(initialPreset?.id) }
    var savePresetAfterSend by remember { mutableStateOf(false) }
    var showHabitPicker by remember { mutableStateOf(false) }

    fun currentSelection(): GrokShareSelection = GrokShareSelection(
        includeOverview = includeOverview,
        includeMomentum = includeMomentum,
        includeTagAverages = includeTagAverages,
        includeHabitDetails = includeHabitDetails,
        includeSleep = includeSleep,
        includeWorkouts = includeWorkouts,
        includePathGoals = includePathGoals,
        includeRecentLogs = includeRecentLogs,
        includeScreenUsage = includeScreenUsage,
        captureIds = if (pickIndividual) selectedCaptures else emptySet(),
        captureTags = if (pickIndividual) emptySet() else selectedNoteTags,
        captureScope = captureScope,
        habitIds = selectedHabits,
        userPrompt = userPrompt
    )

    fun applyPreset(preset: GrokPreset) {
        val sel = GrokShareSelection.fromPreset(preset)
        includeOverview = sel.includeOverview
        includeMomentum = sel.includeMomentum
        includeTagAverages = sel.includeTagAverages
        includeHabitDetails = sel.includeHabitDetails
        includeSleep = sel.includeSleep
        includeWorkouts = sel.includeWorkouts
        includePathGoals = sel.includePathGoals
        includeRecentLogs = sel.includeRecentLogs
        includeScreenUsage = sel.includeScreenUsage
        selectedNoteTags = sel.captureTags
        captureScope = sel.captureScope
        selectedHabits = sel.habitIds
        userPrompt = sel.userPrompt
        pickIndividual = false
        selectedCaptures = emptySet()
        messageOverride = null
        showHabitPicker = sel.habitIds.isNotEmpty()
        activePresetId = preset.id
        onMarkLastGrokPreset(preset.id)
    }

    // Precompute once — not per-row during scroll
    val habitRows = remember(appData.habits, appData.entries) {
        GrokContextBuilder.selectableHabits(appData).map { h ->
            HabitPickRow(
                id = h.id,
                title = DisplayIcon.label(h.icon, h.name),
                avg7Pct = (GrokContextBuilder.habitDueAvg(appData, h, 7) * 100).toInt()
            )
        }
    }

    val scopedCaptures = remember(appData.captures, selectedNoteTags, captureScope) {
        GrokContextBuilder.selectableCaptures(
            data = appData,
            tagsAnyOf = selectedNoteTags,
            scope = captureScope
        )
    }
    val listCaptures = remember(scopedCaptures, pickIndividual) {
        if (pickIndividual) {
            scopedCaptures.take(40).map { cap ->
                CapturePickRow(
                    id = cap.id,
                    title = cap.title.ifBlank { "(untitled)" },
                    subtitle = buildString {
                        append(if (cap.tags.isEmpty()) "Note" else cap.tags.joinToString(" · "))
                        append(" · ")
                        append(dateFmt.format(Date(cap.createdAt)))
                        if (cap.note.isNotBlank()) {
                            append(" · ")
                            append(cap.note.take(48))
                            if (cap.note.length > 48) append("…")
                        }
                    }
                )
            }
        } else emptyList()
    }

    val autoCaptureCount = if (pickIndividual) selectedCaptures.size else scopedCaptures.size

    // Heavy build only when needed (preview or after user opens it once)
    val builtMessage = remember(
        showPreview,
        appData.captures.size,
        appData.entries.size,
        includeOverview,
        includeMomentum,
        includeTagAverages,
        includeHabitDetails,
        includeSleep,
        includeWorkouts,
        includePathGoals,
        includeRecentLogs,
        includeScreenUsage,
        selectedCaptures,
        selectedNoteTags,
        captureScope,
        pickIndividual,
        selectedHabits,
        userPrompt
    ) {
        if (!showPreview) "" else GrokContextBuilder.build(appData, currentSelection())
    }

    fun messageForSend(): String =
        messageOverride?.takeIf { it.isNotBlank() }
            ?: GrokContextBuilder.build(appData, currentSelection())

    val displayMessage = messageOverride ?: builtMessage
    val grokInstalled = remember { GrokLauncher.isGrokInstalled(context) }
    val filterTags = remember { GrokContextBuilder.captureFilterTags() }
    val presets = appData.grokPresets

    if (showSavePreset) {
        AlertDialog(
            onDismissRequest = { showSavePreset = false },
            title = { Text("Save Grok preset") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Saves question, tags, time scope, and tools. Live data is rebuilt on use.",
                        fontSize = 12.sp,
                        color = onVariant
                    )
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = { presetName = it },
                        label = { Text("Preset name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = presetName.trim().ifBlank { "Grok preset" }
                        val preset = currentSelection().toPreset(name)
                        onSaveGrokPreset(preset)
                        activePresetId = preset.id
                        showSavePreset = false
                        presetName = ""
                        Toast.makeText(context, "Preset saved: $name", Toast.LENGTH_SHORT).show()
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSavePreset = false }) { Text("Cancel") }
            }
        )
    }

    if (showPasteReply) {
        AlertDialog(
            onDismissRequest = { showPasteReply = false },
            title = { Text("Paste Grok reply") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pasteTitle,
                        onValueChange = { pasteTitle = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = pasteBody,
                        onValueChange = { pasteBody = it },
                        label = { Text("Reply") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        minLines = 4
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val body = pasteBody.trim()
                        if (body.isNotEmpty()) {
                            onSaveGrokReply(
                                pasteTitle.trim().ifBlank { "Grok reply" },
                                body,
                                listOf(CaptureTags.NOTES, "Grok")
                            )
                            showPasteReply = false
                            pasteBody = ""
                        }
                    },
                    enabled = pasteBody.isNotBlank()
                ) { Text("Save note") }
            },
            dismissButton = {
                TextButton(onClick = { showPasteReply = false }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← Back") }
            Column(Modifier.weight(1f)) {
                Text(
                    "Chat with Grok",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = onSurface
                )
                Text(
                    "$autoCaptureCount notes in scope · ${selectedHabits.size} habits",
                    fontSize = 11.sp,
                    color = onVariant
                )
            }
            TextButton(onClick = { showPasteReply = true }) {
                Text("Paste reply", fontSize = 12.sp, color = accent)
            }
        }

        Spacer(Modifier.height(4.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "presets", contentType = "section") {
                SectionLabel("Presets", accent)
                if (presets.isEmpty()) {
                    BasicText(
                        "No presets yet — configure below, then save.",
                        style = TextStyle(color = onVariant, fontSize = 12.sp)
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        presets.forEach { preset ->
                            FilterChip(
                                selected = activePresetId == preset.id,
                                onClick = { applyPreset(preset) },
                                label = { Text(preset.name, fontSize = 11.sp) }
                            )
                        }
                    }
                    val active = presets.find { it.id == activePresetId }
                    if (active != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = {
                                    onSaveGrokPreset(
                                        currentSelection().toPreset(active.name, active.id)
                                    )
                                    Toast.makeText(context, "Updated “${active.name}”", Toast.LENGTH_SHORT)
                                        .show()
                                }
                            ) { Text("Update", fontSize = 11.sp) }
                            TextButton(
                                onClick = {
                                    onDeleteGrokPreset(active.id)
                                    activePresetId = null
                                }
                            ) { Text("Delete", fontSize = 11.sp) }
                        }
                    }
                }
                TextButton(onClick = {
                    presetName = activePresetId
                        ?.let { id -> presets.find { it.id == id }?.name }
                        .orEmpty()
                    showSavePreset = true
                }) {
                    Text("Save as new preset…", fontSize = 12.sp, color = accent)
                }
            }

            item(key = "question", contentType = "field") {
                SectionLabel("Your question", accent)
                OutlinedTextField(
                    value = userPrompt,
                    onValueChange = {
                        userPrompt = it
                        messageOverride = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    placeholder = { Text("What do you want Grok to help with?") }
                )
            }

            item(key = "tags", contentType = "chips") {
                SectionLabel("Note categories", accent)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    filterTags.forEach { tag ->
                        val on = tag in selectedNoteTags
                        FilterChip(
                            selected = on,
                            onClick = {
                                selectedNoteTags =
                                    if (on) selectedNoteTags - tag else selectedNoteTags + tag
                                messageOverride = null
                            },
                            label = {
                                Text("${CaptureTags.glyph(tag)} $tag", fontSize = 11.sp)
                            }
                        )
                    }
                }
            }

            item(key = "scope", contentType = "chips") {
                SectionLabel("Time scope", accent)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CaptureTimeScope.entries.forEach { scope ->
                        FilterChip(
                            selected = captureScope == scope,
                            onClick = {
                                captureScope = scope
                                messageOverride = null
                            },
                            label = { Text(scope.label, fontSize = 11.sp) }
                        )
                    }
                }
            }

            item(key = "tools", contentType = "tools") {
                SectionLabel("Stats & tools", accent)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(surface, RoundedCornerShape(12.dp))
                        .padding(vertical = 4.dp)
                ) {
                    ToolRowLite("Overview averages", includeOverview, onSurface, onVariant) {
                        includeOverview = it
                        messageOverride = null
                    }
                    ToolRowLite("Momentum", includeMomentum, onSurface, onVariant) {
                        includeMomentum = it
                        messageOverride = null
                    }
                    ToolRowLite("Tag averages", includeTagAverages, onSurface, onVariant) {
                        includeTagAverages = it
                        messageOverride = null
                    }
                    ToolRowLite("Recent logs", includeRecentLogs, onSurface, onVariant) {
                        includeRecentLogs = it
                        messageOverride = null
                    }
                    ToolRowLite("Habit details", includeHabitDetails, onSurface, onVariant) {
                        includeHabitDetails = it
                        if (it) showHabitPicker = true
                        messageOverride = null
                    }
                    ToolRowLite("Path goals", includePathGoals, onSurface, onVariant) {
                        includePathGoals = it
                        messageOverride = null
                    }
                    ToolRowLite("Sleep audio", includeSleep, onSurface, onVariant) {
                        includeSleep = it
                        messageOverride = null
                    }
                    ToolRowLite("Workouts", includeWorkouts, onSurface, onVariant) {
                        includeWorkouts = it
                        messageOverride = null
                    }
                    ToolRowLite("Screen usage", includeScreenUsage, onSurface, onVariant) {
                        includeScreenUsage = it
                        messageOverride = null
                    }
                }
            }

            item(key = "fine_notes", contentType = "toggle") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel("Notes", accent)
                    FilterChip(
                        selected = pickIndividual,
                        onClick = {
                            pickIndividual = !pickIndividual
                            messageOverride = null
                        },
                        label = { Text("Pick individual", fontSize = 11.sp) }
                    )
                }
                if (!pickIndividual) {
                    BasicText(
                        "Including all $autoCaptureCount matching notes automatically.",
                        style = TextStyle(color = onVariant, fontSize = 11.sp)
                    )
                }
            }

            if (pickIndividual) {
                item(key = "cap_actions", contentType = "actions") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                selectedCaptures = selectedCaptures + listCaptures.map { it.id }
                                messageOverride = null
                            },
                            enabled = listCaptures.isNotEmpty()
                        ) { Text("Select shown", fontSize = 12.sp) }
                        TextButton(
                            onClick = {
                                selectedCaptures = emptySet()
                                messageOverride = null
                            },
                            enabled = selectedCaptures.isNotEmpty()
                        ) { Text("Clear", fontSize = 12.sp) }
                    }
                }
                items(
                    listCaptures,
                    key = { it.id },
                    contentType = { "cap" }
                ) { cap ->
                    val checked = cap.id in selectedCaptures
                    SelectRowLite(
                        checked = checked,
                        title = cap.title,
                        subtitle = cap.subtitle,
                        onSurface = onSurface,
                        onVariant = onVariant,
                        surface = surface,
                        primaryContainer = primaryContainer,
                        onToggle = {
                            selectedCaptures = if (checked) {
                                selectedCaptures - cap.id
                            } else {
                                selectedCaptures + cap.id
                            }
                            messageOverride = null
                        }
                    )
                }
            }

            item(key = "habits_hdr", contentType = "toggle") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel("Habits", accent)
                    FilterChip(
                        selected = showHabitPicker,
                        onClick = {
                            showHabitPicker = !showHabitPicker
                            if (showHabitPicker) includeHabitDetails = true
                        },
                        label = {
                            Text(
                                if (showHabitPicker) "Hide list" else "Pick · ${selectedHabits.size}",
                                fontSize = 11.sp
                            )
                        }
                    )
                }
            }

            if (showHabitPicker) {
                item(key = "habit_actions", contentType = "actions") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                selectedHabits = habitRows.map { it.id }.toSet()
                                includeHabitDetails = true
                                messageOverride = null
                            }
                        ) { Text("Select all", fontSize = 12.sp) }
                        TextButton(
                            onClick = {
                                selectedHabits = emptySet()
                                messageOverride = null
                            }
                        ) { Text("Clear", fontSize = 12.sp) }
                    }
                }
                items(
                    habitRows,
                    key = { it.id },
                    contentType = { "habit" }
                ) { h ->
                    val checked = h.id in selectedHabits
                    SelectRowLite(
                        checked = checked,
                        title = h.title,
                        subtitle = "7d avg ${h.avg7Pct}%",
                        onSurface = onSurface,
                        onVariant = onVariant,
                        surface = surface,
                        primaryContainer = primaryContainer,
                        onToggle = {
                            selectedHabits = if (checked) {
                                selectedHabits - h.id
                            } else {
                                selectedHabits + h.id
                            }
                            if (!checked) includeHabitDetails = true
                            messageOverride = null
                        }
                    )
                }
            }

            item(key = "preview", contentType = "preview") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel("Message preview", accent)
                    TextButton(onClick = {
                        showPreview = !showPreview
                        if (!showPreview) messageOverride = null
                    }) {
                        Text(if (showPreview) "Hide" else "Build & show", fontSize = 12.sp)
                    }
                }
                if (showPreview) {
                    OutlinedTextField(
                        value = displayMessage,
                        onValueChange = { messageOverride = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 240.dp),
                        minLines = 5,
                        maxLines = 12
                    )
                    if (messageOverride != null) {
                        TextButton(onClick = { messageOverride = null }) {
                            Text("Reset to auto-built", fontSize = 12.sp)
                        }
                    }
                    BasicText(
                        "${displayMessage.length} characters",
                        style = TextStyle(color = onVariant, fontSize = 10.sp)
                    )
                } else {
                    BasicText(
                        "Preview stays closed for smooth scrolling. Built only when you show it or send.",
                        style = TextStyle(color = onVariant, fontSize = 11.sp)
                    )
                }
            }

            item(key = "spacer", contentType = "sp") {
                Spacer(Modifier.height(80.dp))
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(scheme.background)
                .padding(top = 8.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            fun maybePromptSavePreset() {
                if (savePresetAfterSend) {
                    presetName = ""
                    showSavePreset = true
                }
            }
            Button(
                onClick = {
                    GrokLauncher.sendToGrok(context, messageForSend())
                    maybePromptSavePreset()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (grokInstalled) "Open in Grok" else "Share / open Grok",
                    fontWeight = FontWeight.SemiBold
                )
            }
            OutlinedButton(
                onClick = {
                    GrokLauncher.copyOnly(context, messageForSend())
                    maybePromptSavePreset()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Copy message")
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    presetName = ""
                    showSavePreset = true
                }) {
                    Text("Save preset", fontSize = 12.sp)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { savePresetAfterSend = !savePresetAfterSend }
                ) {
                    BasicText(
                        if (savePresetAfterSend) "☑ Save after send" else "☐ Save after send",
                        style = TextStyle(color = onVariant, fontSize = 11.sp)
                    )
                }
            }
        }
    }
}

@Immutable
private data class HabitPickRow(val id: String, val title: String, val avg7Pct: Int)

@Immutable
private data class CapturePickRow(val id: String, val title: String, val subtitle: String)

@Composable
private fun SectionLabel(text: String, accent: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        color = accent,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
    )
}

@Composable
private fun ToolRowLite(
    title: String,
    checked: Boolean,
    onSurface: androidx.compose.ui.graphics.Color,
    onVariant: androidx.compose.ui.graphics.Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            if (checked) "☑" else "☐",
            style = TextStyle(fontSize = 16.sp, color = onSurface)
        )
        Spacer(Modifier.padding(horizontal = 6.dp))
        BasicText(
            title,
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = onSurface
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SelectRowLite(
    checked: Boolean,
    title: String,
    subtitle: String,
    onSurface: androidx.compose.ui.graphics.Color,
    onVariant: androidx.compose.ui.graphics.Color,
    surface: androidx.compose.ui.graphics.Color,
    primaryContainer: androidx.compose.ui.graphics.Color,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (checked) primaryContainer.copy(alpha = 0.35f) else surface,
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            if (checked) "☑" else "☐",
            style = TextStyle(fontSize = 15.sp, color = onSurface)
        )
        Spacer(Modifier.padding(horizontal = 6.dp))
        Column(Modifier.weight(1f)) {
            BasicText(
                title,
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = onSurface
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            BasicText(
                subtitle,
                style = TextStyle(fontSize = 11.sp, color = onVariant),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
