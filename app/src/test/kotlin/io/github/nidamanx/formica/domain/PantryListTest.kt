// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.domain

import io.github.nidamanx.formica.domain.model.MovementType
import io.github.nidamanx.formica.domain.model.Origin
import io.github.nidamanx.formica.domain.model.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PantryListTest {

    private val thresholds = ListThresholds(expiryWarningDays = 3, runningLowDays = 7)

    private fun product(id: String, name: String = id, notes: String? = null, warning: Int? = null) = Product(
        id = id,
        name = name,
        notes = notes,
        expiryWarningDays = warning,
        createdAt = 0,
        modifiedAt = 0,
        modifiedBy = "d",
    )

    private fun summary(
        product: Product,
        batches: List<io.github.nidamanx.formica.domain.model.Batch>,
        quantities: Map<String, Long>,
        movements: List<io.github.nidamanx.formica.domain.model.Movement> = emptyList(),
    ) = summarizeProduct(product, batches, quantities, movements, TODAY, NOW, thresholds)

    @Test
    fun `the nearest expiry ignores empty and deleted batches`() {
        val s = summary(
            product("milk"),
            listOf(
                batch("empty", productId = "milk", expiresOn = TODAY - 5),
                batch("deleted", productId = "milk", expiresOn = TODAY - 4, deletedAt = NOW),
                batch("late", productId = "milk", expiresOn = TODAY + 10, locationId = "cellar"),
                batch("soon", productId = "milk", expiresOn = TODAY + 2),
            ),
            mapOf("empty" to 0L, "deleted" to units(1), "late" to units(1), "soon" to units(1)),
        )
        assertEquals(TODAY + 2, s.nearestExpiry)
        assertEquals(ExpiryStatus.EXPIRING, s.expiryStatus)
        assertEquals(units(2), s.quantity)
        assertEquals(listOf("fridge", "cellar"), s.locationIds)
    }

    @Test
    fun `expiry status follows the warning days of the product or the default`() {
        val b = listOf(batch("b", productId = "p", expiresOn = TODAY + 5))
        val q = mapOf("b" to units(1))
        assertEquals(ExpiryStatus.OK, summary(product("p"), b, q).expiryStatus)
        assertEquals(ExpiryStatus.EXPIRING, summary(product("p", warning = 5), b, q).expiryStatus)
        val expired = listOf(batch("b", productId = "p", expiresOn = TODAY - 1))
        assertEquals(ExpiryStatus.EXPIRED, summary(product("p"), expired, q).expiryStatus)
        assertEquals(ExpiryStatus.NONE, summary(product("p"), listOf(batch("b", productId = "p")), q).expiryStatus)
    }

    @Test
    fun `preparation dates and readiness come from the batches with stock`() {
        val s = summary(
            product("yogurt"),
            listOf(
                batch("old", origin = Origin.HOMEMADE, preparedOn = TODAY - 90),
                batch("new", origin = Origin.HOMEMADE, preparedOn = TODAY - 2, readyFrom = TODAY + 1),
                batch("gone", origin = Origin.HOMEMADE, preparedOn = TODAY - 200),
            ),
            mapOf("old" to units(1), "new" to units(6), "gone" to 0L),
        )
        assertEquals(TODAY - 90, s.oldestPreparedOn)
        assertEquals(TODAY + 1, s.readyFrom)
    }

    @Test
    fun `running low needs an estimate under the threshold and some stock`() {
        val movements = listOf(
            movement("b", units(10), MovementType.PURCHASE, NOW - 30 * DAY),
            movement("b", -units(3), MovementType.CONSUMPTION, NOW - 20 * DAY),
            movement("b", -units(3), MovementType.CONSUMPTION, NOW - 10 * DAY),
            movement("b", -units(3), MovementType.CONSUMPTION, NOW - 2 * DAY),
        )
        // 9 units in 30 days: 1 unit lasts about 3 days
        val b = listOf(batch("b"))
        assertTrue(summary(product("p1"), b, mapOf("b" to units(1)), movements).runningLow)
        assertFalse(summary(product("p1"), b, mapOf("b" to units(5)), movements).runningLow)
        assertFalse(summary(product("p1"), b, mapOf("b" to 0L), movements).runningLow)
        assertFalse(summary(product("p1"), b, mapOf("b" to units(1))).runningLow)
    }

    @Test
    fun `a negative batch marks the product to check`() {
        val s = summary(product("p1"), listOf(batch("a"), batch("b")), mapOf("a" to units(2), "b" to -units(1)))
        assertTrue(s.needsCheck)
        assertEquals(units(1), s.quantity)
    }

    @Test
    fun `search looks into names and notes of products and batches`() {
        val withNote = summary(
            product("passata", name = "Passata", notes = "Ricetta della nonna"),
            listOf(batch("b", productId = "passata").copy(notes = "con BASILICO")),
            mapOf("b" to units(1)),
        )
        val other = summary(product("eggs", name = "Uova"), emptyList(), emptyMap())
        val all = listOf(withNote, other)
        fun search(q: String) = filterAndSort(all, PantryFilter(query = q), PantrySort.NAME, naturalOrder())
            .map { it.product.id }

        assertEquals(listOf("passata"), search("  basilico "))
        assertEquals(listOf("passata"), search("NONNA"))
        assertEquals(listOf("eggs"), search("uov"))
        assertEquals(listOf("passata", "eggs"), search(""))
    }

    @Test
    fun `location and category filters`() {
        val inCellar = summary(product("a"), listOf(batch("b", productId = "a", locationId = "cellar")), mapOf("b" to units(1)))
        val emptyDefaultCellar = summary(product("b").copy(defaultLocationId = "cellar"), emptyList(), emptyMap())
        val inFridge = summary(product("c").copy(categoryId = "dairy"), listOf(batch("x", productId = "c")), mapOf("x" to units(1)))
        val all = listOf(inCellar, emptyDefaultCellar, inFridge)

        assertEquals(
            listOf("a", "b"),
            filterAndSort(all, PantryFilter(locationId = "cellar"), PantrySort.NAME, naturalOrder()).map { it.product.id },
        )
        assertEquals(
            listOf("c"),
            filterAndSort(all, PantryFilter(categoryId = "dairy"), PantrySort.NAME, naturalOrder()).map { it.product.id },
        )
    }

    @Test
    fun `sort by expiry puts products without expiry last, then by name`() {
        val noExpiry = summary(product("a", name = "Aceto"), emptyList(), emptyMap())
        val late = summary(product("b", name = "Burro"), listOf(batch("1", productId = "b", expiresOn = TODAY + 9)), mapOf("1" to units(1)))
        val soon = summary(product("c", name = "Crema"), listOf(batch("2", productId = "c", expiresOn = TODAY + 1)), mapOf("2" to units(1)))
        val sorted = filterAndSort(listOf(noExpiry, late, soon), PantryFilter(), PantrySort.EXPIRY, naturalOrder())
        assertEquals(listOf("c", "b", "a"), sorted.map { it.product.id })
        assertNull(sorted.last().nearestExpiry)
    }

    @Test
    fun `a homemade product strictly under its minimum stock is to make`() {
        val pesto = product("pesto").copy(homemade = true, minStock = units(2))
        val b = listOf(batch("b", productId = "pesto", origin = Origin.HOMEMADE, preparedOn = TODAY - 60))
        assertTrue(summary(pesto, b, mapOf("b" to units(1))).toMake)
        assertFalse(summary(pesto, b, mapOf("b" to units(2))).toMake)
        assertTrue(summary(pesto, emptyList(), emptyMap()).toMake)
        assertFalse(summary(pesto.copy(minStock = null), emptyList(), emptyMap()).toMake)
        assertFalse(summary(pesto.copy(homemade = false), b, mapOf("b" to units(1))).toMake)
    }

    @Test
    fun `the homemade filter keeps only homemade products`() {
        val bought = summary(product("milk"), emptyList(), emptyMap())
        val made = summary(product("jam").copy(homemade = true), emptyList(), emptyMap())
        assertEquals(
            listOf("jam"),
            filterAndSort(listOf(bought, made), PantryFilter(homemadeOnly = true), PantrySort.NAME, naturalOrder())
                .map { it.product.id },
        )
    }

    @Test
    fun `sort by preparation date puts the oldest first and the rest last`() {
        val bought = summary(product("a", name = "Aceto"), emptyList(), emptyMap())
        val recent = summary(
            product("b", name = "Burro"),
            listOf(batch("1", productId = "b", origin = Origin.HOMEMADE, preparedOn = TODAY - 5)),
            mapOf("1" to units(1)),
        )
        val old = summary(
            product("c", name = "Crauti"),
            listOf(batch("2", productId = "c", origin = Origin.HOMEMADE, preparedOn = TODAY - 50)),
            mapOf("2" to units(1)),
        )
        val sorted = filterAndSort(listOf(bought, recent, old), PantryFilter(), PantrySort.PREPARATION, naturalOrder())
        assertEquals(listOf("c", "b", "a"), sorted.map { it.product.id })
    }
}
