// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val MINUTES_PER_DAY = 24 * 60
private const val DEFAULT_TIME = 9 * 60

data class NotificationPreferences(
    val enabled: Boolean = true,
    /** Time of the daily notification, in minutes after midnight (local time). */
    val minuteOfDay: Int = DEFAULT_TIME,
    val warningDays: Int = DisplayDefaults.EXPIRY_WARNING_DAYS,
)

/** Daily notification settings (SPEC §9): on by default, 9:00, 3 days of warning. */
class NotificationSettings(context: Context) {
    private val store = context.settingsDataStore

    val preferences: Flow<NotificationPreferences> = store.data
        .map { it.toNotificationPreferences() }
        .distinctUntilChanged()

    suspend fun current(): NotificationPreferences = preferences.first()

    suspend fun setEnabled(value: Boolean) {
        store.edit { it[ENABLED] = value }
    }

    suspend fun setMinuteOfDay(minute: Int) {
        require(minute in 0 until MINUTES_PER_DAY) { "minute out of range" }
        store.edit { it[MINUTE_OF_DAY] = minute }
    }

    suspend fun setWarningDays(days: Int) {
        require(days > 0) { "days must be positive" }
        store.edit { it[WARNING_DAYS] = days }
    }

    /** Epoch day of the last completed daily check, null if it never ran. */
    suspend fun lastCheckDay(): Long? = store.data.first()[LAST_CHECK_DAY]

    suspend fun setLastCheckDay(day: Long) {
        store.edit { it[LAST_CHECK_DAY] = day }
    }

    private fun Preferences.toNotificationPreferences() = NotificationPreferences(
        enabled = this[ENABLED] ?: true,
        minuteOfDay = this[MINUTE_OF_DAY]?.takeIf { it in 0 until MINUTES_PER_DAY } ?: DEFAULT_TIME,
        warningDays = this[WARNING_DAYS]?.takeIf { it > 0 } ?: DisplayDefaults.EXPIRY_WARNING_DAYS,
    )

    private companion object {
        val ENABLED = booleanPreferencesKey("notification_enabled")
        val MINUTE_OF_DAY = intPreferencesKey("notification_minute_of_day")
        val WARNING_DAYS = intPreferencesKey("expiry_warning_days")
        val LAST_CHECK_DAY = longPreferencesKey("notification_last_check_day")
    }
}
