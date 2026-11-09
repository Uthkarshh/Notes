package com.notesapp.ui.search

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import com.notesapp.data.repository.SearchRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightTest {

    private val style = SpanStyle(fontWeight = FontWeight.Bold)
    private val start = SearchRepository.HIGHLIGHT_START
    private val end = SearchRepository.HIGHLIGHT_END

    @Test
    fun `matched words are styled and markers are removed`() {
        val result = highlightSnippet("pack a ${start}tripod$end today", style)
        assertEquals("pack a tripod today", result.text)
        assertEquals(1, result.spanStyles.size)
        assertEquals(7, result.spanStyles.single().start)
        assertEquals(13, result.spanStyles.single().end)
    }

    @Test
    fun `several matches are all styled`() {
        val result = highlightSnippet("${start}a$end b ${start}c$end", style)
        assertEquals("a b c", result.text)
        assertEquals(listOf(0 to 1, 4 to 5), result.spanStyles.map { it.start to it.end })
    }

    @Test
    fun `text without markers is unchanged`() {
        val result = highlightSnippet("nothing to see", style)
        assertEquals("nothing to see", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun `stray end marker is ignored`() {
        val result = highlightSnippet("x${end}y", style)
        assertEquals("xy", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun `unterminated match is still closed`() {
        val result = highlightSnippet("x ${start}y", style)
        assertEquals("x y", result.text)
        assertEquals(2, result.spanStyles.single().start)
        assertEquals(3, result.spanStyles.single().end)
    }

    @Test
    fun `nested start markers do not stack`() {
        val result = highlightSnippet("${start}a${start}b$end", style)
        assertEquals("ab", result.text)
        assertEquals(1, result.spanStyles.size)
    }
}
