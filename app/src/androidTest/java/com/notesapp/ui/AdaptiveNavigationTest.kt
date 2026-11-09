package com.notesapp.ui

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.roundToInt

/** Real activity/window resize exercises navigation owners, saved state and IME insets together. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AdaptiveNavigationTest : UiTestBase() {
    private var originalOverride: String? = null
    private var originalWidth = 0
    private var density = 1f

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }

    @Before fun rememberWindow() {
        originalOverride = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1)
        originalWidth = composeRule.activity.resources.configuration.screenWidthDp
        density = composeRule.activity.resources.displayMetrics.density
    }
    @After fun restoreWindow() {
        shell("wm size ${originalOverride ?: "reset"}")
        // Let the real configuration change finish before ActivityScenario closes its activity.
        composeRule.waitUntil(20_000) {
            kotlin.math.abs(composeRule.activity.resources.configuration.screenWidthDp - originalWidth) <= 2 &&
                composeRule.activity.lifecycle.currentState == androidx.lifecycle.Lifecycle.State.RESUMED
        }
        composeRule.waitForIdle()
    }

    private fun window(width: Int, height: Int = 900) {
        hideKeyboard()
        shell("wm size ${(width * density).roundToInt()}x${(height * density).roundToInt()}")
        composeRule.waitUntil(20_000) { kotlin.math.abs(composeRule.activity.resources.configuration.screenWidthDp - width) <= 2 }
        composeRule.waitForIdle()
    }
    private fun movies() {
        openTab("collections")
        val id = collectionByName("Movies").id
        waitForTag("collection_card_$id"); click("collection_card_$id"); waitForTag("item_list")
    }
    private fun editorText(field: String, value: String) {
        try {
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasTestTag(field) and hasText(value)).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (error: Throwable) {
            snapshot("failure_$field")
            throw error
        }
    }
    private fun snapshot(name: String) {
        if (InstrumentationRegistry.getArguments().getString("paneTour") != "true") return
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.waitForIdle(250, 3000)
        val bitmap = checkNotNull(automation.takeScreenshot())
        val directory = File(composeRule.activity.getExternalFilesDir(null), "redesign-m4").apply { check(isDirectory || mkdirs()) }
        File(directory, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }

    @Test fun collectionSelectionSavesPreviousItemAndBackReturnsOnce() {
        seedDefaults(); val a = addItem("Arrival", "Movies"); val b = addItem("Moonlight", "Movies")
        window(1200); movies(); waitForTag("pane_placeholder")
        // Exercise actual pointer navigation over the title, distinct from the nested status chip.
        tag("item_row_$a").performTouchInput { click(androidx.compose.ui.geometry.Offset(center.x, 12f * density)) }
        waitForTag("item_detail")
        replaceText("item_title", "Arrival, reviewed"); hideKeyboard()
        tag("item_row_$b").performTouchInput { click(androidx.compose.ui.geometry.Offset(center.x, 12f * density)) }
        eventually { db { itemRepository.get(a)!!.title == "Arrival, reviewed" } }
        editorText("item_title", "Moonlight"); tag("item_row_$b").assertIsSelected()
        assertEquals("Moonlight", db { itemRepository.get(b)!!.title })
        snapshot("01_collection_panes")
        click("fab_add_item"); waitForTag("capture_input"); tag("capture_collection").assertTextContains("Movies", substring = true)
        type("capture_input", "Pane capture"); click("capture_save"); waitUntilGone("capture_input")
        assertEquals(collectionByName("Movies").id, db { itemRepository.getAllFiled().single { it.item.title == "Pane capture" }.item.collectionId })
        tag("item_title").assertTextContains("Moonlight")
        click("back"); waitForTag("pane_placeholder"); tag("item_detail").assertDoesNotExist()
        click("pane_list_back"); waitForTag("collections_grid")
    }

    @Test fun selectedEditorAndInvalidDraftSurviveAllWidthsAndRecreation() {
        seedDefaults(); val id = addItem("A window-independent draft", "Movies")
        window(360); movies(); click("item_row_$id"); waitForTag("item_detail")
        replaceText("item_title", "Retained title"); scrollIntoView("metadata_runtimeMinutes")
        replaceText("metadata_runtimeMinutes", "unfinished"); hideKeyboard()
        for (width in listOf(600, 840, 1200, 360)) {
            window(width); waitForTag("item_detail"); scrollIntoView("metadata_runtimeMinutes")
            tag("metadata_runtimeMinutes").assertTextContains("unfinished")
            if (width >= 840) waitForTag("list_pane") else tag("list_pane").assertDoesNotExist()
        }
        composeRule.activityRule.scenario.recreate(); waitForTag("metadata_runtimeMinutes")
        tag("metadata_runtimeMinutes").assertTextContains("unfinished")
        click("back"); waitForTag("item_row_$id")
        assertEquals("Retained title", db { itemRepository.get(id)!!.title })
        assertEquals("unfinished", db { itemRepository.editorDraft(id) }["runtimeMinutes"])
    }

    @Test fun vaultSelectionSavesNoteBeforeChangingRowsOrTabs() {
        seedDefaults()
        val a = db { noteRepository.create("First note", "Original text") }
        val b = db { noteRepository.create("Second note", "Other text") }
        val fact = db { factRepository.create("A fact to revise") }
        window(1200); openTab("vault"); waitForTag("note_card_$a"); click("note_card_$a"); waitForTag("note_content")
        replaceText("note_content", "Saved before switching"); hideKeyboard(); click("note_card_$b")
        editorText("note_title", "Second note")
        assertEquals("Saved before switching", db { noteRepository.get(a)!!.content })
        replaceText("note_title", "Second note revised"); hideKeyboard(); snapshot("02_vault_panes")
        click("vault_segment_facts"); waitForTag("fact_card_$fact"); click("fact_card_$fact"); waitForTag("fact_text_field")
        replaceText("fact_text_field", "A revised fact"); hideKeyboard()
        openTab("today"); waitForTag("today_list")
        assertEquals("Second note revised", db { noteRepository.get(b)!!.title })
        assertEquals("A revised fact", db { factRepository.get(fact)!!.text })
    }

    @Test fun searchKeepsQueryAndCanSwitchBetweenEntityTypes() {
        seedDefaults(); val item = addItem("Shared query movie", "Movies")
        val note = db { noteRepository.create("Shared query note", "Some thoughts") }
        window(1200); waitForTag("action_search"); click("action_search"); waitForTag("search_input")
        type("search_input", "Shared query"); hideKeyboard(); waitForTag("search_hit_item_$item"); click("search_hit_item_$item")
        waitForTag("item_detail"); tag("search_input").assertTextContains("Shared query")
        click("search_hit_note_$note"); waitForTag("note_title"); tag("note_title").assertTextContains("Shared query note")
        snapshot("03_search_panes")
        click("back"); waitForTag("pane_placeholder"); tag("search_input").assertTextContains("Shared query")
    }

    @Test fun settingsSectionSelectionDoesNotAccumulateBackHistory() {
        seedDefaults(); window(1200); click("action_settings"); waitForTag("settings_content")
        scrollIntoView("settings_notifications"); click("settings_notifications"); waitForTag("notifications_enabled")
        scrollIntoView("settings_advanced"); click("settings_advanced"); waitForTag("lookup_enabled")
        snapshot("04_settings_panes")
        click("back"); waitForTag("pane_placeholder"); tag("lookup_enabled").assertDoesNotExist()
        tag("notifications_enabled").assertDoesNotExist()
    }

    @Test fun shortLandscapeKeepsOneEditorAndReturnsToPanesWhenTaller() {
        seedDefaults(); val id = addItem("Landscape draft", "Movies")
        window(1200); movies(); click("item_row_$id"); waitForTag("item_title")
        replaceText("item_title", "Still the same editor"); window(1200, 400)
        waitForTag("single_pane"); tag("item_title").assertTextContains("Still the same editor"); tag("list_pane").assertDoesNotExist()
        window(1200); waitForTag("list_pane"); tag("item_title").assertTextContains("Still the same editor")
    }

    @Test fun compactBackRestoresTheListPositionFromTheWidePane() {
        seedDefaults()
        val ids = (1..30).map { addItem("Film ${it.toString().padStart(2, '0')}", "Movies") }
        val selected = ids[24]
        window(1200); movies(); scrollTo("item_list", "item_row_$selected"); click("item_row_$selected")
        waitForTag("item_detail"); window(360); click("back"); waitForTag("item_list")
        tag("item_row_$selected").assertIsDisplayed()
        tag("item_row_${ids.first()}").assertDoesNotExist()
    }

    @Test fun notificationEntryHasNoInventedListOrExtraBackStep() {
        seedDefaults(); val id = addItem("Opened directly", "Movies")
        window(360)
        val context = composeRule.activity
        val originalIntent = android.content.Intent(context.intent)
        try {
            context.startActivity(android.content.Intent(context, com.notesapp.MainActivity::class.java)
                .putExtra(com.notesapp.automation.NotificationHelper.EXTRA_TYPE, "item")
                .putExtra(com.notesapp.automation.NotificationHelper.EXTRA_ID, id)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP))
            waitForTag("item_detail"); tag("item_title").assertTextContains("Opened directly")
            // ActivityScenario identifies recreated activities by their original launch intent.
            composeRule.runOnUiThread { composeRule.activity.intent = originalIntent }
            window(1200); tag("list_pane").assertDoesNotExist(); tag("nav_today").assertExists()
            click("back"); waitForTag("today_list")
        } finally { composeRule.runOnUiThread { composeRule.activity.intent = originalIntent } }
    }
}
