package com.notesapp.ui.vault

import com.notesapp.ui.motion.AppAlertDialog as AlertDialog
import com.notesapp.ui.navigation.paneSelection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notesapp.domain.facts.FactFormatter
import com.notesapp.domain.model.FactWithTags
import com.notesapp.domain.model.MasteryState
import com.notesapp.domain.model.NoteWithTags
import com.notesapp.domain.model.StatusRole
import com.notesapp.ui.components.AppTopBarActions
import com.notesapp.ui.components.ConfirmDialog
import com.notesapp.ui.components.EmptyState
import com.notesapp.ui.components.FavoriteToggle
import com.notesapp.ui.components.StatusLabel
import com.notesapp.ui.components.TagLabels
import com.notesapp.ui.components.relativeTime
import com.notesapp.ui.components.shareText
import com.notesapp.ui.navigation.AppNavigator

/** Mastery states share the status colour language: new = not started, reviewing = in progress, learned = done. */
internal fun MasteryState.role(): StatusRole = when (this) {
    MasteryState.NEW -> StatusRole.BACKLOG
    MasteryState.REVIEWING -> StatusRole.ACTIVE
    MasteryState.LEARNED -> StatusRole.DONE
    MasteryState.HIDDEN -> StatusRole.DROPPED
}

@Composable
fun VaultScreen(navigator: AppNavigator, viewModel: VaultViewModel = hiltViewModel()) {
    VaultContent(navigator, viewModel)
}

/** Route-free content; the caller supplies the navigation-entry-scoped ViewModel. */
@Composable
internal fun VaultContent(navigator: AppNavigator, viewModel: VaultViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val randomFact by viewModel.randomFact.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val onFacts = state.segment == VaultSegment.FACTS

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vault") },
                actions = {
                    if (onFacts) {
                        IconButton(
                            onClick = viewModel::drawRandomFact,
                            enabled = state.facts.any { it.fact.masteryState != MasteryState.HIDDEN },
                            modifier = Modifier.testTag("vault_random_fact"),
                        ) {
                            Icon(Icons.Filled.Casino, contentDescription = "Draw a random fact")
                        }
                    }
                    AppTopBarActions(navigator)
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                VaultSegment.entries.forEachIndexed { index, segment ->
                    SegmentedButton(
                        selected = state.segment == segment,
                        onClick = { viewModel.selectSegment(segment) },
                        shape = SegmentedButtonDefaults.itemShape(index, VaultSegment.entries.size),
                        modifier = Modifier.testTag("vault_segment_${segment.name.lowercase()}"),
                    ) { Text(segment.label) }
                }
            }

            TextButton(onClick = { if (onFacts) navigator.openFact(null) else navigator.openNote(null) }, modifier = Modifier.padding(horizontal = 8.dp).testTag("fab_vault_new")) { Text(if (onFacts) "New fact" else "New note") }

            val bottomPadding = padding.calculateBottomPadding() + 96.dp
            when (state.segment) {
                VaultSegment.NOTES -> NotesTab(state, viewModel, navigator, bottomPadding)
                VaultSegment.FACTS -> FactsTab(state, viewModel, navigator, bottomPadding) { fact ->
                    context.shareText(FactFormatter.shareText(fact.fact.text, fact.fact.source), "Share fact")
                }
            }
        }
    }

    randomFact?.let { fact ->
        RandomFactDialog(
            fact = fact,
            onAnother = viewModel::drawRandomFact,
            onFavorite = { viewModel.setFactFavorite(fact, !fact.fact.isFavorite) },
            onShare = { context.shareText(FactFormatter.shareText(fact.fact.text, fact.fact.source), "Share fact") },
            onDismiss = viewModel::dismissRandomFact,
        )
    }
}

@Composable
private fun NotesTab(state: VaultUiState, viewModel: VaultViewModel, navigator: AppNavigator, bottomPadding: androidx.compose.ui.unit.Dp) {
    if (state.totalNotes == 0) {
        EmptyState(
            icon = Icons.AutoMirrored.Filled.StickyNote2,
            title = "No notes yet",
            message = "Capture reflections, plans and anything free-form. Notes are searchable and taggable.",
            actionLabel = "Write a note",
            onAction = { navigator.openNote(null) },
        )
        return
    }
    if (state.noteTags.isNotEmpty()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().testTag("note_filter_row"),
        ) {
            items(state.noteTags, key = { it }) { tag ->
                FilterChip(
                    selected = state.selectedNoteTags.any { it.equals(tag, ignoreCase = true) },
                    onClick = { viewModel.toggleNoteTag(tag) },
                    label = { Text("#$tag") },
                    modifier = Modifier.testTag("note_filter_$tag"),
                )
            }
        }
    }
    if (state.notes.isEmpty()) {
        EmptyState(Icons.Filled.FilterListOff, "No notes match", "Try another tag.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("notes_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.notes, key = { it.note.id }) { note ->
            NoteCard(
                note = note,
                onClick = { navigator.openNote(note.note.id) },
                onTogglePin = { viewModel.setNotePinned(note, !note.note.isPinned) },
            )
        }
    }
}

@Composable
private fun NoteCard(note: NoteWithTags, onClick: () -> Unit, onTogglePin: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().paneSelection("note", note.note.id).testTag("note_card_${note.note.id}")) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = note.note.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconToggleButton(
                    checked = note.note.isPinned,
                    onCheckedChange = { onTogglePin() },
                    modifier = Modifier.testTag("note_pin_${note.note.id}"),
                ) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = if (note.note.isPinned) "Unpin note" else "Pin note",
                        tint = if (note.note.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                }
            }
            if (note.note.content.isNotBlank()) {
                Text(
                    text = note.note.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
            Row(
                modifier = Modifier.padding(top = 8.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(relativeTime(note.note.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TagLabels(note.tags, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FactsTab(
    state: VaultUiState,
    viewModel: VaultViewModel,
    navigator: AppNavigator,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onShare: (FactWithTags) -> Unit,
) {
    if (state.totalFacts == 0) {
        EmptyState(
            icon = Icons.Filled.Lightbulb,
            title = "No facts yet",
            message = "Save trivia, quotes and snippets. One of them greets you every morning.",
            actionLabel = "Add a fact",
            onAction = { navigator.openFact(null) },
        )
        return
    }
    val filters = state.factFilters
    if (state.factTopics.isNotEmpty() || state.factTags.isNotEmpty()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().testTag("fact_topic_row"),
        ) {
            items(state.factTopics, key = { "topic_$it" }) { topic ->
                FilterChip(
                    selected = filters.topic.equals(topic, ignoreCase = true),
                    onClick = { viewModel.selectTopic(topic) },
                    label = { Text(topic) },
                    modifier = Modifier.testTag("fact_topic_$topic"),
                )
            }
            items(state.factTags, key = { "tag_$it" }) { tag ->
                FilterChip(
                    selected = filters.tag.equals(tag, ignoreCase = true),
                    onClick = { viewModel.selectFactTag(tag) },
                    label = { Text("#$tag") },
                    modifier = Modifier.testTag("fact_tag_$tag"),
                )
            }
        }
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().testTag("fact_mastery_row"),
    ) {
        items(MasteryFilter.entries, key = { it.name }) { option ->
            FilterChip(
                selected = filters.mastery == option,
                onClick = { viewModel.selectMastery(option) },
                label = { Text(option.label) },
                modifier = Modifier.testTag("fact_mastery_${option.name.lowercase()}"),
            )
        }
    }
    if (state.facts.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.FilterListOff,
            title = "No facts match",
            message = "Try other filters.",
            actionLabel = "Clear filters",
            onAction = viewModel::clearFactFilters,
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("facts_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.facts, key = { it.fact.id }) { fact ->
            FactCard(
                fact = fact,
                onClick = { navigator.openFact(fact.fact.id) },
                onFavorite = { viewModel.setFactFavorite(fact, !fact.fact.isFavorite) },
                onShare = { onShare(fact) },
                onMastery = { viewModel.setFactMastery(fact, it) },
                onDelete = { viewModel.deleteFact(fact) },
            )
        }
    }
}

@Composable
private fun FactCard(
    fact: FactWithTags,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onMastery: (MasteryState) -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var masteryMenu by remember { mutableStateOf(false) }
    val item = fact.fact
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().paneSelection("fact", item.id).testTag("fact_card_${item.id}")) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 4.dp)) {
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 8.dp),
            )
            item.source?.let {
                Text(
                    "— $it",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    TextButton(onClick = { masteryMenu = true }, modifier = Modifier.testTag("fact_mastery_${item.id}")) {
                        StatusLabel(item.masteryState.label + " ▾", item.masteryState.role())
                    }
                    DropdownMenu(masteryMenu, { masteryMenu = false }) {
                        MasteryState.entries.forEach { target -> DropdownMenuItem(text = { Text(target.label) },
                            onClick = { masteryMenu = false; onMastery(target) }, modifier = Modifier.testTag("fact_mastery_to_${target.name.lowercase()}_${item.id}")) }
                    }
                }
                item.topic?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TagLabels(fact.tags, modifier = Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FavoriteToggle(item.isFavorite, onFavorite, modifier = Modifier.testTag("fact_card_favorite_${item.id}"))
                IconButton(onClick = onShare) { Icon(Icons.Filled.Share, contentDescription = "Share fact") }
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("fact_card_menu_${item.id}")) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        MasteryState.entries.filter { it != item.masteryState }.forEach { target ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when (target) {
                                            MasteryState.HIDDEN -> "Hide from rotation"
                                            else -> "Mark as ${target.label.lowercase()}"
                                        },
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    onMastery(target)
                                },
                                modifier = Modifier.testTag("fact_mark_${target.name.lowercase()}_${item.id}"),
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                menuOpen = false
                                confirmDelete = true
                            },
                            modifier = Modifier.testTag("fact_delete_${item.id}"),
                        )
                    }
                }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this fact?",
            text = "This can't be undone.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun RandomFactDialog(
    fact: FactWithTags,
    onAnother: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Random fact") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("random_fact")) {
                Text(fact.fact.text, style = MaterialTheme.typography.bodyLarge)
                fact.fact.source?.let {
                    Text("— $it", style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FavoriteToggle(fact.fact.isFavorite, onFavorite)
                    IconButton(onClick = onShare) { Icon(Icons.Filled.Share, contentDescription = "Share fact") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onAnother, modifier = Modifier.testTag("random_fact_another")) { Text("Another") } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("random_fact_close")) { Text("Close") } },
    )
}
