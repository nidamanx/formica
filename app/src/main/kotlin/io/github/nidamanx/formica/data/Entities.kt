// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.nidamanx.formica.domain.model.ImportMode
import io.github.nidamanx.formica.domain.model.MeasureUnit
import io.github.nidamanx.formica.domain.model.MovementType
import io.github.nidamanx.formica.domain.model.Origin
import io.github.nidamanx.formica.domain.model.ShoppingStatus
import io.github.nidamanx.formica.domain.model.StoreType

/*
 * Room tables, one per domain model (mapping in Mappers.kt). No foreign keys on purpose:
 * an import may bring records that reference others not yet (or no longer) present, and
 * deletions are soft (deletedAt). Enums are stored by name.
 */

@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int,
    val color: Int?,
    val icon: String?,
    val notes: String?,
    val notesModifiedAt: Long?,
    val createdAt: Long,
    val modifiedAt: Long,
    val modifiedBy: String,
    val deletedAt: Long?,
    val mergedIntoId: String?,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int,
    val color: Int?,
    val icon: String?,
    val createdAt: Long,
    val modifiedAt: Long,
    val modifiedBy: String,
    val deletedAt: Long?,
    val mergedIntoId: String?,
)

@Entity(tableName = "stores")
data class StoreEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: StoreType,
    val sortOrder: Int,
    val notes: String?,
    val notesModifiedAt: Long?,
    val createdAt: Long,
    val modifiedAt: Long,
    val modifiedBy: String,
    val deletedAt: Long?,
    val mergedIntoId: String?,
)

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val unit: MeasureUnit,
    val categoryId: String?,
    val defaultLocationId: String?,
    val homemade: Boolean,
    val bestPriceStoreId: String?,
    val bestPriceCents: Long?,
    val bestPriceReference: String?,
    val bestPriceNotedAt: Long?,
    val minStock: Long?,
    val shelfLifeDays: Int?,
    val daysAfterOpening: Int?,
    val daysUntilReady: Int?,
    val expiryWarningDays: Int?,
    val barcode: String?,
    val mergedIntoId: String?,
    val notes: String?,
    val notesModifiedAt: Long?,
    val createdAt: Long,
    val modifiedAt: Long,
    val modifiedBy: String,
    val deletedAt: Long?,
)

@Entity(tableName = "batches", indices = [Index("productId")])
data class BatchEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val locationId: String,
    val origin: Origin,
    val preparedOn: Long?,
    val purchasedOn: Long?,
    val storeId: String?,
    val expiresOn: Long?,
    val readyFrom: Long?,
    val openedOn: Long?,
    val notes: String?,
    val notesModifiedAt: Long?,
    val createdAt: Long,
    val modifiedAt: Long,
    val modifiedBy: String,
    val deletedAt: Long?,
)

@Entity(tableName = "movements", indices = [Index("batchId")])
data class MovementEntity(
    @PrimaryKey val id: String,
    val batchId: String,
    val delta: Long,
    val type: MovementType,
    val groupId: String?,
    val timestamp: Long,
    val deviceId: String,
)

@Entity(tableName = "shopping_items", indices = [Index("productId")])
data class ShoppingItemEntity(
    @PrimaryKey val id: String,
    val productId: String?,
    val text: String?,
    val quantity: Long?,
    val unit: MeasureUnit?,
    val storeId: String?,
    val categoryId: String?,
    val status: ShoppingStatus,
    val automatic: Boolean,
    val notes: String?,
    val notesModifiedAt: Long?,
    val createdAt: Long,
    val modifiedAt: Long,
    val modifiedBy: String,
    val deletedAt: Long?,
)

/** Local log of the imported files (not synced): the merge of notes needs the last exportedAt per device. */
@Entity(tableName = "import_records", indices = [Index("deviceId")])
data class ImportRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceId: String,
    val deviceName: String,
    val exportedAt: Long,
    val importedAt: Long,
    val mode: ImportMode,
)

/** Result row of the quantity queries. */
data class IdQuantity(val id: String, val quantity: Long)
