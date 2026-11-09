package com.notesapp.ui

import android.app.LocaleManager
import android.graphics.Bitmap
import android.os.LocaleList
import android.os.ParcelFileDescriptor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.notesapp.data.prefs.AppSettings
import com.notesapp.domain.model.ThemeMode
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.roundToInt

@HiltAndroidTest
@SdkSuppress(minSdkVersion = 33)
@RunWith(AndroidJUnit4::class)
class AccessibilityThemeTest : UiTestBase() {
    private var size: String? = null
    private var width = 0
    private var font = "1.0"
    private var fontValue = 1f
    private var locales = ""
    private lateinit var localeManager: LocaleManager
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText().trim() }

    @Before fun rememberConfiguration() {
        size = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1)
        width = composeRule.activity.resources.configuration.screenWidthDp
        fontValue = composeRule.activity.resources.configuration.fontScale
        font = shell("settings get system font_scale")
        localeManager = composeRule.activity.applicationContext.getSystemService(LocaleManager::class.java)
        locales = localeManager.applicationLocales.toLanguageTags()
    }
    @After fun restoreConfiguration() {
        localeManager.applicationLocales = LocaleList.forLanguageTags(locales)
        shell(if (font == "null") "settings delete system font_scale" else "settings put system font_scale $font")
        shell("wm size ${size ?: "reset"}")
        composeRule.waitUntil(30_000) {
            val c = composeRule.activity.resources.configuration
            kotlin.math.abs(c.fontScale - fontValue) < .01f && kotlin.math.abs(c.screenWidthDp - width) <= 2 &&
                composeRule.activity.lifecycle.currentState == androidx.lifecycle.Lifecycle.State.RESUMED
        }
        composeRule.waitForIdle()
    }
    private fun movie(id: Long) {
        openTab("collections"); val c = collectionByName("Movies").id
        waitForTag("collection_card_$c"); click("collection_card_$c"); waitForTag("item_row_$id"); click("item_row_$id"); waitForTag("item_detail")
    }
    @Test fun ratingSupportsKeyboardAndSwitchActions() {
        seedDefaults(); val id = addItem("Keyboard rating", "Movies"); movie(id)
        scrollIntoView("item_rating")
        tag("item_rating").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        tag("item_rating").performKeyInput { pressKey(Key.DirectionRight) }
        eventually { db { itemRepository.get(id)!!.rating == .5f } }
        tag("item_rating").performKeyInput { pressKey(Key.MoveEnd) }
        eventually { db { itemRepository.get(id)!!.rating == 5f } }
        eventually { textOf("item_rating_text").contains("5 / 5") }
        val decrease = tag("item_rating").fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == "Decrease rating" }
        composeRule.runOnUiThread { assertTrue(decrease.action()) }
        eventually { db { itemRepository.get(id)!!.rating == 4.5f } }
        tag("item_rating").performKeyInput { pressKey(Key.Delete) }
        eventually { db { itemRepository.get(id)!!.rating == null } }
    }
    @Test fun twoHundredPercentRtlKeepsViewsAndPaneActionsReachable() {
        seedDefaults(); val id = addItem("A long movie title that must remain understandable in every collection view", "Movies")
        shell("settings put system font_scale 2.0")
        localeManager.applicationLocales = LocaleList.forLanguageTags("ar")
        val density = composeRule.activity.resources.displayMetrics.density
        shell("wm size ${(1200 * density).roundToInt()}x${(900 * density).roundToInt()}")
        composeRule.waitUntil(30_000) {
            val c = composeRule.activity.resources.configuration
            c.fontScale >= 1.99f && c.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL && c.screenWidthDp >= 1198
        }
        openTab("collections"); val c = collectionByName("Movies").id
        waitForTag("collection_card_$c"); click("collection_card_$c"); waitForTag("list_pane")
        click("view_grid"); waitForTag("item_card_$id"); click("item_card_$id"); waitForTag("item_detail")
        tag("item_card_$id").assertIsSelected(); tag("item_title").assertIsDisplayed()
        snapshotMilestoneFour("05_rtl_large_text_panes")
        click("back"); waitForTag("pane_placeholder"); click("view_board"); waitForTag("status_menu_$id")
        tag("status_menu_$id").performScrollTo().performClick(); waitForTag("status_to_${id}_watching"); click("status_to_${id}_watching")
        eventually { db { itemRepository.get(id)!!.status == "watching" } }
        openTab("today"); click("action_settings"); waitForTag("settings_notifications")
        scrollIntoView("settings_notifications"); click("settings_notifications"); waitForTag("notification_settings")
        tag("detail_pane").assertIsDisplayed()
        tag("notifications_enabled").assertIsDisplayed().assertTextContains("Daily notification")
        click("back"); waitForTag("pane_placeholder")
        scrollIntoView("settings_advanced"); click("settings_advanced"); waitForTag("advanced_settings")
        tag("app_lock_toggle").assertIsDisplayed().assertTextContains("Require biometrics or device PIN").assertIsOff()
        snapshotMilestoneFour("07_rtl_settings")
        scrollIntoView("lookup_enabled"); tag("lookup_enabled").assertIsDisplayed().assertTextContains("Enable title lookup")
        click("back"); waitForTag("pane_placeholder")
        scrollIntoView("settings_backups"); click("settings_backups"); waitForTag("backup_settings")
        scrollIntoView("backup_enabled"); tag("backup_enabled").assertIsDisplayed().assertTextContains("Automatic encrypted backups")
    }
    @Test fun appearanceModesKeepNavigationAndCaptureUsable() {
        seedDefaults(); addItem("Plan a quiet evening", "Movies", status = "watching")
        val variants = listOf(
            "01_light" to AppSettings(ThemeMode.LIGHT, dynamicColor = false),
            "02_dark" to AppSettings(ThemeMode.DARK, dynamicColor = false),
            "03_amoled" to AppSettings(ThemeMode.DARK, amoledBlack = true, dynamicColor = false),
            "04_dynamic" to AppSettings(ThemeMode.LIGHT, dynamicColor = true),
        )
        for ((name, appearance) in variants) {
            db { settingsRepository.restoreAppearance(appearance) }
            composeRule.activityRule.scenario.recreate(); waitForTag("nav_today"); waitForTag("fab_capture")
            snapshotMilestoneFour(name)
            click("fab_capture"); waitForTag("capture_input"); tapText("Cancel"); waitUntilGone("capture_input")
            openTab("vault"); waitForTag("nav_vault"); openTab("today")
        }
    }
}
