package com.notesapp.ui

import android.content.Intent
import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.notesapp.CaptureActivity
import com.notesapp.MainActivity
import com.notesapp.automation.NotificationHelper
import com.notesapp.data.prefs.FeaturePreferences
import com.notesapp.launcher.LauncherActions
import com.notesapp.security.LockSession
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PrivacyTransitionsTest : UiTestBase() {
    @Inject lateinit var features: FeaturePreferences
    @Inject lateinit var session: LockSession
    @After fun unlock() { db { features.update { it.copy(lockEnabled = false) } }; session.unlock() }
    private fun enableLock() { session.unlock(); db { features.update { it.copy(lockEnabled = true) } } }
    private fun secure(): Boolean = composeRule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    @Test fun captureDraftIsHiddenDuringRelockAndReturnsOnlyAfterUnlock() {
        enableLock(); click("fab_capture"); waitForTag("capture_input"); type("capture_input", "Private capture draft")
        session.lock(); waitForTag("app_locked"); tag("capture_input").assertDoesNotExist()
        text("Private capture draft").assertDoesNotExist(); assertTrue(secure())
        session.unlock(); waitForTag("capture_input"); tag("capture_input").assertTextContains("Private capture draft")
        assertTrue(secure()); tapText("Cancel"); waitUntilGone("capture_input")
        db { features.update { it.copy(lockEnabled = false) } }
        eventually { !secure() }
    }
    @Test fun metadataConsentAndEditorAreHiddenWhenLocked() {
        seedDefaults(); val id = addItem("Private movie title", "Movies"); enableLock()
        openTab("collections"); val c = collectionByName("Movies").id
        waitForTag("collection_card_$c"); click("collection_card_$c"); waitForTag("item_row_$id"); click("item_row_$id")
        waitForTag("item_detail"); scrollIntoView("metadata_lookup"); click("metadata_lookup"); waitForTag("metadata_consent")
        session.lock(); waitForTag("app_locked")
        tag("metadata_consent").assertDoesNotExist(); tag("item_detail").assertDoesNotExist()
        text("Private movie title").assertDoesNotExist(); assertTrue(secure())
        session.unlock(); waitForTag("item_detail"); assertTrue(secure())
    }
    @Test fun externalVoiceEntryCannotLaunchRecognizerBeforeUnlock() {
        enableLock(); session.lock(); waitForTag("app_locked")
        val intent = LauncherActions.intent(composeRule.activity, "voice")
        val main = composeRule.activity
        var external: CaptureActivity? = null
        main.startActivity(intent)
        try {
            composeRule.waitUntil(20_000) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    external = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<CaptureActivity>().singleOrNull()
                }
                external != null
            }
            tag("voice_waiting").assertDoesNotExist(); tag("share_capture").assertDoesNotExist()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                assertTrue(checkNotNull(external).window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            }
        } finally { InstrumentationRegistry.getInstrumentation().runOnMainSync { external?.finish() } }
        composeRule.waitUntil(20_000) { main.lifecycle.currentState == androidx.lifecycle.Lifecycle.State.RESUMED }
    }
    @Test fun notificationTargetWaitsBehindLockAndOpensOnceAfterUnlock() {
        seedDefaults(); val id = addItem("Private notification destination", "Movies"); enableLock()
        session.lock(); waitForTag("app_locked")
        val original = Intent(composeRule.activity.intent)
        try {
            composeRule.activity.startActivity(Intent(composeRule.activity, MainActivity::class.java)
                .putExtra(NotificationHelper.EXTRA_TYPE, "item").putExtra(NotificationHelper.EXTRA_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            composeRule.runOnUiThread { composeRule.activity.intent = original }
            tag("item_detail").assertDoesNotExist(); text("Private notification destination").assertDoesNotExist()
            session.unlock(); waitForTag("item_detail"); tag("item_title").assertTextContains("Private notification destination")
            click("back"); waitForTag("nav_today"); tag("item_detail").assertDoesNotExist()
        } finally { composeRule.runOnUiThread { composeRule.activity.intent = original } }
    }
}
