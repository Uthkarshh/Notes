package com.notesapp.ui

import androidx.compose.ui.graphics.Color
import com.notesapp.data.prefs.AppSettings
import com.notesapp.ui.collections.boardEdgeVelocity
import com.notesapp.ui.components.ratingAtPosition
import com.notesapp.ui.motion.MotionPolicy
import com.notesapp.ui.theme.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class MilestoneFourInteractionTest {
    @Test fun longAuthorsCannotPushPageCountsOutOfEssentialMetadata() {
        val item = com.notesapp.data.db.entity.ItemEntity(id = 1, title = "Book", status = "want_to_read", createdAt = 0, updatedAt = 0,
            metadataJson = com.notesapp.domain.metadata.ItemMetadata.merge(null,
                com.notesapp.domain.metadata.ItemMetadata(author = "A very long author name ".repeat(8), pageCount = 1234)))
        val presentation = com.notesapp.domain.model.ItemPresentation.from(com.notesapp.domain.model.ItemWithTags(item, emptyList()), com.notesapp.domain.model.CategoryType.BOOK)
        assertEquals("1234 pages", presentation.essentialDetails)
        assertTrue(presentation.descriptiveDetails.startsWith("A very long author"))
    }
    @Test fun appAndSystemMotionPreferencesAreIndependent() {
        assertTrue(MotionPolicy(false, 1f).enabled)
        assertFalse(MotionPolicy(true, 1f).enabled)
        assertFalse(MotionPolicy(false, 0f).enabled)
        assertEquals(0, MotionPolicy(true, 2f).duration(320))
        assertEquals(320, MotionPolicy(false, .5f).duration(320))
    }
    @Test fun appearanceArchivesRemainBackwardCompatible() {
        assertFalse(Json.decodeFromString<AppSettings>("{}").reduceMotion)
        val value = AppSettings(reduceMotion = true)
        assertEquals(value, Json.decodeFromString<AppSettings>(Json.encodeToString(value)))
    }
    @Test fun boardScrollHasAQuietCenterAndCappedEdges() {
        assertEquals(0f, boardEdgeVelocity(150f, 0f, 300f, 48f, 800f), 0f)
        assertEquals(-800f, boardEdgeVelocity(0f, 0f, 300f, 48f, 800f), 0f)
        assertEquals(800f, boardEdgeVelocity(300f, 0f, 300f, 48f, 800f), 0f)
        assertEquals(400f, boardEdgeVelocity(276f, 0f, 300f, 48f, 800f), .01f)
    }
    @Test fun leavingTheBoardStopsAutoscroll() {
        assertEquals(0f, boardEdgeVelocity(-1f, 0f, 300f, 48f, 800f), 0f)
        assertEquals(0f, boardEdgeVelocity(301f, 0f, 300f, 48f, 800f), 0f)
        assertEquals(0f, boardEdgeVelocity(0f, 0f, 0f, 48f, 800f), 0f)
    }
    @Test fun narrowScrollRegionsStillHaveSymmetricEdges() {
        assertEquals(-400f, boardEdgeVelocity(12f, 0f, 48f, 48f, 800f), .01f)
        assertEquals(400f, boardEdgeVelocity(36f, 0f, 48f, 48f, 800f), .01f)
    }
    @Test fun ratingPointerFollowsRtlStarOrder() {
        assertEquals(.5f, ratingAtPosition(1f, 200, false), 0f)
        assertEquals(5f, ratingAtPosition(1f, 200, true), 0f)
        assertEquals(5f, ratingAtPosition(199f, 200, false), 0f)
        assertEquals(.5f, ratingAtPosition(199f, 200, true), 0f)
        assertEquals(2.5f, ratingAtPosition(100f, 200, true), 0f)
    }
    @Test fun collectionInkAndFunctionalAccentsStayReadable() {
        for (background in listOf(Color.White, Color.Black, Color(0xFFFFD54F), Color(0xFF90A4AE))) {
            assertTrue(contrastRatio(contrastInk(background), background) >= 4.5f)
            for (accent in listOf(Color.White, Color.Black, Color.Yellow, Color.Cyan)) {
                assertTrue(contrastRatio(accessibleAccent(accent, background), background) >= 3f)
            }
        }
    }
    @Test fun staticAndAmoledTextRolesMeetContrastAndKeepBlackRoots() {
        for (scheme in listOf(LightColors, DarkColors, DarkColors.toAmoled())) {
            for ((foreground, background) in listOf(scheme.onSurface to scheme.surface,
                scheme.onSurfaceVariant to scheme.surfaceContainerHighest, scheme.onPrimary to scheme.primary,
                scheme.onPrimaryContainer to scheme.primaryContainer, scheme.inverseOnSurface to scheme.inverseSurface)) {
                assertTrue("Contrast ${contrastRatio(foreground, background)}", contrastRatio(foreground, background) >= 4.5f)
            }
        }
        val black = DarkColors.toAmoled()
        assertEquals(Color.Black, black.surface)
        assertEquals(Color.Black, black.background)
        assertEquals(Color.Black, black.surfaceContainerLowest)
    }
}
