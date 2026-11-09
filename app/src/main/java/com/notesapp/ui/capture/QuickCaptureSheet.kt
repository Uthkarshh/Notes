package com.notesapp.ui.capture

import com.notesapp.ui.motion.AppAlertDialog as AlertDialog
import com.notesapp.ui.motion.AppModalBottomSheet as ModalBottomSheet
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.notesapp.data.db.entity.CollectionEntity
import com.notesapp.data.repository.CollectionRepository
import com.notesapp.data.repository.ItemRepository
import com.notesapp.domain.capture.*
import com.notesapp.domain.model.Priority
import com.notesapp.domain.usecase.CaptureResult
import com.notesapp.domain.usecase.SaveQuickCapture
import com.notesapp.ui.item.ItemActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val AUTO = -1L
private const val INBOX = 0L

@HiltViewModel
class QuickCaptureViewModel @Inject constructor(
    collectionRepository: CollectionRepository,
    private val saveQuickCapture: SaveQuickCapture,
    private val items: ItemRepository,
    private val actions: ItemActions,
    private val handle: SavedStateHandle,
) : ViewModel() {
    var collectionsReady by mutableStateOf(false)
        private set
    val collections = collectionRepository.observeAll().onEach { collectionsReady = true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val tags = items.observeTagUsage().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    var input by mutableStateOf(TextFieldValue(handle.get<String>("raw").orEmpty(), TextRange(handle.get<Int>("anchor") ?: 0, handle.get<Int>("cursor") ?: 0)))
        private set
    var choice by mutableLongStateOf(handle.get<Long>("choice") ?: AUTO)
        private set
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    fun edit(value: TextFieldValue) {
        input = value
        handle["raw"] = value.text
        handle["cursor"] = value.selection.end
        handle["anchor"] = value.selection.start
        error = null
    }
    fun edit(value: CaptureEdit) = edit(TextFieldValue(value.text, TextRange(value.cursor)))
    fun choose(id: Long) { choice = id; handle["choice"] = id; error = null }
    fun prepare(initialCollectionId: Long?) { if (input.text.isBlank()) choose(initialCollectionId ?: AUTO) }
    fun clear() { edit(TextFieldValue()); choose(AUTO) }
    suspend fun save(parsed: ParsedCapture, target: CollectionEntity?): CaptureResult? {
        if (saving || parsed.title.isBlank() || !collectionsReady) return null
        saving = true
        return try {
            // The saved UI draft is cleared only after the database transaction commits.
            withContext(NonCancellable) {
                check(choice <= 0 || target != null) { "This collection no longer exists. Choose another destination." }
                val result = saveQuickCapture(parsed, target) ?: return@withContext null
                val snapshot = items.get(result.itemId)
                clear()
                actions.reversible("Saved to ${result.collection?.name ?: "Inbox"}") {
                    items.deleteIfUnchanged(result.itemId, snapshot)
                }
                result
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = if (e is IllegalStateException) e.message ?: "Choose another destination." else "Could not save. Your draft is still here."; null }
        finally { saving = false }
    }
}

/** Same-length styling leaves selection, IME composition and accessibility text untouched. */
@Composable
fun captureTransformation(parsed: ParsedCapture): VisualTransformation {
    val colors = MaterialTheme.colorScheme
    return remember(parsed, colors) { VisualTransformation { raw ->
        TransformedText(buildAnnotatedString {
            append(raw)
            parsed.tokens.filter { it.kind != CaptureTokenKind.UNRESOLVED }.forEach { token ->
                val background = when (token.kind) {
                    CaptureTokenKind.PRIORITY -> colors.tertiaryContainer
                    CaptureTokenKind.TAG -> colors.secondaryContainer
                    else -> colors.primaryContainer
                }
                val foreground = when (token.kind) {
                    CaptureTokenKind.PRIORITY -> colors.onTertiaryContainer
                    CaptureTokenKind.TAG -> colors.onSecondaryContainer
                    else -> colors.onPrimaryContainer
                }
                if (token.end <= raw.length) addStyle(SpanStyle(color = foreground, background = background, fontWeight = FontWeight.Medium), token.start, token.end)
            }
        }, OffsetMapping.Identity)
    } }
}

@Composable
fun QuickCaptureSheet(
    onDismiss: () -> Unit,
    onSaved: (CaptureResult) -> Unit,
    modifier: Modifier = Modifier,
    initialCollectionId: Long? = null,
    viewModel: QuickCaptureViewModel = hiltViewModel(),
) {
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val knownTags by viewModel.tags.collectAsStateWithLifecycle()
    val motion = com.notesapp.ui.motion.LocalAppMotion.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var picker by remember { mutableStateOf(false) }
    var priorityMenu by remember { mutableStateOf(false) }
    var tagMenu by remember { mutableStateOf(false) }
    var newTag by remember { mutableStateOf("") }
    val input = viewModel.input
    val parsed = remember(input.text, collections) { QuickCaptureParser.parse(input.text, collections) }
    val target = when (viewModel.choice) {
        AUTO -> QuickCaptureParser.resolveCollection(parsed, collections)
        INBOX -> null
        else -> collections.firstOrNull { it.id == viewModel.choice }
    }
    fun insert(value: String) { viewModel.edit(CaptureEdits.insert(viewModel.input.text, viewModel.input.selection.start, viewModel.input.selection.end, value)) }
    fun remove(tokens: List<CaptureToken>) { viewModel.edit(CaptureEdits.remove(input.text, input.selection.end, tokens)) }
    fun close(after: () -> Unit = {}) {
        keyboard?.hide()
        focusManager.clearFocus()
        scope.launch { if (motion.enabled) sheetState.hide(); after(); onDismiss() }
    }
    fun save() {
        if (parsed.title.isBlank() || viewModel.saving) return
        scope.launch {
            val result = viewModel.save(parsed, target)
            if (result != null) close { onSaved(result) }
        }
    }
    LaunchedEffect(Unit) { viewModel.prepare(initialCollectionId); focus.requestFocus() }
    ModalBottomSheet(onDismissRequest = { if (!viewModel.saving) close() }, sheetState = sheetState,
        sheetMaxWidth = 640.dp, modifier = modifier, dragHandle = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BottomSheetDefaults.DragHandle()
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
                    Icon(androidx.compose.material.icons.Icons.Default.Add, null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(12.dp).size(24.dp))
                }
            }
        }) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp).padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Only the body scrolls; the controls and save action stay above the IME.
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Quick capture", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = viewModel::clear, enabled = !viewModel.saving, modifier = Modifier.testTag("capture_clear")) { Text("Clear draft") }
                }
                OutlinedTextField(value = input, onValueChange = viewModel::edit, label = { Text("Capture something") },
                    placeholder = { Text("Watch Arrival #scifi !high") }, singleLine = true,
                    enabled = !viewModel.saving, visualTransformation = captureTransformation(parsed),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("capture_input"))
                Text("Will save to", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(onClick = { picker = true }, label = { Text((target?.name ?: "Inbox") + if (viewModel.choice != AUTO) " · Manual" else "") },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, "Choose collection") }, modifier = Modifier.testTag("capture_collection"))
                    parsed.tags.forEach { tag ->
                        InputChip(selected = true, onClick = { remove(parsed.tokens.filter { it.kind == CaptureTokenKind.TAG && com.notesapp.domain.model.TagNames.normalize(it.value)?.equals(tag, true) == true }) },
                            label = { Text("#$tag ×") }, modifier = Modifier.testTag("capture_tag_$tag"))
                    }
                    parsed.priority?.let { priority -> InputChip(selected = true, onClick = { priorityMenu = true }, label = { Text(priority.label) }, modifier = Modifier.testTag("capture_priority_preview")) }
                }
                if (parsed.title.isNotBlank()) Text("Title: ${parsed.title}", style = MaterialTheme.typography.bodySmall, maxLines = 2, modifier = Modifier.testTag("capture_title_preview"))
                Text(hintSummary(parsed) ?: "Try Watch, Read, Visit or Buy, #tags and !high.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("capture_hints"))
                if (parsed.unresolvedCollection) Text("Collection not found. Choose a collection; the unmatched @text stays in your title.", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("capture_unresolved"))
                if (viewModel.collectionsReady && viewModel.choice > 0 && target == null) Text("This collection no longer exists. Choose another destination.", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("capture_missing_collection"))
                if (input.text.isNotBlank() && parsed.title.isBlank()) Text("Add a title or link to save.", color = MaterialTheme.colorScheme.error)
                viewModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("capture_error")) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { picker = true }, modifier = Modifier.weight(1f).testTag("capture_suggest_collection")) { Text("Collection", maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                TextButton(onClick = { tagMenu = true }, modifier = Modifier.weight(1f).testTag("capture_suggest_tag")) { Text("# Tag") }
                Box(Modifier.weight(1f)) {
                    TextButton(onClick = { priorityMenu = true }, modifier = Modifier.testTag("capture_suggest_priority")) { Text("! Priority", maxLines = 1) }
                    DropdownMenu(priorityMenu, { priorityMenu = false }) {
                        Priority.entries.forEach { priority -> DropdownMenuItem(text = { Text(priority.label) }, onClick = {
                            viewModel.edit(CaptureEdits.priority(input.text, input.selection.start, input.selection.end, priority)); priorityMenu = false
                        }, modifier = Modifier.testTag("capture_priority_${priority.name.lowercase()}")) }
                    }
                }
                VoiceCaptureButton(onTranscript = ::insert, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { close() }, enabled = !viewModel.saving) { Text("Cancel") }
                Button(onClick = ::save, enabled = parsed.title.isNotBlank() && viewModel.collectionsReady && !viewModel.saving && (viewModel.choice <= 0 || target != null), modifier = Modifier.testTag("capture_save")) {
                    Text(if (viewModel.saving) "Saving…" else "Save to ${target?.name ?: "Inbox"}")
                }
            }
        }
    }
    if (picker) CollectionPickerDialog(collections, onDismiss = { picker = false }, onPick = { viewModel.choose(it); picker = false }, capture = true)
    if (tagMenu) AlertDialog(onDismissRequest = { tagMenu = false }, title = { Text("Add a tag") }, text = {
        Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
            OutlinedTextField(newTag, { newTag = it }, label = { Text("Tag name") }, singleLine = true, modifier = Modifier.testTag("capture_new_tag"))
            knownTags.filter { it.name.contains(newTag, true) }.take(8).forEach { tag ->
                TextButton(onClick = { insert("#${tag.name}"); tagMenu = false }) { Text("#${tag.name}") }
            }
        }
    }, confirmButton = { TextButton(onClick = { insert("#${newTag.trim()}"); tagMenu = false; newTag = "" },
        enabled = newTag.trim().matches(Regex("""[\p{L}\p{N}_-]+""")), modifier = Modifier.testTag("capture_add_tag")) { Text("Add tag") } }, dismissButton = { TextButton(onClick = { tagMenu = false }) { Text("Cancel") } })
}

@Composable
fun CollectionPickerDialog(collections: List<CollectionEntity>, onDismiss: () -> Unit, onPick: (Long) -> Unit, capture: Boolean = false) {
    var query by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Choose collection") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(query, { query = it }, label = { Text("Search collections") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("collection_picker_search"))
            Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                if (capture) {
                    TextButton(onClick = { onPick(AUTO) }, modifier = Modifier.testTag("capture_choice_auto")) { Text("Auto-detect") }
                    TextButton(onClick = { onPick(INBOX) }, modifier = Modifier.testTag("capture_choice_inbox")) { Text("Inbox (sort it later)") }
                }
                collections.filter { it.name.contains(query, true) }.forEach { collection ->
                    TextButton(onClick = { onPick(collection.id) }, modifier = Modifier.fillMaxWidth().testTag(if (capture) "capture_choice_${collection.id}" else "inbox_choice_${collection.id}")) { Text(collection.name) }
                }
                if (collections.isEmpty()) Text("Create a collection to file captures.")
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

internal fun hintSummary(parsed: ParsedCapture): String? = buildList {
    if (parsed.tags.isNotEmpty()) add(parsed.tags.joinToString(" ") { "#$it" })
    parsed.priority?.takeIf { it != Priority.NONE }?.let { add("${it.label} priority") }
    if (parsed.url != null) add("link attached")
}.takeIf { it.isNotEmpty() }?.joinToString(" · ")
