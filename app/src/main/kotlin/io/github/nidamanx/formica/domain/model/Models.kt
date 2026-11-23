// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.domain.model

/*
 * Domain models, independent of Room and of the export format.
 * Conventions: ids are UUID strings; createdAt/modifiedAt/deletedAt/notesModifiedAt and
 * Movement.timestamp are epoch millis (UTC); calendar dates are epoch days; quantities
 * are thousandths of the unit; prices are euro cents.
 */

/** One unit (1 piece, 1 kg, ...) expressed in thousandths. */
const val ONE_UNIT: Long = 1000L

data class Location(
    val id: String,
    val name: String,
    val sortOrder: Int,
    val color: Int? = null,
    val icon: String? = null,
    val notes: String? = null,
    val notesModifiedAt: Long? = null,
    val createdAt: Long,
    val modifiedAt: Long,
    val modifiedBy: String,
    val deletedAt: Long? = null,
    /** Set with deletedAt when merged into a duplicate: references move to that record. */
    val mergedIntoId: String? = null,
)

data class Category(
    val id: String,
    val name: String,
    val sortOrder: Int,
    val color: Int? = null,
    val icon: String? = null,
    val createdAt: Long,
    val modifiedAt: Long,
    val modifiedBy: String,
    val deletedAt: Long? = null,
    /** Set with deletedAt when merged into a duplicate: references move to that record. */
    val mergedIntoId: String? = null,
)

data class Store(
    val id: String,
    val name: String,
    val type: StoreType,
    val sortOrder: Int,
    val notes: String? = null,
    val notesModifiedAt: Long? = null,
    val createdAt: Long,
    val modifiedAt: Long,
    val modifiedBy: String,
    val deletedAt: Long? = null,
    /** Set with deletedAt when merged into a duplicate: references move to that record. */
    val mergedIntoId: String? = null,
)

data class Product(
    val id: String,
    val name: String,
    val unit: MeasureUnit = MeasureUnit.PIECE,
    val categoryId: String? = null,
    val defaultLocationId: String? = null,
    val homemade: Boolean = false,
    val bestPriceStoreId: String? = null,
    val bestPriceCents: Long? = null,
    val bestPriceReference: String? = null,
    val bestPriceNotedAt: Long? = null,
    val minStock: Long? = null,
    val shelfLifeDays: Int? = null,
    val daysAfterOpening: Int? = null,
    val daysUntilReady: Int? = null,
    val expiryWarningDays: Int? = null,
    val barcode: String? = null,
    val mergedIntoId: String? = null,
    val notes: String? = null,
    val notesModifiedAt: Long? = null,
    val createdAt: Long,
    val modifiedAt: Long,
    val modifiedBy: String,
    val deletedAt: Long? = null,
)

data class Batch(
    val id: String,
    val productId: String,
    val locationId: String,
    val origin: Origin,
    val preparedOn: Long? = null,
    val purchasedOn: Long? = null,
    val storeId: String? = null,
    val expiresOn: Long? = null,
    val readyFrom: Long? = null,
    val openedOn: Long? = null,
    val notes: String? = null,
    val notesModifiedAt: Long? = null,
    val createdAt: Long,
    val modifiedAt: Long,
    val modifiedBy: String,
    val deletedAt: Long? = null,
)

/** Immutable: never updated or deleted. */
data class Movement(
    val id: String,
    val batchId: String,
    val delta: Long,
    val type: MovementType,
    val groupId: String? = null,
    val timestamp: Long,
    val deviceId: String,
)

data class ShoppingItem(
    val id: String,
    val productId: String? = null,
    val text: String? = null,
    val quantity: Long? = null,
    val unit: MeasureUnit? = null,
    val storeId: String? = null,
    val categoryId: String? = null,
    val status: ShoppingStatus = ShoppingStatus.TO_BUY,
    val automatic: Boolean = false,
    val notes: String? = null,
    val notesModifiedAt: Long? = null,
    val createdAt: Long,
    val modifiedAt: Long,
    val modifiedBy: String,
    val deletedAt: Long? = null,
)

/** This phone: stored locally, not a synced entity (the export only names it). */
data class Device(val id: String, val name: String)
