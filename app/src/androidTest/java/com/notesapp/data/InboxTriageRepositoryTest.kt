package com.notesapp.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.notesapp.data.repository.*
import com.notesapp.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration

@RunWith(AndroidJUnit4::class)
class InboxTriageRepositoryTest : DataTestBase() {
    @Test fun pendingDeletionHidesTheItemEverywhereAndUndoRetainsItsEntireGraph() = runBlocking {
        val id = env.items.create(NewItem("Heptapod", tags = listOf("scifi")))
        env.items.setImage(id, "file:///test-cover.jpg")
        env.items.setReminder(id, env.clock.millis() + 60_000)
        env.items.recordDraw(id)
        val note = KnowledgeRepository(env.db, env.notes).reflect(id, "Language reflection")!!
        env.items.journalEditor(id, mapOf("runtimeMinutes" to "unfinished"))
        val before = env.items.get(id)!!
        val result = env.items.deleteInbox(listOf(id))
        assertEquals(1, result.count)
        assertNull(env.items.get(id))
        assertEquals(before, env.db.itemDao().getIncludingPending(id))
        assertEquals(0, env.items.observeInboxCount().first())
        assertTrue(env.search.search("Heptapod").none { it.type == EntityType.ITEM })
        assertTrue(env.items.observeReminders().first().isEmpty())
        assertTrue(env.items.observeHistory().first().isEmpty())
        assertTrue(env.items.observeTagUsage().first().isEmpty())
        val snapshot = DataTransferRepository(env.db, env.settings, SearchIndexer(env.db.searchDao()), env.clock).snapshot()
        snapshot.validate()
        assertTrue(snapshot.items.isEmpty()); assertTrue(snapshot.history.isEmpty())
        assertTrue(snapshot.links.isEmpty())
        assertTrue(snapshot.tagRefs.none { it.targetType == EntityType.ITEM })
        assertTrue(env.items.undoDeletion(result.operationId))
        assertEquals(before, env.items.get(id))
        assertEquals(listOf("scifi"), env.items.observeInbox().first().single().tags)
        assertEquals(1, env.search.search("Heptapod").count { it.type == EntityType.ITEM })
        assertEquals(1, env.items.observeReminders().first().size)
        assertEquals(1, env.items.observeHistory().first().size)
        assertEquals(note, env.db.phase2Dao().links().single().sourceId)
        assertEquals(mapOf("runtimeMinutes" to "unfinished"), env.items.editorDraft(id))
    }
    @Test fun expiryIsEnforcedBeforeCleanupAndFinalizationKeepsStandaloneReflections() = runBlocking {
        val id = env.items.create(NewItem("Expired", tags = listOf("trash")))
        env.items.setImage(id, "file:///test-cover.jpg")
        env.items.recordDraw(id)
        val note = KnowledgeRepository(env.db, env.notes).reflect(id, "Keep the reflection")!!
        val deletion = env.items.deleteInbox(listOf(id))
        env.clock.advance(Duration.ofMillis(ItemRepository.DELETE_UNDO_MILLIS))
        assertFalse(env.items.undoDeletion(deletion.operationId))
        assertNotNull(env.db.itemDao().getIncludingPending(id))
        assertEquals(listOf("file:///test-cover.jpg"), env.items.finalizeExpiredDeletions())
        assertNull(env.db.itemDao().getIncludingPending(id))
        assertTrue(env.db.pendingDeletionDao().observe().first().isEmpty())
        assertTrue(env.db.tagDao().tagsFor(EntityType.ITEM, id).isEmpty())
        assertTrue(env.db.phase2Dao().links().isEmpty())
        assertTrue(env.db.phase2Dao().history().isEmpty())
        assertNotNull(env.notes.get(note))
    }
    @Test fun batchFilingSkipsStaleIdsAndUndoPreservesLaterTextEdits() = runBlocking {
        val target = collection()
        val ids = (1..10).map { env.items.create(NewItem("Capture $it", tags = listOf("batch"))) }
        env.items.moveToCollection(listOf(ids.first()), target)
        val result = env.items.fileInbox(ids + 99999L, target.id)
        assertEquals(9, result.changes.size); assertEquals(2, result.skipped)
        env.items.commitEditor(ids.last(), mapOf("description" to "Keep newer notes"))
        assertTrue(env.items.undoBatch(result.changes))
        assertEquals(9, env.items.observeInboxCount().first())
        assertEquals(target.id, env.items.get(ids.first())!!.collectionId)
        assertEquals("Keep newer notes", env.items.get(ids.last())!!.description)
        assertEquals(10, env.search.search("Capture").size)
    }
    @Test fun undoBatchRollsBackEveryRowIfOneWorkflowChangedAgain() = runBlocking {
        val target = collection()
        val ids = (1..3).map { env.items.create(NewItem("Capture $it")) }
        val result = env.items.fileInbox(ids, target.id)
        env.items.setStatus(ids.last(), "watching")
        assertFalse(env.items.undoBatch(result.changes))
        ids.forEach { assertEquals(target.id, env.items.get(it)!!.collectionId) }
        assertEquals("watching", env.items.get(ids.last())!!.status)
    }
    @Test fun deletedDestinationsFailWithoutMovingAnySelectedItems() = runBlocking {
        val target = collection()
        val id = env.items.create(NewItem("Keep in Inbox"))
        env.collections.delete(target.id, deleteItems = false)
        try { env.items.fileInbox(listOf(id), target.id); fail("Expected stale destination rejection") }
        catch (_: IllegalStateException) { }
        assertNull(env.items.get(id)!!.collectionId)
    }
    @Test fun pendingItemsCannotBeEditedOrMovedAndSharedCoversAreRetained() = runBlocking {
        val target = collection()
        val id = env.items.create(NewItem("Delete me"))
        val shared = env.items.create(NewItem("Keep me", target))
        env.items.setImage(id, "file:///shared-cover.jpg"); env.items.setImage(shared, "file:///shared-cover.jpg")
        val draft = mapOf("runtimeMinutes" to "unfinished")
        env.items.journalEditor(id, draft)
        val deletion = env.items.deleteInbox(listOf(id))
        env.items.moveToCollection(listOf(id), target)
        env.items.commitEditor(id, mapOf("title" to "Late title"))
        env.items.setPriority(id, Priority.URGENT)
        env.items.journalEditor(id, emptyMap(), draft)
        assertEquals(draft, env.items.editorDraft(id))
        assertEquals("Delete me", env.db.itemDao().getIncludingPending(id)!!.title)
        assertEquals(0, env.db.itemDao().getIncludingPending(id)!!.priority)
        assertNull(env.db.itemDao().getIncludingPending(id)!!.collectionId)
        env.clock.advance(Duration.ofMillis(ItemRepository.DELETE_UNDO_MILLIS + 1))
        assertTrue(env.items.finalizeExpiredDeletions().isEmpty())
        assertNotNull(env.items.get(shared))
        assertFalse(env.items.undoDeletion(deletion.operationId))
    }
    @Test fun ledgerSurvivesClosingAndReopeningADiskDatabase() = runBlocking {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "pending-delete-${java.util.UUID.randomUUID()}.db"
        var disk = TestEnv(databaseName = name)
        try {
            val id = disk.items.create(NewItem("Survives restart", tags = listOf("recover")))
            val deletion = disk.items.deleteInbox(listOf(id))
            disk.close()
            disk = TestEnv(databaseName = name)
            assertNull(disk.items.get(id))
            assertEquals(deletion.operationId, disk.items.observePendingDeletions().first().single().operationId)
            assertTrue(disk.items.undoDeletion(deletion.operationId))
            assertEquals("Survives restart", disk.items.get(id)!!.title)
            assertEquals(listOf("recover"), disk.items.observeInbox().first().single().tags)
        } finally { disk.close(); context.deleteDatabase(name) }
    }
    @Test fun gesturePreferencesAndRecentDestinationsPersistWithSafeDefaults() = runBlocking {
        assertEquals(com.notesapp.data.prefs.InboxGestures(), env.settings.inboxGestures.first())
        env.settings.setInboxGesture(true, com.notesapp.data.prefs.InboxSwipeAction.CHOOSE_COLLECTION)
        env.settings.setInboxGesture(false, com.notesapp.data.prefs.InboxSwipeAction.NONE)
        env.settings.recordFiling(3); env.settings.recordFiling(2); env.settings.recordFiling(3)
        val recreated = com.notesapp.data.prefs.SettingsRepository(env.dataStore)
        assertEquals(com.notesapp.data.prefs.InboxSwipeAction.CHOOSE_COLLECTION, recreated.inboxGestures.first().startToEnd)
        assertEquals(com.notesapp.data.prefs.InboxSwipeAction.NONE, recreated.inboxGestures.first().endToStart)
        assertEquals(listOf(3L, 2L), recreated.recentFilingDestinations.first())
    }

}
