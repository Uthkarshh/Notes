package com.notesapp.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.notesapp.domain.metadata.ItemMetadata
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

internal fun snapshotMilestoneThree(name: String) {
    if (InstrumentationRegistry.getArguments().getString("tour") != "true") return
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.uiAutomation.waitForIdle(250, 3000)
    val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
    val output = instrumentation.targetContext.getExternalFilesDir(null)!!
    val directory = File(output, "redesign-m3").apply { check(isDirectory || mkdirs()) }
    File(directory, "$name.png").outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }; image.recycle()
}

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RedesignMilestoneThreeTour : UiTestBase() {
    @Test fun collectionDiscoverDetailAndVault() {
        seedDefaults()
        val id = addItem("Arrival", "Movies", tags = listOf("quiet-evening"), description = "A thoughtful choice for tonight.")
        addItem("The Grand Budapest Hotel", "Movies", status = "watching", tags = listOf("comedy"))
        db { itemRepository.saveMetadata(id, ItemMetadata(runtimeMinutes = 116, releaseYear = 2016, genre = "Science fiction")); itemRepository.setFavorite(id, true) }
        openTab("collections"); val c = collectionByName("Movies"); waitForTag("collection_card_${c.id}"); click("collection_card_${c.id}"); waitForTag("item_row_$id")
        composeRule.waitForIdle(); snapshotMilestoneThree("01_collection_list")
        click("view_grid"); waitForTag("item_card_$id"); composeRule.waitForIdle(); snapshotMilestoneThree("02_collection_grid")
        click("item_card_$id"); waitForTag("item_detail"); composeRule.waitForIdle(); snapshotMilestoneThree("03_compact_detail")
        click("back"); click("back"); openTab("discover"); waitForTag("discover_summary"); composeRule.waitForIdle(); snapshotMilestoneThree("04_discover_presets")
        click("discover_advanced"); scrollIntoView("discover_price"); composeRule.waitForIdle(); snapshotMilestoneThree("05_filter_draft")
        click("discover_cancel"); waitUntilGone("discover_filter_sheet")
        db { noteRepository.create("A place for small observations", "The moments I remember most are often the quiet ones.", listOf("reflection")); factRepository.create("An octopus has three hearts.", source = "Ocean notes", topic = "Nature") }
        openTab("vault"); waitForTag("notes_list"); composeRule.waitForIdle(); snapshotMilestoneThree("07_vault_notes")
        click("vault_segment_facts"); waitForTag("facts_list"); composeRule.waitForIdle(); snapshotMilestoneThree("08_vault_facts")
    }
}
