// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.domain

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyScheduleTest {

    private val rome = ZoneId.of("Europe/Rome")
    private val nine = 9 * 60

    private fun at(text: String): ZonedDateTime = LocalDateTime.parse(text).atZone(rome)

    private fun day(text: String): Long = LocalDateTime.parse(text).toLocalDate().toEpochDay()

    @Test
    fun `before the chosen time the first run is today`() {
        val plan = dailyPlan(at("2026-11-23T07:30"), nine, lastCheckDay = day("2026-11-22T09:00"))
        assertEquals(DailyPlan(Duration.ofMinutes(90), runNow = false), plan)
    }

    @Test
    fun `after today's check the first run is tomorrow`() {
        val plan = dailyPlan(at("2026-11-23T15:00"), nine, lastCheckDay = day("2026-11-23T09:00"))
        assertEquals(DailyPlan(Duration.ofHours(18), runNow = false), plan)
    }

    @Test
    fun `a missed check runs now and the daily time stays the same`() {
        val plan = dailyPlan(at("2026-11-23T15:00"), nine, lastCheckDay = day("2026-11-21T09:00"))
        assertEquals(DailyPlan(Duration.ofHours(18), runNow = true), plan)
        assertEquals(true, dailyPlan(at("2026-11-23T09:00"), nine, lastCheckDay = null).runNow)
    }

    @Test
    fun `the first run follows daylight saving changes`() {
        // 2027-03-28: clocks go from 2:00 to 3:00 in Rome, the night is 23 hours long
        val spring = dailyPlan(at("2027-03-27T21:00"), nine, lastCheckDay = day("2027-03-27T09:00"))
        assertEquals(Duration.ofHours(11), spring.firstDelay)
        // 2026-10-25: clocks go from 3:00 back to 2:00, the night is 25 hours long
        val autumn = dailyPlan(at("2026-10-24T21:00"), nine, lastCheckDay = day("2026-10-24T09:00"))
        assertEquals(Duration.ofHours(13), autumn.firstDelay)
    }
}
