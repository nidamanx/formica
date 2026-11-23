// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.product

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nidamanx.formica.R
import io.github.nidamanx.formica.domain.model.MeasureUnit
import io.github.nidamanx.formica.ui.components.BackTopBar
import io.github.nidamanx.formica.ui.components.DateField
import io.github.nidamanx.formica.ui.components.SelectField
import io.github.nidamanx.formica.ui.format.DisplayFormat
import io.github.nidamanx.formica.ui.format.fullName
import io.github.nidamanx.formica.ui.format.rememberDisplayFormat
import io.github.nidamanx.formica.ui.settings.NameError

/** New product ([productId] null) or edit of an existing one. */
@Composable
fun ProductFormScreen(
    productId: String?,
    onDone: () -> Unit,
    viewModel: ProductFormViewModel = viewModel(factory = ProductFormViewModel.factory(productId)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val format = rememberDisplayFormat()
    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    val title = stringResource(if (productId == null) R.string.product_new else R.string.product_edit)
    Scaffold(topBar = { BackTopBar(title, onDone) }) { padding ->
        if (state.loaded && (productId == null || state.product != null)) {
            ProductForm(
                state = state,
                isNew = productId == null,
                format = format,
                onSave = { fields -> viewModel.save(fields, format::parse) },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun ProductForm(
    state: ProductFormState,
    isNew: Boolean,
    format: DisplayFormat,
    onSave: (ProductFields) -> ProductFormErrors,
    modifier: Modifier = Modifier,
) {
    val p = state.product
    var name by rememberSaveable { mutableStateOf(p?.name.orEmpty()) }
    var quantity by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf(p?.unit ?: MeasureUnit.PIECE) }
    var locationId by rememberSaveable { mutableStateOf(p?.defaultLocationId ?: state.locations.firstOrNull()?.id) }
    var categoryId by rememberSaveable { mutableStateOf(p?.categoryId) }
    var expiresOn by rememberSaveable { mutableStateOf<Long?>(null) }
    var shelfLife by rememberSaveable { mutableStateOf(p?.shelfLifeDays?.toString().orEmpty()) }
    var afterOpening by rememberSaveable { mutableStateOf(p?.daysAfterOpening?.toString().orEmpty()) }
    var warning by rememberSaveable { mutableStateOf(p?.expiryWarningDays?.toString().orEmpty()) }
    var minStock by rememberSaveable { mutableStateOf(p?.minStock?.let { format.number(it) }.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(p?.notes.orEmpty()) }
    var moreOpen by rememberSaveable {
        mutableStateOf(listOf(shelfLife, afterOpening, warning, minStock).any { it.isNotEmpty() })
    }
    var errors by remember { mutableStateOf(ProductFormErrors()) }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                errors = errors.copy(name = null)
            },
            label = { Text(stringResource(R.string.field_name)) },
            singleLine = true,
            isError = errors.name != null,
            supportingText = errors.name?.let { e ->
                @Composable {
                    Text(
                        when (e) {
                            NameError.Required -> stringResource(R.string.error_name_required)
                            is NameError.Exists -> stringResource(R.string.error_name_exists, e.name)
                        },
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (isNew) {
                NumberField(
                    label = stringResource(R.string.field_quantity),
                    value = quantity,
                    error = if (errors.quantity) stringResource(R.string.error_quantity) else null,
                    decimal = true,
                    modifier = Modifier.weight(1f),
                ) {
                    quantity = it
                    errors = errors.copy(quantity = false)
                }
            }
            SelectField(
                label = stringResource(R.string.field_unit),
                options = MeasureUnit.entries,
                selected = unit,
                optionLabel = { stringResource(it.fullName()) },
                onSelect = { unit = it },
                modifier = if (isNew) Modifier.width(160.dp) else Modifier.fillMaxWidth(),
            )
        }
        SelectField(
            label = stringResource(R.string.field_location),
            options = state.locations.map { it.id },
            selected = locationId.orEmpty(),
            optionLabel = { id -> state.locations.firstOrNull { it.id == id }?.name.orEmpty() },
            onSelect = { locationId = it },
            modifier = Modifier.fillMaxWidth(),
        )
        val none = stringResource(R.string.value_none_f)
        SelectField(
            label = stringResource(R.string.field_category),
            options = listOf<String?>(null) + state.categories.map { it.id },
            selected = categoryId,
            optionLabel = { id -> state.categories.firstOrNull { it.id == id }?.name ?: none },
            onSelect = { categoryId = it },
            modifier = Modifier.fillMaxWidth(),
        )
        if (isNew) {
            DateField(
                label = stringResource(R.string.field_expiry),
                value = expiresOn,
                emptyText = none,
                format = format,
                onChange = { expiresOn = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        HorizontalDivider()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { moreOpen = !moreOpen }
                .padding(vertical = 8.dp),
        ) {
            Text(
                stringResource(R.string.form_more),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (moreOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (moreOpen) {
            val daysError = stringResource(R.string.error_days)
            NumberField(
                label = stringResource(R.string.field_shelf_life),
                value = shelfLife,
                error = if (errors.shelfLifeDays) daysError else null,
                help = stringResource(R.string.field_shelf_life_help),
            ) {
                shelfLife = it
                errors = errors.copy(shelfLifeDays = false)
            }
            NumberField(
                label = stringResource(R.string.field_days_after_opening),
                value = afterOpening,
                error = if (errors.daysAfterOpening) daysError else null,
            ) {
                afterOpening = it
                errors = errors.copy(daysAfterOpening = false)
            }
            NumberField(
                label = stringResource(R.string.field_warning_days),
                value = warning,
                error = if (errors.warningDays) daysError else null,
            ) {
                warning = it
                errors = errors.copy(warningDays = false)
            }
            NumberField(
                label = stringResource(R.string.field_min_stock),
                value = minStock,
                error = if (errors.minStock) stringResource(R.string.error_quantity) else null,
                decimal = true,
            ) {
                minStock = it
                errors = errors.copy(minStock = false)
            }
        }
        HorizontalDivider()

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text(stringResource(R.string.field_notes)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                errors = onSave(
                    ProductFields(
                        name = name,
                        quantity = quantity,
                        unit = unit,
                        locationId = locationId,
                        categoryId = categoryId,
                        expiresOn = expiresOn,
                        shelfLifeDays = shelfLife,
                        daysAfterOpening = afterOpening,
                        warningDays = warning,
                        minStock = minStock,
                        notes = notes,
                    ),
                )
            },
            modifier = Modifier.align(Alignment.End),
        ) { Text(stringResource(R.string.action_save)) }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    error: String?,
    modifier: Modifier = Modifier.fillMaxWidth(),
    decimal: Boolean = false,
    help: String? = null,
    onChange: (String) -> Unit,
) {
    val supporting = error ?: help
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = supporting?.let { text -> @Composable { Text(text) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}
