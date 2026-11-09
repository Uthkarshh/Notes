package com.notesapp.ui.components

import com.notesapp.ui.navigation.paneSelection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.notesapp.data.db.entity.CollectionEntity
import com.notesapp.domain.model.ItemPresentation
import com.notesapp.domain.model.ItemWithTags
import com.notesapp.domain.model.Priority
import com.notesapp.domain.model.StatusSchema

/** Flag for items that matter: shown for High and Urgent only to keep lists calm. */
@Composable
fun PriorityFlag(priority: Priority, modifier: Modifier = Modifier) {
    if (priority.level < Priority.HIGH.level) return
    val tint = if (priority == Priority.URGENT) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
    Icon(
        imageVector = Icons.Filled.Flag,
        contentDescription = "${priority.label} priority",
        tint = tint,
        modifier = modifier.size(16.dp),
    )
}

@Composable
fun FavoriteToggle(favorite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onToggle, modifier = modifier) {
        Icon(
            imageVector = if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
            contentDescription = if (favorite) "Remove from favorites" else "Add to favorites",
            tint = if (favorite) RatingColor else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Compact list row: collection badge, title, status, rating and tags. */
@Composable
fun ItemListRow(
    entry: ItemWithTags,
    collection: CollectionEntity?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showCollectionName: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onMoveTo: ((String) -> Unit)? = null,
) {
    val item = entry.item
    val schema = collection?.schema
    ListItem(
        modifier = modifier.paneSelection("item", item.id).clickable(onClick = onClick).testTag("item_row_${item.id}"),
        leadingContent = collection?.let { { ItemCover(entry, it, Modifier.size(44.dp, 60.dp)) } },
        headlineContent = { Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FlowRow(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (schema != null) ItemStatusMenu(item.id, item.status, schema, onMoveTo)
                    item.rating?.let { RatingStars(it) }
                    PriorityFlag(item.priorityLevel)
                }
                if (showCollectionName && collection != null) {
                    Text(
                        collection.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ItemMetadataLine(entry, collection)
                TagLabels(entry.tags)
            }
        },
        trailingContent = onToggleFavorite?.let { toggle -> { FavoriteToggle(item.isFavorite, toggle) } },
    )
}

/** Poster-style tile for the cover grid. Without cover art it shows the collection's accent and icon. */
@Composable
fun PosterCard(
    entry: ItemWithTags,
    collection: CollectionEntity?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = entry.item
    val schema = collection?.schema
    val accent = parseHexColor(collection?.colorHex ?: "#90A4AE")
    ElevatedCard(onClick = onClick, modifier = modifier.paneSelection("item", item.id).testTag("item_card_${item.id}")) {
        Box {
            ItemCover(entry, collection, Modifier.fillMaxWidth().aspectRatio(ItemPresentation.from(entry, collection?.categoryType).coverRatio))
            if (item.isFavorite) Surface(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp), shape = MaterialTheme.shapes.small) {
                Icon(Icons.Filled.Star, "Favorite", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(6.dp).size(20.dp))
            }
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (schema != null) StatusLabel(schema.labelOf(item.status), schema.roleOf(item.status))
            ItemMetadataLine(entry, collection)
            item.rating?.let { RatingStars(it, starSize = 12.dp) }
        }
    }
}

/** Card on the Kanban board with a menu to move it to another status column. */
@Composable
fun KanbanCard(
    entry: ItemWithTags,
    schema: StatusSchema,
    onClick: () -> Unit,
    onMoveTo: (String) -> Unit,
    modifier: Modifier = Modifier,
    collection: CollectionEntity? = null,
    dragHandle: (@Composable (() -> Unit) -> Unit)? = null,
) {
    val item = entry.item
    var menuOpen by remember { mutableStateOf(false) }
    OutlinedCard(onClick = onClick, modifier = modifier.paneSelection("item", item.id).fillMaxWidth().testTag("kanban_card_${item.id}")) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 0.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item.rating?.let { RatingStars(it) }
                    PriorityFlag(item.priorityLevel)
                }
                ItemMetadataLine(entry, collection)
                ItemStatusMenu(item.id, item.status, schema, onMoveTo)
                TagLabels(entry.tags)
            }
            Column {
              dragHandle?.invoke { menuOpen = true }
              Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("kanban_move_${item.id}")) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Move ${item.title}")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    schema.statuses.forEach { status ->
                        DropdownMenuItem(
                            text = { Text(status.label) },
                            leadingIcon = {
                                if (status.key == item.status) Icon(Icons.Filled.Check, contentDescription = "Current status")
                            },
                            onClick = {
                                menuOpen = false
                                if (status.key != item.status) onMoveTo(status.key)
                            },
                            modifier = Modifier.testTag("kanban_menu_${item.id}_${status.key}"),
                        )
                    }
                }
              }
            }
        }
    }
}

@Composable
fun ItemMetadataLine(entry: ItemWithTags, collection: CollectionEntity?) {
    val presentation = ItemPresentation.from(entry, collection?.categoryType)
    if (presentation.essentialDetails.isNotBlank()) Text(presentation.essentialDetails, style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (presentation.descriptiveDetails.isNotBlank()) Text(presentation.descriptiveDetails, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
fun ItemStatusMenu(id: Long, key: String, schema: StatusSchema, onMove: ((String) -> Unit)?) {
    if (onMove == null) { StatusLabel(schema.labelOf(key), schema.roleOf(key)); return }
    var expanded by remember { mutableStateOf(false) }
    Box {
        androidx.compose.material3.TextButton(onClick = { expanded = true }, modifier = Modifier.testTag("status_menu_$id")) {
            StatusLabel(schema.labelOf(key) + " ▾", schema.roleOf(key))
        }
        DropdownMenu(expanded, { expanded = false }) {
            schema.statuses.forEach { status -> DropdownMenuItem(text = { Text(status.label) },
                onClick = { expanded = false; if (status.key != key) onMove(status.key) }, modifier = Modifier.testTag("status_to_${id}_${status.key}")) }
        }
    }
}
