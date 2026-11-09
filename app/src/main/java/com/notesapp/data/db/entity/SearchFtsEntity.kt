package com.notesapp.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.PrimaryKey

/**
 * One searchable document per item, fact or note, kept in sync by the repositories.
 *
 * [rowId] is derived from the owner (see [com.notesapp.data.repository.SearchIndexer.rowId]) so a document can be
 * replaced or removed by rowid instead of scanning the virtual table. [entityType] and [originalId] are stored but
 * deliberately not tokenised.
 */
@Fts4(
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    notIndexed = ["entityType", "originalId"],
)
@Entity(tableName = "search_fts")
data class SearchFtsEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    val title: String,
    val body: String,
    val entityType: String,
    val originalId: Long,
)
