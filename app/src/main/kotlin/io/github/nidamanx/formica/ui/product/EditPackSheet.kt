// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.product

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.nidamanx.formica.R
import io.github.nidamanx.formica.domain.BatchGroup
import io.github.nidamanx.formica.domain.Edited
import io.github.nidamanx.formica.domain.GroupEdit
import io.github.nidamanx.formica.domain.model.Origin
import io.github.nidamanx.formica.domain.model.Store
import io.github.nidamanx.formica.ui.components.DateField
import io.github.nidamanx.formica.ui.components.SelectField
import io.github.nidamanx.formica.ui.format.DisplayFormat
import io.github.nidamanx.formica.ui.pantry.SheetColumn

/** Stands for "the batches of the group disagree" in the store field; never a real id. */
private const val MIXED = "\u0000mixed"

/**
 * "Edit pack": dates, store and opening of a group. Purchase date and store may differ
 * between the group's batches: they show "Several values" and change only if touched.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPackSheet(
    group: BatchGroup,
    productName: String,
    stores: List<Store>,
    format: DisplayFormat,
    onConfirm: (GroupEdit) -> Unit,
    onDismiss: () -> Unit,
) {
    val key = group.key
    val batches = group.batches.filter { it.deletedAt == null }
    val purchased = key.origin == Origin.PURCHASED
    val commonPurchasedOn = batches.map { it.purchasedOn }.distinct().singleOrNull()
    val samePurchasedOn = batches.map { it.purchasedOn }.distinct().size <= 1
    val commonStoreId = batches.map { it.storeId }.distinct().singleOrNull()
    val sameStore = batches.map { it.storeId }.distinct().size <= 1

    var purchasedOn by rememberSaveable { mutableStateOf(commonPurchasedOn) }
    var purchasedOnEdited by rememberSaveable { mutableStateOf(false) }
    var storeId by rememberSaveable { mutableStateOf(if (sameStore) commonStoreId else MIXED) }
    var preparedOn by rememberSaveable { mutableStateOf(key.preparedOn) }
    var expiresOn by rememberSaveable { mutableStateOf(key.expiresOn) }
    var readyFrom by rememberSaveable { mutableStateOf(key.readyFrom) }
    var openedOn by rememberSaveable { mutableStateOf(key.openedOn) }

    val none = stringResource(R.string.value_none_f)
    val mixed = stringResource(R.string.value_mixed)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn(title = stringResource(R.string.edit_pack_title), subtitle = productName) {
            if (purchased) {
                DateField(
                    label = stringResource(R.string.field_purchased_on),
                    value = purchasedOn,
                    emptyText = if (samePurchasedOn || purchasedOnEdited) none else mixed,
                    format = format,
                    onChange = {
                        purchasedOn = it
                        purchasedOnEdited = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                val noStore = stringResource(R.string.value_none_m)
                SelectField(
                    label = stringResource(R.string.field_store),
                    options = listOf<String?>(null) + stores.map { it.id },
                    selected = storeId,
                    optionLabel = { id ->
                        if (id == MIXED) mixed else stores.firstOrNull { it.id == id }?.name ?: noStore
                    },
                    onSelect = { storeId = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
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
                    emptyText = none,
                    format = format,
                    onChange = { readyFrom = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            DateField(
                label = stringResource(R.string.field_expiry),
                value = expiresOn,
                emptyText = none,
                format = format,
                onChange = { expiresOn = it },
                modifier = Modifier.fillMaxWidth(),
            )
            DateField(
                label = stringResource(R.string.field_opened_on),
                value = openedOn,
                emptyText = none,
                format = format,
                onChange = { openedOn = it },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    onConfirm(
                        GroupEdit(
                            purchasedOn = if (purchasedOnEdited) Edited(purchasedOn) else null,
                            storeId = if (storeId == MIXED) null else Edited(storeId),
                            preparedOn = preparedOn?.takeIf { it != key.preparedOn }?.let { Edited(it) },
                            expiresOn = Edited(expiresOn).takeIf { expiresOn != key.expiresOn },
                            readyFrom = Edited(readyFrom).takeIf { readyFrom != key.readyFrom },
                            openedOn = Edited(openedOn).takeIf { openedOn != key.openedOn },
                        ),
                    )
                },
                modifier = Modifier.align(Alignment.End),
            ) { Text(stringResource(R.string.action_save)) }
        }
    }
}
