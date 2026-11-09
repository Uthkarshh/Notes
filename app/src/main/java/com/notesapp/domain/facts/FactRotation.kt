package com.notesapp.domain.facts

import com.notesapp.data.db.entity.FactEntity
import com.notesapp.domain.model.MasteryState
import kotlin.random.Random

/**
 * Decides which fact to show next. Unshown facts come first, then the least-recently shown;
 * facts marked Learned are shown less often and Hidden facts never.
 */
object FactRotation {

    /** A learned fact counts as if it had already been shown this many extra times. */
    private const val LEARNED_PENALTY = 3

    /**
     * Picks the next fact from [candidates]. [excludeId] (the fact currently on screen) is skipped
     * unless it is the only candidate. Returns null when there is nothing to show.
     */
    fun pick(candidates: List<FactEntity>, excludeId: Long? = null): FactEntity? {
        val pool = candidates.filter { it.masteryState != MasteryState.HIDDEN }
        val preferred = pool.filter { it.id != excludeId }.ifEmpty { pool }
        return preferred.minWithOrNull(
            compareBy<FactEntity>(
                { effectiveShownCount(it) },
                { it.lastShownAt ?: Long.MIN_VALUE },
                { it.id },
            ),
        )
    }

    private fun effectiveShownCount(fact: FactEntity): Int =
        fact.shownCount + if (fact.masteryState == MasteryState.LEARNED) LEARNED_PENALTY else 0

    /**
     * Uniformly random fact for the manual "draw a fact" action. Hidden facts are ignored and the fact currently
     * shown ([excludeId]) is avoided unless it is the only choice.
     */
    fun drawRandom(candidates: List<FactEntity>, excludeId: Long? = null, random: Random = Random.Default): FactEntity? {
        val pool = candidates.filter { it.masteryState != MasteryState.HIDDEN }
        val options = pool.filter { it.id != excludeId }.ifEmpty { pool }
        return if (options.isEmpty()) null else options[random.nextInt(options.size)]
    }
}
