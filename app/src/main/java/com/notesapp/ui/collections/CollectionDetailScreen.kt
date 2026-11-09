package com.notesapp.ui.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.material3.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewKanban
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notesapp.data.db.entity.CollectionEntity
import com.notesapp.domain.model.ItemWithTags
import com.notesapp.domain.model.SortField
import com.notesapp.domain.model.StatusRole
import com.notesapp.ui.components.CollectionIcons
import com.notesapp.ui.components.DeleteCollectionDialog
import com.notesapp.ui.components.EmptyState
import com.notesapp.ui.components.ItemListRow
import com.notesapp.ui.components.KanbanCard
import com.notesapp.ui.components.PosterCard
import com.notesapp.ui.components.StatusLabel
import com.notesapp.ui.navigation.AppNavigator
import kotlinx.coroutines.launch

@Composable
fun CollectionDetailScreen(navigator: AppNavigator, viewModel: CollectionDetailViewModel = hiltViewModel()) {
    CollectionDetailContent(navigator, viewModel)
}

/** Route-free content; the caller supplies the navigation-entry-scoped ViewModel. */
@Composable
internal fun CollectionDetailContent(navigator: AppNavigator, viewModel: CollectionDetailViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val collection = state.collection
    val scope = rememberCoroutineScope()
    var sortMenuOpen by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    var deleteItemCount by remember { mutableStateOf<Int?>(null) }

    // The collection disappears after being deleted (from here or the editor): return to the overview.
    LaunchedEffect(state.loading, collection) {
        if (!state.loading && collection == null) navigator.backToCollections()
    }
    if (collection == null) {
        Box(Modifier.fillMaxSize())
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(collection.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = navigator::back, modifier = Modifier.testTag(if (com.notesapp.ui.navigation.LocalPaneList.current) "pane_list_back" else "back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { sortMenuOpen = true }, modifier = Modifier.testTag("sort_button")) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                            SortField.entries.forEach { field ->
                                val selected = state.sort.field == field
                                DropdownMenuItem(
                                    text = {
                                        Text(field.label + if (selected) (if (state.sort.descending) "  ↓" else "  ↑") else "")
                                    },
                                    leadingIcon = { if (selected) Icon(Icons.Filled.Check, contentDescription = "Sorted by") },
                                    onClick = {
                                        viewModel.selectSort(field)
                                        sortMenuOpen = false
                                    },
                                    modifier = Modifier.testTag("sort_${field.name}"),
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { overflowOpen = true }, modifier = Modifier.testTag("collection_menu")) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Edit collection") },
                                onClick = {
                                    overflowOpen = false
                                    navigator.editCollection(collection.id)
                                },
                                modifier = Modifier.testTag("menu_edit_collection"),
                            )
                            DropdownMenuItem(
                                text = { Text("Delete collection") },
                                onClick = {
                                    overflowOpen = false
                                    scope.launch { deleteItemCount = viewModel.itemCount() }
                                },
                                modifier = Modifier.testTag("menu_delete_collection"),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                ViewMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = state.viewMode == mode,
                        onClick = { viewModel.setViewMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, ViewMode.entries.size),
                        icon = {
                            SegmentedButtonDefaults.Icon(active = state.viewMode == mode) {
                                Icon(mode.icon(), contentDescription = null, modifier = Modifier.size(SegmentedButtonDefaults.IconSize))
                            }
                        },
                        modifier = Modifier.testTag("view_${mode.name.lowercase()}"),
                    ) { Text(mode.label) }
                }
            }

            FilterRow(collection, state, viewModel)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${state.items.size} of ${state.totalCount} items · ${state.sort.field.label} ${if (state.sort.descending) "↓" else "↑"}",
                    style = MaterialTheme.typography.bodySmall.copy(textDirection = androidx.compose.ui.text.style.TextDirection.ContentOrLtr),
                    modifier = Modifier.weight(1f).testTag("collection_match_count"))
                if (state.filters.isActive) TextButton(onClick = viewModel::clearFilters, modifier = Modifier.testTag("clear_collection_filters")) { Text("Clear filters") }
            }

            val bottomPadding = padding.calculateBottomPadding() + 96.dp
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.totalCount == 0 -> EmptyState(
                        icon = CollectionIcons.get(collection.iconName),
                        title = "Nothing here yet",
                        message = "Tap + to add your first item to ${collection.name}.",
                    )
                    state.items.isEmpty() -> EmptyState(
                        icon = Icons.Filled.FilterListOff,
                        title = "No items match",
                        message = "Try removing one of the filters.",
                        actionLabel = "Clear filters",
                        onAction = viewModel::clearFilters,
                    )
                    else -> when (state.viewMode) {
                        ViewMode.LIST -> ItemList(state.items, collection, bottomPadding, navigator, viewModel)
                        ViewMode.GRID -> ItemGrid(state.items, collection, bottomPadding, navigator)
                        ViewMode.BOARD -> StatusBoard(collection, state.items, bottomPadding, navigator::openItem, viewModel::moveCard)
                    }
                }
            }
        }
    }

    deleteItemCount?.let { count ->
        DeleteCollectionDialog(
            name = collection.name,
            itemCount = count,
            onKeepItemsInInbox = {
                deleteItemCount = null
                viewModel.deleteCollection(deleteItems = false)
            },
            onDeleteEverything = {
                deleteItemCount = null
                viewModel.deleteCollection(deleteItems = true)
            },
            onDismiss = { deleteItemCount = null },
        )
    }
}

private fun ViewMode.icon(): ImageVector = when (this) {
    ViewMode.LIST -> Icons.AutoMirrored.Filled.ViewList
    ViewMode.GRID -> Icons.Filled.GridView
    ViewMode.BOARD -> Icons.Filled.ViewKanban
}

/** Filter chips for favorites, every status of the collection, and the tags in use. */
@Composable
private fun FilterRow(
    collection: CollectionEntity,
    state: CollectionDetailUiState,
    viewModel: CollectionDetailViewModel,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().testTag("filter_row"),
    ) {
        item(key = "favorites") {
            FilterChip(
                selected = state.filters.favoritesOnly,
                onClick = viewModel::toggleFavoritesOnly,
                label = { Text("Favorites") },
                leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.testTag("filter_favorites"),
            )
        }
        items(collection.schema.statuses, key = { "status_${it.key}" }) { status ->
            FilterChip(
                selected = status.key in state.filters.statusKeys,
                onClick = { viewModel.toggleStatusFilter(status.key) },
                label = { Text(status.label) },
                modifier = Modifier.testTag("filter_status_${status.key}"),
            )
        }
        items(state.tags, key = { "tag_$it" }) { tag ->
            FilterChip(
                selected = state.filters.tags.any { it.equals(tag, ignoreCase = true) },
                onClick = { viewModel.toggleTagFilter(tag) },
                label = { Text("#$tag") },
                modifier = Modifier.testTag("filter_tag_$tag"),
            )
        }
    }
}

@Composable
private fun ItemList(
    items: List<ItemWithTags>,
    collection: CollectionEntity,
    bottomPadding: Dp,
    navigator: AppNavigator,
    viewModel: CollectionDetailViewModel,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("item_list"),
        contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding),
    ) {
        items(items, key = { it.item.id }) { entry ->
            val statuses = collection.schema.statuses
            val next = statuses.getOrNull(statuses.indexOfFirst { it.key == entry.item.status } + 1)
            val swipe = rememberSwipeToDismissBoxState()
            val scope = rememberCoroutineScope()
            SwipeToDismissBox(state = swipe, enableDismissFromStartToEnd = next != null, enableDismissFromEndToStart = false,
                onDismiss = { if (next != null) viewModel.setStatus(entry.item.id, next.key); scope.launch { swipe.reset() } },
                backgroundContent = { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer).padding(20.dp), contentAlignment = Alignment.CenterStart) { Text(next?.let { "Move to ${it.label}" }.orEmpty()) } },
                modifier = Modifier.testTag("collection_swipe_${entry.item.id}")) {
            ItemListRow(
                entry = entry,
                collection = collection,
                onClick = { navigator.openItem(entry.item.id) },
                onToggleFavorite = { viewModel.toggleFavorite(entry) },
                onMoveTo = { viewModel.setStatus(entry.item.id, it) },
            )
            }
        }
    }
}

@Composable
private fun ItemGrid(
    items: List<ItemWithTags>,
    collection: CollectionEntity,
    bottomPadding: Dp,
    navigator: AppNavigator,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        modifier = Modifier.fillMaxSize().testTag("item_grid"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.item.id }) { entry ->
            PosterCard(entry, collection, onClick = { navigator.openItem(entry.item.id) })
        }
    }
}

