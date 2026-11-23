// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.domain

import io.github.nidamanx.formica.domain.model.Batch
import io.github.nidamanx.formica.domain.model.Category
import io.github.nidamanx.formica.domain.model.Location
import io.github.nidamanx.formica.domain.model.Product
import io.github.nidamanx.formica.domain.model.Store

/*
 * Edits by the user: identity, creation and deletion come from the stored record; the
 * merge metadata (modifiedAt, modifiedBy, notesModifiedAt, bestPriceNotedAt) is
 * recomputed here, so the merge rules of the sync can rely on it.
 */

private fun notesModifiedAt(old: String?, new: String?, previous: Long?, now: Long): Long? =
    if (old == new) previous else now

fun editProduct(stored: Product, edited: Product, ctx: OperationContext): Product {
    val bestPriceChanged = stored.bestPriceStoreId != edited.bestPriceStoreId ||
        stored.bestPriceCents != edited.bestPriceCents ||
        stored.bestPriceReference != edited.bestPriceReference
    return edited.copy(
        id = stored.id,
        createdAt = stored.createdAt,
        deletedAt = stored.deletedAt,
        mergedIntoId = stored.mergedIntoId,
        bestPriceNotedAt = if (bestPriceChanged) ctx.now else stored.bestPriceNotedAt,
        notesModifiedAt = notesModifiedAt(stored.notes, edited.notes, stored.notesModifiedAt, ctx.now),
        modifiedAt = nextModifiedAt(ctx.now, stored.modifiedAt),
        modifiedBy = ctx.deviceId,
    )
}

/** Only the notes of a batch are edited directly: every other change goes through movements. */
fun editBatchNotes(stored: Batch, notes: String?, ctx: OperationContext): Batch = stored.copy(
    notes = notes,
    notesModifiedAt = notesModifiedAt(stored.notes, notes, stored.notesModifiedAt, ctx.now),
    modifiedAt = nextModifiedAt(ctx.now, stored.modifiedAt),
    modifiedBy = ctx.deviceId,
)

fun editLocation(stored: Location, edited: Location, ctx: OperationContext): Location = edited.copy(
    id = stored.id,
    createdAt = stored.createdAt,
    deletedAt = stored.deletedAt,
    mergedIntoId = stored.mergedIntoId,
    notesModifiedAt = notesModifiedAt(stored.notes, edited.notes, stored.notesModifiedAt, ctx.now),
    modifiedAt = nextModifiedAt(ctx.now, stored.modifiedAt),
    modifiedBy = ctx.deviceId,
)

fun editCategory(stored: Category, edited: Category, ctx: OperationContext): Category = edited.copy(
    id = stored.id,
    createdAt = stored.createdAt,
    deletedAt = stored.deletedAt,
    mergedIntoId = stored.mergedIntoId,
    modifiedAt = nextModifiedAt(ctx.now, stored.modifiedAt),
    modifiedBy = ctx.deviceId,
)

fun editStore(stored: Store, edited: Store, ctx: OperationContext): Store = edited.copy(
    id = stored.id,
    createdAt = stored.createdAt,
    deletedAt = stored.deletedAt,
    mergedIntoId = stored.mergedIntoId,
    notesModifiedAt = notesModifiedAt(stored.notes, edited.notes, stored.notesModifiedAt, ctx.now),
    modifiedAt = nextModifiedAt(ctx.now, stored.modifiedAt),
    modifiedBy = ctx.deviceId,
)

/**
 * A product created by the user: the form fills the user fields, the metadata is set
 * here (new id, creation by this device, notes and best price dated if present).
 */
fun newProduct(draft: Product, ctx: OperationContext): Product {
    val hasBestPrice = draft.bestPriceStoreId != null || draft.bestPriceCents != null ||
        draft.bestPriceReference != null
    return draft.copy(
        id = ctx.newId(),
        mergedIntoId = null,
        bestPriceNotedAt = if (hasBestPrice) ctx.now else null,
        notesModifiedAt = if (draft.notes != null) ctx.now else null,
        createdAt = ctx.now,
        modifiedAt = ctx.now,
        modifiedBy = ctx.deviceId,
        deletedAt = null,
    )
}

/*
 * Soft delete (tombstone): deletedAt equals the new modifiedAt, so in the merge the
 * deletion wins over any version modified before it.
 */

fun Product.softDeleted(ctx: OperationContext): Product =
    nextModifiedAt(ctx.now, modifiedAt).let { copy(deletedAt = it, modifiedAt = it, modifiedBy = ctx.deviceId) }

fun Location.softDeleted(ctx: OperationContext): Location =
    nextModifiedAt(ctx.now, modifiedAt).let { copy(deletedAt = it, modifiedAt = it, modifiedBy = ctx.deviceId) }

fun Category.softDeleted(ctx: OperationContext): Category =
    nextModifiedAt(ctx.now, modifiedAt).let { copy(deletedAt = it, modifiedAt = it, modifiedBy = ctx.deviceId) }

fun Store.softDeleted(ctx: OperationContext): Store =
    nextModifiedAt(ctx.now, modifiedAt).let { copy(deletedAt = it, modifiedAt = it, modifiedBy = ctx.deviceId) }
