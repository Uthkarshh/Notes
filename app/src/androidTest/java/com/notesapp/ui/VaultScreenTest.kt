package com.notesapp.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.notesapp.domain.model.MasteryState
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class VaultScreenTest : UiTestBase() {

    private fun yOf(tag: String): Float = tag(tag).fetchSemanticsNode().positionInRoot.y

    private fun openFacts() {
        openTab("vault")
        waitForTag("vault_segment_facts")
        click("vault_segment_facts")
    }

    // --- notes ---------------------------------------------------------------------------------------------

    @Test
    fun emptyVaultOffersToWriteTheFirstNote() {
        openTab("vault")
        waitForText("No notes yet")
        tapText("Write a note")
        waitForTag("note_title")
    }

    @Test
    fun aNewNoteIsSavedWhenLeavingTheEditor() {
        openTab("vault")
        waitForTag("fab_vault_new")
        click("fab_vault_new")
        waitForTag("note_title")

        type("note_title", "Trip plan")
        type("note_content", "Pack the tripod and a spare battery")
        scrollIntoView("tag_input")
        type("tag_input", "travel,")
        click("back")

        waitForTag("notes_list")
        waitForText("Trip plan")
        val saved = db { noteRepository.observeAll().first().single() }
        assertEquals("Trip plan", saved.note.title)
        assertEquals("Pack the tripod and a spare battery", saved.note.content)
        assertEquals(listOf("travel"), saved.tags)
        assertEquals(1, db { searchRepository.search("tripod") }.size)
    }

    @Test
    fun blankNotesAreNeverCreated() {
        openTab("vault")
        waitForTag("fab_vault_new")
        click("fab_vault_new")
        waitForTag("note_title")
        pressBack()

        waitForText("No notes yet")
        assertTrue(db { noteRepository.observeAll().first().isEmpty() })
    }

    @Test
    fun emptyingAnExistingNoteDeletesIt() {
        val id = db { noteRepository.create("Scratch", "temporary thoughts") }
        openTab("vault")
        waitForTag("note_card_$id")
        click("note_card_$id")
        waitForTag("note_title")

        replaceText("note_title", "")
        replaceText("note_content", "")
        click("back")

        waitForText("No notes yet")
        eventually(description = "note deleted") { db { noteRepository.get(id) } == null }
        assertEquals(0, db { searchRepository.search("temporary") }.size)
    }

    @Test
    fun editingANoteKeepsItsIdentityAndUpdatesTheSearchIndex() {
        val id = db { noteRepository.create("Groceries", "milk and eggs") }
        openTab("vault")
        waitForTag("note_card_$id")
        click("note_card_$id")
        waitForTag("note_content")

        replaceText("note_content", "oat milk and sourdough")
        click("back")
        waitForTag("notes_list")

        eventually(description = "note updated") { db { noteRepository.get(id)!!.content == "oat milk and sourdough" } }
        assertEquals(1, db { noteRepository.observeAll().first().size })
        assertEquals(0, db { searchRepository.search("eggs") }.size)
        assertEquals(1, db { searchRepository.search("sourdough") }.size)
    }

    @Test
    fun pinnedNotesComeFirstAndTagsFilterTheList() {
        val older = db { noteRepository.create("Older note", "a", listOf("work")) }
        Thread.sleep(5)
        val newer = db { noteRepository.create("Newer note", "b", listOf("home")) }
        openTab("vault")
        waitForTag("note_card_$newer")
        assertTrue("newest first", yOf("note_card_$newer") < yOf("note_card_$older"))

        click("note_pin_$older")
        composeRule.waitUntil(8_000) { yOf("note_card_$older") < yOf("note_card_$newer") }
        assertTrue(db { noteRepository.get(older)!!.isPinned })

        click("note_filter_work")
        waitUntilGone("note_card_$newer")
        assertTrue(isShown("note_card_$older"))
        click("note_filter_work")
        waitForTag("note_card_$newer")
    }

    @Test
    fun deletingFromTheNoteEditorNeedsConfirmation() {
        val id = db { noteRepository.create("Doomed", "bye") }
        openTab("vault")
        waitForTag("note_card_$id")
        click("note_card_$id")
        waitForTag("note_menu")
        click("note_menu")
        waitForTag("note_delete")
        click("note_delete")
        waitForText("This can't be undone.")
        tapText("Delete")

        waitForText("No notes yet")
        eventually(description = "note deleted") { db { noteRepository.get(id) } == null }
    }

    // --- facts ---------------------------------------------------------------------------------------------

    @Test
    fun aNewFactKeepsAllItsDetails() {
        openFacts()
        waitForText("No facts yet")
        click("fab_vault_new")
        waitForTag("fact_text_field")

        type("fact_text_field", "Octopuses have three hearts")
        type("fact_source", "Marine biology")
        type("fact_topic", "Animals")
        scrollIntoView("fact_mastery_reviewing")
        click("fact_mastery_reviewing")
        scrollIntoView("fact_favorite_switch")
        click("fact_favorite_switch")
        scrollIntoView("tag_input")
        type("tag_input", "trivia,")
        click("back")

        waitForTag("facts_list")
        val saved = db { factRepository.observeAll().first().single() }
        assertEquals("Octopuses have three hearts", saved.fact.text)
        assertEquals("Marine biology", saved.fact.source)
        assertEquals("Animals", saved.fact.topic)
        assertEquals(MasteryState.REVIEWING, saved.fact.masteryState)
        assertTrue(saved.fact.isFavorite)
        assertEquals(listOf("trivia"), saved.tags)
    }

    @Test
    fun editingAFactDoesNotResetItsCounters() {
        val id = db { factRepository.create("Honey never spoils", topic = "Food") }
        // Today picks the fact of the day (an impression) in the background; wait for that to settle first.
        waitForTag("fact_text")
        val shownBefore = db { factRepository.get(id)!!.shownCount }
        openFacts()
        waitForTag("fact_card_$id")
        click("fact_card_$id")
        waitForTag("fact_text_field")

        replaceText("fact_text_field", "Honey never really spoils")
        click("back")

        eventually(description = "fact updated") { db { factRepository.get(id)!!.text == "Honey never really spoils" } }
        assertEquals(shownBefore, db { factRepository.get(id)!!.shownCount })
        assertEquals("Food", db { factRepository.get(id)!!.topic })
    }

    @Test
    fun factsCanBeFilteredByTopicTagAndMastery() {
        val hearts = db { factRepository.create("Octopuses have three hearts", topic = "Animals", tags = listOf("trivia")) }
        val honey = db { factRepository.create("Honey never spoils", topic = "Food") }
        val secret = db { factRepository.create("Hidden gem") }
        db { factRepository.setMastery(honey, MasteryState.LEARNED) }
        db { factRepository.setMastery(secret, MasteryState.HIDDEN) }
        openFacts()

        waitForTag("fact_card_$hearts")
        assertTrue(isShown("fact_card_$honey"))
        assertFalse("hidden facts stay out of the default list", isShown("fact_card_$secret"))

        click("fact_topic_Animals")
        waitUntilGone("fact_card_$honey")
        assertTrue(isShown("fact_card_$hearts"))
        click("fact_topic_Animals")
        waitForTag("fact_card_$honey")

        click("fact_tag_trivia")
        waitUntilGone("fact_card_$honey")
        click("fact_tag_trivia")
        waitForTag("fact_card_$honey")

        click("fact_mastery_learned")
        waitUntilGone("fact_card_$hearts")
        assertTrue(isShown("fact_card_$honey"))

        click("fact_mastery_hidden")
        waitForTag("fact_card_$secret")
        assertFalse(isShown("fact_card_$honey"))
    }

    @Test
    fun masteryAndFavoritesChangeFromTheCardMenu() {
        val id = db { factRepository.create("Bananas are berries") }
        openFacts()
        waitForTag("fact_card_$id")

        click("fact_card_favorite_$id")
        eventually(description = "favorite") { db { factRepository.get(id)!!.isFavorite } }

        click("fact_card_menu_$id")
        waitForTag("fact_mark_learned_$id")
        click("fact_mark_learned_$id")
        eventually(description = "learned") { db { factRepository.get(id)!!.masteryState == MasteryState.LEARNED } }

        click("fact_card_menu_$id")
        waitForTag("fact_mark_hidden_$id")
        click("fact_mark_hidden_$id")
        waitUntilGone("fact_card_$id")
        eventually(description = "hidden") { db { factRepository.get(id)!!.masteryState == MasteryState.HIDDEN } }
    }

    @Test
    fun deletingAFactFromTheListAsksFirst() {
        val id = db { factRepository.create("Temporary fact") }
        openFacts()
        waitForTag("fact_card_$id")
        click("fact_card_menu_$id")
        waitForTag("fact_delete_$id")
        click("fact_delete_$id")
        waitForText("Delete this fact?")
        tapText("Delete")

        waitUntilGone("fact_card_$id")
        eventually(description = "fact deleted") { db { factRepository.get(id) } == null }
    }

    @Test
    fun theDiceDrawsRandomFactsAndCountsThemAsSeen() {
        val a = db { factRepository.create("Fact A") }
        val b = db { factRepository.create("Fact B") }
        // Today picks the fact of the day (an impression) in the background; wait for that to settle first.
        waitForTag("fact_text")
        fun shown(id: Long) = db { factRepository.get(id)!!.shownCount }
        val shownBeforeA = shown(a)
        val shownBeforeB = shown(b)

        openFacts()
        waitForTag("vault_random_fact")
        click("vault_random_fact")
        waitForTag("random_fact")
        eventually(description = "first draw recorded") { shown(a) + shown(b) == shownBeforeA + shownBeforeB + 1 }
        val firstDrawnWasA = shown(a) > shownBeforeA

        click("random_fact_another")
        eventually(description = "a different fact was drawn next") {
            if (firstDrawnWasA) shown(b) == shownBeforeB + 1 else shown(a) == shownBeforeA + 1
        }

        click("random_fact_close")
        waitUntilGone("random_fact")
    }

    @Test
    fun theDiceDrawsFromTheFilteredFactsOnly() {
        db { factRepository.create("About animals", topic = "Animals") }
        db { factRepository.create("About food", topic = "Food") }
        openFacts()
        waitForTag("fact_topic_Animals")
        click("fact_topic_Animals")
        waitUntilTextGone("About food")

        click("vault_random_fact")
        waitForTag("random_fact")
        assertTrue(isTextShown("About animals"))
        assertFalse(isTextShown("About food"))
    }
}
