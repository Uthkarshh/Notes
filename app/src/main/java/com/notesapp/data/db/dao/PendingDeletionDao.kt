package com.notesapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.notesapp.data.db.entity.PendingItemDeletion
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingDeletionDao {
    @Insert suspend fun insert(entries: List<PendingItemDeletion>)
    @Query("SELECT * FROM pending_item_deletions ORDER BY expiresAt, itemId")
    fun observe(): Flow<List<PendingItemDeletion>>
    @Query("SELECT * FROM pending_item_deletions WHERE operationId = :operationId")
    suspend fun forOperation(operationId: String): List<PendingItemDeletion>
    @Query("SELECT * FROM pending_item_deletions WHERE expiresAt <= :now")
    suspend fun expired(now: Long): List<PendingItemDeletion>
    @Query("DELETE FROM pending_item_deletions WHERE operationId = :operationId")
    suspend fun undo(operationId: String)
}
