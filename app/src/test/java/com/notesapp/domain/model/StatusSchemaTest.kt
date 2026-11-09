package com.notesapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusSchemaTest {

    private val schema = StatusSchema(
        listOf(
            StatusSchema.status("Want to Watch", StatusRole.BACKLOG),
            StatusSchema.status("Watching", StatusRole.ACTIVE),
            StatusSchema.status("Watched", StatusRole.DONE),
            StatusSchema.status("Dropped", StatusRole.DROPPED),
        ),
    )

    @Test
    fun `json round trip preserves the schema`() {
        assertEquals(schema, StatusSchema.fromJson(schema.toJson()))
    }

    @Test
    fun `exposes the roles used by the app`() {
        assertEquals("want_to_watch", schema.initial.key)
        assertEquals("watching", schema.startStatus?.key)
        assertEquals("watched", schema.completionStatus?.key)
    }

    @Test
    fun `terminal statuses are done and dropped only`() {
        assertFalse(schema.isTerminal("want_to_watch"))
        assertFalse(schema.isTerminal("watching"))
        assertTrue(schema.isTerminal("watched"))
        assertTrue(schema.isTerminal("dropped"))
    }

    @Test
    fun `unknown status keys count as not started`() {
        assertEquals(StatusRole.BACKLOG, schema.roleOf("INBOX"))
        assertEquals("INBOX", schema.labelOf("INBOX"))
        assertNull(schema.find("INBOX"))
    }

    @Test
    fun `initial falls back to the first status when nothing is a backlog`() {
        val onlyActive = StatusSchema(listOf(StatusSchema.status("Going", StatusRole.ACTIVE)))
        assertEquals("going", onlyActive.initial.key)
    }

    @Test
    fun `corrupt or empty json falls back to the default schema`() {
        assertEquals(StatusSchema.DEFAULT, StatusSchema.fromJson("not json"))
        assertEquals(StatusSchema.DEFAULT, StatusSchema.fromJson("{\"statuses\":[]}"))
        assertEquals(StatusSchema.DEFAULT, StatusSchema.fromJson(""))
    }

    @Test
    fun `unknown json fields are ignored`() {
        val json = """{"statuses":[{"key":"a","label":"A","role":"BACKLOG","extra":1}],"future":true}"""
        assertEquals("a", StatusSchema.fromJson(json).initial.key)
    }

    @Test
    fun `keys are derived from labels`() {
        assertEquals("want_to_watch", StatusSchema.keyFor("Want to Watch"))
        assertEquals("in_progress", StatusSchema.keyFor("  In   Progress! "))
        assertEquals("status", StatusSchema.keyFor("!!"))
    }

    @Test
    fun `unique keys get a numeric suffix`() {
        assertEquals("done", StatusSchema.uniqueKey("done", emptySet()))
        assertEquals("done_2", StatusSchema.uniqueKey("done", setOf("done")))
        assertEquals("done_3", StatusSchema.uniqueKey("done", setOf("done", "done_2")))
    }

    @Test
    fun `fallback for a removed status prefers the same role`() {
        val removed = StatusSchema.status("Abandoned", StatusRole.DROPPED)
        assertEquals("dropped", schema.fallbackFor(removed).key)
        val noDropped = StatusSchema(schema.statuses.filter { it.role != StatusRole.DROPPED })
        assertEquals("want_to_watch", noDropped.fallbackFor(removed).key)
    }

    @Test
    fun `default schema has all four roles`() {
        assertEquals(
            StatusRole.entries.toSet(),
            StatusSchema.DEFAULT.statuses.map { it.role }.toSet(),
        )
    }
}
