// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.data

import androidx.room.withTransaction
import io.github.nidamanx.formica.domain.OperationContext
import io.github.nidamanx.formica.domain.cleanName
import io.github.nidamanx.formica.domain.editCategory
import io.github.nidamanx.formica.domain.editLocation
import io.github.nidamanx.formica.domain.editStore
import io.github.nidamanx.formica.domain.model.Category
import io.github.nidamanx.formica.domain.model.Location
import io.github.nidamanx.formica.domain.model.Store
import io.github.nidamanx.formica.domain.model.StoreType
import io.github.nidamanx.formica.domain.softDeleted
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Locations, categories and stores: user-editable lists with a manual order. */
class CatalogRepository(
    private val db: FormicaDatabase,
    private val context: suspend () -> OperationContext,
) {
    private val locationDao = db.locationDao()
    private val categoryDao = db.categoryDao()
    private val storeDao = db.storeDao()

    val locations: Flow<List<Location>> = locationDao.observeAll().map { list -> list.map { it.toDomain() } }
    val categories: Flow<List<Category>> = categoryDao.observeAll().map { list -> list.map { it.toDomain() } }
    val stores: Flow<List<Store>> = storeDao.observeAll().map { list -> list.map { it.toDomain() } }

    // Locations

    suspend fun addLocation(name: String, notes: String? = null): Location {
        val ctx = context()
        return db.withTransaction {
            val location = Location(
                id = ctx.newId(),
                name = cleanName(name),
                sortOrder = nextSortOrder(locationDao.all().map { it.sortOrder }),
                notes = notes,
                notesModifiedAt = notes?.let { ctx.now },
                createdAt = ctx.now,
                modifiedAt = ctx.now,
                modifiedBy = ctx.deviceId,
            )
            locationDao.upsert(listOf(location.toEntity()))
            location
        }
    }

    suspend fun updateLocation(id: String, change: (Location) -> Location) {
        val ctx = context()
        db.withTransaction {
            val stored = locationDao.get(id)?.toDomain() ?: error("location $id not found")
            val edited = change(stored)
            locationDao.upsert(listOf(editLocation(stored, edited.copy(name = cleanName(edited.name)), ctx).toEntity()))
        }
    }

    suspend fun isLocationInUse(id: String): Boolean = db.batchDao().countWithStockIn(id) > 0

    /** A location still holding stock is never deleted: returns false and changes nothing. */
    suspend fun deleteLocation(id: String): Boolean {
        val ctx = context()
        return db.withTransaction {
            val stored = locationDao.get(id)?.toDomain() ?: error("location $id not found")
            when {
                stored.deletedAt != null -> true
                db.batchDao().countWithStockIn(id) > 0 -> false
                else -> {
                    locationDao.upsert(listOf(stored.softDeleted(ctx).toEntity()))
                    true
                }
            }
        }
    }

    /** Saves the order shown in the settings: only the records whose position changes are touched. */
    suspend fun reorderLocations(idsInOrder: List<String>) {
        val ctx = context()
        db.withTransaction {
            val byId = locationDao.all().associateBy { it.id }
            val changed = idsInOrder.mapIndexedNotNull { index, id ->
                val stored = byId[id]?.toDomain() ?: return@mapIndexedNotNull null
                if (stored.sortOrder == index) null else editLocation(stored, stored.copy(sortOrder = index), ctx)
            }
            if (changed.isNotEmpty()) locationDao.upsert(changed.map { it.toEntity() })
        }
    }

    // Categories

    suspend fun addCategory(name: String): Category {
        val ctx = context()
        return db.withTransaction {
            val category = Category(
                id = ctx.newId(),
                name = cleanName(name),
                sortOrder = nextSortOrder(categoryDao.all().map { it.sortOrder }),
                createdAt = ctx.now,
                modifiedAt = ctx.now,
                modifiedBy = ctx.deviceId,
            )
            categoryDao.upsert(listOf(category.toEntity()))
            category
        }
    }

    suspend fun updateCategory(id: String, change: (Category) -> Category) {
        val ctx = context()
        db.withTransaction {
            val stored = categoryDao.get(id)?.toDomain() ?: error("category $id not found")
            val edited = change(stored)
            categoryDao.upsert(listOf(editCategory(stored, edited.copy(name = cleanName(edited.name)), ctx).toEntity()))
        }
    }

    suspend fun deleteCategory(id: String) {
        val ctx = context()
        db.withTransaction {
            val stored = categoryDao.get(id)?.toDomain() ?: error("category $id not found")
            if (stored.deletedAt != null) return@withTransaction
            categoryDao.upsert(listOf(stored.softDeleted(ctx).toEntity()))
        }
    }

    suspend fun reorderCategories(idsInOrder: List<String>) {
        val ctx = context()
        db.withTransaction {
            val byId = categoryDao.all().associateBy { it.id }
            val changed = idsInOrder.mapIndexedNotNull { index, id ->
                val stored = byId[id]?.toDomain() ?: return@mapIndexedNotNull null
                if (stored.sortOrder == index) null else editCategory(stored, stored.copy(sortOrder = index), ctx)
            }
            if (changed.isNotEmpty()) categoryDao.upsert(changed.map { it.toEntity() })
        }
    }

    // Stores

    suspend fun addStore(name: String, type: StoreType, notes: String? = null): Store {
        val ctx = context()
        return db.withTransaction {
            val store = Store(
                id = ctx.newId(),
                name = cleanName(name),
                type = type,
                sortOrder = nextSortOrder(storeDao.all().map { it.sortOrder }),
                notes = notes,
                notesModifiedAt = notes?.let { ctx.now },
                createdAt = ctx.now,
                modifiedAt = ctx.now,
                modifiedBy = ctx.deviceId,
            )
            storeDao.upsert(listOf(store.toEntity()))
            store
        }
    }

    suspend fun updateStore(id: String, change: (Store) -> Store) {
        val ctx = context()
        db.withTransaction {
            val stored = storeDao.get(id)?.toDomain() ?: error("store $id not found")
            val edited = change(stored)
            storeDao.upsert(listOf(editStore(stored, edited.copy(name = cleanName(edited.name)), ctx).toEntity()))
        }
    }

    suspend fun deleteStore(id: String) {
        val ctx = context()
        db.withTransaction {
            val stored = storeDao.get(id)?.toDomain() ?: error("store $id not found")
            if (stored.deletedAt != null) return@withTransaction
            storeDao.upsert(listOf(stored.softDeleted(ctx).toEntity()))
        }
    }

    suspend fun reorderStores(idsInOrder: List<String>) {
        val ctx = context()
        db.withTransaction {
            val byId = storeDao.all().associateBy { it.id }
            val changed = idsInOrder.mapIndexedNotNull { index, id ->
                val stored = byId[id]?.toDomain() ?: return@mapIndexedNotNull null
                if (stored.sortOrder == index) null else editStore(stored, stored.copy(sortOrder = index), ctx)
            }
            if (changed.isNotEmpty()) storeDao.upsert(changed.map { it.toEntity() })
        }
    }

    private fun nextSortOrder(existing: List<Int>): Int = (existing.maxOrNull() ?: -1) + 1
}
