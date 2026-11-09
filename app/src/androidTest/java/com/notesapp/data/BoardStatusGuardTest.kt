package com.notesapp.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.notesapp.data.repository.NewItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BoardStatusGuardTest : DataTestBase() {
    @Test fun staleDropCannotOverwriteANewerStatusOrCollection() = runBlocking {
        val movies = collection(); val books = collection("books")
        val id = env.items.create(NewItem("Moved during pickup", movies))
        env.items.setStatus(id, "watching")
        env.items.setStatus(id, "watched", expectedStatus = movies.schema.initial.key, expectedCollectionId = movies.id)
        assertEquals("watching", env.items.get(id)!!.status)
        env.items.moveToCollection(listOf(id), books)
        env.items.setStatus(id, "reading", expectedStatus = books.schema.initial.key, expectedCollectionId = movies.id)
        assertEquals(books.schema.initial.key, env.items.get(id)!!.status)
    }
    @Test fun archivedOrMissingTargetCannotBeChangedByADrop() = runBlocking {
        val movies = collection(); val id = env.items.create(NewItem("Archived during pickup", movies))
        env.items.setArchived(id, true)
        env.items.setStatus(id, "watching", expectedStatus = movies.schema.initial.key, expectedCollectionId = movies.id)
        assertEquals(movies.schema.initial.key, env.items.get(id)!!.status)
        env.items.setArchived(id, false)
        env.items.setStatus(id, "removed-status", expectedStatus = movies.schema.initial.key, expectedCollectionId = movies.id)
        assertEquals(movies.schema.initial.key, env.items.get(id)!!.status)
    }
}
