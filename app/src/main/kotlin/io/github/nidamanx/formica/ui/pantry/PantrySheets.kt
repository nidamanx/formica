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
import io.github.nidamanx.formica.ui.components.DateField
import io.github.nidamanx.formica.ui.components.SelectField
import io.github.nidamanx.formica.ui.format.DisplayFormat

/** "Add purchase", prefilled: 1, proposed expiry, usual location; one tap to confirm. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseSheet(
    card: ProductSummary,
    locations: List<Location>,
    stores: List<Store>,
    today: Long,
    format: DisplayFormat,
    onConfirm: (quantity: Long, expiresOn: Long?, locationId: String, storeId: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val product = card.product
    val defaultLocation = listOfNotNull(product.defaultLocationId, card.locationIds.firstOrNull())
        .firstOrNull { id -> locations.any { it.id == id } }
        ?: locations.firstOrNull()?.id
    var quantityText by rememberSaveable { mutableStateOf(format.number(ONE_UNIT)) }
    var quantityError by rememberSaveable { mutableStateOf(false) }
    var expiresOn by rememberSaveable { mutableStateOf(proposedExpiry(today, product.shelfLifeDays)) }
    var locationId by rememberSaveable { mutableStateOf(defaultLocation) }
    var storeId by rememberSaveable { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn(title = stringResource(R.string.purchase_title), subtitle = product.name) {
            QuantityField(quantityText, quantityError) {
                quantityText = it
                quantityError = false
            }
            DateField(
                label = stringResource(R.string.field_expiry),
                value = expiresOn,
                emptyText = stringResource(R.string.value_none_f),
                format = format,
                onChange = { expiresOn = it },
                modifier = Modifier.fillMaxWidth(),
            )
            SelectField(
                label = stringResource(R.string.field_location),
                options = locations.map { it.id },
                selected = locationId.orEmpty(),
                optionLabel = { id -> locations.firstOrNull { it.id == id }?.name.orEmpty() },
                onSelect = { locationId = it },
                modifier = Modifier.fillMaxWidth(),
            )
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
                    if (quantity != null && location != null) onConfirm(quantity, expiresOn, location, storeId)
                },
                modifier = Modifier.align(Alignment.End),
            ) { Text(stringResource(R.string.action_add)) }
        }
    }
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

@Composable
private fun SheetColumn(
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
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
