package com.notesapp.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.notesapp.data.db.entity.*
import com.notesapp.data.repository.*
import com.notesapp.domain.model.EntityType
import com.notesapp.domain.transfer.BackupJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataTransferTest : DataTestBase() {
    private fun transfers() = DataTransferRepository(env.db, env.settings, SearchIndexer(env.db.searchDao()), env.clock)

    @Test fun replacementRestoresAllRowsRelationsAndSearch() = runTest {
        val collection = collection()
        val item = env.items.create(NewItem("Arrival", collection, listOf("scifi")))
        val note = env.notes.create("Reflection", "Language and time", listOf("ideas"), pinned = true)
        env.facts.create("A fact", source = "A source", tags = listOf("scifi"))
        env.db.phase2Dao().insertLink(KnowledgeLinkEntity(note, EntityType.NOTE, item, EntityType.ITEM))
        env.db.phase2Dao().insertHistory(SelectionHistoryEntity(itemId = item, selectedAt = 123, actionTaken = SelectionAction.ACCEPTED))
        val backup = transfers().snapshot()
        env.notes.create("Remove me", "Not in the backup")
        transfers().import(BackupJson.decode(BackupJson.encode(backup)), ImportMode.REPLACE)
        val restored = transfers().snapshot()
        assertEquals(backup.copy(exportedAt = 0), restored.copy(exportedAt = 0))
        assertEquals("Reflection", env.db.phase2Dao().observeLinkedNotes(item).first().single().title)
        assertEquals(1, env.search.search("Language").size)
        assertTrue(env.search.search("Remove").isEmpty())
    }

    @Test fun mergeRemapsIdsAndKeepsExistingRows() = runTest {
        val c = collection()
        val item = env.items.create(NewItem("Arrival", c, listOf("scifi")))
        val note = env.notes.create("Reflection", "A thought")
        env.db.phase2Dao().insertLink(KnowledgeLinkEntity(note, EntityType.NOTE, item, EntityType.ITEM))
        val backup = transfers().snapshot()
        transfers().import(backup, ImportMode.MERGE)
        assertEquals(2, env.db.phase2Dao().items().size)
        assertEquals(2, env.db.phase2Dao().notes().size)
        assertEquals(1, env.db.phase2Dao().tags().size)
        val imported = env.db.phase2Dao().items().single { it.id != item }
        val importedNote = env.db.phase2Dao().observeLinkedNotes(imported.id).first().single()
        assertNotEquals(note, importedNote.id)
        assertEquals(2, env.search.search("Arrival").size)
    }

    @Test fun malformedImportDoesNotRemoveExistingData() = runTest {
        val item = env.items.create(NewItem("Keep me"))
        val backup = transfers().snapshot().copy(tagRefs = listOf(EntityTagCrossRef(999, item, EntityType.ITEM)))
        try { transfers().import(backup, ImportMode.REPLACE); fail("Invalid import must fail") } catch (_: IllegalArgumentException) { }
        assertNotNull(env.items.get(item))
        assertEquals(1, env.search.search("Keep").size)
    }
}
