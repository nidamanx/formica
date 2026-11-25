// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.domain

import io.github.nidamanx.formica.domain.model.MovementType
import io.github.nidamanx.formica.domain.model.Origin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StockOperationsTest {

    // Purchase and preparation

    @Test
    fun `a purchase with an existing key adds to the newest batch of that key`() {
        val old = batch("old", expiresOn = TODAY + 7, createdAt = NOW - 9 * DAY)
        val new = batch("new", expiresOn = TODAY + 7, createdAt = NOW - 2 * DAY)
        val plan = planPurchase("p1", listOf(old, new), units(2), "fridge", TODAY + 7, TODAY, "s1", context())

        assertTrue(plan.newBatches.isEmpty())
        val m = plan.movements.single()
        assertEquals("new", m.batchId)
        assertEquals(units(2), m.delta)
        assertEquals(MovementType.PURCHASE, m.type)
        assertEquals(NOW, m.timestamp)
        assertEquals(DEVICE, m.deviceId)
    }

    @Test
    fun `a purchase with a new key creates a batch`() {
        val existing = batch("b", expiresOn = TODAY + 7)
        val plan = planPurchase("p1", listOf(existing), units(1), "fridge", TODAY + 8, TODAY, "s1", context())

        val created = plan.newBatches.single()
        assertEquals(Origin.PURCHASED, created.origin)
        assertEquals("fridge", created.locationId)
        assertEquals(TODAY + 8, created.expiresOn)
        assertEquals(TODAY, created.purchasedOn)
        assertEquals("s1", created.storeId)
        assertEquals(NOW, created.createdAt)
        assertEquals(NOW, created.modifiedAt)
        assertEquals(DEVICE, created.modifiedBy)
        assertEquals(created.id, plan.movements.single().batchId)
    }

    @Test
    fun `a purchase never reuses an opened, deleted or homemade batch`() {
        val batches = listOf(
            batch("opened", openedOn = TODAY),
            batch("deleted", deletedAt = NOW),
            batch("homemade", origin = Origin.HOMEMADE, preparedOn = TODAY),
        )
        val plan = planPurchase("p1", batches, units(1), "fridge", null, TODAY, null, context())
        assertEquals(1, plan.newBatches.size)
    }

    @Test
    fun `a preparation creates a homemade batch with its dates`() {
        val plan = planPreparation("p1", emptyList(), units(12), "cellar", TODAY - 3, TODAY + 362, TODAY + 18, context())

        val created = plan.newBatches.single()
        assertEquals(Origin.HOMEMADE, created.origin)
        assertEquals(TODAY - 3, created.preparedOn)
        assertEquals(TODAY + 362, created.expiresOn)
        assertEquals(TODAY + 18, created.readyFrom)
        assertEquals(MovementType.PREPARATION, plan.movements.single().type)
        assertEquals(units(12), plan.movements.single().delta)
    }

    @Test
    fun `a preparation with the same key goes to the existing batch`() {
        val existing = batch("b", origin = Origin.HOMEMADE, locationId = "cellar", preparedOn = TODAY, expiresOn = TODAY + 365)
        val plan = planPreparation("p1", listOf(existing), units(4), "cellar", TODAY, TODAY + 365, null, context())
        assertTrue(plan.newBatches.isEmpty())
        assertEquals("b", plan.movements.single().batchId)
    }

    // Consumption and discard

    @Test
    fun `consumption takes from the nearest expiry first`() {
        val batches = listOf(
            batch("late", expiresOn = TODAY + 20),
            batch("soon", expiresOn = TODAY + 2),
            batch("none"),
        )
        val quantities = mapOf("late" to units(5), "soon" to units(2), "none" to units(3))
        val plan = planConsumption(batches, quantities, units(4), null, context())

        assertEquals(listOf("soon" to -units(2), "late" to -units(2)), plan.movements.map { it.batchId to it.delta })
        assertTrue(plan.movements.all { it.type == MovementType.CONSUMPTION })
        assertEquals(0L, plan.unallocated)
        assertEquals(mapOf("late" to units(3), "soon" to 0L, "none" to units(3)), quantities.after(plan))
    }

    @Test
    fun `consumption skips batches in preparation, deleted or empty`() {
        val batches = listOf(
            batch("fermenting", origin = Origin.HOMEMADE, preparedOn = TODAY - 2, readyFrom = TODAY + 5),
            batch("deleted", expiresOn = TODAY, deletedAt = NOW),
            batch("empty", expiresOn = TODAY),
            batch("ready", origin = Origin.HOMEMADE, preparedOn = TODAY - 30, readyFrom = TODAY),
        )
        val quantities = mapOf("fermenting" to units(4), "deleted" to units(4), "empty" to 0L, "ready" to units(1))
        val plan = planConsumption(batches, quantities, units(1), null, context())
        assertEquals(listOf("ready"), plan.movements.map { it.batchId })
    }

    @Test
    fun `consuming more than available takes what there is and reports the rest`() {
        val batches = listOf(batch("a"), batch("b", readyFrom = TODAY + 1))
        val plan = planConsumption(batches, mapOf("a" to units(2), "b" to units(9)), units(3), null, context())
        assertEquals(-units(2), plan.movements.single().delta)
        assertEquals(units(1), plan.unallocated)
    }

    @Test
    fun `a decimal quantity is consumed exactly`() {
        val plan = planConsumption(listOf(batch("flour")), mapOf("flour" to 1500L), 250L, null, context())
        assertEquals(-250L, plan.movements.single().delta)
    }

    @Test
    fun `a chosen group can be discarded even while in preparation`() {
        val a = batch("a", origin = Origin.HOMEMADE, preparedOn = TODAY, readyFrom = TODAY + 10, createdAt = NOW - 2 * DAY)
        val b = batch("b", origin = Origin.HOMEMADE, preparedOn = TODAY, readyFrom = TODAY + 10, createdAt = NOW - DAY)
        val quantities = mapOf("a" to units(1), "b" to units(3))
        val group = groupBatches(listOf(a, b), quantities, null).single()

        val plan = planGroupRemoval(group, quantities, units(2), MovementType.DISCARD, null, context())

        assertEquals(listOf("a" to -units(1), "b" to -units(1)), plan.movements.map { it.batchId to it.delta })
        assertTrue(plan.movements.all { it.type == MovementType.DISCARD })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `group removal accepts only consumption and discard`() {
        val group = groupBatches(listOf(batch("a")), mapOf("a" to units(1)), null).single()
        planGroupRemoval(group, mapOf("a" to units(1)), units(1), MovementType.TRANSFER, null, context())
    }

    // Transfer

    @Test
    fun `moving part of a group creates a batch in the new location with the same values`() {
        val source = batch("a", expiresOn = TODAY + 30, purchasedOn = TODAY - 1).copy(storeId = "s1", notes = "big jar")
        val quantities = mapOf("a" to units(6))
        val group = groupBatches(listOf(source), quantities, null).single()

        val plan = planTransfer(group, quantities, units(2), "freezer", listOf(source), null, context())

        val target = plan.newBatches.single()
        assertEquals("freezer", target.locationId)
        assertEquals(source.groupKey.copy(locationId = "freezer"), target.groupKey)
        assertEquals("s1", target.storeId)
        assertEquals(TODAY - 1, target.purchasedOn)
        assertEquals("big jar", target.notes)
        assertEquals(NOW, target.createdAt)

        assertTrue(plan.movements.all { it.type == MovementType.TRANSFER })
        assertEquals(1, plan.movements.map { it.groupId }.distinct().size)
        assertTrue(plan.movements.first().groupId != null)
        assertEquals(mapOf("a" to units(4), target.id to units(2)), quantities.after(plan))
    }

    @Test
    fun `moving to a location with the same key reuses that batch`() {
        val source = batch("a", expiresOn = TODAY + 30)
        val there = batch("b", expiresOn = TODAY + 30, locationId = "freezer")
        val quantities = mapOf("a" to units(3), "b" to units(1))
        val group = groupBatches(listOf(source), quantities, null).single()

        val plan = planTransfer(group, quantities, units(3), "freezer", listOf(source, there), null, context())

        assertTrue(plan.newBatches.isEmpty())
        assertEquals(mapOf("a" to 0L, "b" to units(4)), quantities.after(plan))
    }

    @Test
    fun `moving a group made of several batches takes from each`() {
        val a = batch("a", createdAt = NOW - 5 * DAY)
        val b = batch("b", createdAt = NOW - DAY)
        val quantities = mapOf("a" to units(1), "b" to units(2))
        val group = groupBatches(listOf(a, b), quantities, null).single()

        val plan = planTransfer(group, quantities, units(5), "cellar", listOf(a, b), null, context())

        val target = plan.newBatches.single()
        assertEquals(mapOf("a" to 0L, "b" to 0L, target.id to units(3)), quantities.after(plan))
        assertEquals(units(2), plan.unallocated)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `moving to the same location is refused`() {
        val group = groupBatches(listOf(batch("a")), mapOf("a" to units(1)), null).single()
        planTransfer(group, mapOf("a" to units(1)), units(1), "fridge", listOf(batch("a")), null, context())
    }

    // Opening

    @Test
    fun `opening one unit of a larger group moves it to an opened batch`() {
        val source = batch("a", expiresOn = TODAY + 100)
        val quantities = mapOf("a" to units(6))
        val group = groupBatches(listOf(source), quantities, 3).single()

        val plan = planOpening(group, quantities, units(1), TODAY, listOf(source), 3, context())

        val opened = plan.newBatches.single()
        assertEquals(TODAY, opened.openedOn)
        assertEquals("fridge", opened.locationId)
        assertEquals(TODAY + 3, effectiveExpiry(opened, 3))
        assertTrue(plan.updatedBatches.isEmpty())
        assertTrue(plan.movements.all { it.type == MovementType.TRANSFER })
        assertEquals(mapOf("a" to units(5), opened.id to units(1)), quantities.after(plan))
    }

    @Test
    fun `opening the whole group sets the opening date on its batches`() {
        val a = batch("a", expiresOn = TODAY + 100)
        val b = batch("b", expiresOn = TODAY + 100).copy(modifiedAt = NOW + 50)
        val quantities = mapOf("a" to 500L, "b" to 500L)
        val group = groupBatches(listOf(a, b), quantities, null).single()

        val plan = planOpening(group, quantities, units(1), TODAY, listOf(a, b), null, context())

        assertTrue(plan.movements.isEmpty())
        assertTrue(plan.newBatches.isEmpty())
        val updated = plan.updatedBatches.associateBy { it.id }
        assertEquals(TODAY, updated.getValue("a").openedOn)
        assertEquals(TODAY, updated.getValue("b").openedOn)
        assertEquals(NOW, updated.getValue("a").modifiedAt)
        assertEquals(NOW + 51, updated.getValue("b").modifiedAt)
        assertEquals(DEVICE, updated.getValue("a").modifiedBy)
    }

    // Correction

    @Test
    fun `an increase is recorded on the newest batch`() {
        val old = batch("old", createdAt = NOW - 9 * DAY)
        val new = batch("new", createdAt = NOW - DAY)
        val quantities = mapOf("old" to units(2), "new" to units(1))

        val plan = planCorrection(listOf(old, new), quantities, units(5), context())

        val m = plan.movements.single()
        assertEquals("new", m.batchId)
        assertEquals(units(2), m.delta)
        assertEquals(MovementType.CORRECTION, m.type)
    }

    @Test
    fun `a decrease goes from the newest batch back, never below zero`() {
        val old = batch("old", createdAt = NOW - 9 * DAY)
        val new = batch("new", createdAt = NOW - DAY)
        val quantities = mapOf("old" to units(3), "new" to units(3))

        val plan = planCorrection(listOf(old, new), quantities, units(1), context())

        assertEquals(listOf("new" to -units(3), "old" to -units(2)), plan.movements.map { it.batchId to it.delta })
        assertTrue(plan.movements.all { it.type == MovementType.CORRECTION })
        assertEquals(0L, plan.unallocated)
        assertEquals(mapOf("old" to units(1), "new" to 0L), quantities.after(plan))
    }

    @Test
    fun `a correction to zero also clears a negative batch total`() {
        val a = batch("a", createdAt = NOW - 9 * DAY)
        val b = batch("b", createdAt = NOW - DAY)
        val quantities = mapOf("a" to units(3), "b" to -units(1))

        val plan = planCorrection(listOf(a, b), quantities, 0L, context())

        val after = quantities.after(plan)
        assertEquals(0L, after.getValue("a") + after.getValue("b"))
        assertEquals(0L, plan.unallocated)
    }

    @Test
    fun `no correction when the value is already right`() {
        val plan = planCorrection(listOf(batch("a")), mapOf("a" to units(2)), units(2), context())
        assertEquals(StockPlan(), plan)
    }

    // Undo of a purchase or preparation

    @Test
    fun `undoing a purchase takes its quantity back with a linked correction`() {
        val purchase = movement("a", units(3), MovementType.PURCHASE, id = "buy")
        val all = listOf(movement("a", units(2), MovementType.PURCHASE, NOW - 20 * DAY), purchase)
        val plan = planUndoAddition(purchase, all, quantitiesByBatch(all), context())

        val m = plan.movements.single()
        assertEquals("a", m.batchId)
        assertEquals(-units(3), m.delta)
        assertEquals(MovementType.CORRECTION, m.type)
        assertEquals("buy", m.groupId)
        assertEquals(0L, plan.unallocated)
        assertEquals(setOf("buy"), undoneMovementIds(all + plan.movements))
    }

    @Test
    fun `undoing a partly used preparation takes only what is left`() {
        val preparation = movement("a", units(4), MovementType.PREPARATION, id = "prep")
        val all = listOf(preparation, movement("a", -units(3), MovementType.CONSUMPTION, NOW - DAY))
        val plan = planUndoAddition(preparation, all, quantitiesByBatch(all), context())

        assertEquals(-units(1), plan.movements.single().delta)
        assertEquals(units(3), plan.unallocated)
    }

    @Test
    fun `undoing a fully used purchase records nothing`() {
        val purchase = movement("a", units(2), MovementType.PURCHASE, id = "buy")
        val all = listOf(purchase, movement("a", -units(2), MovementType.CONSUMPTION, NOW - DAY))
        val plan = planUndoAddition(purchase, all, quantitiesByBatch(all), context())

        assertTrue(plan.movements.isEmpty())
        assertEquals(units(2), plan.unallocated)
    }

    @Test
    fun `a purchase already undone is not undone again`() {
        val purchase = movement("a", units(2), MovementType.PURCHASE, id = "buy")
        val undo = movement("a", -units(2), MovementType.CORRECTION, NOW - DAY).copy(groupId = "buy")
        val all = listOf(movement("a", units(5), MovementType.PURCHASE, NOW - 20 * DAY), purchase, undo)
        val plan = planUndoAddition(purchase, all, quantitiesByBatch(all), context())

        assertTrue(plan.movements.isEmpty())
        assertEquals(0L, plan.unallocated)
    }
}
