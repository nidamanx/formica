// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.sync

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** versionName of the installed app: written into every exported file, shown in About. */
fun appVersionName(context: Context): String {
    val manager = context.packageManager
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        manager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        manager.getPackageInfo(context.packageName, 0)
    }
    return info.versionName.orEmpty()
}
