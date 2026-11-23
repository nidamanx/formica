// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.exchange

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nidamanx.formica.R
import io.github.nidamanx.formica.domain.ChangeCounts
import io.github.nidamanx.formica.domain.ProductDuplicate
import io.github.nidamanx.formica.domain.model.ImportMode
import io.github.nidamanx.formica.sync.ReadResult
import io.github.nidamanx.formica.ui.components.BackTopBar
import io.github.nidamanx.formica.ui.format.rememberDisplayFormat

/** The import preview (SPEC §11.3): nothing changes until "Import". */
@Composable
fun ImportScreen(
    onClose: () -> Unit,
    onImported: () -> Unit,
    viewModel: ImportViewModel = viewModel(factory = ImportViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.done.collect { onImported() } }

    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.import_title), onClose) },
        bottomBar = {
            val ready = state as? ImportUiState.Ready
            if (ready != null) {
                Column {
                    HorizontalDivider()
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        TextButton(onClick = onClose, enabled = !ready.importing) {
                            Text(stringResource(R.string.action_cancel))
                        }
                        Button(onClick = viewModel::import, enabled = !ready.importing) {
                            Text(stringResource(R.string.import_title))
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when (val s = state) {
                ImportUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is ImportUiState.Refused -> Message(refusalText(s.reason), onClose)
                ImportUiState.Failed -> Message(stringResource(R.string.import_failed), onClose)
                is ImportUiState.Ready -> Preview(s, viewModel)
            }
        }
    }
}

@Composable
private fun refusalText(reason: ReadResult): String = stringResource(
    when (reason) {
        is ReadResult.NewerVersion -> R.string.import_newer_version
        ReadResult.NotFormica -> R.string.import_not_formica
        else -> R.string.import_unreadable
    },
)

@Composable
private fun Message(text: String, onClose: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        Text(text, textAlign = TextAlign.Center)
        TextButton(onClick = onClose, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.action_ok))
        }
    }
}

@Composable
private fun Preview(state: ImportUiState.Ready, viewModel: ImportViewModel) {
    val merge = state.mode == ImportMode.MERGE
    val result = state.result
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        if (state.sameDevice) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(stringResource(R.string.import_same_device), modifier = Modifier.padding(14.dp))
            }
        }
        FileCard(state.file.device.name, state.file.exportedAt)

        SectionTitle(stringResource(R.string.import_mode))
        Column(Modifier.selectableGroup()) {
            ModeRow(ImportMode.MERGE, R.string.mode_merge, R.string.mode_merge_hint, state.mode, viewModel::setMode)
            ModeRow(
                ImportMode.NEW_PRODUCTS_ONLY,
                R.string.mode_new_products,
                R.string.mode_new_products_hint,
                state.mode,
                viewModel::setMode,
            )
            ModeRow(
                ImportMode.REPLACE_ALL,
                R.string.mode_replace_all,
                R.string.mode_replace_all_hint,
                state.mode,
                viewModel::setMode,
            )
        }

        SectionTitle(stringResource(R.string.import_changes))
        CountRow(
            stringResource(R.string.import_products),
            result.products,
            R.plurals.import_new_masculine,
            R.plurals.import_updated_masculine,
            R.plurals.import_deleted_masculine,
        )
        CountRow(
            stringResource(R.string.settings_shopping),
            result.shoppingItems,
            R.plurals.import_new_feminine,
            R.plurals.import_updated_feminine,
            R.plurals.import_deleted_feminine,
        )

        if (merge && (result.autoMerged > 0 || result.productDuplicates.isNotEmpty())) {
            SectionTitle(stringResource(R.string.import_duplicates))
            if (result.autoMerged > 0) {
                Text(
                    stringResource(R.string.import_auto_merged, result.autoMerged),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            result.productDuplicates.forEach { duplicate ->
                DuplicateCard(duplicate, merged = duplicate.remoteId !in state.keepSeparate) {
                    viewModel.setMerge(duplicate.remoteId, it)
                }
            }
        }

        if (merge && state.conflictingNotes.isNotEmpty()) {
            SectionTitle(stringResource(R.string.import_both_notes))
            state.conflictingNotes.forEach { name ->
                LabeledRow(name, stringResource(R.string.import_notes_appended))
            }
        }
    }
}

@Composable
private fun FileCard(deviceName: String, exportedAt: Long) {
    val exported = rememberDisplayFormat().dateTime(exportedAt)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.import_file_from),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(deviceName, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.import_exported_on, exported),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
private fun ModeRow(
    mode: ImportMode,
    @StringRes title: Int,
    @StringRes hint: Int,
    selected: ImportMode,
    onSelect: (ImportMode) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = mode == selected, role = Role.RadioButton) { onSelect(mode) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        RadioButton(selected = mode == selected, onClick = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CountRow(
    label: String,
    counts: ChangeCounts,
    @PluralsRes new: Int,
    @PluralsRes updated: Int,
    @PluralsRes deleted: Int,
) {
    val resources = LocalResources.current
    val text = if (counts.isEmpty) {
        stringResource(R.string.import_no_changes)
    } else {
        listOfNotNull(
            counts.new.takeIf { it > 0 }?.let { resources.getQuantityString(new, it, it) },
            counts.updated.takeIf { it > 0 }?.let { resources.getQuantityString(updated, it, it) },
            counts.deleted.takeIf { it > 0 }?.let { resources.getQuantityString(deleted, it, it) },
        ).joinToString(" · ")
    }
    LabeledRow(label, text)
}

@Composable
private fun LabeledRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DuplicateCard(duplicate: ProductDuplicate, merged: Boolean, onChoice: (Boolean) -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(duplicate.localName, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.import_duplicate_names, duplicate.localName, duplicate.remoteName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = merged,
                    onClick = { onChoice(true) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text(stringResource(R.string.duplicate_merge)) }
                SegmentedButton(
                    selected = !merged,
                    onClick = { onChoice(false) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text(stringResource(R.string.duplicate_keep_separate)) }
            }
        }
    }
}
