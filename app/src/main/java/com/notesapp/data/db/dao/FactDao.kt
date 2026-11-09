package com.notesapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.notesapp.data.db.entity.FactEntity
import com.notesapp.domain.model.MasteryState
import kotlinx.coroutines.flow.Flow

@Dao
interface FactDao {

    @Insert
    suspend fun insert(fact: FactEntity): Long

    @Update
    suspend fun update(fact: FactEntity)

    @Query("DELETE FROM facts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM facts WHERE id = :id")
    suspend fun getById(id: Long): FactEntity?

    @Query("$ROW_SELECT WHERE f.id = :id")
    fun observeRowById(id: Long): Flow<FactRow?>

    @Query("$ROW_SELECT ORDER BY f.createdAt DESC, f.id DESC")
    fun observeAll(): Flow<List<FactRow>>

    @Query("SELECT * FROM facts WHERE masteryState != 'HIDDEN'")
    suspend fun getRotationCandidates(): List<FactEntity>

    @Query("$ROW_SELECT WHERE f.masteryState != 'HIDDEN'")
    suspend fun getDrawableRows(): List<FactRow>

    @Query("UPDATE facts SET shownCount = shownCount + 1, lastShownAt = :at WHERE id = :id")
    suspend fun markShown(id: Long, at: Long)

    @Query("UPDATE facts SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE facts SET masteryState = :state WHERE id = :id")
    suspend fun setMastery(id: Long, state: MasteryState)

    /** One entry per topic ignoring case (the alphabetically first spelling wins). */
    @Query(
        "SELECT MIN(topic) AS topic FROM facts WHERE topic IS NOT NULL AND topic != '' " +
            "GROUP BY topic COLLATE NOCASE ORDER BY topic COLLATE NOCASE",
    )
    fun observeTopics(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM facts")
    fun observeCount(): Flow<Int>

    /** Ids of facts that may be shown, so the daily pick can react to facts being added, deleted or hidden. */
    @Query("SELECT id FROM facts WHERE masteryState != 'HIDDEN'")
    fun observeShowableIds(): Flow<List<Long>>

    companion object {
        const val ROW_SELECT =
            "SELECT f.*, (SELECT GROUP_CONCAT(t.name, ',') FROM entity_tag_cross_ref x " +
                "INNER JOIN tags t ON t.id = x.tagId WHERE x.targetType = 'FACT' AND x.targetId = f.id) AS tagNames " +
                "FROM facts f"
    }
}
