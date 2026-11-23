// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nidamanx.formica.domain.cleanName
import io.github.nidamanx.formica.settings.DeviceSettings
import io.github.nidamanx.formica.ui.appContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val deviceSettings: DeviceSettings) : ViewModel() {

    val deviceName: StateFlow<String?> = deviceSettings.device
        .map { it?.name }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** @return false if the name is empty (nothing is saved) */
    fun rename(name: String): Boolean {
        val clean = cleanName(name)
        if (clean.isEmpty()) return false
        viewModelScope.launch { deviceSettings.rename(clean) }
        return true
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { SettingsViewModel(appContainer().deviceSettings) }
        }
    }
}
