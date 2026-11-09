package com.notesapp.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notesapp.data.db.entity.CollectionEntity
import com.notesapp.data.db.entity.FactEntity
import com.notesapp.data.db.entity.ItemEntity
import com.notesapp.data.prefs.SettingsRepository
import com.notesapp.data.repository.CollectionRepository
import com.notesapp.data.repository.FactRepository
import com.notesapp.data.repository.ItemRepository
import com.notesapp.di.ApplicationScope
import com.notesapp.domain.model.*
import com.notesapp.domain.random.RandomFilter
import com.notesapp.domain.usecase.RotateDailyFact
import com.notesapp.ui.item.ItemActions
import com.notesapp.ui.surprise.SurpriseSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class InProgressEntry(val entry: ItemWithTags, val collection: CollectionEntity)
data class DueEntry(val item: ItemEntity, val collection: CollectionEntity?)
data class TodayUiState(
    val fact: FactWithTags? = null,
    val inProgress: List<InProgressEntry> = emptyList(),
    val inboxCount: Int = 0,
    val reminders: List<DueEntry> = emptyList(),
    val now: Long = 0,
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val factRepository: FactRepository,
    private val itemRepository: ItemRepository,
    private val collectionRepository: CollectionRepository,
    settingsRepository: SettingsRepository,
    private val rotateDailyFact: RotateDailyFact,
    val clock: Clock,
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val actions: ItemActions,
) : ViewModel() {
    val surprise = SurpriseSession(itemRepository, collectionRepository, viewModelScope)
    private val refreshSignal = MutableStateFlow(0L)
    private val ticks = refreshSignal.flatMapLatest {
        flow { while (currentCoroutineContext().isActive) { emit(clock.millis()); delay(30_000) } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), clock.millis())
    private val todaysFact = settingsRepository.dailyFact.map { it.factId }.distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else factRepository.observe(id) }
        .map { it?.takeIf { row -> row.fact.masteryState != MasteryState.HIDDEN } }
    private val inProgress = collectionRepository.observeAll().flatMapLatest { collections ->
        val keys = collections.flatMap { c -> c.schema.statuses.filter { it.role == StatusRole.ACTIVE }.map { it.key } }.toSet()
        val byId = collections.associateBy { it.id }
        itemRepository.observeByStatusKeys(keys).map { rows -> rows.mapNotNull { row ->
            val collection = byId[row.item.collectionId] ?: return@mapNotNull null
            if (!row.item.isArchived && collection.schema.roleOf(row.item.status) == StatusRole.ACTIVE) InProgressEntry(row, collection) else null
        } }
    }
    private val reminders = combine(itemRepository.observeReminders(), collectionRepository.observeAll(), ticks) { items, collections, now ->
        val byId = collections.associateBy { it.id }
        items.filter { item -> !item.isArchived && item.reminderAt!! <= now + 48 * 3_600_000L &&
            byId[item.collectionId]?.schema?.isTerminal(item.status) != true
        }.sortedBy { it.reminderAt }.map { DueEntry(it, byId[it.collectionId]) }
    }
    val uiState: StateFlow<TodayUiState> = combine(todaysFact, inProgress, itemRepository.observeInboxCount(), reminders, ticks) { fact, progress, inbox, due, now ->
        val dueIds = due.map { it.item.id }.toSet()
        TodayUiState(fact, progress.filterNot { it.entry.item.id in dueIds }, inbox, due, now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodayUiState(now = clock.millis()))

    init { viewModelScope.launch {
        combine(factRepository.observeShowableIds(), settingsRepository.dailyFact) { ids, daily -> ids to daily }
            .collect { rotateDailyFact.ensureToday() }
    } }
    fun refresh() { refreshSignal.value++; viewModelScope.launch { rotateDailyFact.ensureToday() } }
    fun nextFact() { viewModelScope.launch { rotateDailyFact.next() } }
    fun toggleFavorite(fact: FactEntity) { viewModelScope.launch { factRepository.setFavorite(fact.id, !fact.isFavorite) } }
    fun drawSurprise(category: CategoryType? = null) { surprise.draw(RandomFilter(categoryType = category)) }
    fun undoCapture(itemId: Long) { viewModelScope.launch { itemRepository.delete(itemId) } }

    private val activeActions = mutableSetOf<Long>()
    fun complete(id: Long) = perform(id, "Item completed") {
        val item = itemRepository.get(id) ?: return@perform
        if (item.isArchived) return@perform
        val collection = item.collectionId?.let { collectionRepository.get(it) } ?: return@perform
        if (collection.schema.isTerminal(item.status)) return@perform
        collection.schema.completionStatus?.let { itemRepository.setStatus(id, it.key, requestReflection = true) }
    }
    fun snooze(id: Long, at: Long) = perform(id, "Reminder snoozed") {
        require(at > clock.millis()) { "Choose a future time." }
        val item = itemRepository.get(id) ?: return@perform
        if (item.isArchived || item.collectionId?.let { collectionRepository.get(it)?.schema?.isTerminal(item.status) } == true) return@perform
        itemRepository.setReminder(id, at)
    }
    fun tomorrowMorning(): Long = LocalDate.now(clock).plusDays(1).atTime(LocalTime.of(9, 0)).atZone(clock.zone).toInstant().toEpochMilli()
    private fun perform(id: Long, label: String, action: suspend () -> Unit) {
        // Enter the guard on the UI thread; persistence outlives navigation.
        if (!activeActions.add(id)) return
        applicationScope.launch(Dispatchers.Main.immediate) {
            try { actions.change(id, label, action) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { actions.report("Couldn't update this item. Please try again.") }
            finally { activeActions.remove(id) }
        }
    }
}
