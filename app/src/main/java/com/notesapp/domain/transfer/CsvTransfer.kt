package com.notesapp.domain.transfer

import com.notesapp.data.db.entity.*
import com.notesapp.domain.model.*

/** RFC 4180 cells, including embedded newlines and escaped quotes. */
object Csv {
    fun encode(rows: List<List<String>>): String = rows.joinToString("\r\n") { row ->
        row.joinToString(",") { value -> if (value.any { it in ",\"\r\n" }) "\"${value.replace("\"", "\"\"") }\"" else value }
    } + "\r\n"

    fun decode(input: String): List<List<String>> {
        val text = input.removePrefix("\uFEFF")
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var closed = false
        var i = 0
        fun finishCell() { row += cell.toString(); cell.setLength(0); closed = false }
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { cell.append('"'); i++ }
                    else { quoted = false; closed = true }
                } else cell.append(c)
            } else when (c) {
                '"' -> { require(cell.isEmpty() && !closed) { "Unexpected quote in CSV." }; quoted = true }
                ',' -> finishCell()
                '\r', '\n' -> {
                    finishCell(); rows += row; row = mutableListOf()
                    require(rows.size <= 100_001) { "The CSV contains too many rows." }
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                }
                else -> { require(!closed) { "Unexpected text after a quoted CSV value." }; cell.append(c) }
            }
            i++
        }
        require(!quoted) { "The CSV contains an unfinished quoted value." }
        if (cell.isNotEmpty() || closed || row.isNotEmpty()) { finishCell(); rows += row }
        return rows
    }
}

object CsvTransfer {
    private val headers = listOf("title", "description", "status", "priority", "rating", "favorite", "tags", "url", "collection", "category", "statusSchemaJson", "metadataJson", "imageUri", "reminderAt", "lastSelectedAt", "selectedCount", "createdAt", "updatedAt", "completedAt", "archived", "collectionId", "collectionIcon", "collectionColor", "collectionCreatedAt", "collectionSortOrder")

    fun encode(backup: Backup, collectionId: Long? = null, inboxOnly: Boolean = false): String {
        val collections = backup.collections.associateBy { it.id }
        val tags = backup.tags.associate { it.id to it.name }
        val refs = backup.tagRefs.filter { it.targetType == EntityType.ITEM }.groupBy { it.targetId }
        val rows = backup.items.filter { if (inboxOnly) it.collectionId == null else collectionId == null || it.collectionId == collectionId }.map { item ->
            val collection = collections[item.collectionId]
            listOf(item.title, item.description.orEmpty(), item.status, item.priority.toString(), item.rating?.toString().orEmpty(),
                item.isFavorite.toString(), refs[item.id].orEmpty().joinToString(";") { tags.getValue(it.tagId) }, item.externalUrl.orEmpty(),
                collection?.name.orEmpty(), collection?.categoryType?.name.orEmpty(), collection?.statusSchemaJson.orEmpty(),
                item.metadataJson.orEmpty(), item.imageUri.orEmpty(), item.reminderAt?.toString().orEmpty(), item.lastSelectedAt?.toString().orEmpty(),
                item.selectedCount.toString(), item.createdAt.toString(), item.updatedAt.toString(), item.completedAt?.toString().orEmpty(), item.isArchived.toString(),
                collection?.id?.toString().orEmpty(), collection?.iconName.orEmpty(), collection?.colorHex.orEmpty(), collection?.createdAt?.toString().orEmpty(), collection?.sortOrder?.toString().orEmpty())
        }
        return Csv.encode(listOf(headers) + rows)
    }

    fun decode(text: String, now: Long): Backup {
        val rows = Csv.decode(text)
        require(rows.isNotEmpty()) { "The CSV is empty." }
        val names = rows.first().map { it.trim().lowercase() }
        require("title" in names && names.distinct().size == names.size) { "CSV needs a title column and unique column names." }
        val collections = mutableListOf<CollectionEntity>()
        val collectionKeys = mutableMapOf<String, CollectionEntity>()
        val items = mutableListOf<ItemEntity>()
        val tagNames = linkedMapOf<String, TagEntity>()
        val refs = mutableListOf<EntityTagCrossRef>()
        rows.drop(1).filterNot { it.all(String::isBlank) }.forEachIndexed { index, cells ->
            require(cells.size <= names.size) { "CSV row ${index + 2} has too many values." }
            val row = names.mapIndexed { i, name -> name to cells.getOrElse(i) { "" } }.toMap()
            fun value(key: String) = row[key].orEmpty()
            fun optional(key: String) = value(key).ifBlank { null }
            fun long(key: String, default: Long? = null): Long? = optional(key)?.let { it.toLongOrNull() ?: throw IllegalArgumentException("Invalid $key in CSV row ${index + 2}.") } ?: default
            fun bool(key: String): Boolean = when (value(key).trim().lowercase()) { "", "false", "0" -> false; "true", "1" -> true; else -> throw IllegalArgumentException("Invalid $key in CSV row ${index + 2}.") }
            val collectionName = optional("collection")
            val collection = collectionName?.let { name ->
                val key = optional("collectionid")?.let { "id:$it" } ?: "name:$name"
                collectionKeys[key]?.also { require(it.name == name) { "Inconsistent collection names in CSV." } } ?: run {
                    val schema = optional("statusschemajson")?.let { BackupJson.codec.decodeFromString<StatusSchema>(it) } ?: StatusSchema.DEFAULT
                    val category = optional("category")?.let { CategoryType.valueOf(it.uppercase()) } ?: CategoryType.CUSTOM
                    CollectionEntity(collections.size + 1L, name, category, optional("collectionicon") ?: "folder", optional("collectioncolor") ?: "#006A6A", schema.toJson(),
                        long("collectionsortorder", collections.size.toLong())!!.also { require(it in 0..Int.MAX_VALUE.toLong()) }.toInt(), long("collectioncreatedat", now)!!)
                        .also { collections += it; collectionKeys[key] = it }
                }
            }
            val status = if (collection == null) INBOX_STATUS else {
                val input = optional("status")
                if (input == null) collection.schema.initial.key else collection.schema.statuses.firstOrNull { it.key.equals(input, true) || it.label.equals(input, true) }?.key
                    ?: throw IllegalArgumentException("Unknown status '$input' in CSV row ${index + 2}.")
            }
            val priority = optional("priority")?.let { raw -> raw.toIntOrNull() ?: Priority.entries.firstOrNull { it.name.equals(raw, true) || it.label.equals(raw, true) }?.level ?: throw IllegalArgumentException("Invalid priority in CSV row ${index + 2}.") } ?: 0
            val id = items.size + 1L
            items += ItemEntity(id = id, title = value("title").trim(), collectionId = collection?.id, description = optional("description"), status = status,
                priority = priority, rating = optional("rating")?.let { it.toFloatOrNull() ?: throw IllegalArgumentException("Invalid rating in CSV row ${index + 2}.") },
                isFavorite = bool("favorite"), isArchived = bool("archived"), externalUrl = optional("url"), metadataJson = optional("metadatajson"), imageUri = optional("imageuri"),
                reminderAt = long("reminderat"), lastSelectedAt = long("lastselectedat"), selectedCount = long("selectedcount", 0)!!.also { require(it in 0..Int.MAX_VALUE.toLong()) }.toInt(),
                createdAt = long("createdat", now)!!, updatedAt = long("updatedat", now)!!, completedAt = long("completedat"))
            TagNames.parseList(value("tags")).forEach { name ->
                val tag = tagNames.getOrPut(name.lowercase()) { TagEntity(tagNames.size + 1L, name) }
                refs += EntityTagCrossRef(tag.id, id, EntityType.ITEM)
            }
        }
        return Backup(exportedAt = now, collections = collections, items = items, tags = tagNames.values.toList(), tagRefs = refs.distinct()).validate()
    }
}
