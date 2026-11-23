// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import io.github.nidamanx.formica.domain.model.StoreType
import io.github.nidamanx.formica.ui.components.BackTopBar
import kotlinx.coroutines.launch

@StringRes
fun StoreType.label(): Int = when (this) {
    StoreType.SUPERMARKET -> R.string.store_type_supermarket
    StoreType.SHOP -> R.string.store_type_shop
    StoreType.MARKET -> R.string.store_type_market
    StoreType.PRODUCER -> R.string.store_type_producer
}

@StringRes
private fun CatalogKind.title(): Int = when (this) {
    CatalogKind.LOCATIONS -> R.string.settings_locations
    CatalogKind.CATEGORIES -> R.string.settings_categories
    CatalogKind.STORES -> R.string.settings_stores
}

@StringRes
private fun CatalogKind.dialogTitle(new: Boolean): Int = when (this) {
    CatalogKind.LOCATIONS -> if (new) R.string.location_new else R.string.location_edit
    CatalogKind.CATEGORIES -> if (new) R.string.category_new else R.string.category_edit
    CatalogKind.STORES -> if (new) R.string.store_new else R.string.store_edit
}

@StringRes
private fun CatalogKind.deleteMessage(): Int? = when (this) {
    CatalogKind.LOCATIONS -> null
    CatalogKind.CATEGORIES -> R.string.delete_category_message
    CatalogKind.STORES -> R.string.delete_store_message
}

/** Settings list of locations, categories or stores: add, edit, reorder, delete. */
@Composable
fun CatalogScreen(
    kind: CatalogKind,
    onBack: () -> Unit,
    viewModel: CatalogViewModel = viewModel(factory = CatalogViewModel.factory(kind)),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }

    val resources = LocalResources.current
    fun showInUse(name: String) {
        scope.launch { snackbar.showSnackbar(resources.getString(R.string.location_in_use, name)) }
    }

    Scaffold(
        topBar = { BackTopBar(stringResource(kind.title()), onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingId = null
                editorOpen = true
            }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (items.isEmpty() && kind == CatalogKind.STORES) {
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.stores_empty), textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(Modifier.padding(padding)) {
                itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                    CatalogRow(
                        item = item,
                        canMoveUp = index > 0,
                        canMoveDown = index < items.lastIndex,
                        onEdit = {
                            editingId = item.id
                            editorOpen = true
                        },
                        onMove = { up -> viewModel.move(item.id, up) },
                        onDelete = {
                            scope.launch {
                                if (viewModel.canDelete(item.id)) deletingId = item.id else showInUse(item.name)
                            }
                        },
                    )
                }
            }
        }
    }

    if (editorOpen) {
        val editing = items.firstOrNull { it.id == editingId }
        CatalogEditDialog(
            kind = kind,
            initial = editing,
            validate = { name -> viewModel.validate(name, editing?.id) },
            onSave = { name, type, notes ->
                viewModel.save(editing?.id, name, type, notes)
                editorOpen = false
            },
            onDismiss = { editorOpen = false },
        )
    }

    // An item deleted meanwhile (e.g. by an import) simply closes the dialog
    items.firstOrNull { it.id == deletingId }?.let { item ->
        val deleteMessage = kind.deleteMessage()
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text(stringResource(R.string.delete_confirm, item.name)) },
            text = deleteMessage?.let { message -> @Composable { Text(stringResource(message)) } },
            confirmButton = {
                TextButton(onClick = {
                    deletingId = null
                    viewModel.delete(item.id) { showInUse(item.name) }
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingId = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun CatalogRow(
    item: CatalogItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onMove: (up: Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val supporting = listOfNotNull(
        item.type?.let { stringResource(it.label()) },
        item.notes?.lineSequence()?.firstOrNull(),
    ).joinToString(" · ")
    ListItem(
        headlineContent = { Text(item.name) },
        supportingContent = if (supporting.isNotEmpty()) {
            { Text(supporting, maxLines = 1) }
        } else {
            null
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_edit)) },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_move_up)) },
                        enabled = canMoveUp,
                        onClick = {
                            menuOpen = false
                            onMove(true)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_move_down)) },
                        enabled = canMoveDown,
                        onClick = {
                            menuOpen = false
                            onMove(false)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        },
        modifier = Modifier.clickable(onClick = onEdit),
    )
}

@Composable
private fun CatalogEditDialog(
    kind: CatalogKind,
    initial: CatalogItem?,
    validate: (String) -> NameError?,
    onSave: (name: String, type: StoreType, notes: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var type by rememberSaveable { mutableStateOf(initial?.type ?: StoreType.SUPERMARKET) }
    var notes by rememberSaveable { mutableStateOf(initial?.notes.orEmpty()) }
    var error by remember { mutableStateOf<NameError?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(kind.dialogTitle(new = initial == null))) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.field_name)) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { e ->
                        @Composable {
                            Text(
                                when (e) {
                                    NameError.Required -> stringResource(R.string.error_name_required)
                                    is NameError.Exists -> stringResource(R.string.error_name_exists, e.name)
                                },
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (kind.hasType) {
                    Text(
                        stringResource(R.string.field_type),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                    Column(Modifier.selectableGroup()) {
                        StoreType.entries.forEach { option ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = option == type,
                                        onClick = { type = option },
                                        role = Role.RadioButton,
                                    ),
                            ) {
                                RadioButton(selected = option == type, onClick = null)
                                Text(stringResource(option.label()), Modifier.padding(start = 12.dp))
                            }
                        }
                    }
                }
                if (kind.hasNotes) {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(stringResource(R.string.field_notes)) },
                        minLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = validate(name)
                if (error == null) onSave(name, type, notes)
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
