package com.notesapp.data.repository

import androidx.room.withTransaction
import com.notesapp.data.db.AppDatabase
import com.notesapp.data.db.dao.CollectionDao
import com.notesapp.data.db.dao.ItemDao
import com.notesapp.data.db.entity.CollectionEntity
import com.notesapp.domain.model.CategoryType
import com.notesapp.domain.model.EntityType
import com.notesapp.domain.model.INBOX_STATUS
import com.notesapp.domain.model.StatusRole
import com.notesapp.domain.model.StatusSchema
import com.notesapp.domain.template.CollectionTemplate
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CollectionRepository @Inject constructor(
    private val db: AppDatabase,
    private val collectionDao: CollectionDao,
    private val itemDao: ItemDao,
    private val tagWriter: TagWriter,
    private val indexer: SearchIndexer,
    private val clock: Clock,
) {

    fun observeAll(): Flow<List<CollectionEntity>> = collectionDao.observeAll()

    fun observe(id: Long): Flow<CollectionEntity?> = collectionDao.observeById(id)

    suspend fun getAll(): List<CollectionEntity> = collectionDao.getAll()

    suspend fun get(id: Long): CollectionEntity? = collectionDao.getById(id)

    suspend fun itemCount(id: Long): Int = itemDao.countInCollection(id)

    suspend fun create(
        name: String,
        categoryType: CategoryType,
        iconName: String,
        colorHex: String,
        schema: StatusSchema,
    ): Long = db.withTransaction {
        collectionDao.insert(
            CollectionEntity(
                name = name.trim(),
                categoryType = categoryType,
                iconName = iconName,
                colorHex = colorHex,
                statusSchemaJson = schema.toJson(),
                sortOrder = collectionDao.maxSortOrder() + 1,
                createdAt = clock.millis(),
            ),
        )
    }

    suspend fun createFromTemplate(template: CollectionTemplate, name: String = template.name): Long = create(
        name = name,
        categoryType = template.categoryType,
        iconName = template.iconName,
        colorHex = template.colorHex,
        schema = template.schema,
    )

    suspend fun createAll(templates: List<CollectionTemplate>) = db.withTransaction {
        templates.forEach { createFromTemplate(it) }
    }

    /**
     * Updates a collection. Items whose status was removed from the schema move to the first status with the same
     * role, and `completedAt` is re-derived for items whose status changed role.
     */
    suspend fun update(
        id: Long,
        name: String,
        categoryType: CategoryType,
        iconName: String,
        colorHex: String,
        schema: StatusSchema,
    ) = db.withTransaction {
        val current = collectionDao.getById(id) ?: return@withTransaction
        val old = current.schema
        val now = clock.millis()

        old.statuses.forEach { oldStatus ->
            val kept = schema.find(oldStatus.key)
            when {
                kept == null -> {
                    val target = schema.fallbackFor(oldStatus)
                    itemDao.remapStatus(id, oldStatus.key, target.key, target.role == StatusRole.DONE, now)
                }
                kept.role != oldStatus.role ->
                    itemDao.remapStatus(id, kept.key, kept.key, kept.role == StatusRole.DONE, now)
            }
        }

        collectionDao.update(
            current.copy(
                name = name.trim(),
                categoryType = categoryType,
                iconName = iconName,
                colorHex = colorHex,
                statusSchemaJson = schema.toJson(),
            ),
        )
    }

    /** Deletes a collection, either with its items or by moving them to the Smart Inbox. */
    suspend fun delete(id: Long, deleteItems: Boolean) = db.withTransaction {
        if (deleteItems) {
            val ids = itemDao.idsInCollection(id)
            ids.chunked(TagWriter.SQL_CHUNK).forEach { db.phase2Dao().removeLinks(EntityType.ITEM, it) }
            indexer.remove(EntityType.ITEM, ids)
            tagWriter.removeAll(EntityType.ITEM, ids)
            // Item rows go with the collection through the foreign-key cascade.
        } else {
            itemDao.moveCollectionItemsToInbox(id, INBOX_STATUS, clock.millis())
        }
        collectionDao.deleteById(id)
    }
}
