// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.shopping

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nidamanx.formica.R
import io.github.nidamanx.formica.domain.ShoppingGrouping
import io.github.nidamanx.formica.domain.ShoppingRow
import io.github.nidamanx.formica.domain.model.MeasureUnit
import io.github.nidamanx.formica.domain.model.ONE_UNIT
import io.github.nidamanx.formica.domain.model.Product
import io.github.nidamanx.formica.domain.model.ShoppingStatus
import io.github.nidamanx.formica.ui.components.SectionTopBar
import io.github.nidamanx.formica.ui.format.DisplayFormat
import io.github.nidamanx.formica.ui.format.quantityText
import io.github.nidamanx.formica.ui.format.rememberDisplayFormat
import io.github.nidamanx.formica.ui.pantry.MenuChip
import io.github.nidamanx.formica.ui.pantry.PurchaseSheet

@Composable
fun ShoppingScreen(
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: ShoppingViewModel = viewModel(factory = ShoppingViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val format = rememberDisplayFormat()
    val hasSuggestions = state.runningLow.isNotEmpty() || state.frequent.isNotEmpty()

    val clearLabel = stringResource(R.string.shopping_clear_bought)
    Scaffold(
        topBar = {
            SectionTopBar(
                title = stringResource(R.string.nav_shopping_list),
                onOpenSettings = onOpenSettings,
                onOpenAbout = onOpenAbout,
                actions = if (state.view.bought.isEmpty()) {
                    emptyList()
                } else {
                    listOf(clearLabel to viewModel::clearBought)
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.open(ShoppingDialog.NewItem) }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.shopping_new_item))
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            when {
                !state.loaded -> Unit
                state.empty && !hasSuggestions -> EmptyList()
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    item { OptionsRow(state, viewModel) }
                    suggestions(R.string.shopping_running_low, state.runningLow, viewModel::addSuggestion)
                    suggestions(R.string.shopping_frequent, state.frequent, viewModel::addSuggestion)
                    if (state.empty) item { EmptyList() }
                    state.view.groups.forEach { group ->
                        item(key = "group-${group.id}") { GroupTitle(groupName(state, group.id)) }
                        items(group.rows, key = { it.item.id }) { row ->
                            ItemRow(row, state, format, onToggle = { viewModel.toggle(row) }) {
                                viewModel.open(ShoppingDialog.Edit(row.item.id))
                            }
                        }
                    }
                    if (state.view.bought.isNotEmpty()) {
                        item(key = "bought") { GroupTitle(stringResource(R.string.shopping_bought)) }
                        items(state.view.bought, key = { it.item.id }) { row ->
                            ItemRow(row, state, format, onToggle = { viewModel.toggle(row) }) {
                                viewModel.open(ShoppingDialog.Edit(row.item.id))
                            }
                        }
                    }
                }
            }
        }
    }

    Dialogs(dialog, state, format, viewModel)
}

@Composable
private fun Dialogs(
    dialog: ShoppingDialog?,
    state: ShoppingUiState,
    format: DisplayFormat,
    viewModel: ShoppingViewModel,
) {
    val rows = state.view.groups.flatMap { it.rows } + state.view.bought
    when (dialog) {
        null -> Unit
        ShoppingDialog.NewItem -> ItemSheet(
            row = null,
            categories = state.categories,
            stores = state.stores,
            format = format,
            onSave = viewModel::addFree,
            onDelete = null,
            onDismiss = viewModel::close,
        )
        is ShoppingDialog.Edit -> {
            val row = rows.firstOrNull { it.item.id == dialog.itemId }
            if (row == null) {
                LaunchedEffect(dialog) { viewModel.close() }
            } else {
                ItemSheet(
                    row = row,
                    categories = state.categories,
                    stores = state.stores,
                    format = format,
                    onSave = viewModel::update,
                    onDelete = { viewModel.delete(row.item.id) },
                    onDismiss = viewModel::close,
                )
            }
        }
        is ShoppingDialog.AskPantry -> {
            val row = rows.firstOrNull { it.item.id == dialog.itemId }
            val product = row?.product
            if (row == null || product == null) {
                LaunchedEffect(dialog) { viewModel.close() }
            } else {
                AskPantryDialog(
                    name = product.name,
                    quantity = row.item.quantity?.let { LocalResources.current.quantityText(it, product.unit, format) },
                    onAdd = { viewModel.open(ShoppingDialog.Purchase(row.item.id)) },
                    onDismiss = viewModel::close,
                )
            }
        }
        is ShoppingDialog.Purchase -> {
            val row = rows.firstOrNull { it.item.id == dialog.itemId }
            val summary = row?.product?.let { state.summaries[it.id] }
            if (row == null || summary == null) {
                LaunchedEffect(dialog) { viewModel.close() }
            } else {
                PurchaseSheet(
                    card = summary,
                    locations = state.locations,
                    stores = state.stores,
                    today = state.today,
                    format = format,
                    onConfirm = { quantity, purchasedOn, expiry, location, store ->
                        viewModel.purchase(summary.product.id, quantity, purchasedOn, expiry, location, store)
                    },
                    onDismiss = viewModel::close,
                    initialQuantity = row.item.quantity ?: ONE_UNIT,
                    initialStoreId = summary.product.bestPriceStoreId,
                )
            }
        }
    }
}

@Composable
private fun EmptyList() {
    Box(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.placeholder_shopping_list), textAlign = TextAlign.Center)
    }
}

@Composable
private fun OptionsRow(state: ShoppingUiState, viewModel: ShoppingViewModel) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        MenuChip(
            label = stringResource(state.grouping.label()),
            selected = false,
            options = ShoppingGrouping.entries.map { it to stringResource(it.label()) },
            onSelect = viewModel::setGrouping,
        )
        MenuChip(
            label = state.stores.firstOrNull { it.id == state.storeFilter }?.name
                ?: stringResource(R.string.filter_store),
            selected = state.storeFilter != null,
            options = listOf<Pair<String?, String>>(null to stringResource(R.string.filter_all)) +
                state.stores.map { it.id to it.name },
            onSelect = viewModel::setStoreFilter,
        )
    }
}

private fun ShoppingGrouping.label(): Int = when (this) {
    ShoppingGrouping.CATEGORY -> R.string.shopping_group_category
    ShoppingGrouping.STORE -> R.string.shopping_group_store
}

/** A row of products suggested for the list; one tap adds the product. */
private fun LazyListScope.suggestions(title: Int, products: List<Product>, onAdd: (String) -> Unit) {
    if (products.isEmpty()) return
    item(key = "suggestions-$title") {
        Column(Modifier.padding(bottom = 4.dp)) {
            Text(
                stringResource(title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                products.forEach { product ->
                    val description = stringResource(R.string.cd_add_to_list, product.name)
                    AssistChip(
                        onClick = { onAdd(product.id) },
                        label = { Text(product.name) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        modifier = Modifier.semantics { contentDescription = description },
                    )
                }
            }
        }
    }
}

@Composable
private fun groupName(state: ShoppingUiState, id: String?): String = when (state.grouping) {
    ShoppingGrouping.CATEGORY -> state.categories.firstOrNull { it.id == id }?.name
        ?: stringResource(R.string.shopping_no_category)
    ShoppingGrouping.STORE -> state.stores.firstOrNull { it.id == id }?.name
        ?: stringResource(R.string.shopping_no_store)
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun ItemRow(
    row: ShoppingRow,
    state: ShoppingUiState,
    format: DisplayFormat,
    onToggle: () -> Unit,
    onClick: () -> Unit,
) {
    val resources = LocalResources.current
    val bought = row.item.status == ShoppingStatus.BOUGHT
    val unit = row.product?.unit ?: row.item.unit ?: MeasureUnit.PIECE
    val quantity = row.item.quantity?.let { resources.quantityText(it, unit, format) }
    val cheapestAt = row.storeId
        ?.takeIf { row.product != null && state.grouping == ShoppingGrouping.CATEGORY }
        ?.let { id -> state.stores.firstOrNull { it.id == id }?.name }
    val decoration = if (bought) TextDecoration.LineThrough else null
    val color = if (bought) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
    ) {
        Checkbox(
            checked = bought,
            onCheckedChange = { onToggle() },
            // TalkBack names the item it ticks
            modifier = Modifier.semantics { contentDescription = row.name },
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.name, style = MaterialTheme.typography.bodyLarge, color = color, textDecoration = decoration)
            if (!bought && (row.item.automatic || cheapestAt != null)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (row.item.automatic) AutoBadge()
                    cheapestAt?.let {
                        Text(
                            stringResource(R.string.line_cheapest_at, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        quantity?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = color, textDecoration = decoration)
        }
    }
}

@Composable
private fun AutoBadge() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            stringResource(R.string.badge_min_stock),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun AskPantryDialog(name: String, quantity: String?, onAdd: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tick_title)) },
        text = { Text(listOfNotNull(name, quantity).joinToString(" · ")) },
        confirmButton = {
            TextButton(onClick = onAdd) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_no)) }
        },
    )
}
