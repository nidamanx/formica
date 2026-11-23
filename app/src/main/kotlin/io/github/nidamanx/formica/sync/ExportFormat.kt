// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.sync

import io.github.nidamanx.formica.domain.model.MeasureUnit
import io.github.nidamanx.formica.domain.model.MovementType
import io.github.nidamanx.formica.domain.model.Origin
import io.github.nidamanx.formica.domain.model.ShoppingStatus
import io.github.nidamanx.formica.domain.model.StoreType
import kotlinx.serialization.Serializable

/*
 * The exchange file (SPEC §11.2). Field names are part of the format: renaming one, or
 * changing its meaning, needs a new FORMAT_VERSION. New optional fields with a default
 * keep the version: older readers ignore unknown fields. Enums are written by name;
 * quantities in thousandths, prices in cents, dates as epoch days, times as epoch millis.
 */

const val FORMAT_NAME = "formica"
const val FORMAT_VERSION = 1

@Serializable
data class ExportFile(
    val format: String = FORMAT_NAME,
    val formatVersion: Int = FORMAT_VERSION,
    val appVersion: String,
    val exportedAt: Long,
    val device: DeviceDto,
    val locations: List<LocationDto> = emptyList(),
    val categories: List<CategoryDto> = emptyList(),
    val stores: List<StoreDto> = emptyList(),
    val products: List<ProductDto> = emptyList(),
    val batches: List<BatchDto> = emptyList(),
    val movements: List<MovementDto> = emptyList(),
    val shoppingItems: List<ShoppingItemDto> = emptyList(),
)

@Serializable
data class DeviceDto(val id: String, val name: String)

@Serializable
data class LocationDto(
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
    val mergedIntoId: String? = null,
)

@Serializable
data class CategoryDto(
    val id: String,
    val name: String,
    val sortOrder: Int,
    val color: Int? = null,
    val icon: String? = null,
    val createdAt: Long,
    val modifiedAt: Long,
    val modifiedBy: String,
    val deletedAt: Long? = null,
    val mergedIntoId: String? = null,
)

@Serializable
data class StoreDto(
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
    val mergedIntoId: String? = null,
)

@Serializable
data class ProductDto(
    val id: String,
    val name: String,
    val unit: MeasureUnit,
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

@Serializable
data class BatchDto(
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

@Serializable
data class MovementDto(
    val id: String,
    val batchId: String,
    val delta: Long,
    val type: MovementType,
    val groupId: String? = null,
    val timestamp: Long,
    val deviceId: String,
)

@Serializable
data class ShoppingItemDto(
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
