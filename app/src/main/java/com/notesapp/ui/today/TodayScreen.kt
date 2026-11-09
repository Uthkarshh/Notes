package com.notesapp.ui.today

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notesapp.domain.facts.FactFormatter
import com.notesapp.domain.model.FactWithTags
import com.notesapp.ui.components.*
import com.notesapp.ui.navigation.AppNavigator
import com.notesapp.ui.surprise.SurpriseSheet
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

@Composable
fun TodayScreen(navigator: AppNavigator, viewModel: TodayViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val surpriseState by viewModel.surprise.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val dateLocale = LocalConfiguration.current.locales[0] ?: Locale.ROOT
    val localNow = Instant.ofEpochMilli(state.now).atZone(viewModel.clock.zone)
    var dismissedInboxCount by rememberSaveable(localNow.toLocalDate().toString()) { mutableIntStateOf(-1) }
    var allDue by rememberSaveable { mutableStateOf(false) }
    var allActive by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) { viewModel.refresh(); onPauseOrDispose { } }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Today") }, actions = { AppTopBarActions(navigator) }) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().testTag("today_list"),
            contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 8.dp, 16.dp, padding.calculateBottomPadding() + 96.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item("greeting") {
                Column {
                    Text(when { localNow.hour < 12 -> "Good morning"; localNow.hour < 18 -> "Good afternoon"; else -> "Good evening" }, style = MaterialTheme.typography.headlineMedium)
                    Text(localNow.format(DateTimeFormatter.ofPattern("EEEE, MMM d", dateLocale)),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.inboxCount > 0 && state.inboxCount != dismissedInboxCount) item("inbox") {
                Surface(onClick = navigator::openInbox, color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().testTag("inbox_banner")) {
                    Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Inbox, null, Modifier.size(20.dp))
                        Text("${state.inboxCount} ${if (state.inboxCount == 1) "capture" else "captures"} to file", Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = navigator::openInbox) { Text("Review") }
                        IconButton(onClick = { dismissedInboxCount = state.inboxCount }, modifier = Modifier.testTag("dismiss_inbox_banner")) { Icon(Icons.Default.Close, "Dismiss Inbox prompt") }
                    }
                }
            }
            if (state.reminders.isNotEmpty()) item("due") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("due_reminders")) {
                    SectionHeader("Due soon", state.reminders.size > 3, allDue) { allDue = !allDue }
                    (if (allDue) state.reminders else state.reminders.take(3)).forEach { due ->
                        val item = due.item
                        OutlinedCard(onClick = { navigator.openItem(item.id) }, modifier = Modifier.fillMaxWidth().testTag("due_reminder_${item.id}")) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(item.title, style = MaterialTheme.typography.titleMedium)
                                val overdue = item.reminderAt!! < state.now
                                Text((if (overdue) "Overdue · " else "") + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, dateLocale).format(java.util.Date(item.reminderAt)),
                                    style = MaterialTheme.typography.bodySmall, color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (due.collection?.schema?.completionStatus != null) TextButton(onClick = { viewModel.complete(item.id) }, modifier = Modifier.testTag("due_complete_${item.id}")) { Text("Complete") }
                                    else TextButton(onClick = { navigator.openItem(item.id) }) { Text("Change status") }
                                    SnoozeAction(item.id, { viewModel.snooze(item.id, viewModel.clock.millis() + 3_600_000) },
                                        { viewModel.snooze(item.id, viewModel.tomorrowMorning()) }, { at -> viewModel.snooze(item.id, at) }, viewModel.clock.millis())
                                }
                            }
                        }
                    }
                }
            }
            item("active") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("up_next_row")) {
                    SectionHeader("Up next", state.inProgress.size > 3, allActive) { allActive = !allActive }
                    if (state.inProgress.isEmpty()) Text("Nothing in progress. Pick something when you're ready.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("up_next_empty"))
                    (if (allActive) state.inProgress else state.inProgress.take(3)).forEach { progress ->
                        val item = progress.entry.item
                        ListItem(headlineContent = { Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Column {
                                    Text("${progress.collection.name} · ${progress.collection.schema.labelOf(item.status)}")
                                    com.notesapp.ui.components.ItemMetadataLine(progress.entry, progress.collection)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = { if (item.externalUrl.isNullOrBlank()) navigator.openItem(item.id) else context.openLink(item.externalUrl) }, modifier = Modifier.testTag("resume_${item.id}")) { Text("Resume") }
                                        if (progress.collection.schema.completionStatus != null) TextButton(onClick = { viewModel.complete(item.id) }, modifier = Modifier.testTag("active_complete_${item.id}")) { Text("Complete") }
                                    }
                                }
                            }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth().clickable { navigator.openItem(item.id) }.testTag("up_next_${item.id}"))
                    }
                }
            }
            item("fact") {
                MorningFactCard(state.fact, { viewModel.toggleFavorite(it.fact) },
                    { context.shareText(FactFormatter.shareText(it.fact.text, it.fact.source), "Share fact") },
                    viewModel::nextFact, navigator::openFact, { navigator.openFact(null) })
            }
            item("draw") {
                TextButton(onClick = { viewModel.drawSurprise() }, modifier = Modifier.fillMaxWidth().testTag("surprise_any")) {
                    Icon(Icons.Default.Casino, null, Modifier.size(20.dp))
                    Text("Pick something for me", Modifier.padding(start = 8.dp))
                }
            }
        }
    }
    SurpriseSheet(surpriseState, viewModel.surprise, navigator::openItem)
}

@Composable
private fun SectionHeader(title: String, expandable: Boolean, expanded: Boolean, onExpand: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        if (expandable) TextButton(onClick = onExpand) { Text(if (expanded) "Show less" else "View all") }
    }
}

@Composable
private fun SnoozeAction(id: Long, onHour: () -> Unit, onTomorrow: () -> Unit, onChoose: (Long) -> Unit, now: Long) {
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Box {
        TextButton(onClick = { open = true }, modifier = Modifier.testTag("due_snooze_$id")) { Text("Snooze") }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem(text = { Text("In 1 hour") }, onClick = { open = false; onHour() })
            DropdownMenuItem(text = { Text("Tomorrow 9:00") }, onClick = { open = false; onTomorrow() })
            DropdownMenuItem(text = { Text("Choose date and time") }, onClick = {
                open = false
                val calendar = Calendar.getInstance().apply { timeInMillis = now + 3_600_000 }
                DatePickerDialog(context, { _, year, month, day ->
                    TimePickerDialog(context, { _, hour, minute ->
                        calendar.set(year, month, day, hour, minute, 0); calendar.set(Calendar.MILLISECOND, 0)
                        onChoose(calendar.timeInMillis)
                    }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), android.text.format.DateFormat.is24HourFormat(context)).show()
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
            })
        }
    }
}

@Composable
private fun MorningFactCard(fact: FactWithTags?, onFavorite: (FactWithTags) -> Unit, onShare: (FactWithTags) -> Unit,
    onNext: () -> Unit, onOpen: (Long) -> Unit, onAdd: () -> Unit) {
    val latestNext by rememberUpdatedState(onNext)
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().testTag("morning_fact").pointerInput(fact?.fact?.id) {
            var distance = 0f
            detectHorizontalDragGestures(onDragStart = { distance = 0f }, onDragEnd = { if (abs(distance) > threshold) latestNext() }) { change, amount ->
                distance += amount; change.consume()
            }
        }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Morning fact", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (fact == null) {
                Text("A little knowledge, every day.", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onAdd, modifier = Modifier.testTag("fact_add")) { Text("Add a fact") }
            } else {
                Text(fact.fact.text, style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif), maxLines = 3,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { onOpen(fact.fact.id) }.testTag("fact_text"))
                fact.fact.source?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onNext, modifier = Modifier.testTag("fact_next")) { Text("Next fact") }
                    FavoriteToggle(fact.fact.isFavorite, { onFavorite(fact) }, Modifier.testTag("fact_favorite"))
                    IconButton(onClick = { onShare(fact) }, modifier = Modifier.testTag("fact_share")) { Icon(Icons.Default.Share, "Share fact") }
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
