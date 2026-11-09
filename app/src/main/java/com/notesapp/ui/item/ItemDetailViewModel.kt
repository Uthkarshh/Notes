package com.notesapp.ui.item

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.notesapp.data.db.entity.CollectionEntity
import com.notesapp.data.repository.CollectionRepository
import com.notesapp.data.repository.ItemRepository
import com.notesapp.domain.model.ItemWithTags
import com.notesapp.domain.model.Priority
import com.notesapp.ui.navigation.ItemDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.notesapp.di.ApplicationScope
import com.notesapp.domain.capture.ItemEditorSession
import com.notesapp.domain.capture.ItemEditorStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

data class ItemDetailUiState(
    val loading: Boolean = true,
    /** Null once loading finished means the item no longer exists. */
    val entry: ItemWithTags? = null,
    /** The collection the item belongs to; null for inbox items. */
    val collection: CollectionEntity? = null,
    val collections: List<CollectionEntity> = emptyList(),
    /** Tags already used by other items, offered as one-tap suggestions. */
    val knownTags: List<String> = emptyList(),
)

@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val itemRepository: ItemRepository,
    private val collectionRepository: CollectionRepository,
    knowledgeRepository: com.notesapp.data.repository.KnowledgeRepository,
    @ApplicationScope applicationScope: CoroutineScope,
    private val actions: ItemActions,
) : ViewModel() {

    val itemId: Long = savedStateHandle.toRoute<ItemDetailRoute>().itemId
    private val persistenceScope = CoroutineScope(applicationScope.coroutineContext + Dispatchers.Main.immediate)
    val editor = ItemEditorSession(object : ItemEditorStore {
        private var journalled = emptyMap<String, String>()
        override suspend fun read() = itemRepository.editorValues(itemId)
        override suspend fun draft() = itemRepository.editorDraft(itemId).also { journalled = it }
        override suspend fun journal(fields: Map<String, String>) {
            itemRepository.journalEditor(itemId, fields, journalled)
            journalled = fields
        }
        override suspend fun commit(fields: Map<String, String>) { itemRepository.commitEditor(itemId, fields) }
        override fun changes() = itemRepository.observeItem(itemId).map { Unit }
    }, persistenceScope)

    private val entry = itemRepository.observeItem(itemId)
    val linkedNotes = knowledgeRepository.observeNotes(itemId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val collection = entry
        .map { it?.item?.collectionId }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else collectionRepository.observe(id) }

    val uiState: StateFlow<ItemDetailUiState> = combine(
        entry,
        collection,
        collectionRepository.observeAll(),
        itemRepository.observeTagUsage(),
    ) { entry, collection, all, usage ->
        ItemDetailUiState(
            loading = false,
            entry = entry,
            collection = collection,
            collections = all,
            knownTags = usage.map { it.name },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItemDetailUiState())

    fun setStatus(key: String) {
        change("Status updated") { itemRepository.setStatus(itemId, key, requestReflection = true) }
    }

    fun setRating(rating: Float?) {
        viewModelScope.launch { editor.mutate { itemRepository.setRating(itemId, rating) } }
    }

    fun setPriority(priority: Priority) {
        viewModelScope.launch { editor.mutate { itemRepository.setPriority(itemId, priority) } }
    }

    fun setFavorite(favorite: Boolean) {
        viewModelScope.launch { editor.mutate { itemRepository.setFavorite(itemId, favorite) } }
    }

    fun setArchived(archived: Boolean) {
        change(if (archived) "Item archived" else "Item unarchived") { itemRepository.setArchived(itemId, archived) }
    }

    fun setReminder(at: Long?) { change("Reminder updated") { itemRepository.setReminder(itemId, at) } }

    /** Files the item into [collection] (or the Inbox when null); its status restarts at the collection's first. */
    fun moveTo(collection: CollectionEntity?) {
        change("Moved to ${collection?.name ?: "Inbox"}") { itemRepository.moveToCollection(listOf(itemId), collection) }
    }

    fun delete() {
        viewModelScope.launch { editor.mutate { itemRepository.delete(itemId) } }
    }

    private fun change(label: String, action: suspend () -> Unit) {
        viewModelScope.launch { editor.mutate { actions.change(itemId, label, action) } }
    }

    suspend fun applyMetadata(expected: Map<String, String>, fields: Map<String, String>): Map<String, String>? {
        var conflict: Map<String, String>? = null
        check(editor.mutate {
            check(fields.keys.none { it in editor.state.value.errors }) { "Finish the highlighted local detail before replacing it with provider details." }
            val current = editor.state.value.fields
            if (com.notesapp.domain.metadata.MetadataReview.conflicts(expected, current, fields.keys).isNotEmpty()) conflict = current
            else actions.change(itemId, "Details updated") { conflict = itemRepository.applyProviderFields(itemId, expected, fields) }
        }) { "Couldn't apply these details. Try again." }
        return conflict
    }

    suspend fun applyProviderImage(uri: String?, expected: String?): Boolean {
        var applied = false
        check(editor.mutate { actions.change(itemId, "Cover updated") {
            applied = itemRepository.setImageIfUnchanged(itemId, uri, expected)
        } }) { "Couldn't save this cover. Try again." }
        return applied
    }

    suspend fun applyImage(uri: String?) {
        check(editor.mutate { actions.change(itemId, "Cover updated") { itemRepository.setImage(itemId, uri) } }) {
            "Couldn't save this cover. Try again."
        }
    }

    fun flush() { persistenceScope.launch { editor.flush() } }
    override fun onCleared() { editor.close() }
}
