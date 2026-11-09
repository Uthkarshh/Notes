package com.notesapp.ui.collections

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.notesapp.data.db.entity.CollectionEntity
import com.notesapp.data.repository.CollectionRepository
import com.notesapp.data.repository.ItemRepository
import com.notesapp.domain.model.ItemFilters
import com.notesapp.domain.model.ItemListing
import com.notesapp.domain.model.ItemWithTags
import com.notesapp.domain.model.SortField
import com.notesapp.domain.model.SortSpec
import com.notesapp.ui.navigation.CollectionDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ViewMode(val label: String) {
    LIST("List"),
    GRID("Grid"),
    BOARD("Board"),
}

data class CollectionDetailUiState(
    val loading: Boolean = true,
    /** Null once loading finished means the collection no longer exists. */
    val collection: CollectionEntity? = null,
    /** Items in the collection before filters are applied. */
    val totalCount: Int = 0,
    /** Items after filters and sorting. */
    val items: List<ItemWithTags> = emptyList(),
    /** Every tag used in this collection, for the filter chips. */
    val tags: List<String> = emptyList(),
    val viewMode: ViewMode = ViewMode.LIST,
    val sort: SortSpec = SortSpec(),
    val filters: ItemFilters = ItemFilters(),
)

@HiltViewModel
class CollectionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val collectionRepository: CollectionRepository,
    private val itemRepository: ItemRepository,
    private val settings: com.notesapp.data.prefs.SettingsRepository,
    private val actions: com.notesapp.ui.item.ItemActions,
    @com.notesapp.di.ApplicationScope applicationScope: kotlinx.coroutines.CoroutineScope,
) : ViewModel() {

    private val persistenceScope = kotlinx.coroutines.CoroutineScope(applicationScope.coroutineContext + kotlinx.coroutines.Dispatchers.Main.immediate)
    val collectionId: Long = savedStateHandle.toRoute<CollectionDetailRoute>().collectionId

    val uiState: StateFlow<CollectionDetailUiState> = combine(
        collectionRepository.observe(collectionId),
        itemRepository.observeCollectionItems(collectionId),
        settings.collectionView(collectionId),
    ) { collection, items, preferences ->
        val mode = ViewMode.entries.firstOrNull { it.name == preferences.mode } ?: ViewMode.LIST
        val sortSpec = preferences.sort
        val activeFilters = preferences.filters
        CollectionDetailUiState(
            loading = false,
            collection = collection,
            totalCount = items.size,
            items = ItemListing.apply(items, activeFilters, sortSpec),
            tags = items.flatMap { it.tags }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() },
            viewMode = mode,
            sort = sortSpec,
            filters = activeFilters,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionDetailUiState())

    private fun update(change: (com.notesapp.data.prefs.CollectionViewPreferences) -> com.notesapp.data.prefs.CollectionViewPreferences) {
        persistenceScope.launch { settings.updateCollectionView(collectionId, change) }
    }
    fun setViewMode(mode: ViewMode) = update { it.copy(mode = mode.name) }
    fun selectSort(field: SortField) = update { it.copy(sort = it.sort.select(field)) }
    fun toggleStatusFilter(key: String) = update { it.copy(filters = it.filters.toggleStatus(key)) }
    fun toggleTagFilter(tag: String) = update { it.copy(filters = it.filters.toggleTag(tag)) }
    fun toggleFavoritesOnly() = update { it.copy(filters = it.filters.copy(favoritesOnly = !it.filters.favoritesOnly)) }
    fun clearFilters() = update { it.copy(filters = ItemFilters()) }
    private val changing = mutableSetOf<Long>()
    fun setStatus(itemId: Long, statusKey: String) = changeStatus(itemId, statusKey, null)
    fun moveCard(itemId: Long, from: String, to: String) = changeStatus(itemId, to, from)
    private fun changeStatus(itemId: Long, statusKey: String, expected: String?) {
        if (!changing.add(itemId)) return
        persistenceScope.launch { try { actions.change(itemId, "Status updated") { itemRepository.setStatus(itemId, statusKey, requestReflection = true, expectedStatus = expected, expectedCollectionId = collectionId) } }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { actions.report("Couldn’t update status. Please try again.") }
            finally { changing.remove(itemId) } }
    }

    fun toggleFavorite(entry: ItemWithTags) {
        viewModelScope.launch { itemRepository.setFavorite(entry.item.id, !entry.item.isFavorite) }
    }

    fun undoCapture(itemId: Long) {
        viewModelScope.launch { itemRepository.delete(itemId) }
    }

    suspend fun itemCount(): Int = collectionRepository.itemCount(collectionId)

    fun deleteCollection(deleteItems: Boolean) {
        viewModelScope.launch { collectionRepository.delete(collectionId, deleteItems) }
    }
}
