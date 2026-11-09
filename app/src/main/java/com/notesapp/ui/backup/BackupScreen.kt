package com.notesapp.ui.backup

import com.notesapp.ui.motion.AppAlertDialog as AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.notesapp.automation.BackupAutomation
import com.notesapp.data.backup.*
import com.notesapp.data.prefs.*
import com.notesapp.data.repository.ImportMode
import com.notesapp.data.security.PrivateSecrets
import com.notesapp.ui.components.ConfirmDialog
import com.notesapp.ui.components.DropdownField
import com.notesapp.ui.navigation.AppNavigator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class BackupUiState(val busy: Boolean = false, val message: String? = null, val preview: String? = null)

@HiltViewModel
class BackupViewModel @Inject constructor(private val backups: EncryptedBackups, private val prefs: FeaturePreferences, private val secrets: PrivateSecrets,
    private val scheduler: BackupAutomation, @ApplicationContext private val context: Context) : ViewModel() {
    val settings = prefs.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FeatureSettings())
    val password = MutableStateFlow("")
    val confirmation = MutableStateFlow("")
    val state = MutableStateFlow(BackupUiState())
    private var pending: PreparedArchive? = null
    private fun task(action: suspend () -> String) {
        if (state.value.busy) return
        state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try { val message = withContext(Dispatchers.IO) { action() }; state.update { it.copy(busy = false, message = message) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { state.update { it.copy(busy = false, message = e.message ?: "Backup operation failed.") } }
        }
    }
    fun validPassword(): Boolean {
        val valid = password.value.length >= 10 && password.value == confirmation.value
        if (!valid) state.update { it.copy(message = "Enter matching passphrases of at least 10 characters.") }
        return valid
    }
    fun export(uri: Uri) = task {
        val pass = password.value.toCharArray()
        try { backups.write(uri, backups.create(pass)); "Encrypted archive saved, including local cover images." } finally { pass.fill('\u0000') }
    }
    fun preview(uri: Uri) = task {
        val pass = password.value.toCharArray()
        try {
            val prepared = backups.preview(backups.read(uri), pass)
            pending = prepared
            state.update { it.copy(preview = "${prepared.backup.recordCount} records and ${prepared.images.size} cover images.") }
            "Archive authenticated and validated."
        } finally { pass.fill('\u0000') }
    }
    fun cancel() { pending = null; state.update { it.copy(preview = null) } }
    fun restore(mode: ImportMode) = task { val count = backups.restore(checkNotNull(pending), mode); pending = null; state.update { it.copy(preview = null) }; "$count records restored." }
    fun folder(uri: Uri) = task {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        prefs.update { it.copy(backupFolder = uri.toString()) }
        "Backup folder selected. Keep its access permission available."
    }
    fun enable(enabled: Boolean) {
        if (enabled && !validPassword()) return
        task {
            if (enabled) {
                require(prefs.settings.first().backupFolder != null) { "Choose a backup folder first." }
                secrets.put("backup_password", password.value)
            }
            prefs.update { it.copy(backupEnabled = enabled) }
            scheduler.schedule()
            if (!enabled) secrets.put("backup_password", null)
            if (enabled) "Automatic encrypted backups enabled. Keep your passphrase separately to restore on another device." else "Automatic backups disabled."
        }
    }
    fun frequency(days: Int) = task { prefs.update { it.copy(backupEveryDays = days) }; scheduler.schedule(); "Backup frequency saved." }
    fun retention(keep: Int) = task { prefs.update { it.copy(backupKeep = keep) }; "Backup retention saved." }
    fun now() = task { check(backups.automatic()) { "Enable automatic backups and choose a folder first." }; "Encrypted folder backup saved." }
}

@Composable
fun BackupScreen(navigator: AppNavigator, viewModel: BackupViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val password by viewModel.password.collectAsStateWithLifecycle()
    val confirmation by viewModel.confirmation.collectAsStateWithLifecycle()
    var replace by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { it?.let(viewModel::export) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(viewModel::preview) }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(viewModel::folder) }
    Scaffold(topBar = { TopAppBar(title = { Text("Encrypted backups") }, navigationIcon = { IconButton(onClick = navigator::back, modifier = Modifier.testTag("back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp).testTag("backup_settings"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Portable encrypted archive", style = MaterialTheme.typography.titleLarge)
            Text("Includes records, tags, history, links, appearance, notification settings, and local cover images. Device lock, API tokens, and backup credentials stay on this device.")
            OutlinedTextField(value = password, onValueChange = { viewModel.password.value = it }, visualTransformation = PasswordVisualTransformation(), label = { Text("Backup passphrase") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("backup_password"))
            OutlinedTextField(value = confirmation, onValueChange = { viewModel.confirmation.value = it }, visualTransformation = PasswordVisualTransformation(), label = { Text("Confirm passphrase (export/setup)") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("backup_confirmation"))
            Text("Keep this passphrase separately. It is required to restore on another device and cannot be recovered by the app.", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { if (viewModel.validPassword()) export.launch("notes-app.notesenc") }, enabled = !state.busy, modifier = Modifier.testTag("encrypted_export")) { Text("Export archive") }
                OutlinedButton(onClick = { restore.launch(arrayOf("*/*")) }, enabled = !state.busy && password.isNotBlank(), modifier = Modifier.testTag("encrypted_restore")) { Text("Restore archive") }
            }
            HorizontalDivider()
            Text("Automatic folder backups", style = MaterialTheme.typography.titleLarge)
            Text("Choose a local folder or a Google Drive folder offered by Android's picker. The document provider handles its account and upload. Scheduled backups may be delayed by Android.")
            OutlinedButton(onClick = { folder.launch(null) }, enabled = !state.busy, modifier = Modifier.testTag("backup_choose_folder")) { Text(if (settings.backupFolder == null) "Choose backup folder" else "Change backup folder") }
            if (settings.backupFolder != null) Text("A backup folder is configured.")
            com.notesapp.ui.components.LabeledSwitchRow("Automatic encrypted backups", settings.backupEnabled, viewModel::enable, enabled = !state.busy, modifier = Modifier.testTag("backup_enabled"))
            DropdownField(label = "Frequency", selectedLabel = if (settings.backupEveryDays == 1) "Daily" else "Every ${settings.backupEveryDays} days", options = listOf(1, 7, 30), optionLabel = { if (it == 1) "Daily" else "Every $it days" }, onSelect = viewModel::frequency, modifier = Modifier.fillMaxWidth())
            DropdownField(label = "Keep recent automatic archives", selectedLabel = settings.backupKeep.toString(), options = listOf(3, 7, 14, 30), optionLabel = Int::toString, onSelect = viewModel::retention, modifier = Modifier.fillMaxWidth())
            Text("Older automatic Notes App archives from this device are removed from this folder after a successful backup. Manual exports and other files are kept.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = viewModel::now, enabled = settings.backupEnabled && !state.busy, modifier = Modifier.testTag("backup_now")) { Text("Back up now") }
            settings.lastBackupAt?.let { Text("Last successful backup: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it))}") }
            settings.backupMessage?.let { Text(it) }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.message?.let { Text(it, modifier = Modifier.testTag("backup_message")) }
        }
    }
    if (state.preview != null && !state.busy && !replace) AlertDialog(onDismissRequest = viewModel::cancel, title = { Text("Encrypted archive validated") }, text = { Text("${state.preview} Merge adds copies and retains existing settings; replace removes the existing records and restores archive settings.") },
        confirmButton = { FlowRow { TextButton(onClick = { viewModel.restore(ImportMode.MERGE) }, modifier = Modifier.testTag("encrypted_merge")) { Text("Merge") }; TextButton(onClick = { replace = true }) { Text("Replace all") } } }, dismissButton = { TextButton(onClick = viewModel::cancel) { Text("Cancel") } })
    if (replace) ConfirmDialog("Replace all app data?", "Your existing data will be replaced by this authenticated archive. Keep a backup first.", "Replace all data", onConfirm = { replace = false; viewModel.restore(ImportMode.REPLACE) }, onDismiss = { replace = false })
}
