package com.notesapp.data.repository

import androidx.room.withTransaction
import com.notesapp.data.db.AppDatabase
import com.notesapp.data.db.dao.CollectionDao
import com.notesapp.data.db.dao.ItemDao
import com.notesapp.data.db.dao.ItemRow
import com.notesapp.data.db.dao.StatusCount
import com.notesapp.data.db.dao.TagDao
import com.notesapp.data.db.entity.CollectionEntity
import com.notesapp.data.db.entity.ItemEntity
import com.notesapp.data.db.entity.SelectionAction
import com.notesapp.data.db.entity.SelectionHistoryEntity
import com.notesapp.domain.model.EntityType
import com.notesapp.domain.model.INBOX_STATUS
import com.notesapp.domain.model.ItemWithTags
import com.notesapp.domain.model.Priority
import com.notesapp.domain.model.StatusRole
import com.notesapp.domain.model.toTagList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import com.notesapp.domain.capture.ItemEditFields
import com.notesapp.domain.metadata.ItemMetadata
import com.notesapp.domain.metadata.MetadataFields
import com.notesapp.data.db.entity.ItemDraftEntity
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Everything needed to create an item; a null [collection] files it in the Smart Inbox. */
data class NewItem(
    val title: String,
    val collection: CollectionEntity? = null,
    val tags: List<String> = emptyList(),
    val priority: Priority = Priority.NONE,
    val url: String? = null,
    val description: String? = null,
    val captureCategory: com.notesapp.domain.model.CategoryType? = null,
    val captureCollectionQuery: String? = null,
)

data class CompletionRequest(val itemId: Long, val title: String)
data class ItemUndo(val before: ItemEntity, val after: ItemEntity)
data class InboxBatchResult(val changes: List<ItemUndo>, val skipped: Int)
data class InboxDeleteResult(val operationId: String, val count: Int, val skipped: Int, val expiresAt: Long)

@Singleton
class ItemRepository @Inject constructor(
    private val db: AppDatabase,
    private val itemDao: ItemDao,
    private val collectionDao: CollectionDao,
    private val tagDao: TagDao,
    private val tagWriter: TagWriter,
    private val indexer: SearchIndexer,
    private val clock: Clock,
) {
    private val _completions = MutableSharedFlow<CompletionRequest>(extraBufferCapacity = 16)
    val completions = _completions.asSharedFlow()
    private val _completionCancellations = MutableSharedFlow<Long>(extraBufferCapacity = 16)
    val completionCancellations = _completionCancellations.asSharedFlow()
    fun now(): Long = clock.millis()
    fun observeHistory() = db.phase2Dao().observeHistory()
    fun observeReminders() = db.phase2Dao().observeReminders()
    fun observePendingDeletions() = db.pendingDeletionDao().observe()

    fun observeItem(id: Long): Flow<ItemWithTags?> = itemDao.observeRowById(id).map { it?.toModel() }

    fun observeCollectionItems(collectionId: Long): Flow<List<ItemWithTags>> =
        itemDao.observeByCollection(collectionId).map { rows -> rows.map(ItemRow::toModel) }

    fun observeInbox(): Flow<List<ItemWithTags>> =
        itemDao.observeInbox().map { rows -> rows.map(ItemRow::toModel) }

    fun observeInboxCount(): Flow<Int> = itemDao.observeInboxCount()

    fun observeByStatusKeys(statusKeys: Collection<String>): Flow<List<ItemWithTags>> =
        itemDao.observeByStatusKeys(statusKeys.toList()).map { rows -> rows.map(ItemRow::toModel) }

    fun observeStatusCounts(): Flow<List<StatusCount>> = itemDao.observeStatusCounts()

    /** Tags in use by items, for filter chips. */
    fun observeTagUsage() = tagDao.observeUsage(EntityType.ITEM)

    suspend fun get(id: Long): ItemEntity? = itemDao.getById(id)

    suspend fun editorValues(id: Long): Map<String, String>? = observeItem(id).first()?.let { entry ->
        MetadataFields.values(ItemMetadata.decode(entry.item.metadataJson)) + mapOf(
            ItemEditFields.TITLE to entry.item.title, ItemEditFields.NOTES to entry.item.description.orEmpty(),
            ItemEditFields.LINK to entry.item.externalUrl.orEmpty(), ItemEditFields.TAGS to Json.encodeToString(entry.tags),
        )
    }

    suspend fun editorDraft(id: Long): Map<String, String> = db.itemDraftDao().get(id)?.let {
        Json.decodeFromString<Map<String, String>>(it.fieldsJson)
    }.orEmpty()

    suspend fun journalEditor(id: Long, fields: Map<String, String>, previous: Map<String, String>? = null) = db.withTransaction {
        if (itemDao.getById(id) == null) return@withTransaction
        val merged = if (previous == null) fields else editorDraft(id).filter { (key, value) -> previous[key] != value } + fields
        if (merged.isEmpty()) db.itemDraftDao().delete(id)
        else if (itemDao.getById(id) != null) db.itemDraftDao().put(ItemDraftEntity(id, Json.encodeToString(merged), clock.millis()))
    }

    /** Applies only edited, valid fields to the latest row; inline actions and extension JSON survive. */
    suspend fun commitEditor(id: Long, fields: Map<String, String>) = db.withTransaction {
        val current = itemDao.getById(id) ?: return@withTransaction
        fields.forEach { (key, raw) -> require(ItemEditFields.error(key, raw) == null) }
        val metadataFields = fields.filterKeys { it in MetadataFields.keys }
        val metadata = metadataFields.entries.fold(ItemMetadata.decode(current.metadataJson)) { value, (key, raw) -> MetadataFields.apply(value, key, raw) }
        val updated = current.copy(
            title = fields[ItemEditFields.TITLE]?.trim() ?: current.title,
            description = if (ItemEditFields.NOTES in fields) fields.getValue(ItemEditFields.NOTES).trim().ifEmpty { null } else current.description,
            externalUrl = if (ItemEditFields.LINK in fields) fields.getValue(ItemEditFields.LINK).trim().ifEmpty { null } else current.externalUrl,
            metadataJson = if (metadataFields.isNotEmpty()) ItemMetadata.merge(current.metadataJson, metadata) else current.metadataJson,
            updatedAt = clock.millis(),
        )
        itemDao.update(updated)
        fields[ItemEditFields.TAGS]?.let { tagWriter.replace(EntityType.ITEM, id, ItemEditFields.tags(it)) }
        indexer.index(updated, tagsOf(id))
        // A crash between committing and UI acknowledgement must not leave an obsolete saved draft.
        journalEditor(id, editorDraft(id).filter { (key, value) -> fields[key] != value })
    }

    /** Compare and patch in one transaction. A conflict applies nothing until reviewed again. */
    suspend fun applyProviderFields(id: Long, expected: Map<String, String>, fields: Map<String, String>): Map<String, String>? = db.withTransaction {
        val current = checkNotNull(editorValues(id)) { "This item no longer exists." }
        require(fields.keys.all { it in com.notesapp.domain.metadata.MetadataReview.labels })
        if (com.notesapp.domain.metadata.MetadataReview.conflicts(expected, current, fields.keys).isNotEmpty()) current
        else { commitEditor(id, fields); null }
    }

    suspend fun undoable(id: Long, change: suspend () -> Unit): ItemUndo? = db.withTransaction {
        val before = itemDao.getById(id) ?: return@withTransaction null
        change()
        val after = itemDao.getById(id) ?: return@withTransaction null
        ItemUndo(before, after).takeIf { before != after }
    }

    /** Reverts changed values only when they still match this operation, preserving later edits. */
    suspend fun undo(change: ItemUndo): Boolean {
        val restored = db.withTransaction {
            val current = itemDao.getById(change.after.id) ?: return@withTransaction false
            val before = change.before
            val after = change.after
            fun <T> restore(old: T, applied: T, actual: T): T = if (old == applied) actual else {
                check(actual == applied) { "This field changed again." }; old
            }
            val updated = runCatching { current.copy(
                collectionId = restore(before.collectionId, after.collectionId, current.collectionId),
                status = restore(before.status, after.status, current.status),
                completedAt = restore(before.completedAt, after.completedAt, current.completedAt),
                reminderAt = restore(before.reminderAt, after.reminderAt, current.reminderAt),
                isArchived = restore(before.isArchived, after.isArchived, current.isArchived),
                imageUri = restore(before.imageUri, after.imageUri, current.imageUri),
                metadataJson = if (before.metadataJson == after.metadataJson) current.metadataJson else {
                    val oldFields = MetadataFields.values(ItemMetadata.decode(before.metadataJson))
                    val applied = MetadataFields.values(ItemMetadata.decode(after.metadataJson))
                    val actual = MetadataFields.values(ItemMetadata.decode(current.metadataJson))
                    val changed = oldFields.keys.filter { oldFields[it] != applied[it] }
                    check(changed.all { actual[it] == applied[it] }) { "These details changed again." }
                    val value = changed.fold(ItemMetadata.decode(current.metadataJson)) { metadata, key -> MetadataFields.apply(metadata, key, oldFields.getValue(key)) }
                    ItemMetadata.merge(current.metadataJson, value)
                },
                rating = restore(before.rating, after.rating, current.rating),
                priority = restore(before.priority, after.priority, current.priority),
                isFavorite = restore(before.isFavorite, after.isFavorite, current.isFavorite),
                updatedAt = clock.millis(),
            ) }.getOrNull() ?: return@withTransaction false
            if (updated.collectionId != null) {
                val collection = collectionDao.getById(updated.collectionId) ?: return@withTransaction false
                if (collection.schema.find(updated.status) == null) return@withTransaction false
            }
            itemDao.update(updated)
            indexer.index(updated, tagsOf(updated.id))
            true
        }
        if (restored && change.before.status != change.after.status) _completionCancellations.emit(change.before.id)
        return restored
    }

    suspend fun saveMetadata(id: Long, metadata: com.notesapp.domain.metadata.ItemMetadata) = db.withTransaction {
        val current = itemDao.getById(id) ?: return@withTransaction
        val updated = current.copy(metadataJson = com.notesapp.domain.metadata.ItemMetadata.merge(current.metadataJson, metadata), updatedAt = clock.millis())
        itemDao.update(updated)
        indexer.index(updated, tagsOf(id))
    }

    suspend fun setImageIfUnchanged(id: Long, uri: String?, expected: String?): Boolean = db.withTransaction {
        val current = itemDao.getById(id) ?: return@withTransaction false
        if (current.imageUri != expected) return@withTransaction false
        itemDao.update(current.copy(imageUri = uri, updatedAt = clock.millis()))
        true
    }

    suspend fun setImage(id: Long, uri: String?) = db.withTransaction {
        val current = itemDao.getById(id) ?: return@withTransaction
        itemDao.update(current.copy(imageUri = uri, updatedAt = clock.millis()))
    }

    /** All filed items, used by the randomizer. */
    suspend fun getAllFiled(): List<ItemWithTags> = itemDao.getAllFiledRows().map(ItemRow::toModel)

    fun observeAllFiled(): Flow<List<ItemWithTags>> =
        itemDao.observeAllFiledRows().map { rows -> rows.map(ItemRow::toModel) }

    suspend fun create(new: NewItem): Long = db.withTransaction {
        val now = clock.millis()
        val collection = new.collection?.let { checkNotNull(collectionDao.getById(it.id)) { "This collection no longer exists. Choose another destination." } }
        val entity = ItemEntity(
            collectionId = collection?.id,
            title = new.title.trim(),
            description = new.description?.trim()?.ifEmpty { null },
            status = collection?.schema?.initial?.key ?: INBOX_STATUS,
            priority = new.priority.level,
            externalUrl = new.url?.trim()?.ifEmpty { null },
            metadataJson = kotlinx.serialization.json.buildJsonObject {
                new.captureCategory?.let { put("captureCategory", kotlinx.serialization.json.JsonPrimitive(it.name)) }
                new.captureCollectionQuery?.let { put("captureCollection", kotlinx.serialization.json.JsonPrimitive(it)) }
            }.takeIf { it.isNotEmpty() }?.toString(),
            createdAt = now,
            updatedAt = now,
        )
        val id = itemDao.insert(entity)
        tagWriter.replace(EntityType.ITEM, id, new.tags)
        indexer.index(entity.copy(id = id), tagsOf(id))
        id
    }

    /** Saves the text fields of the detail screen without touching status, rating or other inline-edited fields. */
    suspend fun saveDetails(
        id: Long,
        title: String,
        description: String?,
        externalUrl: String?,
        tags: List<String>,
    ) = db.withTransaction {
        val current = itemDao.getById(id) ?: return@withTransaction
        val updated = current.copy(
            title = title.trim().ifEmpty { current.title },
            description = description?.trim()?.ifEmpty { null },
            externalUrl = externalUrl?.trim()?.ifEmpty { null },
            updatedAt = clock.millis(),
        )
        itemDao.update(updated)
        tagWriter.replace(EntityType.ITEM, id, tags)
        indexer.index(updated, tagsOf(id))
    }

    /** Moves the item to [newKey] if it is a valid status of its collection, maintaining `completedAt`. */
    suspend fun setStatus(id: Long, newKey: String, requestReflection: Boolean = false, expectedStatus: String? = null, expectedCollectionId: Long? = null) {
        val completion = db.withTransaction {
            val item = itemDao.getById(id) ?: return@withTransaction null
            if (expectedStatus != null && (item.status != expectedStatus || item.collectionId != expectedCollectionId || item.isArchived)) return@withTransaction null
            val collection = item.collectionId?.let { collectionDao.getById(it) } ?: return@withTransaction null
            val status = collection.schema.find(newKey) ?: return@withTransaction null
            if (item.status == status.key) return@withTransaction null
            val now = clock.millis()
            val completedAt = if (status.role == StatusRole.DONE) item.completedAt ?: now else null
            itemDao.updateStatus(id, status.key, completedAt, now)
            if (status.isTerminal && item.reminderAt != null) db.phase2Dao().consumeReminder(id, item.reminderAt)
            if (requestReflection && status.role == StatusRole.DONE && collection.schema.roleOf(item.status) != StatusRole.DONE) CompletionRequest(id, item.title) else null
        }
        completion?.let { _completions.emit(it) }
    }

    /**
     * "Accept & Start" on a Surprise Me pick: starts the item (first in-progress status, if the collection has one)
     * and records that it was drawn. Returns the status the item ended up in, or null if the item is gone.
     */
    suspend fun acceptPick(id: Long, historyId: Long? = null): String? = db.withTransaction {
        val item = itemDao.getById(id)?.takeUnless { it.isArchived } ?: return@withTransaction null
        val collection = item.collectionId?.let { collectionDao.getById(it) }
        val start = collection?.schema?.startStatus
        if (start != null && collection.schema.roleOf(item.status) == StatusRole.BACKLOG) {
            itemDao.updateStatus(id, start.key, null, clock.millis())
        }
        if (historyId == null) {
            itemDao.markSelected(id, clock.millis())
            db.phase2Dao().insertHistory(SelectionHistoryEntity(itemId = id, selectedAt = clock.millis(), actionTaken = SelectionAction.ACCEPTED))
        } else db.phase2Dao().updateHistory(historyId, id, SelectionAction.ACCEPTED)
        itemDao.getById(id)?.status
    }

    /** Status, reminder and history form one completion; repeated taps cannot prompt twice. */
    suspend fun completePick(id: Long, historyId: Long) {
        val request = db.withTransaction {
            val item = itemDao.getById(id)?.takeUnless { it.isArchived } ?: return@withTransaction null
            val collection = item.collectionId?.let { collectionDao.getById(it) } ?: return@withTransaction null
            val done = collection.schema.completionStatus ?: return@withTransaction null
            if (collection.schema.roleOf(item.status) == StatusRole.DONE) return@withTransaction null
            itemDao.updateStatus(id, done.key, clock.millis(), clock.millis())
            item.reminderAt?.let { db.phase2Dao().consumeReminder(id, it) }
            db.phase2Dao().updateHistory(historyId, id, SelectionAction.COMPLETED)
            CompletionRequest(id, item.title)
        }
        request?.let { _completions.emit(it) }
    }

    /** Eligibility and history commit share a database snapshot, including concurrent deletion/moves. */
    suspend fun pickAndRecord(filter: com.notesapp.domain.random.RandomFilter, skipped: Set<Long>, random: kotlin.random.Random,
        at: Long, originalLastSelected: Map<Long, Long?>): Triple<com.notesapp.domain.random.PickResult, Long?, Int> = db.withTransaction {
        val rows = getAllFiled().map { row -> if (row.item.id in originalLastSelected)
            row.copy(item = row.item.copy(lastSelectedAt = originalLastSelected[row.item.id])) else row }
        val collections = collectionDao.getAll()
        val eligible = com.notesapp.domain.random.PickRandomItem.candidates(rows, collections, filter, at).size
        val result = com.notesapp.domain.random.PickRandomItem.pick(rows, collections, filter, skipped, random, at)
        val historyId = (result as? com.notesapp.domain.random.PickResult.Picked)?.let { recordDraw(it.item.item.id) }
        Triple(result, historyId, eligible)
    }

    suspend fun recordDraw(id: Long): Long? = db.withTransaction {
        if (itemDao.getById(id)?.isArchived != false) return@withTransaction null
        val at = clock.millis()
        itemDao.markSelected(id, at)
        db.phase2Dao().insertHistory(SelectionHistoryEntity(itemId = id, selectedAt = at, actionTaken = SelectionAction.DRAWN))
    }

    suspend fun updatePick(historyId: Long, itemId: Long, action: SelectionAction) = db.phase2Dao().updateHistory(historyId, itemId, action)

    suspend fun setReminder(id: Long, at: Long?) = db.phase2Dao().setReminder(id, at, clock.millis())

    suspend fun setRating(id: Long, rating: Float?) = itemDao.setRating(id, rating, clock.millis())

    suspend fun setPriority(id: Long, priority: Priority) = itemDao.setPriority(id, priority.level, clock.millis())

    suspend fun setFavorite(id: Long, favorite: Boolean) = itemDao.setFavorite(id, favorite, clock.millis())

    /** The backlog decision is conditional at commit time, preserving items started elsewhere. */
    suspend fun revisitBacklog(id: Long, letGo: Boolean = false): Boolean = db.withTransaction {
        val item = itemDao.getById(id) ?: return@withTransaction false
        val collection = item.collectionId?.let { collectionDao.getById(it) } ?: return@withTransaction false
        if (item.isArchived || item.createdAt >= clock.millis() - 90 * 86_400_000L || collection.schema.roleOf(item.status) != StatusRole.BACKLOG) return@withTransaction false
        val dropped = collection.schema.statuses.firstOrNull { it.role == StatusRole.DROPPED }
        if (letGo && dropped != null) setStatus(id, dropped.key) else setArchived(id, true)
        true
    }

    suspend fun setArchived(id: Long, archived: Boolean) = itemDao.setArchived(id, archived, clock.millis())

    /** Files items into [collection] (or back into the inbox when null), resetting them to its initial status. */
    suspend fun moveToCollection(ids: List<Long>, collection: CollectionEntity?) = db.withTransaction {
        val target = collection?.let { checkNotNull(collectionDao.getById(it.id)) { "Collection no longer exists." } }
        val status = target?.schema?.initial?.key ?: INBOX_STATUS
        val now = clock.millis()
        ids.distinct().chunked(TagWriter.SQL_CHUNK).forEach { chunk ->
            itemDao.moveToCollection(chunk, target?.id, status, now)
        }
    }

    /** Rechecks IDs and the destination in one transaction; stale selection cannot move a filed item. */
    suspend fun fileInbox(ids: List<Long>, targetId: Long): InboxBatchResult = db.withTransaction {
        val target = checkNotNull(collectionDao.getById(targetId)) { "This collection no longer exists. Choose another destination." }
        val changes = ids.distinct().mapNotNull { id ->
            val before = itemDao.getById(id)?.takeIf { it.collectionId == null && !it.isArchived } ?: return@mapNotNull null
            val after = before.copy(collectionId = target.id, status = target.schema.initial.key, completedAt = null, updatedAt = clock.millis())
            itemDao.update(after)
            indexer.index(after, tagsOf(id))
            ItemUndo(before, after)
        }
        InboxBatchResult(changes, ids.distinct().size - changes.size)
    }

    /** All-or-nothing Undo: a conflicting later workflow edit never produces a partially restored batch. */
    suspend fun undoBatch(changes: List<ItemUndo>): Boolean = try {
        db.withTransaction { changes.forEach { check(undo(it)) }; true }
    } catch (e: kotlinx.coroutines.CancellationException) { throw e
    } catch (_: IllegalStateException) { false }

    suspend fun deleteInbox(ids: List<Long>): InboxDeleteResult = db.withTransaction {
        val entries = ids.distinct().mapNotNull { itemDao.getById(it)?.takeIf { item -> item.collectionId == null && !item.isArchived } }
        val operation = java.util.UUID.randomUUID().toString()
        val expires = clock.millis() + DELETE_UNDO_MILLIS
        db.pendingDeletionDao().insert(entries.map { com.notesapp.data.db.entity.PendingItemDeletion(it.id, operation, expires) })
        InboxDeleteResult(operation, entries.size, ids.distinct().size - entries.size, expires)
    }

    suspend fun undoDeletion(operationId: String): Boolean = db.withTransaction {
        val entries = db.pendingDeletionDao().forOperation(operationId)
        if (entries.isEmpty() || entries.any { it.expiresAt <= clock.millis() }) return@withTransaction false
        db.pendingDeletionDao().undo(operationId)
        true
    }

    /** Physical removal runs only after the deadline. Covers remain available through the entire Undo window. */
    suspend fun finalizeExpiredDeletions(): List<String> = db.withTransaction {
        val entries = db.pendingDeletionDao().expired(clock.millis())
        val covers = entries.mapNotNull { itemDao.getIncludingPending(it.itemId)?.imageUri }.distinct()
        entries.forEach { delete(it.itemId) }
        covers.filter { itemDao.imageReferences(it) == 0 }
    }

    suspend fun delete(id: Long) = db.withTransaction {
        db.phase2Dao().removeLinks(EntityType.ITEM, listOf(id))
        itemDao.deleteById(id)
        indexer.remove(EntityType.ITEM, listOf(id))
        tagWriter.removeAll(EntityType.ITEM, listOf(id))
    }

    suspend fun deleteIfUnchanged(id: Long, expected: ItemEntity?): Boolean = db.withTransaction {
        if (expected == null || itemDao.getById(id) != expected) return@withTransaction false
        delete(id)
        true
    }

    private suspend fun tagsOf(id: Long): List<String> = tagDao.tagsFor(EntityType.ITEM, id).map { it.name }

    companion object { const val DELETE_UNDO_MILLIS = 15_000L }
}

internal fun ItemRow.toModel() = ItemWithTags(item, tagNames.toTagList())
