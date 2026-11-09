package com.notesapp.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notesapp.data.db.entity.CollectionEntity
import com.notesapp.data.repository.CollectionRepository
import com.notesapp.data.repository.ItemRepository
import com.notesapp.domain.model.Priority
import com.notesapp.domain.random.PickRandomItem
import com.notesapp.domain.random.RandomFilter
import com.notesapp.ui.surprise.SurpriseSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

enum class StatusMode(val label: String) {
    NOT_FINISHED("Not finished"),
    UNSTARTED("Not started"),
    ANY("Any status"),
}

/** The filter builder of the Discover screen ("I want a [collection] that is [status] tagged [tag]..."). */
@kotlinx.serialization.Serializable
data class DiscoverFilters(
    val collectionId: Long? = null,
    val status: StatusMode = StatusMode.NOT_FINISHED,
    val favoritesOnly: Boolean = false,
    val highPriorityOnly: Boolean = false,
    val tags: Set<String> = emptySet(),
    val cooldownDays: Int = 0,
    val maxRuntime: Int? = null,
    val maxPrice: Double? = null,
    val ageMode: Int = 0,
    val currency: String? = null,
    val category: com.notesapp.domain.model.CategoryType? = null,
) {
    fun toRandomFilter(now: Long) = RandomFilter(
        categoryType = category, currency = currency,
        collectionIds = setOfNotNull(collectionId),
        excludeFinished = status != StatusMode.ANY,
        unstartedOnly = status == StatusMode.UNSTARTED,
        favoritesOnly = favoritesOnly,
        minPriority = if (highPriorityOnly) Priority.HIGH else null,
        tags = tags,
        cooldownDays = cooldownDays,
        maxRuntimeMinutes = maxRuntime,
        maxPrice = maxPrice,
        addedAfter = if (ageMode == 1) now - 30 * 86_400_000L else null,
        addedBefore = if (ageMode == 2) now - 90 * 86_400_000L else null,
    )
}

data class DiscoverUiState(
    val collections: List<CollectionEntity> = emptyList(),
    /** Tags that at least one item carries. */
    val tags: List<String> = emptyList(),
    val filters: DiscoverFilters = DiscoverFilters(),
    /** How many items the current filters would draw from. */
    val matchCount: Int = 0,
    val currencies: List<String> = emptyList(),
)

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val savedState: androidx.lifecycle.SavedStateHandle,
    private val clock: java.time.Clock,
    collectionRepository: CollectionRepository,
    itemRepository: ItemRepository,
) : ViewModel() {

    val surprise = SurpriseSession(itemRepository, collectionRepository, viewModelScope)

    private val filters = savedState.getStateFlow("discover_filters", "{}").let { source ->
        source.map { runCatching { kotlinx.serialization.json.Json.decodeFromString<DiscoverFilters>(it) }.getOrDefault(DiscoverFilters()) }
    }
    private val instant = MutableStateFlow(clock.millis())
    private fun change(next: DiscoverFilters) {
        surprise.dismiss()
        instant.value = clock.millis()
        savedState["discover_filters"] = kotlinx.serialization.json.Json.encodeToString(next)
    }
    private var current = DiscoverFilters()
    private var rows: List<com.notesapp.domain.model.ItemWithTags> = emptyList()
    private var allCollections: List<CollectionEntity> = emptyList()
    fun previewCount(filters: DiscoverFilters): Int = PickRandomItem.candidates(rows, allCollections, filters.toRandomFilter(instant.value), instant.value).size



    val uiState: StateFlow<DiscoverUiState> = combine(
        collectionRepository.observeAll(),
        itemRepository.observeTagUsage(),
        itemRepository.observeAllFiled(),
        combine(filters, instant) { f, at -> f to at },
    ) { collections, tagUsage, items, context ->
        val (selected, at) = context
        // A collection that was deleted while selected simply stops filtering.
        val effective = if (selected.collectionId != null && collections.none { it.id == selected.collectionId }) {
            selected.copy(collectionId = null)
        } else {
            selected
        }
        rows = items
        allCollections = collections
        current = effective
        if (effective != selected) {
            savedState["discover_filters"] = kotlinx.serialization.json.Json.encodeToString(effective)
            surprise.dismiss()
        }
        DiscoverUiState(
            collections = collections,
            tags = tagUsage.map { it.name },
            filters = effective,
            matchCount = PickRandomItem.candidates(items, collections, effective.toRandomFilter(at), at).size,
            currencies = items.mapNotNull { com.notesapp.domain.metadata.ItemMetadata.decode(it.item.metadataJson).currency?.trim()?.uppercase()?.takeIf { it.isNotBlank() } }.distinct().sorted(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiscoverUiState())

    fun applyFilters(value: DiscoverFilters) = change(value)
    fun setCollection(id: Long?) = change(current.copy(collectionId = id))
    fun setStatus(status: StatusMode) = change(current.copy(status = status))
    fun toggleFavorites() = change(current.copy(favoritesOnly = !current.favoritesOnly))
    fun toggleHighPriority() = change(current.copy(highPriorityOnly = !current.highPriorityOnly))
    fun setCooldown(days: Int) = change(current.copy(cooldownDays = days))
    fun setRuntime(minutes: Int?) = change(current.copy(maxRuntime = minutes))
    fun setPrice(price: Double?) = change(current.copy(maxPrice = price))
    fun setAge(mode: Int) = change(current.copy(ageMode = mode))
    fun toggleTag(tag: String) = change(current.copy(tags = if (tag in current.tags) current.tags - tag else current.tags + tag))
    fun draw() { instant.value = clock.millis(); surprise.draw(current.toRandomFilter(instant.value)) }
}

enum class DiscoverPreset(val label: String, val filters: DiscoverFilters) {
    ANYTHING("Anything", DiscoverFilters()),
    QUICK_WATCH("Quick watch", DiscoverFilters(maxRuntime = 90, category = com.notesapp.domain.model.CategoryType.MOVIE)),
    FAVORITES("Favorites", DiscoverFilters(favoritesOnly = true)),
    FREE("Free", DiscoverFilters(maxPrice = 0.0)),
    FORGOTTEN("Forgotten", DiscoverFilters(status = StatusMode.UNSTARTED, ageMode = 2)),
}
