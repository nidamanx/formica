// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.github.nidamanx.formica.R

/** Top bar of the bottom-bar sections, with the menu that opens the settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionTopBar(title: String, onOpenSettings: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(title) },
        actions = {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_title)) },
                    onClick = {
                        menuOpen = false
                        onOpenSettings()
                    },
                )
            }
        },
    )
}

/** Top bar of the screens opened from another one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
            }
        },
    )
}
