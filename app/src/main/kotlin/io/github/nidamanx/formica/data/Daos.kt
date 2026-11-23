// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
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
}
