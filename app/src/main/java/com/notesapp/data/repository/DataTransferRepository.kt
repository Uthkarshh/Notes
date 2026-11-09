package com.notesapp.data.repository

import androidx.room.withTransaction
import com.notesapp.data.db.AppDatabase
import com.notesapp.data.prefs.SettingsRepository
import com.notesapp.domain.model.EntityType
import com.notesapp.domain.transfer.Backup
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

enum class ImportMode { MERGE, REPLACE }

@Singleton
class DataTransferRepository @Inject constructor(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val indexer: SearchIndexer,
    private val clock: Clock,
) {
    suspend fun snapshot(): Backup {
        val data = db.withTransaction {
            val dao = db.phase2Dao()
            val refs = dao.tagRefs()
            val activeTags = refs.map { it.tagId }.toSet()
            Backup(exportedAt = clock.millis(), collections = dao.collections(), items = dao.items(), notes = dao.notes(),
                facts = dao.facts(), tags = dao.tags().filter { it.id in activeTags }, tagRefs = refs, history = dao.history(), links = dao.links())
        }
        return data.copy(appearance = settings.settings.first(), notifications = settings.notifications.first())
    }

    /** Merge remaps every foreign ID; replacement preserves the backup's IDs. All record/index writes are atomic. */
    suspend fun import(backup: Backup, mode: ImportMode): Int {
        backup.validate()
        return withContext(NonCancellable) {
            db.withTransaction {
                val dao = db.phase2Dao()
                val replace = mode == ImportMode.REPLACE
                if (replace) {
                    dao.clearLinks(); dao.clearHistory(); dao.clearRefs(); dao.clearItems(); dao.clearCollections()
                    dao.clearNotes(); dao.clearFacts(); dao.clearTags(); dao.clearSearch()
                }
                val collectionIds = backup.collections.associate { it.id to dao.insertCollection(it.copy(id = if (replace) it.id else 0)) }
                val itemIds = backup.items.associate { it.id to dao.insertItem(it.copy(id = if (replace) it.id else 0, collectionId = it.collectionId?.let(collectionIds::getValue))) }
                val noteIds = backup.notes.associate { it.id to dao.insertNote(it.copy(id = if (replace) it.id else 0)) }
                val factIds = backup.facts.associate { it.id to dao.insertFact(it.copy(id = if (replace) it.id else 0)) }
                val existingTags = dao.tags().associateBy { com.notesapp.domain.model.TagNames.databaseKey(it.name) }.toMutableMap()
                val tagIds = backup.tags.associate { tag ->
                    val key = com.notesapp.domain.model.TagNames.databaseKey(tag.name)
                    val found = existingTags[key]
                    val id = found?.id ?: dao.insertTag(tag.copy(id = if (replace) tag.id else 0)).also { existingTags[key] = tag.copy(id = it) }
                    tag.id to id
                }
                val ids = mapOf(EntityType.ITEM to itemIds, EntityType.NOTE to noteIds, EntityType.FACT to factIds)
                backup.tagRefs.forEach { dao.insertRef(it.copy(tagId = tagIds.getValue(it.tagId), targetId = ids.getValue(it.targetType).getValue(it.targetId))) }
                backup.history.forEach { dao.insertHistory(it.copy(id = if (replace) it.id else 0, itemId = itemIds.getValue(it.itemId))) }
                backup.links.forEach { dao.insertLink(it.copy(sourceId = ids.getValue(it.sourceType).getValue(it.sourceId), targetId = ids.getValue(it.targetType).getValue(it.targetId))) }
                val tagsById = backup.tags.associate { it.id to it.name }
                val tagsByOwner = backup.tagRefs.groupBy { it.targetType to it.targetId }
                fun tags(type: EntityType, id: Long) = tagsByOwner[type to id].orEmpty().map { tagsById.getValue(it.tagId) }
                backup.items.forEach { indexer.index(it.copy(id = itemIds.getValue(it.id)), tags(EntityType.ITEM, it.id)) }
                backup.notes.forEach { indexer.index(it.copy(id = noteIds.getValue(it.id)), tags(EntityType.NOTE, it.id)) }
                backup.facts.forEach { indexer.index(it.copy(id = factIds.getValue(it.id)), tags(EntityType.FACT, it.id)) }
            }
            if (mode == ImportMode.REPLACE) {
                backup.appearance?.let { settings.restoreAppearance(it) }
                backup.notifications?.let { settings.setNotifications(it) }
                settings.clearDailyFact()
                settings.clearRecentFiling()
            settings.clearCollectionViews()
            }
            settings.markSeeded()
            backup.recordCount
        }
    }
}
