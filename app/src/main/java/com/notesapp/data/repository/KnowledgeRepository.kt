package com.notesapp.data.repository

import androidx.room.withTransaction
import com.notesapp.data.db.AppDatabase
import com.notesapp.data.db.entity.KnowledgeLinkEntity
import com.notesapp.domain.model.EntityType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

@Singleton
class KnowledgeRepository @Inject constructor(private val db: AppDatabase, private val notes: NoteRepository) {
    fun observeNotes(itemId: Long) = db.phase2Dao().observeLinkedNotes(itemId)

    suspend fun reflect(itemId: Long, text: String): Long? = withContext(NonCancellable) { db.withTransaction {
        if (text.isBlank()) return@withTransaction null
        val item = db.itemDao().getById(itemId) ?: return@withTransaction null
        val tags = db.tagDao().tagsFor(EntityType.ITEM, itemId).map { it.name }
        val noteId = notes.create("Reflection: ${item.title}", text.trim(), tags)
        db.phase2Dao().insertLink(KnowledgeLinkEntity(noteId, EntityType.NOTE, itemId, EntityType.ITEM, "Completion reflection"))
        noteId
    } }
}
