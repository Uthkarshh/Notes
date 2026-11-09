package com.notesapp.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.notesapp.domain.model.MasteryState

@kotlinx.serialization.Serializable
@Entity(tableName = "facts")
data class FactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The fact itself; Markdown formatting is allowed. */
    val text: String,
    val source: String? = null,
    val topic: String? = null,
    val personalNote: String? = null,
    val isFavorite: Boolean = false,
    val masteryState: MasteryState = MasteryState.NEW,
    val shownCount: Int = 0,
    val lastShownAt: Long? = null,
    val createdAt: Long,
)
