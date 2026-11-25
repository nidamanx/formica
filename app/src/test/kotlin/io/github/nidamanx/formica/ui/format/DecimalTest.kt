// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DecimalTest {

    @Test
    fun `italian input uses the comma`() {
        assertEquals(1500L, parseThousandths("1,5", ','))
        assertEquals(250L, parseThousandths("0,25", ','))
        assertEquals(250L, parseThousandths(",25", ','))
        assertEquals(2000L, parseThousandths("2", ','))
        assertEquals(2000L, parseThousandths("2,", ','))
        assertEquals(1L, parseThousandths("0,001", ','))
        assertEquals(12000L, parseThousandths(" 12 ", ','))
    }

    @Test
    fun `english input uses the point`() {
        assertEquals(1500L, parseThousandths("1.5", '.'))
        assertNull(parseThousandths("1,5", '.'))
    }

    @Test
    fun `invalid input is refused`() {
        assertNull(parseThousandths("", ','))
        assertNull(parseThousandths(",", ','))
        assertNull(parseThousandths("1.5", ','))
        assertNull(parseThousandths("1,5,0", ','))
        assertNull(parseThousandths("-1", ','))
        assertNull(parseThousandths("1,2345", ','))
        assertNull(parseThousandths("abc", ','))
        assertNull(parseThousandths("1234567890", ','))
    }

    @Test
    fun `formatting drops useless zeros`() {
        assertEquals("1,5", formatThousandths(1500, ','))
        assertEquals("1.5", formatThousandths(1500, '.'))
        assertEquals("2", formatThousandths(2000, ','))
        assertEquals("0,25", formatThousandths(250, ','))
        assertEquals("0,001", formatThousandths(1, ','))
        assertEquals("0", formatThousandths(0, ','))
        assertEquals("-1,5", formatThousandths(-1500, ','))
    }

    @Test
    fun `parsing reads back what formatting writes`() {
        listOf(0L, 1L, 10L, 999L, 1000L, 1250L, 123_456_789L).forEach {
            assertEquals(it, parseThousandths(formatThousandths(it, ','), ','))
        }
    }

    @Test
    fun `prices are read as cents with at most two decimals`() {
        assertEquals(240L, parseCents("2,40", ','))
        assertEquals(240L, parseCents("2,4", ','))
        assertEquals(200L, parseCents("2", ','))
        assertEquals(5L, parseCents("0.05", '.'))
        assertNull(parseCents("2,405", ','))
        assertNull(parseCents("", ','))
        assertEquals("2,40", formatCentsInput(240, ','))
        assertEquals("2", formatCentsInput(200, ','))
        assertEquals("0.05", formatCentsInput(5, '.'))
    }
}
