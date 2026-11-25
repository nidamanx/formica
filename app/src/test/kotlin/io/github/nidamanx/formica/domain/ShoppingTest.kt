// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.domain

import io.github.nidamanx.formica.domain.model.MovementType
import io.github.nidamanx.formica.domain.model.Product
import io.github.nidamanx.formica.domain.model.ShoppingItem
import io.github.nidamanx.formica.domain.model.ShoppingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingTest {

    private fun product(
        id: String,
        name: String = id,
        minStock: Long? = null,
        homemade: Boolean = false,
        categoryId: String? = null,
        storeId: String? = null,
    ) = Product(
        id = id,
        name = name,
        minStock = minStock,
        homemade = homemade,
        categoryId = categoryId,
        bestPriceStoreId = storeId,
        createdAt = 0,
        modifiedAt = 0,
        modifiedBy = "d",
    )

    private fun item(
        id: String,
        productId: String? = null,
        text: String? = null,
        status: ShoppingStatus = ShoppingStatus.TO_BUY,
        categoryId: String? = null,
        storeId: String? = null,
        modifiedAt: Long = NOW - DAY,
        deletedAt: Long? = null,
    ) = ShoppingItem(
        id = id,
        productId = productId,
        text = text,
        categoryId = categoryId,
        storeId = storeId,
        status = status,
        createdAt = NOW - 2 * DAY,
        modifiedAt = modifiedAt,
        modifiedBy = "device-b",
        deletedAt = deletedAt,
    )

    @Test
    fun `to buy creates one open item per product and updates its quantity`() {
        val created = planToBuy("milk", null, emptyList(), automatic = false, ctx = context())
        assertNotNull(created)
        assertEquals("milk", created!!.productId)
        assertEquals(ShoppingStatus.TO_BUY, created.status)

        val open = item("i1", productId = "milk")
        assertNull(planToBuy("milk", null, listOf(open), automatic = false, ctx = context()))
        val updated = planToBuy("milk", units(2), listOf(open), automatic = false, ctx = context())
        assertEquals("i1", updated!!.id)
        assertEquals(units(2), updated.quantity)
        assertEquals(DEVICE, updated.modifiedBy)

        // A bought or deleted item is not open: a new one is created
        val bought = item("i2", productId = "milk", status = ShoppingStatus.BOUGHT)
        val deleted = item("i3", productId = "milk", deletedAt = NOW)
        assertEquals("id-1", planToBuy("milk", null, listOf(bought, deleted), false, context())!!.id)
    }

    @Test
    fun `the minimum stock triggers only when the total drops under it`() {
        val milk = product("milk", minStock = units(2))
        assertTrue(droppedBelowMinStock(milk, before = units(2), after = units(1)))
        assertTrue(droppedBelowMinStock(milk, before = units(3), after = 0))
        assertFalse(droppedBelowMinStock(milk, before = units(3), after = units(2)))
        assertFalse(droppedBelowMinStock(milk, before = units(1), after = 0))
        assertFalse(droppedBelowMinStock(milk.copy(homemade = true), before = units(2), after = units(1)))
        assertFalse(droppedBelowMinStock(milk.copy(minStock = null), before = units(2), after = units(1)))
    }

    @Test
    fun `frequent products count recent purchases and skip those in the list`() {
        val products = listOf(product("bread"), product("milk"), product("eggs"), product("old"))
        val batchProduct = mapOf("b-bread" to "bread", "b-milk" to "milk", "b-eggs" to "eggs", "b-old" to "old")
        val movements = listOf(
            movement("b-bread", units(1), MovementType.PURCHASE, NOW - 2 * DAY),
            movement("b-bread", units(1), MovementType.PURCHASE, NOW - 9 * DAY),
            movement("b-milk", units(1), MovementType.PURCHASE, NOW - 3 * DAY),
            movement("b-milk", -units(1), MovementType.CONSUMPTION, NOW - DAY),
            movement("b-eggs", units(6), MovementType.PURCHASE, NOW - 5 * DAY),
            movement("b-old", units(1), MovementType.PURCHASE, NOW - 200 * DAY),
        )
        val inList = listOf(item("i", productId = "milk"))
        val frequent = frequentProducts(products, movements, batchProduct, inList, NOW, naturalOrder())
        assertEquals(listOf("bread", "eggs"), frequent.map { it.id })
    }

    @Test
    fun `undone purchases do not count as frequent`() {
        val products = listOf(product("milk"), product("eggs"))
        val movements = listOf(
            movement("b-milk", units(1), MovementType.PURCHASE, NOW - DAY, id = "buy"),
            movement("b-milk", -units(1), MovementType.CORRECTION, NOW).copy(groupId = "buy"),
            movement("b-eggs", units(6), MovementType.PURCHASE, NOW - DAY),
        )
        val batchProduct = mapOf("b-milk" to "milk", "b-eggs" to "eggs")
        val frequent = frequentProducts(products, movements, batchProduct, emptyList(), NOW, naturalOrder())
        assertEquals(listOf("eggs"), frequent.map { it.id })
    }

    @Test
    fun `frequent products are at most ten`() {
        val products = (1..12).map { product("p$it", name = "P%02d".format(it)) }
        val movements = products.map { movement("b-${it.id}", units(1), MovementType.PURCHASE, NOW - DAY) }
        val batchProduct = products.associate { "b-${it.id}" to it.id }
        assertEquals(10, frequentProducts(products, movements, batchProduct, emptyList(), NOW, naturalOrder()).size)
    }

    @Test
    fun `running low suggestions skip homemade products and those in the list`() {
        fun summary(p: Product, low: Boolean) = ProductSummary(
            product = p, quantity = units(1), nearestExpiry = null, expiryStatus = ExpiryStatus.NONE,
            readyFrom = null, oldestPreparedOn = null, runningLow = low, toMake = false, needsCheck = false,
            locationIds = emptyList(), hasNotes = false, searchText = p.name,
        )
        val summaries = listOf(
            summary(product("oil", name = "Olio"), true),
            summary(product("cheese", name = "Formaggio"), true),
            summary(product("jam", homemade = true), true),
            summary(product("rice"), false),
            summary(product("coffee"), true),
        )
        val suggestions = runningLowSuggestions(summaries, listOf(item("i", productId = "coffee")), naturalOrder())
        assertEquals(listOf("cheese", "oil"), suggestions.map { it.id })
    }

    @Test
    fun `the list groups by category or store and puts bought items last`() {
        val products = mapOf(
            "milk" to product("milk", name = "Latte", categoryId = "dairy", storeId = "market"),
            "eggs" to product("eggs", name = "Uova", categoryId = "dairy"),
            "gone" to product("gone").copy(deletedAt = NOW),
        )
        val items = listOf(
            item("1", productId = "milk"),
            item("2", productId = "eggs"),
            item("3", text = "Pile", storeId = "market"),
            item("4", productId = "gone"),
            item("5", text = "Caffè", status = ShoppingStatus.BOUGHT, modifiedAt = NOW - 5 * DAY),
            item("6", text = "Banane", status = ShoppingStatus.BOUGHT, modifiedAt = NOW - DAY),
            item("7", text = "Vecchia", deletedAt = NOW),
        )
        val byCategory = shoppingView(items, products, ShoppingGrouping.CATEGORY, null, listOf("dairy"), naturalOrder())
        assertEquals(listOf("dairy", null), byCategory.groups.map { it.id })
        assertEquals(listOf("Latte", "Uova"), byCategory.groups[0].rows.map { it.name })
        assertEquals(listOf("Pile"), byCategory.groups[1].rows.map { it.name })
        assertEquals(listOf("Banane", "Caffè"), byCategory.bought.map { it.name })

        val byStore = shoppingView(items, products, ShoppingGrouping.STORE, null, listOf("market"), naturalOrder())
        assertEquals(listOf("market", null), byStore.groups.map { it.id })
        assertEquals(listOf("Latte", "Pile"), byStore.groups[0].rows.map { it.name })

        val onlyMarket =
            shoppingView(items, products, ShoppingGrouping.CATEGORY, "market", listOf("dairy"), naturalOrder())
        assertEquals(listOf("Latte", "Pile"), onlyMarket.groups.flatMap { g -> g.rows.map { it.name } })
        assertTrue(onlyMarket.bought.isEmpty())
    }

    @Test
    fun `a group of a deleted category falls into the none group`() {
        val items = listOf(item("1", text = "Sale", categoryId = "deleted"))
        val view = shoppingView(items, emptyMap(), ShoppingGrouping.CATEGORY, null, listOf("dairy"), naturalOrder())
        assertEquals(listOf<String?>(null), view.groups.map { it.id })
    }

    @Test
    fun `clearing bought items leaves tombstones`() {
        val items = listOf(item("1", text = "A"), item("2", text = "B", status = ShoppingStatus.BOUGHT))
        val cleared = clearedBought(items, context())
        assertEquals(listOf("2"), cleared.map { it.id })
        assertEquals(NOW, cleared.single().deletedAt)
        assertEquals(cleared.single().deletedAt, cleared.single().modifiedAt)
    }

    @Test
    fun `a best price older than six months is stale`() {
        val today = TODAY
        val sixMonthsAgo = java.time.LocalDate.ofEpochDay(today).minusMonths(6).toEpochDay()
        assertFalse(isBestPriceStale(sixMonthsAgo * DAY, today))
        assertTrue(isBestPriceStale((sixMonthsAgo - 1) * DAY, today))
        assertFalse(isBestPriceStale(null, today))
    }
}
