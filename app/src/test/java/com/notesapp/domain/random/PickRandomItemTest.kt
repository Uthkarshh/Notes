package com.notesapp.domain.random

import com.notesapp.data.db.entity.CollectionEntity
import com.notesapp.data.db.entity.ItemEntity
import com.notesapp.domain.model.CategoryType
import com.notesapp.domain.model.INBOX_STATUS
import com.notesapp.domain.model.ItemWithTags
import com.notesapp.domain.model.Priority
import com.notesapp.domain.template.CollectionTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PickRandomItemTest {

    private fun collectionFrom(id: Long, templateId: String) = CollectionTemplates.byId(templateId)!!.let {
        CollectionEntity(
            id = id,
            name = it.name,
            categoryType = it.categoryType,
            iconName = it.iconName,
            colorHex = it.colorHex,
            statusSchemaJson = it.schema.toJson(),
            sortOrder = id.toInt(),
            createdAt = 0,
        )
    }

    private val movies = collectionFrom(1, "movies")
    private val books = collectionFrom(2, "books")
    private val collections = listOf(movies, books)

    private fun item(
        id: Long,
        collection: CollectionEntity?,
        status: String,
        tags: List<String> = emptyList(),
        favorite: Boolean = false,
        archived: Boolean = false,
        priority: Priority = Priority.NONE,
    ) = ItemWithTags(
        ItemEntity(
            id = id,
            collectionId = collection?.id,
            title = "item $id",
            status = status,
            isFavorite = favorite,
            isArchived = archived,
            priority = priority.level,
            createdAt = 0,
            updatedAt = 0,
        ),
        tags,
    )

    private val backlogMovie = item(1, movies, "want_to_watch", tags = listOf("Weekend", "family"))
    private val activeMovie = item(2, movies, "watching", favorite = true)
    private val doneMovie = item(3, movies, "watched")
    private val droppedMovie = item(4, movies, "dropped")
    private val urgentBook = item(5, books, "want_to_read", priority = Priority.HIGH)
    private val archivedMovie = item(6, movies, "want_to_watch", archived = true)
    private val inboxItem = item(7, null, INBOX_STATUS)

    private val items = listOf(backlogMovie, activeMovie, doneMovie, droppedMovie, urgentBook, archivedMovie, inboxItem)

    private fun ids(filter: RandomFilter) =
        PickRandomItem.candidates(items, collections, filter).map { it.first.item.id }.sorted()

    @Test
    fun `default filter skips finished, dropped, archived and inbox items`() {
        assertEquals(listOf(1L, 2L, 5L), ids(RandomFilter()))
    }

    @Test
    fun `turning off the finished filter includes completed and dropped but never archived or inbox`() {
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), ids(RandomFilter(excludeFinished = false)))
    }

    @Test
    fun `unstarted only keeps backlog items`() {
        assertEquals(listOf(1L, 5L), ids(RandomFilter(unstartedOnly = true)))
    }

    @Test
    fun `filters by category and by collection`() {
        assertEquals(listOf(5L), ids(RandomFilter(categoryType = CategoryType.BOOK)))
        assertEquals(listOf(1L, 2L), ids(RandomFilter(collectionIds = setOf(movies.id))))
        assertEquals(emptyList<Long>(), ids(RandomFilter(categoryType = CategoryType.BOOK, collectionIds = setOf(1L))))
    }

    @Test
    fun `favorites only`() {
        assertEquals(listOf(2L), ids(RandomFilter(favoritesOnly = true)))
    }

    @Test
    fun `minimum priority is inclusive`() {
        assertEquals(listOf(5L), ids(RandomFilter(minPriority = Priority.HIGH)))
        assertEquals(listOf(5L), ids(RandomFilter(minPriority = Priority.MEDIUM)))
        assertEquals(emptyList<Long>(), ids(RandomFilter(minPriority = Priority.URGENT)))
    }

    @Test
    fun `tags match any of the wanted tags ignoring case`() {
        assertEquals(listOf(1L), ids(RandomFilter(tags = setOf("weekend"))))
        assertEquals(listOf(1L), ids(RandomFilter(tags = setOf("FAMILY", "nothing"))))
        assertEquals(emptyList<Long>(), ids(RandomFilter(tags = setOf("nothing"))))
    }

    @Test
    fun `status filter uses roles of the owning collection, not label text`() {
        // "dropped" is a terminal role in Movies, but the same key in a custom schema could mean something else.
        val custom = CollectionEntity(
            id = 9,
            name = "Custom",
            categoryType = CategoryType.CUSTOM,
            iconName = "folder",
            colorHex = "#000000",
            statusSchemaJson = """{"statuses":[{"key":"dropped","label":"Parked","role":"BACKLOG"},{"key":"done","label":"Done","role":"DONE"}]}""",
            sortOrder = 9,
            createdAt = 0,
        )
        val parked = item(10, custom, "dropped")
        val result = PickRandomItem.candidates(listOf(parked), listOf(custom), RandomFilter())
        assertEquals(1, result.size)
    }

    @Test
    fun `picks only from candidates and reports the match count`() {
        val result = PickRandomItem.pick(items, collections, RandomFilter(), random = Random(1))
        result as PickResult.Picked
        assertTrue(result.item.item.id in listOf(1L, 2L, 5L))
        assertEquals(3, result.matchCount)
        assertEquals(result.item.item.collectionId, result.collection.id)
    }

    @Test
    fun `skipped items are not drawn again`() {
        repeat(50) { seed ->
            val result = PickRandomItem.pick(
                items, collections, RandomFilter(), skippedIds = setOf(1L, 2L), random = Random(seed),
            ) as PickResult.Picked
            assertEquals(5L, result.item.item.id)
        }
    }

    @Test
    fun `reports exhaustion when everything matching was skipped`() {
        val result = PickRandomItem.pick(items, collections, RandomFilter(), skippedIds = setOf(1L, 2L, 5L))
        assertEquals(PickResult.Exhausted, result)
    }

    @Test
    fun `reports no matches when the filter excludes everything`() {
        val result = PickRandomItem.pick(items, collections, RandomFilter(minPriority = Priority.URGENT))
        assertEquals(PickResult.NoMatches, result)
        assertEquals(PickResult.NoMatches, PickRandomItem.pick(emptyList(), collections, RandomFilter()))
    }

    @Test
    fun `every candidate can be drawn`() {
        val random = Random(42)
        val seen = (1..300).map {
            (PickRandomItem.pick(items, collections, RandomFilter(), random = random) as PickResult.Picked).item.item.id
        }.toSet()
        assertEquals(setOf(1L, 2L, 5L), seen)
    }
}
