// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.sync

import io.github.nidamanx.formica.domain.model.Batch
import io.github.nidamanx.formica.domain.model.Category
import io.github.nidamanx.formica.domain.model.Device
import io.github.nidamanx.formica.domain.model.Location
import io.github.nidamanx.formica.domain.model.Movement
import io.github.nidamanx.formica.domain.model.Product
import io.github.nidamanx.formica.domain.model.ShoppingItem
import io.github.nidamanx.formica.domain.model.Snapshot
import io.github.nidamanx.formica.domain.model.Store

fun Snapshot.toExportFile(device: Device, appVersion: String, exportedAt: Long) = ExportFile(
    appVersion = appVersion,
    exportedAt = exportedAt,
    device = DeviceDto(device.id, device.name),
    locations = locations.map { it.toDto() },
    categories = categories.map { it.toDto() },
    stores = stores.map { it.toDto() },
    products = products.map { it.toDto() },
    batches = batches.map { it.toDto() },
    movements = movements.map { it.toDto() },
    shoppingItems = shoppingItems.map { it.toDto() },
)

fun ExportFile.toSnapshot() = Snapshot(
    locations = locations.map { it.toDomain() },
    categories = categories.map { it.toDomain() },
    stores = stores.map { it.toDomain() },
    products = products.map { it.toDomain() },
    batches = batches.map { it.toDomain() },
    movements = movements.map { it.toDomain() },
    shoppingItems = shoppingItems.map { it.toDomain() },
)

private fun Location.toDto() = LocationDto(
    id, name, sortOrder, color, icon, notes, notesModifiedAt,
    createdAt, modifiedAt, modifiedBy, deletedAt, mergedIntoId,
)

private fun LocationDto.toDomain() = Location(
    id, name, sortOrder, color, icon, notes, notesModifiedAt,
    createdAt, modifiedAt, modifiedBy, deletedAt, mergedIntoId,
)

private fun Category.toDto() = CategoryDto(
    id, name, sortOrder, color, icon, createdAt, modifiedAt, modifiedBy, deletedAt, mergedIntoId,
)

private fun CategoryDto.toDomain() = Category(
    id, name, sortOrder, color, icon, createdAt, modifiedAt, modifiedBy, deletedAt, mergedIntoId,
)

private fun Store.toDto() = StoreDto(
    id, name, type, sortOrder, notes, notesModifiedAt, createdAt, modifiedAt, modifiedBy, deletedAt, mergedIntoId,
)

private fun StoreDto.toDomain() = Store(
    id, name, type, sortOrder, notes, notesModifiedAt, createdAt, modifiedAt, modifiedBy, deletedAt, mergedIntoId,
)

private fun Product.toDto() = ProductDto(
    id = id,
    name = name,
    unit = unit,
    categoryId = categoryId,
    defaultLocationId = defaultLocationId,
    homemade = homemade,
    bestPriceStoreId = bestPriceStoreId,
    bestPriceCents = bestPriceCents,
    bestPriceReference = bestPriceReference,
    bestPriceNotedAt = bestPriceNotedAt,
    minStock = minStock,
    shelfLifeDays = shelfLifeDays,
    daysAfterOpening = daysAfterOpening,
    daysUntilReady = daysUntilReady,
    expiryWarningDays = expiryWarningDays,
    barcode = barcode,
    mergedIntoId = mergedIntoId,
    notes = notes,
    notesModifiedAt = notesModifiedAt,
    createdAt = createdAt,
    modifiedAt = modifiedAt,
    modifiedBy = modifiedBy,
    deletedAt = deletedAt,
)

private fun ProductDto.toDomain() = Product(
    id = id,
    name = name,
    unit = unit,
    categoryId = categoryId,
    defaultLocationId = defaultLocationId,
    homemade = homemade,
    bestPriceStoreId = bestPriceStoreId,
    bestPriceCents = bestPriceCents,
    bestPriceReference = bestPriceReference,
    bestPriceNotedAt = bestPriceNotedAt,
    minStock = minStock,
    shelfLifeDays = shelfLifeDays,
    daysAfterOpening = daysAfterOpening,
    daysUntilReady = daysUntilReady,
    expiryWarningDays = expiryWarningDays,
    barcode = barcode,
    mergedIntoId = mergedIntoId,
    notes = notes,
    notesModifiedAt = notesModifiedAt,
    createdAt = createdAt,
    modifiedAt = modifiedAt,
    modifiedBy = modifiedBy,
    deletedAt = deletedAt,
)

private fun Batch.toDto() = BatchDto(
    id, productId, locationId, origin, preparedOn, purchasedOn, storeId, expiresOn, readyFrom, openedOn,
    notes, notesModifiedAt, createdAt, modifiedAt, modifiedBy, deletedAt,
)

private fun BatchDto.toDomain() = Batch(
    id, productId, locationId, origin, preparedOn, purchasedOn, storeId, expiresOn, readyFrom, openedOn,
    notes, notesModifiedAt, createdAt, modifiedAt, modifiedBy, deletedAt,
)

private fun Movement.toDto() = MovementDto(id, batchId, delta, type, groupId, timestamp, deviceId)

private fun MovementDto.toDomain() = Movement(id, batchId, delta, type, groupId, timestamp, deviceId)

private fun ShoppingItem.toDto() = ShoppingItemDto(
    id, productId, text, quantity, unit, storeId, categoryId, status, automatic,
    notes, notesModifiedAt, createdAt, modifiedAt, modifiedBy, deletedAt,
)

private fun ShoppingItemDto.toDomain() = ShoppingItem(
    id, productId, text, quantity, unit, storeId, categoryId, status, automatic,
    notes, notesModifiedAt, createdAt, modifiedAt, modifiedBy, deletedAt,
)
