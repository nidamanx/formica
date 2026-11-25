// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nidamanx.formica.data.CatalogRepository
import io.github.nidamanx.formica.data.PantryRepository
import io.github.nidamanx.formica.data.ShoppingRepository
import io.github.nidamanx.formica.domain.BatchGroup
import io.github.nidamanx.formica.domain.DurationEstimate
import io.github.nidamanx.formica.domain.GroupKey
import io.github.nidamanx.formica.domain.HistoryEntry
import io.github.nidamanx.formica.domain.ListThresholds
import io.github.nidamanx.formica.domain.ProductSummary
import io.github.nidamanx.formica.domain.RecordRef
import io.github.nidamanx.formica.domain.RecordType
import io.github.nidamanx.formica.domain.StockPlan
import io.github.nidamanx.formica.domain.buildHistory
import io.github.nidamanx.formica.domain.estimateDuration
import io.github.nidamanx.formica.domain.groupBatches
import io.github.nidamanx.formica.domain.model.Category
import io.github.nidamanx.formica.domain.model.Location
import io.github.nidamanx.formica.domain.model.MeasureUnit
import io.github.nidamanx.formica.domain.model.MovementType
import io.github.nidamanx.formica.domain.model.ONE_UNIT
import io.github.nidamanx.formica.domain.model.Product
import io.github.nidamanx.formica.domain.model.Store
import io.github.nidamanx.formica.domain.model.isCountable
import io.github.nidamanx.formica.domain.openItemFor
import io.github.nidamanx.formica.domain.quantitiesByBatch
import io.github.nidamanx.formica.domain.summarizeProduct
import io.github.nidamanx.formica.domain.visibleGroups
import io.github.nidamanx.formica.ui.appContainer
import java.time.LocalDate
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProductDetailState(
    val loaded: Boolean = false,
    /** Null when the product does not exist or was deleted. */
    val summary: ProductSummary? = null,
    val groups: List<BatchGroup> = emptyList(),
    /** False for a single group without dates: its actions move to the top menu (SPEC §4). */
    val showGroups: Boolean = false,
    val estimate: DurationEstimate = DurationEstimate.InsufficientData,
    val history: List<HistoryEntry> = emptyList(),
    val locations: List<Location> = emptyList(),
    val categories: List<Category> = emptyList(),
    val stores: List<Store> = emptyList(),
    /** The product has an open item in the shopping list. */
    val inList: Boolean = false,
    /** "Notes to review" marks: on the product, and the batches that have one. */
    val reviewProduct: Boolean = false,
    val reviewBatches: Set<String> = emptySet(),
    val today: Long = 0,
)

/** The dialog or panel open on the detail screen; [key] null means the whole product. */
sealed interface DetailDialog {
    data object Purchase : DetailDialog
    data object Preparation : DetailDialog
    data class Use(val key: GroupKey?) : DetailDialog
    data class Discard(val key: GroupKey) : DetailDialog
    data class Move(val key: GroupKey) : DetailDialog
    data class Open(val key: GroupKey) : DetailDialog
    data class Correct(val key: GroupKey?) : DetailDialog
    data class Notes(val key: GroupKey) : DetailDialog
    data object Delete : DetailDialog
    data class Undo(val entry: HistoryEntry) : DetailDialog
}

sealed interface DetailMessage {
    data object NothingToUse : DetailMessage
    data class UsedOnly(val amount: Long, val unit: MeasureUnit) : DetailMessage
    data class OnlyWere(val amount: Long, val unit: MeasureUnit) : DetailMessage
    data object NothingToUndo : DetailMessage
    data class UndidOnly(val amount: Long, val unit: MeasureUnit) : DetailMessage
}

class ProductDetailViewModel(
    private val productId: String,
    private val pantry: PantryRepository,
    private val shopping: ShoppingRepository,
    catalog: CatalogRepository,
    thresholds: Flow<ListThresholds>,
    notesToReview: Flow<Set<RecordRef>>,
) : ViewModel() {

    val restockQuestions: Flow<Product> = pantry.restockQuestions

    private val messageChannel = Channel<DetailMessage>(Channel.BUFFERED)
    val messages: Flow<DetailMessage> = messageChannel.receiveAsFlow()

    private val _dialog = MutableStateFlow<DetailDialog?>(null)
    val dialog: StateFlow<DetailDialog?> = _dialog

    private val catalogs = combine(catalog.locations, catalog.categories, catalog.stores) { l, c, s -> Triple(l, c, s) }

    private val listContext = combine(shopping.items, thresholds, notesToReview) { items, limits, marks ->
        Triple(openItemFor(items, productId) != null, limits, marks)
    }

    val state: StateFlow<ProductDetailState> = combine(
        pantry.observeProduct(productId),
        pantry.observeBatches(productId),
        pantry.observeMovements(productId),
        catalogs,
        listContext,
    ) { product, batches, movements, (locations, categories, stores), (inList, limits, marks) ->
        val today = LocalDate.now().toEpochDay()
        val now = System.currentTimeMillis()
        if (product == null || product.deletedAt != null) {
            return@combine ProductDetailState(loaded = true)
        }
        val quantities = quantitiesByBatch(movements)
        val summary = summarizeProduct(product, batches, quantities, movements, today, now, limits)
        val groups = visibleGroups(groupBatches(batches, quantities, product.daysAfterOpening))
        val only = groups.singleOrNull()?.key
        val plain = only != null && only.expiresOn == null && only.preparedOn == null &&
            only.openedOn == null && only.readyFrom == null
        ProductDetailState(
            loaded = true,
            summary = summary,
            groups = groups,
            showGroups = groups.isNotEmpty() && !plain,
            estimate = estimateDuration(movements, summary.quantity, now),
            history = buildHistory(movements, batches.associateBy { it.id }),
            locations = locations,
            categories = categories,
            stores = stores,
            inList = inList,
            reviewProduct = RecordRef(RecordType.PRODUCT, product.id) in marks,
            reviewBatches = batches.filter { RecordRef(RecordType.BATCH, it.id) in marks }.mapTo(HashSet()) { it.id },
            today = today,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProductDetailState())

    private val unit: MeasureUnit?
        get() = state.value.summary?.product?.unit

    fun open(dialog: DetailDialog) {
        _dialog.value = dialog
    }

    fun close() {
        _dialog.value = null
    }

    /** "Mark as opened": asks how many only for counted units with more than one in the group. */
    fun onOpenGroup(group: BatchGroup) {
        val countable = unit?.isCountable == true
        if (countable && group.quantity > ONE_UNIT) {
            _dialog.value = DetailDialog.Open(group.key)
        } else {
            markOpened(group.key, group.quantity)
        }
    }

    fun use(key: GroupKey?, amount: Long) = perform(amount, DetailMessage::UsedOnly, DetailMessage.NothingToUse) {
        if (key == null) {
            pantry.consume(productId, amount)
        } else {
            pantry.removeFromGroup(productId, key, amount, MovementType.CONSUMPTION)
        }
    }

    fun discard(key: GroupKey, amount: Long) = perform(amount, DetailMessage::OnlyWere) {
        pantry.removeFromGroup(productId, key, amount, MovementType.DISCARD)
    }

    fun move(key: GroupKey, amount: Long, toLocationId: String) = perform(amount, DetailMessage::OnlyWere) {
        pantry.transfer(productId, key, amount, toLocationId)
    }

    fun markOpened(key: GroupKey, amount: Long) = perform(amount, DetailMessage::OnlyWere) {
        pantry.open(productId, key, amount, LocalDate.now().toEpochDay())
    }

    fun correct(key: GroupKey?, realQuantity: Long) = perform(0L, DetailMessage::OnlyWere) {
        pantry.correct(productId, key, realQuantity)
    }

    fun purchase(quantity: Long, purchasedOn: Long, expiresOn: Long?, locationId: String, storeId: String?) =
        perform(0L, DetailMessage::OnlyWere) {
            pantry.purchase(productId, quantity, locationId, expiresOn, purchasedOn, storeId)
        }

    fun prepare(quantity: Long, preparedOn: Long, readyFrom: Long?, expiresOn: Long?, locationId: String) =
        perform(0L, DetailMessage::OnlyWere) {
            pantry.prepare(productId, quantity, locationId, preparedOn, expiresOn, readyFrom)
        }

    fun undo(entry: HistoryEntry) = perform(entry.delta, DetailMessage::UndidOnly, DetailMessage.NothingToUndo) {
        pantry.undoAddition(productId, entry.id)
    }

    fun toggleToBuy() {
        val listed = state.value.inList
        viewModelScope.launch { if (listed) shopping.removeFromList(productId) else shopping.toBuy(productId) }
    }

    fun addToList(id: String) {
        viewModelScope.launch { shopping.toBuy(id, automatic = true) }
    }

    /** The notes of a group are written on each of its batches. */
    fun saveNotes(key: GroupKey, notes: String) {
        _dialog.value = null
        val batches = state.value.groups.firstOrNull { it.key == key }?.batches.orEmpty()
        val clean = notes.trim().ifEmpty { null }
        viewModelScope.launch {
            batches.forEach { pantry.updateBatchNotes(productId, it.id, clean) }
        }
    }

    /** The screen closes by itself when the product disappears from [state]. */
    fun delete() {
        _dialog.value = null
        viewModelScope.launch { pantry.deleteProduct(productId) }
    }

    private fun perform(
        requested: Long,
        partial: (Long, MeasureUnit) -> DetailMessage,
        nothing: DetailMessage? = null,
        operation: suspend () -> StockPlan,
    ) {
        _dialog.value = null
        val currentUnit = unit ?: return
        viewModelScope.launch {
            val plan = operation()
            when {
                plan.unallocated == 0L -> Unit
                nothing != null && plan.unallocated == requested -> messageChannel.send(nothing)
                else -> messageChannel.send(partial(requested - plan.unallocated, currentUnit))
            }
        }
    }

    companion object {
        fun factory(productId: String) = viewModelFactory {
            initializer {
                val c = appContainer()
                ProductDetailViewModel(
                    productId,
                    c.pantry,
                    c.shopping,
                    c.catalog,
                    c.listThresholds,
                    c.exchange.notesToReview,
                )
            }
        }
    }
}
