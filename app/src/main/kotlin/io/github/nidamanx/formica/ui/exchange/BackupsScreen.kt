// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.exchange

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nidamanx.formica.R
import io.github.nidamanx.formica.ui.components.BackTopBar
import io.github.nidamanx.formica.ui.format.rememberDisplayFormat

/** The copies taken before each import (SPEC §11.6); restoring one replaces all the data. */
@Composable
fun BackupsScreen(
    onBack: () -> Unit,
    viewModel: BackupsViewModel = viewModel(factory = BackupsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val format = rememberDisplayFormat()
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    var confirming by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.results.collect { restored ->
            val text = if (restored) R.string.msg_copy_restored else R.string.import_unreadable
            snackbar.showSnackbar(resources.getString(text))
        }
    }

    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.saved_copies), onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            !state.loaded -> Unit
            state.copies.isEmpty() -> Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.no_saved_copies), textAlign = TextAlign.Center)
            }
            else -> LazyColumn(Modifier.padding(padding)) {
                items(state.copies, key = { it.takenAt }) { copy ->
                    ListItem(
                        headlineContent = { Text(format.dateTime(copy.takenAt)) },
                        supportingContent = copy.importedFrom?.let { name ->
                            { Text(stringResource(R.string.backup_before_import, name)) }
                        },
                        modifier = Modifier.clickable { confirming = copy.takenAt },
                    )
                }
            }
        }
    }

    confirming?.let { takenAt ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(stringResource(R.string.restore_title)) },
            text = { Text(stringResource(R.string.restore_text, format.dateTime(takenAt))) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = null
                    viewModel.restore(takenAt)
                }) { Text(stringResource(R.string.action_restore)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
