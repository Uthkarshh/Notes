package com.notesapp.ui.navigation

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute

/** Only immediate parent/child pairs share a pane. A deep link needs no invented parent. */
internal fun paneParent(current: NavBackStackEntry?, previous: NavBackStackEntry?): NavBackStackEntry? {
    if (current == null) return null
    if (current.destination.isPaneList()) return current
    return previous?.takeIf { it.destination.canShowBeside(current.destination) }
}

internal fun NavDestination.isPaneList(): Boolean =
    hasRoute<CollectionDetailRoute>() || hasRoute<VaultRoute>() || hasRoute<SearchRoute>() || hasRoute<SettingsRoute>()

internal fun NavDestination.canShowBeside(detail: NavDestination): Boolean = when {
    hasRoute<CollectionDetailRoute>() -> detail.hasRoute<ItemDetailRoute>()
    hasRoute<VaultRoute>() -> detail.hasRoute<NoteEditorRoute>() || detail.hasRoute<FactEditorRoute>()
    hasRoute<SearchRoute>() -> detail.hasRoute<ItemDetailRoute>() || detail.hasRoute<NoteEditorRoute>() || detail.hasRoute<FactEditorRoute>()
    hasRoute<SettingsRoute>() -> detail.hasRoute<DataTransferRoute>() || detail.hasRoute<NotificationSettingsRoute>() ||
        detail.hasRoute<AdvancedSettingsRoute>() || detail.hasRoute<BackupSettingsRoute>() || detail.hasRoute<InsightsRoute>()
    else -> false
}

internal fun NavDestination.canShowRouteBeside(route: Any): Boolean = when {
    hasRoute<CollectionDetailRoute>() -> route is ItemDetailRoute
    hasRoute<VaultRoute>() -> route is NoteEditorRoute || route is FactEditorRoute
    hasRoute<SearchRoute>() -> route is ItemDetailRoute || route is NoteEditorRoute || route is FactEditorRoute
    hasRoute<SettingsRoute>() -> route is DataTransferRoute || route is NotificationSettingsRoute ||
        route is AdvancedSettingsRoute || route is BackupSettingsRoute || route is InsightsRoute
    else -> false
}

internal fun NavBackStackEntry.matchesRoute(route: Any): Boolean = when (route) {
    is ItemDetailRoute -> destination.hasRoute<ItemDetailRoute>() && toRoute<ItemDetailRoute>() == route
    is NoteEditorRoute -> route.noteId != null && destination.hasRoute<NoteEditorRoute>() && toRoute<NoteEditorRoute>() == route
    is FactEditorRoute -> route.factId != null && destination.hasRoute<FactEditorRoute>() && toRoute<FactEditorRoute>() == route
    is CollectionDetailRoute -> destination.hasRoute<CollectionDetailRoute>() && toRoute<CollectionDetailRoute>() == route
    is CollectionEditorRoute -> route.collectionId != null && destination.hasRoute<CollectionEditorRoute>() && toRoute<CollectionEditorRoute>() == route
    else -> destination.hasRoute(route::class)
}

/** Content coordinates in dp, independent of Android window APIs so minimum widths can be tested. */
internal data class PaneObstruction(val left: Float, val top: Float, val right: Float, val bottom: Float)

internal object PaneLayoutPolicy {
    const val MIN_LIST = 280f
    const val MIN_DETAIL = 360f
    const val GAP = 24f

    fun canSplit(windowWidth: Float, contentWidth: Float, contentHeight: Float,
                 obstructions: List<PaneObstruction> = emptyList(), rtl: Boolean = false): Boolean {
        if (windowWidth < 840 || contentHeight < 480 || contentWidth < MIN_LIST + MIN_DETAIL + GAP) return false
        val cuts = obstructions.filter { it.right >= 0 && it.left <= contentWidth && it.bottom > 0 && it.top < contentHeight }
        if (cuts.isEmpty()) return true
        if (cuts.size != 1) return false
        val hinge = cuts.single()
        if (hinge.bottom - hinge.top < contentHeight / 2 || hinge.right - hinge.left > contentWidth / 2) return false
        val left = hinge.left.coerceIn(0f, contentWidth)
        val right = (contentWidth - hinge.right).coerceIn(0f, contentWidth)
        return if (rtl) right >= MIN_LIST && left >= MIN_DETAIL else left >= MIN_LIST && right >= MIN_DETAIL
    }
}
