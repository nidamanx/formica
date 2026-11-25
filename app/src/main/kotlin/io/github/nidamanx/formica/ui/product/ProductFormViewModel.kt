// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nidamanx.formica.data.CatalogRepository
import io.github.nidamanx.formica.data.InitialStock
import io.github.nidamanx.formica.data.PantryRepository
import io.github.nidamanx.formica.domain.cleanName
import io.github.nidamanx.formica.domain.model.Category
import io.github.nidamanx.formica.domain.model.Location
import io.github.nidamanx.formica.domain.model.MeasureUnit
import io.github.nidamanx.formica.domain.model.Product
import io.github.nidamanx.formica.domain.model.Store
import io.github.nidamanx.formica.domain.normalizedName
import io.github.nidamanx.formica.ui.appContainer
import io.github.nidamanx.formica.ui.settings.NameError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the user typed, as text: parsed only when saving. */
data class ProductFields(
    val name: String,
    val quantity: String,
    val unit: MeasureUnit,
    val locationId: String?,
    val categoryId: String?,
    val homemade: Boolean,
    /** First stock only (new product): preparation or purchase date, ready date and expiry. */
    val preparedOn: Long,
    val purchasedOn: Long,
    val readyFrom: Long?,
    val expiresOn: Long?,
    val shelfLifeDays: String,
    val daysUntilReady: String,
    val daysAfterOpening: String,
    val warningDays: String,
    val minStock: String,
    val bestStoreId: String?,
    val bestPrice: String,
    val bestReference: String,
    val notes: String,
)

data class ProductFormErrors(
    val name: NameError? = null,
    val quantity: Boolean = false,
    val shelfLifeDays: Boolean = false,
    val daysUntilReady: Boolean = false,
    val daysAfterOpening: Boolean = false,
    val warningDays: Boolean = false,
    val minStock: Boolean = false,
    val bestPrice: Boolean = false,
) {
    val any: Boolean
        get() = name != null || quantity || shelfLifeDays || daysUntilReady || daysAfterOpening || warningDays ||
            minStock || bestPrice
}

data class ProductFormState(
    val loaded: Boolean = false,
    /** The stored product when editing, null for a new one. */
    val product: Product? = null,
    val locations: List<Location> = emptyList(),
    val categories: List<Category> = emptyList(),
    val stores: List<Store> = emptyList(),
    val saved: Boolean = false,
)

private const val MAX_DAYS_DIGITS = 4

class ProductFormViewModel(
    private val productId: String?,
    private val pantry: PantryRepository,
    catalog: CatalogRepository,
) : ViewModel() {

    private val saved = MutableStateFlow(false)

    val state: StateFlow<ProductFormState> = combine(
        if (productId == null) flowOf(null) else pantry.observeProduct(productId),
        catalog.locations,
        catalog.categories,
        catalog.stores,
        saved,
    ) { product, locations, categories, stores, isSaved ->
        ProductFormState(true, product, locations, categories, stores, isSaved)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProductFormState())

    private var otherNames: Set<String> = emptySet()

    init {
        viewModelScope.launch {
            pantry.products.collect { products ->
                otherNames = products.filter { it.id != productId }.map { normalizedName(it.name) }.toSet()
            }
        }
    }

    /**
     * Validates and saves; the screen closes when [ProductFormState.saved] turns true.
     * @param parse locale-aware decimal parser
     * @param parsePrice locale-aware price parser (cents)
     */
    fun save(fields: ProductFields, parse: (String) -> Long?, parsePrice: (String) -> Long?): ProductFormErrors {
        val quantity = fields.quantity.trim().takeIf { it.isNotEmpty() }?.let(parse)
        val minStock = fields.minStock.trim().takeIf { it.isNotEmpty() }?.let(parse)
        val bestPrice = fields.bestPrice.trim().takeIf { it.isNotEmpty() }?.let(parsePrice)
        val errors = ProductFormErrors(
            name = when {
                normalizedName(fields.name).isEmpty() -> NameError.Required
                normalizedName(fields.name) in otherNames -> NameError.Exists(cleanName(fields.name))
                else -> null
            },
            quantity = fields.quantity.isNotBlank() && quantity == null,
            shelfLifeDays = !fields.shelfLifeDays.isValidDays(),
            daysUntilReady = !fields.daysUntilReady.isValidDays(),
            daysAfterOpening = !fields.daysAfterOpening.isValidDays(),
            warningDays = !fields.warningDays.isValidDays(),
            minStock = fields.minStock.isNotBlank() && minStock == null,
            bestPrice = fields.bestPrice.isNotBlank() && bestPrice == null,
        )
        if (errors.any) return errors

        val stored = state.value.product
        viewModelScope.launch {
            if (stored == null) {
                val location = fields.locationId
                val initial = when {
                    quantity == null || quantity <= 0 || location == null -> null
                    fields.homemade -> InitialStock.Preparation(
                        quantity, location, fields.preparedOn, fields.expiresOn, fields.readyFrom,
                    )
                    else -> InitialStock.Purchase(
                        quantity, location, fields.expiresOn, fields.purchasedOn, null,
                    )
                }
                pantry.createProduct(fields.applyTo(emptyProduct(), minStock, bestPrice), initial)
            } else {
                pantry.updateProduct(fields.applyTo(stored, minStock, bestPrice))
            }
            saved.value = true
        }
        return errors
    }

    private fun ProductFields.applyTo(product: Product, minStock: Long?, bestPrice: Long?): Product =
        product.copy(
            name = cleanName(name),
            unit = unit,
            categoryId = categoryId,
            defaultLocationId = locationId,
            homemade = homemade,
            shelfLifeDays = shelfLifeDays.toDays(),
            daysUntilReady = daysUntilReady.toDays(),
            daysAfterOpening = daysAfterOpening.toDays(),
            expiryWarningDays = warningDays.toDays(),
            minStock = minStock,
            bestPriceStoreId = bestStoreId,
            bestPriceCents = bestPrice,
            bestPriceReference = bestReference.trim().ifEmpty { null },
            notes = notes.trim().ifEmpty { null },
        )

    companion object {
        fun factory(productId: String?) = viewModelFactory {
            initializer { ProductFormViewModel(productId, appContainer().pantry, appContainer().catalog) }
        }
    }
}

private fun emptyProduct() = Product(id = "", name = "", createdAt = 0, modifiedAt = 0, modifiedBy = "")

private fun String.isValidDays(): Boolean {
    val t = trim()
    return t.isEmpty() || (t.length <= MAX_DAYS_DIGITS && t.all { it in '0'..'9' })
}

private fun String.toDays(): Int? = trim().takeIf { it.isNotEmpty() }?.toInt()

/** Days typed in the form, or null while empty or not valid (used for the live proposals). */
fun String.daysOrNull(): Int? = if (isValidDays()) toDays() else null
