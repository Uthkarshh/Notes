package com.notesapp.data.repository

import androidx.room.withTransaction
import com.notesapp.data.db.AppDatabase
import com.notesapp.data.db.dao.FactDao
import com.notesapp.data.db.dao.FactRow
import com.notesapp.data.db.dao.TagDao
import com.notesapp.data.db.entity.FactEntity
import com.notesapp.domain.model.EntityType
import com.notesapp.domain.model.FactWithTags
import com.notesapp.domain.model.MasteryState
import com.notesapp.domain.model.toTagList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FactRepository @Inject constructor(
    private val db: AppDatabase,
    private val factDao: FactDao,
    private val tagDao: TagDao,
    private val tagWriter: TagWriter,
    private val indexer: SearchIndexer,
    private val clock: Clock,
) {

    fun observeAll(): Flow<List<FactWithTags>> = factDao.observeAll().map { rows -> rows.map(FactRow::toModel) }

    fun observe(id: Long): Flow<FactWithTags?> = factDao.observeRowById(id).map { it?.toModel() }

    fun observeTopics(): Flow<List<String>> = factDao.observeTopics()

    fun observeCount(): Flow<Int> = factDao.observeCount()

    fun observeShowableIds(): Flow<List<Long>> = factDao.observeShowableIds()

    fun observeTagUsage() = tagDao.observeUsage(EntityType.FACT)

    suspend fun get(id: Long): FactEntity? = factDao.getById(id)

    /** Every fact that may be shown (anything not hidden), for rotation and random draws. */
    suspend fun rotationCandidates(): List<FactEntity> = factDao.getRotationCandidates()

    suspend fun drawable(): List<FactWithTags> = factDao.getDrawableRows().map(FactRow::toModel)

    suspend fun create(
        text: String,
        source: String? = null,
        topic: String? = null,
        personalNote: String? = null,
        tags: List<String> = emptyList(),
        favorite: Boolean = false,
        mastery: MasteryState = MasteryState.NEW,
    ): Long = db.withTransaction {
        val entity = FactEntity(
            text = text.trim(),
            source = source.clean(),
            topic = topic.clean(),
            personalNote = personalNote.clean(),
            isFavorite = favorite,
            masteryState = mastery,
            createdAt = clock.millis(),
        )
        val id = factDao.insert(entity)
        tagWriter.replace(EntityType.FACT, id, tags)
        indexer.index(entity.copy(id = id), tagNames(id))
        id
    }

    /** Saves the editable fields; mastery, favorite and impression counters are left untouched. */
    suspend fun saveDetails(
        id: Long,
        text: String,
        source: String?,
        topic: String?,
        personalNote: String?,
        tags: List<String>,
    ) = db.withTransaction {
        val current = factDao.getById(id) ?: return@withTransaction
        val updated = current.copy(
            text = text.trim().ifEmpty { current.text },
            source = source.clean(),
            topic = topic.clean(),
            personalNote = personalNote.clean(),
        )
        factDao.update(updated)
        tagWriter.replace(EntityType.FACT, id, tags)
        indexer.index(updated, tagNames(id))
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) = factDao.setFavorite(id, favorite)

    suspend fun setMastery(id: Long, state: MasteryState) = factDao.setMastery(id, state)

    /** Records an impression (morning fact or manual draw) for the rotation logic. */
    suspend fun markShown(id: Long) = factDao.markShown(id, clock.millis())

    suspend fun delete(id: Long) = db.withTransaction {
        db.phase2Dao().removeLinks(EntityType.FACT, listOf(id))
        factDao.deleteById(id)
        indexer.remove(EntityType.FACT, listOf(id))
        tagWriter.removeAll(EntityType.FACT, listOf(id))
    }

    private suspend fun tagNames(id: Long): List<String> = tagDao.tagsFor(EntityType.FACT, id).map { it.name }

    private fun String?.clean(): String? = this?.trim()?.ifEmpty { null }
}

internal fun FactRow.toModel() = FactWithTags(fact, tagNames.toTagList())
