package com.notesapp.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.notesapp.data.repository.*
import com.notesapp.domain.metadata.*
import com.notesapp.domain.model.*
import com.notesapp.domain.random.*
import com.notesapp.ui.surprise.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration

@RunWith(AndroidJUnit4::class)
class RedesignMilestoneThreeRepositoryTest : DataTestBase() {
    @Test fun selectedProviderFieldsApplyAndUndoPreservesLaterUnrelatedFields() = runBlocking {
        val c = collection(); val id = env.items.create(NewItem("My title", c, description = "My notes"))
        env.items.commitEditor(id, mapOf("genre" to "Personal", "streamingService" to "Local"))
        val expected = env.items.editorValues(id)!!
        val undo = env.items.undoable(id) { assertNull(env.items.applyProviderFields(id, expected, mapOf("runtimeMinutes" to "90"))) }!!
        env.items.commitEditor(id, mapOf("author" to "Later"))
        assertTrue(env.items.undo(undo))
        val result = ItemMetadata.decode(env.items.get(id)!!.metadataJson)
        assertNull(result.runtimeMinutes); assertEquals("Personal", result.genre); assertEquals("Local", result.streamingService); assertEquals("Later", result.author)
        assertEquals("My title", env.items.get(id)!!.title); assertEquals("My notes", env.items.get(id)!!.description)
    }
    @Test fun providerConflictAppliesNothingAndRequiresRenewedExpectedValues() = runBlocking {
        val id = env.items.create(NewItem("Local", collection()))
        val expected = env.items.editorValues(id)!!
        env.items.commitEditor(id, mapOf("runtimeMinutes" to "100"))
        val changed = env.items.applyProviderFields(id, expected, mapOf("runtimeMinutes" to "90", "genre" to "Drama"))!!
        assertEquals("100", changed["runtimeMinutes"]); assertNull(ItemMetadata.decode(env.items.get(id)!!.metadataJson).genre)
        assertNull(env.items.applyProviderFields(id, changed, mapOf("runtimeMinutes" to "90")))
        assertEquals(90, ItemMetadata.decode(env.items.get(id)!!.metadataJson).runtimeMinutes)
    }
    @Test fun newerCoverIsKeptAndPersonalProviderKeysAreRejected() = runBlocking {
        val id = env.items.create(NewItem("Personal", collection()))
        env.items.setImage(id, "new-local")
        assertFalse(env.items.setImageIfUnchanged(id, "provider", "old-local")); assertEquals("new-local", env.items.get(id)!!.imageUri)
        assertTrue(runCatching { env.items.applyProviderFields(id, emptyMap(), mapOf("description" to "Provider")) }.isFailure)
        assertEquals("Personal", env.items.get(id)!!.title)
    }
    @Test fun viewPreferencesRemainIndependentForEachCollectionAndRepositoryInstance() = runBlocking {
        val a = collection(); val b = collection("books")
        env.settings.updateCollectionView(a.id) { it.copy(mode = "BOARD", sort = SortSpec(SortField.TITLE), filters = ItemFilters(tags = setOf("Weekend"), favoritesOnly = true)) }
        val reopened = com.notesapp.data.prefs.SettingsRepository(env.dataStore)
        assertEquals("BOARD", reopened.collectionView(a.id).first().mode)
        assertEquals(setOf("Weekend"), reopened.collectionView(a.id).first().filters.tags)
        assertEquals("LIST", reopened.collectionView(b.id).first().mode)
    }
    @Test fun backlogDecisionRechecksStatusAndExactCutoff() = runBlocking {
        val c = collection(); val id = env.items.create(NewItem("Old", c)); env.clock.advanceDays(90)
        assertFalse(env.items.revisitBacklog(id))
        env.clock.advance(Duration.ofMillis(1)); env.items.setStatus(id, "watching")
        assertFalse(env.items.revisitBacklog(id, true)); assertFalse(env.items.get(id)!!.isArchived)
        env.items.setStatus(id, c.schema.initial.key); assertTrue(env.items.revisitBacklog(id, true))
        assertEquals(StatusRole.DROPPED, c.schema.roleOf(env.items.get(id)!!.status))
    }
    @Test fun rapidCommandsProduceOneHistoryEntryPerDrawAndNeverRepeat() = runBlocking {
        val c = collection(); repeat(3) { env.items.create(NewItem("Pick $it", c)) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val session = SurpriseSession(env.items, env.collections, scope)
            withContext(Dispatchers.Main) { repeat(5) { session.draw(RandomFilter(cooldownDays = 7)) } }
            withTimeout(5000) { session.busy.first { !it } }
            val first = (session.state.value as SurpriseState.Showing).entry.item.id
            assertEquals(1, env.db.phase2Dao().history().size); assertEquals(2, session.pool.value.remaining)
            withContext(Dispatchers.Main) { repeat(5) { session.rollAgain() } }
            withTimeout(5000) { session.busy.first { !it } }
            val second = (session.state.value as SurpriseState.Showing).entry.item.id
            assertNotEquals(first, second); assertEquals(2, env.db.phase2Dao().history().size); assertEquals(1, session.pool.value.remaining)
            withContext(Dispatchers.Main) { session.rollAgain() }; withTimeout(5000) { session.busy.first { !it } }
            assertEquals(0, session.pool.value.remaining)
            withContext(Dispatchers.Main) { session.rollAgain() }; withTimeout(5000) { session.busy.first { !it } }
            assertEquals(SurpriseState.Exhausted, session.state.value); assertEquals(3, env.db.phase2Dao().history().size)
        } finally { scope.cancel() }
    }
    @Test fun openSessionCountsRefreshWhenRemainingItemsAreArchivedOrDeleted() = runBlocking {
        val c = collection(); val ids = (1..3).map { env.items.create(NewItem("Live $it", c)) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val session = SurpriseSession(env.items, env.collections, scope)
            withContext(Dispatchers.Main) { session.draw(RandomFilter()) }
            withTimeout(5000) { session.busy.first { !it } }
            val picked = (session.state.value as SurpriseState.Showing).entry.item.id
            val remaining = ids.filter { it != picked }
            env.items.setArchived(remaining[0], true)
            withTimeout(5000) { session.pool.first { it.eligible == 2 && it.remaining == 1 } }
            env.items.moveToCollection(listOf(remaining[1]), null); env.items.deleteInbox(listOf(remaining[1]))
            withTimeout(5000) { session.pool.first { it.eligible == 1 && it.remaining == 0 } }
            assertEquals(1, env.db.phase2Dao().history().size)
        } finally { scope.cancel() }
    }

    @Test fun completionIsOneTransitionOneHistoryAndOneReflectionRequest() = runBlocking {
        val c = collection(); val id = env.items.create(NewItem("Finish", c)); val history = env.items.recordDraw(id)!!
        env.items.setReminder(id, env.clock.millis() + 1000)
        val received = mutableListOf<CompletionRequest>()
        val observer = launch(start = CoroutineStart.UNDISPATCHED) { env.items.completions.collect { received += it } }
        repeat(5) { env.items.completePick(id, history) }; yield()
        assertEquals(1, received.size); assertEquals(1, env.db.phase2Dao().history().size)
        assertEquals(com.notesapp.data.db.entity.SelectionAction.COMPLETED, env.db.phase2Dao().history().single().actionTaken)
        assertNull(env.items.get(id)!!.reminderAt); observer.cancelAndJoin()
    }
    @Test fun historyShowsLatestLinkedReflectionAndDeletionHidesIt() = runBlocking {
        val id = env.items.create(NewItem("Reflected", collection())); val history = env.items.recordDraw(id)!!
        KnowledgeRepository(env.db, env.notes).reflect(id, "My recommendation")
        assertEquals("My recommendation", env.items.observeHistory().first().single().reflectionExcerpt)
        env.items.moveToCollection(listOf(id), null); val deletion = env.items.deleteInbox(listOf(id))
        assertTrue(env.items.observeHistory().first().isEmpty()); env.items.undoDeletion(deletion.operationId)
        assertEquals(history, env.items.observeHistory().first().single().selection.id)
    }
    @Test fun atomicDrawNeverIncludesArchivedOrPendingItemsEvenForAnyStatus() = runBlocking {
        val c = collection(); val archived = env.items.create(NewItem("Archived", c)); env.items.setArchived(archived, true)
        val hidden = env.items.create(NewItem("Pending")); env.items.deleteInbox(listOf(hidden))
        val (result, history, count) = env.items.pickAndRecord(RandomFilter(excludeFinished = false), emptySet(), kotlin.random.Random(1), env.clock.millis(), emptyMap())
        assertEquals(PickResult.NoMatches, result); assertNull(history); assertEquals(0, count); assertTrue(env.db.phase2Dao().history().isEmpty())
    }
}
