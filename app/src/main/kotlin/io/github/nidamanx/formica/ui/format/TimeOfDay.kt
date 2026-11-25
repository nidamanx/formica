// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.util.Calendar

/** The time in the phone's format (9:00 or 9:00 AM). */
@Composable
fun timeOfDayText(minuteOfDay: Int): String {
    val context = LocalContext.current
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        set(Calendar.MINUTE, minuteOfDay % 60)
    }
    return android.text.format.DateFormat.getTimeFormat(context).format(calendar.time)
}
