// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.data

import io.github.nidamanx.formica.domain.model.Batch
import io.github.nidamanx.formica.domain.model.Category
import io.github.nidamanx.formica.domain.model.Location
import io.github.nidamanx.formica.domain.model.Movement
import io.github.nidamanx.formica.domain.model.Product
import io.github.nidamanx.formica.domain.model.ShoppingItem
import io.github.nidamanx.formica.domain.model.Store

fun LocationEntity.toDomain() = Location(
    id, name, sortOrder, color, icon, notes, notesModifiedAt,
    createdAt, modifiedAt, modifiedBy, deletedAt, mergedIntoId,
)

fun Location.toEntity() = LocationEntity(
    id, name, sortOrder, color, icon, notes, notesModifiedAt,
    createdAt, modifiedAt, modifiedBy, deletedAt, mergedIntoId,
)

fun CategoryEntity.toDomain() = Category(
    id, name, sortOrder, color, icon, createdAt, modifiedAt, modifiedBy, deletedAt, mergedIntoId,
)

fun Category.toEntity() = CategoryEntity(
    id, name, sortOrder, color, icon, createdAt, modifiedAt, modifiedBy, deletedAt, mergedIntoId,
)

fun StoreEntity.toDomain() = Store(
    id, name, type, sortOrder, notes, notesModifiedAt, createdAt, modifiedAt, modifiedBy, deletedAt, mergedIntoId,
)

fun Store.toEntity() = StoreEntity(
    id, name, type, sortOrder, notes, notesModifiedAt, createdAt, modifiedAt, modifiedBy, deletedAt, mergedIntoId,
)

fun ProductEntity.toDomain() = Product(
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

fun Product.toEntity() = ProductEntity(
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

fun BatchEntity.toDomain() = Batch(
    id = id,
    productId = productId,
    locationId = locationId,
    origin = origin,
    preparedOn = preparedOn,
    purchasedOn = purchasedOn,
    storeId = storeId,
    expiresOn = expiresOn,
    readyFrom = readyFrom,
    openedOn = openedOn,
    notes = notes,
    notesModifiedAt = notesModifiedAt,
    createdAt = createdAt,
    modifiedAt = modifiedAt,
    modifiedBy = modifiedBy,
    deletedAt = deletedAt,
)

fun Batch.toEntity() = BatchEntity(
    id = id,
    productId = productId,
    locationId = locationId,
    origin = origin,
    preparedOn = preparedOn,
    purchasedOn = purchasedOn,
    storeId = storeId,
    expiresOn = expiresOn,
    readyFrom = readyFrom,
    openedOn = openedOn,
    notes = notes,
    notesModifiedAt = notesModifiedAt,
    createdAt = createdAt,
    modifiedAt = modifiedAt,
    modifiedBy = modifiedBy,
    deletedAt = deletedAt,
)

fun MovementEntity.toDomain() = Movement(id, batchId, delta, type, groupId, timestamp, deviceId)

fun Movement.toEntity() = MovementEntity(id, batchId, delta, type, groupId, timestamp, deviceId)

fun ShoppingItemEntity.toDomain() = ShoppingItem(
    id = id,
    productId = productId,
    text = text,
    quantity = quantity,
    unit = unit,
    storeId = storeId,
    categoryId = categoryId,
    status = status,
    automatic = automatic,
    notes = notes,
    notesModifiedAt = notesModifiedAt,
    createdAt = createdAt,
    modifiedAt = modifiedAt,
    modifiedBy = modifiedBy,
    deletedAt = deletedAt,
)

fun ShoppingItem.toEntity() = ShoppingItemEntity(
    id = id,
    productId = productId,
    text = text,
    quantity = quantity,
    unit = unit,
    storeId = storeId,
    categoryId = categoryId,
    status = status,
    automatic = automatic,
    notes = notes,
    notesModifiedAt = notesModifiedAt,
    createdAt = createdAt,
    modifiedAt = modifiedAt,
    modifiedBy = modifiedBy,
    deletedAt = deletedAt,
)
