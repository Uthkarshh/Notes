package com.notesapp.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performImeAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.notesapp.domain.model.MasteryState
import com.notesapp.domain.model.Priority
import com.notesapp.domain.model.ThemeMode
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Walks every screen with realistic data. It always runs as an end-to-end smoke test; when started with
 * `-e tour true` it also writes PNGs to Gradle's additional test output (or `.../files/tour` with adb).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ScreenTour : UiTestBase() {

    private val saveShots = InstrumentationRegistry.getArguments().getString("tour") == "true"

    private fun snap(name: String) {
        composeRule.waitForIdle()
        Thread.sleep(600)
        if (!saveShots) return
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?: checkNotNull(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null))
        val dir = File(output, "tour")
        check(dir.isDirectory || dir.mkdirs()) { "Cannot create screenshot directory: $dir" }
        FileOutputStream(File(dir, "$name.png")).use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Cannot save screenshot: $name" }
        }
    }

    private fun seed() {
        seedDefaults()
        addCollection("games")

        val arrival = addItem("Arrival", "Movies", tags = listOf("scifi", "drama"), status = "watched")
        val matrix = addItem("The Matrix", "Movies", tags = listOf("scifi", "classic"), status = "watching")
        addItem("Amélie", "Movies", tags = listOf("romance"))
        val heat = addItem("Heat", "Movies", tags = listOf("crime"))
        val parasite = addItem("Parasite", "Movies", tags = listOf("weekend"))
        val dune = addItem("Dune", "Books", tags = listOf("scifi"), status = "reading")
        addItem("The Pragmatic Programmer", "Books", tags = listOf("tech"))
        val hail = addItem("Project Hail Mary", "Books", tags = listOf("scifi"), status = "finished")
        val kyoto = addItem("Kyoto", "Places", status = "planned")
        addItem("Lisbon", "Places", tags = listOf("weekend"))
        val headphones = addItem("Noise-cancelling headphones", "Things to Buy", tags = listOf("tech"))
        addItem("Standing desk", "Things to Buy", status = "to_buy")
        addItem("Hades", "Games", tags = listOf("indie"))
        addItem("Call the dentist", null)
        addItem("Look into sourdough starter", null, tags = listOf("baking"))

        db {
            itemRepository.setRating(arrival, 4.5f)
            itemRepository.setFavorite(arrival, true)
            itemRepository.setRating(matrix, 5f)
            itemRepository.setPriority(heat, Priority.HIGH)
            itemRepository.setPriority(parasite, Priority.MEDIUM)
            itemRepository.setRating(hail, 5f)
            itemRepository.setFavorite(dune, true)
            itemRepository.setPriority(headphones, Priority.URGENT)
            itemRepository.setRating(kyoto, 4f)
            itemRepository.saveDetails(
                id = arrival,
                title = "Arrival",
                description = "A linguist is recruited to communicate with alien visitors.",
                externalUrl = "https://www.imdb.com/title/tt2543164/",
                tags = listOf("scifi", "drama"),
            )

            val octopus = factRepository.create(
                "Octopuses have three hearts and blue blood.", "Marine biology", "Animals", "Two pump blood to the gills.", listOf("trivia"),
            )
            factRepository.create("Honey never spoils; edible honey was found in ancient Egyptian tombs.", "Smithsonian", "Food", tags = listOf("trivia"))
            val quote = factRepository.create("The only way to do great work is to love what you do.", "Steve Jobs", "Quotes")
            factRepository.setFavorite(quote, true)
            factRepository.setMastery(octopus, MasteryState.REVIEWING)
            factRepository.create("A group of flamingos is called a flamboyance.", "Wikipedia", "Animals")

            noteRepository.create("Trip to Kyoto", "Visit Fushimi Inari at sunrise.\nBook a tea ceremony.\nPack the tripod.", listOf("travel"))
            val ideas = noteRepository.create("App ideas", "A reading log that nudges me weekly.\nA shared watchlist.", listOf("ideas"))
            noteRepository.setPinned(ideas, true)
        }
    }

    @Test
    fun walkThroughEveryScreen() {
        seed()

        // Today
        waitForTag("up_next_row")
        snap("01_today")

        scrollTo("today_list", "surprise_any")
        click("surprise_any")
        waitForTag("surprise_card")
        snap("02_surprise_sheet")
        click("surprise_roll_again")
        composeRule.waitForIdle()
        pressBack()
        waitUntilGone("surprise_card")
        tag("today_list").performScrollToIndex(0)

        click("fab_capture")
        waitForTag("capture_input")
        type("capture_input", "Watch Parasite 2 #weekend !high")
        snap("03_quick_capture")
        tapText("Cancel")
        waitUntilGone("capture_input")

        // Collections
        openTab("collections")
        waitForTag("collections_grid")
        snap("04_collections")

        val movies = collectionByName("Movies")
        click("collection_card_${movies.id}")
        waitForTag("item_list")
        snap("05_movies_list")
        click("view_grid")
        waitForTag("item_grid")
        snap("06_movies_grid")
        click("view_board")
        waitForTag("board")
        snap("07_movies_board")
        click("view_list")
        waitForTag("item_list")

        val arrival = db { itemRepository.getAllFiled().first { it.item.title == "Arrival" }.item.id }
        click("item_row_$arrival")
        waitForTag("item_detail")
        snap("08_item_detail")
        click("back")
        waitForTag("item_list")

        click("collection_menu")
        click("menu_edit_collection")
        waitForTag("collection_editor")
        snap("09_collection_editor")
        click("editor_close")
        waitForTag("item_list")
        click("back")
        waitForTag("collections_grid")

        // Vault
        openTab("vault")
        waitForTag("notes_list")
        snap("10_vault_notes")
        click("vault_segment_facts")
        waitForTag("facts_list")
        snap("11_vault_facts")
        click("vault_random_fact")
        waitForTag("random_fact")
        snap("12_random_fact")
        click("random_fact_close")
        waitUntilGone("random_fact")

        // Discover
        openTab("discover")
        waitForTag("discover_draw")
        snap("13_discover")
        scrollIntoView("discover_draw")
        click("discover_draw")
        waitForTag("surprise_card")
        tag("surprise_accept").assertIsDisplayed()
        snap("14_discover_result")

        // Inbox, search, settings
        openTab("today")
        waitForTag("inbox_banner")
        click("action_inbox")
        waitForTag("inbox_list")
        snap("15_inbox")
        click("back")

        waitForTag("action_search")
        click("action_search")
        waitForTag("search_input")
        type("search_input", "tri")
        waitForText("Trip to Kyoto")
        tag("search_input").performImeAction()
        snap("16_search")
        click("back")

        waitForTag("action_settings")
        click("action_settings")
        waitForTag("settings_content")
        snap("17_settings")
        db { settingsRepository.setThemeMode(ThemeMode.DARK) }
        composeRule.waitForIdle()
        Thread.sleep(800)
        snap("18_settings_dark")
        click("back")

        waitForTag("fab_capture")
        snap("19_today_dark")
        openTab("collections")
        waitForTag("collections_grid")
        click("collection_card_${movies.id}")
        waitForTag("item_list")
        click("view_board")
        waitForTag("board")
        snap("20_movies_board_dark")

        text("Movies").assertIsDisplayed()
    }
}

