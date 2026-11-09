package com.notesapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagNamesTest {

    @Test
    fun `normalize strips hashes, whitespace and invalid characters`() {
        assertEquals("Sci-Fi", TagNames.normalize("#Sci-Fi "))
        assertEquals("weekend", TagNames.normalize("weekend!"))
        assertEquals("a_b", TagNames.normalize("a_b"))
    }

    @Test
    fun `normalize rejects empty results`() {
        assertNull(TagNames.normalize(""))
        assertNull(TagNames.normalize("###"))
        assertNull(TagNames.normalize("!!!"))
    }

    @Test
    fun `normalize limits the length`() {
        assertEquals(TagNames.MAX_LENGTH, TagNames.normalize("x".repeat(100))!!.length)
    }

    @Test
    fun `parseList splits on commas, spaces and hashes and removes duplicates`() {
        assertEquals(
            listOf("weekend", "family", "scifi"),
            TagNames.parseList("weekend, family #scifi  Weekend"),
        )
    }

    @Test
    fun `merge keeps the first spelling`() {
        assertEquals(listOf("Sci", "fun"), TagNames.merge(listOf("Sci"), listOf("sci", "fun")))
    }

    @Test
    fun `toTagList sorts case-insensitively and skips blanks`() {
        assertEquals(listOf("alpha", "Beta", "gamma"), "gamma,Beta,,alpha".toTagList())
        assertEquals(emptyList<String>(), (null as String?).toTagList())
    }
}
