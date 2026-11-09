package com.notesapp.ui

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.notesapp.domain.model.Priority
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RedesignMilestoneTwoTest : UiTestBase() {
    @Test fun captureIsSharedAcrossTabsAndDismissalRetainsTheDraft() {
        seedDefaults()
        for ((index, tab) in listOf("today", "collections", "vault", "discover").withIndex()) {
            openTab(tab); waitForTag("fab_capture"); click("fab_capture"); waitForTag("capture_input")
            if (index == 0) type("capture_input", "Read Dune #fiction")
            tag("capture_input").assertTextContains("Read Dune #fiction", substring = true)
            tapText("Cancel"); waitUntilGone("capture_input")
            if (tab == "collections") tag("fab_new_collection").assertTextContains("New collection")
            if (tab == "vault") tag("fab_vault_new").assertTextContains("New note")
        }
        click("fab_capture"); waitForTag("capture_input"); click("capture_clear")
        tag("capture_save").assertIsNotEnabled()
        tapText("Cancel")
    }
    @Test fun captureDefaultsToTheOpenCollectionAndExplicitInboxOverridesIt() {
        seedDefaults()
        val movies = collectionByName("Movies")
        openTab("collections"); waitForTag("collection_card_${movies.id}"); click("collection_card_${movies.id}")
        waitForTag("fab_add_item"); click("fab_add_item"); waitForTag("capture_input")
        type("capture_input", "Read Dune")
        tag("capture_collection").assertTextContains("Movies", substring = true)
        click("capture_collection"); click("capture_choice_inbox")
        tag("capture_collection").assertTextContains("Inbox", substring = true)
        click("capture_save"); waitForText("Saved to Inbox")
        assertEquals("Dune", db { itemRepository.observeInbox().first().single().item.title })
    }
    @Test fun tokenControlsReplacePriorityAndRemoveOnlyTheChosenTags() {
        seedDefaults(); click("fab_capture"); waitForTag("capture_input")
        type("capture_input", "Task #old #keep !low !urgent")
        click("capture_suggest_priority"); click("capture_priority_high")
        click("capture_tag_old")
        click("capture_suggest_tag"); type("capture_new_tag", "focus"); click("capture_add_tag")
        tag("capture_hints").assertTextContains("High priority", substring = true)
        click("capture_save"); waitForText("Saved to Inbox")
        val saved = db { itemRepository.observeInbox().first().single() }
        assertEquals("Task", saved.item.title)
        assertEquals(Priority.HIGH, saved.item.priorityLevel)
        assertEquals(setOf("keep", "focus"), saved.tags.toSet())
    }
    @Test fun anUnknownCollectionStaysInTheTitleAndHasAnHonestPreview() {
        seedDefaults(); click("fab_capture"); waitForTag("capture_input")
        type("capture_input", "Watch Arrival @missing #scifi")
        waitForTag("capture_unresolved")
        tag("capture_collection").assertTextContains("Inbox", substring = true)
        click("capture_save"); waitForText("Saved to Inbox")
        assertEquals("Arrival @missing", db { itemRepository.observeInbox().first().single().item.title })
    }
    @Test fun tenCapturesFileInOneBatchAndOneUndoRestoresAllOfThem() {
        seedDefaults(); val movies = collectionByName("Movies")
        repeat(10) { addItem("Capture $it", null) }
        click("action_inbox"); waitForTag("inbox_select"); click("inbox_select"); click("inbox_select")
        waitForText("10 selected"); click("inbox_batch_file"); click("inbox_choice_${movies.id}")
        waitForText("Inbox zero")
        eventually { db { itemRepository.getAllFiled().size == 10 } }
        tapText("Undo")
        eventually { db { itemRepository.observeInboxCount().first() == 10 } }
        waitForText("Inbox (10)")
    }
    @Test fun swipingDeleteCanBeRestoredWithoutLosingTags() {
        seedDefaults(); val id = addItem("Swipe capture", null, tags = listOf("keep"))
        click("action_inbox"); waitForTag("inbox_swipe_$id")
        tag("inbox_swipe_$id").performTouchInput { swipeLeft() }
        eventually { db { itemRepository.get(id) == null } }
        waitForText("Restore"); tapText("Restore"); waitForTag("inbox_card_$id")
        assertEquals(listOf("keep"), db { itemRepository.observeInbox().first().single().tags })
    }
    @Test fun fileSwipeUsesAnInferenceAndOtherwiseAsksForADestination() {
        seedDefaults(); val places = collectionByName("Places"); val books = collectionByName("Books")
        val inferred = addItem("Visit Kyoto", null)
        val unknown = addItem("Call mom", null)
        click("action_inbox"); waitForTag("inbox_swipe_$inferred")
        tag("inbox_swipe_$inferred").performTouchInput { swipeRight() }
        eventually { db { itemRepository.get(inferred)?.collectionId == places.id } }
        waitForTag("inbox_swipe_$unknown")
        tag("inbox_swipe_$unknown").performTouchInput { swipeRight() }
        waitForTag("inbox_choice_${books.id}"); click("inbox_choice_${books.id}")
        eventually { db { itemRepository.get(unknown)?.collectionId == books.id } }
    }
    @Test fun selectionUsesIdsAndSurvivesActivityRecreation() {
        seedDefaults(); val id = addItem("Select me", null); addItem("Leave me", null)
        click("action_inbox"); waitForTag("inbox_select"); click("inbox_select"); click("inbox_check_$id")
        waitForText("1 selected")
        composeRule.activityRule.scenario.recreate()
        waitForText("1 selected"); tag("inbox_check_$id").assertIsOn()
    }
    @Test fun removingTheChosenCollectionKeepsTheDraftAndRequiresANewDestination() {
        seedDefaults(); val movies = collectionByName("Movies")
        click("fab_capture"); waitForTag("capture_input"); type("capture_input", "Keep this draft")
        click("capture_collection"); click("capture_choice_${movies.id}")
        db { collectionRepository.delete(movies.id, deleteItems = false) }
        waitForTag("capture_missing_collection")
        tag("capture_save").assertIsNotEnabled()
        tag("capture_input").assertTextContains("Keep this draft", substring = true)
        click("capture_collection"); click("capture_choice_inbox"); click("capture_save")
        waitForText("Saved to Inbox")
        assertEquals("Keep this draft", db { itemRepository.observeInbox().first().single().item.title })
    }

}
