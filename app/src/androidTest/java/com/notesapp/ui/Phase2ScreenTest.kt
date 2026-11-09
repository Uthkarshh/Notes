package com.notesapp.ui

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.notesapp.ShareActivity
import com.notesapp.MainActivity
import com.notesapp.automation.NotificationHelper
import com.notesapp.data.db.entity.SelectionAction
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class Phase2ScreenTest : UiTestBase() {
    private fun openItem(id: Long) {
        val collection = collectionByName("Movies")
        openTab("collections"); waitForTag("collection_card_${collection.id}"); click("collection_card_${collection.id}")
        waitForTag("item_row_$id"); click("item_row_$id"); waitForTag("item_detail")
    }

    @Test fun completionReflectionCreatesALinkedNote() {
        seedDefaults()
        val item = addItem("Arrival", "Movies", tags = listOf("scifi"))
        openItem(item)
        click("item_status_watched")
        waitForTag("reflection_input")
        type("reflection_input", "The language and time story stayed with me.")
        click("reflection_save")
        waitUntilGone("reflection_sheet")
        val note = db { noteRepository.observeAll().first().single() }
        assertEquals("Reflection: Arrival", note.note.title)
        assertEquals(listOf("scifi"), note.tags)
        waitForTag("linked_note_${note.note.id}")
        scrollIntoView("linked_note_${note.note.id}")
        click("linked_note_${note.note.id}")
        waitForTag("note_content")
        tag("note_content").assertTextContains("The language and time story stayed with me.")
    }

    @Test fun decliningAReflectionKeepsTheCompletedStatus() {
        seedDefaults()
        val item = addItem("Arrival", "Movies")
        openItem(item)
        click("item_status_watched"); waitForTag("reflection_skip"); click("reflection_skip"); waitUntilGone("reflection_sheet")
        assertEquals("watched", db { itemRepository.get(item)!!.status })
        assertTrue(db { noteRepository.observeAll().first().isEmpty() })
    }

    @Test fun cooldownAndHistoryUsePersistentDraws() {
        seedDefaults()
        val recent = addItem("Recent", "Movies")
        val fresh = addItem("Fresh", "Movies")
        db { itemRepository.recordDraw(recent) }
        openTab("discover")
        click("discover_advanced"); waitForTag("discover_filter_sheet"); scrollIntoView("discover_cooldown"); click("discover_cooldown"); tapText("Last 7 days"); click("discover_apply"); waitUntilGone("discover_filter_sheet")
        eventually { textOf("discover_match_count") == "1 eligible" }
        scrollIntoView("discover_draw"); click("discover_draw"); waitForTag("surprise_title")
        assertEquals("Fresh", textOf("surprise_title"))
        click("surprise_accept"); waitForTag("surprise_confirmation")
        scrollIntoView("discover_history"); click("discover_history"); waitForTag("history_list")
        text("Fresh").assertIsDisplayed()
        assertEquals(1, db { itemRepository.get(fresh)!!.selectedCount })
        assertEquals(SelectionAction.ACCEPTED, db { itemRepository.observeHistory().first().first().selection.actionTaken })
    }

    @Test fun settingsExposeDataTransferAndNotificationControls() {
        click("action_settings"); waitForTag("settings_content")
        scrollIntoView("settings_data_transfer"); click("settings_data_transfer"); waitForTag("data_transfer")
        tag("export_json").assertIsEnabled(); tag("import_json").assertIsEnabled()
        click("back"); waitForTag("settings_notifications"); scrollIntoView("settings_notifications"); click("settings_notifications")
        waitForTag("notification_settings"); tag("notification_time").assertIsEnabled()
        click("notification_day_1")
        eventually { db { settingsRepository.notifications.first().days }.contains(1).not() }
    }

    @Test fun sharingALinkPreservesItsTitleAndSavesToInbox() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.startActivity(Intent(context, ShareActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "An article to read").putExtra(Intent.EXTRA_TEXT, "https://example.com/article").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitForTag("share_text")
        tag("share_text").assertTextContains("https://example.com/article", substring = true)
        click("share_save"); waitUntilGone("share_capture")
        val saved = db { itemRepository.observeInbox().first().single() }.item
        assertEquals("An article to read", saved.title)
        assertEquals("https://example.com/article", saved.externalUrl)
    }

    @Test fun selectedTextCanBeSavedAsAFact() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.startActivity(Intent(context, ShareActivity::class.java).setAction(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
            .putExtra(Intent.EXTRA_PROCESS_TEXT, "Octopuses have three hearts").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitForTag("share_kind"); click("share_kind"); tapText("Fact"); click("share_save"); waitUntilGone("share_capture")
        assertEquals("Octopuses have three hearts", db { factRepository.observeAll().first().single().fact.text })
    }

    @Test fun dueRemindersAppearOnTodayAndCanBeClearedFromTheItem() {
        seedDefaults()
        val id = addItem("Remember this", "Movies")
        db { itemRepository.setReminder(id, System.currentTimeMillis() + 3_600_000) }
        waitForTag("due_reminder_$id")
        click("due_reminder_$id"); waitForTag("item_detail")
        scrollIntoView("item_clear_reminder"); click("item_clear_reminder")
        eventually { db { itemRepository.get(id)!!.reminderAt } == null }
        click("back"); waitUntilGone("due_reminder_$id")
    }

    @Test fun notificationIntentsOpenTheirItemAndFactWhileTheAppIsRunning() {
        seedDefaults()
        val item = addItem("Notification target", "Movies")
        val fact = db { factRepository.create("A fact opened from a notification") }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val originalIntent = Intent(composeRule.activity.intent)
        fun open(type: String, id: Long) {
            context.startActivity(Intent(context, MainActivity::class.java)
                .putExtra(NotificationHelper.EXTRA_TYPE, type).putExtra(NotificationHelper.EXTRA_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        try {
            open("item", item); waitForTag("item_detail")
            tag("item_title").assertTextContains("Notification target")
            open("fact", fact); waitForTag("fact_text_field")
            tag("fact_text_field").assertTextContains("A fact opened from a notification")
        } finally {
            // ActivityScenario uses the launch intent to finish its task after the test.
            composeRule.runOnUiThread { composeRule.activity.intent = originalIntent }
        }
    }
}
