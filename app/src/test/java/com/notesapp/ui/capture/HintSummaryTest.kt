package com.notesapp.ui.capture

import com.notesapp.domain.capture.QuickCaptureParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HintSummaryTest {

    private fun summary(input: String) = hintSummary(QuickCaptureParser.parse(input))

    @Test
    fun `nothing recognised means no summary`() {
        assertNull(summary("Call the dentist"))
        assertNull(summary("Watch Arrival"))
    }

    @Test
    fun `lists tags, priority and link`() {
        assertEquals("#scifi #weekend · High priority", summary("Arrival #scifi #weekend !high"))
        assertEquals("link attached", summary("https://example.com/post"))
        assertEquals("#a · Urgent priority · link attached", summary("Read https://example.com #a !urgent"))
    }
}
