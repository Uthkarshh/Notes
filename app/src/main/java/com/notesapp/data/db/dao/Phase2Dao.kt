package com.notesapp.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.notesapp.data.db.entity.*
import com.notesapp.domain.model.EntityType
import kotlinx.coroutines.flow.Flow

data class HistoryRow(
    @Embedded val selection: SelectionHistoryEntity,
    val itemTitle: String,
    val collectionName: String?,
    val reflectionExcerpt: String?,
)

@Dao
interface Phase2Dao {
    @Insert suspend fun insertHistory(entry: SelectionHistoryEntity): Long
    @Query("UPDATE selection_history SET actionTaken = :action WHERE id = :id AND itemId = :itemId")
    suspend fun updateHistory(id: Long, itemId: Long, action: SelectionAction)
    @Query("SELECT h.*, i.title AS itemTitle, c.name AS collectionName, (SELECT n.content FROM notes n INNER JOIN knowledge_links l ON l.sourceId = n.id AND l.sourceType = 'NOTE' WHERE l.targetType = 'ITEM' AND l.targetId = i.id ORDER BY n.createdAt DESC, n.id DESC LIMIT 1) AS reflectionExcerpt FROM selection_history h INNER JOIN items i ON i.id = h.itemId LEFT JOIN collections c ON c.id = i.collectionId WHERE i.id NOT IN (SELECT itemId FROM pending_item_deletions) ORDER BY h.selectedAt DESC, h.id DESC")
    fun observeHistory(): Flow<List<HistoryRow>>
    @Query("SELECT n.* FROM notes n INNER JOIN knowledge_links l ON l.sourceId = n.id AND l.sourceType = 'NOTE' WHERE l.targetType = 'ITEM' AND l.targetId = :itemId ORDER BY n.createdAt DESC")
    fun observeLinkedNotes(itemId: Long): Flow<List<NoteEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertLink(link: KnowledgeLinkEntity)
    @Query("DELETE FROM knowledge_links WHERE (sourceType = :type AND sourceId IN (:ids)) OR (targetType = :type AND targetId IN (:ids))")
    suspend fun removeLinks(type: EntityType, ids: List<Long>)
    @Query("UPDATE items SET reminderAt = :at, updatedAt = :now WHERE id = :id AND id NOT IN (SELECT itemId FROM pending_item_deletions)")
    suspend fun setReminder(id: Long, at: Long?, now: Long)
    @Query("UPDATE items SET reminderAt = NULL WHERE id = :id AND reminderAt = :at")
    suspend fun consumeReminder(id: Long, at: Long)
    @Query("SELECT * FROM items WHERE reminderAt IS NOT NULL AND id NOT IN (SELECT itemId FROM pending_item_deletions)")
    fun observeReminders(): Flow<List<ItemEntity>>
    @Query("SELECT * FROM items WHERE reminderAt IS NOT NULL AND id NOT IN (SELECT itemId FROM pending_item_deletions)")
    suspend fun reminders(): List<ItemEntity>

    @Query("SELECT * FROM collections ORDER BY sortOrder, id") suspend fun collections(): List<CollectionEntity>
    @Query("SELECT * FROM items WHERE id NOT IN (SELECT itemId FROM pending_item_deletions) ORDER BY id") suspend fun items(): List<ItemEntity>
    @Query("SELECT * FROM notes ORDER BY id") suspend fun notes(): List<NoteEntity>
    @Query("SELECT * FROM facts ORDER BY id") suspend fun facts(): List<FactEntity>
    @Query("SELECT * FROM tags ORDER BY id") suspend fun tags(): List<TagEntity>
    @Query("SELECT * FROM entity_tag_cross_ref WHERE targetType != 'ITEM' OR targetId NOT IN (SELECT itemId FROM pending_item_deletions)") suspend fun tagRefs(): List<EntityTagCrossRef>
    @Query("SELECT * FROM selection_history WHERE itemId NOT IN (SELECT itemId FROM pending_item_deletions) ORDER BY id") suspend fun history(): List<SelectionHistoryEntity>
    @Query("SELECT * FROM knowledge_links WHERE (sourceType != 'ITEM' OR sourceId NOT IN (SELECT itemId FROM pending_item_deletions)) AND (targetType != 'ITEM' OR targetId NOT IN (SELECT itemId FROM pending_item_deletions))") suspend fun links(): List<KnowledgeLinkEntity>

    @Insert suspend fun insertCollection(entry: CollectionEntity): Long
    @Insert suspend fun insertItem(entry: ItemEntity): Long
    @Insert suspend fun insertNote(entry: NoteEntity): Long
    @Insert suspend fun insertFact(entry: FactEntity): Long
    @Insert suspend fun insertTag(entry: TagEntity): Long
    @Insert suspend fun insertRef(entry: EntityTagCrossRef)

    @Query("DELETE FROM knowledge_links") suspend fun clearLinks()
    @Query("DELETE FROM selection_history") suspend fun clearHistory()
    @Query("DELETE FROM entity_tag_cross_ref") suspend fun clearRefs()
    @Query("DELETE FROM items") suspend fun clearItems()
    @Query("DELETE FROM collections") suspend fun clearCollections()
    @Query("DELETE FROM notes") suspend fun clearNotes()
    @Query("DELETE FROM facts") suspend fun clearFacts()
    @Query("DELETE FROM tags") suspend fun clearTags()
    @Query("DELETE FROM search_fts") suspend fun clearSearch()
}
