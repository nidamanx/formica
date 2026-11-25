// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica

import android.app.Application
import io.github.nidamanx.formica.notifications.ExpiryNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FormicaApplication : Application() {
    lateinit var container: AppContainer
        private set

    /** Work that must outlive any single screen. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ExpiryNotifications.createChannel(this)
        applicationScope.launch { container.initialize() }
    }
}
