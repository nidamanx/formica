// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.expiries

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nidamanx.formica.R
import io.github.nidamanx.formica.domain.ExpiryEntry
import io.github.nidamanx.formica.domain.ExpirySection
import io.github.nidamanx.formica.ui.components.DaysDisc
import io.github.nidamanx.formica.ui.components.DiscTone
import io.github.nidamanx.formica.ui.components.NotificationAccess
import io.github.nidamanx.formica.ui.components.NotificationBanner
import io.github.nidamanx.formica.ui.components.rememberNotificationAccess
import io.github.nidamanx.formica.ui.components.SectionTopBar
import io.github.nidamanx.formica.ui.format.DisplayFormat
import io.github.nidamanx.formica.ui.format.quantityText
import io.github.nidamanx.formica.ui.format.rememberDisplayFormat

@Composable
fun ExpiriesScreen(
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenProduct: (String) -> Unit,
    viewModel: ExpiriesViewModel = viewModel(factory = ExpiriesViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val format = rememberDisplayFormat()
    var laterOpen by rememberSaveable { mutableStateOf(false) }
    val access = rememberNotificationAccess(
        asked = notifications?.permissionAsked ?: true,
        onAsked = viewModel::onPermissionAsked,
    )
    val showBanner = notifications?.enabled == true && access.access != NotificationAccess.GRANTED

    Scaffold(topBar = { SectionTopBar(stringResource(R.string.nav_expiries), onOpenSettings, onOpenAbout) }) { padding ->
        Column(Modifier.padding(padding)) {
            if (showBanner) {
                NotificationBanner(
                    access = access.access,
                    onEnable = access.resolve,
                    onDismiss = viewModel::declineNotifications,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                )
            }
            ExpiryList(state, format, laterOpen, { laterOpen = !laterOpen }, onOpenProduct)
        }
    }
}

@Composable
private fun ExpiryList(
    state: ExpiriesUiState,
    format: DisplayFormat,
    laterOpen: Boolean,
    onToggleLater: () -> Unit,
    onOpenProduct: (String) -> Unit,
) {
    val overview = state.overview
    when {
        !state.loaded -> Unit
        overview.isEmpty -> Box(
            Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.placeholder_expiries), textAlign = TextAlign.Center)
        }
        else -> LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val card: @Composable (ExpiryEntry) -> Unit = { entry ->
                ExpiryCard(entry, state.today, state.locationNames, format) { onOpenProduct(entry.product.id) }
            }
            section(R.string.expiries_expired, overview.expired, card, error = true)
            section(R.string.expiries_expiring, overview.expiring, card)
            section(R.string.expiries_preparing, overview.preparing, card)
            if (overview.later.isNotEmpty()) {
                item(key = "later-toggle") {
                    LaterToggle(overview.later.size, laterOpen, onToggleLater)
                }
                if (laterOpen) entries(overview.later, card)
            }
        }
    }
}

private fun LazyListScope.section(
    @StringRes title: Int,
    list: List<ExpiryEntry>,
    card: @Composable (ExpiryEntry) -> Unit,
    error: Boolean = false,
) {
    if (list.isEmpty()) return
    item(key = "header-$title") {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        )
    }
    entries(list, card)
}

private fun LazyListScope.entries(list: List<ExpiryEntry>, card: @Composable (ExpiryEntry) -> Unit) {
    items(list, key = { "${it.section}-${it.group.batches.first().id}" }) { card(it) }
}

@Composable
private fun LaterToggle(count: Int, open: Boolean, onToggle: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clickable(
                onClickLabel = stringResource(if (open) R.string.action_hide else R.string.action_show),
                onClick = onToggle,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                pluralStringResource(R.plurals.expiries_later, count, count),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun ExpiryCard(
    entry: ExpiryEntry,
    today: Long,
    locationNames: Map<String, String>,
    format: DisplayFormat,
    onClick: () -> Unit,
) {
    val resources = LocalResources.current
    val product = entry.product
    val key = entry.group.key
    val preparing = entry.section == ExpirySection.PREPARING
    val dateLine = when {
        preparing -> key.readyFrom?.let { stringResource(R.string.line_ready_from, format.date(it)) }
        else -> entry.expiry?.let {
            val line = if (entry.section == ExpirySection.EXPIRED) R.string.line_expired else R.string.line_expires
            stringResource(line, format.date(it))
        }
    }
    val secondLine = listOfNotNull(
        locationNames[key.locationId],
        key.openedOn?.let { stringResource(R.string.line_opened, format.date(it)) },
        dateLine,
    ).joinToString(" · ")
    val (tone, status) = when (entry.section) {
        ExpirySection.EXPIRED -> DiscTone.EXPIRED to stringResource(R.string.badge_expired)
        ExpirySection.EXPIRING -> DiscTone.EXPIRING to stringResource(R.string.badge_expiring)
        ExpirySection.PREPARING -> DiscTone.PREPARING to
            if ((key.readyFrom ?: today) > today) stringResource(R.string.badge_not_ready) else ""
        ExpirySection.LATER -> DiscTone.NEUTRAL to ""
    }
    val days = (if (preparing) key.readyFrom else entry.expiry)?.let { it - today }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 16.dp),
        ) {
            DaysDisc(days, tone, status)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        product.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (product.homemade) {
                        Icon(
                            painterResource(R.drawable.ic_homemade),
                            contentDescription = stringResource(R.string.label_homemade),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                if (secondLine.isNotEmpty()) {
                    Text(
                        secondLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                resources.quantityText(entry.group.quantity, product.unit, format),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
