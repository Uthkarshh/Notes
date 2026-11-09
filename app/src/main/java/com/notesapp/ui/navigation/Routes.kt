package com.notesapp.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Today
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

// Type-safe navigation routes (Navigation Compose 2.8+ with Kotlin Serialization).

@Serializable
data object TodayRoute

@Serializable
data object CollectionsRoute

@Serializable
data object VaultRoute

@Serializable
data object DiscoverRoute

@Serializable
data class CollectionDetailRoute(val collectionId: Long)

@Serializable
data class ItemDetailRoute(val itemId: Long)

/** Edits [collectionId], or creates a collection prefilled from [templateId] (blank when both are null). */
@Serializable
data class CollectionEditorRoute(val collectionId: Long? = null, val templateId: String? = null)

@Serializable
data class NoteEditorRoute(val noteId: Long? = null)

@Serializable
data class FactEditorRoute(val factId: Long? = null)

@Serializable
data object SearchRoute

@Serializable
data object InboxRoute

@Serializable
data object SettingsRoute

@Serializable
data object DataTransferRoute

@Serializable
data object HistoryRoute

@Serializable
data object NotificationSettingsRoute

@Serializable data object RandomLaunchRoute
@Serializable data object AdvancedSettingsRoute
@Serializable data object BackupSettingsRoute
@Serializable data object InsightsRoute

/** The four destinations shown in the bottom bar / navigation rail. */
enum class TopLevelDestination(
    val route: Any,
    val label: String,
    val icon: ImageVector,
) {
    TODAY(TodayRoute, "Today", Icons.Filled.Today),
    COLLECTIONS(CollectionsRoute, "Collections", Icons.Filled.CollectionsBookmark),
    VAULT(VaultRoute, "Vault", Icons.AutoMirrored.Filled.StickyNote2),
    DISCOVER(DiscoverRoute, "Discover", Icons.Filled.Explore),
}
