// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.domain

import io.github.nidamanx.formica.domain.model.Batch
import io.github.nidamanx.formica.domain.model.Category
import io.github.nidamanx.formica.domain.model.ImportMode
import io.github.nidamanx.formica.domain.model.Location
import io.github.nidamanx.formica.domain.model.Movement
import io.github.nidamanx.formica.domain.model.Product
import io.github.nidamanx.formica.domain.model.ShoppingItem
import io.github.nidamanx.formica.domain.model.ShoppingStatus
import io.github.nidamanx.formica.domain.model.Snapshot
import io.github.nidamanx.formica.domain.model.Store

enum class RecordType { LOCATION, CATEGORY, STORE, PRODUCT, BATCH, SHOPPING_ITEM }

data class RecordRef(val type: RecordType, val id: String)

data class ImportRequest(
    val local: Snapshot,
    val remote: Snapshot,
    val mode: ImportMode,
    val localDeviceName: String,
    val remoteDeviceName: String,
    /** exportedAt of the last file imported from the same device, 0 if never (SPEC §11.5.3). */
    val lastExportedAt: Long,
    /** Products of the file that the user keeps apart from their local duplicate. */
    val keepSeparate: Set<String> = emptySet(),
)

/** A product of the file with the same name as a local one (SPEC §11.5.6). */
data class ProductDuplicate(val localId: String, val localName: String, val remoteId: String, val remoteName: String)

data class ChangeCounts(val new: Int = 0, val updated: Int = 0, val deleted: Int = 0) {
    val isEmpty: Boolean get() = new == 0 && updated == 0 && deleted == 0
}

data class MergeResult(
    /** The complete new state of this phone. */
    val snapshot: Snapshot,
    val products: ChangeCounts,
    val shoppingItems: ChangeCounts,
    /** Offered in the preview, whatever the choice; merged unless listed in keepSeparate. */
    val productDuplicates: List<ProductDuplicate>,
    /** Locations, categories and stores merged into a duplicate with the same name. */
    val autoMerged: Int,
    /** Records whose notes were appended to each other: shown as "notes to review". */
    val notesToReview: Set<RecordRef>,
)

/**
 * The result of importing a file (SPEC §11.4, §11.5), computed without touching any data:
 * the preview shows it, the import writes [MergeResult.snapshot].
 *
 * @param noteHeader the line between appended notes, for a device name ("— Anna, 23/11/2026 —")
 */
fun mergeImport(request: ImportRequest, ctx: OperationContext, noteHeader: (String) -> String): MergeResult {
    val local = request.local
    return when (request.mode) {
        ImportMode.REPLACE_ALL -> result(local, request.remote, emptyList(), 0, emptySet())
        ImportMode.NEW_PRODUCTS_ONLY -> NewProductsImport(request, ctx).run()
        ImportMode.MERGE -> Merger(request, ctx, noteHeader).run()
    }
}

/** Accessors shared by the records with modifiedAt. */
private class Kind<T>(
    val type: RecordType,
    val id: (T) -> String,
    val modifiedAt: (T) -> Long,
    val modifiedBy: (T) -> String,
    val deletedAt: (T) -> Long?,
    val createdAt: (T) -> Long,
    /** A new version written by this device: modifiedAt and modifiedBy. */
    val touch: (T, Long, String) -> T,
)

private class NotesAccess<T>(
    val notes: (T) -> String?,
    val modifiedAt: (T) -> Long?,
    val with: (T, String?, Long?) -> T,
)

private val LOCATION = Kind<Location>(
    RecordType.LOCATION, { it.id }, { it.modifiedAt }, { it.modifiedBy }, { it.deletedAt }, { it.createdAt },
    { r, at, by -> r.copy(modifiedAt = at, modifiedBy = by) },
)
private val CATEGORY = Kind<Category>(
    RecordType.CATEGORY, { it.id }, { it.modifiedAt }, { it.modifiedBy }, { it.deletedAt }, { it.createdAt },
    { r, at, by -> r.copy(modifiedAt = at, modifiedBy = by) },
)
private val STORE = Kind<Store>(
    RecordType.STORE, { it.id }, { it.modifiedAt }, { it.modifiedBy }, { it.deletedAt }, { it.createdAt },
    { r, at, by -> r.copy(modifiedAt = at, modifiedBy = by) },
)
private val PRODUCT = Kind<Product>(
    RecordType.PRODUCT, { it.id }, { it.modifiedAt }, { it.modifiedBy }, { it.deletedAt }, { it.createdAt },
    { r, at, by -> r.copy(modifiedAt = at, modifiedBy = by) },
)
private val BATCH = Kind<Batch>(
    RecordType.BATCH, { it.id }, { it.modifiedAt }, { it.modifiedBy }, { it.deletedAt }, { it.createdAt },
    { r, at, by -> r.copy(modifiedAt = at, modifiedBy = by) },
)
private val ITEM = Kind<ShoppingItem>(
    RecordType.SHOPPING_ITEM, { it.id }, { it.modifiedAt }, { it.modifiedBy }, { it.deletedAt }, { it.createdAt },
    { r, at, by -> r.copy(modifiedAt = at, modifiedBy = by) },
)

private val LOCATION_NOTES = NotesAccess<Location>({ it.notes }, { it.notesModifiedAt }) { r, n, at ->
    r.copy(notes = n, notesModifiedAt = at)
}
private val STORE_NOTES = NotesAccess<Store>({ it.notes }, { it.notesModifiedAt }) { r, n, at ->
    r.copy(notes = n, notesModifiedAt = at)
}
private val PRODUCT_NOTES = NotesAccess<Product>({ it.notes }, { it.notesModifiedAt }) { r, n, at ->
    r.copy(notes = n, notesModifiedAt = at)
}
private val BATCH_NOTES = NotesAccess<Batch>({ it.notes }, { it.notesModifiedAt }) { r, n, at ->
    r.copy(notes = n, notesModifiedAt = at)
}
private val ITEM_NOTES = NotesAccess<ShoppingItem>({ it.notes }, { it.notesModifiedAt }) { r, n, at ->
    r.copy(notes = n, notesModifiedAt = at)
}

/** The oldest record survives a duplicate merge: every phone makes the same choice. */
private fun <T> Kind<T>.oldestFirst(): Comparator<T> = compareBy<T>({ createdAt(it) }, { id(it) })

/** Unisci (SPEC §11.5). */
private class Merger(
    private val request: ImportRequest,
    private val ctx: OperationContext,
    private val noteHeader: (String) -> String,
) {
    private val review = mutableSetOf<RecordRef>()
    private val locations = request.local.locations.associateByTo(LinkedHashMap()) { it.id }
    private val categories = request.local.categories.associateByTo(LinkedHashMap()) { it.id }
    private val stores = request.local.stores.associateByTo(LinkedHashMap()) { it.id }
    private val products = request.local.products.associateByTo(LinkedHashMap()) { it.id }
    private val batches = request.local.batches.associateByTo(LinkedHashMap()) { it.id }
    private val items = request.local.shoppingItems.associateByTo(LinkedHashMap()) { it.id }
    private val inserted = mutableMapOf<RecordType, MutableSet<String>>()
    private var autoMerged = 0

    fun run(): MergeResult {
        val remote = request.remote
        unite(locations, remote.locations, LOCATION, LOCATION_NOTES)
        unite(categories, remote.categories, CATEGORY, null)
        unite(stores, remote.stores, STORE, STORE_NOTES)
        unite(products, remote.products, PRODUCT, PRODUCT_NOTES, ::bestPrice)
        unite(batches, remote.batches, BATCH, BATCH_NOTES)
        unite(items, remote.shoppingItems, ITEM, ITEM_NOTES)

        // Rule 1: movements are immutable, so the union by id is always right
        val knownMovements = request.local.movements.mapTo(HashSet()) { it.id }
        val movements: List<Movement> = request.local.movements + remote.movements.filter { it.id !in knownMovements }

        // Rule 6: duplicates of the records that arrived with this file
        autoMerged += mergeDuplicates(locations, LOCATION, { it.name }) { r, into, at ->
            r.copy(deletedAt = at, modifiedAt = at, modifiedBy = ctx.deviceId, mergedIntoId = into)
        }
        autoMerged += mergeDuplicates(categories, CATEGORY, { it.name }) { r, into, at ->
            r.copy(deletedAt = at, modifiedAt = at, modifiedBy = ctx.deviceId, mergedIntoId = into)
        }
        autoMerged += mergeDuplicates(stores, STORE, { it.name }) { r, into, at ->
            r.copy(deletedAt = at, modifiedAt = at, modifiedBy = ctx.deviceId, mergedIntoId = into)
        }
        val duplicates = productDuplicates()
        duplicates.filter { it.remoteId !in request.keepSeparate }.forEach { mergeProducts(it) }

        // Rule 6, on every phone: references follow the records merged into another one
        redirectReferences()
        // Rule 7: one open item per product (the oldest)
        keepOneOpenItemPerProduct()

        val merged = Snapshot(
            locations = locations.values.toList(),
            categories = categories.values.toList(),
            stores = stores.values.toList(),
            products = products.values.toList(),
            batches = batches.values.toList(),
            movements = movements,
            shoppingItems = items.values.toList(),
        )
        return result(request.local, merged, duplicates, autoMerged, review)
    }

    private fun <T> unite(
        target: MutableMap<String, T>,
        remote: List<T>,
        kind: Kind<T>,
        notes: NotesAccess<T>?,
        extra: ((T, T, T) -> T)? = null,
    ) {
        for (r in remote) {
            val id = kind.id(r)
            val l = target[id]
            if (l == null) {
                target[id] = r
                inserted.getOrPut(kind.type) { mutableSetOf() } += id
            } else {
                target[id] = mergePair(l, r, kind, notes, extra)
            }
        }
    }

    private fun <T> mergePair(l: T, r: T, kind: Kind<T>, notes: NotesAccess<T>?, extra: ((T, T, T) -> T)?): T {
        val winner = winner(l, r, kind)
        if (kind.deletedAt(winner) != null) return winner
        var result = winner
        if (notes != null) result = mergeNotes(l, r, result, kind, notes)
        if (extra != null) result = extra(l, r, result)
        if (result == l || result == r) return result
        // A version that neither phone had: it must win on the other phone too
        return kind.touch(result, nextModifiedAt(ctx.now, maxOf(kind.modifiedAt(l), kind.modifiedAt(r))), ctx.deviceId)
    }

    /** Rules 2 and 5: a deletion wins over the versions it saw; otherwise the latest version. */
    private fun <T> winner(l: T, r: T, kind: Kind<T>): T {
        val ld = kind.deletedAt(l)
        val rd = kind.deletedAt(r)
        if (ld != null && rd == null) return if (ld >= kind.modifiedAt(r)) l else r
        if (rd != null && ld == null) return if (rd >= kind.modifiedAt(l)) r else l
        val byVersion = compareBy<T>({ kind.modifiedAt(it) }, { kind.modifiedBy(it) })
        return if (byVersion.compare(r, l) > 0) r else l
    }

    /** Rule 3: notes are merged apart from the rest of the record. */
    private fun <T> mergeNotes(l: T, r: T, result: T, kind: Kind<T>, access: NotesAccess<T>): T {
        val ln = access.notes(l)?.takeIf { it.isNotBlank() }
        val rn = access.notes(r)?.takeIf { it.isNotBlank() }
        if (ln == rn) return result
        val since = request.lastExportedAt
        val localChanged = (access.modifiedAt(l) ?: 0L) > since
        val remoteChanged = (access.modifiedAt(r) ?: 0L) > since
        val takeRemote = access.with(result, access.notes(r), access.modifiedAt(r))
        val keepLocal = access.with(result, access.notes(l), access.modifiedAt(l))
        return when {
            remoteChanged && !localChanged -> takeRemote
            localChanged && !remoteChanged -> keepLocal
            !localChanged -> result
            // Already appended on the other phone (or here, before): no second copy
            ln == null || rn != null && rn.contains(ln) -> takeRemote
            rn == null || ln.contains(rn) -> keepLocal
            else -> {
                review += RecordRef(kind.type, kind.id(result))
                val at = nextModifiedAt(ctx.now, maxOf(access.modifiedAt(l) ?: 0L, access.modifiedAt(r) ?: 0L))
                access.with(result, appendNotes(ln, rn, request.remoteDeviceName), at)
            }
        }
    }

    private fun appendNotes(first: String, second: String, secondDevice: String) =
        first + "\n\n" + noteHeader(secondDevice) + "\n" + second

    /** Rule 4: the best price fields travel together, the latest noted wins. */
    private fun bestPrice(l: Product, r: Product, result: Product): Product {
        val latest = if ((r.bestPriceNotedAt ?: Long.MIN_VALUE) > (l.bestPriceNotedAt ?: Long.MIN_VALUE)) r else l
        return result.copy(
            bestPriceStoreId = latest.bestPriceStoreId,
            bestPriceCents = latest.bestPriceCents,
            bestPriceReference = latest.bestPriceReference,
            bestPriceNotedAt = latest.bestPriceNotedAt,
        )
    }

    /**
     * Groups each record that arrived with this file with the oldest local record of the
     * same name; the newer one of each pair is merged into the older.
     *
     * @return the number of records merged
     */
    private fun <T> mergeDuplicates(
        target: MutableMap<String, T>,
        kind: Kind<T>,
        name: (T) -> String,
        mergedInto: (T, String, Long) -> T,
    ): Int {
        var count = 0
        for ((localOne, remoteOne) in duplicatePairs(target, kind, name)) {
            val (keeper, loser) = listOf(localOne, remoteOne)
                .map { target.getValue(kind.id(it)) }
                .sortedWith(kind.oldestFirst())
            if (kind.deletedAt(keeper) != null || kind.deletedAt(loser) != null) continue
            val at = nextModifiedAt(ctx.now, kind.modifiedAt(loser))
            target[kind.id(loser)] = mergedInto(loser, kind.id(keeper), at)
            count++
        }
        return count
    }

    private fun <T> duplicatePairs(target: Map<String, T>, kind: Kind<T>, name: (T) -> String): List<Pair<T, T>> {
        val arrived = inserted[kind.type].orEmpty()
        val alive = target.values.filter { kind.deletedAt(it) == null }
        val localByName = alive
            .filter { kind.id(it) !in arrived }
            .groupBy { normalizedName(name(it)) }
            .mapValues { (_, list) -> list.minWith(kind.oldestFirst()) }
        return alive
            .filter { kind.id(it) in arrived }
            .sortedWith(kind.oldestFirst())
            .mapNotNull { remoteOne -> localByName[normalizedName(name(remoteOne))]?.let { it to remoteOne } }
    }

    private fun productDuplicates(): List<ProductDuplicate> =
        duplicatePairs(products, PRODUCT) { it.name }.map { (l, r) -> ProductDuplicate(l.id, l.name, r.id, r.name) }

    /** Batches and items follow in [redirectReferences]; the notes of the merged product are appended. */
    private fun mergeProducts(duplicate: ProductDuplicate) {
        val localOne = products.getValue(duplicate.localId)
        val remoteOne = products.getValue(duplicate.remoteId)
        if (localOne.deletedAt != null || remoteOne.deletedAt != null) return
        val (keeper, loser) = listOf(localOne, remoteOne).sortedWith(PRODUCT.oldestFirst())
        val loserDevice = if (loser === remoteOne) request.remoteDeviceName else request.localDeviceName
        val at = nextModifiedAt(ctx.now, loser.modifiedAt)
        products[loser.id] =
            loser.copy(deletedAt = at, modifiedAt = at, modifiedBy = ctx.deviceId, mergedIntoId = keeper.id)
        val kn = keeper.notes?.takeIf { it.isNotBlank() }
        val ln = loser.notes?.takeIf { it.isNotBlank() }
        if (ln != null && kn?.contains(ln) != true) {
            if (kn != null) review += RecordRef(RecordType.PRODUCT, keeper.id)
            val notes = if (kn == null) ln else appendNotes(kn, ln, loserDevice)
            val keeperAt = nextModifiedAt(ctx.now, keeper.modifiedAt)
            products[keeper.id] = keeper.copy(
                notes = notes,
                notesModifiedAt = keeperAt,
                modifiedAt = keeperAt,
                modifiedBy = ctx.deviceId,
            )
        }
    }

    private fun redirectReferences() {
        val toLocation = redirects(locations.values, LOCATION) { it.mergedIntoId }
        val toCategory = redirects(categories.values, CATEGORY) { it.mergedIntoId }
        val toStore = redirects(stores.values, STORE) { it.mergedIntoId }
        val toProduct = redirects(products.values, PRODUCT) { it.mergedIntoId }
        fun String.via(map: Map<String, String>) = map[this] ?: this

        update(products, PRODUCT) { p ->
            p.copy(
                categoryId = p.categoryId?.via(toCategory),
                defaultLocationId = p.defaultLocationId?.via(toLocation),
                bestPriceStoreId = p.bestPriceStoreId?.via(toStore),
            )
        }
        update(batches, BATCH) { b ->
            b.copy(
                productId = b.productId.via(toProduct),
                locationId = b.locationId.via(toLocation),
                storeId = b.storeId?.via(toStore),
            )
        }
        update(items, ITEM) { i ->
            i.copy(
                productId = i.productId?.via(toProduct),
                categoryId = i.categoryId?.via(toCategory),
                storeId = i.storeId?.via(toStore),
            )
        }
    }

    /** Applies [change] to the records that are not deleted; a changed record becomes a new version. */
    private fun <T> update(target: MutableMap<String, T>, kind: Kind<T>, change: (T) -> T) {
        for ((id, record) in target) {
            if (kind.deletedAt(record) != null) continue
            val changed = change(record)
            if (changed != record) {
                target[id] = kind.touch(changed, nextModifiedAt(ctx.now, kind.modifiedAt(record)), ctx.deviceId)
            }
        }
    }

    private fun keepOneOpenItemPerProduct() {
        items.values
            .filter { it.deletedAt == null && it.status == ShoppingStatus.TO_BUY && it.productId != null }
            .groupBy { it.productId }
            .values
            .filter { it.size > 1 }
            .flatMap { it.sortedWith(ITEM.oldestFirst()).drop(1) }
            .forEach { extra ->
                val at = nextModifiedAt(ctx.now, extra.modifiedAt)
                items[extra.id] = extra.copy(deletedAt = at, modifiedAt = at, modifiedBy = ctx.deviceId)
            }
    }
}

/** Each deleted and merged id → the record it finally lives in, following chains (cycles are cut). */
private fun <T> redirects(records: Collection<T>, kind: Kind<T>, mergedInto: (T) -> String?): Map<String, String> {
    val direct = HashMap<String, String>()
    for (r in records) {
        if (kind.deletedAt(r) == null) continue
        val target = mergedInto(r) ?: continue
        direct[kind.id(r)] = target
    }
    fun resolve(id: String): String {
        var current = id
        repeat(direct.size) { current = direct[current] ?: return current }
        return current
    }
    return direct.keys.associateWith(::resolve).filter { (from, to) -> from != to }
}

/** Solo nuovi prodotti (SPEC §11.4): nothing already here is touched. */
private class NewProductsImport(private val request: ImportRequest, private val ctx: OperationContext) {
    private val local = request.local
    private val remote = request.remote

    fun run(): MergeResult {
        val knownProducts = local.products.mapTo(HashSet()) { it.id }
        val newProducts = remote.products.filter { it.id !in knownProducts && it.deletedAt == null }
        val newIds = newProducts.mapTo(HashSet()) { it.id }
        val knownBatches = local.batches.mapTo(HashSet()) { it.id }
        val newBatches = remote.batches.filter {
            it.productId in newIds && it.id !in knownBatches && it.deletedAt == null
        }
        val batchIds = newBatches.mapTo(HashSet()) { it.id }
        val knownMovements = local.movements.mapTo(HashSet()) { it.id }
        val newMovements = remote.movements.filter { it.batchId in batchIds && it.id !in knownMovements }

        // A referenced location, category or store: the local one with the same name, or the one in the file
        val addedLocations = mutableListOf<Location>()
        val addedCategories = mutableListOf<Category>()
        val addedStores = mutableListOf<Store>()
        val location = resolver(local.locations, remote.locations, addedLocations, LOCATION) { it.name }
        val category = resolver(local.categories, remote.categories, addedCategories, CATEGORY) { it.name }
        val store = resolver(local.stores, remote.stores, addedStores, STORE) { it.name }

        val products = newProducts.map { p ->
            val changed = p.copy(
                categoryId = p.categoryId?.let(category),
                defaultLocationId = p.defaultLocationId?.let(location),
                bestPriceStoreId = p.bestPriceStoreId?.let(store),
            )
            if (changed == p) p else PRODUCT.touch(changed, nextModifiedAt(ctx.now, p.modifiedAt), ctx.deviceId)
        }
        val batches = newBatches.map { b ->
            val changed = b.copy(locationId = location(b.locationId), storeId = b.storeId?.let(store))
            if (changed == b) b else BATCH.touch(changed, nextModifiedAt(ctx.now, b.modifiedAt), ctx.deviceId)
        }
        val merged = local.copy(
            locations = local.locations + addedLocations,
            categories = local.categories + addedCategories,
            stores = local.stores + addedStores,
            products = local.products + products,
            batches = local.batches + batches,
            movements = local.movements + newMovements,
        )
        return result(local, merged, emptyList(), 0, emptySet())
    }

    private fun <T> resolver(
        localList: List<T>,
        remoteList: List<T>,
        added: MutableList<T>,
        kind: Kind<T>,
        name: (T) -> String,
    ): (String) -> String {
        val localIds = localList.mapTo(HashSet()) { kind.id(it) }
        val localByName = localList
            .filter { kind.deletedAt(it) == null }
            .groupBy { normalizedName(name(it)) }
            .mapValues { (_, list) -> list.minWith(kind.oldestFirst()) }
        val remoteById = remoteList.associateBy { kind.id(it) }
        val chosen = HashMap<String, String>()
        return { ref ->
            chosen.getOrPut(ref) {
                val inFile = remoteById[ref]
                when {
                    ref in localIds || inFile == null -> ref
                    else -> localByName[normalizedName(name(inFile))]?.let(kind.id) ?: ref.also { added += inFile }
                }
            }
        }
    }
}

/** Counts for the preview: what changes for the user between [before] and [after]. */
private fun result(
    before: Snapshot,
    after: Snapshot,
    duplicates: List<ProductDuplicate>,
    autoMerged: Int,
    review: Set<RecordRef>,
): MergeResult {
    // A product is also updated when its stock changes: new movements or changed batches
    val batchProduct = (before.batches + after.batches).associate { it.id to it.productId }
    val oldBatches = before.batches.associateBy { it.id }
    val oldMovements = before.movements.mapTo(HashSet()) { it.id }
    val stockChanged = after.movements
        .filter { it.id !in oldMovements }
        .mapNotNullTo(HashSet()) { batchProduct[it.batchId] }
        .plus(
            after.batches
                .filter { oldBatches[it.id] != it }
                .flatMap { listOfNotNull(it.productId, oldBatches[it.id]?.productId) },
        )
    return MergeResult(
        snapshot = after,
        products = changes(before.products, after.products, { it.id }, { it.deletedAt }) { it in stockChanged },
        shoppingItems = changes(before.shoppingItems, after.shoppingItems, { it.id }, { it.deletedAt }) { false },
        productDuplicates = duplicates,
        autoMerged = autoMerged,
        notesToReview = review,
    )
}

private fun <T> changes(
    before: List<T>,
    after: List<T>,
    id: (T) -> String,
    deletedAt: (T) -> Long?,
    alsoUpdated: (String) -> Boolean,
): ChangeCounts {
    val old = before.filter { deletedAt(it) == null }.associateBy(id)
    val new = after.filter { deletedAt(it) == null }.associateBy(id)
    return ChangeCounts(
        new = new.keys.count { it !in old },
        updated = new.count { (key, record) -> key in old && (old[key] != record || alsoUpdated(key)) },
        deleted = old.keys.count { it !in new },
    )
}

/**
 * Rule 8: the products that this import took under their minimum stock (the same moment
 * as for a consumption, SPEC §8); homemade and deleted products excluded.
 */
fun productsDroppedBelowMinStock(before: Snapshot, after: Snapshot): List<String> {
    val oldQuantities = quantitiesByBatch(before.movements)
    val newQuantities = quantitiesByBatch(after.movements)
    val oldBatches = before.batches.groupBy { it.productId }
    val newBatches = after.batches.groupBy { it.productId }
    val oldProducts = before.products.associateBy { it.id }
    return after.products
        .filter { product ->
            val old = oldProducts[product.id]
            val wasAbove = old == null || old.deletedAt != null ||
                !isBelowMinStock(productQuantity(oldBatches[product.id].orEmpty(), oldQuantities), old.minStock)
            wasAbove && droppedBelowMinStock(
                product,
                before = Long.MAX_VALUE,
                after = productQuantity(newBatches[product.id].orEmpty(), newQuantities),
            )
        }
        .map { it.id }
}

/** The products with a "notes to review" mark on themselves or on one of their batches. */
fun reviewedProducts(marks: Set<RecordRef>, batches: List<Batch>): Set<String> {
    val batchProduct = batches.associate { it.id to it.productId }
    return marks.mapNotNullTo(HashSet()) { ref ->
        when (ref.type) {
            RecordType.PRODUCT -> ref.id
            RecordType.BATCH -> batchProduct[ref.id]
            else -> null
        }
    }
}
