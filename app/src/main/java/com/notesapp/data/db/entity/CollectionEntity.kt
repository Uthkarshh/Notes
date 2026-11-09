package com.notesapp.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.notesapp.domain.model.CategoryType
import com.notesapp.domain.model.StatusSchema

@kotlinx.serialization.Serializable
@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val categoryType: CategoryType,
    val iconName: String,
    /** Accent colour as `#RRGGBB`. */
    val colorHex: String,
    /** Ordered lifecycle statuses; see [StatusSchema]. */
    val statusSchemaJson: String,
    val sortOrder: Int,
    val createdAt: Long,
) {
    val schema: StatusSchema get() = StatusSchema.fromJson(statusSchemaJson)
}
