package com.notesapp.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.notesapp.domain.model.Priority
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ItemDetailScreenTest : UiTestBase() {

    private fun openItem(collection: String, id: Long) {
        val collectionId = collectionByName(collection).id
        openTab("collections")
        waitForTag("collection_card_$collectionId")
        click("collection_card_$collectionId")
        waitForTag("item_row_$id")
        click("item_row_$id")
        waitForTag("item_detail")
    }

    @Test
    fun inlineControlsSaveImmediately() {
        seedDefaults()
        val id = addItem("Arrival", "Movies")
        openItem("Movies", id)

        click("item_status_watching")
        eventually(description = "status") { db { itemRepository.get(id)!!.status == "watching" } }

        scrollIntoView("item_rating")
        tag("item_rating").performSemanticsAction(SemanticsActions.SetProgress) { it(4.5f) }
        eventually(description = "rating") { db { itemRepository.get(id)!!.rating == 4.5f } }
        eventually(description = "rating reaches the screen") { textOf("item_rating_text").contains("4.5") }
        tag("item_rating_text").assertTextContains("4.5", substring = true)

        click("item_rating_clear")
        eventually(description = "rating cleared") { db { itemRepository.get(id)!!.rating == null } }

        scrollIntoView("item_priority_urgent"); click("item_priority_urgent")
        eventually(description = "priority") { db { itemRepository.get(id)!!.priorityLevel == Priority.URGENT } }

        click("item_favorite")
        eventually(description = "favorite") { db { itemRepository.get(id)!!.isFavorite } }

        scrollIntoView("item_status_watched"); click("item_status_watched")
        eventually(description = "completion") { db { itemRepository.get(id)!!.completedAt != null } }
    }

    @Test
    fun textEditsAndTagsAreSavedWhenLeavingTheScreen() {
        seedDefaults()
        val id = addItem("Arrival", "Movies")
        openItem("Movies", id)

        replaceText("item_title", "Arrival (2016)")
        scrollIntoView("item_description")
        replaceText("item_description", "Linguist meets aliens")
        scrollIntoView("item_link")
        type("item_link", "https://example.com/arrival")
        scrollIntoView("tag_input")
        type("tag_input", "scifi, drama ")
        text("#scifi").assertIsDisplayed()
        text("#drama").assertIsDisplayed()

        click("back")
        waitForTag("item_row_$id")
        waitForText("Arrival (2016)")
        eventually(description = "details saved") { db { itemRepository.get(id)!!.title == "Arrival (2016)" } }

        val saved = db { itemRepository.observeItem(id).first()!! }
        assertEquals("Linguist meets aliens", saved.item.description)
        assertEquals("https://example.com/arrival", saved.item.externalUrl)
        assertEquals(listOf("drama", "scifi"), saved.tags)
        assertEquals("the new description is searchable", 1, db { searchRepository.search("aliens") }.size)
    }

    @Test
    fun aBlankTitleIsNeverSaved() {
        seedDefaults()
        val id = addItem("Arrival", "Movies")
        openItem("Movies", id)

        replaceText("item_title", "   ")
        click("back")
        waitForTag("item_row_$id")
        assertEquals("Arrival", db { itemRepository.get(id)!!.title })
    }

    @Test
    fun removingATagTakesItOffTheItem() {
        seedDefaults()
        val id = addItem("Arrival", "Movies", tags = listOf("scifi", "drama"))
        openItem("Movies", id)

        scrollIntoView("tag_input")
        composeRule.onNodeWithContentDescription("Remove tag scifi").performClick()
        click("back")
        waitForTag("item_row_$id")
        eventually(description = "tag removed") { db { itemRepository.observeItem(id).first()!!.tags } == listOf("drama") }
    }

    @Test
    fun movingAnItemToAnotherCollectionResetsItsStatus() {
        seedDefaults()
        val books = collectionByName("Books")
        val id = addItem("Dune", "Movies", status = "watched")
        openItem("Movies", id)

        scrollIntoView("item_collection")
        click("item_collection")
        waitForText("Books")
        tapText("Books")

        eventually(description = "item moved") {
            db { itemRepository.get(id)!!.let { it.collectionId == books.id && it.status == "want_to_read" && it.completedAt == null } }
        }
        waitForTag("item_status_reading")
    }

    @Test
    fun inboxItemsAreFiledByChoosingACollection() {
        seedDefaults()
        val id = addItem("Loose thought", null)
        val movies = collectionByName("Movies")

        openTab("today")
        waitForTag("inbox_banner")
        click("inbox_banner")
        waitForTag("inbox_card_$id")
        tapText("Open")
        waitForTag("item_detail")
        text("This item is in your Inbox. Move it to a collection to track its status.").assertIsDisplayed()

        scrollIntoView("item_collection")
        click("item_collection")
        waitForText("Movies")
        tapText("Movies")
        eventually(description = "filed") { db { itemRepository.get(id)!!.collectionId == movies.id } }
        waitForTag("item_status_watching")
    }

    @Test
    fun archivingHidesTheItemFromItsCollection() {
        seedDefaults()
        val id = addItem("Arrival", "Movies")
        openItem("Movies", id)

        click("item_menu")
        waitForTag("menu_archive")
        click("menu_archive")
        eventually(description = "archived") { db { itemRepository.get(id)!!.isArchived } }
        waitForText("Archived — hidden from lists and Surprise me")

        click("back")
        waitForText("Nothing here yet")
        assertFalse(isShown("item_row_$id"))
    }

    @Test
    fun deletingAnItemAsksForConfirmationAndReturnsToTheList() {
        seedDefaults()
        val id = addItem("Arrival", "Movies")
        addItem("Heat", "Movies")
        openItem("Movies", id)

        click("item_menu")
        waitForTag("menu_delete")
        click("menu_delete")
        waitForText("This can't be undone.")
        tapText("Cancel")
        assertNotNull(db { itemRepository.get(id) })

        click("item_menu")
        waitForTag("menu_delete")
        click("menu_delete")
        waitForText("This can't be undone.")
        tapText("Delete")

        eventually(description = "deleted") { db { itemRepository.get(id) } == null }
        waitForText("Heat")
        waitUntilTextGone("Arrival")
    }
}
