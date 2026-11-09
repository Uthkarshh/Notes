package com.notesapp.ui.settings

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notesapp.data.prefs.*
import com.notesapp.data.security.PrivateSecrets
import com.notesapp.security.*
import com.notesapp.ui.navigation.AppNavigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AdvancedSettingsViewModel @Inject constructor(private val prefs: FeaturePreferences, private val secrets: PrivateSecrets, val session: LockSession, @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context) : ViewModel() {
    val settings = prefs.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FeatureSettings())
    val message = MutableStateFlow<String?>(null)
    val tokenSaved = MutableStateFlow(false)
    init { viewModelScope.launch(Dispatchers.IO) { tokenSaved.value = runCatching { secrets.get("tmdb_token") != null }.getOrDefault(false) } }
    fun lookup(enabled: Boolean) { viewModelScope.launch { prefs.update { it.copy(lookupEnabled = enabled) } } }
    fun lock(enabled: Boolean) { viewModelScope.launch {
        if (enabled) context.getSystemService(android.app.NotificationManager::class.java).cancelAll()
        session.unlock(); prefs.update { it.copy(lockEnabled = enabled) }
    } }
    fun token(value: String) { viewModelScope.launch {
        try { withContext(Dispatchers.IO) { secrets.put("tmdb_token", value.trim().ifEmpty { null }) }; tokenSaved.value = value.isNotBlank(); message.value = if (value.isBlank()) "TMDB token removed." else "TMDB token stored securely." }
        catch (_: Exception) { message.value = "Could not save the token. Try again." }
    } }
}

fun Context.protectedActivity(): ProtectedActivity? {
    var current = this
    while (current is ContextWrapper) { if (current is ProtectedActivity) return current; current = current.baseContext }
    return current as? ProtectedActivity
}

@Composable
fun AdvancedSettingsScreen(navigator: AppNavigator, viewModel: AdvancedSettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val saved by viewModel.tokenSaved.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var token by remember { mutableStateOf("") }
    var localMessage by remember { mutableStateOf<String?>(null) }
    Scaffold(topBar = { TopAppBar(title = { Text("Privacy & metadata") }, navigationIcon = { IconButton(onClick = navigator::back, modifier = Modifier.testTag("back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp).testTag("advanced_settings"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("App lock", style = MaterialTheme.typography.titleLarge)
            com.notesapp.ui.components.LabeledSwitchRow("Require biometrics or device PIN", settings.lockEnabled, onCheckedChange = { enabled ->
                    val activity = context.protectedActivity()
                    if (activity == null || !activity.canAuthenticate()) localMessage = "Set up a device PIN, password, or biometrics in Android first."
                    else activity.authenticate { authenticated -> if (authenticated) viewModel.lock(enabled) else localMessage = "Authentication cancelled. App lock was not changed." }
                }, modifier = Modifier.testTag("app_lock_toggle"))
            Text("Locks after leaving the app. Capture entry points require unlock too. Screenshots are blocked, notifications hide content, and the fact widget hides saved facts while this setting is on.")
            if (settings.lockEnabled) OutlinedButton(onClick = viewModel.session::lock, modifier = Modifier.testTag("lock_now")) { Text("Lock now") }
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }) { Text("Android security settings") }
            localMessage?.let { Text(it, modifier = Modifier.testTag("security_message")) }
            HorizontalDivider()
            Text("Online metadata", style = MaterialTheme.typography.titleLarge)
            com.notesapp.ui.components.LabeledSwitchRow("Enable title lookup", settings.lookupEnabled, viewModel::lookup, Modifier.testTag("lookup_enabled"))
            Text("When you press Search on an item, its query is sent to Open Library for books or TMDB for movies. Covers download only when selected. All manual editing and saved images work offline.")
            Text(if (saved) "A TMDB token is configured." else "Book lookup needs no account. Movie lookup needs your TMDB API Read Access Token.")
            OutlinedTextField(value = token, onValueChange = { token = it }, visualTransformation = PasswordVisualTransformation(), label = { Text("TMDB API Read Access Token") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("tmdb_token"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { viewModel.token(token); token = "" }, enabled = token.isNotBlank(), modifier = Modifier.testTag("tmdb_save")) { Text("Save token") }; TextButton(onClick = { viewModel.token("") }) { Text("Remove token") } }
            message?.let { Text(it) }
            Text("Book data: Open Library. Movie data: TMDB. This product uses the TMDB API but is not endorsed or certified by TMDB.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
