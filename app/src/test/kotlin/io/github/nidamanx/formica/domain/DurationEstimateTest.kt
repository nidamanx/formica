// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.domain

import io.github.nidamanx.formica.domain.DurationEstimate.Days
import io.github.nidamanx.formica.domain.DurationEstimate.InsufficientData
import io.github.nidamanx.formica.domain.DurationEstimate.Months
import io.github.nidamanx.formica.domain.DurationEstimate.MoreThanAYear
import io.github.nidamanx.formica.domain.DurationEstimate.Weeks
import io.github.nidamanx.formica.domain.model.MovementType.CONSUMPTION
import io.github.nidamanx.formica.domain.model.MovementType.CORRECTION
import io.github.nidamanx.formica.domain.model.MovementType.DISCARD
import io.github.nidamanx.formica.domain.model.MovementType.PURCHASE
import io.github.nidamanx.formica.domain.model.MovementType.TRANSFER
import org.junit.Assert.assertEquals
import org.junit.Test

class DurationEstimateTest {

    private fun daysAgo(days: Long) = NOW - days * DAY

    @Test
    fun `no movements means insufficient data`() {
        assertEquals(InsufficientData, estimateDuration(emptyList(), units(5), NOW))
    }

    @Test
    fun `fewer than three consumptions are not enough`() {
        val movements = listOf(
            movement("b", units(10), PURCHASE, daysAgo(40)),
            movement("b", -units(1), CONSUMPTION, daysAgo(30)),
            movement("b", -units(1), CONSUMPTION, daysAgo(1)),
        )
        assertEquals(InsufficientData, estimateDuration(movements, units(8), NOW))
    }

    @Test
    fun `consumptions within less than fourteen days are not enough`() {
        val movements = listOf(
            movement("b", units(10), PURCHASE, daysAgo(40)),
            movement("b", -units(1), CONSUMPTION, daysAgo(13)),
            movement("b", -units(1), CONSUMPTION, daysAgo(5)),
            movement("b", -units(1), CONSUMPTION, daysAgo(1)),
        )
        assertEquals(InsufficientData, estimateDuration(movements, units(7), NOW))
    }

    @Test
    fun `the period starts at the first movement when it is recent`() {
        // 3 units in 30 days = 0.1 a day; 1 unit left = 10 days
        val movements = listOf(
            movement("b", units(4), PURCHASE, daysAgo(30)),
            movement("b", -units(1), CONSUMPTION, daysAgo(20)),
            movement("b", -units(1), CONSUMPTION, daysAgo(10)),
            movement("b", -units(1), CONSUMPTION, daysAgo(2)),
        )
        assertEquals(Days(10), estimateDuration(movements, units(1), NOW))
    }

    @Test
    fun `only the last ninety days of consumption count`() {
        // 9 units in the last 90 days = 0.1 a day; 3 units left = 30 days = about 4 weeks
        val movements = listOf(
            movement("b", units(50), PURCHASE, daysAgo(200)),
            movement("b", -units(30), CONSUMPTION, daysAgo(120)),
            movement("b", -units(3), CONSUMPTION, daysAgo(80)),
            movement("b", -units(3), CONSUMPTION, daysAgo(50)),
            movement("b", -units(3), CONSUMPTION, daysAgo(10)),
        )
        assertEquals(Weeks(4), estimateDuration(movements, units(3), NOW))
    }

    @Test
    fun `discards, corrections and transfers are not consumption`() {
        val movements = listOf(
            movement("b", units(10), PURCHASE, daysAgo(30)),
            movement("b", -units(1), CONSUMPTION, daysAgo(20)),
            movement("b", -units(1), CONSUMPTION, daysAgo(10)),
            movement("b", -units(1), CONSUMPTION, daysAgo(2)),
            movement("b", -units(5), DISCARD, daysAgo(3)),
            movement("b", -units(5), CORRECTION, daysAgo(4)),
            movement("b", -units(5), TRANSFER, daysAgo(5)),
        )
        assertEquals(Days(10), estimateDuration(movements, units(1), NOW))
    }

    @Test
    fun `nothing left lasts zero days`() {
        val movements = listOf(
            movement("b", units(3), PURCHASE, daysAgo(30)),
            movement("b", -units(1), CONSUMPTION, daysAgo(20)),
            movement("b", -units(1), CONSUMPTION, daysAgo(10)),
            movement("b", -units(1), CONSUMPTION, daysAgo(2)),
        )
        assertEquals(Days(0), estimateDuration(movements, 0L, NOW))
    }

    @Test
    fun `rounding follows days, weeks, months and more than a year`() {
        assertEquals(Days(0), roundDuration(0.2))
        assertEquals(Days(13), roundDuration(13.4))
        assertEquals(Weeks(2), roundDuration(13.6))
        assertEquals(Weeks(4), roundDuration(30.0))
        assertEquals(Weeks(8), roundDuration(55.0))
        assertEquals(Months(2), roundDuration(56.0))
        assertEquals(Months(6), roundDuration(183.0))
        assertEquals(Months(12), roundDuration(364.0))
        assertEquals(MoreThanAYear, roundDuration(365.0))
        assertEquals(MoreThanAYear, roundDuration(5000.0))
    }
}
