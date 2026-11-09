package com.notesapp.ui.share

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.notesapp.data.repository.*
import com.notesapp.domain.capture.QuickCaptureParser
import com.notesapp.domain.usecase.SaveQuickCapture
import com.notesapp.ui.capture.hintSummary
import com.notesapp.ui.components.DropdownField
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class ShareKind(val label: String) { ITEM("Collection item"), NOTE("Note"), FACT("Fact") }
data class ShareSaveState(val saving: Boolean = false, val done: Boolean = false, val error: String? = null)

@HiltViewModel
class ShareCaptureViewModel @Inject constructor(
    collections: CollectionRepository,
    private val capture: SaveQuickCapture,
    private val notes: NoteRepository,
    private val facts: FactRepository,
) : ViewModel() {
    val collections = collections.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _state = MutableStateFlow(ShareSaveState())
    val state = _state.asStateFlow()
    fun save(text: String, kind: ShareKind, choice: Long) {
        if (text.isBlank() || _state.value.saving) return
        _state.value = ShareSaveState(saving = true)
        viewModelScope.launch {
            try {
                withContext(NonCancellable) {
                    val parsed = QuickCaptureParser.parse(text, collections.value)
                    when (kind) {
                        ShareKind.ITEM -> {
                            val collection = when (choice) { -1L -> QuickCaptureParser.resolveCollection(parsed, collections.value); 0L -> null; else -> checkNotNull(collections.value.firstOrNull { it.id == choice }) { "Choose another collection." } }
                            checkNotNull(capture(parsed, collection))
                        }
                        ShareKind.NOTE -> notes.create(text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(120), text.trim(), parsed.tags)
                        ShareKind.FACT -> facts.create(text.trim(), source = parsed.url, tags = parsed.tags)
                    }
                }
                _state.value = ShareSaveState(done = true)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.value = ShareSaveState(error = "Could not save this capture. Please try again.") }
        }
    }
}

@Composable
fun ShareCaptureScreen(initialText: String, onClose: () -> Unit, viewModel: ShareCaptureViewModel = hiltViewModel()) {
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf(initialText) }
    var kind by rememberSaveable { mutableStateOf(ShareKind.ITEM) }
    var choice by rememberSaveable { mutableLongStateOf(-1L) }
    val parsed = remember(text, collections) { QuickCaptureParser.parse(text, collections) }
    val detected = QuickCaptureParser.resolveCollection(parsed, collections)
    LaunchedEffect(state.done) { if (state.done) onClose() }
    Scaffold(topBar = { TopAppBar(title = { Text("Save to Notes App") }, navigationIcon = { IconButton(onClick = onClose, enabled = !state.saving, modifier = Modifier.testTag("share_cancel")) { Icon(Icons.Filled.Close, "Cancel") } },
        actions = { TextButton(onClick = { viewModel.save(text, kind, choice) }, enabled = text.isNotBlank() && !state.saving, modifier = Modifier.testTag("share_save")) { Text("Save") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp).testTag("share_capture"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DropdownField(label = "Save as", selectedLabel = kind.label, options = ShareKind.entries, optionLabel = { it.label }, onSelect = { kind = it }, modifier = Modifier.fillMaxWidth().testTag("share_kind"))
            OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Shared text or link") }, minLines = 5,
                visualTransformation = if (kind == ShareKind.ITEM) com.notesapp.ui.capture.captureTransformation(parsed) else androidx.compose.ui.text.input.VisualTransformation.None,
                modifier = Modifier.fillMaxWidth().testTag("share_text"))
            if (kind == ShareKind.ITEM) DropdownField(label = "Collection", selectedLabel = when (choice) { -1L -> "Auto · ${detected?.name ?: "Inbox"}"; 0L -> "Inbox"; else -> collections.firstOrNull { it.id == choice }?.name ?: "Inbox" },
                options = listOf(-1L, 0L) + collections.map { it.id }, optionLabel = { id -> when (id) { -1L -> "Auto-detect"; 0L -> "Inbox"; else -> collections.first { it.id == id }.name } }, onSelect = { choice = it }, modifier = Modifier.fillMaxWidth().testTag("share_collection"))
            hintSummary(parsed)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (parsed.unresolvedCollection) Text("Collection not found. Choose a collection or keep this text in Inbox.", color = MaterialTheme.colorScheme.error)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}
