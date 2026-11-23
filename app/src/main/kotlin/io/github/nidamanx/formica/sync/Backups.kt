// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.sync

import android.content.Context
import io.github.nidamanx.formica.domain.model.Device
import io.github.nidamanx.formica.domain.model.Snapshot
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val BACKUP_DIR = "backups"
private const val KEPT = 5
private val BACKUP_NAME = Regex("backup-(\\d+)\\.json")

/**
 * Copies of all the data taken before every import (SPEC §11.6), in the export format, in
 * the app's private storage (never in an Android backup). The last [KEPT] are kept; each
 * is named by the time of the import it precedes, which is also its import log time.
 */
class Backups(private val context: Context) {
    private val dir get() = File(context.filesDir, BACKUP_DIR)

    suspend fun save(snapshot: Snapshot, device: Device, takenAt: Long) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val file = snapshot.toExportFile(device, appVersionName(context), exportedAt = takenAt)
        val target = File(dir, "backup-$takenAt.json")
        val temporary = File(dir, "backup-$takenAt.json.tmp")
        temporary.writeText(FormatCodec.write(file))
        // A copy is never left half written
        check(temporary.renameTo(target)) { "cannot save the backup" }
        list().drop(KEPT).forEach { File(dir, "backup-$it.json").delete() }
    }

    /** The times of the saved copies, newest first. */
    suspend fun list(): List<Long> = withContext(Dispatchers.IO) {
        dir.listFiles().orEmpty()
            .mapNotNull { BACKUP_NAME.matchEntire(it.name)?.groupValues?.get(1)?.toLongOrNull() }
            .sortedDescending()
    }

    suspend fun read(takenAt: Long): ReadResult = withContext(Dispatchers.IO) {
        val file = File(dir, "backup-$takenAt.json")
        if (file.exists()) FormatCodec.read(file.readText()) else ReadResult.Unreadable
    }
}
