package com.notesapp.domain.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FtsQueryBuilderTest {

    @Test
    fun `single word becomes a prefix term`() {
        assertEquals("matrix*", FtsQueryBuilder.build("matrix"))
    }

    @Test
    fun `every word must match as a prefix`() {
        assertEquals("dark* knight*", FtsQueryBuilder.build("Dark   Knight"))
    }

    @Test
    fun `fts syntax characters and operators cannot alter the query`() {
        assertEquals("dark* or* knight*", FtsQueryBuilder.build("\"dark\" OR -knight*"))
        assertEquals("title* foo*", FtsQueryBuilder.build("title:foo"))
        assertEquals("near* 2* a*", FtsQueryBuilder.build("NEAR/2 (a)"))
    }

    @Test
    fun `blank and symbol-only input is not searchable`() {
        assertNull(FtsQueryBuilder.build(""))
        assertNull(FtsQueryBuilder.build("   "))
        assertNull(FtsQueryBuilder.build("\"*-()"))
    }

    @Test
    fun `unicode letters are kept`() {
        assertEquals("café* 映画*", FtsQueryBuilder.build("Café 映画"))
    }

    @Test
    fun `digits are kept`() {
        assertEquals("2001*", FtsQueryBuilder.build("2001"))
    }

    @Test
    fun `the number of terms is capped`() {
        val query = FtsQueryBuilder.build((1..20).joinToString(" ") { "w$it" })!!
        assertEquals(8, query.split(' ').size)
    }

    @Test
    fun `very long terms are truncated`() {
        val query = FtsQueryBuilder.build("a".repeat(200))!!
        assertEquals(64 + 1, query.length)
    }
}
