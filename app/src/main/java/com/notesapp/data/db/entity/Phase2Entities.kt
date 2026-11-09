package com.notesapp.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.notesapp.domain.model.EntityType
import kotlinx.serialization.Serializable

@Serializable
enum class SelectionAction { DRAWN, ACCEPTED, SKIPPED, COMPLETED }

@Serializable
@Entity(
    tableName = "selection_history",
    foreignKeys = [ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("itemId"), Index("selectedAt")],
)
data class SelectionHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val selectedAt: Long,
    val actionTaken: SelectionAction,
)

@Serializable
@Entity(
    tableName = "knowledge_links",
    primaryKeys = ["sourceId", "sourceType", "targetId", "targetType"],
    indices = [Index(value = ["targetType", "targetId"])],
)
data class KnowledgeLinkEntity(
    val sourceId: Long,
    val sourceType: EntityType,
    val targetId: Long,
    val targetType: EntityType,
    val relationNote: String? = null,
)
