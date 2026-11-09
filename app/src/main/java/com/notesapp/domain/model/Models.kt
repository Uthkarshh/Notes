package com.notesapp.domain.model

import com.notesapp.data.db.entity.FactEntity
import com.notesapp.data.db.entity.ItemEntity
import com.notesapp.data.db.entity.NoteEntity

/** An item together with the names of the tags attached to it (sorted, case preserved). */
data class ItemWithTags(val item: ItemEntity, val tags: List<String>)

data class FactWithTags(val fact: FactEntity, val tags: List<String>)

data class NoteWithTags(val note: NoteEntity, val tags: List<String>)

/** Splits the comma-joined `GROUP_CONCAT` tag column into a stable, alphabetically sorted list. */
fun String?.toTagList(): List<String> =
    this?.split(',')
        ?.filter { it.isNotBlank() }
        ?.sortedBy { it.lowercase() }
        .orEmpty()
