// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.exchange

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nidamanx.formica.data.ExchangeRepository
import io.github.nidamanx.formica.sync.Backups
import io.github.nidamanx.formica.sync.Importer
import io.github.nidamanx.formica.ui.appContainer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A saved copy; [importedFrom] is null when the import it preceded has been undone. */
data class SavedCopy(val takenAt: Long, val importedFrom: String?)

data class BackupsUiState(val loaded: Boolean = false, val copies: List<SavedCopy> = emptyList())

class BackupsViewModel(exchange: ExchangeRepository, backups: Backups, private val importer: Importer) : ViewModel() {

    val state: StateFlow<BackupsUiState> = combine(backups.copies, exchange.importLog) { copies, log ->
        val names = log.associate { it.importedAt to it.deviceName }
        BackupsUiState(loaded = true, copies = copies.map { SavedCopy(it, names[it]) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupsUiState())

    private val resultChannel = Channel<Boolean>(Channel.BUFFERED)
    /** true: the copy is back; false: it could not be read and nothing changed. */
    val results: Flow<Boolean> = resultChannel.receiveAsFlow()

    init {
        viewModelScope.launch { backups.refresh() }
    }

    fun restore(takenAt: Long) {
        viewModelScope.launch { resultChannel.send(importer.restore(takenAt)) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                BackupsViewModel(c.exchange, c.backups, c.importer)
            }
        }
    }
}
