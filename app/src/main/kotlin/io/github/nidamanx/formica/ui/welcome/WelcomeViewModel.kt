// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nidamanx.formica.domain.cleanName
import io.github.nidamanx.formica.settings.DeviceSettings
import io.github.nidamanx.formica.settings.NotificationSettings
import io.github.nidamanx.formica.ui.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the welcome screen proposes: the device's current name and notification setting. */
data class WelcomeProposal(val deviceName: String, val notificationsEnabled: Boolean, val minuteOfDay: Int)

class WelcomeViewModel(
    private val deviceSettings: DeviceSettings,
    private val notificationSettings: NotificationSettings,
) : ViewModel() {

    private val _proposal = MutableStateFlow<WelcomeProposal?>(null)
    val proposal: StateFlow<WelcomeProposal?> = _proposal.asStateFlow()

    init {
        viewModelScope.launch {
            val notifications = notificationSettings.current()
            _proposal.value = WelcomeProposal(
                deviceName = deviceSettings.device().name,
                notificationsEnabled = notifications.enabled,
                minuteOfDay = notifications.minuteOfDay,
            )
        }
    }

    fun onPermissionAsked() {
        viewModelScope.launch { notificationSettings.setPermissionAsked() }
    }

    /** Saves the choices and leaves the welcome screen; an empty name keeps the current one. */
    fun finish(deviceName: String, notificationsEnabled: Boolean) {
        viewModelScope.launch {
            val clean = cleanName(deviceName)
            if (clean.isNotEmpty()) deviceSettings.rename(clean)
            notificationSettings.setEnabled(notificationsEnabled)
            deviceSettings.finishFirstRun()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                WelcomeViewModel(container.deviceSettings, container.notificationSettings)
            }
        }
    }
}
