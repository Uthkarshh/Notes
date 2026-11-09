package com.notesapp.domain.model

enum class SortField(val label: String, val defaultDescending: Boolean) {
    DATE_ADDED("Date added", defaultDescending = true),
    PRIORITY("Priority", defaultDescending = true),
    RATING("Rating", defaultDescending = true),
    TITLE("Title", defaultDescending = false),
    RUNTIME("Runtime", defaultDescending = false),
    PRICE("Price", defaultDescending = false),
}

@kotlinx.serialization.Serializable
data class SortSpec(
    val field: SortField = SortField.DATE_ADDED,
    val descending: Boolean = field.defaultDescending,
) {
    /** Choosing the active field again flips the direction; choosing another field starts at its natural direction. */
    fun select(newField: SortField): SortSpec =
        if (newField == field) copy(descending = !descending) else SortSpec(newField)
}

/** Filters of a collection's item list. Status keys and tags each mean "any of the selected". */
@kotlinx.serialization.Serializable
data class ItemFilters(
    val statusKeys: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val favoritesOnly: Boolean = false,
) {
    val isActive: Boolean get() = statusKeys.isNotEmpty() || tags.isNotEmpty() || favoritesOnly

    fun toggleStatus(key: String) = copy(statusKeys = statusKeys.toggle(key))

    fun toggleTag(tag: String): ItemFilters {
        val existing = tags.firstOrNull { it.equals(tag, ignoreCase = true) }
        return copy(tags = if (existing != null) tags - existing else tags + tag)
    }

    private fun Set<String>.toggle(value: String) = if (value in this) this - value else this + value
}

object ItemListing {

    fun apply(items: List<ItemWithTags>, filters: ItemFilters, sort: SortSpec): List<ItemWithTags> {
        val wantedTags = filters.tags.map { it.lowercase() }.toSet()
        val filtered = items.filter { entry ->
            val item = entry.item
            (filters.statusKeys.isEmpty() || item.status in filters.statusKeys) &&
                (!filters.favoritesOnly || item.isFavorite) &&
                (wantedTags.isEmpty() || entry.tags.any { it.lowercase() in wantedTags })
        }
        return filtered.sortedWith(comparator(sort))
    }

    private fun comparator(sort: SortSpec): Comparator<ItemWithTags> {
        val newestFirst = compareByDescending<ItemWithTags> { it.item.createdAt }.thenBy { -it.item.id }
        val byTitle = compareBy<ItemWithTags, String>(String.CASE_INSENSITIVE_ORDER) { it.item.title }
        val primary: Comparator<ItemWithTags> = when (sort.field) {
            SortField.DATE_ADDED -> if (sort.descending) newestFirst else newestFirst.reversed()
            SortField.TITLE -> if (sort.descending) byTitle.reversed() else byTitle
            SortField.PRIORITY -> {
                val byPriority = compareBy<ItemWithTags> { it.item.priority }
                (if (sort.descending) byPriority.reversed() else byPriority).then(newestFirst)
            }
            // Unrated items always sink to the bottom, in either direction.
            SortField.RATING -> Comparator<ItemWithTags> { a, b ->
                val ra = a.item.rating
                val rb = b.item.rating
                when {
                    ra == null && rb == null -> 0
                    ra == null -> 1
                    rb == null -> -1
                    sort.descending -> rb.compareTo(ra)
                    else -> ra.compareTo(rb)
                }
            }.then(newestFirst)
            SortField.RUNTIME, SortField.PRICE -> Comparator<ItemWithTags> { a, b ->
                fun value(row: ItemWithTags): Double? = com.notesapp.domain.metadata.ItemMetadata.decode(row.item.metadataJson).let {
                    if (sort.field == SortField.RUNTIME) it.runtimeMinutes?.toDouble() else it.price
                }
                val left = value(a); val right = value(b)
                when { left == null && right == null -> 0; left == null -> 1; right == null -> -1; sort.descending -> right.compareTo(left); else -> left.compareTo(right) }
            }.then(newestFirst)
        }
        return primary
    }
}
