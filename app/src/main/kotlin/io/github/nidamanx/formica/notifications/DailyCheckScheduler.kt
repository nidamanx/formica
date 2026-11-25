// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import io.github.nidamanx.formica.domain.dailyPlan
import io.github.nidamanx.formica.settings.NotificationPreferences
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

private const val DAILY_WORK = "daily-expiry-check"
private const val CATCH_UP_WORK = "daily-expiry-check-now"

/**
 * Schedules the daily check (SPEC §9): periodic work every 24 hours, started at the next
 * chosen time; no exact alarms. Called at every app start and when the settings change, so
 * a daylight saving change or a new time zone is followed at the next start.
 */
class DailyCheckScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun schedule(preferences: NotificationPreferences, lastCheckDay: Long?) {
        if (!preferences.enabled) {
            workManager.cancelUniqueWork(DAILY_WORK)
            workManager.cancelUniqueWork(CATCH_UP_WORK)
            return
        }
        val plan = dailyPlan(ZonedDateTime.now(), preferences.minuteOfDay, lastCheckDay)
        val daily = PeriodicWorkRequestBuilder<ExpiryCheckWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(plan.firstDelay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(DAILY_WORK, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, daily)
        if (plan.runNow) {
            val now = OneTimeWorkRequestBuilder<ExpiryCheckWorker>().build()
            workManager.enqueueUniqueWork(CATCH_UP_WORK, ExistingWorkPolicy.KEEP, now)
        }
    }
}
