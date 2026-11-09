package com.notesapp.ui.discover

import com.notesapp.ui.motion.AppModalBottomSheet as ModalBottomSheet
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notesapp.data.db.entity.CollectionEntity
import com.notesapp.domain.random.PickRandomItem
import com.notesapp.ui.components.AppTopBarActions
import com.notesapp.ui.components.DropdownField
import com.notesapp.ui.components.TagFilterRow
import com.notesapp.ui.navigation.AppNavigator
import com.notesapp.ui.surprise.SurpriseCard
import com.notesapp.ui.surprise.SurpriseState
import kotlinx.serialization.json.Json

@Composable
fun DiscoverScreen(navigator: AppNavigator, viewModel: DiscoverViewModel = hiltViewModel(), autoDraw: Boolean = false) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val result by viewModel.surprise.state.collectAsStateWithLifecycle()
    val pool by viewModel.surprise.pool.collectAsStateWithLifecycle()
    val busy by viewModel.surprise.busy.collectAsStateWithLifecycle()
    val resultRequester = remember { BringIntoViewRequester() }
    var resultHeight by remember { mutableIntStateOf(0) }
    LaunchedEffect(result, resultHeight) { if (result != SurpriseState.Idle && resultHeight > 0) resultRequester.bringIntoView() }
    var autoDrawn by rememberSaveable { mutableStateOf(false) }
    var draftJson by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(autoDraw, state.collections) {
        if (autoDraw && !autoDrawn && state.collections.isNotEmpty()) { autoDrawn = true; viewModel.draw() }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Discover") }, actions = { AppTopBarActions(navigator) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp).testTag("discover_content"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("What fits your day?", style = MaterialTheme.typography.headlineMedium)
            Text("Choose a starting point, then make it yours.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DiscoverPreset.entries.forEach { preset -> FilterChip(selected = state.filters == preset.filters,
                    onClick = { viewModel.applyFilters(preset.filters) }, enabled = !busy,
                    label = { Text(preset.label) }, modifier = Modifier.testTag("preset_${preset.name.lowercase()}")) }
            }
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(filterSummary(state.filters, state.collections), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("discover_summary"))
                    Text(if (result == SurpriseState.Idle) "${state.matchCount} eligible" else "${pool.remaining} unpicked · ${pool.eligible} eligible in this session",
                        modifier = Modifier.testTag("discover_match_count"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { draftJson = Json.encodeToString(state.filters) }, enabled = !busy, modifier = Modifier.testTag("discover_advanced")) { Text("Adjust filters") }
                }
            }
            Button(onClick = viewModel::draw, enabled = !busy && state.matchCount > 0, modifier = Modifier.fillMaxWidth().testTag("discover_draw")) { Text(if (busy) "Choosing…" else "Surprise me") }
            if (state.matchCount == 0 && result == SurpriseState.Idle) Text("No eligible items. Try another preset or adjust your filters.")
            SurpriseCard(state = result, onAccept = viewModel.surprise::accept, onRollAgain = viewModel.surprise::rollAgain,
                onComplete = viewModel.surprise::complete, onOpen = navigator::openItem, onStartOver = viewModel.surprise::startOver,
                modifier = Modifier.bringIntoViewRequester(resultRequester).onSizeChanged { resultHeight = it.height })
            OutlinedButton(onClick = navigator::openHistory, modifier = Modifier.fillMaxWidth().testTag("discover_history")) { Text("Selection history") }
        }
    }
    draftJson?.let { raw ->
        val draft = Json.decodeFromString<DiscoverFilters>(raw)
        fun update(value: DiscoverFilters) { draftJson = Json.encodeToString(value) }
        ModalBottomSheet(onDismissRequest = { draftJson = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 20.dp).testTag("discover_filter_sheet")) {
                Text("Make room for the right thing", style = MaterialTheme.typography.headlineSmall)
                Text("${viewModel.previewCount(draft)} eligible with these filters", modifier = Modifier.testTag("discover_draft_count"))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DropdownField("Collection", state.collections.firstOrNull { it.id == draft.collectionId }?.name ?: "Any collection",
                        listOf<CollectionEntity?>(null) + state.collections, { it?.name ?: "Any collection" }, { update(draft.copy(collectionId = it?.id, category = null)) }, Modifier.fillMaxWidth().testTag("discover_collection"))
                    DropdownField("Status", draft.status.label, StatusMode.entries, { it.label }, { update(draft.copy(status = it)) }, Modifier.fillMaxWidth().testTag("discover_status"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(draft.favoritesOnly, { update(draft.copy(favoritesOnly = !draft.favoritesOnly)) }, { Text("Favorites only") }, modifier = Modifier.testTag("discover_favorites"))
                        FilterChip(draft.highPriorityOnly, { update(draft.copy(highPriorityOnly = !draft.highPriorityOnly)) }, { Text("High priority") }, modifier = Modifier.testTag("discover_high_priority"))
                    }
                    if (state.tags.isNotEmpty()) { Text("Tags · matches any selected tag"); TagFilterRow(state.tags, draft.tags, onToggle = { tag -> update(draft.copy(tags = if (tag in draft.tags) draft.tags - tag else draft.tags + tag)) }) }
                    DropdownField("Avoid recent picks", if (draft.cooldownDays == 0) "No cooldown" else "Last ${draft.cooldownDays} days",
                        listOf(0, 1, 3, 7, 14, 30), { if (it == 0) "No cooldown" else "Last $it days" }, { update(draft.copy(cooldownDays = it)) }, Modifier.fillMaxWidth().testTag("discover_cooldown"))
                    DropdownField("Time available", draft.maxRuntime?.let { "Up to $it minutes" } ?: "Any duration", listOf<Int?>(null, 30, 60, 90, 120, 180),
                        { it?.let { "Up to $it minutes" } ?: "Any duration" }, { update(draft.copy(maxRuntime = it)) }, Modifier.fillMaxWidth().testTag("discover_runtime"))
                    DropdownField("Maximum price", draft.maxPrice?.let { if (it == 0.0) "Free" else "Up to $it" } ?: "Any price", listOf<Double?>(null, 0.0, 10.0, 25.0, 50.0, 100.0, 500.0),
                        { it?.let { if (it == 0.0) "Free" else "Up to $it" } ?: "Any price" }, { update(draft.copy(maxPrice = it)) }, Modifier.fillMaxWidth().testTag("discover_price"))
                    if (draft.maxPrice != null && draft.maxPrice > 0) DropdownField("Currency", draft.currency ?: "Choose currency", state.currencies,
                        { it }, { update(draft.copy(currency = it)) }, Modifier.fillMaxWidth().testTag("discover_currency"))
                    Text("Time and price need saved details. Free means a known price of zero. Paid budgets match only the currency you choose.", style = MaterialTheme.typography.bodySmall)
                    DropdownField("When added", ageLabels[draft.ageMode], listOf(0, 1, 2), { ageLabels[it] }, { update(draft.copy(ageMode = it)) }, Modifier.fillMaxWidth().testTag("discover_age"))
                    Text("Archived items and items awaiting deletion are always excluded.", style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = { viewModel.applyFilters(draft); draftJson = null }, enabled = draft.maxPrice == null || draft.maxPrice == 0.0 || draft.currency != null,
                    modifier = Modifier.fillMaxWidth().testTag("discover_apply")) { Text("Apply filters") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { update(DiscoverFilters()) }) { Text("Reset") }
                    TextButton(onClick = { draftJson = null }, modifier = Modifier.testTag("discover_cancel")) { Text("Cancel") }
                }
            }
        }
    }
}

private val ageLabels = listOf("Any date", "Last 30 days", "More than 90 days ago")
private fun filterSummary(f: DiscoverFilters, collections: List<CollectionEntity>): String = buildList {
    add(collections.firstOrNull { it.id == f.collectionId }?.name ?: f.category?.name?.lowercase()?.replaceFirstChar(Char::uppercase) ?: "Any collection")
    add(f.status.label)
    if (f.favoritesOnly) add("Favorites")
    if (f.highPriorityOnly) add("High priority")
    if (f.tags.isNotEmpty()) add(f.tags.joinToString { "#$it" })
    if (f.cooldownDays > 0) add("${f.cooldownDays}-day cooldown")
    f.maxRuntime?.let { add("≤ $it min") }
    f.maxPrice?.let { add(if (it == 0.0) "Free" else "≤ $it ${f.currency.orEmpty()}") }
    if (f.ageMode > 0) add(ageLabels[f.ageMode])
}.joinToString(" · ")
