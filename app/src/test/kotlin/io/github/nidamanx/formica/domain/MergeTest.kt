// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.domain

import io.github.nidamanx.formica.domain.model.Category
import io.github.nidamanx.formica.domain.model.ImportMode
import io.github.nidamanx.formica.domain.model.Location
import io.github.nidamanx.formica.domain.model.MovementType
import io.github.nidamanx.formica.domain.model.Product
import io.github.nidamanx.formica.domain.model.ShoppingItem
import io.github.nidamanx.formica.domain.model.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The scenarios of SPEC §14 (the newer formatVersion one is in FormatTest) and the rules of §11.5. */
class MergeTest {

    private val header = { device: String -> "— $device, 23/11/2026 —" }

    private fun product(
        id: String,
        name: String = id,
        createdAt: Long = 10,
        modifiedAt: Long = createdAt,
        modifiedBy: String = "device-b",
        deletedAt: Long? = null,
        notes: String? = null,
        notesAt: Long? = null,
        categoryId: String? = null,
        defaultLocationId: String? = null,
    ) = Product(
        id = id, name = name, categoryId = categoryId, defaultLocationId = defaultLocationId, notes = notes,
        notesModifiedAt = notesAt, createdAt = createdAt, modifiedAt = modifiedAt, modifiedBy = modifiedBy,
        deletedAt = deletedAt,
    )

    private fun location(id: String, name: String, createdAt: Long = 1) =
        Location(id = id, name = name, sortOrder = 0, createdAt = createdAt, modifiedAt = createdAt, modifiedBy = "x")

    private fun category(id: String, name: String, createdAt: Long = 1) =
        Category(id = id, name = name, sortOrder = 0, createdAt = createdAt, modifiedAt = createdAt, modifiedBy = "x")

    private fun item(id: String, productId: String, createdAt: Long, automatic: Boolean = true) = ShoppingItem(
        id = id, productId = productId, automatic = automatic, createdAt = createdAt, modifiedAt = createdAt,
        modifiedBy = "x",
    )

    private fun merge(
        local: Snapshot,
        remote: Snapshot,
        mode: ImportMode = ImportMode.MERGE,
        lastExportedAt: Long = 0,
        keepSeparate: Set<String> = emptySet(),
    ) = mergeImport(
        ImportRequest(local, remote, mode, "Pixel", "Anna", lastExportedAt, keepSeparate),
        context(),
        header,
    )

    private fun Snapshot.product(id: String) = products.single { it.id == id }

    // Scenario 1
    @Test
    fun `eggs used on both phones add up after the exchange`() {
        val eggs = product("eggs")
        val box = batch("box", productId = "eggs")
        val bought = movement("box", units(6))
        val onA = Snapshot(products = listOf(eggs), batches = listOf(box), movements = listOf(bought))
        val usedOnA = movement("box", -units(2), MovementType.CONSUMPTION, timestamp = NOW - DAY, id = "a1")
        val usedOnB = movement("box", -units(1), MovementType.CONSUMPTION, timestamp = NOW - DAY, id = "b1")
        val a = onA.copy(movements = onA.movements + usedOnA)
        val b = onA.copy(movements = onA.movements + usedOnB)

        val aAfter = merge(a, b)
        val bAfter = merge(b, a)
        assertEquals(units(3), quantitiesByBatch(aAfter.snapshot.movements).getValue("box"))
        assertEquals(units(3), quantitiesByBatch(bAfter.snapshot.movements).getValue("box"))
        assertEquals(ChangeCounts(updated = 1), aAfter.products)
    }

    // Scenario 2
    @Test
    fun `importing the same file again changes nothing`() {
        val local = Snapshot(products = listOf(product("milk", notes = "intero", notesAt = 20)))
        val remote = Snapshot(
            products = listOf(product("milk", modifiedAt = 30, notes = "scremato", notesAt = 30), product("rice")),
            batches = listOf(batch("b", productId = "rice")),
            movements = listOf(movement("b", units(1))),
        )
        val first = merge(local, remote)
        val again = merge(first.snapshot, remote, lastExportedAt = 0)
        assertEquals(first.snapshot, again.snapshot)
        assertTrue(again.products.isEmpty)
        assertTrue(again.shoppingItems.isEmpty)
    }

    // Scenario 3
    @Test
    fun `an old file does not bring back a deleted product`() {
        val local = Snapshot(products = listOf(product("milk", modifiedAt = 100, deletedAt = 100)))
        val oldFile = Snapshot(products = listOf(product("milk", modifiedAt = 50)))
        val result = merge(local, oldFile)
        assertEquals(100L, result.snapshot.product("milk").deletedAt)
        assertTrue(result.products.isEmpty)
    }

    // Scenario 4
    @Test
    fun `a deletion wins over the changes it saw, a later change wins over the deletion`() {
        val deletedRemotely = Snapshot(products = listOf(product("milk", modifiedAt = 150, deletedAt = 150)))
        val editedBefore = Snapshot(products = listOf(product("milk", name = "Latte", modifiedAt = 100)))
        val editedAfter = Snapshot(products = listOf(product("milk", name = "Latte", modifiedAt = 200)))

        val deleted = merge(editedBefore, deletedRemotely)
        assertEquals(150L, deleted.snapshot.product("milk").deletedAt)
        assertEquals(ChangeCounts(deleted = 1), deleted.products)

        val kept = merge(editedAfter, deletedRemotely)
        assertNull(kept.snapshot.product("milk").deletedAt)
        assertEquals("Latte", kept.snapshot.product("milk").name)
    }

    @Test
    fun `the latest version wins, with the device id breaking ties`() {
        val local = Snapshot(products = listOf(product("p", name = "Mine", modifiedAt = 40, modifiedBy = "device-b")))
        val newer = Snapshot(products = listOf(product("p", name = "Theirs", modifiedAt = 41, modifiedBy = "device-a")))
        val tie = Snapshot(products = listOf(product("p", name = "Theirs", modifiedAt = 40, modifiedBy = "device-c")))
        val tieLower = Snapshot(
            products = listOf(product("p", name = "Theirs", modifiedAt = 40, modifiedBy = "device-0")),
        )
        assertEquals("Theirs", merge(local, newer).snapshot.product("p").name)
        assertEquals("Theirs", merge(local, tie).snapshot.product("p").name)
        assertEquals("Mine", merge(local, tieLower).snapshot.product("p").name)
    }

    // Scenario 5
    @Test
    fun `notes changed on both phones are appended and marked for review`() {
        val local = Snapshot(products = listOf(product("p", modifiedAt = 1500, notes = "Mine", notesAt = 1500)))
        val remote = Snapshot(products = listOf(product("p", modifiedAt = 1600, notes = "Theirs", notesAt = 1600)))
        val result = merge(local, remote, lastExportedAt = 1000)
        val merged = result.snapshot.product("p")
        assertEquals("Mine\n\n— Anna, 23/11/2026 —\nTheirs", merged.notes)
        assertEquals(setOf(RecordRef(RecordType.PRODUCT, "p")), result.notesToReview)
        assertEquals(DEVICE, merged.modifiedBy)
        assertTrue(merged.modifiedAt > 1600)
    }

    @Test
    fun `notes changed on one phone only follow that phone`() {
        val local = Snapshot(products = listOf(product("p", modifiedAt = 1500, notes = "Old", notesAt = 500)))
        val remote = Snapshot(products = listOf(product("p", modifiedAt = 1400, notes = "New", notesAt = 1400)))
        // The record of this phone is newer, its notes are not
        val takeRemote = merge(local, remote, lastExportedAt = 1000).snapshot.product("p")
        assertEquals("New", takeRemote.notes)
        assertEquals(1400L, takeRemote.notesModifiedAt)

        val keepLocal = merge(
            Snapshot(products = listOf(product("p", modifiedAt = 1500, notes = "Mine", notesAt = 1500))),
            Snapshot(products = listOf(product("p", modifiedAt = 1600, notes = "Theirs", notesAt = 500))),
            lastExportedAt = 1000,
        )
        assertEquals("Mine", keepLocal.snapshot.product("p").notes)
        assertTrue(keepLocal.notesToReview.isEmpty())
    }

    @Test
    fun `appended notes coming back are not appended twice`() {
        val appended = "Theirs\n\n— Pixel, 23/11/2026 —\nMine"
        val local = Snapshot(products = listOf(product("p", modifiedAt = 1600, notes = "Theirs", notesAt = 1600)))
        val remote = Snapshot(products = listOf(product("p", modifiedAt = 2000, notes = appended, notesAt = 2000)))
        val result = merge(local, remote, lastExportedAt = 1000)
        assertEquals(appended, result.snapshot.product("p").notes)
        assertTrue(result.notesToReview.isEmpty())
    }

    @Test
    fun `the best price fields travel together`() {
        val local = Snapshot(
            products = listOf(
                product("p", modifiedAt = 50).copy(bestPriceStoreId = "s1", bestPriceCents = 120, bestPriceNotedAt = 40),
            ),
        )
        val remote = Snapshot(
            products = listOf(
                product("p", name = "Latte", modifiedAt = 60)
                    .copy(bestPriceStoreId = "s2", bestPriceCents = 99, bestPriceNotedAt = 30),
            ),
        )
        val merged = merge(local, remote).snapshot.product("p")
        assertEquals("Latte", merged.name)
        assertEquals("s1", merged.bestPriceStoreId)
        assertEquals(120L, merged.bestPriceCents)
        assertEquals(40L, merged.bestPriceNotedAt)
        assertTrue(merged.modifiedAt > 60)
    }

    // Scenario 6
    @Test
    fun `a duplicate product is merged into the older one`() {
        val local = Snapshot(
            products = listOf(product("pA", name = "Latte", createdAt = 10, notes = "Mine", notesAt = 10)),
        )
        val remote = Snapshot(
            products = listOf(
                product("pB", name = " latte ", createdAt = 20, notes = "Theirs", notesAt = 20),
            ),
            batches = listOf(batch("bB", productId = "pB")),
            movements = listOf(movement("bB", units(2))),
            shoppingItems = listOf(item("iB", "pB", createdAt = 20, automatic = false)),
        )
        val result = merge(local, remote)
        assertEquals(listOf(ProductDuplicate("pA", "Latte", "pB", " latte ")), result.productDuplicates)
        val s = result.snapshot
        assertEquals("pA", s.product("pB").mergedIntoId)
        assertNotNull(s.product("pB").deletedAt)
        assertEquals("pA", s.batches.single().productId)
        assertEquals("pA", s.shoppingItems.single().productId)
        assertEquals("Mine\n\n— Anna, 23/11/2026 —\nTheirs", s.product("pA").notes)
        assertEquals(ChangeCounts(updated = 1), result.products)

        val separate = merge(local, remote, keepSeparate = setOf("pB")).snapshot
        assertNull(separate.product("pB").deletedAt)
        assertEquals("pB", separate.batches.single().productId)
    }

    @Test
    fun `a merge made on the other phone moves the references of this phone too`() {
        // This phone (B) still has its own "Latte" and has just added a batch to it
        val onB = Snapshot(
            products = listOf(product("pB", name = "Latte", createdAt = 20)),
            batches = listOf(batch("bB", productId = "pB"), batch("bNew", productId = "pB", createdAt = NOW)),
        )
        // Phone A merged pB into its older pA and moved bB
        val fromA = Snapshot(
            products = listOf(
                product("pA", name = "Latte", createdAt = 10),
                product("pB", name = "Latte", createdAt = 20, modifiedAt = 500, deletedAt = 500)
                    .copy(mergedIntoId = "pA"),
            ),
            batches = listOf(batch("bB", productId = "pA").copy(modifiedAt = 500)),
        )
        val s = merge(onB, fromA).snapshot
        assertNotNull(s.product("pB").deletedAt)
        assertEquals(setOf("pA"), s.batches.map { it.productId }.toSet())
        assertTrue(s.product("pA").deletedAt == null)
    }

    @Test
    fun `locations and categories with the same name merge on their own`() {
        val local = Snapshot(
            locations = listOf(location("fridgeA", "Frigo", createdAt = 1)),
            categories = listOf(category("dairyA", "Latticini", createdAt = 5)),
        )
        val remote = Snapshot(
            locations = listOf(location("fridgeB", "frigo", createdAt = 2)),
            categories = listOf(category("dairyB", "Latticini", createdAt = 3)),
            products = listOf(product("milk", categoryId = "dairyB", defaultLocationId = "fridgeB")),
            batches = listOf(batch("b", productId = "milk", locationId = "fridgeB")),
        )
        val result = merge(local, remote)
        val s = result.snapshot
        assertEquals(2, result.autoMerged)
        // The older one stays: the local fridge, the remote category
        assertEquals("fridgeA", s.locations.single { it.id == "fridgeB" }.mergedIntoId)
        assertEquals("dairyB", s.categories.single { it.id == "dairyA" }.mergedIntoId)
        assertEquals("fridgeA", s.product("milk").defaultLocationId)
        assertEquals("dairyB", s.product("milk").categoryId)
        assertEquals("fridgeA", s.batches.single().locationId)
    }

    // Scenario 7
    @Test
    fun `duplicate automatic items become one`() {
        val local = Snapshot(products = listOf(product("p")), shoppingItems = listOf(item("i1", "p", createdAt = 10)))
        val remote = Snapshot(products = listOf(product("p")), shoppingItems = listOf(item("i2", "p", createdAt = 20)))
        val items = merge(local, remote).snapshot.shoppingItems
        assertNull(items.single { it.id == "i1" }.deletedAt)
        assertNotNull(items.single { it.id == "i2" }.deletedAt)
    }

    // Scenario 8
    @Test
    fun `new products only adds unknown products and nothing else`() {
        val local = Snapshot(
            locations = listOf(location("fridgeA", "Frigo")),
            products = listOf(product("known", name = "Pane")),
        )
        val remote = Snapshot(
            locations = listOf(location("fridgeB", "Frigo"), location("cellar", "Cantina")),
            categories = listOf(category("jam", "Conserve")),
            products = listOf(
                product("known", name = "Pane nero", modifiedAt = 99),
                product("new", name = "Marmellata", categoryId = "jam", defaultLocationId = "fridgeB"),
                product("gone", modifiedAt = 50, deletedAt = 50),
            ),
            batches = listOf(batch("b1", productId = "new", locationId = "cellar"), batch("b2", productId = "known")),
            movements = listOf(movement("b1", units(3)), movement("b2", units(1))),
        )
        val result = merge(local, remote, mode = ImportMode.NEW_PRODUCTS_ONLY)
        val s = result.snapshot
        assertEquals("Pane", s.product("known").name)
        assertEquals(listOf("known", "new"), s.products.map { it.id })
        assertEquals("fridgeA", s.product("new").defaultLocationId)
        assertEquals("jam", s.product("new").categoryId)
        assertEquals(listOf("fridgeA", "cellar"), s.locations.map { it.id })
        assertEquals(listOf("b1"), s.batches.map { it.id })
        assertEquals(units(3), quantitiesByBatch(s.movements).getValue("b1"))
        assertEquals(ChangeCounts(new = 1), result.products)
    }

    @Test
    fun `replace everything takes the file as it is`() {
        val local = Snapshot(products = listOf(product("mine")), movements = listOf(movement("x", units(1))))
        val remote = Snapshot(products = listOf(product("theirs")))
        val result = merge(local, remote, mode = ImportMode.REPLACE_ALL)
        assertEquals(remote, result.snapshot)
        assertEquals(ChangeCounts(new = 1, deleted = 1), result.products)
    }
}
