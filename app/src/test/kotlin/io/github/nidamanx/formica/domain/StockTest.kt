// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.domain

import io.github.nidamanx.formica.domain.model.MovementType
import io.github.nidamanx.formica.domain.model.Origin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StockTest {

    @Test
    fun `batch quantity is the sum of its movements`() {
        val quantities = quantitiesByBatch(
            listOf(
                movement("b1", 1500),
                movement("b1", -250, MovementType.CONSUMPTION),
                movement("b2", 1000),
                movement("b1", -250, MovementType.DISCARD),
            ),
        )
        assertEquals(mapOf("b1" to 1000L, "b2" to 1000L), quantities)
    }

    @Test
    fun `product quantity ignores deleted batches`() {
        val batches = listOf(batch("b1"), batch("b2"), batch("b3", deletedAt = NOW))
        val quantities = mapOf("b1" to 1000L, "b2" to 500L, "b3" to 9000L)
        assertEquals(1500L, productQuantity(batches, quantities))
    }

    @Test
    fun `negative quantities are shown as zero and marked to check`() {
        val batches = listOf(batch("b1"), batch("b2"))
        val quantities = mapOf("b1" to 2000L, "b2" to -1000L)
        assertEquals(0L, displayedQuantity(-1000L))
        assertEquals(1000L, displayedQuantity(1000L))
        assertTrue(needsCheck(batches, quantities))
        assertFalse(needsCheck(batches, mapOf("b1" to 2000L, "b2" to 0L)))
    }

    @Test
    fun `effective expiry is the earlier of expiry and opening plus validity`() {
        assertNull(effectiveExpiry(batch("b"), 3))
        assertEquals(TODAY + 30, effectiveExpiry(batch("b", expiresOn = TODAY + 30), 3))
        assertEquals(TODAY + 3, effectiveExpiry(batch("b", expiresOn = TODAY + 30, openedOn = TODAY), 3))
        assertEquals(TODAY + 2, effectiveExpiry(batch("b", expiresOn = TODAY + 2, openedOn = TODAY), 3))
        assertEquals(TODAY + 3, effectiveExpiry(batch("b", openedOn = TODAY), 3))
        assertEquals(TODAY + 30, effectiveExpiry(batch("b", expiresOn = TODAY + 30, openedOn = TODAY), null))
    }

    @Test
    fun `proposed dates come from the product durations`() {
        assertEquals(TODAY + 365, proposedExpiry(TODAY, 365))
        assertNull(proposedExpiry(TODAY, null))
        assertEquals(TODAY + 21, proposedReadyFrom(TODAY, 21))
        assertNull(proposedReadyFrom(TODAY, null))
    }

    @Test
    fun `a batch is in preparation until its ready date`() {
        assertTrue(isInPreparation(batch("b", readyFrom = TODAY + 1), TODAY))
        assertFalse(isInPreparation(batch("b", readyFrom = TODAY), TODAY))
        assertFalse(isInPreparation(batch("b"), TODAY))
    }

    @Test
    fun `batches with the same key form one group even if created on different devices`() {
        val a = batch("a", expiresOn = TODAY + 5, createdAt = NOW - 3 * DAY).copy(storeId = "s1")
        val b = batch("b", expiresOn = TODAY + 5, createdAt = NOW - DAY).copy(modifiedBy = "device-c")
        val opened = batch("c", expiresOn = TODAY + 5, openedOn = TODAY)
        val elsewhere = batch("d", expiresOn = TODAY + 5, locationId = "cellar")
        val homemade = batch("e", origin = Origin.HOMEMADE, preparedOn = TODAY - 30)
        val deleted = batch("f", expiresOn = TODAY + 5, deletedAt = NOW)
        val quantities = mapOf("a" to 2000L, "b" to 4000L, "c" to 1000L, "d" to 1000L, "e" to 3000L, "f" to 7000L)

        val groups = groupBatches(listOf(a, b, opened, elsewhere, homemade, deleted), quantities, 2)

        assertEquals(4, groups.size)
        val main = groups.single { it.key == a.groupKey }
        assertEquals(listOf("a", "b"), main.batches.map { it.id })
        assertEquals(6000L, main.quantity)
        // Opened two days of validity: nearest effective expiry, first group
        assertEquals("c", groups.first().batches.single().id)
        assertEquals("e", groups.last().batches.single().id)
    }

    @Test
    fun `groups at zero are hidden`() {
        val groups = groupBatches(
            listOf(batch("a", expiresOn = TODAY), batch("b", expiresOn = TODAY + 1)),
            mapOf("a" to 0L, "b" to 1000L),
            null,
        )
        assertEquals(listOf("b"), visibleGroups(groups).map { it.batches.single().id })
    }

    @Test
    fun `consumption order puts nearest expiry first and batches without expiry last`() {
        val batches = listOf(
            batch("none-old", purchasedOn = TODAY - 50),
            batch("late", expiresOn = TODAY + 10),
            batch("opened", expiresOn = TODAY + 10, openedOn = TODAY - 1),
            batch("none-new", purchasedOn = TODAY - 5),
            batch("soon-new", expiresOn = TODAY + 2, purchasedOn = TODAY - 1),
            batch("soon-old", expiresOn = TODAY + 2, purchasedOn = TODAY - 9),
            batch("none-prepared", origin = Origin.HOMEMADE, preparedOn = TODAY - 60),
        )
        val ordered = batches.sortedWith(consumptionOrder(2)).map { it.id }
        assertEquals(
            listOf("opened", "soon-old", "soon-new", "late", "none-prepared", "none-old", "none-new"),
            ordered,
        )
    }

    @Test
    fun `without dates the oldest created batch comes first`() {
        val ordered = listOf(
            batch("new", createdAt = NOW - DAY),
            batch("old", createdAt = NOW - 20 * DAY),
        ).sortedWith(consumptionOrder(null)).map { it.id }
        assertEquals(listOf("old", "new"), ordered)
    }

    @Test
    fun `modifiedAt always grows`() {
        assertEquals(NOW, nextModifiedAt(NOW, null))
        assertEquals(NOW, nextModifiedAt(NOW, NOW - 5))
        assertEquals(NOW + 1, nextModifiedAt(NOW, NOW))
        assertEquals(NOW + 501, nextModifiedAt(NOW, NOW + 500))
    }
}
