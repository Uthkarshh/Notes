package com.notesapp.data.repository

import com.notesapp.data.db.dao.TagDao
import com.notesapp.data.db.entity.EntityTagCrossRef
import com.notesapp.data.db.entity.TagEntity
import com.notesapp.domain.model.EntityType
import com.notesapp.domain.model.TagNames
import javax.inject.Inject

/** Keeps the tag tables in sync with the tag list of one record. Must be called inside the caller's transaction. */
class TagWriter @Inject constructor(private val tagDao: TagDao) {

    /** Replaces every tag on the record, creating missing tags and dropping tags nobody uses any more. */
    suspend fun replace(type: EntityType, id: Long, names: List<String>) {
        tagDao.deleteRefsFor(type, id)
        val clean = names.mapNotNull(TagNames::normalize).distinctBy { it.lowercase() }
        if (clean.isNotEmpty()) {
            val refs = clean.map { name -> EntityTagCrossRef(tagId = tagIdFor(name), targetId = id, targetType = type) }
            tagDao.insertRefs(refs)
        }
        tagDao.deleteOrphans()
    }

    /** Removes tag links of deleted records and then any tag that became unused. */
    suspend fun removeAll(type: EntityType, ids: List<Long>) {
        ids.chunked(SQL_CHUNK).forEach { tagDao.deleteRefsForAll(type, it) }
        tagDao.deleteOrphans()
    }

    private suspend fun tagIdFor(name: String): Long {
        tagDao.getByName(name)?.let { return it.id }
        val inserted = tagDao.insert(TagEntity(name = name))
        return if (inserted > 0) inserted else checkNotNull(tagDao.getByName(name)).id
    }

    companion object {
        /** Stays well below SQLite's bound-variable limit on older Android versions. */
        const val SQL_CHUNK = 500
    }
}
