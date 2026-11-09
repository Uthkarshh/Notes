package com.notesapp.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.notesapp.data.repository.ItemRepository
import com.notesapp.ui.navigation.AppNavigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class InboxBadgeViewModel @Inject constructor(items: ItemRepository) : ViewModel() {
    val count: StateFlow<Int> = items.observeInboxCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}

/** The global utilities of the top bar: full-text search, Smart Inbox (with badge) and settings. */
@Composable
fun AppTopBarActions(navigator: AppNavigator) {
    val badge: InboxBadgeViewModel = hiltViewModel()
    val inboxCount by badge.count.collectAsStateWithLifecycle()

    IconButton(onClick = navigator::openSearch, modifier = Modifier.testTag("action_search")) {
        Icon(Icons.Filled.Search, contentDescription = "Search")
    }
    IconButton(onClick = navigator::openInbox, modifier = Modifier.testTag("action_inbox")) {
        BadgedBox(
            badge = {
                if (inboxCount > 0) Badge { Text(if (inboxCount > 99) "99+" else inboxCount.toString()) }
            },
        ) {
            Icon(Icons.Filled.Inbox, contentDescription = "Inbox")
        }
    }
    IconButton(onClick = navigator::openSettings, modifier = Modifier.testTag("action_settings")) {
        Icon(Icons.Filled.Settings, contentDescription = "Settings")
    }
}
