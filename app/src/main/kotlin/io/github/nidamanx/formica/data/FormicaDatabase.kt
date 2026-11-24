// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Every version's schema is exported to app/schemas/ and versioned: a schema change needs a
// new version, its explicit migration and the new schema file (checked by the Build workflow)
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
        NotesReviewEntity::class,
    ],
    version = 3,
    exportSchema = true,
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
    abstract fun notesReviewDao(): NotesReviewDao

    companion object {
        /** mergedIntoId on locations, categories and stores (SPEC §11.8). */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("locations", "categories", "stores").forEach { table ->
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `mergedIntoId` TEXT")
                }
            }
        }

        /** The local "notes to review" marks (SPEC §11.8). */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `notes_review` " +
                        "(`type` TEXT NOT NULL, `id` TEXT NOT NULL, PRIMARY KEY(`type`, `id`))",
                )
            }
        }

        fun create(context: Context): FormicaDatabase =
            Room.databaseBuilder(context, FormicaDatabase::class.java, "formica.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
