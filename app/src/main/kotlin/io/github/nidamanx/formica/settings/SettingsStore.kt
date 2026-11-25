// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.settings

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

/** The one preferences file of the app: local, never synced, excluded from backups. */
internal val Context.settingsDataStore by preferencesDataStore(name = "settings")
