package com.notesapp.ui

import android.Manifest
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutManager
import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.notesapp.ShareActivity
import com.notesapp.automation.NotificationHelper
import com.notesapp.data.db.AppDatabase
import com.notesapp.data.prefs.FeaturePreferences
import com.notesapp.domain.metadata.ItemMetadata
import com.notesapp.launcher.LauncherActions
import com.notesapp.security.LockSession
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@SdkSuppress(minSdkVersion = 33)
@RunWith(AndroidJUnit4::class)
class Phase34ScreenTest : UiTestBase() {
    @get:Rule(order = 2) val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    @Inject lateinit var database: AppDatabase
    @Inject lateinit var features: FeaturePreferences
    @Inject lateinit var session: LockSession
    @Inject lateinit var notifications: NotificationHelper
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After fun cleanup() { context.getSystemService(NotificationManager::class.java).cancelAll(); db { features.update { it.copy(lockEnabled = false) } }; session.unlock() }
    private fun openMovie(id: Long) {
        openTab("collections"); val collection = collectionByName("Movies")
        waitForTag("collection_card_${collection.id}"); click("collection_card_${collection.id}"); waitForTag("item_row_$id"); click("item_row_$id"); waitForTag("item_detail")
    }

    @Test fun movieRuntimeAndYearPersistThroughTheEditor() {
        seedDefaults(); val id = addItem("Arrival", "Movies"); openMovie(id)
        scrollIntoView("metadata_runtimeMinutes"); type("metadata_runtimeMinutes", "116")
        scrollIntoView("metadata_releaseYear"); type("metadata_releaseYear", "2016"); pressBack()
        eventually { ItemMetadata.decode(db { itemRepository.get(id)!!.metadataJson }).runtimeMinutes == 116 }
        click("back"); waitForTag("item_row_$id"); click("item_row_$id"); waitForTag("item_detail"); scrollIntoView("metadata_runtimeMinutes")
        tag("metadata_runtimeMinutes").assertTextContains("116")
        tag("metadata_releaseYear").assertTextContains("2016")
    }
    @Test fun invalidMetadataShowsAnErrorAndDoesNotWrite() {
        seedDefaults(); val id = addItem("Arrival", "Movies"); openMovie(id)
        scrollIntoView("metadata_runtimeMinutes"); type("metadata_runtimeMinutes", "nonsense"); pressBack()
        waitForTag("metadata_message")
        text("Enter a whole number.").assertIsDisplayed()
        assertNull(db { itemRepository.get(id)!!.metadataJson })
    }
    @Test fun discoverRuntimeFilterChangesBothTheCountAndTheDraw() {
        seedDefaults(); val short = addItem("A short movie", "Movies"); val long = addItem("A long movie", "Movies"); addItem("Unknown runtime", "Movies")
        db { itemRepository.saveMetadata(short, ItemMetadata(runtimeMinutes = 90)); itemRepository.saveMetadata(long, ItemMetadata(runtimeMinutes = 140)) }
        openTab("discover"); click("discover_advanced"); waitForTag("discover_filter_sheet"); scrollIntoView("discover_runtime"); click("discover_runtime"); tapText("Up to 90 minutes"); click("discover_apply"); waitUntilGone("discover_filter_sheet")
        eventually { textOf("discover_match_count") == "1 eligible" }
        scrollIntoView("discover_draw"); click("discover_draw"); waitForTag("surprise_title")
        assertEquals("A short movie", textOf("surprise_title"))
    }
    @Test fun encryptedExportRequiresMatchingLongPassphrasesBeforeOpeningThePicker() {
        click("action_settings"); waitForTag("settings_backups"); scrollIntoView("settings_backups"); click("settings_backups"); waitForTag("backup_settings")
        type("backup_password", "short"); type("backup_confirmation", "short"); pressBack()
        scrollIntoView("encrypted_export"); click("encrypted_export"); waitForTag("backup_message")
        tag("backup_message").assertTextContains("at least 10", substring = true)
    }
    @Test fun insightsCanArchiveOnlyForgottenUnstartedItemsAfterConfirmation() {
        seedDefaults(); val old = addItem("Old backlog", "Movies"); val completed = addItem("Completed movie", "Movies", status = "watched"); addItem("New movie", "Movies")
        db { val item = itemRepository.get(old)!!; database.itemDao().update(item.copy(createdAt = System.currentTimeMillis() - 100 * 86_400_000L)) }
        click("action_settings"); waitForTag("settings_insights"); scrollIntoView("settings_insights"); click("settings_insights"); waitForTag("insights_counts")
        tag("insights_counts").assertTextContains("1 completed", substring = true)
        scrollIntoView("archive_forgotten"); click("archive_forgotten"); composeRule.onNode(hasText("Archive") and hasAnyAncestor(isDialog())).performClick()
        eventually { db { itemRepository.get(old)!!.isArchived } }
        assertFalse(db { itemRepository.get(completed)!!.isArchived })
    }
    @Test fun enablingAppLockHidesTheContentAndSetsSecureWindowFlags() {
        seedDefaults(); waitForTag("nav_today")
        db { features.update { it.copy(lockEnabled = true) } }; session.lock(); waitForTag("app_locked")
        composeRule.onAllNodesWithTag("nav_today").assertCountEquals(0)
        assertTrue(composeRule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        session.unlock(); waitForTag("nav_today")
    }
    @Test fun sharedTextEntryAlsoRequiresUnlock() {
        db { features.update { it.copy(lockEnabled = true) } }; session.lock(); waitForTag("app_locked")
        context.startActivity(Intent(context, ShareActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "A private capture").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Thread.sleep(400)
        composeRule.onAllNodesWithTag("share_text").assertCountEquals(0)
        session.unlock(); waitForTag("share_capture"); click("share_save"); waitUntilGone("share_capture")
        assertEquals("A private capture", db { itemRepository.observeInbox().first().single().item.title })
    }
    @Test fun appLockRedactsActualAndroidNotificationContent() {
        db { features.update { it.copy(lockEnabled = true) } }; session.unlock()
        val id = db { factRepository.create("This fact must remain private") }
        assertTrue(db { notifications.showFact(factRepository.get(id)!!) })
        val manager = context.getSystemService(NotificationManager::class.java)
        eventually { manager.activeNotifications.any { it.tag == "daily" } }
        val posted = manager.activeNotifications.single { it.tag == "daily" }.notification
        assertEquals("Notes App", posted.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString())
        assertFalse(posted.extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT).toString().contains("must remain private"))
    }
    @Test fun launcherCaptureSavesThroughTheNormalParser() {
        seedDefaults(); context.startActivity(LauncherActions.intent(context, "capture")); waitForTag("share_text")
        type("share_text", "Read Dune #scifi"); click("share_save"); waitUntilGone("share_capture")
        val saved = db { itemRepository.getAllFiled().single() }
        assertEquals("Dune", saved.item.title); assertEquals(collectionByName("Books").id, saved.item.collectionId); assertEquals(listOf("scifi"), saved.tags)
    }
    @Test fun randomLauncherActionDrawsAndRecordsOnePick() {
        seedDefaults(); addItem("Arrival", "Movies")
        val originalIntent = Intent(composeRule.activity.intent)
        try {
            context.startActivity(LauncherActions.intent(context, "random")); waitForTag("surprise_title")
            assertEquals("Arrival", textOf("surprise_title")); assertEquals(1, db { itemRepository.observeHistory().first().size })
        } finally { composeRule.runOnUiThread { composeRule.activity.intent = originalIntent } }
    }
    @Test fun bothWidgetsAndThreeAppShortcutsAreRegistered() {
        val widgets = AppWidgetManager.getInstance(context).installedProviders.filter { it.provider.packageName == context.packageName }
        assertTrue(widgets.any { it.provider.className.endsWith("DailyFactWidgetReceiver") })
        assertTrue(widgets.any { it.provider.className.endsWith("CaptureWidgetReceiver") })
        assertEquals(setOf("capture", "voice", "random"), context.getSystemService(ShortcutManager::class.java).manifestShortcuts.map { it.id }.toSet())
    }
}
