package com.notesapp.domain.facts

import com.notesapp.data.db.entity.FactEntity
import com.notesapp.domain.model.MasteryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FactRotationTest {

    private fun fact(
        id: Long,
        shown: Int = 0,
        lastShownAt: Long? = null,
        mastery: MasteryState = MasteryState.NEW,
    ) = FactEntity(
        id = id,
        text = "fact $id",
        masteryState = mastery,
        shownCount = shown,
        lastShownAt = lastShownAt,
        createdAt = 0,
    )

    @Test
    fun `unshown facts come before shown ones`() {
        val picked = FactRotation.pick(listOf(fact(1, shown = 1, lastShownAt = 10), fact(2), fact(3, shown = 2, lastShownAt = 5)))
        assertEquals(2L, picked?.id)
    }

    @Test
    fun `among equally shown facts the least recently shown wins`() {
        val picked = FactRotation.pick(
            listOf(fact(1, shown = 1, lastShownAt = 300), fact(2, shown = 1, lastShownAt = 100), fact(3, shown = 1, lastShownAt = 200)),
        )
        assertEquals(2L, picked?.id)
    }

    @Test
    fun `ties fall back to the lowest id so the order is stable`() {
        assertEquals(1L, FactRotation.pick(listOf(fact(3), fact(1), fact(2)))?.id)
    }

    @Test
    fun `hidden facts are never picked`() {
        val picked = FactRotation.pick(listOf(fact(1, mastery = MasteryState.HIDDEN), fact(2, shown = 5)))
        assertEquals(2L, picked?.id)
        assertNull(FactRotation.pick(listOf(fact(1, mastery = MasteryState.HIDDEN))))
    }

    @Test
    fun `learned facts resurface less often`() {
        val learnedNeverShown = fact(1, mastery = MasteryState.LEARNED)
        val newShownTwice = fact(2, shown = 2, lastShownAt = 50)
        assertEquals(2L, FactRotation.pick(listOf(learnedNeverShown, newShownTwice))?.id)
    }

    @Test
    fun `learned facts still appear once everything else was shown more`() {
        val learnedNeverShown = fact(1, mastery = MasteryState.LEARNED)
        val newShownFour = fact(2, shown = 4, lastShownAt = 50)
        assertEquals(1L, FactRotation.pick(listOf(learnedNeverShown, newShownFour))?.id)
    }

    @Test
    fun `the fact on screen is skipped unless it is the only one`() {
        assertEquals(2L, FactRotation.pick(listOf(fact(1), fact(2, shown = 3)), excludeId = 1)?.id)
        assertEquals(1L, FactRotation.pick(listOf(fact(1)), excludeId = 1)?.id)
    }

    @Test
    fun `nothing to show`() {
        assertNull(FactRotation.pick(emptyList()))
    }

    @Test
    fun `random draw ignores hidden facts and avoids the current one`() {
        val facts = listOf(fact(1), fact(2), fact(3, mastery = MasteryState.HIDDEN))
        repeat(30) { seed ->
            assertEquals(2L, FactRotation.drawRandom(facts, excludeId = 1, random = kotlin.random.Random(seed))?.id)
        }
        assertEquals(1L, FactRotation.drawRandom(listOf(fact(1)), excludeId = 1)?.id)
        assertNull(FactRotation.drawRandom(listOf(fact(1, mastery = MasteryState.HIDDEN))))
    }

    @Test
    fun `random draw can reach every showable fact`() {
        val facts = listOf(fact(1), fact(2), fact(3))
        val random = kotlin.random.Random(7)
        val seen = (1..100).map { FactRotation.drawRandom(facts, random = random)!!.id }.toSet()
        assertEquals(setOf(1L, 2L, 3L), seen)
    }
}
