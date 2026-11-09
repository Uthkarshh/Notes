package com.notesapp.ui.item

import com.notesapp.ui.motion.AppModalBottomSheet as ModalBottomSheet
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.notesapp.data.images.CoverImages
import com.notesapp.data.metadata.*
import com.notesapp.data.prefs.FeaturePreferences
import com.notesapp.data.security.PrivateSecrets
import com.notesapp.domain.metadata.MetadataFields
import com.notesapp.domain.model.CategoryType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class EnrichmentState(val busy: Boolean = false, val results: List<MetadataResult> = emptyList(),
    val message: String? = null, val movieTokenConfigured: Boolean = false)

@HiltViewModel
class ItemEnrichmentViewModel @Inject constructor(
    private val covers: CoverImages, private val lookup: MetadataSource,
    private val prefs: FeaturePreferences, private val secrets: PrivateSecrets,
) : ViewModel() {
    val features = prefs.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.notesapp.data.prefs.FeatureSettings())
    private val _state = MutableStateFlow(EnrichmentState())
    val state = _state.asStateFlow()
    init { viewModelScope.launch {
        val configured = withContext(Dispatchers.IO) { runCatching { !secrets.get("tmdb_token").isNullOrBlank() }.getOrDefault(false) }
        _state.update { it.copy(movieTokenConfigured = configured) }
    } }
    private fun task(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try { block() } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(message = e.message ?: "This action could not be completed. Manual editing is available offline.") } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
    fun image(uri: Uri, onImage: suspend (String?) -> Unit) = task { onImage(covers.import(uri)) }
    fun removeImage(onImage: suspend (String?) -> Unit) = task { onImage(null) }
    private suspend fun find(query: String, category: CategoryType) {
        val results = lookup.search(query, category)
        _state.update { it.copy(results = results, message = if (results.isEmpty()) "No matching titles found." else null) }
    }
    fun search(query: String, category: CategoryType) = task { find(query, category) }
    fun enableAndSearch(query: String, category: CategoryType, token: String, onConsentSaved: () -> Unit) = task {
        if (category == CategoryType.MOVIE) {
            if (token.isNotBlank()) withContext(Dispatchers.IO) { secrets.put("tmdb_token", token.trim()) }
            val configured = withContext(Dispatchers.IO) { !secrets.get("tmdb_token").isNullOrBlank() }
            require(configured) { "Enter your TMDB API Read Access Token to search movies." }
            _state.update { it.copy(movieTokenConfigured = true) }
        }
        // The explicit persisted opt-in must complete before the first provider request.
        prefs.update { it.copy(lookupEnabled = true) }
        onConsentSaved()
        find(query, category)
    }
    private val _preview = MutableStateFlow<MetadataResult?>(null)
    val preview = _preview.asStateFlow()
    fun preview(result: MetadataResult, category: CategoryType) = task {
        _preview.value = lookup.details(result, category)
    }
    fun closePreview() { if (!_state.value.busy) _preview.value = null }
    fun apply(result: MetadataResult, fields: Map<String, String>, expected: Map<String, String>, includeCover: Boolean,
        expectedImage: String?, onMetadata: suspend (Map<String, String>, Map<String, String>) -> Map<String, String>?,
        onProviderImage: suspend (String?, String?) -> Boolean, onConflict: (Map<String, String>) -> Unit) = task {
        val conflicts = onMetadata(expected, fields)
        if (conflicts != null) {
            onConflict(conflicts)
            _state.update { it.copy(message = "Some details changed while you reviewed. Check the new current values and choose those fields again.") }
            return@task
        }
        var message = "Selected details applied. Your title, notes and workflow are kept."
        if (includeCover && result.coverUrl != null) {
            try {
                val image = lookup.downloadCover(result.coverUrl)
                if (!onProviderImage(image, expectedImage)) message = "Details applied. Your cover changed while downloading, so that newer cover was kept."
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = "Details applied. The cover could not be downloaded; your saved cover is kept." }
        }
        _preview.value = null
        _state.update { it.copy(results = emptyList(), message = message) }
    }

}

@Composable
fun ItemEnrichment(itemId: Long, title: String, imageUri: String?, category: CategoryType?, fields: Map<String, String>,
    onMetadata: suspend (Map<String, String>, Map<String, String>) -> Map<String, String>?, onImage: suspend (String?) -> Unit,
    onProviderImage: suspend (String?, String?) -> Boolean,
    viewModel: ItemEnrichmentViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val features by viewModel.features.collectAsStateWithLifecycle()
    var query by remember(itemId) { mutableStateOf(title) }
    val selected by viewModel.preview.collectAsStateWithLifecycle()
    var expected by remember(itemId) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedKeys by remember(itemId) { mutableStateOf<Set<String>>(emptySet()) }
    var expectedImage by remember(itemId) { mutableStateOf<String?>(null) }
    val currentFields by rememberUpdatedState(fields)
    LaunchedEffect(selected) {
        selected?.let { result ->
            expected = currentFields
            expectedImage = imageUri
            selectedKeys = com.notesapp.domain.metadata.MetadataReview.supplied(result.metadata).keys.filter { currentFields[it].isNullOrBlank() }.toSet()
        }
    }
    var cover by remember { mutableStateOf(false) }
    var consent by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { uri -> viewModel.image(uri, onImage) } }
    OutlinedCard(Modifier.fillMaxWidth().testTag("item_enrichment")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Cover & online details", style = MaterialTheme.typography.titleMedium)
            imageUri?.let { AsyncImage(model = it, contentDescription = "Cover for $title", contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).testTag("item_cover")) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { picker.launch(arrayOf("image/*")) }, enabled = !state.busy, modifier = Modifier.testTag("choose_cover")) { Text("Choose cover") }
                if (imageUri != null) TextButton(onClick = { viewModel.removeImage(onImage) }, enabled = !state.busy) { Text("Remove cover") }
            }
            if (category in setOf(CategoryType.MOVIE, CategoryType.BOOK)) {
                OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Look up a title") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("metadata_query"))
                OutlinedButton(onClick = {
                    if (!features.lookupEnabled || category == CategoryType.MOVIE && !state.movieTokenConfigured) consent = true
                    else viewModel.search(query, category!!)
                }, enabled = !state.busy && query.isNotBlank(), modifier = Modifier.testTag("metadata_lookup")) {
                    Text(if (category == CategoryType.BOOK) "Find book details" else "Find movie details")
                }
                if (!features.lookupEnabled) Text("Online lookup is off. You can enable it here when you search.", style = MaterialTheme.typography.bodySmall)
                state.results.forEach { result ->
                    OutlinedCard(onClick = { cover = false; viewModel.preview(result, category!!) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(result.title)
                            Text(listOfNotNull(result.metadata.author, result.metadata.releaseYear?.toString()).joinToString(" · "))
                        }
                    }
                }
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.message?.let { Text(it, modifier = Modifier.testTag("enrichment_message")) }
        }
    }
    if (consent) ModalBottomSheet(onDismissRequest = { if (!state.busy) { consent = false; token = "" } },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(20.dp).testTag("metadata_consent"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val provider = if (category == CategoryType.BOOK) "Open Library" else "TMDB"
            Text("Find details online?", style = MaterialTheme.typography.headlineSmall)
            Text("Your search text goes to $provider. Your notes stay on this device. Covers download only when selected.")
            Text("Future searches remain yours to start. Turn lookup off anytime in Privacy & metadata.", style = MaterialTheme.typography.bodySmall)
            if (category == CategoryType.MOVIE && !state.movieTokenConfigured) {
                Text("Movie lookup needs a TMDB API Read Access Token.")
                OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text("TMDB API Read Access Token") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth().testTag("context_tmdb_token"))
            }
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            Button(onClick = { viewModel.enableAndSearch(query, category!!, token) { consent = false; token = "" } },
                enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("metadata_enable_search")) { Text("Enable & search") }
            TextButton(onClick = { consent = false; token = "" }, enabled = !state.busy, modifier = Modifier.testTag("metadata_decline")) { Text("Keep editing offline") }
        }
    }
    selected?.let { result ->
        val supplied = com.notesapp.domain.metadata.MetadataReview.supplied(result.metadata)
        ModalBottomSheet(onDismissRequest = viewModel::closePreview, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).testTag("metadata_review")) {
                Text("Review details", style = MaterialTheme.typography.headlineSmall)
                Text(result.title, style = MaterialTheme.typography.titleMedium)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Choose each field to update. Existing values stay unless selected. Title, notes, tags and workflow stay yours.")
                    if (supplied.isEmpty()) Text("This provider returned no supported details.")
                    supplied.forEach { (key, incoming) ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(8.dp)) {
                                Checkbox(key in selectedKeys, { checked -> selectedKeys = if (checked) selectedKeys + key else selectedKeys - key }, enabled = !state.busy,
                                    modifier = Modifier.testTag("review_select_$key").semantics { contentDescription = "Apply ${com.notesapp.domain.metadata.MetadataReview.labels.getValue(key)}" })
                                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                    Text(com.notesapp.domain.metadata.MetadataReview.labels.getValue(key), style = MaterialTheme.typography.titleSmall)
                                    Text("Current: ${expected[key].orEmpty().ifBlank { "Empty" }}", modifier = Modifier.testTag("review_current_$key"))
                                    Text("Provider: $incoming", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                    if (result.coverUrl != null) Row { Checkbox(cover, { cover = it }, enabled = !state.busy, modifier = Modifier.testTag("review_cover").semantics { contentDescription = "Download cover image" }); Text("Download cover image", Modifier.weight(1f).padding(top = 12.dp)) }
                    state.message?.let { Text(it, modifier = Modifier.testTag("review_message")) }
                }
                Button(onClick = { viewModel.apply(result, supplied.filterKeys { it in selectedKeys }, expected, cover, expectedImage, onMetadata, onProviderImage) { latest ->
                    val changed = com.notesapp.domain.metadata.MetadataReview.conflicts(expected, latest, selectedKeys)
                    selectedKeys = selectedKeys - changed
                    expected = latest
                } }, enabled = !state.busy && (selectedKeys.isNotEmpty() || cover), modifier = Modifier.fillMaxWidth().testTag("metadata_apply_selected")) { Text(if (state.busy) "Applying…" else "Apply selected details") }
                TextButton(onClick = viewModel::closePreview, enabled = !state.busy, modifier = Modifier.testTag("metadata_review_cancel")) { Text("Cancel") }
            }
        }
    }
}
