package com.notesapp.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** Private recovery data, intentionally outside search and portable record exports. */
@Entity(tableName = "item_drafts", foreignKeys = [ForeignKey(entity = ItemEntity::class,
    parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE)])
data class ItemDraftEntity(@PrimaryKey val itemId: Long, val fieldsJson: String, val updatedAt: Long)
