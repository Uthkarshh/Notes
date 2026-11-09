package com.notesapp.data.db.dao

import androidx.room.Embedded
import com.notesapp.data.db.entity.FactEntity
import com.notesapp.data.db.entity.ItemEntity
import com.notesapp.data.db.entity.NoteEntity

/** Row types returned by queries that aggregate tag names next to the entity. */
data class ItemRow(@Embedded val item: ItemEntity, val tagNames: String?)

data class FactRow(@Embedded val fact: FactEntity, val tagNames: String?)

data class NoteRow(@Embedded val note: NoteEntity, val tagNames: String?)

data class StatusCount(val collectionId: Long, val status: String, val count: Int)

data class TagUsage(val name: String, val count: Int)

data class SearchHitRow(
    val entityType: String,
    val originalId: Long,
    val title: String,
    val snippet: String?,
)
