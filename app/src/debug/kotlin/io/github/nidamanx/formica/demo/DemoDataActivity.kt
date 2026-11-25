// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.demo

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.github.nidamanx.formica.FormicaApplication
import io.github.nidamanx.formica.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * Debug builds only: loads the sample data ([DemoData]) into an app without products, then
 * closes. Started with adb by the emulator smoke test, for screenshots:
 * `adb shell am start -n io.github.nidamanx.formica.dev/io.github.nidamanx.formica.demo.DemoDataActivity`
 */
class DemoDataActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as FormicaApplication).container
        lifecycleScope.launch {
            container.initialize()
            val current = container.exchange.snapshot()
            if (current.products.any { it.deletedAt == null }) {
                Log.i(TAG, "Sample data not loaded: the app already has products")
            } else {
                val zone = ZoneId.systemDefault()
                val data = DemoData(
                    today = LocalDate.now().toEpochDay(),
                    deviceId = container.deviceSettings.device().id,
                    newId = { UUID.randomUUID().toString() },
                    millisAt = { day -> LocalDate.ofEpochDay(day).atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli() },
                    locations = current.locations.filter { it.deletedAt == null }.sortedBy { it.sortOrder },
                    categories = current.categories.filter { it.deletedAt == null }.sortedBy { it.sortOrder },
                    text = { getString(it.resource) },
                ).build()
                container.exchange.apply(data, replace = false, record = null, review = emptySet())
                Log.i(TAG, "Sample data loaded: ${data.products.size} products")
            }
            finish()
        }
    }

    private companion object {
        const val TAG = "FormicaDemo"
    }
}

private val DemoText.resource: Int
    get() = when (this) {
        DemoText.STORE_SUPERMARKET -> R.string.demo_store_supermarket
        DemoText.STORE_MARKET -> R.string.demo_store_market
        DemoText.STORE_BAKERY -> R.string.demo_store_bakery
        DemoText.STORE_FARM -> R.string.demo_store_farm
        DemoText.STORE_OIL_MILL -> R.string.demo_store_oil_mill
        DemoText.MILK -> R.string.demo_milk
        DemoText.YOGURT -> R.string.demo_yogurt
        DemoText.EGGS -> R.string.demo_eggs
        DemoText.RICOTTA -> R.string.demo_ricotta
        DemoText.SPINACH -> R.string.demo_spinach
        DemoText.RAGU -> R.string.demo_ragu
        DemoText.PEAS -> R.string.demo_peas
        DemoText.PASSATA -> R.string.demo_passata
        DemoText.APRICOT_JAM -> R.string.demo_apricot_jam
        DemoText.HONEY -> R.string.demo_honey
        DemoText.OLIVE_OIL -> R.string.demo_olive_oil
        DemoText.WHOLEMEAL_FLOUR -> R.string.demo_wholemeal_flour
        DemoText.BROWN_RICE -> R.string.demo_brown_rice
        DemoText.LENTILS -> R.string.demo_lentils
        DemoText.SAUERKRAUT -> R.string.demo_sauerkraut
        DemoText.POTATOES -> R.string.demo_potatoes
        DemoText.APPLES -> R.string.demo_apples
        DemoText.WHOLEMEAL_BREAD -> R.string.demo_wholemeal_bread
        DemoText.LEMONS -> R.string.demo_lemons
        DemoText.TOMATOES -> R.string.demo_tomatoes
        DemoText.PRICE_EACH -> R.string.demo_price_each
        DemoText.PRICE_PER_LITRE -> R.string.demo_price_per_litre
        DemoText.NOTE_RAGU -> R.string.demo_note_ragu
        DemoText.NOTE_OIL -> R.string.demo_note_oil
    }
