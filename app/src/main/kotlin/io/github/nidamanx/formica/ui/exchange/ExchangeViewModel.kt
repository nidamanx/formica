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
import io.github.nidamanx.formica.sync.Exporter
import io.github.nidamanx.formica.ui.appContainer
import java.io.IOException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExchangeUiState(val loaded: Boolean = false, val lastImport: ImportRecord? = null)

sealed interface ExchangeEvent {
    data class Share(val chooser: Intent) : ExchangeEvent
    data class SaveAs(val fileName: String) : ExchangeEvent
    data object Saved : ExchangeEvent
    data object SaveFailed : ExchangeEvent
}

class ExchangeViewModel(exchange: ExchangeRepository, private val exporter: Exporter) : ViewModel() {

    val state: StateFlow<ExchangeUiState> = exchange.importLog
        .map { ExchangeUiState(loaded = true, lastImport = it.firstOrNull()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExchangeUiState())

    private val eventChannel = Channel<ExchangeEvent>(Channel.BUFFERED)
    val events: Flow<ExchangeEvent> = eventChannel.receiveAsFlow()

    fun share() {
        viewModelScope.launch { eventChannel.send(ExchangeEvent.Share(exporter.shareIntent())) }
    }

    /** "Save to…": the system file picker opens with the proposed name. */
    fun chooseSaveLocation() {
        viewModelScope.launch { eventChannel.send(ExchangeEvent.SaveAs(exporter.fileName())) }
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
                ExchangeViewModel(c.exchange, c.exporter)
            }
        }
    }
}
