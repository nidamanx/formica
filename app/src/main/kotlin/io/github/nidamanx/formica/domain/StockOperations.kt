// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.domain

import io.github.nidamanx.formica.domain.model.Batch
import io.github.nidamanx.formica.domain.model.Movement
import io.github.nidamanx.formica.domain.model.MovementType
import io.github.nidamanx.formica.domain.model.Origin

/**
 * The changes an operation makes, applied by the repository in one transaction.
 * Quantities never change directly: only through new movements.
 *
 * @param unallocated part of the requested amount that could not be taken (stock too low)
 */
data class StockPlan(
    val newBatches: List<Batch> = emptyList(),
    val updatedBatches: List<Batch> = emptyList(),
    val movements: List<Movement> = emptyList(),
    val unallocated: Long = 0L,
)

/** Purchase: a PURCHASE movement on the newest batch with the same group key, or on a new batch. */
fun planPurchase(
    productId: String,
    productBatches: List<Batch>,
    quantity: Long,
    locationId: String,
    expiresOn: Long?,
    purchasedOn: Long?,
    storeId: String?,
    ctx: OperationContext,
): StockPlan {
    require(quantity > 0) { "quantity must be positive" }
    val key = GroupKey(productId, locationId, Origin.PURCHASED, expiresOn, null, null, null)
    return addToKey(productBatches, key, quantity, MovementType.PURCHASE, ctx) {
        it.copy(purchasedOn = purchasedOn, storeId = storeId)
    }
}

/** Homemade preparation: a PREPARATION movement, on a new batch unless the key already exists. */
fun planPreparation(
    productId: String,
    productBatches: List<Batch>,
    quantity: Long,
    locationId: String,
    preparedOn: Long,
    expiresOn: Long?,
    readyFrom: Long?,
    ctx: OperationContext,
): StockPlan {
    require(quantity > 0) { "quantity must be positive" }
    val key = GroupKey(productId, locationId, Origin.HOMEMADE, expiresOn, preparedOn, null, readyFrom)
    return addToKey(productBatches, key, quantity, MovementType.PREPARATION, ctx) { it }
}

/** Consumption of a product: spread over its batches in consumption order, skipping those in preparation. */
fun planConsumption(
    productBatches: List<Batch>,
    quantities: Map<String, Long>,
    amount: Long,
    daysAfterOpening: Int?,
    ctx: OperationContext,
): StockPlan {
    require(amount > 0) { "amount must be positive" }
    val eligible = productBatches
        .filter { it.deletedAt == null && !isInPreparation(it, ctx.today) }
        .sortedWith(consumptionOrder(daysAfterOpening))
    return takeFrom(eligible, quantities, amount, MovementType.CONSUMPTION, ctx)
}

/** Consumption or discard from a group chosen by the user (also allowed while in preparation). */
fun planGroupRemoval(
    group: BatchGroup,
    quantities: Map<String, Long>,
    amount: Long,
    type: MovementType,
    daysAfterOpening: Int?,
    ctx: OperationContext,
): StockPlan {
    require(amount > 0) { "amount must be positive" }
    require(type == MovementType.CONSUMPTION || type == MovementType.DISCARD) { "unsupported type $type" }
    val ordered = group.batches.filter { it.deletedAt == null }.sortedWith(consumptionOrder(daysAfterOpening))
    return takeFrom(ordered, quantities, amount, type, ctx)
}

/** Moves all or part of a group to another location (TRANSFER movements sharing one groupId). */
fun planTransfer(
    group: BatchGroup,
    quantities: Map<String, Long>,
    amount: Long,
    toLocationId: String,
    productBatches: List<Batch>,
    daysAfterOpening: Int?,
    ctx: OperationContext,
): StockPlan {
    require(toLocationId != group.key.locationId) { "same location" }
    val target = group.key.copy(locationId = toLocationId)
    return moveToKey(group, quantities, amount, target, productBatches, daysAfterOpening, ctx)
}

/**
 * Marks a group as opened on [openedOn]. Opening less than the whole group moves [amount]
 * to a batch with openedOn (TRANSFER); otherwise every batch of the group gets openedOn.
 */
fun planOpening(
    group: BatchGroup,
    quantities: Map<String, Long>,
    amount: Long,
    openedOn: Long,
    productBatches: List<Batch>,
    daysAfterOpening: Int?,
    ctx: OperationContext,
): StockPlan {
    require(amount > 0) { "amount must be positive" }
    if (amount < group.quantity) {
        val target = group.key.copy(openedOn = openedOn)
        return moveToKey(group, quantities, amount, target, productBatches, daysAfterOpening, ctx)
    }
    val updated = group.batches
        .filter { it.deletedAt == null }
        .map {
            it.copy(openedOn = openedOn, modifiedAt = nextModifiedAt(ctx.now, it.modifiedAt), modifiedBy = ctx.deviceId)
        }
    return StockPlan(updatedBatches = updated)
}

/**
 * The user enters the real quantity of a group or product: a CORRECTION records the
 * difference, never overwriting. An increase goes to the newest batch; a decrease is
 * taken from the newest batches first, never bringing a batch below zero.
 * A product without batches gets a new one with the key [newBatchKey] (nothing if null).
 */
fun planCorrection(
    batches: List<Batch>,
    quantities: Map<String, Long>,
    realQuantity: Long,
    ctx: OperationContext,
    newBatchKey: GroupKey? = null,
): StockPlan {
    require(realQuantity >= 0) { "real quantity must not be negative" }
    val active = batches.filter { it.deletedAt == null }.sortedWith(newestFirst)
    if (active.isEmpty()) {
        return if (realQuantity == 0L || newBatchKey == null) {
            StockPlan()
        } else {
            addToKey(emptyList(), newBatchKey, realQuantity, MovementType.CORRECTION, ctx) { it }
        }
    }
    val delta = realQuantity - active.sumOf { quantities.of(it) }
    return when {
        delta == 0L -> StockPlan()
        delta > 0 -> StockPlan(movements = listOf(movement(active.first(), delta, MovementType.CORRECTION, null, ctx)))
        // The sum of the positive batches always covers the decrease: the plan is complete
        else -> takeFrom(active, quantities, -delta, MovementType.CORRECTION, ctx)
    }
}

/** Movement types that the user can undo from the history. */
val UNDOABLE_TYPES = setOf(MovementType.PURCHASE, MovementType.PREPARATION)

/** Ids of the purchases and preparations undone by a CORRECTION (its groupId is the undone movement's id). */
fun undoneMovementIds(productMovements: List<Movement>): Set<String> = productMovements
    .filter { it.type == MovementType.CORRECTION && it.groupId != null }
    .mapNotNullTo(HashSet()) { it.groupId }

/**
 * Undo of a purchase or preparation: a CORRECTION on the same batch, with groupId = the
 * undone movement's id, taking what is left of it (never below zero). Not a consumption
 * nor a discard; no movement is deleted.
 */
fun planUndoAddition(
    original: Movement,
    productMovements: List<Movement>,
    quantities: Map<String, Long>,
    ctx: OperationContext,
): StockPlan {
    require(original.type in UNDOABLE_TYPES && original.delta > 0) { "not an undoable movement" }
    if (original.id in undoneMovementIds(productMovements)) return StockPlan()
    val amount = minOf(original.delta, maxOf(quantities[original.batchId] ?: 0L, 0L))
    if (amount == 0L) return StockPlan(unallocated = original.delta)
    val correction = Movement(
        id = ctx.newId(),
        batchId = original.batchId,
        delta = -amount,
        type = MovementType.CORRECTION,
        groupId = original.id,
        timestamp = ctx.now,
        deviceId = ctx.deviceId,
    )
    return StockPlan(movements = listOf(correction), unallocated = original.delta - amount)
}

private fun addToKey(
    productBatches: List<Batch>,
    key: GroupKey,
    quantity: Long,
    type: MovementType,
    ctx: OperationContext,
    customize: (Batch) -> Batch,
): StockPlan {
    val existing = productBatches.filter { it.deletedAt == null && it.groupKey == key }.minWithOrNull(newestFirst)
    if (existing != null) {
        return StockPlan(movements = listOf(movement(existing, quantity, type, null, ctx)))
    }
    val batch = customize(
        Batch(
            id = ctx.newId(),
            productId = key.productId,
            locationId = key.locationId,
            origin = key.origin,
            createdAt = ctx.now,
            modifiedAt = ctx.now,
            modifiedBy = ctx.deviceId,
        ).withKey(key),
    )
    return StockPlan(newBatches = listOf(batch), movements = listOf(movement(batch, quantity, type, null, ctx)))
}

private fun moveToKey(
    group: BatchGroup,
    quantities: Map<String, Long>,
    amount: Long,
    targetKey: GroupKey,
    productBatches: List<Batch>,
    daysAfterOpening: Int?,
    ctx: OperationContext,
): StockPlan {
    require(amount > 0) { "amount must be positive" }
    val sources = group.batches.filter { it.deletedAt == null }.sortedWith(consumptionOrder(daysAfterOpening))
    val (taken, remaining) = allocate(sources, quantities, amount)
    if (taken.isEmpty()) return StockPlan(unallocated = remaining)

    val existing = productBatches.filter { it.deletedAt == null && it.groupKey == targetKey }.minWithOrNull(newestFirst)
    // A new batch keeps every other value of the source (purchase date, store, notes)
    val target = existing ?: taken.first().first.withKey(targetKey).copy(
        id = ctx.newId(),
        createdAt = ctx.now,
        modifiedAt = ctx.now,
        modifiedBy = ctx.deviceId,
        deletedAt = null,
    )
    val groupId = ctx.newId()
    val movements = taken.map { (batch, qty) -> movement(batch, -qty, MovementType.TRANSFER, groupId, ctx) } +
        movement(target, taken.sumOf { it.second }, MovementType.TRANSFER, groupId, ctx)
    return StockPlan(
        newBatches = if (existing == null) listOf(target) else emptyList(),
        movements = movements,
        unallocated = remaining,
    )
}

private fun takeFrom(
    ordered: List<Batch>,
    quantities: Map<String, Long>,
    amount: Long,
    type: MovementType,
    ctx: OperationContext,
): StockPlan {
    val (taken, remaining) = allocate(ordered, quantities, amount)
    return StockPlan(
        movements = taken.map { (batch, qty) -> movement(batch, -qty, type, null, ctx) },
        unallocated = remaining,
    )
}

/** Takes [amount] from the batches in the given order, each down to zero at most. */
private fun allocate(
    ordered: List<Batch>,
    quantities: Map<String, Long>,
    amount: Long,
): Pair<List<Pair<Batch, Long>>, Long> {
    var remaining = amount
    val taken = mutableListOf<Pair<Batch, Long>>()
    for (batch in ordered) {
        if (remaining == 0L) break
        val available = quantities.of(batch)
        if (available <= 0) continue
        val qty = minOf(available, remaining)
        taken += batch to qty
        remaining -= qty
    }
    return taken to remaining
}

private fun movement(batch: Batch, delta: Long, type: MovementType, groupId: String?, ctx: OperationContext) =
    Movement(
        id = ctx.newId(),
        batchId = batch.id,
        delta = delta,
        type = type,
        groupId = groupId,
        timestamp = ctx.now,
        deviceId = ctx.deviceId,
    )
