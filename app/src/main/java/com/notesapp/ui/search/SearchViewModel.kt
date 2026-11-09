package com.notesapp.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notesapp.data.repository.SearchHit
import com.notesapp.data.repository.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Results for [query]; kept together so the UI can tell fresh results from stale ones. */
data class SearchResults(val query: String = "", val hits: List<SearchHit> = emptyList())

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val results: StateFlow<SearchResults> = query
        .debounce(DEBOUNCE_MS)
        .distinctUntilChanged()
        .mapLatest { text -> SearchResults(text, if (text.isBlank()) emptyList() else searchRepository.search(text)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    fun onQueryChange(text: String) {
        query.value = text
    }

    private companion object {
        const val DEBOUNCE_MS = 150L
    }
}
