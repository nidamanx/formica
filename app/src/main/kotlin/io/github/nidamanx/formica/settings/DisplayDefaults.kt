// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.settings

import io.github.nidamanx.formica.domain.ListThresholds

/**
 * Values that will become user settings (SPEC §3, local settings); fixed until those
 * settings exist. The duration estimate stays off in the list by default (owner, 2026-11-23).
 * RUNNING_LOW_DAYS is only the default of [ShoppingSettings].
 */
object DisplayDefaults {
    const val EXPIRY_WARNING_DAYS = 3
    const val RUNNING_LOW_DAYS = 7
    const val SHOW_ESTIMATE_IN_LIST = false

    fun thresholds(runningLowDays: Int) = ListThresholds(EXPIRY_WARNING_DAYS, runningLowDays)
}
