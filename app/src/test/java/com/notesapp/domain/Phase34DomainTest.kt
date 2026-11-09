package com.notesapp.domain

import com.notesapp.data.db.entity.*
import com.notesapp.data.metadata.MetadataLookup
import com.notesapp.domain.capture.*
import com.notesapp.domain.insights.PersonalInsights
import com.notesapp.domain.metadata.ItemMetadata
import com.notesapp.domain.model.*
import com.notesapp.domain.random.*
import com.notesapp.domain.security.EncryptedArchive
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class Phase34DomainTest {
    private val schema = StatusSchema.DEFAULT
    private val collection = CollectionEntity(1, "Movies", CategoryType.MOVIE, "movie", "#123456", schema.toJson(), 0, 1)
    private fun item(id: Long, metadata: String? = null, created: Long = 1, completed: Long? = null) = ItemEntity(id = id, collectionId = 1, title = "Item $id", status = if (completed == null) schema.initial.key else schema.completionStatus!!.key,
        metadataJson = metadata, createdAt = created, updatedAt = created, completedAt = completed)

    @Test fun metadataPreservesExtensionsAndClearsKnownFields() {
        val result = ItemMetadata.merge("{\"author\":\"Old\",\"customField\":42}", ItemMetadata(pageCount = 320))
        assertTrue(result.contains("\"customField\":42"))
        assertNull(ItemMetadata.decode(result).author)
        assertEquals(320, ItemMetadata.decode(result).pageCount)
    }
    @Test fun unavailableMetadataDoesNotCrashTheOfflineEditor() { assertEquals(ItemMetadata(), ItemMetadata.decode("not json")); assertEquals(ItemMetadata(), ItemMetadata.decode(null)) }
    @Test(expected = IllegalArgumentException::class) fun negativePriceIsRejected() { ItemMetadata(price = -1.0).validate() }
    @Test(expected = IllegalArgumentException::class) fun invalidCoordinatesAreRejected() { ItemMetadata(latitude = 91.0).validate() }
    @Test(expected = IllegalArgumentException::class) fun nonfiniteBudgetIsRejected() { ItemMetadata(budget = Double.NaN).validate() }
    @Test fun timeAndBudgetFiltersExcludeUnknownValuesAndIncludeBoundaries() {
        val rows = listOf(item(1, "{\"runtimeMinutes\":90,\"price\":10.0,\"currency\":\"USD\"}"), item(2, "{\"runtimeMinutes\":120,\"price\":25.0}"), item(3)).map { ItemWithTags(it, emptyList()) }
        assertEquals(listOf(1L), PickRandomItem.candidates(rows, listOf(collection), RandomFilter(maxRuntimeMinutes = 90, maxPrice = 10.0, currency = "USD")).map { it.first.item.id })
        assertEquals(listOf(1L, 2L), PickRandomItem.candidates(rows, listOf(collection), RandomFilter(maxRuntimeMinutes = 120)).map { it.first.item.id })
    }
    @Test fun recencyRangeIncludesLowerBoundAndExcludesUpperBound() {
        val rows = listOf(item(1, created = 9), item(2, created = 10), item(3, created = 20), item(4, created = 21)).map { ItemWithTags(it, emptyList()) }
        assertEquals(listOf(2L), PickRandomItem.candidates(rows, listOf(collection), RandomFilter(addedAfter = 10, addedBefore = 20)).map { it.first.item.id })
    }
    @Test fun metadataSortingKeepsMissingValuesLastInEitherDirection() {
        val rows = listOf(item(1), item(2, "{\"runtimeMinutes\":90,\"price\":20.0}"), item(3, "{\"runtimeMinutes\":120,\"price\":10.0,\"currency\":\"USD\"}")).map { ItemWithTags(it, emptyList()) }
        assertEquals(listOf(2L, 3L, 1L), ItemListing.apply(rows, ItemFilters(), SortSpec(SortField.RUNTIME, false)).map { it.item.id })
        assertEquals(listOf(3L, 2L, 1L), ItemListing.apply(rows, ItemFilters(), SortSpec(SortField.RUNTIME, true)).map { it.item.id })
        assertEquals(listOf(3L, 2L, 1L), ItemListing.apply(rows, ItemFilters(), SortSpec(SortField.PRICE, false)).map { it.item.id })
    }
    @Test fun spokenBookCommandBecomesAParsedBookCapture() {
        val parsed = QuickCaptureParser.parse(VoiceCaptureParser.normalize("Add The Pragmatic Programmer to Books, want to read."))
        assertEquals("The Pragmatic Programmer", parsed.title); assertEquals(CategoryType.BOOK, parsed.verbCategory)
    }
    @Test fun spokenShoppingCommandKeepsTheTitleAndPriority() {
        val parsed = QuickCaptureParser.parse(VoiceCaptureParser.normalize("Please add oat milk to shopping high priority"))
        assertEquals("oat milk", parsed.title); assertEquals(CategoryType.BUY, parsed.verbCategory); assertEquals(Priority.HIGH, parsed.priority)
    }
    @Test fun aSpokenRawThoughtStillGoesToInbox() {
        val parsed = QuickCaptureParser.parse(VoiceCaptureParser.normalize("Remember call the dentist"))
        assertEquals("call the dentist", parsed.title); assertNull(parsed.verbCategory)
    }
    @Test fun providerBookDataHandlesMissingFieldsAndUnknownFields() {
        val rows = MetadataLookup.parseBooks("""{"docs":[{"key":"/works/OL1W","title":"Dune","author_name":["Frank Herbert"],"number_of_pages_median":412,"cover_i":22,"extra":true},{"key":"/works/OL2W","title":"No details"},{}]}""")
        assertEquals(2, rows.size); assertEquals("Frank Herbert", rows[0].metadata.author); assertEquals(412, rows[0].metadata.pageCount)
        assertEquals("https://covers.openlibrary.org/b/id/22-L.jpg", rows[0].coverUrl); assertNull(rows[1].metadata.pageCount)
    }
    @Test fun providerMovieDataHandlesNullPostersAndBadDates() {
        val rows = MetadataLookup.parseMovies("""{"results":[{"id":1,"title":"Arrival","release_date":"2016-11-11","poster_path":null},{"id":2,"title":"Unknown","release_date":""}]}""")
        assertEquals(2016, rows[0].metadata.releaseYear); assertNull(rows[0].coverUrl); assertNull(rows[1].metadata.releaseYear)
    }
    @Test fun encryptedArchivesRoundTripUnicodeAndUseDifferentRandomEnvelopes() {
        val content = "Notes, café, 東京\nprivate text".toByteArray()
        val password = "a long recovery passphrase".toCharArray()
        val first = EncryptedArchive.encrypt(content, password); val second = EncryptedArchive.encrypt(content, password)
        assertFalse(first.contentEquals(second)); assertFalse(String(first).contains("private text")); assertArrayEquals(content, EncryptedArchive.decrypt(first, password))
    }
    @Test(expected = IllegalArgumentException::class) fun wrongArchivePasswordIsRejected() { EncryptedArchive.decrypt(EncryptedArchive.encrypt("secret".toByteArray(), "correct password".toCharArray()), "wrong password".toCharArray()) }
    @Test(expected = IllegalArgumentException::class) fun modifiedArchiveCiphertextIsRejected() {
        val data = EncryptedArchive.encrypt("secret".toByteArray(), "correct password".toCharArray()); data[data.lastIndex] = (data.last().toInt() xor 1).toByte(); EncryptedArchive.decrypt(data, "correct password".toCharArray())
    }
    @Test(expected = IllegalArgumentException::class) fun truncatedArchivesAreRejected() { EncryptedArchive.decrypt(ByteArray(10), "correct password".toCharArray()) }
    @Test(expected = IllegalArgumentException::class) fun unreasonableKdfIterationsAreRejectedBeforeDerivation() {
        val data = EncryptedArchive.encrypt(byteArrayOf(1), "correct password".toCharArray()); java.nio.ByteBuffer.wrap(data).putInt(12, Int.MAX_VALUE); EncryptedArchive.decrypt(data, "correct password".toCharArray())
    }
    @Test(expected = IllegalArgumentException::class) fun shortExportPassphrasesAreRejected() { EncryptedArchive.encrypt(byteArrayOf(1), "short".toCharArray()) }

    @Test fun insightsSeparatePeriodCompletionsFromTheAddedCohortAndFindForgottenItems() {
        fun at(date: String) = Instant.parse(date).toEpochMilli()
        val rows = listOf(item(1, created = at("2026-01-01T12:00:00Z"), completed = at("2026-06-10T12:00:00Z")),
            item(2, created = at("2026-06-01T12:00:00Z"), completed = at("2026-06-11T12:00:00Z")),
            item(3, created = at("2026-06-01T12:00:00Z"), completed = at("2026-06-12T12:00:00Z")), item(4, created = at("2026-02-01T12:00:00Z")))
        val stats = PersonalInsights.compute(rows, listOf(collection), listOf(TagEntity(1, "scifi")), listOf(EntityTagCrossRef(1, 1, EntityType.ITEM), EntityTagCrossRef(1, 2, EntityType.ITEM)), 2026, 6, Instant.parse("2026-06-12T18:00:00Z"), ZoneOffset.UTC)
        assertEquals(2, stats.added); assertEquals(3, stats.completed); assertEquals(100, stats.completionRate)
        assertEquals(3, stats.currentStreak); assertEquals(3, stats.longestStreak); assertEquals(listOf("scifi" to 2), stats.activeTags)
        assertEquals(listOf(4L), stats.forgotten.map { it.id }); assertEquals(3, stats.months[5].completed)
        assertEquals(3 to 3, stats.mediaStreaks[CategoryType.MOVIE])
    }
    @Test fun completionYearUsesLocalCalendarRatherThanUtc() {
        val completion = Instant.parse("2026-01-01T00:30:00Z").toEpochMilli()
        val rows = listOf(item(1, completed = completion))
        val stats = PersonalInsights.compute(rows, listOf(collection), emptyList(), emptyList(), 2025, 12, Instant.parse("2026-01-02T12:00:00Z"), ZoneId.of("America/New_York"))
        assertEquals(1, stats.completed)
    }
    @Test fun multipleCompletionsOnOneDayDoNotInflateStreaksAndFutureCompletionsAreIgnored() {
        fun completed(value: String) = Instant.parse(value).toEpochMilli()
        val rows = listOf(item(1, completed = completed("2026-06-10T12:00:00Z")), item(2, completed = completed("2026-06-10T18:00:00Z")), item(3, completed = completed("2027-06-10T12:00:00Z")))
        val stats = PersonalInsights.compute(rows, listOf(collection), emptyList(), emptyList(), 2026, null, Instant.parse("2026-06-11T12:00:00Z"), ZoneOffset.UTC)
        assertEquals(1, stats.currentStreak); assertEquals(1, stats.longestStreak); assertEquals(2, stats.completed)
    }
}
