// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nidamanx.formica.data.CatalogRepository
import io.github.nidamanx.formica.domain.RecordRef
import io.github.nidamanx.formica.domain.RecordType
import io.github.nidamanx.formica.domain.model.StoreType
import io.github.nidamanx.formica.domain.normalizedName
import io.github.nidamanx.formica.ui.appContainer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The three user-editable lists of the settings. */
enum class CatalogKind(val hasNotes: Boolean, val hasType: Boolean) {
    LOCATIONS(hasNotes = true, hasType = false),
    CATEGORIES(hasNotes = false, hasType = false),
    STORES(hasNotes = true, hasType = true),
}

data class CatalogItem(
    val id: String,
    val name: String,
    val type: StoreType?,
    val notes: String?,
    /** Its notes were appended in an import and are to review. */
    val review: Boolean = false,
)

sealed interface NameError {
    data object Required : NameError
    data class Exists(val name: String) : NameError
}

class CatalogViewModel(
    private val kind: CatalogKind,
    private val catalog: CatalogRepository,
    notesToReview: Flow<Set<RecordRef>>,
) : ViewModel() {

    val items: StateFlow<List<CatalogItem>> = when (kind) {
        CatalogKind.LOCATIONS -> combine(catalog.locations, notesToReview) { list, marks ->
            list.map { CatalogItem(it.id, it.name, null, it.notes, RecordRef(RecordType.LOCATION, it.id) in marks) }
        }
        CatalogKind.CATEGORIES -> catalog.categories.map { list ->
            list.map { CatalogItem(it.id, it.name, null, null) }
        }
        CatalogKind.STORES -> combine(catalog.stores, notesToReview) { list, marks ->
            list.map { CatalogItem(it.id, it.name, it.type, it.notes, RecordRef(RecordType.STORE, it.id) in marks) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Names must be present and unique within the list (ignoring case and extra spaces). */
    fun validate(name: String, editingId: String?): NameError? {
        val normalized = normalizedName(name)
        if (normalized.isEmpty()) return NameError.Required
        val clash = items.value.firstOrNull { it.id != editingId && normalizedName(it.name) == normalized }
        return clash?.let { NameError.Exists(it.name) }
    }

    fun save(editingId: String?, name: String, type: StoreType, notes: String) {
        val cleanNotes = notes.trim().ifEmpty { null }
        viewModelScope.launch {
            when (kind) {
                CatalogKind.LOCATIONS ->
                    if (editingId == null) {
                        catalog.addLocation(name, cleanNotes)
                    } else {
                        catalog.updateLocation(editingId) { it.copy(name = name, notes = cleanNotes) }
                    }
                CatalogKind.CATEGORIES ->
                    if (editingId == null) {
                        catalog.addCategory(name)
                    } else {
                        catalog.updateCategory(editingId) { it.copy(name = name) }
                    }
                CatalogKind.STORES ->
                    if (editingId == null) {
                        catalog.addStore(name, type, cleanNotes)
                    } else {
                        catalog.updateStore(editingId) { it.copy(name = name, type = type, notes = cleanNotes) }
                    }
            }
        }
    }

    fun move(id: String, up: Boolean) {
        val ids = items.value.map { it.id }.toMutableList()
        val from = ids.indexOf(id)
        val to = if (up) from - 1 else from + 1
        if (from < 0 || to !in ids.indices) return
        ids[from] = ids[to].also { ids[to] = ids[from] }
        viewModelScope.launch {
            when (kind) {
                CatalogKind.LOCATIONS -> catalog.reorderLocations(ids)
                CatalogKind.CATEGORIES -> catalog.reorderCategories(ids)
                CatalogKind.STORES -> catalog.reorderStores(ids)
            }
        }
    }

    /** Whether the item can be deleted: only a location still holding stock cannot. */
    suspend fun canDelete(id: String): Boolean =
        kind != CatalogKind.LOCATIONS || !catalog.isLocationInUse(id)

    /** @param onBlocked called when a location turned out to be in use at the time of deletion */
    fun delete(id: String, onBlocked: () -> Unit) {
        viewModelScope.launch {
            when (kind) {
                CatalogKind.LOCATIONS -> if (!catalog.deleteLocation(id)) onBlocked()
                CatalogKind.CATEGORIES -> catalog.deleteCategory(id)
                CatalogKind.STORES -> catalog.deleteStore(id)
            }
        }
    }

    companion object {
        fun factory(kind: CatalogKind) = viewModelFactory {
            initializer {
                val c = appContainer()
                CatalogViewModel(kind, c.catalog, c.exchange.notesToReview)
            }
        }
    }
}
