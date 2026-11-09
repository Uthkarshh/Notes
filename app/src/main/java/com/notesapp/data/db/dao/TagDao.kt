package com.notesapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.notesapp.data.db.entity.EntityTagCrossRef
import com.notesapp.data.db.entity.TagEntity
import com.notesapp.domain.model.EntityType
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    /** Returns -1 when a tag with the same (case-insensitive) name already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    @Query("SELECT * FROM tags WHERE name = :name")
    suspend fun getByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRefs(refs: List<EntityTagCrossRef>)

    @Query("DELETE FROM entity_tag_cross_ref WHERE targetType = :type AND targetId = :id")
    suspend fun deleteRefsFor(type: EntityType, id: Long)

    @Query("DELETE FROM entity_tag_cross_ref WHERE targetType = :type AND targetId IN (:ids)")
    suspend fun deleteRefsForAll(type: EntityType, ids: List<Long>)

    @Query(
        "SELECT t.* FROM tags t INNER JOIN entity_tag_cross_ref x ON x.tagId = t.id " +
            "WHERE x.targetType = :type AND x.targetId = :id ORDER BY t.name COLLATE NOCASE",
    )
    suspend fun tagsFor(type: EntityType, id: Long): List<TagEntity>

    /** Tags attached to at least one record of [type], with how many records use them. */
    @Query(
        "SELECT t.name AS name, COUNT(*) AS count FROM tags t " +
            "INNER JOIN entity_tag_cross_ref x ON x.tagId = t.id WHERE x.targetType = :type AND (x.targetType != 'ITEM' OR x.targetId NOT IN (SELECT itemId FROM pending_item_deletions)) " +
            "GROUP BY t.id ORDER BY t.name COLLATE NOCASE",
    )
    fun observeUsage(type: EntityType): Flow<List<TagUsage>>

    @Query("DELETE FROM tags WHERE id NOT IN (SELECT DISTINCT tagId FROM entity_tag_cross_ref)")
    suspend fun deleteOrphans()

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<TagEntity>>
}
