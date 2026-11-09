package com.notesapp.ui

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.notesapp.data.repository.KnowledgeRepository
import com.notesapp.domain.metadata.ItemMetadata
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RedesignMilestoneThreeTest : UiTestBase() {
    private fun openMovies() {
        openTab("collections"); val c = collectionByName("Movies"); waitForTag("collection_card_${c.id}"); click("collection_card_${c.id}"); waitForTag("view_list")
    }
    @Test fun presetAndAdvancedDraftCancelApplyAndRecreation() {
        seedDefaults(); val id = addItem("Short film", "Movies"); addItem("Unknown duration", "Movies")
        db { itemRepository.saveMetadata(id, ItemMetadata(runtimeMinutes = 60)) }
        openTab("discover"); click("preset_quick_watch"); eventually { textOf("discover_match_count") == "1 eligible" }
        click("discover_advanced"); click("discover_high_priority"); click("discover_cancel"); waitUntilGone("discover_filter_sheet")
        assertEquals("1 eligible", textOf("discover_match_count"))
        click("discover_advanced"); scrollIntoView("discover_runtime"); click("discover_runtime"); tapText("Up to 30 minutes")
        click("discover_apply"); waitUntilGone("discover_filter_sheet"); eventually { textOf("discover_match_count") == "0 eligible" }
        composeRule.activityRule.scenario.recreate(); waitForTag("discover_summary"); tag("discover_summary").assertTextContains("30 min", substring = true)
    }
    @Test fun viewSortAndFiltersSurviveLeavingCollection() {
        seedDefaults(); val a = addItem("A long movie title for a quiet evening", "Movies"); addItem("Other", "Movies")
        db { itemRepository.setFavorite(a, true) }; openMovies(); click("view_grid"); click("filter_favorites")
        click("sort_button"); click("sort_TITLE"); waitForTag("item_card_$a")
        eventually { textOf("collection_match_count").startsWith("1 of 2") }
        click("back"); waitForTag("collection_card_${collectionByName("Movies").id}"); click("collection_card_${collectionByName("Movies").id}")
        waitForTag("item_grid"); tag("filter_favorites").assertIsSelected(); tag("collection_match_count").assertTextContains("Title", substring = true)
        click("clear_collection_filters"); eventually { textOf("collection_match_count").startsWith("2 of 2") }
    }
    @Test fun listSwipeAdvancesAndStatusChipOffersUndo() {
        seedDefaults(); val id = addItem("Arrival", "Movies"); openMovies(); waitForTag("collection_swipe_$id")
        tag("collection_swipe_$id").performTouchInput { swipeRight() }
        eventually { db { itemRepository.get(id)!!.status == "watching" } }
        waitForText("Undo"); tapText("Undo"); eventually { db { itemRepository.get(id)!!.status == "want_to_watch" } }
        click("status_menu_$id"); click("status_to_${id}_watched"); waitForTag("reflection_skip"); click("reflection_skip")
        eventually { db { itemRepository.get(id)!!.status == "watched" } }
    }
    @Test fun detailStartsCompactAndRestoredErrorOpensItsGroup() {
        seedDefaults(); val id = addItem("Arrival", "Movies"); openMovies(); click("item_row_$id"); waitForTag("item_detail")
        tag("metadata_runtimeMinutes").assertDoesNotExist(); tag("item_description").assertExists()
        scrollIntoView("metadata_runtimeMinutes"); replaceText("metadata_runtimeMinutes", "unfinished"); hideKeyboard(); click("back")
        waitForTag("item_row_$id"); click("item_row_$id"); waitForTag("item_detail")
        waitForTag("metadata_runtimeMinutes"); tag("metadata_runtimeMinutes").assertTextContains("unfinished")
    }
    @Test fun masteryChipCanChangeFactWithoutOpeningEditor() {
        seedDefaults(); val id = db { factRepository.create("A useful fact") }
        openTab("vault"); click("vault_segment_facts"); waitForTag("fact_mastery_$id"); click("fact_mastery_$id"); click("fact_mastery_to_learned_$id")
        eventually { db { factRepository.get(id)!!.masteryState == com.notesapp.domain.model.MasteryState.LEARNED } }
        tag("fact_card_$id").assertExists()
    }
    @Test fun completionPromptsQueueAndKeepEachReflectionWithItsItem() {
        seedDefaults(); val a = addItem("First completion", "Movies"); val b = addItem("Second completion", "Movies")
        waitForTag("today_list")
        db { itemRepository.setStatus(a, "watched", requestReflection = true); itemRepository.setStatus(b, "watched", requestReflection = true) }
        waitForText("First completion · Would you recommend it?"); type("reflection_input", "First recommendation"); click("reflection_save")
        waitForText("Second completion · Would you recommend it?"); assertEquals("", tag("reflection_input").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text)
        click("reflection_skip"); waitUntilGone("reflection_sheet")
        val notes = db { noteRepository.observeAll().first() }
        assertEquals(1, notes.size); assertEquals("First recommendation", notes.single().note.content)
    }
}
