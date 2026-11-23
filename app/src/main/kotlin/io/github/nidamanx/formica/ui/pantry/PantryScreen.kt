// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.pantry

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nidamanx.formica.R
import io.github.nidamanx.formica.domain.ExpiryStatus
import io.github.nidamanx.formica.domain.PantrySort
import io.github.nidamanx.formica.domain.ProductSummary
import io.github.nidamanx.formica.domain.displayedQuantity
import io.github.nidamanx.formica.ui.components.DaysDisc
import io.github.nidamanx.formica.ui.components.DiscTone
import io.github.nidamanx.formica.ui.components.RestockQuestions
import io.github.nidamanx.formica.ui.components.SectionTopBar
import io.github.nidamanx.formica.ui.format.DisplayFormat
import io.github.nidamanx.formica.ui.format.preparedAgo
import io.github.nidamanx.formica.ui.format.quantityText
import io.github.nidamanx.formica.ui.format.rememberDisplayFormat

@Composable
fun PantryScreen(
    onOpenSettings: () -> Unit,
    onNewProduct: () -> Unit,
    onOpenProduct: (String) -> Unit,
    viewModel: PantryViewModel = viewModel(factory = PantryViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    val format = rememberDisplayFormat()
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val text = when (message) {
                PantryMessage.NothingToUse -> resources.getString(R.string.msg_nothing_to_use)
                is PantryMessage.UsedOnly -> resources.getString(
                    R.string.msg_used_only,
                    resources.quantityText(message.amount, message.unit, format),
                )
            }
            snackbar.showSnackbar(text)
        }
    }
    RestockQuestions(viewModel.restockQuestions, snackbar, viewModel::addToList)

    Scaffold(
        topBar = { SectionTopBar(stringResource(R.string.nav_pantry), onOpenSettings) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewProduct) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_new_product))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (state.loaded && !state.pantryEmpty) {
                OutlinedTextField(
                    value = state.filter.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = { Text(stringResource(R.string.pantry_search)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
                FilterRow(state, viewModel)
            }
            when {
                !state.loaded -> Unit
                state.pantryEmpty -> CenteredText(stringResource(R.string.placeholder_pantry))
                state.cards.isEmpty() -> CenteredText(stringResource(R.string.pantry_no_match))
                else -> {
                    val locationNames = state.locations.associate { it.id to it.name }
                    LazyColumn(
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.cards, key = { it.product.id }) { card ->
                            ProductCard(
                                card = card,
                                today = state.today,
                                locationNames = locationNames,
                                format = format,
                                inList = card.product.id in state.inList,
                                review = card.product.id in state.review,
                                onClick = { onOpenProduct(card.product.id) },
                                onToBuy = { viewModel.toggleToBuy(card) },
                                onMinus = { viewModel.onMinus(card) },
                                onPlus = { viewModel.onPlus(card) },
                            )
                        }
                    }
                }
            }
        }
    }

    sheet?.let { current ->
        val card = state.cards.firstOrNull { it.product.id == current.productId }
        if (card == null) {
            LaunchedEffect(current) { viewModel.closeSheet() }
        } else {
            when (current) {
                is PantrySheet.Purchase -> PurchaseSheet(
                    card = card,
                    locations = state.locations,
                    stores = state.stores,
                    today = state.today,
                    format = format,
                    onConfirm = { quantity, purchasedOn, expiry, location, store ->
                        viewModel.addPurchase(card.product.id, quantity, purchasedOn, expiry, location, store)
                    },
                    onDismiss = viewModel::closeSheet,
                )
                is PantrySheet.Preparation -> PreparationSheet(
                    card = card,
                    locations = state.locations,
                    today = state.today,
                    format = format,
                    onConfirm = { quantity, preparedOn, ready, expiry, location ->
                        viewModel.addPreparation(card.product.id, quantity, preparedOn, ready, expiry, location)
                    },
                    onDismiss = viewModel::closeSheet,
                )
                is PantrySheet.Use -> UseSheet(
                    card = card,
                    format = format,
                    onConfirm = { amount -> viewModel.use(card.product.id, amount) },
                    onDismiss = viewModel::closeSheet,
                )
            }
        }
    }
}

@Composable
private fun CenteredText(text: String) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FilterRow(state: PantryUiState, viewModel: PantryViewModel) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        MenuChip(
            label = stringResource(state.sort.label()),
            selected = false,
            leading = { Icon(painterResource(R.drawable.ic_sort), contentDescription = null, Modifier.size(18.dp)) },
            options = PantrySort.entries.map { it to stringResource(it.label()) },
            onSelect = viewModel::setSort,
        )
        val all = stringResource(R.string.filter_all)
        MenuChip(
            label = state.locations.firstOrNull { it.id == state.filter.locationId }?.name
                ?: stringResource(R.string.filter_location),
            selected = state.filter.locationId != null,
            options = listOf<Pair<String?, String>>(null to all) + state.locations.map { it.id to it.name },
            onSelect = viewModel::setLocation,
        )
        MenuChip(
            label = state.categories.firstOrNull { it.id == state.filter.categoryId }?.name
                ?: stringResource(R.string.filter_category),
            selected = state.filter.categoryId != null,
            options = listOf<Pair<String?, String>>(null to all) + state.categories.map { it.id to it.name },
            onSelect = viewModel::setCategory,
        )
        FilterChip(
            selected = state.filter.homemadeOnly,
            onClick = viewModel::toggleHomemade,
            label = { Text(stringResource(R.string.label_homemade)) },
            leadingIcon = if (state.filter.homemadeOnly) {
                { Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(18.dp)) }
            } else {
                null
            },
        )
    }
}

private fun PantrySort.label(): Int = when (this) {
    PantrySort.NAME -> R.string.sort_name
    PantrySort.EXPIRY -> R.string.sort_expiry
    PantrySort.PREPARATION -> R.string.sort_preparation
}

/** A filter chip that opens a menu of [options] (value and label). */
@Composable
fun <T> MenuChip(
    label: String,
    selected: Boolean,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    leading: (@Composable () -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected,
            onClick = { open = true },
            label = { Text(label) },
            leadingIcon = leading,
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, Modifier.size(18.dp)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        open = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

@Composable
private fun ProductCard(
    card: ProductSummary,
    today: Long,
    locationNames: Map<String, String>,
    format: DisplayFormat,
    inList: Boolean,
    review: Boolean,
    onClick: () -> Unit,
    onToBuy: () -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    val resources = LocalResources.current
    val product = card.product
    val secondLine = listOfNotNull(
        when {
            card.nearestExpiry != null && card.expiryStatus == ExpiryStatus.EXPIRED ->
                stringResource(R.string.line_expired, format.date(card.nearestExpiry))
            card.nearestExpiry != null -> stringResource(R.string.line_expires, format.date(card.nearestExpiry))
            card.readyFrom != null -> stringResource(R.string.line_ready_from, format.date(card.readyFrom))
            card.oldestPreparedOn != null -> resources.preparedAgo(card.oldestPreparedOn, today)
            else -> null
        },
        card.locationIds.mapNotNull { locationNames[it] }.takeIf { it.isNotEmpty() }?.joinToString(", "),
    ).joinToString(" · ")
    val badges = listOfNotNull(
        if (card.readyFrom != null) R.string.badge_not_ready else null,
        if (card.toMake) R.string.badge_to_make else null,
        if (card.runningLow) R.string.badge_running_low else null,
        if (card.needsCheck) R.string.badge_check else null,
        if (review) R.string.badge_notes_review else null,
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        ) {
            ExpiryDisc(card, today)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        product.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (product.homemade) {
                        Icon(
                            painterResource(R.drawable.ic_homemade),
                            contentDescription = stringResource(R.string.label_homemade),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    if (card.hasNotes) {
                        Icon(
                            painterResource(R.drawable.ic_notes),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                if (secondLine.isNotEmpty()) {
                    Text(
                        secondLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (badges.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        badges.forEach { Badge(stringResource(it), warning = it == R.string.badge_check) }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    resources.quantityText(displayedQuantity(card.quantity), product.unit, format),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Row {
                    IconButton(onClick = onToBuy) {
                        if (inList) {
                            Icon(
                                Icons.Filled.ShoppingCart,
                                contentDescription = stringResource(R.string.action_remove_from_list),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Icon(
                                painterResource(R.drawable.ic_add_shopping_cart),
                                contentDescription = stringResource(R.string.action_to_buy),
                            )
                        }
                    }
                    IconButton(onClick = onMinus) {
                        Icon(painterResource(R.drawable.ic_remove), contentDescription = stringResource(R.string.cd_use))
                    }
                    IconButton(onClick = onPlus) {
                        val plus = if (product.homemade) R.string.preparation_title else R.string.cd_add_purchase
                        Icon(Icons.Filled.Add, contentDescription = stringResource(plus))
                    }
                }
            }
        }
    }
}

/** Days left to the nearest expiry, colored by status. */
@Composable
private fun ExpiryDisc(card: ProductSummary, today: Long) {
    val tone = when {
        card.expiryStatus == ExpiryStatus.EXPIRED -> DiscTone.EXPIRED
        card.expiryStatus == ExpiryStatus.EXPIRING -> DiscTone.EXPIRING
        card.readyFrom != null -> DiscTone.PREPARING
        else -> DiscTone.NEUTRAL
    }
    val statusText = when (card.expiryStatus) {
        ExpiryStatus.EXPIRED -> stringResource(R.string.badge_expired)
        ExpiryStatus.EXPIRING -> stringResource(R.string.badge_expiring)
        else -> ""
    }
    DaysDisc(card.nearestExpiry?.let { it - today }, tone, statusText)
}

@Composable
private fun Badge(text: String, warning: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (warning) Color.Transparent else colors.secondaryContainer,
        contentColor = if (warning) colors.error else colors.onSecondaryContainer,
        border = if (warning) androidx.compose.foundation.BorderStroke(1.dp, colors.error) else null,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
