package com.notesapp.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.HingePolicy
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldValue
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.LocalOwnersProvider
import com.notesapp.ui.AppNavHost
import com.notesapp.ui.collections.CollectionDetailScreen
import com.notesapp.ui.search.SearchScreen
import com.notesapp.ui.settings.SettingsScreen
import com.notesapp.ui.vault.VaultScreen

internal val LocalPaneList = staticCompositionLocalOf { false }
internal val LocalPaneMasterContent = staticCompositionLocalOf<(@Composable (NavBackStackEntry) -> Unit)?> { null }

@Composable
internal fun PaneListDestination(entry: NavBackStackEntry, navigator: AppNavigator) {
    val render = LocalPaneMasterContent.current
    if (render != null) render(entry) else PaneListContent(entry, navigator)
}

@Composable
private fun PaneListContent(entry: NavBackStackEntry, navigator: AppNavigator) {
    when {
        entry.destination.hasRoute<CollectionDetailRoute>() -> CollectionDetailScreen(navigator)
        entry.destination.hasRoute<VaultRoute>() -> VaultScreen(navigator)
        entry.destination.hasRoute<SearchRoute>() -> SearchScreen(navigator)
        entry.destination.hasRoute<SettingsRoute>() -> SettingsScreen(navigator)
    }
}

/**
 * Navigation Compose owns every entry and editor. The list uses the SAME parent entry in both
 * placements, with movable content for resize and a saveable holder for compact detail/Back.
 * There is no second navigator, editor instance, or selection stack to reconcile.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun AdaptiveAppContent(
    navController: NavHostController,
    navigator: AppNavigator,
    current: NavBackStackEntry?,
    parent: NavBackStackEntry?,
    modifier: Modifier = Modifier,
) {
    val holder = rememberSaveableStateHolder()
    val lifecycle = LocalLifecycleOwner.current
    val renderList = remember(navigator, holder, lifecycle) {
        movableContentOf<NavBackStackEntry, androidx.compose.animation.AnimatedVisibilityScope?> { entry, visibility ->
            entry.LocalOwnersProvider(holder) {
                // Covered parent entries may be CREATED; a visible list must still observe live data.
                CompositionLocalProvider(LocalLifecycleOwner provides lifecycle, com.notesapp.ui.motion.LocalCoverVisibility provides visibility) {
                    key(entry.id) { PaneListContent(entry, navigator) }
                }
            }
        }
    }
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val recommended = calculatePaneScaffoldDirective(adaptiveInfo, HingePolicy.AlwaysAvoid)
    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    val windowWidth = with(density) { windowSize.width.toDp().value }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var origin by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .onGloballyPositioned { origin = it.positionInWindow() }) {
        val obstructions = recommended.excludedBounds.map { rect ->
            with(density) { PaneObstruction((rect.left - origin.x).toDp().value, (rect.top - origin.y).toDp().value,
                (rect.right - origin.x).toDp().value, (rect.bottom - origin.y).toDp().value) }
        }
        val split = parent != null && PaneLayoutPolicy.canSplit(windowWidth, maxWidth.value, maxHeight.value, obstructions, rtl)
        val directive = recommended.copy(maxHorizontalPartitions = if (split) 2 else 1,
            maxVerticalPartitions = 1, horizontalPartitionSpacerSize = if (split) 24.dp else 0.dp)
        val value = ThreePaneScaffoldValue(primary = PaneAdaptedValue.Expanded,
            secondary = if (split) PaneAdaptedValue.Expanded else PaneAdaptedValue.Hidden, tertiary = PaneAdaptedValue.Hidden)
        CompositionLocalProvider(LocalPaneSelection provides if (split) current?.paneSelection() else null,
            LocalPaneMasterContent provides { entry ->
            // Outgoing list destinations must not briefly compose a second copy during a transition.
            if (entry.id == current?.id || (!split && entry.id == parent?.id &&
                    entry.destination.hasRoute<CollectionDetailRoute>() && current?.destination?.hasRoute<ItemDetailRoute>() == true)) {
                if (split) PanePlaceholder(entry) else renderList(entry, com.notesapp.ui.motion.LocalCoverVisibility.current)
            }
        }) {
            ListDetailPaneScaffold(directive = directive, value = value,
                listPane = {
                    if (split) {
                        Box(Modifier.preferredWidth((maxWidth * .4f).coerceIn(280.dp, 400.dp)).fillMaxSize()
                            .testTag("list_pane").semantics { isTraversalGroup = true; paneTitle = "List" }) {
                            CompositionLocalProvider(LocalPaneList provides true) { renderList(checkNotNull(parent), null) }
                        }
                    }
                },
                detailPane = {
                    Box(Modifier.fillMaxSize().testTag(if (split) "detail_pane" else "single_pane")
                        .semantics { isTraversalGroup = true }) {
                        AppNavHost(navController, navigator, Modifier.fillMaxSize())
                    }
                },
            )
        }
    }
}

@Composable
private fun PanePlaceholder(entry: NavBackStackEntry) {
    val message = when {
        entry.destination.hasRoute<CollectionDetailRoute>() -> "Choose an item"
        entry.destination.hasRoute<VaultRoute>() -> "Choose a note or fact"
        entry.destination.hasRoute<SearchRoute>() -> "Select a search result"
        else -> "Select a settings section"
    }
    Box(Modifier.fillMaxSize().testTag("pane_placeholder"), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, style = MaterialTheme.typography.headlineSmall)
            Text("Its details will appear here.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
