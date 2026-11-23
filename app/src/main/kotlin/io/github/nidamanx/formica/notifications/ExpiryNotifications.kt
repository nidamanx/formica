// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.notifications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.nidamanx.formica.MainActivity
import io.github.nidamanx.formica.R
import io.github.nidamanx.formica.domain.DailyDigest
import io.github.nidamanx.formica.domain.DigestKind
import io.github.nidamanx.formica.domain.DigestLine

private const val CHANNEL_ID = "expiry_reminders"
private const val NOTIFICATION_ID = 1
private const val MAX_LINES = 5

/** The daily summary notification (SPEC §9) and its dedicated channel. */
object ExpiryNotifications {

    /** Creates or updates the channel: names follow the current language. */
    fun createChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.notification_channel_name))
            .setDescription(context.getString(R.string.notification_channel_description))
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    /** Shows [digest], or removes yesterday's notification when there is nothing to report. */
    // areNotificationsEnabled covers the runtime permission of Android 13+
    @SuppressLint("MissingPermission")
    fun show(context: Context, digest: DailyDigest?) {
        val manager = NotificationManagerCompat.from(context)
        if (digest == null) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        if (!manager.areNotificationsEnabled()) return
        createChannel(context)
        val res = context.resources
        val summary = res.summaryText(digest)
        val style = NotificationCompat.InboxStyle().setBigContentTitle(res.getString(R.string.notification_title))
        digest.lines.take(MAX_LINES).forEach { style.addLine(res.lineText(it)) }
        val more = digest.lines.size - MAX_LINES
        if (more > 0) style.addLine(res.getQuantityString(R.plurals.notif_more, more, more))
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(res.getString(R.string.notification_title))
            .setContentText(summary)
            .setStyle(style)
            .setContentIntent(openExpiries(context))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun openExpiries(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_EXPIRIES, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/** "3 products expire within 3 days, 2 products have expired, 1 product is ready". */
private fun Resources.summaryText(digest: DailyDigest): String = listOfNotNull(
    digest.expiringProducts.takeIf { it > 0 }?.let { count ->
        val days = digest.expiringWithinDays.toInt()
        val within = when (days) {
            0 -> getString(R.string.notif_when_today)
            1 -> getString(R.string.notif_when_tomorrow)
            else -> getQuantityString(R.plurals.notif_when_days, days, days)
        }
        getQuantityString(R.plurals.notif_expiring, count, count, within)
    },
    digest.expiredProducts.takeIf { it > 0 }?.let { getQuantityString(R.plurals.notif_expired, it, it) },
    digest.readyProducts.takeIf { it > 0 }?.let { getQuantityString(R.plurals.notif_ready, it, it) },
).joinToString(", ")

/** "Milk · expires tomorrow". */
private fun Resources.lineText(line: DigestLine): String {
    val days = line.days.toInt()
    val status = when (line.kind) {
        DigestKind.EXPIRED -> getString(R.string.notif_status_expired)
        DigestKind.READY -> getString(R.string.notif_status_ready)
        DigestKind.EXPIRING -> when (days) {
            0 -> getString(R.string.notif_status_today)
            1 -> getString(R.string.notif_status_tomorrow)
            else -> getQuantityString(R.plurals.notif_status_days, days, days)
        }
    }
    return "${line.productName} · $status"
}
