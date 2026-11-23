// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nidamanx.formica.R
import io.github.nidamanx.formica.domain.BatchGroup
import io.github.nidamanx.formica.domain.model.Location
import io.github.nidamanx.formica.ui.components.SelectField
import io.github.nidamanx.formica.ui.format.DisplayFormat
import io.github.nidamanx.formica.ui.pantry.QuantityField

/** Asks a quantity; [allowZero] for corrections, where 0 is a valid real quantity. */
@Composable
fun AmountDialog(
    title: String,
    initial: String,
    format: DisplayFormat,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
    help: String? = null,
    allowZero: Boolean = false,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    var error by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                help?.let { Text(it) }
                QuantityField(text, error) {
                    text = it
                    error = false
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = format.parse(text)?.takeIf { if (allowZero) it >= 0 else it > 0 }
                error = amount == null
                if (amount != null) onConfirm(amount)
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Moves all (prefilled) or part of a group to another location. */
@Composable
fun MoveDialog(
    group: BatchGroup,
    locations: List<Location>,
    format: DisplayFormat,
    onConfirm: (amount: Long, toLocationId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(format.number(group.quantity)) }
    var error by rememberSaveable { mutableStateOf(false) }
    var target by rememberSaveable { mutableStateOf(locations.firstOrNull()?.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                QuantityField(text, error) {
                    text = it
                    error = false
                }
                SelectField(
                    label = stringResource(R.string.field_new_location),
                    options = locations.map { it.id },
                    selected = target.orEmpty(),
                    optionLabel = { id -> locations.firstOrNull { it.id == id }?.name.orEmpty() },
                    onSelect = { target = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = target != null,
                onClick = {
                    val amount = format.parse(text)?.takeIf { it > 0 }
                    val to = target
                    error = amount == null
                    if (amount != null && to != null) onConfirm(amount, to)
                },
            ) { Text(stringResource(R.string.move_title)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
fun NotesDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notes_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.field_notes)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
fun DeleteDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_confirm, name)) },
        text = { Text(stringResource(R.string.delete_product_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
