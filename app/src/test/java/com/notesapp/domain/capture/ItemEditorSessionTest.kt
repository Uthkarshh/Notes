package com.notesapp.domain.capture

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ItemEditorSessionTest {
    private class Store : ItemEditorStore {
        var saved = mapOf("title" to "Original", "description" to "", "runtimeMinutes" to "90")
        var raw = emptyMap<String, String>()
        var failJournal = false
        var failRead = false
        var commitDelay = 0L
        val commits = mutableListOf<Map<String, String>>()
        override suspend fun read(): Map<String, String> {
            check(!failRead)
            return saved
        }
        override suspend fun draft() = raw
        override suspend fun journal(fields: Map<String, String>) { check(!failJournal); raw = fields }
        override suspend fun commit(fields: Map<String, String>) {
            delay(commitDelay)
            saved = saved + fields
            commits += fields
        }
        override fun changes(): Flow<Unit> = MutableSharedFlow()
    }

    @Test fun mixedEditsSaveWithoutAnExitOrButtonAndInvalidNumbersStayDrafts() = runTest {
        val store = Store()
        val editor = ItemEditorSession(store, this)
        runCurrent()
        editor.edit(mapOf("description" to "My thoughts", "runtimeMinutes" to "12x", "author" to "Ursula Le Guin"))
        runCurrent()
        assertEquals("12x", store.raw["runtimeMinutes"])
        advanceTimeBy(500); runCurrent()
        assertEquals("My thoughts", store.saved["description"])
        assertEquals("Ursula Le Guin", store.saved["author"])
        assertEquals("90", store.saved["runtimeMinutes"])
        assertEquals(mapOf("runtimeMinutes" to "12x"), store.raw)
        assertTrue(editor.state.value.errors.containsKey("runtimeMinutes"))
        editor.close(); advanceUntilIdle()
    }

    @Test fun anOlderAcknowledgementCannotClearOrDisplayOverANewerEdit() = runTest {
        val store = Store().apply { commitDelay = 1000 }
        val editor = ItemEditorSession(store, this)
        runCurrent()
        editor.edit("description", "First")
        advanceTimeBy(500); runCurrent()
        editor.edit("description", "Second")
        advanceTimeBy(1000); runCurrent()
        assertEquals("Second", editor.state.value.fields["description"])
        assertTrue(editor.state.value.pending)
        advanceUntilIdle()
        assertEquals("Second", store.saved["description"])
        assertEquals(listOf("First", "Second"), store.commits.map { it["description"] })
        assertFalse(editor.state.value.pending)
        editor.close(); advanceUntilIdle()
    }

    @Test fun closeFlushesBeforeTheDebounceAndRecoveryKeepsInvalidRawValues() = runTest {
        val store = Store()
        val first = ItemEditorSession(store, this)
        runCurrent()
        first.edit(mapOf("title" to "Edited", "runtimeMinutes" to "-"))
        first.close(); advanceUntilIdle()
        assertEquals("Edited", store.saved["title"])
        assertEquals("-", store.raw["runtimeMinutes"])
        val recovered = ItemEditorSession(store, this)
        runCurrent()
        assertTrue(recovered.state.value.restored)
        assertEquals("-", recovered.state.value.fields["runtimeMinutes"])
        assertEquals("90", store.saved["runtimeMinutes"])
        recovered.edit("runtimeMinutes", "116")
        advanceUntilIdle()
        assertEquals("116", store.saved["runtimeMinutes"])
        assertTrue(store.raw.isEmpty())
        recovered.close(); advanceUntilIdle()
    }

    @Test fun mutationsFlushValidEditsWithoutDiscardingAnInvalidTitle() = runTest {
        val store = Store()
        val editor = ItemEditorSession(store, this)
        runCurrent()
        editor.edit(mapOf("title" to "", "description" to "Before status change"))
        val result = async { editor.mutate { assertEquals("Before status change", store.saved["description"]) } }
        advanceUntilIdle()
        assertTrue(result.await())
        assertEquals("Original", store.saved["title"])
        assertEquals("", store.raw["title"])
        editor.close(); advanceUntilIdle()
    }

    @Test fun failedWritesRetainTheNewestDraftAndCanBeRetried() = runTest {
        val store = Store().apply { failJournal = true }
        val editor = ItemEditorSession(store, this)
        runCurrent()
        editor.edit("title", "Recover me")
        advanceUntilIdle()
        assertNotNull(editor.state.value.failure)
        assertEquals("Original", store.saved["title"])
        assertEquals("Recover me", editor.state.value.fields["title"])
        store.failJournal = false
        val retry = async { editor.flush() }; advanceUntilIdle()
        assertTrue(retry.await())
        assertEquals("Recover me", store.saved["title"])
        assertNull(editor.state.value.failure)
        editor.close(); advanceUntilIdle()
    }

    @Test fun aLoadFailureCannotEraseAnExistingRecoveryDraft() = runTest {
        val store = Store().apply { raw = mapOf("runtimeMinutes" to "unfinished"); failRead = true }
        val editor = ItemEditorSession(store, this)
        runCurrent()
        val flush = async { editor.flush() }; advanceUntilIdle()
        assertFalse(flush.await())
        assertEquals(mapOf("runtimeMinutes" to "unfinished"), store.raw)
        editor.close(); advanceUntilIdle()
    }

    @Test fun localizedDecimalsAndExplicitClearingAreValidButNonfiniteNumbersAreNot() {
        assertNull(ItemEditFields.error("price", "12,50"))
        assertNull(ItemEditFields.error("runtimeMinutes", ""))
        assertNull(ItemEditFields.error("latitude", "-40.75"))
        assertNotNull(ItemEditFields.error("price", "NaN"))
        assertNotNull(ItemEditFields.error("price", "Infinity"))
        assertNotNull(ItemEditFields.error("runtimeMinutes", "12.5"))
        assertNotNull(ItemEditFields.error("latitude", "91"))
        assertNotNull(ItemEditFields.error("rewatchCount", "-1"))
    }
}
