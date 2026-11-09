package com.notesapp.domain.template

import com.notesapp.domain.model.StatusRole
import com.notesapp.domain.model.StatusSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionTemplatesTest {

    @Test
    fun `template ids are unique`() {
        val ids = CollectionTemplates.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `every template has a usable lifecycle`() {
        CollectionTemplates.all.forEach { template ->
            val statuses = template.schema.statuses
            val keys = statuses.map { it.key }
            assertEquals("${template.id} has duplicate status keys", keys.size, keys.toSet().size)
            assertTrue("${template.id} needs a not-started status", statuses.any { it.role == StatusRole.BACKLOG })
            assertTrue("${template.id} needs a completed status", statuses.any { it.role == StatusRole.DONE })
            assertTrue("${template.id} needs an in-progress status", statuses.any { it.role == StatusRole.ACTIVE })
            assertEquals(template.schema, StatusSchema.fromJson(template.schema.toJson()))
        }
    }

    @Test
    fun `colors look like hex rgb`() {
        CollectionTemplates.all.forEach {
            assertTrue("${it.id}: ${it.colorHex}", Regex("#[0-9A-Fa-f]{6}").matches(it.colorHex))
        }
    }

    @Test
    fun `default collections exist and cover the quick bar categories`() {
        val defaults = CollectionTemplates.defaults
        assertEquals(CollectionTemplates.defaultIds.size, defaults.size)
        assertEquals(
            setOf("MOVIE", "BOOK", "PLACE", "BUY"),
            defaults.map { it.categoryType.name }.toSet(),
        )
    }

    @Test
    fun `spec statuses are present for the core templates`() {
        fun labels(id: String) = CollectionTemplates.byId(id)!!.schema.statuses.map { it.label }
        assertEquals(listOf("Want to Watch", "Watching", "Watched", "Dropped"), labels("movies"))
        assertEquals(listOf("Want to Read", "Reading", "Finished", "Abandoned"), labels("books"))
        assertEquals(listOf("Want to Visit", "Planned", "Visited", "Closed"), labels("places"))
        assertEquals(listOf("Considering", "To Buy", "Bought", "Passed"), labels("things_to_buy"))
        assertNotNull(CollectionTemplates.byId("custom"))
    }
}
