package com.notesapp.ui.collections

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.notesapp.data.repository.CollectionRepository
import com.notesapp.domain.model.CategoryType
import com.notesapp.domain.model.StatusDef
import com.notesapp.domain.model.StatusRole
import com.notesapp.domain.model.StatusSchema
import com.notesapp.domain.template.CollectionTemplates
import com.notesapp.ui.components.CollectionColorChoices
import com.notesapp.ui.navigation.CollectionEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** A status row in the editor. [key] is null for statuses added in this session; it is assigned on save. */
data class EditableStatus(
    val id: Int,
    val key: String?,
    val label: String,
    val role: StatusRole,
)

data class CollectionDraft(
    val name: String = "",
    val categoryType: CategoryType = CategoryType.CUSTOM,
    val iconName: String = "folder",
    val colorHex: String = CollectionColorChoices.first(),
    val statuses: List<EditableStatus> = emptyList(),
) {
    val nameProblem: String? get() = if (name.isBlank()) "Give the collection a name" else null

    /** Problems with the status list, in the order they should be shown. */
    val statusProblems: List<String>
        get() = buildList {
            if (statuses.isEmpty()) add("Add at least one status")
            if (statuses.any { it.label.isBlank() }) add("Every status needs a name")
            val labels = statuses.map { it.label.trim().lowercase() }.filter { it.isNotEmpty() }
            if (labels.size != labels.toSet().size) add("Status names must be different")
            if (statuses.isNotEmpty() && statuses.none { it.role == StatusRole.BACKLOG }) {
                add("Include a “Not started” status so new items have a starting point")
            }
        }

    val isValid: Boolean get() = nameProblem == null && statusProblems.isEmpty()
}

data class CollectionEditorUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val draft: CollectionDraft = CollectionDraft(),
    val original: CollectionDraft = CollectionDraft(),
    val itemCount: Int = 0,
) {
    val dirty: Boolean get() = draft != original
}

@HiltViewModel
class CollectionEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val collectionRepository: CollectionRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<CollectionEditorRoute>()
    private var nextStatusId = 0

    private val _uiState = MutableStateFlow(CollectionEditorUiState())
    val uiState: StateFlow<CollectionEditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val existing = route.collectionId?.let { collectionRepository.get(it) }
        val template = route.templateId?.let(CollectionTemplates::byId)
        val draft = when {
            existing != null -> CollectionDraft(
                name = existing.name,
                categoryType = existing.categoryType,
                iconName = existing.iconName,
                colorHex = existing.colorHex,
                statuses = existing.schema.statuses.map(::editable),
            )
            template != null -> CollectionDraft(
                name = template.name,
                categoryType = template.categoryType,
                iconName = template.iconName,
                colorHex = template.colorHex,
                statuses = template.schema.statuses.map(::editable),
            )
            else -> CollectionDraft(statuses = StatusSchema.DEFAULT.statuses.map(::editable))
        }
        _uiState.value = CollectionEditorUiState(
            loading = false,
            isNew = existing == null,
            draft = draft,
            original = draft,
            itemCount = existing?.let { collectionRepository.itemCount(it.id) } ?: 0,
        )
    }

    private fun editable(def: StatusDef) = EditableStatus(nextStatusId++, def.key, def.label, def.role)

    private fun edit(block: (CollectionDraft) -> CollectionDraft) = _uiState.update { it.copy(draft = block(it.draft)) }

    fun setName(name: String) = edit { it.copy(name = name) }

    fun setCategory(type: CategoryType) = edit { it.copy(categoryType = type) }

    fun setIcon(icon: String) = edit { it.copy(iconName = icon) }

    fun setColor(hex: String) = edit { it.copy(colorHex = hex) }

    fun setStatusLabel(id: Int, label: String) = editStatus(id) { it.copy(label = label) }

    fun setStatusRole(id: Int, role: StatusRole) = editStatus(id) { it.copy(role = role) }

    fun addStatus() = edit {
        it.copy(statuses = it.statuses + EditableStatus(nextStatusId++, null, "", StatusRole.ACTIVE))
    }

    fun removeStatus(id: Int) = edit { draft ->
        if (draft.statuses.size <= 1) draft else draft.copy(statuses = draft.statuses.filterNot { it.id == id })
    }

    fun moveStatus(id: Int, delta: Int) = edit { draft ->
        val from = draft.statuses.indexOfFirst { it.id == id }
        val to = from + delta
        if (from < 0 || to !in draft.statuses.indices) {
            draft
        } else {
            draft.copy(statuses = draft.statuses.toMutableList().apply { add(to, removeAt(from)) })
        }
    }

    private fun editStatus(id: Int, block: (EditableStatus) -> EditableStatus) = edit { draft ->
        draft.copy(statuses = draft.statuses.map { if (it.id == id) block(it) else it })
    }

    /** Creates or updates the collection. Returns false when the draft is invalid. */
    suspend fun save(): Boolean {
        val draft = _uiState.value.draft
        if (!draft.isValid) return false

        val taken = draft.statuses.mapNotNull { it.key }.toMutableSet()
        val statuses = draft.statuses.map { editable ->
            val key = editable.key ?: StatusSchema.uniqueKey(StatusSchema.keyFor(editable.label), taken).also { taken += it }
            StatusDef(key, editable.label.trim(), editable.role)
        }
        val schema = StatusSchema(statuses)

        withContext(NonCancellable) {
            if (route.collectionId == null) {
                collectionRepository.create(draft.name, draft.categoryType, draft.iconName, draft.colorHex, schema)
            } else {
                collectionRepository.update(
                    route.collectionId,
                    draft.name,
                    draft.categoryType,
                    draft.iconName,
                    draft.colorHex,
                    schema,
                )
            }
        }
        return true
    }

    suspend fun delete(deleteItems: Boolean) {
        val id = route.collectionId ?: return
        withContext(NonCancellable) { collectionRepository.delete(id, deleteItems) }
    }
}
