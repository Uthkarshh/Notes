package com.notesapp.ui

import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MilestoneFourPolishTest : UiTestBase() {
    private var originalSize: String? = null
    private var originalWidth = 0
    private var scale = "1.0"
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText().trim() }

    @Before fun windowSetup() {
        originalSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1)
        originalWidth = composeRule.activity.resources.configuration.screenWidthDp
        scale = shell("settings get global animator_duration_scale")
    }
    @After fun restore() {
        composeRule.mainClock.autoAdvance = true
        shell(if (scale == "null") "settings delete global animator_duration_scale" else "settings put global animator_duration_scale $scale")
        shell("wm size ${originalSize ?: "reset"}")
        composeRule.waitUntil(20_000) {
            kotlin.math.abs(composeRule.activity.resources.configuration.screenWidthDp - originalWidth) <= 2 &&
                composeRule.activity.lifecycle.currentState == androidx.lifecycle.Lifecycle.State.RESUMED
        }
        composeRule.waitForIdle()
    }
    private fun width(dp: Int) {
        val density = composeRule.activity.resources.displayMetrics.density
        shell("wm size ${(dp * density).roundToInt()}x${(900 * density).roundToInt()}")
        composeRule.waitUntil(20_000) { kotlin.math.abs(composeRule.activity.resources.configuration.screenWidthDp - dp) <= 2 }
    }
    private fun movies(mode: String = "board") {
        openTab("collections")
        val id = collectionByName("Movies").id
        waitForTag("collection_card_$id"); click("collection_card_$id"); waitForTag("view_$mode"); click("view_$mode")
        waitForTag(if (mode == "board") "board" else "item_$mode")
    }
    private fun dragTo(id: Long, lane: String, cancel: Boolean = false) {
        val source = tag("board_drag_$id").fetchSemanticsNode().boundsInRoot
        val destination = tag("board_lane_$lane").fetchSemanticsNode().boundsInRoot
        val end = Offset(destination.center.x - source.left, source.center.y - source.top)
        tag("board_drag_$id").performTouchInput {
            down(center); advanceEventTime(650); moveTo(end, 300)
            if (cancel) cancel() else up()
        }
    }
    @Test fun boardDropCommitsOneReversibleStatusChange() {
        seedDefaults(); val id = addItem("Drag Arrival", "Movies"); width(600); movies()
        waitForTag("board_drag_$id"); dragTo(id, "watching")
        eventually { db { itemRepository.get(id)!!.status == "watching" } }
        composeRule.waitUntil(8000) {
            composeRule.onAllNodes(hasTestTag("kanban_card_$id") and hasAnyAncestor(hasTestTag("board_column_watching"))).fetchSemanticsNodes().size == 1
        }
        tag("board_drag_preview").assertDoesNotExist()
        composeRule.waitForIdle()
        snapshotMilestoneFour("06_board_move")
        waitForText("Undo"); tapText("Undo")
        eventually { db { itemRepository.get(id)!!.status == "want_to_watch" } }
        tag("board_drag_preview").assertDoesNotExist()
    }
    @Test fun boardCancelledPickupDoesNotChangeStatus() {
        seedDefaults(); val id = addItem("Keep Arrival", "Movies"); width(600); movies()
        waitForTag("board_drag_$id"); dragTo(id, "watching", cancel = true)
        composeRule.waitForIdle()
        assertEquals("want_to_watch", db { itemRepository.get(id)!!.status })
        tag("board_drag_preview").assertDoesNotExist()
        text("Status updated").assertDoesNotExist()
    }
    @Test fun boardOutsideDropDoesNotChangeStatusAndMenuStillWorks() {
        seedDefaults(); val id = addItem("Menu Arrival", "Movies"); width(600); movies()
        waitForTag("board_drag_$id")
        tag("board_drag_$id").performTouchInput { down(center); advanceEventTime(650); moveTo(Offset(center.x, -500f), 300); up() }
        assertEquals("want_to_watch", db { itemRepository.get(id)!!.status })
        click("status_menu_$id"); waitForTag("status_to_${id}_watching"); click("status_to_${id}_watching")
        eventually { db { itemRepository.get(id)!!.status == "watching" } }
    }
    @Test fun reducedMotionPersistsAndCaptureSavesWithoutAnimation() {
        click("action_settings"); waitForTag("settings_reduce_motion"); scrollIntoView("settings_reduce_motion"); click("settings_reduce_motion")
        eventually { db { settingsRepository.settings.first().reduceMotion } }
        composeRule.activityRule.scenario.recreate()
        waitForTag("settings_reduce_motion"); scrollIntoView("settings_reduce_motion"); tag("settings_reduce_motion").assertIsOn()
        click("back"); waitForTag("fab_capture"); click("fab_capture"); waitForTag("capture_input")
        type("capture_input", "Immediate capture"); click("capture_save"); waitUntilGone("capture_input")
        eventually { db { itemRepository.observeInbox().first().any { it.item.title == "Immediate capture" } } }
    }
    @Test fun liveSystemAnimatorZeroKeepsCaptureAndNavigationUsable() {
        shell("settings put global animator_duration_scale 0")
        click("fab_capture"); waitForTag("capture_input"); type("capture_input", "System motion off")
        click("capture_save"); waitUntilGone("capture_input")
        click("action_settings"); waitForTag("settings_content"); click("back"); waitForTag("nav_today")
        assertFalse(db { settingsRepository.settings.first().reduceMotion })
        assertTrue(db { itemRepository.observeInbox().first().any { it.item.title == "System motion off" } })
    }
    @Test fun coverNavigationSurvivesMissingImagesAndRemovedSource() {
        seedDefaults(); val id = addItem("Cover fallback", "Movies")
        db { itemRepository.setImage(id, "file:///missing-cover.png") }
        movies("grid"); waitForTag("item_card_$id"); click("item_card_$id"); waitForTag("item_detail")
        tag("item_cover_$id").assertIsDisplayed()
        replaceText("item_title", "Cover edited"); hideKeyboard()
        db { itemRepository.setArchived(id, true) }
        click("back"); waitForTag("view_grid"); tag("view_grid").assertIsSelected(); tag("item_card_$id").assertDoesNotExist()
        assertEquals("Cover edited", db { itemRepository.get(id)!!.title })
    }
    @Test fun cancelledPredictiveBackKeepsDraftAndCompletedBackSavesIt() {
        seedDefaults(); val id = addItem("Gesture draft", "Movies"); movies("list")
        waitForTag("item_row_$id"); click("item_row_$id"); waitForTag("item_detail")
        replaceText("item_title", "Gesture draft edited"); hideKeyboard()
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.dispatchOnBackStarted(androidx.activity.BackEventCompat(0f, 400f, 0f, androidx.activity.BackEventCompat.EDGE_LEFT))
            composeRule.activity.onBackPressedDispatcher.dispatchOnBackProgressed(androidx.activity.BackEventCompat(100f, 400f, .5f, androidx.activity.BackEventCompat.EDGE_LEFT))
            composeRule.activity.onBackPressedDispatcher.dispatchOnBackCancelled()
        }
        tag("item_title").assertTextContains("Gesture draft edited")
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.dispatchOnBackStarted(androidx.activity.BackEventCompat(0f, 400f, 0f, androidx.activity.BackEventCompat.EDGE_LEFT))
            composeRule.activity.onBackPressedDispatcher.dispatchOnBackProgressed(androidx.activity.BackEventCompat(200f, 400f, 1f, androidx.activity.BackEventCompat.EDGE_LEFT))
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        waitForTag("item_list")
        assertEquals("Gesture draft edited", db { itemRepository.get(id)!!.title })
    }
    @Test fun heldPickupScrollsAcrossColumnsAndCancellationKeepsStatus() {
        seedDefaults(); val id = addItem("Scroll across board", "Movies"); width(600); movies()
        waitForTag("board_drag_$id")
        val board = tag("board").fetchSemanticsNode().boundsInRoot
        val handle = tag("board_drag_$id").fetchSemanticsNode().boundsInRoot
        composeRule.mainClock.autoAdvance = false
        try {
            tag("board").performTouchInput {
                down(handle.center - board.topLeft); advanceEventTime(650)
                moveTo(Offset(board.width - 4f, handle.center.y - board.top), 300)
            }
            composeRule.mainClock.advanceTimeBy(1500)
            tag("board_column_watched").assertIsDisplayed()
        } finally {
            tag("board").performTouchInput { cancel() }
            composeRule.mainClock.autoAdvance = true
        }
        composeRule.waitForIdle()
        assertEquals("want_to_watch", db { itemRepository.get(id)!!.status })
    }
    @Test fun heldPickupScrollsInsideTargetColumn() {
        seedDefaults(); val waiting = (0..19).map { addItem("Watching $it", "Movies", status = "watching") }
        val id = addItem("Scroll inside column", "Movies"); width(600); movies(); waitForTag("board_drag_$id")
        val board = tag("board").fetchSemanticsNode().boundsInRoot
        val handle = tag("board_drag_$id").fetchSemanticsNode().boundsInRoot
        val lane = tag("board_lane_watching").fetchSemanticsNode().boundsInRoot
        tag("kanban_card_${waiting.last()}").assertIsDisplayed()
        composeRule.mainClock.autoAdvance = false
        try {
            tag("board").performTouchInput {
                down(handle.center - board.topLeft); advanceEventTime(650)
                moveTo(Offset(lane.center.x - board.left, board.height - 4f), 300)
            }
            composeRule.mainClock.advanceTimeBy(1500)
            tag("kanban_card_${waiting.last()}").assertIsNotDisplayed()
        } finally {
            tag("board").performTouchInput { cancel() }
            composeRule.mainClock.autoAdvance = true
        }
        composeRule.waitForIdle()
        assertEquals("want_to_watch", db { itemRepository.get(id)!!.status })
    }
}
