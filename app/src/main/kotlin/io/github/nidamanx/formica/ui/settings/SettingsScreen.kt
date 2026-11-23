// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nidamanx.formica.R
import io.github.nidamanx.formica.domain.MinStockBehavior
import io.github.nidamanx.formica.domain.TickBehavior
import io.github.nidamanx.formica.ui.components.BackTopBar

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenCatalog: (CatalogKind) -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val deviceName by viewModel.deviceName.collectAsStateWithLifecycle()
    val shopping by viewModel.shopping.collectAsStateWithLifecycle()
    var renaming by rememberSaveable { mutableStateOf(false) }
    var choosing by rememberSaveable { mutableStateOf<ShoppingChoice?>(null) }

    Scaffold(topBar = { BackTopBar(stringResource(R.string.settings_title), onBack) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            NavigationRow(stringResource(R.string.settings_locations)) { onOpenCatalog(CatalogKind.LOCATIONS) }
            NavigationRow(stringResource(R.string.settings_categories)) { onOpenCatalog(CatalogKind.CATEGORIES) }
            NavigationRow(stringResource(R.string.settings_stores)) { onOpenCatalog(CatalogKind.STORES) }
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_device_name)) },
                supportingContent = { Text(deviceName.orEmpty()) },
                modifier = Modifier.clickable(enabled = deviceName != null) { renaming = true },
            )
            shopping?.let { prefs ->
                HorizontalDivider()
                Text(
                    stringResource(R.string.settings_shopping),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                )
                ValueRow(stringResource(R.string.settings_on_tick), stringResource(prefs.onTick.label())) {
                    choosing = ShoppingChoice.ON_TICK
                }
                ValueRow(stringResource(R.string.settings_min_stock), stringResource(prefs.minStock.label())) {
                    choosing = ShoppingChoice.MIN_STOCK
                }
                ValueRow(
                    stringResource(R.string.settings_running_low),
                    pluralStringResource(R.plurals.running_low_summary, prefs.runningLowDays, prefs.runningLowDays),
                ) { choosing = ShoppingChoice.RUNNING_LOW }
            }
        }
    }

    val prefs = shopping
    when (choosing) {
        null -> Unit
        ShoppingChoice.ON_TICK -> if (prefs != null) {
            ChoiceDialog(
                title = stringResource(R.string.settings_on_tick),
                options = TickBehavior.entries,
                selected = prefs.onTick,
                label = { it.label() },
                onSelect = {
                    viewModel.setOnTick(it)
                    choosing = null
                },
                onDismiss = { choosing = null },
            )
        }
        ShoppingChoice.MIN_STOCK -> if (prefs != null) {
            ChoiceDialog(
                title = stringResource(R.string.settings_min_stock),
                options = MinStockBehavior.entries,
                selected = prefs.minStock,
                label = { it.label() },
                onSelect = {
                    viewModel.setMinStock(it)
                    choosing = null
                },
                onDismiss = { choosing = null },
            )
        }
        ShoppingChoice.RUNNING_LOW -> if (prefs != null) {
            RunningLowDialog(
                current = prefs.runningLowDays,
                onSave = { if (viewModel.setRunningLowDays(it)) choosing = null },
                onDismiss = { choosing = null },
            )
        }
    }

    if (renaming) {
        DeviceNameDialog(
            current = deviceName.orEmpty(),
            onSave = { if (viewModel.rename(it)) renaming = false },
            onDismiss = { renaming = false },
        )
    }
}

private enum class ShoppingChoice { ON_TICK, MIN_STOCK, RUNNING_LOW }

private fun TickBehavior.label(): Int = when (this) {
    TickBehavior.ASK -> R.string.on_tick_ask
    TickBehavior.ALWAYS -> R.string.on_tick_always
    TickBehavior.NEVER -> R.string.on_tick_never
}

private fun MinStockBehavior.label(): Int = when (this) {
    MinStockBehavior.AUTOMATIC -> R.string.min_stock_automatic
    MinStockBehavior.ASK -> R.string.min_stock_ask
}

@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** A list of options with radio buttons; choosing one closes the dialog. */
@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> Int,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup()) {
                options.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(option) },
                            )
                            .padding(vertical = 12.dp),
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(stringResource(label(option)), modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun RunningLowDialog(current: Int, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var days by rememberSaveable { mutableStateOf(current.toString()) }
    var showError by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_running_low)) },
        text = {
            Column {
                Text(stringResource(R.string.settings_running_low_help))
                OutlinedTextField(
                    value = days,
                    onValueChange = {
                        days = it
                        showError = false
                    },
                    label = { Text(stringResource(R.string.field_days)) },
                    singleLine = true,
                    isError = showError,
                    supportingText = if (showError) {
                        { Text(stringResource(R.string.error_days)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(days)
                showError = true
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun NavigationRow(title: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

@Composable
private fun DeviceNameDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    var showError by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_device_name)) },
        text = {
            Column {
                Text(stringResource(R.string.settings_device_name_hint))
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        showError = false
                    },
                    label = { Text(stringResource(R.string.field_name)) },
                    singleLine = true,
                    isError = showError,
                    supportingText = if (showError) {
                        { Text(stringResource(R.string.error_name_required)) }
                    } else {
                        null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                showError = name.isBlank()
                if (!showError) onSave(name)
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
