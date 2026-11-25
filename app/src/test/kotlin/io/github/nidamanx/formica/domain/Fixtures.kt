// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.domain

import io.github.nidamanx.formica.domain.model.Batch
import io.github.nidamanx.formica.domain.model.Movement
import io.github.nidamanx.formica.domain.model.MovementType
import io.github.nidamanx.formica.domain.model.ONE_UNIT
import io.github.nidamanx.formica.domain.model.Origin

const val DAY = 86_400_000L
const val TODAY = 20_000L
const val NOW = TODAY * DAY + 10 * 3_600_000L
const val DEVICE = "device-a"

fun units(n: Long): Long = n * ONE_UNIT

fun context(now: Long = NOW, today: Long = TODAY): OperationContext {
    var counter = 0
    return OperationContext(now, today, DEVICE) { "id-${++counter}" }
}

fun batch(
    id: String,
    productId: String = "p1",
    locationId: String = "fridge",
    origin: Origin = Origin.PURCHASED,
    expiresOn: Long? = null,
    preparedOn: Long? = null,
    purchasedOn: Long? = null,
    openedOn: Long? = null,
    readyFrom: Long? = null,
    createdAt: Long = NOW - 10 * DAY,
    deletedAt: Long? = null,
) = Batch(
    id = id,
    productId = productId,
    locationId = locationId,
    origin = origin,
    preparedOn = preparedOn,
    purchasedOn = purchasedOn,
    expiresOn = expiresOn,
    readyFrom = readyFrom,
    openedOn = openedOn,
    createdAt = createdAt,
    modifiedAt = createdAt,
    modifiedBy = "device-b",
    deletedAt = deletedAt,
)

fun movement(
    batchId: String,
    delta: Long,
    type: MovementType = MovementType.PURCHASE,
    timestamp: Long = NOW - 10 * DAY,
    id: String = "m-$batchId-$timestamp-$type-$delta",
) = Movement(id, batchId, delta, type, null, timestamp, "device-b")

/** Applies a plan's movements to the quantities, as the database would. */
fun Map<String, Long>.after(plan: StockPlan): Map<String, Long> =
    quantitiesByBatch(this.map { (id, q) -> movement(id, q) } + plan.movements)
