package com.steady.habittracker.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.steady.habittracker.data.CapturePrefs
import com.steady.habittracker.data.CaptureTags
import kotlinx.coroutines.delay

/**
 * Modern, polished Quick Capture sheet.
 * Configured via [CapturePrefs] (Manage → Blocks → Capture).
 *
 * @param presetTags when non-null, pre-select these tags (e.g. random ESM check-in → Check-in).
 * @param dialogTitle optional header override (defaults to "Quick capture").
 * @param forceTags tags always applied on save even if the user deselects them (e.g. Check-in).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaptureDialog(
    prefs: CapturePrefs = CapturePrefs(),
    presetTags: List<String>? = null,
    dialogTitle: String? = null,
    forceTags: List<String> = emptyList(),
    /** Prefill when editing an existing capture. */
    initialTitle: String = "",
    initialNote: String = "",
    onDismiss: () -> Unit,
    onCapture: (title: String, note: String, tags: List<String>) -> Unit
) {
    val visibleTags = remember(prefs, presetTags, forceTags) {
        val base = prefs.visibleTags()
        val extra = ((presetTags.orEmpty()) + forceTags).filter { it.isNotBlank() }
        (base + extra).distinct()
    }
    val initialTags = remember(prefs, presetTags, forceTags, visibleTags) {
        val forced = forceTags.filter { it in visibleTags }.toSet()
        val fromPreset = presetTags
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() && it in visibleTags }
            ?.toSet()
            .orEmpty()
        when {
            fromPreset.isNotEmpty() || forced.isNotEmpty() -> fromPreset + forced
            else -> prefs.defaultTags.filter { it in visibleTags }.ifEmpty {
                visibleTags.take(1)
            }.toSet()
        }
    }
    var title by remember { mutableStateOf(initialTitle) }
    var note by remember { mutableStateOf(initialNote) }
    var selectedTags by remember { mutableStateOf(initialTags) }
    var energy by remember { mutableIntStateOf(0) } // 0 = unset
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        delay(80)
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) { }
    }

    fun submit() {
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
        onCapture(t, finalNote, tags)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp
        ) {
            Column(
                Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✦", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            dialogTitle ?: "Quick capture",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            if (forceTags.any { it.equals(CaptureTags.CHECKIN, ignoreCase = true) } ||
                                presetTags.orEmpty().any { it.equals(CaptureTags.CHECKIN, ignoreCase = true) }
                            ) {
                                "Awareness check-in — tagged as Check-in and saved to Journal."
                            } else {
                                "Ideas · Todo · Reminders → Inbox. Memories · Gratitude · Thoughts → Journal."
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = {
                        Text(
                            prefs.placeholderTitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = if (prefs.showNoteField) ImeAction.Next else ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { submit() }
                    )
                )

                if (prefs.showNoteField) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                prefs.placeholderNote,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        },
                        minLines = 2,
                        maxLines = 5,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f)
                        )
                    )
                }

                if (visibleTags.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Tags",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.6.sp
                    )
                    Spacer(Modifier.height(8.dp))
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
                                        prefs.multiTag -> {
                                            if (selected) selectedTags - tag else selectedTags + tag
                                        }
                                        selected -> emptySet()
                                        else -> setOf(tag)
                                    }
                                }
                            )
                        }
                    }
                }

                if (prefs.showEnergyScale) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Energy",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.6.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..5).forEach { n ->
                            val selected = energy == n
                            val bg by animateColorAsState(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                label = "energy$n"
                            )
                            val fg by animateColorAsState(
                                if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                label = "energyFg$n"
                            )
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(bg)
                                    .clickable { energy = if (energy == n) 0 else n },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("$n", color = fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(0.35f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            keyboard?.hide()
                            submit()
                        },
                        enabled = title.isNotBlank(),
                        modifier = Modifier
                            .weight(0.65f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(
                            prefs.saveLabel.ifBlank { "Save" },
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CaptureTagChip(
    label: String,
    glyph: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        label = "tagBg"
    )
    val fg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurface,
        label = "tagFg"
    )
    val borderColor by animateColorAsState(
        if (selected) Color.Transparent
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
        label = "tagBorder"
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(glyph, fontSize = 13.sp)
        Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = fg)
    }
}
