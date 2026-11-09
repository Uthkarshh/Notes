package com.notesapp.ui.vault

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notesapp.domain.model.MasteryState
import com.notesapp.ui.components.ConfirmDialog
import com.notesapp.ui.components.SectionTitle
import com.notesapp.ui.components.TagInput
import com.notesapp.ui.navigation.AppNavigator
import kotlinx.coroutines.launch

@Composable
fun FactEditorScreen(navigator: AppNavigator, viewModel: FactEditorViewModel = hiltViewModel()) {
    FactEditorContent(navigator, viewModel)
}

/** Route-free content; the caller supplies the navigation-entry-scoped ViewModel. */
@Composable
internal fun FactEditorContent(navigator: AppNavigator, viewModel: FactEditorViewModel) {
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val knownTopics by viewModel.knownTopics.collectAsStateWithLifecycle()
    val knownTags by viewModel.knownTags.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var text by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf("") }
    var topic by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var tags by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var mastery by rememberSaveable { mutableStateOf(MasteryState.NEW) }
    var favorite by rememberSaveable { mutableStateOf(false) }
    var initialized by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(loaded) {
        val current = loaded ?: return@LaunchedEffect
        if (!initialized) {
            current.fact?.let {
                text = it.fact.text
                source = it.fact.source.orEmpty()
                topic = it.fact.topic.orEmpty()
                note = it.fact.personalNote.orEmpty()
                tags = it.tags
                mastery = it.fact.masteryState
                favorite = it.fact.isFavorite
            }
            initialized = true
        }
    }

    val latestDraft by rememberUpdatedState(FactDraft(text, source, topic, note, tags, mastery, favorite))
    val ready by rememberUpdatedState(initialized)

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
                title = { Text(if (viewModel.isNew) "New fact" else "Fact") },
                navigationIcon = {
                    IconButton(onClick = ::leave, modifier = Modifier.testTag("back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!viewModel.isNew) {
                        IconButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("fact_delete")) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete fact")
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
                value = text,
                onValueChange = { text = it },
                label = { Text("Fact") },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().testTag("fact_text_field"),
            )
            OutlinedTextField(
                value = source,
                onValueChange = { source = it },
                label = { Text("Source") },
                placeholder = { Text("Book, URL, article or person") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("fact_source"),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("Topic") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth().testTag("fact_topic"),
                )
                val suggestions = knownTopics.filterNot { it.equals(topic.trim(), ignoreCase = true) }.take(6)
                if (suggestions.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { suggestion ->
                            SuggestionChip(onClick = { topic = suggestion }, label = { Text(suggestion) })
                        }
                    }
                }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Personal commentary") },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().testTag("fact_note"),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Mastery")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    MasteryState.entries.forEachIndexed { index, state ->
                        SegmentedButton(
                            selected = mastery == state,
                            onClick = { mastery = state },
                            shape = SegmentedButtonDefaults.itemShape(index, MasteryState.entries.size),
                            icon = {},
                            modifier = Modifier.testTag("fact_mastery_${state.name.lowercase()}"),
                        ) { Text(state.label, maxLines = 1) }
                    }
                }
                Text(
                    "Learned facts appear less often; hidden facts never appear in the morning rotation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            com.notesapp.ui.components.LabeledSwitchRow("Favorite", favorite, { favorite = it }, Modifier.testTag("fact_favorite_switch"))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 24.dp)) {
                SectionTitle("Tags")
                TagInput(tags = tags, onTagsChange = { tags = it }, suggestions = knownTags)
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
                scope.launch {
                    viewModel.delete()
                    navigator.back()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
