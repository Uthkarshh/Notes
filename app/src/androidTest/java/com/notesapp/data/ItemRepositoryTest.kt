package com.notesapp.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.notesapp.data.repository.NewItem
import com.notesapp.domain.model.INBOX_STATUS
import com.notesapp.domain.model.Priority
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ItemRepositoryTest : DataTestBase() {

    @Test
    fun inboxItemsHaveNoCollectionUntilTheyAreFiled() = runTest {
        val id = env.items.create(NewItem("Call mom"))
        val inboxed = checkNotNull(env.items.get(id))
        assertNull(inboxed.collectionId)
        assertEquals(INBOX_STATUS, inboxed.status)
        assertEquals(1, env.items.observeInboxCount().first())

        val movies = collection()
        env.items.moveToCollection(listOf(id), movies)

        val filed = checkNotNull(env.items.get(id))
        assertEquals(movies.id, filed.collectionId)
        assertEquals("want_to_watch", filed.status)
        assertEquals(0, env.items.observeInboxCount().first())
        assertTrue(env.items.observeInbox().first().isEmpty())
    }

    @Test
    fun newItemsStartInTheCollectionsInitialStatus() = runTest {
        val books = collection("books")
        val id = env.items.create(NewItem("Dune", books, priority = Priority.HIGH, url = "https://example.com"))
        val item = checkNotNull(env.items.get(id))
        assertEquals("want_to_read", item.status)
        assertEquals(Priority.HIGH, item.priorityLevel)
        assertEquals("https://example.com", item.externalUrl)
        assertEquals(item.createdAt, item.updatedAt)
    }

    @Test
    fun completedAtFollowsTheStatusRole() = runTest {
        val movies = collection()
        val id = env.items.create(NewItem("Heat", movies))

        env.items.setStatus(id, "watching")
        assertNull(env.items.get(id)!!.completedAt)

        env.clock.advanceDays(1)
        env.items.setStatus(id, "watched")
        val done = checkNotNull(env.items.get(id))
        assertEquals("watched", done.status)
        assertNotNull(done.completedAt)

        env.clock.advanceDays(1)
        env.items.setStatus(id, "watched")
        assertEquals("re-selecting the same status keeps the original completion time", done.completedAt, env.items.get(id)!!.completedAt)

        env.items.setStatus(id, "dropped")
        assertNull("dropped is terminal but not a completion", env.items.get(id)!!.completedAt)
    }

    @Test
    fun invalidStatusKeysAndInboxItemsAreIgnored() = runTest {
        val movies = collection()
        val filed = env.items.create(NewItem("Heat", movies))
        env.items.setStatus(filed, "reading")
        assertEquals("want_to_watch", env.items.get(filed)!!.status)

        val inbox = env.items.create(NewItem("Loose thought"))
        env.items.setStatus(inbox, "watching")
        assertEquals(INBOX_STATUS, env.items.get(inbox)!!.status)
    }

    @Test
    fun acceptingAPickStartsBacklogItemsAndRecordsTheDraw() = runTest {
        val movies = collection()
        val id = env.items.create(NewItem("Heat", movies))

        assertEquals("watching", env.items.acceptPick(id))

        val item = checkNotNull(env.items.get(id))
        assertEquals(1, item.selectedCount)
        assertNotNull(item.lastSelectedAt)
    }

    @Test
    fun acceptingAnAlreadyStartedItemOnlyRecordsTheDraw() = runTest {
        val movies = collection()
        val id = env.items.create(NewItem("Heat", movies))
        env.items.setStatus(id, "watched")

        assertEquals("watched", env.items.acceptPick(id))
        assertEquals(1, env.items.get(id)!!.selectedCount)
        assertNull(env.items.acceptPick(9999))
    }

    @Test
    fun tagsAreCaseInsensitivelyUniqueAndSharedBetweenItems() = runTest {
        val movies = collection()
        env.items.create(NewItem("Arrival", movies, tags = listOf("SciFi")))
        env.items.create(NewItem("Dune", movies, tags = listOf("scifi", "epic")))

        assertEquals(listOf("epic", "SciFi"), env.db.tagDao().observeAll().first().map { it.name })
        val usage = env.items.observeTagUsage().first().associate { it.name to it.count }
        assertEquals(mapOf("epic" to 1, "SciFi" to 2), usage)
    }

    @Test
    fun listsExposeSortedTagNames() = runTest {
        val movies = collection()
        env.items.create(NewItem("Arrival", movies, tags = listOf("scifi", "Alien", "drama")))
        val row = env.items.observeCollectionItems(movies.id).first().single()
        assertEquals(listOf("Alien", "drama", "scifi"), row.tags)
    }

    @Test
    fun statusCountsGroupByCollectionAndStatusAndSkipArchivedAndInbox() = runTest {
        val movies = collection()
        val books = collection("books")
        val a = env.items.create(NewItem("A", movies))
        env.items.create(NewItem("B", movies))
        val c = env.items.create(NewItem("C", movies))
        env.items.create(NewItem("D", books))
        env.items.create(NewItem("inbox"))
        env.items.setStatus(a, "watched")
        env.items.setArchived(c, true)

        val counts = env.items.observeStatusCounts().first()
            .associate { (it.collectionId to it.status) to it.count }
        assertEquals(
            mapOf(
                (movies.id to "watched") to 1,
                (movies.id to "want_to_watch") to 1,
                (books.id to "want_to_read") to 1,
            ),
            counts,
        )
    }

    @Test
    fun inProgressQueryReturnsOnlyFiledVisibleItemsWithMatchingStatusKeys() = runTest {
        val movies = collection()
        val a = env.items.create(NewItem("A", movies))
        val b = env.items.create(NewItem("B", movies))
        val c = env.items.create(NewItem("C", movies))
        env.items.create(NewItem("inbox"))
        env.items.setStatus(a, "watching")
        env.items.setStatus(b, "watching")
        env.items.setStatus(c, "watching")
        env.items.setArchived(c, true)

        val rows = env.items.observeByStatusKeys(setOf("watching", "planned")).first()
        assertEquals(setOf(a, b), rows.map { it.item.id }.toSet())
        assertTrue(env.items.observeByStatusKeys(emptySet()).first().isEmpty())
    }

    @Test
    fun archivedItemsLeaveListsButStayAvailableToTheRandomizer() = runTest {
        val movies = collection()
        val id = env.items.create(NewItem("Heat", movies))
        env.items.setArchived(id, true)

        assertTrue(env.items.observeCollectionItems(movies.id).first().isEmpty())
        assertEquals(1, env.items.getAllFiled().size)
    }

    @Test
    fun savingDetailsDoesNotOverwriteInlineEditedFields() = runTest {
        val movies = collection()
        val id = env.items.create(NewItem("Heat", movies))
        env.items.setRating(id, 4.5f)
        env.items.setPriority(id, Priority.URGENT)
        env.items.setFavorite(id, true)
        env.items.setStatus(id, "watching")

        env.items.saveDetails(id, "  Heat (1995) ", "  Great  ", " https://x.test ", listOf("crime"))

        val item = checkNotNull(env.items.get(id))
        assertEquals("Heat (1995)", item.title)
        assertEquals("Great", item.description)
        assertEquals("https://x.test", item.externalUrl)
        assertEquals(4.5f, item.rating)
        assertEquals(Priority.URGENT, item.priorityLevel)
        assertTrue(item.isFavorite)
        assertEquals("watching", item.status)
    }

    @Test
    fun blankTitlesAreRejectedOnSave() = runTest {
        val movies = collection()
        val id = env.items.create(NewItem("Heat", movies))
        env.items.saveDetails(id, "   ", null, null, emptyList())
        assertEquals("Heat", env.items.get(id)!!.title)
    }

    @Test
    fun deletingAnItemRemovesItsTagLinksAndUnusedTags() = runTest {
        val movies = collection()
        val a = env.items.create(NewItem("A", movies, tags = listOf("shared", "only-a")))
        env.items.create(NewItem("B", movies, tags = listOf("shared")))

        env.items.delete(a)

        assertEquals(listOf("shared"), env.db.tagDao().observeAll().first().map { it.name })
        assertNull(env.items.get(a))
    }

    @Test
    fun movingItemsResetsStatusAndCompletion() = runTest {
        val movies = collection()
        val books = collection("books")
        val id = env.items.create(NewItem("Dune", movies))
        env.items.setStatus(id, "watched")

        env.items.moveToCollection(listOf(id), books)

        val item = checkNotNull(env.items.get(id))
        assertEquals(books.id, item.collectionId)
        assertEquals("want_to_read", item.status)
        assertNull(item.completedAt)
    }
}
