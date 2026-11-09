package com.notesapp.domain.capture

import com.notesapp.data.db.entity.CollectionEntity
import com.notesapp.data.db.entity.ItemEntity
import com.notesapp.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class InboxSuggestionsTest {
    private val movies = CollectionEntity(1, "Movies", CategoryType.MOVIE, "movie", "#000000", StatusSchema.DEFAULT.toJson(), 0, 0)
    private val books = movies.copy(id = 2, name = "Books", categoryType = CategoryType.BOOK)
    private val places = movies.copy(id = 3, name = "Places", categoryType = CategoryType.PLACE)
    private val collections = listOf(movies, books, places)
    private fun entry(title: String, metadata: String? = null) = ItemWithTags(ItemEntity(id = 1, title = title, status = "INBOX", metadataJson = metadata, createdAt = 0, updatedAt = 0), emptyList())
    @Test fun savedCaptureHintsOutrankRecentFilingDestinations() {
        val ranked = InboxSuggestions.rank(entry("Dune", """{"captureCategory":"BOOK"}"""), collections, listOf(1, 3))
        assertEquals(listOf(2L, 1L), ranked.map { it.collection.id })
        assertTrue(ranked.first().inferred)
        assertEquals("Recent", ranked[1].reason)
        assertFalse(ranked[1].inferred)
    }
    @Test fun explicitDestinationOutranksCategoryAndDuplicatesAreRemoved() {
        val ranked = InboxSuggestions.rank(entry("Dune", """{"captureCategory":"BOOK","captureCollection":"places"}"""), collections, listOf(3))
        assertEquals(listOf(3L, 2L), ranked.map { it.collection.id })
    }
    @Test fun noHintDoesNotPresentDisplayOrderAsAnInference() {
        val ranked = InboxSuggestions.rank(entry("Call mom"), collections, listOf(999))
        assertEquals(2, ranked.size)
        assertTrue(ranked.none { it.inferred })
    }
    @Test fun olderRawCapturesStillUseTheLocalParser() {
        val ranked = InboxSuggestions.rank(entry("Visit Kyoto"), collections, emptyList())
        assertEquals(3L, ranked.first().collection.id)
        assertTrue(ranked.first().inferred)
    }
}
