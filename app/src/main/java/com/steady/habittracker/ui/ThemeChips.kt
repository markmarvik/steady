package com.steady.habittracker.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Selection chips and controls that always follow the app accent (issue #23).
 * Material3 FilterChip defaults use secondaryContainer, which our theme may not
 * map to the user-chosen color scheme — so we pin colors explicitly.
 */
@Composable
fun themedFilterChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
    selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimary,
    containerColor = MaterialTheme.colorScheme.surfaceVariant,
    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
    disabledSelectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
)

@Composable
fun themedCheckboxColors() = CheckboxDefaults.colors(
    checkedColor = MaterialTheme.colorScheme.primary,
    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
    checkmarkColor = MaterialTheme.colorScheme.onPrimary,
    disabledCheckedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
    disabledUncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
)

@Composable
fun themedSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
)

@Composable
fun ThemedFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Row { label() } },
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        colors = themedFilterChipColors()
    )
}

@Composable
fun ThemedCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = themedCheckboxColors()
    )
}

@Composable
fun ThemedSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = themedSwitchColors()
    )
}
