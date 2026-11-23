// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.shopping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import io.github.nidamanx.formica.domain.ShoppingRow
import io.github.nidamanx.formica.domain.cleanName
import io.github.nidamanx.formica.domain.model.Category
import io.github.nidamanx.formica.domain.model.MeasureUnit
import io.github.nidamanx.formica.domain.model.ShoppingItem
import io.github.nidamanx.formica.domain.model.Store
import io.github.nidamanx.formica.ui.components.SelectField
import io.github.nidamanx.formica.ui.format.DisplayFormat
import io.github.nidamanx.formica.ui.format.fullName
import io.github.nidamanx.formica.ui.pantry.SheetColumn

/**
 * New free item ([row] null) or edit of an item. A linked item changes only its quantity:
 * name, unit, category and store come from the product.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemSheet(
    row: ShoppingRow?,
    categories: List<Category>,
    stores: List<Store>,
    format: DisplayFormat,
    onSave: (ShoppingItem) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val item = row?.item
    val linked = row?.product != null
    var name by rememberSaveable { mutableStateOf(item?.text.orEmpty()) }
    var nameError by rememberSaveable { mutableStateOf(false) }
    var quantity by rememberSaveable { mutableStateOf(item?.quantity?.let { format.number(it) }.orEmpty()) }
    var quantityError by rememberSaveable { mutableStateOf(false) }
    var unit by rememberSaveable { mutableStateOf(item?.unit ?: MeasureUnit.PIECE) }
    var categoryId by rememberSaveable {
        mutableStateOf(item?.categoryId?.takeIf { id -> categories.any { it.id == id } })
    }
    var storeId by rememberSaveable { mutableStateOf(item?.storeId?.takeIf { id -> stores.any { it.id == id } }) }

    val title = stringResource(if (item == null) R.string.shopping_new_item else R.string.shopping_edit_item)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn(title = title, subtitle = row?.product?.name.orEmpty()) {
            if (!linked) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = false
                    },
                    label = { Text(stringResource(R.string.field_name)) },
                    singleLine = true,
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text(stringResource(R.string.error_name_required)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = quantity,
                    onValueChange = {
                        quantity = it
                        quantityError = false
                    },
                    label = { Text(stringResource(R.string.field_quantity)) },
                    singleLine = true,
                    isError = quantityError,
                    supportingText = if (quantityError) {
                        { Text(stringResource(R.string.error_quantity)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                if (!linked) {
                    SelectField(
                        label = stringResource(R.string.field_unit),
                        options = MeasureUnit.entries,
                        selected = unit,
                        optionLabel = { stringResource(it.fullName()) },
                        onSelect = { unit = it },
                        modifier = Modifier.width(160.dp),
                    )
                }
            }
            if (!linked) {
                val noneF = stringResource(R.string.value_none_f)
                SelectField(
                    label = stringResource(R.string.field_category),
                    options = listOf<String?>(null) + categories.map { it.id },
                    selected = categoryId,
                    optionLabel = { id -> categories.firstOrNull { it.id == id }?.name ?: noneF },
                    onSelect = { categoryId = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                val noneM = stringResource(R.string.value_none_m)
                SelectField(
                    label = stringResource(R.string.field_store),
                    options = listOf<String?>(null) + stores.map { it.id },
                    selected = storeId,
                    optionLabel = { id -> stores.firstOrNull { it.id == id }?.name ?: noneM },
                    onSelect = { storeId = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                onDelete?.let { TextButton(onClick = it) { Text(stringResource(R.string.action_delete)) } }
                Spacer(Modifier.weight(1f))
                Button(onClick = {
                    val parsed = quantity.trim().takeIf { it.isNotEmpty() }?.let(format::parse)
                    quantityError = quantity.isNotBlank() && (parsed == null || parsed == 0L)
                    nameError = !linked && cleanName(name).isEmpty()
                    if (quantityError || nameError) return@Button
                    val base = item ?: ShoppingItem(id = "", createdAt = 0, modifiedAt = 0, modifiedBy = "")
                    onSave(
                        if (linked) {
                            base.copy(quantity = parsed)
                        } else {
                            base.copy(
                                text = cleanName(name),
                                quantity = parsed,
                                unit = unit,
                                categoryId = categoryId,
                                storeId = storeId,
                            )
                        },
                    )
                }) {
                    Text(stringResource(if (item == null) R.string.action_add else R.string.action_save))
                }
            }
        }
    }
}
