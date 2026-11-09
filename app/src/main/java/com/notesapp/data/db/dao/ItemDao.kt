package com.notesapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.notesapp.data.db.entity.ItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE id NOT IN (SELECT itemId FROM pending_item_deletions) ORDER BY createdAt DESC")
    fun observeAllItems(): Flow<List<ItemEntity>>

    @Insert
    suspend fun insert(item: ItemEntity): Long

    @Update
    suspend fun update(item: ItemEntity)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM items WHERE id = :id AND id NOT IN (SELECT itemId FROM pending_item_deletions)")
    suspend fun getById(id: Long): ItemEntity?

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getIncludingPending(id: Long): ItemEntity?

    @Query("SELECT COUNT(*) FROM items WHERE imageUri = :uri")
    suspend fun imageReferences(uri: String): Int

    @Query("$ROW_SELECT WHERE i.id = :id")
    fun observeRowById(id: Long): Flow<ItemRow?>

    @Query("$ROW_SELECT WHERE i.collectionId = :collectionId AND i.isArchived = 0 ORDER BY i.createdAt DESC")
    fun observeByCollection(collectionId: Long): Flow<List<ItemRow>>

    @Query("$ROW_SELECT WHERE i.collectionId IS NULL AND i.isArchived = 0 ORDER BY i.createdAt DESC")
    fun observeInbox(): Flow<List<ItemRow>>

    @Query("SELECT COUNT(*) FROM items WHERE collectionId IS NULL AND isArchived = 0 AND id NOT IN (SELECT itemId FROM pending_item_deletions)")
    fun observeInboxCount(): Flow<Int>

    /** Candidate pre-filter for the "in progress" carousel; roles are re-checked against each collection's schema. */
    @Query(
        "$ROW_SELECT WHERE i.collectionId IS NOT NULL AND i.isArchived = 0 AND i.status IN (:statusKeys) " +
            "ORDER BY i.updatedAt DESC",
    )
    fun observeByStatusKeys(statusKeys: List<String>): Flow<List<ItemRow>>

    /** Every filed item (archived ones included) for the randomizer, which applies its own filters in memory. */
    @Query("$ROW_SELECT WHERE i.collectionId IS NOT NULL")
    suspend fun getAllFiledRows(): List<ItemRow>

    @Query("$ROW_SELECT WHERE i.collectionId IS NOT NULL")
    fun observeAllFiledRows(): Flow<List<ItemRow>>

    @Query(
        "SELECT collectionId, status, COUNT(*) AS count FROM items " +
            "WHERE collectionId IS NOT NULL AND isArchived = 0 AND id NOT IN (SELECT itemId FROM pending_item_deletions) GROUP BY collectionId, status",
    )
    fun observeStatusCounts(): Flow<List<StatusCount>>

    @Query("SELECT COUNT(*) FROM items WHERE collectionId = :collectionId")
    suspend fun countInCollection(collectionId: Long): Int

    @Query("SELECT COUNT(*) FROM items WHERE collectionId = :collectionId AND status = :status")
    suspend fun countWithStatus(collectionId: Long, status: String): Int

    @Query("SELECT id FROM items WHERE collectionId = :collectionId")
    suspend fun idsInCollection(collectionId: Long): List<Long>

    @Query(
        "UPDATE items SET status = :status, completedAt = :completedAt, updatedAt = :now WHERE id = :id AND id NOT IN (SELECT itemId FROM pending_item_deletions)",
    )
    suspend fun updateStatus(id: Long, status: String, completedAt: Long?, now: Long)

    @Query("UPDATE items SET isFavorite = :favorite, updatedAt = :now WHERE id = :id AND id NOT IN (SELECT itemId FROM pending_item_deletions)")
    suspend fun setFavorite(id: Long, favorite: Boolean, now: Long)

    @Query("UPDATE items SET isArchived = :archived, updatedAt = :now WHERE id = :id AND id NOT IN (SELECT itemId FROM pending_item_deletions)")
    suspend fun setArchived(id: Long, archived: Boolean, now: Long)

    @Query("UPDATE items SET rating = :rating, updatedAt = :now WHERE id = :id AND id NOT IN (SELECT itemId FROM pending_item_deletions)")
    suspend fun setRating(id: Long, rating: Float?, now: Long)

    @Query("UPDATE items SET priority = :priority, updatedAt = :now WHERE id = :id AND id NOT IN (SELECT itemId FROM pending_item_deletions)")
    suspend fun setPriority(id: Long, priority: Int, now: Long)

    @Query("UPDATE items SET lastSelectedAt = :at, selectedCount = selectedCount + 1 WHERE id = :id")
    suspend fun markSelected(id: Long, at: Long)

    @Query(
        "UPDATE items SET collectionId = :collectionId, status = :status, completedAt = NULL, updatedAt = :now " +
            "WHERE id IN (:ids) AND id NOT IN (SELECT itemId FROM pending_item_deletions)",
    )
    suspend fun moveToCollection(ids: List<Long>, collectionId: Long?, status: String, now: Long)

    @Query(
        "UPDATE items SET status = :toKey, " +
            "completedAt = CASE WHEN :completed = 1 THEN COALESCE(completedAt, :now) ELSE NULL END, " +
            "updatedAt = :now WHERE collectionId = :collectionId AND status = :fromKey",
    )
    suspend fun remapStatus(collectionId: Long, fromKey: String, toKey: String, completed: Boolean, now: Long)

    @Query(
        "UPDATE items SET collectionId = NULL, status = :inboxStatus, completedAt = NULL, updatedAt = :now " +
            "WHERE collectionId = :collectionId",
    )
    suspend fun moveCollectionItemsToInbox(collectionId: Long, inboxStatus: String, now: Long)

    companion object {
        const val ROW_SELECT =
            "SELECT i.*, (SELECT GROUP_CONCAT(t.name, ',') FROM entity_tag_cross_ref x " +
                "INNER JOIN tags t ON t.id = x.tagId WHERE x.targetType = 'ITEM' AND x.targetId = i.id) AS tagNames " +
                "FROM (SELECT * FROM items WHERE id NOT IN (SELECT itemId FROM pending_item_deletions)) i"
    }
}
