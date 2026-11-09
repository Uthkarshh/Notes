package com.notesapp.ui.navigation

import org.junit.Assert.*
import org.junit.Test

class PaneLayoutPolicyTest {
    @Test fun compactAndMediumWindowsStaySingle() {
        listOf(360f, 600f, 839f).forEach { assertFalse(PaneLayoutPolicy.canSplit(it, it, 900f)) }
    }
    @Test fun expandedWindowRequiresBothMinimumWidthsAndGapAfterInsets() {
        assertFalse(PaneLayoutPolicy.canSplit(840f, 663f, 900f))
        assertTrue(PaneLayoutPolicy.canSplit(840f, 664f, 900f))
        assertTrue(PaneLayoutPolicy.canSplit(1200f, 1104f, 900f))
    }
    @Test fun shortLandscapeUsesSinglePane() {
        assertFalse(PaneLayoutPolicy.canSplit(1200f, 1104f, 479f))
        assertTrue(PaneLayoutPolicy.canSplit(1200f, 1104f, 480f))
    }
    @Test fun verticalHingeNeedsUsableSpaceOnBothSides() {
        assertTrue(PaneLayoutPolicy.canSplit(1000f, 904f, 900f, listOf(PaneObstruction(400f, 0f, 424f, 900f))))
        assertFalse(PaneLayoutPolicy.canSplit(1000f, 904f, 900f, listOf(PaneObstruction(270f, 0f, 294f, 900f))))
        assertFalse(PaneLayoutPolicy.canSplit(1000f, 904f, 900f, listOf(PaneObstruction(530f, 0f, 554f, 900f))))
    }
    @Test fun rtlExchangesListAndDetailMinimumsAtTheHinge() {
        val hinge = listOf(PaneObstruction(370f, 0f, 394f, 900f))
        assertFalse(PaneLayoutPolicy.canSplit(840f, 700f, 900f, hinge))
        assertTrue(PaneLayoutPolicy.canSplit(840f, 700f, 900f, hinge, rtl = true))
    }
    @Test fun tabletopAndMultipleHingesStaySingle() {
        assertFalse(PaneLayoutPolicy.canSplit(1200f, 1104f, 900f, listOf(PaneObstruction(0f, 440f, 1104f, 460f))))
        assertFalse(PaneLayoutPolicy.canSplit(1200f, 1104f, 900f, listOf(PaneObstruction(300f, 0f, 320f, 900f), PaneObstruction(700f, 0f, 720f, 900f))))
    }
    @Test fun hingeOutsideContentDoesNotPreventSplit() {
        assertTrue(PaneLayoutPolicy.canSplit(1200f, 1104f, 900f, listOf(PaneObstruction(-100f, 0f, -80f, 900f))))
    }
}
