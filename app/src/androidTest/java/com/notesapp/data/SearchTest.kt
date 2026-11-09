package com.notesapp.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.notesapp.data.repository.NewItem
import com.notesapp.data.repository.SearchIndexer
import com.notesapp.domain.model.EntityType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchTest : DataTestBase() {

    @Test
    fun findsItemsByTitlePrefixTagAndAllTerms() = runTest {
        val movies = collection()
        env.items.create(NewItem("Arrival", movies, tags = listOf("scifi")))
        env.items.create(NewItem("The Matrix", movies, tags = listOf("scifi", "classic")))
        env.items.create(NewItem("Amélie", movies))

        assertEquals(listOf("Arrival"), env.search.search("arr").map { it.title })
        assertEquals(2, env.search.search("scifi").size)
        assertEquals(listOf("The Matrix"), env.search.search("matrix classic").map { it.title })
        assertEquals("diacritics are folded", 1, env.search.search("amelie").size)
        assertTrue(env.search.search("zzz").isEmpty())
    }

    @Test
    fun searchIsCaseInsensitiveAndMatchesDescriptions() = runTest {
        val movies = collection()
        env.items.create(NewItem("Heat", movies, description = "Cops and ROBBERS in Los Angeles"))
        assertEquals(1, env.search.search("robbers").size)
        assertEquals(1, env.search.search("ROBB").size)
        assertEquals(1, env.search.search("angeles cops").size)
    }

    @Test
    fun searchesNotesFactsAndItemsTogether() = runTest {
        val movies = collection()
        env.items.create(NewItem("Tripod review", movies))
        env.notes.create("Trip plan", "Pack the camera and a tripod for the sunrise", listOf("travel"))
        env.facts.create("Octopuses have three hearts", source = "Marine biology", topic = "Animals")

        val tripod = env.search.search("tripod")
        assertEquals(setOf(EntityType.ITEM, EntityType.NOTE), tripod.map { it.type }.toSet())

        val facts = env.search.search("hearts")
        assertEquals(EntityType.FACT, facts.single().type)
        assertEquals("Octopuses have three hearts", facts.single().title)

        assertEquals("topic and source are indexed", 1, env.search.search("marine").size)
        assertEquals("tags are indexed", 1, env.search.search("travel").size)
    }

    @Test
    fun snippetsMarkTheMatchedWords() = runTest {
        env.notes.create("Trip plan", "Pack the camera and a tripod for the sunrise")
        val hit = env.search.search("tripod").single()
        assertTrue(hit.snippet, hit.snippet.contains("\uE000tripod\uE001"))
    }

    @Test
    fun recordsOfDifferentTypesWithTheSameIdDoNotCollide() = runTest {
        val movies = collection()
        val itemId = env.items.create(NewItem("Alpha item", movies))
        val noteId = env.notes.create("Alpha note", "body")
        val factId = env.facts.create("Alpha fact")
        assertEquals("fresh tables start at the same id", itemId, noteId)
        assertEquals(itemId, factId)

        val hits = env.search.search("alpha")
        assertEquals(3, hits.size)
        assertEquals(setOf(EntityType.ITEM, EntityType.NOTE, EntityType.FACT), hits.map { it.type }.toSet())
        assertEquals(3, env.db.searchDao().count())
        assertEquals("rowid = id * 4 + type code", 5L, SearchIndexer.rowId(EntityType.ITEM, 1))
    }

    @Test
    fun editingAnItemReplacesItsIndexEntry() = runTest {
        val movies = collection()
        val id = env.items.create(NewItem("Arrival", movies, description = "alien linguist"))

        env.items.saveDetails(id, "Arrival (2016)", "heptapod language", null, listOf("scifi"))

        assertTrue(env.search.search("alien").isEmpty())
        assertEquals(1, env.search.search("heptapod").size)
        assertEquals(1, env.search.search("2016").size)
        assertEquals(1, env.search.search("scifi").size)
        assertEquals(1, env.db.searchDao().count())
    }

    @Test
    fun deletingRecordsRemovesThemFromTheIndex() = runTest {
        val movies = collection()
        val itemId = env.items.create(NewItem("Arrival", movies))
        val noteId = env.notes.create("Arrival notes", "x")
        val factId = env.facts.create("Arrival fact")
        assertEquals(3, env.search.search("arrival").size)

        env.items.delete(itemId)
        env.notes.delete(noteId)
        env.facts.delete(factId)

        assertTrue(env.search.search("arrival").isEmpty())
        assertEquals(0, env.db.searchDao().count())
    }

    @Test
    fun hostileInputNeverThrows() = runTest {
        val movies = collection()
        env.items.create(NewItem("Dark Knight", movies))
        listOf("\"unterminated", "OR", "a AND b", "*", "title:x", "NEAR/2", "(", "-", "dark OR", "\u0000", "\uE000")
            .forEach { query -> env.search.search(query) }
        assertEquals(1, env.search.search("\"dark\" -knight").size)
    }

    @Test
    fun blankQueriesReturnNothing() = runTest {
        assertTrue(env.search.search("").isEmpty())
        assertTrue(env.search.search("   ").isEmpty())
    }
}
