// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.format

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import io.github.nidamanx.formica.R
import io.github.nidamanx.formica.domain.model.MeasureUnit
import io.github.nidamanx.formica.domain.model.ONE_UNIT
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

/** Number and date conventions of the user's language, read once per configuration. */
class DisplayFormat(val locale: Locale) {
    val decimalSeparator: Char = DecimalFormatSymbols.getInstance(locale).decimalSeparator

    // The short localized pattern, always with a four-digit year (25/11/2026, 11/25/2026)
    private val dateFormatter: DateTimeFormatter = DateTimeFormatterBuilder
        .getLocalizedDateTimePattern(FormatStyle.SHORT, null, IsoChronology.INSTANCE, locale)
        .let { if (it.contains("yyyy")) it else it.replace("yy", "yyyy") }
        .let { DateTimeFormatter.ofPattern(it, locale) }

    fun date(epochDay: Long): String = dateFormatter.format(LocalDate.ofEpochDay(epochDay))

    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)

    /** An instant in this phone's time zone: "23/11/2026 18:30". */
    fun dateTime(epochMillis: Long): String {
        val time = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
        return date(time.toLocalDate().toEpochDay()) + " " + timeFormatter.format(time)
    }

    fun number(thousandths: Long): String = formatThousandths(thousandths, decimalSeparator)

    fun parse(text: String): Long? = parseThousandths(text, decimalSeparator)

    // Euro only (SPEC §3): the symbol and its position follow the language
    private val priceFormat: NumberFormat = NumberFormat.getCurrencyInstance(locale).apply {
        currency = Currency.getInstance("EUR")
    }

    /** 240 → "2,40 €" in Italian, "€2.40" in English. */
    fun price(cents: Long): String = priceFormat.format(BigDecimal.valueOf(cents, 2))

    fun priceInput(cents: Long): String = formatCentsInput(cents, decimalSeparator)

    fun parsePrice(text: String): Long? = parseCents(text, decimalSeparator)
}

@Composable
fun rememberDisplayFormat(): DisplayFormat {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { DisplayFormat(locale) }
}

@StringRes
fun MeasureUnit.fullName(): Int = when (this) {
    MeasureUnit.PIECE -> R.string.unit_piece_name
    MeasureUnit.GRAM -> R.string.unit_gram_name
    MeasureUnit.KILOGRAM -> R.string.unit_kilogram_name
    MeasureUnit.MILLILITER -> R.string.unit_milliliter_name
    MeasureUnit.LITER -> R.string.unit_liter_name
    MeasureUnit.PACKAGE -> R.string.unit_package_name
    MeasureUnit.JAR -> R.string.unit_jar_name
    MeasureUnit.BOTTLE -> R.string.unit_bottle_name
}

/** "12 bottiglie", "1,5 kg", "1 vasetto": quantity and short unit. */
fun Resources.quantityText(thousandths: Long, unit: MeasureUnit, format: DisplayFormat): String {
    val number = format.number(thousandths)
    // Plural forms need an integer: a fractional quantity takes the "other" form
    val count = if (thousandths % ONE_UNIT == 0L) (thousandths / ONE_UNIT).toInt() else 2
    val label = when (unit) {
        MeasureUnit.PIECE -> getString(R.string.unit_piece_short)
        MeasureUnit.GRAM -> getString(R.string.unit_gram_short)
        MeasureUnit.KILOGRAM -> getString(R.string.unit_kilogram_short)
        MeasureUnit.MILLILITER -> getString(R.string.unit_milliliter_short)
        MeasureUnit.LITER -> getString(R.string.unit_liter_short)
        MeasureUnit.PACKAGE -> getQuantityString(R.plurals.unit_package, count)
        MeasureUnit.JAR -> getQuantityString(R.plurals.unit_jar, count)
        MeasureUnit.BOTTLE -> getQuantityString(R.plurals.unit_bottle, count)
    }
    return "$number $label"
}

/** "preparata 3 mesi fa": days up to 59, then months up to 23, then years. */
fun Resources.preparedAgo(preparedOn: Long, today: Long): String {
    val days = (today - preparedOn).coerceAtLeast(0).toInt()
    val months = days * 12 / 365
    return when {
        days == 0 -> getString(R.string.made_today)
        days < 60 -> getQuantityString(R.plurals.made_days_ago, days, days)
        months < 24 -> getQuantityString(R.plurals.made_months_ago, months, months)
        else -> (months / 12).let { getQuantityString(R.plurals.made_years_ago, it, it) }
    }
}
