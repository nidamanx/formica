// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nidamanx.formica.domain.MinStockBehavior
import io.github.nidamanx.formica.domain.TickBehavior
import io.github.nidamanx.formica.domain.cleanName
import io.github.nidamanx.formica.settings.DeviceSettings
import io.github.nidamanx.formica.settings.NotificationPreferences
import io.github.nidamanx.formica.settings.NotificationSettings
import io.github.nidamanx.formica.settings.ShoppingPreferences
import io.github.nidamanx.formica.settings.ShoppingSettings
import io.github.nidamanx.formica.ui.appContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val MAX_DAYS_DIGITS = 4

class SettingsViewModel(
    private val deviceSettings: DeviceSettings,
    private val shoppingSettings: ShoppingSettings,
    private val notificationSettings: NotificationSettings,
) : ViewModel() {

    val notifications: StateFlow<NotificationPreferences?> = notificationSettings.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setNotificationsEnabled(value: Boolean) {
        viewModelScope.launch { notificationSettings.setEnabled(value) }
    }

    fun onPermissionAsked() {
        viewModelScope.launch { notificationSettings.setPermissionAsked() }
    }

    fun setNotificationTime(hour: Int, minute: Int) {
        viewModelScope.launch { notificationSettings.setMinuteOfDay(hour * 60 + minute) }
    }

    /** @return false if [text] is not a number of days from 1 to 9999 (nothing is saved) */
    fun setWarningDays(text: String): Boolean {
        val days = parseDays(text) ?: return false
        viewModelScope.launch { notificationSettings.setWarningDays(days) }
        return true
    }

    val shopping: StateFlow<ShoppingPreferences?> = shoppingSettings.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setOnTick(value: TickBehavior) {
        viewModelScope.launch { shoppingSettings.setOnTick(value) }
    }

    fun setMinStock(value: MinStockBehavior) {
        viewModelScope.launch { shoppingSettings.setMinStock(value) }
    }

    /** @return false if [text] is not a number of days from 1 to 9999 (nothing is saved) */
    fun setRunningLowDays(text: String): Boolean {
        val days = parseDays(text) ?: return false
        viewModelScope.launch { shoppingSettings.setRunningLowDays(days) }
        return true
    }

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

    private fun parseDays(text: String): Int? = text.trim()
        .takeIf { it.length in 1..MAX_DAYS_DIGITS && it.all(Char::isDigit) }
        ?.toInt()
        ?.takeIf { it > 0 }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                SettingsViewModel(c.deviceSettings, c.shoppingSettings, c.notificationSettings)
            }
        }
    }
}
