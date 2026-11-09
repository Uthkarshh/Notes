package com.notesapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.notesapp.data.db.entity.ItemDraftEntity

@Dao
interface ItemDraftDao {
    @Query("SELECT * FROM item_drafts WHERE itemId = :id") suspend fun get(id: Long): ItemDraftEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(draft: ItemDraftEntity)
    @Query("DELETE FROM item_drafts WHERE itemId = :id") suspend fun delete(id: Long)
}
