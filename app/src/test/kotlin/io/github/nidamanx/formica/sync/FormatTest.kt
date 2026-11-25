// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.sync

import io.github.nidamanx.formica.domain.model.Batch
import io.github.nidamanx.formica.domain.model.Category
import io.github.nidamanx.formica.domain.model.Device
import io.github.nidamanx.formica.domain.model.Location
import io.github.nidamanx.formica.domain.model.MeasureUnit
import io.github.nidamanx.formica.domain.model.Movement
import io.github.nidamanx.formica.domain.model.MovementType
import io.github.nidamanx.formica.domain.model.Origin
import io.github.nidamanx.formica.domain.model.Product
import io.github.nidamanx.formica.domain.model.ShoppingItem
import io.github.nidamanx.formica.domain.model.ShoppingStatus
import io.github.nidamanx.formica.domain.model.Snapshot
import io.github.nidamanx.formica.domain.model.Store
import io.github.nidamanx.formica.domain.model.StoreType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTest {

    private val device = Device("device-a", "Telefono di Anna")

    // Every field set, so that a field lost on the way fails the comparison
    private val snapshot = Snapshot(
        locations = listOf(
            Location(
                id = "l1", name = "Frigo", sortOrder = 0, color = 0xFF00FF, icon = "kitchen",
                notes = "In alto", notesModifiedAt = 5, createdAt = 1, modifiedAt = 2, modifiedBy = "device-a",
                deletedAt = 3, mergedIntoId = "l2",
            ),
        ),
        categories = listOf(
            Category(
                id = "c1", name = "Latticini", sortOrder = 1, color = 7, icon = "egg",
                createdAt = 1, modifiedAt = 2, modifiedBy = "device-b", deletedAt = 4, mergedIntoId = "c2",
            ),
        ),
        stores = listOf(
            Store(
                id = "s1", name = "Cascina", type = StoreType.PRODUCER, sortOrder = 2, notes = "Sabato",
                notesModifiedAt = 6, createdAt = 1, modifiedAt = 2, modifiedBy = "device-a", deletedAt = null,
                mergedIntoId = null,
            ),
        ),
        products = listOf(
            Product(
                id = "p1", name = "Latte", unit = MeasureUnit.LITER, categoryId = "c1", defaultLocationId = "l1",
                homemade = true, bestPriceStoreId = "s1", bestPriceCents = 129, bestPriceReference = "al litro",
                bestPriceNotedAt = 8, minStock = 2_000, shelfLifeDays = 7, daysAfterOpening = 3, daysUntilReady = 1,
                expiryWarningDays = 2, barcode = "8001234567890", mergedIntoId = "p9", notes = "Intero",
                notesModifiedAt = 9, createdAt = 1, modifiedAt = 10, modifiedBy = "device-a", deletedAt = 11,
            ),
        ),
        batches = listOf(
            Batch(
                id = "b1", productId = "p1", locationId = "l1", origin = Origin.HOMEMADE, preparedOn = 20_000,
                purchasedOn = 20_001, storeId = "s1", expiresOn = 20_007, readyFrom = 20_002, openedOn = 20_003,
                notes = "Vasetto blu", notesModifiedAt = 12, createdAt = 1, modifiedAt = 13, modifiedBy = "device-b",
                deletedAt = 14,
            ),
        ),
        movements = listOf(
            Movement("m1", "b1", -1_500, MovementType.TRANSFER, "g1", 15, "device-b"),
        ),
        shoppingItems = listOf(
            ShoppingItem(
                id = "i1", productId = null, text = "Pile", quantity = 4_000, unit = MeasureUnit.PACKAGE,
                storeId = "s1", categoryId = "c1", status = ShoppingStatus.BOUGHT, automatic = true,
                notes = "AA", notesModifiedAt = 16, createdAt = 1, modifiedAt = 17, modifiedBy = "device-a",
                deletedAt = 18,
            ),
        ),
    )

    private fun valid(text: String): ExportFile {
        val result = FormatCodec.read(text)
        assertTrue("expected a valid file, got $result", result is ReadResult.Valid)
        return (result as ReadResult.Valid).file
    }

    @Test
    fun `a snapshot survives the round trip unchanged`() {
        val file = snapshot.toExportFile(device, appVersion = "0.6.0", exportedAt = 1_795_000_000_000)
        val read = valid(FormatCodec.write(file))
        assertEquals(file, read)
        assertEquals(snapshot, read.toSnapshot())
        assertEquals(DeviceDto("device-a", "Telefono di Anna"), read.device)
    }

    @Test
    fun `the file has the documented header and writes null fields`() {
        val text = FormatCodec.write(Snapshot().toExportFile(device, "0.6.0", 42))
        assertTrue(text.contains("\"format\":\"formica\""))
        assertTrue(text.contains("\"formatVersion\":1"))
        assertTrue(text.contains("\"exportedAt\":42"))
        val withNull = FormatCodec.write(
            Snapshot(stores = snapshot.stores).toExportFile(device, "0.6.0", 42),
        )
        assertTrue(withNull.contains("\"deletedAt\":null"))
        assertTrue(withNull.contains("\"type\":\"PRODUCER\""))
    }

    @Test
    fun `unknown fields are ignored`() {
        val text = """
            {"format":"formica","formatVersion":1,"appVersion":"9.0.0","exportedAt":1,"futureHeader":true,
             "device":{"id":"d","name":"n","model":"x"},
             "categories":[{"id":"c","name":"Pane","sortOrder":0,"createdAt":1,"modifiedAt":1,
                            "modifiedBy":"d","futureField":[1,2]}]}
        """.trimIndent()
        val file = valid(text)
        assertEquals("Pane", file.categories.single().name)
        assertEquals(emptyList<ProductDto>(), file.products)
    }

    @Test
    fun `a newer format version is refused`() {
        val text = """{"format":"formica","formatVersion":2,"appVersion":"2.0.0","exportedAt":1,"device":{}}"""
        assertEquals(ReadResult.NewerVersion(2), FormatCodec.read(text))
    }

    @Test
    fun `other files are not Formica files`() {
        assertEquals(ReadResult.NotFormica, FormatCodec.read("hello"))
        assertEquals(ReadResult.NotFormica, FormatCodec.read("[1,2,3]"))
        assertEquals(ReadResult.NotFormica, FormatCodec.read("""{"format":"other","formatVersion":1}"""))
        assertEquals(ReadResult.NotFormica, FormatCodec.read(""))
    }

    @Test
    fun `a damaged Formica file is unreadable`() {
        assertEquals(ReadResult.Unreadable, FormatCodec.read("""{"format":"formica"}"""))
        assertEquals(
            ReadResult.Unreadable,
            FormatCodec.read("""{"format":"formica","formatVersion":1,"appVersion":"1","exportedAt":1}"""),
        )
        val badEnum = """
            {"format":"formica","formatVersion":1,"appVersion":"1","exportedAt":1,"device":{"id":"d","name":"n"},
             "movements":[{"id":"m","batchId":"b","delta":1,"type":"TELEPORT","timestamp":1,"deviceId":"d"}]}
        """.trimIndent()
        assertEquals(ReadResult.Unreadable, FormatCodec.read(badEnum))
    }
}
