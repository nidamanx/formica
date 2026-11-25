// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.sync

import android.content.Context
import android.net.Uri
import io.github.nidamanx.formica.data.ExchangeRepository
import io.github.nidamanx.formica.data.PantryRepository
import io.github.nidamanx.formica.domain.ImportRequest
import io.github.nidamanx.formica.domain.MergeResult
import io.github.nidamanx.formica.domain.OperationContext
import io.github.nidamanx.formica.domain.mergeImport
import io.github.nidamanx.formica.domain.model.ImportMode
import io.github.nidamanx.formica.domain.model.ImportRecord
import io.github.nidamanx.formica.domain.model.Snapshot
import io.github.nidamanx.formica.domain.productsDroppedBelowMinStock
import io.github.nidamanx.formica.settings.DeviceSettings
import io.github.nidamanx.formica.ui.format.DisplayFormat
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Far beyond any household pantry: a bigger file is not read into memory
private const val MAX_FILE_BYTES = 50L * 1024 * 1024

/** What the preview shows: nothing has changed yet. */
data class ImportPreview(val file: ExportFile, val result: MergeResult, val sameDevice: Boolean)

/** Reading, preview and application of an exchange file (SPEC §11.3-§11.5). */
class Importer(
    private val context: Context,
    private val exchange: ExchangeRepository,
    private val pantry: PantryRepository,
    private val deviceSettings: DeviceSettings,
    private val backups: Backups,
    private val operationContext: suspend () -> OperationContext,
) {

    suspend fun read(uri: Uri): ReadResult = withContext(Dispatchers.IO) {
        try {
            val stream = context.contentResolver.openInputStream(uri) ?: return@withContext ReadResult.Unreadable
            val bytes = stream.use { it.readNBytesCompat(MAX_FILE_BYTES + 1) }
            if (bytes.size > MAX_FILE_BYTES) ReadResult.Unreadable else FormatCodec.read(bytes.decodeToString())
        } catch (_: IOException) {
            ReadResult.Unreadable
        } catch (_: SecurityException) {
            ReadResult.Unreadable
        }
    }

    suspend fun preview(file: ExportFile, mode: ImportMode, keepSeparate: Set<String>): ImportPreview {
        val result = merge(file, mode, keepSeparate, exchange.snapshot(), operationContext())
        return ImportPreview(file, result, sameDevice = file.device.id == deviceSettings.device().id)
    }

    /**
     * Saves a copy of all the data, then applies the import and logs it. Reading, copy, merge
     * and writing are one transaction (SPEC §11.3): a change made meanwhile cannot be lost.
     *
     * @return the time of the import, which names the copy taken just before it
     */
    suspend fun import(file: ExportFile, mode: ImportMode, keepSeparate: Set<String>): Long {
        val ctx = operationContext()
        val device = deviceSettings.device()
        val (before, result) = exchange.inTransaction {
            val local = exchange.snapshot()
            backups.save(local, device, takenAt = ctx.now)
            val result = merge(file, mode, keepSeparate, local, ctx)
            val record = ImportRecord(file.device.id, file.device.name, file.exportedAt, ctx.now, mode)
            exchange.apply(result.snapshot, replace = mode == ImportMode.REPLACE_ALL, record, result.notesToReview)
            local to result
        }
        // The import is written: a failure here must not report it as failed
        try {
            pantry.restock(productsDroppedBelowMinStock(before, result.snapshot))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The minimum stock is checked again at the next use of these products
        }
        return ctx.now
    }

    /**
     * Puts back the copy taken at [takenAt] with the logic of "Replace everything"
     * (SPEC §11.6); the imports made from that moment on leave the log.
     *
     * @return false if the copy cannot be read or written (nothing changes: one transaction)
     */
    suspend fun restore(takenAt: Long): Boolean {
        val copy = backups.read(takenAt) as? ReadResult.Valid ?: return false
        return try {
            exchange.apply(
                snapshot = copy.file.toSnapshot(),
                replace = true,
                record = null,
                review = emptySet(),
                undoImportsFrom = takenAt,
            )
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    /** "Undo last import": the copy taken just before it. */
    suspend fun undoLastImport(): Boolean {
        val last = exchange.lastImport() ?: return false
        return restore(last.importedAt)
    }

    private suspend fun merge(
        file: ExportFile,
        mode: ImportMode,
        keepSeparate: Set<String>,
        local: Snapshot,
        ctx: OperationContext,
    ): MergeResult {
        val request = ImportRequest(
            local = local,
            remote = file.toSnapshot(),
            mode = mode,
            localDeviceName = deviceSettings.device().name,
            remoteDeviceName = file.device.name,
            lastExportedAt = exchange.lastExportedAtFrom(file.device.id),
            keepSeparate = keepSeparate,
        )
        // "— Anna, 23/11/2026 —": the day the part was written, in the format of the phone's language
        val format = DisplayFormat(Locale.getDefault())
        return mergeImport(request, ctx) { device, at ->
            val day = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
            "— $device, ${format.date(day)} —"
        }
    }
}

/** InputStream.readNBytes exists only from Android 13: reads at most [limit] bytes. */
private fun InputStream.readNBytesCompat(limit: Long): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (total < limit) {
        val read = read(buffer, 0, minOf(buffer.size.toLong(), limit - total).toInt())
        if (read < 0) break
        out.write(buffer, 0, read)
        total += read
    }
    return out.toByteArray()
}
