package com.notesapp.ui.transfer

import com.notesapp.ui.motion.AppAlertDialog as AlertDialog
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notesapp.data.db.entity.CollectionEntity
import com.notesapp.data.repository.ImportMode
import com.notesapp.ui.components.ConfirmDialog
import com.notesapp.ui.components.DropdownField
import com.notesapp.ui.navigation.AppNavigator

@Composable
fun DataTransferScreen(navigator: AppNavigator, viewModel: DataTransferViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    var collectionId by remember { mutableStateOf<Long?>(null) }
    var confirmReplace by remember { mutableStateOf(false) }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { uri -> viewModel.export(uri, TransferFormat.JSON, null) } }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let { uri -> viewModel.export(uri, TransferFormat.CSV, collectionId) } }
    val exportMarkdown = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let { uri -> viewModel.export(uri, TransferFormat.MARKDOWN, null) } }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { uri -> viewModel.preview(uri, TransferFormat.JSON) } }
    val importCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { uri -> viewModel.preview(uri, TransferFormat.CSV) } }
    val importMarkdown = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let { uri -> viewModel.preview(uri, TransferFormat.MARKDOWN) } }

    Scaffold(topBar = { TopAppBar(title = { Text("Import & export") }, navigationIcon = { IconButton(onClick = navigator::back, modifier = Modifier.testTag("back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp).testTag("data_transfer"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("JSON backup", style = MaterialTheme.typography.titleLarge)
            Text("Includes all collections, items, notes, facts, tags, selection history, links, and settings. Choose where Android saves the file.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exportJson.launch("notes-app-backup.json") }, enabled = !state.busy, modifier = Modifier.testTag("export_json")) { Text("Export JSON") }
                OutlinedButton(onClick = { importJson.launch(arrayOf("*/*")) }, enabled = !state.busy, modifier = Modifier.testTag("import_json")) { Text("Import JSON") }
            }
            HorizontalDivider()
            Text("Collection CSV", style = MaterialTheme.typography.titleLarge)
            DropdownField(label = "Export collection", selectedLabel = collections.firstOrNull { it.id == collectionId }?.name ?: "All items, including Inbox",
                options = listOf<CollectionEntity?>(null) + collections, optionLabel = { it?.name ?: "All items, including Inbox" }, onSelect = { collectionId = it?.id }, modifier = Modifier.fillMaxWidth())
            Text("CSV imports need a title column. Optional columns include description, collection, status, priority, rating, tags, and url. Rows without a collection go to Inbox.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exportCsv.launch("notes-app-items.csv") }, enabled = !state.busy, modifier = Modifier.testTag("export_csv")) { Text("Export CSV") }
                OutlinedButton(onClick = { importCsv.launch(arrayOf("*/*")) }, enabled = !state.busy, modifier = Modifier.testTag("import_csv")) { Text("Import CSV") }
            }
            HorizontalDivider()
            Text("Markdown folder", style = MaterialTheme.typography.titleLarge)
            Text("Exports notes and facts into a new NotesApp folder. Imports ordinary Markdown as notes and restores the details from files exported by this app.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exportMarkdown.launch(null) }, enabled = !state.busy, modifier = Modifier.testTag("export_markdown")) { Text("Export folder") }
                OutlinedButton(onClick = { importMarkdown.launch(null) }, enabled = !state.busy, modifier = Modifier.testTag("import_markdown")) { Text("Import folder") }
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.message?.let { Text(it, modifier = Modifier.testTag("transfer_message")) }
        }
    }
    if (state.preview != null && !state.busy && !confirmReplace) {
        AlertDialog(onDismissRequest = viewModel::cancelImport, title = { Text("Import validated") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(state.preview.orEmpty()); Text("Merge adds copies and keeps your existing data and settings. Replace removes existing data and restores this file instead.")
        } }, confirmButton = { FlowRow { TextButton(onClick = { viewModel.confirmImport(ImportMode.MERGE) }, modifier = Modifier.testTag("import_merge")) { Text("Merge") }; TextButton(onClick = { confirmReplace = true }) { Text("Replace all") } } },
            dismissButton = { TextButton(onClick = viewModel::cancelImport) { Text("Cancel") } })
    }
    if (confirmReplace) ConfirmDialog(title = "Replace all app data?", text = "${state.preview.orEmpty()} Your current data will be removed. Keep a JSON backup if you want to restore it later.", confirmLabel = "Replace all data",
        onConfirm = { confirmReplace = false; viewModel.confirmImport(ImportMode.REPLACE) }, onDismiss = { confirmReplace = false })
}
