package com.notesapp.domain.transfer

import com.notesapp.data.db.entity.*
import com.notesapp.domain.model.EntityType
import com.notesapp.domain.model.TagNames
import kotlinx.serialization.Serializable

@Serializable
data class MarkdownRecord(val type: EntityType, val note: NoteEntity? = null, val fact: FactEntity? = null, val tags: List<String> = emptyList())

object MarkdownTransfer {
    private const val PREFIX = "---\nnotes-app: "

    fun encode(backup: Backup): Map<String, String> {
        val tagNames = backup.tags.associate { it.id to it.name }
        fun tags(type: EntityType, id: Long) = backup.tagRefs.filter { it.targetType == type && it.targetId == id }.map { tagNames.getValue(it.tagId) }
        fun fileName(title: String) = title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").take(70).ifBlank { "Untitled" }
        fun render(record: MarkdownRecord, title: String, body: String) = "$PREFIX${BackupJson.codec.encodeToString(record)}\n---\n\n# $title\n\n$body\n"
        return buildMap {
            backup.notes.forEach { note -> put("Notes/${note.id}-${fileName(note.title)}.md", render(MarkdownRecord(EntityType.NOTE, note = note, tags = tags(EntityType.NOTE, note.id)), note.title.ifBlank { "Untitled" }, note.content)) }
            backup.facts.forEach { fact -> put("Facts/${fact.id}-${fileName(fact.text.lineSequence().first())}.md", render(MarkdownRecord(EntityType.FACT, fact = fact, tags = tags(EntityType.FACT, fact.id)), fact.topic ?: "Fact", fact.text)) }
        }
    }

    /** Our metadata preserves editable fields and counters; ordinary Markdown imports as a note. */
    fun decode(files: Map<String, String>, now: Long): Backup {
        require(files.size <= 10_000) { "The Markdown folder contains too many files." }
        val notes = mutableListOf<NoteEntity>()
        val facts = mutableListOf<FactEntity>()
        val tags = linkedMapOf<String, TagEntity>()
        val refs = mutableListOf<EntityTagCrossRef>()
        files.forEach { (path, raw) ->
            val text = raw.removePrefix("\uFEFF").replace("\r\n", "\n")
            val record = if (text.startsWith(PREFIX)) {
                val metadata = text.substring(PREFIX.length).substringBefore('\n')
                require(text.substring(PREFIX.length + metadata.length).startsWith("\n---\n")) { "Invalid Markdown metadata in $path." }
                val saved = BackupJson.codec.decodeFromString<MarkdownRecord>(metadata)
                val visible = text.substring(PREFIX.length + metadata.length + "\n---\n".length).removePrefix("\n")
                val originalTitle = saved.note?.title?.ifBlank { "Untitled" } ?: saved.fact?.topic ?: "Fact"
                val originalBody = saved.note?.content ?: saved.fact?.text.orEmpty()
                val expected = "# $originalTitle\n\n$originalBody\n".replace("\r\n", "\n")
                if (visible == expected) saved else {
                    // The readable Markdown is authoritative after editing; metadata retains the other fields.
                    val heading = visible.lineSequence().firstOrNull()?.takeIf { it.startsWith("# ") }
                    val body = (if (heading != null) visible.removePrefix(heading).removePrefix("\n").removePrefix("\n") else visible).removeSuffix("\n")
                    saved.copy(
                        note = saved.note?.copy(title = heading?.removePrefix("# ") ?: saved.note.title, content = body, updatedAt = now),
                        fact = saved.fact?.copy(text = body, topic = heading?.removePrefix("# ")?.takeUnless { it == "Fact" && saved.fact.topic == null } ?: saved.fact.topic),
                    )
                }
            } else {
                val heading = text.lineSequence().firstOrNull { it.startsWith("# ") }
                MarkdownRecord(EntityType.NOTE, note = NoteEntity(title = heading?.removePrefix("# ") ?: path.substringAfterLast('/').removeSuffix(".md"),
                    content = if (heading != null && text.startsWith(heading)) text.removePrefix(heading).trimStart('\n') else text, createdAt = now, updatedAt = now))
            }
            val id = when (record.type) {
                EntityType.NOTE -> {
                    require(record.note != null && record.fact == null) { "Invalid note metadata in $path." }
                    val id = notes.size + 1L; notes += record.note.copy(id = id); id
                }
                EntityType.FACT -> {
                    require(record.fact != null && record.note == null) { "Invalid fact metadata in $path." }
                    val id = facts.size + 1L; facts += record.fact.copy(id = id); id
                }
                else -> throw IllegalArgumentException("Unsupported Markdown record in $path.")
            }
            record.tags.mapNotNull(TagNames::normalize).distinctBy { it.lowercase() }.forEach { name ->
                val tag = tags.getOrPut(name.lowercase()) { TagEntity(tags.size + 1L, name) }
                refs += EntityTagCrossRef(tag.id, id, record.type)
            }
        }
        return Backup(exportedAt = now, notes = notes, facts = facts, tags = tags.values.toList(), tagRefs = refs).validate()
    }
}
