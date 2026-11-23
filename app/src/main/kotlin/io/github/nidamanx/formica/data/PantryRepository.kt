// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.data

import androidx.room.withTransaction
import io.github.nidamanx.formica.domain.BatchGroup
import io.github.nidamanx.formica.domain.GroupKey
import io.github.nidamanx.formica.domain.OperationContext
import io.github.nidamanx.formica.domain.StockPlan
import io.github.nidamanx.formica.domain.editBatchNotes
import io.github.nidamanx.formica.domain.editProduct
import io.github.nidamanx.formica.domain.groupBatches
import io.github.nidamanx.formica.domain.newProduct
import io.github.nidamanx.formica.domain.planConsumption
import io.github.nidamanx.formica.domain.planCorrection
import io.github.nidamanx.formica.domain.planGroupRemoval
import io.github.nidamanx.formica.domain.planOpening
import io.github.nidamanx.formica.domain.planPreparation
import io.github.nidamanx.formica.domain.planPurchase
import io.github.nidamanx.formica.domain.planTransfer
import io.github.nidamanx.formica.domain.softDeleted
import io.github.nidamanx.formica.domain.quantitiesByBatch
import io.github.nidamanx.formica.domain.model.Batch
import io.github.nidamanx.formica.domain.model.Movement
import io.github.nidamanx.formica.domain.model.MovementType
import io.github.nidamanx.formica.domain.model.Product
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** First stock of a new product, entered in the product form. */
sealed interface InitialStock {
    val quantity: Long
    val locationId: String

    data class Purchase(
        override val quantity: Long,
        override val locationId: String,
        val expiresOn: Long?,
        val purchasedOn: Long?,
        val storeId: String?,
    ) : InitialStock

    data class Preparation(
        override val quantity: Long,
        override val locationId: String,
        val preparedOn: Long,
        val expiresOn: Long?,
        val readyFrom: Long?,
    ) : InitialStock
}

/**
 * Products, batches and movements. Every stock operation reads the product's batches and
 * movements, asks the domain for a plan and applies it in the same transaction.
 * Operations return the plan: its `unallocated` part tells the UI what could not be taken.
 */
class PantryRepository(
    private val db: FormicaDatabase,
    private val context: suspend () -> OperationContext,
) {
    private val productDao = db.productDao()
    private val batchDao = db.batchDao()
    private val movementDao = db.movementDao()

    val products: Flow<List<Product>> = productDao.observeAll().map { list -> list.map { it.toDomain() } }

    /** Total of every product, in thousandths (may be negative after a merge). */
    val productQuantities: Flow<Map<String, Long>> =
        movementDao.observeProductQuantities().map { rows -> rows.associate { it.id to it.quantity } }

    val batchQuantities: Flow<Map<String, Long>> =
        movementDao.observeBatchQuantities().map { rows -> rows.associate { it.id to it.quantity } }

    val batches: Flow<List<Batch>> = batchDao.observeAll().map { list -> list.map { it.toDomain() } }

    /** Every movement: the list needs them for the duration estimate ("running low"). */
    val movements: Flow<List<Movement>> = movementDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeProduct(id: String): Flow<Product?> = productDao.observe(id).map { it?.toDomain() }

    fun observeBatches(productId: String): Flow<List<Batch>> =
        batchDao.observeForProduct(productId).map { list -> list.map { it.toDomain() } }

    fun observeMovements(productId: String): Flow<List<Movement>> =
        movementDao.observeForProduct(productId).map { list -> list.map { it.toDomain() } }

    suspend fun createProduct(draft: Product, initial: InitialStock?): Product {
        val ctx = context()
        val product = newProduct(draft, ctx)
        db.withTransaction {
            productDao.upsert(listOf(product.toEntity()))
            if (initial != null && initial.quantity > 0) {
                applyPlan(initialPlan(product, initial, ctx))
            }
        }
        return product
    }

    suspend fun updateProduct(edited: Product) {
        val ctx = context()
        db.withTransaction {
            val stored = requireProduct(edited.id)
            productDao.upsert(listOf(editProduct(stored, edited, ctx).toEntity()))
        }
    }

    /** Soft delete: batches and movements stay, hidden with the product. */
    suspend fun deleteProduct(id: String) {
        val ctx = context()
        db.withTransaction {
            val stored = requireProduct(id)
            if (stored.deletedAt != null) return@withTransaction
            productDao.upsert(listOf(stored.softDeleted(ctx).toEntity()))
        }
    }

    suspend fun updateBatchNotes(productId: String, batchId: String, notes: String?) {
        val ctx = context()
        db.withTransaction {
            val stored = batchDao.forProduct(productId).firstOrNull { it.id == batchId }?.toDomain()
                ?: error("batch $batchId not found")
            batchDao.upsert(listOf(editBatchNotes(stored, notes, ctx).toEntity()))
        }
    }

    suspend fun purchase(
        productId: String,
        quantity: Long,
        locationId: String,
        expiresOn: Long?,
        purchasedOn: Long?,
        storeId: String?,
    ): StockPlan = withStock(productId) { _, batches, _, ctx ->
        planPurchase(productId, batches, quantity, locationId, expiresOn, purchasedOn, storeId, ctx)
    }

    suspend fun prepare(
        productId: String,
        quantity: Long,
        locationId: String,
        preparedOn: Long,
        expiresOn: Long?,
        readyFrom: Long?,
    ): StockPlan = withStock(productId) { _, batches, _, ctx ->
        planPreparation(productId, batches, quantity, locationId, preparedOn, expiresOn, readyFrom, ctx)
    }

    suspend fun consume(productId: String, amount: Long): StockPlan =
        withStock(productId) { product, batches, quantities, ctx ->
            planConsumption(batches, quantities, amount, product.daysAfterOpening, ctx)
        }

    /** Consumption or discard from one group chosen in the product detail. */
    suspend fun removeFromGroup(productId: String, key: GroupKey, amount: Long, type: MovementType): StockPlan =
        withStock(productId) { product, batches, quantities, ctx ->
            val group = findGroup(batches, quantities, product, key)
            planGroupRemoval(group, quantities, amount, type, product.daysAfterOpening, ctx)
        }

    suspend fun transfer(productId: String, key: GroupKey, amount: Long, toLocationId: String): StockPlan =
        withStock(productId) { product, batches, quantities, ctx ->
            val group = findGroup(batches, quantities, product, key)
            planTransfer(group, quantities, amount, toLocationId, batches, product.daysAfterOpening, ctx)
        }

    suspend fun open(productId: String, key: GroupKey, amount: Long, openedOn: Long): StockPlan =
        withStock(productId) { product, batches, quantities, ctx ->
            val group = findGroup(batches, quantities, product, key)
            planOpening(group, quantities, amount, openedOn, batches, product.daysAfterOpening, ctx)
        }

    /** Real quantity of a group ([key] given) or of the whole product ([key] null). */
    suspend fun correct(productId: String, key: GroupKey?, realQuantity: Long): StockPlan =
        withStock(productId) { product, batches, quantities, ctx ->
            val target = if (key == null) batches else findGroup(batches, quantities, product, key).batches
            planCorrection(target, quantities, realQuantity, ctx)
        }

    private fun initialPlan(product: Product, initial: InitialStock, ctx: OperationContext): StockPlan =
        when (initial) {
            is InitialStock.Purchase -> planPurchase(
                product.id, emptyList(), initial.quantity, initial.locationId,
                initial.expiresOn, initial.purchasedOn, initial.storeId, ctx,
            )
            is InitialStock.Preparation -> planPreparation(
                product.id, emptyList(), initial.quantity, initial.locationId,
                initial.preparedOn, initial.expiresOn, initial.readyFrom, ctx,
            )
        }

    private suspend fun withStock(
        productId: String,
        plan: (Product, List<Batch>, Map<String, Long>, OperationContext) -> StockPlan,
    ): StockPlan {
        val ctx = context()
        return db.withTransaction {
            val product = requireProduct(productId)
            val batches = batchDao.forProduct(productId).map { it.toDomain() }
            val quantities = quantitiesByBatch(movementDao.forProduct(productId).map { it.toDomain() })
            plan(product, batches, quantities, ctx).also { applyPlan(it) }
        }
    }

    private suspend fun applyPlan(plan: StockPlan) {
        val changed = plan.newBatches + plan.updatedBatches
        if (changed.isNotEmpty()) batchDao.upsert(changed.map { it.toEntity() })
        if (plan.movements.isNotEmpty()) movementDao.insert(plan.movements.map { it.toEntity() })
    }

    private suspend fun requireProduct(id: String): Product =
        productDao.get(id)?.toDomain() ?: error("product $id not found")

    private fun findGroup(
        batches: List<Batch>,
        quantities: Map<String, Long>,
        product: Product,
        key: GroupKey,
    ): BatchGroup = groupBatches(batches, quantities, product.daysAfterOpening).firstOrNull { it.key == key }
        ?: error("group not found")
}
