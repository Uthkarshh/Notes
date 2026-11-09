package com.notesapp.domain.usecase

import com.notesapp.data.prefs.SettingsRepository
import com.notesapp.data.repository.FactRepository
import com.notesapp.domain.facts.FactRotation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chooses the "morning fact": one fact per calendar day, picked by [FactRotation] and remembered in settings.
 * Each pick counts as an impression so the rotation moves on to facts the user has not seen yet.
 */
@Singleton
class RotateDailyFact @Inject constructor(
    private val facts: FactRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    private val lock = Mutex()

    /** Makes sure a fact is selected for today and returns its id, or null when no fact can be shown. */
    suspend fun ensureToday(): Long? = lock.withLock {
        val today = LocalDate.now(clock).toEpochDay()
        val state = settings.dailyFact.first()
        val candidates = facts.rotationCandidates()
        val current = state.factId?.let { id -> candidates.firstOrNull { it.id == id } }
        if (state.epochDay == today && current != null) return@withLock current.id
        pickAndStore(excludeId = state.factId, today = today)
    }

    /** Replaces today's fact with the next one in the rotation ("Next Fact"). */
    suspend fun next(): Long? = lock.withLock {
        val today = LocalDate.now(clock).toEpochDay()
        pickAndStore(excludeId = settings.dailyFact.first().factId, today = today)
    }

    suspend fun forNotification(topic: String?, source: String?): Long? = lock.withLock {
        val candidates = facts.rotationCandidates().filter {
            (topic == null || it.topic.equals(topic, true)) && (source == null || it.source?.contains(source, true) == true)
        }
        val today = LocalDate.now(clock).toEpochDay()
        val state = settings.dailyFact.first()
        if (state.epochDay == today && candidates.any { it.id == state.factId }) return@withLock state.factId
        val pick = FactRotation.pick(candidates, state.factId) ?: return@withLock null
        facts.markShown(pick.id)
        settings.setDailyFact(pick.id, today)
        pick.id
    }

    private suspend fun pickAndStore(excludeId: Long?, today: Long): Long? {
        val pick = FactRotation.pick(facts.rotationCandidates(), excludeId)
        if (pick == null) {
            settings.clearDailyFact()
            return null
        }
        facts.markShown(pick.id)
        settings.setDailyFact(pick.id, today)
        return pick.id
    }
}
