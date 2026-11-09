package com.notesapp.ui.surprise

import com.notesapp.data.db.entity.CollectionEntity
import com.notesapp.data.db.entity.SelectionAction
import com.notesapp.data.repository.CollectionRepository
import com.notesapp.data.repository.ItemRepository
import com.notesapp.domain.model.ItemWithTags
import com.notesapp.domain.random.PickRandomItem
import com.notesapp.domain.random.PickResult
import com.notesapp.domain.random.RandomFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.random.Random

/** How far the user got with the item that was drawn. */
enum class PickPhase { PICKED, STARTED, COMPLETED }

sealed interface SurpriseState {
    data object Idle : SurpriseState

    data class Showing(
        val entry: ItemWithTags,
        val collection: CollectionEntity,
        /** How many items were available at this draw. */
        val matchCount: Int,
        val phase: PickPhase = PickPhase.PICKED,
        val historyId: Long = 0,
    ) : SurpriseState {
        val statusLabel: String get() = collection.schema.labelOf(entry.item.status)
    }

    /** Nothing satisfies the filter. */
    data class Failed(val message: String) : SurpriseState

    data object NoMatches : SurpriseState

    /** Everything that matches was already skipped with "Roll again". */
    data object Exhausted : SurpriseState
}

/**
 * One "Surprise Me" interaction: draw, roll again (skipped items are suppressed until the session ends),
 * accept & start, or mark completed. Used by both the Today quick bar and the Discover screen.
 */
class SurpriseSession(
    private val items: ItemRepository,
    private val collections: CollectionRepository,
    private val scope: CoroutineScope,
    private val random: Random = Random.Default,
) {
    private val _state = MutableStateFlow<SurpriseState>(SurpriseState.Idle)
    val state: StateFlow<SurpriseState> = _state.asStateFlow()

    private var filter = RandomFilter()
    private val skipped = mutableSetOf<Long>()
    private var job: Job? = null
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _pool = MutableStateFlow(DrawPool())
    val pool = _pool.asStateFlow()
    private var generation = 0
    private var at = items.now()
    private val originalLastSelected = mutableMapOf<Long, Long?>()

    init {
        scope.launch {
            combine(items.observeAllFiled(), collections.observeAll(), _busy, _state) { rows, allCollections, busy, state ->
                if (busy || state == SurpriseState.Idle) return@combine null
                val normalized = rows.map { row -> if (row.item.id in originalLastSelected)
                    row.copy(item = row.item.copy(lastSelectedAt = originalLastSelected[row.item.id])) else row }
                val eligible = PickRandomItem.candidates(normalized, allCollections, filter, at)
                val shownId = (state as? SurpriseState.Showing)?.entry?.item?.id
                DrawPool(eligible.size, eligible.count { it.first.item.id !in skipped && it.first.item.id != shownId })
            }.collect { pool -> if (pool != null) _pool.value = pool }
        }
    }

    /** Every command reserves the session synchronously, before a coroutine can suspend. */
    fun draw(filter: RandomFilter) {
        if (_busy.value) return
        this.filter = filter
        generation++
        at = items.now()
        skipped.clear()
        originalLastSelected.clear()
        pick()
    }

    fun rollAgain() {
        if (_busy.value) return
        val current = _state.value as? SurpriseState.Showing ?: return
        skipped += current.entry.item.id
        pick(current.takeIf { it.phase == PickPhase.PICKED })
    }

    fun startOver() {
        if (_busy.value) return
        draw(filter)
    }

    fun dismiss() {
        generation++
        skipped.clear()
        _state.value = SurpriseState.Idle
        _pool.value = DrawPool()
    }

    fun accept() = act(PickPhase.STARTED)
    fun complete() = act(PickPhase.COMPLETED)

    private fun act(phase: PickPhase) {
        val current = _state.value as? SurpriseState.Showing ?: return
        if (_busy.value || current.phase == PickPhase.COMPLETED ||
            phase == PickPhase.STARTED && current.phase != PickPhase.PICKED) return
        _busy.value = true
        val version = generation
        scope.launch {
            try {
                // A committed history/status operation must finish even if its screen goes away.
                withContext(NonCancellable) {
                    if (phase == PickPhase.STARTED) items.acceptPick(current.entry.item.id, current.historyId)
                    else items.completePick(current.entry.item.id, current.historyId)
                }
                if (generation == version) refresh(current.entry.item.id, phase)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { if (generation == version) _state.value = SurpriseState.Failed("This action could not finish. Try again.") }
            finally { _busy.value = false }
        }
    }

    private fun pick(skippedPick: SurpriseState.Showing? = null) {
        _busy.value = true
        val version = generation
        job = scope.launch {
            try {
                val next = withContext(NonCancellable) {
                    skippedPick?.let { items.updatePick(it.historyId, it.entry.item.id, SelectionAction.SKIPPED) }
                    val (result, historyId, eligibleCount) = items.pickAndRecord(filter, skipped.toSet(), random, at, originalLastSelected.toMap())
                    when (result) {
                        is PickResult.Picked -> {
                            if (historyId == null) SurpriseState.NoMatches else {
                                originalLastSelected.putIfAbsent(result.item.item.id, result.item.item.lastSelectedAt)
                                if (version == generation) _pool.value = DrawPool(eligibleCount, result.matchCount - 1)
                                SurpriseState.Showing(result.item, result.collection, result.matchCount, historyId = historyId)
                            }
                        }
                        PickResult.NoMatches -> { if (version == generation) _pool.value = DrawPool(); SurpriseState.NoMatches }
                        PickResult.Exhausted -> { if (version == generation) _pool.value = DrawPool(eligibleCount, 0); SurpriseState.Exhausted }
                    }
                }
                if (generation == version) _state.value = next
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { if (generation == version) _state.value = SurpriseState.Failed("This action could not finish. Try again.") }
            finally { _busy.value = false }
        }
    }

    private suspend fun refresh(itemId: Long, phase: PickPhase) {
        val updated = items.observeItem(itemId).first()?.takeUnless { it.item.isArchived }
        val collection = updated?.item?.collectionId?.let { id -> collections.getAll().firstOrNull { it.id == id } }
        val current = _state.value as? SurpriseState.Showing ?: return
        if (current.entry.item.id != itemId) return
        if (updated == null || collection == null) { _state.value = SurpriseState.NoMatches; return }
        val actual = collection.schema.roleOf(updated.item.status)
        val nextPhase = if (actual == com.notesapp.domain.model.StatusRole.DONE) PickPhase.COMPLETED
            else if (phase == PickPhase.COMPLETED) PickPhase.PICKED else phase
        _state.value = current.copy(entry = updated, collection = collection, phase = nextPhase)
    }
}

data class DrawPool(val eligible: Int = 0, val remaining: Int = 0)
