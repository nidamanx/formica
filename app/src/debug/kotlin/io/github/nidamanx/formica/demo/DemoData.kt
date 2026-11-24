// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.demo

import io.github.nidamanx.formica.domain.model.Batch
import io.github.nidamanx.formica.domain.model.Category
import io.github.nidamanx.formica.domain.model.Location
import io.github.nidamanx.formica.domain.model.MeasureUnit
import io.github.nidamanx.formica.domain.model.Movement
import io.github.nidamanx.formica.domain.model.MovementType
import io.github.nidamanx.formica.domain.model.ONE_UNIT
import io.github.nidamanx.formica.domain.model.Origin
import io.github.nidamanx.formica.domain.model.Product
import io.github.nidamanx.formica.domain.model.ShoppingItem
import io.github.nidamanx.formica.domain.model.ShoppingStatus
import io.github.nidamanx.formica.domain.model.Snapshot
import io.github.nidamanx.formica.domain.model.Store
import io.github.nidamanx.formica.domain.model.StoreType

/** The texts of the sample data, in the phone's language (debug string resources). */
enum class DemoText {
    STORE_SUPERMARKET, STORE_MARKET, STORE_BAKERY, STORE_FARM, STORE_OIL_MILL,
    MILK, YOGURT, EGGS, RICOTTA, SPINACH, RAGU, PEAS, PASSATA, APRICOT_JAM, HONEY, OLIVE_OIL,
    WHOLEMEAL_FLOUR, BROWN_RICE, LENTILS, SAUERKRAUT, POTATOES, APPLES,
    WHOLEMEAL_BREAD, LEMONS, TOMATOES,
    PRICE_EACH, PRICE_PER_LITRE, NOTE_RAGU, NOTE_OIL,
}

// Indexes of the initial locations and categories (SPEC §15), in their sort order
private const val FRIDGE = 0
private const val FREEZER = 1
private const val PANTRY = 2
private const val CELLAR = 3
private const val FRUIT_VEG = 0
private const val DAIRY = 1
private const val BREAD_PASTA = 3
private const val RICE_CEREALS = 4
private const val FLOURS = 5
private const val PRESERVES = 6
private const val FROZEN = 7
private const val CONDIMENTS = 9
private const val BREAKFAST = 10

/**
 * Sample data for screenshots and manual tests (debug builds only): 17 products that show
 * every state of the app. Dates are relative to [today], so the screens look the same on
 * any day: milk expiring tomorrow, ricotta expired yesterday, sauerkraut not ready yet,
 * flour running low, brown rice bought often...
 *
 * @param locations the initial locations, in sort order (Fridge, Freezer, Pantry, Cellar)
 * @param categories the initial categories, in sort order
 * @param millisAt epoch millis of noon of an epoch day, in the phone's time zone
 */
class DemoData(
    private val today: Long,
    private val deviceId: String,
    private val newId: () -> String,
    private val millisAt: (Long) -> Long,
    private val locations: List<Location>,
    private val categories: List<Category>,
    private val text: (DemoText) -> String,
) {
    private val stores = mutableListOf<Store>()
    private val products = mutableListOf<Product>()
    private val batches = mutableListOf<Batch>()
    private val movements = mutableListOf<Movement>()
    private val items = mutableListOf<ShoppingItem>()

    private fun location(index: Int) = locations.getOrElse(index) { locations.first() }.id
    private fun category(index: Int) = categories.getOrNull(index)?.id
    private fun daysAgo(days: Long) = millisAt(today - days)

    private fun store(name: DemoText, type: StoreType): Store = Store(
        id = newId(), name = text(name), type = type, sortOrder = stores.size,
        createdAt = daysAgo(DATA_AGE), modifiedAt = daysAgo(DATA_AGE), modifiedBy = deviceId,
    ).also { stores += it }

    private fun product(
        name: DemoText,
        unit: MeasureUnit,
        categoryIndex: Int,
        locationIndex: Int,
        homemade: Boolean = false,
        configure: Product.() -> Product = { this },
    ): Product = Product(
        id = newId(), name = text(name), unit = unit, categoryId = category(categoryIndex),
        defaultLocationId = location(locationIndex), homemade = homemade,
        createdAt = daysAgo(DATA_AGE), modifiedAt = daysAgo(DATA_AGE), modifiedBy = deviceId,
    ).configure().also { products += it }

    /**
     * A batch with its movements: the first one (purchase or preparation) [startDaysAgo],
     * then each consumption as (days ago, quantity).
     */
    private fun batch(
        product: Product,
        quantity: Long,
        startDaysAgo: Long,
        expiresIn: Long? = null,
        store: Store? = null,
        readyIn: Long? = null,
        openedDaysAgo: Long? = null,
        consumed: List<Pair<Long, Long>> = emptyList(),
    ) {
        val homemade = product.homemade
        val batch = Batch(
            id = newId(), productId = product.id, locationId = product.defaultLocationId!!,
            origin = if (homemade) Origin.HOMEMADE else Origin.PURCHASED,
            preparedOn = if (homemade) today - startDaysAgo else null,
            purchasedOn = if (homemade) null else today - startDaysAgo,
            storeId = store?.id, expiresOn = expiresIn?.let { today + it },
            readyFrom = readyIn?.let { today + it }, openedOn = openedDaysAgo?.let { today - it },
            createdAt = daysAgo(startDaysAgo), modifiedAt = daysAgo(openedDaysAgo ?: startDaysAgo),
            modifiedBy = deviceId,
        )
        batches += batch
        movements += Movement(
            id = newId(), batchId = batch.id, delta = quantity,
            type = if (homemade) MovementType.PREPARATION else MovementType.PURCHASE,
            timestamp = daysAgo(startDaysAgo), deviceId = deviceId,
        )
        consumed.forEach { (days, amount) ->
            movements += Movement(
                id = newId(), batchId = batch.id, delta = -amount, type = MovementType.CONSUMPTION,
                timestamp = daysAgo(days), deviceId = deviceId,
            )
        }
    }

    private fun item(
        product: Product? = null,
        name: DemoText? = null,
        store: Store? = null,
        categoryIndex: Int? = null,
        status: ShoppingStatus = ShoppingStatus.TO_BUY,
        automatic: Boolean = false,
    ) {
        items += ShoppingItem(
            id = newId(), productId = product?.id, text = name?.let(text), storeId = store?.id,
            categoryId = categoryIndex?.let { category(it) }, status = status, automatic = automatic,
            createdAt = daysAgo(1), modifiedAt = daysAgo(1), modifiedBy = deviceId,
        )
    }

    private fun notes(textId: DemoText, days: Long): Product.() -> Product =
        { copy(notes = text(textId), notesModifiedAt = daysAgo(days)) }

    fun build(): Snapshot {
        val supermarket = store(DemoText.STORE_SUPERMARKET, StoreType.SUPERMARKET)
        val market = store(DemoText.STORE_MARKET, StoreType.MARKET)
        val bakery = store(DemoText.STORE_BAKERY, StoreType.SHOP)
        val farm = store(DemoText.STORE_FARM, StoreType.PRODUCER)
        val oilMill = store(DemoText.STORE_OIL_MILL, StoreType.PRODUCER)
        val litre = ONE_UNIT
        val kilo = ONE_UNIT
        val gram = ONE_UNIT

        // Fridge
        val milk = product(DemoText.MILK, MeasureUnit.LITER, DAIRY, FRIDGE) {
            copy(minStock = litre, daysAfterOpening = 3)
        }
        batch(milk, 4 * litre, 22, expiresIn = -12, store = supermarket,
            consumed = listOf(20L to litre, 17L to litre, 14L to litre, 12L to litre))
        batch(milk, litre, 2, expiresIn = 1, store = supermarket, openedDaysAgo = 1,
            consumed = listOf(1L to litre / 2))
        val yogurt = product(DemoText.YOGURT, MeasureUnit.JAR, DAIRY, FRIDGE, homemade = true) {
            copy(shelfLifeDays = 8)
        }
        batch(yogurt, 6 * ONE_UNIT, 3, expiresIn = 5, consumed = listOf(2L to ONE_UNIT, 1L to ONE_UNIT))
        val eggs = product(DemoText.EGGS, MeasureUnit.PIECE, DAIRY, FRIDGE) {
            copy(bestPriceStoreId = farm.id, bestPriceCents = 35, bestPriceReference = text(DemoText.PRICE_EACH),
                bestPriceNotedAt = daysAgo(10))
        }
        batch(eggs, 6 * ONE_UNIT, 10, expiresIn = 12, store = farm)
        val ricotta = product(DemoText.RICOTTA, MeasureUnit.GRAM, DAIRY, FRIDGE)
        batch(ricotta, 250 * gram, 6, expiresIn = -1, store = supermarket)
        val spinach = product(DemoText.SPINACH, MeasureUnit.GRAM, FRUIT_VEG, FRIDGE)
        batch(spinach, 300 * gram, 1, expiresIn = 2, store = market)

        // Freezer
        val ragu = product(DemoText.RAGU, MeasureUnit.PACKAGE, PRESERVES, FREEZER, homemade = true,
            configure = notes(DemoText.NOTE_RAGU, 60))
        batch(ragu, 5 * ONE_UNIT, 60, expiresIn = 120, consumed = listOf(40L to ONE_UNIT, 15L to ONE_UNIT))
        val peas = product(DemoText.PEAS, MeasureUnit.GRAM, FROZEN, FREEZER)
        batch(peas, 450 * gram, 20, expiresIn = 240, store = supermarket)

        // Pantry
        val passata = product(DemoText.PASSATA, MeasureUnit.BOTTLE, PRESERVES, PANTRY, homemade = true)
        batch(passata, 12 * ONE_UNIT, 465, expiresIn = 265, consumed = listOf(
            400L to 2 * ONE_UNIT, 300L to 2 * ONE_UNIT, 200L to 2 * ONE_UNIT, 150L to 2 * ONE_UNIT, 40L to 2 * ONE_UNIT,
        ))
        batch(passata, 6 * ONE_UNIT, 100, expiresIn = 630, consumed = listOf(20L to 2 * ONE_UNIT))
        val jam = product(DemoText.APRICOT_JAM, MeasureUnit.JAR, BREAKFAST, PANTRY, homemade = true)
        batch(jam, 6 * ONE_UNIT, 130, expiresIn = 600, consumed = listOf(60L to ONE_UNIT, 20L to ONE_UNIT))
        val honey = product(DemoText.HONEY, MeasureUnit.JAR, BREAKFAST, PANTRY)
        batch(honey, 2 * ONE_UNIT, 45, expiresIn = 700, store = farm)
        val oil = product(DemoText.OLIVE_OIL, MeasureUnit.LITER, CONDIMENTS, PANTRY) {
            val priced = copy(
                bestPriceStoreId = oilMill.id, bestPriceCents = 950,
                bestPriceReference = text(DemoText.PRICE_PER_LITRE), bestPriceNotedAt = daysAgo(25),
            )
            notes(DemoText.NOTE_OIL, 25)(priced)
        }
        batch(oil, 5 * litre, 25, store = oilMill, consumed = listOf(
            20L to litre / 2, 15L to litre / 2, 10L to litre / 2, 5L to litre / 2, 2L to litre / 2,
        ))
        val flour = product(DemoText.WHOLEMEAL_FLOUR, MeasureUnit.KILOGRAM, FLOURS, PANTRY)
        batch(flour, 3400, 35, expiresIn = 150, store = supermarket, consumed = listOf(
            30L to kilo / 2, 25L to kilo / 2, 20L to kilo / 2, 14L to kilo / 2, 8L to kilo / 2, 3L to kilo / 2,
        ))
        val rice = product(DemoText.BROWN_RICE, MeasureUnit.KILOGRAM, RICE_CEREALS, PANTRY)
        batch(rice, kilo, 150, expiresIn = 210, store = supermarket, consumed = listOf(140L to kilo / 2, 120L to kilo / 2))
        batch(rice, kilo, 110, expiresIn = 250, store = supermarket, consumed = listOf(90L to kilo / 2, 70L to kilo / 2))
        batch(rice, kilo, 60, expiresIn = 300, store = supermarket, consumed = listOf(50L to kilo / 2, 30L to kilo / 2))
        batch(rice, 2 * kilo, 15, expiresIn = 345, store = supermarket)
        val lentils = product(DemoText.LENTILS, MeasureUnit.GRAM, RICE_CEREALS, PANTRY)
        batch(lentils, 500 * gram, 30, expiresIn = 330, store = supermarket)

        // Cellar
        val sauerkraut = product(DemoText.SAUERKRAUT, MeasureUnit.JAR, PRESERVES, CELLAR, homemade = true) {
            copy(daysUntilReady = 19)
        }
        batch(sauerkraut, 2 * ONE_UNIT, 5, expiresIn = 180, readyIn = 14)
        val potatoes = product(DemoText.POTATOES, MeasureUnit.KILOGRAM, FRUIT_VEG, CELLAR)
        batch(potatoes, 3 * kilo, 12, store = market)
        val apples = product(DemoText.APPLES, MeasureUnit.KILOGRAM, FRUIT_VEG, CELLAR)
        batch(apples, 2 * kilo, 6, store = market)

        // Shopping list: milk is under its minimum stock (0.5 of 1 l)
        item(product = milk, automatic = true)
        item(name = DemoText.WHOLEMEAL_BREAD, store = bakery, categoryIndex = BREAD_PASTA)
        item(name = DemoText.LEMONS, categoryIndex = FRUIT_VEG)
        item(name = DemoText.TOMATOES, categoryIndex = FRUIT_VEG, status = ShoppingStatus.BOUGHT)

        return Snapshot(
            stores = stores, products = products, batches = batches, movements = movements, shoppingItems = items,
        )
    }

    private companion object {
        /** Age of the oldest records: the stores and products existed before the oldest batch. */
        const val DATA_AGE = 500L
    }
}
