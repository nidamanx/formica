// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.nidamanx.formica.R
import kotlin.math.ceil
import kotlin.math.hypot

private const val DAYS_SHOWN_AS_DAYS = 60
private val MIN_DISC_SIZE = 52.dp

enum class DiscTone { EXPIRED, EXPIRING, PREPARING, NEUTRAL }

/**
 * Days left to a date ("3 days", "today", "2 months"), or "expired" with [DiscTone.EXPIRED];
 * "–" without a date. TalkBack reads [statusText] followed by the visible text.
 */
@Composable
fun DaysDisc(days: Long?, tone: DiscTone, statusText: String) {
    val colors = MaterialTheme.colorScheme
    val resources = LocalResources.current
    val (background, foreground) = when (tone) {
        DiscTone.EXPIRED -> colors.errorContainer to colors.onErrorContainer
        DiscTone.EXPIRING -> colors.tertiaryContainer to colors.onTertiaryContainer
        DiscTone.PREPARING -> colors.secondaryContainer to colors.onSecondaryContainer
        DiscTone.NEUTRAL -> colors.surfaceContainerHighest to colors.onSurfaceVariant
    }
    val (number, word) = when {
        tone == DiscTone.EXPIRED -> "" to resources.getString(R.string.disc_expired)
        days == null -> "–" to ""
        days <= 0L -> "" to resources.getString(R.string.disc_today)
        days < DAYS_SHOWN_AS_DAYS -> days.toString() to resources.getQuantityString(R.plurals.disc_days, days.toInt())
        else -> (days * 12 / 365).let { months ->
            months.toString() to resources.getQuantityString(R.plurals.disc_months, months.toInt())
        }
    }
    // "Expiring, 3 days"; the status alone when the disc says the same ("expired")
    val spoken = listOf(statusText, listOf(number, word).filter { it.isNotEmpty() && it != "–" }.joinToString(" "))
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
        .joinToString(", ")
    Surface(
        shape = CircleShape,
        color = background,
        contentColor = foreground,
        modifier = Modifier
            .circleAround(MIN_DISC_SIZE)
            .clearAndSetSemantics { if (spoken.isNotEmpty()) contentDescription = spoken },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (number.isNotEmpty()) {
                Text(number, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (word.isNotEmpty()) {
                Text(word, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

/**
 * A square of at least [min] that grows with its content, for a circle that keeps the text
 * inside with large font sizes. The side is between the content's longer side and its
 * diagonal: text lines are narrower at the top and bottom than a full rectangle.
 */
private fun Modifier.circleAround(min: Dp) = layout { measurable, _ ->
    val width = measurable.maxIntrinsicWidth(Constraints.Infinity)
    val height = measurable.maxIntrinsicHeight(width)
    val content = (maxOf(width, height) + hypot(width.toFloat(), height.toFloat())) / 2
    val side = maxOf(min.roundToPx(), ceil(content).toInt())
    val placeable = measurable.measure(Constraints.fixed(side, side))
    layout(side, side) { placeable.place(0, 0) }
}
