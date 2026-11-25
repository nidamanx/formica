// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class NamesTest {

    @Test
    fun `names are compared without case and extra spaces`() {
        assertEquals("latte intero", normalizedName("  Latte   Intero "))
        assertEquals(normalizedName("LATTE"), normalizedName("latte"))
    }

    @Test
    fun `stored names keep their case`() {
        assertEquals("Latte Intero", cleanName("  Latte \t Intero "))
    }
}
