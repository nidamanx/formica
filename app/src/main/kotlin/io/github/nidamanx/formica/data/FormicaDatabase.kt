// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 2,
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
        /** mergedIntoId on locations, categories and stores (SPEC §11.8). */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("locations", "categories", "stores").forEach { table ->
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `mergedIntoId` TEXT")
                }
            }
        }

        fun create(context: Context): FormicaDatabase =
            Room.databaseBuilder(context, FormicaDatabase::class.java, "formica.db")
                .addMigrations(MIGRATION_1_2)
                // Development only, until the first release: a schema change recreates the
                // database. It must be removed before the first release, when every
                // migration becomes explicit.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
