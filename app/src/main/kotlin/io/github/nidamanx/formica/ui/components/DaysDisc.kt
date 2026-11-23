// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.nidamanx.formica.R

private const val DAYS_SHOWN_AS_DAYS = 60

enum class DiscTone { EXPIRED, EXPIRING, PREPARING, NEUTRAL }

/**
 * Days left to a date ("3 days", "today", "2 months"), or "expired" with [DiscTone.EXPIRED];
 * "–" without a date. TalkBack reads [statusText] instead of the visible text.
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
    Surface(
        shape = CircleShape,
        color = background,
        contentColor = foreground,
        modifier = Modifier
            .size(52.dp)
            .clearAndSetSemantics { if (statusText.isNotEmpty()) contentDescription = statusText },
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
