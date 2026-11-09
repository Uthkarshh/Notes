package com.notesapp.ui

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.notesapp.data.metadata.*
import com.notesapp.domain.metadata.ItemMetadata
import com.notesapp.domain.model.CategoryType
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

class FixtureMetadataSource : MetadataSource {
    var detailsRequested = false
    var failCover = false
    val partial = MetadataResult("fixture", "Provider title", ItemMetadata(author = "Provider author"), coverUrl = "https://covers.openlibrary.org/fixture.jpg")
    override suspend fun search(query: String, category: CategoryType) = listOf(partial)
    override suspend fun details(result: MetadataResult, category: CategoryType): MetadataResult {
        detailsRequested = true
        return result.copy(metadata = result.metadata.copy(pageCount = 320, isbn = "1234567890"))
    }
    override suspend fun downloadCover(url: String): String {
        if (failCover) error("Fixture download failure")
        return "file:///fixture-cover.jpg"
    }
}

@dagger.hilt.android.testing.UninstallModules(MetadataModule::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MetadataReviewTest : UiTestBase() {
    private val fixture = FixtureMetadataSource()
    @BindValue @JvmField val metadataSource: MetadataSource = fixture
    private fun review(): Long {
        seedDefaults(); val id = addItem("My book title", "Books", description = "Private notes")
        db { itemRepository.saveMetadata(id, ItemMetadata(author = "My author")); itemRepository.setImage(id, "file:///original-cover.jpg") }
        openTab("collections"); val c = collectionByName("Books"); waitForTag("collection_card_${c.id}"); click("collection_card_${c.id}")
        waitForTag("item_row_$id"); click("item_row_$id"); waitForTag("item_detail")
        scrollIntoView("metadata_lookup"); click("metadata_lookup"); waitForTag("metadata_enable_search"); click("metadata_enable_search")
        waitForText("Provider title"); text("Provider title").performScrollTo().performClick(); waitForTag("metadata_review")
        assertTrue(fixture.detailsRequested); tag("review_select_pageCount").assertIsOn(); tag("review_select_author").assertIsOff()
        return id
    }
    @Test fun fullDetailsAreReviewedAndOnlySelectedFieldsAreApplied() {
        val id = review(); composeRule.waitForIdle(); snapshotMilestoneThree("06_metadata_review"); text("Provider: 320").assertExists(); click("metadata_apply_selected"); waitUntilGone("metadata_review")
        eventually { ItemMetadata.decode(db { itemRepository.get(id)!!.metadataJson }).pageCount == 320 }
        val saved = db { itemRepository.get(id)!! }; assertEquals("My book title", saved.title); assertEquals("Private notes", saved.description)
        assertEquals("My author", ItemMetadata.decode(saved.metadataJson).author); assertEquals("file:///original-cover.jpg", saved.imageUri)
        waitForText("Undo"); tapText("Undo"); eventually { ItemMetadata.decode(db { itemRepository.get(id)!!.metadataJson }).pageCount == null }
    }
    @Test fun changingAFieldDuringReviewRequiresSelectingItAgain() {
        val id = review(); click("review_select_author")
        db { itemRepository.commitEditor(id, mapOf("author" to "New local author")) }
        click("metadata_apply_selected"); waitForText("Some details changed while you reviewed.", substring = true)
        tag("review_select_author").assertIsOff(); tag("review_current_author").assertTextEquals("Current: New local author")
        assertNull(ItemMetadata.decode(db { itemRepository.get(id)!!.metadataJson }).pageCount)
        click("review_select_author"); click("metadata_apply_selected"); waitUntilGone("metadata_review")
        eventually { ItemMetadata.decode(db { itemRepository.get(id)!!.metadataJson }).author == "Provider author" }
    }
    @Test fun failedCoverDownloadKeepsSavedCoverAndAppliedDetails() {
        fixture.failCover = true; val id = review()
        scrollIntoView("review_cover"); click("review_cover")
        click("metadata_apply_selected"); waitUntilGone("metadata_review")
        waitForText("Details applied. The cover could not be downloaded; your saved cover is kept.")
        val saved = db { itemRepository.get(id)!! }; assertEquals("file:///original-cover.jpg", saved.imageUri)
        assertEquals(320, ItemMetadata.decode(saved.metadataJson).pageCount)
    }
}
