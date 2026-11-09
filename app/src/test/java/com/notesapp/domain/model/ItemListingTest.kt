package com.notesapp.domain.model

import com.notesapp.data.db.entity.ItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemListingTest {

    private fun item(
        id: Long,
        title: String,
        created: Long = id,
        status: String = "want_to_watch",
        rating: Float? = null,
        priority: Priority = Priority.NONE,
        favorite: Boolean = false,
        tags: List<String> = emptyList(),
    ) = ItemWithTags(
        ItemEntity(
            id = id,
            collectionId = 1,
            title = title,
            status = status,
            rating = rating,
            priority = priority.level,
            isFavorite = favorite,
            createdAt = created,
            updatedAt = created,
        ),
        tags,
    )

    private val a = item(1, "banana", created = 100, rating = 3f, priority = Priority.LOW, tags = listOf("fruit"))
    private val b = item(2, "Apple", created = 300, rating = 5f, priority = Priority.URGENT, favorite = true, status = "watching")
    private val c = item(3, "cherry", created = 200, rating = null, priority = Priority.HIGH, tags = listOf("Fruit", "red"))
    private val d = item(4, "date", created = 400, rating = 3f, priority = Priority.NONE, status = "watched")
    private val all = listOf(a, b, c, d)

    private fun ids(sort: SortSpec, filters: ItemFilters = ItemFilters()) =
        ItemListing.apply(all, filters, sort).map { it.item.id }

    @Test
    fun `date added defaults to newest first and can be reversed`() {
        assertEquals(listOf(4L, 2L, 3L, 1L), ids(SortSpec(SortField.DATE_ADDED)))
        assertEquals(listOf(1L, 3L, 2L, 4L), ids(SortSpec(SortField.DATE_ADDED, descending = false)))
    }

    @Test
    fun `title sorts case-insensitively`() {
        assertEquals(listOf(2L, 1L, 3L, 4L), ids(SortSpec(SortField.TITLE)))
        assertEquals(listOf(4L, 3L, 1L, 2L), ids(SortSpec(SortField.TITLE, descending = true)))
    }

    @Test
    fun `priority puts urgent first and breaks ties by newest`() {
        assertEquals(listOf(2L, 3L, 1L, 4L), ids(SortSpec(SortField.PRIORITY)))
        assertEquals(listOf(4L, 1L, 3L, 2L), ids(SortSpec(SortField.PRIORITY, descending = false)))
    }

    @Test
    fun `rating sorts best first and unrated items always come last`() {
        // b (5.0) first, then a and d (3.0) by newest, then unrated c.
        assertEquals(listOf(2L, 4L, 1L, 3L), ids(SortSpec(SortField.RATING)))
        assertEquals(listOf(4L, 1L, 2L, 3L), ids(SortSpec(SortField.RATING, descending = false)))
    }

    @Test
    fun `selecting the active field flips direction and another field resets it`() {
        val byTitle = SortSpec(SortField.TITLE)
        assertFalse(byTitle.descending)
        assertTrue(byTitle.select(SortField.TITLE).descending)
        assertTrue(byTitle.select(SortField.RATING).descending)
        assertEquals(SortField.RATING, byTitle.select(SortField.RATING).field)
    }

    @Test
    fun `status filter keeps any of the selected statuses`() {
        assertEquals(listOf(4L, 2L), ids(SortSpec(), ItemFilters(statusKeys = setOf("watching", "watched"))))
    }

    @Test
    fun `tag filter is any-of and ignores case`() {
        assertEquals(listOf(3L, 1L), ids(SortSpec(), ItemFilters(tags = setOf("fruit"))))
        assertEquals(listOf(3L), ids(SortSpec(), ItemFilters(tags = setOf("RED"))))
    }

    @Test
    fun `favorites filter and combined filters`() {
        assertEquals(listOf(2L), ids(SortSpec(), ItemFilters(favoritesOnly = true)))
        assertEquals(emptyList<Long>(), ids(SortSpec(), ItemFilters(favoritesOnly = true, tags = setOf("red"))))
    }

    @Test
    fun `filter toggles add and remove`() {
        val on = ItemFilters().toggleStatus("x").toggleTag("Red")
        assertTrue(on.isActive)
        val off = on.toggleStatus("x").toggleTag("red")
        assertFalse(off.isActive)
    }
}
