// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.nidamanx.formica.ui.FormicaApp
import io.github.nidamanx.formica.ui.theme.FormicaTheme
import io.github.nidamanx.formica.ui.welcome.WelcomeScreen
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Only on a fresh start: after a rotation the user may have moved elsewhere
        val openExpiries = savedInstanceState == null && intent.getBooleanExtra(EXTRA_OPEN_EXPIRIES, false)
        if (savedInstanceState == null) takeImport(intent)
        scheduleDailyCheck()
        val container = (application as FormicaApplication).container
        setContent {
            FormicaTheme {
                // null while the setting is read: nothing is drawn for that moment
                val firstRunDone by container.deviceSettings.firstRunDone
                    .collectAsStateWithLifecycle(initialValue = null)
                when (firstRunDone) {
                    null -> Unit
                    // A file opened at the very first start waits for the welcome screen
                    false -> WelcomeScreen()
                    true -> FormicaApp(
                        openExpiries = openExpiries,
                        pendingImport = container.pendingImport,
                        undoImport = container.importer::undoLastImport,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        takeImport(intent)
    }

    /** A file opened or shared from another app goes to the import preview. */
    private fun takeImport(intent: Intent) {
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        }
        if (uri != null) (application as FormicaApplication).container.pendingImport.value = uri
    }

    // Here and not in the Application: WorkManager also starts the process to run the check,
    // and rescheduling then would cancel the running work
    private fun scheduleDailyCheck() {
        val container = (application as FormicaApplication).container
        val settings = container.notificationSettings
        lifecycleScope.launch {
            settings.preferences
                .map { it.enabled to it.minuteOfDay }
                .distinctUntilChanged()
                .collect { container.dailyCheckScheduler.schedule(settings.current(), settings.lastCheckDay()) }
        }
    }

    companion object {
        const val EXTRA_OPEN_EXPIRIES = "io.github.nidamanx.formica.OPEN_EXPIRIES"
    }
}
