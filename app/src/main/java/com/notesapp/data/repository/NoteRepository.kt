package com.notesapp.data.repository

import androidx.room.withTransaction
import com.notesapp.data.db.AppDatabase
import com.notesapp.data.db.dao.NoteDao
import com.notesapp.data.db.dao.NoteRow
import com.notesapp.data.db.dao.TagDao
import com.notesapp.data.db.entity.NoteEntity
import com.notesapp.domain.model.EntityType
import com.notesapp.domain.model.NoteWithTags
import com.notesapp.domain.model.toTagList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepository @Inject constructor(
    private val db: AppDatabase,
    private val noteDao: NoteDao,
    private val tagDao: TagDao,
    private val tagWriter: TagWriter,
    private val indexer: SearchIndexer,
    private val clock: Clock,
) {

    fun observeAll(): Flow<List<NoteWithTags>> = noteDao.observeAll().map { rows -> rows.map(NoteRow::toModel) }

    fun observe(id: Long): Flow<NoteWithTags?> = noteDao.observeRowById(id).map { it?.toModel() }

    fun observeTagUsage() = tagDao.observeUsage(EntityType.NOTE)

    suspend fun get(id: Long): NoteEntity? = noteDao.getById(id)

    suspend fun create(
        title: String,
        content: String,
        tags: List<String> = emptyList(),
        pinned: Boolean = false,
    ): Long = db.withTransaction {
        val now = clock.millis()
        val entity = NoteEntity(title = title.trim(), content = content, isPinned = pinned, createdAt = now, updatedAt = now)
        val id = noteDao.insert(entity)
        tagWriter.replace(EntityType.NOTE, id, tags)
        indexer.index(entity.copy(id = id), tagNames(id))
        id
    }

    suspend fun saveDetails(id: Long, title: String, content: String, tags: List<String>) = db.withTransaction {
        val current = noteDao.getById(id) ?: return@withTransaction
        val updated = current.copy(title = title.trim(), content = content, updatedAt = clock.millis())
        noteDao.update(updated)
        tagWriter.replace(EntityType.NOTE, id, tags)
        indexer.index(updated, tagNames(id))
    }

    suspend fun setPinned(id: Long, pinned: Boolean) = noteDao.setPinned(id, pinned)

    suspend fun delete(id: Long) = db.withTransaction {
        db.phase2Dao().removeLinks(EntityType.NOTE, listOf(id))
        noteDao.deleteById(id)
        indexer.remove(EntityType.NOTE, listOf(id))
        tagWriter.removeAll(EntityType.NOTE, listOf(id))
    }

    private suspend fun tagNames(id: Long): List<String> = tagDao.tagsFor(EntityType.NOTE, id).map { it.name }
}

internal fun NoteRow.toModel() = NoteWithTags(note, tagNames.toTagList())
