package com.notesapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notesapp.domain.model.ThemeMode
import com.notesapp.ui.navigation.AppNavigator
import com.notesapp.ui.theme.dynamicColorSupported

@Composable
fun SettingsScreen(navigator: AppNavigator, viewModel: SettingsViewModel = hiltViewModel()) {
    SettingsContent(navigator, viewModel)
}

/** Route-free content; the caller supplies the navigation-entry-scoped ViewModel. */
@Composable
internal fun SettingsContent(navigator: AppNavigator, viewModel: SettingsViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val gestures by viewModel.inboxGestures.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = navigator::back, modifier = Modifier.testTag(if (com.notesapp.ui.navigation.LocalPaneList.current) "pane_list_back" else "back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .testTag("settings_content"),
        ) {
            SectionHeader("Appearance")
            ThemeMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            role = Role.RadioButton,
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("settings_theme_${mode.name.lowercase()}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    RadioButton(selected = settings.themeMode == mode, onClick = null)
                    Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                }
            }

            SwitchRow(
                title = "AMOLED black",
                description = "Pure black backgrounds in dark mode to save power on OLED screens.",
                checked = settings.amoledBlack,
                onCheckedChange = viewModel::setAmoledBlack,
                tag = "settings_amoled",
            )
            SwitchRow(
                title = "Dynamic color",
                description = if (dynamicColorSupported) {
                    "Match the colors of your wallpaper (Material You)."
                } else {
                    "Requires Android 12 or newer."
                },
                checked = settings.dynamicColor && dynamicColorSupported,
                onCheckedChange = viewModel::setDynamicColor,
                enabled = dynamicColorSupported,
                tag = "settings_dynamic",
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionHeader("Interactions")
            SwitchRow(
                title = "Reduce motion",
                description = "Show screen and sheet transitions immediately. Android’s animation setting is also respected.",
                checked = settings.reduceMotion,
                onCheckedChange = viewModel::setReduceMotion,
                tag = "settings_reduce_motion",
            )
            Text("Inbox gestures", modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleMedium)
            com.notesapp.ui.components.DropdownField(label = "Swipe start to end", selectedLabel = gestures.startToEnd.label,
                options = com.notesapp.data.prefs.InboxSwipeAction.entries, optionLabel = { it.label },
                onSelect = { viewModel.setInboxGesture(true, it) }, modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("settings_inbox_start"))
            com.notesapp.ui.components.DropdownField(label = "Swipe end to start", selectedLabel = gestures.endToStart.label,
                options = com.notesapp.data.prefs.InboxSwipeAction.entries, optionLabel = { it.label },
                onSelect = { viewModel.setInboxGesture(false, it) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("settings_inbox_end"))
            SectionHeader("Your data")
            androidx.compose.material3.TextButton(onClick = navigator::openNotificationSettings, modifier = Modifier.padding(horizontal = 16.dp).testTag("settings_notifications")) { Text("Notifications & reminders") }
            androidx.compose.material3.TextButton(onClick = navigator::openDataTransfer, modifier = Modifier.padding(horizontal = 16.dp).testTag("settings_data_transfer")) { Text("Import & export") }
            androidx.compose.material3.TextButton(onClick = navigator::openAdvancedSettings, modifier = Modifier.padding(horizontal = 16.dp).testTag("settings_advanced")) { Text("Privacy & metadata") }
            androidx.compose.material3.TextButton(onClick = navigator::openBackupSettings, modifier = Modifier.padding(horizontal = 16.dp).testTag("settings_backups")) { Text("Encrypted backups") }
            androidx.compose.material3.TextButton(onClick = navigator::openInsights, modifier = Modifier.padding(horizontal = 16.dp).testTag("settings_insights")) { Text("Personal insights") }
            SectionHeader("About")
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Notes App", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Version $versionName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Your data stays local unless you choose online lookup or a backup destination. No app account or telemetry.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
