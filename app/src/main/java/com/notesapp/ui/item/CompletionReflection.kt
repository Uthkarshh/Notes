package com.notesapp.ui.item

import com.notesapp.ui.motion.AppModalBottomSheet as ModalBottomSheet
import androidx.compose.foundation.layout.*
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
import com.notesapp.data.repository.CompletionRequest
import com.notesapp.data.repository.ItemRepository
import com.notesapp.data.repository.KnowledgeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CompletionReflectionViewModel @Inject constructor(items: ItemRepository, private val knowledge: KnowledgeRepository) : ViewModel() {
    private val _request = MutableStateFlow<CompletionRequest?>(null)
    val request = _request.asStateFlow()
    private val queued = ArrayDeque<CompletionRequest>()
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    init {
        viewModelScope.launch { items.completions.collect { request ->
            if (_request.value?.itemId != request.itemId && queued.none { it.itemId == request.itemId }) {
                if (_request.value == null) _request.value = request else queued.addLast(request)
            }
        } }
        viewModelScope.launch { items.completionCancellations.collect { id -> queued.removeAll { it.itemId == id }; if (_request.value?.itemId == id) advance() } }
    }
    private fun advance() { _error.value = null; _request.value = queued.removeFirstOrNull() }
    fun dismiss() { if (!_saving.value) advance() }
    fun save(text: String) {
        val current = _request.value ?: return
        if (_saving.value || text.isBlank()) return
        _saving.value = true
        viewModelScope.launch {
            try { knowledge.reflect(current.itemId, text); if (_request.value?.itemId == current.itemId) advance() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { _error.value = "Couldn’t save this reflection. Your text is kept here; try again." }
            finally { _saving.value = false }
        }
    }
}

@Composable
fun CompletionReflection(viewModel: CompletionReflectionViewModel = hiltViewModel()) {
    val request by viewModel.request.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    request?.let { current ->
        var text by rememberSaveable(current.itemId) { mutableStateOf("") }
        if (com.notesapp.ui.motion.LocalModalCoordinator.current?.busy != true) ModalBottomSheet(blocksReflection = false, onDismissRequest = viewModel::dismiss, modifier = Modifier.testTag("reflection_sheet")) {
            Column(Modifier.fillMaxWidth().imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("What stood out?", style = MaterialTheme.typography.headlineSmall)
                Text("${current.title} · Would you recommend it?")
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Your reflection") }, minLines = 3, modifier = Modifier.fillMaxWidth().testTag("reflection_input"))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = viewModel::dismiss, enabled = !saving, modifier = Modifier.testTag("reflection_skip")) { Text("Not now") }
                    Button(onClick = { viewModel.save(text) }, enabled = text.isNotBlank() && !saving, modifier = Modifier.testTag("reflection_save")) { Text("Save note") }
                }
            }
        }
    }
}
