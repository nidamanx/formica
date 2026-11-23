// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.domain

import java.time.Duration
import java.time.ZonedDateTime

/**
 * When to run the daily check (SPEC §9).
 *
 * @property firstDelay from now to the next chosen time, strictly in the future: the start of
 *   the daily periodic work
 * @property runNow today's check was missed (the chosen time has passed and it has not run
 *   today): run it once now, without moving the daily time
 */
data class DailyPlan(val firstDelay: Duration, val runNow: Boolean)

/**
 * @param minuteOfDay the chosen local time, in minutes after midnight
 * @param lastCheckDay the epoch day of the last completed check, null if it never ran
 */
fun dailyPlan(now: ZonedDateTime, minuteOfDay: Int, lastCheckDay: Long?): DailyPlan {
    val today = now.toLocalDate()
    // atZone picks the valid instant when a daylight saving change skips or repeats the time
    val todayAt = today.atTime(minuteOfDay / 60, minuteOfDay % 60).atZone(now.zone)
    val next = if (now.isBefore(todayAt)) todayAt else todayAt.toLocalDateTime().plusDays(1).atZone(now.zone)
    val missed = !now.isBefore(todayAt) && (lastCheckDay == null || lastCheckDay < today.toEpochDay())
    return DailyPlan(Duration.between(now, next), missed)
}
