package com.notesapp.ui.collections

import com.notesapp.ui.motion.AppModalBottomSheet as ModalBottomSheet
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notesapp.domain.template.CollectionTemplates
import com.notesapp.domain.template.TemplateGroup
import com.notesapp.ui.components.AppTopBarActions
import com.notesapp.ui.components.CollectionBadge
import com.notesapp.ui.components.EmptyState
import com.notesapp.ui.components.parseHexColor
import com.notesapp.ui.navigation.AppNavigator

@Composable
fun CollectionsScreen(navigator: AppNavigator, viewModel: CollectionsViewModel = hiltViewModel()) {
    val summaries by viewModel.summaries.collectAsStateWithLifecycle()
    var showTemplates by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
            TopAppBar(
                title = { Text("Collections") },
                actions = { AppTopBarActions(navigator) },
            )
            androidx.compose.material3.TextButton(onClick = { showTemplates = true }, modifier = Modifier.padding(horizontal = 8.dp).testTag("fab_new_collection")) { Text("New collection") }
            }
        },
    ) { padding ->
        val list = summaries
        when {
            list == null -> Unit
            list.isEmpty() -> EmptyState(
                icon = Icons.Filled.CollectionsBookmark,
                title = "No collections yet",
                message = "Collections keep movies, books, places and anything else you want to get to.",
                actionLabel = "Create a collection",
                onAction = { showTemplates = true },
                modifier = Modifier.padding(padding),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier.fillMaxSize().testTag("collections_grid"),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list, key = { it.collection.id }) { summary ->
                    CollectionCard(summary, onClick = { navigator.openCollection(summary.collection.id) })
                }
            }
        }
    }

    if (showTemplates) {
        TemplatePickerSheet(
            onDismiss = { showTemplates = false },
            onPick = { template ->
                showTemplates = false
                navigator.newCollection(template.id)
            },
        )
    }
}

@Composable
private fun CollectionCard(summary: CollectionSummary, onClick: () -> Unit) {
    val collection = summary.collection
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("collection_card_${collection.id}"),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CollectionBadge(collection.iconName, collection.colorHex, size = 44.dp)
            Text(
                collection.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${summary.open} active",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("collection_active_${collection.id}"),
            )
            LinearProgressIndicator(
                progress = { summary.progress },
                color = com.notesapp.ui.theme.accessibleAccent(parseHexColor(collection.colorHex), MaterialTheme.colorScheme.surfaceContainerHighest),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = if (summary.total == 0) "Empty" else "${summary.done} of ${summary.total} done",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Lists the built-in templates by group; the pick opens the collection editor prefilled with it. */
@Composable
private fun TemplatePickerSheet(
    onDismiss: () -> Unit,
    onPick: (com.notesapp.domain.template.CollectionTemplate) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.navigationBarsPadding().testTag("template_list"),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item {
                Text(
                    "New collection",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            TemplateGroup.entries.forEach { group ->
                item(key = "group_${group.name}") {
                    Text(
                        group.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(CollectionTemplates.all.filter { it.group == group }, key = { it.id }) { template ->
                    ListItem(
                        modifier = Modifier.clickable { onPick(template) }.testTag("template_${template.id}"),
                        leadingContent = { CollectionBadge(template.iconName, template.colorHex) },
                        headlineContent = { Text(template.name) },
                        supportingContent = {
                            Text(
                                template.schema.statuses.joinToString(" → ") { it.label },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        }
    }
}
