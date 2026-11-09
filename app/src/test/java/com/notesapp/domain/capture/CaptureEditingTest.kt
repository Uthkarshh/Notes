package com.notesapp.domain.capture

import com.notesapp.domain.model.Priority
import org.junit.Assert.*
import org.junit.Test

class CaptureEditingTest {
    @Test fun rangesPointToUnchangedUtf16InputAndUrlsDoNotCreateTokens() {
        val raw = "🎬 Watch Arrival #日本語 !high https://example.com/#fragment?q=!low"
        val parsed = QuickCaptureParser.parse(raw)
        assertEquals(listOf("日本語"), parsed.tags)
        assertEquals(Priority.HIGH, parsed.priority)
        assertEquals(listOf("#日本語", "!high", "https://example.com/#fragment?q=!low"), parsed.tokens.map { raw.substring(it.start, it.end) })
        assertEquals("🎬 Watch Arrival", parsed.title)
    }
    @Test fun incompleteMarkersRemainLiteralAndAreNotHighlightedAsComplete() {
        val parsed = QuickCaptureParser.parse("Task # !h @")
        assertEquals("Task # !h @", parsed.title)
        assertNull(parsed.priority)
        assertTrue(parsed.tags.isEmpty())
        assertTrue(parsed.tokens.all { it.kind == CaptureTokenKind.UNRESOLVED })
    }
    @Test fun escapedMarkersBecomeLiteralText() {
        val parsed = QuickCaptureParser.parse("""Keep \#literal \!high \@books #real""")
        assertEquals("Keep #literal !high @books", parsed.title)
        assertEquals(listOf("real"), parsed.tags)
        assertNull(parsed.priority)
        assertNull(parsed.collectionQuery)
    }
    @Test fun unknownCollectionIsPreservedInTitleWithNoSilentFallback() {
        val parsed = QuickCaptureParser.parse("Watch Arrival @missing #scifi", emptyList())
        assertEquals("Arrival @missing", parsed.title)
        assertTrue(parsed.unresolvedCollection)
        assertNull(QuickCaptureParser.resolveCollection(parsed, emptyList()))
        assertEquals(CaptureTokenKind.UNRESOLVED, parsed.tokens.first { it.value == "missing" }.kind)
    }
    @Test fun priorityChoiceRemovesEveryPreviousPriorityAndLeavesOne() {
        val raw = "Task !low #work !urgent"
        val edit = CaptureEdits.priority(raw, 4, 4, Priority.HIGH)
        val parsed = QuickCaptureParser.parse(edit.text)
        assertEquals("Task", parsed.title)
        assertEquals(Priority.HIGH, parsed.priority)
        assertEquals(1, parsed.tokens.count { it.kind == CaptureTokenKind.PRIORITY })
        assertEquals(listOf("work"), parsed.tags)
        val cleared = CaptureEdits.priority(edit.text, edit.cursor, edit.cursor, Priority.NONE)
        assertNull(QuickCaptureParser.parse(cleared.text).priority)
    }
    @Test fun transcriptReplacesOnlyTheCurrentSelection() {
        val edit = CaptureEdits.insert("Read Dune tomorrow", 5, 9, "The Dispossessed")
        assertEquals("Read The Dispossessed tomorrow", edit.text)
        assertEquals("Read The Dispossessed".length, edit.cursor)
    }
    @Test fun insertingMidSentencePreservesBothSidesAndSeparatesTokens() {
        val edit = CaptureEdits.insert("Read Dune", 5, 5, "#fiction")
        assertEquals("Read #fiction Dune", edit.text)
        assertEquals("Dune", QuickCaptureParser.parse(edit.text).title)
    }
    @Test fun removalUsesOnlySourceRangesAndMapsTheCaret() {
        val raw = "Task #one #two"
        val token = QuickCaptureParser.parse(raw).tokens.first { it.value == "one" }
        val edit = CaptureEdits.remove(raw, raw.length, listOf(token))
        assertEquals("Task #two", edit.text)
        assertEquals(edit.text.length, edit.cursor)
    }
}
