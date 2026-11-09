package com.notesapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.notesapp.data.db.entity.SearchFtsEntity

@Dao
interface SearchDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: SearchFtsEntity)

    @Query("DELETE FROM search_fts WHERE rowid = :rowId")
    suspend fun delete(rowId: Long)

    @Query("DELETE FROM search_fts WHERE rowid IN (:rowIds)")
    suspend fun deleteAll(rowIds: List<Long>)

    @Query("DELETE FROM search_fts")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM search_fts")
    suspend fun count(): Int

    /** [match] must already be a sanitised FTS4 expression (see FtsQueryBuilder). Matches are wrapped in private-use markers. */
    @Query(
        "SELECT entityType, originalId, title, snippet(search_fts, '\uE000', '\uE001', '…', 1, 14) AS snippet " +
            "FROM search_fts WHERE search_fts MATCH :match AND (entityType != 'ITEM' OR originalId NOT IN (SELECT itemId FROM pending_item_deletions)) ORDER BY originalId DESC LIMIT :limit",
    )
    suspend fun search(match: String, limit: Int): List<SearchHitRow>
}
