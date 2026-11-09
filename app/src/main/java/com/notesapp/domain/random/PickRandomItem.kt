package com.notesapp.domain.random

import com.notesapp.data.db.entity.CollectionEntity
import com.notesapp.domain.model.CategoryType
import com.notesapp.domain.model.ItemWithTags
import com.notesapp.domain.model.Priority
import com.notesapp.domain.model.StatusRole
import kotlin.random.Random

/** Filters for "Surprise Me". The defaults match the spec: skip anything finished, dropped or archived. */
data class RandomFilter(
    /** Empty means any collection. */
    val collectionIds: Set<Long> = emptySet(),
    val categoryType: CategoryType? = null,
    /** Excludes completed, dropped and archived items. */
    val excludeFinished: Boolean = true,
    /** Only items that have not been started (the BACKLOG role). */
    val unstartedOnly: Boolean = false,
    val favoritesOnly: Boolean = false,
    /** Minimum priority; null means any. */
    val minPriority: Priority? = null,
    /** Item must carry at least one of these tags (case-insensitive); empty means any. */
    val tags: Set<String> = emptySet(),
    val cooldownDays: Int = 0,
    val maxRuntimeMinutes: Int? = null,
    val maxPrice: Double? = null,
    /** A positive price ceiling requires an explicit matching currency. Zero means known free. */
    val currency: String? = null,
    val addedAfter: Long? = null,
    val addedBefore: Long? = null,
)

sealed interface PickResult {
    /** [matchCount] is how many items satisfied the filter available for this draw, after session exclusions. */
    data class Picked(
        val item: ItemWithTags,
        val collection: CollectionEntity,
        val matchCount: Int,
    ) : PickResult

    /** Nothing satisfies the filter. */
    data object NoMatches : PickResult

    /** Items match, but all of them were skipped with "Roll again" in this session. */
    data object Exhausted : PickResult
}

/** Picks a uniformly random item that satisfies a [RandomFilter]. Pure logic so it is trivial to test. */
object PickRandomItem {

    fun candidates(
        items: List<ItemWithTags>,
        collections: List<CollectionEntity>,
        filter: RandomFilter,
        now: Long = System.currentTimeMillis(),
    ): List<Pair<ItemWithTags, CollectionEntity>> {
        val byId = collections.associateBy { it.id }
        val wantedTags = filter.tags.map { it.lowercase() }.toSet()
        return items.mapNotNull { row ->
            val item = row.item
            val collection = item.collectionId?.let(byId::get) ?: return@mapNotNull null
            val role = collection.schema.roleOf(item.status)
            val metadata = com.notesapp.domain.metadata.ItemMetadata.decode(item.metadataJson)

            val matches =
                !item.isArchived &&
                (filter.collectionIds.isEmpty() || collection.id in filter.collectionIds) &&
                    (filter.categoryType == null || collection.categoryType == filter.categoryType) &&
                    (!filter.excludeFinished ||
                        (!item.isArchived && role != StatusRole.DONE && role != StatusRole.DROPPED)) &&
                    (!filter.unstartedOnly || role == StatusRole.BACKLOG) &&
                    (!filter.favoritesOnly || item.isFavorite) &&
                    (filter.minPriority == null || item.priority >= filter.minPriority.level) &&
                    (wantedTags.isEmpty() || row.tags.any { it.lowercase() in wantedTags }) &&
                    (filter.cooldownDays <= 0 || item.lastSelectedAt == null || item.lastSelectedAt < now - filter.cooldownDays.coerceAtMost(3650) * 86_400_000L) &&
                    (filter.maxRuntimeMinutes == null || metadata.runtimeMinutes?.let { it <= filter.maxRuntimeMinutes } == true) &&
                    (filter.maxPrice == null || metadata.price?.let { price ->
                        price <= filter.maxPrice && (filter.maxPrice == 0.0 ||
                            !filter.currency.isNullOrBlank() && metadata.currency?.equals(filter.currency, ignoreCase = true) == true)
                    } == true) &&
                    (filter.addedAfter == null || item.createdAt >= filter.addedAfter) &&
                    (filter.addedBefore == null || item.createdAt < filter.addedBefore)

            if (matches) row to collection else null
        }
    }

    fun pick(
        items: List<ItemWithTags>,
        collections: List<CollectionEntity>,
        filter: RandomFilter,
        skippedIds: Set<Long> = emptySet(),
        random: Random = Random.Default,
        now: Long = System.currentTimeMillis(),
    ): PickResult {
        val all = candidates(items, collections, filter, now)
        if (all.isEmpty()) return PickResult.NoMatches
        val available = all.filter { it.first.item.id !in skippedIds }
        if (available.isEmpty()) return PickResult.Exhausted
        val (item, collection) = available[random.nextInt(available.size)]
        return PickResult.Picked(item, collection, available.size)
    }
}
