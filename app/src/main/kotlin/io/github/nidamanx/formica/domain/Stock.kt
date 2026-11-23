// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.domain

import io.github.nidamanx.formica.domain.model.Batch
import io.github.nidamanx.formica.domain.model.Movement
import io.github.nidamanx.formica.domain.model.Origin

private const val MILLIS_PER_DAY = 86_400_000L

/** Quantity of every batch: the sum of its movements' deltas. Batches without movements are absent. */
fun quantitiesByBatch(movements: Iterable<Movement>): Map<String, Long> {
    val result = HashMap<String, Long>()
    for (m in movements) result[m.batchId] = (result[m.batchId] ?: 0L) + m.delta
    return result
}

fun Map<String, Long>.of(batch: Batch): Long = this[batch.id] ?: 0L

/** Total of a product: the sum of its batches that are not deleted. May be negative after a merge. */
fun productQuantity(batches: Iterable<Batch>, quantities: Map<String, Long>): Long =
    batches.filter { it.deletedAt == null }.sumOf { quantities.of(it) }

/** Negative values (possible after a merge) are shown as 0 and the product is marked "to check". */
fun displayedQuantity(quantity: Long): Long = maxOf(0L, quantity)

fun needsCheck(batches: Iterable<Batch>, quantities: Map<String, Long>): Boolean =
    batches.any { it.deletedAt == null && quantities.of(it) < 0 }

/** The earlier of expiresOn and openedOn + daysAfterOpening, among those present. */
fun effectiveExpiry(batch: Batch, daysAfterOpening: Int?): Long? {
    val afterOpening = if (batch.openedOn != null && daysAfterOpening != null) {
        batch.openedOn + daysAfterOpening
    } else {
        null
    }
    return listOfNotNull(batch.expiresOn, afterOpening).minOrNull()
}

/** Proposed expiry for a new batch: purchase or preparation date + shelf life. */
fun proposedExpiry(date: Long, shelfLifeDays: Int?): Long? = shelfLifeDays?.let { date + it }

fun proposedReadyFrom(preparedOn: Long, daysUntilReady: Int?): Long? = daysUntilReady?.let { preparedOn + it }

/** "In preparation": counted in the total, never consumed automatically. */
fun isInPreparation(batch: Batch, today: Long): Boolean = batch.readyFrom != null && batch.readyFrom > today

/** Batches with the same key are shown as a single group. */
data class GroupKey(
    val productId: String,
    val locationId: String,
    val origin: Origin,
    val expiresOn: Long?,
    val preparedOn: Long?,
    val openedOn: Long?,
    val readyFrom: Long?,
)

val Batch.groupKey: GroupKey
    get() = GroupKey(productId, locationId, origin, expiresOn, preparedOn, openedOn, readyFrom)

fun Batch.withKey(key: GroupKey): Batch = copy(
    productId = key.productId,
    locationId = key.locationId,
    origin = key.origin,
    expiresOn = key.expiresOn,
    preparedOn = key.preparedOn,
    openedOn = key.openedOn,
    readyFrom = key.readyFrom,
)

data class BatchGroup(val key: GroupKey, val batches: List<Batch>, val quantity: Long)

/** Groups of the batches that are not deleted, in consumption order of their first batch. */
fun groupBatches(
    batches: Iterable<Batch>,
    quantities: Map<String, Long>,
    daysAfterOpening: Int?,
): List<BatchGroup> = batches
    .filter { it.deletedAt == null }
    .sortedWith(consumptionOrder(daysAfterOpening))
    .groupBy { it.groupKey }
    .map { (key, list) -> BatchGroup(key, list, list.sumOf { quantities.of(it) }) }

/** Groups shown in the UI: a group at quantity 0 (or less) is hidden but stays in the database. */
fun visibleGroups(groups: List<BatchGroup>): List<BatchGroup> = groups.filter { it.quantity > 0 }

/**
 * Consumption order: nearest effective expiry first, batches without expiry last; then the
 * oldest preparation/purchase/creation date; createdAt and id make the order total.
 */
fun consumptionOrder(daysAfterOpening: Int?): Comparator<Batch> =
    compareBy<Batch, Long?>(nullsLast<Long>()) { effectiveExpiry(it, daysAfterOpening) }
        .thenBy { referenceDay(it) }
        .thenBy { it.createdAt }
        .thenBy { it.id }

// createdAt is converted with UTC days: a few hours of difference cannot change a real
// ordering, and the domain stays independent of the time zone
private fun referenceDay(batch: Batch): Long =
    batch.preparedOn ?: batch.purchasedOn ?: Math.floorDiv(batch.createdAt, MILLIS_PER_DAY)

/** Newest batch first (most recently created). */
val newestFirst: Comparator<Batch> = compareByDescending<Batch> { it.createdAt }.thenByDescending { it.id }
