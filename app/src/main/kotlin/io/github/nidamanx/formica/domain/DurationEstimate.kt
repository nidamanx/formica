// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.domain

import io.github.nidamanx.formica.domain.model.Movement
import io.github.nidamanx.formica.domain.model.MovementType
import kotlin.math.abs
import kotlin.math.roundToLong

/** How long the current stock of a product will last, rounded as shown in the UI. */
sealed interface DurationEstimate {
    data object InsufficientData : DurationEstimate
    data class Days(val count: Long) : DurationEstimate
    data class Weeks(val count: Long) : DurationEstimate
    data class Months(val count: Long) : DurationEstimate
    data object MoreThanAYear : DurationEstimate
}

private const val DAY_MILLIS = 86_400_000L
private const val WINDOW_DAYS = 90L
private const val MIN_CONSUMPTIONS = 3
private const val MIN_SPREAD_DAYS = 14L
private const val DAYS_PER_MONTH = 365.25 / 12

/**
 * Average daily consumption over the last 90 days (or since the product's first movement,
 * if more recent); only CONSUMPTION counts. Needs at least 3 consumptions spread over at
 * least 14 days.
 *
 * @param productMovements every movement of the product's batches
 * @param quantity current total of the product, thousandths
 * @param now epoch millis
 */
fun estimateDuration(productMovements: List<Movement>, quantity: Long, now: Long): DurationEstimate {
    val firstMovement = productMovements.minOfOrNull { it.timestamp } ?: return DurationEstimate.InsufficientData
    val periodStart = maxOf(now - WINDOW_DAYS * DAY_MILLIS, firstMovement)
    val consumptions = productMovements.filter {
        it.type == MovementType.CONSUMPTION && it.timestamp >= periodStart && it.timestamp <= now
    }
    if (consumptions.size < MIN_CONSUMPTIONS) return DurationEstimate.InsufficientData
    val spread = consumptions.maxOf { it.timestamp } - consumptions.minOf { it.timestamp }
    if (spread < MIN_SPREAD_DAYS * DAY_MILLIS) return DurationEstimate.InsufficientData

    val periodDays = (now - periodStart).toDouble() / DAY_MILLIS
    val dailyConsumption = consumptions.sumOf { abs(it.delta) } / periodDays
    if (dailyConsumption <= 0.0) return DurationEstimate.InsufficientData
    if (quantity <= 0) return DurationEstimate.Days(0)
    return roundDuration(quantity / dailyConsumption)
}

/** < 14 days → days; < 8 weeks → weeks; < 12 months → months; then "more than a year". */
fun roundDuration(days: Double): DurationEstimate {
    val wholeDays = days.roundToLong()
    return when {
        wholeDays < 14 -> DurationEstimate.Days(wholeDays)
        wholeDays < 56 -> DurationEstimate.Weeks((wholeDays / 7.0).roundToLong())
        wholeDays < 365 -> DurationEstimate.Months((wholeDays / DAYS_PER_MONTH).roundToLong().coerceIn(2L, 12L))
        else -> DurationEstimate.MoreThanAYear
    }
}
