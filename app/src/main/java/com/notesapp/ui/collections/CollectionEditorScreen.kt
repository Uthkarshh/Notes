package com.notesapp.ui.collections

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notesapp.domain.model.CategoryType
import com.notesapp.domain.model.StatusRole
import com.notesapp.ui.components.CollectionBadge
import com.notesapp.ui.components.CollectionColorChoices
import com.notesapp.ui.components.CollectionIcons
import com.notesapp.ui.components.ConfirmDialog
import com.notesapp.ui.components.DeleteCollectionDialog
import com.notesapp.ui.components.DropdownField
import com.notesapp.ui.components.SectionTitle
import com.notesapp.ui.components.parseHexColor
import com.notesapp.ui.navigation.AppNavigator
import kotlinx.coroutines.launch

fun CategoryType.displayName(): String = when (this) {
    CategoryType.MOVIE -> "Movies & shows"
    CategoryType.BOOK -> "Books & articles"
    CategoryType.PLACE -> "Places"
    CategoryType.BUY -> "Shopping"
    CategoryType.CUSTOM -> "Custom"
}

@Composable
fun CollectionEditorScreen(navigator: AppNavigator, viewModel: CollectionEditorViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val draft = state.draft
    val scope = rememberCoroutineScope()
    var confirmDiscard by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    fun close() {
        if (state.dirty) confirmDiscard = true else navigator.back()
    }
    BackHandler(onBack = ::close)

    if (state.loading) {
        Box(Modifier.fillMaxSize())
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New collection" else "Edit collection") },
                navigationIcon = {
                    IconButton(onClick = ::close, modifier = Modifier.testTag("editor_close")) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                if (viewModel.save()) navigator.back()
                            }
                        },
                        enabled = draft.isValid,
                        modifier = Modifier.testTag("editor_save"),
                    ) { Text("Save") }
                    if (!state.isNew) {
                        Box {
                            IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("editor_menu")) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Delete collection") },
                                    onClick = {
                                        menuOpen = false
                                        showDelete = true
                                    },
                                    modifier = Modifier.testTag("editor_delete"),
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("collection_editor"),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CollectionBadge(draft.iconName, draft.colorHex, size = 56.dp)
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = viewModel::setName,
                    label = { Text("Name") },
                    singleLine = true,
                    isError = draft.nameProblem != null,
                    supportingText = draft.nameProblem?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.weight(1f).testTag("editor_name"),
                )
            }

            DropdownField(
                label = "Type",
                selectedLabel = draft.categoryType.displayName(),
                options = CategoryType.entries,
                optionLabel = { it.displayName() },
                onSelect = viewModel::setCategory,
                modifier = Modifier.fillMaxWidth().testTag("editor_category"),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Icon")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CollectionIcons.names.forEach { name ->
                        FilledIconToggleButton(
                            checked = draft.iconName == name,
                            onCheckedChange = { viewModel.setIcon(name) },
                            modifier = Modifier.testTag("editor_icon_$name"),
                        ) {
                            Icon(CollectionIcons.get(name), contentDescription = name)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Color")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CollectionColorChoices.forEach { hex ->
                        ColorSwatch(hex, selected = draft.colorHex.equals(hex, ignoreCase = true), onClick = { viewModel.setColor(hex) })
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Statuses")
                Text(
                    "Statuses are the stages an item moves through. Mark which ones mean not started, in progress, completed or dropped; " +
                        "Surprise me and progress bars rely on that.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                draft.statuses.forEachIndexed { index, status ->
                    StatusEditorCard(
                        index = index,
                        count = draft.statuses.size,
                        label = status.label,
                        role = status.role,
                        onLabel = { viewModel.setStatusLabel(status.id, it) },
                        onRole = { viewModel.setStatusRole(status.id, it) },
                        onMoveUp = { viewModel.moveStatus(status.id, -1) },
                        onMoveDown = { viewModel.moveStatus(status.id, 1) },
                        onRemove = { viewModel.removeStatus(status.id) },
                    )
                }
                OutlinedButton(onClick = viewModel::addStatus, modifier = Modifier.testTag("editor_add_status")) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Add status", modifier = Modifier.padding(start = 6.dp))
                }
                draft.statusProblems.forEach { problem ->
                    Text(problem, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (!state.isNew) {
                    Text(
                        "Items in a removed status move to the first status of the same type.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard changes?",
            text = "Your edits to this collection haven't been saved.",
            confirmLabel = "Discard",
            dismissLabel = "Keep editing",
            onConfirm = {
                confirmDiscard = false
                navigator.back()
            },
            onDismiss = { confirmDiscard = false },
        )
    }

    if (showDelete) {
        DeleteCollectionDialog(
            name = draft.name,
            itemCount = state.itemCount,
            onKeepItemsInInbox = {
                showDelete = false
                scope.launch {
                    viewModel.delete(deleteItems = false)
                    navigator.backToCollections()
                }
            },
            onDeleteEverything = {
                showDelete = false
                scope.launch {
                    viewModel.delete(deleteItems = true)
                    navigator.backToCollections()
                }
            },
            onDismiss = { showDelete = false },
        )
    }
}

@Composable
private fun ColorSwatch(hex: String, selected: Boolean, onClick: () -> Unit) {
    val color = parseHexColor(hex)
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(color, CircleShape)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .clickable(onClick = onClick)
            .testTag("editor_color_${hex.removePrefix("#")}"),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = com.notesapp.ui.theme.contrastInk(color), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun StatusEditorCard(
    index: Int,
    count: Int,
    label: String,
    role: StatusRole,
    onLabel: (String) -> Unit,
    onRole: (StatusRole) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    var roleMenuOpen by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().testTag("editor_status_$index"),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = label,
                onValueChange = onLabel,
                label = { Text("Status ${index + 1}") },
                singleLine = true,
                isError = label.isBlank(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth().testTag("editor_status_label_$index"),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    AssistChip(
                        onClick = { roleMenuOpen = true },
                        label = { Text(role.label) },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = "Change type") },
                        modifier = Modifier.testTag("editor_status_role_$index"),
                    )
                    DropdownMenu(expanded = roleMenuOpen, onDismissRequest = { roleMenuOpen = false }) {
                        StatusRole.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    onRole(option)
                                    roleMenuOpen = false
                                },
                                modifier = Modifier.testTag("editor_role_${index}_${option.name.lowercase()}"),
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onMoveUp, enabled = index > 0) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Move status up")
                }
                IconButton(onClick = onMoveDown, enabled = index < count - 1) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Move status down")
                }
                IconButton(onClick = onRemove, enabled = count > 1, modifier = Modifier.testTag("editor_status_remove_$index")) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove status")
                }
            }
        }
    }
}
