// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.product

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nidamanx.formica.R
import io.github.nidamanx.formica.domain.BatchGroup
import io.github.nidamanx.formica.domain.DurationEstimate
import io.github.nidamanx.formica.domain.HistoryEntry
import io.github.nidamanx.formica.domain.displayedQuantity
import io.github.nidamanx.formica.domain.effectiveExpiry
import io.github.nidamanx.formica.domain.isBestPriceStale
import io.github.nidamanx.formica.domain.model.MeasureUnit
import io.github.nidamanx.formica.domain.model.MovementType
import io.github.nidamanx.formica.domain.model.ONE_UNIT
import io.github.nidamanx.formica.domain.model.Origin
import io.github.nidamanx.formica.domain.model.Product
import io.github.nidamanx.formica.domain.model.Store
import io.github.nidamanx.formica.ui.components.RestockQuestions
import io.github.nidamanx.formica.ui.format.DisplayFormat
import io.github.nidamanx.formica.ui.format.quantityText
import io.github.nidamanx.formica.ui.format.rememberDisplayFormat
import io.github.nidamanx.formica.ui.pantry.PreparationSheet
import io.github.nidamanx.formica.ui.pantry.PurchaseSheet
import io.github.nidamanx.formica.ui.pantry.UseSheet
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    productId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    viewModel: ProductDetailViewModel = viewModel(factory = ProductDetailViewModel.factory(productId)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val format = rememberDisplayFormat()
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }

    // Deleted here or by an import: nothing left to show
    LaunchedEffect(state.loaded, state.summary == null) {
        if (state.loaded && state.summary == null) onBack()
    }
    RestockQuestions(viewModel.restockQuestions, snackbar, viewModel::addToList)
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbar.showSnackbar(
                when (message) {
                    DetailMessage.NothingToUse -> resources.getString(R.string.msg_nothing_to_use)
                    is DetailMessage.UsedOnly -> resources.getString(
                        R.string.msg_used_only,
                        resources.quantityText(message.amount, message.unit, format),
                    )
                    is DetailMessage.OnlyWere -> resources.getString(
                        R.string.msg_only_were,
                        resources.quantityText(message.amount, message.unit, format),
                    )
                },
            )
        }
    }

    val summary = state.summary ?: return
    val product = summary.product
    val locationNames = state.locations.associate { it.id to it.name }
    val storeNames = state.stores.associate { it.id to it.name }
    val plainGroup = state.groups.singleOrNull()?.takeIf { !state.showGroups }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(product.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
                    }
                    OverflowMenu(
                        buildList<Pair<Int, () -> Unit>> {
                            if (product.homemade) {
                                add(R.string.purchase_title to { viewModel.open(DetailDialog.Purchase) })
                            }
                            add(listActionLabel(state.inList) to viewModel::toggleToBuy)
                            if (plainGroup != null) addAll(groupActions(plainGroup, viewModel))
                            add(R.string.menu_correct to { viewModel.open(DetailDialog.Correct(null)) })
                            add(R.string.action_delete to { viewModel.open(DetailDialog.Delete) })
                        },
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.padding(padding),
        ) {
            item {
                Header(
                    state = state,
                    format = format,
                    locationNames = locationNames,
                    onUse = { viewModel.open(DetailDialog.Use(null)) },
                    onAdd = {
                        viewModel.open(if (product.homemade) DetailDialog.Preparation else DetailDialog.Purchase)
                    },
                )
            }
            bestPrice(product, state.stores)?.let { best ->
                item { BestPriceBox(best, isBestPriceStale(product.bestPriceNotedAt, state.today), format) }
            }
            if (state.showGroups) {
                item { SectionTitle(stringResource(R.string.section_stock)) }
                items(state.groups, key = { it.batches.first().id }) { group ->
                    GroupRow(
                        group, product.unit, product.daysAfterOpening, state.today, locationNames, storeNames, format,
                    ) {
                        OverflowMenu(groupActions(group, viewModel))
                    }
                }
            }
            if (state.history.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.section_history)) }
                items(state.history, key = { it.id }) { entry ->
                    HistoryRow(entry, product.unit, locationNames, format, resources)
                }
            }
        }
    }

    when (val current = dialog) {
        null -> Unit
        DetailDialog.Purchase -> PurchaseSheet(
            card = summary,
            locations = state.locations,
            stores = state.stores,
            today = state.today,
            format = format,
            onConfirm = viewModel::purchase,
            onDismiss = viewModel::close,
        )
        DetailDialog.Preparation -> PreparationSheet(
            card = summary,
            locations = state.locations,
            today = state.today,
            format = format,
            onConfirm = viewModel::prepare,
            onDismiss = viewModel::close,
        )
        is DetailDialog.Use -> UseSheet(
            card = summary,
            format = format,
            onConfirm = { viewModel.use(current.key, it) },
            onDismiss = viewModel::close,
        )
        is DetailDialog.Discard -> AmountDialog(
            title = stringResource(R.string.discard_title),
            initial = "",
            format = format,
            onConfirm = { viewModel.discard(current.key, it) },
            onDismiss = viewModel::close,
        )
        is DetailDialog.Open -> AmountDialog(
            title = stringResource(R.string.open_title),
            help = stringResource(R.string.open_how_many),
            initial = format.number(ONE_UNIT),
            format = format,
            onConfirm = { viewModel.markOpened(current.key, it) },
            onDismiss = viewModel::close,
        )
        is DetailDialog.Correct -> {
            val currentQuantity = current.key
                ?.let { key -> state.groups.firstOrNull { it.key == key }?.quantity }
                ?: summary.quantity
            AmountDialog(
                title = stringResource(R.string.correct_title),
                help = stringResource(R.string.correct_help),
                initial = format.number(displayedQuantity(currentQuantity)),
                format = format,
                allowZero = true,
                onConfirm = { viewModel.correct(current.key, it) },
                onDismiss = viewModel::close,
            )
        }
        is DetailDialog.Move -> {
            val group = state.groups.firstOrNull { it.key == current.key }
            if (group == null) {
                LaunchedEffect(current) { viewModel.close() }
            } else {
                MoveDialog(
                    group = group,
                    locations = state.locations.filter { it.id != group.key.locationId },
                    format = format,
                    onConfirm = { amount, to -> viewModel.move(current.key, amount, to) },
                    onDismiss = viewModel::close,
                )
            }
        }
        is DetailDialog.Notes -> NotesDialog(
            initial = state.groups.firstOrNull { it.key == current.key }
                ?.batches?.firstNotNullOfOrNull { it.notes }.orEmpty(),
            onConfirm = { viewModel.saveNotes(current.key, it) },
            onDismiss = viewModel::close,
        )
        DetailDialog.Delete -> DeleteDialog(product.name, onConfirm = viewModel::delete, onDismiss = viewModel::close)
    }
}

private fun listActionLabel(inList: Boolean): Int =
    if (inList) R.string.action_remove_from_list else R.string.action_to_buy

private fun groupActions(group: BatchGroup, viewModel: ProductDetailViewModel): List<Pair<Int, () -> Unit>> = listOf(
    R.string.menu_use to { viewModel.open(DetailDialog.Use(group.key)) },
    R.string.menu_move to { viewModel.open(DetailDialog.Move(group.key)) },
    R.string.menu_open to { viewModel.onOpenGroup(group) },
    R.string.menu_discard to { viewModel.open(DetailDialog.Discard(group.key)) },
    R.string.menu_correct to { viewModel.open(DetailDialog.Correct(group.key)) },
    R.string.menu_notes to { viewModel.open(DetailDialog.Notes(group.key)) },
)

@Composable
private fun OverflowMenu(actions: List<Pair<Int, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            // The same label may appear twice (product and group correction): the first wins
            actions.distinctBy { it.first }.forEach { (label, action) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        open = false
                        action()
                    },
                )
            }
        }
    }
}

@Composable
private fun Header(
    state: ProductDetailState,
    format: DisplayFormat,
    locationNames: Map<String, String>,
    onUse: () -> Unit,
    onAdd: () -> Unit,
) {
    val summary = state.summary ?: return
    val product = summary.product
    val resources = LocalResources.current
    val meta = listOfNotNull(
        if (product.homemade) stringResource(R.string.label_homemade) else null,
        state.categories.firstOrNull { it.id == product.categoryId }?.name,
        summary.locationIds.mapNotNull { locationNames[it] }.takeIf { it.isNotEmpty() }?.joinToString(", "),
    ).joinToString(" · ")
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                resources.quantityText(displayedQuantity(summary.quantity), product.unit, format),
                style = MaterialTheme.typography.headlineMedium,
            )
            if (summary.toMake) SmallBadge(stringResource(R.string.badge_to_make))
        }
        if (meta.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (product.homemade) {
                    Icon(
                        painterResource(R.drawable.ic_homemade),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                SecondaryText(meta)
            }
        }
        SecondaryText(stringResource(R.string.estimate_label, resources.estimateText(state.estimate)))
        product.notes?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            Button(onClick = onUse) {
                Text(stringResource(R.string.use_title))
            }
            FilledTonalButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(if (product.homemade) R.string.preparation_title else R.string.purchase_title))
            }
        }
    }
}

@Composable
private fun SecondaryText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun GroupRow(
    group: BatchGroup,
    unit: MeasureUnit,
    daysAfterOpening: Int?,
    today: Long,
    locationNames: Map<String, String>,
    storeNames: Map<String, String>,
    format: DisplayFormat,
    menu: @Composable () -> Unit,
) {
    val resources = LocalResources.current
    val key = group.key
    val expiry = effectiveExpiry(group.batches.first(), daysAfterOpening)
    val details = listOfNotNull(
        key.preparedOn?.let { stringResource(R.string.line_made_on, format.date(it)) },
        boughtLine(group, storeNames, format),
        expiry?.let {
            stringResource(if (it < today) R.string.line_expired else R.string.line_expires, format.date(it))
        },
        key.openedOn?.let { stringResource(R.string.line_opened, format.date(it)) },
        key.readyFrom?.takeIf { it > today }?.let { stringResource(R.string.line_ready_from, format.date(it)) },
        locationNames[key.locationId],
        group.batches.firstNotNullOfOrNull { it.notes?.lineSequence()?.firstOrNull() },
    ).joinToString(" · ")
    val badges = listOfNotNull(
        if (key.openedOn != null) R.string.badge_opened else null,
        if (key.readyFrom != null && key.readyFrom > today) R.string.badge_not_ready else null,
    )
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        resources.quantityText(group.quantity, unit, format),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    badges.forEach { SmallBadge(stringResource(it)) }
                }
                if (details.isNotEmpty()) SecondaryText(details)
            }
            menu()
        }
    }
}

private class BestPrice(val storeName: String?, val cents: Long?, val reference: String?, val notedAt: Long?)

private fun bestPrice(product: Product, stores: List<Store>): BestPrice? {
    val storeName = product.bestPriceStoreId?.let { id -> stores.firstOrNull { it.id == id }?.name }
    if (storeName == null && product.bestPriceCents == null && product.bestPriceReference.isNullOrBlank()) return null
    return BestPrice(storeName, product.bestPriceCents, product.bestPriceReference, product.bestPriceNotedAt)
}

/** "Cheapest at": store, price and reference, faded when noted more than 6 months ago. */
@Composable
private fun BestPriceBox(best: BestPrice, stale: Boolean, format: DisplayFormat) {
    val line = listOfNotNull(
        best.storeName,
        listOfNotNull(best.cents?.let { format.price(it) }, best.reference?.takeIf { it.isNotBlank() })
            .takeIf { it.isNotEmpty() }?.joinToString(" "),
    ).joinToString(" · ")
    val noted = best.notedAt?.let {
        val day = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        stringResource(R.string.best_noted_on, format.date(day))
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .alpha(if (stale) STALE_ALPHA else 1f),
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                stringResource(R.string.best_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodyLarge)
            noted?.let { SecondaryText(it) }
        }
    }
}

private const val STALE_ALPHA = 0.55f

/** Purchase date and store, shown only when every batch of the group agrees on them. */
@Composable
private fun boughtLine(group: BatchGroup, storeNames: Map<String, String>, format: DisplayFormat): String? {
    if (group.key.origin != Origin.PURCHASED) return null
    val date = group.batches.map { it.purchasedOn }.distinct().singleOrNull()
    val store = group.batches.map { it.storeId }.distinct().singleOrNull()?.let { storeNames[it] }
    return when {
        date != null && store != null -> stringResource(R.string.line_bought_on_at, format.date(date), store)
        date != null -> stringResource(R.string.line_bought_on, format.date(date))
        store != null -> stringResource(R.string.line_bought_at, store)
        else -> null
    }
}

@Composable
private fun SmallBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    unit: MeasureUnit,
    locationNames: Map<String, String>,
    format: DisplayFormat,
    resources: Resources,
) {
    val day = Instant.ofEpochMilli(entry.timestamp).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
    val label = when {
        entry.isOpening -> stringResource(R.string.history_opening)
        entry.type == MovementType.TRANSFER -> stringResource(
            R.string.history_transfer,
            entry.fromLocationId?.let { locationNames[it] }.orEmpty(),
            entry.toLocationId?.let { locationNames[it] }.orEmpty(),
        )
        else -> stringResource(entry.type.historyLabel())
    }
    val amount = resources.quantityText(Math.abs(entry.delta), unit, format)
    val signed = when {
        entry.type == MovementType.TRANSFER -> amount
        entry.delta < 0 -> "−$amount"
        else -> "+$amount"
    }
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            Text(
                "${format.date(day)} · $label",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(signed, style = MaterialTheme.typography.bodyMedium)
        }
        HorizontalDivider()
    }
}

private fun MovementType.historyLabel(): Int = when (this) {
    MovementType.PURCHASE -> R.string.history_purchase
    MovementType.PREPARATION -> R.string.history_preparation
    MovementType.CONSUMPTION -> R.string.history_consumption
    MovementType.CORRECTION -> R.string.history_correction
    MovementType.DISCARD -> R.string.history_discard
    MovementType.TRANSFER -> R.string.history_opening
}

private fun Resources.estimateText(estimate: DurationEstimate): String = when (estimate) {
    DurationEstimate.InsufficientData -> getString(R.string.estimate_insufficient)
    is DurationEstimate.Days -> estimate.count.toInt().let { getQuantityString(R.plurals.estimate_days, it, it) }
    is DurationEstimate.Weeks -> estimate.count.toInt().let { getQuantityString(R.plurals.estimate_weeks, it, it) }
    is DurationEstimate.Months -> estimate.count.toInt().let { getQuantityString(R.plurals.estimate_months, it, it) }
    DurationEstimate.MoreThanAYear -> getString(R.string.estimate_more_than_year)
}
