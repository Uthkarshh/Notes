package com.notesapp.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.notesapp.domain.model.CategoryType
import com.notesapp.domain.model.INBOX_STATUS
import com.notesapp.domain.model.StatusRole
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CollectionsScreenTest : UiTestBase() {

    private fun openCollection(name: String) {
        val id = collectionByName(name).id
        openTab("collections")
        waitForTag("collection_card_$id")
        click("collection_card_$id")
        waitForTag("view_list")
    }

    private fun yOf(tag: String): Float = tag(tag).fetchSemanticsNode().positionInRoot.y

    @Test
    fun overviewShowsActiveCountsAndProgress() {
        seedDefaults()
        addItem("A", "Movies")
        addItem("B", "Movies", status = "watching")
        addItem("C", "Movies", status = "watched")
        addItem("D", "Movies", status = "dropped")
        val movies = collectionByName("Movies")

        openTab("collections")
        waitForTag("collection_card_${movies.id}")
        eventually(description = "open count and progress") {
            val shown = textsOf("collection_card_${movies.id}")
            "2 active" in shown && "1 of 4 done" in shown
        }
    }

    @Test
    fun createsACollectionFromATemplateWithAnExtraStatus() {
        seedDefaults()
        openTab("collections")
        waitForTag("fab_new_collection")
        click("fab_new_collection")
        waitForTag("template_games")
        click("template_games")

        waitForTag("collection_editor")
        tag("editor_name").assertTextContains("Games")
        replaceText("editor_name", "Video Games")

        scrollIntoView("editor_add_status")
        click("editor_add_status")
        waitForTag("editor_status_label_4")
        scrollIntoView("editor_status_label_4")
        type("editor_status_label_4", "On Hold")
        click("editor_save")

        waitForTag("collections_grid")
        waitForText("Video Games")
        val created = db { collectionRepository.getAll().first { it.name == "Video Games" } }
        assertEquals(CategoryType.CUSTOM, created.categoryType)
        assertEquals(
            listOf("Want to Play", "Playing", "Finished", "Dropped", "On Hold"),
            created.schema.statuses.map { it.label },
        )
        assertEquals("on_hold", created.schema.statuses.last().key)
        assertEquals(StatusRole.ACTIVE, created.schema.statuses.last().role)
    }

    @Test
    fun editorExplainsWhatIsMissingAndKeepsSaveDisabled() {
        seedDefaults()
        openTab("collections")
        waitForTag("fab_new_collection")
        click("fab_new_collection")
        waitForTag("template_list")
        scrollTo("template_list", "template_custom")
        waitForTag("template_custom")
        click("template_custom")
        waitForTag("collection_editor")

        clearText("editor_name")
        tag("editor_save").assertIsNotEnabled()
        text("Give the collection a name").assertIsDisplayed()

        replaceText("editor_name", "Trips")
        tag("editor_save").assertIsEnabled()

        scrollIntoView("editor_status_label_1")
        replaceText("editor_status_label_1", "To Do")
        text("Status names must be different").performScrollTo().assertIsDisplayed()
        tag("editor_save").assertIsNotEnabled()
        replaceText("editor_status_label_1", "Doing")
        tag("editor_save").assertIsEnabled()

        scrollIntoView("editor_status_role_0")
        click("editor_status_role_0")
        waitForTag("editor_role_0_done")
        click("editor_role_0_done")
        waitForText("Include a “Not started” status so new items have a starting point")
        hideKeyboard()
        composeRule.waitForIdle()
        text("Include a “Not started” status so new items have a starting point").performScrollTo()
        composeRule.waitForIdle()
        text("Include a “Not started” status so new items have a starting point").assertIsDisplayed()
        tag("editor_save").assertIsNotEnabled()
    }

    @Test
    fun leavingTheEditorWithUnsavedChangesAsksFirst() {
        seedDefaults()
        val movies = collectionByName("Movies")
        openCollection("Movies")
        click("collection_menu")
        waitForTag("menu_edit_collection")
        click("menu_edit_collection")
        waitForTag("collection_editor")

        replaceText("editor_name", "Films")
        click("editor_close")
        waitForText("Discard changes?")
        tapText("Keep editing")
        waitForTag("collection_editor")

        click("editor_close")
        waitForText("Discard changes?")
        tapText("Discard")
        waitForTag("view_list")
        assertEquals("Movies", db { collectionRepository.get(movies.id)!!.name })
    }

    @Test
    fun editingACollectionRenamesItAndMovesItemsOfRemovedStatuses() {
        seedDefaults()
        val movies = collectionByName("Movies")
        val dropped = addItem("Abandoned film", "Movies", status = "dropped")
        val watched = addItem("Seen film", "Movies", status = "watched")
        openCollection("Movies")

        click("collection_menu")
        waitForTag("menu_edit_collection")
        click("menu_edit_collection")
        waitForTag("collection_editor")
        replaceText("editor_name", "Films")
        scrollIntoView("editor_status_remove_3")
        click("editor_status_remove_3")
        click("editor_save")

        waitForTag("view_list")
        waitForText("Films")
        eventually(description = "removed status remapped") {
            db { itemRepository.get(dropped)!!.status == "want_to_watch" }
        }
        assertEquals("Films", db { collectionRepository.get(movies.id)!!.name })
        assertEquals("watched", db { itemRepository.get(watched)!!.status })
    }

    @Test
    fun deletingACollectionCanKeepItsItemsInTheInbox() {
        seedDefaults()
        val movies = collectionByName("Movies")
        val item = addItem("Arrival", "Movies")
        openCollection("Movies")

        click("collection_menu")
        waitForTag("menu_delete_collection")
        click("menu_delete_collection")
        waitForTag("delete_keep_items")
        click("delete_keep_items")

        waitForTag("collections_grid")
        waitUntilGone("collection_card_${movies.id}")
        eventually(description = "item moved to the inbox") {
            db { itemRepository.get(item)!!.let { it.collectionId == null && it.status == INBOX_STATUS } }
        }
    }

    @Test
    fun deletingACollectionCanRemoveItsItemsToo() {
        seedDefaults()
        val movies = collectionByName("Movies")
        val item = addItem("Arrival", "Movies")
        openCollection("Movies")

        click("collection_menu")
        waitForTag("menu_delete_collection")
        click("menu_delete_collection")
        waitForTag("delete_everything")
        click("delete_everything")

        waitUntilGone("collection_card_${movies.id}")
        eventually(description = "item deleted") { db { itemRepository.get(item) } == null }
    }

    @Test
    fun deletingAnEmptyCollectionNeedsNoDecision() {
        seedDefaults()
        val places = collectionByName("Places")
        openCollection("Places")

        click("collection_menu")
        waitForTag("menu_delete_collection")
        click("menu_delete_collection")
        waitForText("This collection is empty.")
        assertFalse(isShown("delete_keep_items"))
        click("delete_everything")
        waitUntilGone("collection_card_${places.id}")
    }

    @Test
    fun listGridAndBoardShowTheSameItems() {
        seedDefaults()
        val alpha = addItem("Alpha", "Movies")
        val bravo = addItem("Bravo", "Movies", status = "watching")
        val charlie = addItem("Charlie", "Movies", status = "watched")
        openCollection("Movies")

        waitForTag("item_row_$alpha")
        waitForTag("item_row_$bravo")
        waitForTag("item_row_$charlie")

        click("view_grid")
        waitForTag("item_grid")
        waitForTag("item_card_$alpha")
        waitForTag("item_card_$bravo")
        waitForTag("item_card_$charlie")

        click("view_board")
        waitForTag("board")
        waitForTag("board_column_want_to_watch")
        waitForTag("kanban_card_$alpha")
        tag("board").performScrollToNode(hasTestTag("board_column_watched"))
        waitForTag("kanban_card_$charlie")
    }

    @Test
    fun boardCardsMoveBetweenStatusColumns() {
        seedDefaults()
        val alpha = addItem("Alpha", "Movies")
        openCollection("Movies")

        click("view_board")
        waitForTag("kanban_card_$alpha")
        click("kanban_move_$alpha")
        waitForTag("kanban_menu_${alpha}_watching")
        click("kanban_menu_${alpha}_watching")
        eventually(description = "card moved") { db { itemRepository.get(alpha)!!.status == "watching" } }

        // The card now sits in the second lane; bring that lane fully into view before using its menu.
        tag("board").performScrollToNode(hasTestTag("board_column_watching"))
        waitForTag("kanban_move_$alpha")
        click("kanban_move_$alpha")
        waitForTag("kanban_menu_${alpha}_watched")
        click("kanban_menu_${alpha}_watched")
        eventually(description = "card completed") { db { itemRepository.get(alpha)!!.completedAt != null } }
    }

    @Test
    fun sortingReordersTheList() {
        seedDefaults()
        val charlie = addItem("Charlie", "Movies")
        Thread.sleep(5)
        val alpha = addItem("alpha", "Movies")
        Thread.sleep(5)
        val bravo = addItem("Bravo", "Movies")
        openCollection("Movies")

        waitForTag("item_row_$bravo")
        assertTrue("newest first by default", yOf("item_row_$bravo") < yOf("item_row_$alpha"))
        assertTrue(yOf("item_row_$alpha") < yOf("item_row_$charlie"))

        click("sort_button")
        waitForTag("sort_TITLE")
        click("sort_TITLE")
        composeRule.waitUntil(8_000) { yOf("item_row_$alpha") < yOf("item_row_$bravo") }
        assertTrue(yOf("item_row_$bravo") < yOf("item_row_$charlie"))

        click("sort_button")
        waitForTag("sort_TITLE")
        click("sort_TITLE")
        composeRule.waitUntil(8_000) { yOf("item_row_$charlie") < yOf("item_row_$alpha") }
    }

    @Test
    fun filtersNarrowTheListAndCanBeCleared() {
        seedDefaults()
        val starred = addItem("Starred", "Movies", tags = listOf("scifi"))
        val moving = addItem("Moving", "Movies", status = "watching")
        val plain = addItem("Plain", "Movies")
        db { itemRepository.setFavorite(starred, true) }
        openCollection("Movies")
        waitForTag("item_row_$plain")

        click("filter_favorites")
        waitUntilGone("item_row_$plain")
        assertTrue(isShown("item_row_$starred"))
        assertFalse(isShown("item_row_$moving"))
        click("filter_favorites")
        waitForTag("item_row_$plain")

        click("filter_status_watching")
        waitUntilGone("item_row_$plain")
        assertTrue(isShown("item_row_$moving"))
        click("filter_status_watching")

        // Tag chips sit at the end of a horizontally scrolling row and are only composed once scrolled into view.
        scrollFilterRowTo("filter_tag_scifi")
        click("filter_tag_scifi")
        waitUntilGone("item_row_$plain")
        assertTrue(isShown("item_row_$starred"))

        // Filters from different groups combine: scifi items that are also "watching" do not exist.
        scrollFilterRowTo("filter_status_watching")
        click("filter_status_watching")
        waitForText("No items match")
        click("clear_collection_filters")
        waitForTag("item_row_$plain")
        assertTrue(isShown("item_row_$moving"))
    }

    private fun scrollFilterRowTo(target: String) {
        tag("filter_row").performScrollToNode(hasTestTag(target))
    }

    @Test
    fun addingFromACollectionPreselectsIt() {
        seedDefaults()
        val books = collectionByName("Books")
        openCollection("Books")
        waitForText("Nothing here yet")

        click("fab_add_item")
        waitForTag("capture_input")
        tag("capture_collection").assertTextContains("Books", substring = true)
        type("capture_input", "Dune")
        click("capture_save")

        eventually(description = "item filed in the open collection") {
            db { itemRepository.getAllFiled().any { it.item.title == "Dune" && it.item.collectionId == books.id } }
        }
        waitUntilTextGone("Nothing here yet")
    }

    @Test
    fun overviewOffersCreatingTheFirstCollectionWhenEverythingWasDeleted() {
        seedDefaults()
        db { collectionRepository.getAll().forEach { collectionRepository.delete(it.id, deleteItems = true) } }
        assertTrue(db { collectionRepository.getAll().isEmpty() })

        openTab("collections")
        waitForText("No collections yet")
        tapText("Create a collection")
        waitForTag("template_movies")
    }
}
