package com.steady.habittracker.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
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
 * Full-screen composer: multi-select note categories + time scope + stats → Chat with Grok.
 * Supports pasting Grok's reply back into Steady as a note (#39 / #45).
 * Save / load command presets so copy+open uses your usual setup.
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
    val accent = MaterialTheme.colorScheme.primary
    val dateFmt = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }

    val initialPreset = remember(appData.grokPresets, appData.lastGrokPresetId) {
        appData.grokPresets.find { it.id == appData.lastGrokPresetId }
            ?: appData.grokPresets.firstOrNull()
    }
    val initialSel = remember(initialPreset) {
        initialPreset?.let { GrokShareSelection.fromPreset(it) }
    }

    var includeOverview by remember {
        mutableStateOf(initialSel?.includeOverview ?: true)
    }
    var includeMomentum by remember {
        mutableStateOf(initialSel?.includeMomentum ?: true)
    }
    var includeTagAverages by remember {
        mutableStateOf(initialSel?.includeTagAverages ?: true)
    }
    var includeHabitDetails by remember {
        mutableStateOf(initialSel?.includeHabitDetails ?: false)
    }
    var includeSleep by remember {
        mutableStateOf(initialSel?.includeSleep ?: appData.sleepNights.isNotEmpty())
    }
    var includeWorkouts by remember {
        mutableStateOf(initialSel?.includeWorkouts ?: appData.workoutSessions.isNotEmpty())
    }
    var includePathGoals by remember {
        mutableStateOf(initialSel?.includePathGoals ?: appData.goals.any { !it.archived })
    }
    var includeRecentLogs by remember {
        mutableStateOf(initialSel?.includeRecentLogs ?: true)
    }
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
    var selectedHabits by remember {
        mutableStateOf(initialSel?.habitIds ?: emptySet())
    }
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
    var showPreview by remember { mutableStateOf(true) }
    var showPasteReply by remember { mutableStateOf(false) }
    var pasteBody by remember { mutableStateOf("") }
    var pasteTitle by remember { mutableStateOf("Grok reply") }
    var showSavePreset by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }
    var activePresetId by remember { mutableStateOf(initialPreset?.id) }
    var savePresetAfterSend by remember { mutableStateOf(false) }

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
        activePresetId = preset.id
        onMarkLastGrokPreset(preset.id)
    }

    fun currentSelectionForPreset(): GrokShareSelection = GrokShareSelection(
        includeOverview = includeOverview,
        includeMomentum = includeMomentum,
        includeTagAverages = includeTagAverages,
        includeHabitDetails = includeHabitDetails,
        includeSleep = includeSleep,
        includeWorkouts = includeWorkouts,
        includePathGoals = includePathGoals,
        includeRecentLogs = includeRecentLogs,
        includeScreenUsage = includeScreenUsage,
        captureIds = emptySet(),
        captureTags = selectedNoteTags,
        captureScope = captureScope,
        habitIds = selectedHabits,
        userPrompt = if (messageOverride != null) {
            // Keep structured tools; user edited body is one-shot unless they edit prompt field
            userPrompt
        } else {
            userPrompt
        }
    )

    val habits = remember(appData) { GrokContextBuilder.selectableHabits(appData) }
    val scopedCaptures = remember(appData, selectedNoteTags, captureScope) {
        GrokContextBuilder.selectableCaptures(
            data = appData,
            tagsAnyOf = selectedNoteTags,
            scope = captureScope
        )
    }
    val listCaptures = remember(scopedCaptures, pickIndividual) {
        if (pickIndividual) scopedCaptures.take(80) else emptyList()
    }

    val selection = GrokShareSelection(
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

    val builtMessage = remember(
        appData,
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
        GrokContextBuilder.build(appData, selection)
    }

    val displayMessage = messageOverride ?: builtMessage
    val autoCaptureCount = if (pickIndividual) selectedCaptures.size else scopedCaptures.size
    val charCount = displayMessage.length
    val grokInstalled = remember { GrokLauncher.isGrokInstalled(context) }

    if (showSavePreset) {
        AlertDialog(
            onDismissRequest = { showSavePreset = false },
            title = { Text("Save Grok preset") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Saves your question, note tags, time scope, and which tools are on. " +
                            "Live data is always rebuilt when you use the preset.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = { presetName = it },
                        label = { Text("Preset name") },
                        placeholder = { Text("e.g. Weekly review") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = presetName.trim().ifBlank { "Grok preset" }
                        val preset = currentSelectionForPreset().toPreset(name)
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
                    Text(
                        "Paste Grok’s answer to save it as a Steady note.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
            TextButton(onClick = onBack) {
                Text("← Back")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "Chat with Grok",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    if (grokInstalled) "Pick context · edit · open Grok"
                    else "Pick context · edit · share / install Grok",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { showPasteReply = true }) {
                Text("Paste reply", fontSize = 12.sp, color = accent)
            }
        }

        Spacer(Modifier.height(6.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                SectionLabel("Presets")
                Text(
                    "Save this command setup and reuse it when you copy or open Grok.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                if (appData.grokPresets.isEmpty()) {
                    Text(
                        "No presets yet — configure below, then Save preset.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        appData.grokPresets.forEach { preset ->
                            FilterChip(
                                selected = activePresetId == preset.id,
                                onClick = { applyPreset(preset) },
                                label = { Text(preset.name, fontSize = 11.sp) }
                            )
                        }
                    }
                    val active = appData.grokPresets.find { it.id == activePresetId }
                    if (active != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = {
                                    val updated = currentSelectionForPreset().toPreset(
                                        name = active.name,
                                        id = active.id
                                    )
                                    onSaveGrokPreset(updated)
                                    Toast.makeText(
                                        context,
                                        "Updated “${active.name}”",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            ) { Text("Update preset", fontSize = 11.sp) }
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
                        ?.let { id -> appData.grokPresets.find { it.id == id }?.name }
                        .orEmpty()
                    showSavePreset = true
                }) {
                    Text("Save as new preset…", fontSize = 12.sp, color = accent)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { savePresetAfterSend = !savePresetAfterSend }
                ) {
                    Checkbox(
                        checked = savePresetAfterSend,
                        onCheckedChange = { savePresetAfterSend = it }
                    )
                    Text(
                        "When copying / opening, offer to save current setup as a preset",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                SectionLabel("Your question")
                OutlinedTextField(
                    value = userPrompt,
                    onValueChange = {
                        userPrompt = it
                        messageOverride = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5,
                    placeholder = { Text("What do you want Grok to help with?") }
                )
            }

            item {
                SectionLabel("Note categories")
                Text(
                    "Multi-select tags to include from your captures",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    GrokContextBuilder.captureFilterTags().forEach { tag ->
                        val on = tag in selectedNoteTags
                        FilterChip(
                            selected = on,
                            onClick = {
                                selectedNoteTags = if (on) selectedNoteTags - tag else selectedNoteTags + tag
                                messageOverride = null
                            },
                            label = {
                                Text("${CaptureTags.glyph(tag)} $tag", fontSize = 11.sp)
                            }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        selectedNoteTags = GrokContextBuilder.captureFilterTags().toSet()
                        messageOverride = null
                    }) { Text("All tags", fontSize = 11.sp) }
                    TextButton(onClick = {
                        selectedNoteTags = emptySet()
                        messageOverride = null
                    }) { Text("None", fontSize = 11.sp) }
                }
            }

            item {
                SectionLabel("Time scope")
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
                Text(
                    "$autoCaptureCount note(s) match · ~$charCount chars",
                    fontSize = 11.sp,
                    color = accent,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            item {
                SectionLabel("Stats & tools")
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        ToolRow("Overview averages", "Streak, 7d/30d completion", includeOverview) {
                            includeOverview = it
                            messageOverride = null
                        }
                        ToolRow("Momentum", "Level, points, recent scores", includeMomentum) {
                            includeMomentum = it
                            messageOverride = null
                        }
                        ToolRow("Tag averages", "7-day rates by category", includeTagAverages) {
                            includeTagAverages = it
                            messageOverride = null
                        }
                        ToolRow("Recent logs", "Habit entries in scope", includeRecentLogs) {
                            includeRecentLogs = it
                            messageOverride = null
                        }
                        ToolRow(
                            "Habit details",
                            "Per-habit averages for selected habits",
                            includeHabitDetails
                        ) {
                            includeHabitDetails = it
                            messageOverride = null
                        }
                        ToolRow("Path goals", "Dreamline goals & progress", includePathGoals) {
                            includePathGoals = it
                            messageOverride = null
                        }
                        ToolRow("Sleep audio", "Recent night summaries", includeSleep) {
                            includeSleep = it
                            messageOverride = null
                        }
                        ToolRow("Workouts", "Recent sessions", includeWorkouts) {
                            includeWorkouts = it
                            messageOverride = null
                        }
                        ToolRow("Screen usage", "Daily screen-time snapshots", includeScreenUsage) {
                            includeScreenUsage = it
                            messageOverride = null
                        }
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel("Fine-tune notes")
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
                    Text(
                        "All matching notes in the time scope are included automatically.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (pickIndividual) {
                item {
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
                        Text(
                            "${selectedCaptures.size} selected",
                            fontSize = 11.sp,
                            color = accent,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }
                if (listCaptures.isEmpty()) {
                    item {
                        Text(
                            "No captures match tags + scope.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(listCaptures, key = { it.id }) { cap ->
                        val checked = cap.id in selectedCaptures
                        SelectRow(
                            checked = checked,
                            title = cap.title.ifBlank { "(untitled)" },
                            subtitle = buildString {
                                val tags = if (cap.tags.isEmpty()) "Note" else cap.tags.joinToString(" · ")
                                append(tags)
                                append(" · ")
                                append(dateFmt.format(Date(cap.createdAt)))
                                if (cap.note.isNotBlank()) {
                                    append(" · ")
                                    append(cap.note.take(60))
                                    if (cap.note.length > 60) append("…")
                                }
                            },
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
            }

            item {
                SectionLabel("Habits (optional)")
                Text(
                    "Include averages for specific habits",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            selectedHabits = habits.map { it.id }.toSet()
                            includeHabitDetails = true
                            messageOverride = null
                        },
                        enabled = habits.isNotEmpty()
                    ) { Text("Select all", fontSize = 12.sp) }
                    TextButton(
                        onClick = {
                            selectedHabits = emptySet()
                            messageOverride = null
                        },
                        enabled = selectedHabits.isNotEmpty()
                    ) { Text("Clear", fontSize = 12.sp) }
                }
            }

            items(habits.take(40), key = { it.id }) { h ->
                val checked = h.id in selectedHabits
                val avg7 = GrokContextBuilder.habitDueAvg(appData, h, 7)
                SelectRow(
                    checked = checked,
                    title = DisplayIcon.label(h.icon, h.name),
                    subtitle = "7d avg ${(avg7 * 100).toInt()}%",
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

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel("Message preview")
                    TextButton(onClick = { showPreview = !showPreview }) {
                        Text(if (showPreview) "Hide" else "Show", fontSize = 12.sp)
                    }
                }
                if (showPreview) {
                    Text(
                        "Edit freely — you can also tweak inside Grok after open.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = displayMessage,
                        onValueChange = { messageOverride = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 320.dp),
                        minLines = 8,
                        maxLines = 20
                    )
                    if (messageOverride != null) {
                        TextButton(onClick = { messageOverride = null }) {
                            Text("Reset to auto-built message", fontSize = 12.sp)
                        }
                    }
                    Text(
                        "$charCount characters",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
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
                    GrokLauncher.sendToGrok(context, displayMessage)
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
                    GrokLauncher.copyOnly(context, displayMessage)
                    maybePromptSavePreset()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Copy message")
            }
            OutlinedButton(
                onClick = {
                    presetName = ""
                    showSavePreset = true
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save as Grok preset")
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun ToolRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SelectRow(
    checked: Boolean,
    title: String,
    subtitle: String,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = if (checked)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
