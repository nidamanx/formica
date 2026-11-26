// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.welcome

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nidamanx.formica.R
import io.github.nidamanx.formica.domain.cleanName
import io.github.nidamanx.formica.ui.components.AppLogo
import io.github.nidamanx.formica.ui.format.timeOfDayText

/**
 * Shown once, at the first start (SPEC §12): what the app is, the device name and the daily
 * notification. On Android 13+ the notification permission is asked when the user starts.
 */
@Composable
fun WelcomeScreen(viewModel: WelcomeViewModel = viewModel(factory = WelcomeViewModel.Factory)) {
    val proposal by viewModel.proposal.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize()) {
        proposal?.let { WelcomeContent(it, viewModel) }
    }
}

@Composable
private fun WelcomeContent(proposal: WelcomeProposal, viewModel: WelcomeViewModel) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(proposal.deviceName) }
    var notify by rememberSaveable { mutableStateOf(proposal.notificationsEnabled) }
    var starting by rememberSaveable { mutableStateOf(false) }
    // Whatever the user answers, the app starts: a refusal is shown later in Expiry dates
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.finish(name, notify)
    }
    val start = {
        starting = true
        val ask = notify &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (ask) {
            viewModel.onPermissionAsked()
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.finish(name, notify)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            AppLogo()
            Text(
                stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.welcome_text),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.settings_device_name)) },
                supportingText = { Text(stringResource(R.string.settings_device_name_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = notify, role = Role.Switch, onValueChange = { notify = it }),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.welcome_notification), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.welcome_notification_hint, timeOfDayText(proposal.minuteOfDay)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = notify, onCheckedChange = null)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // The text beside it says it all
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.welcome_initial_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(onClick = start, enabled = !starting && cleanName(name).isNotEmpty()) {
                Text(stringResource(R.string.action_start))
            }
        }
    }
}
