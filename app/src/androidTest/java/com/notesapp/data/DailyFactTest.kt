package com.notesapp.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.notesapp.domain.model.MasteryState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DailyFactTest : DataTestBase() {

    @Test
    fun noFactsMeansNoDailyFact() = runTest {
        assertNull(env.rotateDailyFact.ensureToday())
        assertNull(env.rotateDailyFact.next())
    }

    @Test
    fun theSameFactIsKeptForTheWholeDayAndCountsAsOneImpression() = runTest {
        env.facts.create("Fact A")
        env.facts.create("Fact B")

        val today = checkNotNull(env.rotateDailyFact.ensureToday())
        repeat(3) { assertEquals(today, env.rotateDailyFact.ensureToday()) }

        assertEquals(1, checkNotNull(env.facts.get(today)).shownCount)
        assertNotNull(env.facts.get(today)!!.lastShownAt)
    }

    @Test
    fun everyFactIsShownOnceBeforeAnyRepeats() = runTest {
        val ids = listOf(env.facts.create("A"), env.facts.create("B"), env.facts.create("C"))

        val shown = (1..3).map {
            val id = checkNotNull(env.rotateDailyFact.ensureToday())
            env.clock.advanceDays(1)
            id
        }
        assertEquals(ids.toSet(), shown.toSet())

        // Day four starts the second lap with the least recently shown fact, which is the first one shown.
        assertEquals(shown.first(), env.rotateDailyFact.ensureToday())
    }

    @Test
    fun nextFactSwapsTodaysFactAndPrefersUnseenOnes() = runTest {
        env.facts.create("A")
        env.facts.create("B")
        env.facts.create("C")

        val first = checkNotNull(env.rotateDailyFact.ensureToday())
        val second = checkNotNull(env.rotateDailyFact.next())
        val third = checkNotNull(env.rotateDailyFact.next())

        assertEquals(3, setOf(first, second, third).size)
        assertEquals("the swap is remembered for the rest of the day", third, env.rotateDailyFact.ensureToday())
    }

    @Test
    fun aSingleFactIsReshownRatherThanNothing() = runTest {
        val only = env.facts.create("Only")
        assertEquals(only, env.rotateDailyFact.ensureToday())
        assertEquals(only, env.rotateDailyFact.next())
    }

    @Test
    fun aDeletedFactIsReplacedImmediately() = runTest {
        val a = env.facts.create("A")
        val b = env.facts.create("B")
        val today = checkNotNull(env.rotateDailyFact.ensureToday())

        env.facts.delete(today)

        assertEquals(if (today == a) b else a, env.rotateDailyFact.ensureToday())
    }

    @Test
    fun hiddenFactsAreNeverChosenAndReplaceTodaysFact() = runTest {
        val a = env.facts.create("A")
        val b = env.facts.create("B")
        val today = checkNotNull(env.rotateDailyFact.ensureToday())

        env.facts.setMastery(today, MasteryState.HIDDEN)

        val replacement = checkNotNull(env.rotateDailyFact.ensureToday())
        assertNotEquals(today, replacement)
        assertEquals(setOf(a, b), setOf(today, replacement))
    }

    @Test
    fun aFactAddedLaterBecomesTodaysFactWhenNothingWasShown() = runTest {
        assertNull(env.rotateDailyFact.ensureToday())
        val id = env.facts.create("Late")
        assertEquals(id, env.rotateDailyFact.ensureToday())
    }

    @Test
    fun factEditsKeepCountersAndMastery() = runTest {
        val id = env.facts.create("Original", topic = "Science")
        env.facts.markShown(id)
        env.facts.setMastery(id, MasteryState.REVIEWING)
        env.facts.setFavorite(id, true)

        env.facts.saveDetails(id, "Edited", "A book", "History", "my note", listOf("trivia"))

        val fact = checkNotNull(env.facts.get(id))
        assertEquals("Edited", fact.text)
        assertEquals("A book", fact.source)
        assertEquals("History", fact.topic)
        assertEquals("my note", fact.personalNote)
        assertEquals(1, fact.shownCount)
        assertEquals(MasteryState.REVIEWING, fact.masteryState)
        assertTrue(fact.isFavorite)
        assertEquals(listOf("trivia"), env.facts.observe(id).first()!!.tags)
    }

    @Test
    fun topicsAreListedOncePerNameIgnoringCaseAndBlanks() = runTest {
        env.facts.create("a", topic = "Space")
        env.facts.create("b", topic = "space")
        env.facts.create("c", topic = "Animals")
        env.facts.create("d", topic = "  ")
        env.facts.create("e")

        assertEquals(listOf("Animals", "Space"), env.facts.observeTopics().first())
    }
}
