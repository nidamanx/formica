// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.nidamanx.formica.FormicaApplication
import io.github.nidamanx.formica.domain.dailyDigest
import io.github.nidamanx.formica.domain.expiryOverview
import java.text.Collator
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/** The daily check: at most one notification per day, none when there is nothing to report. */
class ExpiryCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as FormicaApplication).container
        val settings = container.notificationSettings
        val preferences = settings.current()
        val today = LocalDate.now().toEpochDay()
        if (!preferences.enabled || settings.lastCheckDay() == today) return Result.success()

        val pantry = container.pantry
        val collator = Collator.getInstance()
        val names = Comparator<String> { a, b -> collator.compare(a, b) }
        val overview = expiryOverview(
            products = pantry.products.first(),
            batches = pantry.batches.first(),
            quantities = pantry.batchQuantities.first(),
            today = today,
            defaultWarningDays = preferences.warningDays,
            compareNames = names,
        )
        ExpiryNotifications.show(applicationContext, dailyDigest(overview, today, names))
        settings.setLastCheckDay(today)
        return Result.success()
    }
}
