package com.notesapp.ui

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.notesapp.data.db.AppDatabase
import com.notesapp.data.prefs.FeaturePreferences
import com.notesapp.domain.metadata.ItemMetadata
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RedesignMilestoneOneTest : UiTestBase() {
    @Inject lateinit var database: AppDatabase
    @Inject lateinit var features: FeaturePreferences

    private fun openMovie(id: Long) {
        openTab("collections")
        val movies = collectionByName("Movies")
        waitForTag("collection_card_${movies.id}"); click("collection_card_${movies.id}")
        waitForTag("item_row_$id"); click("item_row_$id"); waitForTag("item_detail")
    }

    @Test fun richDetailsAutosaveWhileOpenAndIncompleteNumbersRecoverAfterLeaving() {
        seedDefaults()
        val id = addItem("Arrival", "Movies")
        db { itemRepository.saveMetadata(id, ItemMetadata(runtimeMinutes = 90)) }
        openMovie(id)
        scrollIntoView("metadata_runtimeMinutes"); replaceText("metadata_runtimeMinutes", "116")
        eventually { ItemMetadata.decode(db { itemRepository.get(id)!!.metadataJson }).runtimeMinutes == 116 }
        replaceText("metadata_runtimeMinutes", "12x")
        scrollIntoView("metadata_genre"); type("metadata_genre", "Science fiction")
        click("back"); waitForTag("item_row_$id")
        assertEquals(116, ItemMetadata.decode(db { itemRepository.get(id)!!.metadataJson }).runtimeMinutes)
        assertEquals("Science fiction", ItemMetadata.decode(db { itemRepository.get(id)!!.metadataJson }).genre)
        assertEquals("12x", db { itemRepository.editorDraft(id)["runtimeMinutes"] })
        click("item_row_$id"); waitForTag("item_detail")
        scrollIntoView("metadata_runtimeMinutes"); tag("metadata_runtimeMinutes").assertTextContains("12x")
        text("Enter a whole number.").assertIsDisplayed()
    }

    @Test fun lookupCanBeDeclinedInPlaceWithoutEnablingIt() {
        seedDefaults(); val id = addItem("Arrival", "Movies"); openMovie(id)
        scrollIntoView("metadata_lookup"); click("metadata_lookup")
        waitForTag("metadata_consent"); tag("context_tmdb_token").assertExists()
        tag("metadata_decline").assertIsDisplayed()
        click("metadata_decline"); waitUntilGone("metadata_consent")
        assertFalse(db { features.settings.first().lookupEnabled })
        tag("item_detail").assertExists()
        assertEquals("Arrival", db { itemRepository.get(id)!!.title })
    }

    @Test fun movieLookupRequiresCredentialsBeforeEnablingOrSearching() {
        seedDefaults(); val id = addItem("Arrival", "Movies"); openMovie(id)
        scrollIntoView("metadata_lookup"); click("metadata_lookup"); waitForTag("metadata_enable_search")
        click("metadata_enable_search")
        waitForText("Enter your TMDB API Read Access Token to search movies.")
        assertFalse(db { features.settings.first().lookupEnabled })
        click("metadata_decline")
    }

    @Test fun dueReminderCanBeSnoozedCompletedAndUndoneFromToday() {
        seedDefaults(); val id = addItem("Arrival", "Movies")
        val original = System.currentTimeMillis() - 60_000
        db { itemRepository.setReminder(id, original) }
        waitForTag("due_reminder_$id"); tag("due_reminder_$id").assertIsDisplayed()
        click("due_snooze_$id"); tapText("In 1 hour")
        eventually { db { itemRepository.get(id)!!.reminderAt!! > System.currentTimeMillis() } }
        waitForText("Undo"); tapText("Undo")
        eventually { db { itemRepository.get(id)!!.reminderAt == original } }
        click("due_complete_$id")
        eventually { db { itemRepository.get(id)!!.status == "watched" } }
        waitForTag("reflection_skip"); click("reflection_skip"); waitUntilGone("reflection_sheet")
        waitForText("Undo"); tapText("Undo")
        eventually { db { itemRepository.get(id)!!.let { it.status == "want_to_watch" && it.reminderAt == original && it.completedAt == null } } }
    }

    @Test fun dismissingTheInboxPromptKeepsTheGlobalInboxEntry() {
        seedDefaults(); addItem("Loose thought", null)
        waitForTag("dismiss_inbox_banner"); click("dismiss_inbox_banner")
        waitUntilGone("inbox_banner")
        tag("action_inbox").assertExists()
        click("action_inbox"); waitForTag("inbox_list")
    }
}
