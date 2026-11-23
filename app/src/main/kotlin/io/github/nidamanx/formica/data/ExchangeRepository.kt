// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.data

import androidx.room.withTransaction
import io.github.nidamanx.formica.domain.model.ImportRecord
import io.github.nidamanx.formica.domain.model.Snapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Whole-database reads and writes of the data exchange (SPEC §11). */
class ExchangeRepository(private val db: FormicaDatabase) {

    /** Every synced record, deleted ones included, read in one transaction. */
    suspend fun snapshot(): Snapshot = db.withTransaction {
        Snapshot(
            locations = db.locationDao().all().map { it.toDomain() },
            categories = db.categoryDao().all().map { it.toDomain() },
            stores = db.storeDao().all().map { it.toDomain() },
            products = db.productDao().all().map { it.toDomain() },
            batches = db.batchDao().all().map { it.toDomain() },
            movements = db.movementDao().all().map { it.toDomain() },
            shoppingItems = db.shoppingItemDao().all().map { it.toDomain() },
        )
    }

    /** The imported files, newest first. */
    val importLog: Flow<List<ImportRecord>> =
        db.importRecordDao().observeAll().map { list -> list.map { it.toDomain() } }
}
