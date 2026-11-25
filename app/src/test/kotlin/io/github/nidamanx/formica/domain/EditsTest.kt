// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.domain

import io.github.nidamanx.formica.domain.model.Batch
import io.github.nidamanx.formica.domain.model.Location
import io.github.nidamanx.formica.domain.model.MeasureUnit
import io.github.nidamanx.formica.domain.model.Origin
import io.github.nidamanx.formica.domain.model.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditsTest {

    private val stored = Product(
        id = "p1",
        name = "Milk",
        unit = MeasureUnit.LITER,
        bestPriceStoreId = "s1",
        bestPriceCents = 120,
        bestPriceNotedAt = NOW - 100 * DAY,
        notes = "whole",
        notesModifiedAt = NOW - 50 * DAY,
        createdAt = NOW - 200 * DAY,
        modifiedAt = NOW - 10 * DAY,
        modifiedBy = "device-b",
    )

    @Test
    fun `an edit keeps identity and creation and updates the metadata`() {
        val edited = editProduct(stored, stored.copy(id = "other", createdAt = 0, name = "Whole milk"), context())

        assertEquals("p1", edited.id)
        assertEquals(stored.createdAt, edited.createdAt)
        assertEquals("Whole milk", edited.name)
        assertEquals(NOW, edited.modifiedAt)
        assertEquals(DEVICE, edited.modifiedBy)
        assertEquals(stored.notesModifiedAt, edited.notesModifiedAt)
        assertEquals(stored.bestPriceNotedAt, edited.bestPriceNotedAt)
    }

    @Test
    fun `changed notes and best price are dated separately`() {
        val edited = editProduct(stored, stored.copy(notes = "skimmed", bestPriceCents = 99), context())
        assertEquals(NOW, edited.notesModifiedAt)
        assertEquals(NOW, edited.bestPriceNotedAt)
    }

    @Test
    fun `an edit cannot undelete or unmerge`() {
        val deleted = stored.copy(deletedAt = NOW - DAY, mergedIntoId = "p2")
        val edited = editProduct(deleted, deleted.copy(deletedAt = null, mergedIntoId = null), context())
        assertEquals(NOW - DAY, edited.deletedAt)
        assertEquals("p2", edited.mergedIntoId)
    }

    @Test
    fun `modifiedAt grows even with a clock behind the stored record`() {
        val future = stored.copy(modifiedAt = NOW + 5000)
        assertEquals(NOW + 5001, editProduct(future, future, context()).modifiedAt)
    }

    @Test
    fun `a new product gets fresh metadata`() {
        val draft = stored.copy(bestPriceStoreId = null, bestPriceCents = null, notes = null, deletedAt = NOW)
        val created = newProduct(draft, context())
        assertEquals("id-1", created.id)
        assertEquals(NOW, created.createdAt)
        assertEquals(NOW, created.modifiedAt)
        assertEquals(DEVICE, created.modifiedBy)
        assertNull(created.deletedAt)
        assertNull(created.bestPriceNotedAt)
        assertNull(created.notesModifiedAt)

        val withData = newProduct(stored, context())
        assertEquals(NOW, withData.bestPriceNotedAt)
        assertEquals(NOW, withData.notesModifiedAt)
    }

    @Test
    fun `batch notes are dated only when they change`() {
        val b = batch("b").copy(notes = "basil", notesModifiedAt = NOW - DAY)
        assertEquals(NOW - DAY, editBatchNotes(b, "basil", context()).notesModifiedAt)
        assertEquals(NOW, editBatchNotes(b, "basil and garlic", context()).notesModifiedAt)
    }

    @Test
    fun `a deletion is a tombstone at the new modifiedAt`() {
        val location = Location(
            id = "l1",
            name = "Cellar",
            sortOrder = 3,
            createdAt = NOW - 9 * DAY,
            modifiedAt = NOW + 10,
            modifiedBy = "device-b",
        )
        val deleted = location.softDeleted(context())
        assertEquals(NOW + 11, deleted.deletedAt)
        assertEquals(NOW + 11, deleted.modifiedAt)
        assertEquals(DEVICE, deleted.modifiedBy)
    }

    // Edit pack

    private fun groupOf(vararg batches: Batch) =
        BatchGroup(batches.first().groupKey, batches.toList(), 0L)

    @Test
    fun `editing a pack changes every batch and only what was edited`() {
        val a = batch("a", expiresOn = TODAY + 5, purchasedOn = TODAY - 3).copy(storeId = "s1")
        val b = batch("b", expiresOn = TODAY + 5, purchasedOn = TODAY - 1).copy(storeId = "s2")
        val edit = GroupEdit(expiresOn = Edited(TODAY + 9), openedOn = Edited(TODAY))
        val changed = editGroup(groupOf(a, b), edit, context()).associateBy { it.id }

        assertEquals(setOf("a", "b"), changed.keys)
        assertEquals(TODAY + 9, changed.getValue("a").expiresOn)
        assertEquals(TODAY, changed.getValue("b").openedOn)
        assertEquals(TODAY - 3, changed.getValue("a").purchasedOn)
        assertEquals("s2", changed.getValue("b").storeId)
        assertEquals(DEVICE, changed.getValue("a").modifiedBy)
        assertTrue(changed.getValue("a").modifiedAt > a.modifiedAt)
    }

    @Test
    fun `an edited purchase date and store apply to all batches, and can be cleared`() {
        val a = batch("a", purchasedOn = TODAY - 3).copy(storeId = "s1")
        val b = batch("b", purchasedOn = TODAY - 1).copy(storeId = "s2")
        val changed = editGroup(groupOf(a, b), GroupEdit(purchasedOn = Edited(TODAY), storeId = Edited(null)), context())

        assertEquals(listOf(TODAY, TODAY), changed.map { it.purchasedOn })
        assertEquals(listOf(null, null), changed.map { it.storeId })
    }

    @Test
    fun `a homemade pack gets the preparation date, not the purchase fields`() {
        val a = batch("a", origin = Origin.HOMEMADE, preparedOn = TODAY - 10)
        val edit = GroupEdit(preparedOn = Edited(TODAY - 12), purchasedOn = Edited(TODAY), storeId = Edited("s1"))
        val changed = editGroup(groupOf(a), edit, context()).single()

        assertEquals(TODAY - 12, changed.preparedOn)
        assertEquals(null, changed.purchasedOn)
        assertEquals(null, changed.storeId)
    }

    @Test
    fun `an edit that changes nothing returns no batch`() {
        val a = batch("a", expiresOn = TODAY + 5)
        assertTrue(editGroup(groupOf(a), GroupEdit(expiresOn = Edited(TODAY + 5)), context()).isEmpty())
    }
}
