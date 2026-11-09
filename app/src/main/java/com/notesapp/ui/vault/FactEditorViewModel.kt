package com.notesapp.ui.vault

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.notesapp.data.repository.FactRepository
import com.notesapp.domain.model.FactWithTags
import com.notesapp.domain.model.MasteryState
import com.notesapp.ui.navigation.FactEditorRoute
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

data class FactDraft(
    val text: String,
    val source: String,
    val topic: String,
    val note: String,
    val tags: List<String>,
    val mastery: MasteryState,
    val favorite: Boolean,
) {
    private val key: List<Any> get() =
        listOf(text.trim(), source.trim(), topic.trim(), note.trim(), tags.map { it.lowercase() }.toSet(), mastery, favorite)

    fun sameAs(other: FactDraft?): Boolean = other != null && key == other.key
}

data class FactEditorLoad(val fact: FactWithTags?)

@HiltViewModel
class FactEditorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val factRepository: FactRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<FactEditorRoute>()
    private var factId: Long? = savedStateHandle[SAVED_FACT_ID] ?: route.factId
    private var lastSaved: FactDraft? = null
    private var deleted = false
    private val lock = Mutex()

    private val _loaded = MutableStateFlow<FactEditorLoad?>(null)
    val loaded: StateFlow<FactEditorLoad?> = _loaded.asStateFlow()

    val isNew: Boolean get() = route.factId == null

    val knownTopics: StateFlow<List<String>> = factRepository.observeTopics()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val knownTags: StateFlow<List<String>> = factRepository.observeTagUsage()
        .map { usage -> usage.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val fact = factId?.let { factRepository.observe(it).first() }
            lastSaved = fact?.toDraft()
            _loaded.value = FactEditorLoad(fact)
        }
    }

    /** Saves [draft]; a new fact is created only once it has text, and an existing fact is never blanked out. */
    suspend fun save(draft: FactDraft) {
        lock.withLock {
            withContext(NonCancellable) {
                if (deleted || draft.sameAs(lastSaved) || draft.text.isBlank()) return@withContext
                val id = factId
                if (id == null) {
                    factId = factRepository.create(
                        text = draft.text,
                        source = draft.source,
                        topic = draft.topic,
                        personalNote = draft.note,
                        tags = draft.tags,
                        favorite = draft.favorite,
                        mastery = draft.mastery,
                    )
                } else {
                    factRepository.saveDetails(id, draft.text, draft.source, draft.topic, draft.note, draft.tags)
                    factRepository.setFavorite(id, draft.favorite)
                    factRepository.setMastery(id, draft.mastery)
                }
                savedStateHandle[SAVED_FACT_ID] = factId
                lastSaved = draft
            }
        }
    }

    suspend fun delete() {
        lock.withLock {
            withContext(NonCancellable) {
                factId?.let { factRepository.delete(it) }
                factId = null
                deleted = true
            }
        }
    }

    private fun FactWithTags.toDraft() = FactDraft(
        text = fact.text,
        source = fact.source.orEmpty(),
        topic = fact.topic.orEmpty(),
        note = fact.personalNote.orEmpty(),
        tags = tags,
        mastery = fact.masteryState,
        favorite = fact.isFavorite,
    )

    private companion object {
        const val SAVED_FACT_ID = "saved_fact_id"
    }
}
