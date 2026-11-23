// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.expiries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nidamanx.formica.data.CatalogRepository
import io.github.nidamanx.formica.data.PantryRepository
import io.github.nidamanx.formica.domain.ExpiryOverview
import io.github.nidamanx.formica.domain.ListThresholds
import io.github.nidamanx.formica.domain.expiryOverview
import io.github.nidamanx.formica.ui.appContainer
import java.text.Collator
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ExpiriesUiState(
    val loaded: Boolean = false,
    val overview: ExpiryOverview = ExpiryOverview(emptyList(), emptyList(), emptyList(), emptyList()),
    val locationNames: Map<String, String> = emptyMap(),
    val today: Long = 0,
)

class ExpiriesViewModel(
    pantry: PantryRepository,
    catalog: CatalogRepository,
    thresholds: Flow<ListThresholds>,
) : ViewModel() {

    val state: StateFlow<ExpiriesUiState> = combine(
        pantry.products,
        pantry.batches,
        pantry.batchQuantities,
        catalog.locations,
        thresholds,
    ) { products, batches, quantities, locations, limits ->
        val today = LocalDate.now().toEpochDay()
        val collator = Collator.getInstance()
        ExpiriesUiState(
            loaded = true,
            overview = expiryOverview(
                products = products,
                batches = batches,
                quantities = quantities,
                today = today,
                defaultWarningDays = limits.expiryWarningDays,
                compareNames = { a, b -> collator.compare(a, b) },
            ),
            locationNames = locations.associate { it.id to it.name },
            today = today,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpiriesUiState())

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                ExpiriesViewModel(c.pantry, c.catalog, c.listThresholds)
            }
        }
    }
}
