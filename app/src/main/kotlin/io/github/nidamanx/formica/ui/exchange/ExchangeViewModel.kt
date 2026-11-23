// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.exchange

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nidamanx.formica.data.ExchangeRepository
import io.github.nidamanx.formica.domain.model.ImportRecord
import io.github.nidamanx.formica.sync.Backups
import io.github.nidamanx.formica.sync.Exporter
import io.github.nidamanx.formica.sync.Importer
import io.github.nidamanx.formica.ui.appContainer
import java.io.IOException
import java.time.ZonedDateTime
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val CLEAN_UP_MONTHS = 6L

data class ExchangeUiState(
    val loaded: Boolean = false,
    val lastImport: ImportRecord? = null,
    /** The copy taken before [lastImport] still exists: the import can be undone. */
    val canUndo: Boolean = false,
    val copies: Int = 0,
    /** The imported files, newest first. */
    val log: List<ImportRecord> = emptyList(),
)

sealed interface ExchangeEvent {
    data class Share(val chooser: Intent) : ExchangeEvent
    data class SaveAs(val fileName: String) : ExchangeEvent
    data object Saved : ExchangeEvent
    data object SaveFailed : ExchangeEvent
    data object ImportUndone : ExchangeEvent
    data object CopyUnreadable : ExchangeEvent
    data class CleanedUp(val removed: Int) : ExchangeEvent
}

class ExchangeViewModel(
    private val exchange: ExchangeRepository,
    private val exporter: Exporter,
    private val importer: Importer,
    backups: Backups,
    private val pendingImport: MutableStateFlow<Uri?>,
) : ViewModel() {

    val state: StateFlow<ExchangeUiState> = combine(exchange.importLog, backups.copies) { log, copies ->
        val last = log.firstOrNull()
        ExchangeUiState(
            loaded = true,
            lastImport = last,
            canUndo = last != null && last.importedAt in copies,
            copies = copies.size,
            log = log,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExchangeUiState())

    init {
        viewModelScope.launch { backups.refresh() }
    }

    /** "Clean up deleted items": those deleted more than 6 months ago (SPEC §11.7). */
    fun cleanUp() {
        viewModelScope.launch {
            val before = ZonedDateTime.now().minusMonths(CLEAN_UP_MONTHS).toInstant().toEpochMilli()
            eventChannel.send(ExchangeEvent.CleanedUp(exchange.purgeDeleted(before)))
        }
    }

    fun undoLastImport() {
        viewModelScope.launch {
            val undone = importer.undoLastImport()
            eventChannel.send(if (undone) ExchangeEvent.ImportUndone else ExchangeEvent.CopyUnreadable)
        }
    }

    private val eventChannel = Channel<ExchangeEvent>(Channel.BUFFERED)
    val events: Flow<ExchangeEvent> = eventChannel.receiveAsFlow()

    fun share() {
        viewModelScope.launch { eventChannel.send(ExchangeEvent.Share(exporter.shareIntent())) }
    }

    /** "Save to…": the system file picker opens with the proposed name. */
    fun chooseSaveLocation() {
        viewModelScope.launch { eventChannel.send(ExchangeEvent.SaveAs(exporter.fileName())) }
    }

    /** "Import from file": the import preview takes the chosen file. */
    fun importFrom(uri: Uri) {
        pendingImport.value = uri
    }

    fun saveTo(uri: Uri) {
        viewModelScope.launch {
            val event = try {
                exporter.writeTo(uri)
                ExchangeEvent.Saved
            } catch (_: IOException) {
                ExchangeEvent.SaveFailed
            } catch (_: IllegalStateException) {
                ExchangeEvent.SaveFailed
            } catch (_: SecurityException) {
                ExchangeEvent.SaveFailed
            }
            eventChannel.send(event)
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                ExchangeViewModel(c.exchange, c.exporter, c.importer, c.backups, c.pendingImport)
            }
        }
    }
}
