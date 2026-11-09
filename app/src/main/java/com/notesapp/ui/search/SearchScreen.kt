package com.notesapp.ui.search

import com.notesapp.ui.navigation.paneSelection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notesapp.data.repository.SearchHit
import com.notesapp.domain.model.EntityType
import com.notesapp.ui.components.EmptyState
import com.notesapp.ui.navigation.AppNavigator

/** Universal full-text search across items, notes and facts, powered by the FTS4 index. */
@Composable
fun SearchScreen(navigator: AppNavigator, viewModel: SearchViewModel = hiltViewModel()) {
    SearchContent(navigator, viewModel)
}

/** Route-free content; the caller supplies the navigation-entry-scoped ViewModel. */
@Composable
internal fun SearchContent(navigator: AppNavigator, viewModel: SearchViewModel) {
    val results by viewModel.results.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        viewModel.onQueryChange(text)
        focusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = text,
                        onValueChange = {
                            text = it
                            viewModel.onQueryChange(it)
                        },
                        placeholder = { Text("Search items, notes and facts") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).testTag("search_input"),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = navigator::back, modifier = Modifier.testTag(if (com.notesapp.ui.navigation.LocalPaneList.current) "pane_list_back" else "back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (text.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                text = ""
                                viewModel.onQueryChange("")
                            },
                            modifier = Modifier.testTag("search_clear"),
                        ) { Icon(Icons.Filled.Close, contentDescription = "Clear search") }
                    }
                },
            )
        },
    ) { padding ->
        val hits = results.hits
        when {
            text.isBlank() -> EmptyState(
                icon = Icons.Filled.Search,
                title = "Search everything",
                message = "Find items, notes and facts by title, text, source or tag.",
                modifier = Modifier.padding(padding).testTag("search_hint"),
            )
            hits.isEmpty() && results.query == text -> EmptyState(
                icon = Icons.Filled.SearchOff,
                title = "No results",
                message = "Nothing matches “${text.trim()}”.",
                modifier = Modifier.padding(padding).testTag("search_empty"),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("search_results"),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
            ) {
                val grouped = hits.groupBy { it.type }
                listOf(EntityType.ITEM to "Items", EntityType.NOTE to "Notes", EntityType.FACT to "Facts").forEach { (type, title) ->
                    val group = grouped[type].orEmpty()
                    if (group.isNotEmpty()) {
                        item(key = "header_${type.name}") {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                            )
                        }
                        items(group, key = { "${it.type}_${it.id}" }) { hit ->
                            SearchResultRow(
                                hit = hit,
                                onClick = {
                                    when (hit.type) {
                                        EntityType.ITEM -> navigator.openItem(hit.id)
                                        EntityType.NOTE -> navigator.openNote(hit.id)
                                        EntityType.FACT -> navigator.openFact(hit.id)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(hit: SearchHit, onClick: () -> Unit) {
    val highlight = SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    ListItem(
        modifier = Modifier.paneSelection(hit.type.name.lowercase(), hit.id).clickable(onClick = onClick).testTag("search_hit_${hit.type.name.lowercase()}_${hit.id}"),
        leadingContent = {
            Icon(
                imageVector = when (hit.type) {
                    EntityType.ITEM -> Icons.Filled.CollectionsBookmark
                    EntityType.NOTE -> Icons.AutoMirrored.Filled.StickyNote2
                    EntityType.FACT -> Icons.Filled.Lightbulb
                },
                contentDescription = hit.type.name.lowercase(),
            )
        },
        headlineContent = {
            Text(hit.title.ifBlank { "Untitled" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = hit.snippet.takeIf { it.isNotBlank() }?.let { snippet ->
            { Text(highlightSnippet(snippet, highlight), maxLines = 2, overflow = TextOverflow.Ellipsis) }
        },
    )
}
