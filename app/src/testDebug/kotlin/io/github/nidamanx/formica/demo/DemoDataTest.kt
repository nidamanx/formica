// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.demo

import io.github.nidamanx.formica.domain.DurationEstimate
import io.github.nidamanx.formica.domain.ExpiryStatus
import io.github.nidamanx.formica.domain.ListThresholds
import io.github.nidamanx.formica.domain.estimateDuration
import io.github.nidamanx.formica.domain.expiryOverview
import io.github.nidamanx.formica.domain.frequentProducts
import io.github.nidamanx.formica.domain.isBelowMinStock
import io.github.nidamanx.formica.domain.quantitiesByBatch
import io.github.nidamanx.formica.domain.runningLowSuggestions
import io.github.nidamanx.formica.domain.summarizeProduct
import io.github.nidamanx.formica.domain.model.Category
import io.github.nidamanx.formica.domain.model.Location
import io.github.nidamanx.formica.domain.model.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sample data must show what the screenshots promise, on any day. */
class DemoDataTest {

    private val today = 20_000L
    private val now = today * DAY_MILLIS + 18 * HOUR_MILLIS
    private var counter = 0
    private val locations = (0 until 4).map {
        Location(id = "loc$it", name = "L$it", sortOrder = it, createdAt = 0, modifiedAt = 0, modifiedBy = "d")
    }
    private val categories = (0 until 14).map {
        Category(id = "cat$it", name = "C$it", sortOrder = it, createdAt = 0, modifiedAt = 0, modifiedBy = "d")
    }
    private val data: Snapshot = DemoData(
        today = today,
        deviceId = "d",
        newId = { "id${counter++}" },
        millisAt = { day -> day * DAY_MILLIS + 12 * HOUR_MILLIS },
        locations = locations,
        categories = categories,
        text = { it.name },
    ).build()
    private val quantities = quantitiesByBatch(data.movements)
    private val byName = data.products.associateBy { it.name }
    private val thresholds = ListThresholds(expiryWarningDays = 3, runningLowDays = 7)

    private fun summary(name: String) = byName.getValue(name).let { product ->
        val batches = data.batches.filter { it.productId == product.id }
        val ids = batches.map { it.id }.toSet()
        summarizeProduct(product, batches, quantities, data.movements.filter { it.batchId in ids }, today, now, thresholds)
    }

    @Test
    fun `seventeen products, no batch below zero, nothing in the future`() {
        assertEquals(17, data.products.size)
        assertTrue(quantities.values.all { it >= 0 })
        assertTrue(data.movements.all { it.timestamp < now })
        assertTrue(data.batches.all { it.productId in byName.values.map { p -> p.id } })
    }

    @Test
    fun `quantities match the list`() {
        assertEquals(500L, summary("MILK").quantity)
        assertEquals(4_000L, summary("YOGURT").quantity)
        assertEquals(3_000L, summary("RAGU").quantity)
        assertEquals(6_000L, summary("PASSATA").quantity)
        assertEquals(2_500L, summary("OLIVE_OIL").quantity)
        assertEquals(400L, summary("WHOLEMEAL_FLOUR").quantity)
        assertEquals(2_000L, summary("BROWN_RICE").quantity)
    }

    @Test
    fun `expiries, preparation and minimum stock`() {
        assertEquals(ExpiryStatus.EXPIRED, summary("RICOTTA").expiryStatus)
        assertEquals(today + 1, summary("MILK").nearestExpiry)
        assertEquals(today + 2, summary("SPINACH").nearestExpiry)
        assertEquals(today + 14, summary("SAUERKRAUT").readyFrom)
        assertTrue(isBelowMinStock(summary("MILK").quantity, byName.getValue("MILK").minStock))
        val overview = expiryOverview(data.products, data.batches, quantities, today, 3, naturalOrder())
        assertEquals(listOf("RICOTTA"), overview.expired.map { it.product.name })
        assertEquals(setOf("MILK", "SPINACH"), overview.expiring.map { it.product.name }.toSet())
        assertEquals(listOf("SAUERKRAUT"), overview.preparing.map { it.product.name })
    }

    @Test
    fun `estimate, running low and frequent`() {
        assertTrue(estimateDuration(movementsOf("OLIVE_OIL"), 2_500L, now) is DurationEstimate.Weeks)
        val summaries = data.products.map { summary(it.name) }
        assertEquals(listOf("WHOLEMEAL_FLOUR"), runningLowSuggestions(summaries, data.shoppingItems, naturalOrder()).map { it.name })
        val batchProduct = data.batches.associate { it.id to it.productId }
        val frequent = frequentProducts(data.products, data.movements, batchProduct, data.shoppingItems, now, naturalOrder())
        assertEquals("BROWN_RICE", frequent.first().name)
    }

    @Test
    fun `passata has two batches, the older one first`() {
        val batches = data.batches.filter { it.productId == byName.getValue("PASSATA").id && quantities.getValue(it.id) > 0 }
        assertEquals(2, batches.size)
        assertEquals(listOf(2_000L, 4_000L), batches.sortedBy { it.preparedOn }.map { quantities.getValue(it.id) })
    }

    private fun movementsOf(name: String) = data.batches.filter { it.productId == byName.getValue(name).id }
        .map { it.id }.toSet().let { ids -> data.movements.filter { it.batchId in ids } }

    private companion object {
        const val HOUR_MILLIS = 3_600_000L
        const val DAY_MILLIS = 24 * HOUR_MILLIS
    }
}
