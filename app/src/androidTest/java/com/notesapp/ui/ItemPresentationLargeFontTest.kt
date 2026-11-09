package com.notesapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.notesapp.data.db.entity.*
import com.notesapp.domain.model.*
import com.notesapp.domain.metadata.ItemMetadata
import com.notesapp.domain.template.CollectionTemplates
import com.notesapp.ui.components.*
import com.notesapp.ui.theme.NotesAppTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ItemPresentationLargeFontTest {
    @get:Rule val compose = createComposeRule()
    @Test fun listGridAndBoardActionsRemainReachableAtTwoHundredPercentFonts() {
        val template = CollectionTemplates.byId("movies")!!
        val c = CollectionEntity(1, "Movies", CategoryType.MOVIE, "movie", "#326176", template.schema.toJson(), 0, 0)
        val entry = ItemWithTags(ItemEntity(id = 1, collectionId = 1, title = "A very long movie title to enjoy on a quiet evening with friends",
            status = c.schema.initial.key, metadataJson = ItemMetadata.merge(null, ItemMetadata(runtimeMinutes = 116, releaseYear = 2016)), createdAt = 0, updatedAt = 0), listOf("weekend"))
        var opened = 0; var moved: String? = null; var favorite = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                NotesAppTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ItemListRow(entry, c, { opened++ }, onToggleFavorite = { favorite = true }, onMoveTo = { moved = it })
                    PosterCard(entry, c, { opened++ }, Modifier.width(220.dp))
                    KanbanCard(entry, c.schema, { opened++ }, { moved = it }, collection = c)
                } }
            }
        }
        compose.onAllNodesWithTag("status_menu_1").onFirst().performScrollTo().performClick()
        compose.onNodeWithTag("status_to_1_watching").performClick(); assertEquals("watching", moved)
        compose.onNodeWithContentDescription("Add to favorites").performScrollTo().performClick(); assertTrue(favorite)
        compose.onNodeWithTag("item_card_1").performScrollTo().assertIsDisplayed().performClick(); assertEquals(1, opened)
        compose.waitForIdle(); snapshotMilestoneThree("09_grid_large_font")
        compose.onNodeWithTag("kanban_move_1").performScrollTo().performClick(); compose.onNodeWithTag("kanban_menu_1_watched").performClick()
        assertEquals("watched", moved)
    }
}
