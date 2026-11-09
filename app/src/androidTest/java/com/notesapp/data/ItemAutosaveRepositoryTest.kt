package com.notesapp.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.notesapp.data.repository.NewItem
import com.notesapp.domain.capture.ItemEditFields
import com.notesapp.domain.metadata.ItemMetadata
import com.notesapp.domain.model.Priority
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ItemAutosaveRepositoryTest : DataTestBase() {
    @Test fun patchesPreserveWorkflowMetadataExtensionsAndSearchIndex() = runBlocking {
        val movies = collection()
        val id = env.items.create(NewItem("Arrival", movies))
        val item = env.items.get(id)!!
        env.db.itemDao().update(item.copy(metadataJson = """{"runtimeMinutes":90,"customField":42}"""))
        env.items.setStatus(id, "watching")
        env.items.setPriority(id, Priority.URGENT)
        env.items.journalEditor(id, mapOf("author" to "Draft author", "runtimeMinutes" to "unfinished"))
        env.items.commitEditor(id, mapOf("description" to "Heptapod language", "author" to "Draft author"))
        val saved = env.items.get(id)!!
        assertEquals("watching", saved.status)
        assertEquals(Priority.URGENT, saved.priorityLevel)
        assertEquals(90, ItemMetadata.decode(saved.metadataJson).runtimeMinutes)
        assertTrue(saved.metadataJson!!.contains("\"customField\":42"))
        assertEquals(mapOf("runtimeMinutes" to "unfinished"), env.items.editorDraft(id))
        assertEquals(1, env.search.search("Heptapod").size)
        env.items.commitEditor(id, mapOf("runtimeMinutes" to ""))
        assertNull(ItemMetadata.decode(env.items.get(id)!!.metadataJson).runtimeMinutes)
    }

    @Test fun anOlderEditorClosingCannotDeleteANewerDraft() = runBlocking {
        val id = env.items.create(NewItem("Original"))
        val old = mapOf("description" to "Old draft")
        env.items.journalEditor(id, old)
        env.items.journalEditor(id, mapOf("description" to "New draft", "title" to "New title"))
        env.items.journalEditor(id, emptyMap(), old)
        assertEquals(mapOf("description" to "New draft", "title" to "New title"), env.items.editorDraft(id))
    }

    @Test fun deletionCascadesRecoveryAndLateSavesCannotRecreateAnItem() = runBlocking {
        val id = env.items.create(NewItem("Original"))
        env.items.journalEditor(id, mapOf("runtimeMinutes" to "unfinished"))
        env.items.delete(id)
        env.items.commitEditor(id, mapOf(ItemEditFields.TITLE to "Late title"))
        env.items.journalEditor(id, mapOf("runtimeMinutes" to "unfinished"))
        assertNull(env.items.get(id))
        assertTrue(env.items.editorDraft(id).isEmpty())
        assertTrue(env.search.search("Late").isEmpty())
    }

    @Test fun completionUndoRestoresTheReminderAndPreservesALaterTextEdit() = runBlocking {
        val movies = collection()
        val id = env.items.create(NewItem("Arrival", movies))
        val at = env.clock.millis() + 60_000
        env.items.setReminder(id, at)
        val undo = env.items.undoable(id) { env.items.setStatus(id, "watched") }!!
        assertNull(env.items.get(id)!!.reminderAt)
        env.items.commitEditor(id, mapOf("description" to "Keep my newer notes"))
        assertTrue(env.items.undo(undo))
        val restored = env.items.get(id)!!
        assertEquals("want_to_watch", restored.status)
        assertNull(restored.completedAt)
        assertEquals(at, restored.reminderAt)
        assertEquals("Keep my newer notes", restored.description)
    }

    @Test fun undoCannotOverwriteANewerWorkflowChange() = runBlocking {
        val movies = collection()
        val id = env.items.create(NewItem("Arrival", movies))
        val undo = env.items.undoable(id) { env.items.setStatus(id, "watching") }!!
        env.items.setStatus(id, "dropped")
        assertFalse(env.items.undo(undo))
        assertEquals("dropped", env.items.get(id)!!.status)
    }
}
