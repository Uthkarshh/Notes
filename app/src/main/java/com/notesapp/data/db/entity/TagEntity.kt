package com.notesapp.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.notesapp.domain.model.EntityType

@kotlinx.serialization.Serializable
@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)],
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Case-insensitively unique. */
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val colorHex: String? = null,
)

/** Links a tag to an item, fact or note. [targetId] is polymorphic, so it cannot be a foreign key. */
@kotlinx.serialization.Serializable
@Entity(
    tableName = "entity_tag_cross_ref",
    primaryKeys = ["tagId", "targetId", "targetType"],
    foreignKeys = [
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["targetType", "targetId"])],
)
data class EntityTagCrossRef(
    val tagId: Long,
    val targetId: Long,
    val targetType: EntityType,
)
