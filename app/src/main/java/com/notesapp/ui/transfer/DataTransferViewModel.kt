package com.notesapp.ui.transfer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notesapp.data.repository.CollectionRepository
import com.notesapp.data.repository.DataTransferRepository
import com.notesapp.data.repository.ImportMode
import com.notesapp.data.transfer.DocumentTransfer
import com.notesapp.domain.transfer.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import javax.inject.Inject

enum class TransferFormat { JSON, CSV, MARKDOWN }
data class TransferState(val busy: Boolean = false, val message: String? = null, val preview: String? = null)

@HiltViewModel
class DataTransferViewModel @Inject constructor(
    private val transfers: DataTransferRepository,
    private val documents: DocumentTransfer,
    collections: CollectionRepository,
    private val clock: Clock,
) : ViewModel() {
    val collections = collections.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _state = MutableStateFlow(TransferState())
    val state = _state.asStateFlow()
    private var pending: Backup? = null

    private fun run(block: suspend () -> String) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                val message = withContext(Dispatchers.IO) { block() }
                _state.update { it.copy(busy = false, message = message) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(busy = false, message = e.message ?: "This operation could not be completed.") } }
        }
    }

    fun export(uri: Uri, format: TransferFormat, collectionId: Long?) = run {
        val backup = transfers.snapshot()
        when (format) {
            TransferFormat.JSON -> { documents.write(uri, BackupJson.encode(backup)); "JSON backup exported." }
            TransferFormat.CSV -> { documents.write(uri, CsvTransfer.encode(backup, collectionId)); "CSV exported." }
            TransferFormat.MARKDOWN -> "${documents.exportMarkdown(uri, backup)} Markdown files exported."
        }
    }

    fun preview(uri: Uri, format: TransferFormat) = run {
        val backup = when (format) {
            TransferFormat.JSON -> BackupJson.decode(documents.read(uri))
            TransferFormat.CSV -> CsvTransfer.decode(documents.read(uri), clock.millis())
            TransferFormat.MARKDOWN -> MarkdownTransfer.decode(documents.readMarkdown(uri), clock.millis())
        }
        require(format == TransferFormat.JSON || backup.recordCount > 0) { "No records were found to import." }
        pending = backup
        _state.update { it.copy(preview = "${backup.collections.size} collections, ${backup.items.size} items, ${backup.notes.size} notes, and ${backup.facts.size} facts.") }
        "File validated. Choose how to import it."
    }

    fun cancelImport() { pending = null; _state.update { it.copy(preview = null) } }

    fun confirmImport(mode: ImportMode) = run {
        val backup = checkNotNull(pending)
        val count = transfers.import(backup, mode)
        pending = null
        _state.update { it.copy(preview = null) }
        "$count records imported."
    }
}
