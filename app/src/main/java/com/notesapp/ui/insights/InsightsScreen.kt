package com.notesapp.ui.insights

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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.withTransaction
import com.notesapp.data.db.AppDatabase
import com.notesapp.data.repository.ItemRepository
import com.notesapp.data.repository.CollectionRepository
import com.notesapp.domain.insights.*
import com.notesapp.domain.model.CategoryType
import com.notesapp.domain.model.StatusRole
import com.notesapp.ui.components.*
import com.notesapp.ui.navigation.AppNavigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class InsightsViewModel @Inject constructor(private val db: AppDatabase, private val items: ItemRepository, collections: CollectionRepository, private val clock: Clock, private val actions: com.notesapp.ui.item.ItemActions) : ViewModel() {
    val surprise = com.notesapp.ui.surprise.SurpriseSession(items, collections, viewModelScope)
    fun rollForgotten() = surprise.draw(com.notesapp.domain.random.RandomFilter(unstartedOnly = true, addedBefore = clock.millis() - 90 * 86_400_000L))
    val year = MutableStateFlow(LocalDate.now(clock).year)
    val month = MutableStateFlow<Int?>(null)
    val years = ((year.value - 30)..year.value).reversed().toList()
    private val data = combine(db.itemDao().observeAllItems(), collections.observeAll()) { allItems, allCollections ->
        db.withTransaction { Triple(allItems to allCollections, db.phase2Dao().tags(), db.phase2Dao().tagRefs()) }
    }
    val summary = combine(data, year, month) { data, year, month ->
        PersonalInsights.compute(data.first.first, data.first.second, data.second, data.third, year, month, clock.instant(), clock.zone)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun archive(ids: List<Long>, letGo: Boolean = false) { viewModelScope.launch {
        ids.distinct().forEach { id -> actions.change(id, if (letGo) "Item released" else "Item archived") { items.revisitBacklog(id, letGo) } }
    } }

}

@Composable
fun InsightsScreen(navigator: AppNavigator, viewModel: InsightsViewModel = hiltViewModel()) {
    val pick by viewModel.surprise.state.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val year by viewModel.year.collectAsStateWithLifecycle()
    val month by viewModel.month.collectAsStateWithLifecycle()
    var archive by remember { mutableStateOf<List<Long>?>(null) }
    Scaffold(topBar = { TopAppBar(title = { Text("Personal insights") }, navigationIcon = { IconButton(onClick = navigator::back, modifier = Modifier.testTag("back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp).testTag("insights"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DropdownField(label = "Year in review", selectedLabel = year.toString(), options = viewModel.years, optionLabel = Int::toString, onSelect = { viewModel.year.value = it }, modifier = Modifier.fillMaxWidth().testTag("insights_year"))
            DropdownField(label = "Period", selectedLabel = month?.let { java.time.Month.of(it).name.lowercase().replaceFirstChar(Char::uppercase) } ?: "Whole year",
                options = listOf<Int?>(null) + (1..12).toList(), optionLabel = { it?.let { java.time.Month.of(it).name.lowercase().replaceFirstChar(Char::uppercase) } ?: "Whole year" }, onSelect = { viewModel.month.value = it }, modifier = Modifier.fillMaxWidth().testTag("insights_month"))
            val data = summary
            if (data == null) CircularProgressIndicator() else {
                Text("${data.completed} completed · ${data.added} added", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("insights_counts"))
                Text("${data.completionRate}% completion rate", style = MaterialTheme.typography.titleMedium)
                Text("Rate is the share of items added in this period that were completed by its end. Completed count also includes older backlog items finished during this period.", style = MaterialTheme.typography.bodySmall)
                Text("Current streak: ${data.currentStreak} days · Longest: ${data.longestStreak} days")
                data.mediaStreaks.forEach { (category, streak) -> Text("${if (category == CategoryType.BOOK) "Reading" else "Watching"}: ${streak.first} days current · ${streak.second} best") }
                Text("A streak counts days with a completion, using your device's local timezone. Deleted records are no longer counted.", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text("$year activity", style = MaterialTheme.typography.titleLarge)
                val max = data.months.maxOfOrNull { it.completed }?.coerceAtLeast(1) ?: 1
                data.months.forEach { bucket -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${java.time.Month.of(bucket.month).name.take(3)} · ${bucket.completed} completed, ${bucket.added} added")
                    LinearProgressIndicator(progress = { bucket.completed.toFloat() / max }, modifier = Modifier.fillMaxWidth())
                } }
                Text("Most active completed tags", style = MaterialTheme.typography.titleLarge)
                if (data.activeTags.isEmpty()) Text("Complete tagged items to see patterns here.")
                data.activeTags.forEach { (tag, count) -> Text("#$tag · $count completed") }
                HorizontalDivider()
                Text("Still interested?", style = MaterialTheme.typography.titleLarge)
                Text("Unstarted items saved more than 90 days ago. Archiving keeps them in backups and history.")
                if (data.forgotten.isEmpty()) Text("No forgotten backlog right now.", modifier = Modifier.testTag("forgotten_empty"))
                if (data.forgotten.isNotEmpty()) OutlinedButton(onClick = viewModel::rollForgotten, modifier = Modifier.testTag("roll_forgotten")) { Text("Roll now") }
                data.forgotten.take(20).forEach { item -> OutlinedCard(onClick = { navigator.openItem(item.id) }, modifier = Modifier.fillMaxWidth().testTag("forgotten_${item.id}")) {
                    Column(Modifier.padding(12.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { archive = listOf(item.id) }) { Text("Archive") }
                            TextButton(onClick = { viewModel.archive(listOf(item.id), letGo = true) }, modifier = Modifier.testTag("let_go_${item.id}")) { Text("Let go") }
                        }
                    }
                } }
                if (data.forgotten.isNotEmpty()) OutlinedButton(onClick = { archive = data.forgotten.map { it.id } }, modifier = Modifier.testTag("archive_forgotten")) { Text("Archive all ${data.forgotten.size} forgotten items") }
            }
        }
    }
    com.notesapp.ui.surprise.SurpriseSheet(pick, viewModel.surprise, navigator::openItem)
    archive?.let { ids -> ConfirmDialog(title = "Archive ${ids.size} forgotten items?", text = "These items stay saved and can be unarchived later. Items that are now in progress or completed are kept active.", confirmLabel = "Archive", onConfirm = { viewModel.archive(ids); archive = null }, onDismiss = { archive = null }) }
}
