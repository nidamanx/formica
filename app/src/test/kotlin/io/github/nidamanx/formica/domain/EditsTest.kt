// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.domain

import io.github.nidamanx.formica.domain.model.Location
import io.github.nidamanx.formica.domain.model.MeasureUnit
import io.github.nidamanx.formica.domain.model.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
