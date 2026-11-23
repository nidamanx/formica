// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.domain

import io.github.nidamanx.formica.domain.model.Batch
import io.github.nidamanx.formica.domain.model.Origin
import io.github.nidamanx.formica.domain.model.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpiriesTest {

    private val byName = naturalOrder<String>()

    private fun product(
        id: String,
        warning: Int? = null,
        daysAfterOpening: Int? = null,
        deletedAt: Long? = null,
    ) = Product(
        id = id,
        name = id,
        expiryWarningDays = warning,
        daysAfterOpening = daysAfterOpening,
        createdAt = 0,
        modifiedAt = 0,
        modifiedBy = "d",
        deletedAt = deletedAt,
    )

    private fun overview(products: List<Product>, batches: List<Batch>, quantities: Map<String, Long>? = null) =
        expiryOverview(
            products = products,
            batches = batches,
            quantities = quantities ?: batches.associate { it.id to units(1) },
            today = TODAY,
            defaultWarningDays = 3,
            compareNames = byName,
        )

    private fun List<ExpiryEntry>.ids() = map { it.group.batches.first().id }

    @Test
    fun `groups go to the first section that applies`() {
        val o = overview(
            listOf(product("p")),
            listOf(
                batch("expired", productId = "p", expiresOn = TODAY - 1),
                batch("today", productId = "p", expiresOn = TODAY, locationId = "a"),
                batch("limit", productId = "p", expiresOn = TODAY + 3, locationId = "b"),
                batch("later", productId = "p", expiresOn = TODAY + 4),
                batch(
                    "preparing",
                    productId = "p",
                    origin = Origin.HOMEMADE,
                    preparedOn = TODAY - 1,
                    readyFrom = TODAY + 5,
                    expiresOn = TODAY + 30,
                ),
                batch("ready-expired", productId = "p", readyFrom = TODAY + 1, expiresOn = TODAY - 2, locationId = "c"),
                batch("no-expiry", productId = "p", locationId = "d"),
                batch("ready-no-expiry", productId = "p", readyFrom = TODAY - 1, locationId = "e"),
            ),
        )
        assertEquals(listOf("ready-expired", "expired"), o.expired.ids())
        assertEquals(listOf("today", "limit"), o.expiring.ids())
        assertEquals(listOf("preparing"), o.preparing.ids())
        assertEquals(listOf("later"), o.later.ids())
    }

    @Test
    fun `the warning days of the product win over the default`() {
        val b = listOf(batch("b", productId = "p", expiresOn = TODAY + 6))
        assertEquals(listOf("b"), overview(listOf(product("p", warning = 6)), b).expiring.ids())
        assertEquals(listOf("b"), overview(listOf(product("p", warning = 1)), b).later.ids())
    }

    @Test
    fun `an opened batch expires after its days after opening`() {
        val o = overview(
            listOf(product("milk", daysAfterOpening = 3)),
            listOf(batch("b", productId = "milk", expiresOn = TODAY + 20, openedOn = TODAY - 4)),
        )
        assertEquals(listOf("b"), o.expired.ids())
        assertEquals(TODAY - 1, o.expired.single().expiry)
    }

    @Test
    fun `empty and deleted batches and deleted products are not shown`() {
        val o = overview(
            listOf(product("p"), product("gone", deletedAt = NOW)),
            listOf(
                batch("empty", productId = "p", expiresOn = TODAY),
                batch("deleted", productId = "p", expiresOn = TODAY - 1, deletedAt = NOW),
                batch("of-deleted-product", productId = "gone", expiresOn = TODAY),
            ),
            mapOf("empty" to 0L, "deleted" to units(1), "of-deleted-product" to units(1)),
        )
        assertTrue(o.isEmpty)
    }

    @Test
    fun `batches with the same key are one entry with their total`() {
        val o = overview(
            listOf(product("p")),
            listOf(
                batch("b1", productId = "p", expiresOn = TODAY + 1),
                batch("b2", productId = "p", expiresOn = TODAY + 1),
            ),
            mapOf("b1" to units(2), "b2" to units(3)),
        )
        assertEquals(units(5), o.expiring.single().group.quantity)
    }

    @Test
    fun `sections are sorted by date, then by name`() {
        val o = overview(
            listOf(product("b"), product("a"), product("c")),
            listOf(
                batch("b1", productId = "b", expiresOn = TODAY + 2),
                batch("a1", productId = "a", expiresOn = TODAY + 2),
                batch("c1", productId = "c", expiresOn = TODAY + 1),
                batch("b2", productId = "b", readyFrom = TODAY + 9),
                batch("a2", productId = "a", readyFrom = TODAY + 2),
            ),
        )
        assertEquals(listOf("c1", "a1", "b1"), o.expiring.ids())
        assertEquals(listOf("a2", "b2"), o.preparing.ids())
    }

    @Test
    fun `no digest when nothing expires or becomes ready today`() {
        val o = overview(
            listOf(product("p")),
            listOf(
                batch("later", productId = "p", expiresOn = TODAY + 10),
                batch("preparing", productId = "p", readyFrom = TODAY + 1),
                batch("ready-yesterday", productId = "p", readyFrom = TODAY - 1, expiresOn = TODAY + 20),
            ),
        )
        assertNull(dailyDigest(o, TODAY, byName))
    }

    @Test
    fun `the digest counts products, not batches`() {
        val o = overview(
            listOf(product("milk"), product("yogurt"), product("cheese", warning = 5), product("sauerkraut")),
            listOf(
                batch("milk1", productId = "milk", expiresOn = TODAY + 1),
                batch("milk2", productId = "milk", expiresOn = TODAY + 2),
                batch("milk-old", productId = "milk", expiresOn = TODAY - 3),
                batch("yogurt1", productId = "yogurt", expiresOn = TODAY - 1),
                batch("cheese1", productId = "cheese", expiresOn = TODAY + 5),
                batch("kraut1", productId = "sauerkraut", readyFrom = TODAY, expiresOn = TODAY + 60),
                batch("kraut2", productId = "sauerkraut", readyFrom = TODAY, expiresOn = TODAY + 90, locationId = "x"),
            ),
        )
        val digest = requireNotNull(dailyDigest(o, TODAY, byName))
        assertEquals(2, digest.expiringProducts)
        assertEquals(5L, digest.expiringWithinDays)
        assertEquals(2, digest.expiredProducts)
        assertEquals(1, digest.readyProducts)
        assertEquals(
            listOf(
                DigestLine("milk", DigestKind.EXPIRED, -3),
                DigestLine("yogurt", DigestKind.EXPIRED, -1),
                DigestLine("milk", DigestKind.EXPIRING, 1),
                DigestLine("cheese", DigestKind.EXPIRING, 5),
                DigestLine("sauerkraut", DigestKind.READY, 0),
            ),
            digest.lines,
        )
    }

    @Test
    fun `a batch ready today is reported even when it is also expiring`() {
        val o = overview(
            listOf(product("yogurt")),
            listOf(batch("b", productId = "yogurt", readyFrom = TODAY, expiresOn = TODAY + 2)),
        )
        val digest = requireNotNull(dailyDigest(o, TODAY, byName))
        assertEquals(1, digest.expiringProducts)
        assertEquals(1, digest.readyProducts)
        assertEquals(0, digest.expiredProducts)
    }
}
