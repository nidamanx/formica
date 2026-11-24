// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.pantry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.nidamanx.formica.R
import io.github.nidamanx.formica.domain.ProductSummary
import io.github.nidamanx.formica.domain.model.Location
import io.github.nidamanx.formica.domain.model.ONE_UNIT
import io.github.nidamanx.formica.domain.model.Store
import io.github.nidamanx.formica.domain.proposedExpiry
import io.github.nidamanx.formica.domain.proposedReadyFrom
import io.github.nidamanx.formica.ui.components.DateField
import io.github.nidamanx.formica.ui.components.SelectField
import io.github.nidamanx.formica.ui.format.DisplayFormat

/**
 * "Add purchase", prefilled: 1, today, proposed expiry, usual location; one tap to confirm.
 * The expiry follows the purchase date until the user sets it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseSheet(
    card: ProductSummary,
    locations: List<Location>,
    stores: List<Store>,
    today: Long,
    format: DisplayFormat,
    onConfirm: (quantity: Long, purchasedOn: Long, expiresOn: Long?, locationId: String, storeId: String?) -> Unit,
    onDismiss: () -> Unit,
    initialQuantity: Long = ONE_UNIT,
    initialStoreId: String? = null,
) {
    val product = card.product
    var quantityText by rememberSaveable { mutableStateOf(format.number(initialQuantity)) }
    var quantityError by rememberSaveable { mutableStateOf(false) }
    var purchasedOn by rememberSaveable { mutableLongStateOf(today) }
    var expiry by rememberSaveable { mutableStateOf<Long?>(null) }
    var expiryEdited by rememberSaveable { mutableStateOf(false) }
    var locationId by rememberSaveable { mutableStateOf(defaultLocation(card, locations)) }
    var storeId by rememberSaveable { mutableStateOf(initialStoreId?.takeIf { id -> stores.any { it.id == id } }) }
    val expiresOn = if (expiryEdited) expiry else proposedExpiry(purchasedOn, product.shelfLifeDays)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn(title = stringResource(R.string.purchase_title), subtitle = product.name) {
            QuantityField(quantityText, quantityError) {
                quantityText = it
                quantityError = false
            }
            DateField(
                label = stringResource(R.string.field_purchased_on),
                value = purchasedOn,
                emptyText = "",
                format = format,
                onChange = { date -> date?.let { purchasedOn = it } },
                clearable = false,
                modifier = Modifier.fillMaxWidth(),
            )
            DateField(
                label = stringResource(R.string.field_expiry),
                value = expiresOn,
                emptyText = stringResource(R.string.value_none_f),
                format = format,
                onChange = {
                    expiry = it
                    expiryEdited = true
                },
                modifier = Modifier.fillMaxWidth(),
            )
            LocationField(locations, locationId) { locationId = it }
            val none = stringResource(R.string.value_none_m)
            SelectField(
                label = stringResource(R.string.field_store),
                options = listOf<String?>(null) + stores.map { it.id },
                selected = storeId,
                optionLabel = { id -> stores.firstOrNull { it.id == id }?.name ?: none },
                onSelect = { storeId = it },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = locationId != null,
                onClick = {
                    val quantity = format.parse(quantityText)?.takeIf { it > 0 }
                    val location = locationId
                    quantityError = quantity == null
                    if (quantity != null && location != null) {
                        onConfirm(quantity, purchasedOn, expiresOn, location, storeId)
                    }
                },
                modifier = Modifier.align(Alignment.End),
            ) { Text(stringResource(R.string.action_add)) }
        }
    }
}

/**
 * "New preparation" of a homemade product, prefilled: 1, today, proposed ready date and
 * expiry, usual location. Both proposals follow the preparation date until the user sets them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreparationSheet(
    card: ProductSummary,
    locations: List<Location>,
    today: Long,
    format: DisplayFormat,
    onConfirm: (quantity: Long, preparedOn: Long, readyFrom: Long?, expiresOn: Long?, locationId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val product = card.product
    var quantityText by rememberSaveable { mutableStateOf(format.number(ONE_UNIT)) }
    var quantityError by rememberSaveable { mutableStateOf(false) }
    var preparedOn by rememberSaveable { mutableLongStateOf(today) }
    var ready by rememberSaveable { mutableStateOf<Long?>(null) }
    var readyEdited by rememberSaveable { mutableStateOf(false) }
    var expiry by rememberSaveable { mutableStateOf<Long?>(null) }
    var expiryEdited by rememberSaveable { mutableStateOf(false) }
    var locationId by rememberSaveable { mutableStateOf(defaultLocation(card, locations)) }
    val readyFrom = if (readyEdited) ready else proposedReadyFrom(preparedOn, product.daysUntilReady)
    val expiresOn = if (expiryEdited) expiry else proposedExpiry(preparedOn, product.shelfLifeDays)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn(title = stringResource(R.string.preparation_title), subtitle = product.name) {
            QuantityField(quantityText, quantityError) {
                quantityText = it
                quantityError = false
            }
            DateField(
                label = stringResource(R.string.field_prepared_on),
                value = preparedOn,
                emptyText = "",
                format = format,
                onChange = { date -> date?.let { preparedOn = it } },
                clearable = false,
                modifier = Modifier.fillMaxWidth(),
            )
            DateField(
                label = stringResource(R.string.field_ready_from),
                value = readyFrom,
                emptyText = stringResource(R.string.value_none_f),
                format = format,
                onChange = {
                    ready = it
                    readyEdited = true
                },
                modifier = Modifier.fillMaxWidth(),
            )
            DateField(
                label = stringResource(R.string.field_expiry),
                value = expiresOn,
                emptyText = stringResource(R.string.value_none_f),
                format = format,
                onChange = {
                    expiry = it
                    expiryEdited = true
                },
                modifier = Modifier.fillMaxWidth(),
            )
            LocationField(locations, locationId) { locationId = it }
            Button(
                enabled = locationId != null,
                onClick = {
                    val quantity = format.parse(quantityText)?.takeIf { it > 0 }
                    val location = locationId
                    quantityError = quantity == null
                    if (quantity != null && location != null) {
                        onConfirm(quantity, preparedOn, readyFrom, expiresOn, location)
                    }
                },
                modifier = Modifier.align(Alignment.End),
            ) { Text(stringResource(R.string.action_add)) }
        }
    }
}

private fun defaultLocation(card: ProductSummary, locations: List<Location>): String? =
    listOfNotNull(card.product.defaultLocationId, card.locationIds.firstOrNull())
        .firstOrNull { id -> locations.any { it.id == id } }
        ?: locations.firstOrNull()?.id

@Composable
private fun LocationField(locations: List<Location>, selected: String?, onSelect: (String) -> Unit) {
    SelectField(
        label = stringResource(R.string.field_location),
        options = locations.map { it.id },
        selected = selected.orEmpty(),
        optionLabel = { id -> locations.firstOrNull { it.id == id }?.name.orEmpty() },
        onSelect = onSelect,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Amount to use, for products measured by weight or volume. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UseSheet(
    card: ProductSummary,
    format: DisplayFormat,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var quantityText by rememberSaveable { mutableStateOf("") }
    var quantityError by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn(title = stringResource(R.string.use_title), subtitle = card.product.name) {
            QuantityField(quantityText, quantityError) {
                quantityText = it
                quantityError = false
            }
            Button(
                onClick = {
                    val amount = format.parse(quantityText)?.takeIf { it > 0 }
                    quantityError = amount == null
                    if (amount != null) onConfirm(amount)
                },
                modifier = Modifier.align(Alignment.End),
            ) { Text(stringResource(R.string.use_title)) }
        }
    }
}

/** Title, optional subtitle and content of a bottom panel. */
@Composable
fun SheetColumn(
    title: String,
    subtitle: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
            .navigationBarsPadding(),
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        content()
    }
}

@Composable
fun QuantityField(value: String, error: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.field_quantity)) },
        singleLine = true,
        isError = error,
        supportingText = if (error) {
            { Text(stringResource(R.string.error_quantity)) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}
