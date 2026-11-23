// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui

import io.github.nidamanx.formica.data.PantryRepository
import io.github.nidamanx.formica.domain.ProductSummary
import io.github.nidamanx.formica.domain.summarizeProduct
import io.github.nidamanx.formica.settings.DisplayDefaults
import io.github.nidamanx.formica.settings.ShoppingSettings
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** The summary of every product, as shown in the pantry list and used by the shopping suggestions. */
fun productSummaries(pantry: PantryRepository, settings: ShoppingSettings): Flow<List<ProductSummary>> = combine(
    pantry.products,
    pantry.batches,
    pantry.batchQuantities,
    pantry.movements,
    settings.preferences,
) { products, batches, quantities, movements, preferences ->
    val today = LocalDate.now().toEpochDay()
    val now = System.currentTimeMillis()
    val batchesByProduct = batches.groupBy { it.productId }
    val productOfBatch = batches.associate { it.id to it.productId }
    val movementsByProduct = movements.groupBy { productOfBatch[it.batchId] }
    val thresholds = DisplayDefaults.thresholds(preferences.runningLowDays)
    products.map { product ->
        summarizeProduct(
            product = product,
            productBatches = batchesByProduct[product.id].orEmpty(),
            quantities = quantities,
            productMovements = movementsByProduct[product.id].orEmpty(),
            today = today,
            now = now,
            thresholds = thresholds,
        )
    }
}
