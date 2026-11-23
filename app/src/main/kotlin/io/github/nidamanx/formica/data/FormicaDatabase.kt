// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// The schema is exported and versioned from the first release on (with explicit migrations)
@Database(
    entities = [
        LocationEntity::class,
        CategoryEntity::class,
        StoreEntity::class,
        ProductEntity::class,
        BatchEntity::class,
        MovementEntity::class,
        ShoppingItemEntity::class,
        ImportRecordEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class FormicaDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    abstract fun categoryDao(): CategoryDao
    abstract fun storeDao(): StoreDao
    abstract fun productDao(): ProductDao
    abstract fun batchDao(): BatchDao
    abstract fun movementDao(): MovementDao
    abstract fun shoppingItemDao(): ShoppingItemDao
    abstract fun importRecordDao(): ImportRecordDao

    companion object {
        fun create(context: Context): FormicaDatabase =
            Room.databaseBuilder(context, FormicaDatabase::class.java, "formica.db")
                // Development only, until the first release: a schema change recreates the
                // database. It must be removed before the first release, when every
                // migration becomes explicit.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
