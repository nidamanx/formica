// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.domain

import io.github.nidamanx.formica.domain.model.Batch
import io.github.nidamanx.formica.domain.model.Movement
import io.github.nidamanx.formica.domain.model.MovementType

/**
 * One row of a product's history. The two movements of a transfer become one row: a
 * move between locations, or an opening when the location does not change (partial
 * opening moves units to an opened batch in the same place).
 */
data class HistoryEntry(
    val id: String,
    val timestamp: Long,
    val type: MovementType,
    /** Signed amount; for a transfer the amount moved (positive). */
    val delta: Long,
    val fromLocationId: String? = null,
    val toLocationId: String? = null,
    /** A purchase or preparation that can still be undone. */
    val undoable: Boolean = false,
    /** A purchase or preparation undone later. */
    val undone: Boolean = false,
    /** For the CORRECTION of an undo: the type of the undone movement. */
    val undoOf: MovementType? = null,
) {
    val isOpening: Boolean
        get() = type == MovementType.TRANSFER && fromLocationId == toLocationId
}

/** Newest first. */
fun buildHistory(productMovements: List<Movement>, batchesById: Map<String, Batch>): List<HistoryEntry> {
    val (transfers, others) = productMovements.partition { it.type == MovementType.TRANSFER }
    val undone = undoneMovementIds(productMovements)
    val byId = others.associateBy { it.id }
    val single = others.map {
        val undoOf = if (it.type == MovementType.CORRECTION) {
            it.groupId?.let { id -> byId[id]?.type }?.takeIf { type -> type in UNDOABLE_TYPES }
        } else {
            null
        }
        val isAddition = it.type in UNDOABLE_TYPES && it.delta > 0
        HistoryEntry(
            id = it.id,
            timestamp = it.timestamp,
            type = it.type,
            delta = it.delta,
            undoable = isAddition && it.id !in undone,
            undone = isAddition && it.id in undone,
            undoOf = undoOf,
        )
    }
    val moves = transfers.groupBy { it.groupId ?: it.id }.map { (groupId, parts) ->
        val from = parts.firstOrNull { it.delta < 0 }
        val to = parts.firstOrNull { it.delta > 0 }
        HistoryEntry(
            id = groupId,
            timestamp = parts.minOf { it.timestamp },
            type = MovementType.TRANSFER,
            delta = parts.filter { it.delta > 0 }.sumOf { it.delta },
            fromLocationId = from?.let { batchesById[it.batchId]?.locationId },
            toLocationId = to?.let { batchesById[it.batchId]?.locationId },
        )
    }
    return (single + moves).sortedWith(compareByDescending<HistoryEntry> { it.timestamp }.thenBy { it.id })
}
