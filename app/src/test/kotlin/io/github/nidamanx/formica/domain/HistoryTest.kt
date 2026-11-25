// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.domain

import io.github.nidamanx.formica.domain.model.MovementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryTest {

    @Test
    fun `a transfer becomes one move row, newest first`() {
        val cellar = batch("a", locationId = "cellar")
        val fridge = batch("b", locationId = "fridge")
        val movements = listOf(
            movement("a", units(6), MovementType.PURCHASE, NOW - 5 * DAY),
            movement("a", -units(1), MovementType.TRANSFER, NOW - DAY).copy(groupId = "g"),
            movement("b", units(1), MovementType.TRANSFER, NOW - DAY).copy(groupId = "g"),
            movement("b", -units(1), MovementType.CONSUMPTION, NOW),
        )
        val history = buildHistory(movements, mapOf("a" to cellar, "b" to fridge))

        assertEquals(listOf(MovementType.CONSUMPTION, MovementType.TRANSFER, MovementType.PURCHASE), history.map { it.type })
        val move = history[1]
        assertEquals("cellar", move.fromLocationId)
        assertEquals("fridge", move.toLocationId)
        assertEquals(units(1), move.delta)
        assertFalse(move.isOpening)
        assertEquals(-units(1), history[0].delta)
    }

    @Test
    fun `a transfer within the same location is an opening`() {
        val closed = batch("a")
        val opened = batch("b", openedOn = TODAY)
        val movements = listOf(
            movement("a", -units(1), MovementType.TRANSFER, NOW).copy(groupId = "g"),
            movement("b", units(1), MovementType.TRANSFER, NOW).copy(groupId = "g"),
        )
        assertTrue(buildHistory(movements, mapOf("a" to closed, "b" to opened)).single().isOpening)
    }

    @Test
    fun `an undone purchase is marked and its correction names it`() {
        val movements = listOf(
            movement("a", units(2), MovementType.PURCHASE, NOW - 2 * DAY, id = "buy"),
            movement("a", units(1), MovementType.PREPARATION, NOW - DAY, id = "prep"),
            movement("a", -units(2), MovementType.CORRECTION, NOW, id = "undo").copy(groupId = "buy"),
            movement("a", -units(1), MovementType.CORRECTION, NOW, id = "fix"),
        )
        val history = buildHistory(movements, mapOf("a" to batch("a"))).associateBy { it.id }

        assertTrue(history.getValue("buy").undone)
        assertFalse(history.getValue("buy").undoable)
        assertTrue(history.getValue("prep").undoable)
        assertEquals(MovementType.PURCHASE, history.getValue("undo").undoOf)
        assertEquals(null, history.getValue("fix").undoOf)
        assertFalse(history.getValue("fix").undoable)
    }
}
