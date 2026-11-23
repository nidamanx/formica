// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nidamanx.formica.R
import io.github.nidamanx.formica.ui.components.BackTopBar

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenCatalog: (CatalogKind) -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val deviceName by viewModel.deviceName.collectAsStateWithLifecycle()
    var renaming by rememberSaveable { mutableStateOf(false) }

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
