package com.notesapp.domain.notifications

import com.notesapp.data.db.entity.CollectionEntity
import com.notesapp.data.db.entity.ItemEntity
import com.notesapp.data.prefs.NotificationSettings
import com.notesapp.domain.capture.prepareSharedText
import com.notesapp.domain.model.*
import com.notesapp.domain.random.*
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class Phase2DomainTest {
    @Test fun nextDeliveryRespectsEnabledDays() {
        val now = ZonedDateTime.parse("2026-11-05T09:00:00-05:00[America/New_York]")
        val next = NotificationSchedule.next(now, NotificationSettings(enabled = true, days = setOf(5)))
        assertEquals(ZonedDateTime.parse("2026-11-06T08:00:00-05:00[America/New_York]").toInstant(), next)
    }
    @Test fun disabledOrEmptyDaysNeverSchedule() {
        val now = ZonedDateTime.now(ZoneId.of("UTC"))
        assertNull(NotificationSchedule.next(now, NotificationSettings()))
        assertNull(NotificationSchedule.next(now, NotificationSettings(enabled = true, days = emptySet())))
    }
    @Test fun daylightSavingGapMovesToAValidLocalTime() {
        val now = ZonedDateTime.parse("2026-03-07T12:00:00-05:00[America/New_York]")
        val next = NotificationSchedule.next(now, NotificationSettings(enabled = true, minuteOfDay = 150, days = setOf(7)))
        assertEquals(ZonedDateTime.parse("2026-03-08T03:30:00-04:00[America/New_York]").toInstant(), next)
    }
    @Test fun lateMorningWorkIsAcceptedButEveningDeliveryIsSkipped() {
        val schedule = NotificationSettings(enabled = true)
        assertTrue(NotificationSchedule.isDue(ZonedDateTime.parse("2026-11-05T09:00:00Z"), schedule))
        assertFalse(NotificationSchedule.isDue(ZonedDateTime.parse("2026-11-05T07:59:59Z"), schedule))
        assertFalse(NotificationSchedule.isDue(ZonedDateTime.parse("2026-11-05T19:00:00Z"), schedule))
    }
    @Test fun sharingPreservesSubjectAndLinkWithoutDuplicatingTheTitle() {
        assertEquals("A title\nhttps://example.com", prepareSharedText("A title", "https://example.com"))
        assertEquals("A title\nBody", prepareSharedText("A title", "A title\nBody"))
        assertEquals("Selected text", prepareSharedText(null, "Selected text"))
    }
    @Test fun cooldownExcludesRecentlyDrawnItemsAndKeepsOlderOrNeverDrawnItems() {
        val schema = StatusSchema.DEFAULT
        val collection = CollectionEntity(1, "Tests", CategoryType.CUSTOM, "folder", "#006A6A", schema.toJson(), 0, 0)
        val now = 20 * 86_400_000L
        fun item(id: Long, at: Long?) = ItemWithTags(ItemEntity(id, 1, "Item $id", status = schema.initial.key, lastSelectedAt = at, createdAt = 0, updatedAt = 0), emptyList())
        val items = listOf(item(1, now - 86_400_000L), item(2, now - 8 * 86_400_000L), item(3, null))
        assertEquals(listOf(2L, 3L), PickRandomItem.candidates(items, listOf(collection), RandomFilter(cooldownDays = 7), now).map { it.first.item.id })
        assertEquals(3, PickRandomItem.candidates(items, listOf(collection), RandomFilter(), now).size)
    }
}
