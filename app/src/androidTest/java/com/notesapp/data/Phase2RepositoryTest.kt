package com.notesapp.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.notesapp.data.db.entity.SelectionAction
import com.notesapp.data.repository.KnowledgeRepository
import com.notesapp.data.repository.NewItem
import com.notesapp.domain.model.MasteryState
import com.notesapp.domain.model.EntityType
import com.notesapp.domain.random.PickRandomItem
import com.notesapp.domain.random.RandomFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase2RepositoryTest : DataTestBase() {
    @Test fun drawnAndAcceptedIsOneHistoryEntryAndOneSelectionCount() = runTest {
        val c = collection()
        val item = env.items.create(NewItem("Arrival", c))
        val history = env.items.recordDraw(item)!!
        env.items.acceptPick(item, history)
        assertEquals(1, env.items.get(item)!!.selectedCount)
        assertEquals(SelectionAction.ACCEPTED, env.db.phase2Dao().history().single().actionTaken)
        env.items.updatePick(history, item, SelectionAction.COMPLETED)
        assertEquals(1, env.db.phase2Dao().history().size)
        assertEquals(SelectionAction.COMPLETED, env.items.observeHistory().first().single().selection.actionTaken)
    }
    @Test fun cooldownUsesDrawTimeIncludingSkippedPicks() = runTest {
        val c = collection()
        val item = env.items.create(NewItem("Arrival", c))
        val history = env.items.recordDraw(item)!!
        env.items.updatePick(history, item, SelectionAction.SKIPPED)
        assertTrue(PickRandomItem.candidates(env.items.getAllFiled(), listOf(c), RandomFilter(cooldownDays = 7), env.clock.millis()).isEmpty())
        env.clock.advanceDays(8)
        assertEquals(1, PickRandomItem.candidates(env.items.getAllFiled(), listOf(c), RandomFilter(cooldownDays = 7), env.clock.millis()).size)
    }
    @Test fun deletingAnItemRemovesItsHistoryAndLinksButKeepsTheReflectionNote() = runTest {
        val c = collection()
        val item = env.items.create(NewItem("Arrival", c))
        env.items.recordDraw(item)
        val note = KnowledgeRepository(env.db, env.notes).reflect(item, "A wonderful film")!!
        env.items.delete(item)
        assertTrue(env.db.phase2Dao().history().isEmpty())
        assertTrue(env.db.phase2Dao().links().isEmpty())
        assertNotNull(env.notes.get(note))
    }
    @Test fun reflectionsAreLinkedSearchableAndInheritTags() = runTest {
        val c = collection()
        val item = env.items.create(NewItem("Arrival", c, listOf("scifi")))
        val knowledge = KnowledgeRepository(env.db, env.notes)
        val note = knowledge.reflect(item, "What language means")!!
        assertEquals(note, knowledge.observeNotes(item).first().single().id)
        assertEquals(listOf("scifi"), env.notes.observe(note).first()!!.tags)
        assertEquals(EntityType.NOTE, env.search.search("language").single().type)
        env.notes.delete(note)
        assertTrue(env.db.phase2Dao().links().isEmpty())
    }
    @Test fun notificationFiltersDoNotSelectHiddenOrUnrelatedFacts() = runTest {
        val match = env.facts.create("Ocean fact", source = "Nature book", topic = "Animals")
        env.facts.create("Food fact", source = "Nature book", topic = "Food")
        val hidden = env.facts.create("Hidden ocean fact", source = "Nature book", topic = "Animals")
        env.facts.setMastery(hidden, MasteryState.HIDDEN)
        assertEquals(match, env.rotateDailyFact.forNotification("Animals", "book"))
        assertEquals(match, env.rotateDailyFact.forNotification("Animals", "book"))
        assertEquals(1, env.facts.get(match)!!.shownCount)
        assertNull(env.rotateDailyFact.forNotification("Unknown", null))
    }
}
