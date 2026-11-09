package com.notesapp.ui.inbox

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notesapp.data.repository.CollectionRepository
import com.notesapp.data.repository.ItemRepository
import com.notesapp.data.prefs.SettingsRepository
import com.notesapp.data.prefs.InboxGestures
import com.notesapp.di.ApplicationScope
import com.notesapp.ui.item.ItemActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val itemsRepository: ItemRepository,
    collectionsRepository: CollectionRepository,
    private val settings: SettingsRepository,
    private val actions: ItemActions,
    private val handle: SavedStateHandle,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {
    val items = itemsRepository.observeInbox().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val collections = collectionsRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recentDestinations = settings.recentFilingDestinations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val gestures = settings.inboxGestures.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InboxGestures())
    val selectedIds = handle.getStateFlow("selected", longArrayOf()).map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, handle.get<LongArray>("selected")?.toSet().orEmpty())
    val selecting = handle.getStateFlow("selecting", false)
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private var retry: (() -> Unit)? = null
    private val ticks = flow { while (currentCoroutineContext().isActive) { emit(itemsRepository.now()); delay(500) } }
    val pendingDeletions = combine(itemsRepository.observePendingDeletions(), ticks) { entries, now ->
        entries.filter { it.expiresAt > now }.groupBy { it.operationId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun toggle(id: Long) {
        if (_busy.value) return
        handle["selecting"] = true
        val selection = handle.get<LongArray>("selected")?.toMutableSet() ?: mutableSetOf()
        if (!selection.add(id)) selection.remove(id)
        handle["selected"] = selection.toLongArray()
    }
    fun startSelection() { handle["selecting"] = true }
    fun clearSelection() { if (!_busy.value) { handle["selected"] = longArrayOf(); handle["selecting"] = false } }
    fun selectAll() { if (!_busy.value) handle["selected"] = items.value.orEmpty().map { it.item.id }.toLongArray() }
    fun retry() { retry?.invoke() }

    fun file(ids: List<Long>, collectionId: Long): Unit = perform({ file(ids, collectionId) }) {
        val result = itemsRepository.fileInbox(ids, collectionId)
        val destination = collections.value.firstOrNull { it.id == collectionId }?.name ?: "collection"
        val label = "${result.changes.size} filed to $destination" + skipped(result.skipped)
        if (result.changes.isEmpty()) actions.report(label)
        else actions.reversible(label) { itemsRepository.undoBatch(result.changes) }
        removeSelection(ids)
        try { if (result.changes.isNotEmpty()) settings.recordFiling(collectionId) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* Filing already committed; recent suggestions are best effort. */ }
    }
    fun delete(ids: List<Long>): Unit = perform({ delete(ids) }) {
        val result = itemsRepository.deleteInbox(ids)
        val label = "${result.count} deleted" + skipped(result.skipped)
        if (result.count == 0) actions.report(label)
        else actions.reversible(label, expiresAt = result.expiresAt) { itemsRepository.undoDeletion(result.operationId) }
        removeSelection(ids)
    }
    fun undoDeletion(id: String): Unit = perform({ undoDeletion(id) }) {
        if (!itemsRepository.undoDeletion(id)) actions.report("The Undo window expired.")
    }
    private fun removeSelection(ids: List<Long>) {
        val remaining = handle.get<LongArray>("selected")?.toSet().orEmpty() - ids.toSet()
        handle["selected"] = remaining.toLongArray()
        if (remaining.isEmpty()) handle["selecting"] = false
    }
    private fun skipped(count: Int) = if (count == 0) "" else "; $count already moved or unavailable"
    private fun perform(retryAction: () -> Unit, action: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        _error.value = null
        applicationScope.launch(Dispatchers.Main.immediate) {
            try { action(); retry = null }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _error.value = if (e is IllegalStateException) e.message ?: "Choose another destination." else "Could not complete this action. Try again."; retry = retryAction }
            finally { _busy.value = false }
        }
    }
}
