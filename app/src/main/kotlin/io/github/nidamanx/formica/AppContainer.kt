// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica

import android.content.Context
import android.os.Build
import io.github.nidamanx.formica.data.CatalogRepository
import io.github.nidamanx.formica.data.FormicaDatabase
import io.github.nidamanx.formica.data.InitialData
import io.github.nidamanx.formica.data.PantryRepository
import io.github.nidamanx.formica.domain.OperationContext
import io.github.nidamanx.formica.settings.DeviceSettings
import java.time.LocalDate
import java.util.UUID

/** The app's single instances, created by [FormicaApplication] and passed to ViewModels by factories. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: FormicaDatabase = FormicaDatabase.create(appContext)
    val deviceSettings = DeviceSettings(appContext, defaultName = Build.MODEL.ifBlank { Build.MANUFACTURER })
    val catalog = CatalogRepository(database, ::operationContext)
    val pantry = PantryRepository(database, ::operationContext)
    private val initialData = InitialData(database, appContext.resources, ::operationContext)

    /** Time, date and device of an operation, taken when the operation starts. */
    suspend fun operationContext(): OperationContext = OperationContext(
        now = System.currentTimeMillis(),
        today = LocalDate.now().toEpochDay(),
        deviceId = deviceSettings.device().id,
        newId = { UUID.randomUUID().toString() },
    )

    /** First start: device id, then the initial locations and categories. */
    suspend fun initialize() {
        deviceSettings.device()
        initialData.createIfEmpty()
    }
}
