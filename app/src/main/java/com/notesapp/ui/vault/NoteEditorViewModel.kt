package com.notesapp.ui.vault

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.notesapp.data.repository.NoteRepository
import com.notesapp.domain.model.NoteWithTags
import com.notesapp.ui.navigation.NoteEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class NoteDraft(
    val title: String,
    val content: String,
    val tags: List<String>,
    val pinned: Boolean,
) {
    val isBlank: Boolean get() = title.isBlank() && content.isBlank()

    /** Tags compare as a set, ignoring case and order. */
    private val key: List<Any> get() = listOf(title.trim(), content, tags.map { it.lowercase() }.toSet(), pinned)

    fun sameAs(other: NoteDraft?): Boolean = other != null && key == other.key
}

/** The note being edited once it has been read; [note] is null when creating a new one. */
data class NoteEditorLoad(val note: NoteWithTags?)

@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val noteRepository: NoteRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<NoteEditorRoute>()

    /** Id of the note being edited; set after the first save of a brand-new note so later saves update it. */
    private var noteId: Long? = savedStateHandle[SAVED_NOTE_ID] ?: route.noteId
    private var lastSaved: NoteDraft? = null
    private var deleted = false
    private val lock = Mutex()

    private val _loaded = MutableStateFlow<NoteEditorLoad?>(null)
    val loaded: StateFlow<NoteEditorLoad?> = _loaded.asStateFlow()

    val isNew: Boolean get() = route.noteId == null

    val knownTags: StateFlow<List<String>> = noteRepository.observeTagUsage()
        .map { usage -> usage.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val note = noteId?.let { noteRepository.observe(it).first() }
            lastSaved = note?.let { NoteDraft(it.note.title, it.note.content, it.tags, it.note.isPinned) }
            _loaded.value = NoteEditorLoad(note)
        }
    }

    /**
     * Saves [draft]. A new note is only created once it has content, and an existing note that was emptied is
     * deleted. Safe to call repeatedly (leaving the screen and stopping the app both save).
     */
    suspend fun save(draft: NoteDraft) {
        lock.withLock {
            withContext(NonCancellable) {
                if (deleted || draft.sameAs(lastSaved)) return@withContext
                val id = noteId
                when {
                    id == null && draft.isBlank -> return@withContext
                    id == null -> noteId = noteRepository.create(draft.title, draft.content, draft.tags, draft.pinned)
                    draft.isBlank -> {
                        noteRepository.delete(id)
                        noteId = null
                    }
                    else -> {
                        noteRepository.saveDetails(id, draft.title, draft.content, draft.tags)
                        noteRepository.setPinned(id, draft.pinned)
                    }
                }
                savedStateHandle[SAVED_NOTE_ID] = noteId
                lastSaved = draft
            }
        }
    }

    suspend fun delete() {
        lock.withLock {
            withContext(NonCancellable) {
                noteId?.let { noteRepository.delete(it) }
                noteId = null
                deleted = true
            }
        }
    }

    private companion object {
        const val SAVED_NOTE_ID = "saved_note_id"
    }
}
