// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import io.github.nidamanx.formica.domain.RecordType
import kotlinx.coroutines.flow.Flow

/*
 * "observe*" queries exclude deleted records (what the UI shows); "all*" and "get" queries
 * include them (needed by edits, the sync and the initial data check).
 */

@Dao
interface LocationDao {
    @Query("SELECT * FROM locations WHERE deletedAt IS NULL ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<LocationEntity>>

    @Query("SELECT * FROM locations WHERE id = :id")
    suspend fun get(id: String): LocationEntity?

    @Query("SELECT * FROM locations")
    suspend fun all(): List<LocationEntity>

    @Query("DELETE FROM locations")
    suspend fun deleteAll()

    /** Tombstone clean-up (SPEC §11.7): the number of records removed. */
    @Query("DELETE FROM locations WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun purgeDeleted(before: Long): Int

    @Query("SELECT COUNT(*) FROM locations")
    suspend fun countIncludingDeleted(): Int

    @Upsert
    suspend fun upsert(items: List<LocationEntity>)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE deletedAt IS NULL ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: String): CategoryEntity?

    @Query("SELECT * FROM categories")
    suspend fun all(): List<CategoryEntity>

    @Query("DELETE FROM categories")
    suspend fun deleteAll()

    /** Tombstone clean-up (SPEC §11.7): the number of records removed. */
    @Query("DELETE FROM categories WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun purgeDeleted(before: Long): Int

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun countIncludingDeleted(): Int

    @Upsert
    suspend fun upsert(items: List<CategoryEntity>)
}

@Dao
interface StoreDao {
    @Query("SELECT * FROM stores WHERE deletedAt IS NULL ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<StoreEntity>>

    @Query("SELECT * FROM stores WHERE id = :id")
    suspend fun get(id: String): StoreEntity?

    @Query("SELECT * FROM stores")
    suspend fun all(): List<StoreEntity>

    @Query("DELETE FROM stores")
    suspend fun deleteAll()

    /** Tombstone clean-up (SPEC §11.7): the number of records removed. */
    @Query("DELETE FROM stores WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun purgeDeleted(before: Long): Int

    @Upsert
    suspend fun upsert(items: List<StoreEntity>)
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id")
    fun observe(id: String): Flow<ProductEntity?>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun get(id: String): ProductEntity?

    @Query("SELECT * FROM products")
    suspend fun all(): List<ProductEntity>

    @Query("DELETE FROM products")
    suspend fun deleteAll()

    /** Tombstone clean-up (SPEC §11.7): the number of records removed. */
    @Query("DELETE FROM products WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun purgeDeleted(before: Long): Int

    @Upsert
    suspend fun upsert(items: List<ProductEntity>)
}

@Dao
interface BatchDao {
    @Query("SELECT * FROM batches WHERE productId = :productId")
    suspend fun forProduct(productId: String): List<BatchEntity>

    @Query("SELECT * FROM batches WHERE productId = :productId AND deletedAt IS NULL")
    fun observeForProduct(productId: String): Flow<List<BatchEntity>>

    @Query("SELECT * FROM batches WHERE deletedAt IS NULL")
    fun observeAll(): Flow<List<BatchEntity>>

    @Query("SELECT * FROM batches")
    suspend fun all(): List<BatchEntity>

    @Query("DELETE FROM batches")
    suspend fun deleteAll()

    /** Tombstone clean-up (SPEC §11.7): the number of records removed. */
    @Query("DELETE FROM batches WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun purgeDeleted(before: Long): Int

    /** Batches with stock left in a location, among the products that are not deleted. */
    @Query(
        "SELECT COUNT(*) FROM batches b JOIN products p ON p.id = b.productId " +
            "WHERE b.locationId = :locationId AND b.deletedAt IS NULL AND p.deletedAt IS NULL " +
            "AND (SELECT COALESCE(SUM(m.delta), 0) FROM movements m WHERE m.batchId = b.id) > 0",
    )
    suspend fun countWithStockIn(locationId: String): Int

    @Upsert
    suspend fun upsert(items: List<BatchEntity>)
}

/** Movements are immutable: inserted once, never updated or deleted; a known id is ignored. */
@Dao
interface MovementDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(items: List<MovementEntity>)

    @Query(
        "SELECT m.* FROM movements m JOIN batches b ON m.batchId = b.id " +
            "WHERE b.productId = :productId ORDER BY m.timestamp",
    )
    suspend fun forProduct(productId: String): List<MovementEntity>

    @Query(
        "SELECT m.* FROM movements m JOIN batches b ON m.batchId = b.id " +
            "WHERE b.productId = :productId ORDER BY m.timestamp",
    )
    fun observeForProduct(productId: String): Flow<List<MovementEntity>>

    @Query("SELECT * FROM movements")
    fun observeAll(): Flow<List<MovementEntity>>

    @Query("SELECT * FROM movements")
    suspend fun all(): List<MovementEntity>

    @Query("DELETE FROM movements")
    suspend fun deleteAll()

    @Query("SELECT batchId AS id, SUM(delta) AS quantity FROM movements GROUP BY batchId")
    fun observeBatchQuantities(): Flow<List<IdQuantity>>

    /** Totals of the products, from their batches that are not deleted. */
    @Query(
        "SELECT b.productId AS id, SUM(m.delta) AS quantity FROM movements m " +
            "JOIN batches b ON m.batchId = b.id WHERE b.deletedAt IS NULL GROUP BY b.productId",
    )
    fun observeProductQuantities(): Flow<List<IdQuantity>>
}

@Dao
interface ShoppingItemDao {
    @Query("SELECT * FROM shopping_items WHERE deletedAt IS NULL ORDER BY createdAt")
    fun observeAll(): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_items WHERE id = :id")
    suspend fun get(id: String): ShoppingItemEntity?

    @Query("SELECT * FROM shopping_items WHERE deletedAt IS NULL")
    suspend fun active(): List<ShoppingItemEntity>

    @Query("SELECT * FROM shopping_items")
    suspend fun all(): List<ShoppingItemEntity>

    @Query("DELETE FROM shopping_items")
    suspend fun deleteAll()

    /** Tombstone clean-up (SPEC §11.7): the number of records removed. */
    @Query("DELETE FROM shopping_items WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun purgeDeleted(before: Long): Int

    @Upsert
    suspend fun upsert(items: List<ShoppingItemEntity>)
}

@Dao
interface ImportRecordDao {
    @Insert
    suspend fun insert(record: ImportRecordEntity)

    @Query("SELECT * FROM import_records ORDER BY importedAt DESC")
    fun observeAll(): Flow<List<ImportRecordEntity>>

    /** Last import from a device: its exportedAt is the reference time of the notes merge. */
    @Query("SELECT * FROM import_records WHERE deviceId = :deviceId ORDER BY exportedAt DESC LIMIT 1")
    suspend fun lastFrom(deviceId: String): ImportRecordEntity?

    /** The imports undone by restoring the copy taken at [time]. */
    @Query("DELETE FROM import_records WHERE importedAt >= :time")
    suspend fun deleteFrom(time: Long)
}

@Dao
interface NotesReviewDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(items: List<NotesReviewEntity>)

    @Query("SELECT * FROM notes_review")
    fun observeAll(): Flow<List<NotesReviewEntity>>

    @Query("DELETE FROM notes_review WHERE type = :type AND id = :id")
    suspend fun delete(type: RecordType, id: String)
}
