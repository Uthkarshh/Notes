package com.notesapp.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.notesapp.data.repository.NewItem
import com.notesapp.domain.model.CategoryType
import com.notesapp.domain.model.INBOX_STATUS
import com.notesapp.domain.model.StatusRole
import com.notesapp.domain.model.StatusSchema
import com.notesapp.domain.template.CollectionTemplates
import com.notesapp.domain.usecase.SeedDefaultCollections
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CollectionRepositoryTest : DataTestBase() {

    @Test
    fun newCollectionsAreAppendedInCreationOrder() = runTest {
        collection("movies")
        collection("books")
        collection("places")
        assertEquals(listOf("Movies", "Books", "Places"), env.collections.observeAll().first().map { it.name })
        assertEquals(listOf(0, 1, 2), env.collections.getAll().map { it.sortOrder })
    }

    @Test
    fun templatesProduceTheDocumentedStatuses() = runTest {
        val things = collection("things_to_buy")
        assertEquals(CategoryType.BUY, things.categoryType)
        assertEquals(
            listOf("Considering", "To Buy", "Bought", "Passed"),
            things.schema.statuses.map { it.label },
        )
    }

    @Test
    fun removedStatusesMoveItemsToTheClosestRemainingStatus() = runTest {
        val movies = collection()
        val done = env.items.create(NewItem("Seen", movies))
        val dropped = env.items.create(NewItem("Dropped one", movies))
        env.items.setStatus(done, "watched")
        env.items.setStatus(dropped, "dropped")
        val completedAt = env.items.get(done)!!.completedAt

        env.collections.update(
            id = movies.id,
            name = "Films",
            categoryType = CategoryType.MOVIE,
            iconName = "movie",
            colorHex = "#112233",
            schema = StatusSchema(
                listOf(
                    StatusSchema.status("Want to Watch", StatusRole.BACKLOG),
                    StatusSchema.status("Watching", StatusRole.ACTIVE),
                    StatusSchema.status("Finished", StatusRole.DONE),
                ),
            ),
        )

        val updated = checkNotNull(env.collections.get(movies.id))
        assertEquals("Films", updated.name)
        assertEquals("#112233", updated.colorHex)

        val finished = checkNotNull(env.items.get(done))
        assertEquals("same role keeps the item completed", "finished", finished.status)
        assertEquals(completedAt, finished.completedAt)

        val reset = checkNotNull(env.items.get(dropped))
        assertEquals("no dropped status left, so back to the start", "want_to_watch", reset.status)
        assertNull(reset.completedAt)
    }

    @Test
    fun changingAStatusRoleRederivesCompletion() = runTest {
        val movies = collection()
        val id = env.items.create(NewItem("Heat", movies))
        env.items.setStatus(id, "watching")
        assertNull(env.items.get(id)!!.completedAt)

        val rolesChanged = StatusSchema(
            movies.schema.statuses.map { if (it.key == "watching") it.copy(role = StatusRole.DONE) else it },
        )
        env.collections.update(movies.id, movies.name, movies.categoryType, movies.iconName, movies.colorHex, rolesChanged)

        assertNotNull(env.items.get(id)!!.completedAt)
    }

    @Test
    fun deletingACollectionCanKeepItsItemsInTheInbox() = runTest {
        val movies = collection()
        val id = env.items.create(NewItem("Arrival", movies, tags = listOf("scifi")))
        env.items.setStatus(id, "watched")

        env.collections.delete(movies.id, deleteItems = false)

        assertNull(env.collections.get(movies.id))
        val item = checkNotNull(env.items.get(id))
        assertNull(item.collectionId)
        assertEquals(INBOX_STATUS, item.status)
        assertNull(item.completedAt)
        assertEquals("still searchable and tagged", 1, env.search.search("arrival").size)
        assertEquals(listOf("scifi"), env.items.observeInbox().first().single().tags)
    }

    @Test
    fun deletingACollectionCanRemoveItsItemsEverywhere() = runTest {
        val movies = collection()
        val books = collection("books")
        val a = env.items.create(NewItem("Arrival", movies, tags = listOf("only-movies", "shared")))
        val b = env.items.create(NewItem("Dune", books, tags = listOf("shared")))

        env.collections.delete(movies.id, deleteItems = true)

        assertNull(env.items.get(a))
        assertNotNull(env.items.get(b))
        assertTrue(env.search.search("arrival").isEmpty())
        assertEquals(1, env.search.search("dune").size)
        assertEquals(listOf("shared"), env.db.tagDao().observeAll().first().map { it.name })
    }

    @Test
    fun itemCountsAreAvailableForConfirmationDialogs() = runTest {
        val movies = collection()
        env.items.create(NewItem("A", movies))
        env.items.create(NewItem("B", movies))
        assertEquals(2, env.collections.itemCount(movies.id))
    }

    @Test
    fun seedingCreatesTheFourDefaultCollectionsExactlyOnce() = runTest {
        val seed = SeedDefaultCollections(env.collections, env.settings)
        seed()
        seed()

        assertEquals(
            listOf("Movies", "Books", "Places", "Things to Buy"),
            env.collections.getAll().map { it.name },
        )
        assertTrue(env.settings.isSeeded())
    }

    @Test
    fun seedingNeverAddsToExistingData() = runTest {
        env.collections.createFromTemplate(CollectionTemplates.byId("games")!!)
        SeedDefaultCollections(env.collections, env.settings)()
        assertEquals(listOf("Games"), env.collections.getAll().map { it.name })
    }

    @Test
    fun deletingTheLastCollectionDoesNotReseed() = runTest {
        val seed = SeedDefaultCollections(env.collections, env.settings)
        seed()
        env.collections.getAll().forEach { env.collections.delete(it.id, deleteItems = true) }
        seed()
        assertTrue(env.collections.getAll().isEmpty())
    }
}
