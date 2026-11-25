// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.components

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.nidamanx.formica.R

/** Whether the app can show notifications, and what the user can do about it. */
enum class NotificationAccess {
    GRANTED,

    /** Android 13+: the system permission dialog can still be shown. */
    ASK,

    /** Denied for good, or notifications turned off: only the Android settings can change it. */
    BLOCKED,
}

class NotificationAccessState(
    val access: NotificationAccess,
    /** Shows the system dialog when [access] is ASK, otherwise opens the app's notification settings. */
    val resolve: () -> Unit,
)

/**
 * Notification access, checked again every time the screen comes back (the user may have
 * changed it in the Android settings).
 *
 * @param asked the permission has been asked before: without it, a dialog that Android no
 *   longer shows cannot be told apart from one never shown
 * @param onAsked called when the system dialog is about to be shown
 */
@Composable
fun rememberNotificationAccess(asked: Boolean, onAsked: () -> Unit): NotificationAccessState {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    val access = remember(refresh, asked) {
        when {
            NotificationManagerCompat.from(context).areNotificationsEnabled() -> NotificationAccess.GRANTED
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> NotificationAccess.BLOCKED
            !asked -> NotificationAccess.ASK
            activity != null && ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.POST_NOTIFICATIONS,
            ) -> NotificationAccess.ASK
            else -> NotificationAccess.BLOCKED
        }
    }
    return NotificationAccessState(access) {
        if (access == NotificationAccess.ASK && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onAsked()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

/** The explanation shown before the system dialog (SPEC §9), or the way to the Android settings. */
@Composable
fun NotificationBanner(
    access: NotificationAccess,
    onEnable: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val blocked = access == NotificationAccess.BLOCKED
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp)) {
            Text(stringResource(R.string.notif_banner_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(if (blocked) R.string.notif_permission_denied else R.string.notif_banner_text),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, end = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_no_thanks)) }
                TextButton(onClick = onEnable) {
                    Text(stringResource(if (blocked) R.string.action_open_settings else R.string.action_enable))
                }
            }
        }
    }
}
