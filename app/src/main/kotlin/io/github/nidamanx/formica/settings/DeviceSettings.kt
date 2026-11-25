// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.nidamanx.formica.domain.model.Device
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Identity of this phone: an id generated once (written into modifiedBy and the
 * movements, never reused) and a name shown to the other devices after an exchange.
 */
class DeviceSettings(context: Context, private val defaultName: String) {
    private val store = context.settingsDataStore

    val device: Flow<Device?> = store.data.map { it.toDevice() }

    @Volatile
    private var cached: Device? = null

    /** The device, created on first use with a new id and the default name. */
    suspend fun device(): Device = cached ?: store.edit { prefs ->
        if (prefs[ID] == null) {
            prefs[ID] = UUID.randomUUID().toString()
            prefs[NAME] = defaultName
        }
    }.toDevice()!!.also { cached = it }

    /** The welcome screen has been completed (SPEC §12, first start). */
    val firstRunDone: Flow<Boolean> = store.data.map { it[FIRST_RUN_DONE] ?: false }

    suspend fun finishFirstRun() {
        store.edit { it[FIRST_RUN_DONE] = true }
    }

    suspend fun rename(name: String) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "empty device name" }
        device()
        cached = store.edit { it[NAME] = trimmed }.toDevice()
    }

    private fun Preferences.toDevice(): Device? {
        val id = this[ID] ?: return null
        return Device(id, this[NAME] ?: defaultName)
    }

    private companion object {
        val ID = stringPreferencesKey("device_id")
        val NAME = stringPreferencesKey("device_name")
        val FIRST_RUN_DONE = booleanPreferencesKey("first_run_done")
    }
}
