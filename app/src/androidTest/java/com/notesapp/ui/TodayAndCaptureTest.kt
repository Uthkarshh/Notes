package com.notesapp.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.notesapp.domain.model.Priority
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TodayScreenTest : UiTestBase() {

    @Test
    fun emptyTodayGuidesTheUserAndHidesTheInboxBanner() {
        seedDefaults()
        waitForTag("morning_fact")
        text("A little knowledge, every day.").assertIsDisplayed()
        tag("up_next_empty").assertIsDisplayed()
        assertFalse(isShown("inbox_banner"))
    }

    @Test
    fun navigationBarSwitchesBetweenTheFourMainDestinations() {
        seedDefaults()
        waitForTag("fab_capture")

        openTab("collections")
        waitForTag("collections_grid")
        openTab("vault")
        waitForTag("vault_segment_notes")
        openTab("discover")
        waitForTag("discover_draw")
        openTab("today")
        waitForTag("fab_capture")
    }

    @Test
    fun morningFactCanBeFavoritedAndSwappedForTheNextOne() {
        val first = db { factRepository.create("The first fact", source = "Source A") }
        val second = db { factRepository.create("The second fact") }

        waitForTag("fact_text")
        tag("fact_text").assertTextEquals("The first fact")
        text("Source A").assertIsDisplayed()

        click("fact_favorite")
        eventually(description = "fact marked as favorite") { db { factRepository.get(first)!!.isFavorite } }

        click("fact_next")
        waitUntilTextGone("The first fact")
        tag("fact_text").assertTextEquals("The second fact")
        eventually(description = "impression recorded for both facts") {
            db { factRepository.get(first)!!.shownCount == 1 && factRepository.get(second)!!.shownCount == 1 }
        }
    }

    @Test
    fun inboxBannerAppearsWhenItemsNeedTriageAndOpensTheInbox() {
        seedDefaults()
        addItem("Call the dentist", null)
        addItem("Sourdough starter", null)

        waitForTag("inbox_banner")
        text("2 captures to file").assertIsDisplayed()
        tag("action_inbox").assertTextContains("2")

        click("inbox_banner")
        waitForTag("inbox_list")
        text("Inbox (2)").assertIsDisplayed()
    }

    @Test
    fun inProgressItemsAppearUnderUpNext() {
        seedDefaults()
        val watching = addItem("The Matrix", "Movies", status = "watching")
        addItem("Heat", "Movies")
        val planned = addItem("Kyoto", "Places", status = "planned")

        waitForTag("up_next_row")
        waitForTag("up_next_$watching")
        waitForTag("up_next_$planned")
        assertFalse("backlog items are not in progress", isTextShown("Heat"))
    }

    @Test
    fun surpriseChipDrawsAnItemAndAcceptingStartsIt() {
        seedDefaults()
        val id = addItem("Arrival", "Movies")

        waitForTag("surprise_any")
        click("surprise_any")
        waitForTag("surprise_card")
        tag("surprise_title").assertTextEquals("Arrival")

        click("surprise_accept")
        waitForTag("surprise_confirmation")
        tag("surprise_confirmation").assertTextContains("Watching", substring = true)
        eventually(description = "item started") {
            db { itemRepository.get(id)!!.let { it.status == "watching" && it.selectedCount == 1 && it.lastSelectedAt != null } }
        }

        pressBack()
        waitUntilGone("surprise_card")
        waitForTag("up_next_$id")
    }

    @Test
    fun surpriseCanCompleteAnItemRightFromTheCard() {
        seedDefaults()
        val id = addItem("Arrival", "Movies")

        click("surprise_any")
        waitForTag("surprise_card")
        click("surprise_complete")
        waitForTag("reflection_sheet")
        tag("surprise_card").assertDoesNotExist()
        eventually(description = "item completed") {
            db { itemRepository.get(id)!!.let { it.status == "watched" && it.completedAt != null } }
        }
    }

    @Test
    fun surpriseExplainsWhenThereIsNothingToPick() {
        seedDefaults()
        waitForTag("surprise_any")
        click("surprise_any")
        waitForTag("surprise_message")
        text("Nothing matches").assertIsDisplayed()
    }

    @Test
    fun surpriseSkipsFinishedItems() {
        seedDefaults()
        addItem("A movie", "Movies")
        addItem("Seen already", "Movies", status = "watched")


        click("surprise_any")
        waitForTag("surprise_card")
        tag("surprise_title").assertTextEquals("A movie")
    }

    @Test
    fun rollingAgainSkipsDrawnItemsUntilEverythingWasSkipped() {
        seedDefaults()
        addItem("One", "Movies")
        addItem("Two", "Movies")

        click("surprise_any")
        waitForTag("surprise_card")
        val firstTitle = textOf("surprise_title")

        click("surprise_roll_again")
        composeRule.waitUntil(8_000) { isShown("surprise_title") && textOf("surprise_title") != firstTitle }
        val secondTitle = textOf("surprise_title")
        assertNotEquals(firstTitle, secondTitle)

        click("surprise_roll_again")
        waitForTag("surprise_message")
        text("You've skipped everything").assertIsDisplayed()

        tapText("Start over")
        waitForTag("surprise_card")
    }

    @Test
    fun acceptedPickCanBeOpenedFromTheSheet() {
        seedDefaults()
        val id = addItem("Arrival", "Movies")

        click("surprise_any")
        waitForTag("surprise_accept")
        click("surprise_accept")
        waitForTag("surprise_open")
        click("surprise_open")

        waitForTag("item_detail")
        tag("item_title").assertTextContains("Arrival")
        assertNotNull(db { itemRepository.get(id) })
    }
}

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class QuickCaptureTest : UiTestBase() {

    @Test
    fun captureParsesVerbTagAndPriorityAndFilesIntoTheDetectedCollection() {
        seedDefaults()
        waitForTag("fab_capture")
        click("fab_capture")
        waitForTag("capture_input")

        type("capture_input", "Watch Arrival #scifi !high")
        composeRule.waitForIdle()
        tag("capture_collection").assertTextContains("Movies", substring = true)
        tag("capture_hints").assertTextContains("#scifi", substring = true)
        tag("capture_hints").assertTextContains("High priority", substring = true)

        click("capture_save")
        waitForText("Saved to Movies")
        eventually(description = "item saved") { db { itemRepository.getAllFiled().any { it.item.title == "Arrival" } } }

        val saved = db { itemRepository.getAllFiled().single() }
        assertEquals("Arrival", saved.item.title)
        assertEquals(collectionByName("Movies").id, saved.item.collectionId)
        assertEquals("want_to_watch", saved.item.status)
        assertEquals(listOf("scifi"), saved.tags)
        assertEquals(Priority.HIGH, saved.item.priorityLevel)
    }

    @Test
    fun captureWithoutHintsGoesToTheInboxAndRaisesTheBadgeAndBanner() {
        seedDefaults()
        waitForTag("fab_capture")
        click("fab_capture")
        waitForTag("capture_input")

        type("capture_input", "Call the dentist")
        composeRule.waitForIdle()
        tag("capture_collection").assertTextContains("Inbox", substring = true)
        click("capture_save")

        waitForText("Saved to Inbox")
        waitForTag("inbox_banner")
        text("1 capture to file").assertIsDisplayed()
        tag("action_inbox").assertTextContains("1")
        assertEquals(1, db { itemRepository.observeInbox().first().size })
    }

    @Test
    fun saveStaysDisabledUntilThereIsATitle() {
        seedDefaults()
        waitForTag("fab_capture")
        click("fab_capture")
        waitForTag("capture_input")

        tag("capture_save").assertIsNotEnabled()
        type("capture_input", "#idea !high")
        composeRule.waitForIdle()
        tag("capture_save").assertIsNotEnabled()

        type("capture_input", " Learn the cello")
        composeRule.waitForIdle()
        tag("capture_save").assertIsEnabled()
    }

    @Test
    fun theCollectionChipOverridesTheDetectedCollection() {
        seedDefaults()
        val books = collectionByName("Books")
        waitForTag("fab_capture")
        click("fab_capture")
        waitForTag("capture_input")

        type("capture_input", "Watch Dune")
        composeRule.waitForIdle()
        tag("capture_collection").assertTextContains("Movies", substring = true)

        click("capture_collection")
        waitForTag("capture_choice_${books.id}")
        click("capture_choice_${books.id}")
        composeRule.waitForIdle()
        tag("capture_collection").assertTextContains("Books", substring = true)

        click("capture_save")
        waitForText("Saved to Books")
        eventually(description = "item filed in Books") {
            db { itemRepository.getAllFiled().any { it.item.title == "Dune" && it.item.collectionId == books.id } }
        }
    }

    @Test
    fun undoRemovesTheCapturedItem() {
        seedDefaults()
        waitForTag("fab_capture")
        click("fab_capture")
        waitForTag("capture_input")
        type("capture_input", "Watch Heat")
        click("capture_save")

        waitForText("Saved to Movies")
        eventually(description = "item saved") { db { itemRepository.getAllFiled().size == 1 } }
        tapText("Undo")
        eventually(description = "item removed again") { db { itemRepository.getAllFiled().isEmpty() } }
    }

    @Test
    fun inputIsParsedLocallyForLinksAndExplicitCollections() {
        seedDefaults()
        waitForTag("fab_capture")
        click("fab_capture")
        waitForTag("capture_input")

        type("capture_input", "Hades @things https://example.com/hades")
        composeRule.waitForIdle()
        tag("capture_collection").assertTextContains("Things to Buy", substring = true)
        tag("capture_hints").assertTextContains("link attached", substring = true)
        click("capture_save")

        waitForText("Saved to Things to Buy")
        eventually(description = "item saved with link") {
            db { itemRepository.getAllFiled().any { it.item.externalUrl == "https://example.com/hades" } }
        }
    }
}

