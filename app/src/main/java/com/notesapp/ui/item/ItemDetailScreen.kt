package com.notesapp.ui.item

import com.notesapp.ui.navigation.EditorBackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notesapp.data.db.entity.CollectionEntity
import com.notesapp.domain.model.Priority
import com.notesapp.ui.components.ConfirmDialog
import com.notesapp.ui.components.DropdownField
import com.notesapp.ui.components.FavoriteToggle
import com.notesapp.ui.components.SectionTitle
import com.notesapp.ui.components.StarRatingBar
import com.notesapp.ui.components.TagInput
import com.notesapp.ui.components.formatDate
import com.notesapp.ui.components.formatRating
import com.notesapp.ui.components.openLink
import com.notesapp.ui.components.relativeTime
import com.notesapp.ui.navigation.AppNavigator
import kotlinx.coroutines.launch
import com.notesapp.domain.capture.ItemEditFields
import kotlinx.serialization.json.Json

@Composable
fun ItemDetailScreen(navigator: AppNavigator, viewModel: ItemDetailViewModel = hiltViewModel()) {
    ItemDetailContent(navigator, viewModel)
}

/** Route-free content; the caller supplies the navigation-entry-scoped ViewModel. */
@Composable
internal fun ItemDetailContent(navigator: AppNavigator, viewModel: ItemDetailViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val linkedNotes by viewModel.linkedNotes.collectAsStateWithLifecycle()
    val entry = state.entry
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val draft by viewModel.editor.state.collectAsStateWithLifecycle()
    val title = draft.fields[ItemEditFields.TITLE].orEmpty()
    val description = draft.fields[ItemEditFields.NOTES].orEmpty()
    val link = draft.fields[ItemEditFields.LINK].orEmpty()
    val tags = draft.fields[ItemEditFields.TAGS]?.let(ItemEditFields::tags).orEmpty()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // The item vanished (deleted here or elsewhere).
    LaunchedEffect(state.loading, entry) {
        if (!state.loading && entry == null) navigator.back()
    }

    fun leave() = navigator.back()

    com.notesapp.ui.navigation.EditorNavigationGuard(navigator) { !draft.ready || viewModel.editor.flush() }
    EditorBackHandler(onBack = ::leave)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flush() }

    if (entry == null || !draft.ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            draft.failure?.let { Text(it, Modifier.padding(24.dp)) } ?: androidx.compose.material3.CircularProgressIndicator()
        }
        return
    }
    val item = entry.item
    val collection = state.collection
    val schema = collection?.schema

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(collection?.name ?: "Inbox", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = ::leave, modifier = Modifier.testTag("back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    FavoriteToggle(
                        favorite = item.isFavorite,
                        onToggle = { viewModel.setFavorite(!item.isFavorite) },
                        modifier = Modifier.testTag("item_favorite"),
                    )
                    Box {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("item_menu")) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(if (item.isArchived) "Unarchive" else "Archive") },
                                onClick = {
                                    menuOpen = false
                                    viewModel.setArchived(!item.isArchived)
                                },
                                modifier = Modifier.testTag("menu_archive"),
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = {
                                    menuOpen = false
                                    confirmDelete = true
                                },
                                modifier = Modifier.testTag("menu_delete"),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("item_detail"),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(when {
                    draft.failure != null -> draft.failure!!
                    draft.errors.isNotEmpty() -> "Some edits need attention"
                    draft.pending -> "Saving…"
                    else -> "Saved on device"
                }, style = MaterialTheme.typography.bodySmall,
                    color = if (draft.failure != null || draft.errors.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).testTag("item_save_state"))
                if (draft.failure != null) TextButton(onClick = viewModel::flush) { Text("Retry") }
            }
            if (draft.restored) Text("Draft restored. Incomplete fields are kept until you finish them.", style = MaterialTheme.typography.bodySmall)
            if (item.isArchived) {
                Text(
                    "Archived — hidden from lists and Surprise me",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                com.notesapp.ui.components.ItemCover(entry, collection, Modifier.size(64.dp, 88.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { viewModel.editor.edit(ItemEditFields.TITLE, it) },
                isError = ItemEditFields.TITLE in draft.errors,
                supportingText = draft.errors[ItemEditFields.TITLE]?.let { message -> { Text(message) } },
                label = { Text("Title") },
                textStyle = MaterialTheme.typography.titleLarge,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.weight(1f).testTag("item_title"),
            )
            }

            DetailGroup("Collection", "detail_collection") {
            DropdownField(
                label = "Collection",
                selectedLabel = collection?.name ?: "Inbox",
                options = listOf<CollectionEntity?>(null) + state.collections,
                optionLabel = { it?.name ?: "Inbox" },
                onSelect = { target -> if (target?.id != collection?.id) viewModel.moveTo(target) },
                modifier = Modifier.fillMaxWidth().testTag("item_collection"),
            )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Status")
                if (schema == null) {
                    Text(
                        "This item is in your Inbox. Move it to a collection to track its status.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.testTag("item_status_row"),
                    ) {
                        schema.statuses.forEach { status ->
                            val selected = status.key == item.status
                            FilterChip(
                                selected = selected,
                                onClick = { if (!selected) viewModel.setStatus(status.key) },
                                label = { Text(status.label) },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Filled.Check, contentDescription = "Current status", modifier = Modifier.size(18.dp)) }
                                } else {
                                    null
                                },
                                modifier = Modifier.testTag("item_status_${status.key}"),
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { viewModel.editor.edit(ItemEditFields.NOTES, it) },
                label = { Text("Quick notes") },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().testTag("item_description"),
            )

            ReminderControls(item.reminderAt, viewModel::setReminder, navigator::openNotificationSettings)

            DetailGroup("Rating & priority", "detail_rating") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Rating")
                    Text(
                        text = item.rating?.let { "  ${formatRating(it)} / 5" } ?: "  not rated",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("item_rating_text"),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StarRatingBar(
                        rating = item.rating,
                        onRatingChange = viewModel::setRating,
                        modifier = Modifier.testTag("item_rating"),
                    )
                    if (item.rating != null) {
                        TextButton(onClick = { viewModel.setRating(null) }, modifier = Modifier.testTag("item_rating_clear")) {
                            Text("Clear")
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Priority")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Priority.entries.forEach { priority ->
                        FilterChip(selected = item.priorityLevel == priority, onClick = { viewModel.setPriority(priority) },
                            label = { Text(priority.label) }, modifier = Modifier.testTag("item_priority_${priority.name.lowercase()}"))
                    }
                }
            }

            }
            DetailGroup("Details", "detail_metadata", needsAttention = draft.errors.keys.any { it in com.notesapp.domain.metadata.MetadataFields.keys }) {
                MetadataEditor(draft.fields, draft.errors, collection?.categoryType, viewModel.editor::edit)
            }
            DetailGroup("Cover & online details", "detail_cover") {
                ItemEnrichment(item.id, title, item.imageUri, collection?.categoryType, draft.fields,
                    viewModel::applyMetadata, viewModel::applyImage, viewModel::applyProviderImage)
            }
            DetailGroup("Links & tags", "detail_links") {
            OutlinedTextField(
                value = link,
                onValueChange = { viewModel.editor.edit(ItemEditFields.LINK, it) },
                label = { Text("Link") },
                singleLine = true,
                trailingIcon = {
                    if (link.isNotBlank()) {
                        IconButton(onClick = { context.openLink(link) }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open link")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("item_link"),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Tags")
                TagInput(tags = tags, onTagsChange = { viewModel.editor.edit(ItemEditFields.TAGS, Json.encodeToString(it)) }, suggestions = state.knownTags)
            }

            }

            if (linkedNotes.isNotEmpty()) {
                DetailGroup("Reflections & linked notes", "detail_reflections", initiallyOpen = true) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("linked_notes")) {
                    linkedNotes.forEach { note ->
                        androidx.compose.material3.OutlinedCard(onClick = { navigator.openNote(note.id) }, modifier = Modifier.fillMaxWidth().testTag("linked_note_${note.id}")) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(note.title.ifBlank { "Untitled note" }, style = MaterialTheme.typography.titleSmall)
                                Text(note.content, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(bottom = 24.dp)) {
                val footer = MaterialTheme.typography.bodySmall
                val color = MaterialTheme.colorScheme.onSurfaceVariant
                Text("Added ${formatDate(item.createdAt)}", style = footer, color = color)
                Text("Updated ${relativeTime(item.updatedAt)}", style = footer, color = color)
                item.completedAt?.let { Text("Completed ${formatDate(it)}", style = footer, color = color) }
                if (item.selectedCount > 0) {
                    Text("Picked by Surprise me ${item.selectedCount}×", style = footer, color = color)
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete “${item.title}”?",
            text = "This can't be undone.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun DetailGroup(title: String, tag: String, needsAttention: Boolean = false, initiallyOpen: Boolean = false, content: @Composable () -> Unit) {
    var expanded by rememberSaveable(tag) { mutableStateOf(initiallyOpen) }
    LaunchedEffect(needsAttention) { if (needsAttention) expanded = true }
    androidx.compose.material3.OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }.testTag(tag)) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(if (expanded) "−" else "+", style = MaterialTheme.typography.titleLarge)
            }
            if (expanded) { content(); androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp)) }
        }
    }
}
