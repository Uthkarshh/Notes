package com.notesapp.ui

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RedesignMilestoneTwoTour : UiTestBase() {
    private fun snap(name: String) {
        if (InstrumentationRegistry.getArguments().getString("tour") != "true") return
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.waitForIdle(250, 3_000)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
            ?: checkNotNull(instrumentation.targetContext.getExternalFilesDir(null))
        val directory = File(output, "redesign-m2").apply { check(isDirectory || mkdirs()) }
        File(directory, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
    @Test fun walkThroughCaptureAndInbox() {
        seedDefaults()
        val first = addItem("Visit Kyoto in spring", null, tags = listOf("travel"))
        addItem("Find a trail for the weekend", null, tags = listOf("outdoors"))
        click("fab_capture"); waitForTag("capture_input")
        type("capture_input", "Watch Arrival #scifi !high")
        snap("01_capture_preview")
        click("capture_suggest_priority"); snap("02_priority_picker"); click("capture_priority_high")
        tapText("Cancel"); click("action_inbox"); waitForTag("inbox_list"); snap("03_inbox_suggestions")
        click("inbox_select"); click("inbox_select"); waitForText("2 selected"); snap("04_batch_selection")
        click("back"); click("inbox_delete_$first"); waitForText("Restore"); snap("05_delete_undo")
        tapText("Restore"); waitForTag("inbox_card_$first")
    }
}
