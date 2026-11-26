// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.nidamanx.formica.R
import io.github.nidamanx.formica.ui.format.DisplayFormat

private const val MILLIS_PER_DAY = 86_400_000L

/** A read-only field that opens a menu of [options]; null in the list stands for "none". */
@Composable
fun <T> SelectField(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val valueText = optionLabel(selected)
    Box(modifier) {
        OutlinedTextField(
            value = valueText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            singleLine = true,
            // TalkBack reads the box laid over it instead, as one control
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {},
        )
        // Covers the text field, which would otherwise take the tap for editing
        Box(
            Modifier
                .matchParentSize()
                .semantics { contentDescription = "$label, $valueText" }
                .clickable(role = Role.DropdownList) { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

/**
 * A date (epoch day) chosen with the Material calendar; [emptyText] when null.
 * A required date ([clearable] false) has no clear button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    value: Long?,
    emptyText: String,
    format: DisplayFormat,
    onChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    clearable: Boolean = true,
) {
    var picking by remember { mutableStateOf(false) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val valueText = value?.let { format.date(it) } ?: emptyText
        Box(Modifier.weight(1f)) {
            OutlinedTextField(
                value = valueText,
                onValueChange = {},
                readOnly = true,
                label = { Text(label) },
                trailingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                singleLine = true,
                // TalkBack reads the box laid over it instead, as one control
                modifier = Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics {},
            )
            Box(
                Modifier
                    .matchParentSize()
                    .semantics { contentDescription = "$label, $valueText" }
                    .clickable(role = Role.Button) { picking = true },
            )
        }
        if (clearable && value != null) {
            IconButton(onClick = { onChange(null) }) {
                Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.cd_clear_date))
            }
        }
    }
    if (picking) {
        // The Material picker works in UTC millis at midnight
        val state = rememberDatePickerState(initialSelectedDateMillis = value?.let { it * MILLIS_PER_DAY })
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    picking = false
                    state.selectedDateMillis?.let { onChange(Math.floorDiv(it, MILLIS_PER_DAY)) }
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            DatePicker(state = state)
        }
    }
}
