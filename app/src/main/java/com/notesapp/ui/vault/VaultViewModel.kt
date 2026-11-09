package com.notesapp.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notesapp.data.repository.FactRepository
import com.notesapp.data.repository.NoteRepository
import com.notesapp.domain.facts.FactRotation
import com.notesapp.domain.model.FactWithTags
import com.notesapp.domain.model.MasteryState
import com.notesapp.domain.model.NoteWithTags
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class VaultSegment(val label: String) { NOTES("Notes"), FACTS("Facts") }

/** Mastery chips of the Facts tab. "All" hides hidden facts, which only show up under "Hidden". */
enum class MasteryFilter(val label: String, val state: MasteryState?) {
    ALL("All", null),
    NEW("New", MasteryState.NEW),
    REVIEWING("Reviewing", MasteryState.REVIEWING),
    LEARNED("Learned", MasteryState.LEARNED),
    HIDDEN("Hidden", MasteryState.HIDDEN),
}

data class FactFilters(
    val topic: String? = null,
    val tag: String? = null,
    val mastery: MasteryFilter = MasteryFilter.ALL,
) {
    val isActive: Boolean get() = topic != null || tag != null || mastery != MasteryFilter.ALL
}

data class VaultUiState(
    val segment: VaultSegment = VaultSegment.NOTES,
    val notes: List<NoteWithTags> = emptyList(),
    val totalNotes: Int = 0,
    val noteTags: List<String> = emptyList(),
    val selectedNoteTags: Set<String> = emptySet(),
    val facts: List<FactWithTags> = emptyList(),
    val totalFacts: Int = 0,
    val factTopics: List<String> = emptyList(),
    val factTags: List<String> = emptyList(),
    val factFilters: FactFilters = FactFilters(),
)

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val factRepository: FactRepository,
) : ViewModel() {

    private val segment = MutableStateFlow(VaultSegment.NOTES)
    private val noteTagFilter = MutableStateFlow<Set<String>>(emptySet())
    private val factFilters = MutableStateFlow(FactFilters())
    private val randomFactId = MutableStateFlow<Long?>(null)

    private val noteState = combine(noteRepository.observeAll(), noteTagFilter) { notes, selected ->
        val wanted = selected.map { it.lowercase() }.toSet()
        Triple(
            notes.filter { wanted.isEmpty() || it.tags.any { tag -> tag.lowercase() in wanted } },
            notes.size,
            notes.flatMap { it.tags }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() },
        )
    }

    private val factState = combine(factRepository.observeAll(), factFilters) { facts, filters ->
        val filtered = facts.filter { entry ->
            val fact = entry.fact
            val masteryOk = when (val wanted = filters.mastery.state) {
                null -> fact.masteryState != MasteryState.HIDDEN
                else -> fact.masteryState == wanted
            }
            masteryOk &&
                (filters.topic == null || fact.topic.equals(filters.topic, ignoreCase = true)) &&
                (filters.tag == null || entry.tags.any { it.equals(filters.tag, ignoreCase = true) })
        }
        Triple(filtered, facts.size, facts.flatMap { it.tags }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() })
    }

    val uiState: StateFlow<VaultUiState> = combine(
        segment,
        noteState,
        noteTagFilter,
        factState,
        combine(factRepository.observeTopics(), factFilters) { topics, filters -> topics to filters },
    ) { seg, notes, selectedNoteTags, facts, topicsAndFilters ->
        VaultUiState(
            segment = seg,
            notes = notes.first,
            totalNotes = notes.second,
            noteTags = notes.third,
            selectedNoteTags = selectedNoteTags,
            facts = facts.first,
            totalFacts = facts.second,
            factTopics = topicsAndFilters.first,
            factTags = facts.third,
            factFilters = topicsAndFilters.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VaultUiState())

    /** The fact currently shown by the "draw a fact" dialog, kept live so its buttons reflect changes. */
    val randomFact: StateFlow<FactWithTags?> = randomFactId
        .flatMapLatest { id -> if (id == null) flowOf(null) else factRepository.observe(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectSegment(value: VaultSegment) {
        segment.value = value
    }

    fun toggleNoteTag(tag: String) = noteTagFilter.update { selected ->
        val existing = selected.firstOrNull { it.equals(tag, ignoreCase = true) }
        if (existing != null) selected - existing else selected + tag
    }

    fun selectTopic(topic: String?) = factFilters.update { it.copy(topic = if (it.topic.equals(topic, true)) null else topic) }

    fun selectFactTag(tag: String?) = factFilters.update { it.copy(tag = if (it.tag.equals(tag, true)) null else tag) }

    fun selectMastery(filter: MasteryFilter) = factFilters.update { it.copy(mastery = filter) }

    fun clearFactFilters() {
        factFilters.value = FactFilters()
    }

    fun setNotePinned(note: NoteWithTags, pinned: Boolean) {
        viewModelScope.launch { noteRepository.setPinned(note.note.id, pinned) }
    }

    fun setFactFavorite(fact: FactWithTags, favorite: Boolean) {
        viewModelScope.launch { factRepository.setFavorite(fact.fact.id, favorite) }
    }

    fun setFactMastery(fact: FactWithTags, state: MasteryState) {
        viewModelScope.launch { factRepository.setMastery(fact.fact.id, state) }
    }

    fun deleteFact(fact: FactWithTags) {
        viewModelScope.launch { factRepository.delete(fact.fact.id) }
    }

    /** Draws a random fact from whatever the current filters show, counting it as an impression. */
    fun drawRandomFact() {
        viewModelScope.launch {
            val candidates = uiState.value.facts.map { it.fact }
            val pick = FactRotation.drawRandom(candidates, excludeId = randomFactId.value) ?: return@launch
            factRepository.markShown(pick.id)
            randomFactId.value = pick.id
        }
    }

    fun dismissRandomFact() {
        randomFactId.value = null
    }
}
