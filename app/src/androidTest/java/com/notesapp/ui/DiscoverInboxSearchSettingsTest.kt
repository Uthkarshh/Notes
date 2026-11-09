package com.notesapp.ui

import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.notesapp.domain.model.Priority
import com.notesapp.domain.model.ThemeMode
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DiscoverScreenTest : UiTestBase() {
    private fun filters(block: () -> Unit) {
        scrollIntoView("discover_advanced"); click("discover_advanced"); waitForTag("discover_filter_sheet")
        block()
        click("discover_apply"); waitUntilGone("discover_filter_sheet")
    }
    private fun count(value: Int) = eventually { textOf("discover_match_count") == "$value eligible" }
    @Test fun matchCountFollowsTheFilters() {
        seedDefaults()
        val heat = addItem("Heat", "Movies", tags = listOf("crime")); addItem("Dune", "Books"); addItem("Seen already", "Movies", status = "watched")
        db { itemRepository.setFavorite(heat, true) }; openTab("discover"); count(2)
        filters { click("discover_status"); tapText("Any status") }; count(3)
        filters { click("discover_status"); tapText("Not started") }; count(2)
        filters { click("discover_collection"); tapText("Books") }; count(1)
        filters { click("discover_collection"); tapText("Any collection"); click("discover_favorites") }; count(1)
        filters { click("discover_favorites"); click("discover_high_priority") }; count(0)
        filters { click("discover_high_priority"); tapText("#crime") }; count(1)
    }
    @Test fun highPriorityFilterKeepsHighAndUrgentItems() {
        seedDefaults(); val urgent = addItem("Urgent one", "Things to Buy"); addItem("Low one", "Things to Buy")
        db { itemRepository.setPriority(urgent, Priority.URGENT) }; openTab("discover")
        filters { click("discover_high_priority") }; count(1)
        click("discover_draw"); waitForTag("surprise_card"); assertEquals("Urgent one", textOf("surprise_title"))
    }
    @Test fun drawShowsAnInlineCardAndAcceptingStartsTheItem() {
        seedDefaults(); val id = addItem("Arrival", "Movies"); openTab("discover"); count(1)
        click("discover_draw"); waitForTag("surprise_card"); assertEquals("Arrival", textOf("surprise_title"))
        scrollIntoView("surprise_accept"); click("surprise_accept"); waitForTag("surprise_confirmation")
        eventually { db { itemRepository.get(id)!!.status == "watching" } }
    }
    @Test fun drawingWithNothingToPickExplainsIt() {
        seedDefaults(); openTab("discover"); count(0)
        text("No eligible items. Try another preset or adjust your filters.").assertIsDisplayed()
        tag("discover_draw").assertIsNotEnabled()
    }
    @Test fun rollingAgainAndMarkingCompletedWorkInline() {
        seedDefaults(); val a = addItem("One", "Movies"); val b = addItem("Two", "Movies"); openTab("discover"); count(2)
        click("discover_draw"); waitForTag("surprise_card"); val first = textOf("surprise_title")
        scrollIntoView("surprise_roll_again"); click("surprise_roll_again")
        eventually { isShown("surprise_title") && textOf("surprise_title") != first }
        scrollIntoView("surprise_complete"); click("surprise_complete"); waitForTag("reflection_skip"); click("reflection_skip")
        waitForTag("surprise_confirmation"); val completed = if (textOf("surprise_title") == "One") a else b
        eventually { db { itemRepository.get(completed)!!.completedAt != null } }
    }
    @Test fun deletedCollectionStopsFilteringTheDraw() {
        seedDefaults(); val books = collectionByName("Books"); addItem("Arrival", "Movies"); openTab("discover")
        filters { click("discover_collection"); tapText("Books") }; count(0)
        db { collectionRepository.delete(books.id, deleteItems = true) }; count(1)
        composeRule.activityRule.scenario.recreate(); waitForTag("discover_summary"); count(1)
    }
}

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class InboxScreenTest : UiTestBase() {

    @Test
    fun anEmptyInboxCelebrates() {
        seedDefaults()
        openTab("today")
        waitForTag("action_inbox")
        click("action_inbox")
        waitForText("Inbox zero")
    }

    @Test
    fun itemsAreFiledWithOneTap() {
        seedDefaults()
        val first = addItem("Visit Kyoto", null)
        val second = addItem("Call mom", null)
        val places = collectionByName("Places")
        val books = collectionByName("Books")

        openTab("today")
        waitForTag("action_inbox")
        click("action_inbox")
        waitForTag("inbox_card_$first")
        text("Inbox (2)").assertIsDisplayed()

        click("inbox_file_${first}_${places.id}")
        waitUntilGone("inbox_card_$first")
        eventually(description = "filed in Places") {
            db { itemRepository.get(first)!!.let { it.collectionId == places.id && it.status == "want_to_visit" } }
        }
        text("Inbox (1)").assertIsDisplayed()

        click("inbox_other_$second")
        waitForTag("inbox_choice_${books.id}")
        click("inbox_choice_${books.id}")
        waitForText("Inbox zero")
    }

    @Test
    fun inboxItemsCanBeDeletedAndRestoredWithUndo() {
        seedDefaults()
        val id = addItem("Spam", null)
        openTab("today")
        waitForTag("action_inbox"); click("action_inbox"); waitForTag("inbox_card_$id")
        click("inbox_delete_$id")
        eventually(description = "hidden while pending deletion") { db { itemRepository.get(id) } == null }
        waitForText("Inbox zero")
        tapText("Restore")
        waitForTag("inbox_card_$id")
        assertTrue(db { itemRepository.get(id) } != null)
    }

    @Test
    fun withoutCollectionsTheInboxExplainsWhatToDo() {
        val id = addItem("Orphan", null)
        openTab("today")
        waitForTag("action_inbox")
        click("action_inbox")
        waitForTag("inbox_card_$id")
        waitForText("Create a collection first to file this item.")
    }
}

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SearchScreenTest : UiTestBase() {

    private fun openSearch() {
        openTab("today")
        waitForTag("action_search")
        click("action_search")
        waitForTag("search_input")
    }

    @Test
    fun searchFindsItemsNotesAndFactsAndOpensTheTappedResult() {
        seedDefaults()
        val item = addItem("Tripod review", "Movies", tags = listOf("gear"))
        val note = db { noteRepository.create("Trip plan", "Pack the camera and a tripod") }
        val fact = db { factRepository.create("Tripods have three legs", source = "Photography 101") }
        openSearch()
        waitForTag("search_hint")

        type("search_input", "tripod")
        waitForTag("search_hit_item_$item")
        waitForTag("search_hit_note_$note")
        waitForTag("search_hit_fact_$fact")

        click("search_hit_note_$note")
        waitForTag("note_title")
        pressBack()
        waitForTag("search_input")
    }

    @Test
    fun searchMatchesPrefixesTagsAndSources() {
        seedDefaults()
        val item = addItem("Arrival", "Movies", tags = listOf("scifi"))
        val fact = db { factRepository.create("Octopuses have three hearts", source = "Marine biology") }
        openSearch()

        type("search_input", "arr")
        waitForTag("search_hit_item_$item")

        replaceText("search_input", "scifi")
        waitForTag("search_hit_item_$item")

        replaceText("search_input", "marine")
        waitForTag("search_hit_fact_$fact")
        waitUntilGone("search_hit_item_$item")
    }

    @Test
    fun noResultsAndClearingTheQuery() {
        seedDefaults()
        addItem("Arrival", "Movies")
        openSearch()

        type("search_input", "zzzzzz")
        waitForTag("search_empty")
        text("Nothing matches “zzzzzz”.").assertIsDisplayed()

        click("search_clear")
        waitForTag("search_hint")
    }

    @Test
    fun searchSurvivesPunctuationAndOperators() {
        seedDefaults()
        val item = addItem("Dark Knight", "Movies")
        openSearch()

        type("search_input", "\"dark\" -knight*")
        waitForTag("search_hit_item_$item")

        // Operators are neutralised, so "OR" is just another word that must match.
        replaceText("search_input", "dark OR knight")
        waitForTag("search_empty")
    }

    @Test
    fun deletedItemsDisappearFromSearch() {
        seedDefaults()
        val id = addItem("Ephemeral", "Movies")
        db { itemRepository.delete(id) }
        openSearch()

        type("search_input", "ephemeral")
        waitForTag("search_empty")
    }
}

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest : UiTestBase() {

    private fun openSettings() {
        openTab("today")
        waitForTag("action_settings")
        click("action_settings")
        waitForTag("settings_content")
    }

    @Test
    fun themeChoiceIsSavedAndShownAsSelected() {
        openSettings()
        assertEquals(ThemeMode.SYSTEM, db { settingsRepository.settings.first().themeMode })

        click("settings_theme_dark")
        eventually(description = "dark theme saved") { db { settingsRepository.settings.first().themeMode } == ThemeMode.DARK }

        click("settings_theme_light")
        eventually(description = "light theme saved") { db { settingsRepository.settings.first().themeMode } == ThemeMode.LIGHT }
    }

    @Test
    fun amoledAndDynamicColorSwitchesArePersisted() {
        openSettings()
        assertFalse(db { settingsRepository.settings.first().amoledBlack })
        assertTrue(db { settingsRepository.settings.first().dynamicColor })

        click("settings_amoled")
        eventually(description = "amoled saved") { db { settingsRepository.settings.first().amoledBlack } }

        click("settings_dynamic")
        eventually(description = "dynamic color saved") { !db { settingsRepository.settings.first().dynamicColor } }
    }

    @Test
    fun aboutExplainsThePrivacyModel() {
        openSettings()
        text("Your data stays local unless you choose online lookup or a backup destination. No app account or telemetry.").performScrollTo().assertIsDisplayed()
        text("Version", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun backReturnsToTheScreenSettingsWasOpenedFrom() {
        openSettings()
        click("back")
        waitForTag("fab_capture")
    }
}

