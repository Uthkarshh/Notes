package com.notesapp.data.repository

import com.notesapp.data.db.dao.SearchDao
import com.notesapp.data.db.entity.FactEntity
import com.notesapp.data.db.entity.ItemEntity
import com.notesapp.data.db.entity.NoteEntity
import com.notesapp.data.db.entity.SearchFtsEntity
import com.notesapp.domain.model.EntityType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maintains the FTS4 table. Repositories call it inside the same transaction that writes the record, so the
 * index can never drift from the data.
 */
@Singleton
class SearchIndexer @Inject constructor(private val dao: SearchDao) {

    suspend fun index(item: ItemEntity, tags: List<String>) = put(
        EntityType.ITEM,
        item.id,
        title = item.title,
        body = joinParts(item.description, com.notesapp.domain.metadata.ItemMetadata.decode(item.metadataJson).let { joinParts(it.author, it.isbn, it.genre, it.streamingService, it.address, it.openingHours, it.storeUrl) }, tags.joinToString(" ")),
    )

    suspend fun index(fact: FactEntity, tags: List<String>) = put(
        EntityType.FACT,
        fact.id,
        title = fact.text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim().take(FACT_TITLE_LENGTH),
        body = joinParts(fact.text, fact.source, fact.topic, fact.personalNote, tags.joinToString(" ")),
    )

    suspend fun index(note: NoteEntity, tags: List<String>) = put(
        EntityType.NOTE,
        note.id,
        title = note.title,
        body = joinParts(note.content, tags.joinToString(" ")),
    )

    suspend fun remove(type: EntityType, ids: List<Long>) {
        ids.chunked(TagWriter.SQL_CHUNK).forEach { chunk -> dao.deleteAll(chunk.map { rowId(type, it) }) }
    }

    private suspend fun put(type: EntityType, id: Long, title: String, body: String) {
        dao.upsert(
            SearchFtsEntity(
                rowId = rowId(type, id),
                title = title,
                body = body,
                entityType = type.name,
                originalId = id,
            ),
        )
    }

    private fun joinParts(vararg parts: String?): String =
        parts.filterNot { it.isNullOrBlank() }.joinToString("\n")

    companion object {
        private const val FACT_TITLE_LENGTH = 80

        /** Unique, reversible rowid per (type, id): ids of different record types can never collide. */
        fun rowId(type: EntityType, id: Long): Long = id * 4 + when (type) {
            EntityType.ITEM -> 1
            EntityType.FACT -> 2
            EntityType.NOTE -> 3
        }
    }
}
