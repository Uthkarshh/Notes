package com.notesapp.ui.inbox

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notesapp.data.prefs.InboxGestures
import com.notesapp.data.prefs.InboxSwipeAction
import com.notesapp.domain.capture.InboxSuggestion
import com.notesapp.domain.capture.InboxSuggestions
import com.notesapp.domain.model.ItemWithTags
import com.notesapp.ui.capture.CollectionPickerDialog
import com.notesapp.ui.components.*
import com.notesapp.ui.navigation.AppNavigator
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun InboxScreen(navigator: AppNavigator, viewModel: InboxViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val recent by viewModel.recentDestinations.collectAsStateWithLifecycle()
    val gestures by viewModel.gestures.collectAsStateWithLifecycle()
    val selected by viewModel.selectedIds.collectAsStateWithLifecycle()
    val selecting by viewModel.selecting.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val pending by viewModel.pendingDeletions.collectAsStateWithLifecycle()
    var pickerIds by rememberSaveable { mutableStateOf<List<Long>?>(null) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Scaffold(topBar = {
        TopAppBar(title = { Text(if (selecting) "${selected.size} selected" else items?.takeIf { it.isNotEmpty() }?.let { "Inbox (${it.size})" } ?: "Inbox") },
            navigationIcon = { IconButton(onClick = { if (selecting) viewModel.clearSelection() else navigator.back() }, enabled = !busy, modifier = Modifier.testTag("back")) {
                Icon(if (selecting) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack, if (selecting) "Close selection" else "Back")
            } }, actions = {
                TextButton(onClick = { if (selecting) viewModel.selectAll() else viewModel.startSelection() }, enabled = !busy && !items.isNullOrEmpty(), modifier = Modifier.testTag("inbox_select")) { Text(if (selecting) "Select all" else "Select") }
            })
    }, bottomBar = {
        if (selecting) Surface(tonalElevation = 3.dp) {
            FlowRow(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { pickerIds = selected.toList() }, enabled = selected.isNotEmpty() && !busy && collections.isNotEmpty(), modifier = Modifier.testTag("inbox_batch_file")) { Text("File ${selected.size} items") }
                OutlinedButton(onClick = { viewModel.delete(selected.toList()) }, enabled = selected.isNotEmpty() && !busy, modifier = Modifier.testTag("inbox_batch_delete")) { Text("Delete ${selected.size}") }
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!selecting && !items.isNullOrEmpty()) {
                Text("Swipe ${if (rtl) "left" else "right"}: ${gestures.startToEnd.label.lowercase()} · ${if (rtl) "right" else "left"}: ${gestures.endToStart.label.lowercase()}",
                    Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = navigator::openSettings, modifier = Modifier.testTag("inbox_customize")) { Text("Customize gestures") }
            }
            error?.let { message ->
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = viewModel::retry, enabled = !busy, modifier = Modifier.testTag("inbox_retry")) { Text("Retry") }
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            pending.forEach { (operation, entries) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${entries.size} deleted · Undo available briefly", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { viewModel.undoDeletion(operation) }, enabled = !busy, modifier = Modifier.testTag("inbox_undo_$operation")) { Text("Restore") }
                }
            }
            val list = items
            when {
                list == null -> Unit
                list.isEmpty() -> EmptyState(Icons.Default.CheckCircle, "Inbox zero", "Anything you capture without a clear home lands here until you file it.", modifier = Modifier.weight(1f))
                else -> LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("inbox_list"), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(list, key = { it.item.id }) { entry ->
                        val id = entry.item.id
                        val suggestions = InboxSuggestions.rank(entry, collections, recent)
                        val choose = { pickerIds = listOf(id) }
                        val file: (Long) -> Unit = { viewModel.file(listOf(id), it) }
                        val delete = { viewModel.delete(listOf(id)) }
                        val action: (InboxSwipeAction) -> Unit = { swipe -> when (swipe) {
                            InboxSwipeAction.FILE_SUGGESTION -> suggestions.firstOrNull()?.takeIf { it.inferred }?.let { file(it.collection.id) } ?: choose()
                            InboxSwipeAction.CHOOSE_COLLECTION -> choose()
                            InboxSwipeAction.DELETE -> delete()
                            InboxSwipeAction.NONE -> Unit
                        } }
                        SwipeInboxCard(entry, suggestions, gestures, selecting, selected.contains(id), busy,
                            onOpen = { navigator.openItem(id) }, onSelect = { viewModel.toggle(id) }, onChoose = choose, onFile = file, onDelete = delete, onSwipe = action)
                    }
                }
            }
        }
    }
    pickerIds?.let { ids -> CollectionPickerDialog(collections, onDismiss = { pickerIds = null }, onPick = { destination -> pickerIds = null; viewModel.file(ids, destination) }) }
}

@Composable
private fun SwipeInboxCard(entry: ItemWithTags, suggestions: List<InboxSuggestion>, gestures: InboxGestures,
    selecting: Boolean, selected: Boolean, busy: Boolean, onOpen: () -> Unit, onSelect: () -> Unit,
    onChoose: () -> Unit, onFile: (Long) -> Unit, onDelete: () -> Unit, onSwipe: (InboxSwipeAction) -> Unit) {
    val state = rememberSwipeToDismissBoxState(positionalThreshold = { it * .35f })
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var width by remember { mutableIntStateOf(0) }
    val latestSwipe by rememberUpdatedState(onSwipe)
    val latestGestures by rememberUpdatedState(gestures)
    LaunchedEffect(state, width) {
        var sent = false
        snapshotFlow { runCatching { state.requireOffset() }.getOrDefault(0f) }.collect { offset ->
            if (abs(offset) < 1) sent = false
            else if (!sent && width > 0 && abs(offset) >= width * .35f) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); sent = true }
        }
    }
    fun label(action: InboxSwipeAction) = when (action) {
        InboxSwipeAction.FILE_SUGGESTION -> suggestions.firstOrNull()?.takeIf { it.inferred }?.let { "File to ${it.collection.name}" } ?: "Choose collection"
        else -> action.label
    }
    SwipeToDismissBox(state = state, modifier = Modifier.onSizeChanged { width = it.width }.testTag("inbox_swipe_${entry.item.id}"),
        enableDismissFromStartToEnd = !selecting && !busy && gestures.startToEnd != InboxSwipeAction.NONE,
        enableDismissFromEndToStart = !selecting && !busy && gestures.endToStart != InboxSwipeAction.NONE,
        gesturesEnabled = !selecting && !busy,
        onDismiss = { direction ->
            if (!busy && !selecting) latestSwipe(if (direction == SwipeToDismissBoxValue.StartToEnd) latestGestures.startToEnd else latestGestures.endToStart)
            scope.launch { state.reset() }
        }, backgroundContent = {
            val fromStart = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val action = if (fromStart) gestures.startToEnd else gestures.endToStart
            Surface(color = if (action == InboxSwipeAction.DELETE) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxSize()) {
                Box(Modifier.padding(16.dp), contentAlignment = if (fromStart) Alignment.CenterStart else Alignment.CenterEnd) { Text(label(action), style = MaterialTheme.typography.labelLarge) }
            }
        }) {
        Card(Modifier.fillMaxWidth().combinedClickable(enabled = !busy, onClick = { if (selecting) onSelect() else onOpen() }, onLongClick = onSelect, onLongClickLabel = "Select item")
            .semantics {
                customActions = listOf(CustomAccessibilityAction("Choose collection") { onChoose(); true }, CustomAccessibilityAction("Delete with Undo") { onDelete(); true }, CustomAccessibilityAction("Select item") { onSelect(); true })
            }.testTag("inbox_card_${entry.item.id}")) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selecting) Checkbox(selected, onCheckedChange = { onSelect() }, enabled = !busy, modifier = Modifier.testTag("inbox_check_${entry.item.id}"))
                    Text(entry.item.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Text(relativeTime(entry.item.createdAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TagLabels(entry.tags)
                if (!selecting) {
                    if (suggestions.isEmpty()) Text("Create a collection first to file this item.", style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { suggestion -> AssistChip(onClick = { onFile(suggestion.collection.id) }, enabled = !busy,
                            label = { Column { Text(suggestion.collection.name); Text(suggestion.reason, style = MaterialTheme.typography.labelSmall) } }, modifier = Modifier.testTag("inbox_file_${entry.item.id}_${suggestion.collection.id}")) }
                        if (suggestions.isNotEmpty()) AssistChip(onClick = onChoose, enabled = !busy, label = { Text("Other…") }, modifier = Modifier.testTag("inbox_other_${entry.item.id}"))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onOpen, enabled = !busy) { Text("Open") }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onDelete, enabled = !busy, modifier = Modifier.testTag("inbox_delete_${entry.item.id}")) { Icon(Icons.Default.Delete, "Delete ${entry.item.title} with Undo") }
                    }
                }
            }
        }
    }
}
