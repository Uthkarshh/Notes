package com.notesapp.domain

import com.notesapp.data.db.entity.*
import com.notesapp.domain.metadata.*
import com.notesapp.domain.model.*
import com.notesapp.domain.random.*
import com.notesapp.domain.template.CollectionTemplates
import com.notesapp.ui.discover.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class RedesignMilestoneThreeTest {
    private val now = 200 * 86_400_000L
    private val template = CollectionTemplates.byId("movies")!!
    private val c = CollectionEntity(1, "Movies", CategoryType.MOVIE, "movie", "#123456", template.schema.toJson(), 0, 0)
    private fun row(id: Long, metadata: ItemMetadata = ItemMetadata(), age: Long = 1) = ItemWithTags(ItemEntity(
        id = id, collectionId = 1, title = "Long title $id", status = c.schema.initial.key,
        metadataJson = ItemMetadata.merge(null, metadata), createdAt = now - age, updatedAt = now), listOf("Weekend"))
    private fun ids(rows: List<ItemWithTags>, f: RandomFilter) = PickRandomItem.candidates(rows, listOf(c), f, now).map { it.first.item.id }

    @Test fun positivePriceRequiresExplicitMatchingCurrency() {
        val rows = listOf(row(1, ItemMetadata(price = 10.0, currency = "USD")), row(2, ItemMetadata(price = 10.0, currency = "EUR")), row(3, ItemMetadata(price = 5.0)))
        assertEquals(emptyList<Long>(), ids(rows, RandomFilter(maxPrice = 10.0)))
        assertEquals(listOf(1L), ids(rows, RandomFilter(maxPrice = 10.0, currency = "usd")))
    }
    @Test fun freeMeansKnownZeroRegardlessOfCurrency() {
        assertEquals(listOf(1L, 2L), ids(listOf(row(1, ItemMetadata(price = 0.0)), row(2, ItemMetadata(price = 0.0, currency = "EUR")), row(3), row(4, ItemMetadata(price = .01))), RandomFilter(maxPrice = 0.0)))
    }
    @Test fun forgottenUsesStrictNinetyDayBoundaryAndInjectedInstant() {
        val day = 86_400_000L
        assertEquals(listOf(1L), ids(listOf(row(1, age = 90 * day + 1), row(2, age = 90 * day), row(3, age = 90 * day - 1)), DiscoverFilters(ageMode = 2).toRandomFilter(now)))
    }
    @Test fun allNineDimensionsComposeOnTheSamePool() {
        val good = row(1, ItemMetadata(runtimeMinutes = 90, price = 25.0, currency = "USD"), age = 100 * 86_400_000L).let { it.copy(item = it.item.copy(isFavorite = true, priority = Priority.HIGH.level)) }
        val bad = good.copy(item = good.item.copy(id = 2, lastSelectedAt = now - 1))
        val f = DiscoverFilters(collectionId = 1, status = StatusMode.UNSTARTED, favoritesOnly = true, highPriorityOnly = true,
            tags = setOf("weekend", "absent"), cooldownDays = 7, maxRuntime = 90, maxPrice = 25.0, currency = "USD", ageMode = 2).toRandomFilter(now)
        assertEquals(listOf(1L), ids(listOf(good, bad), f))
        assertEquals(1, (PickRandomItem.pick(listOf(good, bad), listOf(c), f, now = now) as PickResult.Picked).matchCount)
    }
    @Test fun drawCountUsesRemainingCandidatesAndExhausts() {
        val rows = listOf(row(1), row(2), row(3))
        val result = PickRandomItem.pick(rows, listOf(c), RandomFilter(), skippedIds = setOf(1, 2), now = now) as PickResult.Picked
        assertEquals(1, result.matchCount); assertEquals(3L, result.item.item.id)
        assertEquals(PickResult.Exhausted, PickRandomItem.pick(rows, listOf(c), RandomFilter(), skippedIds = setOf(1, 2, 3), now = now))
    }
    @Test fun anyStatusStillExcludesArchivedItems() {
        val archived = row(1).let { it.copy(item = it.item.copy(isArchived = true)) }
        assertTrue(ids(listOf(archived), RandomFilter(excludeFinished = false)).isEmpty())
    }
    @Test fun providerWhitelistAndConflictDetectionProtectPersonalFields() {
        val supplied = MetadataReview.supplied(ItemMetadata(author = "Author", runtimeMinutes = 90, price = 40.0, streamingService = "Personal", rewatchCount = 7))
        assertEquals(setOf("author", "runtimeMinutes"), supplied.keys)
        assertEquals(setOf("author"), MetadataReview.conflicts(mapOf("author" to "Old"), mapOf("author" to "Mine", "genre" to "Drama"), setOf("author", "runtimeMinutes")))
    }
    @Test fun presentationHasUnitsCurrencyAndTypeSpecificRatios() {
        val p = ItemPresentation.from(row(1, ItemMetadata(runtimeMinutes = 90, releaseYear = 2024, price = 10.0, currency = "USD")), CategoryType.MOVIE, Locale.US)
        assertTrue(p.details.contains("90 min")); assertTrue(p.details.contains("$10.00")); assertEquals(.72f, p.coverRatio)
        assertEquals(1f, ItemPresentation.from(row(1), CategoryType.BUY).coverRatio)
    }
    @Test fun missingMetadataAndUnspecifiedCurrencyStayHonest() {
        assertEquals("", ItemPresentation.from(row(1), CategoryType.BOOK).details)
        assertTrue(ItemPresentation.from(row(1, ItemMetadata(price = 10.0)), CategoryType.BUY).details.contains("currency unspecified"))
        assertEquals("Free", ItemPresentation.from(row(1, ItemMetadata(price = 0.0)), CategoryType.BUY).details)
    }
}
