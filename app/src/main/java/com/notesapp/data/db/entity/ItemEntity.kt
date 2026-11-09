package com.notesapp.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.notesapp.domain.model.Priority

/**
 * A saved thing (movie, book, place, purchase, ...). [collectionId] is null while the item sits in the Smart Inbox.
 */
@kotlinx.serialization.Serializable
@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["collectionId", "status"]),
        Index(value = ["isArchived"]),
        Index(value = ["createdAt"]),
    ],
)
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val collectionId: Long? = null,
    val title: String,
    val description: String? = null,
    /** Key of a [com.notesapp.domain.model.StatusDef] of the owning collection, or `INBOX` for inbox items. */
    val status: String,
    /** 0.5 to 5.0 in 0.5 steps, null when unrated. */
    val rating: Float? = null,
    /** 0 = none ... 4 = urgent; see [Priority]. */
    val priority: Int = 0,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    /** Reserved for type-specific metadata (runtime, author, price, ...) added in a later phase. */
    val metadataJson: String? = null,
    val imageUri: String? = null,
    val externalUrl: String? = null,
    val reminderAt: Long? = null,
    val lastSelectedAt: Long? = null,
    val selectedCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null,
) {
    val priorityLevel: Priority get() = Priority.fromLevel(priority)
}
