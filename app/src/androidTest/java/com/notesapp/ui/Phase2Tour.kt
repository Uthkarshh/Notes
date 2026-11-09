package com.notesapp.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.notesapp.ShareActivity
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** New Phase 2 flows, using synthetic data, Android notification delivery, and the document picker. */
@HiltAndroidTest
@SdkSuppress(minSdkVersion = 33)
@RunWith(AndroidJUnit4::class)
class Phase2Tour : UiTestBase() {
    @get:Rule(order = 2) val notifications: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @After fun cleanupNotifications() { context.getSystemService(NotificationManager::class.java).cancelAll() }

    private fun snap(name: String, synchronizeCompose: Boolean = true) {
        if (synchronizeCompose) composeRule.waitForIdle()
        Thread.sleep(600)
        if (InstrumentationRegistry.getArguments().getString("tour") != "true") return
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.takeIf { it.isNotBlank() }
            ?.let(::File) ?: checkNotNull(context.getExternalFilesDir(null))
        val dir = File(output, "phase2-tour")
        check(dir.isDirectory || dir.mkdirs())
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(dir, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }

    private fun systemCommand(command: String) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
    }

    @Test fun walkThroughPhase2() {
        systemCommand("cmd statusbar collapse")
        seedDefaults()
        val item = addItem("Arrival", "Movies", tags = listOf("scifi"))
        addItem("Project Hail Mary", "Books", tags = listOf("scifi"))
        db {
            factRepository.create("Octopuses have three hearts and blue blood.", source = "Marine biology", topic = "Animals")
            itemRepository.setReminder(item, System.currentTimeMillis() + 3_600_000)
        }
        waitForTag("due_reminder_$item")
        scrollTo("today_list", "due_reminders")
        snap("01_due_reminders")
        click("due_reminder_$item"); waitForTag("item_detail")
        click("item_status_watched"); waitForTag("reflection_input")
        type("reflection_input", "The story made me think about how language shapes memory.")
        pressBack()
        snap("02_completion_reflection")
        click("reflection_save"); waitUntilGone("reflection_sheet")
        waitForText("Reflections & linked notes")
        scrollIntoView("linked_notes")
        snap("03_linked_reflection")
        click("back")

        waitForTag("action_settings"); click("action_settings"); waitForTag("settings_notifications")
        scrollIntoView("settings_notifications"); click("settings_notifications"); waitForTag("notification_settings")
        snap("04_notification_settings")
        scrollIntoView("notification_preview"); click("notification_preview"); waitForText("Preview notification sent.")
        eventually(description = "Android posts the preview") { context.getSystemService(NotificationManager::class.java).activeNotifications.any { it.tag == "daily" } }
        snap("05_notification_preview")
        click("back"); waitForTag("settings_data_transfer")
        scrollIntoView("settings_data_transfer"); click("settings_data_transfer"); waitForTag("data_transfer")
        snap("06_import_export")
        click("export_json")
        // DocumentsUI is an external process and may need a cold start on a loaded emulator.
        composeRule.waitUntil(30_000) { instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()?.contains("documentsui") == true }
        snap("07_android_document_picker")
        repeat(3) {
            if (instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() != context.packageName) { pressBack(); Thread.sleep(300) }
        }
        assertEquals(context.packageName, instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString())
        tag("export_json").assertIsDisplayed()
        click("back"); click("back")

        openTab("discover"); click("discover_advanced"); waitForTag("discover_filter_sheet"); scrollIntoView("discover_cooldown"); click("discover_cooldown"); tapText("Last 7 days"); click("discover_apply"); waitUntilGone("discover_filter_sheet")
        scrollIntoView("discover_draw"); click("discover_draw"); waitForTag("surprise_accept"); click("surprise_accept")
        waitForTag("surprise_confirmation"); scrollIntoView("discover_history"); click("discover_history"); waitForTag("history_list")
        snap("08_selection_history")

        context.startActivity(Intent(context, ShareActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "A science article").putExtra(Intent.EXTRA_TEXT, "https://example.com/science #reading")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitForTag("share_capture"); snap("09_share_capture")
        click("share_cancel"); waitUntilGone("share_capture")
    }
}
