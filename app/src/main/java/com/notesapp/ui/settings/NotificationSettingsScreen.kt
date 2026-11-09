package com.notesapp.ui.settings

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewModelScope
import com.notesapp.automation.*
import com.notesapp.data.prefs.*
import com.notesapp.data.repository.FactRepository
import com.notesapp.domain.usecase.RotateDailyFact
import com.notesapp.ui.components.DropdownField
import com.notesapp.ui.navigation.AppNavigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val prefs: SettingsRepository,
    private val scheduler: AutomationScheduler,
    private val helper: NotificationHelper,
    private val facts: FactRepository,
    private val rotation: RotateDailyFact,
) : ViewModel() {
    val settings = prefs.notifications.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NotificationSettings())
    val topics = facts.observeTopics().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val lock = Mutex()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    fun allowed() = helper.canNotify()
    fun exact() = scheduler.exactAlarmsAllowed()
    fun update(transform: (NotificationSettings) -> NotificationSettings) {
        viewModelScope.launch { lock.withLock { prefs.setNotifications(transform(prefs.notifications.first())); scheduler.scheduleDaily() } }
    }
    fun preview() { viewModelScope.launch {
        if (!helper.canNotify()) { _message.value = "Enable Android notifications first."; return@launch }
        val id = rotation.forNotification(settings.value.topic, settings.value.source)
        val fact = id?.let { facts.get(it) }
        _message.value = if (fact == null) "No saved facts match these filters." else if (helper.showFact(fact)) "Preview notification sent." else "The morning-facts notification channel is off."
    } }
}

@Composable
fun NotificationSettingsScreen(navigator: AppNavigator, viewModel: NotificationSettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val topics by viewModel.topics.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(viewModel.allowed()) }
    var exact by remember { mutableStateOf(viewModel.exact()) }
    var source by rememberSaveable(settings.source) { mutableStateOf(settings.source.orEmpty()) }
    var enableDailyAfterPermission by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { allowed = viewModel.allowed(); exact = viewModel.exact() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted
        if (enableDailyAfterPermission) viewModel.update { it.copy(enabled = granted) }
        enableDailyAfterPermission = false
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Notifications & reminders") }, navigationIcon = { IconButton(onClick = navigator::back, modifier = Modifier.testTag("back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp).testTag("notification_settings"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Morning facts", style = MaterialTheme.typography.titleLarge)
            com.notesapp.ui.components.LabeledSwitchRow("Daily notification", settings.enabled, onCheckedChange = { enabled ->
                    if (enabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) { enableDailyAfterPermission = true; permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
                    else viewModel.update { it.copy(enabled = enabled) }
                }, modifier = Modifier.testTag("notifications_enabled"))
            val time = "%02d:%02d".format(settings.minuteOfDay / 60, settings.minuteOfDay % 60)
            OutlinedButton(onClick = { TimePickerDialog(context, { _, h, m -> viewModel.update { it.copy(minuteOfDay = h * 60 + m) } }, settings.minuteOfDay / 60, settings.minuteOfDay % 60, android.text.format.DateFormat.is24HourFormat(context)).show() }, modifier = Modifier.testTag("notification_time")) { Text("Delivery time · $time") }
            Text("Facts arrive around your chosen time. Android may delay background delivery.", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEachIndexed { index, label ->
                    val day = index + 1
                    FilterChip(selected = day in settings.days, onClick = { viewModel.update { current -> current.copy(days = if (day in current.days) current.days - day else current.days + day) } }, label = { Text(label) }, modifier = Modifier.testTag("notification_day_$day"))
                }
            }
            if (settings.days.isEmpty()) Text("Choose at least one day to receive morning facts.")
            DropdownField(label = "Topic", selectedLabel = settings.topic ?: "All personal facts", options = listOf<String?>(null) + topics,
                optionLabel = { it ?: "All personal facts" }, onSelect = { topic -> viewModel.update { it.copy(topic = topic) } }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = source, onValueChange = { source = it }, label = { Text("Source contains (optional)") }, singleLine = true,
                trailingIcon = { TextButton(onClick = { viewModel.update { it.copy(source = source.ifBlank { null }) } }) { Text("Apply") } }, modifier = Modifier.fillMaxWidth().testTag("notification_source"))
            OutlinedButton(onClick = viewModel::preview, modifier = Modifier.testTag("notification_preview")) { Text("Preview notification") }
            message?.let { Text(it, modifier = Modifier.testTag("notification_message")) }
            if (!allowed) {
                Text("Android notifications are off for this app. Reminders and morning facts need notification access.")
                Button(onClick = {
                    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) { enableDailyAfterPermission = false; permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
                    else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }) { Text("Enable Android notifications") }
            }
            HorizontalDivider()
            Text("Item reminders", style = MaterialTheme.typography.titleLarge)
            Text(if (exact) "Exact reminders are available. Set a date and time on an item's detail screen." else "Reminders use an approximate window while exact-alarm access is off.")
            if (!exact && Build.VERSION.SDK_INT >= 31) OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())) }, modifier = Modifier.testTag("enable_exact_alarms")) { Text("Allow exact reminders") }
        }
    }
}
