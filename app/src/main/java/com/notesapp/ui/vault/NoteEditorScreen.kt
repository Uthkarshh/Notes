package com.notesapp.ui.vault

import com.notesapp.ui.navigation.EditorBackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notesapp.ui.components.ConfirmDialog
import com.notesapp.ui.components.TagInput
import com.notesapp.ui.navigation.AppNavigator
import kotlinx.coroutines.launch

@Composable
fun NoteEditorScreen(navigator: AppNavigator, viewModel: NoteEditorViewModel = hiltViewModel()) {
    NoteEditorContent(navigator, viewModel)
}

/** Route-free content; the caller supplies the navigation-entry-scoped ViewModel. */
@Composable
internal fun NoteEditorContent(navigator: AppNavigator, viewModel: NoteEditorViewModel) {
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val knownTags by viewModel.knownTags.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var title by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    var tags by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var pinned by rememberSaveable { mutableStateOf(false) }
    var initialized by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(loaded) {
        val current = loaded ?: return@LaunchedEffect
        if (!initialized) {
            current.note?.let {
                title = it.note.title
                content = it.note.content
                tags = it.tags
                pinned = it.note.isPinned
            }
            initialized = true
        }
    }

    val latestDraft by rememberUpdatedState(NoteDraft(title, content, tags, pinned))
    val ready by rememberUpdatedState(initialized)

    // Never save before the note was loaded: an empty draft would otherwise delete an existing note.
    fun leave() = navigator.back()

    com.notesapp.ui.navigation.EditorNavigationGuard(navigator) { if (ready) viewModel.save(latestDraft); true }
    EditorBackHandler(onBack = ::leave)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        scope.launch { if (ready) viewModel.save(latestDraft) }
    }

    if (!initialized) {
        Box(Modifier.fillMaxSize())
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isNew) "New note" else "Note") },
                navigationIcon = {
                    IconButton(onClick = ::leave, modifier = Modifier.testTag("back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconToggleButton(
                        checked = pinned,
                        onCheckedChange = { pinned = it },
                        modifier = Modifier.testTag("note_pin"),
                    ) {
                        Icon(
                            Icons.Filled.PushPin,
                            contentDescription = if (pinned) "Unpin note" else "Pin note",
                            tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        )
                    }
                    if (!viewModel.isNew) {
                        Box {
                            IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("note_menu")) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        menuOpen = false
                                        confirmDelete = true
                                    },
                                    modifier = Modifier.testTag("note_delete"),
                                )
                            }
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                textStyle = MaterialTheme.typography.titleLarge,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().testTag("note_title"),
            )
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Write something (Markdown is fine)") },
                minLines = 10,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().testTag("note_content"),
            )
            TagInput(tags = tags, onTagsChange = { tags = it }, suggestions = knownTags, modifier = Modifier.padding(bottom = 24.dp))
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this note?",
            text = "This can't be undone.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    viewModel.delete()
                    navigator.back()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
