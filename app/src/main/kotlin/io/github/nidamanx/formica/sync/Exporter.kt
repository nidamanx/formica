// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.sync

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import io.github.nidamanx.formica.data.ExchangeRepository
import io.github.nidamanx.formica.settings.DeviceSettings
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val EXPORT_DIR = "exports"
private val FILE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
private val UNSAFE_IN_NAMES = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

/** The export (SPEC §11.1): always complete, deleted records and movements included. */
class Exporter(
    private val context: Context,
    private val exchange: ExchangeRepository,
    private val deviceSettings: DeviceSettings,
) {

    /** formica-<device>-<yyyyMMdd-HHmm>.json, without the characters file systems refuse. */
    suspend fun fileName(): String {
        val device = deviceSettings.device().name.replace(UNSAFE_IN_NAMES, "_").trim()
        return "formica-$device-${LocalDateTime.now().format(FILE_TIME)}.json"
    }

    /** A share intent for a temporary copy in the cache, readable by the chosen app only. */
    suspend fun shareIntent(): Intent = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, EXPORT_DIR)
        // Only the latest export is kept: older ones were already shared or abandoned
        dir.listFiles()?.forEach { it.delete() }
        dir.mkdirs()
        val file = File(dir, fileName())
        file.writeText(exportText())
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(MIME)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(file.name, uri)
        Intent.createChooser(send, null)
    }

    /** Writes the export to a document chosen by the user ("Save to…"). */
    suspend fun writeTo(uri: Uri) = withContext(Dispatchers.IO) {
        val text = exportText()
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("cannot open $uri")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    private suspend fun exportText(): String {
        val file = exchange.snapshot().toExportFile(
            device = deviceSettings.device(),
            appVersion = appVersion(),
            exportedAt = System.currentTimeMillis(),
        )
        return FormatCodec.write(file)
    }

    private fun appVersion(): String {
        val manager = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            manager.getPackageInfo(context.packageName, 0)
        }
        return info.versionName.orEmpty()
    }

    companion object {
        const val MIME = "application/json"
    }
}
