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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import io.github.nidamanx.formica.ui.components.NotificationAccess
import io.github.nidamanx.formica.ui.components.rememberNotificationAccess
import io.github.nidamanx.formica.ui.format.timeOfDayText

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenCatalog: (CatalogKind) -> Unit,
    onOpenExchange: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val deviceName by viewModel.deviceName.collectAsStateWithLifecycle()
    val shopping by viewModel.shopping.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    var renaming by rememberSaveable { mutableStateOf(false) }
    var choosing by rememberSaveable { mutableStateOf<Choice?>(null) }
    val access = rememberNotificationAccess(
        asked = notifications?.permissionAsked ?: true,
        onAsked = viewModel::onPermissionAsked,
    )

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
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_exchange)) },
                supportingContent = { Text(stringResource(R.string.settings_exchange_hint)) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenExchange),
            )
            notifications?.let { prefs ->
                HorizontalDivider()
                SectionTitle(stringResource(R.string.settings_notifications))
                SwitchRow(stringResource(R.string.settings_daily_notification), prefs.enabled) { on ->
                    viewModel.setNotificationsEnabled(on)
                    if (on && access.access == NotificationAccess.ASK) access.resolve()
                }
                if (prefs.enabled && access.access == NotificationAccess.BLOCKED) {
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.notif_permission_denied),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingContent = {
                            TextButton(onClick = access.resolve) { Text(stringResource(R.string.action_open_settings)) }
                        },
                    )
                }
                ValueRow(stringResource(R.string.settings_notification_time), timeOfDayText(prefs.minuteOfDay)) {
                    choosing = Choice.TIME
                }
                ValueRow(
                    stringResource(R.string.settings_warning_days),
                    pluralStringResource(R.plurals.warning_days_summary, prefs.warningDays, prefs.warningDays),
                ) { choosing = Choice.WARNING_DAYS }
            }
            shopping?.let { prefs ->
                HorizontalDivider()
                SectionTitle(stringResource(R.string.settings_shopping))
                ValueRow(stringResource(R.string.settings_on_tick), stringResource(prefs.onTick.label())) {
                    choosing = Choice.ON_TICK
                }
                ValueRow(stringResource(R.string.settings_min_stock), stringResource(prefs.minStock.label())) {
                    choosing = Choice.MIN_STOCK
                }
                ValueRow(
                    stringResource(R.string.settings_running_low),
                    pluralStringResource(R.plurals.running_low_summary, prefs.runningLowDays, prefs.runningLowDays),
                ) { choosing = Choice.RUNNING_LOW }
            }
            HorizontalDivider()
            NavigationRow(stringResource(R.string.about_title), onOpenAbout)
        }
    }

    val prefs = shopping
    val notificationPrefs = notifications
    when (choosing) {
        null -> Unit
        Choice.TIME -> if (notificationPrefs != null) {
            TimeDialog(
                minuteOfDay = notificationPrefs.minuteOfDay,
                onSave = { hour, minute ->
                    viewModel.setNotificationTime(hour, minute)
                    choosing = null
                },
                onDismiss = { choosing = null },
            )
        }
        Choice.WARNING_DAYS -> if (notificationPrefs != null) {
            DaysDialog(
                title = stringResource(R.string.settings_warning_days),
                help = stringResource(R.string.settings_warning_days_help),
                current = notificationPrefs.warningDays,
                onSave = { if (viewModel.setWarningDays(it)) choosing = null },
                onDismiss = { choosing = null },
            )
        }
        Choice.ON_TICK -> if (prefs != null) {
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
        Choice.MIN_STOCK -> if (prefs != null) {
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
        Choice.RUNNING_LOW -> if (prefs != null) {
            DaysDialog(
                title = stringResource(R.string.settings_running_low),
                help = stringResource(R.string.settings_running_low_help),
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

private enum class Choice { TIME, WARNING_DAYS, ON_TICK, MIN_STOCK, RUNNING_LOW }

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(minuteOfDay: Int, onSave: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(
        initialHour = minuteOfDay / 60,
        initialMinute = minuteOfDay % 60,
        is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notification_time_title)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onSave(state.hour, state.minute) }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

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
private fun DaysDialog(title: String, help: String, current: Int, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var days by rememberSaveable { mutableStateOf(current.toString()) }
    var showError by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(help)
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
