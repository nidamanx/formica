// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.exchange

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nidamanx.formica.R
import io.github.nidamanx.formica.domain.model.ImportMode
import io.github.nidamanx.formica.domain.model.ImportRecord
import io.github.nidamanx.formica.sync.Exporter
import io.github.nidamanx.formica.ui.components.BackTopBar
import io.github.nidamanx.formica.ui.format.rememberDisplayFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// Chat apps and file managers label exchange files differently (SPEC §11.3)
private val IMPORT_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream")

/** Export, import, backups, exchange log and clean-up (SPEC §11.8, option A). */
@Composable
fun ExchangeScreen(
    onBack: () -> Unit,
    onOpenBackups: () -> Unit,
    viewModel: ExchangeViewModel = viewModel(factory = ExchangeViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    var exporting by rememberSaveable { mutableStateOf(false) }
    var confirmingUndo by rememberSaveable { mutableStateOf(false) }
    var confirmingCleanUp by rememberSaveable { mutableStateOf(false) }
    val format = rememberDisplayFormat()
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Exporter.MIME)) { uri ->
        if (uri != null) viewModel.saveTo(uri)
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFrom(uri)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ExchangeEvent.Share -> context.startActivity(event.chooser)
                is ExchangeEvent.SaveAs -> try {
                    saveLauncher.launch(event.fileName)
                } catch (_: ActivityNotFoundException) {
                    snackbar.showSnackbar(resources.getString(R.string.msg_save_failed))
                }
                ExchangeEvent.Saved -> snackbar.showSnackbar(resources.getString(R.string.msg_file_saved))
                ExchangeEvent.SaveFailed -> snackbar.showSnackbar(resources.getString(R.string.msg_save_failed))
                ExchangeEvent.ImportUndone -> snackbar.showSnackbar(resources.getString(R.string.msg_import_undone))
                ExchangeEvent.CopyUnreadable -> snackbar.showSnackbar(resources.getString(R.string.import_unreadable))
                is ExchangeEvent.CleanedUp -> snackbar.showSnackbar(
                    if (event.removed == 0) {
                        resources.getString(R.string.clean_up_nothing)
                    } else {
                        resources.getQuantityString(R.plurals.clean_up_done, event.removed, event.removed)
                    },
                )
            }
        }
    }

    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.settings_exchange), onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.loaded) {
                LastExchangeCard(
                    last = state.lastImport,
                    onExport = { exporting = true },
                    onImport = {
                        try {
                            openLauncher.launch(IMPORT_TYPES)
                        } catch (_: ActivityNotFoundException) {
                            // No file picker on this phone: files can still be opened from other apps
                        }
                    },
                )
                SectionTitle(stringResource(R.string.section_backups))
                val last = state.lastImport
                if (last != null && state.canUndo) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.undo_last_import)) },
                        supportingContent = {
                            Text(stringResource(R.string.undo_last_import_hint, format.dateTime(last.importedAt)))
                        },
                        modifier = Modifier.clickable { confirmingUndo = true },
                    )
                }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.saved_copies)) },
                    supportingContent = {
                        Text(pluralStringResource(R.plurals.saved_copies_count, state.copies, state.copies))
                    },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onOpenBackups),
                )
                if (state.log.isNotEmpty()) {
                    SectionTitle(stringResource(R.string.section_exchange_log))
                    state.log.forEach { record ->
                        ListItem(
                            headlineContent = { Text(record.deviceName) },
                            supportingContent = {
                                Text(
                                    stringResource(
                                        R.string.exchange_log_entry,
                                        format.dateTime(record.importedAt),
                                        stringResource(record.mode.label()),
                                    ),
                                )
                            },
                        )
                    }
                }
                SectionTitle(stringResource(R.string.section_maintenance))
                ListItem(
                    headlineContent = { Text(stringResource(R.string.clean_up)) },
                    supportingContent = { Text(stringResource(R.string.clean_up_hint)) },
                    modifier = Modifier.clickable { confirmingCleanUp = true },
                )
            }
        }
    }

    val last = state.lastImport
    if (confirmingUndo && last != null) {
        AlertDialog(
            onDismissRequest = { confirmingUndo = false },
            title = { Text(stringResource(R.string.undo_import_title)) },
            text = {
                Text(stringResource(R.string.undo_import_text, format.dateTime(last.importedAt), last.deviceName))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingUndo = false
                    viewModel.undoLastImport()
                }) { Text(stringResource(R.string.action_restore)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingUndo = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (confirmingCleanUp) {
        AlertDialog(
            onDismissRequest = { confirmingCleanUp = false },
            title = { Text(stringResource(R.string.clean_up)) },
            text = { Text(stringResource(R.string.clean_up_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingCleanUp = false
                    viewModel.cleanUp()
                }) { Text(stringResource(R.string.action_clean_up)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingCleanUp = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (exporting) {
        ExportSheet(
            onShare = {
                exporting = false
                viewModel.share()
            },
            onSave = {
                exporting = false
                viewModel.chooseSaveLocation()
            },
            onDismiss = { exporting = false },
        )
    }
}

@Composable
private fun LastExchangeCard(last: ImportRecord?, onExport: () -> Unit, onImport: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.exchange_last),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                if (last == null) {
                    stringResource(R.string.exchange_never)
                } else {
                    stringResource(R.string.exchange_last_with, last.deviceName, daysAgo(last.importedAt))
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                Button(onClick = onExport) { Text(stringResource(R.string.action_export)) }
                FilledTonalButton(onClick = onImport) { Text(stringResource(R.string.action_import_file)) }
            }
        }
    }
}

private fun ImportMode.label(): Int = when (this) {
    ImportMode.MERGE -> R.string.mode_merge
    ImportMode.NEW_PRODUCTS_ONLY -> R.string.mode_new_products
    ImportMode.REPLACE_ALL -> R.string.mode_replace_all
}

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
private fun daysAgo(millis: Long): String {
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()
    val days = (LocalDate.now(zone).toEpochDay() - day).coerceAtLeast(0).toInt()
    return when (days) {
        0 -> stringResource(R.string.exchange_today)
        1 -> stringResource(R.string.exchange_yesterday)
        else -> pluralStringResource(R.plurals.exchange_days_ago, days, days)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportSheet(onShare: () -> Unit, onSave: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            Text(stringResource(R.string.action_export), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.export_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            )
            val colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            ListItem(
                headlineContent = { Text(stringResource(R.string.action_share)) },
                leadingContent = { Icon(Icons.Filled.Share, contentDescription = null) },
                colors = colors,
                modifier = Modifier.clickable(onClick = onShare),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.action_save_to)) },
                leadingContent = { Icon(painterResource(R.drawable.ic_save), contentDescription = null) },
                colors = colors,
                modifier = Modifier.clickable(onClick = onSave),
            )
        }
    }
}
