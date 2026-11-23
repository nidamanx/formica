// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.nidamanx.formica.domain.MinStockBehavior
import io.github.nidamanx.formica.domain.TickBehavior
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class ShoppingPreferences(
    val onTick: TickBehavior = TickBehavior.ASK,
    val minStock: MinStockBehavior = MinStockBehavior.AUTOMATIC,
    val runningLowDays: Int = DisplayDefaults.RUNNING_LOW_DAYS,
)

/** Shopping list settings (SPEC §3, local settings): defaults from SPEC §8 and §15. */
class ShoppingSettings(context: Context) {
    private val store = context.settingsDataStore

    val preferences: Flow<ShoppingPreferences> = store.data.map { it.toShoppingPreferences() }

    suspend fun current(): ShoppingPreferences = preferences.first()

    suspend fun setOnTick(value: TickBehavior) {
        store.edit { it[ON_TICK] = value.name }
    }

    suspend fun setMinStock(value: MinStockBehavior) {
        store.edit { it[MIN_STOCK] = value.name }
    }

    suspend fun setRunningLowDays(days: Int) {
        require(days > 0) { "days must be positive" }
        store.edit { it[RUNNING_LOW_DAYS] = days }
    }

    private fun Preferences.toShoppingPreferences() = ShoppingPreferences(
        onTick = this[ON_TICK].toEnum(TickBehavior.ASK),
        minStock = this[MIN_STOCK].toEnum(MinStockBehavior.AUTOMATIC),
        runningLowDays = this[RUNNING_LOW_DAYS]?.takeIf { it > 0 } ?: DisplayDefaults.RUNNING_LOW_DAYS,
    )

    private companion object {
        val ON_TICK = stringPreferencesKey("shopping_on_tick")
        val MIN_STOCK = stringPreferencesKey("shopping_min_stock")
        val RUNNING_LOW_DAYS = intPreferencesKey("running_low_days")
    }
}

// An unknown value (written by a newer version) falls back to the default
private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
    enumValues<T>().firstOrNull { it.name == this } ?: default
