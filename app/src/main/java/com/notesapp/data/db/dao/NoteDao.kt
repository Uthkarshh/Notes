package com.notesapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.notesapp.data.db.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): NoteEntity?

    @Query("$ROW_SELECT WHERE n.id = :id")
    fun observeRowById(id: Long): Flow<NoteRow?>

    @Query("$ROW_SELECT ORDER BY n.isPinned DESC, n.updatedAt DESC")
    fun observeAll(): Flow<List<NoteRow>>

    @Query("UPDATE notes SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    companion object {
        const val ROW_SELECT =
            "SELECT n.*, (SELECT GROUP_CONCAT(t.name, ',') FROM entity_tag_cross_ref x " +
                "INNER JOIN tags t ON t.id = x.tagId WHERE x.targetType = 'NOTE' AND x.targetId = n.id) AS tagNames " +
                "FROM notes n"
    }
}
