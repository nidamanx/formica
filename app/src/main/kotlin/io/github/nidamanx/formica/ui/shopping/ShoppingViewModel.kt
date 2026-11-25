// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nidamanx.formica.data.CatalogRepository
import io.github.nidamanx.formica.data.PantryRepository
import io.github.nidamanx.formica.data.ShoppingRepository
import io.github.nidamanx.formica.domain.ListThresholds
import io.github.nidamanx.formica.domain.ProductSummary
import io.github.nidamanx.formica.domain.ShoppingGrouping
import io.github.nidamanx.formica.domain.ShoppingRow
import io.github.nidamanx.formica.domain.ShoppingView
import io.github.nidamanx.formica.domain.TickBehavior
import io.github.nidamanx.formica.domain.frequentProducts
import io.github.nidamanx.formica.domain.model.Category
import io.github.nidamanx.formica.domain.model.Location
import io.github.nidamanx.formica.domain.model.Product
import io.github.nidamanx.formica.domain.model.ShoppingItem
import io.github.nidamanx.formica.domain.model.ShoppingStatus
import io.github.nidamanx.formica.domain.model.Store
import io.github.nidamanx.formica.domain.runningLowSuggestions
import io.github.nidamanx.formica.domain.shoppingView
import io.github.nidamanx.formica.settings.ShoppingSettings
import io.github.nidamanx.formica.ui.appContainer
import io.github.nidamanx.formica.ui.productSummaries
import java.text.Collator
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ShoppingUiState(
    val loaded: Boolean = false,
    /** No item at all, before the store filter. */
    val empty: Boolean = true,
    val view: ShoppingView = ShoppingView(emptyList(), emptyList()),
    val runningLow: List<Product> = emptyList(),
    val frequent: List<Product> = emptyList(),
    val grouping: ShoppingGrouping = ShoppingGrouping.CATEGORY,
    val storeFilter: String? = null,
    val categories: List<Category> = emptyList(),
    val stores: List<Store> = emptyList(),
    val locations: List<Location> = emptyList(),
    /** Summaries by product id, for the purchase panel. */
    val summaries: Map<String, ProductSummary> = emptyMap(),
    val today: Long = 0,
)

/** The dialog or panel open on the list. */
sealed interface ShoppingDialog {
    data object NewItem : ShoppingDialog
    data class Edit(val itemId: String) : ShoppingDialog
    data class AskPantry(val itemId: String) : ShoppingDialog
    data class Purchase(val itemId: String) : ShoppingDialog
}

private data class ListOptions(val grouping: ShoppingGrouping, val storeFilter: String?)

private data class Catalogs(val categories: List<Category>, val stores: List<Store>, val locations: List<Location>)

class ShoppingViewModel(
    private val shopping: ShoppingRepository,
    private val pantry: PantryRepository,
    catalog: CatalogRepository,
    private val settings: ShoppingSettings,
    thresholds: Flow<ListThresholds>,
) : ViewModel() {

    private val options = MutableStateFlow(ListOptions(ShoppingGrouping.CATEGORY, null))

    private val _dialog = MutableStateFlow<ShoppingDialog?>(null)
    val dialog: StateFlow<ShoppingDialog?> = _dialog

    private val catalogs = combine(catalog.categories, catalog.stores, catalog.locations) { c, s, l ->
        Catalogs(c, s, l)
    }

    private val pantryData: Flow<Pair<List<ProductSummary>, Map<String, String>>> = combine(
        productSummaries(pantry, thresholds),
        pantry.batches,
    ) { summaries, batches -> summaries to batches.associate { it.id to it.productId } }

    val state: StateFlow<ShoppingUiState> = combine(
        shopping.items,
        pantryData,
        pantry.movements,
        catalogs,
        options,
    ) { items, (summaries, batchProduct), movements, cat, opts ->
        val collator = Collator.getInstance()
        val names = Comparator<String> { a, b -> collator.compare(a, b) }
        val products = summaries.associate { it.product.id to it.product }
        val groupOrder = when (opts.grouping) {
            ShoppingGrouping.CATEGORY -> cat.categories.map { it.id }
            ShoppingGrouping.STORE -> cat.stores.map { it.id }
        }
        // A filter on a store deleted meanwhile is dropped
        val storeFilter = opts.storeFilter?.takeIf { id -> cat.stores.any { it.id == id } }
        val view = shoppingView(items, products, opts.grouping, storeFilter, groupOrder, names)
        val active = summaries.filter { it.product.deletedAt == null }
        ShoppingUiState(
            loaded = true,
            empty = items.none { it.deletedAt == null },
            view = view,
            runningLow = runningLowSuggestions(active, items, names),
            frequent = frequentProducts(
                active.map { it.product }, movements, batchProduct, items, System.currentTimeMillis(), names,
            ),
            grouping = opts.grouping,
            storeFilter = storeFilter,
            categories = cat.categories,
            stores = cat.stores,
            locations = cat.locations,
            summaries = active.associateBy { it.product.id },
            today = LocalDate.now().toEpochDay(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShoppingUiState())

    fun setGrouping(value: ShoppingGrouping) {
        options.value = options.value.copy(grouping = value)
    }

    fun setStoreFilter(storeId: String?) {
        options.value = options.value.copy(storeFilter = storeId)
    }

    fun open(dialog: ShoppingDialog) {
        _dialog.value = dialog
    }

    fun close() {
        _dialog.value = null
    }

    fun addSuggestion(productId: String) {
        viewModelScope.launch { shopping.toBuy(productId) }
    }

    /**
     * Ticks or unticks a row. Ticking a linked item may add it to the pantry, as set:
     * ask first, open the purchase panel, or nothing.
     */
    fun toggle(row: ShoppingRow) {
        val item = row.item
        val bought = item.status == ShoppingStatus.BOUGHT
        viewModelScope.launch {
            shopping.setBought(item.id, !bought)
            if (bought || row.product == null) return@launch
            _dialog.value = when (settings.current().onTick) {
                TickBehavior.ASK -> ShoppingDialog.AskPantry(item.id)
                TickBehavior.ALWAYS -> ShoppingDialog.Purchase(item.id)
                TickBehavior.NEVER -> null
            }
        }
    }

    fun purchase(
        productId: String,
        quantity: Long,
        purchasedOn: Long,
        expiresOn: Long?,
        locationId: String,
        storeId: String?,
    ) {
        _dialog.value = null
        viewModelScope.launch { pantry.purchase(productId, quantity, locationId, expiresOn, purchasedOn, storeId) }
    }

    fun addFree(draft: ShoppingItem) {
        _dialog.value = null
        viewModelScope.launch { shopping.addFree(draft) }
    }

    fun update(edited: ShoppingItem) {
        _dialog.value = null
        viewModelScope.launch { shopping.update(edited) }
    }

    fun delete(id: String) {
        _dialog.value = null
        viewModelScope.launch { shopping.delete(id) }
    }

    fun clearBought() {
        viewModelScope.launch { shopping.clearBought() }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                ShoppingViewModel(c.shopping, c.pantry, c.catalog, c.shoppingSettings, c.listThresholds)
            }
        }
    }
}
