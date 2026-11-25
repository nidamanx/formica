// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalResources
import io.github.nidamanx.formica.R
import io.github.nidamanx.formica.domain.model.Product
import kotlinx.coroutines.flow.Flow

/** "Below minimum stock" with an action that adds the product to the shopping list. */
@Composable
fun RestockQuestions(questions: Flow<Product>, snackbar: SnackbarHostState, onAdd: (String) -> Unit) {
    val resources = LocalResources.current
    LaunchedEffect(questions) {
        questions.collect { product ->
            val result = snackbar.showSnackbar(
                message = resources.getString(R.string.msg_below_min_stock, product.name),
                actionLabel = resources.getString(R.string.action_add_to_list),
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) onAdd(product.id)
        }
    }
}
