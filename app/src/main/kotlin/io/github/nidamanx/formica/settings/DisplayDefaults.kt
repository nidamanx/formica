// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.settings

/**
 * Defaults of the display settings (SPEC §3, local settings). EXPIRY_WARNING_DAYS is the
 * default of [NotificationSettings], RUNNING_LOW_DAYS of [ShoppingSettings]. The duration
 * estimate stays off in the list (owner, 2026-11-23) until it becomes a setting.
 */
object DisplayDefaults {
    const val EXPIRY_WARNING_DAYS = 3
    const val RUNNING_LOW_DAYS = 7
    const val SHOW_ESTIMATE_IN_LIST = false
}
