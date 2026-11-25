// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.exchange

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nidamanx.formica.domain.MergeResult
import io.github.nidamanx.formica.domain.RecordType
import io.github.nidamanx.formica.domain.model.ImportMode
import io.github.nidamanx.formica.domain.model.Snapshot
import io.github.nidamanx.formica.sync.ExportFile
import io.github.nidamanx.formica.sync.Importer
import io.github.nidamanx.formica.sync.ReadResult
import io.github.nidamanx.formica.ui.appContainer
import java.text.Collator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface ImportUiState {
    data object Loading : ImportUiState

    /** The file cannot be imported: nothing changed. */
    data class Refused(val reason: ReadResult) : ImportUiState

    data class Ready(
        val file: ExportFile,
        val sameDevice: Boolean,
        val mode: ImportMode,
        val result: MergeResult,
        val keepSeparate: Set<String>,
        /** Names of the records whose notes will be appended, sorted. */
        val conflictingNotes: List<String>,
        val importing: Boolean = false,
    ) : ImportUiState

    /** The import itself failed: the transaction left the data as it was. */
    data object Failed : ImportUiState
}

class ImportViewModel(private val importer: Importer, pending: MutableStateFlow<Uri?>) : ViewModel() {

    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Loading)
    val state: StateFlow<ImportUiState> = _state

    private val doneChannel = Channel<Unit>(Channel.CONFLATED)
    /** The import is applied: the screen closes. */
    val done: Flow<Unit> = doneChannel.receiveAsFlow()

    init {
        // A file opened while this screen is already shown replaces the previous one
        viewModelScope.launch {
            pending.filterNotNull().collect { uri ->
                pending.value = null
                load(uri)
            }
        }
    }

    private suspend fun load(uri: Uri) {
        _state.value = ImportUiState.Loading
        _state.value = orFailed {
            when (val read = importer.read(uri)) {
                is ReadResult.Valid -> ready(read.file, ImportMode.MERGE, emptySet())
                else -> ImportUiState.Refused(read)
            }
        }
    }

    fun setMode(mode: ImportMode) = update { it.copy(mode = mode) }

    /** A duplicate product of the file: merged (default) or kept separate. */
    fun setMerge(remoteId: String, merge: Boolean) = update {
        it.copy(keepSeparate = if (merge) it.keepSeparate - remoteId else it.keepSeparate + remoteId)
    }

    fun import() {
        val current = _state.value as? ImportUiState.Ready ?: return
        if (current.importing) return
        // The import merges again from the chosen options: a preview still running is not needed
        updating?.cancel()
        _state.value = current.copy(importing = true)
        viewModelScope.launch {
            try {
                importer.import(current.file, current.mode, current.keepSeparate)
                doneChannel.send(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value = ImportUiState.Failed
            }
        }
    }

    private var updating: Job? = null

    /**
     * The choice is shown at once; its preview follows. Quick taps: each change starts from the
     * previous choices and only the latest computes its preview.
     */
    private fun update(change: (ImportUiState.Ready) -> ImportUiState.Ready) {
        val current = _state.value as? ImportUiState.Ready ?: return
        if (current.importing) return
        val chosen = change(current)
        _state.value = chosen
        updating?.cancel()
        updating = viewModelScope.launch {
            _state.value = orFailed { ready(chosen.file, chosen.mode, chosen.keepSeparate) }
        }
    }

    /** An unexpected error while preparing the preview: shown as a failure, nothing changed. */
    private suspend fun orFailed(block: suspend () -> ImportUiState): ImportUiState = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        ImportUiState.Failed
    }

    private suspend fun ready(file: ExportFile, mode: ImportMode, keepSeparate: Set<String>): ImportUiState.Ready {
        val preview = importer.preview(file, mode, keepSeparate)
        return ImportUiState.Ready(
            file = file,
            sameDevice = preview.sameDevice,
            mode = mode,
            result = preview.result,
            keepSeparate = keepSeparate,
            conflictingNotes = conflictNames(preview.result),
        )
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                ImportViewModel(c.importer, c.pendingImport)
            }
        }
    }
}

private fun conflictNames(result: MergeResult): List<String> {
    val s: Snapshot = result.snapshot
    val products = s.products.associate { it.id to it.name }
    val batchProduct = s.batches.associate { it.id to it.productId }
    val collator = Collator.getInstance()
    return result.notesToReview.mapNotNull { ref ->
        when (ref.type) {
            RecordType.PRODUCT -> products[ref.id]
            RecordType.BATCH -> batchProduct[ref.id]?.let { products[it] }
            RecordType.LOCATION -> s.locations.firstOrNull { it.id == ref.id }?.name
            RecordType.STORE -> s.stores.firstOrNull { it.id == ref.id }?.name
            RecordType.SHOPPING_ITEM -> s.shoppingItems.firstOrNull { it.id == ref.id }
                ?.let { item -> item.text ?: item.productId?.let { products[it] } }
            RecordType.CATEGORY -> null
        }
    }.distinct().sortedWith { a, b -> collator.compare(a, b) }
}
