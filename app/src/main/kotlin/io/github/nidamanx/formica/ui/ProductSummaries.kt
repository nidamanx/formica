// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui

import io.github.nidamanx.formica.data.PantryRepository
import io.github.nidamanx.formica.domain.ListThresholds
import io.github.nidamanx.formica.domain.ProductSummary
import io.github.nidamanx.formica.domain.summarizeProduct
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** The summary of every product, as shown in the pantry list and used by the shopping suggestions. */
fun productSummaries(pantry: PantryRepository, thresholds: Flow<ListThresholds>): Flow<List<ProductSummary>> = combine(
    pantry.products,
    pantry.batches,
    pantry.batchQuantities,
    pantry.movements,
    thresholds,
) { products, batches, quantities, movements, limits ->
    val today = LocalDate.now().toEpochDay()
    val now = System.currentTimeMillis()
    val batchesByProduct = batches.groupBy { it.productId }
    val productOfBatch = batches.associate { it.id to it.productId }
    val movementsByProduct = movements.groupBy { productOfBatch[it.batchId] }
    products.map { product ->
        summarizeProduct(
            product = product,
            productBatches = batchesByProduct[product.id].orEmpty(),
            quantities = quantities,
            productMovements = movementsByProduct[product.id].orEmpty(),
            today = today,
            now = now,
            thresholds = limits,
        )
    }
}
