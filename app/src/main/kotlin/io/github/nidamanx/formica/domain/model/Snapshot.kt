// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.domain.model

/** Every synced record of one phone, deleted ones included: what an export contains. */
data class Snapshot(
    val locations: List<Location> = emptyList(),
    val categories: List<Category> = emptyList(),
    val stores: List<Store> = emptyList(),
    val products: List<Product> = emptyList(),
    val batches: List<Batch> = emptyList(),
    val movements: List<Movement> = emptyList(),
    val shoppingItems: List<ShoppingItem> = emptyList(),
)
