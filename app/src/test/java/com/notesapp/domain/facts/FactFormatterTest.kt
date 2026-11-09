package com.notesapp.domain.facts

import org.junit.Assert.assertEquals
import org.junit.Test

class FactFormatterTest {

    @Test
    fun `quotes the fact and credits the source`() {
        assertEquals(
            "“Octopuses have three hearts”\n— Marine biology",
            FactFormatter.shareText("Octopuses have three hearts", "Marine biology"),
        )
    }

    @Test
    fun `omits the credit line without a source`() {
        assertEquals("“Honey never spoils”", FactFormatter.shareText("Honey never spoils", null))
        assertEquals("“Honey never spoils”", FactFormatter.shareText("Honey never spoils", "   "))
    }

    @Test
    fun `trims whitespace`() {
        assertEquals("“A fact”\n— Someone", FactFormatter.shareText("  A fact \n", " Someone "))
    }
}
