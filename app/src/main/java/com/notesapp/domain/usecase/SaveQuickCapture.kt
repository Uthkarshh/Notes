package com.notesapp.domain.usecase

import com.notesapp.data.db.entity.CollectionEntity
import com.notesapp.data.repository.ItemRepository
import com.notesapp.data.repository.NewItem
import com.notesapp.domain.capture.ParsedCapture
import com.notesapp.domain.model.Priority
import javax.inject.Inject

/** Result of saving a Quick Capture; [collection] is null when the item went to the Smart Inbox. */
data class CaptureResult(val itemId: Long, val collection: CollectionEntity?)

/** Saves a parsed Quick Capture, filing it into [target] or, when that is null, into the Smart Inbox. */
class SaveQuickCapture @Inject constructor(private val items: ItemRepository) {

    suspend operator fun invoke(parsed: ParsedCapture, target: CollectionEntity?): CaptureResult? {
        if (parsed.title.isBlank()) return null
        val id = items.create(
            NewItem(
                title = parsed.title,
                collection = target,
                tags = parsed.tags,
                priority = parsed.priority ?: Priority.NONE,
                url = parsed.url,
                captureCategory = parsed.verbCategory,
                captureCollectionQuery = parsed.collectionQuery,
            ),
        )
        return CaptureResult(id, target)
    }
}
