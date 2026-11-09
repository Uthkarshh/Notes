package com.notesapp.ui.navigation

import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute

internal data class PaneSelection(val kind: String, val id: Long)
internal val LocalPaneSelection = staticCompositionLocalOf<PaneSelection?> { null }

internal fun NavBackStackEntry.paneSelection(): PaneSelection? = when {
    destination.hasRoute<ItemDetailRoute>() -> PaneSelection("item", toRoute<ItemDetailRoute>().itemId)
    destination.hasRoute<NoteEditorRoute>() -> toRoute<NoteEditorRoute>().noteId?.let { PaneSelection("note", it) }
    destination.hasRoute<FactEditorRoute>() -> toRoute<FactEditorRoute>().factId?.let { PaneSelection("fact", it) }
    else -> null
}

@Composable
internal fun Modifier.paneSelection(kind: String, id: Long): Modifier {
    val selected = LocalPaneSelection.current == PaneSelection(kind, id)
    return if (selected) border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
        .semantics { this.selected = true } else this
}
