package com.notesapp.domain.transfer

import com.notesapp.data.db.entity.*
import com.notesapp.data.prefs.AppSettings
import com.notesapp.data.prefs.NotificationSettings
import com.notesapp.domain.model.EntityType
import com.notesapp.domain.model.StatusSchema
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull

@Serializable
data class Backup(
    val format: String = "notes-app",
    val version: Int = 1,
    val exportedAt: Long = 0,
    val collections: List<CollectionEntity> = emptyList(),
    val items: List<ItemEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val facts: List<FactEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val tagRefs: List<EntityTagCrossRef> = emptyList(),
    val history: List<SelectionHistoryEntity> = emptyList(),
    val links: List<KnowledgeLinkEntity> = emptyList(),
    val appearance: AppSettings? = null,
    val notifications: NotificationSettings? = null,
) {
    val recordCount: Int get() = collections.size + items.size + notes.size + facts.size

    /** Validate the whole graph before any database mutation. Unknown format versions never import silently. */
    fun validate(): Backup {
        require(format == "notes-app" && version == 1) { "This backup format or version is not supported." }
        require(recordCount <= 100_000 && tags.size <= 100_000 && tagRefs.size <= 500_000 && history.size <= 500_000 && links.size <= 500_000) { "The backup contains too many records." }
        fun ids(values: List<Long>, label: String): Set<Long> {
            require(values.all { it in 1..(Long.MAX_VALUE / 4 - 4) } && values.size == values.toSet().size) { "Invalid or duplicate $label IDs." }
            return values.toSet()
        }
        val collectionIds = ids(collections.map { it.id }, "collection")
        val itemIds = ids(items.map { it.id }, "item")
        val noteIds = ids(notes.map { it.id }, "note")
        val factIds = ids(facts.map { it.id }, "fact")
        val tagIds = ids(tags.map { it.id }, "tag")
        ids(history.map { it.id }, "history")
        val all = mapOf(EntityType.ITEM to itemIds, EntityType.NOTE to noteIds, EntityType.FACT to factIds)
        val schemas = collections.associate { collection ->
            require(collection.name.isNotBlank()) { "A collection has no name." }
            val schema = runCatching { BackupJson.codec.decodeFromString<StatusSchema>(collection.statusSchemaJson) }
                .getOrElse { throw IllegalArgumentException("A collection has an invalid status schema.") }
            require(schema.statuses.isNotEmpty() && schema.statuses.any { it.role == com.notesapp.domain.model.StatusRole.BACKLOG } &&
                schema.statuses.all { it.key.isNotBlank() && it.label.isNotBlank() } &&
                schema.statuses.map { it.key }.distinct().size == schema.statuses.size) { "A collection has invalid statuses." }
            collection.id to schema
        }
        items.forEach {
            require(it.title.isNotBlank() && (it.collectionId == null || it.collectionId in collectionIds)) { "An item has a missing title or collection." }
            require(it.collectionId == null || schemas.getValue(it.collectionId).find(it.status) != null) { "An item has an unknown status." }
            require(it.priority in 0..4 && it.selectedCount >= 0 && (it.rating == null || (it.rating.isFinite() && it.rating in 0.5f..5f && it.rating * 2 % 1f == 0f))) { "An item has an invalid rating or priority." }
        }
        require(facts.all { it.text.isNotBlank() && it.shownCount >= 0 }) { "A fact is empty or has an invalid impression count." }
        require(tags.all { com.notesapp.domain.model.TagNames.normalize(it.name) == it.name }) { "A tag has an invalid name." }
        require(tags.map { com.notesapp.domain.model.TagNames.databaseKey(it.name) }.distinct().size == tags.size) { "Duplicate tag names." }
        require(tagRefs.distinct().size == tagRefs.size && tagRefs.all { it.tagId in tagIds && it.targetId in all.getValue(it.targetType) }) { "A tag points to a missing record." }
        require(history.all { it.itemId in itemIds }) { "History points to a missing item." }
        require(links.map { listOf(it.sourceType, it.sourceId, it.targetType, it.targetId) }.distinct().size == links.size && links.all {
            it.sourceId in all.getValue(it.sourceType) && it.targetId in all.getValue(it.targetType)
        }) { "A knowledge link is duplicated or points to a missing record." }
        notifications?.let { require(it.minuteOfDay in 0..1439 && it.days.all { day -> day in 1..7 }) { "Invalid notification schedule." } }
        return this
    }
}

object BackupJson {
    val codec = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val pretty = Json(codec) { prettyPrint = true }
    fun encode(backup: Backup): String = pretty.encodeToString(backup)
    fun decode(text: String): Backup {
        val input = text.removePrefix("\uFEFF")
        val root = codec.parseToJsonElement(input).jsonObject
        require(root["format"]?.jsonPrimitive?.content == "notes-app" && root["version"]?.jsonPrimitive?.intOrNull == 1) { "This is not a supported Notes App backup." }
        return codec.decodeFromString<Backup>(input).validate()
    }
}
