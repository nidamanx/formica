// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.domain

import io.github.nidamanx.formica.domain.model.Movement
import io.github.nidamanx.formica.domain.model.MovementType
import io.github.nidamanx.formica.domain.model.Product
import io.github.nidamanx.formica.domain.model.ShoppingItem
import io.github.nidamanx.formica.domain.model.ShoppingStatus
import java.time.LocalDate

private const val DAY_MILLIS = 86_400_000L
private const val FREQUENT_WINDOW_DAYS = 180L
private const val FREQUENT_MAX = 10
private const val BEST_PRICE_FRESH_MONTHS = 6L

/** What a tick on a linked item does to the pantry (SPEC §8). */
enum class TickBehavior { ASK, ALWAYS, NEVER }

/** What happens when a product drops under its minimum stock (SPEC §8, §15). */
enum class MinStockBehavior { AUTOMATIC, ASK }

enum class ShoppingGrouping { CATEGORY, STORE }

val ShoppingItem.isOpen: Boolean
    get() = deletedAt == null && status == ShoppingStatus.TO_BUY

/** The open item of a product: there is never more than one. */
fun openItemFor(items: Iterable<ShoppingItem>, productId: String): ShoppingItem? =
    items.firstOrNull { it.isOpen && it.productId == productId }

/**
 * A linked or free item created by the user or by the minimum stock; the metadata is set
 * here. Linked items take unit, store and category from the product, so they keep them null.
 */
fun newShoppingItem(draft: ShoppingItem, ctx: OperationContext): ShoppingItem = draft.copy(
    id = ctx.newId(),
    status = ShoppingStatus.TO_BUY,
    notesModifiedAt = if (draft.notes != null) ctx.now else null,
    createdAt = ctx.now,
    modifiedAt = ctx.now,
    modifiedBy = ctx.deviceId,
    deletedAt = null,
)

/**
 * "To buy" from the pantry: a new linked item, or the open one of the product with the new
 * quantity. Null when nothing changes (already in the list, no quantity given).
 */
fun planToBuy(
    productId: String,
    quantity: Long?,
    items: List<ShoppingItem>,
    automatic: Boolean,
    ctx: OperationContext,
): ShoppingItem? {
    val open = openItemFor(items, productId)
    return when {
        open == null -> newShoppingItem(
            ShoppingItem(
                id = "",
                productId = productId,
                quantity = quantity,
                automatic = automatic,
                createdAt = 0,
                modifiedAt = 0,
                modifiedBy = "",
            ),
            ctx,
        )
        quantity == null || quantity == open.quantity -> null
        else -> open.copy(quantity = quantity).touched(ctx)
    }
}

fun editShoppingItem(stored: ShoppingItem, edited: ShoppingItem, ctx: OperationContext): ShoppingItem =
    edited.copy(
        id = stored.id,
        productId = stored.productId,
        status = stored.status,
        automatic = stored.automatic,
        createdAt = stored.createdAt,
        deletedAt = stored.deletedAt,
        notesModifiedAt = if (stored.notes == edited.notes) stored.notesModifiedAt else ctx.now,
        modifiedAt = nextModifiedAt(ctx.now, stored.modifiedAt),
        modifiedBy = ctx.deviceId,
    )

fun ShoppingItem.withStatus(status: ShoppingStatus, ctx: OperationContext): ShoppingItem =
    copy(status = status).touched(ctx)

fun ShoppingItem.softDeleted(ctx: OperationContext): ShoppingItem =
    nextModifiedAt(ctx.now, modifiedAt).let { copy(deletedAt = it, modifiedAt = it, modifiedBy = ctx.deviceId) }

/** "Clear bought items": a tombstone for every bought item. */
fun clearedBought(items: List<ShoppingItem>, ctx: OperationContext): List<ShoppingItem> =
    items.filter { it.deletedAt == null && it.status == ShoppingStatus.BOUGHT }.map { it.softDeleted(ctx) }

private fun ShoppingItem.touched(ctx: OperationContext): ShoppingItem =
    copy(modifiedAt = nextModifiedAt(ctx.now, modifiedAt), modifiedBy = ctx.deviceId)

/**
 * A movement took a purchased product from its minimum stock or more to under it: the
 * moment to add it to the list. Staying under the minimum does not count again, so an item
 * removed by the user is not added back at every use.
 */
fun droppedBelowMinStock(product: Product, before: Long, after: Long): Boolean =
    !product.homemade && product.deletedAt == null &&
        !isBelowMinStock(before, product.minStock) && isBelowMinStock(after, product.minStock)

/**
 * "Frequent": the products with the most purchases in the last 180 days, not already in
 * the list; at most 10, ties by name.
 */
fun frequentProducts(
    products: List<Product>,
    movements: List<Movement>,
    batchProduct: Map<String, String>,
    items: List<ShoppingItem>,
    now: Long,
    compareNames: Comparator<String>,
): List<Product> {
    val since = now - FREQUENT_WINDOW_DAYS * DAY_MILLIS
    val purchases = movements
        .filter { it.type == MovementType.PURCHASE && it.timestamp in since..now }
        .groupingBy { batchProduct[it.batchId] }
        .eachCount()
    val inList = items.filter { it.isOpen }.mapNotNull { it.productId }.toSet()
    return products
        .filter { it.deletedAt == null && it.id !in inList && (purchases[it.id] ?: 0) > 0 }
        .sortedWith(
            compareByDescending<Product> { purchases[it.id] ?: 0 }.thenBy(compareNames) { it.name },
        )
        .take(FREQUENT_MAX)
}

/** "Running low": purchased products whose estimate is under the threshold, not already in the list. */
fun runningLowSuggestions(
    summaries: List<ProductSummary>,
    items: List<ShoppingItem>,
    compareNames: Comparator<String>,
): List<Product> {
    val inList = items.filter { it.isOpen }.mapNotNull { it.productId }.toSet()
    return summaries
        .filter { it.runningLow && !it.product.homemade && it.product.id !in inList }
        .map { it.product }
        .sortedWith(compareBy(compareNames) { it.name })
}

/** One row of the list: a linked item shows its product, a free item its text. */
data class ShoppingRow(
    val item: ShoppingItem,
    val product: Product?,
    val name: String,
    val categoryId: String?,
    val storeId: String?,
)

/** A group of the list; [id] null is "no category" or "no store", always last. */
data class ShoppingGroup(val id: String?, val rows: List<ShoppingRow>)

data class ShoppingView(val groups: List<ShoppingGroup>, val bought: List<ShoppingRow>)

/**
 * Open items grouped by category or store (the product's best price store for linked
 * items, the item's own store for free ones), in the order of [groupOrder]; the store
 * filter keeps one store. Bought items follow, most recent first. Items of deleted
 * products are hidden.
 */
fun shoppingView(
    items: List<ShoppingItem>,
    products: Map<String, Product>,
    grouping: ShoppingGrouping,
    storeFilter: String?,
    groupOrder: List<String>,
    compareNames: Comparator<String>,
): ShoppingView {
    val rows = items.filter { it.deletedAt == null }.mapNotNull { item ->
        val product = item.productId?.let { products[it] }
        when {
            item.productId != null && (product == null || product.deletedAt != null) -> null
            product != null -> ShoppingRow(item, product, product.name, product.categoryId, product.bestPriceStoreId)
            else -> ShoppingRow(item, null, item.text.orEmpty(), item.categoryId, item.storeId)
        }
    }.filter { storeFilter == null || it.storeId == storeFilter }

    val byName = compareBy<ShoppingRow, String>(compareNames) { it.name }
    val position = groupOrder.withIndex().associate { (i, id) -> id to i }
    val groups = rows
        .filter { it.item.status == ShoppingStatus.TO_BUY }
        .groupBy { row ->
            val id = if (grouping == ShoppingGrouping.CATEGORY) row.categoryId else row.storeId
            id?.takeIf { it in position }
        }
        .map { (id, list) -> ShoppingGroup(id, list.sortedWith(byName)) }
        .sortedBy { it.id?.let(position::get) ?: Int.MAX_VALUE }
    val bought = rows
        .filter { it.item.status == ShoppingStatus.BOUGHT }
        .sortedByDescending { it.item.modifiedAt }
    return ShoppingView(groups, bought)
}

/** A best price noted more than 6 months ago is shown faded. */
fun isBestPriceStale(notedAt: Long?, today: Long): Boolean {
    if (notedAt == null) return false
    val notedOn = Math.floorDiv(notedAt, DAY_MILLIS)
    return notedOn < LocalDate.ofEpochDay(today).minusMonths(BEST_PRICE_FRESH_MONTHS).toEpochDay()
}
