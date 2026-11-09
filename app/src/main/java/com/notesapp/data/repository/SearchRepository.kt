package com.notesapp.data.repository

import com.notesapp.data.db.dao.SearchDao
import com.notesapp.domain.model.EntityType
import com.notesapp.domain.search.FtsQueryBuilder
import javax.inject.Inject
import javax.inject.Singleton

data class SearchHit(
    val type: EntityType,
    val id: Long,
    val title: String,
    /** Body excerpt where matches are wrapped in [HIGHLIGHT_START] / [HIGHLIGHT_END]; may be empty. */
    val snippet: String,
)

@Singleton
class SearchRepository @Inject constructor(private val searchDao: SearchDao) {

    /** Full-text search across items, notes and facts; blank or symbol-only input yields no results. */
    suspend fun search(text: String, limit: Int = DEFAULT_LIMIT): List<SearchHit> {
        val match = FtsQueryBuilder.build(text) ?: return emptyList()
        return searchDao.search(match, limit).mapNotNull { row ->
            val type = EntityType.entries.firstOrNull { it.name == row.entityType } ?: return@mapNotNull null
            SearchHit(type, row.originalId, row.title, row.snippet.orEmpty())
        }
    }

    companion object {
        const val DEFAULT_LIMIT = 100
        const val HIGHLIGHT_START = '\uE000'
        const val HIGHLIGHT_END = '\uE001'
    }
}
