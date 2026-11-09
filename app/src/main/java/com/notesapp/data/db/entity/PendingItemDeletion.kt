package com.notesapp.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Keeps the entire item graph intact until the durable Undo deadline. Never exported. */
@Entity(
    tableName = "pending_item_deletions",
    foreignKeys = [ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("operationId"), Index("expiresAt")],
)
data class PendingItemDeletion(@PrimaryKey val itemId: Long, val operationId: String, val expiresAt: Long)
