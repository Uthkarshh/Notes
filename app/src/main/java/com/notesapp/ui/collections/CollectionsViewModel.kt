package com.notesapp.ui.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notesapp.data.db.entity.CollectionEntity
import com.notesapp.data.repository.CollectionRepository
import com.notesapp.data.repository.ItemRepository
import com.notesapp.domain.model.StatusRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** A collection with the numbers shown on its overview card. */
data class CollectionSummary(
    val collection: CollectionEntity,
    /** Non-archived items. */
    val total: Int,
    /** Items that are neither completed nor dropped. */
    val open: Int,
    val done: Int,
) {
    val progress: Float get() = if (total == 0) 0f else done.toFloat() / total
}

@HiltViewModel
class CollectionsViewModel @Inject constructor(
    collectionRepository: CollectionRepository,
    itemRepository: ItemRepository,
) : ViewModel() {

    /** Null until the first load so the screen does not flash its empty state. */
    val summaries: StateFlow<List<CollectionSummary>?> = combine(
        collectionRepository.observeAll(),
        itemRepository.observeStatusCounts(),
    ) { collections, counts ->
        val byCollection = counts.groupBy { it.collectionId }
        collections.map { collection ->
            val schema = collection.schema
            val rows = byCollection[collection.id].orEmpty()
            CollectionSummary(
                collection = collection,
                total = rows.sumOf { it.count },
                open = rows.filter { !schema.isTerminal(it.status) }.sumOf { it.count },
                done = rows.filter { schema.roleOf(it.status) == StatusRole.DONE }.sumOf { it.count },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
