package com.notesapp.ui

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.notesapp.domain.metadata.ItemMetadata
import com.notesapp.domain.model.ThemeMode
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Synthetic-data smoke flow. Pass tour=true to retain its new screen captures. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RedesignMilestoneOneTour : UiTestBase() {
    private fun snap(name: String) {
        if (InstrumentationRegistry.getArguments().getString("tour") != "true") return
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
            ?: checkNotNull(instrumentation.targetContext.getExternalFilesDir(null))
        val directory = File(output, "redesign-m1").apply { check(isDirectory || mkdirs()) }
        File(directory, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }

    @Test fun walkThroughTheMilestoneOneChanges() {
        seedDefaults()
        val movie = addItem("Arrival", "Movies", tags = listOf("scifi"))
        addItem("The Left Hand of Darkness", "Books", status = "reading")
        addItem("Find a new weekend trail", null)
        db {
            itemRepository.setReminder(movie, System.currentTimeMillis() - 60_000)
            itemRepository.saveMetadata(movie, ItemMetadata(runtimeMinutes = 116, releaseYear = 2016, genre = "Science fiction"))
            factRepository.create("Octopuses have three hearts and blue blood.", source = "Marine biology")
        }
        waitForTag("due_reminder_$movie"); snap("01_today")
        click("due_reminder_$movie"); waitForTag("item_detail"); snap("02_item_detail")
        scrollIntoView("metadata_releaseYear"); snap("03_autosave_details")
        scrollIntoView("metadata_lookup"); click("metadata_lookup"); waitForTag("metadata_consent"); snap("04_contextual_consent")
        click("metadata_decline"); click("back"); waitForTag("nav_today")
        db { settingsRepository.setThemeMode(ThemeMode.DARK); settingsRepository.setAmoledBlack(true) }
        waitForTag("due_reminder_$movie"); composeRule.waitForIdle(); snap("05_today_amoled")
    }
}
