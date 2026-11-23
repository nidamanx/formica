// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.domain

import io.github.nidamanx.formica.domain.model.Batch
import io.github.nidamanx.formica.domain.model.Movement
import io.github.nidamanx.formica.domain.model.Product

enum class ExpiryStatus { NONE, OK, EXPIRING, EXPIRED }

/** Values shown on one card of the pantry list. */
data class ProductSummary(
    val product: Product,
    val quantity: Long,
    /** Nearest effective expiry among the batches with stock (epoch day). */
    val nearestExpiry: Long?,
    val expiryStatus: ExpiryStatus,
    /** Earliest ready date among the batches with stock that are still in preparation. */
    val readyFrom: Long?,
    /** Oldest preparation date among the batches with stock (epoch day). */
    val oldestPreparedOn: Long?,
    val runningLow: Boolean,
    /** Homemade product under its minimum stock. */
    val toMake: Boolean,
    val needsCheck: Boolean,
    /** Locations of the batches with stock, in consumption order, without repetitions. */
    val locationIds: List<String>,
    val hasNotes: Boolean,
    /** Product and batch notes, for the search. */
    val searchText: String,
)

data class ListThresholds(val expiryWarningDays: Int, val runningLowDays: Int)

fun summarizeProduct(
    product: Product,
    productBatches: List<Batch>,
    quantities: Map<String, Long>,
    productMovements: List<Movement>,
    today: Long,
    now: Long,
    thresholds: ListThresholds,
): ProductSummary {
    val active = productBatches.filter { it.deletedAt == null }
    val withStock = active
        .filter { quantities.of(it) > 0 }
        .sortedWith(consumptionOrder(product.daysAfterOpening))
    val quantity = productQuantity(active, quantities)
    val nearestExpiry = withStock.mapNotNull { effectiveExpiry(it, product.daysAfterOpening) }.minOrNull()
    val warningDays = product.expiryWarningDays ?: thresholds.expiryWarningDays
    val status = when {
        nearestExpiry == null -> ExpiryStatus.NONE
        nearestExpiry < today -> ExpiryStatus.EXPIRED
        nearestExpiry <= today + warningDays -> ExpiryStatus.EXPIRING
        else -> ExpiryStatus.OK
    }
    val days = if (quantity > 0) estimatedDays(productMovements, quantity, now) else null
    return ProductSummary(
        product = product,
        quantity = quantity,
        nearestExpiry = nearestExpiry,
        expiryStatus = status,
        readyFrom = withStock.filter { isInPreparation(it, today) }.mapNotNull { it.readyFrom }.minOrNull(),
        oldestPreparedOn = withStock.mapNotNull { it.preparedOn }.minOrNull(),
        runningLow = days != null && days < thresholds.runningLowDays,
        toMake = product.homemade && isBelowMinStock(quantity, product.minStock),
        needsCheck = needsCheck(active, quantities),
        locationIds = withStock.map { it.locationId }.distinct(),
        hasNotes = !product.notes.isNullOrBlank(),
        searchText = (listOf(product.name, product.notes) + active.map { it.notes })
            .filterNotNull()
            .joinToString("\n") { normalizedName(it) },
    )
}

enum class PantrySort { NAME, EXPIRY, PREPARATION }

data class PantryFilter(
    val query: String = "",
    val locationId: String? = null,
    val categoryId: String? = null,
    val homemadeOnly: Boolean = false,
)

/**
 * Search (names and notes, ignoring case and extra spaces), filters and sort of the list.
 * A product without stock matches a location filter through its default location.
 *
 * @param compareNames locale-aware name order (a Collator in the app)
 */
fun filterAndSort(
    summaries: List<ProductSummary>,
    filter: PantryFilter,
    sort: PantrySort,
    compareNames: Comparator<String>,
): List<ProductSummary> {
    val query = normalizedName(filter.query)
    val byName = compareBy<ProductSummary, String>(compareNames) { it.product.name }
    val comparator = when (sort) {
        PantrySort.NAME -> byName
        PantrySort.EXPIRY -> compareBy<ProductSummary, Long?>(nullsLast<Long>()) { it.nearestExpiry }.then(byName)
        PantrySort.PREPARATION -> compareBy<ProductSummary, Long?>(nullsLast<Long>()) { it.oldestPreparedOn }.then(byName)
    }
    return summaries
        .filter { query.isEmpty() || it.searchText.contains(query) }
        .filter { s ->
            filter.locationId == null || filter.locationId in s.locationIds ||
                (s.locationIds.isEmpty() && s.product.defaultLocationId == filter.locationId)
        }
        .filter { filter.categoryId == null || it.product.categoryId == filter.categoryId }
        .filter { !filter.homemadeOnly || it.product.homemade }
        .sortedWith(comparator)
}
