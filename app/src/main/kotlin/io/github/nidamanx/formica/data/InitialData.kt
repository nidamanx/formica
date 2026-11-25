// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.data

import android.content.res.Resources
import androidx.room.withTransaction
import io.github.nidamanx.formica.R
import io.github.nidamanx.formica.domain.OperationContext
import io.github.nidamanx.formica.domain.model.Category
import io.github.nidamanx.formica.domain.model.Location

/**
 * Locations and categories proposed at the first start, in the phone's language at that
 * moment; afterwards they are ordinary user data. A table is filled only if it has never
 * had a record (deleted ones included), so what the user removes never comes back.
 */
class InitialData(
    private val db: FormicaDatabase,
    private val resources: Resources,
    private val context: suspend () -> OperationContext,
) {
    suspend fun createIfEmpty() {
        val ctx = context()
        val locationNames = resources.getStringArray(R.array.initial_locations)
        val categoryNames = resources.getStringArray(R.array.initial_categories)
        db.withTransaction {
            if (db.locationDao().countIncludingDeleted() == 0) {
                db.locationDao().upsert(
                    locationNames.mapIndexed { index, name ->
                        Location(
                            id = ctx.newId(),
                            name = name,
                            sortOrder = index,
                            createdAt = ctx.now,
                            modifiedAt = ctx.now,
                            modifiedBy = ctx.deviceId,
                        ).toEntity()
                    },
                )
            }
            if (db.categoryDao().countIncludingDeleted() == 0) {
                db.categoryDao().upsert(
                    categoryNames.mapIndexed { index, name ->
                        Category(
                            id = ctx.newId(),
                            name = name,
                            sortOrder = index,
                            createdAt = ctx.now,
                            modifiedAt = ctx.now,
                            modifiedBy = ctx.deviceId,
                        ).toEntity()
                    },
                )
            }
        }
    }
}
