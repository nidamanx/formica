// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.data

import androidx.room.withTransaction
import io.github.nidamanx.formica.domain.RecordRef
import io.github.nidamanx.formica.domain.model.ImportRecord
import io.github.nidamanx.formica.domain.model.Snapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Whole-database reads and writes of the data exchange (SPEC §11). */
class ExchangeRepository(private val db: FormicaDatabase) {

    /** Every synced record, deleted ones included, read in one transaction. */
    suspend fun snapshot(): Snapshot = db.withTransaction { readSnapshot() }

    /** The imported files, newest first. */
    val importLog: Flow<List<ImportRecord>> =
        db.importRecordDao().observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun lastImport(): ImportRecord? = importLog.first().firstOrNull()

    /** exportedAt of the last file imported from [deviceId], 0 if none (SPEC §11.5.3). */
    suspend fun lastExportedAtFrom(deviceId: String): Long = db.importRecordDao().lastFrom(deviceId)?.exportedAt ?: 0L

    /**
     * Writes the result of an import in one transaction (SPEC §11.3): with [replace] every
     * synced record is removed first; otherwise records are upserted and movements added.
     * The local log and the "notes to review" marks are written in the same transaction.
     *
     * @param undoImportsFrom when restoring a copy: the imports from this time on are undone,
     *   so they leave the log (the merge of notes relies on the last import per device)
     */
    suspend fun apply(
        snapshot: Snapshot,
        replace: Boolean,
        record: ImportRecord?,
        review: Set<RecordRef>,
        undoImportsFrom: Long? = null,
    ) {
        db.withTransaction {
            if (undoImportsFrom != null) db.importRecordDao().deleteFrom(undoImportsFrom)
            if (replace) {
                db.locationDao().deleteAll()
                db.categoryDao().deleteAll()
                db.storeDao().deleteAll()
                db.productDao().deleteAll()
                db.batchDao().deleteAll()
                db.movementDao().deleteAll()
                db.shoppingItemDao().deleteAll()
            }
            val current = if (replace) Snapshot() else readSnapshot()
            db.locationDao().upsert(changed(current.locations, snapshot.locations) { it.id }.map { it.toEntity() })
            db.categoryDao().upsert(changed(current.categories, snapshot.categories) { it.id }.map { it.toEntity() })
            db.storeDao().upsert(changed(current.stores, snapshot.stores) { it.id }.map { it.toEntity() })
            db.productDao().upsert(changed(current.products, snapshot.products) { it.id }.map { it.toEntity() })
            db.batchDao().upsert(changed(current.batches, snapshot.batches) { it.id }.map { it.toEntity() })
            db.movementDao().insert(snapshot.movements.map { it.toEntity() })
            db.shoppingItemDao().upsert(
                changed(current.shoppingItems, snapshot.shoppingItems) { it.id }.map { it.toEntity() },
            )
            if (record != null) {
                db.importRecordDao().insert(
                    ImportRecordEntity(
                        deviceId = record.deviceId,
                        deviceName = record.deviceName,
                        exportedAt = record.exportedAt,
                        importedAt = record.importedAt,
                        mode = record.mode,
                    ),
                )
            }
            db.notesReviewDao().insert(review.map { NotesReviewEntity(it.type, it.id) })
        }
    }

    /**
     * Removes for good the records deleted before [before] (SPEC §11.7). Movements stay:
     * they are never deleted, and those of a removed batch are simply no longer reached.
     *
     * @return the number of records removed
     */
    suspend fun purgeDeleted(before: Long): Int = db.withTransaction {
        db.locationDao().purgeDeleted(before) +
            db.categoryDao().purgeDeleted(before) +
            db.storeDao().purgeDeleted(before) +
            db.productDao().purgeDeleted(before) +
            db.batchDao().purgeDeleted(before) +
            db.shoppingItemDao().purgeDeleted(before)
    }

    private suspend fun readSnapshot() = Snapshot(
        locations = db.locationDao().all().map { it.toDomain() },
        categories = db.categoryDao().all().map { it.toDomain() },
        stores = db.storeDao().all().map { it.toDomain() },
        products = db.productDao().all().map { it.toDomain() },
        batches = db.batchDao().all().map { it.toDomain() },
        movements = db.movementDao().all().map { it.toDomain() },
        shoppingItems = db.shoppingItemDao().all().map { it.toDomain() },
    )
}

/** Only new or different records are written. */
private fun <T> changed(current: List<T>, wanted: List<T>, id: (T) -> String): List<T> {
    val byId = current.associateBy(id)
    return wanted.filter { byId[id(it)] != it }
}
