// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.data

import androidx.room.withTransaction
import io.github.nidamanx.formica.domain.OperationContext
import io.github.nidamanx.formica.domain.clearedBought
import io.github.nidamanx.formica.domain.editShoppingItem
import io.github.nidamanx.formica.domain.model.ShoppingItem
import io.github.nidamanx.formica.domain.model.ShoppingStatus
import io.github.nidamanx.formica.domain.newShoppingItem
import io.github.nidamanx.formica.domain.openItemFor
import io.github.nidamanx.formica.domain.planToBuy
import io.github.nidamanx.formica.domain.softDeleted
import io.github.nidamanx.formica.domain.withStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The shopping list: every change reads the active items in the same transaction. */
class ShoppingRepository(
    private val db: FormicaDatabase,
    private val context: suspend () -> OperationContext,
) {
    private val dao = db.shoppingItemDao()

    /** Items that are not deleted, to buy and bought. */
    val items: Flow<List<ShoppingItem>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    /** "To buy" from the pantry or a suggestion: never a second open item for a product. */
    suspend fun toBuy(productId: String, quantity: Long? = null, automatic: Boolean = false) {
        val ctx = context()
        db.withTransaction {
            planToBuy(productId, quantity, active(), automatic, ctx)?.let { save(it) }
        }
    }

    suspend fun removeFromList(productId: String) {
        val ctx = context()
        db.withTransaction {
            openItemFor(active(), productId)?.let { save(it.softDeleted(ctx)) }
        }
    }

    /** A free item: [draft] carries text, quantity, unit, category and store. */
    suspend fun addFree(draft: ShoppingItem) {
        val ctx = context()
        db.withTransaction { save(newShoppingItem(draft.copy(productId = null), ctx)) }
    }

    suspend fun update(edited: ShoppingItem) {
        val ctx = context()
        db.withTransaction {
            val stored = requireItem(edited.id)
            save(editShoppingItem(stored, edited, ctx))
        }
    }

    suspend fun delete(id: String) {
        val ctx = context()
        db.withTransaction {
            val stored = requireItem(id)
            if (stored.deletedAt == null) save(stored.softDeleted(ctx))
        }
    }

    /**
     * Ticks an item to buy or unticks a bought one, from its stored status: quick taps act on
     * what the previous tap left. True when the item has just been ticked.
     */
    suspend fun toggleBought(id: String): Boolean {
        val ctx = context()
        return db.withTransaction {
            val stored = requireItem(id)
            if (stored.deletedAt != null) return@withTransaction false
            val bought = stored.status != ShoppingStatus.BOUGHT
            // Unticking must not create a second open item for the same product
            if (!bought && stored.productId != null && openItemFor(active(), stored.productId) != null) {
                save(stored.softDeleted(ctx))
            } else {
                save(stored.withStatus(if (bought) ShoppingStatus.BOUGHT else ShoppingStatus.TO_BUY, ctx))
            }
            bought
        }
    }

    suspend fun clearBought() {
        val ctx = context()
        db.withTransaction {
            val cleared = clearedBought(active(), ctx)
            if (cleared.isNotEmpty()) dao.upsert(cleared.map { it.toEntity() })
        }
    }

    private suspend fun active(): List<ShoppingItem> = dao.active().map { it.toDomain() }

    private suspend fun requireItem(id: String): ShoppingItem = dao.get(id)?.toDomain() ?: error("item $id not found")

    private suspend fun save(item: ShoppingItem) = dao.upsert(listOf(item.toEntity()))
}
