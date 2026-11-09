package com.notesapp.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.notesapp.data.repository.ItemRepository
import com.notesapp.ui.navigation.AppNavigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(items: ItemRepository) : ViewModel() {
    val entries = items.observeHistory().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@Composable
fun HistoryScreen(navigator: AppNavigator, viewModel: HistoryViewModel = hiltViewModel()) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val formatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a").withZone(ZoneId.systemDefault()) }
    Scaffold(topBar = { TopAppBar(title = { Text("Selection history") }, navigationIcon = { IconButton(onClick = navigator::back, modifier = Modifier.testTag("back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("history_list"), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (entries.isEmpty()) item { Text("No picks yet. Draw something from Today or Discover.", Modifier.padding(24.dp)) }
            items(entries, key = { it.selection.id }) { row ->
                ListItem(headlineContent = { Text(row.itemTitle) }, supportingContent = { Column { Text("${row.collectionName ?: "Inbox"} · ${row.selection.actionTaken.name.lowercase().replaceFirstChar(Char::uppercase)}\n${formatter.format(Instant.ofEpochMilli(row.selection.selectedAt))}")
                        row.reflectionExcerpt?.takeIf { it.isNotBlank() }?.let { Text(it, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.testTag("history_reflection_${row.selection.id}")) }
                    } },
                    modifier = Modifier.clickable { navigator.openItem(row.selection.itemId) }.testTag("history_row_${row.selection.id}"))
            }
        }
    }
}
