// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.domain

private val SPACES = Regex("\\s+")

/** Name as compared for duplicates: trimmed, inner spaces collapsed, lowercase. */
fun normalizedName(name: String): String = name.trim().replace(SPACES, " ").lowercase()

/** Name as stored: trimmed, inner spaces collapsed, case kept. */
fun cleanName(name: String): String = name.trim().replace(SPACES, " ")
