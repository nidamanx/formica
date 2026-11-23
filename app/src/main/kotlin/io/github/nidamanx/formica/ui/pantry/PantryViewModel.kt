// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.pantry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nidamanx.formica.data.CatalogRepository
import io.github.nidamanx.formica.data.PantryRepository
import io.github.nidamanx.formica.domain.PantryFilter
import io.github.nidamanx.formica.domain.PantrySort
import io.github.nidamanx.formica.domain.ProductSummary
import io.github.nidamanx.formica.domain.StockPlan
import io.github.nidamanx.formica.domain.filterAndSort
import io.github.nidamanx.formica.domain.model.Category
import io.github.nidamanx.formica.domain.model.Location
import io.github.nidamanx.formica.domain.model.MeasureUnit
import io.github.nidamanx.formica.domain.model.ONE_UNIT
import io.github.nidamanx.formica.domain.model.Store
import io.github.nidamanx.formica.domain.model.isCountable
import io.github.nidamanx.formica.domain.summarizeProduct
import io.github.nidamanx.formica.settings.DisplayDefaults
import io.github.nidamanx.formica.ui.appContainer
import java.text.Collator
import java.time.LocalDate
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PantryUiState(
    val loaded: Boolean = false,
    /** True when the pantry has no product at all (not just none matching the filters). */
    val pantryEmpty: Boolean = true,
    val cards: List<ProductSummary> = emptyList(),
    val locations: List<Location> = emptyList(),
    val categories: List<Category> = emptyList(),
    val stores: List<Store> = emptyList(),
    val filter: PantryFilter = PantryFilter(),
    val sort: PantrySort = PantrySort.EXPIRY,
    val today: Long = 0,
)

/** The panel opened by the − and + buttons of a card. */
sealed interface PantrySheet {
    val productId: String

    data class Purchase(override val productId: String) : PantrySheet
    data class Preparation(override val productId: String) : PantrySheet
    data class Use(override val productId: String) : PantrySheet
}

sealed interface PantryMessage {
    data object NothingToUse : PantryMessage
    data class UsedOnly(val amount: Long, val unit: MeasureUnit) : PantryMessage
}

class PantryViewModel(
    private val pantry: PantryRepository,
    catalog: CatalogRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(PantryFilter())
    private val sort = MutableStateFlow(PantrySort.EXPIRY)
    private val messageChannel = Channel<PantryMessage>(Channel.BUFFERED)
    val messages: Flow<PantryMessage> = messageChannel.receiveAsFlow()

    private val _sheet = MutableStateFlow<PantrySheet?>(null)
    val sheet: StateFlow<PantrySheet?> = _sheet

    private val summaries: Flow<List<ProductSummary>> = combine(
        pantry.products,
        pantry.batches,
        pantry.batchQuantities,
        pantry.movements,
    ) { products, batches, quantities, movements ->
        val today = LocalDate.now().toEpochDay()
        val now = System.currentTimeMillis()
        val batchesByProduct = batches.groupBy { it.productId }
        val productOfBatch = batches.associate { it.id to it.productId }
        val movementsByProduct = movements.groupBy { productOfBatch[it.batchId] }
        products.map { product ->
            summarizeProduct(
                product = product,
                productBatches = batchesByProduct[product.id].orEmpty(),
                quantities = quantities,
                productMovements = movementsByProduct[product.id].orEmpty(),
                today = today,
                now = now,
                thresholds = DisplayDefaults.thresholds,
            )
        }
    }

    private val catalogs = combine(catalog.locations, catalog.categories, catalog.stores) { l, c, s -> Triple(l, c, s) }

    val state: StateFlow<PantryUiState> = combine(summaries, catalogs, filter, sort) { all, (l, c, s), f, so ->
        val collator = Collator.getInstance()
        PantryUiState(
            loaded = true,
            pantryEmpty = all.isEmpty(),
            cards = filterAndSort(all, f, so) { a, b -> collator.compare(a, b) },
            locations = l,
            categories = c,
            stores = s,
            filter = f,
            sort = so,
            today = LocalDate.now().toEpochDay(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PantryUiState())

    fun setQuery(query: String) = filter.update { it.copy(query = query) }
    fun setLocation(locationId: String?) = filter.update { it.copy(locationId = locationId) }
    fun setCategory(categoryId: String?) = filter.update { it.copy(categoryId = categoryId) }
    fun toggleHomemade() = filter.update { it.copy(homemadeOnly = !it.homemadeOnly) }
    fun setSort(value: PantrySort) {
        sort.value = value
    }

    /** − on a card: countable units are used one at a time, measured ones ask the amount. */
    fun onMinus(card: ProductSummary) {
        if (card.product.unit.isCountable) {
            use(card.product.id, ONE_UNIT)
        } else {
            _sheet.value = PantrySheet.Use(card.product.id)
        }
    }

    /** + on a card: a new preparation for homemade products, a purchase for the others. */
    fun onPlus(card: ProductSummary) {
        val id = card.product.id
        _sheet.value = if (card.product.homemade) PantrySheet.Preparation(id) else PantrySheet.Purchase(id)
    }

    fun closeSheet() {
        _sheet.value = null
    }

    fun use(productId: String, amount: Long) {
        _sheet.value = null
        val unit = state.value.cards.firstOrNull { it.product.id == productId }?.product?.unit ?: return
        viewModelScope.launch { report(pantry.consume(productId, amount), amount, unit) }
    }

    fun addPurchase(
        productId: String,
        quantity: Long,
        purchasedOn: Long,
        expiresOn: Long?,
        locationId: String,
        storeId: String?,
    ) {
        _sheet.value = null
        viewModelScope.launch { pantry.purchase(productId, quantity, locationId, expiresOn, purchasedOn, storeId) }
    }

    fun addPreparation(
        productId: String,
        quantity: Long,
        preparedOn: Long,
        readyFrom: Long?,
        expiresOn: Long?,
        locationId: String,
    ) {
        _sheet.value = null
        viewModelScope.launch { pantry.prepare(productId, quantity, locationId, preparedOn, expiresOn, readyFrom) }
    }

    private suspend fun report(plan: StockPlan, requested: Long, unit: MeasureUnit) {
        when {
            plan.unallocated == requested -> messageChannel.send(PantryMessage.NothingToUse)
            plan.unallocated > 0 -> messageChannel.send(PantryMessage.UsedOnly(requested - plan.unallocated, unit))
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { PantryViewModel(appContainer().pantry, appContainer().catalog) }
        }
    }
}
