package com.notesapp.data

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.notesapp.domain.model.MasteryState
import com.notesapp.ui.vault.FactDraft
import com.notesapp.ui.vault.FactEditorViewModel
import com.notesapp.ui.vault.NoteDraft
import com.notesapp.ui.vault.NoteEditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Saves triggered while an editor is closing must respect an explicit delete. */
@RunWith(AndroidJUnit4::class)
class EditorPersistenceTest : DataTestBase() {

    @Test
    fun aPendingAutosaveCannotRecreateADeletedNote() = runBlocking {
        val id = env.notes.create("Original", "Original body")
        val store = ViewModelStore()
        val editor = withContext(Dispatchers.Main) {
            NoteEditorViewModel(SavedStateHandle(mapOf("noteId" to id)), env.notes)
                .also { store.put("editor", it) }
        }
        try {
            withTimeout(5_000) { editor.loaded.filterNotNull().first() }
            editor.delete()
            editor.save(NoteDraft("Edited title", "Edited body", listOf("draft"), false))

            assertTrue(env.notes.observeAll().first().isEmpty())
            assertTrue(env.search.search("Edited").isEmpty())
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
        }
    }

    @Test
    fun aPendingAutosaveCannotRecreateADeletedFact() = runBlocking {
        val id = env.facts.create("Original fact")
        val store = ViewModelStore()
        val editor = withContext(Dispatchers.Main) {
            FactEditorViewModel(SavedStateHandle(mapOf("factId" to id)), env.facts)
                .also { store.put("editor", it) }
        }
        try {
            withTimeout(5_000) { editor.loaded.filterNotNull().first() }
            editor.delete()
            editor.save(FactDraft("Edited fact", "", "", "", listOf("draft"), MasteryState.NEW, false))

            assertTrue(env.facts.observeAll().first().isEmpty())
            assertTrue(env.search.search("Edited").isEmpty())
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
        }
    }

    @Test
    fun restoringANewNoteAfterAnAutosaveUpdatesTheSameRecord() = runBlocking {
        val state = SavedStateHandle(mapOf("noteId" to null))
        val store = ViewModelStore()
        val editor = withContext(Dispatchers.Main) {
            NoteEditorViewModel(state, env.notes).also { store.put("original", it) }
        }
        try {
            withTimeout(5_000) { editor.loaded.filterNotNull().first() }
            editor.save(NoteDraft("Trip", "First draft", emptyList(), false))
            val id = env.notes.observeAll().first().single().note.id

            val restored = withContext(Dispatchers.Main) {
                val restoredState = SavedStateHandle(state.keys().associateWith { state.get<Any?>(it) })
                store.clear()
                NoteEditorViewModel(restoredState, env.notes).also { store.put("restored", it) }
            }
            withTimeout(5_000) { restored.loaded.filterNotNull().first() }
            restored.save(NoteDraft("Trip", "Second draft", emptyList(), false))

            val saved = env.notes.observeAll().first().single().note
            assertEquals(id, saved.id)
            assertEquals("Second draft", saved.content)
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
        }
    }

    @Test
    fun restoringANewFactAfterAnAutosaveUpdatesTheSameRecord() = runBlocking {
        val state = SavedStateHandle(mapOf("factId" to null))
        val store = ViewModelStore()
        val editor = withContext(Dispatchers.Main) {
            FactEditorViewModel(state, env.facts).also { store.put("original", it) }
        }
        try {
            withTimeout(5_000) { editor.loaded.filterNotNull().first() }
            editor.save(FactDraft("First draft", "", "", "", emptyList(), MasteryState.NEW, false))
            val id = env.facts.observeAll().first().single().fact.id

            val restored = withContext(Dispatchers.Main) {
                val restoredState = SavedStateHandle(state.keys().associateWith { state.get<Any?>(it) })
                store.clear()
                FactEditorViewModel(restoredState, env.facts).also { store.put("restored", it) }
            }
            withTimeout(5_000) { restored.loaded.filterNotNull().first() }
            restored.save(FactDraft("Second draft", "", "", "", emptyList(), MasteryState.REVIEWING, true))

            val saved = env.facts.observeAll().first().single().fact
            assertEquals(id, saved.id)
            assertEquals("Second draft", saved.text)
            assertTrue(saved.isFavorite)
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
        }
    }
}
