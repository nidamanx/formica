// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.format

import io.github.nidamanx.formica.domain.model.ONE_UNIT

/*
 * Quantities typed and shown with the decimal separator of the user's language
 * ("1,5" in Italian, "1.5" in English), stored as thousandths: never through Float or Double.
 */

private const val MAX_INTEGER_DIGITS = 9
private const val MAX_DECIMALS = 3

/**
 * Parses a non-negative quantity typed by the user. Only [decimalSeparator] is accepted
 * as separator, so "1.500" in Italian is refused instead of being read as 1.5.
 *
 * @return thousandths, or null if the text is not a valid quantity
 */
fun parseThousandths(text: String, decimalSeparator: Char): Long? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val parts = trimmed.split(decimalSeparator)
    if (parts.size > 2) return null
    val integer = parts[0]
    val fraction = parts.getOrElse(1) { "" }
    if (integer.isEmpty() && fraction.isEmpty()) return null
    if (!integer.all { it in '0'..'9' } || !fraction.all { it in '0'..'9' }) return null
    if (integer.length > MAX_INTEGER_DIGITS || fraction.length > MAX_DECIMALS) return null
    val whole = if (integer.isEmpty()) 0L else integer.toLong()
    val thousandths = if (fraction.isEmpty()) 0L else fraction.padEnd(MAX_DECIMALS, '0').toLong()
    return whole * ONE_UNIT + thousandths
}

/** Formats thousandths without useless zeros: 1500 → "1,5", 2000 → "2", 250 → "0,25". */
fun formatThousandths(value: Long, decimalSeparator: Char): String {
    val sign = if (value < 0) "-" else ""
    val abs = Math.abs(value)
    val whole = abs / ONE_UNIT
    val fraction = abs % ONE_UNIT
    if (fraction == 0L) return "$sign$whole"
    val digits = fraction.toString().padStart(MAX_DECIMALS, '0').trimEnd('0')
    return "$sign$whole$decimalSeparator$digits"
}

private const val THOUSANDTHS_PER_CENT = 10L

/** Parses a price in euro typed by the user ("2,40", "2"): cents, or null with more than 2 decimals. */
fun parseCents(text: String, decimalSeparator: Char): Long? =
    parseThousandths(text, decimalSeparator)
        ?.takeIf { it % THOUSANDTHS_PER_CENT == 0L }
        ?.let { it / THOUSANDTHS_PER_CENT }

/** A price for an input field: 240 → "2,40", 200 → "2". */
fun formatCentsInput(cents: Long, decimalSeparator: Char): String {
    if (cents % 100 == 0L) return (cents / 100).toString()
    return "${cents / 100}$decimalSeparator${(cents % 100).toString().padStart(2, '0')}"
}
